package com.tenix.buildora.build;

/**
 * Built-in catalog of libraries. A version of "+" (or none) means "latest stable at build time".
 * Entries with several coordinates add the whole bundle (for example CameraX).
 */
public final class Catalog {
    private Catalog() {}

    /** Works as a normal dependency. */
    public static final int OK = 0;
    /** Added fine, but its code generator needs an annotation processor / Gradle plugin that Buildora cannot run yet. */
    public static final int PROCESSOR = 1;
    /** Not an app library (build tool, platform feature). Shown for information only. */
    public static final int TOOL = 2;
    /** No Maven artifact: import the .jar / .aar yourself. */
    public static final int MANUAL = 3;
    /** Needs a Gradle plugin to work at all: not supported. */
    public static final int UNSUPPORTED = 4;

    public static final class Lib {
        public final String group, name, desc, note;
        public final int status;
        public final String[] coords;

        Lib(String group, String name, String desc, int status, String note, String... coords) {
            this.group = group; this.name = name; this.desc = desc; this.status = status; this.note = note; this.coords = coords;
        }

        public boolean addable() { return status == OK || status == PROCESSOR; }
    }

    private static Lib ok(String g, String n, String d, String... c) { return new Lib(g, n, d, OK, "", c); }
    private static Lib ok(String g, String n, String d, String note, String[] c) { return new Lib(g, n, d, OK, note, c); }
    private static Lib proc(String g, String n, String d, String note, String... c) { return new Lib(g, n, d, PROCESSOR, note, c); }
    private static Lib tool(String g, String n, String d, String note, String... c) { return new Lib(g, n, d, TOOL, note, c); }
    private static Lib manual(String g, String n, String d, String note) { return new Lib(g, n, d, MANUAL, note); }
    private static Lib nope(String g, String n, String d, String note) { return new Lib(g, n, d, UNSUPPORTED, note); }

    public static final String[] GROUPS = {"UI", "Networking", "JSON & data formats", "Images & camera", "Database & storage",
            "Firebase & backends", "Google Play services", "Async & architecture", "Media", "Logging & debugging", "Testing", "Utilities & tools"};

    public static final Lib[] ALL = {
            // ---------------------------------------------------------------- UI
            ok("UI", "AndroidX Core", "Core utilities, FileProvider, NotificationCompat", "androidx.core:core:1.12.0"),
            ok("UI", "AppCompat", "Backwards compatible Activity, themes, toolbar", "androidx.appcompat:appcompat:1.6.1"),
            ok("UI", "Material Components", "Material Design widgets and themes", "com.google.android.material:material:1.11.0"),
            ok("UI", "Material 3", "Same library: use the Theme.Material3.* styles", "Material 3 is part of Material Components.", new String[]{"com.google.android.material:material:1.11.0"}),
            ok("UI", "ConstraintLayout", "Flexible constraint based layout", "androidx.constraintlayout:constraintlayout:2.1.4"),
            ok("UI", "RecyclerView", "Efficient scrolling lists and grids", "androidx.recyclerview:recyclerview:1.3.2"),
            ok("UI", "ViewPager2", "Swipeable pages with tabs", "androidx.viewpager2:viewpager2:1.0.0"),
            ok("UI", "CardView", "Rounded card container", "androidx.cardview:cardview:1.0.0"),
            ok("UI", "SwipeRefreshLayout", "Pull to refresh", "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0"),
            ok("UI", "CoordinatorLayout", "Scrolling behaviours", "androidx.coordinatorlayout:coordinatorlayout:1.2.0"),
            ok("UI", "Flexbox Layout", "CSS-like flex layout for Android", "com.google.android.flexbox:flexbox:3.0.0"),
            ok("UI", "Google Flexbox", "Same library as Flexbox Layout", "Google's Flexbox is the library above.", new String[]{"com.google.android.flexbox:flexbox:3.0.0"}),
            ok("UI", "Lottie", "Lottie animations", "com.airbnb.android:lottie:6.3.0"),
            proc("UI", "Epoxy", "Build complex RecyclerView screens", "Hand-written models work; the @EpoxyModelClass generators need an annotation processor.",
                    "com.airbnb.android:epoxy:5.1.4"),
            ok("UI", "Shimmer", "Shimmer loading effect", "com.facebook.shimmer:shimmer:0.5.0"),
            ok("UI", "Skeleton Layout", "Skeleton loading placeholders", "com.faltenreich:skeletonlayout:+"),
            ok("UI", "PhotoView", "Zoomable ImageView (JitPack)", "com.github.chrisbanes:PhotoView:2.3.0"),
            ok("UI", "CircleImageView", "Circular ImageView", "de.hdodenhof:circleimageview:3.1.0"),
            ok("UI", "ShapeOfView", "Custom shaped views", "io.github.florent37:shapeofview:+"),
            ok("UI", "AndroidAutoSize", "Automatic screen adaptation (JitPack)", "com.github.JessYanCoding:AndroidAutoSize:v1.2.1"),
            ok("UI", "MPAndroidChart", "Charts (JitPack)", "com.github.PhilJay:MPAndroidChart:v3.1.0"),
            ok("UI", "Navigation Component", "Fragment navigation graph + UI helpers", "androidx.navigation:navigation-fragment:2.7.6", "androidx.navigation:navigation-ui:2.7.6"),
            ok("UI", "Fragment", "Fragments", "androidx.fragment:fragment:1.6.2"),
            ok("UI", "Activity", "Activity result APIs, back handling", "androidx.activity:activity:1.8.2"),
            ok("UI", "Preference", "Settings screens", "androidx.preference:preference:1.2.1"),
            ok("UI", "DrawerLayout", "Navigation drawer", "androidx.drawerlayout:drawerlayout:1.2.0"),
            ok("UI", "WebViewAssetLoader (WebKit)", "Load local assets in a WebView safely", "androidx.webkit:webkit:1.9.0"),
            ok("UI", "Biometric", "Fingerprint / face prompt", "androidx.biometric:biometric:1.1.0"),
            ok("UI", "Browser (Custom Tabs)", "Chrome Custom Tabs", "androidx.browser:browser:1.7.0"),

            // ---------------------------------------------------------------- Networking
            ok("Networking", "Retrofit", "Type-safe REST client", "com.squareup.retrofit2:retrofit:2.9.0"),
            ok("Networking", "Retrofit + Gson", "Retrofit with the Gson converter", "com.squareup.retrofit2:retrofit:2.9.0", "com.squareup.retrofit2:converter-gson:2.9.0"),
            ok("Networking", "Retrofit + Moshi", "Retrofit with the Moshi converter", "com.squareup.retrofit2:retrofit:2.9.0", "com.squareup.retrofit2:converter-moshi:2.9.0", "com.squareup.moshi:moshi:1.15.0"),
            ok("Networking", "OkHttp", "HTTP client", "com.squareup.okhttp3:okhttp:4.12.0"),
            ok("Networking", "OkHttp + Logging Interceptor", "OkHttp with request/response logging", "com.squareup.okhttp3:okhttp:4.12.0", "com.squareup.okhttp3:logging-interceptor:4.12.0"),
            ok("Networking", "Volley", "HTTP library by Google", "com.android.volley:volley:1.2.1"),
            ok("Networking", "Ktor Client", "Kotlin HTTP client (Android engine)", "io.ktor:ktor-client-android:2.3.7"),
            ok("Networking", "Fuel HTTP", "Kotlin HTTP library", "com.github.kittinunf.fuel:fuel:2.3.1"),
            ok("Networking", "AndroidAsync", "Async sockets, HTTP and WebSocket", "com.koushikdutta.async:androidasync:3.1.0"),
            ok("Networking", "Apache HttpClient (Android)", "Android port of Apache HttpClient 4", "cz.msebera.android:httpclient:4.4.1.2"),
            ok("Networking", "Jsoup", "HTML parser / scraper", "org.jsoup:jsoup:1.17.2"),

            // ---------------------------------------------------------------- JSON
            ok("JSON & data formats", "Gson", "JSON serialization", "com.google.code.gson:gson:2.10.1"),
            ok("JSON & data formats", "Moshi", "Modern JSON library", "com.squareup.moshi:moshi:1.15.0"),
            ok("JSON & data formats", "Jackson", "JSON data binding", "com.fasterxml.jackson.core:jackson-databind:2.16.1"),
            ok("JSON & data formats", "Kotlinx Serialization", "Kotlin JSON serialization runtime", "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.2"),
            ok("JSON & data formats", "JSON-java (org.json)", "JSON reference implementation", "org.json:json:20231013"),
            ok("JSON & data formats", "Fastjson2", "Fast JSON library", "com.alibaba.fastjson2:fastjson2:2.0.45"),

            // ---------------------------------------------------------------- Images & camera
            proc("Images & camera", "Glide", "Image loading and caching", "Basic Glide.with(..) works; the generated GlideApp API needs an annotation processor.",
                    "com.github.bumptech.glide:glide:4.16.0"),
            ok("Images & camera", "Picasso", "Image loading", "com.squareup.picasso:picasso:2.71828"),
            ok("Images & camera", "Coil", "Kotlin image loading", "io.coil-kt:coil:2.5.0"),
            ok("Images & camera", "Fresco", "Image pipeline by Facebook", "com.facebook.fresco:fresco:3.1.3"),
            ok("Images & camera", "uCrop", "Image cropping (JitPack)", "com.github.yalantis:ucrop:2.2.8"),
            ok("Images & camera", "Compressor", "Image compression (JitPack)", "com.github.zetbaitsu:Compressor:3.0.1"),
            ok("Images & camera", "Android Image Cropper", "Crop images", "com.vanniktech:android-image-cropper:+"),
            ok("Images & camera", "ExifInterface", "Read/write image EXIF data", "androidx.exifinterface:exifinterface:1.3.7"),
            ok("Images & camera", "CameraX", "Camera API (core, camera2, lifecycle, view)", "androidx.camera:camera-core:1.3.1", "androidx.camera:camera-camera2:1.3.1",
                    "androidx.camera:camera-lifecycle:1.3.1", "androidx.camera:camera-view:1.3.1"),
            ok("Images & camera", "ZXing", "QR / barcode encode & decode", "com.google.zxing:core:3.5.2"),
            ok("Images & camera", "ZXing Android Embedded", "Ready-made barcode scanner screen", "com.journeyapps:zxing-android-embedded:4.3.0", "com.google.zxing:core:3.5.2"),
            ok("Images & camera", "ML Kit Barcode Scanning", "Google's on-device barcode scanner", "com.google.mlkit:barcode-scanning:17.2.0"),
            ok("Images & camera", "Lottie (animations)", "Vector animations", "com.airbnb.android:lottie:6.3.0"),

            // ---------------------------------------------------------------- Database
            proc("Database & storage", "Room", "SQLite object mapping", "Room needs its annotation processor (room-compiler) to generate DAOs: not available in Buildora yet.",
                    "androidx.room:room-runtime:2.6.1"),
            tool("Database & storage", "SQLite", "Built into Android", "SQLite is part of Android: use android.database.sqlite.SQLiteOpenHelper. Nothing to add. (androidx.sqlite is a small wrapper if you want it.)"),
            ok("Database & storage", "AndroidX SQLite", "Support library wrapper around SQLite", "androidx.sqlite:sqlite:2.4.0"),
            ok("Database & storage", "SQLCipher", "Encrypted SQLite", "net.zetetic:sqlcipher-android:4.5.6", "androidx.sqlite:sqlite:2.4.0"),
            nope("Database & storage", "Realm Java", "Object database", "Realm needs its Gradle plugin (bytecode transformer), which Buildora cannot run."),
            proc("Database & storage", "ObjectBox", "Fast object database", "Entities need ObjectBox's Gradle plugin / processor to generate code.", "io.objectbox:objectbox-android:+"),
            ok("Database & storage", "MMKV", "Fast key-value storage (Tencent)", "com.tencent:mmkv:1.3.2"),
            ok("Database & storage", "DataStore (Preferences)", "Modern SharedPreferences replacement (RxJava3 API for Java)", "androidx.datastore:datastore-preferences-rxjava3:1.0.0",
                    "io.reactivex.rxjava3:rxjava:3.1.8"),
            ok("Database & storage", "Hawk", "Simple secure key-value storage", "com.orhanobut:hawk:2.0.1"),
            ok("Database & storage", "Paper", "Fast NoSQL-like storage", "io.paperdb:paperdb:2.7.2"),
            ok("Database & storage", "LitePal", "SQLite ORM", "org.litepal.guolindev:core:3.2.3"),
            ok("Database & storage", "GreenDAO", "ORM runtime (generated DAOs need the generator plugin)", "org.greenrobot:greendao:3.3.0"),
            ok("Database & storage", "ORMLite", "Lightweight ORM", "com.j256.ormlite:ormlite-android:6.1", "com.j256.ormlite:ormlite-core:6.1"),
            ok("Database & storage", "Zip4j", "Zip files with password support", "net.lingala.zip4j:zip4j:2.11.5"),
            ok("Database & storage", "Apache Commons Compress", "zip / tar / 7z / gzip", "org.apache.commons:commons-compress:1.25.0"),

            // ---------------------------------------------------------------- Firebase & backends
            ok("Firebase & backends", "Firebase Authentication", "Sign-in. Needs google-services.json in your app folder", "com.google.firebase:firebase-auth:22.3.1"),
            ok("Firebase & backends", "Firebase Realtime Database", "Realtime JSON database", "com.google.firebase:firebase-database:20.3.0"),
            ok("Firebase & backends", "Firebase Firestore", "Cloud Firestore", "com.google.firebase:firebase-firestore:24.10.1"),
            ok("Firebase & backends", "Firebase Storage", "Cloud file storage", "com.google.firebase:firebase-storage:20.3.0"),
            ok("Firebase & backends", "Firebase Remote Config", "Remote configuration", "com.google.firebase:firebase-config:21.6.1"),
            ok("Firebase & backends", "Firebase Analytics", "Google Analytics for Firebase", "com.google.firebase:firebase-analytics:21.5.1"),
            ok("Firebase & backends", "Firebase Crashlytics", "Crash reports (mapping upload needs the Crashlytics Gradle plugin)", "com.google.firebase:firebase-crashlytics:18.6.1"),
            ok("Firebase & backends", "Firebase Cloud Messaging", "Push notifications", "com.google.firebase:firebase-messaging:23.4.1"),
            ok("Firebase & backends", "Firebase App Check", "App attestation (Play Integrity)", "com.google.firebase:firebase-appcheck-playintegrity:+"),
            ok("Firebase & backends", "Supabase (postgrest-kt)", "supabase-kt: Kotlin client, uses Ktor", "io.github.jan-tennert.supabase:postgrest-kt:+", "io.ktor:ktor-client-android:2.3.7"),
            ok("Firebase & backends", "Appwrite Android SDK", "Appwrite backend client", "io.appwrite:sdk-for-android:+"),
            manual("Firebase & backends", "PocketBase Java SDK", "PocketBase client", "There is no official Maven artifact. Use OkHttp/Retrofit against PocketBase's REST API, or import a .jar with 'Import .jar / .aar'."),
            ok("Firebase & backends", "MongoDB Java Driver", "MongoDB driver (JVM)", "org.mongodb:mongodb-driver-sync:4.11.1"),

            // ---------------------------------------------------------------- Play services
            ok("Google Play services", "Play Services Base", "Core Google Play services", "com.google.android.gms:play-services-base:18.3.0"),
            ok("Google Play services", "Play Services Auth (Google Sign-In)", "Google sign-in", "com.google.android.gms:play-services-auth:20.7.0"),
            ok("Google Play services", "Play Services Location", "Fused location provider", "com.google.android.gms:play-services-location:21.1.0"),
            ok("Google Play services", "Play Services Maps", "Google Maps (needs an API key)", "com.google.android.gms:play-services-maps:18.2.0"),
            ok("Google Play services", "Play Services Ads", "AdMob", "com.google.android.gms:play-services-ads:22.6.0"),

            // ---------------------------------------------------------------- Async & architecture
            ok("Async & architecture", "RxJava", "Reactive streams", "io.reactivex.rxjava3:rxjava:3.1.8"),
            ok("Async & architecture", "RxAndroid", "Android main-thread scheduler", "io.reactivex.rxjava3:rxandroid:3.0.2", "io.reactivex.rxjava3:rxjava:3.1.8"),
            ok("Async & architecture", "Kotlin Coroutines", "Coroutines for Android", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"),
            ok("Async & architecture", "LiveData", "Lifecycle-aware observable data", "androidx.lifecycle:lifecycle-livedata:2.7.0"),
            ok("Async & architecture", "Android Lifecycle", "Lifecycle + ViewModel", "androidx.lifecycle:lifecycle-runtime:2.7.0", "androidx.lifecycle:lifecycle-viewmodel:2.7.0"),
            ok("Async & architecture", "WorkManager", "Background work", "androidx.work:work-runtime:2.9.0"),
            proc("Async & architecture", "Hilt", "Dependency injection (Dagger)", "Hilt needs a Gradle plugin and annotation processors: not available in Buildora yet.", "com.google.dagger:hilt-android:2.50"),
            proc("Async & architecture", "Dagger", "Dependency injection", "Dagger generates code with an annotation processor: not available in Buildora yet.", "com.google.dagger:dagger:2.50"),
            ok("Async & architecture", "Koin", "Lightweight dependency injection (no code generation)", "io.insert-koin:koin-android:3.5.3"),
            ok("Async & architecture", "EventBus", "Publish/subscribe events", "org.greenrobot:eventbus:3.3.1"),

            // ---------------------------------------------------------------- Media
            ok("Media", "Media3 ExoPlayer", "Audio / video player", "androidx.media3:media3-exoplayer:1.2.1", "androidx.media3:media3-ui:1.2.1", "androidx.media3:media3-common:1.2.1"),
            ok("Media", "Media3 Session", "Media session / background playback", "androidx.media3:media3-session:1.2.1"),

            // ---------------------------------------------------------------- Logging & debugging
            ok("Logging & debugging", "Timber", "Logging", "com.jakewharton.timber:timber:5.0.1"),
            ok("Logging & debugging", "Logger", "Pretty logging", "com.orhanobut:logger:2.2.0"),
            ok("Logging & debugging", "LeakCanary", "Memory leak detection (debug builds)", "com.squareup.leakcanary:leakcanary-android:2.13"),
            ok("Logging & debugging", "Chucker", "HTTP inspector for OkHttp", "com.github.chuckerteam.chucker:library:4.0.0"),
            ok("Logging & debugging", "Stetho", "Chrome DevTools bridge", "com.facebook.stetho:stetho:1.6.0"),

            // ---------------------------------------------------------------- Testing
            ok("Testing", "JUnit", "Unit tests (Buildora does not run tests itself)", "junit:junit:4.13.2"),
            ok("Testing", "Mockito", "Mocking (Android flavour)", "org.mockito:mockito-android:5.8.0"),
            ok("Testing", "MockK", "Kotlin mocking (Android)", "io.mockk:mockk-android:1.13.9"),
            ok("Testing", "Espresso", "UI tests", "androidx.test.espresso:espresso-core:3.5.1"),
            ok("Testing", "Robolectric", "Android unit tests on the JVM", "org.robolectric:robolectric:4.11.1"),

            // ---------------------------------------------------------------- Utilities & tools
            ok("Utilities & tools", "Kotlin stdlib", "Kotlin runtime (for Kotlin libraries)", "org.jetbrains.kotlin:kotlin-stdlib:1.9.22"),
            ok("Utilities & tools", "Guava (Android)", "Google core libraries", "com.google.guava:guava:33.0.0-android"),
            ok("Utilities & tools", "Commons IO", "File and stream helpers", "commons-io:commons-io:2.15.1"),
            ok("Utilities & tools", "Commons Lang3", "String / Object helpers", "org.apache.commons:commons-lang3:3.14.0"),
            ok("Utilities & tools", "Commons Codec", "Base64, hex, digests", "commons-codec:commons-codec:1.16.0"),
            ok("Utilities & tools", "Bouncy Castle", "Cryptography provider + PKIX", "org.bouncycastle:bcprov-jdk18on:1.77", "org.bouncycastle:bcpkix-jdk18on:1.77"),
            ok("Utilities & tools", "Dexlib2", "Read and write .dex files (smali project)", "org.smali:dexlib2:2.5.2"),
            ok("Utilities & tools", "smali / baksmali", "Assembler and disassembler for Dalvik bytecode", "org.smali:smali:2.5.2", "org.smali:baksmali:2.5.2"),
            ok("Utilities & tools", "R8", "Google's shrinker/dexer as a library", "Buildora already uses R8 for release builds; add it only if you build tools.", new String[]{"com.android.tools:r8:8.2.42"}),
            ok("Utilities & tools", "ProGuard (base)", "ProGuard as a library (build tools)", "com.guardsquare:proguard-base:7.4.1"),
            tool("Utilities & tools", "Android Asset Packaging Tool 2 (aapt2)", "Resource compiler", "aapt2 is a native program that is bundled in Buildora and runs your builds. It cannot be linked into an app."),
            tool("Utilities & tools", "Android SDK Build Tools", "SDK build tools", "Buildora has its own on-device equivalents: aapt2, D8/R8, apksig and a zipalign check. Nothing to add."),
    };

    public static Lib[] inGroup(String group) {
        int n = 0;
        for (Lib l : ALL) if (l.group.equals(group)) n++;
        Lib[] out = new Lib[n];
        int i = 0;
        for (Lib l : ALL) if (l.group.equals(group)) out[i++] = l;
        return out;
    }

    public static String statusLabel(int s) {
        switch (s) {
            case PROCESSOR: return "needs annotation processor";
            case TOOL: return "tool / built in";
            case MANUAL: return "import manually";
            case UNSUPPORTED: return "not supported";
            default: return "";
        }
    }
}
