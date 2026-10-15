package com.tenix.buildora.build;

import android.content.Context;

import com.tenix.buildora.core.Prefs;

import java.io.File;
import java.io.IOException;

/** BuildEnv backed by the app's files directory, toolchain and settings. */
public final class AndroidBuildEnv implements BuildEnv {
    private final Context ctx;

    public AndroidBuildEnv(Context c) { this.ctx = c.getApplicationContext(); }

    @Override public File aapt2() { return Toolchain.aapt2(ctx); }
    @Override public File androidJar() { return Toolchain.androidJar(ctx); }
    @Override public File lambdaStubs() { return Toolchain.lambdaStubs(ctx); }
    @Override public File m2Dir() { return new File(ctx.getFilesDir(), "m2"); }
    @Override public File dexCacheDir() { return new File(ctx.getFilesDir(), "dexcache"); }
    @Override public void prepare() throws IOException { Toolchain.ensure(ctx); }
    @Override public String aapt2Version() { return Toolchain.aapt2Version(ctx); }

    @Override public boolean incremental() { return Prefs.bool(ctx, Prefs.INCREMENTAL, true); }
    @Override public boolean cleanIntermediates() { return Prefs.bool(ctx, Prefs.CLEAN_BEFORE, true); }
    @Override public boolean autoVersion() { return Prefs.bool(ctx, Prefs.AUTO_VERSION, false); }
    @Override public boolean dexRelease() { return Prefs.bool(ctx, Prefs.DEX_RELEASE, false); }
    @Override public boolean verifySignature() { return Prefs.bool(ctx, Prefs.VERIFY_SIG, true); }
    @Override public int nativeAlign() { return Prefs.bool(ctx, Prefs.ALIGN_16K, true) ? 16384 : 4096; }

    @Override public Signer.Key signingKey(String keyId) throws Exception {
        Keystores.Entry e = null;
        if ("debug".equals(keyId)) {
            e = Keystores.debug(ctx);
        } else if (keyId != null && !keyId.isEmpty()) {
            for (Keystores.Entry x : Keystores.list(ctx)) if (x.id.equals(keyId) && x.path(ctx).isFile()) e = x;
        }
        if (e == null) e = Keystores.active(ctx);
        Signer.Key k = new Signer.Key();
        k.file = e.path(ctx);
        k.alias = e.alias;
        k.storePass = e.storePass;
        k.keyPass = e.keyPass;
        k.name = e.name;
        return k;
    }
}
