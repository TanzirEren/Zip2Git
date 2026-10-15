package com.tenix.buildora.build;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/** Verifies zipalign rules: stored entries 4-byte aligned, native libraries aligned to the page size. */
public final class ZipAlignCheck {
    private ZipAlignCheck() {}

    public static final class Report {
        public int stored, misaligned;
        public final List<String> bad = new ArrayList<>();
        public boolean ok() { return misaligned == 0; }
    }

    private static int u16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    private static long u32(byte[] b, int o) { return (b[o] & 0xffL) | ((b[o + 1] & 0xffL) << 8) | ((b[o + 2] & 0xffL) << 16) | ((b[o + 3] & 0xffL) << 24); }

    public static Report check(File zip, int soAlign) throws IOException {
        Report r = new Report();
        try (RandomAccessFile f = new RandomAccessFile(zip, "r")) {
            long len = f.length();
            int tail = (int) Math.min(len, 70000);
            byte[] t = new byte[tail];
            f.seek(len - tail);
            f.readFully(t);
            int eocd = -1;
            for (int i = tail - 22; i >= 0; i--) {
                if (t[i] == 0x50 && t[i + 1] == 0x4b && t[i + 2] == 5 && t[i + 3] == 6) { eocd = i; break; }
            }
            if (eocd < 0) throw new IOException("Not a zip file");
            int count = u16(t, eocd + 10);
            long cdOff = u32(t, eocd + 16);
            f.seek(cdOff);
            for (int n = 0; n < count; n++) {
                byte[] h = new byte[46];
                f.readFully(h);
                int method = u16(h, 10);
                int nameLen = u16(h, 28), extraLen = u16(h, 30), commentLen = u16(h, 32);
                long lho = u32(h, 42);
                byte[] name = new byte[nameLen];
                f.readFully(name);
                f.skipBytes(extraLen + commentLen);
                if (method != 0) continue;
                long pos = f.getFilePointer();
                f.seek(lho + 26);
                byte[] l = new byte[4];
                f.readFully(l);
                long dataOff = lho + 30 + u16(l, 0) + u16(l, 2);
                f.seek(pos);
                String nm = new String(name, "UTF-8");
                int need = nm.endsWith(".so") ? soAlign : 4;
                r.stored++;
                if (dataOff % need != 0) { r.misaligned++; r.bad.add(nm + " (offset " + dataOff + ", needs " + need + ")"); }
            }
        }
        return r;
    }
}
