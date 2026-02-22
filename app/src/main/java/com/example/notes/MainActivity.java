package com.example.notes;

import static com.google.android.material.internal.ViewUtils.hideKeyboard;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements DocxAdapter.OnItemClickListener {
    private RecyclerView recyclerView;// создание переменной recyclerView переменной типа RecyclerView
    private DocxAdapter adapter; // создание переменной adapter типа DocxAdapter
    //private LinearLayoutManager layoutManager;
    private GridLayoutManager layoutManager;
    private List<DocxFile> fileList = new ArrayList<>();
    private CardView deleteButton;
    private DatabaseHelper databaseHelper;
    private List<String> allTags = new ArrayList<>();
    private Set<String> selectedTags = new HashSet<>();
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    //private ProgressBar progressBar;
    private String currentFilterTag = null;
    // слушатель скролла
    private RecyclerView.OnScrollListener scrollListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View emptyStateView;
    private TextView filterIndicator;

    private static class ButtonConfig { //oбъявление класса параметров цветов кнопок
        private List<DocxFile> fileList = new ArrayList<>();
        int cardViewId; // объявление id cardview кнопки
        int textViewId; // объявление id textview кнопки
        int normalColor; // объявление цвета отпущенной кнопки
        int pressedColor; // объявление цвета нажатой кнопки
        int normalTextColor; // объявление цвета текста отпущенной кнопки
        int pressedTextColor; // объявление цвета текста нажатой кнопки


        ButtonConfig(int cardViewId, int textViewId, int normalColor, int pressedColor,
                     int normalTextColor, int pressedTextColor) {
            this.cardViewId = cardViewId;
            this.textViewId = textViewId;
            this.normalColor = normalColor;
            this.pressedColor = pressedColor;
            this.normalTextColor = normalTextColor;
            this.pressedTextColor = pressedTextColor;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Инициализация базы данных
        databaseHelper = new DatabaseHelper(this);
        //syncFilesWithDatabase();

        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Ваша логика при нажатии "назад"
                if (adapter.isSelectionMode()) {
                    // Пример: выход из режима выделения
                    adapter.clearSelection();
                    hideDeleteButton();
                } else {
                    // Если callback включен (isEnabled=true),
                    // вызов этого метода завершает Activity по умолчанию
                    this.setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        // Регистрируем callback у диспетчера
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        // Инициализация кнопки удаления
        deleteButton = findViewById(R.id.DeleteButton);
        deleteButton.setVisibility(View.INVISIBLE);
        deleteButton.setClickable(false);

        deleteButton.setOnClickListener(v -> {
            deleteSelectedFiles();
        });


        // подключение и настройка адаптера, далее передача в него информаци о файлах через класс
        recyclerView = findViewById(R.id.recyclerView);
        layoutManager = new GridLayoutManager(this, 2); // 2 колонки
        recyclerView.setLayoutManager(layoutManager);


        // Передаем this как слушатель кликов
        adapter = new DocxAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        loadFiles();
        loadAllTags();

        // Массив для конфигураций кнопок
        ButtonConfig[] buttonConfigs = { // конфиг всех кнопок
                new ButtonConfig(
                        R.id.addButton,
                        R.id.addButtonIcon,
                        Color.parseColor("#FFFFFF"),  // нормальный цвет
                        Color.parseColor("#FF000000"),  // цвет при нажатии
                        Color.parseColor("#FF000000"),                  // нормальный цвет текста
                        Color.parseColor("#FFFFFF")   // цвет текста при нажатии
                ),
                new ButtonConfig(
                        R.id.searchButton,
                        R.id.searchButtonIcon,
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FF000000"),
                        Color.parseColor("#FF000000"),
                        Color.parseColor("#FFFFFF")
                ),
                new ButtonConfig(
                        R.id.DeleteButton,
                        R.id.DeleteButtonIcon,
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FF000000"),
                        Color.parseColor("#FF000000"),
                        Color.parseColor("#FFFFFF")
                ),
                new ButtonConfig(
                        R.id.tagButton,
                        R.id.tagButtonIcon,
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FF000000"),
                        Color.parseColor("#FF000000"),
                        Color.parseColor("#FFFFFF")
                )
        };
        setupMultipleButtons(buttonConfigs);
        initEmptyStateView();


        // Обработчик для кнопки добавления
        CardView addButton = findViewById(R.id.addButton);
        if (addButton != null) {
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    createNewFile();
                }
            });
        }

        CardView searchButton = findViewById(R.id.searchButton);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> showSearchDialog());
        }

        CardView tagButton = findViewById(R.id.tagButton);
        if (tagButton != null) {
            tagButton.setOnClickListener(v -> {
                showTagsDropdown();
            });
        }
        // Инициализация ProgressBar
//        progressBar = findViewById(R.id.progressBar);
//        if (progressBar != null) {
//            progressBar.setVisibility(View.GONE);
//        }

        // Настраиваем бесконечный скролл
        setupInfiniteScroll();

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Обновляем данные
                loadFirstPage();
            }
        });
        // Первоначальная загрузка
        //loadFirstPage();

    }// конец oncreate---------------------------------------------------------------------------------------------------------------


    /**
     * Инициализация Empty State View
     */
    private void initEmptyStateView() {
        emptyStateView = findViewById(R.id.emptyStateView);


        // Сначала скрываем empty state
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    //    private void loadAllTags() {
//        new AsyncTask<Void, Void, List<String>>() {
//            @Override
//            protected List<String> doInBackground(Void... voids) {
//                return databaseHelper.getAllUniqueTags();
//            }
//
//            @Override
//            protected void onPostExecute(List<String> tags) {
//                allTags.clear();
//                allTags.addAll(tags);
//                Log.d("Tags", "Загружено тегов: " + allTags.size());
//            }
//        }.execute();
//    }
    private void loadAllTags() {
        new AsyncTask<Void, Void, List<String>>() {
            @Override
            protected List<String> doInBackground(Void... voids) {
                return databaseHelper.getAllUniqueTagsWithDates();
            }

            @Override
            protected void onPostExecute(List<String> tags) {
                allTags.clear();
                allTags.addAll(tags);
                Log.d("Tags", "Загружено тегов (включая даты): " + allTags.size());

                // Для отладки выведем первые 10 тегов
                for (int i = 0; i < Math.min(10, allTags.size()); i++) {
                    Log.d("Tags", "Тег " + i + ": " + allTags.get(i));
                }
            }
        }.execute();
    }

//    @SuppressLint("ClickableViewAccessibility")
//    private void showTagsDropdown() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Выберите теги для фильтрации");
//
//        // Создаем массив для отображения тегов с #
//        String[] displayTags = new String[allTags.size()];
//        for (int i = 0; i < allTags.size(); i++) {
//            displayTags[i] = "#" + allTags.get(i);
//        }
//
//        // Массив для отслеживания выбранных тегов
//        boolean[] checkedTags = new boolean[allTags.size()];
//        for (int i = 0; i < allTags.size(); i++) {
//            checkedTags[i] = selectedTags.contains(allTags.get(i));
//        }
//
//        builder.setMultiChoiceItems(displayTags, checkedTags, (dialog, which, isChecked) -> {
//            String tag = allTags.get(which);
//            if (isChecked) {
//                selectedTags.add(tag);
//            } else {
//                selectedTags.remove(tag);
//            }
//        });
//
//        builder.setPositiveButton("Применить", (dialog, which) -> {
//            applyTagFilter();
//            updateFilterIndicator();
//        });
//
//        builder.setNegativeButton("Сбросить", (dialog, which) -> {
//            selectedTags.clear();
//            loadFiles(); // Показать все файлы
//            Toast.makeText(MainActivity.this, "Фильтр сброшен", Toast.LENGTH_SHORT).show();
//        });
//
//        builder.setNeutralButton("Отмена", null);
//
//        AlertDialog dialog = builder.create();
//        dialog.show();
//    }

    private void showTagsDropdown() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.custom_search_by_tags_view);

        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.6f);
            window.setAttributes(params);
        }

        RecyclerView tagsRecyclerView = dialog.findViewById(R.id.searchByTagsRecyclerView);
        TextView resetButton = dialog.findViewById(R.id.resetSearchButtonText);

        // Настраиваем RecyclerView
        SearchByTagAdapter tagAdapter = new SearchByTagAdapter(allTags, new SearchByTagAdapter.OnTagClickListener() {
            @Override
            public void onTagClick(String tag) {
                if (tag != null) {
                    // Выбран тег - применяем фильтр и закрываем диалог
                    onTagSelected(tag);
                    dialog.dismiss();
                } else {
                    // Тег снят - сбрасываем фильтр
                    onTagSelected(null);
                    dialog.dismiss();
                }
            }
        });

        // Если уже есть активный фильтр, показываем его в адаптере
        if (currentFilterTag != null) {
            tagAdapter.setSelectedTag(currentFilterTag);
        }

        tagsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tagsRecyclerView.setAdapter(tagAdapter);

        resetButton.setOnClickListener(v -> {
            // Сброс фильтра
            onTagSelected(null);
            tagAdapter.clearSelection();
            filterIndicator.setVisibility(View.GONE);
            Toast.makeText(MainActivity.this, "Фильтр сброшен", Toast.LENGTH_SHORT).show();
            // Не закрываем диалог сразу, чтобы пользователь мог выбрать другой тег
        });

        dialog.show();
    }
//    private void showTagsDropdown() {
//        // Создаем кастомный диалог
//        final Dialog dialog = new Dialog(this);
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
//        dialog.setContentView(R.layout.custom_search_by_tags_view);
//
//        // ВАЖНО: разрешаем закрытие при клике вне диалога
//        dialog.setCanceledOnTouchOutside(true);
//        dialog.setCancelable(true);
//
//        // Настраиваем размер и положение диалога
//        Window window = dialog.getWindow();
//        if (window != null) {
//            WindowManager.LayoutParams params = window.getAttributes();
//            params.gravity = Gravity.CENTER;
//            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
//            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
//
//            // ДОБАВЛЯЕМ: прозрачный фон для области вокруг диалога
//            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//
//            // ДОБАВЛЯЕМ: затемнение фона
//            window.setDimAmount(0.6f);
//
//            window.setAttributes(params);
//        }
//
//        // Находим элементы
//        RecyclerView tagsRecyclerView = dialog.findViewById(R.id.searchByTagsRecyclerView);
////        CardView cancelButton = dialog.findViewById(R.id.cancelSearchButtonView);
//        TextView resetButton = dialog.findViewById(R.id.resetSearchButtonText);
//
//        // ДОБАВЛЯЕМ: обработчик закрытия диалога при клике на затемненную область
//        View dialogContainer = dialog.findViewById(android.R.id.content);
//        if (dialogContainer != null) {
//            dialogContainer.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    // Проверяем, что клик был именно на затемненной области, а не на самом диалоге
//                    if (v.getId() == android.R.id.content) {
//                        dialog.dismiss();
//                    }
//                }
//            });
//        }
//
//        // Настраиваем RecyclerView
//        SearchByTagAdapter tagAdapter = new SearchByTagAdapter(allTags, new SearchByTagAdapter.OnTagClickListener() {
//            @Override
//            public void onTagClick(String tag) {
//                if (tag != null) {
//                    // Выбран тег - применяем фильтр и закрываем диалог
//                    selectedTags.clear();
//                    selectedTags.add(tag);
//                    applyTagFilter();
//                    updateFilterIndicator();
//                    dialog.dismiss();
//                } else {
//                    // Тег снят - сбрасываем фильтр
//                    selectedTags.clear();
//                    loadFiles();
//                    updateFilterIndicator();
//                }
//            }
//        });
//
//        // Если уже есть выбранный тег, устанавливаем его в адаптер
//        if (!selectedTags.isEmpty()) {
//            tagAdapter.setSelectedTag(selectedTags.iterator().next());
//        }
//
//        tagsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
//        tagsRecyclerView.setAdapter(tagAdapter);
//
//        // Обработчики кнопок
////        cancelButton.setOnClickListener(v -> {
////            dialog.dismiss();
////        });
//
//        resetButton.setOnClickListener(v -> {
//            // Сброс фильтра
//            selectedTags.clear();
//            loadFiles();
//            updateFilterIndicator();
//            //dialog.dismiss();
//            tagAdapter.clearSelection();
//            Toast.makeText(MainActivity.this, "Фильтр сброшен", Toast.LENGTH_SHORT).show();
//        });
//
//        // ДОБАВЛЯЕМ: слушатель отмены диалога (например, при нажатии кнопки "Назад")
//        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
//            @Override
//            public void onCancel(DialogInterface dialogInterface) {
//                // Ничего не делаем, просто закрываем
//            }
//        });
//
//        // Показываем диалог
//        dialog.show();
//    }

//    private void applyTagFilter() {
//        if (selectedTags.isEmpty()) {
//            loadFiles(); // Если нет выбранных тегов, показать все
//        } else {
//            // Берем первый (и единственный) тег из selectedTags
//            String selectedTag = selectedTags.iterator().next();
//            List<DocxFile> filteredFiles = databaseHelper.searchByTags(new String[]{selectedTag});
//            adapter.updateList(filteredFiles);
//            Toast.makeText(this, "Найдено: " + filteredFiles.size() + " файлов с тегом #" + selectedTag,
//                    Toast.LENGTH_SHORT).show();
//        }
//    }

    //    private void applyTagFilter() {
//        if (selectedTags.isEmpty()) {
//            // Включаем бесконечный скролл при показе всех документов
//            recyclerView.addOnScrollListener(scrollListener);
//            loadFirstPage();
//        } else {
//            // Отключаем бесконечный скролл при фильтрации
//            recyclerView.removeOnScrollListener(scrollListener);
//
//            String selectedTag = selectedTags.iterator().next();
//            List<DocxFile> filteredFiles = databaseHelper.searchByTags(new String[]{selectedTag});
//
//            // Показываем все отфильтрованные результаты сразу
//            fileList.clear();
//            fileList.addAll(filteredFiles);
//            adapter.updateList(fileList);
//
//            // Обновляем empty state
//            updateEmptyState();
//
//            Toast.makeText(this, "Найдено: " + filteredFiles.size() + " файлов с тегом #" + selectedTag,
//                    Toast.LENGTH_SHORT).show();
//        }
//    }
//    private void applyTagFilter() {
//        if (selectedTags.isEmpty()) {
//            // Включаем бесконечный скролл при показе всех документов
//            recyclerView.addOnScrollListener(scrollListener);
//            loadFirstPage();
//        } else {
//            // Отключаем бесконечный скролл при фильтрации
//            recyclerView.removeOnScrollListener(scrollListener);
//
//            String selectedTag = selectedTags.iterator().next();
//            List<DocxFile> filteredFiles = databaseHelper.searchByTags(new String[]{selectedTag});
//
//            // Показываем все отфильтрованные результаты сразу
//            fileList.clear();
//            fileList.addAll(filteredFiles);
//            adapter.updateList(fileList);
//
//            // Обновляем empty state
//            updateEmptyState();
//
//            // Обновляем индикатор с названием тега
//            updateFilterIndicator();
//
//            Toast.makeText(this, "Найдено: " + filteredFiles.size() + " файлов с тегом #" + selectedTag,
//                    Toast.LENGTH_SHORT).show();
//        }
//    }
    private void applyTagFilter(String tag) {
        if (tag == null || tag.isEmpty()) {
            // Показываем все документы
            recyclerView.addOnScrollListener(scrollListener);
            loadFirstPage();
            currentFilterTag = null;
        } else {
            // Фильтруем по тегу
            recyclerView.removeOnScrollListener(scrollListener);

            List<DocxFile> filteredFiles = databaseHelper.searchByTags(new String[]{tag});

            fileList.clear();
            fileList.addAll(filteredFiles);
            adapter.updateList(fileList);

            updateEmptyState();
            currentFilterTag = tag;

            // Обновляем индикатор
            updateFilterIndicator();

            Toast.makeText(this, "Найдено: " + filteredFiles.size() + " файлов с тегом #" + tag,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void onTagSelected(String tag) {
        if (tag != null) {
            selectedTags.clear();
            selectedTags.add(tag);
            currentFilterTag = tag;
            applyTagFilter(tag);
        } else {
            selectedTags.clear();
            currentFilterTag = null;
            applyTagFilter(null);
        }
    }

    /**
     * ОБНОВЛЯЕМ метод при сбросе фильтра
     */
    private void resetFilterAndEnableInfiniteScroll() {
        selectedTags.clear();
        // Включаем бесконечный скролл обратно
        recyclerView.addOnScrollListener(scrollListener);
        loadFirstPage();
    }

    private void showDeleteButton() {
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setClickable(true);
        deleteButton.setAlpha(0f);
        deleteButton.animate().alpha(1f).setDuration(300).start();
    }

    private void hideDeleteButton() {
        deleteButton.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    deleteButton.setVisibility(View.INVISIBLE);
                    deleteButton.setClickable(false);
                })
                .start();
    }

    @SuppressLint("SetTextI18n")
    private void deleteSelectedFiles() {
        List<DocxFile> selectedFiles = adapter.getSelectedFiles();
        if (selectedFiles.isEmpty()) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_file_delete);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            window.setAttributes(params);
        }

        TextView deleteTextView = dialog.findViewById(R.id.deleteTextView);
        CardView btnCancelDeleteFile = dialog.findViewById(R.id.btnCancelDeleteFile);
        CardView btnDeleteFile = dialog.findViewById(R.id.btnDeleteFile);

        deleteTextView.setText("Удалить " + selectedFiles.size() + " файл(ов)?");
        View.OnClickListener dismissListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        };

        btnCancelDeleteFile.setOnClickListener(dismissListener);

        btnDeleteFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performFileDeletion(selectedFiles);
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    //    private void performFileDeletion(List<DocxFile> filesToDelete) {
//        boolean allDeleted = true;
//        int deletedCount = 0;
//
//        for (DocxFile file : filesToDelete) {
//            File fileToDelete = new File(file.getFilePath());
//
//            // УДАЛЯЕМ ИЗ БАЗЫ ДАННЫХ В ЛЮБОМ СЛУЧАЕ
//            databaseHelper.deleteDocument(file.getFilePath());
//            deletedCount++;
//
//            // УДАЛЯЕМ ФАЙЛ ИЗ ФАЙЛОВОЙ СИСТЕМЫ (если существует)
//            if (fileToDelete.exists() && !fileToDelete.delete()) {
//                allDeleted = false;
//                Log.e("FileDelete", "Не удалось удалить файл: " + file.getFilePath());
//            } else {
//                Log.d("FileDelete", "Файл удален: " + file.getFilePath());
//            }
//        }
//
//        // Обновляем список
//        refreshFileList();
//        hideDeleteButton();
//
//        String message;
//        if (allDeleted) {
//            message = "Удалено " + deletedCount + " файл(ов)";
//        } else {
//            message = "Удалено " + deletedCount + " записей, но некоторые файлы не найдены";
//        }
//
//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
//
//        // ВЫЗЫВАЕМ ОЧИСТКУ ДЛЯ ПОДСТРАХОВКИ
//        cleanupOrphanedDatabaseEntriesAsync();
//    }
    private void performFileDeletion(List<DocxFile> filesToDelete) {
        boolean allDeleted = true;
        for (DocxFile file : filesToDelete) {
            File fileToDelete = new File(file.getFilePath());

            // УДАЛЯЕМ ИЗ БАЗЫ ДАННЫХ В ЛЮБОМ СЛУЧАЕ
            databaseHelper.deleteDocument(file.getFilePath());

            // УДАЛЯЕМ ФАЙЛ ИЗ ФАЙЛОВОЙ СИСТЕМЫ (если существует)
            if (fileToDelete.exists() && !fileToDelete.delete()) {
                allDeleted = false;
                Log.e("FileDelete", "Не удалось удалить файл: " + file.getFilePath());
            } else {
                Log.d("FileDelete", "Файл удален: " + file.getFilePath());
            }
        }

        // ВАЖНО: СБРАСЫВАЕМ ВЫДЕЛЕНИЕ ПЕРЕД ОБНОВЛЕНИЕМ СПИСКА
        if (adapter != null) {
            adapter.clearSelection();
        }

        // Обновляем список
        refreshFileList();

        // Скрываем кнопку удаления (на всякий случай)
        hideDeleteButton();

        String message;
        if (allDeleted) {
            message = "Удалено " + filesToDelete.size() + " файл(ов)";
        } else {
            message = "Удалено " + filesToDelete.size() + " записей, но некоторые файлы не найдены";
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        // Очистка БД для подстраховки
        cleanupOrphanedDatabaseEntriesAsync();
    }

    /**
     * Асинхронная очистка устаревших записей в БД
     */
    private void cleanupOrphanedDatabaseEntriesAsync() {
        new Thread(() -> {
            File directory = getDocumentsDirectory();
            cleanupOrphanedDatabaseEntries(directory);
        }).start();
    }

    /**
     * Очистка устаревших записей в БД - ИСПРАВЛЕННАЯ ВЕРСИЯ
     */
    private void cleanupOrphanedDatabaseEntries(File directory) {
        try {
            List<DocxFile> dbFiles = databaseHelper.getAllDocuments();
            Set<String> fileSystemPaths = new HashSet<>();

            // ПОЛУЧАЕМ ВСЕ ФАЙЛЫ .docx ИЗ ДИРЕКТОРИИ
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".docx"));
            if (files != null) {
                for (File file : files) {
                    fileSystemPaths.add(file.getAbsolutePath());
                    Log.d("Cleanup", "Файл в системе: " + file.getAbsolutePath());
                }
            }

            int removedCount = 0;
            for (DocxFile dbFile : dbFiles) {
                if (!fileSystemPaths.contains(dbFile.getFilePath())) {
                    // УДАЛЯЕМ ИЗ БАЗЫ ДАННЫХ, ЕСЛИ ФАЙЛА НЕТ В ФАЙЛОВОЙ СИСТЕМЕ
                    databaseHelper.deleteDocument(dbFile.getFilePath());
                    removedCount++;
                    Log.d("Cleanup", "Удален отсутствующий файл из БД: " + dbFile.getFilePath());
                }
            }

            if (removedCount > 0) {
                Log.d("Cleanup", "Очистка завершена. Удалено записей: " + removedCount);
                //runOnUiThread(() ->
                //        Toast.makeText(this, "Очистка БД: удалено " + removedCount + " записей", Toast.LENGTH_SHORT).show());
            }

        } catch (Exception e) {
            Log.e("Cleanup", "Ошибка при очистке БД", e);
        }
    }

    private void createNewFile() {
        // Запускаем TextEditorActivity без параметров для создания нового файла
        Intent intent = new Intent(this, TextEditorActivity.class);
        startActivity(intent);
    }

    //    private void loadFiles() {
//        // Просто запускаем задачу загрузки из БД
//        new LoadDocxFilesTask().execute();
//    }
    private void loadFiles() {
        loadFirstPage();
    }

//    // ДОБАВЛЯЕМ: очистка устаревших записей в БД
//    private void cleanupOrphanedDatabaseEntries(File directory) {
//        List<DocxFile> dbFiles = databaseHelper.getAllDocuments();
//        Set<String> fileSystemPaths = new HashSet<>();
//
//        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".docx"));
//        if (files != null) {
//            for (File file : files) {
//                fileSystemPaths.add(file.getAbsolutePath());
//            }
//        }
//
//        for (DocxFile dbFile : dbFiles) {
//            if (!fileSystemPaths.contains(dbFile.getFilePath())) {
//                databaseHelper.deleteDocument(dbFile.getFilePath());
//                Log.d("Sync", "Удален отсутствующий файл из БД: " + dbFile.getFilePath());
//            }
//        }
//    }

    @Override
    public void onItemClick(DocxFile file) {
        Log.d("MainActivity", "Клик по файлу: " + file.getFileName());
        openDocxFile(file);
    }

    @Override
    public void onSelectionModeStarted() {
        Log.d("MainActivity", "Режим выделения начат");
        showDeleteButton();
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        Log.d("MainActivity", "Выбрано файлов: " + selectedCount);
        if (selectedCount == 0) {
            adapter.clearSelection();
            hideDeleteButton();
        }
    }

    public void openDocxFile(DocxFile file) {
        Intent intent = new Intent(this, TextEditorActivity.class);
        intent.putExtra("FILE_PATH", file.getFilePath());
        intent.putExtra("FILE_NAME", file.getFileName());
        startActivity(intent);
    }

    //    @Override
//    protected void onResume() {
//        super.onResume();
//        if (layoutManager != null) {
//            layoutManager.scrollToPositionWithOffset(0, 0);
//        }
//        // ОБНОВЛЯЕМ список при возврате в активность
//        refreshFileList();
//        loadAllTags();
//
//        // ПЕРИОДИЧЕСКАЯ ОЧИСТКА БАЗЫ ДАННЫХ ПРИ ЗАПУСКЕ
//        cleanupOrphanedDatabaseEntriesAsync();
//    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainDebug", "onResume вызван");

        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(0, 0);
        }

        // Восстанавливаем фильтр, если он был
        if (currentFilterTag != null && !currentFilterTag.isEmpty()) {
            Log.d("MainDebug", "Восстанавливаем фильтр: " + currentFilterTag);
            applyTagFilter(currentFilterTag);
        } else {
            loadFirstPage();
        }

        loadAllTags();
    }

    private void refreshFileList() {
        loadFirstPage();
    }


    // метод для установки цвета контента из списка конфигурации
    private void setContentViewColor(View contentView, int color) {
        if (contentView instanceof TextView) {
            TextView textView = (TextView) contentView;

            // Проверяем, есть ли текст
            if (textView.getText().length() > 0) {
                // Это текстовая кнопка - меняем цвет текста
                textView.setTextColor(color);
            } else {
                // Это иконка - меняем цвет через ColorFilter
                Drawable background = textView.getBackground();
                if (background != null) {
                    background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    textView.invalidate(); // Важно: обновляем view
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // установка анимации
    private void setupButtonAnimation(CardView cardView, View contentView,
                                      int normalColor, int pressedColor,
                                      int normalContentColor, int pressedContentColor) {

        final float originalScaleX = cardView.getScaleX();
        final float originalScaleY = cardView.getScaleY();
        final float originalElevation = cardView.getCardElevation();

        cardView.setCardBackgroundColor(normalColor);
        setContentViewColor(contentView, normalContentColor);

        cardView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        cardView.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                        cardView.setCardElevation(2f);
                        cardView.setCardBackgroundColor(pressedColor);
                        setContentViewColor(contentView, pressedContentColor);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cardView.animate().scaleX(originalScaleX).scaleY(originalScaleY)
                                .setDuration(120).start();
                        cardView.setCardElevation(originalElevation);
                        cardView.setCardBackgroundColor(normalColor);
                        setContentViewColor(contentView, normalContentColor);

                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            cardView.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    //    private void updateFilterIndicator() {
//        TextView filterIndicator = findViewById(R.id.filterIndicator);
//        if (filterIndicator != null) {
//            if (selectedTags.isEmpty()) {
//                filterIndicator.setVisibility(View.GONE);
//            } else {
//                filterIndicator.setVisibility(View.VISIBLE);
//                filterIndicator.setText("Тегов: " + selectedTags.size());
//            }
//        }
//    }
    private void updateFilterIndicator() {
        filterIndicator = findViewById(R.id.filterIndicator);

        if (filterIndicator != null) {
            if (selectedTags.isEmpty()) {
                filterIndicator.setVisibility(View.GONE);
            } else {
                filterIndicator.setVisibility(View.VISIBLE);
                // Получаем первый (и единственный) выбранный тег
                String selectedTag = selectedTags.iterator().next();
                filterIndicator.setText("Срт. по: " + selectedTag);
            }
        }
    }

    private void setupMultipleButtons(ButtonConfig[] configs) { // установака анимации для кнопок
        for (ButtonConfig config : configs) {
            CardView cardView = findViewById(config.cardViewId);
            TextView textView = findViewById(config.textViewId);

            setupButtonAnimation(cardView, textView,
                    config.normalColor, config.pressedColor,
                    config.normalTextColor, config.pressedTextColor);
        }
    }

    private File getDocumentsDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "NotesApp");
        } else {
            return new File(Environment.getExternalStorageDirectory(), "Documents/NotesApp");
        }

    }


    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (databaseHelper != null) {
//            databaseHelper.close();
//        }
//    }
    protected void onDestroy() {
        super.onDestroy();
        // Убираем слушатель скролла при уничтожении активности
        if (recyclerView != null && scrollListener != null) {
            recyclerView.removeOnScrollListener(scrollListener);
        }
    }

//    private class LoadDocxFilesTask extends AsyncTask<Void, Void, List<DocxFile>> {
//        @Override
////        protected List<DocxFiles> doInBackground(Void... voids) {
////            // ПРОСТО берем данные из БД
////            return databaseHelper.getAllDocuments();
////        }
//        protected List<DocxFile> doInBackground(Void... voids) {
//            // ПРОСТО берем данные из БД
//            return databaseHelper.getAllDocuments();
//        }
//
//        @Override
//        protected void onPostExecute(List<DocxFile> files) {
//            adapter.updateList(files);
//            resetScrollToTop();
//        }
//    }

    /**
     * Обновленный AsyncTask для бесконечного скролла
     */
    /**
     * Обновленный AsyncTask с обработкой empty state
     */
    private class LoadDocxFilesTask extends AsyncTask<Void, Void, List<DocxFile>> {
        private int page;
        private boolean isLoadMore;

        public LoadDocxFilesTask(int page, boolean isLoadMore) {
            this.page = page;
            this.isLoadMore = isLoadMore;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            isLoading = true;

//            if (isLoadMore && progressBar != null) {
//                progressBar.setVisibility(View.VISIBLE);
//            }

            // Скрываем empty state во время загрузки
            if (!isLoadMore && emptyStateView != null) {
                emptyStateView.setVisibility(View.GONE);
            }
        }

        @Override
        protected List<DocxFile> doInBackground(Void... voids) {
            if (isLoadMore) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return databaseHelper.getDocumentsWithPagination(page);
        }

        @Override
        protected void onPostExecute(List<DocxFile> files) {
            isLoading = false;

            // Скрываем ProgressBar
//            if (progressBar != null) {
//                progressBar.setVisibility(View.GONE);
//            }
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }

            // Проверяем, что активность еще существует
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (isLoadMore) {
                // Безопасное добавление
                List<DocxFile> newList = new ArrayList<>(fileList);
                newList.addAll(files);
                fileList = newList;

                if (adapter != null) {
                    adapter.updateList(fileList);
                }
            } else {
                // Заменяем список
                fileList.clear();
                fileList.addAll(files);

                if (adapter != null) {
                    adapter.updateList(fileList);
                }
            }

            // Проверяем, есть ли еще данные
            hasMore = databaseHelper.hasMoreDocuments(currentPage);

            Log.d("InfiniteScroll", "Загружено: " + fileList.size() + " документов, есть еще: " + hasMore);

            // Обновляем состояние пустого списка
            updateEmptyState();

            // Прокручиваем к началу только при первой загрузке
            if (!isLoadMore) {
                resetScrollToTop();
            }
        }
    }

    private void updateEmptyState() {
        if (emptyStateView != null && recyclerView != null) {
            if (fileList.isEmpty() && !isLoading) {
                // Показываем empty state, скрываем recyclerView
                emptyStateView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                // Скрываем empty state, показываем recyclerView
                emptyStateView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void resetScrollToTop() {
        if (layoutManager != null) {
            // Используем post для гарантии что прокрутка после рендера
            recyclerView.post(new Runnable() {
                @Override
                public void run() {
                    layoutManager.scrollToPositionWithOffset(0, 0);
                }
            });
        }
    }

    private void showSearchDialog() {
        SearchDialog searchDialog = new SearchDialog(this);
        searchDialog.show();

        // Настройка размера диалога
        Window window = searchDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    /**
     * Настройка бесконечного скролла
     */
    private void setupInfiniteScroll() {
        scrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

                // Проверяем, достигли ли мы конца списка
                boolean isAtEnd = (lastVisibleItemPosition >= totalItemCount - 5); // Загружаем, когда осталось 5 элементов

                if (!isLoading && hasMore && isAtEnd) {
                    loadNextPage();
                }
            }
        };

        recyclerView.addOnScrollListener(scrollListener);
    }

    /**
     * Загрузка первой страницы
     */
    private void loadFirstPage() {
        currentPage = 0;
        hasMore = true;
        fileList.clear();

        new LoadDocxFilesTask(0, false).execute();
    }

    /**
     * Загрузка следующей страницы
     */
    private void loadNextPage() {
        if (!isLoading && hasMore) {
            currentPage++;
            new LoadDocxFilesTask(currentPage, true).execute();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Сохраняем текущий тег фильтра
        if (currentFilterTag != null) {
            outState.putString("currentFilterTag", currentFilterTag);
        }

        // Сохраняем страницу
        outState.putInt("currentPage", currentPage);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // Восстанавливаем фильтр
        String savedFilterTag = savedInstanceState.getString("currentFilterTag");
        if (savedFilterTag != null) {
            currentFilterTag = savedFilterTag;
            selectedTags.clear();
            selectedTags.add(savedFilterTag);
        }

        // Восстанавливаем страницу
        currentPage = savedInstanceState.getInt("currentPage", 0);
    }
}