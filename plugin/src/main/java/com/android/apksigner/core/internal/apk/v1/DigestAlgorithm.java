/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.apksigner.core.internal.apk.v1;

/**
 * Digest algorithm used with JAR signing (aka v1 signing scheme).
 */
public enum DigestAlgorithm {
    /** SHA-1 */
    SHA1("SHA-1"),

    /** SHA2-256 */
    SHA256("SHA-256");

    private final String mJcaMessageDigestAlgorithm;

    private DigestAlgorithm(String jcaMessageDigestAlgoritm) {
        mJcaMessageDigestAlgorithm = jcaMessageDigestAlgoritm;
    }

    /**
     * Returns the {@link java.security.MessageDigest} algorithm represented by this digest
     * algorithm.
     */
    String getJcaMessageDigestAlgorithm() {
        return mJcaMessageDigestAlgorithm;
    }
}
