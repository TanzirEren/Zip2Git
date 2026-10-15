package com.tenix.buildora.build;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.ModuleCfg;
import com.tenix.buildora.core.Project;
import com.tenix.buildora.core.Variant;

import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The on-device build pipeline (v2):
 * deps -> manifest merge -> aapt2 (cached) -> ECJ (incremental) -> D8 / R8 -> APK or AAB -> sign -> verify.
 * Call {@link #build()} on a background thread.
 */
public final class BuildEngine {

    public interface Listener {
        void onStage(String stage, int percent);
        void onLog(String line);
    }

    public static final class Diag {
        public String file;
        public int line;
        public String message;
        public boolean error = true;
        @Override public String toString() {
            return (file == null ? "" : file + (line > 0 ? ":" + line : "") + ": ") + message;
        }
    }

    public static final class Options {
        public Variant variant;
        public boolean aab;
    }

    public static final class Result {
        public boolean success, cancelled;
        public File apk, aab, mapping;
        public String variant = "";
        public String errorText = "";
        public String log = "";
        public long millis;
        public final List<Diag> diags = new ArrayList<>();
        public File output() { return aab != null ? aab : apk; }
    }

    private static final class BuildException extends Exception {
        final boolean cancel;
        final boolean retryable;   // may be caused by a stale cache: worth one clean retry
        BuildException(String m) { this(m, false, false); }
        BuildException(String m, boolean cancel) { this(m, cancel, false); }
        BuildException(String m, boolean cancel, boolean retryable) { super(m); this.cancel = cancel; this.retryable = retryable; }
    }

    private static final class Libs {
        final List<File> jars = new ArrayList<>();
        final List<File> resDirs = new ArrayList<>();       // lowest priority first after reversal by caller
        final List<File> manifests = new ArrayList<>();
        final List<File> assetDirs = new ArrayList<>();
        final List<File> jniDirs = new ArrayList<>();
        final List<File> proguardFiles = new ArrayList<>();
        final Set<String> packages = new LinkedHashSet<>();
    }

    private final BuildEnv env;
    private final Project p;
    private final Options opt;
    private final Listener listener;
    private final StringBuilder fullLog = new StringBuilder();
    private final StringBuilder stageOut = new StringBuilder();
    private final List<Diag> diags = new ArrayList<>();
    private volatile boolean cancelled;
    private volatile Process current;
    private String stageName = "";
    private boolean usedCache;
    private String retryReason = "";

    public BuildEngine(BuildEnv env, Project p, Options opt, Listener l) {
        this.env = env;
        this.p = p;
        this.opt = opt;
        this.listener = l;
    }

    public void cancel() {
        cancelled = true;
        Process pr = current;
        if (pr != null) pr.destroy();
    }

    // ------------------------------------------------------------------ main flow

    public Result build() {
        long t0 = System.currentTimeMillis();
        Result r = new Result();
        r.variant = opt.variant.name;
        try {
            try {
                doBuild(r, false);
            } catch (BuildException e) {
                if (e.retryable && usedCache && !e.cancel && !cancelled) {
                    retryReason = e.getMessage().split("\n")[0];
                    diags.clear();
                    usedCache = false;
                    doBuild(r, true);
                } else throw e;
            }
        } catch (BuildException e) {
            r.cancelled = e.cancel;
            r.errorText = failureText(e.getMessage());
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            r.errorText = failureText("Unexpected error: " + t + "\n" + trim(sw.toString(), 3000));
        }
        r.millis = System.currentTimeMillis() - t0;
        r.diags.addAll(diags);
        log((r.success ? "BUILD SUCCESSFUL" : r.cancelled ? "BUILD CANCELLED" : "BUILD FAILED") + " in " + (r.millis / 1000.0) + " s");
        r.log = fullLog.toString();
        try { FileUtil.writeText(p.buildLog(), r.log); } catch (IOException ignored) { }
        return r;
    }

    private void doBuild(Result r, boolean freshCache) throws BuildException {
        final Variant v = opt.variant;
        fullLog.setLength(0);
        if (freshCache) {
            log("!! The first attempt failed (" + retryReason + ")");
            log("!! Retrying once with a clean build cache...");
        }
        stage("Preparing " + v.name + (opt.aab ? " (AAB)" : ""), 2);
        try { env.prepare(); } catch (IOException e) { throw new BuildException("Toolchain setup failed: " + e.getMessage()); }
        File aapt2 = env.aapt2();
        File androidJar = env.androidJar();
        if (!aapt2.isFile())
            throw new BuildException("aapt2 is not installed.\nOpen Settings > Toolchain and import an Android (ARM) aapt2 binary,\nor rebuild Buildora with the aapt2 asset (see README).");
        if (!androidJar.isFile())
            throw new BuildException("android.jar is missing. Open Settings > Toolchain and import it.");
        String ver = env.aapt2Version();
        log("aapt2: " + ver);
        if (ver.startsWith("cannot execute") || ver.startsWith("no output"))
            throw new BuildException("aapt2 cannot run on this device:\n" + ver);

        p.detectLayout();
        for (ModuleCfg m : p.modules) m.detect(p.root);
        if (!p.manifestFile.isFile()) throw new BuildException("AndroidManifest.xml not found in " + p.srcRoot);
        if (p.javaDirs.isEmpty())
            throw new BuildException("No Java source folder found (expected 'java' or 'src' next to AndroidManifest.xml).");
        final String origPkg = ManifestMerger.packageOf(p.manifestFile);
        if (origPkg.isEmpty()) throw new BuildException("The manifest has no package attribute.");
        p.packageName = origPkg;
        final int minSdk = v.minSdk, targetSdk = v.targetSdk;
        log("Variant " + v.name + "  •  applicationId " + v.applicationId + "  •  v" + v.versionName + " (" + v.versionCode + ")"
                + (v.minify ? "  •  R8 shrink" : ""));

        // ---------------- gather every source set (modules, app, flavor, build type)
        List<ModuleCfg> mods = p.orderedModules();
        List<File> javaDirs = new ArrayList<>(), resDirs = new ArrayList<>(), assetDirs = new ArrayList<>(),
                libDirs = new ArrayList<>(), overlayManifests = new ArrayList<>();
        Set<String> deps = new LinkedHashSet<>(p.dependencies);
        Set<String> modulePackages = new LinkedHashSet<>();
        for (ModuleCfg m : mods) {
            javaDirs.addAll(m.javaDirs);
            if (hasFiles(m.resDir)) resDirs.add(m.resDir);
            if (hasFiles(m.assetsDir)) assetDirs.add(m.assetsDir);
            libDirs.addAll(m.libDirs);
            deps.addAll(m.dependencies);
            if (m.isAndroidLibrary() && m.manifestFile.isFile()) {
                overlayManifests.add(m.manifestFile);
                if (!m.packageName.isEmpty()) modulePackages.add(m.packageName);
            }
            log("  module :" + m.name + " (" + m.type + ")");
        }
        javaDirs.addAll(p.javaDirs);
        if (hasFiles(p.resDir)) resDirs.add(p.resDir);
        if (hasFiles(p.assetsDir)) assetDirs.add(p.assetsDir);
        libDirs.addAll(p.libDirs);
        for (File sd : new File[]{v.flavorDir, v.typeDir}) {
            if (sd == null || !sd.isDirectory()) continue;
            File j = new File(sd, "java"), rs = new File(sd, "res"), as = new File(sd, "assets"), mf = new File(sd, "AndroidManifest.xml");
            if (j.isDirectory()) javaDirs.add(j);
            if (hasFiles(rs)) resDirs.add(rs);
            if (hasFiles(as)) assetDirs.add(as);
            if (mf.isFile()) overlayManifests.add(mf);
            log("  source set " + sd.getName());
        }
        deps.addAll(v.extraDependencies);
        File gsJson = GoogleServices.find(p.moduleDir(), p.root);
        File proguardFile = p.proguardFile();

        File work = p.workDir();
        File cacheRoot = new File(work, "cache");
        if (freshCache) FileUtil.deleteRecursive(cacheRoot);
        File cacheV = new File(cacheRoot, v.name);
        File resCache = new File(cacheRoot, "res");
        cacheV.mkdirs();
        resCache.mkdirs();
        File out = opt.aab ? v.aabFile(p) : v.apkFile(p);

        // ---------------- nothing changed since the last successful build?
        String overall = overallFingerprint(v, javaDirs, resDirs, assetDirs, overlayManifests, deps, gsJson, proguardFile);
        File overallFile = new File(cacheV, opt.aab ? "overall-aab.fp" : "overall-apk.fp");
        if (env.incremental() && !freshCache && out.isFile() && overallFile.isFile()) {
            try {
                if (FileUtil.readText(overallFile).equals(overall)) {
                    log("Nothing changed since the last build - up to date.");
                    stage("Done", 100);
                    r.success = true;
                    if (opt.aab) r.aab = out; else r.apk = out;
                    File mp = new File(v.outputsDir(p), "mapping.txt");
                    if (mp.isFile()) r.mapping = mp;
                    return;
                }
            } catch (IOException ignored) { }
        }

        File b = new File(work, "build/" + v.name);
        FileUtil.deleteRecursive(b);
        File gen = new File(b, "gen"), resOut = new File(b, "res"), dexOut = new File(b, "dex");
        gen.mkdirs();
        resOut.mkdirs();
        dexOut.mkdirs();
        check();

        // ---------------- 1 libraries
        stage("Resolving libraries", 8);
        Libs libs = resolveLibs(work, libDirs, new ArrayList<>(deps));
        check();

        // ---------------- google-services.json
        File gsRes = null;
        if (gsJson != null) {
            try {
                gsRes = GoogleServices.generate(gsJson, v.applicationId, origPkg, new File(b, "gen_res"));
                log("google-services.json processed (" + gsJson.getName() + ")");
            } catch (IOException e) {
                throw new BuildException(e.getMessage());
            }
        }

        // ---------------- 2 manifest
        stage("Merging manifest", 16);
        File manifest = new File(b, "AndroidManifest.xml");
        List<File> allManifests = new ArrayList<>(libs.manifests);
        allManifests.addAll(overlayManifests);
        try {
            ManifestMerger.merge(p.manifestFile, allManifests, v.applicationId, manifest);
        } catch (Exception e) {
            throw new BuildException("Manifest merge failed: " + e);
        }
        check();

        // ---------------- 3 resources (compiled zips are cached)
        stage("Compiling resources", 26);
        List<File> ordered = new ArrayList<>();            // lowest priority first
        List<File> libRes = new ArrayList<>(libs.resDirs);
        Collections.reverse(libRes);
        ordered.addAll(libRes);
        if (gsRes != null) ordered.add(gsRes);
        ordered.addAll(resDirs);
        List<File> flats = new ArrayList<>();
        int idx = 0, cached = 0;
        for (File rd : ordered) {
            if (!hasFiles(rd)) continue;
            File zip;
            boolean volatileDir = FileUtil.isInside(b, rd);
            if (volatileDir) zip = new File(resOut, "gen" + (idx++) + ".zip");
            else if (FileUtil.isInside(env.m2Dir(), rd) || FileUtil.isInside(new File(work, "aar"), rd)) zip = new File(rd.getParentFile(), "compiled-res.zip");
            else zip = new File(resCache, FileUtil.sha1(rd.getAbsolutePath()) + ".zip");
            if (compileResCached(aapt2, rd, zip, ver, volatileDir)) cached++;
            flats.add(zip);
            check();
        }
        if (cached > 0) { usedCache = true; log("Resources: " + cached + " of " + flats.size() + " compiled sets reused from cache"); }

        stage("Linking resources", 38);
        File assetsStage = new File(b, "assets");
        boolean anyAssets = false;
        for (File ad : libs.assetDirs) { if (hasFiles(ad)) { FileUtil.copyQuiet(ad, assetsStage); anyAssets = true; } }
        for (File ad : assetDirs) { FileUtil.copyQuiet(ad, assetsStage); anyAssets = true; }
        File apRes = new File(b, "resources.ap_");
        File aaptRules = new File(b, "aapt-rules.pro");
        List<String> link = new ArrayList<>(Arrays.asList(aapt2.getPath(), "link",
                "-o", apRes.getPath(), "-I", androidJar.getPath(), "--manifest", manifest.getPath(),
                "--java", gen.getPath(), "--min-sdk-version", String.valueOf(minSdk),
                "--target-sdk-version", String.valueOf(targetSdk),
                "--version-code", String.valueOf(v.versionCode), "--version-name", v.versionName,
                "--auto-add-overlay"));
        if (v.debuggable) link.add("--debug-mode");
        if (!v.applicationId.equals(origPkg)) { link.add("--custom-package"); link.add(origPkg); }
        if (v.minify) { link.add("--proguard"); link.add(aaptRules.getPath()); }
        if (opt.aab) link.add("--proto-format");
        if (anyAssets) { link.add("-A"); link.add(assetsStage.getPath()); }
        Set<String> extra = new LinkedHashSet<>(libs.packages);
        extra.addAll(modulePackages);
        extra.remove(origPkg);
        if (!extra.isEmpty()) { link.add("--extra-packages"); link.add(join(extra, ":")); }
        for (int i = 0; i < flats.size(); i++) {
            if (i > 0) link.add("-R");
            link.add(flats.get(i).getPath());
        }
        aapt(link, true);
        check();

        // ---------------- BuildConfig
        boolean userBuildConfig = false;
        for (File jd : javaDirs) for (File f : FileUtil.listFiles(jd, "BuildConfig.java")) userBuildConfig = true;
        if (!userBuildConfig) {
            try { BuildConfigGen.generate(gen, origPkg, v); } catch (IOException e) { throw new BuildException("BuildConfig: " + e.getMessage()); }
        }

        // ---------------- 4 Java (incremental)
        stage("Compiling Java", 52);
        File classesDir = new File(cacheV, "classes");
        boolean compiled = compileJava(javaDirs, gen, classesDir, new File(cacheV, "java-state.txt"), androidJar, libs);
        check();

        // ---------------- 5 dex / R8
        stage(v.minify ? "Shrinking with R8" : "Dexing (D8)", 72);
        File classesJar = new File(cacheV, "classes.jar");
        if (compiled || !classesJar.isFile()) zipDir(classesDir, classesJar);
        List<File> dexFiles = new ArrayList<>();
        File mapping = null;
        if (v.minify) {
            List<File> program = new ArrayList<>();
            program.add(classesJar);
            program.addAll(libs.jars);
            List<File> rules = new ArrayList<>();
            rules.add(aaptRules);
            rules.add(proguardFile);
            rules.addAll(libs.proguardFiles);
            File mapFile = new File(v.outputsDir(p), "mapping.txt");
            mapFile.getParentFile().mkdirs();
            List<String> msgs = new ArrayList<>();
            try {
                Shrinker.Output so = Shrinker.run(androidJar, program, rules, null, v.obfuscate, v.optimize, minSdk,
                        new File(b, "r8/out"), mapFile, msgs);
                dexFiles.addAll(sortedDex(new File(b, "r8/out")));
                mapping = so.mapping;
            } catch (Exception e) {
                throw new BuildException(String.valueOf(e.getMessage()));
            }
            log("R8 done" + (mapping != null ? "  •  mapping: " + mapping.getPath() : ""));
        } else {
            boolean releaseMode = env.dexRelease() || !v.debuggable;
            List<File> libDex = new ArrayList<>();
            if (!libs.jars.isEmpty()) {
                String key = FileUtil.sha1(minSdk + "|" + releaseMode + "|" + describe(libs.jars));
                File cacheDir = new File(env.dexCacheDir(), key);
                if (!new File(cacheDir, "classes.dex").isFile()) {
                    log("Dexing " + libs.jars.size() + " librar" + (libs.jars.size() == 1 ? "y" : "ies") + " (cached for next time)...");
                    FileUtil.deleteRecursive(cacheDir);
                    cacheDir.mkdirs();
                    dex(libs.jars, androidJar, null, cacheDir, minSdk, releaseMode);
                } else { usedCache = true; log("Libraries dex: cache hit"); }
                libDex.addAll(sortedDex(cacheDir));
            }
            File projDex = new File(cacheV, "dex");
            File projFp = new File(cacheV, "dex.fp");
            String fp = minSdk + "|" + releaseMode;
            boolean redo = compiled || !new File(projDex, "classes.dex").isFile();
            try { if (!redo && !FileUtil.readText(projFp).equals(fp)) redo = true; } catch (IOException e) { redo = true; }
            if (redo) {
                FileUtil.deleteRecursive(projDex);
                projDex.mkdirs();
                dex(Collections.singletonList(classesJar), androidJar, libs.jars, projDex, minSdk, releaseMode);
                try { FileUtil.writeText(projFp, fp); } catch (IOException ignored) { }
            } else { usedCache = true; log("Project dex: reused"); }
            dexFiles.addAll(sortedDex(projDex));
            dexFiles.addAll(libDex);
        }
        check();

        // ---------------- 6 package + sign
        stage(opt.aab ? "Packaging bundle" : "Packaging APK", 86);
        File unsigned = new File(b, opt.aab ? "unsigned.aab" : "unsigned.apk");
        try {
            if (opt.aab) {
                AabBuilder.build(apRes, dexFiles, libs.jars, libs.jniDirs, unsigned);
            } else {
                try (ApkBuilder ab = new ApkBuilder(unsigned)) {
                    ab.addAllFromZip(apRes);
                    int n = 1;
                    for (File d : dexFiles) {
                        byte[] data;
                        try (InputStream in = new java.io.FileInputStream(d)) { data = FileUtil.readAll(in); }
                        ab.addDeflated(n == 1 ? "classes.dex" : "classes" + n + ".dex", data);
                        n++;
                    }
                    for (File jar : libs.jars) ab.addJavaResources(jar);
                    for (File jni : libs.jniDirs) ab.addNativeLibs(jni, env.nativeAlign());
                }
            }
        } catch (IOException e) {
            throw new BuildException("Packaging failed: " + e.getMessage(), false, true);
        }
        check();

        stage("Signing", 94);
        try {
            Signer.Key key = env.signingKey(v.signingKey);
            if (opt.aab) {
                Signer.signBundle(key, unsigned, out);
                log("Bundle signed with: " + key.name);
            } else {
                Signer.sign(key, unsigned, out, minSdk);
                log("Signed with: " + key.name);
                if (env.verifySignature()) log("Signature verified: " + Signer.verify(out, minSdk));
                ZipAlignCheck.Report za = ZipAlignCheck.check(out, env.nativeAlign());
                if (za.ok()) log("zipalign check: OK (" + za.stored + " stored entries aligned)");
                else {
                    log("zipalign check: " + za.misaligned + " misaligned entr" + (za.misaligned == 1 ? "y" : "ies"));
                    for (String s : za.bad) log("   " + s);
                }
            }
        } catch (Exception e) {
            throw new BuildException("Signing failed: " + e.getMessage(), false, true);
        }
        log("Output: " + out.getAbsolutePath() + " (" + FileUtil.human(out.length()) + ")");

        try { FileUtil.writeText(overallFile, overall); } catch (IOException ignored) { }
        if (env.autoVersion()) {
            p.versionCode++;
            try { p.save(); } catch (Throwable ignored) { }
        }
        if (env.cleanIntermediates()) FileUtil.deleteRecursive(b);
        stage("Done", 100);
        r.success = true;
        if (opt.aab) r.aab = out; else r.apk = out;
        r.mapping = mapping;
    }

    // ------------------------------------------------------------------ libraries

    private Libs resolveLibs(File work, List<File> libDirs, List<String> deps) throws BuildException {
        Libs libs = new Libs();
        try {
            for (File dir : libDirs) {
                File[] fs = dir.listFiles();
                if (fs == null) continue;
                Arrays.sort(fs);
                for (File f : fs) {
                    String n = f.getName().toLowerCase();
                    if (n.endsWith(".jar")) { libs.jars.add(f); log("  + local " + f.getName()); }
                    else if (n.endsWith(".aar")) {
                        File ex = new File(work, "aar/" + f.getName() + "-" + f.lastModified());
                        if (!new File(ex, ".ok").isFile()) {
                            FileUtil.deleteRecursive(ex);
                            ex.mkdirs();
                            FileUtil.unzip(f, ex);
                            new File(ex, ".ok").createNewFile();
                        }
                        addAar(libs, ex);
                        log("  + local " + f.getName());
                    }
                }
            }
            if (!deps.isEmpty()) {
                log("Resolving " + deps.size() + " dependenc" + (deps.size() == 1 ? "y" : "ies") + "...");
                MavenResolver mr = new MavenResolver(env.m2Dir(), new MavenResolver.Log() {
                    @Override public void log(String s) { BuildEngine.this.log(s); }
                });
                for (MavenResolver.Artifact a : mr.resolve(deps)) {
                    if (a.aar) addAar(libs, a.extracted());
                    else if (!libs.jars.contains(a.file)) libs.jars.add(a.file);
                }
            }
        } catch (IOException e) {
            throw new BuildException("Dependency resolution failed: " + e.getMessage() +
                    "\nCheck your internet connection or remove the dependency in Libraries.");
        }
        return libs;
    }

    private void addAar(Libs libs, File dir) {
        File cj = new File(dir, "classes.jar");
        if (cj.isFile() && !libs.jars.contains(cj)) libs.jars.add(cj);
        File[] inner = new File(dir, "libs").listFiles();
        if (inner != null) for (File f : inner) if (f.getName().endsWith(".jar") && !libs.jars.contains(f)) libs.jars.add(f);
        File res = new File(dir, "res");
        if (res.isDirectory()) libs.resDirs.add(res);
        File mf = new File(dir, "AndroidManifest.xml");
        if (mf.isFile()) {
            libs.manifests.add(mf);
            String pk = ManifestMerger.packageOf(mf);
            if (!pk.isEmpty()) libs.packages.add(pk);
        }
        File as = new File(dir, "assets");
        if (as.isDirectory()) libs.assetDirs.add(as);
        File jni = new File(dir, "jni");
        if (jni.isDirectory()) libs.jniDirs.add(jni);
        File pg = new File(dir, "proguard.txt");
        if (pg.isFile()) libs.proguardFiles.add(pg);
    }

    // ------------------------------------------------------------------ aapt2

    /** @return true when the compiled zip came from the cache */
    private boolean compileResCached(File aapt2, File resDir, File zip, String aaptVer, boolean volatileDir) throws BuildException {
        String fp = dirFingerprint(resDir) + "|" + aaptVer;
        File fpFile = new File(zip.getPath() + ".fp");
        if (!volatileDir && env.incremental() && zip.isFile() && fpFile.isFile()) {
            try { if (FileUtil.readText(fpFile).equals(fp)) return true; } catch (IOException ignored) { }
        }
        zip.getParentFile().mkdirs();
        aapt(Arrays.asList(aapt2.getPath(), "compile", "--dir", resDir.getPath(), "-o", zip.getPath()), false);
        try { if (!volatileDir) FileUtil.writeText(fpFile, fp); } catch (IOException ignored) { }
        return false;
    }

    private void aapt(List<String> cmd, boolean retryable) throws BuildException {
        File aapt2 = new File(cmd.get(0));
        aapt2.setExecutable(true, false);
        log("$ aapt2 " + cmd.get(1) + " ...");
        int code;
        try {
            code = exec(cmd, p.workDir());
        } catch (IOException | InterruptedException e) {
            throw new BuildException("Could not run aapt2: " + e.getMessage(), cancelled);
        }
        if (cancelled) throw new BuildException("Build cancelled", true);
        if (code != 0) throw new BuildException("aapt2 " + cmd.get(1) + " failed (exit code " + code + ")", false, retryable);
    }

    private static final Pattern AAPT_LINE = Pattern.compile("^(.+?):(\\d+): (error|warning): (.*)$");
    private static final Pattern AAPT_FILE_ERR = Pattern.compile("^(/.+?): error: (.*)$");

    private int exec(List<String> cmd, File dir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        dir.mkdirs();
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        current = pr;
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
            String line;
            while ((line = rd.readLine()) != null) {
                log(line);
                Matcher m = AAPT_LINE.matcher(line);
                if (m.matches()) {
                    addDiag(m.group(1), Integer.parseInt(m.group(2)), m.group(4), m.group(3).equals("error"));
                } else {
                    Matcher m2 = AAPT_FILE_ERR.matcher(line);
                    if (m2.matches()) addDiag(m2.group(1), 0, m2.group(2), true);
                }
            }
        }
        int code = pr.waitFor();
        current = null;
        return code;
    }

    private void addDiag(String file, int line, String msg, boolean error) {
        Diag d = new Diag();
        d.file = file;
        d.line = line;
        d.message = msg;
        d.error = error;
        diags.add(d);
    }

    // ------------------------------------------------------------------ ECJ (incremental)

    /** @return true when at least one file was compiled */
    private boolean compileJava(List<File> javaDirs, File gen, File classesDir, File stateFile, File androidJar, Libs libs) throws BuildException {
        List<File> sourceDirs = new ArrayList<>(javaDirs);
        sourceDirs.add(gen);
        String fp = FileUtil.sha1("ecj1.8|" + androidJar.length() + ":" + androidJar.lastModified() + "|" + describe(libs.jars));
        IncrementalJava inc = new IncrementalJava(stateFile, classesDir);
        IncrementalJava.Plan plan;
        try {
            if (!env.incremental()) { inc.invalidate(); FileUtil.deleteRecursive(classesDir); }
            plan = inc.plan(sourceDirs, Collections.singletonList(gen), fp);
        } catch (IOException e) {
            throw new BuildException("Cannot scan sources: " + e.getMessage());
        }
        if (plan.upToDate) {
            usedCache = true;
            log("Java: up to date (" + plan.all.size() + " files, nothing to compile)");
            return false;
        }
        if (plan.compile.isEmpty()) {
            try { inc.commit(plan); } catch (IOException ignored) { }
            return true;   // only deletions
        }
        if (!plan.full) usedCache = true;
        log("Java: compiling " + plan.compile.size() + " of " + plan.all.size() + " files (" + (plan.full ? "full" : "incremental") + ")");

        List<String> args = new ArrayList<>(Arrays.asList("-1.8", "-encoding", "UTF-8", "-nowarn", "-proc:none", "-g",
                "-bootclasspath", androidJar.getPath()));
        List<String> cp = new ArrayList<>();
        cp.add(env.lambdaStubs().getPath());    // LambdaMetafactory stub: needed for lambdas / method references
        cp.add(classesDir.getPath());
        for (File f : libs.jars) cp.add(f.getPath());
        args.add("-classpath");
        args.add(join(cp, File.pathSeparator));
        args.add("-d");
        args.add(classesDir.getPath());
        if (plan.full) { for (File d : sourceDirs) args.add(d.getPath()); }
        else { for (File f : plan.compile) args.add(f.getPath()); }

        final StringWriter sw = new StringWriter();
        final boolean[] ok = new boolean[1];
        final String[] argv = args.toArray(new String[0]);
        final Throwable[] fail = new Throwable[1];
        Thread t = new Thread(null, new Runnable() {
            @Override public void run() {
                try {
                    PrintWriter pw = new PrintWriter(sw, true);
                    ok[0] = BatchCompiler.compile(argv, pw, pw, new CompilationProgress() {
                        @Override public void begin(int remainingWork) { }
                        @Override public void done() { }
                        @Override public boolean isCanceled() { return cancelled; }
                        @Override public void setTaskName(String name) { }
                        @Override public void worked(int workIncrement, int remainingWork) { }
                    });
                } catch (Throwable e) {
                    fail[0] = e;
                }
            }
        }, "ecj", 512L * 1024 * 1024);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new BuildException("Interrupted", true);
        }
        String output = sw.toString();
        for (String line : output.split("\n")) if (!line.trim().isEmpty()) log(line);
        if (fail[0] != null) {
            inc.invalidate();
            StringWriter ew = new StringWriter();
            fail[0].printStackTrace(new PrintWriter(ew));
            throw new BuildException("The Java compiler crashed: " + fail[0] + "\n" + trim(ew.toString(), 2500));
        }
        if (cancelled) { inc.invalidate(); throw new BuildException("Build cancelled", true); }
        if (!ok[0]) {
            inc.invalidate();     // partial output may exist: the next build starts clean
            parseEcj(output);
            throw new BuildException("Java compilation failed");
        }
        try { inc.commit(plan); } catch (IOException e) { throw new BuildException("Cannot save build state: " + e.getMessage()); }
        return true;
    }

    private static final Pattern ECJ_HEAD = Pattern.compile("^\\d+\\. (ERROR|WARNING) in (.+?) \\(at line (\\d+)\\)\\s*$");

    private void parseEcj(String output) {
        String[] blocks = output.split("----------\\n?");
        for (String blk : blocks) {
            String[] lines = blk.split("\\n");
            int i = 0;
            while (i < lines.length && lines[i].trim().isEmpty()) i++;
            if (i >= lines.length) continue;
            Matcher m = ECJ_HEAD.matcher(lines[i].trim());
            if (!m.matches()) continue;
            int caret = -1;
            for (int k = i + 1; k < lines.length; k++) {
                if (lines[k].trim().matches("\\^+")) { caret = k; break; }
            }
            StringBuilder msg = new StringBuilder();
            for (int k = caret < 0 ? i + 1 : caret + 1; k < lines.length; k++) {
                String s = lines[k].trim();
                if (s.isEmpty()) continue;
                if (msg.length() > 0) msg.append(' ');
                msg.append(s);
            }
            addDiag(m.group(2), Integer.parseInt(m.group(3)), msg.toString(), m.group(1).equals("ERROR"));
        }
    }

    // ------------------------------------------------------------------ D8

    private void dex(List<File> program, File androidJar, List<File> classpath, File out, int minSdk, boolean release) throws BuildException {
        final List<String> msgs = new ArrayList<>();
        DiagnosticsHandler handler = new DiagnosticsHandler() {
            @Override public void error(Diagnostic d) { msgs.add("error: " + d.getDiagnosticMessage()); log("D8 error: " + d.getDiagnosticMessage()); }
            @Override public void warning(Diagnostic d) { }
            @Override public void info(Diagnostic d) { }
        };
        try {
            D8Command.Builder bld = D8Command.builder(handler);
            List<Path> prog = new ArrayList<>();
            for (File f : program) prog.add(f.toPath());
            bld.addProgramFiles(prog);
            bld.addLibraryFiles(androidJar.toPath());
            if (classpath != null && !classpath.isEmpty()) {
                List<Path> cp = new ArrayList<>();
                for (File f : classpath) cp.add(f.toPath());
                bld.addClasspathFiles(cp);
            }
            bld.setMinApiLevel(minSdk);
            bld.setMode(release ? CompilationMode.RELEASE : CompilationMode.DEBUG);
            bld.setOutput(out.toPath(), OutputMode.DexIndexed);
            D8.run(bld.build());
        } catch (CompilationFailedException e) {
            throw new BuildException("D8 dexing failed:\n" + join(msgs, "\n") + (msgs.isEmpty() ? String.valueOf(e.getMessage()) : ""), false, true);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            throw new BuildException("D8 crashed: " + t + "\n" + trim(sw.toString(), 2500));
        }
    }

    private static List<File> sortedDex(File dir) {
        File[] fs = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (fs == null) return out;
        for (File f : fs) if (f.getName().endsWith(".dex")) out.add(f);
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Integer.compare(num(a), num(b)); }
            int num(File f) {
                String n = f.getName().replace("classes", "").replace(".dex", "");
                return n.isEmpty() ? 1 : Integer.parseInt(n);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ fingerprints & helpers

    private String overallFingerprint(Variant v, List<File> java, List<File> res, List<File> assets, List<File> manifests,
                                      Set<String> deps, File gs, File proguard) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.name).append('|').append(v.applicationId).append('|').append(v.versionName).append('|').append(v.versionCode)
                .append('|').append(v.minSdk).append('|').append(v.targetSdk).append('|').append(v.debuggable).append('|')
                .append(v.minify).append(v.obfuscate).append(v.optimize).append('|').append(opt.aab).append('|')
                .append(v.buildConfigFields).append('|').append(deps).append('|').append(env.dexRelease()).append('|');
        try {
            Signer.Key k = env.signingKey(v.signingKey);
            sb.append(k.file.getPath()).append(k.file.length()).append(k.file.lastModified()).append(k.alias);
        } catch (Exception e) { sb.append("nokey"); }
        for (File f : java) sb.append(dirFingerprint(f));
        for (File f : res) sb.append(dirFingerprint(f));
        for (File f : assets) sb.append(dirFingerprint(f));
        for (File f : manifests) sb.append(fileSig(f));
        sb.append(fileSig(p.manifestFile)).append(fileSig(gs)).append(fileSig(proguard));
        for (File d : p.libDirs) sb.append(dirFingerprint(d));
        for (ModuleCfg m : p.orderedModules()) { sb.append(fileSig(m.manifestFile)); for (File d : m.libDirs) sb.append(dirFingerprint(d)); }
        return FileUtil.sha1(sb.toString());
    }

    private static String fileSig(File f) {
        return f != null && f.isFile() ? f.getPath() + ":" + f.length() + ":" + f.lastModified() + ";" : "-;";
    }

    private static String dirFingerprint(File dir) {
        List<File> files = FileUtil.listFiles(dir, null);
        Collections.sort(files);
        StringBuilder sb = new StringBuilder();
        for (File f : files) sb.append(FileUtil.relPath(dir, f)).append(':').append(f.length()).append(':').append(f.lastModified()).append(';');
        return FileUtil.sha1(sb.toString());
    }

    private void stage(String name, int percent) throws BuildException {
        check();
        stageName = name;
        stageOut.setLength(0);
        log("\n== " + name + " ==");
        if (listener != null) listener.onStage(name, percent);
    }

    private void check() throws BuildException {
        if (cancelled) throw new BuildException("Build cancelled", true);
    }

    private void log(String s) {
        fullLog.append(s).append('\n');
        stageOut.append(s).append('\n');
        if (listener != null) listener.onLog(s);
    }

    private String failureText(String headline) {
        StringBuilder sb = new StringBuilder();
        sb.append("Build failed at: ").append(stageName).append("\n").append(headline).append("\n");
        int errs = 0;
        for (Diag d : diags) if (d.error) errs++;
        if (errs > 0) {
            sb.append("\n").append(errs).append(" error").append(errs == 1 ? "" : "s").append(":\n");
            for (Diag d : diags) if (d.error) sb.append("\n• ").append(shortPath(d)).append("\n").append(d.message).append("\n");
        }
        String raw = stageOut.toString().trim();
        if (errs == 0 && !raw.isEmpty()) sb.append("\n--- output ---\n").append(trim(raw, 12000)).append("\n");
        return sb.toString().trim();
    }

    private String shortPath(Diag d) {
        String f = d.file == null ? "" : d.file;
        String root = p.root.getAbsolutePath() + "/";
        if (f.startsWith(root)) f = f.substring(root.length());
        return f + (d.line > 0 ? ":" + d.line : "");
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n…";
    }

    private static String join(Iterable<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) { if (sb.length() > 0) sb.append(sep); sb.append(s); }
        return sb.toString();
    }

    private static String describe(List<File> jars) {
        StringBuilder sb = new StringBuilder();
        for (File f : jars) sb.append(f.getPath()).append(':').append(f.length()).append(':').append(f.lastModified()).append(';');
        return sb.toString();
    }

    private static boolean hasFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        File[] kids = dir.listFiles();
        if (kids == null) return false;
        for (File k : kids) {
            if (k.isFile()) return true;
            if (hasFiles(k)) return true;
        }
        return false;
    }

    private static void zipDir(File dir, File out) throws BuildException {
        try (ZipOutputStream z = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(out)))) {
            addDir(z, dir, "");
        } catch (IOException e) {
            throw new BuildException("Could not create classes.jar: " + e.getMessage());
        }
    }

    private static void addDir(ZipOutputStream z, File d, String prefix) throws IOException {
        File[] kids = d.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File k : kids) {
            if (k.isDirectory()) addDir(z, k, prefix + k.getName() + "/");
            else {
                z.putNextEntry(new ZipEntry(prefix + k.getName()));
                try (InputStream in = new java.io.FileInputStream(k)) { FileUtil.copyStream(in, z); }
                z.closeEntry();
            }
        }
    }
}
