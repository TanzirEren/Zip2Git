package com.tenix.buildora;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tenix.buildora.build.BuildEngine;
import com.tenix.buildora.build.ErrorHints;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.Installer;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.UI;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows build progress, the success dialog (download / install / share) and the clickable error list. */
final class BuildRunner {
    private BuildRunner() {}

    private static ProgressUi active;

    /** Starts a build in the foreground service and shows the progress dialog. */
    static void start(IdeActivity act, Project p, BuildEngine.Options opt) {
        if (!BuildManager.get().start(act, p, opt)) {
            UI.toast(act, BuildManager.get().isRunning() ? "A build is already running" : "Could not start the build service");
            if (BuildManager.get().isRunning()) attach(act, p);
            return;
        }
        attach(act, p);
    }

    /** Call from onResume: re-attaches to a running build or shows a result that arrived while the app was in the background. */
    static void attach(IdeActivity act, Project p) {
        String root = p.root.getAbsolutePath();
        BuildManager m = BuildManager.get();
        if (m.isRunningFor(root)) {
            if (active != null && active.act == act && active.dlg != null && active.dlg.isShowing()) return;
            detach(act);
            active = new ProgressUi(act, p);
            active.show();
            return;
        }
        BuildEngine.Result r = m.takeUnseenResult(root);
        if (r != null) showResult(act, p, r);
    }

    static void detach(IdeActivity act) {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    private static void showResult(IdeActivity act, Project p, BuildEngine.Result r) {
        act.onBuildFinished(r);
        if (r.success) showSuccess(act, p, r);
        else if (r.cancelled) UI.toast(act, "Build cancelled");
        else showErrors(act, p, r);
    }

    // ------------------------------------------------------------------ progress

    private static final class ProgressUi implements BuildManager.Listener {
        final IdeActivity act;
        final Project p;
        AlertDialog dlg;
        TextView stage, log;
        ProgressBar bar;
        ScrollView sv;
        final StringBuilder shown = new StringBuilder();

        ProgressUi(IdeActivity act, Project p) { this.act = act; this.p = p; }

        void show() {
            LinearLayout root = UI.vbox(act);
            root.setPadding(UI.dp(act, 20), UI.dp(act, 8), UI.dp(act, 20), UI.dp(act, 12));
            stage = UI.label(act, "Starting…", 15, false);
            stage.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            root.addView(stage);
            bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(act, 6));
            blp.topMargin = UI.dp(act, 10);
            root.addView(bar, blp);
            log = new TextView(act);
            log.setTypeface(Typeface.MONOSPACE);
            log.setTextSize(10);
            log.setTextColor(UI.color(act, R.attr.bTextDim));
            log.setPadding(UI.dp(act, 10), UI.dp(act, 8), UI.dp(act, 10), UI.dp(act, 8));
            log.setBackgroundResource(R.drawable.bg_input);
            sv = new ScrollView(act);
            sv.addView(log);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(act, 220));
            slp.topMargin = UI.dp(act, 12);
            root.addView(sv, slp);
            LinearLayout row = UI.hbox(act);
            TextView bg = UI.button(act, "Run in background", false);
            TextView cancel = UI.button(act, "Cancel build", false);
            row.addView(bg, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(new View(act), new LinearLayout.LayoutParams(UI.dp(act, 8), 1));
            row.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            root.addView(row);
            root.addView(UI.label(act, "The build keeps running if you leave the app. A notification shows the progress.", 11, true));

            BuildManager m = BuildManager.get();
            dlg = UI.dialog(act).setTitle("Building " + m.projectName() + " • " + m.variantName()).setView(root).setCancelable(false).create();
            dlg.show();
            bg.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { close(); }
            });
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { ((TextView) v).setText("Cancelling…"); BuildManager.get().cancel(); }
            });
            shown.append(m.logSnapshot());
            log.setText(shown);
            stage.setText(m.stage() + "…");
            bar.setProgress(m.percent());
            m.attach(this);
            scroll();
        }

        void scroll() { sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } }); }

        void close() {
            BuildManager.get().detach(this);
            if (dlg != null && dlg.isShowing()) dlg.dismiss();
        }

        @Override public void onStage(String s, int percent) { stage.setText(s + "…"); bar.setProgress(percent); }

        @Override public void onLog(String line) {
            shown.append(line).append('\n');
            if (shown.length() > 24000) shown.delete(0, shown.length() - 16000);
            log.setText(shown);
            scroll();
        }

        @Override public void onFinished(BuildEngine.Result r) {
            close();
            if (active == this) active = null;
            showResult(act, p, r);
        }
    }

    // ------------------------------------------------------------------ success

    private static void showSuccess(final IdeActivity act, final Project p, final BuildEngine.Result r) {
        final File out = r.output();
        LinearLayout box = UI.vbox(act);
        box.setPadding(UI.dp(act, 20), UI.dp(act, 4), UI.dp(act, 20), UI.dp(act, 8));
        StringBuilder info = new StringBuilder();
        info.append(out.getName()).append("  •  ").append(FileUtil.human(out.length()))
                .append("\nVariant ").append(r.variant)
                .append("  •  built in ").append(String.format(Locale.US, "%.1f", r.millis / 1000.0)).append(" s");
        if (r.mapping != null) info.append("\nR8 mapping: ").append(r.mapping.getPath());
        info.append("\n\n").append(out.getAbsolutePath());
        TextView t = UI.label(act, info.toString(), 13, true);
        t.setTextIsSelectable(true);
        box.addView(t);
        final AlertDialog[] d = new AlertDialog[1];
        TextView download = UI.button(act, "Download", true);
        TextView install = UI.button(act, "Install", false);
        TextView share = UI.button(act, "Share", false);
        TextView close = UI.button(act, "Close", false);
        box.addView(download);
        if (r.aab == null) box.addView(install);
        box.addView(share);
        box.addView(close);
        download.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    File f = Installer.copyToDownloads(out);
                    UI.toastLong(act, "Saved to " + f.getAbsolutePath());
                } catch (Exception e) {
                    UI.toastLong(act, "Download failed: " + e.getMessage());
                }
            }
        });
        install.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Installer.install(act, out); }
        });
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Installer.share(act, out); }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d[0].dismiss(); }
        });
        d[0] = UI.dialog(act).setTitle(r.aab != null ? "Bundle ready" : "Build successful").setView(box).create();
        d[0].show();
        if (r.aab == null && com.tenix.buildora.core.Prefs.bool(act, com.tenix.buildora.core.Prefs.AUTO_INSTALL, false)) Installer.install(act, out);
    }

    // ------------------------------------------------------------------ errors

    static void showErrors(final IdeActivity act, final Project p, final BuildEngine.Result r) {
        LinearLayout box = UI.vbox(act);
        box.setPadding(UI.dp(act, 16), UI.dp(act, 4), UI.dp(act, 16), UI.dp(act, 8));
        final AlertDialog[] d = new AlertDialog[1];

        final List<BuildEngine.Diag> rows = new ArrayList<>();
        for (BuildEngine.Diag g : r.diags) if (g.error && g.file != null && new File(g.file).isFile()) rows.add(g);

        ScrollView sv = new ScrollView(act);
        LinearLayout list = UI.vbox(act);
        sv.addView(list);
        if (rows.isEmpty()) {
            TextView t = new TextView(act);
            t.setText(r.errorText);
            t.setTextIsSelectable(true);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextSize(11);
            t.setTextColor(UI.color(act, R.attr.bText));
            t.setPadding(UI.dp(act, 10), UI.dp(act, 8), UI.dp(act, 10), UI.dp(act, 8));
            t.setBackgroundResource(R.drawable.bg_input);
            list.addView(t);
            String h = ErrorHints.hint(r.errorText, null);
            if (h != null) {
                TextView ht = UI.label(act, "Hint: " + h, 12, true);
                ht.setPadding(0, UI.dp(act, 8), 0, 0);
                list.addView(ht);
            }
        } else {
            TextView head = UI.label(act, rows.size() + (rows.size() == 1 ? " problem" : " problems") + " - tap one to jump to the line", 12, true);
            head.setPadding(0, 0, 0, UI.dp(act, 6));
            list.addView(head);
            int shown = 0;
            for (final BuildEngine.Diag g : rows) {
                if (shown++ >= 60) break;
                list.addView(row(act, p, g, d));
            }
        }
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(act, 340)));

        LinearLayout btns = UI.hbox(act);
        TextView copy = UI.button(act, "Copy all", true);
        TextView full = UI.button(act, "Full log", false);
        TextView close = UI.button(act, "Close", false);
        btns.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btns.addView(new View(act), new LinearLayout.LayoutParams(UI.dp(act, 6), 1));
        btns.addView(full, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btns.addView(new View(act), new LinearLayout.LayoutParams(UI.dp(act, 6), 1));
        btns.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btns.setGravity(Gravity.CENTER);
        box.addView(btns);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                UI.copy(act, "Buildora build errors", r.errorText);
                UI.toast(act, "All errors copied");
            }
        });
        full.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLog(act, r); }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d[0].dismiss(); }
        });
        d[0] = UI.dialog(act).setTitle("Build failed").setView(box).create();
        d[0].show();
    }

    private static View row(final IdeActivity act, final Project p, final BuildEngine.Diag g, final AlertDialog[] dlg) {
        LinearLayout r = UI.vbox(act);
        r.setBackgroundResource(R.drawable.bg_card);
        r.setPadding(UI.dp(act, 12), UI.dp(act, 10), UI.dp(act, 12), UI.dp(act, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(act, 8);
        r.setLayoutParams(lp);
        String rel = FileUtil.relPath(p.root, new File(g.file));
        TextView where = UI.label(act, rel + (g.line > 0 ? "  :  " + g.line : ""), 12, false);
        where.setTextColor(UI.color(act, R.attr.bAccent));
        where.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        r.addView(where);
        TextView msg = UI.label(act, g.message, 13, false);
        msg.setPadding(0, UI.dp(act, 3), 0, 0);
        r.addView(msg);
        String hint = ErrorHints.hint(g.message, g.file);
        if (hint != null) {
            TextView h = UI.label(act, hint, 12, true);
            h.setPadding(0, UI.dp(act, 5), 0, 0);
            r.addView(h);
        }
        final String missing = ErrorHints.missingType(g.message);
        if (missing != null && g.file.endsWith(".java")) {
            TextView fix = UI.button(act, "Fix: add import for " + missing, false);
            fix.setTextSize(12);
            fix.setPadding(UI.dp(act, 10), UI.dp(act, 7), UI.dp(act, 10), UI.dp(act, 7));
            r.addView(fix);
            fix.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { act.fixMissingImport(g.file, missing); }
            });
        }
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dlg[0].dismiss();
                act.openFileAt(g.file, g.line);
            }
        });
        return r;
    }

    private static void showLog(IdeActivity act, final BuildEngine.Result r) {
        TextView t = new TextView(act);
        t.setText(r.log.length() > 60000 ? r.log.substring(r.log.length() - 60000) : r.log);
        t.setTextIsSelectable(true);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(10);
        t.setTextColor(UI.color(act, R.attr.bText));
        t.setPadding(UI.dp(act, 14), UI.dp(act, 8), UI.dp(act, 14), UI.dp(act, 8));
        ScrollView sv = new ScrollView(act);
        sv.addView(t);
        UI.dialog(act).setTitle("Full build log").setView(sv).setPositiveButton("Close", null).show();
    }
}
