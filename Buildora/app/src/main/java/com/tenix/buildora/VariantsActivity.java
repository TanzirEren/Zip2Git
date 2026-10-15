package com.tenix.buildora;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.tenix.buildora.build.Keystores;
import com.tenix.buildora.core.BuildTypeCfg;
import com.tenix.buildora.core.FlavorCfg;
import com.tenix.buildora.core.FormDialog;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.UI;
import com.tenix.buildora.core.Variant;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Build types (debug / release / …), product flavors and the active variant. */
public class VariantsActivity extends BaseActivity {

    private Project project;
    private LinearLayout content;
    private boolean binding;

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
        TextView title = UI.label(this, "Build variants", 20, false);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        top.addView(title);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 56)));
        ScrollView sv = new ScrollView(this);
        content = UI.vbox(this);
        content.setPadding(UI.dp(this, 16), UI.dp(this, 4), UI.dp(this, 16), UI.dp(this, 32));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        styleBars();
        render();
    }

    private void save() {
        try { project.save(); } catch (Exception e) { UI.toast(this, e.getMessage()); }
    }

    private TextView section(String t) {
        TextView s = UI.label(this, t.toUpperCase(), 11, true);
        s.setTextColor(UI.color(this, R.attr.bAccent));
        s.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        s.setPadding(UI.dp(this, 4), UI.dp(this, 22), 0, UI.dp(this, 6));
        return s;
    }

    private LinearLayout card(String title, String sub, final Runnable onTap) { return card(title, sub, onTap, null); }

    private LinearLayout card(String title, String sub, final Runnable onTap, final Runnable onLong) {
        LinearLayout r = UI.vbox(this);
        r.setBackgroundResource(R.drawable.bg_card);
        r.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 6);
        r.setLayoutParams(lp);
        r.addView(UI.label(this, title, 15, false));
        r.addView(UI.label(this, sub, 12, true));
        r.setOnClickListener(v -> onTap.run());
        if (onLong != null) r.setOnLongClickListener(v -> { onLong.run(); return true; });
        return r;
    }

    private void render() {
        project.ensureDefaults();
        content.removeAllViews();

        // ---- active variant
        content.addView(section("Active variant (used by Run)"));
        final List<String> bts = new ArrayList<>();
        for (BuildTypeCfg b : project.buildTypes) bts.add(b.name);
        final List<String> fls = new ArrayList<>();
        fls.add("(no flavor)");
        for (FlavorCfg f : project.flavors) fls.add(f.name);
        final Spinner bs = new Spinner(this);
        bs.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, bts));
        bs.setSelection(Math.max(0, bts.indexOf(project.activeBuildType)));
        final Spinner fs = new Spinner(this);
        fs.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fls));
        fs.setSelection(project.activeFlavor.isEmpty() ? 0 : Math.max(0, fls.indexOf(project.activeFlavor)));
        content.addView(UI.label(this, "Build type", 12, true));
        content.addView(bs);
        content.addView(UI.label(this, "Flavor", 12, true));
        content.addView(fs);
        final TextView summary = UI.label(this, "", 12, true);
        summary.setPadding(0, UI.dp(this, 8), 0, 0);
        content.addView(summary);
        final Runnable upd = () -> {
            Variant v = Variant.resolve(project, project.activeBuildType, project.activeFlavor);
            summary.setText("→ " + v.name + "  •  " + v.applicationId + "  •  v" + v.versionName + " (" + v.versionCode + ")"
                    + (v.minify ? "  •  R8 on" : "") + "\nOutput: " + v.apkFile(project).getName());
        };
        AdapterView.OnItemSelectedListener l = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View view, int pos, long id) {
                if (binding) return;
                project.activeBuildType = bts.get(bs.getSelectedItemPosition());
                int fi = fs.getSelectedItemPosition();
                project.activeFlavor = fi <= 0 ? "" : fls.get(fi);
                save();
                upd.run();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        bs.setOnItemSelectedListener(l);
        fs.setOnItemSelectedListener(l);
        upd.run();

        // ---- build types
        content.addView(section("Build types"));
        for (final BuildTypeCfg b : project.buildTypes) {
            String sub = (b.debuggable ? "debuggable" : "not debuggable") + (b.minify ? " • R8 shrink" + (b.obfuscate ? " + obfuscate" : "") : " • no shrinking")
                    + (b.idSuffix.isEmpty() ? "" : " • id " + b.idSuffix)
                    + " • key: " + keyLabel(b.signingKey);
            content.addView(card(b.name, sub, () -> editBuildType(b, false), () -> deleteBuildType(b)));
        }
        TextView addBt = UI.button(this, "Add build type", false);
        addBt.setOnClickListener(v -> {
            BuildTypeCfg n = new BuildTypeCfg();
            n.name = "";
            editBuildType(n, true);
        });
        content.addView(addBt);

        // ---- flavors
        content.addView(section("Product flavors"));
        if (project.flavors.isEmpty()) content.addView(UI.label(this, "No flavors (long-press a card to delete it). Flavors let you build several versions of one app (for example free and pro) with different ids, code, resources and libraries.", 12, true));
        for (final FlavorCfg f : project.flavors) {
            String sub = (f.applicationId.isEmpty() ? project.packageName : f.applicationId) + f.idSuffix
                    + (f.dependencies.isEmpty() ? "" : " • " + f.dependencies.size() + " libs")
                    + "\nCode & resources: " + srcHint(f.name);
            content.addView(card(f.name, sub, () -> editFlavor(f, false), () -> deleteFlavor(f)));
        }
        TextView addFl = UI.button(this, "Add flavor", false);
        addFl.setOnClickListener(v -> { FlavorCfg n = new FlavorCfg(); n.name = ""; editFlavor(n, true); });
        content.addView(addFl);
        content.addView(UI.label(this, "Tip: put flavor-only files in app/src/<flavor>/java and app/src/<flavor>/res - they are added when that flavor builds, and "
                + "res files override the main ones. BuildConfig.FLAVOR, BUILD_TYPE, DEBUG, APPLICATION_ID and your own fields are generated for you.", 12, true));
    }

    private String srcHint(String name) {
        File main = project.srcRoot;
        if (main != null && main.getName().equals("main") && main.getParentFile() != null)
            return FileUtilRel(new File(main.getParentFile(), name));
        return "src/" + name;
    }

    private String FileUtilRel(File f) { return com.tenix.buildora.core.FileUtil.relPath(project.root, f); }

    private String keyLabel(String id) {
        if (id == null || id.isEmpty()) return "Settings key";
        if (id.equals("debug")) return "debug key";
        for (Keystores.Entry e : Keystores.list(this)) if (e.id.equals(id)) return e.name;
        return "Settings key";
    }

    private static final Pattern NAME = Pattern.compile("[a-z][A-Za-z0-9]*");

    private void editBuildType(final BuildTypeCfg b, final boolean isNew) {
        final List<String> keyIds = new ArrayList<>();
        final List<String> keyNames = new ArrayList<>();
        keyIds.add(""); keyNames.add("Key selected in Settings");
        keyIds.add("debug"); keyNames.add("Buildora debug key");
        for (Keystores.Entry e : Keystores.list(this)) { keyIds.add(e.id); keyNames.add(e.name); }
        FormDialog f = new FormDialog(this, isNew ? "New build type" : "Build type: " + b.name);
        if (isNew) f.text("name", "Name (letters, e.g. staging)", b.name);
        f.check("debuggable", "Debuggable", b.debuggable)
                .check("minify", "Shrink code with R8", b.minify)
                .check("obfuscate", "Obfuscate (rename classes)", b.obfuscate)
                .check("optimize", "Optimize", b.optimize)
                .text("idSuffix", "Application id suffix (e.g. .debug)", b.idSuffix)
                .text("versionSuffix", "Version name suffix (e.g. -debug)", b.versionSuffix)
                .choice("key", "Signing key", keyNames.toArray(new String[0]), Math.max(0, keyIds.indexOf(b.signingKey)))
                .multiline("fields", "BuildConfig fields, one per line", String.join("\n", b.buildConfigFields))
                .note("Field example:  String API_URL = \"https://example.com\"   or   boolean LOGGING = true")
                .show("Save", values -> {
                    if (isNew) {
                        String n = String.valueOf(values.get("name"));
                        if (!NAME.matcher(n).matches()) return "Use a name like staging (starts with a lowercase letter)";
                        if (project.findBuildType(n) != null) return "That name already exists";
                        b.name = n;
                    }
                    b.debuggable = (Boolean) values.get("debuggable");
                    b.minify = (Boolean) values.get("minify");
                    b.obfuscate = (Boolean) values.get("obfuscate");
                    b.optimize = (Boolean) values.get("optimize");
                    b.idSuffix = String.valueOf(values.get("idSuffix"));
                    b.versionSuffix = String.valueOf(values.get("versionSuffix"));
                    b.signingKey = keyIds.get((Integer) values.get("key"));
                    b.buildConfigFields = lines(String.valueOf(values.get("fields")));
                    if (isNew) project.buildTypes.add(b);
                    save();
                    render();
                    return null;
                });
    }

    private void editFlavor(final FlavorCfg fl, final boolean isNew) {
        FormDialog f = new FormDialog(this, isNew ? "New flavor" : "Flavor: " + fl.name);
        if (isNew) f.text("name", "Name (letters, e.g. free, pro)", fl.name);
        f.text("appId", "Application id (empty = same as project)", fl.applicationId)
                .text("idSuffix", "Application id suffix (e.g. .free)", fl.idSuffix)
                .text("versionSuffix", "Version name suffix", fl.versionSuffix)
                .number("versionCode", "Version code (0 = same as project)", fl.versionCode)
                .number("minSdk", "Min SDK (0 = same as project)", fl.minSdk)
                .multiline("deps", "Extra libraries (one coordinate per line)", String.join("\n", fl.dependencies))
                .multiline("fields", "BuildConfig fields, one per line", String.join("\n", fl.buildConfigFields))
                .show("Save", values -> {
                    if (isNew) {
                        String n = String.valueOf(values.get("name"));
                        if (!NAME.matcher(n).matches()) return "Use a name like free (starts with a lowercase letter)";
                        if (project.findFlavor(n) != null || project.findBuildType(n) != null) return "That name already exists";
                        fl.name = n;
                    }
                    fl.applicationId = String.valueOf(values.get("appId"));
                    fl.idSuffix = String.valueOf(values.get("idSuffix"));
                    fl.versionSuffix = String.valueOf(values.get("versionSuffix"));
                    fl.versionCode = (Integer) values.get("versionCode");
                    fl.minSdk = (Integer) values.get("minSdk");
                    fl.dependencies = lines(String.valueOf(values.get("deps")));
                    fl.buildConfigFields = lines(String.valueOf(values.get("fields")));
                    if (isNew) {
                        project.flavors.add(fl);
                        File main = project.srcRoot;
                        if (main != null && main.getName().equals("main") && main.getParentFile() != null) {
                            new File(main.getParentFile(), fl.name + "/java").mkdirs();
                            new File(main.getParentFile(), fl.name + "/res").mkdirs();
                        }
                    }
                    save();
                    render();
                    return null;
                });
    }

    private void deleteFlavor(final FlavorCfg fl) {
        UI.dialog(this).setTitle("Delete flavor '" + fl.name + "'?").setMessage("It is removed from the project; its source folder is kept.")
                .setNegativeButton("Keep", null).setPositiveButton("Delete", (d, w) -> {
                    project.flavors.remove(fl);
                    if (project.activeFlavor.equals(fl.name)) project.activeFlavor = "";
                    save();
                    render();
                }).show();
    }

    private void deleteBuildType(final BuildTypeCfg b) {
        if (b.name.equals("debug") || b.name.equals("release")) { UI.toast(this, "debug and release cannot be deleted"); return; }
        UI.dialog(this).setTitle("Delete build type '" + b.name + "'?").setNegativeButton("Keep", null).setPositiveButton("Delete", (d, w) -> {
            project.buildTypes.remove(b);
            if (project.activeBuildType.equals(b.name)) project.activeBuildType = "debug";
            save();
            render();
        }).show();
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        for (String l : text.split("\n")) if (!l.trim().isEmpty()) out.add(l.trim());
        return out;
    }
}
