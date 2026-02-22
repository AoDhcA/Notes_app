package com.example.notes;

//import static com.google.android.material.internal.ViewUtils.dpToPx;

import static androidx.core.util.TypedValueCompat.dpToPx;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Button;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

public class TableBlock extends ContentBlock {
    private int rows;
    private int cols;
    private String[][] data;
    private String caption;
    private Context context; // ДОБАВЛЯЕМ: сохраняем контекст

    private OnTableDeleteListener onTableDeleteListener;

    public TableBlock(int rows, int cols) {
        super();
        this.type = BlockType.TABLE;
        this.rows = rows;
        this.cols = cols;
        this.data = new String[rows][cols];
        this.caption = "Таблица";

        // Инициализация массива с пустыми строками
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data[i][j] = "";
            }
        }
    }

    // метод для установки контекста
    public void setContext(Context context) {
        this.context = context;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    // метод для получения данных ячейки
    public String getCellData(int row, int col) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            return data[row][col] != null ? data[row][col] : "";
        }
        return "";
    }
    // метод для установки данных ячейки
    public void setCellData(int row, int col, String value) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            data[row][col] = value != null ? value : "";
        }
    }

    @Override
    public String getRawContent() {
        return serializeToJson();
    }

    @Override
    public void setRawContent(String content) {
        deserializeFromJson(content);
    }

    @Override
    public View createView(Context context) {
        this.context = context; // Сохраняем контекст при создании View
        return createTableView(context);
    }

    @Override
    public void updateFromView(View view) {
        // ТЕПЕРЬ VIEW - ЭТО CardView, НУЖНО НАЙТИ TableLayout ВНУТРИ НЕГО
        TableLayout tableLayout = findTableLayoutInCardView(view);
        if (tableLayout != null) {
            updateFromTableView(tableLayout);
        } else {
            Log.e("TableBlock", "TableLayout not found in CardView");
        }
    }

    // ДОБАВЛЯЕМ: метод для поиска TableLayout внутри CardView
    private TableLayout findTableLayoutInCardView(View cardView) {
        if (cardView instanceof CardView) {
            // CardView -> LinearLayout (первый дочерний элемент)
            ViewGroup cardViewGroup = (ViewGroup) cardView;
            if (cardViewGroup.getChildCount() > 0) {
                View linearLayout = cardViewGroup.getChildAt(0);
                if (linearLayout instanceof LinearLayout) {
                    LinearLayout linear = (LinearLayout) linearLayout;
                    // Ищем HorizontalScrollView
                    for (int i = 0; i < linear.getChildCount(); i++) {
                        View child = linear.getChildAt(i);
                        if (child instanceof HorizontalScrollView) {
                            HorizontalScrollView scrollView = (HorizontalScrollView) child;
                            // В HorizontalScrollView ищем TableLayout
                            if (scrollView.getChildCount() > 0) {
                                View tableLayout = scrollView.getChildAt(0);
                                if (tableLayout instanceof TableLayout) {
                                    return (TableLayout) tableLayout;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private TableLayout findTableLayout(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TableLayout) {
                return (TableLayout) child;
            }
            if (child instanceof LinearLayout) {
                TableLayout nested = findTableLayout((LinearLayout) child);//заменить обратно
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String serializeToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("Table:").append(rows).append("x").append(cols).append("\n");
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (data[i][j] != null) {
                    sb.append(data[i][j]).append("\t");
                } else {
                    sb.append("\t");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void deserializeFromJson(String content) {
        if (content.startsWith("Table:")) {
            String[] lines = content.split("\n");
            if (lines.length > 1) {
                for (int i = 1; i < lines.length && i-1 < rows; i++) {
                    String[] cells = lines[i].split("\t");
                    for (int j = 0; j < cells.length && j < cols; j++) {
                        data[i-1][j] = cells[j].isEmpty() ? null : cells[j];
                    }
                }
            }
        }
    }

    // ДОБАВЛЯЕМ: ссылки на View из XML
    private EditText captionEditText;
    private TableLayout tableLayout;
    private TextView editTableButton;
    private TextView deleteTableButton;

    public View createTableView(Context context) {
        this.context = context;

        // ИСПОЛЬЗУЕМ LAYOUT INFLATER ДЛЯ ЗАГРУЗКИ XML
        LayoutInflater inflater = LayoutInflater.from(context);
        View tableBlockView = inflater.inflate(R.layout.customtableblock, null);

        // НАХОДИМ ЭЛЕМЕНТЫ ИЗ XML
        captionEditText = tableBlockView.findViewById(R.id.captionEditText);
        tableLayout = tableBlockView.findViewById(R.id.tableLayout);
        editTableButton = tableBlockView.findViewById(R.id.editTableButton);
        deleteTableButton = tableBlockView.findViewById(R.id.deleteTableButton);

        // НАСТРАИВАЕМ ЭЛЕМЕНТЫ
        if (captionEditText != null) {
            captionEditText.setText(caption != null ? caption : "Заголовок таблицы");
            captionEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    caption = s.toString();
                }
            });
        }

        // СОЗДАЕМ СТРУКТУРУ ТАБЛИЦЫ
        createTableStructure(tableLayout, context);

        // НАСТРАИВАЕМ КНОПКУ РЕДАКТИРОВАНИЯ
        if (editTableButton != null) {
            editTableButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTableEditDialog();
                }
            });
        }

        if (deleteTableButton != null) {
            deleteTableButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDeleteConfirmationDialog();
                }
            });
        }

        return tableBlockView;
    }

    // ДОБАВЛЯЕМ: метод для показа диалога подтверждения удаления
    private void showDeleteConfirmationDialog() {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;

            Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_confirm_delete);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams params = window.getAttributes();
                params.gravity = Gravity.CENTER;
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
                window.setAttributes(params);
            }

            Button btnNo = dialog.findViewById(R.id.btnNo);
            Button btnYes = dialog.findViewById(R.id.btnYes);

            btnNo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            btnYes.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onTableDeleteListener != null) {
                        onTableDeleteListener.onTableDelete(TableBlock.this);
                    }
                    dialog.dismiss();
                }
            });

            dialog.show();
        }
    }

    // ДОБАВЛЯЕМ: метод для показа диалога редактирования
    private void showTableEditDialog() {
        if (context instanceof TextEditorActivity) {
            ((TextEditorActivity) context).showTableEditDialog(this);
        }
    }

    public void resizeTable(int newRows, int newCols) {
        if (newRows <= 0 || newCols <= 0) return;

        // СОХРАНЯЕМ СТАРЫЕ ДАННЫЕ
        String[][] newData = new String[newRows][newCols];

        // КОПИРУЕМ СУЩЕСТВУЮЩИЕ ДАННЫЕ
        for (int i = 0; i < Math.min(rows, newRows); i++) {
            for (int j = 0; j < Math.min(cols, newCols); j++) {
                newData[i][j] = data[i][j];
            }
        }

        // ЗАПОЛНЯЕМ НОВЫЕ ЯЧЕЙКИ ПУСТЫМИ СТРОКАМИ
        for (int i = 0; i < newRows; i++) {
            for (int j = 0; j < newCols; j++) {
                if (newData[i][j] == null) {
                    newData[i][j] = "";
                }
            }
        }

        // ОБНОВЛЯЕМ ДАННЫЕ
        this.rows = newRows;
        this.cols = newCols;
        this.data = newData;
    }

    private void createTableStructure(TableLayout tableLayout, Context context) {
        int minCellWidthPx = dpToPx(102); // 120dp минимальная ширина

        for (int i = 0; i < rows; i++) {
            TableRow row = new TableRow(context);
            for (int j = 0; j < cols; j++) {
                EditText cell = createCell(context, false);

                TableRow.LayoutParams params = new TableRow.LayoutParams(
                        TableRow.LayoutParams.WRAP_CONTENT,
                        TableRow.LayoutParams.WRAP_CONTENT);
                params.weight = 1f;
                cell.setLayoutParams(params);

                // УСТАНАВЛИВАЕМ МИНИМАЛЬНУЮ ШИРИНУ НЕПОСРЕДСТВЕННО ДЛЯ View
                cell.setMinimumWidth(minCellWidthPx);

                cell.setBackgroundResource(R.drawable.cell_border);
                cell.setPadding(8, 8, 8, 8);

                if (data[i][j] != null) {
                    cell.setText(data[i][j]);
                }
                cell.setHint(i + "," + j);

                final int finalI = i;
                final int finalJ = j;
                cell.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        data[finalI][finalJ] = s.toString();
                    }
                });

                row.addView(cell);
            }
            tableLayout.addView(row);
        }
    }

    // метод для конвертации dp в пиксели
    private int dpToPx(int dp) {
        if (context == null) return dp;
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    // ИСПРАВЛЯЕМ: передаем контекст как параметр
    private EditText createCell(Context context, boolean isHeader) {
        EditText cell = new EditText(context);
        // Дополнительные настройки ячейки если нужно
        return cell;
    }

    private void updateFromTableView(TableLayout tableLayout) {
        for (int i = 0; i < tableLayout.getChildCount(); i++) {
            TableRow row = (TableRow) tableLayout.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                EditText cell = (EditText) row.getChildAt(j);
                if (i < rows && j < cols) {
                    data[i][j] = cell.getText().toString();
                }
            }
        }
    }

    // ДОБАВЛЯЕМ: интерфейс для удаления таблицы
    public interface OnTableDeleteListener {
        void onTableDelete(TableBlock tableBlock);
    }

    public void setOnTableDeleteListener(OnTableDeleteListener listener) {
        this.onTableDeleteListener = listener;
    }
}