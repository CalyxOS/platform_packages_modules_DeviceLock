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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED_UNREPORTED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_CLEAR;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker.REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.FeatureFlagProvider;
import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient.ReportDeviceProgramCompleteResponse;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public final class FinalizationControllerImplTest {

    private static final int TIMEOUT_MS = 1000;
    private final ExecutionSequencer mExecutionSequencer = ExecutionSequencer.create();
    private final Executor mBgExecutor = Executors.newCachedThreadPool();
    private TestSystemDeviceLockManager mSystemDeviceLockManager;
    private Context mContext;
    private FinalizationControllerImpl mFinalizationController;
    private FinalizationStateDispatchQueue mDispatchQueue;
    private GlobalParametersClient mGlobalParametersClient;
    @Mock
    private FeatureFlagProvider mFeatureFlagProvider;
    private ProvisionStateController mProvisionStateController;
    private TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestApp = ApplicationProvider.getApplicationContext();
        mContext = ApplicationProvider.getApplicationContext();
        mSystemDeviceLockManager = new TestSystemDeviceLockManager(mContext);
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext);

        mGlobalParametersClient = GlobalParametersClient.getInstance();
        mDispatchQueue = new FinalizationStateDispatchQueue(mExecutionSequencer);
        mProvisionStateController = mTestApp.getProvisionStateController();
    }

    @After
    public void tearDown() {
        // Guarantees cleanup even if a test fails, preventing resource leaks.
        LongRunningTestWorker.reset();
    }

    @Test
    public void notifyRestrictionsCleared_startsReportingWork() throws Exception {
        mFinalizationController = makeFinalizationController();

        // WHEN restrictions are cleared
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN work manager has work scheduled to report the device is finalized and the disk
        // value is set to unreported
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
        assertThat(mGlobalParametersClient.getFinalizationState().get())
                .isEqualTo(FINALIZED_UNREPORTED);
    }

    @Test
    public void finalizeNotEnrolledDevice_doesNotStartReportingWork() throws Exception {
        mFinalizationController = makeFinalizationController();

        // WHEN a non enrolled device is finalized and disabled
        ListenableFuture<Void> finalizeFuture =
                mFinalizationController.finalizeNotEnrolledDevice();
        Futures.getChecked(finalizeFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        ListenableFuture<Void> disableFuture = mFinalizationController.disableApplication();
        Futures.getChecked(disableFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);


        // THEN work manager has no work scheduled to report the device is finalized and the disk
        // value is set to finalized
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isEmpty();
        assertThat(mGlobalParametersClient.getFinalizationState().get())
                .isEqualTo(FINALIZED);
        assertThat(mSystemDeviceLockManager.finalized).isTrue();
    }

    @Test
    public void finalizeNotEnrolledDevice_stopsAllWorkers() throws Exception {
        // GIVEN a long running worker is in progress
        mFinalizationController = makeFinalizationController(LongRunningTestWorker.class);
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
                LongRunningTestWorker.class).build();
        WorkManager.getInstance(mContext).enqueue(workRequest);
        // This allows the worker to start.
        shadowOf(Looper.getMainLooper()).idle();
        assertThat(LongRunningTestWorker.startLatch.await(2, TimeUnit.SECONDS)).isTrue();
        WorkInfo workInfo = WorkManager.getInstance(mContext).getWorkInfoById(
                workRequest.getId()).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(workInfo.getState()).isEqualTo(WorkInfo.State.RUNNING);

        // WHEN the device is finalized as not enrolled
        ListenableFuture<Void> finalizedFuture =
                mFinalizationController.finalizeNotEnrolledDevice();
        Futures.getChecked(finalizedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the long running worker is cancelled and pruned
        workInfo = WorkManager.getInstance(mContext).getWorkInfoById(
                workRequest.getId()).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(workInfo).isNull();
    }

    @Test
    public void finalizeNotEnrolledDevice_recolEnabled_resetsStorageParameters() throws Exception {
        // GIVEN recol feature is enabled
        when(mFeatureFlagProvider.isRecolEnabled()).thenReturn(true);
        when(mProvisionStateController.setNextStateForEvent(PROVISION_CLEAR)).thenReturn(
                Futures.immediateVoidFuture());
        mFinalizationController = makeFinalizationController();

        // GIVEN some data in storage parameters
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();
        final SharedPreferences globalPrefs = deviceContext.getSharedPreferences(
                "global-params", Context.MODE_PRIVATE);
        final SharedPreferences setupPrefs = deviceContext.getSharedPreferences(
                "setup-prefs", Context.MODE_PRIVATE);
        final SharedPreferences userPrefs = deviceContext.getSharedPreferences(
                "user-params", Context.MODE_PRIVATE);

        globalPrefs.edit().putString("test_key", "test_value").apply();
        setupPrefs.edit().putString("test_key", "test_value").apply();
        userPrefs.edit().putString("test_key", "test_value").apply();

        assertThat(globalPrefs.getAll()).isNotEmpty();
        assertThat(setupPrefs.getAll()).isNotEmpty();
        assertThat(userPrefs.getAll()).isNotEmpty();

        // WHEN finalizeNotEnrolledDevice is called
        ListenableFuture<Void> finalizedFuture =
                mFinalizationController.finalizeNotEnrolledDevice();
        Futures.getChecked(finalizedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        shadowOf(Looper.getMainLooper()).idle();

        // THEN storage parameters are cleared
        assertThat(globalPrefs.getAll()).isEmpty();
        assertThat(setupPrefs.getAll()).isEmpty();
        //User prefs should only have boot-time-millis with value of 0
        assertThat(userPrefs.getAll()).containsEntry("boot-time-mills", 0L);
        assertThat(userPrefs.getAll()).doesNotContainKey("test_key");
    }


    @Test
    public void finalizeNotEnrolledDevice_recolDisabled_doesNotResetStorageParameters()
            throws Exception {
        // GIVEN recol feature is not enabled
        when(mFeatureFlagProvider.isRecolEnabled()).thenReturn(false);
        mFinalizationController = makeFinalizationController();

        // GIVEN some data in storage parameters
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();
        final SharedPreferences globalPrefs = deviceContext.getSharedPreferences(
                "global-params", Context.MODE_PRIVATE);
        final SharedPreferences setupPrefs = deviceContext.getSharedPreferences(
                "setup-prefs", Context.MODE_PRIVATE);
        final SharedPreferences userPrefs = deviceContext.getSharedPreferences(
                "user-params", Context.MODE_PRIVATE);

        globalPrefs.edit().putString("test_key", "test_value").apply();
        setupPrefs.edit().putString("test_key", "test_value").apply();
        userPrefs.edit().putString("test_key", "test_value").apply();

        assertThat(globalPrefs.getAll()).isNotEmpty();
        assertThat(setupPrefs.getAll()).isNotEmpty();
        assertThat(userPrefs.getAll()).isNotEmpty();

        // WHEN finalizeNotEnrolledDevice is called
        ListenableFuture<Void> finalizedFuture =
                mFinalizationController.finalizeNotEnrolledDevice();
        Futures.getChecked(finalizedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN storage parameters are not cleared
        assertThat(globalPrefs.getString("test_key", "")).isEqualTo("test_value");
        assertThat(setupPrefs.getString("test_key", "")).isEqualTo("test_value");
        assertThat(userPrefs.getString("test_key", "")).isEqualTo("test_value");
    }

    @Test
    public void reportingFinishedSuccessfully_finalizesDevice() throws Exception {
        mFinalizationController = makeFinalizationController();

        // GIVEN the restrictions have been requested to clear
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // WHEN the work is reported successfully
        ReportDeviceProgramCompleteResponse successResponse =
                new ReportDeviceProgramCompleteResponse();
        ListenableFuture<Void> reportedFuture =
                mFinalizationController.notifyFinalizationReportResult(successResponse);
        Futures.getChecked(reportedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the global parameters value is set to finalized
        assertThat(mGlobalParametersClient.getFinalizationState().get()).isEqualTo(FINALIZED);
    }

    @Test
    public void disableDeviceCalled_disablesDlcController() throws Exception {
        mFinalizationController = makeFinalizationController();

        // GIVEN the restrictions have been requested to clear and work is reported successfully
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        ReportDeviceProgramCompleteResponse successResponse =
                new ReportDeviceProgramCompleteResponse();
        ListenableFuture<Void> reportedFuture =
                mFinalizationController.notifyFinalizationReportResult(successResponse);
        Futures.getChecked(reportedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // WHEN the disableDevice method is called
        ListenableFuture<Void> disableFuture = mFinalizationController.disableApplication();
        Futures.getChecked(disableFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN both global parameters and persistent disk states are set to finalized
        assertThat(mGlobalParametersClient.getFinalizationState().get()).isEqualTo(FINALIZED);
        assertThat(mSystemDeviceLockManager.finalized).isTrue();
    }


    @Test
    public void reportingFinishedSuccessfully_stopsAllWorkers() throws Exception {
        // GIVEN a long running worker is in progress
        mFinalizationController = makeFinalizationController(LongRunningTestWorker.class);
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
                LongRunningTestWorker.class).build();
        WorkManager.getInstance(mContext).enqueue(workRequest);
        // This allows the worker to start.
        shadowOf(Looper.getMainLooper()).idle();
        assertThat(LongRunningTestWorker.startLatch.await(2, TimeUnit.SECONDS)).isTrue();
        WorkInfo workInfo = WorkManager.getInstance(mContext).getWorkInfoById(
                workRequest.getId()).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(workInfo.getState()).isEqualTo(WorkInfo.State.RUNNING);

        // WHEN the restrictions are cleared and finalization is reported
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        ReportDeviceProgramCompleteResponse successResponse =
                new ReportDeviceProgramCompleteResponse();
        ListenableFuture<Void> reportedFuture =
                mFinalizationController.notifyFinalizationReportResult(successResponse);
        Futures.getChecked(reportedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the long running worker is cancelled and pruned
        workInfo = WorkManager.getInstance(mContext).getWorkInfoById(
                workRequest.getId()).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(workInfo).isNull();
    }

    @Test
    public void reportingFinishedSuccessfully_recolFlagIsTrue_doesNotDisableDeviceLockController()
            throws Exception {
        when(mFeatureFlagProvider.isRecolEnabled()).thenReturn(true);
        when(mProvisionStateController.setNextStateForEvent(PROVISION_CLEAR)).thenReturn(
                Futures.immediateVoidFuture());
        mFinalizationController = makeFinalizationController();
        final String packageName = mContext.getPackageName();

        // WHEN the restrictions are cleared and finalization is reported successfully
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        ReportDeviceProgramCompleteResponse successResponse =
                new ReportDeviceProgramCompleteResponse();
        ListenableFuture<Void> reportedFuture =
                mFinalizationController.notifyFinalizationReportResult(successResponse);
        Futures.getChecked(reportedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the device lock controller package is not disabled
        int enabledSetting = mContext.getPackageManager().getApplicationEnabledSetting(packageName);
        assertThat(enabledSetting).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        assertThat(enabledSetting).isNotEqualTo(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }

    @Test
    public void reportingFinishedSuccessfully_recolFlagIsFalse_doesDisableDeviceLockController()
            throws Exception {
        when(mFeatureFlagProvider.isRecolEnabled()).thenReturn(false);
        mFinalizationController = makeFinalizationController();
        final String packageName = mContext.getPackageName();

        // WHEN the restrictions are cleared and finalization is reported successfully
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        ReportDeviceProgramCompleteResponse successResponse =
                new ReportDeviceProgramCompleteResponse();
        ListenableFuture<Void> reportedFuture =
                mFinalizationController.notifyFinalizationReportResult(successResponse);
        Futures.getChecked(reportedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the device lock controller package is disabled
        int enabledSetting = mContext.getPackageManager().getApplicationEnabledSetting(packageName);
        assertThat(enabledSetting).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }

    @Test
    public void unreportedStateInitializedFromDisk_reportsWork() throws Exception {
        // GIVEN the state on disk is unreported
        Futures.getChecked(
                mGlobalParametersClient.setFinalizationState(FINALIZED_UNREPORTED),
                Exception.class);

        // WHEN the controller is initialized
        mFinalizationController = makeFinalizationController();
        Futures.getChecked(mFinalizationController.enforceDiskState(/* force= */false),
                Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the state from disk is used and is applied immediately, reporting the work.
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
    }

    @Test
    public void enforceDiskState_noForce_usesCurrentState() throws Exception {
        // GIVEN the controller has an unreported state
        Futures.getChecked(
                mGlobalParametersClient.setFinalizationState(FINALIZED_UNREPORTED),
                Exception.class);
        mFinalizationController = makeFinalizationController();
        Futures.getChecked(mFinalizationController.enforceDiskState(/* force= */ false),
                Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        // GIVEN the disk state is finalized (e.g. on another user)
        Futures.getChecked(
                mGlobalParametersClient.setFinalizationState(FINALIZED),
                Exception.class);

        // WHEN the controller enforces disk state without force
        Futures.getChecked(mFinalizationController.enforceDiskState(/* force= */ false),
                Exception.class);

        // THEN the disk state is not enforced
        assertThat(mSystemDeviceLockManager.finalized).isFalse();
    }

    @Test
    public void enforceDiskState_force_usesDiskState() throws Exception {
        // GIVEN the controller has an unreported state
        Futures.getChecked(
                mGlobalParametersClient.setFinalizationState(FINALIZED_UNREPORTED),
                Exception.class);
        mFinalizationController = makeFinalizationController();
        Futures.getChecked(mFinalizationController.enforceDiskState(/* force= */ false),
                Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        // GIVEN the global parameters and persistent disk state is finalized (e.g. on another user)
        Futures.getChecked(
                mGlobalParametersClient.setFinalizationState(FINALIZED),
                Exception.class);
        mSystemDeviceLockManager.finalized = true;

        // WHEN the controller enforces disk state with force
        Futures.getChecked(mFinalizationController.enforceDiskState(/* force= */ true),
                Exception.class);

        // THEN the state from disk is used and enforced.
        assertThat(mSystemDeviceLockManager.finalized).isTrue();
    }

    private FinalizationControllerImpl makeFinalizationController() {
        return new FinalizationControllerImpl(
                mContext, mDispatchQueue, mBgExecutor, TestWorker.class, mSystemDeviceLockManager,
                mFeatureFlagProvider);
    }

    private FinalizationControllerImpl makeFinalizationController(
            Class<? extends ListenableWorker> workerClass) {
        return new FinalizationControllerImpl(
                mContext, mDispatchQueue, mBgExecutor, workerClass,
                mSystemDeviceLockManager, mFeatureFlagProvider);
    }

    private static final class TestSystemDeviceLockManager implements SystemDeviceLockManager {
        public final Context mContext;
        public boolean finalized = false;

        TestSystemDeviceLockManager(Context context) {
            mContext = context;
        }


        @Override
        public void addFinancedDeviceKioskRole(@NonNull String packageName, Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void removeFinancedDeviceKioskRole(@NonNull String packageName, Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void setDlcExemptFromActivityBgStartRestrictionState(boolean exempt,
                Executor executor, @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void setDlcAllowedToSendUndismissibleNotifications(boolean allowed,
                Executor executor, @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void setKioskAppExemptFromRestrictionsState(String packageName, boolean exempt,
                Executor executor, @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void enableKioskKeepalive(String packageName, Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void disableKioskKeepalive(Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void enableControllerKeepalive(Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void disableControllerKeepalive(Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {

        }

        @Override
        public void setDeviceFinalized(boolean finalized, Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {
            this.finalized = finalized;
            if (finalized) {
                final String packageName = mContext.getPackageName();
                mContext.getPackageManager().setApplicationEnabledSetting(
                        packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        0);
            }
            executor.execute(() -> callback.onResult(null));
        }

        @Override
        public void setPostNotificationsSystemFixed(boolean systemFixed, Executor executor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {

        }
    }

    /**
     * Fake test worker that just finishes work immediately
     */
    private static final class TestWorker extends ListenableWorker {

        TestWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
            super(appContext, workerParams);
        }

        @NonNull
        @Override
        public ListenableFuture<Result> startWork() {
            return Futures.immediateFuture(Result.success());
        }
    }

    /**
     * Fake test worker that simulates a long running worker
     */
    public static final class LongRunningTestWorker extends ListenableWorker {
        public static volatile CountDownLatch startLatch = new CountDownLatch(1);
        // Public completer allows us to externally control the completion of the work.
        public static volatile CallbackToFutureAdapter.Completer<Result> completer;

        public LongRunningTestWorker(@NonNull Context appContext,
                @NonNull WorkerParameters workerParams) {
            super(appContext, workerParams);
        }

        /**
         * Resets the state of this worker during clean up.
         */
        public static void reset() {
            startLatch = new CountDownLatch(1);
            if (completer != null) {
                completer.setCancelled();
            }
            completer = null;
        }

        @NonNull
        @Override
        public ListenableFuture<Result> startWork() {
            return CallbackToFutureAdapter.getFuture(c -> {
                completer = c;
                startLatch.countDown();
                // Returned value is just for debugging purposes.
                return "LongRunningTestWorker Future";
            });
        }
    }
}
