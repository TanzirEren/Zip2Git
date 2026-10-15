package com.tenix.buildora;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.ProguardPresets;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.UI;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Edit proguard-rules.pro with one-tap presets for common libraries. */
public class ProguardActivity extends BaseActivity {

    private Project project;
    private EditText editor;
    private File file;
    private final Map<String, CheckBox> boxes = new HashMap<>();
    private boolean updating;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String r = getIntent().getStringExtra("root");
        if (r == null) { finish(); return; }
        project = Project.load(new File(r));
        file = project.proguardFile();

        LinearLayout root = UI.vbox(this);
        root.setBackgroundColor(UI.color(this, R.attr.bBg));
        root.setFitsSystemWindows(true);
        LinearLayout top = UI.hbox(this);
        top.setPadding(UI.dp(this, 6), 0, UI.dp(this, 12), 0);
        ImageButton back = new ImageButton(this, null, 0, R.style.BIconBtn);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 56)));
        TextView title = UI.label(this, "ProGuard rules", 20, false);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton save = new ImageButton(this, null, 0, R.style.BIconBtn);
        save.setImageResource(R.drawable.ic_save);
        save.setOnClickListener(v -> save());
        top.addView(save, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 56)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = UI.vbox(this);
        body.setPadding(UI.dp(this, 16), UI.dp(this, 4), UI.dp(this, 16), UI.dp(this, 32));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        body.addView(UI.label(this, "Used by build types with 'Shrink code (R8)' on. File: " + FileUtil.relPath(project.root, file)
                + "\nBuildora already applies Android defaults and the rules that ship inside your libraries.", 12, true));

        String text = ProguardPresets.FILE_HEADER;
        try { if (file.isFile()) text = FileUtil.readText(file); } catch (Exception ignored) { }

        TextView ph = UI.label(this, "PRESETS", 11, true);
        ph.setTextColor(UI.color(this, R.attr.bAccent));
        ph.setPadding(UI.dp(this, 4), UI.dp(this, 18), 0, UI.dp(this, 4));
        body.addView(ph);
        editor = new EditText(this);
        for (final ProguardPresets.Preset p : ProguardPresets.ALL) {
            LinearLayout row = UI.vbox(this);
            row.setPadding(0, UI.dp(this, 4), 0, UI.dp(this, 4));
            CheckBox cb = new CheckBox(this);
            cb.setText(p.title);
            cb.setTextColor(UI.color(this, R.attr.bText));
            cb.setTextSize(15);
            cb.setChecked(ProguardPresets.isApplied(text, p.id));
            row.addView(cb);
            TextView d = UI.label(this, p.desc, 12, true);
            d.setPadding(UI.dp(this, 32), 0, 0, 0);
            row.addView(d);
            body.addView(row);
            boxes.put(p.id, cb);
            cb.setOnCheckedChangeListener((v, checked) -> {
                if (updating) return;
                String t = editor.getText().toString();
                t = checked ? ProguardPresets.apply(t, p.id, project.packageName) : ProguardPresets.remove(t, p.id);
                updating = true;
                editor.setText(t);
                updating = false;
            });
        }
        TextView fh = UI.label(this, "RULES FILE", 11, true);
        fh.setTextColor(UI.color(this, R.attr.bAccent));
        fh.setPadding(UI.dp(this, 4), UI.dp(this, 18), 0, UI.dp(this, 4));
        body.addView(fh);
        editor.setText(text);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(12);
        editor.setTextColor(UI.color(this, R.attr.bText));
        editor.setBackgroundResource(R.drawable.bg_input);
        editor.setPadding(UI.dp(this, 12), UI.dp(this, 10), UI.dp(this, 12), UI.dp(this, 10));
        editor.setMinLines(12);
        editor.setGravity(android.view.Gravity.TOP);
        editor.setHorizontallyScrolling(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        body.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView saveBtn = UI.button(this, "Save rules", true);
        saveBtn.setOnClickListener(v -> save());
        body.addView(saveBtn);
        setContentView(root);
        styleBars();
    }

    private void save() {
        try {
            FileUtil.writeText(file, editor.getText().toString());
            for (Map.Entry<String, CheckBox> e : boxes.entrySet()) {
                updating = true;
                e.getValue().setChecked(ProguardPresets.isApplied(editor.getText().toString(), e.getKey()));
                updating = false;
            }
            UI.toast(this, "Saved " + file.getName());
        } catch (Exception e) {
            UI.toast(this, "Save failed: " + e.getMessage());
        }
    }

    @Override protected void onPause() {
        super.onPause();
        try { FileUtil.writeText(file, editor.getText().toString()); } catch (Exception ignored) { }
    }
}
