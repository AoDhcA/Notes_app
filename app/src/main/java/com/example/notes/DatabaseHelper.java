package com.example.notes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
                + COLUMN_TAGS + " TEXT"  // Теги через запятую: "работа,важно,срочно"
                + ")";
        db.execSQL(CREATE_DOCUMENTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOCUMENTS);
        onCreate(db);
    }

    // Добавление документа
//    public long addDocument(DocxFile docxFile) {
//        SQLiteDatabase db = this.getWritableDatabase();
//        ContentValues values = new ContentValues();
//        values.put(COLUMN_TITLE, docxFile.getFileName());
//        values.put(COLUMN_FILE_PATH, docxFile.getFilePath());
//        values.put(COLUMN_PREVIEW_TEXT, docxFile.getPreviewText());
//        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
//
//        long id = db.insert(TABLE_DOCUMENTS, null, values);
//        db.close();
//        return id;
//    }

//    public List<DocxFile> getAllDocuments() {
//        List<DocxFile> documentList = new ArrayList<>();
//        String selectQuery = "SELECT * FROM " + TABLE_DOCUMENTS + " ORDER BY " + COLUMN_UPDATED_AT + " DESC";
//
//        Log.d("DatabaseDebug", "SQL запрос: " + selectQuery);
//
//        SQLiteDatabase db = this.getWritableDatabase();
//        Cursor cursor = db.rawQuery(selectQuery, null);
//
//        if (cursor != null && cursor.moveToFirst()) {
//            do {
//                int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
//                int previewIndex = cursor.getColumnIndex(COLUMN_PREVIEW_TEXT);
//                int filePathIndex = cursor.getColumnIndex(COLUMN_FILE_PATH);
//                int createdAtIndex = cursor.getColumnIndex(COLUMN_CREATED_AT);
//                int updatedAtIndex = cursor.getColumnIndex(COLUMN_UPDATED_AT);
//                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS); // ДОБАВЛЯЕМ
//
//                if (titleIndex != -1 && previewIndex != -1 && filePathIndex != -1 && updatedAtIndex != -1) {
////                    String title = cursor.getString(titleIndex);
////                    long updatedAt = cursor.getLong(updatedAtIndex);
////                    String tags = tagsIndex != -1 ? cursor.getString(tagsIndex) : null; // ПОЛУЧАЕМ ТЕГИ
////
////                    DocxFile document = new DocxFile(
////                            title,
////                            cursor.getString(previewIndex),
////                            cursor.getString(filePathIndex),
////                            updatedAt,
////                            tags // ПЕРЕДАЕМ ТЕГИ
//
//                    long createdAt = cursor.getLong(createdAtIndex);
//
//                    DocxFile document = new DocxFile(
//                            cursor.getString(titleIndex),
//                            cursor.getString(previewIndex),
//                            cursor.getString(filePathIndex),
//                            cursor.getLong(updatedAtIndex),
//                            tagsIndex != -1 ? cursor.getString(tagsIndex) : null,
//                            createdAt
//
//                    );
//                    documentList.add(document);
//                }
//            } while (cursor.moveToNext());
//        }
//
//        if (cursor != null) {
//            cursor.close();
//        }
//        db.close();
//        return documentList;
//    }

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
//    public int updateDocument(String filePath, String newFileName, String newPreview) {
//        SQLiteDatabase db = this.getWritableDatabase();
//        ContentValues values = new ContentValues();
//
//        long currentTime = System.currentTimeMillis();
//
//        // ПРОСТО сохраняем то, что нам передали
//        if (newFileName != null) {
//            values.put(COLUMN_TITLE, newFileName);
//        }
//        if (newPreview != null) {
//            values.put(COLUMN_PREVIEW_TEXT, newPreview);
//        }
//        values.put(COLUMN_UPDATED_AT, currentTime);
//
//        int result = db.update(TABLE_DOCUMENTS, values, COLUMN_FILE_PATH + " = ?",
//                new String[]{filePath});
//        db.close();
//        return result;
//    }

    // Удаление документа
    public synchronized void deleteDocument(String filePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DOCUMENTS, COLUMN_FILE_PATH + " = ?", new String[]{filePath});
        db.close();
    }

    // Проверка существования документа
//    public boolean documentExists(String filePath) {
//        SQLiteDatabase db = this.getReadableDatabase();
//        Cursor cursor = db.query(TABLE_DOCUMENTS, new String[]{COLUMN_ID},
//                COLUMN_FILE_PATH + " = ?", new String[]{filePath}, null, null, null);
//
//        boolean exists = (cursor != null && cursor.getCount() > 0);
//
//        if (cursor != null) {
//            cursor.close();
//        }
//        db.close();
//        return exists;
//    }

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

    /**
     * Получить теги документа по пути файла
     */
//    public String getDocumentTags(String filePath) {
//        SQLiteDatabase db = this.getReadableDatabase();
//        String tags = null;
//
//        Cursor cursor = db.query(TABLE_DOCUMENTS, new String[]{COLUMN_TAGS},
//                COLUMN_FILE_PATH + " = ?", new String[]{filePath}, null, null, null);
//
//        if (cursor != null && cursor.moveToFirst()) {
//            int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
//            if (tagsIndex != -1) {
//                tags = cursor.getString(tagsIndex);
//            }
//            cursor.close();
//        }
//        db.close();
//        return tags;
//    }
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

//    public int updateDocumentTags(String filePath, String tags) {
//        SQLiteDatabase db = this.getWritableDatabase();
//        ContentValues values = new ContentValues();
//        values.put(COLUMN_TAGS, tags);
//        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
//
//        int result = db.update(TABLE_DOCUMENTS, values, COLUMN_FILE_PATH + " = ?",
//                new String[]{filePath});
//        db.close();
//        return result;
//    }


    /**
     * Получить все уникальные теги из базы данных
     */
//    public List<String> getAllUniqueTags() {
//        List<String> allTags = new ArrayList<>();
//        Set<String> uniqueTags = new HashSet<>();
//
//        SQLiteDatabase db = this.getReadableDatabase();
//        Cursor cursor = db.query(TABLE_DOCUMENTS,
//                new String[]{COLUMN_TAGS},
//                null, null, null, null, null);
//
//        if (cursor != null && cursor.moveToFirst()) {
//            do {
//                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
//                if (tagsIndex != -1) {
//                    String tagsString = cursor.getString(tagsIndex);
//                    if (tagsString != null && !tagsString.trim().isEmpty()) {
//                        // Разделяем теги по запятой
//                        String[] tags = tagsString.split(",");
//                        for (String tag : tags) {
//                            String cleanedTag = tag.trim();
//                            if (!cleanedTag.isEmpty()) {
//                                uniqueTags.add(cleanedTag);
//                            }
//                        }
//                    }
//                }
//            } while (cursor.moveToNext());
//            cursor.close();
//        }
//        db.close();
//
//        allTags.addAll(uniqueTags);
//        Collections.sort(allTags);
//        return allTags;
//    }


    public synchronized List<DocxFile> searchByTags(String[] tags) {
        List<DocxFile> documentList = new ArrayList<>();

        if (tags == null || tags.length == 0) {
            return documentList;
        }

        // СОЗДАЕМ УСЛОВИЕ ДЛЯ КАЖДОГО ТЕГА
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

    /**
     * ------------------------------------------------------------------------------------
     * Поиск по нескольким тегам с разделителем (из строки поиска)----------------------------------------------------------------
     * Например: "работа срочно" -> ["работа", "срочно"]----------------------------------------------------
     */
    public synchronized List<DocxFile> searchByMultipleTags(String searchQuery) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // РАЗБИВАЕМ СТРОКУ ПО ПРОБЕЛАМ И ЗАПЯТЫМ
        String[] tags = searchQuery.split("[,\\s]+");
        Log.d("TagsSearch", "Разбивка тегов из '" + searchQuery + "': " + Arrays.toString(tags));

        return searchByTags(tags);
    }

    /**
     * Получить документы с пагинацией
     */
//    public List<DocxFile> getDocumentsWithPagination(int page) {
//        List<DocxFile> documentList = new ArrayList<>();
//
//        // ВАЛИДАЦИЯ ВХОДНЫХ ДАННЫХ
//        if (page < 0) {
//            page = 0;
//        }
//        int offset = page * PAGE_SIZE;
//
//        // БЕЗОПАСНЫЙ ЗАПРОС С ПАРАМЕТРАМИ
//        String selectQuery = "SELECT * FROM " + TABLE_DOCUMENTS +
//                " ORDER BY " + COLUMN_UPDATED_AT + " DESC" +
//                " LIMIT ? OFFSET ?";
//
//        Log.d("Pagination", "SQL запрос: " + selectQuery + ", LIMIT: " + PAGE_SIZE + ", OFFSET: " + offset);
//
//        SQLiteDatabase db = this.getWritableDatabase();
//        Cursor cursor = db.rawQuery(selectQuery, new String[]{
//                String.valueOf(PAGE_SIZE),
//                String.valueOf(offset)
//        });
//
//        if (cursor != null && cursor.moveToFirst()) {
//            do {
//                int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
//                int previewIndex = cursor.getColumnIndex(COLUMN_PREVIEW_TEXT);
//                int filePathIndex = cursor.getColumnIndex(COLUMN_FILE_PATH);
//                int updatedAtIndex = cursor.getColumnIndex(COLUMN_UPDATED_AT);
//                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
//
//                if (titleIndex != -1 && previewIndex != -1 && filePathIndex != -1 && updatedAtIndex != -1) {
//                    String title = cursor.getString(titleIndex);
//                    long updatedAt = cursor.getLong(updatedAtIndex);
//                    String tags = tagsIndex != -1 ? cursor.getString(tagsIndex) : null;
//
//                    DocxFile document = new DocxFile(
//                            title,
//                            cursor.getString(previewIndex),
////                            cursor.getString(filePathPath),
//                            cursor.getString(filePathIndex),
//                            updatedAt,
//                            tags
//                    );
//                    documentList.add(document);
//                }
//            } while (cursor.moveToNext());
//        }
//
//        if (cursor != null) {
//            cursor.close();
//        }
//        db.close();
//
//        Log.d("Pagination", "Загружено документов: " + documentList.size() + " (страница " + page + ")");
//        return documentList;
//    }
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

    /**
     * Проверить, есть ли еще документы для загрузки
     */
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

    /**
     * Получить дату создания в формате дд.мм.гггг
     */
//    private String getFormattedDate(long timestamp) {
//        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
//        return sdf.format(new Date(timestamp));
//    }

    /**
     * Обновить теги документа с добавлением дат
     */
//    public void updateDocumentWithAutoTags(String filePath, String newFileName, String
//            newPreview, long createdAt, long updatedAt) {
//        SQLiteDatabase db = this.getWritableDatabase();
//
//        // Получаем текущие теги
//        String currentTags = getDocumentTags(filePath);
//        List<String> tagsList = new ArrayList<>();
//
//        // Добавляем существующие теги (если есть)
//        if (currentTags != null && !currentTags.trim().isEmpty()) {
//            String[] existingTags = currentTags.split(",");
//            for (String tag : existingTags) {
//                String cleaned = tag.trim();
//                if (!cleaned.isEmpty() && !cleaned.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
//                    // Добавляем только не-даты (теги пользователя)
//                    tagsList.add(cleaned);
//                }
//            }
//        }
//
//        // Добавляем дату создания
//        String createdDateTag = getFormattedDate(createdAt);
//        if (!tagsList.contains(createdDateTag)) {
//            tagsList.add(createdDateTag);
//        }
//
//        // Добавляем дату обновления (если отличается от даты создания)
//        String updatedDateTag = getFormattedDate(updatedAt);
//        if (!createdDateTag.equals(updatedDateTag) && !tagsList.contains(updatedDateTag)) {
//            tagsList.add(updatedDateTag);
//        }
//
//        // Формируем строку тегов
//        String updatedTags = TextUtils.join(",", tagsList);
//
//        ContentValues values = new ContentValues();
//        values.put(COLUMN_TITLE, newFileName);
//        values.put(COLUMN_PREVIEW_TEXT, newPreview);
//        values.put(COLUMN_UPDATED_AT, updatedAt);
//        values.put(COLUMN_TAGS, updatedTags);
//
//        int result = db.update(TABLE_DOCUMENTS, values, COLUMN_FILE_PATH + " = ?",
//                new String[]{filePath});
//
//        if (result > 0) {
//            Log.d("AutoTags", "✅ Автотеги обновлены: " + updatedTags);
//        }
//
//        db.close();
//    }
//    public int updateDocumentWithAutoTags(String filePath, String newFileName,
//                                          String newPreview, long createdAt, long updatedAt) {
//        SQLiteDatabase db = null;
//        try {
//            db = this.getWritableDatabase();
//
//            // Сначала получим существующие теги
//            String currentTags = getDocumentTags(filePath);
//            List<String> tagsList = new ArrayList<>();
//
//            // Добавляем существующие теги (если есть и это не даты)
//            if (currentTags != null && !currentTags.trim().isEmpty()) {
//                String[] existingTags = currentTags.split(",");
//                for (String tag : existingTags) {
//                    String cleaned = tag.trim();
//                    if (!cleaned.isEmpty() && !cleaned.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
//                        // Добавляем только не-даты (теги пользователя)
//                        tagsList.add(cleaned);
//                    }
//                }
//            }
//
//            // Добавляем дату создания
//            String createdDateTag = getFormattedDate(createdAt);
//            if (!tagsList.contains(createdDateTag)) {
//                tagsList.add(createdDateTag);
//            }
//
//            // Добавляем дату обновления (ВСЕГДА, даже если совпадает с созданием)
//            String updatedDateTag = getFormattedDate(updatedAt);
//            if (!tagsList.contains(updatedDateTag)) {
//                tagsList.add(updatedDateTag);
//            }
//
//            // Формируем строку тегов
//            String updatedTags = TextUtils.join(",", tagsList);
//
//            ContentValues values = new ContentValues();
//            values.put(COLUMN_TITLE, newFileName);
//            values.put(COLUMN_PREVIEW_TEXT, newPreview);
//            values.put(COLUMN_UPDATED_AT, updatedAt);
//            values.put(COLUMN_TAGS, updatedTags);
//
//            int result = db.update(TABLE_DOCUMENTS, values, COLUMN_FILE_PATH + " = ?",
//                    new String[]{filePath});
//
//            if (result > 0) {
//                Log.d("AutoTags", "✅ Автотеги обновлены: " + updatedTags);
//            }
//
//            return result;
//
//        } catch (Exception e) {
//            Log.e("AutoTags", "❌ Ошибка обновления автотегов", e);
//            return 0;
//        } finally {
//            // ВАЖНО: Закрываем базу только здесь
//            if (db != null && db.isOpen()) {
//                try {
//                    db.close();
//                } catch (Exception e) {
//                    Log.e("Database", "Ошибка при закрытии базы", e);
//                }
//            }
//        }
//    }

//    public int updateDocumentWithAutoTags(String filePath, String newFileName,
//                                          String newPreview, long createdAt, long updatedAt) {
//        SQLiteDatabase db = null;
//        try {
//            db = this.getWritableDatabase();
//
//            // Получаем текущие теги
//            String currentTags = getDocumentTags(db, filePath);
//            StringBuilder tagsBuilder = new StringBuilder();
//
//            // Добавляем пользовательские теги (не даты)
//            if (currentTags != null && !currentTags.trim().isEmpty()) {
//                String[] existingTags = currentTags.split(",");
//                for (String tag : existingTags) {
//                    String cleaned = tag.trim();
//                    if (!cleaned.isEmpty() && !cleaned.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
//                        if (tagsBuilder.length() > 0) {
//                            tagsBuilder.append(",");
//                        }
//                        tagsBuilder.append(cleaned);
//                    }
//                }
//            }
//
//            // Добавляем дату создания
//            String createdDateTag = getFormattedDate(createdAt);
//            if (tagsBuilder.length() > 0) {
//                tagsBuilder.append(",");
//            }
//            tagsBuilder.append(createdDateTag);
//
//            // Добавляем дату обновления
//            String updatedDateTag = getFormattedDate(updatedAt);
//            if (!createdDateTag.equals(updatedDateTag)) {
//                tagsBuilder.append(",").append(updatedDateTag);
//            }
//
//            String updatedTags = tagsBuilder.toString();
//
//            ContentValues values = new ContentValues();
//            values.put(COLUMN_TITLE, newFileName);
//            values.put(COLUMN_PREVIEW_TEXT, newPreview != null ? newPreview : "");
//            values.put(COLUMN_UPDATED_AT, updatedAt);
//            values.put(COLUMN_TAGS, updatedTags);
//
//            int result = db.update(TABLE_DOCUMENTS, values, COLUMN_FILE_PATH + " = ?",
//                    new String[]{filePath});
//
//            Log.d("AutoTags", "✅ Теги обновлены: " + updatedTags + ", превью: " +
//                    (newPreview != null ? "'" + newPreview + "'" : "null"));
//
//            return result;
//
//        } catch (Exception e) {
//            Log.e("AutoTags", "❌ Ошибка обновления автотегов: " + e.getMessage(), e);
//            return 0;
//        } finally {
//            if (db != null && db.isOpen()) {
//                db.close();
//            }
//        }
//    }

//    private String getDocumentTags(SQLiteDatabase db, String filePath) {
//        Cursor cursor = null;
//        try {
//            cursor = db.query(TABLE_DOCUMENTS, new String[]{COLUMN_TAGS},
//                    COLUMN_FILE_PATH + " = ?", new String[]{filePath},
//                    null, null, null);
//
//            if (cursor != null && cursor.moveToFirst()) {
//                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
//                if (tagsIndex != -1) {
//                    return cursor.getString(tagsIndex);
//                }
//            }
//        } catch (Exception e) {
//            Log.e("Database", "Ошибка получения тегов", e);
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//        return null;
//    }

    /**
     * Добавить документ с автоматическими тегами
     */
//    public long addDocumentWithAutoTags(DocxFile docxFile, long createdAt, long updatedAt) {
//        SQLiteDatabase db = this.getWritableDatabase();
//
//        // Формируем теги с датами
//        List<String> tagsList = new ArrayList<>();
//
//        // Добавляем пользовательские теги (если есть)
//        if (docxFile.getTags() != null && !docxFile.getTags().trim().isEmpty()) {
//            String[] userTags = docxFile.getTags().split(",");
//            for (String tag : userTags) {
//                String cleaned = tag.trim();
//                if (!cleaned.isEmpty()) {
//                    tagsList.add(cleaned);
//                }
//            }
//        }
//
//        // Добавляем дату создания
//        String createdDateTag = getFormattedDate(createdAt);
//        tagsList.add(createdDateTag);
//
//        // Добавляем дату обновления
//        String updatedDateTag = getFormattedDate(updatedAt);
//        tagsList.add(updatedDateTag);
//
//        String tags = TextUtils.join(",", tagsList);
//
//        ContentValues values = new ContentValues();
//        values.put(COLUMN_TITLE, docxFile.getFileName());
//        values.put(COLUMN_FILE_PATH, docxFile.getFilePath());
//        values.put(COLUMN_PREVIEW_TEXT, docxFile.getPreviewText());
//        values.put(COLUMN_CREATED_AT, createdAt);
//        values.put(COLUMN_UPDATED_AT, updatedAt);
//        values.put(COLUMN_TAGS, tags);
//
//        long id = db.insert(TABLE_DOCUMENTS, null, values);
//        db.close();
//
//        Log.d("AutoTags", "📄 Добавлен документ с автотегами: " + tags);
//        return id;
//    }
//    public long addDocumentWithAutoTags(DocxFile docxFile, long createdAt, long updatedAt) {
//        SQLiteDatabase db = null;
//        try {
//            db = this.getWritableDatabase();
//
//            // Формируем теги с датами
//            List<String> tagsList = new ArrayList<>();
//
//            // Добавляем пользовательские теги (если есть)
//            if (docxFile.getTags() != null && !docxFile.getTags().trim().isEmpty()) {
//                String[] userTags = docxFile.getTags().split(",");
//                for (String tag : userTags) {
//                    String cleaned = tag.trim();
//                    if (!cleaned.isEmpty()) {
//                        tagsList.add(cleaned);
//                    }
//                }
//            }
//
//            // Добавляем дату создания
//            String createdDateTag = getFormattedDate(createdAt);
//            tagsList.add(createdDateTag);
//
//            // Добавляем дату обновления
//            String updatedDateTag = getFormattedDate(updatedAt);
//            tagsList.add(updatedDateTag);
//
//            String tags = TextUtils.join(",", tagsList);
//
//            ContentValues values = new ContentValues();
//            values.put(COLUMN_TITLE, docxFile.getFileName());
//            values.put(COLUMN_FILE_PATH, docxFile.getFilePath());
//            values.put(COLUMN_PREVIEW_TEXT, docxFile.getPreviewText());
//            values.put(COLUMN_CREATED_AT, createdAt);
//            values.put(COLUMN_UPDATED_AT, updatedAt);
//            values.put(COLUMN_TAGS, tags);
//
//            long id = db.insert(TABLE_DOCUMENTS, null, values);
//
//            Log.d("AutoTags", "📄 Добавлен документ с автотегами: " + tags);
//            return id;
//
//        } catch (Exception e) {
//            Log.e("AutoTags", "❌ Ошибка добавления документа", e);
//            return -1;
//        } finally {
//            if (db != null && db.isOpen()) {
//                try {
//                    db.close();
//                } catch (Exception e) {
//                    Log.e("Database", "Ошибка при закрытии базы", e);
//                }
//            }
//        }
//    }

    /**
     * Получить все уникальные теги (включая даты)
     */
//    public List<String> getAllUniqueTagsWithDates() {
//        List<String> allTags = new ArrayList<>();
//        Set<String> uniqueTags = new HashSet<>();
//
//        SQLiteDatabase db = this.getReadableDatabase();
//        Cursor cursor = db.query(TABLE_DOCUMENTS,
//                new String[]{COLUMN_TAGS},
//                null, null, null, null, null);
//
//        if (cursor != null && cursor.moveToFirst()) {
//            do {
//                int tagsIndex = cursor.getColumnIndex(COLUMN_TAGS);
//                if (tagsIndex != -1) {
//                    String tagsString = cursor.getString(tagsIndex);
//                    if (tagsString != null && !tagsString.trim().isEmpty()) {
//                        // Разделяем теги по запятой
//                        String[] tags = tagsString.split(",");
//                        for (String tag : tags) {
//                            String cleanedTag = tag.trim();
//                            if (!cleanedTag.isEmpty()) {
//                                uniqueTags.add(cleanedTag);
//                            }
//                        }
//                    }
//                }
//            } while (cursor.moveToNext());
//            cursor.close();
//        }
//        db.close();
//
//        allTags.addAll(uniqueTags);
//
//        // Сортируем: сначала даты (по убыванию), потом обычные теги
//        Collections.sort(allTags, new Comparator<String>() {
//            @Override
//            public int compare(String tag1, String tag2) {
//                boolean isDate1 = tag1.matches("\\d{2}\\.\\d{2}\\.\\d{4}");
//                boolean isDate2 = tag2.matches("\\d{2}\\.\\d{2}\\.\\d{4}");
//
//                if (isDate1 && isDate2) {
//                    // Сортируем даты по убыванию (новые первыми)
//                    return tag2.compareTo(tag1);
//                } else if (isDate1 && !isDate2) {
//                    return -1; // Даты перед обычными тегами
//                } else if (!isDate1 && isDate2) {
//                    return 1; // Обычные теги после дат
//                } else {
//                    return tag1.compareTo(tag2); // Обычные теги по алфавиту
//                }
//            }
//        });
//
//        return allTags;
//    }
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

    //    public long getDocumentCreatedAt(String filePath) {
//        SQLiteDatabase db = this.getReadableDatabase();
//        long createdAt = 0;
//
//        Cursor cursor = db.query(TABLE_DOCUMENTS, new String[]{COLUMN_CREATED_AT},
//                COLUMN_FILE_PATH + " = ?", new String[]{filePath}, null, null, null);
//
//        if (cursor != null && cursor.moveToFirst()) {
//            int createdAtIndex = cursor.getColumnIndex(COLUMN_CREATED_AT);
//            if (createdAtIndex != -1) {
//                createdAt = cursor.getLong(createdAtIndex);
//            }
//            cursor.close();
//        }
//        db.close();
//
//        return createdAt;
//    }
//    public long getDocumentCreatedAt(String filePath) {
//        SQLiteDatabase db = null;
//        Cursor cursor = null;
//        long createdAt = 0;
//
//        try {
//            db = this.getReadableDatabase();
//            cursor = db.query(TABLE_DOCUMENTS, new String[]{COLUMN_CREATED_AT},
//                    COLUMN_FILE_PATH + " = ?", new String[]{filePath}, null, null, null);
//
//            if (cursor != null && cursor.moveToFirst()) {
//                int createdAtIndex = cursor.getColumnIndex(COLUMN_CREATED_AT);
//                if (createdAtIndex != -1) {
//                    createdAt = cursor.getLong(createdAtIndex);
//                }
//                cursor.close();
//            }
//        } catch (Exception e) {
//            Log.e("Database", "Ошибка получения даты создания", e);
//        } finally {
//            if (cursor != null && !cursor.isClosed()) {
//                cursor.close();
//            }
//            if (db != null && db.isOpen()) {
//                try {
//                    db.close();
//                } catch (Exception e) {
//                    Log.e("Database", "Ошибка при закрытии базы", e);
//                }
//            }
//        }
//
//        return createdAt;
//    }
//    public int updateDocumentSimple(String filePath, String newFileName, String newPreview, String newTags) {
//        SQLiteDatabase db = null;
//        try {
//            db = this.getWritableDatabase();
//
//            ContentValues values = new ContentValues();
//            long currentTime = System.currentTimeMillis();
//
//            if (newFileName != null) {
//                values.put(COLUMN_TITLE, newFileName);
//            }
//            if (newPreview != null) {
//                values.put(COLUMN_PREVIEW_TEXT, newPreview);
//            }
//            if (newTags != null) {
//                values.put(COLUMN_TAGS, newTags);
//            }
//            values.put(COLUMN_UPDATED_AT, currentTime);
//
//            int result = db.update(TABLE_DOCUMENTS, values, COLUMN_FILE_PATH + " = ?",
//                    new String[]{filePath});
//
//            Log.d("UpdateSimple", "✅ Документ обновлен: " + filePath);
//            return result;
//
//        } catch (Exception e) {
//            Log.e("UpdateSimple", "❌ Ошибка обновления документа", e);
//            return 0;
//        } finally {
//            if (db != null && db.isOpen()) {
//                db.close();
//            }
//        }
//    }
}