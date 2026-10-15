package com.tenix.buildora;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.tenix.buildora.build.BuildEngine;
import com.tenix.buildora.build.GitHelper;
import com.tenix.buildora.build.ImportIndex;
import com.tenix.buildora.build.Toolchain;
import com.tenix.buildora.core.GalleryPicker;
import com.tenix.buildora.core.Variant;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.FilePicker;
import com.tenix.buildora.core.IconUtil;
import com.tenix.buildora.core.Installer;
import com.tenix.buildora.core.Prefs;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.ProjectStore;
import com.tenix.buildora.core.Templates;
import com.tenix.buildora.core.UI;
import com.tenix.buildora.editor.CodeEditText;
import com.tenix.buildora.editor.EditorScheme;
import com.tenix.buildora.editor.Highlighter;
import com.tenix.buildora.editor.Linter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IdeActivity extends BaseActivity {

    // ------------------------------------------------------------------ model

    private static final class OpenFile {
        File file;
        String text;
        boolean dirty;
        boolean image;
        String lang = "text";
        int selStart, selEnd, scrollY, scrollX;
        CodeEditText.UndoStack undo = new CodeEditText.UndoStack();
    }

    private static final class Node {
        final File file;
        final int depth;
        Node(File f, int d) { file = f; depth = d; }
    }

    private Project project;
    private File root;
    private CodeEditText editor;
    private View explorer, scrim, mainColumn, findBar, emptyEditor, symbolScroll;
    private ImageView ivPreview;
    private LinearLayout tabsBar, symbolBar;
    private HorizontalScrollView tabsScroll;
    private TextView tvTitle, tvStatus, tvExplorerTitle;
    private ListView lvTree;
    private ImageView ibUndo, ibRedo, ibSave;
    private EditText etFind, etReplace;
    private TextView tvCase, tvRegex;
    private boolean matchCase, useRegex;

    private final List<OpenFile> tabs = new ArrayList<>();
    private OpenFile current;
    private final List<Node> nodes = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private BaseAdapter treeAdapter;
    private boolean explorerOpen;
    private boolean buildRunning;
    private interface IconCb { void onIcon(Bitmap b); }
    private IconCb pendingIcon;
    private static final int REQ_GALLERY = 301;
    private String lintMessage = "";
    private final Map<String, Map<Integer, String>> buildErrors = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoSave = new Runnable() { @Override public void run() { if (current != null && current.dirty) saveTab(current, false); } };

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String r = getIntent().getStringExtra("root");
        if (r == null) { finish(); return; }
        root = new File(r);
        project = Project.load(root);
        setContentView(R.layout.activity_ide);
        styleBars();
        bindViews();
        setupExplorer();
        setupEditor();
        setupSymbolBar();
        setupFindBar();
        setupTopBar();
        applyEditorPrefs();
        rebuildTree();
        updateTitle();
        tvExplorerTitle.setText(project.name);
        ProjectStore.touch(project);
        File main = new File(project.javaRoot(), project.packagePath() + "/MainActivity.java");
        if (main.isFile()) openFile(main);
        else refreshAll();
    }

    @Override protected void onResume() {
        super.onResume();
        if (editor != null) applyEditorPrefs();
        if (root != null) {
            Project fresh = Project.load(root);
            project = fresh;
            updateTitle();
            BuildRunner.attach(this, project);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String r = intent.getStringExtra("root");
        setIntent(intent);
        if (r != null && root != null && !r.equals(root.getAbsolutePath())) recreate();   // notification for another project
    }

    @Override protected void onPause() {
        super.onPause();
        saveAll(false);
        BuildRunner.detach(this);
    }

    private void updateTitle() {
        Variant v = project.activeVariant();
        boolean plain = project.flavors.isEmpty() && v.buildType.equals("debug");
        tvTitle.setText(plain ? project.name : project.name + "  •  " + v.name);
    }

    @Override public void onBackPressed() {
        if (explorerOpen) { closeExplorer(); return; }
        if (findBar.getVisibility() == View.VISIBLE) { findBar.setVisibility(View.GONE); return; }
        saveAll(false);
        super.onBackPressed();
    }

    private void bindViews() {
        editor = findViewById(R.id.editor);
        explorer = findViewById(R.id.explorer);
        scrim = findViewById(R.id.scrim);
        mainColumn = findViewById(R.id.mainColumn);
        findBar = findViewById(R.id.findBar);
        emptyEditor = findViewById(R.id.emptyEditor);
        ivPreview = findViewById(R.id.ivPreview);
        tabsBar = findViewById(R.id.tabsBar);
        tabsScroll = findViewById(R.id.tabsScroll);
        symbolBar = findViewById(R.id.symbolBar);
        symbolScroll = findViewById(R.id.symbolScroll);
        tvTitle = findViewById(R.id.tvTitle);
        tvStatus = findViewById(R.id.tvStatus);
        tvExplorerTitle = findViewById(R.id.tvExplorerTitle);
        lvTree = findViewById(R.id.lvTree);
        ibUndo = findViewById(R.id.ibUndo);
        ibRedo = findViewById(R.id.ibRedo);
        ibSave = findViewById(R.id.ibSave);
        etFind = findViewById(R.id.etFind);
        etReplace = findViewById(R.id.etReplace);
        tvCase = findViewById(R.id.tvCase);
        tvRegex = findViewById(R.id.tvRegex);
    }

    // ------------------------------------------------------------------ top bar

    private void setupTopBar() {
        findViewById(R.id.ibMenu).setOnClickListener(v -> { if (explorerOpen) closeExplorer(); else openExplorer(); });
        findViewById(R.id.ibRun).setOnClickListener(v -> runBuild());
        ibUndo.setOnClickListener(v -> { editor.undo(); refreshButtons(); });
        ibRedo.setOnClickListener(v -> { editor.redo(); refreshButtons(); });
        ibSave.setOnClickListener(v -> { saveAll(true); });
        findViewById(R.id.ibMore).setOnClickListener(v -> showMoreMenu(v));
    }

    private void refreshButtons() {
        boolean hasText = current != null && !current.image;
        ibUndo.setAlpha(hasText && editor.canUndo() ? 1f : 0.35f);
        ibRedo.setAlpha(hasText && editor.canRedo() ? 1f : 0.35f);
        boolean anyDirty = false;
        for (OpenFile t : tabs) if (t.dirty) anyDirty = true;
        ibSave.setAlpha(anyDirty ? 1f : 0.35f);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        String[] items = {"Find & replace", "Go to line", "Format code", "Save all", "Close all files",
                "Build variants", "Modules", "Libraries", "ProGuard rules", "Export AAB (bundle)",
                "Project settings", "App icon", "Signing keys",
                "Git: status", "Git: commit", "Git: pull", "Git: push",
                "Clean project", "Last build log", "Install built APK", "Settings"};
        for (int i = 0; i < items.length; i++) m.add(0, i, i, items[i]);
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: showFind(); break;
                case 1: gotoLineDialog(); break;
                case 2: if (current != null && !current.image) { editor.formatDocument(); } break;
                case 3: saveAll(true); break;
                case 4: closeAllTabs(); break;
                case 5: openScreen(VariantsActivity.class); break;
                case 6: openScreen(ModulesActivity.class); break;
                case 7: openScreen(LibrariesActivity.class); break;
                case 8: openScreen(ProguardActivity.class); break;
                case 9: runBuild(true); break;
                case 10: projectSettings(); break;
                case 11: changeIcon(); break;
                case 12: startActivity(new Intent(this, KeystoreActivity.class)); break;
                case 13: gitStatus(); break;
                case 14: gitCommit(); break;
                case 15: gitPull(); break;
                case 16: gitPush(); break;
                case 17: cleanProject(); break;
                case 18: showBuildLog(); break;
                case 19: installBuilt(); break;
                case 20: startActivity(new Intent(this, SettingsActivity.class)); break;
            }
            return true;
        });
        pm.show();
    }

    private void openScreen(Class<?> c) {
        saveAll(false);
        Intent i = new Intent(this, c);
        i.putExtra("root", root.getAbsolutePath());
        startActivity(i);
    }

    // ------------------------------------------------------------------ editor setup

    private void setupEditor() {
        editor.setListener(new CodeEditText.Listener() {
            @Override public void onContentChanged() {
                if (current != null) {
                    if (!current.dirty) { current.dirty = true; refreshTabs(); }
                    if (Prefs.bool(IdeActivity.this, Prefs.AUTO_SAVE, true)) {
                        handler.removeCallbacks(autoSave);
                        handler.postDelayed(autoSave, 1800);
                    }
                }
                refreshButtons();
            }
            @Override public void onCaretMoved(int line, int col) { updateStatus(line, col); }
            @Override public void onLint(List<Linter.Problem> problems) {
                lintMessage = problems.isEmpty() ? "" : "Line " + problems.get(0).line + ": " + problems.get(0).message;
                updateStatus(editor.currentLine(), editor.currentCol());
            }
        });
        editor.setZoomListener(sp -> Prefs.put(this, Prefs.FONT_SIZE, sp));
    }

    private void updateStatus(int line, int col) {
        if (current == null) { tvStatus.setText(""); return; }
        String s = "Ln " + line + ", Col " + col + "  •  " + Highlighter.langLabel(current.lang) + "  •  UTF-8";
        String err = editor.errorAtCaret();
        if (err != null) s += "  •  " + err;
        else if (!lintMessage.isEmpty()) s += "  •  " + lintMessage;
        tvStatus.setText(s);
        tvStatus.setTextColor(UI.color(this, (err != null || !lintMessage.isEmpty()) ? R.attr.bError : R.attr.bTextDim));
    }

    private void applyEditorPrefs() {
        int si = Prefs.num(this, Prefs.SCHEME, -1);
        EditorScheme sc = EditorScheme.get(si < 0 ? (dark ? 1 : 0) : si);
        editor.applyScheme(sc);
        float size = Prefs.flt(this, Prefs.FONT_SIZE, 12f);
        editor.configure(Prefs.bool(this, Prefs.LINE_NUMBERS, true), Prefs.bool(this, Prefs.HL_LINE, true),
                Prefs.bool(this, Prefs.AUTO_INDENT, true), Prefs.bool(this, Prefs.AUTO_CLOSE, true),
                Prefs.num(this, Prefs.TAB_SIZE, 4), Prefs.bool(this, Prefs.USE_SPACES, true),
                Prefs.bool(this, Prefs.WORD_WRAP, false), Prefs.bool(this, Prefs.LIVE_LINT, true), size);
        int fam = Prefs.num(this, Prefs.FONT_FAMILY, 0);
        Typeface tf = Typeface.MONOSPACE;
        if (fam == 1) tf = Typeface.create("sans-serif-monospace", Typeface.NORMAL);
        else if (fam == 2) tf = Typeface.create("serif-monospace", Typeface.NORMAL);
        else if (fam == 3) {
            try { tf = Typeface.createFromFile(Prefs.str(this, Prefs.FONT_PATH, "")); } catch (Exception ignored) { }
        }
        editor.setTypeface(tf);
        symbolScroll.setVisibility(Prefs.bool(this, Prefs.SYMBOL_BAR, true) ? View.VISIBLE : View.GONE);
        if (current != null && !current.image) editor.rehighlight();
        refreshTree();
    }

    // ------------------------------------------------------------------ symbol bar

    private void setupSymbolBar() {
        String[] keys = {"TAB", "←", "→", "{", "}", "(", ")", "[", "]", ";", "=", "\"", "'", "<", ">", "/", ":", ".", ",", "_",
                "!", "&", "|", "+", "-", "*", "#", "@", "$", "?", "%", "\\"};
        for (final String k : keys) {
            TextView t = new TextView(this);
            t.setText(k);
            t.setTextSize(14);
            t.setTypeface(Typeface.MONOSPACE);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(UI.color(this, R.attr.bText));
            t.setBackgroundResource(R.drawable.bg_chip);
            int w = k.length() > 1 ? UI.dp(this, 46) : UI.dp(this, 34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, UI.dp(this, 28));
            lp.setMargins(UI.dp(this, 3), 0, UI.dp(this, 3), 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                if (current == null || current.image) return;
                if (k.equals("TAB")) editor.insertTab();
                else if (k.equals("←")) editor.setSelection(Math.max(0, editor.getSelectionStart() - 1));
                else if (k.equals("→")) editor.setSelection(Math.min(editor.length(), editor.getSelectionEnd() + 1));
                else editor.insertAtCursor(k);
            });
            symbolBar.addView(t);
        }
    }

    // ------------------------------------------------------------------ find / replace

    private void setupFindBar() {
        tvCase.setOnClickListener(v -> { matchCase = !matchCase; tvCase.setAlpha(matchCase ? 1f : 0.45f); });
        tvRegex.setOnClickListener(v -> { useRegex = !useRegex; tvRegex.setAlpha(useRegex ? 1f : 0.45f); });
        tvCase.setAlpha(0.45f);
        tvRegex.setAlpha(0.45f);
        findViewById(R.id.ibFindNext).setOnClickListener(v -> doFind(true));
        findViewById(R.id.ibFindPrev).setOnClickListener(v -> doFind(false));
        findViewById(R.id.ibFindClose).setOnClickListener(v -> findBar.setVisibility(View.GONE));
        etFind.setOnEditorActionListener((tv, id, ev) -> { doFind(true); return true; });
        findViewById(R.id.btnReplace).setOnClickListener(v -> {
            if (current == null || current.image) return;
            int n = editor.replaceCurrent(etFind.getText().toString(), etReplace.getText().toString(), matchCase, useRegex);
            if (n < 0) UI.toast(this, "Invalid pattern");
        });
        findViewById(R.id.btnReplaceAll).setOnClickListener(v -> {
            if (current == null || current.image) return;
            int n = editor.replaceAllMatches(etFind.getText().toString(), etReplace.getText().toString(), matchCase, useRegex);
            UI.toast(this, n < 0 ? "Invalid pattern" : n + " replaced");
        });
    }

    private void showFind() {
        findBar.setVisibility(View.VISIBLE);
        etFind.requestFocus();
    }

    private void doFind(boolean fwd) {
        if (current == null || current.image) return;
        int n = editor.find(etFind.getText().toString(), fwd, matchCase, useRegex);
        if (n < 0) UI.toast(this, "Invalid pattern");
        else if (n == 0) UI.toast(this, "No matches");
        else UI.toast(this, n + (n == 1 ? " match" : " matches"));
    }

    private void gotoLineDialog() {
        if (current == null || current.image) return;
        final EditText e = UI.input(this, "Line number", "");
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        UI.dialog(this).setTitle("Go to line").setView(pad(e)).setNegativeButton("Cancel", null)
                .setPositiveButton("Go", (d, w) -> {
                    try { editor.gotoLine(Integer.parseInt(e.getText().toString().trim())); } catch (Exception ignored) { }
                }).show();
    }

    // ------------------------------------------------------------------ explorer

    private void setupExplorer() {
        explorer.post(() -> explorer.setTranslationX(-explorer.getWidth() - UI.dp(this, 8)));
        scrim.setOnClickListener(v -> closeExplorer());
        treeAdapter = new BaseAdapter() {
            @Override public int getCount() { return nodes.size(); }
            @Override public Object getItem(int i) { return nodes.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(IdeActivity.this).inflate(R.layout.item_node, parent, false);
                Node n = nodes.get(i);
                v.setPadding(UI.dp(IdeActivity.this, 6 + n.depth * 14), 0, UI.dp(IdeActivity.this, 10), 0);
                ImageView arrow = v.findViewById(R.id.ivArrow);
                ImageView icon = v.findViewById(R.id.ivNodeIcon);
                TextView name = v.findViewById(R.id.tvNodeName);
                name.setText(n.file.getName());
                boolean dir = n.file.isDirectory();
                if (dir) {
                    arrow.setVisibility(View.VISIBLE);
                    arrow.setImageResource(expanded.contains(n.file.getAbsolutePath()) ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right);
                    icon.setImageResource(R.drawable.ic_folder);
                    icon.setColorFilter(UI.color(IdeActivity.this, R.attr.bAccent));
                } else {
                    arrow.setVisibility(View.INVISIBLE);
                    icon.setImageResource(iconFor(n.file));
                    icon.setColorFilter(UI.color(IdeActivity.this, R.attr.bTextDim));
                }
                boolean isCurrent = current != null && current.file.equals(n.file);
                name.setTextColor(UI.color(IdeActivity.this, isCurrent ? R.attr.bAccent : R.attr.bText));
                return v;
            }
        };
        lvTree.setAdapter(treeAdapter);
        lvTree.setOnItemClickListener((p, v, pos, id) -> {
            File f = nodes.get(pos).file;
            if (f.isDirectory()) {
                String k = f.getAbsolutePath();
                if (!expanded.remove(k)) expanded.add(k);
                rebuildTree();
            } else {
                openFile(f);
                closeExplorer();
            }
        });
        lvTree.setOnItemLongClickListener((p, v, pos, id) -> { nodeMenu(nodes.get(pos).file); return true; });
        findViewById(R.id.ibNewItem).setOnClickListener(v -> createDialog(root));
        findViewById(R.id.ibImportItem).setOnClickListener(v -> importDialog(root));
        findViewById(R.id.ibRefreshTree).setOnClickListener(v -> { project.detectLayout(); rebuildTree(); });
    }

    private int iconFor(File f) {
        String e = FileUtil.ext(f);
        if (e.equals("java") || e.equals("xml") || e.equals("gradle") || e.equals("json") || e.equals("kt") || e.equals("smali")) return R.drawable.ic_code;
        if (FileUtil.isImage(f)) return R.drawable.ic_image;
        if (e.equals("apk")) return R.drawable.ic_android;
        if (e.equals("jar") || e.equals("aar")) return R.drawable.ic_library;
        return R.drawable.ic_file;
    }

    private void openExplorer() {
        explorerOpen = true;
        explorer.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(180).start();
        explorer.animate().translationX(0).setDuration(200).start();
        if (Build.VERSION.SDK_INT >= 31) {
            mainColumn.setRenderEffect(RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP));
        }
        hideKeyboard();
    }

    private void closeExplorer() {
        explorerOpen = false;
        scrim.animate().alpha(0f).setDuration(160).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        explorer.animate().translationX(-explorer.getWidth() - UI.dp(this, 8)).setDuration(180).start();
        if (Build.VERSION.SDK_INT >= 31) mainColumn.setRenderEffect(null);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }

    private void refreshTree() { if (treeAdapter != null) treeAdapter.notifyDataSetChanged(); }

    private void rebuildTree() {
        nodes.clear();
        addChildren(root, 0);
        treeAdapter.notifyDataSetChanged();
    }

    private void addChildren(File dir, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        boolean hidden = Prefs.bool(this, Prefs.SHOW_HIDDEN, false);
        List<File> list = new ArrayList<>();
        for (File k : kids) {
            if (!hidden && k.getName().startsWith(".")) continue;
            list.add(k);
        }
        Collections.sort(list, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT));
            }
        });
        for (File k : list) {
            nodes.add(new Node(k, depth));
            if (k.isDirectory() && expanded.contains(k.getAbsolutePath())) addChildren(k, depth + 1);
        }
    }

    // ------------------------------------------------------------------ file / folder actions

    private void nodeMenu(final File f) {
        final List<String> items = new ArrayList<>();
        if (f.isDirectory()) {
            items.addAll(Arrays.asList("New file", "New folder", "Import file / folder here"));
        } else {
            items.add("Open");
        }
        items.addAll(Arrays.asList("Rename", "Duplicate", "Move (change path)", "Export copy to…"));
        if (f.isDirectory()) items.add("Export as ZIP");
        if (f.getName().endsWith(".apk")) { items.add("Install"); items.add("Share"); }
        items.addAll(Arrays.asList("Copy path", "Properties", "Delete"));
        UI.dialog(this).setTitle(f.getName())
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String it = items.get(which);
                    switch (it) {
                        case "Open": openFile(f); closeExplorer(); break;
                        case "New file": createDialogKind(f, false); break;
                        case "New folder": createDialogKind(f, true); break;
                        case "Import file / folder here": importDialog(f); break;
                        case "Rename": renameDialog(f); break;
                        case "Duplicate": duplicate(f); break;
                        case "Move (change path)": moveDialog(f); break;
                        case "Export copy to…": exportCopy(f); break;
                        case "Export as ZIP": exportZip(f); break;
                        case "Install": Installer.install(this, f); break;
                        case "Share": Installer.share(this, f); break;
                        case "Copy path": UI.copy(this, "path", f.getAbsolutePath()); UI.toast(this, "Path copied"); break;
                        case "Properties": properties(f); break;
                        case "Delete": deleteDialog(f); break;
                    }
                }).show();
    }

    private View pad(View v) {
        LinearLayout l = UI.vbox(this);
        l.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), 0);
        l.addView(v);
        return l;
    }

    private void createDialog(final File dir) {
        UI.dialog(this).setTitle("Create").setItems(new String[]{"New file", "New folder"}, (d, w) -> createDialogKind(dir, w == 1)).show();
    }

    private void createDialogKind(final File dir, final boolean folder) {
        final EditText e = UI.input(this, folder ? "folder name" : "File name (e.g. Utils.java)", "");
        UI.dialog(this).setTitle(folder ? "New folder" : "New file").setMessage("In: " + FileUtil.relPath(root, dir).replaceAll("^$", "/"))
                .setView(pad(e)).setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty() || n.contains("/")) { UI.toast(this, "Invalid name"); return; }
                    File f = new File(dir, n);
                    if (f.exists()) { UI.toast(this, "Already exists"); return; }
                    try {
                        if (folder) f.mkdirs();
                        else FileUtil.writeText(f, defaultContent(f));
                        expanded.add(dir.getAbsolutePath());
                        rebuildTree();
                        if (!folder) { openFile(f); closeExplorer(); }
                    } catch (Exception ex) { UI.toast(this, "Failed: " + ex.getMessage()); }
                }).show();
    }

    private String defaultContent(File f) {
        String n = f.getName();
        if (n.endsWith(".java")) {
            String cls = n.substring(0, n.length() - 5);
            String pkg = "";
            File jr = project.javaRoot();
            if (FileUtil.isInside(jr, f)) {
                String rel = FileUtil.relPath(jr, f.getParentFile());
                pkg = rel.replace('/', '.');
            }
            return Templates.defaultJava(pkg, cls);
        }
        if (n.endsWith(".xml")) {
            File p = f.getParentFile();
            if (p != null && p.getName().startsWith("layout")) return Templates.defaultLayout();
            return Templates.defaultXml();
        }
        return "";
    }

    private void importDialog(final File dir) {
        UI.dialog(this).setTitle("Import into " + (dir.equals(root) ? project.name : dir.getName()))
                .setItems(new String[]{"Import file", "Import folder"}, (d, w) -> {
                    if (w == 0) FilePicker.pickFile(this, "Select a file", android.os.Environment.getExternalStorageDirectory(), null, src -> copyIn(src, dir));
                    else FilePicker.pickFolder(this, "Select a folder", android.os.Environment.getExternalStorageDirectory(), src -> copyIn(src, dir));
                }).show();
    }

    private void copyIn(final File src, final File dir) {
        new Thread(() -> {
            String msg;
            try {
                File dst = FileUtil.unique(new File(dir, src.getName()));
                FileUtil.copy(src, dst);
                msg = "Imported " + dst.getName();
            } catch (Exception e) { msg = "Import failed: " + e.getMessage(); }
            final String m = msg;
            runOnUiThread(() -> { expanded.add(dir.getAbsolutePath()); rebuildTree(); UI.toast(this, m); });
        }).start();
    }

    private void renameDialog(final File f) {
        final EditText e = UI.input(this, "New name", f.getName());
        UI.dialog(this).setTitle("Rename").setView(pad(e)).setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty() || n.contains("/")) { UI.toast(this, "Invalid name"); return; }
                    File dst = new File(f.getParentFile(), n);
                    if (dst.exists()) { UI.toast(this, "Already exists"); return; }
                    saveAll(false);
                    if (!f.renameTo(dst)) { UI.toast(this, "Rename failed"); return; }
                    retarget(f, dst);
                    rebuildTree();
                }).show();
    }

    /** Keep open tabs pointing at moved/renamed files. */
    private void retarget(File from, File to) {
        for (OpenFile t : tabs) {
            if (t.file.equals(from)) t.file = to;
            else if (FileUtil.isInside(from, t.file)) t.file = new File(to, FileUtil.relPath(from, t.file));
            t.lang = Highlighter.langFor(t.file.getName());
        }
        refreshTabs();
        if (current != null) editor.load(editor.getText().toString(), current.lang);
    }

    private void duplicate(final File f) {
        new Thread(() -> {
            try { FileUtil.copy(f, FileUtil.unique(new File(f.getParentFile(), f.getName()))); } catch (Exception ignored) { }
            runOnUiThread(this::rebuildTree);
        }).start();
    }

    private void moveDialog(final File f) {
        FilePicker.pickFolder(this, "Move to folder…", root, dest -> {
            if (f.isDirectory() && FileUtil.isInside(f, dest)) { UI.toast(this, "Cannot move a folder into itself"); return; }
            File dst = new File(dest, f.getName());
            if (dst.exists()) { UI.toast(this, "Target already has that name"); return; }
            saveAll(false);
            try {
                FileUtil.move(f, dst);
                retarget(f, dst);
                rebuildTree();
            } catch (Exception e) { UI.toast(this, "Move failed: " + e.getMessage()); }
        });
    }

    private void exportCopy(final File f) {
        FilePicker.pickFolder(this, "Export to…", android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), dest -> {
            saveAll(false);
            new Thread(() -> {
                String m;
                try {
                    File dst = FileUtil.unique(new File(dest, f.getName()));
                    FileUtil.copy(f, dst);
                    m = "Exported: " + dst.getAbsolutePath();
                } catch (Exception e) { m = "Export failed: " + e.getMessage(); }
                final String mm = m;
                runOnUiThread(() -> UI.toastLong(this, mm));
            }).start();
        });
    }

    private void exportZip(final File dir) {
        FilePicker.pickFolder(this, "Save ZIP to…", android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), dest -> {
            saveAll(false);
            new Thread(() -> {
                String m;
                try {
                    File z = FileUtil.unique(new File(dest, dir.getName() + ".zip"));
                    FileUtil.zipDir(dir, z, true);
                    m = "Saved: " + z.getAbsolutePath();
                } catch (Exception e) { m = "Failed: " + e.getMessage(); }
                final String mm = m;
                runOnUiThread(() -> UI.toastLong(this, mm));
            }).start();
        });
    }

    private void properties(File f) {
        String s = "Path: " + f.getAbsolutePath() + "\nType: " + (f.isDirectory() ? "Folder" : FileUtil.ext(f).toUpperCase(Locale.ROOT) + " file")
                + "\nSize: " + FileUtil.human(FileUtil.sizeOf(f))
                + "\nModified: " + new java.util.Date(f.lastModified())
                + "\nReadable: " + f.canRead() + "   Writable: " + f.canWrite();
        UI.message(this, f.getName(), s);
    }

    private void deleteDialog(final File f) {
        Runnable go = () -> {
            for (OpenFile t : new ArrayList<>(tabs)) if (t.file.equals(f) || FileUtil.isInside(f, t.file)) closeTab(t, true);
            new Thread(() -> {
                FileUtil.deleteRecursive(f);
                runOnUiThread(this::rebuildTree);
            }).start();
        };
        if (!Prefs.bool(this, Prefs.CONFIRM_DELETE, true)) { go.run(); return; }
        UI.dialog(this).setTitle("Delete " + (f.isDirectory() ? "folder" : "file") + "?")
                .setMessage(f.getName()).setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> go.run()).show();
    }

    // ------------------------------------------------------------------ tabs / files

    private void openFile(File f) {
        if (!f.isFile()) return;
        for (OpenFile t : tabs) if (t.file.equals(f)) { switchTo(t); return; }
        OpenFile t = new OpenFile();
        t.file = f;
        t.lang = Highlighter.langFor(f.getName());
        if (FileUtil.isImage(f)) {
            t.image = true;
        } else {
            if (f.length() > 3L * 1024 * 1024) { UI.toast(this, "File too large to edit (" + FileUtil.human(f.length()) + ")"); return; }
            if (FileUtil.isBinaryExt(f) || FileUtil.looksBinary(f)) { UI.toast(this, "Binary file - cannot be edited"); return; }
            try { t.text = FileUtil.readText(f); } catch (Exception e) { UI.toast(this, "Cannot read: " + e.getMessage()); return; }
        }
        tabs.add(t);
        switchTo(t);
    }

    private void storeCurrentState() {
        if (current == null || current.image) return;
        current.text = editor.getText().toString();
        current.selStart = editor.getSelectionStart();
        current.selEnd = editor.getSelectionEnd();
        current.scrollY = editor.getScrollY();
        current.scrollX = editor.getScrollX();
    }

    private void switchTo(OpenFile t) {
        if (current != null && current != t) storeCurrentState();
        current = t;
        if (t.image) {
            editor.setVisibility(View.INVISIBLE);
            emptyEditor.setVisibility(View.GONE);
            ivPreview.setVisibility(View.VISIBLE);
            Bitmap bm = IconUtil.loadFile(t.file);
            ivPreview.setImageBitmap(bm);
            tvStatus.setText(t.file.getName() + (bm != null ? "  •  " + bm.getWidth() + "×" + bm.getHeight() : "") + "  •  " + FileUtil.human(t.file.length()));
        } else {
            ivPreview.setVisibility(View.GONE);
            emptyEditor.setVisibility(View.GONE);
            editor.setVisibility(View.VISIBLE);
            editor.setUndoStack(t.undo);
            lintMessage = "";
            editor.load(t.text == null ? "" : t.text, t.lang);
            editor.setErrors(errorsFor(t.file));
            final OpenFile tt = t;
            editor.post(() -> {
                int len = editor.length();
                editor.setSelection(Math.min(tt.selStart, len), Math.min(tt.selEnd, len));
                editor.scrollTo(tt.scrollX, tt.scrollY);
                updateStatus(editor.currentLine(), editor.currentCol());
            });
        }
        refreshAll();
    }

    private Map<Integer, String> errorsFor(File f) {
        Map<Integer, String> m = buildErrors.get(f.getAbsolutePath());
        return m == null ? new HashMap<Integer, String>() : m;
    }

    private void refreshAll() {
        refreshTabs();
        refreshButtons();
        refreshTree();
        if (current == null) {
            editor.setVisibility(View.INVISIBLE);
            ivPreview.setVisibility(View.GONE);
            emptyEditor.setVisibility(View.VISIBLE);
            tvStatus.setText("");
        }
    }

    private void refreshTabs() {
        tabsBar.removeAllViews();
        for (final OpenFile t : tabs) {
            LinearLayout tab = UI.hbox(this);
            boolean active = t == current;
            tab.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab);
            tab.setPadding(UI.dp(this, 10), 0, UI.dp(this, 4), 0);
            TextView name = new TextView(this);
            name.setText(t.file.getName() + (t.dirty ? " •" : ""));
            name.setTextSize(12);
            name.setSingleLine(true);
            name.setTextColor(UI.color(this, active ? R.attr.bText : R.attr.bTextDim));
            tab.addView(name);
            ImageView x = new ImageView(this);
            x.setImageResource(R.drawable.ic_close);
            x.setColorFilter(UI.color(this, R.attr.bTextDim));
            x.setPadding(UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6));
            tab.addView(x, new LinearLayout.LayoutParams(UI.dp(this, 26), UI.dp(this, 26)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(this, 28));
            lp.setMargins(UI.dp(this, 3), 0, UI.dp(this, 3), 0);
            tab.setLayoutParams(lp);
            tab.setOnClickListener(v -> switchTo(t));
            x.setOnClickListener(v -> closeTab(t, false));
            tabsBar.addView(tab);
            if (active) tabsScroll.post(() -> tabsScroll.smoothScrollTo(Math.max(0, tab.getLeft() - UI.dp(this, 40)), 0));
        }
    }

    private void closeTab(final OpenFile t, boolean discard) {
        if (!discard && t.dirty) {
            if (Prefs.bool(this, Prefs.AUTO_SAVE, true)) { saveTab(t, false); }
            else {
                UI.dialog(this).setTitle("Unsaved changes").setMessage(t.file.getName())
                        .setPositiveButton("Save", (d, w) -> { saveTab(t, false); removeTab(t); })
                        .setNegativeButton("Discard", (d, w) -> removeTab(t))
                        .setNeutralButton("Cancel", null).show();
                return;
            }
        }
        removeTab(t);
    }

    private void removeTab(OpenFile t) {
        int idx = tabs.indexOf(t);
        tabs.remove(t);
        if (current == t) {
            current = null;
            if (!tabs.isEmpty()) switchTo(tabs.get(Math.max(0, Math.min(idx, tabs.size() - 1))));
            else refreshAll();
        } else refreshAll();
    }

    private void closeAllTabs() {
        saveAll(false);
        tabs.clear();
        current = null;
        refreshAll();
    }

    // ------------------------------------------------------------------ saving

    private void saveTab(OpenFile t, boolean toast) {
        if (t.image) return;
        try {
            String text = (t == current) ? editor.getText().toString() : t.text;
            if (text == null) return;
            if (t == current) t.text = text;
            FileUtil.writeText(t.file, text);
            t.dirty = false;
            if (toast) UI.toast(this, "Saved " + t.file.getName());
        } catch (Exception e) {
            UI.toast(this, "Save failed: " + e.getMessage());
        }
        refreshTabs();
        refreshButtons();
    }

    private void saveAll(boolean toast) {
        boolean any = false;
        for (OpenFile t : tabs) if (t.dirty) { saveTab(t, false); any = true; }
        if (toast) UI.toast(this, any ? "All files saved" : "Nothing to save");
    }

    // ------------------------------------------------------------------ build

    private void runBuild() { runBuild(false); }

    private void runBuild(boolean aab) {
        if (BuildManager.get().isRunning()) { BuildRunner.attach(this, project); return; }
        saveAll(false);
        hideKeyboard();
        project = Project.load(root);
        BuildEngine.Options opt = new BuildEngine.Options();
        opt.variant = project.activeVariant();
        opt.aab = aab;
        if (aab && opt.variant.debuggable) UI.toastLong(this, "Tip: export the bundle from a release variant (Build variants).");
        BuildRunner.start(this, project, opt);
    }

    /** Called when a build (possibly finished while the app was in the background) delivers its result. */
    void onBuildFinished(BuildEngine.Result r) {
        buildErrors.clear();
        if (!r.success) {
            for (BuildEngine.Diag d : r.diags) {
                if (!d.error || d.file == null || d.line <= 0) continue;
                Map<Integer, String> m = buildErrors.get(d.file);
                if (m == null) { m = new HashMap<>(); buildErrors.put(d.file, m); }
                if (!m.containsKey(d.line)) m.put(d.line, d.message);
            }
        }
        if (current != null && !current.image) editor.setErrors(errorsFor(current.file));
        project = Project.load(root);
        rebuildTree();
    }

    /** Opens a file and puts the caret on a line (used by the error list). */
    void openFileAt(String path, int line) {
        File f = new File(path);
        if (!f.isFile()) return;
        openFile(f);
        final int ln = line;
        if (ln > 0) editor.postDelayed(() -> editor.gotoLine(ln), 150);
        if (explorerOpen) closeExplorer();
    }

    /** "Fix" button of a missing-import error: pick the class and add the import line. */
    void fixMissingImport(final String path, final String simple) {
        final List<String> cands = new ArrayList<>();
        if (simple.equals("R")) cands.add(project.packageName + ".R");
        List<File> dirs = new ArrayList<>(project.javaDirs);
        cands.addAll(ImportIndex.candidates(Toolchain.androidJar(this), simple, dirs));
        if (cands.isEmpty()) { UI.toast(this, "No class named " + simple + " found. Add the library in Libraries, or check the spelling."); return; }
        if (cands.size() == 1) { applyImport(path, cands.get(0)); return; }
        UI.dialog(this).setTitle("Import " + simple).setItems(cands.toArray(new String[0]), (d, w) -> applyImport(path, cands.get(w))).show();
    }

    private void applyImport(String path, String fqcn) {
        File f = new File(path);
        try {
            OpenFile open = null;
            for (OpenFile t : tabs) if (t.file.equals(f)) open = t;
            if (open != null && open == current && !open.image) {
                String old = editor.getText().toString();
                String neu = ImportIndex.addImport(old, fqcn);
                if (!neu.equals(old)) editor.replaceAll(neu);
            } else if (open != null && open.text != null) {
                open.text = ImportIndex.addImport(open.text, fqcn);
                open.dirty = true;
                saveTab(open, false);
            } else {
                FileUtil.writeText(f, ImportIndex.addImport(FileUtil.readText(f), fqcn));
            }
            UI.toast(this, "Added import " + fqcn);
        } catch (Exception e) {
            UI.toast(this, "Could not add import: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ project actions

    private void openLibraries() {
        saveAll(false);
        Intent i = new Intent(this, LibrariesActivity.class);
        i.putExtra("root", root.getAbsolutePath());
        startActivity(i);
    }

    private Spinner sdkSpinner(int value) {
        String[] names = new String[14];
        for (int i = 0; i < names.length; i++) names[i] = String.valueOf(21 + i);
        Spinner s = new Spinner(this);
        s.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        s.setSelection(Math.max(0, Math.min(13, value - 21)));
        return s;
    }

    private void projectSettings() {
        LinearLayout form = UI.vbox(this);
        form.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), 0);
        final EditText appName = UI.input(this, "App name", project.appName);
        final EditText vName = UI.input(this, "Version name", project.versionName);
        final EditText vCode = UI.numberInput(this, "Version code", project.versionCode);
        final Spinner min = sdkSpinner(project.minSdk);
        final Spinner tgt = sdkSpinner(project.targetSdk);
        form.addView(UI.label(this, "Package: " + project.packageName + "  (edit AndroidManifest.xml to change)", 11, true));
        form.addView(UI.label(this, "App name", 12, true));
        form.addView(appName);
        LinearLayout r1 = UI.hbox(this);
        LinearLayout c1 = UI.vbox(this); c1.addView(UI.label(this, "Version name", 12, true)); c1.addView(vName);
        LinearLayout c2 = UI.vbox(this); c2.addView(UI.label(this, "Version code", 12, true)); c2.addView(vCode);
        r1.addView(c1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r1.addView(c2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        form.addView(r1);
        LinearLayout r2 = UI.hbox(this);
        LinearLayout c3 = UI.vbox(this); c3.addView(UI.label(this, "Min SDK", 12, true)); c3.addView(min);
        LinearLayout c4 = UI.vbox(this); c4.addView(UI.label(this, "Target SDK", 12, true)); c4.addView(tgt);
        r2.addView(c3, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r2.addView(c4, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r2.setPadding(0, UI.dp(this, 10), 0, 0);
        form.addView(r2);
        UI.dialog(this).setTitle("Project settings").setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    saveAll(false);
                    String an = appName.getText().toString().trim();
                    if (!an.isEmpty() && !an.equals(project.appName)) updateAppName(an);
                    project.appName = an.isEmpty() ? project.appName : an;
                    project.versionName = vName.getText().toString().trim().isEmpty() ? "1.0" : vName.getText().toString().trim();
                    try { project.versionCode = Integer.parseInt(vCode.getText().toString().trim()); } catch (Exception ignored) { }
                    project.minSdk = 21 + min.getSelectedItemPosition();
                    project.targetSdk = Math.max(project.minSdk, 21 + tgt.getSelectedItemPosition());
                    try { project.save(); UI.toast(this, "Project settings saved"); } catch (Exception e) { UI.toast(this, e.getMessage()); }
                }).show();
    }

    private void updateAppName(String an) {
        File sx = new File(project.resDir, "values/strings.xml");
        try {
            if (sx.isFile()) {
                String t = FileUtil.readText(sx);
                String esc = an.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'");
                String n = t.replaceFirst("(<string\\s+name=\"app_name\"[^>]*>)[^<]*(</string>)", "$1" + java.util.regex.Matcher.quoteReplacement(esc) + "$2");
                if (!n.equals(t)) {
                    FileUtil.writeText(sx, n);
                    for (OpenFile o : tabs) if (o.file.equals(sx)) { o.text = n; if (o == current) editor.load(n, o.lang); }
                }
            }
        } catch (Exception ignored) { }
    }

    private void changeIcon() {
        final Bitmap[] bm = {IconUtil.loadDefault(this)};
        LinearLayout box = UI.vbox(this);
        box.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), 0);
        final ImageView prev = new ImageView(this);
        box.addView(prev, new LinearLayout.LayoutParams(UI.dp(this, 96), UI.dp(this, 96)));
        final TextView lbl = UI.label(this, "", 12, true);
        box.addView(lbl);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(50);
        sb.setProgress(project.iconRadius);
        box.addView(sb);
        final Runnable upd = () -> {
            lbl.setText("Corner radius: " + sb.getProgress() + "%");
            prev.setImageBitmap(IconUtil.rounded(bm[0], 192, sb.getProgress()));
        };
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { upd.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        upd.run();
        TextView gal = UI.button(this, "Pick from Gallery", true);
        TextView pick = UI.button(this, "Choose from files…", false);
        TextView def = UI.button(this, "Use Buildora icon", false);
        box.addView(gal);
        box.addView(pick);
        box.addView(def);
        gal.setOnClickListener(v -> {
            pendingIcon = bmp -> { bm[0] = bmp; upd.run(); };
            GalleryPicker.pick(this, REQ_GALLERY);
        });
        pick.setOnClickListener(v -> FilePicker.pickFile(this, "Choose icon image", android.os.Environment.getExternalStorageDirectory(),
                new String[]{"png", "jpg", "jpeg", "webp"}, f -> {
                    Bitmap b2 = IconUtil.loadFile(f);
                    if (b2 != null) { bm[0] = b2; upd.run(); }
                }));
        def.setOnClickListener(v -> { bm[0] = IconUtil.loadDefault(this); upd.run(); });
        UI.dialog(this).setTitle("App icon").setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> {
                    try {
                        IconUtil.writeMipmaps(bm[0], project.resDir, sb.getProgress());
                        project.iconRadius = sb.getProgress();
                        project.save();
                        rebuildTree();
                        UI.toast(this, "Icon updated");
                    } catch (Exception e) { UI.toast(this, "Failed: " + e.getMessage()); }
                }).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_GALLERY && res == RESULT_OK && data != null && data.getData() != null && pendingIcon != null) {
            Bitmap b = GalleryPicker.decode(this, data.getData(), 1024);
            if (b == null) UI.toast(this, "Cannot read that image");
            else pendingIcon.onIcon(b);
        }
    }

    private void cleanProject() {
        UI.dialog(this).setTitle("Clean project?").setMessage("Deletes intermediate build files and the built APK.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clean", (d, w) -> {
                    FileUtil.deleteRecursive(project.workDir());
                    project.apkFile().delete();
                    FileUtil.deleteRecursive(new File(project.root, "outputs"));
                    buildErrors.clear();
                    if (current != null && !current.image) editor.setErrors(null);
                    rebuildTree();
                    UI.toast(this, "Project cleaned");
                }).show();
    }

    private void showBuildLog() {
        File log = project.buildLog();
        if (!log.isFile()) { UI.toast(this, "No build log yet"); return; }
        try {
            final String t = FileUtil.readText(log);
            TextView tv = new TextView(this);
            tv.setText(t.length() > 60000 ? t.substring(t.length() - 60000) : t);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(10);
            tv.setTextIsSelectable(true);
            tv.setTextColor(UI.color(this, R.attr.bText));
            tv.setPadding(UI.dp(this, 16), UI.dp(this, 8), UI.dp(this, 16), UI.dp(this, 8));
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(tv);
            UI.dialog(this).setTitle("Last build log").setView(sv)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Copy", (d, w) -> { UI.copy(this, "build log", t); UI.toast(this, "Copied"); }).show();
        } catch (Exception e) { UI.toast(this, e.getMessage()); }
    }

    private void installBuilt() {
        File apk = project.apkFile();
        if (!apk.isFile()) { UI.toast(this, "Build the project first (▶)"); return; }
        Installer.install(this, apk);
    }

    // ------------------------------------------------------------------ git

    private void gitTask(final String title, final GitJob job) {
        if (!GitHelper.isRepo(root) && !title.equals("Git init")) {
            UI.message(this, "Not a Git repository", "This project has no .git folder. Clone a repository from the home screen to use Git.");
            return;
        }
        saveAll(false);
        LinearLayout l = UI.hbox(this);
        l.setPadding(UI.dp(this, 24), UI.dp(this, 20), UI.dp(this, 24), UI.dp(this, 20));
        l.addView(new ProgressBar(this), new LinearLayout.LayoutParams(UI.dp(this, 36), UI.dp(this, 36)));
        TextView t = UI.label(this, title + "…", 15, false);
        t.setPadding(UI.dp(this, 16), 0, 0, 0);
        l.addView(t);
        final AlertDialog wait = UI.dialog(this).setView(l).setCancelable(false).create();
        wait.show();
        new Thread(() -> {
            String res;
            try { res = job.run(); } catch (Throwable e) { res = "Failed: " + e.getMessage(); }
            final String out = res;
            runOnUiThread(() -> { wait.dismiss(); rebuildTree(); UI.message(this, title, out); });
        }).start();
    }

    private interface GitJob { String run() throws Exception; }

    private void gitStatus() { gitTask("Git status", () -> GitHelper.status(root)); }

    private void gitCommit() {
        if (!GitHelper.isRepo(root)) { gitTask("Git commit", () -> ""); return; }
        final EditText e = UI.input(this, "Commit message", "Update from Buildora");
        UI.dialog(this).setTitle("Commit all changes").setView(pad(e)).setNegativeButton("Cancel", null)
                .setPositiveButton("Commit", (d, w) -> gitTask("Git commit", () -> GitHelper.commit(root,
                        e.getText().toString().trim().isEmpty() ? "Update" : e.getText().toString().trim(),
                        Prefs.str(this, Prefs.GIT_NAME, ""), Prefs.str(this, Prefs.GIT_EMAIL, "")))).show();
    }

    private void gitPull() {
        gitTask("Git pull", () -> GitHelper.pull(root, Prefs.str(this, Prefs.GIT_USER, ""), Prefs.str(this, Prefs.GIT_TOKEN, "")));
    }

    private void gitPush() {
        gitTask("Git push", () -> GitHelper.push(root, Prefs.str(this, Prefs.GIT_USER, ""), Prefs.str(this, Prefs.GIT_TOKEN, "")));
    }
}
