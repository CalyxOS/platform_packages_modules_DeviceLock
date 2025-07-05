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

package com.android.devicelockcontroller.shadows;

import android.security.keystore.KeyGenParameterSpec;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FakeAndroidKeystore extends KeyStoreSpi {
    public FakeAndroidKeystore() {
        // Stub method called when keystore provider instantiates object
    }

    @Override
    public Key engineGetKey(String alias, char[] password) {
        return SingletonKeystore.keys.get(alias);
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        if (SingletonKeystore.certs.containsKey(alias)) {
            System.out.println("Singlekeystore contains certs for alias: " + alias);
            Log.d("FakeAndroidKeystore", "Singlekeystore contains certs for alias: " + alias);
            return SingletonKeystore.certs.get(alias);
        } else {
            System.out.println("Singlekeystore DOES NOT contain certs for alias: " + alias);
            Log.d("FakeAndroidKeystore",
                    "Singlekeystore DOES NOT contain certs for alias: " + alias);
        }

        return new Certificate[]{};
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        return SingletonKeystore.certs.get(alias)[0];
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        return null;
    }

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
        SingletonKeystore.keys.put(alias, key);
        SingletonKeystore.keyCerts.put(alias, chain);
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) {
        try {
            PrivateKey keyEncoded =
                    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
            SingletonKeystore.keys.put(alias, keyEncoded);
            SingletonKeystore.keyCerts.put(alias, chain);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new AssertionError("Invalid arguments:", e);
        }
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        SingletonKeystore.certs.put(alias, new Certificate[]{cert});
    }

    @Override
    public void engineDeleteEntry(String alias) {
        // No-op
    }

    @Override
    public Enumeration<String> engineAliases() {
        Set<String> aliases = new HashSet<>();
        aliases.addAll(SingletonKeystore.keys.keySet());
        aliases.addAll(SingletonKeystore.certs.keySet());
        return Collections.enumeration(aliases);
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        return engineIsKeyEntry(alias) || engineIsCertificateEntry(alias);
    }

    @Override
    public int engineSize() {
        Set<String> aliases = new HashSet<>();
        aliases.addAll(SingletonKeystore.keys.keySet());
        aliases.addAll(SingletonKeystore.keyCerts.keySet());
        return aliases.size();
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        return SingletonKeystore.keys.get(alias) != null;
    }

    @Override
    public KeyStore.Entry engineGetEntry(String alias, KeyStore.ProtectionParameter protParam) {
        Key key = SingletonKeystore.keys.get(alias);
        if (key != null) {
            return new KeyStore.PrivateKeyEntry((PrivateKey) key,
                    SingletonKeystore.keyCerts.get(alias));
        }
        return null;
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return SingletonKeystore.certs.containsKey(alias);
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        for (Map.Entry<String, Certificate[]> entry : SingletonKeystore.certs.entrySet()) {
            for (Certificate certificate : entry.getValue()) {
                if (certificate.equals(cert)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public void engineStore(OutputStream stream, char[] password) {
        // No-op
    }

    @Override
    public void engineLoad(InputStream stream, char[] password) {
        // No-op
    }


    public static class FakeECKeyPairGenerator extends KeyPairGeneratorSpi {
        private KeyPairGenerator wrapped;
        private AlgorithmParameterSpec lastSpec;

        public FakeECKeyPairGenerator() {
            try {
                wrapped = KeyPairGenerator.getInstance("EC");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public KeyPair generateKeyPair() {
            KeyPair keyPair = wrapped.generateKeyPair();
            SingletonKeystore.keys.put(getKeystoreAlias(lastSpec),
                    keyPair.getPublic());

            return keyPair;
        }

        private String getKeystoreAlias(AlgorithmParameterSpec algorithmParameterSpec) {
            return ((KeyGenParameterSpec) algorithmParameterSpec).getKeystoreAlias();
        }

        @Override
        public void initialize(int keysize, SecureRandom random) {
            // No operation
        }

        @Override
        public void initialize(AlgorithmParameterSpec params, SecureRandom random) {
            lastSpec = params;
        }
    }

    public static final class SingletonKeystore {
        public static final HashMap<String, Key> keys = new HashMap<>();
        public static final HashMap<String, Certificate[]> keyCerts = new HashMap<>();
        public static final HashMap<String, Certificate[]> certs = new HashMap<>();

        private SingletonKeystore() {
        }
    }

    static public class FakeSecurityProvider extends Provider {

        private static final String PROVIDER_NAME = "AndroidKeyStore";

        public FakeSecurityProvider() {
            super(PROVIDER_NAME, 1.0D, "");
            put("KeyStore.AndroidKeyStore", FakeAndroidKeystore.class.getName());
            put("KeyPairGenerator.EC", FakeAndroidKeystore.FakeECKeyPairGenerator.class.getName());
        }
    }

}
