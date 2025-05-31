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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_PROVISION_STATE_EVENT__EVENT__EVENT_DEVICE_RESET;

import static org.mockito.Mockito.verify;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;

import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ResetDeviceReceiverTest {
    private Intent mIntent;
    private TestDeviceLockControllerApplication mTestApp;
    private ResetDeviceReceiver mReceiver;
    private StatsLogger mStatsLogger;
    // Checkstyle results in line too long when using original constant.
    private static final int RESET = DEVICE_LOCK_PROVISION_STATE_EVENT__EVENT__EVENT_DEVICE_RESET;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        mIntent = new Intent(mTestApp, ResetDeviceReceiver.class);
        mReceiver = new ResetDeviceReceiver(TestingExecutors.sameThreadScheduledExecutor());
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) mTestApp.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();
    }

    @Test
    public void onReceive_shouldLogToStatsLogger() {
        mReceiver.onReceive(mTestApp, mIntent);

        verify(mStatsLogger).logProvisionStateEvent(RESET);
    }
}
