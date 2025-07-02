/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.devicelockcontroller;

/**
 * Interface that provides the feature flag values for feature flag guarding, A/B testing and other
 * experimentation capabilities
 */
public interface FeatureFlagProvider {

    /**
     * Returns the feature flag value that enables IMEI hardening binding feature
     */
    boolean isImeiHardeningRegistrationEnabled();

}
