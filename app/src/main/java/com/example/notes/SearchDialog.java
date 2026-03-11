package com.example.notes;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SearchDialog extends Dialog {
    private MainActivity activity;
    private DatabaseHelper databaseHelper;

    private EditText searchEditText;
    private RecyclerView searchRecyclerView;
    private CardView btnCancelSearch;

    private SearchAdapter searchAdapter;
    private List<DocxFile> allDocuments;
    private List<DocxFile> searchResults;

    public SearchDialog(@NonNull MainActivity activity) {
        super(activity);
        this.activity = activity;
        this.databaseHelper = new DatabaseHelper(activity);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.custom_search_view);

        initViews();
        loadAllDocuments();
        setupSearchFunctionality();
    }

    private void initViews() {
        searchEditText = findViewById(R.id.searchEditText);
        //searchButton = findViewById(R.id.searchButton);
        searchRecyclerView = findViewById(R.id.searchRecyclerView);
        btnCancelSearch = findViewById(R.id.btnCancelSearch);
        //btnClearSearch = findViewById(R.id.btnClearSearch);

        // Настройка RecyclerView - ПЕРЕДАЕМ CONTEXT
        searchRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        searchResults = new ArrayList<>();
        searchAdapter = new SearchAdapter(searchResults, this::onSearchItemClick, getContext());
        searchRecyclerView.setAdapter(searchAdapter);
    }

    private void loadAllDocuments() {
        new Thread(() -> {
            allDocuments = databaseHelper.getAllDocuments();
            // Показать все документы при открытии
            activity.runOnUiThread(() -> {
                searchResults.clear();
                searchResults.addAll(allDocuments);
                searchAdapter.updateResults(searchResults);
            });
        }).start();
    }

    private void setupSearchFunctionality() {
        // Поиск при вводе текста
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


        // Кнопка отмены
        btnCancelSearch.setOnClickListener(v -> {
            hideKeyboard();
            if (searchAdapter != null) {
                searchAdapter.close();
            }
            dismiss();

        });
    }

    private void performSearch(String query) {
        if (query.trim().isEmpty()) {
            // Показать все документы если запрос пустой
            searchResults.clear();
            searchResults.addAll(allDocuments);
            searchAdapter.updateResults(searchResults);
            return;
        }

        new Thread(() -> {
            List<DocxFile> results = new ArrayList<>();

            // Поиск по названию
            List<DocxFile> titleResults = databaseHelper.searchDocuments(query);
            results.addAll(titleResults);

            // Поиск по тегам (лог и)
            List<DocxFile> tagResults = databaseHelper.searchByMultipleTags(query);
            for (DocxFile file : tagResults) {
                // Добавить только если еще нет в результатах
                boolean alreadyExists = false;
                for (DocxFile existing : results) {
                    if (existing.getFilePath().equals(file.getFilePath())) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (!alreadyExists) {
                    results.add(file);
                }
            }

            // Обновление UI в основном потоке
            activity.runOnUiThread(() -> {
                searchResults.clear();
                searchResults.addAll(results);
                searchAdapter.updateResults(searchResults);

                // Показать количество результатов
                Toast.makeText(activity, "Найдено: " + results.size() + " файлов", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void onSearchItemClick(DocxFile file) {
        // Закрыть диалога и открыть файла
        dismiss();
        activity.openDocxFile(file);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // закрыт адаптер и БД
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}