package com.tenix.buildora.build;

import android.content.Context;

import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.Prefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Registry of signing keys the user generated/imported. Files live in app-private storage. */
public final class Keystores {
    private Keystores() {}

    public static final String DEBUG_ID = "debug";

    public static final class Entry {
        public String id, name, file, alias, storePass, keyPass;
        public File path(Context c) { return new File(dir(c), file); }
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "keystores");
        d.mkdirs();
        return d;
    }

    public static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.str(c, Prefs.KEYSTORES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.getString("id");
                e.name = o.optString("name", e.id);
                e.file = o.getString("file");
                e.alias = o.getString("alias");
                e.storePass = o.getString("sp");
                e.keyPass = o.getString("kp");
                out.add(e);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    private static void saveAll(Context c, List<Entry> list) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("file", e.file);
                o.put("alias", e.alias);
                o.put("sp", e.storePass);
                o.put("kp", e.keyPass);
                a.put(o);
            }
        } catch (JSONException ignored) { }
        Prefs.put(c, Prefs.KEYSTORES, a.toString());
    }

    public static Entry add(Context c, String name, File ksFile, String alias, String sp, String kp) {
        Entry e = new Entry();
        e.id = UUID.randomUUID().toString().substring(0, 8);
        e.name = name;
        e.file = ksFile.getName();
        e.alias = alias;
        e.storePass = sp;
        e.keyPass = kp;
        List<Entry> l = list(c);
        l.add(e);
        saveAll(c, l);
        return e;
    }

    public static void remove(Context c, Entry e) {
        List<Entry> l = list(c);
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(e.id)) l.remove(i--);
        saveAll(c, l);
        e.path(c).delete();
        if (e.id.equals(Prefs.str(c, Prefs.KEYSTORE_ID, DEBUG_ID))) Prefs.put(c, Prefs.KEYSTORE_ID, DEBUG_ID);
    }

    public static String activeId(Context c) { return Prefs.str(c, Prefs.KEYSTORE_ID, DEBUG_ID); }
    public static void setActive(Context c, String id) { Prefs.put(c, Prefs.KEYSTORE_ID, id); }

    /** The active key; creates the shared debug key on first use. */
    public static Entry active(Context c) throws Exception {
        String id = activeId(c);
        if (!id.equals(DEBUG_ID)) {
            for (Entry e : list(c)) if (e.id.equals(id) && e.path(c).isFile()) return e;
        }
        return debug(c);
    }

    public static Entry debug(Context c) throws Exception {
        Entry e = new Entry();
        e.id = DEBUG_ID;
        e.name = "Buildora debug key";
        e.file = "buildora_debug.p12";
        e.alias = "buildora";
        e.storePass = "android";
        e.keyPass = "android";
        File f = e.path(c);
        if (!f.isFile()) {
            KeyTool.generate(f, e.alias, e.storePass.toCharArray(), e.keyPass.toCharArray(),
                    "Buildora Debug", "Buildora", "TENIx", "", "", "US", 30, 2048);
        }
        return e;
    }

    public static File exportDir() {
        File d = new File(com.tenix.buildora.core.Project.baseDir(), "keystores");
        d.mkdirs();
        return d;
    }

    public static void export(Context c, Entry e) throws IOException {
        FileUtil.copyFile(e.path(c), new File(exportDir(), e.name.replaceAll("[^A-Za-z0-9._-]", "_") + ".p12"));
    }
}
