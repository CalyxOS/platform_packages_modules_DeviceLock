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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class TestCertificateProviderUtil {


    public static X509Certificate[] getTestCertificates() throws CertificateException {
        String cert1 = """
                -----BEGIN CERTIFICATE-----
                MIIHHTCCBgWgAwIBAgIGAZeTtz/VMA0GCSqGSIb3DQEBCwUAMFgxCzAJBgNVBAYT
                AlVTMRUwEwYDVQQLEwxBR1NBIFRlc3RpbmcxFzAVBgNVBAoTDkdvb2dsZSBUZXN0
                aW5nMRkwFwYDVQQDDBAqLmMuZ29vZ2xlcnMuY29tMB4XDTI1MDYyMTA2MTkwOVoX
                DTI1MDcyMTE4MTkwOVowWDELMAkGA1UEBhMCVVMxFTATBgNVBAsTDEFHU0EgVGVz
                dGluZzEXMBUGA1UEChMOR29vZ2xlIFRlc3RpbmcxGTAXBgNVBAMMECouYy5nb29n
                bGVycy5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC2JCKNR8G/
                AkwPXUfFTJTVfO6fl5TODlj1Rsv+P3WezjthUMDr0tYUI17QzR/OfNelGA/QTKS5
                a1u4FEkitra9wBRKsC8bBjLzj4Pqx5k+v9iXVxhzU6KUR2NxdxN2Jmtz5zZMW/1s
                2W9Fp07sZz93wpvIDQTn36ypcpA+wDRimzjgbCEv/5huoh3B6ACudyKmxbfpokMt
                4edQtj0j6UBO6q0vdMs7hdeLtDq2us0XHI+l3XGLuQUotW8vY7gQdzP6zl/uDXIp
                NvEF8SVEAg6aOaFr/aNM8gS4DMmUdF4dN2VPC9Jj1B461NF9gcWCQq48mULPxRe2
                NIwO6u2OmZLtAgMBAAGjggPrMIID5zCCATMGA1UdDgSCASoEggEmMIIBIjANBgkq
                hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtiQijUfBvwJMD11HxUyU1Xzun5eUzg5Y
                9UbL/j91ns47YVDA69LWFCNe0M0fznzXpRgP0EykuWtbuBRJIra2vcAUSrAvGwYy
                84+D6seZPr/Yl1cYc1OilEdjcXcTdiZrc+c2TFv9bNlvRadO7Gc/d8KbyA0E59+s
                qXKQPsA0Yps44GwhL/+YbqIdwegArncipsW36aJDLeHnULY9I+lATuqtL3TLO4XX
                i7Q6trrNFxyPpd1xi7kFKLVvL2O4EHcz+s5f7g1yKTbxBfElRAIOmjmha/2jTPIE
                uAzJlHReHTdlTwvSY9QeOtTRfYHFgkKuPJlCz8UXtjSMDurtjpmS7QIDAQABMIIB
                NwYDVR0jBIIBLjCCASqAggEmMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
                AQEAtiQijUfBvwJMD11HxUyU1Xzun5eUzg5Y9UbL/j91ns47YVDA69LWFCNe0M0f
                znzXpRgP0EykuWtbuBRJIra2vcAUSrAvGwYy84+D6seZPr/Yl1cYc1OilEdjcXcT
                diZrc+c2TFv9bNlvRadO7Gc/d8KbyA0E59+sqXKQPsA0Yps44GwhL/+YbqIdwegA
                rncipsW36aJDLeHnULY9I+lATuqtL3TLO4XXi7Q6trrNFxyPpd1xi7kFKLVvL2O4
                EHcz+s5f7g1yKTbxBfElRAIOmjmha/2jTPIEuAzJlHReHTdlTwvSY9QeOtTRfYHF
                gkKuPJlCz8UXtjSMDurtjpmS7QIDAQABMAwGA1UdEwQFMAMBAf8wggFjBgNVHREE
                ggFaMIIBVoIJbG9jYWxob3N0gg8qLmdvb2dsZS5jb20uaGuCFCouc2FuZGJveC5n
                b29nbGUuY29tghAqLmMuZ29vZ2xlcnMuY29tghYqLmdvb2dsZS1hbmFseXRpY3Mu
                Y29tghEqLnByb2QuZ29vZ2xlLmNvbYINKi5nc3RhdGljLmNvbYIZbG9jYWxob3N0
                LmNvcnAuZ29vZ2xlLmNvbYIUKi5jbGllbnRzLmdvb2dsZS5jb22HBAoAAgKCFiou
                Z29vZ2xlYWRzZXJ2aWNlcy5jb22CECouZ29vZ2xlYXBpcy5jb22CESouZG91Ymxl
                Y2xpY2submV0ghcqLmdvb2dsZXVzZXJjb250ZW50LmNvbYIVKi5jbGllbnRzNC5n
                b29nbGUuY29tggwqLmdvb2dsZS5jb22CBGcuY2+CGCouc2FuZGJveC5nb29nbGVh
                cGlzLmNvbYcEfwAAATANBgkqhkiG9w0BAQsFAAOCAQEAmmIzenkGgnAosUXFtVTi
                o0AZ7kNULZwmkgX2kfAIODzHahzw5yI1/6FOxklSz7e4CgDDiSlQoIeOeoC79TyD
                OdCkeYKWQG4i0qO1PVpYTxfQEoyhELmQ90b++CYYyLoSyTLpPM8GRPQfpp/Q7wv/
                X8VstTmeLnvgQvuZhXRnoVvb9VdNznU97Iz9WqhzFC1+y9GTYztxXnab/lNajPrB
                ovSEiKjNSePkS3DpmjaQ/M6nBB69GsCtzBh9k081GxAS8glM2D3ACYbsIs9qprqR
                ktt/hZBddomHnZBNRfsJjIFTJP1RkY+Bv60BMkkDStqb8dO9K3qJ809ilBEvniPO
                Cg==
                -----END CERTIFICATE-----
                """;

        String cert2 = """
                -----BEGIN CERTIFICATE-----
                MIIHHTCCBgWgAwIBAgIGAZeTvZIGMA0GCSqGSIb3DQEBCwUAMFgxCzAJBgNVBAYT
                AlVTMRUwEwYDVQQLEwxBR1NBIFRlc3RpbmcxFzAVBgNVBAoTDkdvb2dsZSBUZXN0
                aW5nMRkwFwYDVQQDDBAqLmMuZ29vZ2xlcnMuY29tMB4XDTI1MDYyMTA2MjYwM1oX
                DTI1MDcyMTE4MjYwM1owWDELMAkGA1UEBhMCVVMxFTATBgNVBAsTDEFHU0EgVGVz
                dGluZzEXMBUGA1UEChMOR29vZ2xlIFRlc3RpbmcxGTAXBgNVBAMMECouYy5nb29n
                bGVycy5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDldPJd4+/J
                tPv+GFcZ0+XW4y7lG1bhT+jnNYpnZt1tjIiJsuQW6V8RfOxquEHrfNsBBv+OUqh3
                YtRBsZWQMJgyGcZ2KKm5E4bRifVuMaIU9FCB380xsh/GECg3C12SQhlR3JCB6MVu
                2ny82T+gTeAZNPDwYBx/XozO5/9BsXenz+31MfunfUM2Ay9Y5lZei0regtqgglo9
                KFjPXdw2sixZQJa0CXHU+0bgeJ3rz4/IembBfAqhDRRh/YIDKo3DXZze5nreEbw2
                1g+zeoN9TuqyxPKMzPf47f3eKfSpnoAjtscEpGbV4Q28u77NaTpC0HvcZ+qXS9hd
                iug234A+CQUrAgMBAAGjggPrMIID5zCCATMGA1UdDgSCASoEggEmMIIBIjANBgkq
                hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5XTyXePvybT7/hhXGdPl1uMu5RtW4U/o
                5zWKZ2bdbYyIibLkFulfEXzsarhB63zbAQb/jlKod2LUQbGVkDCYMhnGdiipuROG
                0Yn1bjGiFPRQgd/NMbIfxhAoNwtdkkIZUdyQgejFbtp8vNk/oE3gGTTw8GAcf16M
                zuf/QbF3p8/t9TH7p31DNgMvWOZWXotK3oLaoIJaPShYz13cNrIsWUCWtAlx1PtG
                4Hid68+PyHpmwXwKoQ0UYf2CAyqNw12c3uZ63hG8NtYPs3qDfU7qssTyjMz3+O39
                3in0qZ6AI7bHBKRm1eENvLu+zWk6QtB73Gfql0vYXYroNt+APgkFKwIDAQABMIIB
                NwYDVR0jBIIBLjCCASqAggEmMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
                AQEA5XTyXePvybT7/hhXGdPl1uMu5RtW4U/o5zWKZ2bdbYyIibLkFulfEXzsarhB
                63zbAQb/jlKod2LUQbGVkDCYMhnGdiipuROG0Yn1bjGiFPRQgd/NMbIfxhAoNwtd
                kkIZUdyQgejFbtp8vNk/oE3gGTTw8GAcf16Mzuf/QbF3p8/t9TH7p31DNgMvWOZW
                XotK3oLaoIJaPShYz13cNrIsWUCWtAlx1PtG4Hid68+PyHpmwXwKoQ0UYf2CAyqN
                w12c3uZ63hG8NtYPs3qDfU7qssTyjMz3+O393in0qZ6AI7bHBKRm1eENvLu+zWk6
                QtB73Gfql0vYXYroNt+APgkFKwIDAQABMAwGA1UdEwQFMAMBAf8wggFjBgNVHREE
                ggFaMIIBVoIJbG9jYWxob3N0gg8qLmdvb2dsZS5jb20uaGuCFCouc2FuZGJveC5n
                b29nbGUuY29tghAqLmMuZ29vZ2xlcnMuY29tghYqLmdvb2dsZS1hbmFseXRpY3Mu
                Y29tghEqLnByb2QuZ29vZ2xlLmNvbYINKi5nc3RhdGljLmNvbYIZbG9jYWxob3N0
                LmNvcnAuZ29vZ2xlLmNvbYIUKi5jbGllbnRzLmdvb2dsZS5jb22HBAoAAgKCFiou
                Z29vZ2xlYWRzZXJ2aWNlcy5jb22CECouZ29vZ2xlYXBpcy5jb22CESouZG91Ymxl
                Y2xpY2submV0ghcqLmdvb2dsZXVzZXJjb250ZW50LmNvbYIVKi5jbGllbnRzNC5n
                b29nbGUuY29tggwqLmdvb2dsZS5jb22CBGcuY2+CGCouc2FuZGJveC5nb29nbGVh
                cGlzLmNvbYcEfwAAATANBgkqhkiG9w0BAQsFAAOCAQEAqvj8rd4l1sfEIUmAc0eo
                puw9OkvaCl1odKyjRUCeF06gsvi+bkfaDNmtgNI9OjaiB0w5XHEShD7fs9bq+F17
                a9AI3zkz10buMF9SX9yUG5GtUPwnqKt4gmDsN3SDZo75hfLHpNzao+h7X/kCJRgC
                RAauJQx03vuugAD6OL8WUWskmkvoQo54uZ6lchLXzetjnWoZOoIxzNfPSKhRaftI
                k1odg8GMvxtekTs8uPFGBFvRJdkvjUFwMWkbItVN6r63V2PV/YlSlIwFgdSttqBI
                8EI7dOqcZvUlSYhd+aG4pA52SPhiLuLm2E6lzJw+TmchIjQceyteWHAU7osppk9E
                nw==
                -----END CERTIFICATE-----
                """;

        String cert3 = """
                -----BEGIN CERTIFICATE-----
                MIIHHTCCBgWgAwIBAgIGAZeTvxfSMA0GCSqGSIb3DQEBCwUAMFgxCzAJBgNVBAYT
                AlVTMRUwEwYDVQQLEwxBR1NBIFRlc3RpbmcxFzAVBgNVBAoTDkdvb2dsZSBUZXN0
                aW5nMRkwFwYDVQQDDBAqLmMuZ29vZ2xlcnMuY29tMB4XDTI1MDYyMTA2Mjc0M1oX
                DTI1MDcyMTE4Mjc0M1owWDELMAkGA1UEBhMCVVMxFTATBgNVBAsTDEFHU0EgVGVz
                dGluZzEXMBUGA1UEChMOR29vZ2xlIFRlc3RpbmcxGTAXBgNVBAMMECouYy5nb29n
                bGVycy5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDhIn86Mb1S
                gaWtczSVGlCxfDAkovm6UMNWo22JHxeh6jGjK9dmGbzCobxQxquRVQ7azsCHUzis
                UJUeYu6RMHSoLwQlnP+FUDhlvtRv3eho9ufMZCUFn6uBkYVqrkWzwsEyZSdubyIA
                C9HE8v+iVqpfLboqK+2XZqbjp1V5zk7p2ZORo4wMDCyGoKMR2Ni3Y6zHRHvocl3h
                hZ6jTy5txNyHp2zS0mpREAPqybE7n00/7vtYGWrXQTM0sKX2V5WWAkSOb/YfMHS3
                AZ9ej4KGdyVQ9op9Mm3+l27Rs3sRewDeqet0uvUdI7ERb9GnlnlmG/hku/3kPoa0
                T0ezI0VlHl9lAgMBAAGjggPrMIID5zCCATMGA1UdDgSCASoEggEmMIIBIjANBgkq
                hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4SJ/OjG9UoGlrXM0lRpQsXwwJKL5ulDD
                VqNtiR8XoeoxoyvXZhm8wqG8UMarkVUO2s7Ah1M4rFCVHmLukTB0qC8EJZz/hVA4
                Zb7Ub93oaPbnzGQlBZ+rgZGFaq5Fs8LBMmUnbm8iAAvRxPL/olaqXy26Kivtl2am
                46dVec5O6dmTkaOMDAwshqCjEdjYt2Osx0R76HJd4YWeo08ubcTch6ds0tJqURAD
                6smxO59NP+77WBlq10EzNLCl9leVlgJEjm/2HzB0twGfXo+ChnclUPaKfTJt/pdu
                0bN7EXsA3qnrdLr1HSOxEW/Rp5Z5Zhv4ZLv95D6GtE9HsyNFZR5fZQIDAQABMIIB
                NwYDVR0jBIIBLjCCASqAggEmMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
                AQEA4SJ/OjG9UoGlrXM0lRpQsXwwJKL5ulDDVqNtiR8XoeoxoyvXZhm8wqG8UMar
                kVUO2s7Ah1M4rFCVHmLukTB0qC8EJZz/hVA4Zb7Ub93oaPbnzGQlBZ+rgZGFaq5F
                s8LBMmUnbm8iAAvRxPL/olaqXy26Kivtl2am46dVec5O6dmTkaOMDAwshqCjEdjY
                t2Osx0R76HJd4YWeo08ubcTch6ds0tJqURAD6smxO59NP+77WBlq10EzNLCl9leV
                lgJEjm/2HzB0twGfXo+ChnclUPaKfTJt/pdu0bN7EXsA3qnrdLr1HSOxEW/Rp5Z5
                Zhv4ZLv95D6GtE9HsyNFZR5fZQIDAQABMAwGA1UdEwQFMAMBAf8wggFjBgNVHREE
                ggFaMIIBVoIJbG9jYWxob3N0gg8qLmdvb2dsZS5jb20uaGuCFCouc2FuZGJveC5n
                b29nbGUuY29tghAqLmMuZ29vZ2xlcnMuY29tghYqLmdvb2dsZS1hbmFseXRpY3Mu
                Y29tghEqLnByb2QuZ29vZ2xlLmNvbYINKi5nc3RhdGljLmNvbYIZbG9jYWxob3N0
                LmNvcnAuZ29vZ2xlLmNvbYIUKi5jbGllbnRzLmdvb2dsZS5jb22HBAoAAgKCFiou
                Z29vZ2xlYWRzZXJ2aWNlcy5jb22CECouZ29vZ2xlYXBpcy5jb22CESouZG91Ymxl
                Y2xpY2submV0ghcqLmdvb2dsZXVzZXJjb250ZW50LmNvbYIVKi5jbGllbnRzNC5n
                b29nbGUuY29tggwqLmdvb2dsZS5jb22CBGcuY2+CGCouc2FuZGJveC5nb29nbGVh
                cGlzLmNvbYcEfwAAATANBgkqhkiG9w0BAQsFAAOCAQEAEgbtskDOQ+E+6obES08c
                4RXoV3Nc76tpzdtWMgonKSGoZTpm9WqkCwS/byqDF58HHwk7NfWTzVzEWRe3zlBj
                cf7Lm5B6XBuU6M3ZzqdXgEuHwSWl0RizTuJ7JXoQNBHxfntPf+gjd08Cb7gHzXNG
                2ci3zlxJa+ZREVp9rboLwnf8pJgCpc+gWSQ9vCz6sZUM1Ty1XPiWe0wP9hKyTaeQ
                GCgyrwmLH95NN23cjs2PrHSUa3LWr4IwQZRAhAXUxcsG0vLUDSHMNEEg4m+NC5MM
                dNWnIQ+GNQFYoMQ3ntYHx7uRUyRenOwfRD7mCMdInijJt+BMxapT5hqpdM+cmNM8
                Lw==
                -----END CERTIFICATE-----
                """;

        return new X509Certificate[]{convertStringToCertificate(cert1), convertStringToCertificate(
                cert2), convertStringToCertificate(cert3)};
    }

    private static X509Certificate convertStringToCertificate(String base64Cert)
            throws CertificateException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                base64Cert.getBytes(StandardCharsets.UTF_8));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(in);
    }
}
