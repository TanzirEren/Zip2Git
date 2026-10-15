package com.tenix.buildora.build;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages the native pieces of the on-device pipeline: aapt2 (executable) and android.jar.
 * They ship inside the APK assets (put there by the GitHub Action) and are extracted to app storage.
 */
public final class Toolchain {
    private Toolchain() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "toolchain");
        d.mkdirs();
        return d;
    }

    public static File aapt2(Context c) { return new File(dir(c), "aapt2"); }
    public static File androidJar(Context c) { return new File(dir(c), "android.jar"); }

    /**
     * Compile-time stub for java.lang.invoke.LambdaMetafactory (the Android SDK ships the same thing as
     * core-lambda-stubs.jar). android.jar does not contain it, so ECJ cannot compile lambdas / method
     * references without it. D8 desugars the invokedynamic calls later, so it is never packaged.
     */
    public static File lambdaStubs(Context c) { return new File(dir(c), "core-lambda-stubs.jar"); }

    private static final String LAMBDA_STUBS_B64 =
            "UEsDBBQAAAAIAAAAIVBi5jaZygEAAPUDAAAoAAAAamF2YS9sYW5nL2ludm9rZS9MYW1iZGFNZXRhZmFjdG9yeS5jbGFzc7VTXU8TQRQ9ty1dKKV8yZcKKJEEeLDxyYcSk1IW3LhA0iU84IOZtiMsbGeb3WkDf8DEf4NPJj74Awy/iXCnBFmpICExm+yde+ac3Dt3zvy6+PETwFss5JBC2kImjz5kCSNHoiOKgVAHxZ3akaxrQnbVV75+R0gvLe9Z6Ccs3JB81QmPZdEVzVpDbEktPou6DqNTwuiGW9785NlVp+w6++U11yaQQxiqhCrWQuk9EbRlmrsgQr5L3ipXP9hVz2Cpa2yt6qxv2l0sQ8hUwoYkDLu+ktvtZk1Gu6IWMDLYTNY+X3J7WuTmDsPGe6EagYxfuWF43G6VEjRPR746KN0l3D1tycdtXpW8X7vcu1sRQeD5WpYIOfukLlva58FZmCKs3DF/nmxHRjHzfgsIBRHoPy7my38azkf3tnf+dSwvbEd1ueGbC5zssdBro2UbOErJqBKIOJZ8/DnC4oPat/CCMHc/lc19RcYbWPwQgDRmMIAc2FHGhchzPpTIC5wPJ/IRVqTM02FklJFZjsSxb+U76JsxN8b4n+2CA/yN4wkLDPUpY9mu1c9u8QqYMHbn9SS+YppL3igyf1WMJxQp5pnWnuE5R9PqLObxEjOXUEsBAhQDFAAAAAgAAAAhUGLmNpnKAQAA9QMAACgAAAAAAAAAAAAAAIABAAAAAGphdmEvbGFuZy9pbnZva2UvTGFtYmRhTWV0YWZhY3RvcnkuY2xhc3NQSwUGAAAAAAEAAQBWAAAAEAIAAAAA";

    private static void writeLambdaStubs(Context c) throws IOException {
        File f = lambdaStubs(c);
        if (f.isFile() && f.length() > 0) return;
        byte[] data = android.util.Base64.decode(LAMBDA_STUBS_B64, android.util.Base64.DEFAULT);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(data);
        }
    }

    private static int appVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    private static boolean hasAsset(Context c, String name) {
        try {
            String[] l = c.getAssets().list("toolchain");
            return l != null && Arrays.asList(l).contains(name);
        } catch (IOException e) {
            return false;
        }
    }

    private static String bundledAapt2Asset(Context c) {
        for (String abi : Build.SUPPORTED_ABIS) {
            String n = "aapt2-" + abi;
            if (hasAsset(c, n)) return n;
        }
        return null;
    }

    /** Extract bundled tools if needed. Safe to call before every build. */
    public static synchronized void ensure(Context c) throws IOException {
        int ver = appVersion(c);
        boolean stale = Prefs.num(c, Prefs.TOOLCHAIN_VER, -1) != ver;
        File aapt = aapt2(c);
        if ((stale || !aapt.exists()) && !Prefs.bool(c, Prefs.AAPT2_CUSTOM, false)) {
            String asset = bundledAapt2Asset(c);
            if (asset != null) {
                try (InputStream in = c.getAssets().open("toolchain/" + asset)) {
                    FileUtil.copyStreamToFile(in, aapt);
                }
            }
        }
        File jar = androidJar(c);
        if ((stale || !jar.exists()) && !Prefs.bool(c, Prefs.JAR_CUSTOM, false) && hasAsset(c, "android.jar")) {
            try (InputStream in = c.getAssets().open("toolchain/android.jar")) {
                FileUtil.copyStreamToFile(in, jar);
            }
        }
        writeLambdaStubs(c);
        if (aapt.exists()) {
            aapt.setReadable(true, false);
            aapt.setExecutable(true, false);
        }
        Prefs.put(c, Prefs.TOOLCHAIN_VER, ver);
    }

    public static boolean ready(Context c) {
        return aapt2(c).isFile() && androidJar(c).isFile();
    }

    /** Runs "aapt2 version" and returns the first line (or an error description). */
    public static String aapt2Version(Context c) {
        File a = aapt2(c);
        if (!a.isFile()) return "not installed";
        a.setExecutable(true, false);
        try {
            Process p = new ProcessBuilder(a.getAbsolutePath(), "version").redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line == null ? "no output (binary may not match this CPU)" : line;
        } catch (Exception e) {
            return "cannot execute: " + e.getMessage();
        }
    }

    public static String status(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("aapt2: ").append(aapt2Version(c)).append('\n');
        File j = androidJar(c);
        sb.append("android.jar: ").append(j.isFile() ? FileUtil.human(j.length()) : "missing").append('\n');
        sb.append("ECJ: bundled   D8/R8: bundled   apksig: bundled");
        return sb.toString();
    }

    public static void importAapt2(Context c, File src) throws IOException {
        FileUtil.copyFile(src, aapt2(c));
        aapt2(c).setExecutable(true, false);
        Prefs.put(c, Prefs.AAPT2_CUSTOM, true);
    }

    public static void importAndroidJar(Context c, File src) throws IOException {
        FileUtil.copyFile(src, androidJar(c));
        Prefs.put(c, Prefs.JAR_CUSTOM, true);
    }

    public static void resetToBundled(Context c) throws IOException {
        Prefs.put(c, Prefs.AAPT2_CUSTOM, false);
        Prefs.put(c, Prefs.JAR_CUSTOM, false);
        Prefs.put(c, Prefs.TOOLCHAIN_VER, -1);
        aapt2(c).delete();
        androidJar(c).delete();
        ensure(c);
    }

    /** Import from a downloaded file: raw binary, or a zip/tar-less archive that contains "aapt2". */
    public static void importAapt2Download(Context c, File downloaded) throws IOException {
        byte[] head = new byte[4];
        try (java.io.FileInputStream in = new java.io.FileInputStream(downloaded)) {
            if (in.read(head) < 4) throw new IOException("File too small");
        }
        if (head[0] == 'P' && head[1] == 'K') {
            try (ZipInputStream z = new ZipInputStream(new java.io.FileInputStream(downloaded))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    String n = new File(e.getName()).getName();
                    if (!e.isDirectory() && n.equals("aapt2")) {
                        FileUtil.copyStreamToFile(z, aapt2(c));
                        aapt2(c).setExecutable(true, false);
                        Prefs.put(c, Prefs.AAPT2_CUSTOM, true);
                        return;
                    }
                }
            }
            throw new IOException("No 'aapt2' file inside the zip");
        }
        importAapt2(c, downloaded);
    }
}
