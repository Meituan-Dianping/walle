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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignatureEncryptionAlgorithmFinder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultCMSSignatureEncryptionAlgorithmFinder;
import org.bouncycastle.cms.SignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import com.android.apksigner.core.internal.jar.ManifestWriter;
import com.android.apksigner.core.internal.jar.SignatureFileWriter;
import com.android.apksigner.core.internal.util.Pair;

/**
 * APK signer which uses JAR signing (aka v1 signing scheme).
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File">Signed JAR File</a>
 */
public abstract class V1SchemeSigner {

    public static final String MANIFEST_ENTRY_NAME = "META-INF/MANIFEST.MF";

    private static final Attributes.Name ATTRIBUTE_NAME_CREATED_BY =
            new Attributes.Name("Created-By");
    private static final String ATTRIBUTE_DEFALT_VALUE_CREATED_BY = "1.0 (Android apksigner)";
    private static final String ATTRIBUTE_VALUE_MANIFEST_VERSION = "1.0";
    private static final String ATTRIBUTE_VALUE_SIGNATURE_VERSION = "1.0";

    private static final Attributes.Name SF_ATTRIBUTE_NAME_ANDROID_APK_SIGNED_NAME =
            new Attributes.Name("X-Android-APK-Signed");

    /**
     * Signer configuration.
     */
    public static class SignerConfig {
        /** Name. */
        public String name;

        /** Private key. */
        public PrivateKey privateKey;

        /**
         * Certificates, with the first certificate containing the public key corresponding to
         * {@link #privateKey}.
         */
        public List<X509Certificate> certificates;

        /**
         * Digest algorithm used for the signature.
         */
        public DigestAlgorithm signatureDigestAlgorithm;

        /**
         * Digest algorithm used for digests of JAR entries and MANIFEST.MF.
         */
        public DigestAlgorithm contentDigestAlgorithm;
    }

    /** Hidden constructor to prevent instantiation. */
    private V1SchemeSigner() {}

    /**
     * Gets the JAR signing digest algorithm to be used for signing an APK using the provided key.
     *
     * @param minSdkVersion minimum API Level of the platform on which the APK may be installed (see
     *        AndroidManifest.xml minSdkVersion attribute)
     *
     * @throws InvalidKeyException if the provided key is not suitable for signing APKs using
     *         JAR signing (aka v1 signature scheme)
     */
    public static DigestAlgorithm getSuggestedSignatureDigestAlgorithm(
            PublicKey signingKey, int minSdkVersion) throws InvalidKeyException {
        String keyAlgorithm = signingKey.getAlgorithm();
        if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            // Prior to API Level 18, only SHA-1 can be used with RSA.
            if (minSdkVersion < 18) {
                return DigestAlgorithm.SHA1;
            }
            return DigestAlgorithm.SHA256;
        } else if ("DSA".equalsIgnoreCase(keyAlgorithm)) {
            // Prior to API Level 21, only SHA-1 can be used with DSA
            if (minSdkVersion < 21) {
                return DigestAlgorithm.SHA1;
            } else {
                return DigestAlgorithm.SHA256;
            }
        } else if ("EC".equalsIgnoreCase(keyAlgorithm)) {
            if (minSdkVersion < 18) {
                throw new InvalidKeyException(
                        "ECDSA signatures only supported for minSdkVersion 18 and higher");
            }
            // Prior to API Level 21, only SHA-1 can be used with ECDSA
            if (minSdkVersion < 21) {
                return DigestAlgorithm.SHA1;
            } else {
                return DigestAlgorithm.SHA256;
            }
        } else {
            throw new InvalidKeyException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    /**
     * Returns the JAR signing digest algorithm to be used for JAR entry digests.
     *
     * @param minSdkVersion minimum API Level of the platform on which the APK may be installed (see
     *        AndroidManifest.xml minSdkVersion attribute)
     */
    public static DigestAlgorithm getSuggestedContentDigestAlgorithm(int minSdkVersion) {
        return (minSdkVersion >= 18) ? DigestAlgorithm.SHA256 : DigestAlgorithm.SHA1;
    }

    /**
     * Returns a new {@link MessageDigest} instance corresponding to the provided digest algorithm.
     */
    public static MessageDigest getMessageDigestInstance(DigestAlgorithm digestAlgorithm) {
        String jcaAlgorithm = digestAlgorithm.getJcaMessageDigestAlgorithm();
        try {
            return MessageDigest.getInstance(jcaAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to obtain " + jcaAlgorithm + " MessageDigest", e);
        }
    }

    /**
     * Returns the JCA {@link MessageDigest} algorithm corresponding to the provided digest
     * algorithm.
     */
    public static String getJcaMessageDigestAlgorithm(DigestAlgorithm digestAlgorithm) {
        return digestAlgorithm.getJcaMessageDigestAlgorithm();
    }

    /**
     * Returns {@code true} if the provided JAR entry must be mentioned in signed JAR archive's
     * manifest.
     */
    public static boolean isJarEntryDigestNeededInManifest(String entryName) {
        // See https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File

        // Entries outside of META-INF must be listed in the manifest.
        if (!entryName.startsWith("META-INF/")) {
            return true;
        }
        // Entries in subdirectories of META-INF must be listed in the manifest.
        if (entryName.indexOf('/', "META-INF/".length()) != -1) {
            return true;
        }

        // Ignored file names (case-insensitive) in META-INF directory:
        //   MANIFEST.MF
        //   *.SF
        //   *.RSA
        //   *.DSA
        //   *.EC
        //   SIG-*
        String fileNameLowerCase =
                entryName.substring("META-INF/".length()).toLowerCase(Locale.US);
        if (("manifest.mf".equals(fileNameLowerCase))
                || (fileNameLowerCase.endsWith(".sf"))
                || (fileNameLowerCase.endsWith(".rsa"))
                || (fileNameLowerCase.endsWith(".dsa"))
                || (fileNameLowerCase.endsWith(".ec"))
                || (fileNameLowerCase.startsWith("sig-"))) {
            return false;
        }
        return true;
    }

    /**
     * Signs the provided APK using JAR signing (aka v1 signature scheme) and returns the list of
     * JAR entries which need to be added to the APK as part of the signature.
     *
     * @param signerConfigs signer configurations, one for each signer. At least one signer config
     *        must be provided.
     *
     * @throws InvalidKeyException if a signing key is not suitable for this signature scheme or
     *         cannot be used in general
     * @throws SignatureException if an error occurs when computing digests of generating
     *         signatures
     */
    public static List<Pair<String, byte[]>> sign(
            List<SignerConfig> signerConfigs,
            DigestAlgorithm jarEntryDigestAlgorithm,
            Map<String, byte[]> jarEntryDigests,
            List<Integer> apkSigningSchemeIds,
            byte[] sourceManifestBytes)
                    throws InvalidKeyException, CertificateEncodingException, SignatureException {
        if (signerConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one signer config must be provided");
        }
        OutputManifestFile manifest =
                generateManifestFile(jarEntryDigestAlgorithm, jarEntryDigests, sourceManifestBytes);

        return signManifest(signerConfigs, jarEntryDigestAlgorithm, apkSigningSchemeIds, manifest);
    }

    /**
     * Signs the provided APK using JAR signing (aka v1 signature scheme) and returns the list of
     * JAR entries which need to be added to the APK as part of the signature.
     *
     * @param signerConfigs signer configurations, one for each signer. At least one signer config
     *        must be provided.
     *
     * @throws InvalidKeyException if a signing key is not suitable for this signature scheme or
     *         cannot be used in general
     * @throws SignatureException if an error occurs when computing digests of generating
     *         signatures
     */
    public static List<Pair<String, byte[]>> signManifest(
            List<SignerConfig> signerConfigs,
            DigestAlgorithm digestAlgorithm,
            List<Integer> apkSigningSchemeIds,
            OutputManifestFile manifest)
                    throws InvalidKeyException, CertificateEncodingException, SignatureException {
        if (signerConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one signer config must be provided");
        }

        // For each signer output .SF and .(RSA|DSA|EC) file, then output MANIFEST.MF.
        List<Pair<String, byte[]>> signatureJarEntries =
                new ArrayList<>(2 * signerConfigs.size() + 1);
        byte[] sfBytes =
                generateSignatureFile(apkSigningSchemeIds, digestAlgorithm, manifest);
        for (SignerConfig signerConfig : signerConfigs) {
            String signerName = signerConfig.name;
            byte[] signatureBlock;
            try {
                signatureBlock = generateSignatureBlock(signerConfig, sfBytes);
            } catch (InvalidKeyException e) {
                throw new InvalidKeyException(
                        "Failed to sign using signer \"" + signerName + "\"", e);
            } catch (CertificateEncodingException e) {
                throw new CertificateEncodingException(
                        "Failed to sign using signer \"" + signerName + "\"", e);
            } catch (SignatureException e) {
                throw new SignatureException(
                        "Failed to sign using signer \"" + signerName + "\"", e);
            }
            signatureJarEntries.add(Pair.of("META-INF/" + signerName + ".SF", sfBytes));
            PublicKey publicKey = signerConfig.certificates.get(0).getPublicKey();
            String signatureBlockFileName =
                    "META-INF/" + signerName + "."
                            + publicKey.getAlgorithm().toUpperCase(Locale.US);
            signatureJarEntries.add(
                    Pair.of(signatureBlockFileName, signatureBlock));
        }
        signatureJarEntries.add(Pair.of(MANIFEST_ENTRY_NAME, manifest.contents));
        return signatureJarEntries;
    }

    /**
     * Returns the names of JAR entries which this signer will produce as part of v1 signature.
     */
    public static Set<String> getOutputEntryNames(List<SignerConfig> signerConfigs) {
        Set<String> result = new HashSet<>(2 * signerConfigs.size() + 1);
        for (SignerConfig signerConfig : signerConfigs) {
            String signerName = signerConfig.name;
            result.add("META-INF/" + signerName + ".SF");
            PublicKey publicKey = signerConfig.certificates.get(0).getPublicKey();
            String signatureBlockFileName =
                    "META-INF/" + signerName + "."
                            + publicKey.getAlgorithm().toUpperCase(Locale.US);
            result.add(signatureBlockFileName);
        }
        result.add(MANIFEST_ENTRY_NAME);
        return result;
    }

    /**
     * Generated and returns the {@code META-INF/MANIFEST.MF} file based on the provided (optional)
     * input {@code MANIFEST.MF} and digests of JAR entries covered by the manifest.
     */
    public static OutputManifestFile generateManifestFile(
            DigestAlgorithm jarEntryDigestAlgorithm,
            Map<String, byte[]> jarEntryDigests,
            byte[] sourceManifestBytes) {
        Manifest sourceManifest = null;
        if (sourceManifestBytes != null) {
            try {
                sourceManifest = new Manifest(new ByteArrayInputStream(sourceManifestBytes));
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to parse source MANIFEST.MF", e);
            }
        }
        ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
        Attributes mainAttrs = new Attributes();
        // Copy the main section from the source manifest (if provided). Otherwise use defaults.
        if (sourceManifest != null) {
            mainAttrs.putAll(sourceManifest.getMainAttributes());
        } else {
            mainAttrs.put(Attributes.Name.MANIFEST_VERSION, ATTRIBUTE_VALUE_MANIFEST_VERSION);
            mainAttrs.put(ATTRIBUTE_NAME_CREATED_BY, ATTRIBUTE_DEFALT_VALUE_CREATED_BY);
        }

        try {
            ManifestWriter.writeMainSection(manifestOut, mainAttrs);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write in-memory MANIFEST.MF", e);
        }

        List<String> sortedEntryNames = new ArrayList<>(jarEntryDigests.keySet());
        Collections.sort(sortedEntryNames);
        SortedMap<String, byte[]> invidualSectionsContents = new TreeMap<>();
        String entryDigestAttributeName = getEntryDigestAttributeName(jarEntryDigestAlgorithm);
        for (String entryName : sortedEntryNames) {
            byte[] entryDigest = jarEntryDigests.get(entryName);
            Attributes entryAttrs = new Attributes();
            entryAttrs.putValue(
                    entryDigestAttributeName,
                    Base64.getEncoder().encodeToString(entryDigest));
            ByteArrayOutputStream sectionOut = new ByteArrayOutputStream();
            byte[] sectionBytes;
            try {
                ManifestWriter.writeIndividualSection(sectionOut, entryName, entryAttrs);
                sectionBytes = sectionOut.toByteArray();
                manifestOut.write(sectionBytes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write in-memory MANIFEST.MF", e);
            }
            invidualSectionsContents.put(entryName, sectionBytes);
        }

        OutputManifestFile result = new OutputManifestFile();
        result.contents = manifestOut.toByteArray();
        result.mainSectionAttributes = mainAttrs;
        result.individualSectionsContents = invidualSectionsContents;
        return result;
    }

    public static class OutputManifestFile {
        public byte[] contents;
        public SortedMap<String, byte[]> individualSectionsContents;
        public Attributes mainSectionAttributes;
    }

    private static byte[] generateSignatureFile(
            List<Integer> apkSignatureSchemeIds,
            DigestAlgorithm manifestDigestAlgorithm,
            OutputManifestFile manifest) {
        Manifest sf = new Manifest();
        Attributes mainAttrs = sf.getMainAttributes();
        mainAttrs.put(Attributes.Name.SIGNATURE_VERSION, ATTRIBUTE_VALUE_SIGNATURE_VERSION);
        mainAttrs.put(ATTRIBUTE_NAME_CREATED_BY, ATTRIBUTE_DEFALT_VALUE_CREATED_BY);
        if (!apkSignatureSchemeIds.isEmpty()) {
            // Add APK Signature Scheme v2 (and newer) signature stripping protection.
            // This attribute indicates that this APK is supposed to have been signed using one or
            // more APK-specific signature schemes in addition to the standard JAR signature scheme
            // used by this code. APK signature verifier should reject the APK if it does not
            // contain a signature for the signature scheme the verifier prefers out of this set.
            StringBuilder attrValue = new StringBuilder();
            for (int id : apkSignatureSchemeIds) {
                if (attrValue.length() > 0) {
                    attrValue.append(", ");
                }
                attrValue.append(String.valueOf(id));
            }
            mainAttrs.put(
                    SF_ATTRIBUTE_NAME_ANDROID_APK_SIGNED_NAME,
                    attrValue.toString());
        }

        // Add main attribute containing the digest of MANIFEST.MF.
        MessageDigest md = getMessageDigestInstance(manifestDigestAlgorithm);
        mainAttrs.putValue(
                getManifestDigestAttributeName(manifestDigestAlgorithm),
                Base64.getEncoder().encodeToString(md.digest(manifest.contents)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            SignatureFileWriter.writeMainSection(out, mainAttrs);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write in-memory .SF file", e);
        }
        String entryDigestAttributeName = getEntryDigestAttributeName(manifestDigestAlgorithm);
        for (Map.Entry<String, byte[]> manifestSection
                : manifest.individualSectionsContents.entrySet()) {
            String sectionName = manifestSection.getKey();
            byte[] sectionContents = manifestSection.getValue();
            byte[] sectionDigest = md.digest(sectionContents);
            Attributes attrs = new Attributes();
            attrs.putValue(
                    entryDigestAttributeName,
                    Base64.getEncoder().encodeToString(sectionDigest));

            try {
                SignatureFileWriter.writeIndividualSection(out, sectionName, attrs);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write in-memory .SF file", e);
            }
        }

        // A bug in the java.util.jar implementation of Android platforms up to version 1.6 will
        // cause a spurious IOException to be thrown if the length of the signature file is a
        // multiple of 1024 bytes. As a workaround, add an extra CRLF in this case.
        if ((out.size() > 0) && ((out.size() % 1024) == 0)) {
            try {
                SignatureFileWriter.writeSectionDelimiter(out);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write to ByteArrayOutputStream", e);
            }
        }

        return out.toByteArray();
    }

    private static byte[] generateSignatureBlock(
            SignerConfig signerConfig, byte[] signatureFileBytes)
                    throws InvalidKeyException, CertificateEncodingException, SignatureException {
        JcaCertStore certs = new JcaCertStore(signerConfig.certificates);
        X509Certificate signerCert = signerConfig.certificates.get(0);
        String jcaSignatureAlgorithm =
                getJcaSignatureAlgorithm(
                        signerCert.getPublicKey(), signerConfig.signatureDigestAlgorithm);
        try {
            ContentSigner signer =
                    new JcaContentSignerBuilder(jcaSignatureAlgorithm)
                    .build(signerConfig.privateKey);
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(
                    new SignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().build(),
                            SignerInfoSignatureAlgorithmFinder.INSTANCE)
                            .setDirectSignature(true)
                            .build(signer, new JcaX509CertificateHolder(signerCert)));
            gen.addCertificates(certs);

            CMSSignedData sigData =
                    gen.generate(new CMSProcessableByteArray(signatureFileBytes), false);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ASN1InputStream asn1 = new ASN1InputStream(sigData.getEncoded())) {
                DEROutputStream dos = new DEROutputStream(out);
                dos.writeObject(asn1.readObject());
            }
            return out.toByteArray();
        } catch (OperatorCreationException | CMSException | IOException e) {
            throw new SignatureException("Failed to generate signature", e);
        }
    }

    /**
     * Chooser of SignatureAlgorithm for PKCS #7 CMS SignerInfo.
     */
    private static class SignerInfoSignatureAlgorithmFinder
            implements CMSSignatureEncryptionAlgorithmFinder {
        private static final SignerInfoSignatureAlgorithmFinder INSTANCE =
                new SignerInfoSignatureAlgorithmFinder();

        private static final AlgorithmIdentifier DSA =
                new AlgorithmIdentifier(X9ObjectIdentifiers.id_dsa, DERNull.INSTANCE);

        private final CMSSignatureEncryptionAlgorithmFinder mDefault =
                new DefaultCMSSignatureEncryptionAlgorithmFinder();

        @Override
        public AlgorithmIdentifier findEncryptionAlgorithm(AlgorithmIdentifier id) {
            // Use the default chooser, but replace dsaWithSha1 with dsa. This is because "dsa" is
            // accepted by any Android platform whereas "dsaWithSha1" is accepted only since
            // API Level 9.
            id = mDefault.findEncryptionAlgorithm(id);
            if (id != null) {
                ASN1ObjectIdentifier oid = id.getAlgorithm();
                if (X9ObjectIdentifiers.id_dsa_with_sha1.equals(oid)) {
                    return DSA;
                }
            }

            return id;
        }
    }

    private static String getEntryDigestAttributeName(DigestAlgorithm digestAlgorithm) {
        switch (digestAlgorithm) {
            case SHA1:
                return "SHA1-Digest";
            case SHA256:
                return "SHA-256-Digest";
            default:
                throw new IllegalArgumentException(
                        "Unexpected content digest algorithm: " + digestAlgorithm);
        }
    }

    private static String getManifestDigestAttributeName(DigestAlgorithm digestAlgorithm) {
        switch (digestAlgorithm) {
            case SHA1:
                return "SHA1-Digest-Manifest";
            case SHA256:
                return "SHA-256-Digest-Manifest";
            default:
                throw new IllegalArgumentException(
                        "Unexpected content digest algorithm: " + digestAlgorithm);
        }
    }

    private static String getJcaSignatureAlgorithm(
            PublicKey publicKey, DigestAlgorithm digestAlgorithm) throws InvalidKeyException {
        String keyAlgorithm = publicKey.getAlgorithm();
        String digestPrefixForSigAlg;
        switch (digestAlgorithm) {
            case SHA1:
                digestPrefixForSigAlg = "SHA1";
                break;
            case SHA256:
                digestPrefixForSigAlg = "SHA256";
                break;
            default:
                throw new IllegalArgumentException(
                        "Unexpected digest algorithm: " + digestAlgorithm);
        }
        if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            return digestPrefixForSigAlg + "withRSA";
        } else if ("DSA".equalsIgnoreCase(keyAlgorithm)) {
            return digestPrefixForSigAlg + "withDSA";
        } else if ("EC".equalsIgnoreCase(keyAlgorithm)) {
            return digestPrefixForSigAlg + "withECDSA";
        } else {
            throw new InvalidKeyException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }
}
