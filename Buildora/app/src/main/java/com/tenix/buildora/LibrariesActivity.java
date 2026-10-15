package com.tenix.buildora;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.tenix.buildora.build.Catalog;
import com.tenix.buildora.build.MavenResolver;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.FilePicker;
import com.tenix.buildora.core.FormDialog;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.UI;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-project library manager: catalog of 120+ libraries, Maven coordinates, local .jar/.aar files. */
public class LibrariesActivity extends BaseActivity {

    private Project project;
    private LinearLayout depList, localList;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String r = getIntent().getStringExtra("root");
        if (r == null) { finish(); return; }
        project = Project.load(new File(r));

        LinearLayout root = UI.vbox(this);
        root.setBackgroundColor(UI.color(this, R.attr.bBg));
        root.setFitsSystemWindows(true);
        LinearLayout top = UI.hbox(this);
        top.setPadding(UI.dp(this, 6), 0, UI.dp(this, 12), 0);
        ImageButton back = new ImageButton(this, null, 0, R.style.BIconBtn);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 56)));
        TextView title = UI.label(this, "Libraries", 20, false);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        top.addView(title);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = UI.vbox(this);
        body.setPadding(UI.dp(this, 16), UI.dp(this, 4), UI.dp(this, 16), UI.dp(this, 32));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        body.addView(UI.label(this, project.name + " • libraries come from Google Maven, Maven Central and JitPack. Use '+' (or no version) for the latest stable release. "
                + "Flavor-specific libraries are set in Variants.", 12, true));
        TextView cat = UI.button(this, "Browse catalog (" + Catalog.ALL.length + " libraries)", true);
        TextView custom = UI.button(this, "Add Maven coordinate", false);
        TextView local = UI.button(this, "Import .jar / .aar file", false);
        TextView dl = UI.button(this, "Download all now", false);
        body.addView(cat);
        body.addView(custom);
        body.addView(local);
        body.addView(dl);
        body.addView(sectionLabel("Dependencies"));
        depList = UI.vbox(this);
        body.addView(depList);
        body.addView(sectionLabel("Local libraries (libs folder)"));
        localList = UI.vbox(this);
        body.addView(localList);
        setContentView(root);
        styleBars();

        cat.setOnClickListener(v -> catalogDialog());
        custom.setOnClickListener(v -> customDialog());
        local.setOnClickListener(v -> importLocal());
        dl.setOnClickListener(v -> downloadNow());
        render();
    }

    private TextView sectionLabel(String t) {
        TextView s = UI.label(this, t.toUpperCase(Locale.ROOT), 11, true);
        s.setTextColor(UI.color(this, R.attr.bAccent));
        s.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        s.setPadding(UI.dp(this, 4), UI.dp(this, 22), 0, UI.dp(this, 6));
        return s;
    }

    private File libDir() {
        if (!project.libDirs.isEmpty()) return project.libDirs.get(0);
        File app = new File(project.root, "app");
        File d = app.isDirectory() ? new File(app, "libs") : new File(project.root, "libs");
        d.mkdirs();
        return d;
    }

    private void save() {
        try { project.save(); } catch (Exception e) { UI.toast(this, e.getMessage()); }
    }

    private static String ga(String coord) {
        String[] p = coord.trim().split(":");
        return p.length >= 2 ? p[0] + ":" + p[1] : coord;
    }

    private void render() {
        depList.removeAllViews();
        if (project.dependencies.isEmpty()) depList.addView(UI.label(this, "No dependencies yet.", 13, true));
        for (final String d : new ArrayList<>(project.dependencies)) {
            LinearLayout r = UI.hbox(this);
            r.setBackgroundResource(R.drawable.bg_card);
            r.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 6), UI.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UI.dp(this, 6);
            r.setLayoutParams(lp);
            TextView t = UI.label(this, d.split(":").length < 3 ? d + ":+" : d, 13, false);
            t.setTypeface(Typeface.MONOSPACE);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            r.setOnClickListener(v -> editVersion(d));
            ImageButton x = new ImageButton(this, null, 0, R.style.BIconBtn);
            x.setImageResource(R.drawable.ic_close);
            x.setOnClickListener(v -> { project.dependencies.remove(d); save(); render(); });
            r.addView(x, new LinearLayout.LayoutParams(UI.dp(this, 36), UI.dp(this, 36)));
            depList.addView(r);
        }
        localList.removeAllViews();
        boolean any = false;
        for (File dir : project.libDirs) {
            File[] fs = dir.listFiles();
            if (fs == null) continue;
            for (final File f : fs) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".jar") && !n.endsWith(".aar")) continue;
                any = true;
                LinearLayout r = UI.hbox(this);
                r.setBackgroundResource(R.drawable.bg_card);
                r.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 6), UI.dp(this, 10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = UI.dp(this, 6);
                r.setLayoutParams(lp);
                r.addView(UI.label(this, f.getName() + "  •  " + FileUtil.human(f.length()), 13, false),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                ImageButton x = new ImageButton(this, null, 0, R.style.BIconBtn);
                x.setImageResource(R.drawable.ic_delete);
                x.setOnClickListener(v -> { f.delete(); render(); });
                r.addView(x, new LinearLayout.LayoutParams(UI.dp(this, 36), UI.dp(this, 36)));
                localList.addView(r);
            }
        }
        if (!any) localList.addView(UI.label(this, "No local .jar / .aar files.", 13, true));
    }

    private boolean addDependency(String coord) {
        coord = coord.trim();
        String[] p = coord.split(":");
        if (p.length < 2) { UI.toast(this, "Use group:artifact or group:artifact:version"); return false; }
        String key = ga(coord);
        for (int i = 0; i < project.dependencies.size(); i++) {
            if (ga(project.dependencies.get(i)).equals(key)) { project.dependencies.set(i, coord); save(); render(); return true; }
        }
        project.dependencies.add(coord);
        save();
        render();
        return true;
    }

    private boolean isAdded(Catalog.Lib l) {
        if (l.coords.length == 0) return false;
        for (String c : l.coords) {
            boolean has = false;
            for (String d : project.dependencies) if (ga(d).equals(ga(c))) has = true;
            if (!has) return false;
        }
        return true;
    }

    private void editVersion(final String dep) {
        final String[] p = dep.split(":");
        if (p.length < 2) return;
        new FormDialog(this, ga(dep)).text("v", "Version (use + for latest stable)", p.length >= 3 ? p[2] : "+")
                .show("Save", values -> {
                    String v = String.valueOf(values.get("v")).trim();
                    if (v.isEmpty()) v = "+";
                    int i = project.dependencies.indexOf(dep);
                    if (i >= 0) project.dependencies.set(i, p[0] + ":" + p[1] + ":" + v);
                    save();
                    render();
                    return null;
                });
    }

    // ------------------------------------------------------------------ dialogs

    private void customDialog() {
        new FormDialog(this, "Add dependency").text("c", "group:artifact:version", "")
                .note("Example: com.google.code.gson:gson:2.10.1  •  version is optional: group:artifact means latest stable.")
                .show("Add", values -> addDependency(String.valueOf(values.get("c"))) ? null : "Invalid coordinate");
    }

    private void catalogDialog() {
        LinearLayout box = UI.vbox(this);
        box.setPadding(UI.dp(this, 16), UI.dp(this, 4), UI.dp(this, 16), 0);
        final EditText search = UI.input(this, "Search " + Catalog.ALL.length + " libraries…", "");
        box.addView(search);
        final List<String> groups = new ArrayList<>();
        groups.add("All groups");
        for (String g : Catalog.GROUPS) groups.add(g);
        final Spinner gs = new Spinner(this);
        gs.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, groups));
        box.addView(gs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final ListView lv = new ListView(this);
        lv.setDivider(null);
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 380)));
        final List<Catalog.Lib> shown = new ArrayList<>();
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) {
                Catalog.Lib l = shown.get(i);
                LinearLayout r = UI.vbox(LibrariesActivity.this);
                r.setPadding(UI.dp(LibrariesActivity.this, 6), UI.dp(LibrariesActivity.this, 9), UI.dp(LibrariesActivity.this, 6), UI.dp(LibrariesActivity.this, 9));
                String badge = isAdded(l) ? "   ✓ added" : (l.status != Catalog.OK ? "   · " + Catalog.statusLabel(l.status) : "");
                TextView name = UI.label(LibrariesActivity.this, l.name + badge, 15, false);
                if (isAdded(l)) name.setTextColor(UI.color(LibrariesActivity.this, R.attr.bAccent));
                r.addView(name);
                r.addView(UI.label(LibrariesActivity.this, l.desc, 12, true));
                if (l.coords.length > 0) {
                    TextView c = UI.label(LibrariesActivity.this, l.coords.length == 1 ? l.coords[0] : l.coords[0] + "  (+" + (l.coords.length - 1) + " more)", 11, true);
                    c.setTypeface(Typeface.MONOSPACE);
                    r.addView(c);
                }
                return r;
            }
        };
        lv.setAdapter(ad);
        final Runnable filter = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int gi = gs.getSelectedItemPosition();
            shown.clear();
            for (Catalog.Lib l : Catalog.ALL) {
                if (gi > 0 && !l.group.equals(Catalog.GROUPS[gi - 1])) continue;
                String hay = (l.name + " " + l.desc + " " + l.group + " " + String.join(" ", l.coords)).toLowerCase(Locale.ROOT);
                if (q.isEmpty() || hay.contains(q)) shown.add(l);
            }
            ad.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { filter.run(); }
        });
        gs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { filter.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        filter.run();
        final AlertDialog dlg = UI.dialog(this).setTitle("Library catalog").setView(box).setNegativeButton("Close", null).create();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            Catalog.Lib l = shown.get(pos);
            pick(l, ad);
        });
        dlg.show();
    }

    private void pick(final Catalog.Lib l, final BaseAdapter ad) {
        if (!l.addable()) {
            AlertDialog.Builder b = UI.dialog(this).setTitle(l.name).setMessage(l.note).setPositiveButton("OK", null);
            if (l.status == Catalog.MANUAL) b.setNeutralButton("Import .jar / .aar", (d, w) -> importLocal());
            b.show();
            return;
        }
        Runnable add = () -> {
            for (String c : l.coords) addDependency(c);
            UI.toast(this, "Added " + l.name);
            ad.notifyDataSetChanged();
        };
        if (l.status == Catalog.PROCESSOR) {
            UI.dialog(this).setTitle(l.name).setMessage(l.note + "\n\nAdd it anyway?").setNegativeButton("Cancel", null)
                    .setPositiveButton("Add", (d, w) -> add.run()).show();
        } else {
            add.run();
            if (!l.note.isEmpty()) UI.toastLong(this, l.note);
        }
    }

    private void importLocal() {
        FilePicker.pickFile(this, "Select .jar or .aar", android.os.Environment.getExternalStorageDirectory(),
                new String[]{"jar", "aar"}, f -> {
                    try {
                        FileUtil.copyFile(f, FileUtil.unique(new File(libDir(), f.getName())));
                        project.detectLayout();
                        render();
                        UI.toast(this, "Imported " + f.getName());
                    } catch (Exception e) { UI.toast(this, "Failed: " + e.getMessage()); }
                });
    }

    private void downloadNow() {
        if (project.dependencies.isEmpty()) { UI.toast(this, "Nothing to download"); return; }
        LinearLayout box = UI.vbox(this);
        box.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), 0);
        box.addView(new ProgressBar(this));
        final TextView log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(10);
        log.setTextColor(UI.color(this, R.attr.bTextDim));
        final ScrollView sv = new ScrollView(this);
        sv.addView(log);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 240)));
        final AlertDialog dlg = UI.dialog(this).setTitle("Downloading libraries").setView(box).setPositiveButton("Close", null).create();
        dlg.show();
        final StringBuilder sb = new StringBuilder();
        new Thread(() -> {
            String result;
            try {
                MavenResolver r = new MavenResolver(new File(getFilesDir(), "m2"), s -> runOnUiThread(() -> {
                    sb.append(s).append('\n');
                    log.setText(sb);
                    sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
                }));
                int n = r.resolve(project.dependencies).size();
                result = "Done: " + n + " artifacts ready";
            } catch (Exception e) { result = "Failed: " + e.getMessage(); }
            final String res = result;
            runOnUiThread(() -> { sb.append('\n').append(res); log.setText(sb); dlg.setTitle(res); });
        }).start();
    }
}
