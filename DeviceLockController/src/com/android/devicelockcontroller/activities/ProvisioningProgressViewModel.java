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

package com.android.devicelockcontroller.activities;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;

/**
 * A {@link ViewModel} which provides {@link ProvisioningProgress} to the
 * {@link ProvisioningActivity}.
 */
public final class ProvisioningProgressViewModel extends AndroidViewModel implements
        ProvisioningProgressController {

    private static final String TAG = "ProvisioningProgressViewModel";

    final MutableLiveData<String> mProviderNameLiveData;
    final MutableLiveData<String> mSupportUrlLiveData;
    private volatile boolean mAreProviderNameAndSupportUrlReady;
    private final MutableLiveData<ProvisioningProgress> mProvisioningProgressLiveData;
    private ProvisioningProgress mProvisioningProgress;
    private final LiveData<Boolean> mIsRecolFailed;

    public ProvisioningProgressViewModel(@NonNull Application application) {
        super(application);
        mProviderNameLiveData = new MutableLiveData<>();
        mSupportUrlLiveData = new MutableLiveData<>();

        ListenableFuture<String> getKioskAppProviderNameFuture =
                SetupParametersClient.getInstance().getKioskAppProviderName();
        Futures.addCallback(
                getKioskAppProviderNameFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(String providerName) {
                        if (TextUtils.isEmpty(providerName)) {
                            LogUtil.e(TAG, "Device provider name is empty, should not reach here.");
                            return;
                        }
                        mProviderNameLiveData.postValue(providerName);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to get Kiosk app provider name", t);
                    }
                }, MoreExecutors.directExecutor());

        ListenableFuture<String> getSupportUrlFuture =
                SetupParametersClient.getInstance().getSupportUrl();
        Futures.addCallback(getSupportUrlFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(String supportUrl) {
                mSupportUrlLiveData.postValue(supportUrl);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to get support Url", t);
            }
        }, MoreExecutors.directExecutor());

        mProvisioningProgressLiveData = new MutableLiveData<>();
        ListenableFuture<?> result = Futures.whenAllSucceed(getKioskAppProviderNameFuture,
                getSupportUrlFuture).run(() -> {
                    mAreProviderNameAndSupportUrlReady = true;
                    if (mProvisioningProgress != null) {
                        mProvisioningProgressLiveData.postValue(mProvisioningProgress);
                    }
                }, MoreExecutors.directExecutor());
        Futures.addCallback(result, new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                LogUtil.i(TAG, "Successfully updated provisioning progress live data");
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);
            }
        }, MoreExecutors.directExecutor());

        // Observe the result of ReportDeviceProvisionStateWorker and if recol has failed update
        // the live data.
        WorkManager workManager = WorkManager.getInstance(application);
        LiveData<List<WorkInfo>> workInfos = workManager.getWorkInfosForUniqueWorkLiveData(
                ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);

        mIsRecolFailed = Transformations.map(workInfos, infoList -> {
            if (infoList == null || infoList.isEmpty()) {
                LogUtil.d(TAG, "No work info found");
                return false;
            }

            WorkInfo workInfo = infoList.getFirst();

            if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                Data outputData = workInfo.getOutputData();
                return outputData.getBoolean(ReportDeviceProvisionStateWorker.KEY_IS_RECOL_FAILED,
                        false);
            }

            // If not succeeded or still running, return false.
            LogUtil.d(TAG, "Work info is not succeeded or still running");
            return false;
        });

    }

    /**
     * Returns the {@link LiveData} which provides the latest {@link ProvisioningProgress}.
     *
     * <p>Note, the caller of this method MUST NOT update the LiveData directly, use
     * {@link #setProvisioningProgress} instead.
     */
    public LiveData<ProvisioningProgress> getProvisioningProgressLiveData() {
        return mProvisioningProgressLiveData;
    }

    /**
     * Set the {@link ProvisioningProgress} to the given state.
     *
     * <p>This method is thread-safe and can be called from any thread.
     */
    @Override
    public void setProvisioningProgress(ProvisioningProgress provisioningProgress) {
        if (mAreProviderNameAndSupportUrlReady) {
            LogUtil.d(TAG, "Updating ProvisioningProgress");
            mProvisioningProgress = provisioningProgress;
            mProvisioningProgressLiveData.postValue(provisioningProgress);
        } else {
            LogUtil.d(TAG,
                    "The upstream LiveData is not ready yet, hold on until it completes");
            mProvisioningProgress = provisioningProgress;
        }
    }

    public LiveData<Boolean> getIsRecolFailed() {
        return mIsRecolFailed;
    }
}
