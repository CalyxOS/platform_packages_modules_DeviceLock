/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.INSTALL_REASON_UNKNOWN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_MEID;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_ALLOW_DEBUGGING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_MANDATORY_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_PROVISIONING_TYPE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.READY_FOR_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.RETRY_CHECK_IN;
import static com.android.devicelockcontroller.common.DeviceLockConstants.STATUS_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.STOP_CHECK_IN;
import static com.android.devicelockcontroller.common.DeviceLockConstants.TOTAL_DEVICE_ID_TYPES;
import static com.android.devicelockcontroller.provision.worker.GetFcmTokenWorker.FCM_TOKEN_WORKER_BACKOFF_DELAY;
import static com.android.devicelockcontroller.provision.worker.GetFcmTokenWorker.FCM_TOKEN_WORKER_INITIAL_DELAY;
import static com.android.devicelockcontroller.provision.worker.GetFcmTokenWorker.FCM_TOKEN_WORK_NAME;
import static com.android.devicelockcontroller.receivers.CheckInBootCompletedReceiver.disableCheckInBootCompletedReceiver;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.CONFIG_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.NETWORK_TIME_UNAVAILABLE;
import static com.android.devicelockcontroller.stats.StatsLogger.CheckInRetryReason.RESPONSE_UNSPECIFIED;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.FeatureFlagProvider;
import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.policy.FinalizationControllerImpl;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ProvisioningConfiguration;
import com.android.devicelockcontroller.receivers.ProvisionReadyReceiver;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.DateTimeException;
import java.time.Duration;

/** Helper class to perform the device check-in process with device lock backend server */
public final class DeviceCheckInHelper extends AbstractDeviceCheckInHelper {
    private static final String TAG = "DeviceCheckInHelper";
    private final Context mAppContext;
    private final TelephonyManager mTelephonyManager;
    private final StatsLogger mStatsLogger;
    private final FeatureFlagProvider mFeatureFlagProvider;

    public DeviceCheckInHelper(Context appContext) {
        mAppContext = appContext;
        mTelephonyManager = mAppContext.getSystemService(TelephonyManager.class);
        mStatsLogger = ((StatsLoggerProvider) mAppContext).getStatsLogger();
        mFeatureFlagProvider = (FeatureFlagProvider) mAppContext;
    }

    private boolean hasCdma() {
        return mAppContext
                .getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA);
    }

    @Override
    ArraySet<DeviceId> getDeviceUniqueIds() {
        final int deviceIdTypeBitmap =
                mAppContext.getResources().getInteger(R.integer.device_id_type_bitmap);
        if (deviceIdTypeBitmap < 0) {
            LogUtil.e(TAG, "getDeviceId: Cannot get device_id_type_bitmap");
            return new ArraySet<>();
        }

        return getDeviceAvailableUniqueIds(deviceIdTypeBitmap);
    }

    @VisibleForTesting
    ArraySet<DeviceId> getDeviceAvailableUniqueIds(int deviceIdTypeBitmap) {

        final int totalSlotCount = mTelephonyManager.getActiveModemCount();
        final int maximumIdCount = TOTAL_DEVICE_ID_TYPES * totalSlotCount;
        final ArraySet<DeviceId> deviceIds = new ArraySet<>(maximumIdCount);
        if (maximumIdCount == 0) return deviceIds;

        for (int i = 0; i < totalSlotCount; i++) {
            if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_IMEI)) != 0) {
                final String imei = mTelephonyManager.getImei(i);

                if (imei != null) {
                    deviceIds.add(new DeviceId(DEVICE_ID_TYPE_IMEI, imei));
                }
            }

            if (hasCdma() && (deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_MEID)) != 0) {
                final String meid = mTelephonyManager.getMeid(i);

                if (meid != null) {
                    deviceIds.add(new DeviceId(DEVICE_ID_TYPE_MEID, meid));
                }
            }
        }

        return deviceIds;
    }

    @Override
    String getCarrierInfo() {
        return mTelephonyManager.getSimOperator();
    }

    @Override
    @WorkerThread
    boolean handleGetDeviceCheckInStatusResponse(
            GetDeviceCheckInStatusGrpcResponse response,
            DeviceLockControllerScheduler scheduler,
            @Nullable String fcmRegistrationToken) {
        Futures.getUnchecked(
                GlobalParametersClient.getInstance()
                        .setRegisteredDeviceId(response.getRegisteredDeviceIdentifier()));
        LogUtil.d(TAG, "check in response: " + response.getDeviceCheckInStatus());
        switch (response.getDeviceCheckInStatus()) {
            case READY_FOR_PROVISION:
                boolean result = handleProvisionReadyResponse(response);
                disableCheckInBootCompletedReceiver(mAppContext);
                maybeEnqueueFcmRegistrationTokenRetrievalWork(fcmRegistrationToken);
                return result;
            case RETRY_CHECK_IN:
                try {
                    Duration delay =
                            Duration.between(
                                    SystemClock.currentNetworkTimeClock().instant(),
                                    response.getNextCheckInTime());
                    // Retry immediately if next check in time is in the past.
                    delay = delay.isNegative() ? Duration.ZERO : delay;
                    scheduler.scheduleRetryCheckInWork(delay);
                    maybeEnqueueFcmRegistrationTokenRetrievalWork(fcmRegistrationToken);
                    return true;
                } catch (DateTimeException e) {
                    LogUtil.e(TAG, "No network time is available!");
                    mStatsLogger.logCheckInRetry(NETWORK_TIME_UNAVAILABLE);
                    return false;
                }
            case STOP_CHECK_IN:
                final FinalizationController finalizationController =
                        ((PolicyObjectsProvider) mAppContext).getFinalizationController();
                final ListenableFuture<Void> finalizeDeviceFuture =
                        finalizationController.finalizeNotEnrolledDevice();
                Futures.addCallback(
                        finalizeDeviceFuture,
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(Void result) {
                                var unused = finalizationController.disableApplication();
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(TAG, "Failed to finalize device", t);
                            }
                        },
                        MoreExecutors.directExecutor());
                return true;
            case STATUS_UNSPECIFIED:
            default:
                mStatsLogger.logCheckInRetry(RESPONSE_UNSPECIFIED);
                return false;
        }
    }

    /**
     * Starts a job to retrieve the FCM registration token later if the current one used to\
     * check-in is invalid.
     *
     * @param fcmRegistrationToken the current token
     */
    private void maybeEnqueueFcmRegistrationTokenRetrievalWork(
            @Nullable String fcmRegistrationToken) {
        if (Strings.isNullOrEmpty(fcmRegistrationToken) || fcmRegistrationToken.isBlank()) {
            NetworkRequest request =
                    new NetworkRequest.Builder()
                            .addCapability(NET_CAPABILITY_NOT_RESTRICTED)
                            .addCapability(NET_CAPABILITY_TRUSTED)
                            .addCapability(NET_CAPABILITY_INTERNET)
                            .addCapability(NET_CAPABILITY_NOT_VPN)
                            .build();
            OneTimeWorkRequest.Builder builder =
                    new OneTimeWorkRequest.Builder(GetFcmTokenWorker.class)
                            .setConstraints(
                                    new Constraints.Builder()
                                            .setRequiredNetworkRequest(
                                                    request, NetworkType.CONNECTED)
                                            .build())
                            .setInitialDelay(FCM_TOKEN_WORKER_INITIAL_DELAY)
                            .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL, FCM_TOKEN_WORKER_BACKOFF_DELAY);

            WorkManager.getInstance(mAppContext)
                    .enqueueUniqueWork(
                            FCM_TOKEN_WORK_NAME, ExistingWorkPolicy.REPLACE, builder.build());
        }
    }

    @VisibleForTesting
    @WorkerThread
    boolean handleProvisionReadyResponse(@NonNull GetDeviceCheckInStatusGrpcResponse response) {
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        final FinalizationController finalizationController =
                ((PolicyObjectsProvider) mAppContext).getFinalizationController();

        if (mFeatureFlagProvider.isRecolEnabled()) {
            // Devices that were previously finalized need to have their finalization state reset
            // back to UNFINALIZED at this point.
            Futures.getUnchecked(
                    GlobalParametersClient.getInstance()
                            .setFinalizationState(
                                    FinalizationControllerImpl.FinalizationState.UNFINALIZED));
            // Now, force the FinalizationController to reload its state from disk.
            Futures.getUnchecked(finalizationController.enforceDiskState(/* force= */ true));
        }

        Futures.getUnchecked(
                globalParametersClient.setProvisionForced(response.isProvisionForced()));
        final ProvisioningConfiguration configuration = response.getProvisioningConfig();
        if (configuration == null) {
            LogUtil.e(TAG, "Provisioning Configuration is not provided by server!");
            mStatsLogger.logCheckInRetry(CONFIG_UNAVAILABLE);
            return false;
        }
        final Bundle provisionBundle = configuration.toBundle();
        provisionBundle.putInt(EXTRA_PROVISIONING_TYPE, response.getProvisioningType());
        provisionBundle.putBoolean(EXTRA_MANDATORY_PROVISION, response.isProvisioningMandatory());
        provisionBundle.putBoolean(EXTRA_ALLOW_DEBUGGING, response.isDebuggingAllowed());
        Futures.getUnchecked(SetupParametersClient.getInstance().createPrefs(provisionBundle));
        Futures.getUnchecked(globalParametersClient.setProvisionReady(true));
        mAppContext.sendBroadcastAsUser(
                new Intent(mAppContext, ProvisionReadyReceiver.class), UserHandle.ALL);
        return true;
    }

    @Override
    String getDeviceLocale() {
        return LocaleList.getAdjustedDefault().get(0).toLanguageTag();
    }

    @Override
    long getDeviceLockApexVersion(String packageName) {
        try {
            return mAppContext
                    .getPackageManager()
                    .getPackageInfo(packageName, PackageManager.MATCH_APEX)
                    .getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(TAG, "Failed to get device lock apex version", e);
        }
        return 0;
    }

    @Override
    void enableCheckInRequiredPackage(String packageName, @CheckInRequiredPackageState int state) {
        DevicePolicyController devicePolicyController =
                ((PolicyObjectsProvider) mAppContext).getPolicyController();
        switch (state) {
            case CheckInRequiredPackageState.DISABLED ->
                    enablePackageForUser(packageName, devicePolicyController);
            case CheckInRequiredPackageState.UNINSTALLED ->
                    initiateExistingPackageInstallForUser(packageName, devicePolicyController);
            default -> {
                /* No-op */
            }
        }
    }

    private void enablePackageForUser(
            String packageName, DevicePolicyController devicePolicyController) {
        try {
            PackageManager mPackageManager =
                    mAppContext.createContextAsUser(UserHandle.SYSTEM, 0).getPackageManager();
            mPackageManager.setApplicationEnabledSetting(
                    packageName, COMPONENT_ENABLED_STATE_DEFAULT, DONT_KILL_APP);

            // Enable the package protection for the required package
            // Once the check-in worker retrieves the device identifiers, this restriction is
            // removed
            devicePolicyController.disableUserControlForCheckInRequiredPackage(packageName);
        } catch (SecurityException ex) {
            // Not expected to happen as the controller has the correct permissions
            LogUtil.e(TAG, "Exception thrown while enabling package", ex);
        }
    }

    private void initiateExistingPackageInstallForUser(
            String packageName, DevicePolicyController devicePolicyController) {
        try {
            // We won't register a status receiver as the check-in will be retried later
            mAppContext
                    .getPackageManager()
                    .getPackageInstaller()
                    .installExistingPackage(
                            packageName, INSTALL_REASON_UNKNOWN, null /* statusReceiver */);
            LogUtil.d(TAG, "Initiated existing package install for " + packageName);
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to initiate install for existing package: Exception", e);
        }
        // Disable user control for the required package
        // Once the check-in worker retrieves the device identifiers, this restriction is removed
        devicePolicyController.disableUserControlForCheckInRequiredPackage(packageName);
    }

    @Override
    int getCheckInRequiredPackageState(String packageName) {
        try {
            Context packageContext =
                    mAppContext.createPackageContextAsUser(
                            packageName, 0 /* flags */, UserHandle.SYSTEM);
            int checkInRequiredPackageState =
                    packageContext.getPackageManager().getApplicationEnabledSetting(packageName);
            // We return `DISABLED` if the state is neither `ENABLED` nor `DEFAULT`
            if (checkInRequiredPackageState == COMPONENT_ENABLED_STATE_DEFAULT
                    || checkInRequiredPackageState == COMPONENT_ENABLED_STATE_ENABLED) {
                return CheckInRequiredPackageState.ENABLED;
            }
            return CheckInRequiredPackageState.DISABLED;
        } catch (PackageManager.NameNotFoundException e) {
            return CheckInRequiredPackageState.UNINSTALLED;
        }
    }

    @Override
    public boolean hasTelephonyFeature() {
        return mAppContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }
}
