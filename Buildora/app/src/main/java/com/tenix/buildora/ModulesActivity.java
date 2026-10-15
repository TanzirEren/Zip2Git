package com.tenix.buildora;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.FormDialog;
import com.tenix.buildora.core.ModuleCfg;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.UI;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Multi-module projects: add library modules, set what the app and each module depend on. */
public class ModulesActivity extends BaseActivity {

    private Project project;
    private LinearLayout content;

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
        TextView title = UI.label(this, "Modules", 20, false);
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

    private LinearLayout card(String title, String sub, Runnable tap) {
        LinearLayout r = UI.vbox(this);
        r.setBackgroundResource(R.drawable.bg_card);
        r.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 6);
        r.setLayoutParams(lp);
        r.addView(UI.label(this, title, 15, false));
        r.addView(UI.label(this, sub, 12, true));
        if (tap != null) r.setOnClickListener(v -> tap.run());
        return r;
    }

    private void render() {
        project.ensureDefaults();
        content.removeAllViews();
        content.addView(UI.label(this, "A project can have extra modules next to the app. Library modules are compiled together with the app: their code, resources and manifest "
                + "are merged into it (the app's resources win). Tap a module to edit it.", 12, true));
        content.addView(card("app  (application)", project.packageName + "\nuses: " + (project.appModuleDeps.isEmpty() ? "no modules" : String.join(", ", project.appModuleDeps)),
                this::editAppDeps));
        for (final ModuleCfg m : project.modules) {
            String sub = (m.isAndroidLibrary() ? "Android library" : "Java library") + " • folder " + m.path
                    + "\nuses modules: " + (m.moduleDeps.isEmpty() ? "none" : String.join(", ", m.moduleDeps))
                    + (m.dependencies.isEmpty() ? "" : "  •  " + m.dependencies.size() + " libs")
                    + (project.appModuleDeps.contains(m.name) ? "" : "\n(not used by the app yet)");
            content.addView(card(m.name, sub, () -> editModule(m)));
        }
        TextView add = UI.button(this, "Add module", true);
        add.setOnClickListener(v -> addModule());
        content.addView(add);
    }

    private void editAppDeps() {
        if (project.modules.isEmpty()) { UI.toast(this, "Add a module first"); return; }
        final String[] names = new String[project.modules.size()];
        final boolean[] on = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = project.modules.get(i).name;
            on[i] = project.appModuleDeps.contains(names[i]);
        }
        UI.dialog(this).setTitle("The app uses these modules").setMultiChoiceItems(names, on, (d, which, checked) -> on[which] = checked)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
                    project.appModuleDeps.clear();
                    for (int i = 0; i < names.length; i++) if (on[i]) project.appModuleDeps.add(names[i]);
                    save();
                    render();
                }).show();
    }

    private void editModule(final ModuleCfg m) {
        String[] items = {"Edit libraries / usage", "Modules this module depends on", "Delete module"};
        UI.dialog(this).setTitle(m.name).setItems(items, (d, which) -> {
            if (which == 0) editBasics(m);
            else if (which == 1) pickModuleDeps(m);
            else deleteModule(m);
        }).show();
    }

    private void editBasics(final ModuleCfg m) {
        new FormDialog(this, "Module: " + m.name)
                .note("Folder: " + m.path + "  •  " + (m.isAndroidLibrary() ? "Android library" : "Java library"))
                .check("use", "The app uses this module", project.appModuleDeps.contains(m.name))
                .multiline("deps", "Libraries of this module (one coordinate per line)", String.join("\n", m.dependencies))
                .show("Save", values -> {
                    if ((Boolean) values.get("use")) { if (!project.appModuleDeps.contains(m.name)) project.appModuleDeps.add(m.name); }
                    else project.appModuleDeps.remove(m.name);
                    List<String> deps = new ArrayList<>();
                    for (String l : String.valueOf(values.get("deps")).split("\n")) if (!l.trim().isEmpty()) deps.add(l.trim());
                    m.dependencies = deps;
                    save();
                    render();
                    return null;
                });
    }

    private void pickModuleDeps(final ModuleCfg m) {
        final List<ModuleCfg> others = new ArrayList<>();
        for (ModuleCfg o : project.modules) if (o != m) others.add(o);
        if (others.isEmpty()) { UI.toast(this, "There is no other module yet"); return; }
        final String[] names = new String[others.size()];
        final boolean[] on = new boolean[names.length];
        for (int i = 0; i < names.length; i++) { names[i] = others.get(i).name; on[i] = m.moduleDeps.contains(names[i]); }
        UI.dialog(this).setTitle(m.name + " uses").setMultiChoiceItems(names, on, (d, which, checked) -> on[which] = checked)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
                    m.moduleDeps.clear();
                    for (int i = 0; i < names.length; i++) if (on[i]) m.moduleDeps.add(names[i]);
                    save();
                    render();
                }).show();
    }

    private void deleteModule(final ModuleCfg m) {
        UI.dialog(this).setTitle("Remove module '" + m.name + "'?")
                .setMessage("It is removed from the project. Its folder '" + m.path + "' is kept on disk.")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (d, w) -> {
                    project.modules.remove(m);
                    project.appModuleDeps.remove(m.name);
                    for (ModuleCfg o : project.modules) o.moduleDeps.remove(m.name);
                    save();
                    render();
                }).show();
    }

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

    private void addModule() {
        new FormDialog(this, "New module").text("name", "Module name (lowercase, e.g. core)", "")
                .choice("type", "Type", new String[]{"Android library (code + resources)", "Java library (code only)"}, 0)
                .show("Create", values -> {
                    String name = String.valueOf(values.get("name"));
                    if (!NAME.matcher(name).matches()) return "Use lowercase letters, digits and _ (start with a letter)";
                    if (project.findModule(name) != null || new File(project.root, name).exists() || name.equals(project.moduleDir().getName()))
                        return "A module or folder with that name already exists";
                    boolean lib = (Integer) values.get("type") == 0;
                    try {
                        createModule(name, lib);
                    } catch (Exception e) {
                        return "Failed: " + e.getMessage();
                    }
                    render();
                    return null;
                });
    }

    private void createModule(String name, boolean androidLib) throws Exception {
        ModuleCfg m = new ModuleCfg(name, androidLib ? ModuleCfg.LIBRARY : ModuleCfg.JAVA);
        String pkg = project.packageName + "." + name.replace("_", "");
        File main = new File(project.root, name + "/src/main");
        File javaDir = new File(main, "java/" + pkg.replace('.', '/'));
        javaDir.mkdirs();
        String cls = Character.toUpperCase(name.charAt(0)) + name.substring(1).replace("_", "");
        if (androidLib) {
            FileUtil.writeText(new File(main, "AndroidManifest.xml"),
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    package=\"" + pkg + "\">\n</manifest>\n");
            FileUtil.writeText(new File(main, "res/values/strings.xml"),
                    "<resources>\n    <string name=\"" + name + "_name\">" + cls + "</string>\n</resources>\n");
            FileUtil.writeText(new File(javaDir, cls + "Lib.java"),
                    "package " + pkg + ";\n\nimport android.content.Context;\n\npublic class " + cls + "Lib {\n\n    public static String hello(Context c) {\n        return c.getString(R.string." + name + "_name);\n    }\n}\n");
        } else {
            FileUtil.writeText(new File(javaDir, cls + "Lib.java"),
                    "package " + pkg + ";\n\npublic class " + cls + "Lib {\n\n    public static String hello() {\n        return \"" + cls + "\";\n    }\n}\n");
        }
        FileUtil.writeText(new File(project.root, name + "/build.gradle"),
                androidLib ? "plugins {\n    id 'com.android.library'\n}\n\nandroid {\n    namespace '" + pkg + "'\n    compileSdk 34\n    defaultConfig { minSdk " + project.minSdk + " }\n}\n"
                        : "plugins {\n    id 'java-library'\n}\n");
        File sg = new File(project.root, "settings.gradle");
        String text = sg.isFile() ? FileUtil.readText(sg) : "";
        if (!text.contains("':" + name + "'")) FileUtil.writeText(sg, text + (text.endsWith("\n") || text.isEmpty() ? "" : "\n") + "include ':" + name + "'\n");
        project.modules.add(m);
        project.appModuleDeps.add(name);
        m.detect(project.root);
        save();
        UI.toast(this, "Module '" + name + "' created. Use " + pkg + "." + cls + "Lib from your app.");
    }
}
