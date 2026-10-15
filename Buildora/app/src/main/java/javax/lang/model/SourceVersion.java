package javax.lang.model;

/**
 * Minimal replacement for javax.lang.model.SourceVersion, which does not exist on Android.
 * The Eclipse Java compiler (ECJ) touches it in FileSystem.<clinit> to detect "running on JRE 12+".
 * RELEASE_9 .. RELEASE_12 are deliberately absent: valueOf("RELEASE_12") must throw
 * IllegalArgumentException so ECJ falls back to plain classpath mode (android.jar via -bootclasspath).
 */
public enum SourceVersion {
    RELEASE_0, RELEASE_1, RELEASE_2, RELEASE_3, RELEASE_4, RELEASE_5, RELEASE_6, RELEASE_7, RELEASE_8;

    public static SourceVersion latest() { return RELEASE_8; }
    public static SourceVersion latestSupported() { return RELEASE_8; }

    public static boolean isIdentifier(CharSequence name) {
        String id = name.toString();
        if (id.length() == 0) return false;
        int cp = id.codePointAt(0);
        if (!Character.isJavaIdentifierStart(cp)) return false;
        for (int i = Character.charCount(cp); i < id.length(); i += Character.charCount(cp)) {
            cp = id.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp)) return false;
        }
        return true;
    }

    public static boolean isName(CharSequence name) {
        for (String part : name.toString().split("\\.", -1)) {
            if (!isIdentifier(part) || isKeyword(part)) return false;
        }
        return true;
    }

    public static boolean isKeyword(CharSequence s) {
        switch (s.toString()) {
            case "abstract": case "assert": case "boolean": case "break": case "byte": case "case": case "catch":
            case "char": case "class": case "const": case "continue": case "default": case "do": case "double":
            case "else": case "enum": case "extends": case "final": case "finally": case "float": case "for":
            case "goto": case "if": case "implements": case "import": case "instanceof": case "int": case "interface":
            case "long": case "native": case "new": case "package": case "private": case "protected": case "public":
            case "return": case "short": case "static": case "strictfp": case "super": case "switch":
            case "synchronized": case "this": case "throw": case "throws": case "transient": case "try":
            case "void": case "volatile": case "while": case "true": case "false": case "null": case "_":
                return true;
            default:
                return false;
        }
    }
}
