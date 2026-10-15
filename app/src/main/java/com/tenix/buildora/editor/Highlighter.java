package com.tenix.buildora.editor;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regex based syntax highlighter for Java, XML, JSON, Gradle, properties and Smali. */
public final class Highlighter {
    private Highlighter() {}

    /** Marker subclass so we can strip only our own spans. */
    public static final class Hl extends ForegroundColorSpan {
        public Hl(int color) { super(color); }
    }

    private static final String JAVA_KW =
            "abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|" +
            "extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|" +
            "package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|" +
            "throws|transient|try|void|volatile|while|true|false|null|var|record|def|task|apply|plugins";

    private static final Pattern JAVA = Pattern.compile(
            "(?<c>//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$))" +
            "|(?<s>\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')" +
            "|(?<a>@[A-Za-z_][A-Za-z0-9_.]*)" +
            "|(?<n>\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*\\.?\\d*(?:[eE][+-]?\\d+)?[fFdDlL]?)\\b)" +
            "|(?<k>\\b(?:" + JAVA_KW + ")\\b)" +
            "|(?<t>\\b[A-Z][A-Za-z0-9_]*\\b)" +
            "|(?<f>\\b[a-z_][A-Za-z0-9_]*(?=\\s*\\())");

    private static final Pattern XML = Pattern.compile(
            "(?<c><!--[\\s\\S]*?(?:-->|$))" +
            "|(?<d><!\\[CDATA\\[[\\s\\S]*?(?:\\]\\]>|$))" +
            "|(?<g></?[A-Za-z_][\\w:.\\-]*|<\\?[\\w:\\-]+|\\?>|/?>)" +
            "|(?<r>[A-Za-z_][\\w:.\\-]*(?=\\s*=))" +
            "|(?<s>\"[^\"]*\"|'[^']*')" +
            "|(?<e>&[#\\w]+;)");

    private static final Pattern JSON = Pattern.compile(
            "(?<r>\"(?:\\\\.|[^\"\\\\\\n])*\"(?=\\s*:))" +
            "|(?<s>\"(?:\\\\.|[^\"\\\\\\n])*\")" +
            "|(?<n>-?\\b\\d+\\.?\\d*(?:[eE][+-]?\\d+)?\\b)" +
            "|(?<k>\\b(?:true|false|null)\\b)");

    private static final Pattern PROPS = Pattern.compile(
            "(?<c>^[ \\t]*[#!][^\\n]*)|(?<r>^[ \\t]*[A-Za-z0-9_.\\-]+(?=[ \\t]*[=:]))", Pattern.MULTILINE);

    private static final Pattern SMALI = Pattern.compile(
            "(?<c>#[^\\n]*)" +
            "|(?<s>\"(?:\\\\.|[^\"\\\\\\n])*\")" +
            "|(?<k>^[ \\t]*\\.[a-z\\-]+)" +
            "|(?<t>L[\\w/$]+;)" +
            "|(?<a>\\b[vp]\\d+\\b)" +
            "|(?<f>:[\\w]+)" +
            "|(?<n>\\b0x[0-9a-fA-F]+\\b|\\b-?\\d+\\b)", Pattern.MULTILINE);

    public static String langFor(String fileName) {
        String n = fileName.toLowerCase();
        if (n.endsWith(".java")) return "java";
        if (n.endsWith(".xml") || n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".svg")) return "xml";
        if (n.endsWith(".json")) return "json";
        if (n.endsWith(".gradle") || n.endsWith(".kts") || n.endsWith(".kt") || n.endsWith(".js") || n.endsWith(".c")
                || n.endsWith(".cpp") || n.endsWith(".h") || n.endsWith(".css") || n.endsWith(".php")) return "java";
        if (n.endsWith(".properties") || n.endsWith(".pro") || n.endsWith(".gitignore") || n.endsWith(".cfg")
                || n.endsWith(".ini") || n.endsWith(".toml")) return "props";
        if (n.endsWith(".smali")) return "smali";
        return "text";
    }

    public static String langLabel(String lang) {
        switch (lang) {
            case "java": return "Java";
            case "xml": return "XML";
            case "json": return "JSON";
            case "props": return "Properties";
            case "smali": return "Smali";
            default: return "Text";
        }
    }

    public static void clear(Editable e, int from, int to) {
        Hl[] old = e.getSpans(from, to, Hl.class);
        for (Hl h : old) e.removeSpan(h);
    }

    /** Re-highlight text between from and to (offsets are clamped). */
    public static void apply(Editable e, String lang, EditorScheme s, int from, int to) {
        int len = e.length();
        from = Math.max(0, Math.min(from, len));
        to = Math.max(from, Math.min(to, len));
        clear(e, from, to);
        Pattern p;
        switch (lang) {
            case "java": p = JAVA; break;
            case "xml": p = XML; break;
            case "json": p = JSON; break;
            case "props": p = PROPS; break;
            case "smali": p = SMALI; break;
            default: return;
        }
        CharSequence region = e.subSequence(from, to);
        Matcher m = p.matcher(region);
        while (m.find()) {
            int col = colorFor(m, lang, s);
            if (col != 0 && m.end() > m.start()) {
                e.setSpan(new Hl(col), from + m.start(), from + m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static int colorFor(Matcher m, String lang, EditorScheme s) {
        try {
            if (m.group("c") != null) return s.comment;
        } catch (IllegalArgumentException ignored) { }
        if (lang.equals("xml")) {
            if (grp(m, "d")) return s.comment;
            if (grp(m, "g")) return s.tag;
            if (grp(m, "r")) return s.attr;
            if (grp(m, "s")) return s.string;
            if (grp(m, "e")) return s.number;
            return 0;
        }
        if (grp(m, "r")) return lang.equals("json") ? s.attr : s.keyword;
        if (grp(m, "s")) return s.string;
        if (grp(m, "a")) return lang.equals("smali") ? s.number : s.annot;
        if (grp(m, "n")) return s.number;
        if (grp(m, "k")) return s.keyword;
        if (grp(m, "t")) return s.type;
        if (grp(m, "f")) return s.func;
        return 0;
    }

    private static boolean grp(Matcher m, String name) {
        try {
            return m.group(name) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
