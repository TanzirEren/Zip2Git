package com.tenix.buildora.build;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Signer {
    private Signer() {}

    /** Key material for signing. */
    public static final class Key {
        public File file;
        public String alias, storePass, keyPass, name;
    }

    private static ApkSigner.SignerConfig config(Key k) throws Exception {
        KeyStore ks = KeyTool.load(k.file, k.storePass.toCharArray(), null);
        PrivateKey pk = KeyTool.privateKey(ks, k.alias, k.keyPass.toCharArray());
        if (pk == null) throw new Exception("Key alias '" + k.alias + "' not found in " + k.name);
        Certificate[] chain = ks.getCertificateChain(k.alias);
        List<X509Certificate> certs = new ArrayList<>();
        for (Certificate cert : chain) certs.add((X509Certificate) cert);
        return new ApkSigner.SignerConfig.Builder("CERT", pk, certs).build();
    }

    /** Signs an APK with v1 (only when minSdk < 24), v2 and v3. */
    public static String sign(Key k, File unsigned, File out, int minSdk) throws Exception {
        if (out.exists()) out.delete();
        new ApkSigner.Builder(Collections.singletonList(config(k)))
                .setInputApk(unsigned)
                .setOutputApk(out)
                .setMinSdkVersion(Math.max(1, minSdk))
                .setV1SigningEnabled(minSdk < 24)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign();
        return k.name;
    }

    /** Signs an Android App Bundle: JAR (v1) signature only, as jarsigner would. */
    public static void signBundle(Key k, File unsigned, File out) throws Exception {
        if (out.exists()) out.delete();
        new ApkSigner.Builder(Collections.singletonList(config(k)))
                .setInputApk(unsigned)
                .setOutputApk(out)
                .setMinSdkVersion(21)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(false)
                .setV3SigningEnabled(false)
                .build()
                .sign();
    }

    /** Verifies the signature of a signed APK; returns e.g. "v2 v3" or throws with the problems. */
    public static String verify(File apk, int minSdk) throws Exception {
        ApkVerifier.Result r = new ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(Math.max(1, minSdk)).build().verify();
        if (!r.isVerified()) {
            StringBuilder sb = new StringBuilder("Signature verification failed:");
            for (Object e : r.getErrors()) sb.append("\n  ").append(e);
            throw new Exception(sb.toString());
        }
        StringBuilder s = new StringBuilder();
        if (r.isVerifiedUsingV1Scheme()) s.append("v1 ");
        if (r.isVerifiedUsingV2Scheme()) s.append("v2 ");
        if (r.isVerifiedUsingV3Scheme()) s.append("v3 ");
        return s.toString().trim();
    }
}
