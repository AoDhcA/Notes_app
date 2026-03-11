package com.example.notes;

import android.annotation.SuppressLint;
import android.app.Dialog;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements DocxAdapter.OnItemClickListener {
    private RecyclerView recyclerView;// создание переменной recyclerView переменной типа RecyclerView
    private DocxAdapter adapter; // создание переменной adapter типа DocxAdapter
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

        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Ваша логика при нажатии "назад"
                if (adapter.isSelectionMode()) {
                    adapter.clearSelection();
                    hideDeleteButton();
                } else {
                    this.setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        // Регистрация callback у диспетчера
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


        // Передача this как слушатель кликов
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

        // Настройка бесконечного скролла
        setupInfiniteScroll();

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Обновление данных
                loadFirstPage();
            }
        });
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

    @SuppressLint("StaticFieldLeak")
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

        // Настройка RecyclerView
        SearchByTagAdapter tagAdapter = new SearchByTagAdapter(allTags, new SearchByTagAdapter.OnTagClickListener() {
            @Override
            public void onTagClick(String tag) {
                if (tag != null) {
                    // Выбран тег - применение фильтра и закрытие диалога
                    onTagSelected(tag);
                    dialog.dismiss();
                } else {
                    // Тег снят - сброс фильтра
                    onTagSelected(null);
                    dialog.dismiss();
                }
            }
        });

        // Если уже есть активный фильтр - показать его в адаптере
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

    private void applyTagFilter(String tag) {
        if (tag == null || tag.isEmpty()) {
            // Вывести все документы
            recyclerView.addOnScrollListener(scrollListener);
            loadFirstPage();
            currentFilterTag = null;
        } else {
            // Фильтр по тегу
            recyclerView.removeOnScrollListener(scrollListener);

            List<DocxFile> filteredFiles = databaseHelper.searchByTags(new String[]{tag});

            fileList.clear();
            fileList.addAll(filteredFiles);
            adapter.updateList(fileList);

            updateEmptyState();
            currentFilterTag = tag;

            // Обновление индикатора
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

    private void performFileDeletion(List<DocxFile> filesToDelete) {
        boolean allDeleted = true;
        for (DocxFile file : filesToDelete) {
            File fileToDelete = new File(file.getFilePath());

            // Удаление из БД
            databaseHelper.deleteDocument(file.getFilePath());

            // удаление из файловой системы
            if (fileToDelete.exists() && !fileToDelete.delete()) {
                allDeleted = false;
                Log.e("FileDelete", "Не удалось удалить файл: " + file.getFilePath());
            } else {
                Log.d("FileDelete", "Файл удален: " + file.getFilePath());
            }
        }

        // сброс выделения перед обновлением списка
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

    // Асинхронная очистка устаревших записей в БД
    private void cleanupOrphanedDatabaseEntriesAsync() {
        new Thread(() -> {
            File directory = getDocumentsDirectory();
            cleanupOrphanedDatabaseEntries(directory);
        }).start();
    }

    // Очистка устаревших записей в БД
    private void cleanupOrphanedDatabaseEntries(File directory) {
        try {
            List<DocxFile> dbFiles = databaseHelper.getAllDocuments();
            Set<String> fileSystemPaths = new HashSet<>();

            // Получение всех файлов docx из директории
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
                    // удаление из БД если их нет в файловой системе
                    databaseHelper.deleteDocument(dbFile.getFilePath());
                    removedCount++;
                    Log.d("Cleanup", "Удален отсутствующий файл из БД: " + dbFile.getFilePath());
                }
            }

            if (removedCount > 0) {
                Log.d("Cleanup", "Очистка завершена. Удалено записей: " + removedCount);
                }

        } catch (Exception e) {
            Log.e("Cleanup", "Ошибка при очистке БД", e);
        }
    }

    private void createNewFile() {
        // Запуск TextEditorActivity без параметров для создания нового файла
        Intent intent = new Intent(this, TextEditorActivity.class);
        startActivity(intent);
    }

    private void loadFiles() {
        loadFirstPage();
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainDebug", "onResume вызван");

        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(0, 0);
        }

        // Восстановление фильтра, если он был
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


    // Метод для установки цвета контента из списка конфигурации
    private void setContentViewColor(View contentView, int color) {
        if (contentView instanceof TextView) {
            TextView textView = (TextView) contentView;

            // Проверка - есть ли текст
            if (textView.getText().length() > 0) {
                // Если текстовая кнопка - изменение цвета текста
                textView.setTextColor(color);
            } else {
                // Если иконка - изменение цвета через ColorFilter
                Drawable background = textView.getBackground();
                if (background != null) {
                    background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                    textView.invalidate(); // обновление view
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

    private void updateFilterIndicator() {
        filterIndicator = findViewById(R.id.filterIndicator);

        if (filterIndicator != null) {
            if (selectedTags.isEmpty()) {
                filterIndicator.setVisibility(View.GONE);
            } else {
                filterIndicator.setVisibility(View.VISIBLE);
                // Получение первого (и единственного) выбранного тега
                String selectedTag = selectedTags.iterator().next();
                filterIndicator.setText("Срт. по: " + selectedTag);
            }
        }
    }

    private void setupMultipleButtons(ButtonConfig[] configs) { // установка анимации для кнопок
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
    protected void onDestroy() {
        super.onDestroy();
        // Убираем слушатель скролла при уничтожении активности
        if (recyclerView != null && scrollListener != null) {
            recyclerView.removeOnScrollListener(scrollListener);
        }
    }

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

            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }

            // Проверка: что активность еще существует
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
                // Замена списка
                fileList.clear();
                fileList.addAll(files);

                if (adapter != null) {
                    adapter.updateList(fileList);
                }
            }

            // Проверка: есть ли еще данные
            hasMore = databaseHelper.hasMoreDocuments(currentPage);

            Log.d("InfiniteScroll", "Загружено: " + fileList.size() + " документов, есть еще: " + hasMore);

            // Обновление состояния пустого списка
            updateEmptyState();

            // Прокрутка к началу только при первой загрузке
            if (!isLoadMore) {
                resetScrollToTop();
            }
        }
    }

    private void updateEmptyState() {
        if (emptyStateView != null && recyclerView != null) {
            if (fileList.isEmpty() && !isLoading) {
                // Показать empty state, скрыть recyclerView
                emptyStateView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                // Скрыть empty state, показать recyclerView
                emptyStateView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void resetScrollToTop() {
        if (layoutManager != null) {
            // post для гарантии что прокрутка после рендера
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

    // Настройка бесконечного скролла
    private void setupInfiniteScroll() {
        scrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                //int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                //int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
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

    // Загрузка первой страницы
    private void loadFirstPage() {
        currentPage = 0;
        hasMore = true;
        fileList.clear();

        new LoadDocxFilesTask(0, false).execute();
    }

    // Загрузка следующей страницы
    private void loadNextPage() {
        if (!isLoading && hasMore) {
            currentPage++;
            new LoadDocxFilesTask(currentPage, true).execute();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Сохранение текущего тега фильтра
        if (currentFilterTag != null) {
            outState.putString("currentFilterTag", currentFilterTag);
        }

        // Сохранение страницы
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