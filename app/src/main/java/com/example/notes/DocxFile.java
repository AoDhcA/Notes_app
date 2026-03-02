package com.example.notes;

public class DocxFile {
    private String fileName;
    private String previewText;
    private String filePath;
    private long lastModified;
    private String tags;
    private long createdAt;

    public DocxFile(String fileName, String previewText, String filePath, long lastModified) {
        this.fileName = fileName;
        this.previewText = previewText;
        this.filePath = filePath;
        this.lastModified = lastModified;
    }

    public DocxFile(String fileName, String previewText, String filePath, long lastModified, String tags) {
        this(fileName, previewText, filePath, lastModified);
        this.tags = tags;
    }

    // Конструктор
    public DocxFile(String fileName, String previewText, String filePath, long lastModified, String tags, long createdAt) {
        this(fileName, previewText, filePath, lastModified, tags);
        this.createdAt = createdAt;
    }

    // Геттеры
    public String getFileName() { return fileName; }
    public String getPreviewText() { return previewText; }
    public String getFilePath() { return filePath; }
    public String getTags() { return tags; }
    public long getCreatedAt() { return createdAt; }
    public long getLastModified() { return lastModified; }
    public void setTags(String tags) { this.tags = tags; }
}