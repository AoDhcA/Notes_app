package com.example.notes;

import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFormattingHelper {
    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 25;
    public static final int DEFAULT_FONT_SIZE = 16;

    private static class SpanData {
        int start, end, size;
        SpanData(int start, int end, int size) {
            this.start = start;
            this.end = end;
            this.size = size;
        }
    }

    // ========== РАБОТА С РАЗМЕРОМ ШРИФТА ==========

    public static void applyFontSize(Editable editable, int start, int end, int sizeSp) {
        if (start < 0) start = 0;
        if (end > editable.length()) end = editable.length();
        if (start >= end) return;

        // Собираем все спаны размера, которые пересекаются с диапазоном [start, end]
        AbsoluteSizeSpan[] spans = editable.getSpans(start, end, AbsoluteSizeSpan.class);

        for (AbsoluteSizeSpan span : spans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            int spanSize = span.getSize();

            // Удаление старого спана
            editable.removeSpan(span);

            // Случай 1: спан полностью внутри выделения — ничего не восстанавливаем

            // Случай 2: спан выходит за левую границу
            if (spanStart < start && spanEnd <= end) {
                // Восстанавливаем левую часть (до start) со старым размером
                if (spanStart < start) {
                    AbsoluteSizeSpan leftSpan = new AbsoluteSizeSpan(spanSize, true);
                    editable.setSpan(leftSpan, spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            // Случай 3: спан выходит за правую границу
            else if (spanStart >= start && spanStart < end && spanEnd > end) {
                // Восстанавливаем правую часть (от end до spanEnd)
                if (spanEnd > end) {
                    AbsoluteSizeSpan rightSpan = new AbsoluteSizeSpan(spanSize, true);
                    editable.setSpan(rightSpan, end, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            // Случай 4: спан охватывает весь диапазон и выходит за обе границы
            else if (spanStart < start && spanEnd > end) {
                // Левая часть
                AbsoluteSizeSpan leftSpan = new AbsoluteSizeSpan(spanSize, true);
                editable.setSpan(leftSpan, spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                // Правая часть
                AbsoluteSizeSpan rightSpan = new AbsoluteSizeSpan(spanSize, true);
                editable.setSpan(rightSpan, end, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Теперь устанавливаем новый спан на выделение
        AbsoluteSizeSpan newSpan = new AbsoluteSizeSpan(sizeSp, true);
        editable.setSpan(newSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public static int getFontSizeAtPosition(Editable editable, int position) {
        AbsoluteSizeSpan[] spans = editable.getSpans(0, editable.length(), AbsoluteSizeSpan.class);
        for (AbsoluteSizeSpan span : spans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            if (position >= spanStart && position < spanEnd) {
                return span.getSize();
            }
        }
        return DEFAULT_FONT_SIZE; // базовый размер, если спан не найден
    }

    // ========== ПРЕОБРАЗОВАНИЕ HTML ⇄ SPANNABLE ==========

    public static SpannableString fromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return new SpannableString("");
        }

        // Разбиваем по <br> и обрабатываем каждую часть отдельно
        String[] parts = html.split("<br>");
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!part.trim().isEmpty()) {
                builder.append(processHtmlPart(part));
            }
            if (i < parts.length - 1) {
                builder.append("\n");
            }
        }

        return new SpannableString(builder);
    }


    private static SpannableString processHtmlPart(String html) {
        if (!html.contains("<font")) {
            // Обычный текст без тегов <font>
            return new SpannableString(Html.fromHtml(html).toString());
        }

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

    public static String toHtml(Spanned spanned) {
        String fullText = spanned.toString();
        int textLength = fullText.length();
        if (textLength == 0) return "";

        AbsoluteSizeSpan[] sizeSpans = spanned.getSpans(0, textLength, AbsoluteSizeSpan.class);
        Arrays.sort(sizeSpans, Comparator.comparingInt(spanned::getSpanStart));

        StringBuilder htmlBuilder = new StringBuilder();
        int currentPosition = 0;

        for (AbsoluteSizeSpan span : sizeSpans) {
            int spanStart = spanned.getSpanStart(span);
            int spanEnd = spanned.getSpanEnd(span);
            int fontSize = span.getSize();

            if (spanStart > currentPosition) {
                String beforeText = fullText.substring(currentPosition, spanStart);
                htmlBuilder.append(escapeHtml(beforeText).replace("\n", "<br>"));
            }

            String spanText = fullText.substring(spanStart, spanEnd);
            if (!spanText.isEmpty()) {
                htmlBuilder.append("<font size=\"")
                        .append(fontSize)
                        .append("\">")
                        .append(escapeHtml(spanText).replace("\n", "<br>"))
                        .append("</font>");
            }
            currentPosition = spanEnd;
        }

        if (currentPosition < textLength) {
            String afterText = fullText.substring(currentPosition);
            htmlBuilder.append(escapeHtml(afterText).replace("\n", "<br>"));
        }

        return htmlBuilder.toString();
    }


    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static boolean hasFontTags(String html) {
        return html != null && html.contains("<font");
    }

    // ========== РАБОТА СО СПИСКАМИ (ЧЕРЕЗ СПАНЫ) ==========

    public static void applyNumberedList(Editable editable, int start, int end) {
        List<ParagraphInfo> paragraphs = extractParagraphs(editable, start, end);
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            ParagraphInfo p = paragraphs.get(i);
            String paraText = editable.subSequence(p.start, p.end).toString();
            String cleanText = removeListMarkers(paraText);
            String newText = (i + 1) + ". " + cleanText;
            replaceTextPreservingSpans(editable, p.start, p.end, newText);
        }
    }


    public static void applyBulletedList(Editable editable, int start, int end) {
        List<ParagraphInfo> paragraphs = extractParagraphs(editable, start, end);
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            ParagraphInfo p = paragraphs.get(i);
            String paraText = editable.subSequence(p.start, p.end).toString();
            String cleanText = removeListMarkers(paraText);
            String newText = "• " + cleanText;
            replaceTextPreservingSpans(editable, p.start, p.end, newText);
        }
    }

    public static void removeListFormatting(Editable editable, int start, int end) {
        List<ParagraphInfo> paragraphs = extractParagraphs(editable, start, end);
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            ParagraphInfo p = paragraphs.get(i);
            String paraText = editable.subSequence(p.start, p.end).toString();
            String cleanText = removeListMarkers(paraText);
            replaceTextPreservingSpans(editable, p.start, p.end, cleanText);
        }
    }

    private static String removeListMarkers(String text) {
        // Удаляем нумерованные маркеры (цифра с точкой и пробелом)
        String result = text.replaceFirst("^\\s*\\d+\\.\\s+", "");
        // Удаляем маркированные маркеры
        result = result.replaceFirst("^\\s*[•\\-]\\s+", "");
        return result;
    }


    private static List<ParagraphInfo> extractParagraphs(Editable editable, int start, int end) {
        List<ParagraphInfo> list = new ArrayList<>();
        int current = start;
        while (current < end) {
            int paraStart = findParagraphStart(editable, current);
            int paraEnd = findParagraphEnd(editable, current);
            if (paraStart < start) paraStart = start;
            if (paraEnd > end) paraEnd = end;
            if (paraStart < paraEnd) {
                list.add(new ParagraphInfo(paraStart, paraEnd));
            }
            current = paraEnd + 1; // переходим к следующему абзацу (после \n)
            if (current >= editable.length()) break;
        }
        return list;
    }

    public static int findParagraphStart(CharSequence text, int position) {
        if (position <= 0) return 0;
        for (int i = position - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') return i + 1;
        }
        return 0;
    }

    public static int findParagraphEnd(CharSequence text, int position) {
        for (int i = position; i < text.length(); i++) {
            if (text.charAt(i) == '\n') return i;
        }
        return text.length();
    }

    private static class ParagraphInfo {
        int start, end;

        ParagraphInfo(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }


    private static void replaceTextPreservingSpans(Editable editable, int start, int end, String newText) {
        // Сохраняем все спаны размера, пересекающиеся с диапазоном [start, end)
        AbsoluteSizeSpan[] sizeSpans = editable.getSpans(start, end, AbsoluteSizeSpan.class);
        List<SpanData> spansData = new ArrayList<>();
        for (AbsoluteSizeSpan span : sizeSpans) {
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            int size = span.getSize();
            spansData.add(new SpanData(spanStart, spanEnd, size));
            editable.removeSpan(span);
        }

        // Выполняем замену текста
        editable.replace(start, end, newText);
        int delta = newText.length() - (end - start); // изменение длины

        // Восстанавливаем спаны с учётом смещения
        for (SpanData data : spansData) {
            int newSpanStart = data.start;
            int newSpanEnd = data.end;

            // Если спан начинался после start, смещаем
            if (data.start >= start) {
                newSpanStart = data.start + delta;
            }
            // Если спан заканчивался после start, смещаем
            if (data.end >= start) {
                newSpanEnd = data.end + delta;
            }

            // Ограничиваем границы новым диапазоном
            newSpanStart = Math.max(newSpanStart, start);
            newSpanEnd = Math.min(newSpanEnd, start + newText.length());

            if (newSpanStart < newSpanEnd) {
                AbsoluteSizeSpan newSpan = new AbsoluteSizeSpan(data.size, true);
                editable.setSpan(newSpan, newSpanStart, newSpanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
}