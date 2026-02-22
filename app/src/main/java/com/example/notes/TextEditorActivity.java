package com.example.notes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.io.File;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class TextEditorActivity extends AppCompatActivity implements DocxAdapter.OnItemClickListener, BlockFocusListener, TableBlock.OnTableDeleteListener {
    private EditText titleEditText;
    private String filePath;
    private String originalFileName;
    private boolean isContentChanged = false;
    private boolean isNewFile = false;

    private String originalTitle;
    private List<ContentBlock> originalBlocks = new ArrayList<>();
    private boolean isInitialLoadComplete = false;

    // ДОБАВЛЯЕМ: поля для блочного редактора
    private LinearLayout blocksContainer;
    private BlockManager blockManager;
    private CardView btnAddTable;

    // ДОБАВЛЯЕМ: поля для отслеживания курсора
    private String focusedBlockId = null;
    private int cursorPosition = 0;
    private EditText focusedEditText = null;

    // поля для диалога редактирования таблицы
    private CardView tableEditDialog;
    private EditText editRows, editColumns;
    private TagEditText tagEditText;
    private String originalTags = "";
    private TableBlock currentEditingTable;
    private Dialog listDialog;
    private CardView orderedListBtn, unorderedListBtn, removeListBtn, cancelListBtn;
    private CardView textSizeCardView;
    private TextView decreaseTextSizeTV;
    private TextView increaseTextSizeTV;
    private TextView currentTextSizeTV;
    private int currentFontSize = 16; // размер по умолчанию
    private final int MIN_FONT_SIZE = 10;
    private final int MAX_FONT_SIZE = 25;
    private DatabaseHelper databaseHelper;
    private ScrollView scrollView;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        setContentView(R.layout.texteditor);
        databaseHelper = new DatabaseHelper(this);

        Log.d("EditorInit", "Получены данные: filePath=" + filePath + ", fileName=" + originalFileName);
        Log.d("FontSizeDebug", "=== onCreate TextEditorActivity ===");
        initFontSizeControls();

        try {
            // Получаем данные из Intent
            Intent intent = getIntent();
            filePath = intent.getStringExtra("FILE_PATH");
            originalFileName = intent.getStringExtra("FILE_NAME");

            // Проверяем, это новый файл или существующий
            if (filePath == null || originalFileName == null) {
                isNewFile = true;
                createNewFile();
                initTableEditDialog();
                initFontSizeControls();
                initTagsField();
                initListDialog();
            } else {
                initViews();
                loadFileContent();
                debugScrollViewState();
                initTagsField();
                initListDialog();
            }

            // Настройка обработчика кнопки "Назад"
            setupBackPressHandler();

        } catch (Exception e) {
            Log.e("EditorInit", "Критическая ошибка инициализации: " + e.getMessage(), e);
            e.printStackTrace();
            Toast.makeText(this, "Ошибка инициализации редактора: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }

    }

    private void initTagsField() {
        tagEditText = findViewById(R.id.tagEditText);
        Log.d("TagsDebug", "🏷️ Инициализация поля тегов");

        // Загружаем теги из БД при открытии существующего файла
        if (!isNewFile && filePath != null) {
            Log.d("TagsDebug", "📥 Загрузка тегов из БД для файла: " + filePath);
            loadTagsFromDatabase();
        } else {
            // Для нового файла - сразу добавляем #
            tagEditText.setText("#");
            tagEditText.setSelection(1);
            originalTags = "";
            Log.d("TagsDebug", "🆕 Новый файл - установлен начальный #, originalTags: '" + originalTags + "'");
        }

        // Слушатель изменений тегов
        tagEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String currentTags = tagEditText.getTagsForStorage();
                Log.d("TagsDebug", "✏️ Теги изменены: '" + currentTags + "'");
                markContentChanged();
            }
        });
    }

    private void loadTagsFromDatabase() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String tags = databaseHelper.getDocumentTags(filePath);
                    Log.d("TagsDebug", "📥 Получены теги из БД: '" + tags + "'");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (tags != null) {
                                tagEditText.setTagsFromStorage(tags);
                                originalTags = tags;
                                Log.d("TagsDebug", "✅ Теги загружены и установлены: '" + originalTags + "'");
                            } else {
                                tagEditText.setText("#");
                                tagEditText.setSelection(1);
                                originalTags = "";
                                Log.d("TagsDebug", "ℹ️ Тегов нет в БД, установлен #, originalTags: '" + originalTags + "'");
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e("TagsDebug", "❌ Ошибка загрузки тегов", e);
                }
            }
        }).start();
    }

    private void initFontSizeControls() {
        Log.d("FontSizeDebug", "=== ИНИЦИАЛИЗАЦИЯ УПРАВЛЕНИЯ РАЗМЕРОМ ШРИФТА ===");

        // Находим элементы управления размером шрифта
        textSizeCardView = findViewById(R.id.textSizeCardView);
        decreaseTextSizeTV = findViewById(R.id.decreaseTextSizeTV);
        increaseTextSizeTV = findViewById(R.id.increaseTextSizeTV);
        currentTextSizeTV = findViewById(R.id.CurrentTextSizeTV);

        // Логируем найденные элементы
        Log.d("FontSizeDebug", "textSizeCardView: " + (textSizeCardView != null ? "найден" : "НЕ НАЙДЕН"));
        Log.d("FontSizeDebug", "decreaseTextSizeTV: " + (decreaseTextSizeTV != null ? "найден" : "НЕ НАЙДЕН"));
        Log.d("FontSizeDebug", "increaseTextSizeTV: " + (increaseTextSizeTV != null ? "найден" : "НЕ НАЙДЕН"));
        Log.d("FontSizeDebug", "currentTextSizeTV: " + (currentTextSizeTV != null ? "найден" : "НЕ НАЙДЕН"));

        // Кнопка для показа/скрытия панели размера шрифта
        CardView textSizeButton = findViewById(R.id.text_size_button);
        Log.d("FontSizeDebug", "textSizeButton: " + (textSizeButton != null ? "найден" : "НЕ НАЙДЕН"));

        if (textSizeButton != null) {
            textSizeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d("FontSizeDebug", "Нажата кнопка размера шрифта");
                    toggleFontSizePanel();
                }
            });
        } else {
            Log.e("FontSizeDebug", "Кнопка text_size_button не найдена в layout!");
        }

        // Обработчики для кнопок увеличения/уменьшения
        if (decreaseTextSizeTV != null) {
            decreaseTextSizeTV.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d("FontSizeDebug", "Нажата кнопка УМЕНЬШЕНИЯ размера шрифта");
                    adjustFontSize(-1);
                }
            });
            // Проверяем кликабельность
            decreaseTextSizeTV.setClickable(true);
            Log.d("FontSizeDebug", "decreaseTextSizeTV кликабелен: " + decreaseTextSizeTV.isClickable());
        } else {
            Log.e("FontSizeDebug", "decreaseTextSizeTV не найден!");
        }

        if (increaseTextSizeTV != null) {
            increaseTextSizeTV.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d("FontSizeDebug", "Нажата кнопка УВЕЛИЧЕНИЯ размера шрифта");
                    adjustFontSize(1);
                }
            });
            increaseTextSizeTV.setClickable(true);
            Log.d("FontSizeDebug", "increaseTextSizeTV кликабелен: " + increaseTextSizeTV.isClickable());
        } else {
            Log.e("FontSizeDebug", "increaseTextSizeTV не найден!");
        }

        // Проверяем начальное состояние панели
        if (textSizeCardView != null) {
            Log.d("FontSizeDebug", "Начальная видимость textSizeCardView: " +
                    (textSizeCardView.getVisibility() == View.VISIBLE ? "VISIBLE" :
                            textSizeCardView.getVisibility() == View.INVISIBLE ? "INVISIBLE" : "GONE"));
            Log.d("FontSizeDebug", "textSizeCardView кликабелен: " + textSizeCardView.isClickable());
        }

        // Обновляем отображение текущего размера
        updateFontSizeDisplay();
        Log.d("FontSizeDebug", "=== ЗАВЕРШЕНИЕ ИНИЦИАЛИЗАЦИИ ===");
    }

    private void toggleFontSizePanel() {
        Log.d("FontSizeDebug", "toggleFontSizePanel вызван");
        if (textSizeCardView == null) {
            Log.e("FontSizeDebug", "textSizeCardView равен null!");
            return;
        }

        if (textSizeCardView.getVisibility() == View.VISIBLE) {
            Log.d("FontSizeDebug", "Скрываем панель размера шрифта");
            hideFontSizePanel();
        } else {
            Log.d("FontSizeDebug", "Показываем панель размера шрифта");
            showFontSizePanel();
        }
    }

    private void showFontSizePanel() {
        Log.d("FontSizeDebug", "showFontSizePanel вызван");
        if (textSizeCardView != null) {
            // Обновляем текущий размер перед показом
            updateCurrentFontSizeFromSelection();
            textSizeCardView.setVisibility(View.VISIBLE);
            textSizeCardView.setClickable(true);
            Log.d("FontSizeDebug", "Панель установлена в VISIBLE и CLICKABLE");
        } else {
            Log.e("FontSizeDebug", "textSizeCardView равен null в showFontSizePanel!");
        }
    }

    private void hideFontSizePanel() {
        Log.d("FontSizeDebug", "hideFontSizePanel вызван");
        if (textSizeCardView != null) {
            textSizeCardView.setVisibility(View.INVISIBLE);
            textSizeCardView.setClickable(false);
            Log.d("FontSizeDebug", "Панель установлена в INVISIBLE и NOT CLICKABLE");
        }
    }

    // ОБНОВЛЯЕМ: метод обновления размера из выделения
    private void updateCurrentFontSizeFromSelection() {
        Log.d("FontSizeDebug", "updateCurrentFontSizeFromSelection вызван");
        Log.d("FontSizeDebug", "focusedBlockId: " + focusedBlockId);
        Log.d("FontSizeDebug", "focusedEditText: " + focusedEditText);

        if (focusedBlockId != null && focusedEditText != null) {
            int start = focusedEditText.getSelectionStart();
            int end = focusedEditText.getSelectionEnd();
            Log.d("FontSizeDebug", "Позиции выделения - start: " + start + ", end: " + end);

            if (start == end) {
                // Если нет выделения, используем размер в позиции курсора
                currentFontSize = getFontSizeAtPosition(start);
                Log.d("FontSizeDebug", "Нет выделения, размер в позиции курсора: " + currentFontSize);
            } else {
                // Если есть выделение, берем размер первого символа выделения
                currentFontSize = getFontSizeAtPosition(start);
                Log.d("FontSizeDebug", "Размер первого символа выделения: " + currentFontSize);
            }

            updateFontSizeDisplay();
        } else {
            Log.d("FontSizeDebug", "Нет активного блока или EditText, устанавливаем размер по умолчанию");
            currentFontSize = 16;
            updateFontSizeDisplay();
        }
    }


    // ПЕРЕПИСЫВАЕМ: метод для получения размера шрифта в позиции
    private int getFontSizeAtPosition(int position) {
        Log.d("FontSizeDebug", "getFontSizeAtPosition: position=" + position);

        if (focusedBlockId != null && focusedEditText != null) {
            ContentBlock block = blockManager.getBlockById(focusedBlockId);
            if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                int fontSize = textBlock.getFontSizeAt(position);
                Log.d("FontSizeDebug", "Получен размер из TextBlock: " + fontSize);
                return fontSize;
            }
        }

        Log.d("FontSizeDebug", "Используем размер по умолчанию: 16");
        return 16;
    }

    private int spToPx(float sp) {
        return (int) (sp * getResources().getDisplayMetrics().scaledDensity);
    }

    private int pxToSp(float px) {
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        int sp = (int) (px / scaledDensity);
        Log.d("FontSizeDebug", "Конвертация px to sp: " + px + "px -> " + sp + "sp");
        return sp;
    }

    private void adjustFontSize(int delta) {
        Log.d("FontSizeDebug", "adjustFontSize вызван: delta=" + delta + ", currentFontSize=" + currentFontSize);
        int newSize = currentFontSize + delta;
        Log.d("FontSizeDebug", "Новый размер: " + newSize + ", MIN=" + MIN_FONT_SIZE + ", MAX=" + MAX_FONT_SIZE);

        // Проверяем границы
        if (newSize >= MIN_FONT_SIZE && newSize <= MAX_FONT_SIZE) {
            currentFontSize = newSize;
            Log.d("FontSizeDebug", "Применяем новый размер: " + currentFontSize);
            applyFontSizeToSelection(currentFontSize);
            updateFontSizeDisplay();
        } else {
            // Показываем сообщение о достижении предела
            String message = delta > 0 ?
                    "Максимальный размер: " + MAX_FONT_SIZE + "sp" :
                    "Минимальный размер: " + MIN_FONT_SIZE + "sp";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.d("FontSizeDebug", "Достигнут предел: " + message);
        }
    }

    private void applyFontSizeToSelection(int sizeSp) {
        Log.d("FontSizeDebug", "applyFontSizeToSelection: sizeSp=" + sizeSp);
        Log.d("FontSizeDebug", "focusedBlockId: " + focusedBlockId);
        Log.d("FontSizeDebug", "focusedEditText: " + focusedEditText);

        if (focusedBlockId != null && focusedEditText != null) {
            ContentBlock block = blockManager.getBlockById(focusedBlockId);
            Log.d("FontSizeDebug", "Найден блок: " + (block != null ? block.getId() : "null"));

            if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                int start = focusedEditText.getSelectionStart();
                int end = focusedEditText.getSelectionEnd();
                Log.d("FontSizeDebug", "Диапазон выделения: start=" + start + ", end=" + end);

                if (start != end) {
                    Log.d("FontSizeDebug", "Применяем размер к выделенному тексту");
                    textBlock.applyFontSize(start, end, sizeSp);
                    markContentChanged();
                    Log.d("FontSizeDebug", "Размер применен успешно");
                } else {
                    Log.d("FontSizeDebug", "Нет выделения текста");
                    Toast.makeText(this, "Выделите текст для изменения размера", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d("FontSizeDebug", "Блок не является TextBlock");
            }
        } else {
            Log.d("FontSizeDebug", "Нет активного блока или EditText");
            Toast.makeText(this, "Сначала выберите текстовый блок", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFontSizeDisplay() {
        Log.d("FontSizeDebug", "updateFontSizeDisplay: currentFontSize=" + currentFontSize);
        if (currentTextSizeTV != null) {
            currentTextSizeTV.setText(String.valueOf(currentFontSize));
            Log.d("FontSizeDebug", "Текстовое поле обновлено: " + currentFontSize);
        } else {
            Log.e("FontSizeDebug", "currentTextSizeTV равен null!");
        }
    }

//    private void setupTextChangeListener() {
//        if (focusedEditText != null) {
//            focusedEditText.addTextChangedListener(new TextWatcher() {
//                @Override
//                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//                }
//
//                @Override
//                public void onTextChanged(CharSequence s, int start, int before, int count) {
//                }
//
//                @Override
//                public void afterTextChanged(Editable s) {
//                    // Обновляем блок при изменении текста
//                    if (focusedBlockId != null) {
//                        ContentBlock block = blockManager.getBlockById(focusedBlockId);
//                        if (block instanceof TextBlock) {
//                            TextBlock textBlock = (TextBlock) block;
//                            if (s instanceof Spannable) {
//                                textBlock.updateHtmlFromSpannable((Spannable) s);
//                            }
//                            markContentChanged();
//                        }
//                    }
//                }
//            });
//        }
//    }

    private void setupTextChangeListener() {
        if (focusedEditText != null) {
            // Удаляем старые слушатели
            focusedEditText.removeTextChangedListener((TextWatcher) focusedEditText.getTag(R.id.text_watcher));

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // Обновляем блок при изменении текста
                    if (focusedBlockId != null && s instanceof Spannable) {
                        ContentBlock block = blockManager.getBlockById(focusedBlockId);
                        if (block instanceof TextBlock) {
                            TextBlock textBlock = (TextBlock) block;
                            textBlock.updateHtmlFromSpannable((Spannable) s);
                            markContentChanged();
                            Log.d("TextChange", "TextBlock обновлен: " + textBlock.getHtmlContent());
                        }
                    }
                }
            };

            focusedEditText.addTextChangedListener(textWatcher);
            // Сохраняем ссылку на слушатель в tag
            focusedEditText.setTag(R.id.text_watcher, textWatcher);
        }
    }

    private void initTableEditDialog() {
        // НАХОДИМ ЭЛЕМЕНТЫ ДИАЛОГА
        tableEditDialog = findViewById(R.id.tableEditDialog);
        editRows = findViewById(R.id.editRows);
        editColumns = findViewById(R.id.editColumns);

        CardView cancelButton = findViewById(R.id.cancel_button);
        CardView okayButton = findViewById(R.id.okay_button);

        // НАСТРАИВАЕМ КНОПКИ
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideTableEditDialog();
            }
        });

        okayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTableChanges();
            }
        });

        // ЗАКРЫТИЕ ПРИ КЛИКЕ ВНЕ ДИАЛОГА
        tableEditDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Ничего не делаем, чтобы не закрывать при клике на сам диалог
            }
        });

        // ЗАКРЫТИЕ ПРИ КЛИКЕ НА ФОН
        findViewById(R.id.mainContent).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideTableEditDialog();
            }
        });
    }

    // ДОБАВЛЯЕМ: метод для показа диалога редактирования
    public void showTableEditDialog(TableBlock tableBlock) {
        this.currentEditingTable = tableBlock;

        // ЗАПОЛНЯЕМ ПОЛЯ ТЕКУЩИМИ ЗНАЧЕНИЯМИ
        if (editRows != null) {
            editRows.setText(String.valueOf(tableBlock.getRows()));
        }
        if (editColumns != null) {
            editColumns.setText(String.valueOf(tableBlock.getCols()));
        }

        // ПОКАЗЫВАЕМ ДИАЛОГ
        tableEditDialog.setVisibility(View.VISIBLE);

        // Авто-фокус на поле ввода и показ клавиатуры
        editRows.requestFocus();
    }

    // ДОБАВЛЯЕМ: метод для скрытия диалога
    private void hideTableEditDialog() {
        // Скрываем клавиатуру перед закрытием диалога
        hideKeyboard();

        tableEditDialog.setVisibility(View.GONE);
        currentEditingTable = null;
        setBackgroundDim(false);
    }

    // ДОБАВЛЯЕМ: метод для применения изменений
    private void applyTableChanges() {
        if (currentEditingTable == null) return;

        try {
            int newRows = Integer.parseInt(editRows.getText().toString());
            int newCols = Integer.parseInt(editColumns.getText().toString());

            // Скрываем клавиатуру перед применением изменений
            hideKeyboard();

            // ПРИМЕНЯЕМ ИЗМЕНЕНИЯ К ТАБЛИЦЕ
            currentEditingTable.resizeTable(newRows, newCols);

            // ПЕРЕРИСОВЫВАЕМ БЛОКИ
            blockManager.renderBlocks();// замена рефреша

            markContentChanged();
            hideTableEditDialog();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Введите корректные числа", Toast.LENGTH_SHORT).show();
        }
    }

    // ДОБАВЛЯЕМ: методы для работы с клавиатурой
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // Пытаемся скрыть клавиатуру от текущего фокусного View
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

    // ДОБАВЛЯЕМ: метод для затемнения фона
    private void setBackgroundDim(boolean dim) {
        View mainContent = findViewById(R.id.mainContent);
        if (mainContent != null) {
            if (dim) {
                mainContent.setAlpha(0.3f);
                // Блокируем прокрутку и клики на основном контенте
                ScrollView scrollView = findViewById(R.id.scrollView);
                if (scrollView != null) {
                    scrollView.setEnabled(false);
                }
            } else {
                mainContent.setAlpha(1.0f);
                ScrollView scrollView = findViewById(R.id.scrollView);
                if (scrollView != null) {
                    scrollView.setEnabled(true);
                }
            }
        }
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Проверяем для новых файлов, не пустой ли документ
                if (isNewFile && isEmptyDocument()) {
                    Log.d("BackPress", "Пустой документ, просто выходим");

                    // Удаляем временный файл, если он был создан
                    if (filePath != null) {
                        File file = new File(filePath);
                        if (file.exists()) {
                            file.delete();
                            Log.d("BackPress", "Удален временный файл: " + filePath);
                        }
                    }

                    finish();
                    return;
                }

                // Для остальных случаев сохраняем как обычно
                saveAndExit();
            }
        });
    }

    private void createNewFile() {
        try {
            originalFileName = "document_" + System.currentTimeMillis() + ".docx";

            File directory = getDocumentsDirectory();
            if (directory == null) {
                Toast.makeText(this, "Не удалось получить доступ к хранилищу", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            Log.d("FileCreate", "Директория: " + directory.getAbsolutePath());
            Log.d("FileCreate", "Директория существует: " + directory.exists());
            Log.d("FileCreate", "Директория доступна для записи: " + directory.canWrite());

            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                Log.d("FileCreate", "Попытка создания директории: " + created);
                if (!created) {
                    directory = getAlternativeDirectory();
                    if (!directory.exists()) {
                        created = directory.mkdirs();
                        Log.d("FileCreate", "Альтернативная попытка создания: " + created);
                    }
                }

                if (!created || !directory.exists()) {
                    Toast.makeText(this,
                            "Ошибка создания папки. Проверьте разрешения хранилища.",
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }

            filePath = new File(directory, originalFileName).getAbsolutePath();
            Log.d("FileCreate", "Полный путь к файлу: " + filePath);

            initViews();

            TextBlock initialTextBlock = new TextBlock();
            blockManager.addBlock(initialTextBlock);
            isContentChanged = true;

            titleEditText.setText("");
            titleEditText.setHint("Введите название документа");
            titleEditText.selectAll();

            titleEditText.postDelayed(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(titleEditText, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }, 200);

        } catch (Exception e) {
            Log.e("FileCreate", "Критическая ошибка: " + e.getMessage(), e);
            Toast.makeText(this, "Критическая ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private File getDocumentsDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "NotesApp");
        } else {
            return new File(Environment.getExternalStorageDirectory(), "Documents/NotesApp");
        }
    }

    private File getAlternativeDirectory() {
        return new File(getFilesDir(), "Documents");
    }


    private void initViews() {
        try {
            titleEditText = findViewById(R.id.titleEditText);
            CardView buttonBack = findViewById(R.id.button_back);

            blocksContainer = findViewById(R.id.blocksContainer);
            btnAddTable = findViewById(R.id.btnAddTable);

            scrollView = findViewById(R.id.scrollView);

            if (scrollView == null) {
                Log.e("InitViews", "ScrollView not found! Check ID in layout");
            } else {
                Log.d("InitViews", "ScrollView initialized successfully");
            }
            // ПРОВЕРЯЕМ, ЧТО ВСЕ ОСНОВНЫЕ ЭЛЕМЕНТЫ НАЙДЕНЫ
            if (titleEditText == null) Log.e("InitViews", "titleEditText not found");
            if (blocksContainer == null) Log.e("InitViews", "blocksContainer not found");
            if (btnAddTable == null) Log.e("InitViews", "btnAddTable not found");

            // Инициализация BlockManager с слушателем изменений
            blockManager = new BlockManager(blocksContainer, this);
            blockManager.setOnContentChangeListener(new BlockManager.OnContentChangeListener() {
                @Override
                public void onContentChanged() {
                    markContentChanged();
                }
            });

            // ДОБАВЛЯЕМ: установка слушателя фокуса
            // ДОБАВЛЯЕМ: установка слушателя фокуса ЕСЛИ ОН ЕСТЬ В BlockManager
            try {
                blockManager.setBlockFocusListener(this);
            } catch (Exception e) {
                Log.w("InitViews", "BlockFocusListener not available: " + e.getMessage());
            }
            //setupGlobalFocusListener();

            // Настройка слушателей
            setupAdditionalButtons();
            initTableEditDialog();
            initFontSizeControls();
            blockManager.setTableDeleteListener(this);

            Log.d("InitViews", "Все View инициализированы успешно");
            // Слушатель изменений заголовка
            titleEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    markContentChanged();
                }
            });


            // Устанавливаем начальное название
            if (isNewFile) {
                String displayName = originalFileName.replace(".docx", "");
                titleEditText.setText(displayName);
                titleEditText.selectAll();
            } else {
                titleEditText.setText(originalFileName.replace(".docx", ""));
                originalTitle = originalFileName.replace(".docx", "");
            }

            if (!isNewFile) {
                originalTitle = titleEditText.getText().toString();
            }
            // Обработчик кнопки назад
            if (buttonBack != null) {
                buttonBack.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        saveAndExit();
                    }
                });
            }

            // Обработчик кнопки "Готово" на клавиатуре
            titleEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(titleEditText.getWindowToken(), 0);
                        return true;
                    }
                    return false;
                }
            });

        } catch (Exception e) {
            Log.e("InitViews", "Ошибка инициализации View: " + e.getMessage(), e);
            throw e; // Перебрасываем исключение для обработки в onCreate
        }
    }

    @Override
    public void onTableDelete(TableBlock tableBlock) {
        blockManager.updateAllBlocksFromViews();
        deleteTableAndMergeText(tableBlock);
        blockManager.renderBlocks();
    }

    private void debugScrollViewState() {
        Log.d("Debug", "=== SCROLLVIEW DEBUG INFO ===");
        Log.d("Debug", "scrollView variable: " + scrollView);

        ScrollView foundScrollView = findViewById(R.id.scrollView);
        Log.d("Debug", "findViewById result: " + foundScrollView);

        if (foundScrollView != null) {
            Log.d("Debug", "ScrollView dimensions: " + foundScrollView.getWidth() + "x" + foundScrollView.getHeight());
            Log.d("Debug", "ScrollView isShown: " + foundScrollView.isShown());
        }

        Log.d("Debug", "blocksContainer: " + blocksContainer);
        if (blocksContainer != null) {
            Log.d("Debug", "blocksContainer child count: " + blocksContainer.getChildCount());
        }
        Log.d("Debug", "=== END DEBUG INFO ===");
    }

    // ДОБАВЛЯЕМ: основной метод удаления таблицы и объединения текста
    private void deleteTableAndMergeText(TableBlock tableBlockToDelete) {
        try {
            // ОБНОВЛЯЕМ ДАННЫЕ ИЗ ВСЕХ VIEW ПЕРЕД ОПЕРАЦИЕЙ
            blockManager.updateAllBlocksFromViews();

            List<ContentBlock> blocks = blockManager.getBlocks();
            int tableIndex = blockManager.getBlockIndexById(tableBlockToDelete.getId());

            if (tableIndex == -1) {
                Log.e("TableDelete", "Таблица не найдена в блоке");
                return;
            }

            Log.d("TableDelete", "Удаление таблицы с индексом: " + tableIndex);

            TextBlock previousTextBlock = findPreviousTextBlock(blocks, tableIndex);
            TextBlock nextTextBlock = findNextTextBlock(blocks, tableIndex);

            if (previousTextBlock != null && nextTextBlock != null) {

                String previousContent = previousTextBlock.getRawContent();
                String nextContent = nextTextBlock.getRawContent();

                Log.d("TableDelete", "Объединение: предыдущий='" + previousContent + "', следующий='" + nextContent + "'");


                // Объединяем содержимое
                String mergedContent;
                if (previousTextBlock.hasRealFormatting() || nextTextBlock.hasRealFormatting()) {
                    // Если есть форматирование - объединяем как HTML
                    mergedContent = previousContent + " " + nextContent;
                    previousTextBlock.setHtmlContent(mergedContent);
                } else {
                    // Без форматирования - обычный текст
                    mergedContent = previousContent + " " + nextContent;
                    previousTextBlock.setRawContent(mergedContent);
                }

                //Log.d("TableDelete", "Результат объединения: '" + mergedText + "'");
                Log.d("TableDelete", "Результат объединения: '" + mergedContent + "'");

                // ВАЖНО: НЕМЕДЛЕННО ОБНОВЛЯЕМ VIEW ПРЕДЫДУЩЕГО БЛОКА
                //updateTextBlockViewImmediately(previousTextBlock.getId(), mergedText);
                updateTextBlockViewWithFormatting(previousTextBlock);
            }

            // УДАЛЯЕМ ТАБЛИЦУ И СЛЕДУЮЩИЙ ТЕКСТОВЫЙ БЛОК
            blockManager.removeBlock(tableBlockToDelete.getId());

            if (nextTextBlock != null) {
                blockManager.removeBlock(nextTextBlock.getId());
                Log.d("TableDelete", "Удален следующий текстовый блок");
            }

            markContentChanged();

            Toast.makeText(this, "Таблица удалена", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e("TableDelete", "Ошибка при удалении таблицы: " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка при удалении таблицы", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Немедленно обновляет View текстового блока с применением форматирования
     */
    private void updateTextBlockViewWithFormatting(TextBlock textBlock) {
        int blockIndex = blockManager.getBlockIndexById(textBlock.getId());
        if (blockIndex == -1) return;

        if (blocksContainer != null && blockIndex < blocksContainer.getChildCount()) {
            View blockView = blocksContainer.getChildAt(blockIndex);
            if (blockView instanceof EditText) {
                EditText editText = (EditText) blockView;

                // ПРИМЕНЯЕМ ФОРМАТИРОВАНИЕ НЕМЕДЛЕННО
                if (textBlock.hasRealFormatting() && textBlock.getHtmlContent() != null) {
                    SpannableString spannable = textBlock.htmlToSpannable(textBlock.getHtmlContent());
                    editText.setText(spannable);
                    Log.d("TableDelete", "Форматирование применено к View блока " + textBlock.getId());
                } else {
                    editText.setText(textBlock.getPlainText());
                    Log.d("TableDelete", "Обычный текст применен к View блока " + textBlock.getId());
                }
            }
        }
    }


    // ДОБАВЛЯЕМ: рекурсивный поиск EditText в сложных View
    private void findAndUpdateEditTextImmediately(View view, String newText) {
        if (view instanceof EditText) {
            EditText editText = (EditText) view;
            editText.setText(newText);
            Log.d("TableDelete", "EditText найден и обновлен: '" + newText + "'");
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findAndUpdateEditTextImmediately(group.getChildAt(i), newText);
            }
        }
    }

    // ДОБАВЛЯЕМ: метод для поиска предыдущего текстового блока
    private TextBlock findPreviousTextBlock(List<ContentBlock> blocks, int currentIndex) {
        for (int i = currentIndex - 1; i >= 0; i--) {
            ContentBlock block = blocks.get(i);
            if (block instanceof TextBlock) {
                return (TextBlock) block;
            }
        }
        return null;
    }

    // ДОБАВЛЯЕМ: метод для поиска следующего текстового блока
    private TextBlock findNextTextBlock(List<ContentBlock> blocks, int currentIndex) {
        for (int i = currentIndex + 1; i < blocks.size(); i++) {
            ContentBlock block = blocks.get(i);
            if (block instanceof TextBlock) {
                return (TextBlock) block;
            }
        }
        return null;
    }

    private void setupFocusForChildViews(View view) {
        if (view instanceof EditText) {
            EditText editText = (EditText) view;
            editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        focusedEditText = (EditText) v;
                        // Находим ID блока по tag
                        Object tag = v.getTag();
                        if (tag != null && tag.toString().startsWith("TextBlock_")) {
                            focusedBlockId = tag.toString().replace("TextBlock_", "");
                        }
                        cursorPosition = editText.getSelectionStart();
                    }
                }
            });
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setupFocusForChildViews(group.getChildAt(i));
            }
        }
    }

    @Override
    public void onBlockFocused(String blockId, EditText editText) {
        this.focusedBlockId = blockId;
        this.focusedEditText = editText;
        this.cursorPosition = editText.getSelectionStart();

        // Настраиваем слушатель изменений текста
        setupTextChangeListener();

        // Автоматически обновляем размер шрифта при смене фокуса
        if (textSizeCardView != null && textSizeCardView.getVisibility() == View.VISIBLE) {
            Log.d("FontSizeDebug", "Обновляем размер шрифта при смене фокуса");
            updateCurrentFontSizeFromSelection();
        }

        // Настраиваем слушатель выделения текста
        setupTextSelectionListener();
    }

    // Также обновляем при изменении выделения текста
    private void setupTextSelectionListener() {
        Log.d("FontSizeDebug", "setupTextSelectionListener вызван");
        if (focusedEditText != null) {
            // Удаляем предыдущие слушатели
            focusedEditText.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        // Небольшая задержка для обновления после изменения выделения
                        focusedEditText.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (textSizeCardView != null && textSizeCardView.getVisibility() == View.VISIBLE) {
                                    updateCurrentFontSizeFromSelection();
                                }
                            }
                        }, 100);
                    }
                    return false;
                }
            });

            focusedEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    Log.d("FontSizeDebug", "Текст изменен, проверяем выделение");
                    if (textSizeCardView != null && textSizeCardView.getVisibility() == View.VISIBLE) {
                        updateCurrentFontSizeFromSelection();
                    }
                }
            });
            Log.d("FontSizeDebug", "Слушатель текста установлен");
        } else {
            Log.d("FontSizeDebug", "focusedEditText равен null, слушатель не установлен");
        }
    }

    // ДОБАВЛЯЕМ: метод для отметки изменений
    private void markContentChanged() {
        // ДОБАВИМ: проверяем, что загрузка завершена
        if (!isNewFile && !isInitialLoadComplete) {
            Log.d("ChangeDebug", "⏳ Изменение проигнорировано - начальная загрузка не завершена");
            return;
        }

        // Используем точную проверку вместо простого флага
        boolean hasChanges = isNewFile || hasRealChanges();

        if (hasChanges && !isContentChanged) {
            isContentChanged = true;
            updateActivityTitle();
            Log.d("ChangeDebug", "🔴 Контент изменен, isContentChanged = true");
        } else if (!hasChanges && isContentChanged) {
            isContentChanged = false;
            updateActivityTitle();
            Log.d("ChangeDebug", "🟢 Изменения отменены, isContentChanged = false");
        } else {
            Log.d("ChangeDebug", "⚪ Статус изменений не изменился: " + isContentChanged);
        }
    }

    private void showSmartTableInsertDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_table_inssert);

        // Настройки окна
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            window.setAttributes(params);
        }

        CardView btnAddHere = dialog.findViewById(R.id.btnAddHere);
        CardView btnAddThere = dialog.findViewById(R.id.btnAddThere);
        CardView btnCancelInsert = dialog.findViewById(R.id.btnCancelInsert);

        View.OnClickListener dismissListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(dialog);
                dialog.dismiss();
            }
        };

        btnCancelInsert.setOnClickListener(dismissListener);

        btnAddHere.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTableCreationDialog(false);
                isContentChanged = true;
                dialog.dismiss();
            }
        });

        btnAddThere.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTableCreationDialog(true);
                isContentChanged = true;
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    // ДОБАВЛЯЕМ: метод для поиска индекса блока по ID
    private int findBlockIndexById(String blockId) {
        List<ContentBlock> blocks = blockManager.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                return i;
            }
        }
        return -1;
    }

    private void showTableCreationDialog(boolean onCursor) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_table_size);

        // Настройки окна
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            window.setAttributes(params);
        }

        EditText editRows = dialog.findViewById(R.id.editRows);
        EditText editCols = dialog.findViewById(R.id.editCols);
        CardView btnCancel = dialog.findViewById(R.id.btnCancel);
        CardView btnCreate = dialog.findViewById(R.id.btnCreate);

        // Общий слушатель для закрытия
        View.OnClickListener dismissListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(dialog);
                dialog.dismiss();
            }
        };

        btnCancel.setOnClickListener(dismissListener);

        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int rows = Integer.parseInt(editRows.getText().toString());
                    int cols = Integer.parseInt(editCols.getText().toString());

                    hideKeyboard(dialog);

                    // УДАЛИТЕ safeBlockOperation и вызывайте напрямую:
                    if (onCursor) {
                        TableBlock tableBlock = new TableBlock(rows, cols);
                        blockManager.addBlock(tableBlock);
                        TextBlock textBlock = new TextBlock();
                        blockManager.addBlock(textBlock);
                    } else {
                        // Вызываем напрямую без safeBlockOperation
                        insertTableAtCursorOriginal(rows, cols);
                    }

                    isContentChanged = true;
                    dialog.dismiss();

                } catch (NumberFormatException e) {
                    Toast.makeText(TextEditorActivity.this, "Введите корректные числа", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();

        // Авто-фокус на поле ввода
        editRows.requestFocus();
        showKeyboard(editRows);
    }

    // Вспомогательные методы для работы с клавиатурой
    private void hideKeyboard(Dialog dialog) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // Получаем текущее View с фокусом в диалоге
            View currentFocus = dialog.getCurrentFocus();
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            } else {
                // Если нет фокуса, используем дефолтное View диалога
                View dialogView = dialog.findViewById(android.R.id.content);
                if (dialogView != null) {
                    imm.hideSoftInputFromWindow(dialogView.getWindowToken(), 0);
                }
            }
        }
    }

    private void showKeyboard(EditText editText) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void insertTableAtCursorOriginal(int rows, int cols) {
        if (focusedBlockId == null || focusedEditText == null) return;

        try {
            Log.d("TableInsert", "=== УПРОЩЕННЫЙ МЕТОД С ФОРМАТИРОВАНИЕМ ===");

            // Получаем текущий текст и позицию курсора
            Spannable spannable = (Spannable) focusedEditText.getText();
            String currentText = spannable.toString();
            int cursorPos = focusedEditText.getSelectionStart();

            Log.d("TableInsert", "Исходный текст: '" + currentText + "'");
            Log.d("TableInsert", "Курсор на позиции: " + cursorPos);

            // Создаем Spannable для текста до курсора
            SpannableString beforeSpannable = new SpannableString(spannable.subSequence(0, cursorPos));

            // Обновляем EditText с сохранением форматирования
            focusedEditText.setText(beforeSpannable);

            Log.d("TableInsert", "Текст после обрезки: '" + focusedEditText.getText().toString() + "'");

            // Обновляем блок из измененного EditText
            int currentBlockIndex = findBlockIndexById(focusedBlockId);
            ContentBlock currentBlock = blockManager.getBlockById(focusedBlockId);
            if (currentBlock instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) currentBlock;
                textBlock.updateHtmlFromSpannable((Spannable) focusedEditText.getText());
                Log.d("TableInsert", "TextBlock обновлен с HTML: " + textBlock.getHtmlContent());
            }

            // Создаем новый блок для текста после курсора
            SpannableString afterSpannable = new SpannableString(spannable.subSequence(cursorPos, currentText.length()));
            TextBlock afterCursorBlock = new TextBlock();
            afterCursorBlock.updateHtmlFromSpannable(afterSpannable);

            // Создаем таблицу
            TableBlock tableBlock = new TableBlock(rows, cols);

            // Вставляем блоки
            blockManager.insertBlock(currentBlockIndex + 1, tableBlock);
            blockManager.insertBlock(currentBlockIndex + 2, afterCursorBlock);

            markContentChanged();

            Log.d("TableInsert", "=== ЗАВЕРШЕНИЕ УПРОЩЕННОГО МЕТОДА ===");

        } catch (Exception e) {
            Log.e("TableInsert", "Ошибка: " + e.getMessage(), e);
        }
    }


    private static class BlockState {
        String id;
        String content;
        boolean isHtml;

        BlockState(String id, String content, boolean isHtml) {
            this.id = id;
            this.content = content;
            this.isHtml = isHtml;
        }
    }

    private List<BlockState> originalBlockStates = new ArrayList<>();

    private View findViewInGroup(ViewGroup group, String blockId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Object tag = child.getTag();
            if (tag != null && tag.toString().contains(blockId)) {
                return child;
            }

            if (child instanceof ViewGroup) {
                View found = findViewInGroup((ViewGroup) child, blockId);
                if (found != null) return found;
            }
        }
        return null;
    }

    //    private void saveAndExit() {
//        Log.d("SaveDebug", "=== НАЧАЛО СОХРАНЕНИЯ ===");
//        Log.d("SaveDebug", "isNewFile: " + isNewFile);
//        Log.d("SaveDebug", "filePath: " + filePath);
//        Log.d("SaveDebug", "isContentChanged: " + isContentChanged);
//
//        // ВСЕГДА ОБНОВЛЯЕМ ДАННЫЕ ПЕРЕД ПРОВЕРКОЙ
//        blockManager.updateAllBlocksFromViews();
//
//        List<ContentBlock> blocks = blockManager.getBlocks();
//
//        // ТОЧНАЯ ПРОВЕРКА ИЗМЕНЕНИЙ
//        boolean shouldSave = hasRealChanges();
//
//        if (!shouldSave) {
//            Log.d("SaveDebug", "❌ Реальных изменений нет, выходим без сохранения");
//            finish();
//            return;
//        }
//
//        Log.d("SaveDebug", "✅ Обнаружены изменения, сохраняем файл");
//
//        String newFileName = titleEditText.getText().toString().trim();
//        Log.d("SaveDebug", "📝 Новое имя файла: '" + newFileName + "'");
//
//        // Если имя файла пустое, генерируем из содержимого
//        if (newFileName.isEmpty()) {
//            String contentPreview = blockManager.getContentPreview();
//            if (!contentPreview.isEmpty()) {
//                newFileName = generateFileNameFromContent(contentPreview, 25);
//                Toast.makeText(this, "Автоназвание: " + newFileName, Toast.LENGTH_LONG).show();
//                Log.d("SaveDebug", "🔤 Сгенерировано автоназвание: " + newFileName);
//            } else {
//                // Если нет содержимого и нет названия - просто выходим
//                Log.d("SaveDebug", "❌ Нечего сохранять - пустой документ без названия");
//                finish();
//                return;
//            }
//        }
//
//        // Получаем теги ДО запуска AsyncTask
//        // ПОЛУЧАЕМ ПРЕВЬЮ ИЗ БЛОК-МЕНЕДЖЕРА
//        String preview = blockManager.getContentPreview();
//        String currentTags = tagEditText.getTagsForStorage();
//
//        Log.d("SaveDebug", "📝 Превью для сохранения: '" + preview + "'");
//
//        new SaveFileTask(new Runnable() {
//            @Override
//            public void run() {
//                if (!isNewFile) {
//                    originalTitle = titleEditText.getText().toString().trim();
//                    originalTags = currentTags;
//                    saveOriginalBlocksState();
//                    isContentChanged = false;
//                    updateActivityTitle();
//                }
//                finish();
//            }
//        }, currentTags, preview).execute(newFileName);
//        hideKeyboard();
//    }
    private void saveAndExit() {
        Log.d("SaveDebug", "=== НАЧАЛО СОХРАНЕНИЯ ===");
        Log.d("SaveDebug", "isNewFile: " + isNewFile);
        Log.d("SaveDebug", "filePath: " + filePath);
        Log.d("SaveDebug", "isContentChanged: " + isContentChanged);

        // ВСЕГДА ОБНОВЛЯЕМ ДАННЫЕ ПЕРЕД ПРОВЕРКОЙ
        blockManager.updateAllBlocksFromViews();

        // Для НОВЫХ файлов проверяем, не пустой ли документ
        if (isNewFile && isEmptyDocument()) {
            Log.d("SaveDebug", "❌ Пустой документ, отмена создания");

            // Если файл уже был создан на диске (при экспорте), удаляем его
            File file = new File(filePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d("SaveDebug", "Удален пустой файл: " + filePath + ", успех: " + deleted);
            }

            finish();
            return;
        }

        // Продолжаем обычную проверку изменений
        List<ContentBlock> blocks = blockManager.getBlocks();

        // ТОЧНАЯ ПРОВЕРКА ИЗМЕНЕНИЙ
        boolean shouldSave = hasRealChanges();

        if (!shouldSave) {
            Log.d("SaveDebug", "❌ Реальных изменений нет, выходим без сохранения");
            finish();
            return;
        }

        Log.d("SaveDebug", "✅ Обнаружены изменения, сохраняем файл");

        String newFileName = titleEditText.getText().toString().trim();
        Log.d("SaveDebug", "📝 Новое имя файла: '" + newFileName + "'");

        // Если имя файла пустое, генерируем из содержимого
        if (newFileName.isEmpty()) {
            String contentPreview = blockManager.getContentPreview();
            if (!contentPreview.isEmpty() && !contentPreview.equals("Пустой документ")) {
                newFileName = generateFileNameFromContent(contentPreview, 25);
                Toast.makeText(this, "Автоназвание: " + newFileName, Toast.LENGTH_LONG).show();
                Log.d("SaveDebug", "🔤 Сгенерировано автоназвание: " + newFileName);
            } else {
                // Если нет содержимого и нет названия - используем дату
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
                newFileName = "Заметка " + sdf.format(new Date());
                Toast.makeText(this, "Автоназвание: " + newFileName, Toast.LENGTH_LONG).show();
                Log.d("SaveDebug", "🔤 Сгенерировано название по дате: " + newFileName);
            }
        }

        // Получаем теги ДО запуска AsyncTask
        String preview = blockManager.getContentPreview();
        String currentTags = tagEditText.getTagsForStorage();

        Log.d("SaveDebug", "📝 Превью для сохранения: '" + preview + "'");
        Log.d("SaveDebug", "🏷️ Теги для сохранения: '" + currentTags + "'");

        new SaveFileTask(new Runnable() {
            @Override
            public void run() {
                if (!isNewFile) {
                    originalTitle = titleEditText.getText().toString().trim();
                    originalTags = currentTags;
                    saveOriginalBlocksState();
                    isContentChanged = false;
                    updateActivityTitle();
                }
                finish();
            }
        }, currentTags, preview).execute(newFileName);
        hideKeyboard();
    }

//    private String generateFileNameFromContent(String content, int maxLength) {
//        if (content == null || content.trim().isEmpty()) {
//            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
//            return "Заметка " + sdf.format(new java.util.Date());
//        }
//
//        String cleaned = content.trim()
//                .replaceAll("\\s+", " ")
//                .replaceAll("\\n+", " ")
//                .replaceAll("[\\\\/:*?\"<>|]", "")
//                .replaceAll("^[\\s\\p{Punct}]+", "")
//                .replaceAll("[\\s\\p{Punct}]+$", "")
//                .trim();
//
//        if (cleaned.isEmpty()) {
//            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
//            return "Заметка " + sdf.format(new java.util.Date());
//        }
//
//        if (cleaned.length() <= maxLength) {
//            return cleaned;
//        }
//
//        String truncated = cleaned.substring(0, maxLength);
//        int lastSpace = truncated.lastIndexOf(' ');
//        int lastPunctuation = Math.max(
//                truncated.lastIndexOf('.'),
//                Math.max(truncated.lastIndexOf('!'), truncated.lastIndexOf('?'))
//        );
//
//        int cutIndex = Math.max(lastSpace, lastPunctuation);
//        if (cutIndex > maxLength / 2) {
//            return truncated.substring(0, cutIndex).trim();
//        }
//
//        return truncated.trim() + "...";
//    }
private String generateFileNameFromContent(String content, int maxLength) {
    if (content == null || content.trim().isEmpty() || content.equals("Пустой документ")) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        return "Заметка " + sdf.format(new Date());
    }

    String cleaned = content.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("\\n+", " ")
            .replaceAll("[\\\\/:*?\"<>|]", "")
            .replaceAll("^[\\s\\p{Punct}]+", "")
            .replaceAll("[\\s\\p{Punct}]+$", "")
            .trim();

    if (cleaned.isEmpty()) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        return "Заметка " + sdf.format(new Date());
    }

    if (cleaned.length() <= maxLength) {
        return cleaned;
    }

    String truncated = cleaned.substring(0, maxLength);
    int lastSpace = truncated.lastIndexOf(' ');
    int lastPunctuation = Math.max(
            truncated.lastIndexOf('.'),
            Math.max(truncated.lastIndexOf('!'), truncated.lastIndexOf('?'))
    );

    int cutIndex = Math.max(lastSpace, lastPunctuation);
    if (cutIndex > maxLength / 2) {
        return truncated.substring(0, cutIndex).trim();
    }

    return truncated.trim() + "...";
}

    private void updateActivityTitle() {
        String title = originalFileName.replace(".docx", "");
        if (isContentChanged) {
            title = "* " + title;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    private void setupAdditionalButtons() {
        CardView listButton = findViewById(R.id.list_button);
        CardView textColorButton = findViewById(R.id.text_size_button);

        if (listButton != null) {
            listButton.setOnClickListener(v -> {
                Log.d("ListButton", "Нажата кнопка списка");
                showListDialog();
            });
        }

        if (textColorButton != null) {
            textColorButton.setOnClickListener(v -> {
                Toast.makeText(this, "Размер текста", Toast.LENGTH_SHORT).show();
            });
        }

        btnAddTable.setOnClickListener(v -> {
            blockManager.updateAllBlocksFromViews();

            // ПРОВЕРЯЕМ ЕСТЬ ЛИ АКТИВНЫЙ КУРСОР
            if (focusedBlockId != null && focusedEditText != null) {
                showSmartTableInsertDialog();
            } else {
                showTableCreationDialog(true); // обычное создание таблицы
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    private void loadFileContent() {
        new AsyncTask<Void, Void, List<ContentBlock>>() {
            @Override
            protected List<ContentBlock> doInBackground(Void... voids) {
                try {
                    File file = new File(filePath);
                    if (!file.exists()) {
                        android.util.Log.d("LoadDebug", "Файл не существует: " + filePath);
                        return new ArrayList<>();
                    }

                    android.util.Log.d("LoadDebug", "Начало загрузки файла: " + filePath);
                    List<ContentBlock> loadedBlocks = DocxBlockImporter.importFromDocx(filePath);
                    android.util.Log.d("LoadDebug", "Загружено блоков: " + loadedBlocks.size());

                    return loadedBlocks;

                } catch (Exception e) {
                    android.util.Log.e("LoadDebug", "Ошибка загрузки: " + e.getMessage(), e);
                    return new ArrayList<>();
                }
            }

            @Override
            protected void onPostExecute(List<ContentBlock> blocks) {
                if (titleEditText != null) {
                    // ОЧИЩАЕМ И ДОБАВЛЯЕМ БЛОКИ
                    blockManager.getBlocks().clear();

                    for (ContentBlock block : blocks) {
                        blockManager.addBlock(block);
                    }

                    String displayName = originalFileName.replace(".docx", "").replace(".DOCX", "");
                    titleEditText.setText(displayName);
                    originalTitle = displayName;

                    // ВАЖНО: Сохраняем исходное состояние ПОСЛЕ того как все блоки добавлены
                    saveOriginalBlocksState();

                    isInitialLoadComplete = true; // УСТАНАВЛИВАЕМ ФЛАГ
                    isContentChanged = false;
                    updateActivityTitle();

                    Log.d("LoadDebug", "Файл успешно загружен, блоков: " + blocks.size());
                    Log.d("ChangeDebug", "Исходные блоки сохранены: " + originalBlocks.size());
                }
            }
        }.execute();
    }

    //    private class SaveFileTask extends AsyncTask<String, Void, Boolean> {
//        private Runnable onSuccessCallback;
//        private String tags;
//        private String preview; // ДОБАВЛЯЕМ: храним превью
//
//        public SaveFileTask(Runnable onSuccessCallback, String tags, String preview) {
//            this.onSuccessCallback = onSuccessCallback;
//            this.tags = tags;
//            this.preview = preview;
//        }
//
//        @Override
//        protected Boolean doInBackground(String... params) {
//            try {
//                String newFileName = params[0];
//
//                if (!newFileName.toLowerCase().endsWith(".docx")) {
//                    newFileName += ".docx";
//                }
//
//                File originalFile = new File(filePath);
//                File newFile = new File(originalFile.getParent(), newFileName);
//
//                if (!newFile.getAbsolutePath().equals(originalFile.getAbsolutePath())) {
//                    if (newFile.exists()) {
//                        int counter = 1;
//                        String baseName = newFileName.replace(".docx", "");
//                        while (newFile.exists()) {
//                            newFileName = baseName + " (" + counter + ").docx";
//                            newFile = new File(originalFile.getParent(), newFileName);
//                            counter++;
//                        }
//                    }
//
//                    if (!isNewFile && originalFile.exists()) {
//                        originalFile.delete();
//                    }
//                }
//
//                filePath = newFile.getAbsolutePath();
//
//                // Сохраняем через блочный экспортер
//                List<ContentBlock> blocks = blockManager.getBlocks();
//                DocxBlockExporter.exportToDocx(blocks, filePath);
//
//                String preview = blockManager.getContentPreview();
//
//                Log.d("SaveDebug", "💾 Сохранение файла: " + newFileName);
//                Log.d("SaveDebug", "💾 Путь файла: " + filePath);
//                Log.d("SaveDebug", "💾 Превью: '" + preview + "'");
//                Log.d("SaveDebug", "💾 isNewFile: " + isNewFile);
//
//                // ИСПРАВЛЯЕМ: для новых файлов используем addDocument, для существующих - updateDocument
//                if (isNewFile) {
//                    // ДОБАВЛЯЕМ новый файл в БД
//                    DocxFile newDoc = new DocxFile(newFileName, preview, filePath, System.currentTimeMillis());
//                    long result = databaseHelper.addDocument(newDoc);
//                    Log.d("SaveDebug", "✅ Новый файл добавлен в БД с ID: " + result);
//                } else {
//                    // ОБНОВЛЯЕМ существующий файл
//                    int result = databaseHelper.updateDocument(filePath, newFileName, preview);
//                    Log.d("SaveDebug", "✅ Существующий файл обновлен в БД: " + result + " строк");
//                }
//
//                // Обновляем теги
//                databaseHelper.updateDocumentTags(filePath, tags);
//
//                return true;
//
//            } catch (Exception e) {
//                Log.e("SaveDebug", "❌ Ошибка сохранения: " + e.getMessage(), e);
//                return false;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(Boolean success) {
//            if (success) {
//                isContentChanged = false;
//                isNewFile = false;
//                originalFileName = new File(filePath).getName();
//
//                Toast.makeText(TextEditorActivity.this,
//                        "Файл сохранен", Toast.LENGTH_SHORT).show();
//
//                if (onSuccessCallback != null) {
//                    onSuccessCallback.run();
//                }
//            } else {
//                Toast.makeText(TextEditorActivity.this,
//                        "Ошибка при сохранении", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//    private class SaveFileTask extends AsyncTask<String, Void, Boolean> {
//        private Runnable onSuccessCallback;
//        private String userTags; // Теги, введенные пользователем
//        private String preview;
//
//        public SaveFileTask(Runnable onSuccessCallback, String userTags, String preview) {
//            this.onSuccessCallback = onSuccessCallback;
//            this.userTags = userTags;
//            this.preview = preview;
//        }
//
//        @Override
//        protected Boolean doInBackground(String... params) {
//            try {
//                String newFileName = params[0];
//
//                if (!newFileName.toLowerCase().endsWith(".docx")) {
//                    newFileName += ".docx";
//                }
//
//                File originalFile = new File(filePath);
//                File newFile = new File(originalFile.getParent(), newFileName);
//
//                if (!newFile.getAbsolutePath().equals(originalFile.getAbsolutePath())) {
//                    if (newFile.exists()) {
//                        int counter = 1;
//                        String baseName = newFileName.replace(".docx", "");
//                        while (newFile.exists()) {
//                            newFileName = baseName + " (" + counter + ").docx";
//                            newFile = new File(originalFile.getParent(), newFileName);
//                            counter++;
//                        }
//                    }
//
//                    if (!isNewFile && originalFile.exists()) {
//                        originalFile.delete();
//                    }
//                }
//
//                filePath = newFile.getAbsolutePath();
//
//                // Сохраняем документ
//                List<ContentBlock> blocks = blockManager.getBlocks();
//                DocxBlockExporter.exportToDocx(blocks, filePath);
//
//                long currentTime = System.currentTimeMillis();
//                long createdAt = isNewFile ? currentTime : databaseHelper.getDocumentCreatedAt(filePath);
//
//                // Создаем объект документа
//                DocxFile doc = new DocxFile(newFileName, preview, filePath, currentTime, userTags, createdAt);
//
//                if (isNewFile) {
//                    // Добавляем новый документ с автотегами
//                    databaseHelper.addDocumentWithAutoTags(doc, createdAt, currentTime);
//                    Log.d("SaveDebug", "✅ Новый файл добавлен с автотегами");
//                } else {
//                    // Обновляем существующий документ с автотегами
//                    databaseHelper.updateDocumentWithAutoTags(filePath, newFileName, preview, createdAt, currentTime);
//                    Log.d("SaveDebug", "✅ Существующий файл обновлен с автотегами");
//                }
//
//                return true;
//
//            } catch (Exception e) {
//                Log.e("SaveDebug", "❌ Ошибка сохранения: " + e.getMessage(), e);
//                return false;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(Boolean success) {
//            if (success) {
//                isContentChanged = false;
//                isNewFile = false;
//                originalFileName = new File(filePath).getName();
//
//                Toast.makeText(TextEditorActivity.this,
//                        "Файл сохранен", Toast.LENGTH_SHORT).show();
//
//                if (onSuccessCallback != null) {
//                    onSuccessCallback.run();
//                }
//            } else {
//                Toast.makeText(TextEditorActivity.this,
//                        "Ошибка при сохранении", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

//    private class SaveFileTask extends AsyncTask<String, Void, Boolean> {
//        private Runnable onSuccessCallback;
//        private String userTags;
//        private String preview;
//
//        public SaveFileTask(Runnable onSuccessCallback, String userTags, String preview) {
//            this.onSuccessCallback = onSuccessCallback;
//            this.userTags = userTags;
//            this.preview = preview;
//        }
//
//        @Override
//        protected Boolean doInBackground(String... params) {
//            try {
//                String newFileName = params[0];
//
//                if (!newFileName.toLowerCase().endsWith(".docx")) {
//                    newFileName += ".docx";
//                }
//
//                File originalFile = new File(filePath);
//                File newFile = new File(originalFile.getParent(), newFileName);
//
//                // Если имя файла изменилось
//                if (!newFile.getAbsolutePath().equals(originalFile.getAbsolutePath())) {
//                    if (newFile.exists()) {
//                        int counter = 1;
//                        String baseName = newFileName.replace(".docx", "");
//                        while (newFile.exists()) {
//                            newFileName = baseName + " (" + counter + ").docx";
//                            newFile = new File(originalFile.getParent(), newFileName);
//                            counter++;
//                        }
//                    }
//
//                    // Удаляем старый файл, если это не новый файл
//                    if (!isNewFile && originalFile.exists()) {
//                        originalFile.delete();
//                    }
//                }
//
//                filePath = newFile.getAbsolutePath();
//
//                // Сохраняем содержимое документа
//                List<ContentBlock> blocks = blockManager.getBlocks();
//                DocxBlockExporter.exportToDocx(blocks, filePath);
//
//                long currentTime = System.currentTimeMillis();
//
//                if (isNewFile) {
//                    // Получаем дату создания (для нового файла это текущее время)
//                    long createdAt = currentTime;
//
//                    // Создаем объект документа
//                    DocxFile doc = new DocxFile(newFileName, preview, filePath, currentTime, userTags, createdAt);
//
//                    // Добавляем новый документ
//                    long result = databaseHelper.addDocumentWithAutoTags(doc, createdAt, currentTime);
//                    if (result != -1) {
//                        Log.d("SaveDebug", "✅ Новый файл добавлен с автотегами");
//                        return true;
//                    } else {
//                        Log.e("SaveDebug", "❌ Ошибка добавления нового файла");
//                        return false;
//                    }
//                } else {
//                    // Для существующего файла получаем дату создания из БД
//                    long createdAt = databaseHelper.getDocumentCreatedAt(filePath);
//                    if (createdAt == 0) {
//                        // Если не нашли, используем текущее время
//                        createdAt = currentTime;
//                    }
//
//                    // Обновляем существующий документ
//                    int result = databaseHelper.updateDocumentWithAutoTags(filePath,
//                            newFileName, preview, createdAt, currentTime);
//                    if (result > 0) {
//                        Log.d("SaveDebug", "✅ Существующий файл обновлен с автотегами");
//                        return true;
//                    } else {
//                        Log.e("SaveDebug", "❌ Ошибка обновления файла");
//                        return false;
//                    }
//                }
//
//            } catch (Exception e) {
//                Log.e("SaveDebug", "❌ Ошибка сохранения: " + e.getMessage(), e);
//                return false;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(Boolean success) {
//            if (success) {
//                isContentChanged = false;
//                isNewFile = false;
//                originalFileName = new File(filePath).getName();
//
//                Toast.makeText(TextEditorActivity.this,
//                        "Файл сохранен", Toast.LENGTH_SHORT).show();
//
//                if (onSuccessCallback != null) {
//                    onSuccessCallback.run();
//                }
//            } else {
//                Toast.makeText(TextEditorActivity.this,
//                        "Ошибка при сохранении", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

    // Обновленная версия SaveFileTask - используем старые методы для надежности
//    private class SaveFileTask extends AsyncTask<String, Void, Boolean> {
//        private Runnable onSuccessCallback;
//        private String userTags;
//        private String preview;
//
//        public SaveFileTask(Runnable onSuccessCallback, String userTags, String preview) {
//            this.onSuccessCallback = onSuccessCallback;
//            this.userTags = userTags;
//            this.preview = preview;
//        }
//
//        @Override
//        protected Boolean doInBackground(String... params) {
//            try {
//                String newFileName = params[0];
//
//                if (!newFileName.toLowerCase().endsWith(".docx")) {
//                    newFileName += ".docx";
//                }
//
//                File originalFile = new File(filePath);
//                File newFile = new File(originalFile.getParent(), newFileName);
//
//                // Если имя файла изменилось
//                if (!newFile.getAbsolutePath().equals(originalFile.getAbsolutePath())) {
//                    if (newFile.exists()) {
//                        int counter = 1;
//                        String baseName = newFileName.replace(".docx", "");
//                        while (newFile.exists()) {
//                            newFileName = baseName + " (" + counter + ").docx";
//                            newFile = new File(originalFile.getParent(), newFileName);
//                            counter++;
//                        }
//                    }
//
//                    // Удаляем старый файл, если это не новый файл
//                    if (!isNewFile && originalFile.exists()) {
//                        originalFile.delete();
//                    }
//                }
//
//                filePath = newFile.getAbsolutePath();
//
//                // Сохраняем содержимое документа
//                List<ContentBlock> blocks = blockManager.getBlocks();
//                DocxBlockExporter.exportToDocx(blocks, filePath);
//
//
//                long currentTime = System.currentTimeMillis();
//                Log.d("SaveDebug", "📝 Сохранение документа:");
//                Log.d("SaveDebug", "  isNewFile: " + isNewFile);
//                Log.d("SaveDebug", "  filePath: " + filePath);
//                Log.d("SaveDebug", "  newFileName: " + newFileName);
//                Log.d("SaveDebug", "  preview: " + (preview != null ? "'" + preview + "'" : "null"));
//                Log.d("SaveDebug", "  userTags: " + (userTags != null ? "'" + userTags + "'" : "null"));
//                Log.d("SaveDebug", "  currentTime: " + currentTime);
//
//                if (isNewFile) {
//                    // Для нового файла: сначала добавляем через старый метод
//                    DocxFile doc = new DocxFile(newFileName, preview, filePath, currentTime);
//                    long result = databaseHelper.addDocument(doc);
//
//                    if (result != -1) {
//                        // Затем добавляем автотеги
//                        long createdAt = currentTime;
//                        databaseHelper.addDocumentWithAutoTags(doc, createdAt, currentTime);
//                        Log.d("SaveDebug", "✅ Новый файл добавлен");
//                        return true;
//                    } else {
//                        Log.e("SaveDebug", "❌ Ошибка добавления нового файла");
//                        return false;
//                    }
//                } else {
//                    // Для существующего файла: сначала обновляем через старый метод
//                    int result = databaseHelper.updateDocument(filePath, newFileName, preview);
//
//                    if (result > 0) {
//                        // Затем добавляем автотеги
//                        long createdAt = databaseHelper.getDocumentCreatedAt(filePath);
//                        if (createdAt == 0) {
//                            createdAt = currentTime;
//                        }
//
//                        // Обновляем теги с автотегами
//                        databaseHelper.updateDocumentWithAutoTags(filePath,
//                                newFileName, preview, createdAt, currentTime);
//                        Log.d("SaveDebug", "✅ Существующий файл обновлен");
//                        return true;
//                    } else {
//                        Log.e("SaveDebug", "❌ Ошибка обновления файла");
//                        return false;
//                    }
//                }
//
//            } catch (Exception e) {
//                Log.e("SaveDebug", "❌ Ошибка сохранения: " + e.getMessage(), e);
//                return false;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(Boolean success) {
//            if (success) {
//                isContentChanged = false;
//                isNewFile = false;
//                originalFileName = new File(filePath).getName();
//
//                Toast.makeText(TextEditorActivity.this,
//                        "Файл сохранен", Toast.LENGTH_SHORT).show();
//
//                if (onSuccessCallback != null) {
//                    onSuccessCallback.run();
//                }
//            } else {
//                Toast.makeText(TextEditorActivity.this,
//                        "Ошибка при сохранении", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
    private class SaveFileTask extends AsyncTask<String, Void, Boolean> {
        private Runnable onSuccessCallback;
        private String userTags;
        private String preview;

        public SaveFileTask(Runnable onSuccessCallback, String userTags, String preview) {
            this.onSuccessCallback = onSuccessCallback;
            this.userTags = userTags;
            this.preview = preview;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            SQLiteDatabase db = null;
            try {
                String newFileName = params[0];

                if (!newFileName.toLowerCase().endsWith(".docx")) {
                    newFileName += ".docx";
                }

                File originalFile = new File(filePath);
                File newFile = new File(originalFile.getParent(), newFileName);

                // Если имя файла изменилось
                if (!newFile.getAbsolutePath().equals(originalFile.getAbsolutePath())) {
                    if (newFile.exists()) {
                        int counter = 1;
                        String baseName = newFileName.replace(".docx", "");
                        while (newFile.exists()) {
                            newFileName = baseName + " (" + counter + ").docx";
                            newFile = new File(originalFile.getParent(), newFileName);
                            counter++;
                        }
                    }

                    // Удаляем старый файл, если это не новый файл
                    if (!isNewFile && originalFile.exists()) {
                        originalFile.delete();
                    }
                }

                filePath = newFile.getAbsolutePath();

                // Сохраняем содержимое документа
                List<ContentBlock> blocks = blockManager.getBlocks();
                DocxBlockExporter.exportToDocx(blocks, filePath);

                long currentTime = System.currentTimeMillis();

                // Открываем базу ОДИН РАЗ для всех операций
                db = databaseHelper.getWritableDatabase();

                if (isNewFile) {
                    // Для нового файла
                    long createdAt = currentTime;

                    // Формируем теги с датами
                    List<String> tagsList = new ArrayList<>();

                    // Добавляем пользовательские теги (если есть)
                    if (userTags != null && !userTags.trim().isEmpty()) {
                        String[] userTagsArray = userTags.split(",");
                        for (String tag : userTagsArray) {
                            String cleaned = tag.trim();
                            if (!cleaned.isEmpty() && !cleaned.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                                tagsList.add(cleaned);
                            }
                        }
                    }

                    // Добавляем дату создания и обновления
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    String createdDateTag = sdf.format(new Date(createdAt));
                    String updatedDateTag = sdf.format(new Date(currentTime));

                    tagsList.add(createdDateTag);
                    if (!createdDateTag.equals(updatedDateTag)) {
                        tagsList.add(updatedDateTag);
                    }

                    String tags = TextUtils.join(",", tagsList);

                    // Создаем объект документа
                    DocxFile doc = new DocxFile(newFileName, preview, filePath, currentTime, tags, createdAt);

                    // Вставляем документ
                    ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.COLUMN_TITLE, doc.getFileName());
                    values.put(DatabaseHelper.COLUMN_FILE_PATH, doc.getFilePath());
                    values.put(DatabaseHelper.COLUMN_PREVIEW_TEXT, doc.getPreviewText());
                    values.put(DatabaseHelper.COLUMN_CREATED_AT, createdAt);
                    values.put(DatabaseHelper.COLUMN_UPDATED_AT, currentTime);
                    values.put(DatabaseHelper.COLUMN_TAGS, tags);

                    long result = db.insert(DatabaseHelper.TABLE_DOCUMENTS, null, values);

                    if (result != -1) {
                        Log.d("SaveDebug", "✅ Новый файл добавлен с тегами: " + tags);
                        return true;
                    } else {
                        Log.e("SaveDebug", "❌ Ошибка добавления нового файла");
                        return false;
                    }
                } else {
                    // Для существующего файла
                    // Сначала получаем текущие теги и дату создания
                    Cursor cursor = null;
                    long createdAt = 0;
                    String currentTags = null;

                    try {
                        cursor = db.query(DatabaseHelper.TABLE_DOCUMENTS,
                                new String[]{DatabaseHelper.COLUMN_CREATED_AT, DatabaseHelper.COLUMN_TAGS},
                                DatabaseHelper.COLUMN_FILE_PATH + " = ?",
                                new String[]{filePath}, null, null, null);

                        if (cursor != null && cursor.moveToFirst()) {
                            int createdAtIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_CREATED_AT);
                            int tagsIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_TAGS);

                            if (createdAtIndex != -1) {
                                createdAt = cursor.getLong(createdAtIndex);
                            }
                            if (tagsIndex != -1) {
                                currentTags = cursor.getString(tagsIndex);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("SaveDebug", "Ошибка получения данных из БД", e);
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }

                    if (createdAt == 0) {
                        createdAt = currentTime;
                    }

                    // Формируем новые теги
                    List<String> tagsList = new ArrayList<>();

                    // 1. Оставляем пользовательские теги (не даты) из старых тегов
                    if (currentTags != null && !currentTags.trim().isEmpty()) {
                        String[] existingTags = currentTags.split(",");
                        for (String tag : existingTags) {
                            String cleaned = tag.trim();
                            if (!cleaned.isEmpty() && !cleaned.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                                tagsList.add(cleaned);
                            }
                        }
                    }

                    // 2. Добавляем новые пользовательские теги (если есть)
                    if (userTags != null && !userTags.trim().isEmpty()) {
                        String[] userTagsArray = userTags.split(",");
                        for (String tag : userTagsArray) {
                            String cleaned = tag.trim();
                            if (!cleaned.isEmpty() && !cleaned.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                                // Добавляем только если еще нет
                                if (!tagsList.contains(cleaned)) {
                                    tagsList.add(cleaned);
                                }
                            }
                        }
                    }

                    // 3. Добавляем даты
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    String createdDateTag = sdf.format(new Date(createdAt));
                    String updatedDateTag = sdf.format(new Date(currentTime));

                    if (!tagsList.contains(createdDateTag)) {
                        tagsList.add(createdDateTag);
                    }
                    if (!createdDateTag.equals(updatedDateTag) && !tagsList.contains(updatedDateTag)) {
                        tagsList.add(updatedDateTag);
                    }

                    String updatedTags = TextUtils.join(",", tagsList);

                    // Обновляем документ
                    ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.COLUMN_TITLE, newFileName);
                    values.put(DatabaseHelper.COLUMN_PREVIEW_TEXT, preview != null ? preview : "");
                    values.put(DatabaseHelper.COLUMN_UPDATED_AT, currentTime);
                    values.put(DatabaseHelper.COLUMN_TAGS, updatedTags);

                    int result = db.update(DatabaseHelper.TABLE_DOCUMENTS, values,
                            DatabaseHelper.COLUMN_FILE_PATH + " = ?",
                            new String[]{filePath});

                    if (result > 0) {
                        Log.d("SaveDebug", "✅ Файл обновлен. Теги: " + updatedTags);
                        return true;
                    } else {
                        Log.e("SaveDebug", "❌ Ошибка обновления файла");
                        return false;
                    }
                }

            } catch (Exception e) {
                Log.e("SaveDebug", "❌ Ошибка сохранения: " + e.getMessage(), e);
                return false;
            } finally {
                // Закрываем базу только здесь
                if (db != null && db.isOpen()) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        Log.e("SaveDebug", "Ошибка закрытия базы", e);
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                isContentChanged = false;
                isNewFile = false;
                originalFileName = new File(filePath).getName();

                Toast.makeText(TextEditorActivity.this,
                        "Файл сохранен", Toast.LENGTH_SHORT).show();

                if (onSuccessCallback != null) {
                    onSuccessCallback.run();
                }
            } else {
                Toast.makeText(TextEditorActivity.this,
                        "Ошибка при сохранении", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing()) {
            autoSave();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        // ОЧИСТКА РЕСУРСОВ
        if (blockManager != null) {
            blockManager.clearSelection();
        }
        if (scrollView != null && keyboardLayoutListener != null) {
            scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
        }
    }

//    private void autoSave() {
//        Log.d("AutoSave", "=== АВТОСОХРАНЕНИЕ ===");
//        Log.d("AutoSave", "isContentChanged: " + isContentChanged + ", isNewFile: " + isNewFile);
//
//        if ((isContentChanged || isNewFile) && titleEditText != null) {
//            String newFileName = titleEditText.getText().toString().trim();
//
//            // Если имя файла пустое, генерируем из содержимого
//            if (newFileName.isEmpty()) {
//                String contentPreview = blockManager.getContentPreview();
//                if (!contentPreview.isEmpty()) {
//                    newFileName = generateFileNameFromContent(contentPreview, 25);
//                    Log.d("AutoSave", "🔤 Сгенерировано автоназвание: " + newFileName);
//                } else {
//                    Log.d("AutoSave", "❌ Нечего сохранять - пустой документ без названия");
//                    return;
//                }
//            }
//
//            Log.d("AutoSave", "💾 Начинаем автосохранение: " + newFileName);
//            blockManager.updateAllBlocksFromViews();
//
//            // Получаем теги и запускаем сохранение
//            // ПОЛУЧАЕМ ПРЕВЬЮ ИЗ БЛОК-МЕНЕДЖЕРА
//            String preview = blockManager.getContentPreview();
//            String currentTags = tagEditText.getTagsForStorage();
//
//            new SaveFileTask(new Runnable() {
//                @Override
//                public void run() {
//                    if (!isNewFile) {
//                        originalTitle = titleEditText.getText().toString().trim();
//                        originalTags = currentTags;
//                        saveOriginalBlocksState();
//                        isContentChanged = false;
//                        updateActivityTitle();
//                    }
//                    finish();
//                }
//            }, currentTags, preview).execute(newFileName); // ПЕРЕДАЕМ ПРЕВЬЮ
//        } else {
//            Log.d("AutoSave", "⏭️ Автосохранение пропущено - нет изменений");
//        }
//    }

    private void autoSave() {
        Log.d("AutoSave", "=== АВТОСОХРАНЕНИЕ ===");
        Log.d("AutoSave", "isContentChanged: " + isContentChanged + ", isNewFile: " + isNewFile);

        // Для новых файлов проверяем, не пустой ли документ
        if (isNewFile && isEmptyDocument()) {
            Log.d("AutoSave", "❌ Пустой документ, не сохраняем");
            return;
        }

        if ((isContentChanged || isNewFile) && titleEditText != null) {
            String newFileName = titleEditText.getText().toString().trim();

            // Если имя файла пустое, генерируем из содержимого
            if (newFileName.isEmpty()) {
                String contentPreview = blockManager.getContentPreview();
                if (!contentPreview.isEmpty() && !contentPreview.equals("Пустой документ")) {
                    newFileName = generateFileNameFromContent(contentPreview, 25);
                    Log.d("AutoSave", "🔤 Сгенерировано автоназвание: " + newFileName);
                } else {
                    Log.d("AutoSave", "❌ Нечего сохранять - пустой документ без названия");
                    return;
                }
            }

            Log.d("AutoSave", "💾 Начинаем автосохранение: " + newFileName);
            blockManager.updateAllBlocksFromViews();

            String preview = blockManager.getContentPreview();
            String currentTags = tagEditText.getTagsForStorage();

            new SaveFileTask(new Runnable() {
                @Override
                public void run() {
                    if (!isNewFile) {
                        originalTitle = titleEditText.getText().toString().trim();
                        originalTags = currentTags;
                        saveOriginalBlocksState();
                        isContentChanged = false;
                        updateActivityTitle();
                    }
                }
            }, currentTags, preview).execute(newFileName);
        } else {
            Log.d("AutoSave", "⏭️ Автосохранение пропущено - нет изменений");
        }
    }

    // ДОБАВЛЯЕМ: сохранение исходного состояния блоков
    private void saveOriginalBlocksState() {
        originalBlocks.clear();
        List<ContentBlock> currentBlocks = blockManager.getBlocks();

        Log.d("ChangeDebug", "💾 Сохранение исходного состояния. Текущих блоков: " + currentBlocks.size());

        for (ContentBlock block : currentBlocks) {
            if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                TextBlock original = new TextBlock();

                String currentContent = textBlock.getRawContent();
                Log.d("ChangeDebug", "💾 Сохраняем текстовый блок: '" + currentContent + "'");

                if (textBlock.hasRealFormatting()) {
                    original.setHtmlContent(currentContent);
                    Log.d("ChangeDebug", "💾 Сохранен как HTML: " + currentContent);
                } else {
                    original.setRawContent(currentContent);
                    Log.d("ChangeDebug", "💾 Сохранен как обычный текст: '" + currentContent + "'");
                }

                originalBlocks.add(original);
            } else if (block instanceof TableBlock) {
                // ДОБАВЛЯЕМ: сохранение табличных блоков
                TableBlock tableBlock = (TableBlock) block;
                TableBlock originalTable = new TableBlock(tableBlock.getRows(), tableBlock.getCols());

                // Копируем данные ячеек
                for (int i = 0; i < tableBlock.getRows(); i++) {
                    for (int j = 0; j < tableBlock.getCols(); j++) {
                        originalTable.setCellData(i, j, tableBlock.getCellData(i, j));
                    }
                }

                originalBlocks.add(originalTable);
                Log.d("ChangeDebug", "💾 Сохранена таблица: " + tableBlock.getRows() + "x" + tableBlock.getCols() +
                        ", ID: " + tableBlock.getId());
            }
        }

        Log.d("ChangeDebug", "💾 Итоговое количество сохраненных блоков: " + originalBlocks.size());
    }

    private boolean hasRealChanges() {
        Log.d("ChangeDebug", "=== ПРОВЕРКА ИЗМЕНЕНИЙ ===");

        // Для новых файлов всегда считаем, что есть изменения
        if (isNewFile) {
            Log.d("ChangeDebug", "Новый файл - всегда сохраняем");
            return true;
        }

        // Проверяем изменение заголовка
        String currentTitle = titleEditText.getText().toString().trim();
        if (!currentTitle.equals(originalTitle)) {
            Log.d("ChangeDebug", "📝 Обнаружено изменение заголовка: '" + originalTitle + "' -> '" + currentTitle + "'");
            return true;
        }

        Log.d("ChangeDebug", "✅ Заголовок не изменился");

        // Получаем текущие блоки
        List<ContentBlock> currentBlocks = blockManager.getBlocks();

        Log.d("ChangeDebug", "📊 Проверка блоков: текущие=" + currentBlocks.size() + ", исходные=" + originalBlocks.size());

        // Проверяем изменение количества блоков
        if (currentBlocks.size() != originalBlocks.size()) {
            Log.d("ChangeDebug", "🔢 Обнаружено изменение количества блоков: " +
                    currentBlocks.size() + " vs " + originalBlocks.size());
            return true;
        }

        Log.d("ChangeDebug", "✅ Количество блоков не изменилось");

        // ВАЖНО: Сначала обновляем данные из View
        blockManager.updateAllBlocksFromViews();

        // Проверяем изменения в каждом блоке
        for (int i = 0; i < currentBlocks.size(); i++) {
            ContentBlock currentBlock = currentBlocks.get(i);
            ContentBlock originalBlock = originalBlocks.get(i);

            // Проверяем тип блока
            if (currentBlock.getType() != originalBlock.getType()) {
                Log.d("ChangeDebug", "🔄 Обнаружено изменение типа блока " + i +
                        ": " + currentBlock.getType() + " vs " + originalBlock.getType());
                return true;
            }

            // Проверяем текстовые блоки
            if (currentBlock instanceof TextBlock && originalBlock instanceof TextBlock) {
                TextBlock currentTextBlock = (TextBlock) currentBlock;
                TextBlock originalTextBlock = (TextBlock) originalBlock;

                String currentContent = currentTextBlock.getRawContent();
                String originalContent = originalTextBlock.getRawContent();

                // Сравниваем содержимое (включая HTML теги)
                boolean contentChanged = !safeEquals(currentContent, originalContent);
                if (contentChanged) {
                    Log.d("ChangeDebug", "📝 Обнаружено изменение содержимого в блоке " + i);
                    Log.d("ChangeDebug", "   Исходное: '" + originalContent + "'");
                    Log.d("ChangeDebug", "   Текущее: '" + currentContent + "'");
                    return true;
                }
            }

            // Проверяем табличные блоки
            if (currentBlock instanceof TableBlock && originalBlock instanceof TableBlock) {
                TableBlock currentTable = (TableBlock) currentBlock;
                TableBlock originalTable = (TableBlock) originalBlock;

                Log.d("TableDebug", "🔍 Проверка таблицы " + i +
                        ": текущая " + currentTable.getRows() + "x" + currentTable.getCols() +
                        ", исходная " + originalTable.getRows() + "x" + originalTable.getCols());

                if (currentTable.getRows() != originalTable.getRows() ||
                        currentTable.getCols() != originalTable.getCols()) {
                    Log.d("ChangeDebug", "📊 Обнаружено изменение структуры таблицы " + i +
                            ": " + currentTable.getRows() + "x" + currentTable.getCols() +
                            " vs " + originalTable.getRows() + "x" + originalTable.getCols());
                    return true;
                }

                // Проверяем содержимое ячеек таблицы
                boolean tableContentChanged = false;
                for (int row = 0; row < currentTable.getRows(); row++) {
                    for (int col = 0; col < currentTable.getCols(); col++) {
                        String currentCell = currentTable.getCellData(row, col);
                        String originalCell = originalTable.getCellData(row, col);

                        if (!safeEquals(currentCell, originalCell)) {
                            Log.d("ChangeDebug", "📋 Обнаружено изменение в ячейке [" + row + "," + col + "]: '" +
                                    originalCell + "' -> '" + currentCell + "'");
                            tableContentChanged = true;
                            break;
                        }
                    }
                    if (tableContentChanged) break;
                }
                if (tableContentChanged) return true;

                Log.d("TableDebug", "✅ Таблица " + i + " не изменилась");
            }
        }

        Log.d("ChangeDebug", "✅ Содержимое блоков не изменилось");

        // Проверяем изменение тегов
        String currentTags = tagEditText != null ? tagEditText.getTagsForStorage() : "";
        if (!safeEquals(currentTags, originalTags)) {
            Log.d("ChangeDebug", "🏷️ Обнаружено изменение тегов: '" + originalTags + "' -> '" + currentTags + "'");
            return true;
        }

        Log.d("ChangeDebug", "✅ Теги не изменились");
        Log.d("ChangeDebug", "=== ИЗМЕНЕНИЙ НЕТ ===");

        return false;
    }

    // ДОБАВЛЯЕМ: безопасное сравнение строк
    private boolean safeEquals(String str1, String str2) {
        if (str1 == null && str2 == null) return true;
        if (str1 == null) return str2.isEmpty();
        if (str2 == null) return str1.isEmpty();
        return str1.equals(str2);
    }

    // ДОБАВЛЯЕМ: методы интерфейса DocxAdapter.OnItemClickListener
    @Override
    public void onItemClick(DocxFile file) {
        // Не используется в этом контексте, но требуется интерфейсом
    }

    @Override
    public void onSelectionModeStarted() {
        // Не используется в этом контексте
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        // Не используется в этом контексте
    }

    private void safeBlockOperation(Runnable operation) {
        // Сохраняем состояние перед операцией
        blockManager.saveAllBlockStates();

        // Выполняем операцию
        operation.run();

        // Восстанавливаем состояние после операции
        blockManager.restoreAllBlockStates();
    }

    // ДОБАВЛЯЕМ: метод для инициализации диалога списков
    private void initListDialog() {
        listDialog = new Dialog(this);
        listDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        listDialog.setContentView(R.layout.dialog_list_format);

        // Настройки окна
        Window window = listDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }

        // Находим элементы
        orderedListBtn = listDialog.findViewById(R.id.orderedListBtn);
        unorderedListBtn = listDialog.findViewById(R.id.unorderedListBtn);
        removeListBtn = listDialog.findViewById(R.id.removeListBtn);
        cancelListBtn = listDialog.findViewById(R.id.cancelListBtn);

        // Устанавливаем обработчики
        orderedListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyNumberedList();
                listDialog.dismiss();
            }
        });

        unorderedListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyBulletedList();
                listDialog.dismiss();
            }
        });

        removeListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeListFormatting();
                listDialog.dismiss();
            }
        });

        cancelListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listDialog.dismiss();
            }
        });

        // Закрытие при клике вне диалога
        listDialog.findViewById(R.id.listDialogContainer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Ничего не делаем - предотвращаем закрытие при клике на сам контейнер
            }
        });
    }

    // ДОБАВЛЯЕМ: метод для показа диалога списков
    private void showListDialog() {
        Log.d("ListDebug", "=== ПОКАЗ ДИАЛОГА СПИСКОВ ===");
        if (focusedBlockId != null && focusedEditText != null) {
            Log.d("ListDebug", "Активный блок найден: " + focusedBlockId);
            Log.d("ListDebug", "Текст блока: '" + focusedEditText.getText().toString() + "'");
            Log.d("ListDebug", "Выделение: " + focusedEditText.getSelectionStart() + "-" + focusedEditText.getSelectionEnd());

            initListDialog();
            listDialog.show();
        } else {
            Log.d("ListDebug", "Нет активного текстового блока");
            Toast.makeText(this, "Сначала выберите текстовый блок", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyNumberedList() {
        if (focusedEditText == null) return;

        Editable editable = focusedEditText.getText();
        int start = focusedEditText.getSelectionStart();
        int end = focusedEditText.getSelectionEnd();

        Log.d("ListDebug", "=== ПРИМЕНЕНИЕ НУМЕРОВАННОГО СПИСКА ===");
        Log.d("ListDebug", "Выделение: start=" + start + ", end=" + end);
        Log.d("ListDebug", "Текст выделения: '" + editable.subSequence(start, end).toString() + "'");

        // Если нет выделения, работаем с текущим абзацем
        if (start == end) {
            int paragraphStart = findParagraphStart(editable, start);
            int paragraphEnd = findParagraphEnd(editable, start);
            formatParagraphAsNumberedList(editable, paragraphStart, paragraphEnd, 1);
            focusedEditText.setSelection(paragraphEnd);
        } else {
            // СОБИРАЕМ ВСЕ АБЗАЦЫ СНАЧАЛА
            List<ParagraphInfo> paragraphs = new ArrayList<>();
            int currentPos = start;

            while (currentPos < end) {
                int paragraphStart = findParagraphStart(editable, currentPos);
                int paragraphEnd = findParagraphEnd(editable, currentPos);

                // Ограничиваем выделением
                if (paragraphStart < start) paragraphStart = start;
                if (paragraphEnd > end) paragraphEnd = end;

                // Получаем текст абзаца
                String paragraphText = editable.subSequence(paragraphStart, paragraphEnd).toString();

                if (!paragraphText.trim().isEmpty()) {
                    paragraphs.add(new ParagraphInfo(paragraphStart, paragraphEnd, paragraphText));
                    Log.d("ListDebug", "Собран абзац " + paragraphs.size() + ": [" + paragraphStart + "-" + paragraphEnd + "] '" + paragraphText + "'");
                }

                // Переходим к следующему абзацу
                currentPos = paragraphEnd + 1;
                if (currentPos >= editable.length() || currentPos >= end) break;
            }

            Log.d("ListDebug", "Всего собрано абзацев: " + paragraphs.size());

            // ФОРМАТИРУЕМ С КОНЦА, чтобы позиции не смещались
            for (int i = paragraphs.size() - 1; i >= 0; i--) {
                ParagraphInfo info = paragraphs.get(i);
                Log.d("ListDebug", "Форматируем абзац " + (i + 1) + ": [" + info.start + "-" + info.end + "] '" + info.text + "'");
                formatParagraphAsNumberedList(editable, info.start, info.end, i + 1);
            }
        }

        // После форматирования обновляем TextBlock
        updateTextBlockFromEditText();
        markContentChanged();
    }

    private static class ParagraphInfo {
        int start;
        int end;
        String text;

        ParagraphInfo(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private void applyBulletedList() {
        if (focusedEditText == null) return;

        Editable editable = focusedEditText.getText();
        int start = focusedEditText.getSelectionStart();
        int end = focusedEditText.getSelectionEnd();

        Log.d("ListDebug", "=== ПРИМЕНЕНИЕ МАРКИРОВАННОГО СПИСКА ===");
        Log.d("ListDebug", "Выделение: start=" + start + ", end=" + end);

        if (start == end) {
            int paragraphStart = findParagraphStart(editable, start);
            int paragraphEnd = findParagraphEnd(editable, start);
            formatParagraphAsBulletedList(editable, paragraphStart, paragraphEnd);
            focusedEditText.setSelection(paragraphEnd);
        } else {
            // СОБИРАЕМ ВСЕ АБЗАЦЫ СНАЧАЛА
            List<ParagraphInfo> paragraphs = new ArrayList<>();
            int currentPos = start;

            while (currentPos < end) {
                int paragraphStart = findParagraphStart(editable, currentPos);
                int paragraphEnd = findParagraphEnd(editable, currentPos);

                if (paragraphStart < start) paragraphStart = start;
                if (paragraphEnd > end) paragraphEnd = end;

                String paragraphText = editable.subSequence(paragraphStart, paragraphEnd).toString();

                if (!paragraphText.trim().isEmpty()) {
                    paragraphs.add(new ParagraphInfo(paragraphStart, paragraphEnd, paragraphText));
                }

                currentPos = paragraphEnd + 1;
                if (currentPos >= editable.length() || currentPos >= end) break;
            }

            Log.d("ListDebug", "Всего собрано абзацев для маркированного списка: " + paragraphs.size());

            // ФОРМАТИРУЕМ С КОНЦА
            for (int i = paragraphs.size() - 1; i >= 0; i--) {
                ParagraphInfo info = paragraphs.get(i);
                formatParagraphAsBulletedList(editable, info.start, info.end);
            }
        }

        updateTextBlockFromEditText();
        markContentChanged();
    }

    private void removeListFormatting() {
        if (focusedEditText == null) return;

        Editable editable = focusedEditText.getText();
        int start = focusedEditText.getSelectionStart();
        int end = focusedEditText.getSelectionEnd();

        Log.d("ListDebug", "=== УДАЛЕНИЕ ФОРМАТИРОВАНИЯ СПИСКА ===");
        Log.d("ListDebug", "Выделение: start=" + start + ", end=" + end);

        if (start == end) {
            int paragraphStart = findParagraphStart(editable, start);
            int paragraphEnd = findParagraphEnd(editable, start);
            removeListFormattingFromParagraph(editable, paragraphStart, paragraphEnd);
            focusedEditText.setSelection(paragraphEnd);
        } else {
            // СОБИРАЕМ ВСЕ АБЗАЦЫ СНАЧАЛА
            List<ParagraphInfo> paragraphs = new ArrayList<>();
            int currentPos = start;

            while (currentPos < end) {
                int paragraphStart = findParagraphStart(editable, currentPos);
                int paragraphEnd = findParagraphEnd(editable, currentPos);

                if (paragraphStart < start) paragraphStart = start;
                if (paragraphEnd > end) paragraphEnd = end;

                String paragraphText = editable.subSequence(paragraphStart, paragraphEnd).toString();

                if (!paragraphText.trim().isEmpty()) {
                    paragraphs.add(new ParagraphInfo(paragraphStart, paragraphEnd, paragraphText));
                }

                currentPos = paragraphEnd + 1;
                if (currentPos >= editable.length() || currentPos >= end) break;
            }

            Log.d("ListDebug", "Всего собрано абзацев для удаления форматирования: " + paragraphs.size());

            // УДАЛЯЕМ ФОРМАТИРОВАНИЕ С КОНЦА
            for (int i = paragraphs.size() - 1; i >= 0; i--) {
                ParagraphInfo info = paragraphs.get(i);
                removeListFormattingFromParagraph(editable, info.start, info.end);
            }
        }

        updateTextBlockFromEditText();
        markContentChanged();
    }

    private int findParagraphStart(CharSequence text, int position) {
        if (position >= text.length()) {
            position = text.length() - 1;
        }
        if (position < 0) return 0;

        // Ищем предыдущий символ новой строки
        int i = position;
        while (i >= 0) {
            if (i == 0) return 0;
            if (text.charAt(i) == '\n') {
                return i + 1; // Начало после \n
            }
            i--;
        }
        return 0;
    }

    private int findParagraphEnd(CharSequence text, int position) {
        if (position >= text.length()) {
            return text.length();
        }

        // Ищем следующий символ новой строки
        int i = position;
        while (i < text.length()) {
            if (text.charAt(i) == '\n') {
                return i; // Конец на \n
            }
            i++;
        }
        return text.length();
    }

    // Форматировать абзац как нумерованный список
    private void formatParagraphAsNumberedList(Editable editable, int start, int end, int number) {
        String text = editable.subSequence(start, end).toString();
        Log.d("ListDebug", "Форматируем абзац [" + start + "-" + end + "]: '" + text + "', номер: " + number);

        // Сохраняем отступы в начале строки
        String indent = "";
        java.util.regex.Pattern indentPattern = java.util.regex.Pattern.compile("^(\\s*).*");
        java.util.regex.Matcher matcher = indentPattern.matcher(text);
        if (matcher.matches()) {
            indent = matcher.group(1);
        }

        // Удаляем существующие префиксы списков
        String cleanText = text.replaceFirst("^\\s*\\d+\\.\\s*", ""); // Удалить "1. ", "2. " и т.д.
        cleanText = cleanText.replaceFirst("^\\s*[•\\-]\\s*", ""); // Удалить "• " или "- "

        // Если текст не изменился, значит в нем не было префикса
        if (cleanText.equals(text)) {
            cleanText = text.replaceFirst("^\\s*", ""); // Удаляем все пробелы в начале
        }

        // Добавляем новый префикс с номером и сохраняем отступ
        String newText = indent + number + ". " + cleanText;
        Log.d("ListDebug", "Новый текст с отступом: '" + newText + "'");

        // Сохраняем существующее форматирование
        AbsoluteSizeSpan[] sizeSpans = editable.getSpans(start, end, AbsoluteSizeSpan.class);
        List<AbsoluteSizeSpan> spansList = new ArrayList<>(Arrays.asList(sizeSpans));

        // Заменяем текст
        editable.replace(start, end, newText);

        // Восстанавливаем форматирование (с учетом смещения)
        int newEnd = start + newText.length();
        for (AbsoluteSizeSpan span : spansList) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);

            // Если спан был в пределах заменяемого текста
            if (spanStart >= start && spanEnd <= end) {
                // Удаляем старый спан
                editable.removeSpan(span);

                // Вычисляем смещение
                int offset = newText.length() - (end - start);

                // Применяем спан к новому тексту
                AbsoluteSizeSpan newSpan = new AbsoluteSizeSpan(span.getSize(), true);
                editable.setSpan(newSpan, spanStart, spanEnd + offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    // Форматировать абзац как маркированный список
    private void formatParagraphAsBulletedList(Editable editable, int start, int end) {
        String text = editable.subSequence(start, end).toString();
        Log.d("ListDebug", "Форматируем абзац [" + start + "-" + end + "]: '" + text + "'");

        // Удаляем существующие префиксы списков
        String cleanText = text.replaceFirst("^\\s*\\d+\\.\\s*", "");
        cleanText = cleanText.replaceFirst("^\\s*[•\\-]\\s*", "");

        if (cleanText.equals(text)) {
            cleanText = text;
        }

        // Добавляем новый префикс с маркером
        String newText = "• " + cleanText;
        Log.d("ListDebug", "Новый текст: '" + newText + "'");

        // Сохраняем существующее форматирование
        AbsoluteSizeSpan[] sizeSpans = editable.getSpans(start, end, AbsoluteSizeSpan.class);
        List<AbsoluteSizeSpan> spansList = new ArrayList<>(Arrays.asList(sizeSpans));

        editable.replace(start, end, newText);

        // Восстанавливаем форматирование
        int newEnd = start + newText.length();
        for (AbsoluteSizeSpan span : spansList) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);

            if (spanStart >= start && spanEnd <= end) {
                editable.removeSpan(span);

                int offset = newText.length() - (end - start);

                AbsoluteSizeSpan newSpan = new AbsoluteSizeSpan(span.getSize(), true);
                editable.setSpan(newSpan, spanStart, spanEnd + offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    // Удалить форматирование списка из абзаца
    private void removeListFormattingFromParagraph(Editable editable, int start, int end) {
        String text = editable.subSequence(start, end).toString();
        Log.d("ListDebug", "Удаляем форматирование абзаца [" + start + "-" + end + "]: '" + text + "'");

        // Удаляем префиксы списков
        String newText = text.replaceFirst("^\\s*\\d+\\.\\s*", "");
        newText = newText.replaceFirst("^\\s*[•\\-]\\s*", "");

        // Если текст не изменился, ничего не делаем
        if (newText.equals(text)) {
            Log.d("ListDebug", "Текст не содержит форматирования списка");
            return;
        }

        Log.d("ListDebug", "Новый текст без форматирования: '" + newText + "'");

        // Сохраняем форматирование
        AbsoluteSizeSpan[] sizeSpans = editable.getSpans(start, end, AbsoluteSizeSpan.class);
        List<AbsoluteSizeSpan> spansList = new ArrayList<>(Arrays.asList(sizeSpans));

        editable.replace(start, end, newText);

        // Восстанавливаем форматирование
        int newEnd = start + newText.length();
        for (AbsoluteSizeSpan span : spansList) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);

            if (spanStart >= start && spanEnd <= end) {
                editable.removeSpan(span);

                int offset = newText.length() - (end - start);

                AbsoluteSizeSpan newSpan = new AbsoluteSizeSpan(span.getSize(), true);
                editable.setSpan(newSpan, spanStart, spanEnd + offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    // ДОБАВЛЯЕМ: метод для обновления TextBlock из EditText
    private void updateTextBlockFromEditText() {
        if (focusedBlockId != null && focusedEditText != null) {
            ContentBlock block = blockManager.getBlockById(focusedBlockId);
            if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                String currentText = focusedEditText.getText().toString();
                Log.d("ListDebug", "updateTextBlockFromEditText: текст из EditText: '" + currentText + "'");
                Log.d("ListDebug", "Количество переносов: " + countNewlines(currentText));

                textBlock.updateHtmlFromSpannable((Spannable) focusedEditText.getText());
                Log.d("ListDebug", "HTML после обновления: " + textBlock.getHtmlContent());
                markContentChanged();
            }
        }
    }

    private int countNewlines(String text) {
        if (text == null) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    /**
     * Проверяет, является ли документ пустым (не содержит содержания и не имеет названия)
     */
    private boolean isEmptyDocument() {
        Log.d("EmptyCheck", "=== ПРОВЕРКА ПУСТОГО ДОКУМЕНТА ===");

        // 1. Проверяем название
        String title = titleEditText.getText().toString().trim();
        boolean isTitleEmpty = title.isEmpty();
        Log.d("EmptyCheck", "Название: '" + title + "', пустое: " + isTitleEmpty);

        // 2. Проверяем блоки
        blockManager.updateAllBlocksFromViews();
        List<ContentBlock> blocks = blockManager.getBlocks();
        Log.d("EmptyCheck", "Количество блоков: " + blocks.size());

        boolean hasContent = false;

        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                String content = textBlock.getPlainText();
                Log.d("EmptyCheck", "Текстовый блок: '" + content + "'");

                if (content != null && !content.trim().isEmpty()) {
                    hasContent = true;
                    Log.d("EmptyCheck", "Найден непустой текстовый блок");
                    break;
                }
            } else if (block instanceof TableBlock) {
                TableBlock tableBlock = (TableBlock) block;
                Log.d("EmptyCheck", "Таблица: " + tableBlock.getRows() + "x" + tableBlock.getCols());

                // Проверяем, есть ли данные в таблице
                boolean tableHasData = false;
                for (int i = 0; i < tableBlock.getRows(); i++) {
                    for (int j = 0; j < tableBlock.getCols(); j++) {
                        String cellData = tableBlock.getCellData(i, j);
                        if (cellData != null && !cellData.trim().isEmpty()) {
                            tableHasData = true;
                            break;
                        }
                    }
                    if (tableHasData) break;
                }

                if (tableHasData) {
                    hasContent = true;
                    Log.d("EmptyCheck", "Найдена непустая таблица");
                    break;
                }
            }
        }

        // 3. Проверяем теги
        String tags = tagEditText != null ? tagEditText.getText().toString().trim() : "";
        boolean hasTags = tags.length() > 1; // Больше 1, потому что может быть только "#"
        Log.d("EmptyCheck", "Теги: '" + tags + "', есть теги: " + hasTags);

        // Документ считается пустым если:
        // - Название пустое И
        // - Нет содержимого в блоках И
        // - Нет тегов (или только символ #)
        boolean isEmpty = isTitleEmpty && !hasContent && !hasTags;

        Log.d("EmptyCheck", "Документ пустой: " + isEmpty);
        Log.d("EmptyCheck", "=== КОНЕЦ ПРОВЕРКИ ===");

        return isEmpty;
    }
}