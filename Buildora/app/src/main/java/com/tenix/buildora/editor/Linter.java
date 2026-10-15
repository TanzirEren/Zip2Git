package com.tenix.buildora.editor;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Fast, offline syntax checks used for the red error lines while typing. */
public final class Linter {
    private Linter() {}

    public static final class Problem {
        public final int line; // 1-based
        public final String message;
        public Problem(int line, String message) { this.line = line; this.message = message; }
    }

    public static List<Problem> check(String text, String lang) {
        List<Problem> out = new ArrayList<>();
        if (text.length() > 400000) return out;
        if (lang.equals("xml")) checkXml(text, out);
        else if (lang.equals("java") || lang.equals("json")) checkBrackets(text, lang.equals("java"), out);
        return out;
    }

    private static void checkXml(String text, List<Problem> out) {
        if (text.trim().isEmpty()) return;
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(new StringReader(text));
            while (p.next() != XmlPullParser.END_DOCUMENT) { /* walk */ }
        } catch (XmlPullParserException e) {
            int line = Math.max(1, e.getLineNumber());
            String m = String.valueOf(e.getMessage());
            int cut = m.indexOf(" (position");
            if (cut > 0) m = m.substring(0, cut);
            out.add(new Problem(line, m));
        } catch (IOException e) {
            out.add(new Problem(1, String.valueOf(e.getMessage())));
        } catch (RuntimeException ignored) { }
    }

    private static void checkBrackets(String t, boolean java, List<Problem> out) {
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        int line = 1;
        int n = t.length();
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            if (c == '\n') { line++; continue; }
            if (java && c == '/' && i + 1 < n) {
                char d = t.charAt(i + 1);
                if (d == '/') { while (i < n && t.charAt(i) != '\n') i++; i--; continue; }
                if (d == '*') {
                    i += 2;
                    while (i + 1 < n && !(t.charAt(i) == '*' && t.charAt(i + 1) == '/')) { if (t.charAt(i) == '\n') line++; i++; }
                    i++;
                    continue;
                }
            }
            if (c == '"' || (java && c == '\'')) {
                char q = c;
                int startLine = line;
                i++;
                while (i < n && t.charAt(i) != q) {
                    if (t.charAt(i) == '\\') i++;
                    else if (t.charAt(i) == '\n') {
                        out.add(new Problem(startLine, "Unterminated string literal"));
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '(' || c == '{' || c == '[') stack.push(new int[]{c, line});
            else if (c == ')' || c == '}' || c == ']') {
                char open = c == ')' ? '(' : c == '}' ? '{' : '[';
                if (stack.isEmpty()) { out.add(new Problem(line, "Unexpected '" + c + "'")); return; }
                int[] top = stack.pop();
                if (top[0] != open) {
                    out.add(new Problem(line, "Mismatched '" + c + "' (opened '" + (char) top[0] + "' on line " + top[1] + ")"));
                    return;
                }
            }
        }
        if (!stack.isEmpty()) {
            int[] top = stack.peek();
            out.add(new Problem(top[1], "Unclosed '" + (char) top[0] + "'"));
        }
    }
}
