package com.tenix.buildora.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A resolved build variant: flavor + build type merged with the project defaults. */
public class Variant {
    public String name;            // "debug", "release", "freeRelease"
    public String buildType;
    public String flavor = "";
    public String applicationId;
    public String versionName;
    public int versionCode, minSdk, targetSdk;
    public boolean debuggable, minify, obfuscate, optimize;
    public String signingKey = "";
    public final List<String> buildConfigFields = new ArrayList<>();
    public final List<String> extraDependencies = new ArrayList<>();
    public File flavorDir, typeDir;      // optional extra source sets (src/<flavor>, src/<buildType>)
    private boolean legacyName;          // single-flavor projects keep "<Project>.apk" for debug

    public static Variant resolve(Project p, String buildTypeName, String flavorName) {
        BuildTypeCfg bt = p.findBuildType(buildTypeName);
        if (bt == null) bt = p.buildTypes.isEmpty() ? BuildTypeCfg.debug() : p.buildTypes.get(0);
        FlavorCfg fl = (flavorName == null || flavorName.isEmpty()) ? null : p.findFlavor(flavorName);

        Variant v = new Variant();
        v.buildType = bt.name;
        v.flavor = fl == null ? "" : fl.name;
        v.name = fl == null ? bt.name : fl.name + Character.toUpperCase(bt.name.charAt(0)) + bt.name.substring(1);
        String baseId = fl != null && !fl.applicationId.isEmpty() ? fl.applicationId : p.packageName;
        v.applicationId = baseId + (fl == null ? "" : fl.idSuffix) + bt.idSuffix;
        v.versionName = p.versionName + (fl == null ? "" : fl.versionSuffix) + bt.versionSuffix;
        v.versionCode = fl != null && fl.versionCode > 0 ? fl.versionCode : p.versionCode;
        v.minSdk = Math.max(1, fl != null && fl.minSdk > 0 ? fl.minSdk : p.minSdk);
        v.targetSdk = Math.max(v.minSdk, p.targetSdk);
        v.debuggable = bt.debuggable;
        v.minify = bt.minify;
        v.obfuscate = bt.obfuscate;
        v.optimize = bt.optimize;
        v.signingKey = bt.signingKey == null ? "" : bt.signingKey;
        if (fl != null) {
            v.buildConfigFields.addAll(fl.buildConfigFields);
            v.extraDependencies.addAll(fl.dependencies);
        }
        v.buildConfigFields.addAll(bt.buildConfigFields);
        File src = p.srcRoot;
        if (src != null && src.getName().equals("main") && src.getParentFile() != null) {
            File parent = src.getParentFile();
            if (fl != null) v.flavorDir = new File(parent, fl.name);
            v.typeDir = new File(parent, bt.name);
        }
        v.legacyName = p.flavors.isEmpty() && bt.name.equals("debug");
        return v;
    }

    public File apkFile(Project p) {
        return new File(p.root, legacyName ? p.name + ".apk" : p.name + "-" + name + ".apk");
    }

    public File aabFile(Project p) {
        return new File(p.root, p.name + "-" + name + ".aab");
    }

    public File outputsDir(Project p) {
        return new File(p.root, "outputs/" + name);
    }
}
