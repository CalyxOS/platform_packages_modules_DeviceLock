/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient;
import com.android.devicelockcontroller.shadows.FakeAndroidKeystore;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.grpc.testing.GrpcCleanupRule;

/**
 * Tests for {@link DeviceFinalizeClientImpl}.
 */
@RunWith(RobolectricTestRunner.class)
public final class DeviceFinalizeClientImplTest {

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final GrpcCleanupRule mGrpcCleanup = new GrpcCleanupRule();

    private final ExecutorService mBgExecutor = Executors.newSingleThreadExecutor();

    private DeviceFinalizeClientImpl mDeviceFinalizeClientImpl;

    private String deviceIdentifier = "29838923";

    @Before
    public void setUp() throws Exception {
        final ClientInterceptor clientInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions,
                    Channel next) {
                return next.newCall(method, callOptions);
            }
        };

        // Set some static protected field dependencies like deviceId
        DeviceFinalizeClientImpl.getInstance(ApplicationProvider.getApplicationContext(),
                "test.host.name", 7777,
                clientInterceptor, deviceIdentifier, true);

        mDeviceFinalizeClientImpl = new DeviceFinalizeClientImpl(
                clientInterceptor,
                true);
    }

    @Test
    public void reportDeviceProgramComplete_whenKeyAttestationFails_returnsNull() throws Exception {
        Security.removeProvider("AndroidKeyStore");

        AtomicReference<DeviceFinalizeClient.ReportDeviceProgramCompleteResponse> response =
                new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceFinalizeClientImpl.reportDeviceProgramComplete()))
                .get();

        assertThat(response.get()).isNull();
    }

    @Test
    public void reportDeviceProgramComplete_whenKeyAttestationReturnsNull_returnsNull()
            throws Exception {
        Security.addProvider(new FakeAndroidKeystore.FakeSecurityProvider());
        FakeAndroidKeystore.SingletonKeystore.certs.clear();

        AtomicReference<DeviceFinalizeClient.ReportDeviceProgramCompleteResponse> response =
                new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceFinalizeClientImpl.reportDeviceProgramComplete()))
                .get();

        assertThat(response.get()).isNull();
    }
}
