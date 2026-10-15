package com.tenix.buildora.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private FileUtil() {}

    public static String readText(File f) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    public static void writeText(File f, String text) throws IOException {
        File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(32, in.available()));
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    public static void copyFile(File src, File dst) throws IOException {
        File p = dst.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            copyStream(in, out);
        }
    }

    public static void copyStreamToFile(InputStream in, File dst) throws IOException {
        File p = dst.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        try (OutputStream out = new FileOutputStream(dst)) {
            copyStream(in, out);
        }
    }

    /** Copy a file or a whole directory tree. */
    public static void copy(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("Cannot create " + dst);
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copy(k, new File(dst, k.getName()));
        } else {
            copyFile(src, dst);
        }
    }

    /** Like copy(), but never throws: used to merge asset folders. */
    public static void copyQuiet(File src, File dst) {
        try { copy(src, dst); } catch (IOException ignored) { }
    }

    public static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory() && !isSymlink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
    }

    private static boolean isSymlink(File f) {
        try {
            return !f.getCanonicalPath().equals(new File(f.getParentFile().getCanonicalFile(), f.getName()).getPath());
        } catch (IOException e) {
            return false;
        }
    }

    public static void move(File src, File dst) throws IOException {
        File p = dst.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        if (src.renameTo(dst)) return;
        copy(src, dst);
        deleteRecursive(src);
    }

    public static boolean isInside(File parent, File child) {
        try {
            String a = parent.getCanonicalPath();
            String b = child.getCanonicalPath();
            return b.equals(a) || b.startsWith(a + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /** foo.txt -> foo (1).txt if foo.txt exists. */
    public static File unique(File f) {
        if (!f.exists()) return f;
        String n = f.getName();
        String base = n, ext = "";
        int dot = n.lastIndexOf('.');
        if (dot > 0 && !f.isDirectory()) { base = n.substring(0, dot); ext = n.substring(dot); }
        for (int i = 1; i < 10000; i++) {
            File c = new File(f.getParentFile(), base + " (" + i + ")" + ext);
            if (!c.exists()) return c;
        }
        return f;
    }

    public static String ext(File f) {
        String n = f.getName();
        int d = n.lastIndexOf('.');
        return d < 0 ? "" : n.substring(d + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isImage(File f) {
        String e = ext(f);
        return e.equals("png") || e.equals("jpg") || e.equals("jpeg") || e.equals("webp") || e.equals("gif") || e.equals("bmp");
    }

    private static final String[] BINARY = {"apk", "zip", "jar", "aar", "dex", "so", "class", "ttf", "otf", "ogg",
            "mp3", "mp4", "wav", "pdf", "keystore", "jks", "p12", "bin", "arsc", "ap_", "flat", "7z", "rar", "gz", "xz", "ico", "odex", "oat"};

    public static boolean isBinaryExt(File f) {
        String e = ext(f);
        for (String b : BINARY) if (b.equals(e)) return true;
        return false;
    }

    /** Heuristic: look at the first 4 KB for NUL bytes. */
    public static boolean looksBinary(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[4096];
            int n = in.read(b);
            for (int i = 0; i < n; i++) if (b[i] == 0) return true;
        } catch (IOException ignored) { }
        return false;
    }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double k = bytes / 1024.0;
        if (k < 1024) return String.format(Locale.US, "%.1f KB", k);
        double m = k / 1024.0;
        if (m < 1024) return String.format(Locale.US, "%.1f MB", m);
        return String.format(Locale.US, "%.2f GB", m / 1024.0);
    }

    public static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long s = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) s += sizeOf(k);
        return s;
    }

    public static void zipDir(File dir, File outZip, boolean includeHidden) throws IOException {
        File p = outZip.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outZip)))) {
            zipInto(zos, dir, dir.getName(), includeHidden, outZip);
        }
    }

    private static void zipInto(ZipOutputStream zos, File f, String path, boolean hidden, File skip) throws IOException {
        if (f.equals(skip)) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) return;
            for (File k : kids) {
                if (!hidden && k.getName().startsWith(".")) continue;
                zipInto(zos, k, path + "/" + k.getName(), hidden, skip);
            }
        } else {
            zos.putNextEntry(new ZipEntry(path));
            try (InputStream in = new FileInputStream(f)) { copyStream(in, zos); }
            zos.closeEntry();
        }
    }

    /** Unzip into dir (zip-slip safe). */
    public static void unzip(File zip, File dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            String root = dir.getCanonicalPath() + File.separator;
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(dir, e.getName());
                if (!out.getCanonicalPath().startsWith(root) && !out.getCanonicalPath().equals(dir.getCanonicalPath())) {
                    throw new IOException("Blocked unsafe zip entry: " + e.getName());
                }
                if (e.isDirectory()) { out.mkdirs(); continue; }
                copyStreamToFile(zis, out);
            }
        }
    }

    public static List<File> listFiles(File dir, String extension) {
        List<File> r = new ArrayList<>();
        collect(dir, extension, r);
        return r;
    }

    private static void collect(File d, String ext, List<File> out) {
        File[] kids = d.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, ext, out);
            else if (ext == null || k.getName().endsWith(ext)) out.add(k);
        }
    }

    public static String relPath(File base, File f) {
        String b = base.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (p.startsWith(b)) {
            String r = p.substring(b.length());
            while (r.startsWith("/")) r = r.substring(1);
            return r;
        }
        return p;
    }

    public static String sha1(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
