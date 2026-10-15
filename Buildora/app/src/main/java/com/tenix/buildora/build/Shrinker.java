package com.tenix.buildora.build;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.tenix.buildora.core.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Release shrinking / obfuscation / optimisation with R8. */
public final class Shrinker {
    private Shrinker() {}

    /** Baseline rules (the same spirit as proguard-android-optimize.txt). */
    public static final String BASE_RULES =
            "-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions\n" +
            "-keepattributes SourceFile,LineNumberTable\n" +
            "-renamesourcefileattribute SourceFile\n" +
            "-ignorewarnings\n" +
            "-keep public class * extends android.app.Activity\n" +
            "-keep public class * extends android.app.Application\n" +
            "-keep public class * extends android.app.Service\n" +
            "-keep public class * extends android.content.BroadcastReceiver\n" +
            "-keep public class * extends android.content.ContentProvider\n" +
            "-keep public class * extends android.app.backup.BackupAgent\n" +
            "-keep public class * extends android.preference.Preference\n" +
            "-keepclassmembers class * extends android.app.Activity { public void *(android.view.View); }\n" +
            "-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }\n" +
            "-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }\n" +
            "-keepclassmembers class * implements android.os.Parcelable { public static final ** CREATOR; }\n" +
            "-keepclassmembers class * implements java.io.Serializable {\n" +
            "    static final long serialVersionUID; private static final java.io.ObjectStreamField[] serialPersistentFields;\n" +
            "    private void writeObject(java.io.ObjectOutputStream); private void readObject(java.io.ObjectInputStream);\n" +
            "    java.lang.Object writeReplace(); java.lang.Object readResolve(); }\n" +
            "-keepclassmembers class **.R$* { public static <fields>; }\n";

    public static final class Output {
        public final List<File> dex = new ArrayList<>();
        public File mapping;
    }

    public static Output run(File androidJar, List<File> program, List<File> ruleFiles, File extraRulesText,
                             boolean obfuscate, boolean optimize, int minSdk, File outDir, File mappingFile,
                             final List<String> messages) throws Exception {
        File rules = new File(outDir.getParentFile(), "r8-rules.pro");
        StringBuilder sb = new StringBuilder(BASE_RULES);
        if (!obfuscate) sb.append("-dontobfuscate\n");
        if (!optimize) sb.append("-dontoptimize\n");
        FileUtil.writeText(rules, sb.toString());

        DiagnosticsHandler handler = new DiagnosticsHandler() {
            @Override public void error(Diagnostic d) { messages.add("error: " + d.getDiagnosticMessage()); }
            @Override public void warning(Diagnostic d) { }
            @Override public void info(Diagnostic d) { }
        };
        try {
            outDir.mkdirs();
            R8Command.Builder b = R8Command.builder(handler);
            List<Path> prog = new ArrayList<>();
            for (File f : program) prog.add(f.toPath());
            b.addProgramFiles(prog);
            b.addLibraryFiles(androidJar.toPath());
            List<Path> cfg = new ArrayList<>();
            cfg.add(rules.toPath());
            for (File f : ruleFiles) if (f != null && f.isFile() && f.length() > 0) cfg.add(f.toPath());
            if (extraRulesText != null && extraRulesText.isFile()) cfg.add(extraRulesText.toPath());
            b.addProguardConfigurationFiles(cfg);
            b.setMinApiLevel(minSdk);
            b.setMode(CompilationMode.RELEASE);
            b.setOutput(outDir.toPath(), OutputMode.DexIndexed);
            b.setProguardMapOutputPath(mappingFile.toPath());
            R8.run(b.build());
        } catch (CompilationFailedException e) {
            StringBuilder m = new StringBuilder("R8 failed:");
            for (String s : messages) m.append('\n').append(s);
            if (messages.isEmpty()) m.append(' ').append(e.getMessage());
            throw new IOException(m.toString());
        }
        Output o = new Output();
        File[] fs = outDir.listFiles();
        if (fs != null) for (File f : fs) if (f.getName().endsWith(".dex")) o.dex.add(f);
        o.mapping = mappingFile.isFile() ? mappingFile : null;
        return o;
    }
}
