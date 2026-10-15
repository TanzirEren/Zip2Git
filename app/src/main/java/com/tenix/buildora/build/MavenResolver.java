package com.tenix.buildora.build;

import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.Net;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Minimal Maven resolver: downloads POM/JAR/AAR from Google Maven, Maven Central and JitPack,
 * walks compile/runtime dependencies and picks the highest version per group:artifact.
 */
public final class MavenResolver {

    public interface Log { void log(String s); }

    public static final class Artifact {
        public String group, artifact, version;
        public File file;       // .jar or .aar (null for pom-only)
        public boolean aar;
        public String coord() { return group + ":" + artifact + ":" + version; }
        /** Extracted AAR directory (only for aar). */
        public File extracted() {
            return new File(file.getParentFile(), artifact + "-" + version + "-aar");
        }
    }

    private static final String[] REPOS = {
            "https://dl.google.com/dl/android/maven2/",
            "https://repo1.maven.org/maven2/",
            "https://jitpack.io/"
    };

    private static final class Dep {
        String g, a, v, scope, type;
        boolean optional;
    }

    private static final class Pom {
        String g, a, v, packaging = "jar";
        Map<String, String> props = new HashMap<>();
        Map<String, String> mgmt = new HashMap<>();
        List<Dep> deps = new ArrayList<>();
    }

    private final File cache;
    private final Log log;
    private final Map<String, Pom> poms = new HashMap<>();

    public MavenResolver(File cacheDir, Log log) {
        this.cache = cacheDir;
        this.log = log;
        cache.mkdirs();
    }

    // ------------------------------------------------------------------ public API

    public List<Artifact> resolve(List<String> coords) throws IOException {
        Map<String, String> chosen = new LinkedHashMap<>();
        ArrayDeque<String[]> queue = new ArrayDeque<>();
        for (String c : coords) {
            String[] p = c.trim().split(":");
            if (p.length < 2) throw new IOException("Bad dependency (use group:artifact[:version]): " + c);
            String v = p.length >= 3 ? cleanVersion(p[2]) : "+";
            if (v.isEmpty() || v.equals("+") || v.equalsIgnoreCase("latest") || v.equalsIgnoreCase("latest.release")) {
                String latest = latestVersion(p[0], p[1]);
                if (latest == null) throw new IOException("Cannot find any version of " + p[0] + ":" + p[1]);
                log.log("  ~ " + p[0] + ":" + p[1] + " -> latest stable " + latest);
                v = latest;
            }
            queue.add(new String[]{p[0], p[1], v, "root"});
        }
        String kotlinMax = null;
        while (!queue.isEmpty()) {
            String[] gav = queue.poll();
            String ga = gav[0] + ":" + gav[1];
            boolean kotlin = ga.startsWith("org.jetbrains.kotlin:kotlin-stdlib");
            if (kotlin && kotlinMax != null && compare(gav[2], kotlinMax) < 0) gav[2] = kotlinMax;   // keep stdlib artifacts aligned
            String cur = chosen.get(ga);
            if (cur != null && compare(cur, gav[2]) >= 0) continue;
            Pom pom;
            try {
                pom = loadPom(gav[0], gav[1], gav[2]);
            } catch (IOException e) {
                String alt = gav[3].equals("root") ? latestVersion(gav[0], gav[1]) : null;
                if (alt != null && !alt.equals(gav[2])) {
                    log.log("  ~ " + ga + ":" + gav[2] + " not found, using " + alt);
                    gav[2] = alt;
                    try { pom = loadPom(gav[0], gav[1], gav[2]); }
                    catch (IOException e2) { log.log("  ! skipping " + ga + " (" + e2.getMessage() + ")"); continue; }
                } else {
                    log.log("  ! skipping " + ga + ":" + gav[2] + " (" + e.getMessage() + ")");
                    continue;
                }
            }
            chosen.put(ga, gav[2]);
            if (kotlin && (kotlinMax == null || compare(gav[2], kotlinMax) > 0)) {
                kotlinMax = gav[2];
                for (String other : new ArrayList<>(chosen.keySet())) {
                    if (other.startsWith("org.jetbrains.kotlin:kotlin-stdlib") && compare(chosen.get(other), kotlinMax) < 0) {
                        String[] o = other.split(":");
                        queue.add(new String[]{o[0], o[1], kotlinMax, "align"});
                    }
                }
            }
            for (Dep d : pom.deps) {
                if (d.optional) continue;
                String sc = d.scope == null ? "compile" : d.scope;
                if (!sc.equals("compile") && !sc.equals("runtime")) continue;
                if (d.type != null && d.type.equals("test-jar")) continue;
                if (d.v == null || d.v.isEmpty() || d.v.contains("${")) continue;
                queue.add(new String[]{d.g, d.a, cleanVersion(d.v), "dep"});
            }
        }
        List<Artifact> out = new ArrayList<>();
        for (Map.Entry<String, String> e : chosen.entrySet()) {
            String[] ga = e.getKey().split(":");
            Artifact a = fetchArtifact(ga[0], ga[1], e.getValue());
            if (a != null) out.add(a);
        }
        return out;
    }

    // ------------------------------------------------------------------ latest version

    private static boolean isStable(String v) {
        String l = v.toLowerCase();
        return !(l.contains("alpha") || l.contains("beta") || l.contains("rc") || l.contains("snapshot")
                || l.contains("dev") || l.contains("preview") || l.contains("-m") || l.contains("eap") || l.contains("canary"));
    }

    /** Highest stable version published for group:artifact (reads maven-metadata.xml, cached for 12 h). */
    public String latestVersion(String g, String a) {
        File f = new File(cache, ".metadata/" + g + "/" + a + ".xml");
        boolean fresh = f.isFile() && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < 12L * 3600 * 1000;
        if (!fresh) {
            String path = g.replace('.', '/') + "/" + a + "/maven-metadata.xml";
            boolean got = false;
            for (String repo : REPOS) {
                try {
                    if (Net.download(repo + path, f, null)) { got = true; break; }
                } catch (IOException ignored) { }
            }
            if (!got && !f.isFile()) return null;
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            Document doc = dbf.newDocumentBuilder().parse(f);
            NodeList nl = doc.getElementsByTagName("version");
            String best = null;
            for (int i = 0; i < nl.getLength(); i++) {
                String v = nl.item(i).getTextContent().trim();
                if (v.isEmpty() || !isStable(v)) continue;
                if (best == null || compare(v, best) > 0) best = v;
            }
            if (best == null) {
                NodeList rel = doc.getElementsByTagName("release");
                if (rel.getLength() > 0) best = rel.item(0).getTextContent().trim();
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ artifacts

    private File local(String g, String a, String v, String ext) {
        return new File(cache, g.replace('.', '/') + "/" + a + "/" + v + "/" + a + "-" + v + "." + ext);
    }

    private boolean fetch(String g, String a, String v, String ext, File f) {
        if (f.isFile() && f.length() > 0) return true;
        String path = g.replace('.', '/') + "/" + a + "/" + v + "/" + a + "-" + v + "." + ext;
        for (String repo : REPOS) {
            try {
                if (Net.download(repo + path, f, null)) return true;
            } catch (IOException ignored) { }
        }
        return false;
    }

    private Artifact fetchArtifact(String g, String a, String v) throws IOException {
        Pom pom = poms.get(g + ":" + a + ":" + v);
        String pack = pom == null ? "jar" : pom.packaging;
        if ("pom".equals(pack)) return null;
        Artifact art = new Artifact();
        art.group = g; art.artifact = a; art.version = v;
        boolean preferAar = "aar".equals(pack);
        String[] order = preferAar ? new String[]{"aar", "jar"} : new String[]{"jar", "aar"};
        for (String ext : order) {
            File f = local(g, a, v, ext);
            log.log("  + " + g + ":" + a + ":" + v + (f.isFile() ? " (cached)" : ""));
            if (fetch(g, a, v, ext, f)) {
                art.file = f;
                art.aar = ext.equals("aar");
                if (art.aar) extractAar(art);
                return art;
            }
        }
        log.log("  ! no jar/aar for " + g + ":" + a + ":" + v + " (pom only)");
        return null;
    }

    private void extractAar(Artifact a) throws IOException {
        File dir = a.extracted();
        File ok = new File(dir, ".ok");
        if (ok.isFile()) return;
        FileUtil.deleteRecursive(dir);
        dir.mkdirs();
        String root;
        try {
            root = dir.getCanonicalPath() + File.separator;
        } catch (IOException e) { throw e; }
        try (ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(a.file)))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                File out = new File(dir, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue;
                FileUtil.copyStreamToFile(z, out);
            }
        }
        ok.createNewFile();
    }

    // ------------------------------------------------------------------ POM handling

    private Pom loadPom(String g, String a, String v) throws IOException {
        String key = g + ":" + a + ":" + v;
        Pom cached = poms.get(key);
        if (cached != null) return cached;
        File f = local(g, a, v, "pom");
        if (!fetch(g, a, v, "pom", f)) throw new IOException("POM not found");
        Pom pom = parse(f, g, a, v);
        poms.put(key, pom);
        return pom;
    }

    private Pom parse(File f, String g, String a, String v) throws IOException {
        Document doc;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(f);
        } catch (Exception e) {
            throw new IOException("Bad POM: " + e.getMessage());
        }
        Element root = doc.getDocumentElement();
        Pom pom = new Pom();
        pom.g = g; pom.a = a; pom.v = v;

        Element parent = child(root, "parent");
        if (parent != null) {
            String pg = text(parent, "groupId"), pa = text(parent, "artifactId"), pv = text(parent, "version");
            if (pg != null && pa != null && pv != null) {
                try {
                    Pom pp = loadPom(pg, pa, cleanVersion(pv));
                    pom.props.putAll(pp.props);
                    pom.mgmt.putAll(pp.mgmt);
                    pom.props.put("parent.version", pv);
                    pom.props.put("project.parent.version", pv);
                    pom.props.put("parent.groupId", pg);
                } catch (IOException ignored) { }
            }
        }
        String pk = text(root, "packaging");
        if (pk != null) pom.packaging = pk.trim();
        pom.props.put("project.version", v);
        pom.props.put("pom.version", v);
        pom.props.put("version", v);
        pom.props.put("project.groupId", g);
        pom.props.put("project.artifactId", a);
        Element props = child(root, "properties");
        if (props != null) {
            NodeList nl = props.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n instanceof Element) pom.props.put(n.getNodeName(), n.getTextContent().trim());
            }
        }
        Element dm = child(root, "dependencyManagement");
        if (dm != null) {
            Element deps = child(dm, "dependencies");
            if (deps != null) {
                for (Dep d : readDeps(deps, pom)) {
                    if ("import".equals(d.scope) && d.v != null) {
                        try {
                            Pom bom = loadPom(d.g, d.a, cleanVersion(d.v));
                            for (Map.Entry<String, String> e : bom.mgmt.entrySet()) pom.mgmt.putIfAbsent(e.getKey(), e.getValue());
                        } catch (IOException ignored) { }
                    } else if (d.v != null) {
                        pom.mgmt.put(d.g + ":" + d.a, d.v);
                    }
                }
            }
        }
        Element deps = child(root, "dependencies");
        if (deps != null) {
            for (Dep d : readDeps(deps, pom)) {
                if (d.v == null) d.v = pom.mgmt.get(d.g + ":" + d.a);
                pom.deps.add(d);
            }
        }
        return pom;
    }

    private List<Dep> readDeps(Element deps, Pom pom) {
        List<Dep> out = new ArrayList<>();
        NodeList nl = deps.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (!(n instanceof Element) || !n.getNodeName().equals("dependency")) continue;
            Element e = (Element) n;
            Dep d = new Dep();
            d.g = interp(text(e, "groupId"), pom);
            d.a = interp(text(e, "artifactId"), pom);
            d.v = interp(text(e, "version"), pom);
            d.scope = text(e, "scope");
            d.type = text(e, "type");
            String opt = text(e, "optional");
            d.optional = opt != null && opt.trim().equalsIgnoreCase("true");
            if (d.g != null && d.a != null) out.add(d);
        }
        return out;
    }

    private static Element child(Element p, String name) {
        NodeList nl = p.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element && n.getNodeName().equals(name)) return (Element) n;
        }
        return null;
    }

    private static String text(Element p, String name) {
        Element e = child(p, name);
        return e == null ? null : e.getTextContent().trim();
    }

    private static String interp(String s, Pom pom) {
        if (s == null) return null;
        for (int i = 0; i < 10 && s.contains("${"); i++) {
            int a = s.indexOf("${");
            int b = s.indexOf('}', a);
            if (b < 0) break;
            String k = s.substring(a + 2, b);
            String val = pom.props.get(k);
            if (val == null) return s;
            s = s.substring(0, a) + val + s.substring(b + 1);
        }
        return s;
    }

    // ------------------------------------------------------------------ versions

    /** "[1.2.3]" -> "1.2.3", "[1.0,2.0)" -> "1.0" */
    static String cleanVersion(String v) {
        String s = v.trim();
        if (s.startsWith("[") || s.startsWith("(")) {
            s = s.substring(1);
            int end = s.length();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ',' || c == ']' || c == ')') { end = i; break; }
            }
            s = s.substring(0, end).trim();
        }
        return s;
    }

    static int compare(String a, String b) {
        String[] x = a.split("[.\\-_]");
        String[] y = b.split("[.\\-_]");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            String p = i < x.length ? x[i] : "0";
            String q = i < y.length ? y[i] : "0";
            boolean pn = p.matches("\\d+"), qn = q.matches("\\d+");
            int c;
            if (pn && qn) {
                c = Long.compare(Long.parseLong(p.length() > 17 ? p.substring(0, 17) : p), Long.parseLong(q.length() > 17 ? q.substring(0, 17) : q));
            } else if (pn != qn) {
                c = pn ? 1 : -1;  // numbers rank above qualifiers like "alpha"
            } else {
                c = rank(p) - rank(q);
                if (c == 0) c = p.compareTo(q);
            }
            if (c != 0) return c;
        }
        return 0;
    }

    private static int rank(String q) {
        String s = q.toLowerCase();
        if (s.startsWith("alpha") || s.equals("a")) return 1;
        if (s.startsWith("beta") || s.equals("b")) return 2;
        if (s.startsWith("rc") || s.startsWith("cr")) return 3;
        if (s.equals("snapshot")) return 0;
        return 4;
    }
}
