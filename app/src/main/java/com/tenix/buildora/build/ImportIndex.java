package com.tenix.buildora.build;

import com.tenix.buildora.core.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds the package of a class by simple name (from android.jar and the project's own sources) and adds imports. */
public final class ImportIndex {
    private ImportIndex() {}

    private static Map<String, List<String>> platform;
    private static String platformKey = "";

    private static synchronized Map<String, List<String>> platform(File androidJar) {
        String key = androidJar.getPath() + androidJar.length();
        if (platform != null && platformKey.equals(key)) return platform;
        Map<String, List<String>> m = new HashMap<>();
        try (ZipFile z = new ZipFile(androidJar)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (!n.endsWith(".class") || n.indexOf('$') >= 0 || n.endsWith("package-info.class")) continue;
                if (!(n.startsWith("android/") || n.startsWith("java/") || n.startsWith("javax/") || n.startsWith("org/json/")
                        || n.startsWith("org/w3c/") || n.startsWith("org/xml/") || n.startsWith("org/xmlpull/"))) continue;
                if (n.startsWith("android/internal") || n.startsWith("java/lang/invoke/")) continue;
                int slash = n.lastIndexOf('/');
                String simple = n.substring(slash + 1, n.length() - 6);
                String fq = n.substring(0, n.length() - 6).replace('/', '.');
                List<String> l = m.get(simple);
                if (l == null) { l = new ArrayList<>(); m.put(simple, l); }
                l.add(fq);
            }
        } catch (IOException ignored) { }
        platform = m;
        platformKey = key;
        return m;
    }

    private static int score(String fq) {
        if (fq.startsWith("java.lang.") && fq.indexOf('.', 10) < 0) return 0;
        String[] best = {"android.widget.", "android.view.", "android.app.", "android.content.", "android.os.", "android.graphics.",
                "android.util.", "android.net.", "android.text.", "android.media.", "android.webkit.", "android.animation."};
        for (int i = 0; i < best.length; i++) if (fq.startsWith(best[i])) return 1 + i;
        if (fq.startsWith("java.util.") || fq.startsWith("java.io.")) return 20;
        if (fq.startsWith("android.")) return 30;
        if (fq.startsWith("java.")) return 40;
        return 50;
    }

    /** Candidate fully-qualified names for a simple class name, best first (max 6). */
    public static List<String> candidates(File androidJar, String simple, List<File> sourceDirs) {
        List<String> out = new ArrayList<>();
        List<String> pl = platform(androidJar).get(simple);
        if (pl != null) out.addAll(pl);
        Collections.sort(out, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int d = score(a) - score(b);
                return d != 0 ? d : a.compareTo(b);
            }
        });
        Set<String> project = new HashSet<>();
        for (File d : sourceDirs) {
            for (File f : FileUtil.listFiles(d, ".java")) {
                if (!f.getName().equals(simple + ".java")) continue;
                try {
                    SourceIndex.Info i = SourceIndex.parse(FileUtil.readText(f));
                    if (i.types.contains(simple)) project.add(i.pkg.isEmpty() ? simple : i.pkg + "." + simple);
                } catch (IOException ignored) { }
            }
        }
        List<String> res = new ArrayList<>(project);
        res.addAll(out);
        return res.size() > 6 ? new ArrayList<>(res.subList(0, 6)) : res;
    }

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?[^;]+;[ \\t]*$");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+[^;]+;[ \\t]*$");

    /** Returns text with "import fqcn;" added (unchanged if already imported or in java.lang / same package). */
    public static String addImport(String text, String fqcn) {
        if (text.contains("import " + fqcn + ";")) return text;
        String line = "import " + fqcn + ";";
        Matcher m = IMPORT.matcher(text);
        int end = -1;
        while (m.find()) end = m.end();
        if (end >= 0) return text.substring(0, end) + "\n" + line + text.substring(end);
        Matcher p = PACKAGE.matcher(text);
        if (p.find()) return text.substring(0, p.end()) + "\n\n" + line + text.substring(p.end());
        return line + "\n\n" + text;
    }
}
