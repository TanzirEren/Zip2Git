package com.tenix.buildora;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.tenix.buildora.build.Keystores;
import com.tenix.buildora.build.Toolchain;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.FilePicker;
import com.tenix.buildora.core.Net;
import com.tenix.buildora.core.Perm;
import com.tenix.buildora.core.Prefs;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.UI;
import com.tenix.buildora.editor.EditorScheme;

import java.io.File;

public class SettingsActivity extends BaseActivity {

    private LinearLayout content;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = UI.vbox(this);
        root.setBackgroundColor(UI.color(this, R.attr.bBg));
        root.setFitsSystemWindows(true);

        LinearLayout top = UI.hbox(this);
        top.setPadding(UI.dp(this, 6), 0, UI.dp(this, 12), 0);
        ImageButton back = new ImageButton(this, null, 0, R.style.BIconBtn);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(UI.dp(this, 48), UI.dp(this, 56)));
        TextView title = UI.label(this, "Settings", 20, false);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        top.addView(title);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 56)));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content = UI.vbox(this);
        content.setPadding(UI.dp(this, 16), 0, UI.dp(this, 16), UI.dp(this, 40));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        styleBars();
        render();
    }

    // ------------------------------------------------------------------ rows

    private void section(String t) {
        TextView s = UI.label(this, t.toUpperCase(), 11, true);
        s.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        s.setLetterSpacing(0.08f);
        s.setTextColor(UI.color(this, R.attr.bAccent));
        s.setPadding(UI.dp(this, 4), UI.dp(this, 24), 0, UI.dp(this, 6));
        content.addView(s);
    }

    private LinearLayout row(String title, String desc) {
        LinearLayout r = UI.hbox(this);
        r.setBackgroundResource(R.drawable.bg_ripple_rect);
        r.setPadding(UI.dp(this, 6), UI.dp(this, 11), UI.dp(this, 6), UI.dp(this, 11));
        LinearLayout col = UI.vbox(this);
        col.addView(UI.label(this, title, 15, false));
        if (desc != null && !desc.isEmpty()) {
            TextView d = UI.label(this, desc, 12, true);
            d.setPadding(0, UI.dp(this, 2), 0, 0);
            col.addView(d);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(r);
        return r;
    }

    private void switchRow(String title, String desc, final String key, boolean def, final Runnable after) {
        LinearLayout r = row(title, desc);
        final Switch sw = new Switch(this);
        sw.setChecked(Prefs.bool(this, key, def));
        sw.setClickable(false);
        r.addView(sw);
        r.setOnClickListener(v -> {
            sw.toggle();
            Prefs.put(this, key, sw.isChecked());
            if (after != null) after.run();
        });
    }

    private void choiceRow(String title, final String key, final String[] labels, final int def, final Runnable after) {
        final int cur = Math.max(0, Math.min(labels.length - 1, Prefs.num(this, key, def)));
        LinearLayout r = row(title, labels[cur]);
        r.setOnClickListener(v -> UI.dialog(this).setTitle(title).setSingleChoiceItems(labels, cur, (d, which) -> {
            Prefs.put(this, key, which);
            d.dismiss();
            if (after != null) after.run();
            render();
        }).show());
    }

    private void sdkRow(String title, final String key, int def) {
        final String[] names = new String[14];
        for (int i = 0; i < names.length; i++) names[i] = String.valueOf(21 + i);
        final int cur = Math.max(21, Math.min(34, Prefs.num(this, key, def)));
        LinearLayout r = row(title, "API " + cur);
        r.setOnClickListener(v -> UI.dialog(this).setTitle(title).setSingleChoiceItems(names, cur - 21, (d, which) -> {
            Prefs.put(this, key, 21 + which);
            d.dismiss();
            render();
        }).show());
    }

    private void sliderRow(final String title, final String key, final int min, final int max, final int def, final String unit) {
        final int cur = key.equals(Prefs.DEF_RADIUS) ? Prefs.num(this, key, def) : Math.round(Prefs.flt(this, key, def));
        LinearLayout r = row(title, cur + unit);
        r.setOnClickListener(v -> {
            LinearLayout box = UI.vbox(this);
            box.setPadding(UI.dp(this, 24), UI.dp(this, 16), UI.dp(this, 24), 0);
            final TextView val = UI.label(this, cur + unit, 16, false);
            val.setGravity(Gravity.CENTER);
            box.addView(val);
            final SeekBar sb = new SeekBar(this);
            sb.setMax(max - min);
            sb.setProgress(cur - min);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) { val.setText((p + min) + unit); }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { }
            });
            box.addView(sb);
            UI.dialog(this).setTitle(title).setView(box).setNegativeButton("Cancel", null)
                    .setPositiveButton("OK", (d, w) -> {
                        int v2 = sb.getProgress() + min;
                        if (key.equals(Prefs.DEF_RADIUS)) Prefs.put(this, key, v2); else Prefs.put(this, key, (float) v2);
                        render();
                    }).show();
        });
    }

    private void textRow(final String title, final String key, final String hint, final boolean secret) {
        String cur = Prefs.str(this, key, "");
        String shown = cur.isEmpty() ? "Not set" : (secret ? "••••••••" : cur);
        LinearLayout r = row(title, shown);
        r.setOnClickListener(v -> {
            final EditText e = UI.input(this, hint, Prefs.str(this, key, ""));
            if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            LinearLayout box = UI.vbox(this);
            box.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), 0);
            box.addView(e);
            UI.dialog(this).setTitle(title).setView(box).setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> { Prefs.put(this, key, e.getText().toString().trim()); render(); }).show();
        });
    }

    private void actionRow(String title, String desc, final Runnable r) {
        LinearLayout row = row(title, desc);
        row.setOnClickListener(v -> r.run());
    }

    // ------------------------------------------------------------------ page

    private void render() {
        final int sy = scroll.getScrollY();
        content.removeAllViews();

        section("Appearance");
        choiceRow("App theme", Prefs.THEME, new String[]{"Follow system", "Light", "Dark"}, 0, null);

        section("Code editor");
        choiceRow("Color scheme", Prefs.SCHEME, EditorScheme.names(), dark ? 1 : 0, null);
        sliderRow("Font size", Prefs.FONT_SIZE, 8, 24, 12, " sp");
        choiceRow("Font family", Prefs.FONT_FAMILY, new String[]{"Monospace", "Sans-serif monospace", "Serif monospace", "Custom TTF file"}, 0, () -> {
            if (Prefs.num(this, Prefs.FONT_FAMILY, 0) == 3) {
                FilePicker.pickFile(this, "Choose a .ttf / .otf font", android.os.Environment.getExternalStorageDirectory(),
                        new String[]{"ttf", "otf"}, f -> { Prefs.put(this, Prefs.FONT_PATH, f.getAbsolutePath()); render(); });
            }
        });
        choiceRow("Tab size", Prefs.TAB_SIZE, new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8"}, 4, null);
        switchRow("Use spaces for tabs", "Insert spaces instead of the tab character", Prefs.USE_SPACES, true, null);
        switchRow("Word wrap", "Wrap long lines instead of scrolling sideways", Prefs.WORD_WRAP, false, null);
        switchRow("Line numbers", null, Prefs.LINE_NUMBERS, true, null);
        switchRow("Highlight current line", null, Prefs.HL_LINE, true, null);
        switchRow("Auto indent", "Keep indentation when pressing Enter", Prefs.AUTO_INDENT, true, null);
        switchRow("Auto close brackets & tags", "( ) { } [ ] \" \" and XML end tags", Prefs.AUTO_CLOSE, true, null);
        switchRow("Auto save", "Save files while you type and when leaving the editor", Prefs.AUTO_SAVE, true, null);
        switchRow("Live error check", "Show red lines for XML / bracket mistakes while typing", Prefs.LIVE_LINT, true, null);
        switchRow("Symbol bar", "Quick keys above the keyboard", Prefs.SYMBOL_BAR, true, null);
        switchRow("Show hidden files", "Files starting with a dot (.git, .buildora)", Prefs.SHOW_HIDDEN, false, null);
        switchRow("Confirm before deleting", null, Prefs.CONFIRM_DELETE, true, null);

        section("Build");
        switchRow("Incremental builds", "Reuse compiled resources and only recompile changed Java files", Prefs.INCREMENTAL, true, null);
        switchRow("Build notification", "Show progress and the result in the notification bar", Prefs.NOTIFY, true, null);
        switchRow("Verify signature after build", "Checks the v2/v3 signature of every APK", Prefs.VERIFY_SIG, true, null);
        switchRow("Align native libraries to 16 KB", "Needed for devices with 16 KB memory pages (Android 15+)", Prefs.ALIGN_16K, true, null);
        switchRow("Delete intermediate files after build", "Saves storage (.buildora/build)", Prefs.CLEAN_BEFORE, true, null);
        switchRow("Keep screen on while building", null, Prefs.KEEP_AWAKE, true, null);
        switchRow("Install automatically after build", "Opens the installer when the APK is ready", Prefs.AUTO_INSTALL, false, null);
        switchRow("Auto increment version code", "+1 after every successful build", Prefs.AUTO_VERSION, false, null);
        switchRow("Optimized dex (release mode)", "Smaller dex, slower build, no debug info", Prefs.DEX_RELEASE, false, null);

        section("Signing");
        String active;
        try { active = Keystores.active(this).name; } catch (Exception e) { active = "Buildora debug key"; }
        actionRow("Signing keys", "Active: " + active + "  •  generate, import, export", () -> startActivity(new android.content.Intent(this, KeystoreActivity.class)));

        section("Toolchain");
        actionRow("Toolchain status", "aapt2 • android.jar • ECJ • D8 • apksig", this::showToolchain);
        actionRow("Import aapt2 binary", "Android (ARM/x86) executable named aapt2", () ->
                FilePicker.pickFile(this, "Select aapt2", android.os.Environment.getExternalStorageDirectory(), null, f -> {
                    try { Toolchain.importAapt2(this, f); UI.toast(this, "aapt2 imported"); } catch (Exception e) { UI.toast(this, e.getMessage()); }
                }));
        actionRow("Download aapt2 from URL", "Direct link to an aapt2 binary or a zip containing it", this::downloadAapt2);
        actionRow("Import android.jar", "Platform jar used to compile your apps", () ->
                FilePicker.pickFile(this, "Select android.jar", android.os.Environment.getExternalStorageDirectory(), new String[]{"jar"}, f -> {
                    try { Toolchain.importAndroidJar(this, f); UI.toast(this, "android.jar imported"); } catch (Exception e) { UI.toast(this, e.getMessage()); }
                }));
        actionRow("Reset toolchain to bundled", "Re-extract the tools shipped inside Buildora", () -> {
            try { Toolchain.resetToBundled(this); UI.toast(this, "Toolchain reset"); } catch (Exception e) { UI.toast(this, e.getMessage()); }
        });
        actionRow("Clear incremental build cache", "Forces the next build of every project to compile everything", () -> {
            new Thread(() -> {
                for (com.tenix.buildora.core.Project pr : com.tenix.buildora.core.ProjectStore.list()) FileUtil.deleteRecursive(new File(pr.workDir(), "cache"));
                runOnUiThread(() -> UI.toast(this, "Build caches cleared"));
            }).start();
        });
        actionRow("Clear dependency cache", "Deletes downloaded libraries and dex caches", () -> UI.dialog(this)
                .setTitle("Clear caches?").setMessage("Downloaded libraries will be fetched again on the next build.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear", (d, w) -> {
                    FileUtil.deleteRecursive(new File(getFilesDir(), "m2"));
                    FileUtil.deleteRecursive(new File(getFilesDir(), "dexcache"));
                    UI.toast(this, "Caches cleared");
                }).show());

        section("New projects");
        textRow("Default package prefix", Prefs.PKG_PREFIX, "com.example", false);
        sdkRow("Default min SDK", Prefs.DEF_MIN_SDK, 21);
        sdkRow("Default target SDK", Prefs.DEF_TARGET_SDK, 34);
        sliderRow("Default icon corner radius", Prefs.DEF_RADIUS, 0, 50, 22, "%");

        section("Git");
        textRow("Author name", Prefs.GIT_NAME, "Your name", false);
        textRow("Author email", Prefs.GIT_EMAIL, "you@example.com", false);
        textRow("Username", Prefs.GIT_USER, "GitHub username (optional)", false);
        textRow("Access token / password", Prefs.GIT_TOKEN, "Personal access token", true);

        section("Storage");
        actionRow("Projects folder", Project.baseDir().getAbsolutePath(), () -> UI.copy(this, "path", Project.baseDir().getAbsolutePath()));
        actionRow("Storage permission", Perm.hasStorage(this) ? "Granted" : "Not granted - tap to allow", () -> Perm.requestStorage(this, 21));

        section("About");
        actionRow("Buildora 2.0.0", "On-device Java & XML app builder • by TENIx", () -> { });

        scroll.post(() -> scroll.scrollTo(0, sy));
    }

    private void showToolchain() {
        final TextView t = UI.label(this, "Checking…", 13, false);
        t.setPadding(UI.dp(this, 24), UI.dp(this, 12), UI.dp(this, 24), UI.dp(this, 4));
        t.setTextIsSelectable(true);
        final AlertDialog d = UI.dialog(this).setTitle("Toolchain").setView(t).setPositiveButton("Close", null).create();
        d.show();
        new Thread(() -> {
            try { Toolchain.ensure(this); } catch (Exception ignored) { }
            final String s = Toolchain.status(this);
            runOnUiThread(() -> t.setText(s));
        }).start();
    }

    private void downloadAapt2() {
        final EditText e = UI.input(this, "https://…/aapt2", "");
        LinearLayout box = UI.vbox(this);
        box.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), 0);
        box.addView(e);
        UI.dialog(this).setTitle("Download aapt2").setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Download", (d, w) -> {
                    final String url = e.getText().toString().trim();
                    if (url.isEmpty()) return;
                    UI.toast(this, "Downloading…");
                    new Thread(() -> {
                        String msg;
                        try {
                            File tmp = new File(getCacheDir(), "aapt2.download");
                            if (!Net.download(url, tmp, null)) throw new Exception("Server returned 404");
                            Toolchain.importAapt2Download(this, tmp);
                            tmp.delete();
                            msg = "aapt2 installed: " + Toolchain.aapt2Version(this);
                        } catch (Exception ex) { msg = "Failed: " + ex.getMessage(); }
                        final String m = msg;
                        runOnUiThread(() -> UI.toastLong(this, m));
                    }).start();
                }).show();
    }
}
