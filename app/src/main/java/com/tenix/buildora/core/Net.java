package com.tenix.buildora.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Net {
    private Net() {}

    public interface Progress { void onBytes(long done, long total); }

    private static HttpURLConnection open(String url) throws IOException {
        String cur = url;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Buildora/1.0 (Android)");
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect without Location");
                cur = new URL(new URL(cur), loc).toString();
                continue;
            }
            return c;
        }
        throw new IOException("Too many redirects: " + url);
    }

    /** Downloads url to out. Returns false when the server answers 404 / 403. */
    public static boolean download(String url, File out, Progress p) throws IOException {
        HttpURLConnection c = open(url);
        int code = c.getResponseCode();
        if (code == 404 || code == 403 || code == 410) { c.disconnect(); return false; }
        if (code != 200) { c.disconnect(); throw new IOException("HTTP " + code + " for " + url); }
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(out.getPath() + ".part");
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[32768];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                if (p != null) p.onBytes(done, total);
            }
        } finally {
            c.disconnect();
        }
        if (out.exists()) out.delete();
        if (!tmp.renameTo(out)) throw new IOException("Cannot write " + out);
        return true;
    }

    public static String getString(String url) throws IOException {
        HttpURLConnection c = open(url);
        int code = c.getResponseCode();
        if (code != 200) { c.disconnect(); return null; }
        try (InputStream in = c.getInputStream()) {
            return new String(FileUtil.readAll(in), "UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
