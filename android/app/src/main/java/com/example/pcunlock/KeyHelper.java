package com.example.pcunlock;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

public class KeyHelper {
    static final String ALIAS = "pc_unlock_key";

    /** Creates a fingerprint-bound key. Returns the public key (X.509 DER). */
    public static byte[] createKey() throws Exception {
        deleteKey();  // start clean
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        kpg.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)        // fingerprint for every signature
                .setInvalidatedByBiometricEnrollment(true)  // new fingerprint added -> key dies
                .build());
        return kpg.generateKeyPair().getPublic().getEncoded();
    }

    /** Signature object ready for BiometricPrompt.CryptoObject. */
    public static Signature initSignature() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        PrivateKey key = (PrivateKey) ks.getKey(ALIAS, null);
        if (key == null) throw new IllegalStateException("No key. Pair first.");
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(key);  // throws KeyPermanentlyInvalidatedException if fingerprints changed
        return s;
    }

    public static void deleteKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(ALIAS);
        } catch (Exception ignored) {
        }
    }
}