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

import android.util.ArraySet;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Base class that provides abstraction of utility APIs for device check-in. */
public abstract class AbstractDeviceCheckInHelper {

    abstract ArraySet<DeviceId> getDeviceUniqueIds();

    abstract String getCarrierInfo();

    abstract String getDeviceLocale();

    abstract long getDeviceLockApexVersion(String packageName);

    @WorkerThread
    abstract boolean handleGetDeviceCheckInStatusResponse(
            GetDeviceCheckInStatusGrpcResponse response,
            DeviceLockControllerScheduler scheduler,
            @Nullable String fcmRegistrationToken);

    /**
     * Checks whether the device supports the telephony feature.
     *
     * @return {@code true} if the device supports the telephony feature, {@code false} otherwise.
     */
    abstract boolean hasTelephonyFeature();

    /**
     * Gets the state of a system package required for check-in.
     *
     * @param packageName The fully qualified name of the package.
     * @return {@code CheckInRequiredPackageState} The state of the package.
     */
    abstract int getCheckInRequiredPackageState(String packageName);

    /**
     * Enables a system app package required for check-in
     *
     * <p>If the system package is disabled, then this method will attempt to enable the package
     * otherwise if the system package is not currently installed, this method will initiate the
     * installation process.
     *
     * @param packageName The fully qualified name of the package.
     * @param state The state of the package.
     */
    abstract void enableCheckInRequiredPackage(String packageName, int state);

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            CheckInRequiredPackageState.UNDEFINED,
            CheckInRequiredPackageState.ENABLED,
            CheckInRequiredPackageState.DISABLED,
            CheckInRequiredPackageState.UNINSTALLED
    })
    @interface CheckInRequiredPackageState {

        int UNDEFINED = -1;
        /* Package is enabled */
        int ENABLED = 0;

        /* Package is disabled */
        int DISABLED = 1;

        /* Package is uninstalled */
        int UNINSTALLED = 2;
    }
}
