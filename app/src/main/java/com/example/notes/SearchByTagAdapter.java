package com.example.notes;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SearchByTagAdapter extends RecyclerView.Adapter<SearchByTagAdapter.ViewHolder> {
    private List<String> tags;
    private OnTagClickListener listener;
    private String selectedTag = null;

    // Цвета для состояний
    private final int normalColor = Color.parseColor("#DEE3E3"); // синий
    private final int selectedColor = Color.parseColor("#FF000000"); // темно-синий
    private final int normalTextColor = Color.parseColor("#FF000000"); // черный
    private final int selectedTextColor = Color.parseColor("#DEE3E3"); // белый

    public SearchByTagAdapter(List<String> tags, OnTagClickListener listener) {
        this.tags = tags;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.search_by_tag_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String tag = tags.get(position);
        holder.tagTextView.setText("#" + tag);

        // Установка цвета в зависимости от выбора
        boolean isSelected = tag.equals(selectedTag);
        updateCardAppearance(holder, isSelected);

        // Обработчик клика
        holder.tagCard.setOnClickListener(v -> {
            if (listener != null) {
                // Если тег уже выбран - снятие выделения ( null)
                if (tag.equals(selectedTag)) {
                    selectedTag = null;
                    updateCardAppearance(holder, false);
                    listener.onTagClick(null);
                } else {
                    // Выбор нового тега
                    selectedTag = tag;
                    updateCardAppearance(holder, true);
                    listener.onTagClick(tag);
                }
            }
        });
    }

    private void updateCardAppearance(ViewHolder holder, boolean isSelected) {
        if (isSelected) {
            holder.tagCard.setCardBackgroundColor(selectedColor);
            holder.tagTextView.setTextColor(selectedTextColor);
        } else {
            holder.tagCard.setCardBackgroundColor(normalColor);
            holder.tagTextView.setTextColor(normalTextColor);
        }
    }

    public void setSelectedTag(String tag) {
        this.selectedTag = tag;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    public interface OnTagClickListener {
        void onTagClick(String tag);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CardView tagCard;
        TextView tagTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tagCard = itemView.findViewById(R.id.searchByTagCard);
            tagTextView = itemView.findViewById(R.id.searchByTagTextView);
        }
    }
    public void clearSelection() {
        String previousSelected = selectedTag;
        selectedTag = null;

        // Уведомление об изменении, если был выбранный тег
        if (previousSelected != null) {
            int position = tags.indexOf(previousSelected);
            if (position != -1) {
                notifyItemChanged(position);
            }
        }
    }
}