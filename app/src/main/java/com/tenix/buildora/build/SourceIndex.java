package com.tenix.buildora.build;

import java.util.HashSet;
import java.util.Set;

/** Light-weight Java source scanner: package, top-level type names, and every identifier used. */
public final class SourceIndex {
    public static final class Info {
        public String pkg = "";
        public final Set<String> types = new HashSet<>();     // top-level class / interface / enum / @interface names
        public final Set<String> tokens = new HashSet<>();    // all identifiers (comments and strings excluded)
    }

    private SourceIndex() { }

    public static Info parse(String t) {
        Info info = new Info();
        int n = t.length(), i = 0, depth = 0;
        String prev = "";
        boolean wantType = false, wantPkg = false;
        StringBuilder pkg = new StringBuilder();
        while (i < n) {
            char c = t.charAt(i);
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '/') {
                while (i < n && t.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(t.charAt(i) == '*' && t.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            if (c == '"') {
                if (t.startsWith("\"\"\"", i)) {
                    i += 3;
                    while (i + 2 < n && !t.startsWith("\"\"\"", i)) { if (t.charAt(i) == '\\') i++; i++; }
                    i += 3;
                } else {
                    i++;
                    while (i < n && t.charAt(i) != '"' && t.charAt(i) != '\n') { if (t.charAt(i) == '\\') i++; i++; }
                    i++;
                }
                continue;
            }
            if (c == '\'') {
                i++;
                while (i < n && t.charAt(i) != '\'' && t.charAt(i) != '\n') { if (t.charAt(i) == '\\') i++; i++; }
                i++;
                continue;
            }
            if (c == '{') { depth++; i++; continue; }
            if (c == '}') { depth = Math.max(0, depth - 1); i++; continue; }
            if (Character.isJavaIdentifierStart(c)) {
                int st = i;
                while (i < n && Character.isJavaIdentifierPart(t.charAt(i))) i++;
                String id = t.substring(st, i);
                info.tokens.add(id);
                if (depth == 0) {
                    if (wantType) {
                        info.types.add(id);
                        wantType = false;
                    } else if (wantPkg) {
                        pkg.append(id);
                    } else if (id.equals("class") || id.equals("interface") || id.equals("enum")) {
                        if (!prev.equals(".")) wantType = true;
                    } else if (id.equals("package") && pkg.length() == 0) {
                        wantPkg = true;
                    }
                }
                prev = id;
                continue;
            }
            if (wantPkg) {
                if (c == '.') pkg.append('.');
                else if (c == ';') { wantPkg = false; info.pkg = pkg.toString(); }
            }
            if (!Character.isWhitespace(c)) prev = String.valueOf(c);
            i++;
        }
        if (wantPkg && info.pkg.isEmpty()) info.pkg = pkg.toString();
        return info;
    }
}
