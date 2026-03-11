package com.example.notes;

import android.util.Log;

import org.apache.poi.xwpf.usermodel.*;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class DocxBlockImporter {

    public static List<ContentBlock> importFromDocx(String filePath) {
        List<ContentBlock> blocks = new ArrayList<>();
        Log.d("ImportDebug", "Начало импорта из: " + filePath);

        try {
            XWPFDocument document = new XWPFDocument(new FileInputStream(filePath));

            // Получение всех элементов тела документа в правильном порядке
            List<IBodyElement> bodyElements = document.getBodyElements();
            Log.d("ImportDebug", "Всего элементов в документе: " + bodyElements.size());

            for (IBodyElement element : bodyElements) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    String text = paragraph.getText();

                    // Проверка: не является ли параграф пустым или служебным
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

    // Метод для фильтрации пользовательских параграфов
    private static boolean isUserParagraph(XWPFParagraph paragraph) {
        String text = paragraph.getText();

        // Дополнительные проверки при необходимости
        return true;
    }

    // Метод конвертации таблицы
    private static TableBlock convertTableToTableBlock(XWPFTable table) {
        try {
            int rowCount = table.getNumberOfRows();
            int colCount = getMaxColumnCount(table);

            Log.d("ImportDebug", "Создание TableBlock: " + rowCount + "x" + colCount);

            TableBlock tableBlock = new TableBlock(rowCount, colCount);

            // Заполнение данными из таблицы
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
            // Возврат пустой таблицы в случае ошибки
            return new TableBlock(1, 1);
        }
    }

    // Метод для определения максимального количества столбцов
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
                        // Если у нас есть накопленный текст, то добавить его
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
                            // Закрытие предыдущего тега
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
                        // Если предыдущий текст заканчивался пробелом, не добавлять
                        if (lastWasSpace && text.startsWith(" ")) {
                            text = text.substring(1);
                        }

                        accumulatedText += text;
                        lastWasSpace = text.endsWith(" ");
                    }
                }

                // Проверка breaks
                if (run.getCTR() != null && run.getCTR().getBrList() != null &&
                        !run.getCTR().getBrList().isEmpty()) {
                    // Если есть накопленный текст то он добавляется
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

            // Добавление оставшегося текста
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

            // Закрытие последнего тега, если он открыт
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
                // Для обычного текста замена переносов строк на <br>
                if (plainText.contains("\n")) {
                    String htmlWithBreaks = plainText.replace("\n", "<br>");
                    textBlock.setRawContent(htmlWithBreaks);
                    Log.d("ImportDebug", "Создан обычный текстовый блок с <br>: '" + htmlWithBreaks + "'");
                } else {
                    textBlock.setRawContent(plainText);
                    Log.d("ImportDebug  ", "Создан обычный текстовый блок: '" + plainText + "'");
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

        // Заменена специальных символов HTML, кроме <br>
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}