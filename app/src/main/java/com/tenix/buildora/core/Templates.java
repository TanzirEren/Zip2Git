package com.tenix.buildora.core;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.IOException;

/** Creates the default, buildable project files for a new project. */
public final class Templates {
    private Templates() {}

    public static final String[] NAMES = {
            "Empty Activity",
            "Counter Demo",
            "WebView App",
            "AppCompat Activity (uses library manager)"
    };

    public static final String[] THEMES = {"Light", "Dark", "Light - no action bar"};
    public static final String[] ORIENTATIONS = {"Unspecified", "Portrait", "Landscape"};

    public static class Options {
        public String projectName, appName, packageName, versionName = "1.0";
        public int minSdk = 21, targetSdk = 34, versionCode = 1, iconRadius = 22;
        public int template = 0, theme = 0, orientation = 0;
        public boolean internet = false;
        public Bitmap icon;
    }

    private static String j(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    public static Project create(Context ctx, Options o) throws IOException {
        File root = ProjectStore.uniqueRoot(o.projectName);
        File main = new File(root, "app/src/main");
        File javaDir = new File(main, "java/" + o.packageName.replace('.', '/'));
        File res = new File(main, "res");
        boolean appCompat = o.template == 3;
        if (o.template == 2) o.internet = true;

        javaDir.mkdirs();
        new File(res, "layout").mkdirs();
        new File(res, "values").mkdirs();
        new File(main, "assets").mkdirs();
        new File(root, "app/libs").mkdirs();
        FileUtil.writeText(new File(main, "assets/.gitkeep"), "");
        FileUtil.writeText(new File(root, "app/libs/.gitkeep"), "");

        // ---- manifest
        StringBuilder mf = new StringBuilder();
        mf.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        mf.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        mf.append("    package=\"").append(o.packageName).append("\">\n\n");
        if (o.internet) mf.append("    <uses-permission android:name=\"android.permission.INTERNET\" />\n\n");
        mf.append("    <application\n");
        mf.append("        android:allowBackup=\"true\"\n");
        mf.append("        android:icon=\"@mipmap/ic_launcher\"\n");
        mf.append("        android:roundIcon=\"@mipmap/ic_launcher_round\"\n");
        mf.append("        android:label=\"@string/app_name\"\n");
        mf.append("        android:theme=\"@style/AppTheme\">\n\n");
        mf.append("        <activity\n");
        mf.append("            android:name=\".MainActivity\"\n");
        if (o.orientation == 1) mf.append("            android:screenOrientation=\"portrait\"\n");
        if (o.orientation == 2) mf.append("            android:screenOrientation=\"landscape\"\n");
        mf.append("            android:exported=\"true\">\n");
        mf.append("            <intent-filter>\n");
        mf.append("                <action android:name=\"android.intent.action.MAIN\" />\n");
        mf.append("                <category android:name=\"android.intent.category.LAUNCHER\" />\n");
        mf.append("            </intent-filter>\n");
        mf.append("        </activity>\n\n");
        mf.append("    </application>\n</manifest>\n");
        FileUtil.writeText(new File(main, "AndroidManifest.xml"), mf.toString());

        // ---- values
        FileUtil.writeText(new File(res, "values/strings.xml"), j(
                "<resources>",
                "    <string name=\"app_name\">" + xml(o.appName) + "</string>",
                "</resources>"));
        FileUtil.writeText(new File(res, "values/colors.xml"), j(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <color name=\"colorPrimary\">#3F7EB8</color>",
                "    <color name=\"colorPrimaryDark\">#2F5F8C</color>",
                "    <color name=\"colorAccent\">#E0A800</color>",
                "</resources>"));
        String parent;
        String ns = appCompat ? "" : "android:";
        if (appCompat) {
            parent = o.theme == 1 ? "Theme.AppCompat" : o.theme == 2 ? "Theme.AppCompat.Light.NoActionBar" : "Theme.AppCompat.Light.DarkActionBar";
        } else {
            parent = o.theme == 1 ? "android:Theme.Material" : o.theme == 2 ? "android:Theme.Material.Light.NoActionBar" : "android:Theme.Material.Light.DarkActionBar";
        }
        FileUtil.writeText(new File(res, "values/themes.xml"), j(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <style name=\"AppTheme\" parent=\"" + parent + "\">",
                "        <item name=\"" + ns + "colorPrimary\">@color/colorPrimary</item>",
                "        <item name=\"" + ns + "colorPrimaryDark\">@color/colorPrimaryDark</item>",
                "        <item name=\"" + ns + "colorAccent\">@color/colorAccent</item>",
                "    </style>",
                "</resources>"));

        // ---- layout + activity
        String pkg = o.packageName;
        String actBase = appCompat ? "androidx.appcompat.app.AppCompatActivity" : "android.app.Activity";
        String actSimple = appCompat ? "AppCompatActivity" : "Activity";
        switch (o.template) {
            case 1: // counter
                FileUtil.writeText(new File(res, "layout/activity_main.xml"), j(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "    android:layout_width=\"match_parent\"",
                        "    android:layout_height=\"match_parent\"",
                        "    android:gravity=\"center\"",
                        "    android:orientation=\"vertical\"",
                        "    android:padding=\"24dp\">",
                        "",
                        "    <TextView",
                        "        android:id=\"@+id/tvCount\"",
                        "        android:layout_width=\"wrap_content\"",
                        "        android:layout_height=\"wrap_content\"",
                        "        android:text=\"0\"",
                        "        android:textSize=\"64sp\" />",
                        "",
                        "    <Button",
                        "        android:id=\"@+id/btnPlus\"",
                        "        android:layout_width=\"wrap_content\"",
                        "        android:layout_height=\"wrap_content\"",
                        "        android:layout_marginTop=\"16dp\"",
                        "        android:text=\"Tap me\" />",
                        "",
                        "</LinearLayout>"));
                FileUtil.writeText(new File(javaDir, "MainActivity.java"), j(
                        "package " + pkg + ";",
                        "",
                        "import android.os.Bundle;",
                        "import android.view.View;",
                        "import android.widget.Button;",
                        "import android.widget.TextView;",
                        "import android.widget.Toast;",
                        "import " + actBase + ";",
                        "",
                        "public class MainActivity extends " + actSimple + " {",
                        "",
                        "    private int count = 0;",
                        "",
                        "    @Override",
                        "    protected void onCreate(Bundle savedInstanceState) {",
                        "        super.onCreate(savedInstanceState);",
                        "        setContentView(R.layout.activity_main);",
                        "",
                        "        final TextView tvCount = findViewById(R.id.tvCount);",
                        "        Button btnPlus = findViewById(R.id.btnPlus);",
                        "        btnPlus.setOnClickListener(new View.OnClickListener() {",
                        "            @Override",
                        "            public void onClick(View v) {",
                        "                count++;",
                        "                tvCount.setText(String.valueOf(count));",
                        "                if (count % 10 == 0) {",
                        "                    Toast.makeText(MainActivity.this, \"Nice! \" + count, Toast.LENGTH_SHORT).show();",
                        "                }",
                        "            }",
                        "        });",
                        "    }",
                        "}"));
                break;
            case 2: // webview
                FileUtil.writeText(new File(res, "layout/activity_main.xml"), j(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "    android:layout_width=\"match_parent\"",
                        "    android:layout_height=\"match_parent\">",
                        "",
                        "    <WebView",
                        "        android:id=\"@+id/webView\"",
                        "        android:layout_width=\"match_parent\"",
                        "        android:layout_height=\"match_parent\" />",
                        "",
                        "</FrameLayout>"));
                FileUtil.writeText(new File(javaDir, "MainActivity.java"), j(
                        "package " + pkg + ";",
                        "",
                        "import android.os.Bundle;",
                        "import android.webkit.WebSettings;",
                        "import android.webkit.WebView;",
                        "import android.webkit.WebViewClient;",
                        "import " + actBase + ";",
                        "",
                        "public class MainActivity extends " + actSimple + " {",
                        "",
                        "    private WebView webView;",
                        "",
                        "    @Override",
                        "    protected void onCreate(Bundle savedInstanceState) {",
                        "        super.onCreate(savedInstanceState);",
                        "        setContentView(R.layout.activity_main);",
                        "",
                        "        webView = findViewById(R.id.webView);",
                        "        WebSettings s = webView.getSettings();",
                        "        s.setJavaScriptEnabled(true);",
                        "        s.setDomStorageEnabled(true);",
                        "        webView.setWebViewClient(new WebViewClient());",
                        "        webView.loadUrl(\"https://example.com\");",
                        "    }",
                        "",
                        "    @Override",
                        "    public void onBackPressed() {",
                        "        if (webView.canGoBack()) webView.goBack();",
                        "        else super.onBackPressed();",
                        "    }",
                        "}"));
                break;
            default: // empty / appcompat
                FileUtil.writeText(new File(res, "layout/activity_main.xml"), j(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "    android:layout_width=\"match_parent\"",
                        "    android:layout_height=\"match_parent\"",
                        "    android:gravity=\"center\"",
                        "    android:orientation=\"vertical\">",
                        "",
                        "    <TextView",
                        "        android:layout_width=\"wrap_content\"",
                        "        android:layout_height=\"wrap_content\"",
                        "        android:text=\"Hello from " + xml(o.appName) + "!\"",
                        "        android:textSize=\"22sp\" />",
                        "",
                        "</LinearLayout>"));
                FileUtil.writeText(new File(javaDir, "MainActivity.java"), j(
                        "package " + pkg + ";",
                        "",
                        "import android.os.Bundle;",
                        "import " + actBase + ";",
                        "",
                        "public class MainActivity extends " + actSimple + " {",
                        "",
                        "    @Override",
                        "    protected void onCreate(Bundle savedInstanceState) {",
                        "        super.onCreate(savedInstanceState);",
                        "        setContentView(R.layout.activity_main);",
                        "    }",
                        "}"));
        }

        // ---- icon
        Bitmap icon = o.icon != null ? o.icon : IconUtil.loadDefault(ctx);
        IconUtil.writeMipmaps(icon, res, o.iconRadius);

        // ---- gradle compatibility files + housekeeping
        FileUtil.writeText(new File(root, "settings.gradle"), j(
                "pluginManagement {",
                "    repositories { google(); mavenCentral(); gradlePluginPortal() }",
                "}",
                "dependencyResolutionManagement {",
                "    repositories { google(); mavenCentral(); maven { url 'https://jitpack.io' } }",
                "}",
                "rootProject.name = \"" + o.appName.replace("\"", "") + "\"",
                "include ':app'"));
        FileUtil.writeText(new File(root, "build.gradle"), j(
                "plugins {",
                "    id 'com.android.application' version '8.2.2' apply false",
                "}"));
        FileUtil.writeText(new File(root, "gradle.properties"), j(
                "org.gradle.jvmargs=-Xmx2g",
                "android.useAndroidX=true"));
        Project p = new Project();
        p.root = root;
        p.name = root.getName();
        p.appName = o.appName;
        p.packageName = o.packageName;
        p.versionName = o.versionName;
        p.versionCode = o.versionCode;
        p.minSdk = o.minSdk;
        p.targetSdk = o.targetSdk;
        p.iconRadius = o.iconRadius;
        if (appCompat) p.dependencies.add("androidx.appcompat:appcompat:1.6.1");

        StringBuilder deps = new StringBuilder();
        for (String d : p.dependencies) deps.append("    implementation '").append(d).append("'\n");
        FileUtil.writeText(new File(root, "app/build.gradle"), j(
                "plugins {",
                "    id 'com.android.application'",
                "}",
                "",
                "android {",
                "    namespace '" + o.packageName + "'",
                "    compileSdk 34",
                "",
                "    defaultConfig {",
                "        applicationId '" + o.packageName + "'",
                "        minSdk " + o.minSdk,
                "        targetSdk " + o.targetSdk,
                "        versionCode " + o.versionCode,
                "        versionName '" + o.versionName + "'",
                "    }",
                "}",
                "",
                "dependencies {",
                deps.toString() + "}"));
        FileUtil.writeText(new File(root, ".gitignore"), j(
                ".buildora/", "build/", ".gradle/", "local.properties", "*.apk", ".idea/", "*.iml"));
        FileUtil.writeText(new File(root, "README.md"), j(
                "# " + o.appName, "",
                "Created with **Buildora** (Java + XML).", "",
                "- Package: `" + o.packageName + "`",
                "- Min SDK " + o.minSdk + " / Target SDK " + o.targetSdk));

        p.detectLayout();
        FileUtil.writeText(p.proguardFile(), ProguardPresets.FILE_HEADER);
        p.ensureDefaults();
        p.save();
        return p;
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace("\"", "&quot;");
    }

    public static String defaultJava(String pkg, String cls) {
        return (pkg == null || pkg.isEmpty() ? "" : "package " + pkg + ";\n\n") +
                "public class " + cls + " {\n\n}\n";
    }

    public static String defaultLayout() {
        return j("<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "    android:layout_width=\"match_parent\"",
                "    android:layout_height=\"match_parent\"",
                "    android:orientation=\"vertical\">",
                "",
                "</LinearLayout>");
    }

    public static String defaultXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n\n</resources>\n";
    }
}
