package com.tenix.buildora.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Thin wrapper around SharedPreferences with all setting keys in one place. */
public final class Prefs {
    private Prefs() {}

    public static final String THEME = "theme";                 // 0 system, 1 light, 2 dark
    public static final String SCHEME = "editor_scheme";        // index into EditorScheme.ALL
    public static final String FONT_SIZE = "font_size";         // sp, float
    public static final String FONT_FAMILY = "font_family";     // 0 mono, 1 sans-mono, 2 serif-mono, 3 custom
    public static final String FONT_PATH = "font_path";
    public static final String TAB_SIZE = "tab_size";
    public static final String USE_SPACES = "use_spaces";
    public static final String WORD_WRAP = "word_wrap";
    public static final String LINE_NUMBERS = "line_numbers";
    public static final String HL_LINE = "highlight_line";
    public static final String AUTO_INDENT = "auto_indent";
    public static final String AUTO_CLOSE = "auto_close";
    public static final String AUTO_SAVE = "auto_save";
    public static final String SYMBOL_BAR = "symbol_bar";
    public static final String SHOW_HIDDEN = "show_hidden";
    public static final String CONFIRM_DELETE = "confirm_delete";
    public static final String LIVE_LINT = "live_lint";

    public static final String DEBUGGABLE = "build_debuggable";
    public static final String CLEAN_BEFORE = "build_clean_before";
    public static final String KEEP_AWAKE = "build_keep_awake";
    public static final String AUTO_INSTALL = "build_auto_install";
    public static final String AUTO_VERSION = "build_auto_version";
    public static final String DEX_RELEASE = "build_dex_release";
    public static final String VERBOSE_LOG = "build_verbose";
    public static final String INCREMENTAL = "build_incremental";
    public static final String VERIFY_SIG = "build_verify_sig";
    public static final String ALIGN_16K = "build_align_16k";
    public static final String NOTIFY = "build_notify";
    public static final String KEYSTORE_ID = "keystore_active";
    public static final String KEYSTORES = "keystores_json";

    public static final String PKG_PREFIX = "default_pkg_prefix";
    public static final String DEF_MIN_SDK = "default_min_sdk";
    public static final String DEF_TARGET_SDK = "default_target_sdk";
    public static final String DEF_RADIUS = "default_icon_radius";

    public static final String GIT_NAME = "git_name";
    public static final String GIT_EMAIL = "git_email";
    public static final String GIT_USER = "git_user";
    public static final String GIT_TOKEN = "git_token";

    public static final String AAPT2_CUSTOM = "aapt2_custom";
    public static final String JAR_CUSTOM = "androidjar_custom";
    public static final String TOOLCHAIN_VER = "toolchain_version";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("buildora", Context.MODE_PRIVATE);
    }

    public static String str(Context c, String k, String d) { return sp(c).getString(k, d); }
    public static int num(Context c, String k, int d) { return sp(c).getInt(k, d); }
    public static float flt(Context c, String k, float d) { return sp(c).getFloat(k, d); }
    public static boolean bool(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }

    public static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }
    public static void put(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    public static void put(Context c, String k, float v) { sp(c).edit().putFloat(k, v).apply(); }
    public static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
}
