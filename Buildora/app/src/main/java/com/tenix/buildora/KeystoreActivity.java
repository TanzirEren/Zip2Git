package com.tenix.buildora;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.tenix.buildora.build.KeyTool;
import com.tenix.buildora.build.Keystores;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.FilePicker;
import com.tenix.buildora.core.UI;

import java.io.File;
import java.util.List;

/** Create / import / export / choose the key used to sign built apps. */
public class KeystoreActivity extends BaseActivity {

    private LinearLayout list;

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
        TextView title = UI.label(this, "Signing keys", 20, false);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        top.addView(title);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = UI.vbox(this);
        body.setPadding(UI.dp(this, 16), UI.dp(this, 4), UI.dp(this, 16), UI.dp(this, 32));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        body.addView(UI.label(this, "The selected key signs every APK Buildora builds. Keep your own keys safe: an app can only be updated with the same key.", 12, true));
        TextView gen = UI.button(this, "Generate new key", true);
        TextView imp = UI.button(this, "Import keystore (.p12 / .jks / .bks)", false);
        body.addView(gen);
        body.addView(imp);
        list = UI.vbox(this);
        list.setPadding(0, UI.dp(this, 16), 0, 0);
        body.addView(list);
        setContentView(root);
        styleBars();
        gen.setOnClickListener(v -> generateDialog());
        imp.setOnClickListener(v -> importDialog());
        render();
    }

    private void render() {
        list.removeAllViews();
        String active = Keystores.activeId(this);
        try {
            addRow(Keystores.debug(this), active.equals(Keystores.DEBUG_ID) || Keystores.list(this).isEmpty() && active.equals(Keystores.DEBUG_ID), true);
        } catch (Exception e) {
            list.addView(UI.label(this, "Debug key error: " + e.getMessage(), 12, true));
        }
        List<Keystores.Entry> all = Keystores.list(this);
        for (Keystores.Entry e : all) addRow(e, e.id.equals(active), false);
    }

    private void addRow(final Keystores.Entry e, boolean selected, final boolean isDebug) {
        LinearLayout r = UI.hbox(this);
        r.setBackgroundResource(R.drawable.bg_card);
        r.setPadding(UI.dp(this, 14), UI.dp(this, 12), UI.dp(this, 14), UI.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 8);
        r.setLayoutParams(lp);
        LinearLayout col = UI.vbox(this);
        col.addView(UI.label(this, e.name, 15, false));
        col.addView(UI.label(this, "alias: " + e.alias + (isDebug ? "  •  auto-generated" : ""), 12, true));
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (selected) {
            TextView t = UI.label(this, "ACTIVE", 11, false);
            t.setTextColor(UI.color(this, R.attr.bAccent));
            r.addView(t);
        }
        r.setOnClickListener(v -> { Keystores.setActive(this, e.id); render(); UI.toast(this, e.name + " is now the signing key"); });
        r.setOnLongClickListener(v -> { rowMenu(e, isDebug); return true; });
        list.addView(r);
    }

    private void rowMenu(final Keystores.Entry e, boolean isDebug) {
        final String[] items = isDebug ? new String[]{"Use this key", "Details", "Export"}
                : new String[]{"Use this key", "Details", "Export", "Delete"};
        UI.dialog(this).setTitle(e.name).setItems(items, (d, w) -> {
            switch (w) {
                case 0: Keystores.setActive(this, e.id); render(); break;
                case 1: details(e); break;
                case 2:
                    try { Keystores.export(this, e); UI.toastLong(this, "Exported to " + Keystores.exportDir().getAbsolutePath()); }
                    catch (Exception ex) { UI.toast(this, ex.getMessage()); }
                    break;
                case 3:
                    UI.dialog(this).setTitle("Delete key?").setMessage("Apps signed with it can no longer be updated unless you keep a backup.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (dd, ww) -> { Keystores.remove(this, e); render(); }).show();
                    break;
            }
        }).show();
    }

    private void details(Keystores.Entry e) {
        try {
            KeyTool.Info i = KeyTool.describe(e.path(this), e.storePass.toCharArray(), e.alias);
            String s = "Alias: " + i.alias + "\nType: " + i.type + "\nOwner: " + i.owner + "\nValid: " + i.validFrom + " → " + i.validTo
                    + "\n\nSHA-1:\n" + i.sha1 + "\n\nSHA-256:\n" + i.sha256;
            AlertDialog d = UI.message(this, e.name, s);
            TextView m = d.findViewById(android.R.id.message);
            if (m != null) m.setTextIsSelectable(true);
        } catch (Exception ex) {
            UI.message(this, "Cannot read key", String.valueOf(ex.getMessage()));
        }
    }

    // ------------------------------------------------------------------ generate

    private LinearLayout labeled(String label, View v) {
        LinearLayout l = UI.vbox(this);
        TextView t = UI.label(this, label, 12, true);
        t.setPadding(0, UI.dp(this, 10), 0, 0);
        l.addView(t);
        l.addView(v);
        return l;
    }

    private void generateDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout f = UI.vbox(this);
        f.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), UI.dp(this, 8));
        sv.addView(f);
        final EditText name = UI.input(this, "My release key", "My release key");
        final EditText alias = UI.input(this, "alias", "key0");
        final EditText pass = UI.input(this, "Password (min 6 chars)", "");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        final EditText cn = UI.input(this, "Your name or app name", "");
        final EditText ou = UI.input(this, "Organizational unit", "");
        final EditText o = UI.input(this, "Organization", "");
        final EditText city = UI.input(this, "City", "");
        final EditText st = UI.input(this, "State / region", "");
        final EditText c = UI.input(this, "Country code (e.g. BD)", "");
        final EditText years = UI.numberInput(this, "Validity (years)", 30);
        final Spinner size = new Spinner(this);
        size.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"2048 bit", "4096 bit"}));
        f.addView(labeled("Key name", name));
        f.addView(labeled("Alias", alias));
        f.addView(labeled("Password (keystore & key)", pass));
        f.addView(labeled("Common name (CN)", cn));
        f.addView(labeled("Organizational unit (OU)", ou));
        f.addView(labeled("Organization (O)", o));
        f.addView(labeled("City (L)", city));
        f.addView(labeled("State (ST)", st));
        f.addView(labeled("Country code (C)", c));
        f.addView(labeled("Validity in years", years));
        f.addView(labeled("RSA key size", size));
        final AlertDialog dlg = UI.dialog(this).setTitle("Generate signing key").setView(sv)
                .setPositiveButton("Generate", null).setNegativeButton("Cancel", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String pw = pass.getText().toString();
            if (pw.length() < 6) { pass.setError("At least 6 characters"); return; }
            final String al = alias.getText().toString().trim().isEmpty() ? "key0" : alias.getText().toString().trim();
            final String nm = name.getText().toString().trim().isEmpty() ? al : name.getText().toString().trim();
            String cc = c.getText().toString().trim().toUpperCase();
            if (!cc.isEmpty() && cc.length() != 2) { c.setError("2 letters"); return; }
            int yr;
            try { yr = Integer.parseInt(years.getText().toString().trim()); } catch (Exception e) { yr = 30; }
            final int yrs = Math.max(1, Math.min(100, yr));
            final int bits = size.getSelectedItemPosition() == 1 ? 4096 : 2048;
            final String ccf = cc;
            final String cnv = cn.getText().toString(), ouv = ou.getText().toString(), ov = o.getText().toString(),
                    lv = city.getText().toString(), sv2 = st.getText().toString();
            dlg.dismiss();
            UI.toast(this, "Generating key…");
            new Thread(() -> {
                String msg;
                try {
                    File f2 = FileUtil.unique(new File(Keystores.dir(this), nm.replaceAll("[^A-Za-z0-9._-]", "_") + ".p12"));
                    KeyTool.generate(f2, al, pw.toCharArray(), pw.toCharArray(), cnv.isEmpty() ? nm : cnv, ouv, ov, lv, sv2, ccf, yrs, bits);
                    Keystores.Entry e = Keystores.add(this, nm, f2, al, pw, pw);
                    Keystores.setActive(this, e.id);
                    msg = "Key created and selected: " + nm;
                } catch (Exception ex) { msg = "Failed: " + ex.getMessage(); }
                final String m = msg;
                runOnUiThread(() -> { UI.toastLong(this, m); render(); });
            }).start();
        });
    }

    // ------------------------------------------------------------------ import

    private void importDialog() {
        FilePicker.pickFile(this, "Select keystore file", android.os.Environment.getExternalStorageDirectory(),
                new String[]{"p12", "pfx", "jks", "keystore", "bks", "ks"}, src -> {
                    LinearLayout f = UI.vbox(this);
                    f.setPadding(UI.dp(this, 20), UI.dp(this, 4), UI.dp(this, 20), 0);
                    final EditText name = UI.input(this, "Key name", src.getName());
                    final EditText alias = UI.input(this, "Alias (leave empty to auto-detect)", "");
                    final EditText sp = UI.input(this, "Keystore password", "");
                    sp.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    final EditText kp = UI.input(this, "Key password (empty = same)", "");
                    kp.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    f.addView(name); f.addView(alias); f.addView(sp); f.addView(kp);
                    final AlertDialog dlg = UI.dialog(this).setTitle("Import keystore").setView(f)
                            .setPositiveButton("Import", null).setNegativeButton("Cancel", null).create();
                    dlg.show();
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        String spw = sp.getText().toString();
                        String kpw = kp.getText().toString().isEmpty() ? spw : kp.getText().toString();
                        try {
                            KeyTool.Info i = KeyTool.describe(src, spw.toCharArray(), alias.getText().toString().trim());
                            File dst = FileUtil.unique(new File(Keystores.dir(this), src.getName()));
                            FileUtil.copyFile(src, dst);
                            Keystores.Entry e = Keystores.add(this, name.getText().toString().trim().isEmpty() ? src.getName() : name.getText().toString().trim(),
                                    dst, i.alias, spw, kpw);
                            Keystores.setActive(this, e.id);
                            dlg.dismiss();
                            render();
                            UI.toast(this, "Imported and selected");
                        } catch (Exception ex) {
                            UI.toastLong(this, "Cannot open keystore: " + ex.getMessage());
                        }
                    });
                });
    }
}
