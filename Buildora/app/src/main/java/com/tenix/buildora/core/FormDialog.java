package com.tenix.buildora.core;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.tenix.buildora.R;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds a simple settings form dialog: text, number, multi-line, switch, choice. */
public final class FormDialog {
    public interface OnOk {
        /** @return an error message to keep the dialog open, or null to accept */
        String onOk(Map<String, Object> values);
    }

    private final Context c;
    private final String title;
    private final LinearLayout box;
    private final Map<String, View> views = new LinkedHashMap<>();

    public FormDialog(Context c, String title) {
        this.c = c;
        this.title = title;
        box = UI.vbox(c);
        box.setPadding(UI.dp(c, 20), UI.dp(c, 4), UI.dp(c, 20), UI.dp(c, 8));
    }

    private void label(String text) {
        TextView t = UI.label(c, text, 12, true);
        t.setPadding(0, UI.dp(c, 12), 0, 0);
        box.addView(t);
    }

    public FormDialog note(String text) {
        TextView t = UI.label(c, text, 12, true);
        t.setPadding(0, UI.dp(c, 6), 0, UI.dp(c, 4));
        box.addView(t);
        return this;
    }

    public FormDialog text(String key, String label, String value) {
        label(label);
        EditText e = UI.input(c, label, value);
        box.addView(e);
        views.put(key, e);
        return this;
    }

    public FormDialog number(String key, String label, int value) {
        label(label);
        EditText e = UI.numberInput(c, label, value);
        box.addView(e);
        views.put(key, e);
        return this;
    }

    public FormDialog multiline(String key, String label, String value) {
        label(label);
        EditText e = UI.input(c, label, value);
        e.setSingleLine(false);
        e.setMinLines(3);
        e.setGravity(android.view.Gravity.TOP);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(e);
        views.put(key, e);
        return this;
    }

    public FormDialog check(String key, String label, boolean value) {
        Switch s = new Switch(c);
        s.setText(label);
        s.setTextColor(UI.color(c, R.attr.bText));
        s.setChecked(value);
        s.setPadding(0, UI.dp(c, 12), 0, UI.dp(c, 4));
        box.addView(s);
        views.put(key, s);
        return this;
    }

    public FormDialog choice(String key, String label, String[] options, int selected) {
        label(label);
        Spinner sp = new Spinner(c);
        sp.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, options));
        sp.setSelection(Math.max(0, Math.min(options.length - 1, selected)));
        box.addView(sp, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        views.put(key, sp);
        return this;
    }

    public void show(String okLabel, final OnOk cb) {
        ScrollView sv = new ScrollView(c);
        sv.addView(box);
        final AlertDialog d = UI.dialog(c).setTitle(title).setView(sv)
                .setPositiveButton(okLabel, null).setNegativeButton("Cancel", null).create();
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<String, View> e : views.entrySet()) {
                    View w = e.getValue();
                    if (w instanceof Switch) out.put(e.getKey(), ((Switch) w).isChecked());
                    else if (w instanceof Spinner) out.put(e.getKey(), ((Spinner) w).getSelectedItemPosition());
                    else if (w instanceof EditText) {
                        String s = ((EditText) w).getText().toString();
                        if ((((EditText) w).getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER) {
                            try { out.put(e.getKey(), Integer.parseInt(s.trim())); } catch (Exception ex) { out.put(e.getKey(), 0); }
                        } else out.put(e.getKey(), s.trim());
                    }
                }
                String err = cb.onOk(out);
                if (err == null) d.dismiss(); else UI.toast(c, err);
            }
        });
    }
}
