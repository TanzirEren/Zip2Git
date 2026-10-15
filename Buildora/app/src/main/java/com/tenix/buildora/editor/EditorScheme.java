package com.tenix.buildora.editor;

import android.graphics.Color;

public class EditorScheme {
    public final String name;
    public final boolean dark;
    public final int bg, fg, gutterBg, gutterFg, curLine, selection;
    public final int keyword, type, string, comment, number, annot, tag, attr, func, error;

    private EditorScheme(String name, boolean dark, String bg, String fg, String gutterBg, String gutterFg,
                         String curLine, String selection, String keyword, String type, String string,
                         String comment, String number, String annot, String tag, String attr, String func) {
        this.name = name;
        this.dark = dark;
        this.bg = Color.parseColor(bg);
        this.fg = Color.parseColor(fg);
        this.gutterBg = Color.parseColor(gutterBg);
        this.gutterFg = Color.parseColor(gutterFg);
        this.curLine = Color.parseColor(curLine);
        this.selection = Color.parseColor(selection);
        this.keyword = Color.parseColor(keyword);
        this.type = Color.parseColor(type);
        this.string = Color.parseColor(string);
        this.comment = Color.parseColor(comment);
        this.number = Color.parseColor(number);
        this.annot = Color.parseColor(annot);
        this.tag = Color.parseColor(tag);
        this.attr = Color.parseColor(attr);
        this.func = Color.parseColor(func);
        this.error = Color.parseColor("#E5484D");
    }

    public static final EditorScheme[] ALL = {
            new EditorScheme("Buildora Light", false, "#FFFFFF", "#24292F", "#F4F2EE", "#9A9890", "#F3F6FA", "#B3D4F5",
                    "#B5311B", "#0E6E8C", "#1A7F37", "#8A8F98", "#B35900", "#8250DF", "#116329", "#0550AE", "#6639BA"),
            new EditorScheme("Buildora Dark", true, "#16171B", "#D9D7D0", "#1B1C21", "#6A6A72", "#1F2127", "#2F4C6E",
                    "#E58A6B", "#6CC4D9", "#9BCB7A", "#6B7280", "#E5B567", "#C3A0F0", "#7FD1A0", "#8DB8EE", "#C9A7F5"),
            new EditorScheme("Monokai", true, "#272822", "#F8F8F2", "#2D2E27", "#75715E", "#3E3D32", "#49483E",
                    "#F92672", "#66D9EF", "#E6DB74", "#75715E", "#AE81FF", "#A6E22E", "#F92672", "#A6E22E", "#A6E22E"),
            new EditorScheme("Dracula", true, "#282A36", "#F8F8F2", "#21222C", "#6272A4", "#343746", "#44475A",
                    "#FF79C6", "#8BE9FD", "#F1FA8C", "#6272A4", "#BD93F9", "#50FA7B", "#FF79C6", "#50FA7B", "#50FA7B"),
            new EditorScheme("GitHub Light", false, "#FFFFFF", "#1F2328", "#F6F8FA", "#8C959F", "#F6F8FA", "#B6E3FF",
                    "#CF222E", "#953800", "#0A3069", "#6E7781", "#0550AE", "#8250DF", "#116329", "#0550AE", "#8250DF"),
            new EditorScheme("Solarized Dark", true, "#002B36", "#93A1A1", "#073642", "#586E75", "#073642", "#0F4A5C",
                    "#859900", "#B58900", "#2AA198", "#586E75", "#D33682", "#CB4B16", "#268BD2", "#93A1A1", "#268BD2"),
    };

    public static EditorScheme get(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    public static String[] names() {
        String[] n = new String[ALL.length];
        for (int i = 0; i < n.length; i++) n[i] = ALL[i].name;
        return n;
    }
}
