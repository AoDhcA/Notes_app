package com.example.notes;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocxAdapter extends RecyclerView.Adapter<DocxAdapter.ViewHolder> {
    private List<DocxFile> fileList;
    private OnItemClickListener listener;
    private Set<String> selectedFilePaths = new HashSet<>();
    private boolean isSelectionMode = false;

    // Цвета
    private final int normalColor = Color.parseColor("#FFFFFF");
    private final int selectedColor = Color.parseColor("#FF000000");
    private final int normalTextColor = Color.parseColor("#FF000000");
    private final int selectedTextColor = Color.parseColor("#FFFFFFFF");

    public DocxAdapter(List<DocxFile> fileList, OnItemClickListener listener) {
        this.fileList = fileList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_docx, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocxFile file = fileList.get(position);

        // обрезание названия и превью
        String displayName = truncateFileName(file.getFileName(), 20);
        holder.fileName.setText(displayName);
        holder.filePreview.setText(file.getPreviewText());

        // Обновление внешнего вида
        updateCardAppearance(holder, file);
        setupEventHandlers(holder, file);
    }

    //  Обрезает имя файла для отображения (убирает .docx и ограничивает длину)
    private String truncateFileName(String fileName, int maxLength) {
        if (fileName == null || fileName.isEmpty()) return "Новый документ";

        // Убираем расширение .docx
        String nameWithoutExtension = fileName.replace(".docx", "").replace(".DOCX", "");

        if (nameWithoutExtension.length() <= maxLength) {
            return nameWithoutExtension;
        } else {
            return nameWithoutExtension.substring(0, maxLength) + "...";
        }
    }

    private void updateCardAppearance(ViewHolder holder, DocxFile file) {
        boolean isSelected = selectedFilePaths.contains(file.getFilePath());

        if (isSelected) {
            holder.noteButton.setCardBackgroundColor(selectedColor);
            holder.fileName.setTextColor(selectedTextColor);
            holder.filePreview.setTextColor(selectedTextColor);
        } else {
            holder.noteButton.setCardBackgroundColor(normalColor);
            holder.fileName.setTextColor(normalTextColor);
            holder.filePreview.setTextColor(normalTextColor);
        }
    }

    private void setupEventHandlers(ViewHolder holder, DocxFile file) {
        // Очистка все предыдущих обработчики
        holder.noteButton.setOnClickListener(null);
        holder.noteButton.setOnLongClickListener(null);

        if (isSelectionMode) {
            // В режиме выделения - клик переключает выделение
            holder.noteButton.setOnClickListener(v -> {
                toggleFileSelection(file);
                if (listener != null) {
                    listener.onSelectionChanged(selectedFilePaths.size());
                }
            });
        } else {
            // Клик открывает, долгое нажатие начинает выделение
            holder.noteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(file);
                }
            });

            holder.noteButton.setOnLongClickListener(v -> {
                startSelectionMode(file);
                return true;
            });
        }
    }

    private void startSelectionMode(DocxFile file) {
        isSelectionMode = true;
        selectedFilePaths.add(file.getFilePath());
        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectionModeStarted();
        }
    }

    private void toggleFileSelection(DocxFile file) {
        String filePath = file.getFilePath();
        if (selectedFilePaths.contains(filePath)) {
            selectedFilePaths.remove(filePath);
        } else {
            selectedFilePaths.add(filePath);
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        isSelectionMode = false;
        selectedFilePaths.clear();
        notifyDataSetChanged();
    }

    public List<DocxFile> getSelectedFiles() {
        List<DocxFile> selectedFiles = new ArrayList<>();
        for (DocxFile file : fileList) {
            if (selectedFilePaths.contains(file.getFilePath())) {
                selectedFiles.add(file);
            }
        }
        return selectedFiles;
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    @Override
    public int getItemCount() {
        return fileList != null ? fileList.size() : 0;
    }

    public void updateList(List<DocxFile> newList) {
        // Всегда создаем новый список чтобы избежать проблем с ссылками
        this.fileList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(DocxFile file);

        void onSelectionModeStarted();

        void onSelectionChanged(int selectedCount);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CardView noteButton;
        TextView fileName;
        TextView filePreview;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            noteButton = itemView.findViewById(R.id.noteButton);
            fileName = itemView.findViewById(R.id.fileName);
            filePreview = itemView.findViewById(R.id.filePreview);
        }
    }
}

