package com.example.notes;

import android.text.Html;
import android.util.Log;

import org.apache.poi.xwpf.usermodel.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

public class DocxBlockExporter {
    public static void exportToDocx(List<ContentBlock> blocks, String filePath) {

        Log.d("ExportDebug", "Начало экспорта в: " + filePath);
        Log.d("ExportDebug", "Количество блоков: " + blocks.size());

        try {
            XWPFDocument document = new XWPFDocument();

            for (int i = 0; i < blocks.size(); i++) {
                ContentBlock block = blocks.get(i);
                Log.d("ExportDebug", "Экспорт блока " + i + ": " + block.getType() + ", содержимое: '" + block.getRawContent() + "'");

                switch (block.getType()) {
                    case TEXT:
                        // СОХРАНЯЕМ ДАЖЕ ПУСТЫЕ ТЕКСТОВЫЕ БЛОКИ
                        addTextBlockToDocument(document, (TextBlock) block);
                        break;
                    case TABLE:
                        addTableBlockToDocument(document, (TableBlock) block);
                        break;
                }
            }

            FileOutputStream out = new FileOutputStream(filePath);
            document.write(out);
            out.close();
            document.close();

            Log.d("ExportDebug", "Экспорт успешно завершен");

        } catch (Exception e) {
            Log.e("ExportDebug", "Ошибка экспорта: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private static void addTextBlockToDocument(XWPFDocument document, TextBlock textBlock) {
        XWPFParagraph paragraph = document.createParagraph();

        // ВАЖНО: Логируем что получаем от TextBlock
        String content = textBlock.getRawContent();
        Log.d("ExportDebug", "🎯 Экспорт TextBlock, getRawContent(): " + content);
        Log.d("ExportDebug", "🎯 TextBlock.hasRealFormatting(): " + textBlock.hasRealFormatting());

        if (content != null && content.contains("<font")) {
            Log.d("ExportDebug", "🎯 Экспортируем с форматированием");
            exportFormattedText(paragraph, content);
        } else {
            // Обычный текст
            XWPFRun run = paragraph.createRun();
            run.setText(content != null ? content : "");
            Log.d("ExportDebug", "🎯 Сохранен обычный текстовый блок: '" + content + "'");
        }
    }

    //    private static void exportFormattedText(XWPFParagraph paragraph, String htmlContent) {
//        Log.d("ExportDebug", "Экспорт HTML: " + htmlContent);
//        try {
//            // Декодируем HTML-сущности
//            String decodedHtml = htmlContent
//                    .replace("&lt;", "<")
//                    .replace("&gt;", ">")
//                    .replace("&amp;", "&")
//                    .replace("&quot;", "\"")
//                    .replace("&#39;", "'");
//
//            // Теперь обрабатываем <br> как переносы строк
//            String[] lines = decodedHtml.split("<br>");
//
//            for (int i = 0; i < lines.length; i++) {
//                String line = lines[i];
//                if (!line.trim().isEmpty()) {
//                    // Парсим форматирование для каждой строки
//                    List<TextSegment> segments = parseHtmlSegments(line);
//
//                    for (TextSegment segment : segments) {
//                        XWPFRun run = paragraph.createRun();
//                        run.setText(segment.text);
//
//                        if (segment.fontSize != -1) {
//                            run.setFontSize(segment.fontSize * 2);
//                        }
//                    }
//
//                    // Добавляем перенос строки, если это не последняя строка
//                    if (i < lines.length - 1) {
//                        paragraph.createRun().addBreak();
//                    }
//                }
//            }
//
//        } catch (Exception e) {
//            Log.e("ExportFormat", "Ошибка экспорта форматированного текста", e);
//            XWPFRun run = paragraph.createRun();
//            run.setText(Html.fromHtml(htmlContent).toString());
//        }
//    }
    private static void exportFormattedText(XWPFParagraph paragraph, String htmlContent) {
        Log.d("ExportDebug", "Экспорт HTML: " + htmlContent);
        try {
            // ВАЖНО: Декодируем HTML-сущности перед парсингом
            String decodedHtml = htmlContent
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'");

            Log.d("ExportDebug", "Декодированный HTML: " + decodedHtml);
            //String[] lines = decodedHtml.split("<br>");

            // Используем более надежный парсер HTML
            List<TextSegment> segments = parseHtmlWithLineBreaks(decodedHtml);

            for (TextSegment segment : segments) {
                if (segment.isLineBreak) {
                    paragraph.createRun().addBreak();
                } else if (!segment.text.isEmpty()) {
                    XWPFRun run = paragraph.createRun();
                    run.setText(segment.text);
                    if (segment.fontSize != -1) {
                        run.setFontSize(segment.fontSize * 2);
                    }
                }
            }

        } catch (Exception e) {
            Log.e("ExportFormat", "Ошибка экспорта форматированного текста", e);
            // Фолбэк: сохраняем как обычный текст
            XWPFRun run = paragraph.createRun();
            run.setText(Html.fromHtml(htmlContent).toString());
        }
    }

    private static List<TextSegment> parseHtmlWithLineBreaks(String html) {
        List<TextSegment> segments = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return segments;
        }

        Log.d("ExportDebug", "Парсинг HTML с переносами: " + html);

        // Улучшенное регулярное выражение для поиска всех элементов
        // Оно ищет: 1) теги <font> с содержимым, 2) теги <br>, 3) произвольный текст
        Pattern pattern = Pattern.compile("(<font\\s[^>]*>.*?</font>|<br>|\\s+|[^<\\s]+(?:\\s+[^<\\s]+)*)");
        Matcher matcher = pattern.matcher(html);

        int lastEnd = 0;

        while (matcher.find()) {
            String match = matcher.group();

            if (match.startsWith("<font")) {
                // Обработка тега <font>
                Pattern fontPattern = Pattern.compile("<font\\s+size=\"(\\d+)\">(.*?)</font>");
                Matcher fontMatcher = fontPattern.matcher(match);

                if (fontMatcher.find()) {
                    int fontSize = Integer.parseInt(fontMatcher.group(1));
                    String content = fontMatcher.group(2);

                    // Разделяем содержимое тега <font> по <br>
                    String[] parts = content.split("<br>");

                    for (int i = 0; i < parts.length; i++) {
                        if (!parts[i].isEmpty()) {
                            String text = decodeHtmlEntities(parts[i]);
                            segments.add(new TextSegment(text, fontSize));
                        }

                        // Добавляем перенос строки между частями (кроме последней)
                        if (i < parts.length - 1) {
                            segments.add(new TextSegment("", -1, true)); // isLineBreak = true
                        }
                    }
                }
            } else if (match.equals("<br>")) {
                // Отдельный тег <br> (между тегами <font> или текстом)
                segments.add(new TextSegment("", -1, true)); // isLineBreak = true
            } else if (match.trim().isEmpty()) {
                // Пробелы (один или несколько)
                // Если предыдущий сегмент не был пробелом, добавляем один пробел
                if (segments.size() > 0) {
                    TextSegment lastSegment = segments.get(segments.size() - 1);
                    if (!lastSegment.text.endsWith(" ") && !lastSegment.isLineBreak) {
                        segments.add(new TextSegment(" ", -1));
                    }
                }
            } else {
                // Обычный текст без тегов
                String text = decodeHtmlEntities(match);
                if (!text.trim().isEmpty()) {
                    segments.add(new TextSegment(text, -1));
                }
            }

            lastEnd = matcher.end();
        }

        // Обрабатываем оставшийся текст, если есть
        if (lastEnd < html.length()) {
            String remaining = html.substring(lastEnd);
            if (!remaining.trim().isEmpty()) {
                String text = decodeHtmlEntities(remaining);
                segments.add(new TextSegment(text, -1));
            }
        }

        Log.d("ExportDebug", "Создано сегментов: " + segments.size());
        return segments;
    }

    private static void processTextWithoutFont(String text, List<TextSegment> segments) {
        if (text == null || text.isEmpty()) return;

        String[] parts = text.split("<br>");

        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                String decoded = decodeHtmlEntities(parts[i]);
                segments.add(new TextSegment(decoded, -1));
            }

            if (i < parts.length - 1) {
                segments.add(new TextSegment("", -1, true));
            }
        }
    }

    private static void processFontContent(String content, int fontSize, List<TextSegment> segments) {
        // Разделяем по <br> внутри тега <font>
        String[] parts = content.split("<br>");

        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                // Декодируем HTML-сущности
                String text = decodeHtmlEntities(parts[i]);
                segments.add(new TextSegment(text, fontSize));
            }

            // Добавляем перенос строки после каждой части, кроме последней
            if (i < parts.length - 1) {
                segments.add(new TextSegment("", -1, true)); // isLineBreak = true
            }
        }
    }

//    private static List<TextSegment> parseFontSegments(String html) {
//        List<TextSegment> segments = new ArrayList<>();
//        Pattern pattern = Pattern.compile("(<font size=\"(\\d+)\">(.*?)</font>)|([^<]+)");
//        Matcher matcher = pattern.matcher(html);
//
//        while (matcher.find()) {
//            if (matcher.group(1) != null) {
//                int fontSize = Integer.parseInt(matcher.group(2));
//                String content = matcher.group(3);
//                String plainContent = Html.fromHtml(content).toString();
//                if (!plainContent.isEmpty()) {
//                    segments.add(new TextSegment(plainContent, fontSize));
//                }
//            } else if (matcher.group(4) != null) {
//                String content = matcher.group(4);
//                String plainContent = Html.fromHtml(content).toString();
//                if (!plainContent.trim().isEmpty()) {
//                    segments.add(new TextSegment(plainContent, -1));
//                }
//            }
//        }
//
//        return segments;
//    }

    private static List<TextSegment> parseHtmlSegments(String html) {
        List<TextSegment> segments = new ArrayList<>();

        if (html == null || html.isEmpty()) {
            return segments;
        }

        Log.d("ExportDebug", "Парсинг HTML: " + html);

        Pattern pattern = Pattern.compile("(<font size=\"(\\d+)\">(.*?)</font>)|([^<]+)");
        Matcher matcher = pattern.matcher(html);

        int lastPosition = 0;

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                int fontSize = Integer.parseInt(matcher.group(2));
                String content = matcher.group(3);

                // Декодируем HTML-сущности в содержимом тега
                String plainContent = decodeHtmlEntities(content);

                if (!plainContent.isEmpty()) {
                    segments.add(new TextSegment(plainContent, fontSize));
                    Log.d("ExportDebug", "Добавлен текст в теге: '" + plainContent + "' с размером " + fontSize);
                }
            } else if (matcher.group(4) != null) {
                String content = matcher.group(4);
                String plainContent = decodeHtmlEntities(content);

                if (!plainContent.trim().isEmpty()) {
                    segments.add(new TextSegment(plainContent, -1));
                    Log.d("ExportDebug", "Добавлен текст вне тега: '" + plainContent + "'");
                }
            }

            lastPosition = matcher.end();
        }

        if (lastPosition < html.length()) {
            String remaining = html.substring(lastPosition);
            String plainRemaining = decodeHtmlEntities(remaining);
            if (!plainRemaining.trim().isEmpty()) {
                segments.add(new TextSegment(plainRemaining, -1));
                Log.d("ExportDebug", "Добавлен оставшийся текст: '" + plainRemaining + "'");
            }
        }

        Log.d("ExportDebug", "Всего сегментов: " + segments.size());
        return segments;
    }

//    private static String decodeHtmlEntities(String text) {
//        if (text == null) return "";
//
//        return text.replace("&lt;", "<")
//                .replace("&gt;", ">")
//                .replace("&amp;", "&")
//                .replace("&quot;", "\"")
//                .replace("&#39;", "'")
//                .replace("<br>", "\n");
//    }
//    private static String decodeHtmlEntities(String text) {
//        if (text == null) return "";
//
//        return text.replace("&lt;", "<")
//                .replace("&gt;", ">")
//                .replace("&amp;", "&")
//                .replace("&quot;", "\"")
//                .replace("&#39;", "'");
//    }

    private static String decodeHtmlEntities(String text) {
        if (text == null) return "";

        // Декодируем HTML-сущности
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("<br>", "\n")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .trim();
    }

    private static void addTableBlockToDocument(XWPFDocument document, TableBlock tableBlock) {
        try {
            Log.d("ExportDebug", "Создание таблицы: " + tableBlock.getRows() + "x" + tableBlock.getCols());

            XWPFTable table = document.createTable(tableBlock.getRows(), tableBlock.getCols());

            for (int i = 0; i < tableBlock.getRows(); i++) {
                for (int j = 0; j < tableBlock.getCols(); j++) {
                    String cellText = tableBlock.getCellData(i, j);
                    Log.d("ExportDebug", "Ячейка [" + i + "," + j + "]: " + cellText);

                    if (i < table.getRows().size() && j < table.getRow(i).getTableCells().size()) {
                        table.getRow(i).getCell(j).setText(cellText != null ? cellText : "");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ExportDebug", "Ошибка создания таблицы: " + e.getMessage(), e);
        }
    }

    //    private static class TextSegment {
//        String text;
//        int fontSize; // -1 для текста без форматирования
//
//        TextSegment(String text, int fontSize) {
//            this.text = text;
//            this.fontSize = fontSize;
//        }
//    }
    private static class TextSegment {
        String text;
        int fontSize; // -1 для текста без форматирования
        boolean isLineBreak;

        TextSegment(String text, int fontSize) {
            this(text, fontSize, false);
        }

        TextSegment(String text, int fontSize, boolean isLineBreak) {
            this.text = text;
            this.fontSize = fontSize;
            this.isLineBreak = isLineBreak;
        }
    }
}