package com.tenix.buildora.core;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.tenix.buildora.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** In-app storage browser (works with plain java.io paths, no SAF needed). */
public final class FilePicker {
    private FilePicker() {}

    public interface Callback { void onPicked(File f); }

    public static final int FOLDER = 0, FILE = 1, ANY = 2;

    public static void pickFolder(Context c, String title, File start, Callback cb) {
        show(c, title, start, FOLDER, null, cb);
    }

    public static void pickFile(Context c, String title, File start, String[] exts, Callback cb) {
        show(c, title, start, FILE, exts, cb);
    }

    public static void pickAny(Context c, String title, File start, Callback cb) {
        show(c, title, start, ANY, null, cb);
    }

    private static final class State {
        File dir;
        List<File> items = new ArrayList<>();
    }

    private static void show(final Context c, String title, File start, final int mode,
                             final String[] exts, final Callback cb) {
        final State st = new State();
        st.dir = (start != null && start.isDirectory()) ? start : Environment.getExternalStorageDirectory();

        LinearLayout root = UI.vbox(c);
        root.setPadding(UI.dp(c, 16), UI.dp(c, 4), UI.dp(c, 16), 0);
        final TextView path = UI.label(c, "", 12, true);
        path.setSingleLine(true);
        path.setEllipsize(android.text.TextUtils.TruncateAt.START);
        root.addView(path);
        final ListView list = new ListView(c);
        list.setDivider(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 360));
        lp.topMargin = UI.dp(c, 8);
        root.addView(list, lp);

        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return st.items.size() + 1; }
            @Override public Object getItem(int i) { return i == 0 ? null : st.items.get(i - 1); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) {
                TextView t = new TextView(c);
                t.setPadding(UI.dp(c, 8), UI.dp(c, 11), UI.dp(c, 8), UI.dp(c, 11));
                t.setTextSize(14);
                t.setSingleLine(true);
                t.setGravity(Gravity.CENTER_VERTICAL);
                if (i == 0) {
                    t.setText("..  (up)");
                    t.setTextColor(UI.color(c, R.attr.bAccent));
                    UI.tintIcon(c, t, R.drawable.ic_arrow_up, R.attr.bAccent, 18);
                } else {
                    File f = st.items.get(i - 1);
                    t.setText(f.getName());
                    t.setTextColor(UI.color(c, R.attr.bText));
                    UI.tintIcon(c, t, f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file,
                            f.isDirectory() ? R.attr.bAccent : R.attr.bTextDim, 18);
                }
                return t;
            }
        };
        list.setAdapter(ad);

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            @Override public void run() {
                path.setText(st.dir.getAbsolutePath());
                st.items.clear();
                File[] kids = st.dir.listFiles();
                if (kids != null) {
                    List<File> tmp = new ArrayList<>(Arrays.asList(kids));
                    java.util.Collections.sort(tmp, new Comparator<File>() {
                        @Override public int compare(File a, File b) {
                            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                            return a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT));
                        }
                    });
                    for (File f : tmp) {
                        if (mode == FOLDER && !f.isDirectory()) continue;
                        if (mode == FILE && exts != null && f.isFile() && !matches(f, exts)) continue;
                        st.items.add(f);
                    }
                }
                ad.notifyDataSetChanged();
                list.setSelection(0);
            }
        };

        AlertDialog.Builder b = UI.dialog(c).setTitle(title).setView(root).setNegativeButton("Cancel", null);
        if (mode != FILE) b.setPositiveButton(mode == FOLDER ? "Select this folder" : "Select folder", null);
        final AlertDialog dlg = b.create();

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 0) {
                    File up = st.dir.getParentFile();
                    if (up != null && up.canRead()) { st.dir = up; refresh[0].run(); }
                    return;
                }
                File f = st.items.get(pos - 1);
                if (f.isDirectory()) { st.dir = f; refresh[0].run(); }
                else { dlg.dismiss(); cb.onPicked(f); }
            }
        });
        dlg.show();
        if (mode != FILE) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dlg.dismiss(); cb.onPicked(st.dir); }
            });
        }
        refresh[0].run();
    }

    private static boolean matches(File f, String[] exts) {
        String e = FileUtil.ext(f);
        for (String x : exts) if (x.equalsIgnoreCase(e)) return true;
        return false;
    }
}
