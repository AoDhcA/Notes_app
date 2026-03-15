package com.example.notes;

import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.content.Context;

public class TextBlock extends ContentBlock {

    private String htmlContent;

    public TextBlock() {
        super();
        this.type = BlockType.TEXT;
        this.htmlContent = "";
    }

    public TextBlock(String content) {
        this();
        setRawContent(content);
    }

    @Override
    public String getRawContent() {
        return htmlContent;
    }

    @Override
    public void setRawContent(String content) {
        this.htmlContent = content != null ? content : "";
    }

    public void setHtmlContent(String html) {
        this.htmlContent = html;
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public SpannableString toSpannable() {
        return TextFormattingHelper.fromHtml(htmlContent);
    }

    public void updateFromSpannable(Spanned spanned) {
        this.htmlContent = TextFormattingHelper.toHtml(spanned);
    }

    public boolean hasRealFormatting() {
        return TextFormattingHelper.hasFontTags(htmlContent);
    }

    public String getPlainText() {
        return Html.fromHtml(htmlContent).toString();
    }

    @Override
    public View createView(Context context) {
        EditText editText = new EditText(context);
        editText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Устанавливаем текст с форматированием (используем новый метод toSpannable)
        editText.setText(toSpannable());

        editText.setHint("Введите текст...");
        editText.setMinHeight(150);
        editText.setGravity(Gravity.TOP);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, TextFormattingHelper.DEFAULT_FONT_SIZE);

        editText.setTag("TextBlock_" + this.id);

        return editText;
    }

    @Override
    public void updateFromView(View view) {
        if (view instanceof EditText) {
            EditText editText = (EditText) view;
            updateFromSpannable(editText.getText());
        }
    }
}