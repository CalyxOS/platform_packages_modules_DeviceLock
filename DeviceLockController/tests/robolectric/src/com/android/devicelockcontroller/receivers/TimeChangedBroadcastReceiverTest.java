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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.AbstractDeviceLockControllerScheduler;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

@RunWith(RobolectricTestRunner.class)
public final class TimeChangedBroadcastReceiverTest {

    private static final long TEST_BOOT_TIME_MILLIS = Duration.ofDays(1).toMillis();
    private static final long TEST_CURRENT_TIME_AFTER_CHANGE_MILLIS = Duration.ofDays(2).toMillis();
    private TestDeviceLockControllerApplication mTestApp;
    private GlobalParametersClient mClient;
    private AbstractDeviceLockControllerScheduler mScheduler;
    private TimeChangedBroadcastReceiver mReceiver;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        mClient = GlobalParametersClient.getInstance();
        mScheduler = mock(AbstractDeviceLockControllerScheduler.class);
        mReceiver = new TimeChangedBroadcastReceiver(mScheduler, Clock.fixed(
                Instant.ofEpochMilli(TEST_CURRENT_TIME_AFTER_CHANGE_MILLIS),
                ZoneOffset.UTC));
    }

    @Test
    public void onReceive() throws Exception {
        // GIVEN boot time
        mClient.setBootTimeMillis(TEST_BOOT_TIME_MILLIS).get();

        // WHEN time changed broadcast received
        mReceiver.onReceive(mTestApp, new Intent(Intent.ACTION_TIME_CHANGED));

        // THEN boot time should be updated
        long expectedDelta = TEST_CURRENT_TIME_AFTER_CHANGE_MILLIS - (TEST_BOOT_TIME_MILLIS
                + SystemClock.elapsedRealtime());
        assertThat(mClient.getBootTimeMillis().get()).isEqualTo(
                TEST_BOOT_TIME_MILLIS + expectedDelta);

        // THEN should correct scheduler expected to run time
        verify(mScheduler).correctExpectedToRunTime(eq(Duration.ofMillis(expectedDelta)));
    }
}