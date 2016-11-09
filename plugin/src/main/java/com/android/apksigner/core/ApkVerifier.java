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

package com.android.apksigner.core;

import com.android.apksigner.core.apk.ApkUtils;
import com.android.apksigner.core.internal.apk.v2.ContentDigestAlgorithm;
import com.android.apksigner.core.internal.apk.v2.SignatureAlgorithm;
import com.android.apksigner.core.internal.apk.v2.V2SchemeVerifier;
import com.android.apksigner.core.util.DataSource;
import com.android.apksigner.core.zip.ZipFormatException;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * APK signature verifier which mimics the behavior of the Android platform.
 *
 * <p>The verifier is designed to closely mimic the behavior of Android platforms. This is to enable
 * the verifier to be used for checking whether an APK's signatures will verify on Android.
 */
public class ApkVerifier {

    /**
     * Verifies the APK's signatures and returns the result of verification. The APK can be
     * considered verified iff the result's {@link Result#isVerified()} returns {@code true}.
     * The verification result also includes errors, warnings, and information about signers.
     *
     * @param apk APK file contents
     * @param minSdkVersion API Level of the oldest Android platform on which the APK's signatures
     *        may need to be verified
     *
     * @throws IOException if an I/O error is encountered while reading the APK
     * @throws ZipFormatException if the APK is malformed at ZIP format level
     */
    public Result verify(DataSource apk, int minSdkVersion) throws IOException, ZipFormatException {
        ApkUtils.ZipSections zipSections = ApkUtils.findZipSections(apk);

        // Attempt to verify the APK using APK Signature Scheme v2
        Result result = new Result();
        try {
            V2SchemeVerifier.Result v2Result = V2SchemeVerifier.verify(apk, zipSections);
            result.mergeFrom(v2Result);
        } catch (V2SchemeVerifier.SignatureNotFoundException ignored) {}
        if (result.containsErrors()) {
            return result;
        }

        // TODO: Verify JAR signature if necessary
        if (!result.isVerifiedUsingV2Scheme()) {
            return result;
        }

        // Verified
        result.setVerified();
        for (Result.V2SchemeSignerInfo signerInfo : result.getV2SchemeSigners()) {
            result.addSignerCertificate(signerInfo.getCertificate());
        }

        return result;
    }

    /**
     * Result of verifying an APKs signatures. The APK can be considered verified iff
     * {@link #isVerified()} returns {@code true}.
     */
    public static class Result {
        private final List<IssueWithParams> mErrors = new ArrayList<>();
        private final List<IssueWithParams> mWarnings = new ArrayList<>();
        private final List<X509Certificate> mSignerCerts = new ArrayList<>();
        private final List<V2SchemeSignerInfo> mV2SchemeSigners = new ArrayList<>();

        private boolean mVerified;
        private boolean mVerifiedUsingV2Scheme;

        /**
         * Returns {@code true} if the APK's signatures verified.
         */
        public boolean isVerified() {
            return mVerified;
        }

        private void setVerified() {
            mVerified = true;
        }

        /**
         * Returns {@code true} if the APK's APK Signature Scheme v2 signatures verified.
         */
        public boolean isVerifiedUsingV2Scheme() {
            return mVerifiedUsingV2Scheme;
        }

        /**
         * Returns the verified signers' certificates, one per signer.
         */
        public List<X509Certificate> getSignerCertificates() {
            return mSignerCerts;
        }

        private void addSignerCertificate(X509Certificate cert) {
            mSignerCerts.add(cert);
        }

        /**
         * Returns information about APK Signature Scheme v2 signers associated with the APK's
         * signature.
         */
        public List<V2SchemeSignerInfo> getV2SchemeSigners() {
            return mV2SchemeSigners;
        }

        /**
         * Returns errors encountered while verifying the APK's signatures.
         */
        public List<IssueWithParams> getErrors() {
            return mErrors;
        }

        /**
         * Returns warnings encountered while verifying the APK's signatures.
         */
        public List<IssueWithParams> getWarnings() {
            return mWarnings;
        }

        private void mergeFrom(V2SchemeVerifier.Result source) {
            mVerifiedUsingV2Scheme = source.verified;
            mErrors.addAll(source.getErrors());
            mWarnings.addAll(source.getWarnings());
            for (V2SchemeVerifier.Result.SignerInfo signer : source.signers) {
                mV2SchemeSigners.add(new V2SchemeSignerInfo(signer));
            }
        }

        /**
         * Returns {@code true} if an error was encountered while verifying the APK. Any error
         * prevents the APK from being considered verified.
         */
        public boolean containsErrors() {
            if (!mErrors.isEmpty()) {
                return true;
            }
            if (!mV2SchemeSigners.isEmpty()) {
                for (V2SchemeSignerInfo signer : mV2SchemeSigners) {
                    if (signer.containsErrors()) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Information about an APK Signature Scheme v2 signer associated with the APK's signature.
         */
        public static class V2SchemeSignerInfo {
            private final int mIndex;
            private final List<X509Certificate> mCerts;

            private final List<IssueWithParams> mErrors;
            private final List<IssueWithParams> mWarnings;

            private V2SchemeSignerInfo(V2SchemeVerifier.Result.SignerInfo result) {
                mIndex = result.index;
                mCerts = result.certs;
                mErrors = result.getErrors();
                mWarnings = result.getWarnings();
            }

            /**
             * Returns this signer's {@code 0}-based index in the list of signers contained in the
             * APK's APK Signature Scheme v2 signature.
             */
            public int getIndex() {
                return mIndex;
            }

            /**
             * Returns this signer's signing certificate or {@code null} if not available. The
             * certificate is guaranteed to be available if no errors were encountered during
             * verification (see {@link #containsErrors()}.
             *
             * <p>This certificate contains the signer's public key.
             */
            public X509Certificate getCertificate() {
                return mCerts.isEmpty() ? null : mCerts.get(0);
            }

            /**
             * Returns this signer's certificates. The first certificate is for the signer's public
             * key. An empty list may be returned if an error was encountered during verification
             * (see {@link #containsErrors()}).
             */
            public List<X509Certificate> getCertificates() {
                return mCerts;
            }

            public boolean containsErrors() {
                return !mErrors.isEmpty();
            }

            public List<IssueWithParams> getErrors() {
                return mErrors;
            }

            public List<IssueWithParams> getWarnings() {
                return mWarnings;
            }
        }
    }

    /**
     * Error or warning encountered while verifying an APK's signatures.
     */
    public static enum Issue {

        /**
         * Failed to parse the list of signers contained in the APK Signature Scheme v2 signature.
         */
        V2_SIG_MALFORMED_SIGNERS("Malformed list of signers"),

        /**
         * Failed to parse this signer's signer block contained in the APK Signature Scheme v2
         * signature.
         */
        V2_SIG_MALFORMED_SIGNER("Malformed signer block"),

        /**
         * Public key embedded in the APK Signature Scheme v2 signature of this signer could not be
         * parsed.
         *
         * <ul>
         * <li>Parameter 1: error details ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_PUBLIC_KEY("Malformed public key: %1$s"),

        /**
         * This APK Signature Scheme v2 signer's certificate could not be parsed.
         *
         * <ul>
         * <li>Parameter 1: index ({@code 0}-based) of the certificate in the signer's list of
         *     certificates ({@code Integer})</li>
         * <li>Parameter 2: sequence number ({@code 1}-based) of the certificate in the signer's
         *     list of certificates ({@code Integer})</li>
         * <li>Parameter 3: error details ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_CERTIFICATE("Malformed certificate #%2$d: %3$s"),

        /**
         * Failed to parse this signer's signature record contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_SIGNATURE("Malformed APK Signature Scheme v2 signature record #%1$d"),

        /**
         * Failed to parse this signer's digest record contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: record number (first record is {@code 1}) ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_DIGEST("Malformed APK Signature Scheme v2 digest record #%1$d"),

        /**
         * This APK Signature Scheme v2 signer contains a malformed additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute number (first attribute is {@code 1}) {@code Integer})</li>
         * </ul>
         */
        V2_SIG_MALFORMED_ADDITIONAL_ATTRIBUTE("Malformed additional attribute #%1$d"),

        /**
         * APK Signature Scheme v2 signature contains no signers.
         */
        V2_SIG_NO_SIGNERS("No signers in APK Signature Scheme v2 signature"),

        /**
         * This APK Signature Scheme v2 signer contains a signature produced using an unknown
         * algorithm.
         *
         * <ul>
         * <li>Parameter 1: algorithm ID ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_UNKNOWN_SIG_ALGORITHM("Unknown signature algorithm: %1$#x"),

        /**
         * This APK Signature Scheme v2 signer contains an unknown additional attribute.
         *
         * <ul>
         * <li>Parameter 1: attribute ID ({@code Integer})</li>
         * </ul>
         */
        V2_SIG_UNKNOWN_ADDITIONAL_ATTRIBUTE("Unknown additional attribute: ID %1$#x"),

        /**
         * An exception was encountered while verifying APK Signature Scheme v2 signature of this
         * signer.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * <li>Parameter 2: exception ({@code Throwable})</li>
         * </ul>
         */
        V2_SIG_VERIFY_EXCEPTION("Failed to verify %1$s signature: %2$s"),

        /**
         * APK Signature Scheme v2 signature over this signer's signed-data block did not verify.
         *
         * <ul>
         * <li>Parameter 1: signature algorithm ({@link SignatureAlgorithm})</li>
         * </ul>
         */
        V2_SIG_DID_NOT_VERIFY("%1$s signature over signed-data did not verify"),

        /**
         * This APK Signature Scheme v2 signer offers no signatures.
         */
        V2_SIG_NO_SIGNATURES("No signatures"),

        /**
         * This APK Signature Scheme v2 signer offers signatures but none of them are supported.
         */
        V2_SIG_NO_SUPPORTED_SIGNATURES("No supported signatures"),

        /**
         * This APK Signature Scheme v2 signer offers no certificates.
         */
        V2_SIG_NO_CERTIFICATES("No certificates"),

        /**
         * This APK Signature Scheme v2 signer's public key listed in the signer's certificate does
         * not match the public key listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: hex-encoded public key from certificate ({@code String})</li>
         * <li>Parameter 2: hex-encoded public key from signatures record ({@code String})</li>
         * </ul>
         */
        V2_SIG_PUBLIC_KEY_MISMATCH_BETWEEN_CERTIFICATE_AND_SIGNATURES_RECORD(
                "Public key mismatch between certificate and signature record: <%1$s> vs <%2$s>"),

        /**
         * This APK Signature Scheme v2 signer's signature algorithms listed in the signatures
         * record do not match the signature algorithms listed in the signatures record.
         *
         * <ul>
         * <li>Parameter 1: signature algorithms from signatures record ({@code List<Integer>})</li>
         * <li>Parameter 2: signature algorithms from digests record ({@code List<Integer>})</li>
         * </ul>
         */
        V2_SIG_SIG_ALG_MISMATCH_BETWEEN_SIGNATURES_AND_DIGESTS_RECORDS(
                "Signature algorithms mismatch between signatures and digests records"
                        + ": %1$s vs %2$s"),

        /**
         * The APK's digest does not match the digest contained in the APK Signature Scheme v2
         * signature.
         *
         * <ul>
         * <li>Parameter 1: content digest algorithm ({@link ContentDigestAlgorithm})</li>
         * <li>Parameter 2: hex-encoded expected digest of the APK ({@code String})</li>
         * <li>Parameter 3: hex-encoded actual digest of the APK ({@code String})</li>
         * </ul>
         */
        V2_SIG_APK_DIGEST_DID_NOT_VERIFY(
                "APK integrity check failed. %1$s digest mismatch."
                        + " Expected: <%2$s>, actual: <%3$s>"),

        /**
         * APK Signing Block contains an unknown entry.
         *
         * <ul>
         * <li>Parameter 1: entry ID ({@code Integer})</li>
         * </ul>
         */
        APK_SIG_BLOCK_UNKNOWN_ENTRY_ID("APK Signing Block contains unknown entry: ID %1$#x");

        private final String mFormat;

        private Issue(String format) {
            mFormat = format;
        }

        /**
         * Returns the format string suitable for combining the parameters of this issue into a
         * readable string. See {@link java.util.Formatter} for format.
         */
        private String getFormat() {
            return mFormat;
        }
    }

    /**
     * {@link Issue} with associated parameters. {@link #toString()} produces a readable formatted
     * form.
     */
    public static class IssueWithParams {
        private final Issue mIssue;
        private final Object[] mParams;

        /**
         * Constructs a new {@code IssueWithParams} of the specified type and with provided
         * parameters.
         */
        public IssueWithParams(Issue issue, Object[] params) {
            mIssue = issue;
            mParams = params;
        }

        /**
         * Returns the type of this issue.
         */
        public Issue getIssue() {
            return mIssue;
        }

        /**
         * Returns the parameters of this issue.
         */
        public Object[] getParams() {
            return mParams.clone();
        }

        /**
         * Returns a readable form of this issue.
         */
        @Override
        public String toString() {
            return String.format(mIssue.getFormat(), mParams);
        }
    }
}
