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

import static com.android.devicelockcontroller.activities.ProvisioningActivity.EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START;
import static com.android.devicelockcontroller.activities.ProvisioningActivity.EXTRA_SHOW_PROVISION_FAILED_UI_ON_START;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.graphics.Insets;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.common.DeviceLockConstants;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public final class ProvisioningActivityTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock private WindowInsets mWindowInsetsMock;

    @Test
    public void noExtraSet_showDevicePoliciesFragment() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class).create().get();

        shadowOf(Looper.getMainLooper()).idle();
        Fragment fragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        assertThat(fragment).isInstanceOf(DevicePoliciesFragment.class);
    }

    @Test
    public void
            withCriticalFailedUIExtra_setMandatoryFailedProvisionProgressAndShowProgressFragment() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(
                                ProvisioningActivity.class,
                                new Intent()
                                        .putExtra(
                                                EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START,
                                                true))
                        .setup()
                        .get();

        ShadowLooper.runUiThreadTasks();

        ProvisioningProgress actual =
                new ViewModelProvider(activity)
                        .get(ProvisioningProgressViewModel.class)
                        .getProvisioningProgressLiveData()
                        .getValue();
        assertThat(actual)
                .isEqualTo(
                        ProvisioningProgress.getMandatoryProvisioningFailedProgress(
                                DeviceLockConstants.ProvisionFailureReason
                                        .POLICY_ENFORCEMENT_FAILED));

        Fragment fragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        assertThat(fragment).isInstanceOf(ProgressFragment.class);
    }

    @Test
    public void withNonCriticalUIExtra_setNonMandatoryFailedProgressAndShowProgressFragment() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(
                                ProvisioningActivity.class,
                                new Intent()
                                        .putExtra(EXTRA_SHOW_PROVISION_FAILED_UI_ON_START, true))
                        .setup()
                        .get();

        ShadowLooper.runUiThreadTasks();

        ProvisioningProgress actual =
                new ViewModelProvider(activity)
                        .get(ProvisioningProgressViewModel.class)
                        .getProvisioningProgressLiveData()
                        .getValue();
        assertThat(actual)
                .isEqualTo(
                        ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                                DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON));
        Fragment fragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        assertThat(fragment).isInstanceOf(ProgressFragment.class);
    }

    @Test
    public void provisioningActivity_hidesTheSystemBars() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class).setup().get();

        assertThat(
                        activity.getWindow()
                                .getDecorView()
                                .getRootWindowInsets()
                                .isVisible(WindowInsets.Type.systemBars()))
                .isFalse();
    }

    @Test
    public void withZeroTopAndBottomInsets_paddingIsNotApplied() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class).create().get();
        View fragmentContainer = activity.findViewById(R.id.fragment_container);
        int originalPaddingLeft = fragmentContainer.getPaddingLeft();
        int originalPaddingTop = fragmentContainer.getPaddingTop();
        int originalPaddingRight = fragmentContainer.getPaddingRight();
        int originalPaddingBottom = fragmentContainer.getPaddingBottom();
        setupMocksForWindowInsets(0, 0, 0, 0);

        fragmentContainer.dispatchApplyWindowInsets(mWindowInsetsMock);
        ShadowLooper.idleMainLooper();

        assertThat(fragmentContainer.getPaddingLeft()).isEqualTo(originalPaddingLeft);
        assertThat(fragmentContainer.getPaddingTop()).isEqualTo(originalPaddingTop);
        assertThat(fragmentContainer.getPaddingRight()).isEqualTo(originalPaddingRight);
        assertThat(fragmentContainer.getPaddingBottom()).isEqualTo(originalPaddingBottom);
    }

    @Test
    public void withOnlyNonZeroLeftAndRightInsets_paddingIsNotApplied() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class).create().get();
        View fragmentContainer = activity.findViewById(R.id.fragment_container);
        int originalPaddingLeft = fragmentContainer.getPaddingLeft();
        int originalPaddingTop = fragmentContainer.getPaddingTop();
        int originalPaddingRight = fragmentContainer.getPaddingRight();
        int originalPaddingBottom = fragmentContainer.getPaddingBottom();
        setupMocksForWindowInsets(17, 0, 19, 0);

        fragmentContainer.dispatchApplyWindowInsets(mWindowInsetsMock);
        ShadowLooper.idleMainLooper();

        assertThat(fragmentContainer.getPaddingLeft()).isEqualTo(originalPaddingLeft);
        assertThat(fragmentContainer.getPaddingTop()).isEqualTo(originalPaddingTop);
        assertThat(fragmentContainer.getPaddingRight()).isEqualTo(originalPaddingRight);
        assertThat(fragmentContainer.getPaddingBottom()).isEqualTo(originalPaddingBottom);
    }

    @Test
    public void withNonZeroTopInsets_paddingIsApplied() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class).create().get();
        View fragmentContainer = activity.findViewById(R.id.fragment_container);
        setupMocksForWindowInsets(0, 22, 0, 0);

        fragmentContainer.dispatchApplyWindowInsets(mWindowInsetsMock);
        ShadowLooper.idleMainLooper();

        assertThat(fragmentContainer.getPaddingLeft()).isEqualTo(0);
        assertThat(fragmentContainer.getPaddingTop()).isEqualTo(22);
        assertThat(fragmentContainer.getPaddingRight()).isEqualTo(0);
        assertThat(fragmentContainer.getPaddingBottom()).isEqualTo(0);
    }

    @Test
    public void withNonZeroBottomInsets_paddingIsApplied() {
        ProvisioningActivity activity =
                Robolectric.buildActivity(ProvisioningActivity.class).create().get();
        View fragmentContainer = activity.findViewById(R.id.fragment_container);
        setupMocksForWindowInsets(0, 0, 0, 20);

        fragmentContainer.dispatchApplyWindowInsets(mWindowInsetsMock);
        ShadowLooper.idleMainLooper();

        assertThat(fragmentContainer.getPaddingLeft()).isEqualTo(0);
        assertThat(fragmentContainer.getPaddingTop()).isEqualTo(0);
        assertThat(fragmentContainer.getPaddingRight()).isEqualTo(0);
        assertThat(fragmentContainer.getPaddingBottom()).isEqualTo(20);
    }

    private void setupMocksForWindowInsets(int left, int top, int right, int bottom) {
        when(mWindowInsetsMock.getInsets(WindowInsets.Type.systemBars()))
                .thenReturn(Insets.of(left, top, right, bottom));
    }
}
