package com.tenix.buildora.core;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Buildora project. Lives in /storage/emulated/0/Buildora/&lt;Project_Name&gt;/ and contains the
 * sources, a buildora.json config, and the built APK.
 */
public class Project {
    public File root;
    public String name;
    public String appName;
    public String packageName = "com.example.app";
    public String versionName = "1.0";
    public int minSdk = 21;
    public int targetSdk = 34;
    public int versionCode = 1;
    public int iconRadius = 22;
    public long createdAt = System.currentTimeMillis();
    public List<String> dependencies = new ArrayList<>();

    // v2: build types, flavors, modules
    public List<BuildTypeCfg> buildTypes = new ArrayList<>();
    public List<FlavorCfg> flavors = new ArrayList<>();
    public String activeBuildType = "debug";
    public String activeFlavor = "";
    public List<ModuleCfg> modules = new ArrayList<>();       // extra modules (not the app)
    public List<String> appModuleDeps = new ArrayList<>();    // names of modules the app depends on

    // layout (detected)
    public File srcRoot;
    public File manifestFile;
    public File resDir;
    public File assetsDir;
    public List<File> javaDirs = new ArrayList<>();
    public List<File> libDirs = new ArrayList<>();

    public static File baseDir() {
        return new File(Environment.getExternalStorageDirectory(), "Buildora");
    }

    public static String sanitize(String n) {
        if (n == null) return "Project";
        String s = n.trim().replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s+", "_");
        while (s.startsWith(".")) s = s.substring(1);
        return s.isEmpty() ? "Project" : s;
    }

    public File configFile() { return new File(root, "buildora.json"); }
    public File workDir() { return new File(root, ".buildora"); }
    public File apkFile() { return activeVariant().apkFile(this); }
    public Variant activeVariant() { return Variant.resolve(this, activeBuildType, activeFlavor); }
    public BuildTypeCfg findBuildType(String n) {
        for (BuildTypeCfg b : buildTypes) if (b.name.equals(n)) return b;
        return null;
    }
    public FlavorCfg findFlavor(String n) {
        for (FlavorCfg f : flavors) if (f.name.equals(n)) return f;
        return null;
    }
    public ModuleCfg findModule(String n) {
        for (ModuleCfg m : modules) if (m.name.equals(n)) return m;
        return null;
    }
    /** Directory of the app module (contains src/ and proguard-rules.pro). */
    public File moduleDir() {
        File d = srcRoot;
        if (d != null && d.getName().equals("main") && d.getParentFile() != null && d.getParentFile().getParentFile() != null)
            return d.getParentFile().getParentFile();
        return srcRoot == null ? root : srcRoot;
    }
    public File proguardFile() { return new File(moduleDir(), "proguard-rules.pro"); }
    /** Modules the app needs, dependencies first. */
    public List<ModuleCfg> orderedModules() {
        List<ModuleCfg> out = new ArrayList<>();
        for (String n : appModuleDeps) visit(n, out, new ArrayList<String>());
        return out;
    }
    private void visit(String n, List<ModuleCfg> out, List<String> stack) {
        ModuleCfg m = findModule(n);
        if (m == null || out.contains(m) || stack.contains(n)) return;
        stack.add(n);
        for (String d : m.moduleDeps) visit(d, out, stack);
        stack.remove(n);
        out.add(m);
    }
    public void ensureDefaults() {
        if (buildTypes.isEmpty()) { buildTypes.add(BuildTypeCfg.debug()); buildTypes.add(BuildTypeCfg.release()); }
        if (findBuildType(activeBuildType) == null) activeBuildType = buildTypes.get(0).name;
        if (!activeFlavor.isEmpty() && findFlavor(activeFlavor) == null) activeFlavor = "";
        for (ModuleCfg m : modules) m.detect(root);
    }
    public File buildLog() { return new File(workDir(), "last-build.log"); }

    public File javaRoot() {
        if (!javaDirs.isEmpty()) return javaDirs.get(0);
        return new File(srcRoot, "java");
    }

    // ------------------------------------------------------------------ load / save

    public static Project load(File root) {
        Project p = new Project();
        p.root = root;
        p.name = root.getName();
        p.detectLayout();
        File cfg = p.configFile();
        boolean parsed = false;
        if (cfg.isFile()) {
            try {
                JSONObject o = new JSONObject(FileUtil.readText(cfg));
                p.appName = o.optString("appName", p.name);
                p.packageName = o.optString("package", p.packageName);
                p.versionName = o.optString("versionName", "1.0");
                p.minSdk = o.optInt("minSdk", 21);
                p.targetSdk = o.optInt("targetSdk", 34);
                p.versionCode = o.optInt("versionCode", 1);
                p.iconRadius = o.optInt("iconRadius", 22);
                p.createdAt = o.optLong("createdAt", cfg.lastModified());
                JSONArray deps = o.optJSONArray("dependencies");
                if (deps != null) for (int i = 0; i < deps.length(); i++) p.dependencies.add(deps.getString(i));
                JSONArray bts = o.optJSONArray("buildTypes");
                if (bts != null) for (int i = 0; i < bts.length(); i++) p.buildTypes.add(BuildTypeCfg.fromJson(bts.getJSONObject(i)));
                JSONArray fls = o.optJSONArray("flavors");
                if (fls != null) for (int i = 0; i < fls.length(); i++) p.flavors.add(FlavorCfg.fromJson(fls.getJSONObject(i)));
                JSONArray mods = o.optJSONArray("modules");
                if (mods != null) for (int i = 0; i < mods.length(); i++) p.modules.add(ModuleCfg.fromJson(mods.getJSONObject(i)));
                JSONArray amd = o.optJSONArray("appModuleDeps");
                if (amd != null) for (int i = 0; i < amd.length(); i++) p.appModuleDeps.add(amd.getString(i));
                p.activeBuildType = o.optString("activeBuildType", "debug");
                p.activeFlavor = o.optString("activeFlavor", "");
                parsed = true;
            } catch (IOException | JSONException ignored) { }
        }
        if (!parsed) {
            p.infer();
            p.ensureDefaults();
            try { p.save(); } catch (IOException ignored) { }
        }
        p.ensureDefaults();
        return p;
    }

    public void save() throws IOException {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("appName", appName);
            o.put("package", packageName);
            o.put("versionName", versionName);
            o.put("versionCode", versionCode);
            o.put("minSdk", minSdk);
            o.put("targetSdk", targetSdk);
            o.put("iconRadius", iconRadius);
            o.put("createdAt", createdAt);
            JSONArray deps = new JSONArray();
            for (String d : dependencies) deps.put(d);
            o.put("dependencies", deps);
            JSONArray bts = new JSONArray();
            for (BuildTypeCfg b : buildTypes) bts.put(b.toJson());
            o.put("buildTypes", bts);
            JSONArray fls = new JSONArray();
            for (FlavorCfg f : flavors) fls.put(f.toJson());
            o.put("flavors", fls);
            JSONArray mods = new JSONArray();
            for (ModuleCfg m : modules) mods.put(m.toJson());
            o.put("modules", mods);
            JSONArray amd = new JSONArray();
            for (String n : appModuleDeps) amd.put(n);
            o.put("appModuleDeps", amd);
            o.put("activeBuildType", activeBuildType);
            o.put("activeFlavor", activeFlavor);
            FileUtil.writeText(configFile(), o.toString(2));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    // ------------------------------------------------------------------ layout

    public void detectLayout() {
        javaDirs = new ArrayList<>();
        libDirs = new ArrayList<>();
        File[] cands = {new File(root, "app/src/main"), new File(root, "src/main"), root};
        srcRoot = null;
        for (File c : cands) {
            if (new File(c, "AndroidManifest.xml").isFile()) { srcRoot = c; break; }
        }
        if (srcRoot == null) {
            File m = findManifest(root);
            if (m != null) srcRoot = m.getParentFile();
        }
        if (srcRoot == null) srcRoot = new File(root, "app/src/main");
        manifestFile = new File(srcRoot, "AndroidManifest.xml");
        resDir = new File(srcRoot, "res");
        assetsDir = new File(srcRoot, "assets");
        for (String j : new String[]{"java", "src"}) {
            File d = new File(srcRoot, j);
            if (d.isDirectory()) javaDirs.add(d);
        }
        File[] libs = {new File(srcRoot, "libs"), new File(root, "libs"), new File(root, "app/libs"),
                new File(srcRoot.getParentFile() == null ? srcRoot : srcRoot.getParentFile(), "libs")};
        List<String> seen = new ArrayList<>();
        for (File l : libs) {
            try {
                String cp = l.getCanonicalPath();
                if (l.isDirectory() && !seen.contains(cp)) { seen.add(cp); libDirs.add(l); }
            } catch (IOException ignored) { }
        }
    }

    private static File findManifest(File root) {
        ArrayDeque<File> q = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        q.add(root);
        depth.add(0);
        while (!q.isEmpty()) {
            File d = q.poll();
            int dep = depth.poll();
            File m = new File(d, "AndroidManifest.xml");
            if (m.isFile()) return m;
            if (dep >= 4) continue;
            File[] kids = d.listFiles();
            if (kids == null) continue;
            for (File k : kids) {
                String n = k.getName();
                if (!k.isDirectory() || n.startsWith(".") || n.equals("build") || n.equals("node_modules")) continue;
                q.add(k);
                depth.add(dep + 1);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ inference for imported projects

    private void infer() {
        appName = name;
        try {
            if (manifestFile.isFile()) {
                String mx = FileUtil.readText(manifestFile);
                String pkg = first(mx, "package\\s*=\\s*\"([^\"]+)\"");
                if (pkg != null) packageName = pkg;
                String label = first(mx, "<application[^>]*android:label\\s*=\\s*\"([^\"]+)\"");
                if (label != null) appName = resolveString(label);
                String mn = first(mx, "android:minSdkVersion\\s*=\\s*\"(\\d+)\"");
                if (mn != null) minSdk = Integer.parseInt(mn);
                String tg = first(mx, "android:targetSdkVersion\\s*=\\s*\"(\\d+)\"");
                if (tg != null) targetSdk = Integer.parseInt(tg);
                String vc = first(mx, "android:versionCode\\s*=\\s*\"(\\d+)\"");
                if (vc != null) versionCode = Integer.parseInt(vc);
                String vn = first(mx, "android:versionName\\s*=\\s*\"([^\"]+)\"");
                if (vn != null) versionName = vn;
            }
            File[] gradles = {new File(root, "app/build.gradle"), new File(root, "app/build.gradle.kts"),
                    new File(srcRoot.getParentFile() == null ? root : srcRoot.getParentFile(), "build.gradle"),
                    new File(root, "build.gradle")};
            for (File g : gradles) {
                if (!g.isFile()) continue;
                String gx = FileUtil.readText(g);
                if (!gx.contains("android")) continue;
                String id = first(gx, "applicationId\\s*=?\\s*[\"']([^\"']+)[\"']");
                if (id == null) id = first(gx, "namespace\\s*=?\\s*[\"']([^\"']+)[\"']");
                if (id != null) packageName = id;
                String mn = first(gx, "minSdk(?:Version)?\\s*=?\\s*(\\d+)");
                if (mn != null) minSdk = Integer.parseInt(mn);
                String tg = first(gx, "targetSdk(?:Version)?\\s*=?\\s*(\\d+)");
                if (tg != null) targetSdk = Integer.parseInt(tg);
                String vc = first(gx, "versionCode\\s*=?\\s*(\\d+)");
                if (vc != null) versionCode = Integer.parseInt(vc);
                String vn = first(gx, "versionName\\s*=?\\s*[\"']([^\"']+)[\"']");
                if (vn != null) versionName = vn;
                Matcher m = Pattern.compile("(?m)^\\s*(?:implementation|api)\\s*\\(?\\s*[\"']([\\w.\\-]+:[\\w.\\-]+:[\\w.\\-]+)[\"']").matcher(gx);
                while (m.find()) {
                    String c = m.group(1);
                    String lc = c.toLowerCase();
                    if (lc.contains("junit") || lc.contains("espresso") || lc.contains("androidx.test")) continue;
                    if (!dependencies.contains(c)) dependencies.add(c);
                }
                break;
            }
        } catch (IOException | RuntimeException ignored) { }
        if (targetSdk < minSdk) targetSdk = Math.max(minSdk, 34);
        try { inferModules(); } catch (IOException | RuntimeException ignored) { }
    }

    /** Reads settings.gradle(.kts) include lines and the project(':x') dependencies of the app module. */
    private void inferModules() throws IOException {
        File sg = new File(root, "settings.gradle");
        if (!sg.isFile()) sg = new File(root, "settings.gradle.kts");
        if (!sg.isFile()) return;
        String text = FileUtil.readText(sg);
        Matcher inc = Pattern.compile("include\\s*\\(?([^\\n)]*)").matcher(text);
        String appDir = moduleDir().getName();
        List<String> names = new ArrayList<>();
        while (inc.find()) {
            Matcher n = Pattern.compile("[\"']:?([\\w\\-./]+)[\"']").matcher(inc.group(1));
            while (n.find()) names.add(n.group(1).replace(':', '/'));
        }
        for (String n : names) {
            if (n.equals(appDir) || modulesContains(n)) continue;
            File dir = new File(root, n);
            if (!dir.isDirectory()) continue;
            String gradle = "";
            for (String g : new String[]{"build.gradle", "build.gradle.kts"}) {
                File gf = new File(dir, g);
                if (gf.isFile()) { gradle = FileUtil.readText(gf); break; }
            }
            ModuleCfg m = new ModuleCfg();
            m.name = n.replace('/', '_');
            m.path = n;
            m.type = gradle.contains("com.android.library") || new File(dir, "src/main/AndroidManifest.xml").isFile()
                    ? ModuleCfg.LIBRARY : ModuleCfg.JAVA;
            Matcher pm = Pattern.compile("project\\(\\s*[\"']:([\\w\\-./]+)[\"']\\s*\\)").matcher(gradle);
            while (pm.find()) m.moduleDeps.add(pm.group(1).replace(':', '_').replace('/', '_'));
            Matcher mm = Pattern.compile("(?m)^\\s*(?:implementation|api)\\s*\\(?\\s*[\"']([\\w.\\-]+:[\\w.\\-]+:[\\w.\\-]+)[\"']").matcher(gradle);
            while (mm.find()) m.dependencies.add(mm.group(1));
            modules.add(m);
        }
        // which modules does the app use?
        for (File g : new File[]{new File(moduleDir(), "build.gradle"), new File(moduleDir(), "build.gradle.kts")}) {
            if (!g.isFile()) continue;
            Matcher pm = Pattern.compile("project\\(\\s*[\"']:([\\w\\-./]+)[\"']\\s*\\)").matcher(FileUtil.readText(g));
            while (pm.find()) {
                String n = pm.group(1).replace(':', '_').replace('/', '_');
                if (findModule(n) != null && !appModuleDeps.contains(n)) appModuleDeps.add(n);
            }
            break;
        }
    }

    private boolean modulesContains(String path) {
        for (ModuleCfg m : modules) if (m.path.equals(path)) return true;
        return false;
    }

    private String resolveString(String ref) {
        if (ref.startsWith("@string/")) {
            String key = ref.substring(8);
            File sx = new File(resDir, "values/strings.xml");
            try {
                if (sx.isFile()) {
                    String v = first(FileUtil.readText(sx), "<string[^>]*name\\s*=\\s*\"" + Pattern.quote(key) + "\"[^>]*>([^<]*)</string>");
                    if (v != null) return v.trim();
                }
            } catch (IOException ignored) { }
            return name;
        }
        return ref;
    }

    private static String first(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    public String packagePath() { return packageName.replace('.', '/'); }

    @Override public String toString() { return name; }
}
