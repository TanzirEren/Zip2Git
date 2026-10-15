package com.tenix.buildora.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.TimeZone;

/** Generates PKCS12 keystores with a self-signed RSA certificate - pure JDK, no BouncyCastle. */
public final class KeyTool {
    private KeyTool() {}

    public static final class Info {
        public String alias, owner, issuer, sha1, sha256, validFrom, validTo, type;
    }

    public static void generate(File out, String alias, char[] storePass, char[] keyPass,
                                String cn, String ou, String o, String l, String st, String c,
                                int validityYears, int keySize) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(keySize, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        byte[] name = name(cn, ou, o, l, st, c);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.DAY_OF_YEAR, -1);
        Date nb = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.add(Calendar.YEAR, validityYears);
        Date na = cal.getTime();

        byte[] sigAlg = seq(oid("1.2.840.113549.1.1.11"), new byte[]{0x05, 0x00});
        byte[] serial = new byte[16];
        new SecureRandom().nextBytes(serial);
        serial[0] &= 0x7F;
        byte[] tbs = seq(
                tlv(0xA0, integer(BigInteger.valueOf(2))),
                integer(new BigInteger(1, serial)),
                sigAlg,
                name,
                seq(time(nb), time(na)),
                name,
                kp.getPublic().getEncoded());
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbs);
        byte[] cert = seq(tbs, sigAlg, tlv(0x03, concat(new byte[]{0}, sig.sign())));

        X509Certificate x509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(cert));
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, kp.getPrivate(), keyPass, new Certificate[]{x509});
        try (FileOutputStream fos = new FileOutputStream(out)) {
            ks.store(fos, storePass);
        }
    }

    public static KeyStore load(File f, char[] pass, String[] typeOut) throws Exception {
        Exception last = null;
        for (String t : new String[]{"PKCS12", "BKS", "JKS"}) {
            try (FileInputStream in = new FileInputStream(f)) {
                KeyStore ks = KeyStore.getInstance(t);
                ks.load(in, pass);
                if (typeOut != null) typeOut[0] = t;
                return ks;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new Exception("Unsupported keystore") : last;
    }

    public static Info describe(File f, char[] storePass, String alias) throws Exception {
        String[] type = new String[1];
        KeyStore ks = load(f, storePass, type);
        if (alias == null || alias.isEmpty() || !ks.containsAlias(alias)) {
            Enumeration<String> en = ks.aliases();
            alias = en.hasMoreElements() ? en.nextElement() : null;
        }
        if (alias == null) throw new Exception("Keystore has no entries");
        X509Certificate c = (X509Certificate) ks.getCertificate(alias);
        Info i = new Info();
        i.alias = alias;
        i.type = type[0];
        i.owner = c.getSubjectX500Principal().getName();
        i.issuer = c.getIssuerX500Principal().getName();
        i.validFrom = new SimpleDateFormat("yyyy-MM-dd").format(c.getNotBefore());
        i.validTo = new SimpleDateFormat("yyyy-MM-dd").format(c.getNotAfter());
        i.sha1 = fingerprint(c.getEncoded(), "SHA-1");
        i.sha256 = fingerprint(c.getEncoded(), "SHA-256");
        return i;
    }

    public static PrivateKey privateKey(KeyStore ks, String alias, char[] keyPass) throws Exception {
        return (PrivateKey) ks.getKey(alias, keyPass);
    }

    private static String fingerprint(byte[] der, String alg) throws Exception {
        byte[] d = java.security.MessageDigest.getInstance(alg).digest(der);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", d[i]));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ tiny DER encoder

    private static byte[] len(int n) {
        if (n < 128) return new byte[]{(byte) n};
        if (n < 256) return new byte[]{(byte) 0x81, (byte) n};
        if (n < 65536) return new byte[]{(byte) 0x82, (byte) (n >> 8), (byte) n};
        return new byte[]{(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) b.write(p, 0, p.length);
        return b.toByteArray();
    }

    private static byte[] tlv(int tag, byte[]... content) {
        byte[] body = concat(content);
        return concat(new byte[]{(byte) tag}, len(body.length), body);
    }

    private static byte[] seq(byte[]... p) { return tlv(0x30, p); }
    private static byte[] set(byte[]... p) { return tlv(0x31, p); }
    private static byte[] integer(BigInteger v) { return tlv(0x02, v.toByteArray()); }

    private static byte[] oid(String dotted) {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long v = Long.parseLong(parts[i]);
            byte[] tmp = new byte[10];
            int n = 0;
            tmp[n++] = (byte) (v & 0x7F);
            v >>= 7;
            while (v > 0) { tmp[n++] = (byte) ((v & 0x7F) | 0x80); v >>= 7; }
            for (int k = n - 1; k >= 0; k--) b.write(tmp[k]);
        }
        return tlv(0x06, b.toByteArray());
    }

    private static byte[] time(Date d) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTime(d);
        boolean gen = cal.get(Calendar.YEAR) >= 2050;
        SimpleDateFormat f = new SimpleDateFormat(gen ? "yyyyMMddHHmmss'Z'" : "yyMMddHHmmss'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(gen ? 0x18 : 0x17, f.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] rdn(String oid, String value, boolean printable) {
        byte[] v = tlv(printable ? 0x13 : 0x0C, value.getBytes(StandardCharsets.UTF_8));
        return set(seq(oid(oid), v));
    }

    private static byte[] name(String cn, String ou, String o, String l, String st, String c) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        add(b, "2.5.4.6", c, true);
        add(b, "2.5.4.8", st, false);
        add(b, "2.5.4.7", l, false);
        add(b, "2.5.4.10", o, false);
        add(b, "2.5.4.11", ou, false);
        add(b, "2.5.4.3", cn, false);
        byte[] body = b.toByteArray();
        if (body.length == 0) body = rdn("2.5.4.3", "Buildora", false);
        return concat(new byte[]{0x30}, len(body.length), body);
    }

    private static void add(ByteArrayOutputStream b, String oid, String value, boolean printable) {
        if (value == null || value.trim().isEmpty()) return;
        byte[] r = rdn(oid, value.trim(), printable);
        b.write(r, 0, r.length);
    }
}
