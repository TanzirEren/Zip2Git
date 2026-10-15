package com.tenix.buildora.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ProjectStore {
    private ProjectStore() {}

    public static List<Project> list() {
        List<Project> out = new ArrayList<>();
        File base = Project.baseDir();
        File[] dirs = base.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            if (!d.isDirectory() || d.getName().startsWith(".")) continue;
            if (d.getName().equals("keystores")) continue;
            out.add(Project.load(d));
        }
        Collections.sort(out, new Comparator<Project>() {
            @Override public int compare(Project a, Project b) {
                return Long.compare(lastTouched(b), lastTouched(a));
            }
        });
        return out;
    }

    public static long lastTouched(Project p) {
        long t = p.root.lastModified();
        File cfg = p.configFile();
        if (cfg.exists()) t = Math.max(t, cfg.lastModified());
        return t;
    }

    public static void touch(Project p) {
        File cfg = p.configFile();
        if (cfg.exists()) cfg.setLastModified(System.currentTimeMillis());
        p.root.setLastModified(System.currentTimeMillis());
    }

    public static File uniqueRoot(String projectName) {
        File base = Project.baseDir();
        base.mkdirs();
        return FileUtil.unique(new File(base, Project.sanitize(projectName)));
    }
}
