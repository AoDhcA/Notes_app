package com.example.notes;

import android.text.Html;
import android.util.Log;

import org.apache.poi.xwpf.usermodel.*;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocxBlockImporter {

    public static List<ContentBlock> importFromDocx(String filePath) {
        List<ContentBlock> blocks = new ArrayList<>();
        Log.d("ImportDebug", "Начало импорта из: " + filePath);

        try {
            XWPFDocument document = new XWPFDocument(new FileInputStream(filePath));

            // ВАЖНО: получаем все элементы тела документа в правильном порядке
            List<IBodyElement> bodyElements = document.getBodyElements();
            Log.d("ImportDebug", "Всего элементов в документе: " + bodyElements.size());

            for (IBodyElement element : bodyElements) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    String text = paragraph.getText();

                    // Проверяем, не является ли параграф пустым или служебным
                    if (isUserParagraph(paragraph)) {
                        TextBlock textBlock = convertParagraphToTextBlock(paragraph);
                        blocks.add(textBlock);
                        Log.d("ImportDebug", "Импорт текстового блока: '" + text + "'");
                    }
                } else if (element instanceof XWPFTable) {
                    XWPFTable table = (XWPFTable) element;
                    Log.d("ImportDebug", "Найдена таблица с " + table.getNumberOfRows() + " строками");
                    TableBlock tableBlock = convertTableToTableBlock(table);
                    blocks.add(tableBlock);
                }
            }

            document.close();
        } catch (Exception e) {
            Log.e("ImportDebug", "Ошибка импорта: " + e.getMessage(), e);
            e.printStackTrace();
        }

        Log.d("ImportDebug", "Импортировано блоков: " + blocks.size());
        return blocks;
    }

    // ДОБАВЛЯЕМ: метод для фильтрации пользовательских параграфов
    private static boolean isUserParagraph(XWPFParagraph paragraph) {
        String text = paragraph.getText();

        // Дополнительные проверки при необходимости
        return true;
    }

    // ДОБАВЛЯЕМ: метод конвертации таблицы
    private static TableBlock convertTableToTableBlock(XWPFTable table) {
        try {
            int rowCount = table.getNumberOfRows();
            int colCount = getMaxColumnCount(table);

            Log.d("ImportDebug", "Создание TableBlock: " + rowCount + "x" + colCount);

            TableBlock tableBlock = new TableBlock(rowCount, colCount);

            // Заполняем данными из таблицы
            for (int i = 0; i < rowCount; i++) {
                XWPFTableRow row = table.getRow(i);
                if (row != null) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int j = 0; j < cells.size() && j < colCount; j++) {
                        String cellText = cells.get(j).getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            tableBlock.setCellData(i, j, cellText);
                            Log.d("ImportDebug", "Ячейка [" + i + "," + j + "]: " + cellText);
                        }
                    }
                }
            }

            return tableBlock;

        } catch (Exception e) {
            Log.e("ImportDebug", "Ошибка конвертации таблицы: " + e.getMessage(), e);
            // Возвращаем пустую таблицу в случае ошибки
            return new TableBlock(1, 1);
        }
    }

    // ДОБАВЛЯЕМ: метод для определения максимального количества столбцов
    private static int getMaxColumnCount(XWPFTable table) {
        int maxCols = 0;
        for (XWPFTableRow row : table.getRows()) {
            if (row != null) {
                int cellCount = row.getTableCells().size();
                if (cellCount > maxCols) {
                    maxCols = cellCount;
                }
            }
        }
        return maxCols;
    }


//    private static TextBlock convertParagraphToTextBlock(XWPFParagraph paragraph) {
//        try {
//            // Сначала получаем весь текст параграфа с учетом breaks
//            StringBuilder plainTextBuilder = new StringBuilder();
//            List<XWPFRun> runs = paragraph.getRuns();
//
//            Log.d("ImportDebug", "Конвертация параграфа с " + runs.size() + " runs");
//
//            for (int i = 0; i < runs.size(); i++) {
//                XWPFRun run = runs.get(i);
//                String text = run.getText(0);
//
//                if (text != null) {
//                    plainTextBuilder.append(text);
//                }
//
//                // Проверяем, есть ли break после этого run
//                if (run.getCTR() != null && run.getCTR().getBrList() != null && !run.getCTR().getBrList().isEmpty()) {
//                    plainTextBuilder.append("\n");
//                    Log.d("ImportDebug", "Добавлен перенос строки после run " + i);
//                }
//            }
//
//            String plainText = plainTextBuilder.toString();
//            Log.d("ImportDebug", "Полный текст параграфа: '" + plainText.replace("\n", "\\n") + "'");
//
//            if (runs.isEmpty()) {
//                TextBlock textBlock = new TextBlock(plainText);
//                Log.d("ImportDebug", "Создан текстовый блок без форматирования: '" + plainText + "'");
//                return textBlock;
//            }
//
//            Set<Integer> fontSizes = new HashSet<>();
//            boolean hasFormatting = false;
//            StringBuilder htmlBuilder = new StringBuilder();
//
//            for (int i = 0; i < runs.size(); i++) {
//                XWPFRun run = runs.get(i);
//                String text = run.getText(0);
//
//                if (text != null && !text.isEmpty()) {
//                    int fontSize = run.getFontSize();
//                    int fontSizeSp = (fontSize == -1) ? 16 : fontSize / 2;
//
//                    Log.d("ImportDebug", "Run текст: '" + text + "', размер: " + fontSize + " -> " + fontSizeSp + "sp");
//
//                    fontSizes.add(fontSizeSp);
//
//                    if (fontSizeSp != 16) {
//                        hasFormatting = true;
//                        htmlBuilder.append("<font size=\"")
//                                .append(fontSizeSp)
//                                .append("\">")
//                                .append(escapeHtml(text))
//                                .append("</font>");
//                    } else {
//                        htmlBuilder.append(escapeHtml(text));
//                    }
//                }
//
//                // Добавляем <br> если есть break
//                if (run.getCTR() != null && run.getCTR().getBrList() != null && !run.getCTR().getBrList().isEmpty()) {
//                    htmlBuilder.append("<br>");
//                    Log.d("ImportDebug", "Добавлен <br> после run " + i);
//                }
//            }
//
//            // ПРОВЕРЯЕМ РЕАЛЬНОЕ ФОРМАТИРОВАНИЕ
//            boolean realFormatting = hasFormatting;
//
//            TextBlock textBlock = new TextBlock();
//            if (realFormatting) {
//                String html = htmlBuilder.toString();
//                textBlock.setHtmlContent(html);
//                Log.d("ImportDebug", "Создан форматированный текстовый блок: " + html);
//            } else {
//                // Для обычного текста заменяем переносы строк на <br>
//                if (plainText.contains("\n")) {
//                    String html = plainText.replace("\n", "<br>");
//                    textBlock.setRawContent(html);
//                    Log.d("ImportDebug", "Создан обычный текстовый блок с <br>: '" + html + "'");
//                } else {
//                    textBlock.setRawContent(plainText);
//                    Log.d("ImportDebug", "Создан обычный текстовый блок: '" + plainText + "'");
//                }
//            }
//
//            return textBlock;
//
//        } catch (Exception e) {
//            Log.e("ImportDebug", "Ошибка конвертации параграфа", e);
//            return new TextBlock(paragraph.getText());
//        }
//    }

//    private static TextBlock convertParagraphToTextBlock(XWPFParagraph paragraph) {
//        try {
//            StringBuilder plainTextBuilder = new StringBuilder();
//            StringBuilder htmlBuilder = new StringBuilder();
//            List<XWPFRun> runs = paragraph.getRuns();
//
//            Log.d("ImportDebug", "Конвертация параграфа с " + runs.size() + " runs");
//
//            int currentFontSize = -1;
//            boolean inFontTag = false;
//            String accumulatedText = "";
//
//            for (int i = 0; i < runs.size(); i++) {
//                XWPFRun run = runs.get(i);
//                String text = run.getText(0);
//
//                if (text != null && !text.isEmpty()) {
//                    int fontSize = run.getFontSize();
//                    int fontSizeSp = (fontSize == -1) ? 16 : fontSize / 2;
//
//                    Log.d("ImportDebug", "Run текст: '" + text + "', размер: " + fontSize + " -> " + fontSizeSp + "sp");
//
//                    // Если размер шрифта изменился
//                    if (fontSizeSp != currentFontSize) {
//                        // Если у нас есть накопленный текст, добавляем его
//                        if (!accumulatedText.isEmpty()) {
//                            if (inFontTag && currentFontSize != 16) {
//                                htmlBuilder.append("<font size=\"")
//                                        .append(currentFontSize)
//                                        .append("\">")
//                                        .append(escapeHtml(accumulatedText))
//                                        .append("</font>");
//                            } else {
//                                htmlBuilder.append(escapeHtml(accumulatedText));
//                            }
//                            plainTextBuilder.append(accumulatedText);
//                            accumulatedText = "";
//                        }
//
//                        if (inFontTag) {
//                            // Закрываем предыдущий тег
//                            htmlBuilder.append("</font>");
//                            inFontTag = false;
//                        }
//
//                        if (fontSizeSp != 16) {
//                            currentFontSize = fontSizeSp;
//                            inFontTag = true;
//                        } else {
//                            currentFontSize = -1;
//                            inFontTag = false;
//                        }
//                    }
//
//                    // Добавляем текст к накопленному
//                    accumulatedText += text;
//                }
//
//                // Проверяем breaks
//                if (run.getCTR() != null && run.getCTR().getBrList() != null &&
//                        !run.getCTR().getBrList().isEmpty()) {
//                    // Если есть накопленный текст, добавляем его
//                    if (!accumulatedText.isEmpty()) {
//                        if (inFontTag && currentFontSize != 16) {
//                            htmlBuilder.append("<font size=\"")
//                                    .append(currentFontSize)
//                                    .append("\">")
//                                    .append(escapeHtml(accumulatedText))
//                                    .append("</font>");
//                        } else {
//                            htmlBuilder.append(escapeHtml(accumulatedText));
//                        }
//                        plainTextBuilder.append(accumulatedText);
//                        accumulatedText = "";
//                    }
//
//                    htmlBuilder.append("<br>");
//                    plainTextBuilder.append("\n");
//                    Log.d("ImportDebug", "Добавлен <br> после run " + i);
//                }
//            }
//
//            // Добавляем оставшийся текст
//            if (!accumulatedText.isEmpty()) {
//                if (inFontTag && currentFontSize != 16) {
//                    htmlBuilder.append("<font size=\"")
//                            .append(currentFontSize)
//                            .append("\">")
//                            .append(escapeHtml(accumulatedText))
//                            .append("</font>");
//                } else {
//                    htmlBuilder.append(escapeHtml(accumulatedText));
//                }
//                plainTextBuilder.append(accumulatedText);
//            }
//
//            // Закрываем последний тег, если он открыт
//            if (inFontTag) {
//                htmlBuilder.append("</font>");
//            }
//
//            String plainText = plainTextBuilder.toString();
//            String html = htmlBuilder.toString();
//
//            Log.d("ImportDebug", "Полный текст параграфа: '" + plainText.replace("\n", "\\n") + "'");
//            Log.d("ImportDebug", "HTML: " + html);
//
//            TextBlock textBlock = new TextBlock();
//
//            if (html.contains("<font") || html.contains("<br>")) {
//                textBlock.setHtmlContent(html);
//                Log.d("ImportDebug", "Создан форматированный текстовый блок: " + html);
//            } else {
//                // Для обычного текста заменяем переносы строк на <br>
//                if (plainText.contains("\n")) {
//                    String htmlWithBreaks = plainText.replace("\n", "<br>");
//                    textBlock.setRawContent(htmlWithBreaks);
//                    Log.d("ImportDebug", "Создан обычный текстовый блок с <br>: '" + htmlWithBreaks + "'");
//                } else {
//                    textBlock.setRawContent(plainText);
//                    Log.d("ImportDebug", "Создан обычный текстовый блок: '" + plainText + "'");
//                }
//            }
//
//            return textBlock;
//
//        } catch (Exception e) {
//            Log.e("ImportDebug", "Ошибка конвертации параграфа", e);
//            return new TextBlock(paragraph.getText());
//        }
//    }

    private static TextBlock convertParagraphToTextBlock(XWPFParagraph paragraph) {
        try {
            StringBuilder plainTextBuilder = new StringBuilder();
            StringBuilder htmlBuilder = new StringBuilder();
            List<XWPFRun> runs = paragraph.getRuns();

            Log.d("ImportDebug", "Конвертация параграфа с " + runs.size() + " runs");

            int currentFontSize = -1;
            boolean inFontTag = false;
            String accumulatedText = "";
            boolean lastWasSpace = false;

            for (int i = 0; i < runs.size(); i++) {
                XWPFRun run = runs.get(i);
                String text = run.getText(0);

                if (text != null && !text.isEmpty()) {
                    int fontSize = run.getFontSize();
                    int fontSizeSp = (fontSize == -1) ? 16 : fontSize / 2;

                    Log.d("ImportDebug", "Run текст: '" + text + "', размер: " + fontSize + " -> " + fontSizeSp + "sp");

                    // Если размер шрифта изменился
                    if (fontSizeSp != currentFontSize) {
                        // Если у нас есть накопленный текст, добавляем его
                        if (!accumulatedText.isEmpty()) {
                            if (inFontTag && currentFontSize != 16) {
                                htmlBuilder.append("<font size=\"")
                                        .append(currentFontSize)
                                        .append("\">")
                                        .append(escapeHtml(accumulatedText))
                                        .append("</font>");
                            } else {
                                htmlBuilder.append(escapeHtml(accumulatedText));
                            }
                            plainTextBuilder.append(accumulatedText);
                            accumulatedText = "";
                            lastWasSpace = false;
                        }

                        if (inFontTag) {
                            // Закрываем предыдущий тег
                            htmlBuilder.append("</font>");
                            inFontTag = false;
                        }

                        if (fontSizeSp != 16) {
                            currentFontSize = fontSizeSp;
                            inFontTag = true;
                        } else {
                            currentFontSize = -1;
                            inFontTag = false;
                        }
                    }

                    // Добавляем текст к накопленному с учетом пробелов
                    if (!text.isEmpty()) {
                        // Если предыдущий текст заканчивался пробелом, не добавляем лишний
                        if (lastWasSpace && text.startsWith(" ")) {
                            text = text.substring(1);
                        }

                        accumulatedText += text;
                        lastWasSpace = text.endsWith(" ");
                    }
                }

                // Проверяем breaks
                if (run.getCTR() != null && run.getCTR().getBrList() != null &&
                        !run.getCTR().getBrList().isEmpty()) {
                    // Если есть накопленный текст, добавляем его
                    if (!accumulatedText.isEmpty()) {
                        if (inFontTag && currentFontSize != 16) {
                            htmlBuilder.append("<font size=\"")
                                    .append(currentFontSize)
                                    .append("\">")
                                    .append(escapeHtml(accumulatedText))
                                    .append("</font>");
                        } else {
                            htmlBuilder.append(escapeHtml(accumulatedText));
                        }
                        plainTextBuilder.append(accumulatedText);
                        accumulatedText = "";
                        lastWasSpace = false;
                    }

                    htmlBuilder.append("<br>");
                    plainTextBuilder.append("\n");
                    Log.d("ImportDebug", "Добавлен <br> после run " + i);
                }
            }

            // Добавляем оставшийся текст
            if (!accumulatedText.isEmpty()) {
                if (inFontTag && currentFontSize != 16) {
                    htmlBuilder.append("<font size=\"")
                            .append(currentFontSize)
                            .append("\">")
                            .append(escapeHtml(accumulatedText))
                            .append("</font>");
                } else {
                    htmlBuilder.append(escapeHtml(accumulatedText));
                }
                plainTextBuilder.append(accumulatedText);
            }

            // Закрываем последний тег, если он открыт
            if (inFontTag) {
                htmlBuilder.append("</font>");
            }

            String plainText = plainTextBuilder.toString();
            String html = htmlBuilder.toString();

            Log.d("ImportDebug", "Полный текст параграфа: '" + plainText.replace("\n", "\\n") + "'");
            Log.d("ImportDebug", "HTML: " + html);

            TextBlock textBlock = new TextBlock();

            if (html.contains("<font") || html.contains("<br>")) {
                textBlock.setHtmlContent(html);
                Log.d("ImportDebug", "Создан форматированный текстовый блок: " + html);
            } else {
                // Для обычного текста заменяем переносы строк на <br>
                if (plainText.contains("\n")) {
                    String htmlWithBreaks = plainText.replace("\n", "<br>");
                    textBlock.setRawContent(htmlWithBreaks);
                    Log.d("ImportDebug", "Создан обычный текстовый блок с <br>: '" + htmlWithBreaks + "'");
                } else {
                    textBlock.setRawContent(plainText);
                    Log.d("ImportDebug", "Создан обычный текстовый блок: '" + plainText + "'");
                }
            }

            return textBlock;

        } catch (Exception e) {
            Log.e("ImportDebug", "Ошибка конвертации параграфа", e);
            return new TextBlock(paragraph.getText());
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";

        // Заменяем специальные символы HTML, но не трогаем <br>
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

//    private static boolean containsLineBreaks(List<XWPFRun> runs) {
//        for (XWPFRun run : runs) {
//            String text = run.getText(0);
//            if (text != null && text.contains("\n")) {
//                return true;
//            }
//        }
//        return false;
//    }
}