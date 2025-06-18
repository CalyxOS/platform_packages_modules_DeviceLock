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

import static com.google.common.base.Preconditions.checkNotNull;

import android.graphics.Insets;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentContainerView;

import com.android.devicelockcontroller.R;

/** The first activity displayed during the provisioning flow. */
public final class LandingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.landing_activity);

        FragmentContainerView fragmentContainerView = findViewById(R.id.fragment_container);
        checkNotNull(fragmentContainerView);
        fragmentContainerView.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                    // Only set the top and bottom padding if top/bottom insets are non-zero
                    if (systemBars.top != 0 || systemBars.bottom != 0) {
                        view.setPadding(0, systemBars.top, 0, systemBars.bottom);
                    }
                    return insets;
                });

        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, new ProvisionInfoFragment())
                    .commit();
        }
    }
}
