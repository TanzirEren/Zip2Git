package com.tenix.buildora.build;

import com.tenix.buildora.core.FileUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Assembles an Android App Bundle (.aab) with a single "base" module from a proto-format aapt2 link output. */
public final class AabBuilder {
    private AabBuilder() {}

    // BundleConfig { bundletool { version = "1.15.6" } }   (protobuf, hand encoded)
    private static byte[] bundleConfig() {
        byte[] v = "1.15.6".getBytes();
        byte[] out = new byte[2 + 2 + v.length];
        out[0] = 0x0A;                      // field 1 (bundletool), length-delimited
        out[1] = (byte) (2 + v.length);
        out[2] = 0x12;                      // field 2 (version), length-delimited
        out[3] = (byte) v.length;
        System.arraycopy(v, 0, out, 4, v.length);
        return out;
    }

    public static void build(File protoApk, List<File> dex, List<File> javaResJars, List<File> jniDirs, File out) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            put(z, names, "BundleConfig.pb", bundleConfig());
            try (ZipFile in = new ZipFile(protoApk)) {
                Enumeration<? extends ZipEntry> en = in.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    String target;
                    if (n.equals("AndroidManifest.xml")) target = "base/manifest/AndroidManifest.xml";
                    else if (n.equals("resources.pb")) target = "base/resources.pb";
                    else if (n.startsWith("res/") || n.startsWith("assets/")) target = "base/" + n;
                    else target = "base/root/" + n;
                    try (InputStream is = in.getInputStream(e)) { put(z, names, target, FileUtil.readAll(is)); }
                }
            }
            int i = 1;
            for (File d : dex) {
                byte[] data;
                try (InputStream is = new java.io.FileInputStream(d)) { data = FileUtil.readAll(is); }
                put(z, names, "base/dex/" + (i == 1 ? "classes.dex" : "classes" + i + ".dex"), data);
                i++;
            }
            for (File jar : javaResJars) {
                try (ZipFile zf = new ZipFile(jar)) {
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        String n = e.getName();
                        if (e.isDirectory() || n.endsWith(".class") || n.endsWith(".java") || n.endsWith(".kotlin_module")
                                || n.startsWith("META-INF/") && !n.startsWith("META-INF/services/")
                                || n.equals("AndroidManifest.xml") || n.startsWith("res/") || n.startsWith("assets/")
                                || n.endsWith(".dex")) continue;
                        try (InputStream is = zf.getInputStream(e)) { put(z, names, "base/root/" + n, FileUtil.readAll(is)); }
                    }
                }
            }
            for (File jni : jniDirs) {
                File[] abis = jni.listFiles();
                if (abis == null) continue;
                for (File abi : abis) {
                    File[] sos = abi.listFiles();
                    if (!abi.isDirectory() || sos == null) continue;
                    for (File so : sos) {
                        if (!so.getName().endsWith(".so")) continue;
                        byte[] data;
                        try (InputStream is = new java.io.FileInputStream(so)) { data = FileUtil.readAll(is); }
                        put(z, names, "base/lib/" + abi.getName() + "/" + so.getName(), data);
                    }
                }
            }
        }
    }

    private static void put(ZipOutputStream z, Set<String> names, String name, byte[] data) throws IOException {
        if (!names.add(name)) return;
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }
}
