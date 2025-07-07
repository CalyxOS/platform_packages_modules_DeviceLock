/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.devicelockcontroller.util;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;

public final class KeyAttestationUtil {

    public static final String KEY_ALIAS = "DLCKeyAttestation";
    public static final String EC_CURVE = "secp256r1";
    public static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    public static byte[] getKeyAttestationLeafCertificate()
            throws NoSuchAlgorithmException, NoSuchProviderException, CertificateException,
            IOException, KeyStoreException, InvalidAlgorithmParameterException {
        // Create KeyPairGenerator and set generation parameters for an ECDSA key pair
        // using the NIST P-256 curve.
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE);

        // Delete any previous entry
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        keyStore.deleteEntry(KEY_ALIAS);

        keyPairGenerator.initialize(
                new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec(EC_CURVE))
                        .setDigests(KeyProperties.DIGEST_SHA256,
                                KeyProperties.DIGEST_SHA384,
                                KeyProperties.DIGEST_SHA512)
                        .setAttestationChallenge(new byte[0])
                        .build());
        // Generate the key pair. This will result in calls to both generate_key() and
        // attest_key() at the keymaster2 HAL.
        keyPairGenerator.generateKeyPair();

        // Get the Keymint certificate / leaf certificate
        Certificate[] certificateChain = keyStore.getCertificateChain(KEY_ALIAS);
        return certificateChain == null || certificateChain.length < 1 ? null
                : certificateChain[0].getEncoded();
    }
}
