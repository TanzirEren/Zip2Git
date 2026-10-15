package com.tenix.buildora.core;

/** Ready-made R8 / ProGuard rule snippets that can be toggled in the ProGuard rules editor. */
public final class ProguardPresets {
    private ProguardPresets() {}

    public static final class Preset {
        public final String id, title, desc, rules;
        Preset(String id, String title, String desc, String rules) { this.id = id; this.title = title; this.desc = desc; this.rules = rules; }
    }

    public static final String FILE_HEADER =
            "# ProGuard / R8 rules for release builds (build type with 'Shrink code' enabled).\n" +
            "# Buildora already applies sensible Android defaults and the rules that come with your libraries.\n" +
            "# Add your own rules below, or switch presets on from the preset list.\n\n";

    public static final Preset[] ALL = {
            new Preset("keep_package", "Keep all my app's classes", "Disables shrinking/renaming for your own package (safe, bigger APK).",
                    "-keep class %PKG%.** { *; }\n"),
            new Preset("gson", "Gson", "Keeps generic type info and @SerializedName fields.",
                    "-keepattributes Signature\n-keepattributes *Annotation*\n-keep class com.google.gson.reflect.TypeToken { *; }\n" +
                    "-keep class * extends com.google.gson.reflect.TypeToken\n" +
                    "-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }\n" +
                    "# Keep your model classes (change the package):\n#-keep class %PKG%.model.** { *; }\n"),
            new Preset("retrofit_okhttp", "Retrofit + OkHttp", "Keeps Retrofit service interfaces and silences OkHttp warnings.",
                    "-dontwarn okhttp3.**\n-dontwarn okio.**\n-dontwarn retrofit2.**\n-dontwarn javax.annotation.**\n" +
                    "-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault\n" +
                    "-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }\n"),
            new Preset("moshi", "Moshi", "Keeps @ToJson/@FromJson adapters and @JsonClass models.",
                    "-keepclassmembers class * { @com.squareup.moshi.FromJson *; @com.squareup.moshi.ToJson *; }\n" +
                    "-keep @com.squareup.moshi.JsonQualifier @interface *\n-keep @com.squareup.moshi.JsonClass class * { *; }\n"),
            new Preset("glide", "Glide", "Keeps Glide modules and image header parsers.",
                    "-keep public class * implements com.bumptech.glide.module.GlideModule\n" +
                    "-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }\n" +
                    "-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }\n"),
            new Preset("firebase", "Firebase / Play Services", "Keeps Firebase and Google Play services classes.",
                    "-keep class com.google.firebase.** { *; }\n-keep class com.google.android.gms.** { *; }\n" +
                    "-dontwarn com.google.firebase.**\n-dontwarn com.google.android.gms.**\n" +
                    "# Keep model classes used with Realtime Database / Firestore:\n#-keepclassmembers class %PKG%.model.** { *; }\n"),
            new Preset("room", "Room", "Keeps Room databases and entities.",
                    "-keep class * extends androidx.room.RoomDatabase\n-keep @androidx.room.Entity class *\n-dontwarn androidx.room.paging.**\n"),
            new Preset("webview_js", "WebView JavaScript interface", "Keeps methods exposed with @JavascriptInterface.",
                    "-keepattributes JavascriptInterface\n-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }\n"),
            new Preset("keep_annotation", "Honour @Keep", "Keeps anything annotated with androidx.annotation.Keep.",
                    "-keep @androidx.annotation.Keep class * { *; }\n-keepclassmembers class * { @androidx.annotation.Keep *; }\n"),
            new Preset("kotlin", "Kotlin", "Silences Kotlin warnings and keeps Kotlin metadata.",
                    "-dontwarn kotlin.**\n-keep class kotlin.Metadata { *; }\n-keepclassmembers class **$WhenMappings { <fields>; }\n"),
            new Preset("rxjava", "RxJava", "Silences RxJava warnings.",
                    "-dontwarn io.reactivex.**\n-dontwarn java.util.concurrent.Flow*\n"),
            new Preset("eventbus", "EventBus", "Keeps @Subscribe methods.",
                    "-keepattributes *Annotation*\n-keepclassmembers class * { @org.greenrobot.eventbus.Subscribe <methods>; }\n" +
                    "-keep enum org.greenrobot.eventbus.ThreadMode { *; }\n"),
            new Preset("remove_logs", "Remove Log.d / v / i calls", "Strips debug logging from release builds.",
                    "-assumenosideeffects class android.util.Log {\n    public static *** d(...);\n    public static *** v(...);\n    public static *** i(...);\n}\n"),
            new Preset("no_obfuscate", "Don't rename (shrink only)", "Removes unused code but keeps readable names.", "-dontobfuscate\n"),
            new Preset("no_optimize", "Don't optimize", "Skips R8's code optimizations (use if something breaks).", "-dontoptimize\n"),
    };

    private static String open(String id) { return "# >>> preset: " + id + "\n"; }
    private static String close(String id) { return "# <<< preset: " + id + "\n"; }

    public static Preset find(String id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return null;
    }

    public static boolean isApplied(String text, String id) { return text.contains(open(id)); }

    public static String apply(String text, String id, String pkg) {
        Preset p = find(id);
        if (p == null || isApplied(text, id)) return text;
        String body = p.rules.replace("%PKG%", pkg == null || pkg.isEmpty() ? "com.example.app" : pkg);
        String sep = text.isEmpty() || text.endsWith("\n\n") ? "" : (text.endsWith("\n") ? "\n" : "\n\n");
        return text + sep + open(id) + body + close(id);
    }

    public static String remove(String text, String id) {
        int a = text.indexOf(open(id));
        if (a < 0) return text;
        int b = text.indexOf(close(id), a);
        if (b < 0) return text;
        int end = b + close(id).length();
        while (end < text.length() && text.charAt(end) == '\n' && end + 1 < text.length() && text.charAt(end + 1) == '\n') end++;
        return text.substring(0, a) + text.substring(end);
    }
}
