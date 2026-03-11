package com.example.notes;

import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.content.Context;

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextBlock extends ContentBlock {
    private String text;
    private transient EditText editText;
    private String rawHtmlContent;
    private transient boolean wasModified = false;


    public TextBlock() {
        super();
        this.type = BlockType.TEXT;
        this.text = "";
        this.rawHtmlContent = "";
    }

    public TextBlock(String text) {
        this();
        setRawContent(text);
    }

    public void applyFontSize(int start, int end, int sizeSp) {
        Log.d("TextBlock", "applyFontSize: start=" + start + ", end=" + end +
                ", sizeSp=" + sizeSp + ", текст='" +
                (editText != null ? editText.getText().toString() : "null") + "'");

        if (editText != null && editText.getText() instanceof Spannable) {
            Spannable spannable = (Spannable) editText.getText();
            String currentText = spannable.toString();

            // Проверяем границы
            if (start < 0) start = 0;
            if (end > currentText.length()) end = currentText.length();
            if (start >= end) {
                Log.d("TextBlock", "Некорректные границы, пропускаем");
                return;
            }

            // Удаление всех существующих SizeSpan в этом диапазоне
            AbsoluteSizeSpan[] existingSpans = spannable.getSpans(start, end, AbsoluteSizeSpan.class);
            Log.d("TextBlock", "Удаляем существующие спаны в диапазоне " + start + "-" + end + ": " + existingSpans.length);

            for (AbsoluteSizeSpan span : existingSpans) {
                int spanStart = spannable.getSpanStart(span);
                int spanEnd = spannable.getSpanEnd(span);

                // Удаление спана только если он пересекается с нашим диапазоном
                if (!(spanEnd <= start || spanStart >= end)) {
                    spannable.removeSpan(span);
                    Log.d("TextBlock", "Удален спан: " + spanStart + "-" + spanEnd + ", размер=" + span.getSize());
                }
            }

            // Применение новый размер
            AbsoluteSizeSpan sizeSpan = new AbsoluteSizeSpan(sizeSp, true);
            spannable.setSpan(sizeSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            Log.d("TextBlock", "Применен новый спан: " + start + "-" + end + ", размер=" + sizeSp);

            // обновление HTML
            updateHtmlFromSpannable(spannable);
        } else {
            Log.e("TextBlock", "editText не доступен для применения размера");
        }
    }

    public void updateHtmlFromSpannable(Spannable spannable) {
        String fullText = spannable.toString();
        int textLength = fullText.length();

        Log.d("TextBlock", "updateHtmlFromSpannable: текст='" + fullText + "', длина=" + textLength);

        if (textLength == 0) {
            this.rawHtmlContent = null;
            this.text = "";
            return;
        }

        // Получаем все AbsoluteSizeSpan и сортируем по позиции
        AbsoluteSizeSpan[] sizeSpans = spannable.getSpans(0, textLength, AbsoluteSizeSpan.class);
        Arrays.sort(sizeSpans, new Comparator<AbsoluteSizeSpan>() {
            @Override
            public int compare(AbsoluteSizeSpan s1, AbsoluteSizeSpan s2) {
                return spannable.getSpanStart(s1) - spannable.getSpanStart(s2);
            }
        });

        Log.d("TextBlock", "Найдено спанов: " + sizeSpans.length);

        StringBuilder htmlBuilder = new StringBuilder();
        int currentPosition = 0;

        // Обрабатываем каждый спан
        for (int i = 0; i < sizeSpans.length; i++) {
            AbsoluteSizeSpan span = sizeSpans[i];
            int spanStart = spannable.getSpanStart(span);
            int spanEnd = spannable.getSpanEnd(span);
            int fontSize = span.getSize();

            Log.d("TextBlock", "Обработка спана " + i + ": " + spanStart + "-" + spanEnd +
                    ", размер=" + fontSize);

            // Текст до текущего спана (без форматирования)
            if (spanStart > currentPosition) {
                String beforeText = fullText.substring(currentPosition, spanStart);
                // Сохраняем переносы строк как <br>
                htmlBuilder.append(convertNewlinesToBr(beforeText));
                Log.d("TextBlock", "Текст до спана: '" + beforeText + "' -> '" + convertNewlinesToBr(beforeText) + "'");
            }

            // Текст внутри спана
            String spanText = fullText.substring(spanStart, spanEnd);
            if (!spanText.isEmpty()) {
                htmlBuilder.append("<font size=\"")
                        .append(fontSize)
                        .append("\">")
                        .append(convertNewlinesToBr(spanText))
                        .append("</font>");
                Log.d("TextBlock", "Текст в спане: '" + spanText + "' с размером " + fontSize);
            }

            currentPosition = spanEnd;
        }

        // Текст после последнего спана
        if (currentPosition < textLength) {
            String afterText = fullText.substring(currentPosition);
            htmlBuilder.append(convertNewlinesToBr(afterText));
            Log.d("TextBlock", "Текст после спанов: '" + afterText + "' -> '" + convertNewlinesToBr(afterText) + "'");
        }

        this.rawHtmlContent = htmlBuilder.toString();
        this.text = fullText;

        Log.d("TextBlock", "HTML обновлен: " + rawHtmlContent);
        this.wasModified = true;
        Log.d("TextBlock", "Блок помечен как измененный: " + this.id);
    }

    private String convertNewlinesToBr(String text) {
        if (text == null) return "";
        // Заменяем переносы строк на <br> и экранируем HTML
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br>");
    }

    @Override
    public String getRawContent() {
        Log.d("TextBlock", "⚡ getRawContent вызван! hasRealFormatting=" + hasRealFormatting() +
                ", rawHtmlContent=" + rawHtmlContent);

        if (hasRealFormatting()) {
            Log.d("TextBlock", "⚡ Возвращаем HTML: " + rawHtmlContent);
            return rawHtmlContent;
        } else {
            // Для обычного текста конвертируем переносы строк в <br>
            String content = text != null ? text : "";
            if (content.contains("\n")) {
                String htmlContent = content.replace("\n", "<br>");
                Log.d("TextBlock", "⚡ Возвращаем обычный текст с <br>: '" + htmlContent + "'");
                return htmlContent;
            } else {
                Log.d("TextBlock", "⚡ Возвращаем обычный текст: '" + content + "'");
                return content;
            }
        }
    }


    @Override
    public void setRawContent(String content) {
        Log.d("TextBlock", "💥 setRawContent вызван: '" + content + "'");

        if (content != null && content.contains("<br>")) {
            // Если есть <br>, конвертируем их в переносы строк
            String textWithNewlines = content.replace("<br>", "\n");
            this.rawHtmlContent = null;
            this.text = textWithNewlines;
            Log.d("TextBlock", "💥 Конвертирован <br> в переносы строк: '" + this.text + "'");
        } else {
            this.rawHtmlContent = null;
            this.text = content != null ? content : "";
            Log.d("TextBlock", "💥 rawHtmlContent сброшен в null, text установлен: '" + this.text + "'");
        }
    }

    public void setHtmlContent(String htmlContent) {
        Log.d("TextBlock", "🔥 setHtmlContent вызван: " + htmlContent);
        Log.d("TextBlock", "🔥 СТЕКТРЕЙС:", new Throwable()); // Добавляем стектрейс

        if (htmlContent != null && (htmlContent.contains("<font") || htmlContent.contains("<br>"))) {
            this.rawHtmlContent = htmlContent;
            // Преобразуем HTML в текст для отображения
            this.text = htmlToText(htmlContent);
            Log.d("TextBlock", "🔥 Установлен HTML контент: " + htmlContent);
        } else {
            Log.d("TextBlock", "🔥 setHtmlContent: нет тегов font или br, используем setRawContent");
            setRawContent(htmlContent);
        }
    }

    // Метод для проверки реального форматирования
    protected boolean hasRealFormatting() {
        if (rawHtmlContent == null || rawHtmlContent.isEmpty()) {
            Log.d("TextBlock", "hasRealFormatting: false (null or empty)");
            return false;
        }

        boolean hasFontTags = rawHtmlContent.contains("<font");
        Log.d("TextBlock", "hasRealFormatting: " + hasFontTags + " для: " + rawHtmlContent);
        return hasFontTags;
    }

    @Override
    public View createView(Context context) {
        EditText editText = new EditText(context);
        this.editText = editText;

        editText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Устанавливаем текст с форматированием
        if (rawHtmlContent != null && !rawHtmlContent.isEmpty()) {
            SpannableString spannable = htmlToSpannable(rawHtmlContent);
            editText.setText(spannable);
            Log.d("TextBlock", "Установлен HTML контент в EditText");
        } else {
            // Для обычного текста устанавливаем как есть (уже с переносами строк)
            editText.setText(text != null ? text : "");
            Log.d("TextBlock", "Установлен обычный текст в EditText: '" + text + "'");
        }

        editText.setHint("Введите текст...");
        editText.setMinHeight(150);
        editText.setGravity(Gravity.TOP);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); // базовый размер

        // Слушатели фокуса:
        // Слушатель для отслеживания выделения текста
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && v instanceof EditText) {
                    EditText focused = (EditText) v;
                    notifyBlockFocused(focused);
                }
            }
        });

        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v instanceof EditText) {
                    EditText focused = (EditText) v;
                    notifyBlockFocused(focused);
                }
            }
        });
        editText.setTag("TextBlock_" + this.id);

        return editText;
    }

    // Метод для уведомления о фокусе
    private void notifyBlockFocused(EditText editText) {
        // Здесь мы будем использовать интерфейс обратного вызова
        // Пока просто логируем
        Log.d("TextBlock", "Блок в фокусе: " + this.id + ", позиция курсора: " + editText.getSelectionStart());

        // В реальной реализации здесь будет вызов метода в Activity
        // через интерфейс или Broadcast
    }

    @Override
    public void updateFromView(View view) {
        if (view instanceof EditText) {
            EditText editText = (EditText) view;
            // Сохраняем как обычный текст
            this.text = editText.getText().toString();
            Log.d("TextBlock", "🔄 updateFromView: сохранен текст='" + this.text + "'");

            // Вместо этого просто логируем состояние
            Log.d("TextBlock", "🔄 updateFromView: rawHtmlContent=" + rawHtmlContent);
        }
    }

    private String htmlToText(String html) {
        if (html == null) return "";
        // Заменяем <br> на переносы строк перед преобразованием
        String htmlWithLineBreaks = html.replace("<br>", "\n");
        return Html.fromHtml(htmlWithLineBreaks).toString();
    }

    public SpannableString htmlToSpannable(String html) {
        if (html == null || html.isEmpty()) {
            return new SpannableString("");
        }

        Log.d("TextBlock", "htmlToSpannable: входной HTML=" + html);

        // РАЗБИВАЕМ ПО <br> и обрабатываем каждую часть отдельно
        String[] parts = html.split("<br>");
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (!part.trim().isEmpty()) {
                // Обрабатываем форматирование в этой части
                SpannableString partSpannable = processHtmlPart(part);
                builder.append(partSpannable);
            }

            // Добавляем перенос строки, если это не последняя часть
            if (i < parts.length - 1) {
                builder.append("\n");
            }
        }

        Log.d("TextBlock", "Итоговый spannable: '" + builder.toString() + "', длина=" + builder.length());
        return new SpannableString(builder);
    }

    private SpannableString processHtmlPart(String html) {
        if (html == null || html.isEmpty()) {
            return new SpannableString("");
        }

        // Если нет тегов <font>, просто возвращаем текст
        if (!html.contains("<font")) {
            String plainText = Html.fromHtml(html).toString();
            return new SpannableString(plainText);
        }

        // Парсим теги <font> для применения форматирования
        String plainText = Html.fromHtml(html).toString();
        SpannableString spannable = new SpannableString(plainText);

        Pattern pattern = Pattern.compile("<font size=\"(\\d+)\">(.*?)</font>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        int currentPosition = 0;

        while (matcher.find()) {
            int fontSize = Integer.parseInt(matcher.group(1));
            String htmlContent = matcher.group(2);
            String segmentText = Html.fromHtml(htmlContent).toString();

            if (!segmentText.isEmpty()) {
                // Находим позицию в plainText
                int start = plainText.indexOf(segmentText, currentPosition);
                if (start != -1) {
                    int end = start + segmentText.length();

                    if (start <= spannable.length() && end <= spannable.length()) {
                        AbsoluteSizeSpan sizeSpan = new AbsoluteSizeSpan(fontSize, true);
                        spannable.setSpan(sizeSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        currentPosition = end;
                    }
                }
            }
        }

        return spannable;
    }

    public int getFontSizeAt(int position) {
        Log.d("TextBlock", "getFontSizeAt: position=" + position);

        if (editText != null && editText.getText() instanceof Spannable) {
            Spannable spannable = (Spannable) editText.getText();
            AbsoluteSizeSpan[] sizeSpans = spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class);

            Log.d("TextBlock", "Всего спанов: " + sizeSpans.length);
            for (AbsoluteSizeSpan span : sizeSpans) {
                int spanStart = spannable.getSpanStart(span);
                int spanEnd = spannable.getSpanEnd(span);
                Log.d("TextBlock", "Спан: " + spanStart + "-" + spanEnd + ", размер=" + span.getSize());

                // Проверяем, находится ли позиция внутри этого спана
                if (position >= spanStart && position < spanEnd) {
                    Log.d("TextBlock", "Найден спан для позиции " + position + ": размер=" + span.getSize());
                    return span.getSize();
                }
            }

            // Если не найден спан, проверяем базовый размер EditText
            float textSizePx = editText.getTextSize();
            int textSizeSp = (int) (textSizePx / editText.getResources().getDisplayMetrics().scaledDensity);
            Log.d("TextBlock", "Спан не найден, базовый размер: " + textSizeSp);
            return textSizeSp;
        }

        Log.d("TextBlock", "EditText не доступен, возвращаем 16");
        return 16;
    }

    // Геттеры и сеттеры
    public String getPlainText() {
        if (text != null && !text.isEmpty()) {
            return text;
        } else if (rawHtmlContent != null) {
            return Html.fromHtml(rawHtmlContent).toString();
        } else {
            return "";
        }
    }

    public String getHtmlContent() {
        return rawHtmlContent;
    }

    // Метод для отладки
    @Override
    public String toString() {
        return "TextBlock{id='" + id + "', text='" + text + "'}";
    }

    public void markAsModified() {
        this.wasModified = true;
    }

    public boolean wasModified() {
        return wasModified;
    }

    public void clearModifiedFlag() {
        this.wasModified = false;
    }
}