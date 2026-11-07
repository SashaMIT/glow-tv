package com.tv98.glow;

import android.util.Base64;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * Glow's nightly data, checked before use. The job that builds it signs each file (.github/workflows/guide.yml); this key
 * can only sign data, never an app. A file that doesn't match is refused and the box keeps what it has.
 */
final class Signed {
    private static final String KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWsplZQwL2Zga+2EJfjmw2iS0Q+eH8VLetL+ZwwZkg96xurGwXWzI61xRbFl8vfl/pJHGjOkrE5jhXNDsyQeTcw==";

    private Signed() {}

    /** The file, or an error if its signature is missing or doesn't match. */
    static byte[] fetch(String url, int limit) throws IOException {
        byte[] body = Net.bytes(url, limit), sig = Net.bytes(url + ".sig", 4096);
        if (!verify(body, sig, Base64.decode(KEY, Base64.DEFAULT))) {
            Feeds.report(url, "signature doesn't match", System.currentTimeMillis());
            throw new IOException("signature doesn't match: " + url);
        }
        return body;
    }

    static boolean verify(byte[] body, byte[] sig, byte[] publicKeyDer) {
        try {
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicKeyDer));
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(key);
            s.update(body);
            return s.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    /** Only for the tests. */
    static String key() {
        return KEY;
    }
}
