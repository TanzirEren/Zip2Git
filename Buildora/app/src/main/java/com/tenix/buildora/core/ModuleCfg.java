package com.tenix.buildora.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An extra module of a project (the "app" module is described by Project itself).
 * Library modules are compiled together with the app: their sources, resources and manifest
 * are merged into the app build (resources of the app override the module's).
 */
public class ModuleCfg {
    public static final String LIBRARY = "library";   // Android library: manifest + res + java
    public static final String JAVA = "java";         // plain Java library: java sources only

    public String name;
    public String path;                 // relative to the project root
    public String type = LIBRARY;
    public List<String> moduleDeps = new ArrayList<>();
    public List<String> dependencies = new ArrayList<>();   // Maven coordinates

    // detected layout
    public File dir, srcRoot, manifestFile, resDir, assetsDir;
    public List<File> javaDirs = new ArrayList<>();
    public List<File> libDirs = new ArrayList<>();
    public String packageName = "";

    public ModuleCfg() { }

    public ModuleCfg(String name, String type) {
        this.name = name;
        this.path = name;
        this.type = type;
    }

    public void detect(File projectRoot) {
        dir = new File(projectRoot, path);
        File[] cands = {new File(dir, "src/main"), dir};
        srcRoot = null;
        for (File c : cands) if (new File(c, "AndroidManifest.xml").isFile()) { srcRoot = c; break; }
        if (srcRoot == null) srcRoot = new File(dir, "src/main");
        manifestFile = new File(srcRoot, "AndroidManifest.xml");
        resDir = new File(srcRoot, "res");
        assetsDir = new File(srcRoot, "assets");
        javaDirs = new ArrayList<>();
        for (String j : new String[]{"java", "src"}) {
            File d = new File(srcRoot, j);
            if (d.isDirectory()) javaDirs.add(d);
        }
        libDirs = new ArrayList<>();
        for (File l : new File[]{new File(dir, "libs"), new File(srcRoot, "libs")}) {
            if (l.isDirectory() && !libDirs.contains(l)) libDirs.add(l);
        }
        packageName = "";
        if (manifestFile.isFile()) {
            try {
                Matcher m = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"").matcher(FileUtil.readText(manifestFile));
                if (m.find()) packageName = m.group(1);
            } catch (IOException ignored) { }
        }
    }

    public boolean isAndroidLibrary() { return LIBRARY.equals(type); }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("path", path);
        o.put("type", type);
        JSONArray md = new JSONArray();
        for (String s : moduleDeps) md.put(s);
        o.put("moduleDeps", md);
        JSONArray d = new JSONArray();
        for (String s : dependencies) d.put(s);
        o.put("dependencies", d);
        return o;
    }

    public static ModuleCfg fromJson(JSONObject o) {
        ModuleCfg m = new ModuleCfg();
        m.name = o.optString("name", "module");
        m.path = o.optString("path", m.name);
        m.type = o.optString("type", LIBRARY);
        JSONArray md = o.optJSONArray("moduleDeps");
        if (md != null) for (int i = 0; i < md.length(); i++) m.moduleDeps.add(md.optString(i));
        JSONArray d = o.optJSONArray("dependencies");
        if (d != null) for (int i = 0; i < d.length(); i++) m.dependencies.add(d.optString(i));
        return m;
    }
}
