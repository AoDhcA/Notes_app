package com.example.notes;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "NotesApp.db";
    private static final int DATABASE_VERSION = 1;

    // Таблица документов
    public static final String TABLE_DOCUMENTS = "documents";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_FILE_PATH = "file_path";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_PREVIEW_TEXT = "preview_text";
    public static final String COLUMN_TAGS = "tags";

    public static final int PAGE_SIZE = 20;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_DOCUMENTS_TABLE = "CREATE TABLE " + TABLE_DOCUMENTS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TITLE + " TEXT,"
                + COLUMN_FILE_PATH + " TEXT UNIQUE,"
                + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + COLUMN_UPDATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + COLUMN_PREVIEW_TEXT + " TEXT,"
                + COLUMN_TAGS + " TEXT"  // Теги для поиска (через запятую)
                + ")";
        db.execSQL(CREATE_DOCUMENTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOCUMENTS);
        onCreate(db);
    }


public synchronized List<DocxFile> getAllDocuments() {
    SQLiteDatabase db = null;
    Cursor cursor = null;
    List<DocxFile> documentList = new ArrayList<>();

    try {
        db = this.getReadableDatabase();
        String selectQuery = "SELECT * FROM " + TABLE_DOCUMENTS + " ORDER BY " + COLUMN_UPDATED_AT + " DESC";
        cursor = db.rawQuery(selectQuery, null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
                int previewIndex = cursor.getColumnIndex(COLUMN_PREVIEW_TEXT);
                int filePathIndex = cursor.getColumnIndex(COLUMN_FILE_PATH);
                int updatedAtIndex = cursor.getColumnIndex(COLUMN_UPDATED_AT);
                int createdAtIndex = cursor.getColumnIndex(COLUMN_CREATED_AT);
                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);

                if (titleIndex != -1 && previewIndex != -1 && filePathIndex != -1 &&
                        updatedAtIndex != -1 && createdAtIndex != -1) {

                    long createdAt = cursor.getLong(createdAtIndex);

                    DocxFile document = new DocxFile(
                            cursor.getString(titleIndex),
                            cursor.getString(previewIndex),
                            cursor.getString(filePathIndex),
                            cursor.getLong(updatedAtIndex),
                            tagsIndex != -1 ? cursor.getString(tagsIndex) : null,
                            createdAt
                    );
                    documentList.add(document);
                }
            } while (cursor.moveToNext());
        }
    } catch (Exception e) {
        Log.e("Database", "Ошибка получения документов", e);
    } finally {
        if (cursor != null) {
            cursor.close();
        }
        if (db != null && db.isOpen()) {
            try {
                db.close();
            } catch (Exception e) {
                Log.e("Database", "Ошибка при закрытии базы", e);
            }
        }
    }

    return documentList;
}


    // Удаление документа
    public synchronized void deleteDocument(String filePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DOCUMENTS, COLUMN_FILE_PATH + " = ?", new String[]{filePath});
        db.close();
    }


    // Поиск документов
    public synchronized List<DocxFile> searchDocuments(String query) {
        List<DocxFile> documentList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_DOCUMENTS +
                " WHERE " + COLUMN_TITLE + " LIKE ? OR " + COLUMN_PREVIEW_TEXT + " LIKE ?" +
                " ORDER BY " + COLUMN_UPDATED_AT + " DESC";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{"%" + query + "%", "%" + query + "%"});

        if (cursor.moveToFirst()) {
            do {
                int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
                int previewIndex = cursor.getColumnIndex(COLUMN_PREVIEW_TEXT);
                int filePathIndex = cursor.getColumnIndex(COLUMN_FILE_PATH);
                int updatedAtIndex = cursor.getColumnIndex(COLUMN_UPDATED_AT);
                DocxFile document = new DocxFile(
                        cursor.getString(titleIndex),
                        cursor.getString(previewIndex),
                        cursor.getString(filePathIndex),
                        cursor.getLong(updatedAtIndex)
                );
                documentList.add(document);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return documentList;
    }

    public synchronized String getDocumentTags(String filePath) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        String tags = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_DOCUMENTS, new String[]{COLUMN_TAGS},
                    COLUMN_FILE_PATH + " = ?", new String[]{filePath}, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
                if (tagsIndex != -1) {
                    tags = cursor.getString(tagsIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("Database", "Ошибка получения тегов", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                try {
                    db.close();
                } catch (Exception e) {
                    Log.e("Database", "Ошибка при закрытии базы", e);
                }
            }
        }

        return tags;
    }



    public synchronized List<DocxFile> searchByTags(String[] tags) {
        List<DocxFile> documentList = new ArrayList<>();

        if (tags == null || tags.length == 0) {
            return documentList;
        }

        StringBuilder whereClause = new StringBuilder();
        List<String> whereArgs = new ArrayList<>();

        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append(COLUMN_TAGS + " LIKE ?");
            whereArgs.add("%" + tags[i].trim() + "%");
        }

        String selectQuery = "SELECT * FROM " + TABLE_DOCUMENTS +
                " WHERE " + whereClause.toString() +
                " ORDER BY " + COLUMN_UPDATED_AT + " DESC";

        Log.d("TagsSearch", "SQL запрос: " + selectQuery);
        Log.d("TagsSearch", "Параметры: " + whereArgs);

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, whereArgs.toArray(new String[0]));

        if (cursor.moveToFirst()) {
            do {
                int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
                int previewIndex = cursor.getColumnIndex(COLUMN_PREVIEW_TEXT);
                int filePathIndex = cursor.getColumnIndex(COLUMN_FILE_PATH);
                int updatedAtIndex = cursor.getColumnIndex(COLUMN_UPDATED_AT);
                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);

                DocxFile document = new DocxFile(
                        cursor.getString(titleIndex),
                        cursor.getString(previewIndex),
                        cursor.getString(filePathIndex),
                        cursor.getLong(updatedAtIndex)
                );
                documentList.add(document);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();

        Log.d("TagsSearch", "Найдено документов: " + documentList.size());
        return documentList;
    }

    // Поиск по нескольким тегам с разделителем (из строки поиска)

    public synchronized List<DocxFile> searchByMultipleTags(String searchQuery) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // РАЗБИВАЕМ СТРОКУ ПО ПРОБЕЛАМ И ЗАПЯТЫМ
        String[] tags = searchQuery.split("[,\\s]+");
        Log.d("TagsSearch", "Разбивка тегов из '" + searchQuery + "': " + Arrays.toString(tags));

        return searchByTags(tags);
    }


    public synchronized List<DocxFile> getDocumentsWithPagination(int page) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        List<DocxFile> documentList = new ArrayList<>();

        try {
            // ВАЛИДАЦИЯ ВХОДНЫХ ДАННЫХ
            if (page < 0) {
                page = 0;
            }
            int offset = page * PAGE_SIZE;

            // БЕЗОПАСНЫЙ ЗАПРОС С ПАРАМЕТРАМИ
            String selectQuery = "SELECT * FROM " + TABLE_DOCUMENTS +
                    " ORDER BY " + COLUMN_UPDATED_AT + " DESC" +
                    " LIMIT ? OFFSET ?";

            Log.d("Pagination", "SQL запрос: " + selectQuery + ", LIMIT: " + PAGE_SIZE + ", OFFSET: " + offset);

            db = this.getReadableDatabase(); // Используем getReadableDatabase вместо getWritableDatabase
            cursor = db.rawQuery(selectQuery, new String[]{
                    String.valueOf(PAGE_SIZE),
                    String.valueOf(offset)
            });

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
                    int previewIndex = cursor.getColumnIndex(COLUMN_PREVIEW_TEXT);
                    int filePathIndex = cursor.getColumnIndex(COLUMN_FILE_PATH);
                    int updatedAtIndex = cursor.getColumnIndex(COLUMN_UPDATED_AT);
                    int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
                    int createdAtIndex = cursor.getColumnIndex(COLUMN_CREATED_AT);

                    if (titleIndex != -1 && previewIndex != -1 && filePathIndex != -1 && updatedAtIndex != -1) {
                        String title = cursor.getString(titleIndex);
                        long updatedAt = cursor.getLong(updatedAtIndex);
                        String tags = tagsIndex != -1 ? cursor.getString(tagsIndex) : null;
                        long createdAt = createdAtIndex != -1 ? cursor.getLong(createdAtIndex) : updatedAt;

                        DocxFile document = new DocxFile(
                                title,
                                cursor.getString(previewIndex),
                                cursor.getString(filePathIndex),
                                updatedAt,
                                tags,
                                createdAt
                        );
                        documentList.add(document);
                    }
                } while (cursor.moveToNext());
            }

        } catch (Exception e) {
            Log.e("Pagination", "❌ Ошибка пагинации: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                try {
                    db.close();
                } catch (Exception e) {
                    Log.e("Database", "Ошибка закрытия базы", e);
                }
            }
        }

        Log.d("Pagination", "Загружено документов: " + documentList.size() + " (страница " + page + ")");
        return documentList;
    }

    // Проверка: есть ли еще документы для загрузки

    public synchronized boolean hasMoreDocuments(int currentPage) {
        int offset = (currentPage + 1) * PAGE_SIZE;
        String countQuery = "SELECT COUNT(*) FROM " + TABLE_DOCUMENTS;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);

        int totalCount = 0;
        if (cursor != null && cursor.moveToFirst()) {
            totalCount = cursor.getInt(0);
            cursor.close();
        }
        db.close();

        boolean hasMore = offset < totalCount;
        Log.d("Pagination", "Всего документов: " + totalCount + ", offset: " + offset + ", hasMore: " + hasMore);
        return hasMore;
    }

    public synchronized List<String> getAllUniqueTagsWithDates() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        List<String> allTags = new ArrayList<>();
        Set<String> uniqueTags = new HashSet<>();

        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_DOCUMENTS,
                    new String[]{COLUMN_TAGS},
                    null, null, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
                    if (tagsIndex != -1) {
                        String tagsString = cursor.getString(tagsIndex);
                        if (tagsString != null && !tagsString.trim().isEmpty()) {
                            String[] tags = tagsString.split(",");
                            for (String tag : tags) {
                                String cleanedTag = tag.trim();
                                if (!cleanedTag.isEmpty()) {
                                    uniqueTags.add(cleanedTag);
                                }
                            }
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("Database", "Ошибка получения тегов", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                try {
                    db.close();
                } catch (Exception e) {
                    Log.e("Database", "Ошибка при закрытии базы", e);
                }
            }
        }

        allTags.addAll(uniqueTags);

        // Сортировка: сначала даты (по убыванию), потом обычные теги
        Collections.sort(allTags, new Comparator<String>() {
            @Override
            public int compare(String tag1, String tag2) {
                boolean isDate1 = tag1.matches("\\d{2}\\.\\d{2}\\.\\d{4}");
                boolean isDate2 = tag2.matches("\\d{2}\\.\\d{2}\\.\\d{4}");

                if (isDate1 && isDate2) {
                    return tag2.compareTo(tag1); // Даты по убыванию
                } else if (isDate1 && !isDate2) {
                    return -1; // Даты перед обычными тегами
                } else if (!isDate1 && isDate2) {
                    return 1; // Обычные теги после дат
                } else {
                    return tag1.compareTo(tag2); // Обычные теги по алфавиту
                }
            }
        });

        return allTags;
    }
}