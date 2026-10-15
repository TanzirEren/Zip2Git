package com.tenix.buildora.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A product flavor (for example "free" / "pro"). */
public class FlavorCfg {
    public String name;
    public String applicationId = "";   // "" = keep the project's id
    public String idSuffix = "";
    public String versionSuffix = "";
    public int versionCode = 0;         // 0 = keep
    public int minSdk = 0;              // 0 = keep
    public List<String> dependencies = new ArrayList<>();
    public List<String> buildConfigFields = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("applicationId", applicationId);
        o.put("idSuffix", idSuffix);
        o.put("versionSuffix", versionSuffix);
        o.put("versionCode", versionCode);
        o.put("minSdk", minSdk);
        JSONArray d = new JSONArray();
        for (String s : dependencies) d.put(s);
        o.put("dependencies", d);
        JSONArray f = new JSONArray();
        for (String s : buildConfigFields) f.put(s);
        o.put("buildConfigFields", f);
        return o;
    }

    public static FlavorCfg fromJson(JSONObject o) {
        FlavorCfg f = new FlavorCfg();
        f.name = o.optString("name", "flavor");
        f.applicationId = o.optString("applicationId", "");
        f.idSuffix = o.optString("idSuffix", "");
        f.versionSuffix = o.optString("versionSuffix", "");
        f.versionCode = o.optInt("versionCode", 0);
        f.minSdk = o.optInt("minSdk", 0);
        JSONArray d = o.optJSONArray("dependencies");
        if (d != null) for (int i = 0; i < d.length(); i++) f.dependencies.add(d.optString(i));
        JSONArray b = o.optJSONArray("buildConfigFields");
        if (b != null) for (int i = 0; i < b.length(); i++) f.buildConfigFields.add(b.optString(i));
        return f;
    }
}
