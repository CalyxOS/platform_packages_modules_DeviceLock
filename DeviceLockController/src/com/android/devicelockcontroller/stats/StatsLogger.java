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

package com.android.devicelockcontroller.stats;
import com.android.devicelockcontroller.DevicelockStatsLog;

/**
 * Utility class wrapping operations related to Statistics.
 *
 * Please refer to {@link DevicelockStatsLog} class and
 * stats/atoms/devicelock/devicelock_extension_atoms.proto for more information.
 */
public interface StatsLogger {
    /**
     * Log to statistics the event of successfully getting device check in status from the server.
     */
    void logGetDeviceCheckInStatus();

    /**
     * Log to statistics the event of successfully pausing the device provisioning.
     */
    void logPauseDeviceProvisioning();

    /**
     * Log to statistics the event of successfully completing the device provisioning.
     */
    void logReportDeviceProvisioningComplete();

    /**
     * Log to statistics the event of successfully reporting the device provisioning state to the
     * server.
     */
    void logReportDeviceProvisionState();
}