package com.example.notes;

import android.view.View;

import android.content.Context;

import java.util.UUID;

public abstract class ContentBlock {
    protected String id;
    protected BlockType type;

    public enum BlockType {
        TEXT, TABLE, //IMAGE, HEADER
    }

    public ContentBlock() {
        this.id = UUID.randomUUID().toString();
    }

    public abstract String getRawContent();
    public abstract void setRawContent(String content);
    public abstract View createView(Context context);
    public abstract void updateFromView(View view);

    // Геттеры и сеттеры
    public String getId() { return id; }
    public BlockType getType() { return type; }
}
