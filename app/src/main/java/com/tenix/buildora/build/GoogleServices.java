package com.tenix.buildora.build;

import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.MiniJson;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Does what the google-services Gradle plugin does: turns google-services.json into string resources. */
public final class GoogleServices {
    private GoogleServices() {}

    public static File find(File moduleDir, File projectRoot) {
        for (File d : new File[]{moduleDir, projectRoot}) {
            File f = new File(d, "google-services.json");
            if (f.isFile()) return f;
        }
        return null;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace("\"", "\\\"");
    }

    private static void add(StringBuilder sb, String name, String value) {
        if (value == null || value.isEmpty()) return;
        sb.append("    <string name=\"").append(name).append("\" translatable=\"false\">").append(esc(value)).append("</string>\n");
    }

    private static String s(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v instanceof String) return (String) v;
        if (v instanceof Double) return String.valueOf(((Double) v).longValue());
        return null;
    }

    /** @return the resource dir written (contains values/google_services.xml) or null when the JSON has no matching client */
    public static File generate(File json, String applicationId, String originalPackage, File outResDir) throws IOException {
        Map<String, Object> root;
        try {
            root = MiniJson.object(MiniJson.parse(FileUtil.readText(json)));
        } catch (RuntimeException e) {
            throw new IOException("google-services.json is not valid JSON: " + e.getMessage());
        }
        if (root == null) throw new IOException("google-services.json: unexpected format");
        Map<String, Object> info = MiniJson.object(root.get("project_info"));
        List<Object> clients = MiniJson.array(root.get("client"));
        if (clients == null || clients.isEmpty()) throw new IOException("google-services.json has no clients");
        Map<String, Object> chosen = null;
        for (Object o : clients) {
            Map<String, Object> c = MiniJson.object(o);
            Map<String, Object> ci = MiniJson.object(c == null ? null : c.get("client_info"));
            Map<String, Object> ai = MiniJson.object(ci == null ? null : ci.get("android_client_info"));
            String pkg = s(ai, "package_name");
            if (pkg != null && (pkg.equals(applicationId) || pkg.equals(originalPackage))) { chosen = c; break; }
        }
        if (chosen == null) {
            StringBuilder have = new StringBuilder();
            for (Object o : clients) {
                Map<String, Object> c = MiniJson.object(o);
                Map<String, Object> ci = MiniJson.object(c == null ? null : c.get("client_info"));
                Map<String, Object> ai = MiniJson.object(ci == null ? null : ci.get("android_client_info"));
                have.append(' ').append(s(ai, "package_name"));
            }
            throw new IOException("google-services.json has no client for package '" + applicationId + "' (it has:" + have + ")");
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        String webClient = null;
        List<Object> oauth = MiniJson.array(chosen.get("oauth_client"));
        if (oauth != null) for (Object o : oauth) {
            Map<String, Object> oc = MiniJson.object(o);
            if (oc != null && "3".equals(s(oc, "client_type"))) { webClient = s(oc, "client_id"); break; }
        }
        add(sb, "default_web_client_id", webClient);
        add(sb, "firebase_database_url", s(info, "firebase_url"));
        add(sb, "gcm_defaultSenderId", s(info, "project_number"));
        String apiKey = null;
        List<Object> keys = MiniJson.array(chosen.get("api_key"));
        if (keys != null && !keys.isEmpty()) apiKey = s(MiniJson.object(keys.get(0)), "current_key");
        add(sb, "google_api_key", apiKey);
        add(sb, "google_app_id", s(MiniJson.object(chosen.get("client_info")), "mobilesdk_app_id"));
        add(sb, "google_crash_reporting_api_key", apiKey);
        add(sb, "google_storage_bucket", s(info, "storage_bucket"));
        add(sb, "project_id", s(info, "project_id"));
        sb.append("</resources>\n");
        File out = new File(outResDir, "values/google_services.xml");
        FileUtil.writeText(out, sb.toString());
        return outResDir;
    }
}
