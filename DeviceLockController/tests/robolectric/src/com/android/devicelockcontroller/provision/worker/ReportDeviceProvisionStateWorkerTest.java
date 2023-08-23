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

package com.android.devicelockcontroller.provision.worker;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_FACTORY_RESET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.AbstractDeviceLockControllerScheduler;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class ReportDeviceProvisionStateWorkerTest {
    private static final int TEST_DAYS_LEFT_UNTIL_RESET = 3;
    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private DeviceCheckInClient mClient;
    @Mock
    private ReportDeviceProvisionStateGrpcResponse mResponse;
    @Mock
    private AbstractDeviceLockControllerScheduler mScheduler;
    private ReportDeviceProvisionStateWorker mWorker;
    private TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp,
                new Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .setExecutor(new SynchronousExecutor())
                        .build());

        when(mClient.reportDeviceProvisionState(anyInt(), anyBoolean())).thenReturn(mResponse);
        mWorker = TestListenableWorkerBuilder.from(
                        mTestApp, ReportDeviceProvisionStateWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(
                                        ReportDeviceProvisionStateWorker.class.getName())
                                        ? new ReportDeviceProvisionStateWorker(context,
                                        workerParameters, mClient,
                                        TestingExecutors.sameThreadScheduledExecutor(), mScheduler)
                                        : null;
                            }
                        }).build();
    }

    @Test
    public void doWork_responseHasRecoverableError_returnRetry() {
        when(mResponse.hasRecoverableError()).thenReturn(true);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.retry());
    }

    @Test
    public void doWork_responseHasFatalError_returnFailure() {
        when(mResponse.hasFatalError()).thenReturn(true);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.failure());
    }

    @Test
    public void doWork_responseIsSuccessful_globalParametersShouldBeSet_returnSuccess()
            throws Exception {
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getNextClientProvisionState()).thenReturn(PROVISION_STATE_FACTORY_RESET);
        when(mResponse.getDaysLeftUntilReset()).thenReturn(TEST_DAYS_LEFT_UNTIL_RESET);

        assertThat(Futures.getUnchecked(mWorker.startWork())).isEqualTo(Result.success());

        GlobalParametersClient globalParameters = GlobalParametersClient.getInstance();
        assertThat(globalParameters.getLastReceivedProvisionState().get()).isEqualTo(
                PROVISION_STATE_FACTORY_RESET);
        assertThat(UserParameters.getDaysLeftUntilReset(mTestApp)).isEqualTo(
                TEST_DAYS_LEFT_UNTIL_RESET);

        verify(mScheduler).scheduleNextProvisionFailedStepAlarm();
    }
}
