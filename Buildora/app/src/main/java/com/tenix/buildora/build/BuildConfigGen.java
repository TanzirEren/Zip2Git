package com.tenix.buildora.build;

import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.Variant;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generates BuildConfig.java like the Android Gradle plugin does. */
public final class BuildConfigGen {
    private BuildConfigGen() {}

    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(String|int|long|boolean|float|double)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+?)\\s*;?\\s*$");

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static File generate(File genDir, String pkg, Variant v) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("/** Automatically generated file. DO NOT MODIFY */\n");
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("public final class BuildConfig {\n");
        sb.append("    public static final boolean DEBUG = ").append(v.debuggable).append(";\n");
        sb.append("    public static final String APPLICATION_ID = ").append(q(v.applicationId)).append(";\n");
        sb.append("    public static final String BUILD_TYPE = ").append(q(v.buildType)).append(";\n");
        sb.append("    public static final String FLAVOR = ").append(q(v.flavor)).append(";\n");
        sb.append("    public static final int VERSION_CODE = ").append(v.versionCode).append(";\n");
        sb.append("    public static final String VERSION_NAME = ").append(q(v.versionName)).append(";\n");
        for (String line : v.buildConfigFields) {
            Matcher m = FIELD.matcher(line);
            if (!m.matches()) continue;
            String type = m.group(1), name = m.group(2), val = m.group(3);
            if (name.equals("DEBUG") || name.equals("APPLICATION_ID") || name.equals("BUILD_TYPE")
                    || name.equals("FLAVOR") || name.equals("VERSION_CODE") || name.equals("VERSION_NAME")) continue;
            if (type.equals("long") && !val.endsWith("L")) val += "L";
            if (type.equals("float") && !val.endsWith("f") && !val.endsWith("F")) val += "f";
            sb.append("    public static final ").append(type).append(' ').append(name).append(" = ").append(val).append(";\n");
        }
        sb.append("}\n");
        File out = new File(genDir, pkg.replace('.', '/') + "/BuildConfig.java");
        FileUtil.writeText(out, sb.toString());
        return out;
    }
}
