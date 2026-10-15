package com.tenix.buildora.build;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.tenix.buildora.core.FileUtil;

/** Writes an (unsigned) APK. resources.arsc and .so files are stored uncompressed and aligned (zipalign). */
public final class ApkBuilder implements Closeable {

    private static final class Counting extends OutputStream {
        final OutputStream out;
        long count;
        Counting(OutputStream o) { out = o; }
        @Override public void write(int b) throws IOException { out.write(b); count++; }
        @Override public void write(byte[] b, int o, int l) throws IOException { out.write(b, o, l); count += l; }
        @Override public void flush() throws IOException { out.flush(); }
        @Override public void close() throws IOException { out.close(); }
    }

    private final Counting counting;
    private final ZipOutputStream zos;
    private final Set<String> names = new HashSet<>();

    public ApkBuilder(File out) throws IOException {
        counting = new Counting(new BufferedOutputStream(new FileOutputStream(out), 1 << 16));
        zos = new ZipOutputStream(counting);
        zos.setLevel(6);
    }

    public boolean has(String name) { return names.contains(name); }

    public void addDeflated(String name, byte[] data) throws IOException {
        if (!names.add(name)) return;
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    public void addStored(String name, byte[] data, int align) throws IOException {
        if (!names.add(name)) return;
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        e.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        int nameLen = name.getBytes(StandardCharsets.UTF_8).length;
        long dataOffset = counting.count + 30 + nameLen;
        int pad = (int) ((align - (dataOffset % align)) % align);
        if (pad > 0) {
            while (pad < 4) pad += align;
            byte[] extra = new byte[pad];
            extra[0] = (byte) 0x35;       // 0xD935 zipalign padding id (little endian)
            extra[1] = (byte) 0xD9;
            int size = pad - 4;
            extra[2] = (byte) (size & 0xFF);
            extra[3] = (byte) ((size >> 8) & 0xFF);
            e.setExtra(extra);
        }
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    /** Copy every entry of a zip (e.g. resources.ap_) except those whose name matches skip. */
    public void addAllFromZip(File zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                byte[] data;
                try (InputStream in = zf.getInputStream(e)) { data = FileUtil.readAll(in); }
                String n = e.getName();
                if (n.equals("resources.arsc")) addStored(n, data, 4);
                else addDeflated(n, data);
            }
        }
    }

    /** Copy Java resources (not .class, not signature files) from a library jar. */
    public void addJavaResources(File jar) throws IOException {
        try (ZipFile zf = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || n.endsWith(".class") || n.endsWith(".java") || n.endsWith(".kotlin_module")
                        || n.equals("module-info.class") || n.startsWith("META-INF/versions/")) continue;
                if (n.startsWith("META-INF/") && !n.startsWith("META-INF/services/")) continue;
                if (n.equals("AndroidManifest.xml") || n.startsWith("res/") || n.equals("resources.arsc")
                        || n.startsWith("assets/") || n.endsWith(".dex")) continue;
                try (InputStream in = zf.getInputStream(e)) { addDeflated(n, FileUtil.readAll(in)); }
            }
        }
    }

    /** Add native libraries from an extracted AAR jni/ folder: lib/&lt;abi&gt;/x.so */
    public void addNativeLibs(File jniDir) throws IOException {
        addNativeLibs(jniDir, 4096);
    }

    /** align: 4096, or 16384 for devices with 16 KB memory pages. */
    public void addNativeLibs(File jniDir, int align) throws IOException {
        File[] abis = jniDir.listFiles();
        if (abis == null) return;
        for (File abi : abis) {
            File[] sos = abi.listFiles();
            if (!abi.isDirectory() || sos == null) continue;
            for (File so : sos) {
                if (!so.getName().endsWith(".so")) continue;
                byte[] data;
                try (InputStream in = new java.io.FileInputStream(so)) { data = FileUtil.readAll(in); }
                addStored("lib/" + abi.getName() + "/" + so.getName(), data, align);
            }
        }
    }

    @Override public void close() throws IOException {
        zos.close();
    }
}
