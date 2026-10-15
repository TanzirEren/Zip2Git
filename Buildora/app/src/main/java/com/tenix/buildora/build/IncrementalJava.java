package com.tenix.buildora.build;

import com.tenix.buildora.core.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which Java files must be recompiled.
 * A file is dirty when it changed; every file that mentions a type declared in a dirty (or deleted)
 * file is dirty too, transitively. That is conservative on purpose: it also covers inlined
 * constants (static final) and R.java changes, which plain class-file references would miss.
 */
public final class IncrementalJava {

    public static final class Plan {
        public boolean full;
        public boolean upToDate;
        public final List<File> compile = new ArrayList<>();
        public final List<File> all = new ArrayList<>();
        final Map<File, String> sigs = new LinkedHashMap<>();
        final Map<File, SourceIndex.Info> infos = new HashMap<>();
        String globalFp = "";
    }

    private static final class OldEntry {
        String sig, pkg;
        Set<String> types = new HashSet<>();
    }

    private final File stateFile;
    private final File classesDir;

    public IncrementalJava(File stateFile, File classesDir) {
        this.stateFile = stateFile;
        this.classesDir = classesDir;
    }

    /** @param genDirs directories whose files are generated each build (signature = content hash, not mtime) */
    public Plan plan(List<File> sourceDirs, List<File> genDirs, String globalFp) throws IOException {
        Plan p = new Plan();
        p.globalFp = globalFp;
        Set<String> gen = new HashSet<>();
        for (File g : genDirs) gen.add(g.getCanonicalPath() + File.separator);
        for (File d : sourceDirs) {
            for (File f : FileUtil.listFiles(d, ".java")) {
                if (!p.sigs.containsKey(f)) {
                    p.all.add(f);
                    boolean isGen = false;
                    String cp = f.getCanonicalPath();
                    for (String g : gen) if (cp.startsWith(g)) isGen = true;
                    String text = FileUtil.readText(f);
                    p.sigs.put(f, isGen ? "g" + FileUtil.sha1(text) : f.lastModified() + ":" + f.length());
                    p.infos.put(f, SourceIndex.parse(text));
                }
            }
        }
        Collections.sort(p.all);

        Map<String, OldEntry> old = new HashMap<>();
        String oldFp = null;
        if (stateFile.isFile()) {
            for (String line : FileUtil.readText(stateFile).split("\n")) {
                if (line.startsWith("fp=")) { oldFp = line.substring(3); continue; }
                String[] a = line.split("\t", -1);
                if (a.length < 4) continue;
                OldEntry e = new OldEntry();
                e.sig = a[1];
                e.pkg = a[2];
                for (String t : a[3].split(",")) if (!t.isEmpty()) e.types.add(t);
                old.put(a[0], e);
            }
        }
        File[] existing = classesDir.listFiles();
        boolean noClasses = existing == null || existing.length == 0;
        if (oldFp == null || !oldFp.equals(globalFp) || noClasses) {
            p.full = true;
            p.compile.addAll(p.all);
            if (classesDir.isDirectory()) FileUtil.deleteRecursive(classesDir);
            classesDir.mkdirs();
            return p;
        }

        Set<File> dirty = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (File f : p.all) {
            String path = f.getPath();
            seen.add(path);
            OldEntry o = old.get(path);
            if (o == null || !o.sig.equals(p.sigs.get(f))) {
                dirty.add(f);
                names.addAll(p.infos.get(f).types);
                if (o != null) names.addAll(o.types);
            }
        }
        List<OldEntry> removed = new ArrayList<>();
        for (Map.Entry<String, OldEntry> e : old.entrySet()) {
            if (!seen.contains(e.getKey())) { removed.add(e.getValue()); names.addAll(e.getValue().types); }
        }
        if (dirty.isEmpty() && removed.isEmpty()) {
            p.upToDate = true;
            return p;
        }
        // transitive closure over "mentions a changed type"
        ArrayDeque<File> work = new ArrayDeque<>(dirty);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (File f : p.all) {
                if (dirty.contains(f)) continue;
                for (String t : names) {
                    if (p.infos.get(f).tokens.contains(t)) {
                        dirty.add(f);
                        names.addAll(p.infos.get(f).types);
                        grew = true;
                        break;
                    }
                }
            }
        }
        // delete stale class files
        for (File f : dirty) {
            OldEntry o = old.get(f.getPath());
            SourceIndex.Info cur = p.infos.get(f);
            if (o != null) deleteTypes(o.pkg, o.types);
            deleteTypes(cur.pkg, cur.types);
        }
        for (OldEntry o : removed) deleteTypes(o.pkg, o.types);
        for (File f : p.all) if (dirty.contains(f)) p.compile.add(f);
        return p;
    }

    private void deleteTypes(String pkg, Set<String> types) {
        File dir = pkg.isEmpty() ? classesDir : new File(classesDir, pkg.replace('.', '/'));
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String n = k.getName();
            if (!n.endsWith(".class")) continue;
            String base = n.substring(0, n.length() - 6);
            int d = base.indexOf('$');
            String top = d < 0 ? base : base.substring(0, d);
            if (types.contains(top)) k.delete();
        }
    }

    public void commit(Plan p) throws IOException {
        StringBuilder sb = new StringBuilder("fp=").append(p.globalFp).append('\n');
        for (File f : p.all) {
            SourceIndex.Info i = p.infos.get(f);
            sb.append(f.getPath()).append('\t').append(p.sigs.get(f)).append('\t').append(i.pkg).append('\t');
            boolean first = true;
            for (String t : i.types) { if (!first) sb.append(','); sb.append(t); first = false; }
            sb.append('\n');
        }
        FileUtil.writeText(stateFile, sb.toString());
    }

    public void invalidate() { stateFile.delete(); }
}
