package com.example.notes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
    private List<DocxFile> searchResults;
    private OnSearchItemClickListener listener;
    private DatabaseHelper databaseHelper;

    public SearchAdapter(List<DocxFile> searchResults, OnSearchItemClickListener listener, Context context) {
        this.searchResults = searchResults != null ? searchResults : new ArrayList<>();
        this.listener = listener;
        this.databaseHelper = new DatabaseHelper(context); // ИНИЦИАЛИЗИРУЕМ
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.search_item_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocxFile file = searchResults.get(position);

        // Установка отображаемого названия
        String displayName = truncateFileName(file.getFileName(), 25);
        holder.fileName.setText(displayName);

        // Установка тегов
        String tags = getDisplayTags(file.getFilePath());
        if (!tags.isEmpty()) {
            holder.fileTags.setText(tags);
            holder.fileTags.setVisibility(View.VISIBLE);
        } else {
            holder.fileTags.setVisibility(View.GONE);
        }

        // Обработчик клика
        holder.itemCard.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSearchItemClick(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    public void updateResults(List<DocxFile> newResults) {
        this.searchResults = newResults != null ? newResults : new ArrayList<>();
        notifyDataSetChanged();
    }

    private String truncateFileName(String fileName, int maxLength) {
        if (fileName == null || fileName.isEmpty()) return "Без названия";

        String nameWithoutExtension = fileName.replace(".docx", "").replace(".DOCX", "");

        if (nameWithoutExtension.length() <= maxLength) {
            return nameWithoutExtension;
        } else {
            return nameWithoutExtension.substring(0, maxLength) + "...";
        }
    }

    private String getDisplayTags(String filePath) {
        // Получение тегов из БД
        String tags = databaseHelper.getDocumentTags(filePath);
        if (tags == null || tags.trim().isEmpty()) {
            return "";
        }

        // преобразование "тег1,тег2" В "#тег1 #тег2"
        String[] tagArray = tags.split(",");
        StringBuilder displayTags = new StringBuilder();

        for (String tag : tagArray) {
            String cleanedTag = tag.trim();
            if (!cleanedTag.isEmpty()) {
                if (displayTags.length() > 0) {
                    displayTags.append(" ");
                }
                displayTags.append("#").append(cleanedTag);
            }
        }

        return displayTags.toString();
    }

    public interface OnSearchItemClickListener {
        void onSearchItemClick(DocxFile file);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CardView itemCard;
        TextView fileName;
        //TextView filePreview;
        TextView fileTags;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemCard = itemView.findViewById(R.id.searchItemCard);
            fileName = itemView.findViewById(R.id.searchFileName);
            fileTags = itemView.findViewById(R.id.searchFileTags);
        }
    }
    public void close() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}