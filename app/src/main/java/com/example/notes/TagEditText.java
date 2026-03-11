package com.example.notes;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;

public class TagEditText extends AppCompatEditText {
    private static final String TAG_PREFIX = "#";
    private boolean isEditing = false;

    public TagEditText(Context context) {
        super(context);
        init();
    }

    public TagEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TagEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setupTextWatcher();
        setupFocusListener();
    }

    private void setupTextWatcher() {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isEditing) return;

                String currentText = s.toString();

                // Автодобавление # при начале ввода в пустое поле
                if (currentText.isEmpty() && count > 0) {
                    isEditing = true;
                    setText(TAG_PREFIX + s);
                    setSelection(getText().length());
                    isEditing = false;
                    return;
                }

                // Добавление нового # после пробела
                if (count == 1 && start + count == s.length()) {
                    char lastChar = s.charAt(start);
                    if (lastChar == ' ') {
                        isEditing = true;
                        String newText = currentText + TAG_PREFIX;
                        setText(newText);
                        setSelection(newText.length());
                        isEditing = false;
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupFocusListener() {
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getText().toString().isEmpty()) {
                // При фокусе на пустое поле добавляем #
                isEditing = true;
                setText(TAG_PREFIX);
                setSelection(1);
                isEditing = false;
            }
        });
    }

    // Получить список тегов (без #)
    public java.util.List<String> getTagsList() {
        String text = getText().toString().trim();
        if (text.isEmpty()) return new java.util.ArrayList<>();

        // Разделяем по # и убираем пустые элементы
        String[] tagArray = text.split("#");
        java.util.List<String> tags = new java.util.ArrayList<>();

        for (String tag : tagArray) {
            String cleanedTag = tag.trim();
            if (!cleanedTag.isEmpty()) {
                tags.add(cleanedTag);
            }
        }

        return tags;
    }

    // Установить список тегов (автоматически добавляет #)
    public void setTagsList(java.util.List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            setText("");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String tag : tags) {
            if (sb.length() > 0) {
                sb.append(" "); // пробел между тегами
            }
            sb.append(TAG_PREFIX).append(tag.trim());
        }

        isEditing = true;
        setText(sb.toString());
        isEditing = false;
    }

    // Получить теги в формате для хранения в БД
    public String getTagsForStorage() {
        java.util.List<String> tags = getTagsList();
        return android.text.TextUtils.join(",", tags);
    }

    // Загрузить теги из формата БД
    public void setTagsFromStorage(String storedTags) {
        if (storedTags == null || storedTags.trim().isEmpty()) {
            setText("");
            return;
        }

        String[] tags = storedTags.split(",");
        java.util.List<String> tagList = new java.util.ArrayList<>();
        for (String tag : tags) {
            String cleanedTag = tag.trim();
            if (!cleanedTag.isEmpty()) {
                tagList.add(cleanedTag);
            }
        }
        setTagsList(tagList);
    }
}