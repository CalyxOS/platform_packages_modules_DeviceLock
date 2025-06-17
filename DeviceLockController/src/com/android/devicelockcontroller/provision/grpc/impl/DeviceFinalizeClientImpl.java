/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.provision.grpc.impl;

import androidx.annotation.Keep;

import com.android.devicelockcontroller.proto.DeviceLockFinalizeServiceGrpc;
import com.android.devicelockcontroller.proto.ReportDeviceProgramCompleteRequest;
import com.android.devicelockcontroller.util.KeyAttestationUtil;
import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient;
import com.android.devicelockcontroller.util.LogUtil;
import com.android.devicelockcontroller.util.ThreadAsserts;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import io.grpc.ClientInterceptor;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;

/**
 * A client for {@link com.android.devicelockcontroller.proto.DeviceLockFinalizeServiceGrpc}.
 */
@Keep
public final class DeviceFinalizeClientImpl extends DeviceFinalizeClient {
    private final DeviceLockFinalizeServiceGrpc.DeviceLockFinalizeServiceBlockingStub mBlockingStub;

    private boolean mIsImeiHardeningDeregistrationEnabled;

    public DeviceFinalizeClientImpl(ClientInterceptor clientInterceptor,
            boolean isImeiHardeningDeregistrationEnabled) {
        mBlockingStub = DeviceLockFinalizeServiceGrpc.newBlockingStub(
                        OkHttpChannelBuilder
                                .forAddress(sHostName, sPortNumber)
                                .build())
                .withInterceptors(clientInterceptor);
        mIsImeiHardeningDeregistrationEnabled = isImeiHardeningDeregistrationEnabled;
    }

    /**
     * Reports that a device completed a Device Lock program.
     */
    @Override
    public ReportDeviceProgramCompleteResponse reportDeviceProgramComplete() {
        ThreadAsserts.assertWorkerThread("reportDeviceProgramComplete");
        try {

            ReportDeviceProgramCompleteRequest.Builder reportDeviceProgramCompleteRequest =
                    ReportDeviceProgramCompleteRequest.newBuilder()
                            .setRegisteredDeviceIdentifier(sRegisteredId);

            if (mIsImeiHardeningDeregistrationEnabled) {
                byte[] keyAttestationLeafCertificate;

                try {
                    keyAttestationLeafCertificate =
                            KeyAttestationUtil.getKeyAttestationLeafCertificate();
                } catch (InvalidAlgorithmParameterException | CertificateException |
                         NoSuchAlgorithmException | IOException | KeyStoreException |
                         NoSuchProviderException exception) {
                    LogUtil.e(TAG, "Report finalization failed with KeystoreUtil exception",
                            exception);
                    return null;
                }

                if (keyAttestationLeafCertificate != null) {
                    reportDeviceProgramCompleteRequest.setLeafCertificate(
                            ByteString.copyFrom(keyAttestationLeafCertificate));
                } else {
                    return null;
                }
            }

            mBlockingStub.reportDeviceProgramComplete(reportDeviceProgramCompleteRequest.build());
            return new ReportDeviceProgramCompleteResponse();
        } catch (StatusRuntimeException e) {
            return new ReportDeviceProgramCompleteResponse(e.getStatus());
        }
    }
}
