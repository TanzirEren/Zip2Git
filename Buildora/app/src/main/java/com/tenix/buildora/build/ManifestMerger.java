package com.tenix.buildora.build;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Small manifest merger: copies permissions and application components from library manifests. */
public final class ManifestMerger {
    private ManifestMerger() {}

    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS = "http://schemas.android.com/tools";

    private static final String[] TOP = {"uses-permission", "uses-permission-sdk-23", "uses-feature", "permission"};
    private static final String[] APP = {"activity", "activity-alias", "service", "receiver", "provider",
            "meta-data", "uses-library", "uses-native-library"};

    private static Document parse(File f) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(f);
    }

    public static void merge(File appManifest, List<File> libManifests, String appPackage, File out) throws Exception {
        Document doc = parse(appManifest);
        Element root = doc.getDocumentElement();
        Element app = firstChild(root, "application");
        if (app == null) {
            app = doc.createElement("application");
            root.appendChild(app);
        }
        String pkg = root.getAttribute("package");
        if (pkg.isEmpty()) pkg = appPackage;
        final String appId = appPackage;          // the applicationId (may differ from the manifest package)

        Set<String> have = new HashSet<>();
        collectKeys(root, app, pkg, have);

        for (File lf : libManifests) {
            Document ld;
            try { ld = parse(lf); } catch (Exception e) { continue; }
            Element lroot = ld.getDocumentElement();
            String lpkg = lroot.getAttribute("package");
            Element lapp = firstChild(lroot, "application");
            NodeList nl = lroot.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (!(n instanceof Element)) continue;
                Element e = (Element) n;
                if (!contains(TOP, e.getTagName())) continue;
                String key = e.getTagName() + "|" + e.getAttributeNS(ANDROID, "name");
                if (have.contains(key)) continue;
                have.add(key);
                Node imp = doc.importNode(e, true);
                clean((Element) imp, pkg);
                root.insertBefore(imp, app);
            }
            if (lapp == null) continue;
            NodeList an = lapp.getChildNodes();
            for (int i = 0; i < an.getLength(); i++) {
                Node n = an.item(i);
                if (!(n instanceof Element)) continue;
                Element e = (Element) n;
                if (!contains(APP, e.getTagName())) continue;
                String name = fullName(e.getAttributeNS(ANDROID, "name"), lpkg);
                String key = e.getTagName() + "|" + name;
                if (have.contains(key)) continue;
                have.add(key);
                Element imp = (Element) doc.importNode(e, true);
                if (!name.isEmpty()) imp.setAttributeNS(ANDROID, "android:name", name);
                clean(imp, appId);
                app.appendChild(imp);
            }
        }
        // the app's own manifest: ${applicationId} placeholders, and a different applicationId than the package
        replacePlaceholders(root, appId);
        if (!appId.equals(pkg)) {
            expandNames(root, pkg);
            root.setAttribute("package", appId);
        }
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        out.getParentFile().mkdirs();
        t.transform(new DOMSource(doc), new StreamResult(out));
    }

    private static final String[] CLASS_TAGS = {"application", "activity", "activity-alias", "service", "receiver", "provider"};

    /** Makes android:name of components absolute so the package attribute can be renamed safely. */
    private static void expandNames(Element e, String pkg) {
        if (contains(CLASS_TAGS, e.getTagName())) {
            String[] attrs = {"name", "targetActivity", "backupAgent", "parentActivityName"};
            for (String a : attrs) {
                if (!e.hasAttributeNS(ANDROID, a)) continue;
                String v = e.getAttributeNS(ANDROID, a);
                String full = fullName(v, pkg);
                if (!full.equals(v) && !v.isEmpty()) e.setAttributeNS(ANDROID, "android:" + a, full);
            }
        }
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k instanceof Element) expandNames((Element) k, pkg);
        }
    }

    private static void replacePlaceholders(Element e, String appId) {
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            String v = a.getNodeValue();
            if (v.contains("${")) a.setNodeValue(v.replace("${applicationId}", appId).replace("${packageName}", appId));
        }
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k instanceof Element) replacePlaceholders((Element) k, appId);
        }
    }

    /** Reads the package attribute of a manifest (used to generate library R classes). */
    public static String packageOf(File manifest) {
        try {
            return parse(manifest).getDocumentElement().getAttribute("package");
        } catch (Exception e) {
            return "";
        }
    }

    private static void collectKeys(Element root, Element app, String pkg, Set<String> keys) {
        NodeList nl = root.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element && contains(TOP, ((Element) n).getTagName())) {
                Element e = (Element) n;
                keys.add(e.getTagName() + "|" + e.getAttributeNS(ANDROID, "name"));
            }
        }
        NodeList an = app.getChildNodes();
        for (int i = 0; i < an.getLength(); i++) {
            Node n = an.item(i);
            if (n instanceof Element && contains(APP, ((Element) n).getTagName())) {
                Element e = (Element) n;
                keys.add(e.getTagName() + "|" + fullName(e.getAttributeNS(ANDROID, "name"), pkg));
            }
        }
    }

    private static String fullName(String name, String pkg) {
        if (name == null || name.isEmpty()) return "";
        if (name.startsWith(".")) return pkg + name;
        if (!name.contains(".")) return pkg + "." + name;
        return name;
    }

    private static void clean(Element e, String appPkg) {
        List<String> remove = new ArrayList<>();
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (TOOLS.equals(a.getNamespaceURI()) || a.getNodeName().startsWith("xmlns:tools")) {
                remove.add(a.getNodeName());
                continue;
            }
            String v = a.getNodeValue();
            if (v.contains("${")) {
                a.setNodeValue(v.replace("${applicationId}", appPkg).replace("${packageName}", appPkg));
            }
        }
        for (String r : remove) e.removeAttribute(r);
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k instanceof Element) clean((Element) k, appPkg);
        }
    }

    private static Element firstChild(Element p, String tag) {
        NodeList nl = p.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element && ((Element) n).getTagName().equals(tag)) return (Element) n;
        }
        return null;
    }

    private static boolean contains(String[] arr, String s) {
        for (String a : arr) if (a.equals(s)) return true;
        return false;
    }
}
