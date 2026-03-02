package com.example.notes;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.cardview.widget.CardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockManager {
    private List<ContentBlock> blocks = new ArrayList<>();
    private Map<String, BlockState> blockStates = new HashMap<>();
    private LinearLayout container;
    private Context context;
    private BlockFocusListener focusListener;
    private boolean isRendering = false;

    // Интерфейс для уведомления об изменениях
    public interface OnContentChangeListener {
        void onContentChanged();
    }

    // Метод для установки слушателя фокуса
    public void setBlockFocusListener(BlockFocusListener listener) {
        this.focusListener = listener;
    }

    // Класс для хранения состояния блока
    private static class BlockState {
        String htmlContent;
        int selectionStart;
        int selectionEnd;
        boolean hasFocus;

        BlockState(String htmlContent, int selectionStart, int selectionEnd, boolean hasFocus) {
            this.htmlContent = htmlContent;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.hasFocus = hasFocus;
        }
    }


    private OnContentChangeListener contentChangeListener;

    public void setOnContentChangeListener(OnContentChangeListener listener) {
        this.contentChangeListener = listener;
    }

    // Метод для сохранения состояния блоков перед операциями
    public void saveAllBlockStates() {
        blockStates.clear();

        for (int i = 0; i < Math.min(container.getChildCount(), blocks.size()); i++) {
            View blockView = container.getChildAt(i);
            ContentBlock block = blocks.get(i);

            if (block instanceof TextBlock && blockView instanceof EditText) {
                TextBlock textBlock = (TextBlock) block;
                EditText editText = (EditText) blockView;

                // Сохраняем HTML контент
                String htmlContent = textBlock.getHtmlContent();

                // Сохраняем позицию курсора и выделение
                int selectionStart = editText.getSelectionStart();
                int selectionEnd = editText.getSelectionEnd();
                boolean hasFocus = editText.hasFocus();

                // Если null, значит обычный текст
                if (shouldSaveBlockState(textBlock)) {
                    blockStates.put(block.getId(), new BlockState(htmlContent, selectionStart, selectionEnd, hasFocus));
                    Log.d("BlockManager", "Сохранено состояние блока " + block.getId() +
                            ": selection=" + selectionStart + "-" + selectionEnd + ", focus=" + hasFocus);
                } else {
                    Log.d("BlockManager", "Пропущено сохранение состояния блока " + block.getId() + " (был изменен)");
                }
            }
        }
    }

    private boolean shouldSaveBlockState(TextBlock textBlock) {
        // Если HTML null, значит обычный текст без форматирования
        if (textBlock.getHtmlContent() == null) {
            return true;
        }
        return true;
    }

    // Метод для восстановления состояния ВСЕХ блоков после операций
    public void restoreAllBlockStates() {
        Log.d("BlockManager", "=== ВОССТАНОВЛЕНИЕ СОСТОЯНИЙ БЛОКОВ ===");
        for (int i = 0; i < Math.min(container.getChildCount(), blocks.size()); i++) {
            View blockView = container.getChildAt(i);
            ContentBlock block = blocks.get(i);

            BlockState savedState = blockStates.get(block.getId());
            if (savedState != null && block instanceof TextBlock && blockView instanceof EditText) {
                TextBlock textBlock = (TextBlock) block;
                EditText editText = (EditText) blockView;

                Log.d("BlockManager", "Восстановление блока " + block.getId() +
                        ", HTML: " + savedState.htmlContent);
                // Восстанавливаем HTML контент
                if (savedState.htmlContent != null) {
                    textBlock.setHtmlContent(savedState.htmlContent);
                    SpannableString spannable = textBlock.htmlToSpannable(savedState.htmlContent);
                    editText.setText(spannable);

                    // Восстанавливаем позицию курсора и выделение
                    if (savedState.selectionStart >= 0 && savedState.selectionStart <= editText.getText().length() &&
                            savedState.selectionEnd >= 0 && savedState.selectionEnd <= editText.getText().length()) {

                        editText.setSelection(savedState.selectionStart, savedState.selectionEnd);
                    }

                    // Восстанавливаем фокус, если был
                    if (savedState.hasFocus) {
                        editText.requestFocus();

                        // Уведомляем о фокусе
                        if (focusListener != null) {
                            focusListener.onBlockFocused(block.getId(), editText);
                        }
                    }
                }

                Log.d("BlockManager", "Восстановлено состояние блока " + block.getId() +
                        ": selection=" + savedState.selectionStart + "-" + savedState.selectionEnd);
            }
        }

        blockStates.clear();
        Log.d("BlockManager", "=== ВОССТАНОВЛЕНИЕ ЗАВЕРШЕНО ===");
    }

    // Отслеживание текущих View
    private List<View> currentViews = new ArrayList<>();

    public BlockManager(LinearLayout container, Context context) {
        this.container = container;
        this.context = context;
    }

    public void addBlock(ContentBlock block) {
        // обновление данных текущих view перед обновлением
        updateAllBlocksFromViews();

        blocks.add(block);
        renderBlocks();

        // проверка что форматирование сохранилось
        if (block instanceof TextBlock) {
            TextBlock textBlock = (TextBlock) block;
            Log.d("BlockManager", "Добавлен TextBlock с HTML: " + textBlock.getHtmlContent());
        }
    }

    public void insertBlock(int position, ContentBlock block) {
        // обновление данных текущих view
        updateAllBlocksFromViews();

        blocks.add(position, block);
        renderBlocks();
    }

    public void removeBlock(String blockId) {
        // обновление данных текущих view
        updateAllBlocksFromViews();

        // поиск индекса блока
        int indexToRemove = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                indexToRemove = i;
                break;
            }
        }

        if (indexToRemove != -1) {
            blocks.remove(indexToRemove);
            renderBlocks();
            Log.d("BlockManager", "Блок удален: " + blockId + ", индекс: " + indexToRemove);
        }
    }

    // перерисовывает все блоки
    public void renderBlocks() {
        Log.d("BlockManager", "renderBlocks: начало, блоков=" + blocks.size());
        Log.d("BlockManager", "=== НАЧАЛО RENDER BLOCKS ===");

        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                Log.d("BlockManager", "Блок " + block.getId() + " HTML до render: " + textBlock.getHtmlContent());
                Log.d("BlockManager", "Блок " + block.getId() + " текст до render: " + textBlock.getPlainText());
            }
        }

        if (isRendering) {
            Log.d("BlockManager", "renderBlocks: пропуск, уже выполняется рендеринг");
            return;
        }
        isRendering = true;

        try {
            // сохраниение форматирования перед  перерисовкой
            saveCurrentFormatting();

            container.removeAllViews();
            currentViews.clear();

            for (int i = 0; i < blocks.size(); i++) {
                ContentBlock block = blocks.get(i);

                if (block instanceof TextBlock) {
                    TextBlock textBlock = (TextBlock) block;
                    Log.d("BlockManager", "Создание View для TextBlock " + i +
                            ", HTML: " + textBlock.getHtmlContent() +
                            ", plainText: " + textBlock.getPlainText());
                }

                View blockView = block.createView(context);
                Log.d("BlockManager", "Создан View для блока " + i + ": " + block.getType() + ", ID: " + block.getId());

                container.addView(blockView);
                currentViews.add(blockView);

                // Восстанавливает форматирование поле добавление в контейнер
                restoreFormatting(block, blockView);

                // Настройка фокуса и слушателей
                if (block instanceof TextBlock) {
                    setupFocusTracking((TextBlock) block, blockView);
                }
                if (block instanceof TableBlock) {
                    TableBlock tableBlock = (TableBlock) block;
                    tableBlock.setOnTableDeleteListener(tableDeleteListener);
                }
            }

            Log.d("BlockManager", "renderBlocks: завершено");

            // Проверка результата
            for (ContentBlock block : blocks) {
                if (block instanceof TextBlock) {
                    TextBlock textBlock = (TextBlock) block;
                    Log.d("BlockManager", "Блок " + block.getId() + " HTML после render: " + textBlock.getHtmlContent());
                }
            }

        } finally {
            isRendering = false;
        }
        Log.d("BlockManager", "=== КОНЕЦ RENDER BLOCKS ===");
    }

    // Сохранение текущего форматирования
    private void saveCurrentFormatting() {
        Log.d("BlockManager", "saveCurrentFormatting: сохранение форматирования");
        for (int i = 0; i < Math.min(container.getChildCount(), blocks.size()); i++) {
            View blockView = container.getChildAt(i);
            ContentBlock block = blocks.get(i);

            if (block instanceof TextBlock && blockView instanceof EditText) {
                TextBlock textBlock = (TextBlock) block;
                EditText editText = (EditText) blockView;

                // Пропуск блоков, помеченныех как измененные
                if (textBlock.wasModified()) {
                    Log.d("BlockManager", "Пропущено сохранение для измененного блока " + textBlock.getId());
                    textBlock.clearModifiedFlag(); // Сброс флага после рендера
                    continue;
                }

                // Сохранение Spannable в HTML перед удалением View
                if (editText.getText() instanceof Spannable) {
                    Spannable spannable = (Spannable) editText.getText();
                    textBlock.updateHtmlFromSpannable(spannable);
                    Log.d("BlockManager", "Сохранено форматирование для TextBlock " + textBlock.getId());
                }
            }
        }
    }

    public void renderBlocksWithoutRestore() {
        Log.d("BlockManager", "renderBlocksWithoutRestore: начало");
        if (isRendering) {
            Log.d("BlockManager", "renderBlocksWithoutRestore: пропуск, уже выполняется рендеринг");
            return;
        }
        isRendering = true;
        try {
            // Сохранение форматирования перед перерисовкой
            saveCurrentFormatting();

            container.removeAllViews();
            currentViews.clear();

            for (int i = 0; i < blocks.size(); i++) {
                ContentBlock block = blocks.get(i);

                if (block instanceof TextBlock) {
                    TextBlock textBlock = (TextBlock) block;
                    Log.d("BlockManager", "Создание View для TextBlock " + i +
                            ", HTML: " + textBlock.getHtmlContent());
                }

                View blockView = block.createView(context);
                Log.d("BlockManager", "Создан View для блока " + i + ": " + block.getType() + ", ID: " + block.getId());

                container.addView(blockView);
                currentViews.add(blockView);

                // Восстанавливает форматирование после добавления в контейнер
                restoreFormatting(block, blockView);

                // Настройка фокуса и слушателей
                if (block instanceof TextBlock) {
                    setupFocusTracking((TextBlock) block, blockView);
                }
                if (block instanceof TableBlock) {
                    TableBlock tableBlock = (TableBlock) block;
                    tableBlock.setOnTableDeleteListener(tableDeleteListener);
                }
            }

            Log.d("BlockManager", "renderBlocksWithoutRestore: завершено");

        } finally {
            isRendering = false;
        }
    }

    private void restoreFormatting(ContentBlock block, View blockView) {
        if (block instanceof TextBlock && blockView instanceof EditText) {
            TextBlock textBlock = (TextBlock) block;
            EditText editText = (EditText) blockView;

            Log.d("BlockManager", "Восстановление TextBlock " + textBlock.getId() +
                    ", HTML: " + (textBlock.getHtmlContent() != null ? textBlock.getHtmlContent() : "null"));

            // Восстанавливает форматирование из HTML
            if (textBlock.getHtmlContent() != null && !textBlock.getHtmlContent().isEmpty()) {
                try {
                    SpannableString spannable = textBlock.htmlToSpannable(textBlock.getHtmlContent());
                    editText.setText(spannable);

                    // принудителтное обновление Spannable в TextBlock
                    if (editText.getText() instanceof Spannable) {
                        textBlock.updateHtmlFromSpannable((Spannable) editText.getText());
                    }

                    // Проверка результата
                    AbsoluteSizeSpan[] spans = spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class);
                    Log.d("BlockManager", "Спанов после восстановления: " + spans.length);

                } catch (Exception e) {
                    Log.e("BlockManager", "Ошибка восстановления форматирования", e);
                    editText.setText(textBlock.getPlainText());
                }
            } else {
                editText.setText(textBlock.getPlainText());
            }
        }
    }

    // Настройка отслеживания фокуса
    private void setupFocusTracking(TextBlock textBlock, View blockView) {
        if (blockView instanceof EditText) {
            EditText editText = (EditText) blockView;

            editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && focusListener != null) {
                        focusListener.onBlockFocused(textBlock.getId(), editText);
                    }
                }
            });

            editText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (focusListener != null) {
                        focusListener.onBlockFocused(textBlock.getId(), editText);
                    }
                }
            });
        }
    }

    public List<ContentBlock> getBlocks() {
        // Обновление данных перед возвратом
        updateAllBlocksFromViews();
        return blocks;
    }

    // Метод для получения индекса блока по ID
    public int getBlockIndexById(String blockId) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                return i;
            }
        }
        return -1;
    }

    public String getContentPreview() {
        StringBuilder preview = new StringBuilder();
        boolean hasContent = false;

        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                String content = textBlock.getPlainText();
                if (content != null && !content.trim().isEmpty()) {
                    preview.append(content).append(" ");
                    hasContent = true;
                }
            } else if (block instanceof TableBlock) {
                TableBlock tableBlock = (TableBlock) block;
                // Таблица считается содержимым
                preview.append("[Таблица] ");
                hasContent = true;
            }
        }

        String result = preview.toString().trim();

        if (!hasContent) {
            return "Пустой документ";
        }

        if (result.length() > 100) {
            result = result.substring(0, 97) + "...";
        }

        return result;
    }


    public void clearSelection() {
        // Реализация если нужна
    }

    // МЕТОД ОБНОВЛЕНИЯ данных
    public void updateAllBlocksFromViews() {
        for (int i = 0; i < Math.min(container.getChildCount(), blocks.size()); i++) {
            View blockView = container.getChildAt(i);
            if (blockView != null) {
                ContentBlock block = blocks.get(i);

                if (block instanceof TextBlock && blockView instanceof EditText) {
                    TextBlock textBlock = (TextBlock) block;
                    EditText editText = (EditText) blockView;

                    // Сохранение Spannable в HTML только если есть реальное форматирование
                    if (editText.getText() instanceof Spannable) {
                        Spannable spannable = (Spannable) editText.getText();
                        textBlock.updateHtmlFromSpannable(spannable);
                    }
                }

                block.updateFromView(blockView);
            }
        }
    }

    public ContentBlock getBlockById(String blockId) {
        for (ContentBlock block : blocks) {
            if (block.getId().equals(blockId)) {
                return block;
            }
        }
        return null;
    }

    // Слушатель удаления таблицы
    private TableBlock.OnTableDeleteListener tableDeleteListener;

    public void setTableDeleteListener(TableBlock.OnTableDeleteListener listener) {
        this.tableDeleteListener = listener;
    }
}