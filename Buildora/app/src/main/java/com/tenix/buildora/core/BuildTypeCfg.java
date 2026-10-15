package com.tenix.buildora.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A build type such as debug or release. */
public class BuildTypeCfg {
    public String name;
    public boolean debuggable;
    public boolean minify;          // R8 code shrinking
    public boolean obfuscate = true;
    public boolean optimize = true;
    public String idSuffix = "";
    public String versionSuffix = "";
    /** "" = key chosen in Settings, "debug" = Buildora debug key, otherwise a keystore id. */
    public String signingKey = "";
    /** Lines like: String API_URL = "https://example.com"   or   boolean FEATURE_X = true */
    public List<String> buildConfigFields = new ArrayList<>();

    public BuildTypeCfg() { }

    public BuildTypeCfg(String name, boolean debuggable, boolean minify, String signingKey) {
        this.name = name;
        this.debuggable = debuggable;
        this.minify = minify;
        this.signingKey = signingKey;
    }

    public static BuildTypeCfg debug() { return new BuildTypeCfg("debug", true, false, "debug"); }
    public static BuildTypeCfg release() { return new BuildTypeCfg("release", false, false, ""); }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("debuggable", debuggable);
        o.put("minify", minify);
        o.put("obfuscate", obfuscate);
        o.put("optimize", optimize);
        o.put("idSuffix", idSuffix);
        o.put("versionSuffix", versionSuffix);
        o.put("signingKey", signingKey);
        JSONArray a = new JSONArray();
        for (String s : buildConfigFields) a.put(s);
        o.put("buildConfigFields", a);
        return o;
    }

    public static BuildTypeCfg fromJson(JSONObject o) {
        BuildTypeCfg b = new BuildTypeCfg();
        b.name = o.optString("name", "debug");
        b.debuggable = o.optBoolean("debuggable", false);
        b.minify = o.optBoolean("minify", false);
        b.obfuscate = o.optBoolean("obfuscate", true);
        b.optimize = o.optBoolean("optimize", true);
        b.idSuffix = o.optString("idSuffix", "");
        b.versionSuffix = o.optString("versionSuffix", "");
        b.signingKey = o.optString("signingKey", "");
        JSONArray a = o.optJSONArray("buildConfigFields");
        if (a != null) for (int i = 0; i < a.length(); i++) b.buildConfigFields.add(a.optString(i));
        return b;
    }
}
