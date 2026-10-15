package com.tenix.buildora;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.tenix.buildora.build.GitHelper;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.GalleryPicker;
import com.tenix.buildora.core.FilePicker;
import com.tenix.buildora.core.IconUtil;
import com.tenix.buildora.core.Perm;
import com.tenix.buildora.core.Prefs;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.ProjectStore;
import com.tenix.buildora.core.Templates;
import com.tenix.buildora.core.UI;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends BaseActivity {

    private static final int REQ_STORAGE = 11;

    private ListView lv;
    private View emptyView;
    private final List<Project> projects = new ArrayList<>();
    private BaseAdapter adapter;
    private AlertDialog permDialog;
    private static final int REQ_GALLERY = 301;
    private interface IconCb { void onIcon(Bitmap b); }
    private IconCb pendingIcon;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        styleBars();
        lv = findViewById(R.id.lvProjects);
        emptyView = findViewById(R.id.emptyView);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return projects.size(); }
            @Override public Object getItem(int i) { return projects.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_project, parent, false);
                Project p = projects.get(i);
                ((TextView) v.findViewById(R.id.tvName)).setText(p.name);
                ((TextView) v.findViewById(R.id.tvSub)).setText(p.packageName + "  •  v" + p.versionName);
                ((TextView) v.findViewById(R.id.tvDate)).setText(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(ProjectStore.lastTouched(p))));
                ImageView iv = v.findViewById(R.id.ivIcon);
                Bitmap bm = null;
                File ic = new File(p.resDir, "mipmap-xhdpi/ic_launcher.png");
                if (ic.isFile()) bm = BitmapFactory.decodeFile(ic.getAbsolutePath());
                if (bm != null) iv.setImageBitmap(bm); else iv.setImageResource(R.mipmap.ic_launcher);
                return v;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) { openProject(projects.get(pos)); }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) {
                projectMenu(projects.get(pos));
                return true;
            }
        });
        findViewById(R.id.fabNew).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fabMenu(); }
        });
        if (android.os.Build.VERSION.SDK_INT >= 33 && !Prefs.bool(this, "asked_notif", false)) {
            Prefs.put(this, "asked_notif", true);
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 12);
        }
        findViewById(R.id.ibSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (!Perm.hasStorage(this)) {
            showPermissionDialog();
        } else {
            if (permDialog != null) { permDialog.dismiss(); permDialog = null; }
            Project.baseDir().mkdirs();
            reload();
        }
    }

    private void showPermissionDialog() {
        if (permDialog != null && permDialog.isShowing()) return;
        permDialog = UI.dialog(this).setTitle("Storage access needed")
                .setMessage("Buildora saves your projects and built APKs in\n/storage/emulated/0/Buildora/\n\nPlease allow access to files.")
                .setCancelable(false)
                .setPositiveButton("Allow", null)
                .create();
        permDialog.show();
        permDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Perm.requestStorage(MainActivity.this, REQ_STORAGE); }
        });
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_GALLERY && res == RESULT_OK && data != null && data.getData() != null && pendingIcon != null) {
            Bitmap b = GalleryPicker.decode(this, data.getData(), 1024);
            if (b == null) UI.toast(this, "Cannot read that image");
            else pendingIcon.onIcon(b);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (Perm.hasStorage(this)) onResume();
    }

    private void reload() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Project> list = ProjectStore.list();
                ui(new Runnable() {
                    @Override public void run() {
                        projects.clear();
                        projects.addAll(list);
                        adapter.notifyDataSetChanged();
                        emptyView.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }).start();
    }

    private void openProject(Project p) {
        Intent i = new Intent(this, IdeActivity.class);
        i.putExtra("root", p.root.getAbsolutePath());
        startActivity(i);
    }

    // ------------------------------------------------------------------ FAB menu

    private void fabMenu() {
        LinearLayout box = UI.vbox(this);
        box.setPadding(UI.dp(this, 16), UI.dp(this, 8), UI.dp(this, 16), UI.dp(this, 8));
        final AlertDialog[] dlg = new AlertDialog[1];
        String[] titles = {"New project", "Import project", "Clone repository"};
        String[] subs = {"Start from a buildable template", "Pick a folder from storage", "Clone from a Git URL"};
        int[] icons = {R.drawable.ic_add, R.drawable.ic_import, R.drawable.ic_git};
        for (int k = 0; k < 3; k++) {
            final int which = k;
            LinearLayout row = UI.hbox(this);
            row.setBackgroundResource(R.drawable.bg_ripple_rect);
            row.setPadding(UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12));
            ImageView iv = new ImageView(this);
            iv.setImageResource(icons[k]);
            iv.setColorFilter(UI.color(this, R.attr.bAccent));
            row.addView(iv, new LinearLayout.LayoutParams(UI.dp(this, 24), UI.dp(this, 24)));
            LinearLayout col = UI.vbox(this);
            col.setPadding(UI.dp(this, 16), 0, 0, 0);
            TextView t = UI.label(this, titles[k], 16, false);
            col.addView(t);
            col.addView(UI.label(this, subs[k], 12, true));
            row.addView(col);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dlg[0].dismiss();
                    if (which == 0) newProjectDialog();
                    else if (which == 1) importProject();
                    else cloneDialog();
                }
            });
            box.addView(row);
        }
        dlg[0] = UI.dialog(this).setTitle("Add project").setView(box).create();
        dlg[0].show();
    }

    // ------------------------------------------------------------------ New project

    private Spinner spinner(String[] items, int sel) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        s.setAdapter(a);
        s.setSelection(sel);
        return s;
    }

    private LinearLayout field(String label, View input) {
        LinearLayout l = UI.vbox(this);
        TextView t = UI.label(this, label, 12, true);
        t.setPadding(0, UI.dp(this, 12), 0, 0);
        l.addView(t);
        l.addView(input);
        return l;
    }

    private void newProjectDialog() {
        final Templates.Options o = new Templates.Options();
        o.minSdk = Prefs.num(this, Prefs.DEF_MIN_SDK, 21);
        o.targetSdk = Prefs.num(this, Prefs.DEF_TARGET_SDK, 34);
        o.iconRadius = Prefs.num(this, Prefs.DEF_RADIUS, 22);
        final String prefix = Prefs.str(this, Prefs.PKG_PREFIX, "com.example");
        final Bitmap[] iconBmp = {IconUtil.loadDefault(this)};
        final boolean[] customIcon = {false};

        ScrollView sv = new ScrollView(this);
        LinearLayout form = UI.vbox(this);
        form.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), UI.dp(this, 8));
        sv.addView(form);

        final EditText name = UI.input(this, "My App", "");
        final EditText appName = UI.input(this, "App name shown on the launcher", "");
        final EditText pkg = UI.input(this, prefix + ".myapp", prefix + ".myapp");
        form.addView(field("Project name", name));
        form.addView(field("App name", appName));
        form.addView(field("Package ID", pkg));
        final boolean[] touched = {false, false};
        appName.addTextChangedListener(new SimpleWatcher() {
            @Override public void after(String s) { if (appName.hasFocus()) touched[0] = true; }
        });
        pkg.addTextChangedListener(new SimpleWatcher() {
            @Override public void after(String s) { if (pkg.hasFocus()) touched[1] = true; }
        });
        name.addTextChangedListener(new SimpleWatcher() {
            @Override public void after(String s) {
                if (!touched[0]) appName.setText(s);
                if (!touched[1]) {
                    String id = s.toLowerCase().replaceAll("[^a-z0-9]", "");
                    if (id.isEmpty() || Character.isDigit(id.charAt(0))) id = "app" + id;
                    pkg.setText(prefix + "." + id);
                }
            }
        });

        // icon
        LinearLayout iconRow = UI.hbox(this);
        iconRow.setPadding(0, UI.dp(this, 14), 0, 0);
        final ImageView preview = new ImageView(this);
        iconRow.addView(preview, new LinearLayout.LayoutParams(UI.dp(this, 64), UI.dp(this, 64)));
        LinearLayout iconCol = UI.vbox(this);
        iconCol.setPadding(UI.dp(this, 14), 0, 0, 0);
        final TextView radiusLabel = UI.label(this, "", 12, true);
        final SeekBar radius = new SeekBar(this);
        radius.setMax(50);
        radius.setProgress(o.iconRadius);
        iconCol.addView(UI.label(this, "App icon (default: Buildora icon)", 12, true));
        iconCol.addView(radiusLabel);
        iconCol.addView(radius, new LinearLayout.LayoutParams(UI.dp(this, 190), ViewGroup.LayoutParams.WRAP_CONTENT));
        iconRow.addView(iconCol);
        form.addView(iconRow);
        final Runnable updatePreview = new Runnable() {
            @Override public void run() {
                radiusLabel.setText("Icon corner radius: " + radius.getProgress() + "%");
                preview.setImageBitmap(IconUtil.rounded(iconBmp[0], 128, radius.getProgress()));
            }
        };
        radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { updatePreview.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        updatePreview.run();
        TextView gallery = UI.button(this, "Pick icon from Gallery", true);
        form.addView(gallery);
        gallery.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pendingIcon = new IconCb() {
                    @Override public void onIcon(Bitmap b) {
                        iconBmp[0] = b;
                        customIcon[0] = true;
                        updatePreview.run();
                    }
                };
                GalleryPicker.pick(MainActivity.this, REQ_GALLERY);
            }
        });
        LinearLayout iconBtns = UI.hbox(this);
        TextView pick = UI.button(this, "Choose from files", false);
        TextView reset = UI.button(this, "Use default", false);
        iconBtns.addView(pick, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        iconBtns.addView(reset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        form.addView(iconBtns);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                FilePicker.pickFile(MainActivity.this, "Choose icon image", android.os.Environment.getExternalStorageDirectory(),
                        new String[]{"png", "jpg", "jpeg", "webp"}, new FilePicker.Callback() {
                            @Override public void onPicked(File f) {
                                Bitmap bm = IconUtil.loadFile(f);
                                if (bm == null) { UI.toast(MainActivity.this, "Cannot read image"); return; }
                                iconBmp[0] = bm;
                                customIcon[0] = true;
                                updatePreview.run();
                            }
                        });
            }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                iconBmp[0] = IconUtil.loadDefault(MainActivity.this);
                customIcon[0] = false;
                updatePreview.run();
            }
        });

        // sdk
        final String[] sdkNames = new String[14];
        for (int i = 0; i < sdkNames.length; i++) sdkNames[i] = String.valueOf(21 + i);
        final Spinner minS = spinner(sdkNames, Math.max(0, Math.min(13, o.minSdk - 21)));
        final Spinner tgtS = spinner(sdkNames, Math.max(0, Math.min(13, o.targetSdk - 21)));
        LinearLayout sdkRow = UI.hbox(this);
        sdkRow.addView(field("Min SDK", minS), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sdkRow.addView(field("Target SDK", tgtS), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        form.addView(sdkRow);
        form.addView(UI.label(this, "Compile API: 34 (set automatically)", 11, true));

        final Spinner tpl = spinner(Templates.NAMES, 0);
        form.addView(field("Template", tpl));
        LinearLayout row2 = UI.hbox(this);
        final Spinner theme = spinner(Templates.THEMES, 0);
        final Spinner orient = spinner(Templates.ORIENTATIONS, 0);
        row2.addView(field("Theme", theme), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row2.addView(field("Orientation", orient), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        form.addView(row2);
        LinearLayout row3 = UI.hbox(this);
        final EditText vName = UI.input(this, "1.0", "1.0");
        final EditText vCode = UI.numberInput(this, "1", 1);
        row3.addView(field("Version name", vName), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row3.addView(field("Version code", vCode), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        form.addView(row3);
        final Switch internet = new Switch(this);
        internet.setText("Internet permission");
        internet.setTextColor(UI.color(this, R.attr.bText));
        internet.setPadding(0, UI.dp(this, 14), 0, UI.dp(this, 6));
        form.addView(internet);

        final AlertDialog dlg = UI.dialog(this).setTitle("New project").setView(sv)
                .setPositiveButton("Create", null).setNegativeButton("Cancel", null).create();
        dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String pn = name.getText().toString().trim();
                String an = appName.getText().toString().trim();
                String pk = pkg.getText().toString().trim();
                if (pn.isEmpty()) { name.setError("Required"); return; }
                if (an.isEmpty()) an = pn;
                if (!Pattern.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+", pk)) {
                    pkg.setError("Use a valid id like com.example.app"); return;
                }
                o.projectName = pn;
                o.appName = an;
                o.packageName = pk;
                o.minSdk = 21 + minS.getSelectedItemPosition();
                o.targetSdk = Math.max(o.minSdk, 21 + tgtS.getSelectedItemPosition());
                o.template = tpl.getSelectedItemPosition();
                o.theme = theme.getSelectedItemPosition();
                o.orientation = orient.getSelectedItemPosition();
                o.versionName = vName.getText().toString().trim().isEmpty() ? "1.0" : vName.getText().toString().trim();
                try { o.versionCode = Integer.parseInt(vCode.getText().toString().trim()); } catch (Exception e) { o.versionCode = 1; }
                o.internet = internet.isChecked();
                o.iconRadius = radius.getProgress();
                o.icon = iconBmp[0];
                dlg.dismiss();
                createProject(o);
            }
        });
    }

    private void createProject(final Templates.Options o) {
        final AlertDialog wait = busy("Creating project…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Project p = Templates.create(MainActivity.this, o);
                    ui(new Runnable() { @Override public void run() { wait.dismiss(); openProject(p); } });
                } catch (final Exception e) {
                    ui(new Runnable() { @Override public void run() { wait.dismiss(); UI.message(MainActivity.this, "Could not create project", String.valueOf(e)); } });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ Import

    private void importProject() {
        FilePicker.pickFolder(this, "Select a project folder", android.os.Environment.getExternalStorageDirectory(),
                new FilePicker.Callback() {
                    @Override public void onPicked(final File dir) {
                        final File base = Project.baseDir();
                        if (dir.getParentFile() != null && dir.getParentFile().equals(base)) { openProject(Project.load(dir)); return; }
                        if (FileUtil.isInside(base, dir) && !dir.equals(base)) { openProject(Project.load(dir)); return; }
                        final EditText nm = UI.input(MainActivity.this, "Project name", dir.getName());
                        UI.dialog(MainActivity.this).setTitle("Import project")
                                .setMessage("The folder will be copied to\n" + base.getAbsolutePath() + "/")
                                .setView(wrap(nm))
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Import", new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface d, int w) {
                                        doImport(dir, nm.getText().toString());
                                    }
                                }).show();
                    }
                });
    }

    private void doImport(final File src, final String name) {
        final AlertDialog wait = busy("Importing…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File dst = ProjectStore.uniqueRoot(name.isEmpty() ? src.getName() : name);
                    FileUtil.copy(src, dst);
                    final Project p = Project.load(dst);
                    ui(new Runnable() { @Override public void run() { wait.dismiss(); openProject(p); } });
                } catch (final Exception e) {
                    ui(new Runnable() { @Override public void run() { wait.dismiss(); UI.message(MainActivity.this, "Import failed", String.valueOf(e)); } });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ Clone

    private void cloneDialog() {
        LinearLayout form = UI.vbox(this);
        form.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), 0);
        final EditText url = UI.input(this, "https://github.com/user/repo.git", "");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText dir = UI.input(this, "Project / directory name", "");
        final EditText branch = UI.input(this, "Branch (optional)", "");
        final boolean[] edited = {false};
        dir.addTextChangedListener(new SimpleWatcher() {
            @Override public void after(String s) { if (dir.hasFocus()) edited[0] = true; }
        });
        url.addTextChangedListener(new SimpleWatcher() {
            @Override public void after(String s) { if (!edited[0]) dir.setText(s.trim().isEmpty() ? "" : GitHelper.repoName(s)); }
        });
        form.addView(field("Repository URL", url));
        form.addView(field("Directory name", dir));
        form.addView(field("Branch", branch));
        form.addView(UI.label(this, "Private repo? Add your username and token in Settings > Git.", 11, true));

        final AlertDialog dlg = UI.dialog(this).setTitle("Clone repository").setView(form)
                .setPositiveButton("Clone", null).setNegativeButton("Cancel", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String u = url.getText().toString().trim();
                if (u.isEmpty()) { url.setError("Required"); return; }
                String n = dir.getText().toString().trim();
                if (n.isEmpty()) n = GitHelper.repoName(u);
                dlg.dismiss();
                doClone(u, n, branch.getText().toString());
            }
        });
    }

    private void doClone(final String url, String name, final String branch) {
        final File target = ProjectStore.uniqueRoot(name);
        final AlertDialog wait = busy("Cloning " + GitHelper.repoName(url) + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    GitHelper.clone(url, target, branch, Prefs.str(MainActivity.this, Prefs.GIT_USER, ""),
                            Prefs.str(MainActivity.this, Prefs.GIT_TOKEN, ""));
                    final Project p = Project.load(target);
                    ui(new Runnable() { @Override public void run() { wait.dismiss(); openProject(p); } });
                } catch (final Throwable e) {
                    FileUtil.deleteRecursive(target);
                    ui(new Runnable() { @Override public void run() { wait.dismiss(); UI.message(MainActivity.this, "Clone failed", String.valueOf(e.getMessage())); } });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ project menu

    private void projectMenu(final Project p) {
        String[] items = {"Open", "Rename", "Duplicate", "Export as ZIP", "Project info", "Delete"};
        UI.dialog(this).setTitle(p.name).setItems(items, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                switch (which) {
                    case 0: openProject(p); break;
                    case 1: renameProject(p); break;
                    case 2: duplicateProject(p); break;
                    case 3: exportProject(p); break;
                    case 4: infoProject(p); break;
                    case 5: deleteProject(p); break;
                }
            }
        }).show();
    }

    private void renameProject(final Project p) {
        final EditText e = UI.input(this, "Project name", p.name);
        UI.dialog(this).setTitle("Rename project").setView(wrap(e)).setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String n = Project.sanitize(e.getText().toString());
                        File dst = new File(Project.baseDir(), n);
                        if (dst.exists()) { UI.toast(MainActivity.this, "A project with that name exists"); return; }
                        if (!p.root.renameTo(dst)) { UI.toast(MainActivity.this, "Rename failed"); return; }
                        reload();
                    }
                }).show();
    }

    private void duplicateProject(final Project p) {
        final AlertDialog wait = busy("Duplicating…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    FileUtil.copy(p.root, ProjectStore.uniqueRoot(p.name + "_copy"));
                } catch (Exception ignored) { }
                ui(new Runnable() { @Override public void run() { wait.dismiss(); reload(); } });
            }
        }).start();
    }

    private void exportProject(final Project p) {
        FilePicker.pickFolder(this, "Export ZIP to…", android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                new FilePicker.Callback() {
                    @Override public void onPicked(final File dest) {
                        final AlertDialog wait = busy("Creating ZIP…");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                String msg;
                                try {
                                    File z = FileUtil.unique(new File(dest, p.name + ".zip"));
                                    FileUtil.zipDir(p.root, z, false);
                                    msg = "Saved: " + z.getAbsolutePath();
                                } catch (Exception e) { msg = "Export failed: " + e.getMessage(); }
                                final String m = msg;
                                ui(new Runnable() { @Override public void run() { wait.dismiss(); UI.toastLong(MainActivity.this, m); } });
                            }
                        }).start();
                    }
                });
    }

    private void infoProject(Project p) {
        String s = "Path: " + p.root.getAbsolutePath()
                + "\nApp name: " + p.appName
                + "\nPackage: " + p.packageName
                + "\nVersion: " + p.versionName + " (" + p.versionCode + ")"
                + "\nMin / Target SDK: " + p.minSdk + " / " + p.targetSdk
                + "\nLibraries: " + p.dependencies.size()
                + "\nSize: " + FileUtil.human(FileUtil.sizeOf(p.root))
                + "\nAPK: " + (p.apkFile().isFile() ? FileUtil.human(p.apkFile().length()) : "not built yet");
        UI.message(this, p.name, s);
    }

    private void deleteProject(final Project p) {
        UI.dialog(this).setTitle("Delete project?")
                .setMessage("\"" + p.name + "\" and all of its files will be permanently deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        new Thread(new Runnable() {
                            @Override public void run() {
                                FileUtil.deleteRecursive(p.root);
                                ui(new Runnable() { @Override public void run() { reload(); } });
                            }
                        }).start();
                    }
                }).show();
    }

    // ------------------------------------------------------------------ helpers

    private View wrap(View v) {
        LinearLayout l = UI.vbox(this);
        l.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), 0);
        l.addView(v);
        return l;
    }

    private AlertDialog busy(String msg) {
        LinearLayout l = UI.hbox(this);
        l.setPadding(UI.dp(this, 24), UI.dp(this, 20), UI.dp(this, 24), UI.dp(this, 20));
        l.addView(new ProgressBar(this), new LinearLayout.LayoutParams(UI.dp(this, 36), UI.dp(this, 36)));
        TextView t = UI.label(this, msg, 15, false);
        t.setPadding(UI.dp(this, 16), 0, 0, 0);
        l.addView(t);
        AlertDialog d = UI.dialog(this).setView(l).setCancelable(false).create();
        d.show();
        return d;
    }

    abstract static class SimpleWatcher implements TextWatcher {
        abstract void after(String s);
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable e) { after(e.toString()); }
    }
}
