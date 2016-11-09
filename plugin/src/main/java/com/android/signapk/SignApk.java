///*
// * Copyright (C) 2008 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.android.signapk;
//
//import org.bouncycastle.asn1.ASN1InputStream;
//import org.bouncycastle.asn1.ASN1ObjectIdentifier;
//import org.bouncycastle.asn1.DEROutputStream;
//import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
//import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
//import org.bouncycastle.cert.jcajce.JcaCertStore;
//import org.bouncycastle.cms.CMSException;
//import org.bouncycastle.cms.CMSSignedData;
//import org.bouncycastle.cms.CMSSignedDataGenerator;
//import org.bouncycastle.cms.CMSTypedData;
//import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
//import org.bouncycastle.jce.provider.BouncyCastleProvider;
//import org.bouncycastle.operator.ContentSigner;
//import org.bouncycastle.operator.OperatorCreationException;
//import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
//import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
//import org.conscrypt.OpenSSLProvider;
//
//import com.android.apksigner.core.ApkSignerEngine;
//import com.android.apksigner.core.DefaultApkSignerEngine;
//import com.android.apksigner.core.apk.ApkUtils;
//import com.android.apksigner.core.util.DataSink;
//import com.android.apksigner.core.util.DataSources;
//import com.android.apksigner.core.zip.ZipFormatException;
//
//import java.io.Console;
//import java.io.BufferedReader;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.DataInputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.FilterOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.lang.reflect.Constructor;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.security.GeneralSecurityException;
//import java.security.Key;
//import java.security.KeyFactory;
//import java.security.PrivateKey;
//import java.security.Provider;
//import java.security.Security;
//import java.security.cert.CertificateEncodingException;
//import java.security.cert.CertificateFactory;
//import java.security.cert.X509Certificate;
//import java.security.spec.InvalidKeySpecException;
//import java.security.spec.PKCS8EncodedKeySpec;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Enumeration;
//import java.util.List;
//import java.util.Locale;
//import java.util.TimeZone;
//import java.util.jar.JarEntry;
//import java.util.jar.JarFile;
//import java.util.jar.JarOutputStream;
//import java.util.regex.Pattern;
//
//import javax.crypto.Cipher;
//import javax.crypto.EncryptedPrivateKeyInfo;
//import javax.crypto.SecretKeyFactory;
//import javax.crypto.spec.PBEKeySpec;
//
///**
// * HISTORICAL NOTE:
// *
// * Prior to the keylimepie release, SignApk ignored the signature
// * algorithm specified in the certificate and always used SHA1withRSA.
// *
// * Starting with JB-MR2, the platform supports SHA256withRSA, so we use
// * the signature algorithm in the certificate to select which to use
// * (SHA256withRSA or SHA1withRSA). Also in JB-MR2, EC keys are supported.
// *
// * Because there are old keys still in use whose certificate actually
// * says "MD5withRSA", we treat these as though they say "SHA1withRSA"
// * for compatibility with older releases.  This can be changed by
// * altering the getAlgorithm() function below.
// */
//
//
///**
// * Command line tool to sign JAR files (including APKs and OTA updates) in a way
// * compatible with the mincrypt verifier, using EC or RSA keys and SHA1 or
// * SHA-256 (see historical note). The tool can additionally sign APKs using
// * APK Signature Scheme v2.
// */
//class SignApk {
//    private static final String OTACERT_NAME = "META-INF/com/android/otacert";
//
//    /**
//     * Extensible data block/field header ID used for storing information about alignment of
//     * uncompressed entries as well as for aligning the entries's data. See ZIP appnote.txt section
//     * 4.5 Extensible data fields.
//     */
//    private static final short ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID = (short) 0xd935;
//
//    /**
//     * Minimum size (in bytes) of the extensible data block/field used for alignment of uncompressed
//     * entries.
//     */
//    private static final short ALIGNMENT_ZIP_EXTRA_DATA_FIELD_MIN_SIZE_BYTES = 6;
//
//    // bitmasks for which hash algorithms we need the manifest to include.
//    private static final int USE_SHA1 = 1;
//    private static final int USE_SHA256 = 2;
//
//    /**
//     * Returns the digest algorithm ID (one of {@code USE_SHA1} or {@code USE_SHA256}) to be used
//     * for signing an OTA update package using the private key corresponding to the provided
//     * certificate.
//     */
//    private static int getDigestAlgorithmForOta(X509Certificate cert) {
//        String sigAlg = cert.getSigAlgName().toUpperCase(Locale.US);
//        if ("SHA1WITHRSA".equals(sigAlg) || "MD5WITHRSA".equals(sigAlg)) {
//            // see "HISTORICAL NOTE" above.
//            return USE_SHA1;
//        } else if (sigAlg.startsWith("SHA256WITH")) {
//            return USE_SHA256;
//        } else {
//            throw new IllegalArgumentException("unsupported signature algorithm \"" + sigAlg +
//                                               "\" in cert [" + cert.getSubjectDN());
//        }
//    }
//
//    /**
//     * Returns the JCA {@link java.security.Signature} algorithm to be used for signing and OTA
//     * update package using the private key corresponding to the provided certificate and the
//     * provided digest algorithm (see {@code USE_SHA1} and {@code USE_SHA256} constants).
//     */
//    private static String getJcaSignatureAlgorithmForOta(
//            X509Certificate cert, int hash) {
//        String sigAlgDigestPrefix;
//        switch (hash) {
//            case USE_SHA1:
//                sigAlgDigestPrefix = "SHA1";
//                break;
//            case USE_SHA256:
//                sigAlgDigestPrefix = "SHA256";
//                break;
//            default:
//                throw new IllegalArgumentException("Unknown hash ID: " + hash);
//        }
//
//        String keyAlgorithm = cert.getPublicKey().getAlgorithm();
//        if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
//            return sigAlgDigestPrefix + "withRSA";
//        } else if ("EC".equalsIgnoreCase(keyAlgorithm)) {
//            return sigAlgDigestPrefix + "withECDSA";
//        } else {
//            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
//        }
//    }
//
//    private static X509Certificate readPublicKey(File file)
//        throws IOException, GeneralSecurityException {
//        FileInputStream input = new FileInputStream(file);
//        try {
//            CertificateFactory cf = CertificateFactory.getInstance("X.509");
//            return (X509Certificate) cf.generateCertificate(input);
//        } finally {
//            input.close();
//        }
//    }
//
//    /**
//     * If a console doesn't exist, reads the password from stdin
//     * If a console exists, reads the password from console and returns it as a string.
//     *
//     * @param keyFile The file containing the private key.  Used to prompt the user.
//     */
//    private static String readPassword(File keyFile) {
//        Console console;
//        char[] pwd;
//        if ((console = System.console()) == null) {
//            System.out.print("Enter password for " + keyFile + " (password will not be hidden): ");
//            System.out.flush();
//            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
//            try {
//                return stdin.readLine();
//            } catch (IOException ex) {
//                return null;
//            }
//        } else {
//            if ((pwd = console.readPassword("[%s]", "Enter password for " + keyFile)) != null) {
//                return String.valueOf(pwd);
//            } else {
//                return null;
//            }
//        }
//    }
//
//    /**
//     * Decrypt an encrypted PKCS#8 format private key.
//     *
//     * Based on ghstark's post on Aug 6, 2006 at
//     * http://forums.sun.com/thread.jspa?threadID=758133&messageID=4330949
//     *
//     * @param encryptedPrivateKey The raw data of the private key
//     * @param keyFile The file containing the private key
//     */
//    private static PKCS8EncodedKeySpec decryptPrivateKey(byte[] encryptedPrivateKey, File keyFile)
//        throws GeneralSecurityException {
//        EncryptedPrivateKeyInfo epkInfo;
//        try {
//            epkInfo = new EncryptedPrivateKeyInfo(encryptedPrivateKey);
//        } catch (IOException ex) {
//            // Probably not an encrypted key.
//            return null;
//        }
//
//        char[] password = readPassword(keyFile).toCharArray();
//
//        SecretKeyFactory skFactory = SecretKeyFactory.getInstance(epkInfo.getAlgName());
//        Key key = skFactory.generateSecret(new PBEKeySpec(password));
//
//        Cipher cipher = Cipher.getInstance(epkInfo.getAlgName());
//        cipher.init(Cipher.DECRYPT_MODE, key, epkInfo.getAlgParameters());
//
//        try {
//            return epkInfo.getKeySpec(cipher);
//        } catch (InvalidKeySpecException ex) {
//            System.err.println("signapk: Password for " + keyFile + " may be bad.");
//            throw ex;
//        }
//    }
//
//    /** Read a PKCS#8 format private key. */
//    private static PrivateKey readPrivateKey(File file)
//        throws IOException, GeneralSecurityException {
//        DataInputStream input = new DataInputStream(new FileInputStream(file));
//        try {
//            byte[] bytes = new byte[(int) file.length()];
//            input.read(bytes);
//
//            /* Check to see if this is in an EncryptedPrivateKeyInfo structure. */
//            PKCS8EncodedKeySpec spec = decryptPrivateKey(bytes, file);
//            if (spec == null) {
//                spec = new PKCS8EncodedKeySpec(bytes);
//            }
//
//            /*
//             * Now it's in a PKCS#8 PrivateKeyInfo structure. Read its Algorithm
//             * OID and use that to construct a KeyFactory.
//             */
//            PrivateKeyInfo pki;
//            try (ASN1InputStream bIn =
//                    new ASN1InputStream(new ByteArrayInputStream(spec.getEncoded()))) {
//                pki = PrivateKeyInfo.getInstance(bIn.readObject());
//            }
//            String algOid = pki.getPrivateKeyAlgorithm().getAlgorithm().getId();
//
//            return KeyFactory.getInstance(algOid).generatePrivate(spec);
//        } finally {
//            input.close();
//        }
//    }
//
//    /**
//     * Add a copy of the public key to the archive; this should
//     * exactly match one of the files in
//     * /system/etc/security/otacerts.zip on the device.  (The same
//     * cert can be extracted from the OTA update package's signature
//     * block but this is much easier to get at.)
//     */
//    private static void addOtacert(JarOutputStream outputJar,
//                                   File publicKeyFile,
//                                   long timestamp)
//        throws IOException {
//
//        JarEntry je = new JarEntry(OTACERT_NAME);
//        je.setTime(timestamp);
//        outputJar.putNextEntry(je);
//        FileInputStream input = new FileInputStream(publicKeyFile);
//        byte[] b = new byte[4096];
//        int read;
//        while ((read = input.read(b)) != -1) {
//            outputJar.write(b, 0, read);
//        }
//        input.close();
//    }
//
//
//    /** Sign data and write the digital signature to 'out'. */
//    private static void writeSignatureBlock(
//        CMSTypedData data, X509Certificate publicKey, PrivateKey privateKey, int hash,
//        OutputStream out)
//        throws IOException,
//               CertificateEncodingException,
//               OperatorCreationException,
//               CMSException {
//        ArrayList<X509Certificate> certList = new ArrayList<X509Certificate>(1);
//        certList.add(publicKey);
//        JcaCertStore certs = new JcaCertStore(certList);
//
//        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
//        ContentSigner signer =
//                new JcaContentSignerBuilder(
//                        getJcaSignatureAlgorithmForOta(publicKey, hash))
//                        .build(privateKey);
//        gen.addSignerInfoGenerator(
//            new JcaSignerInfoGeneratorBuilder(
//                new JcaDigestCalculatorProviderBuilder()
//                .build())
//            .setDirectSignature(true)
//            .build(signer, publicKey));
//        gen.addCertificates(certs);
//        CMSSignedData sigData = gen.generate(data, false);
//
//        try (ASN1InputStream asn1 = new ASN1InputStream(sigData.getEncoded())) {
//            DEROutputStream dos = new DEROutputStream(out);
//            dos.writeObject(asn1.readObject());
//        }
//    }
//
//    /**
//     * Adds ZIP entries which represent the v1 signature (JAR signature scheme).
//     */
//    private static void addV1Signature(
//            ApkSignerEngine apkSigner,
//            ApkSignerEngine.OutputJarSignatureRequest v1Signature,
//            JarOutputStream out,
//            long timestamp) throws IOException {
//        for (ApkSignerEngine.OutputJarSignatureRequest.JarEntry entry
//                : v1Signature.getAdditionalJarEntries()) {
//            String entryName = entry.getName();
//            JarEntry outEntry = new JarEntry(entryName);
//            outEntry.setTime(timestamp);
//            out.putNextEntry(outEntry);
//            byte[] entryData = entry.getData();
//            out.write(entryData);
//            ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest =
//                    apkSigner.outputJarEntry(entryName);
//            if (inspectEntryRequest != null) {
//                inspectEntryRequest.getDataSink().consume(entryData, 0, entryData.length);
//                inspectEntryRequest.done();
//            }
//        }
//    }
//
//    /**
//     * Copy all JAR entries from input to output. We set the modification times in the output to a
//     * fixed time, so as to reduce variation in the output file and make incremental OTAs more
//     * efficient.
//     */
//    private static void copyFiles(
//            JarFile in,
//            Pattern ignoredFilenamePattern,
//            ApkSignerEngine apkSigner,
//            JarOutputStream out,
//            long timestamp,
//            int defaultAlignment) throws IOException {
//        byte[] buffer = new byte[4096];
//        int num;
//
//        ArrayList<String> names = new ArrayList<String>();
//        for (Enumeration<JarEntry> e = in.entries(); e.hasMoreElements();) {
//            JarEntry entry = e.nextElement();
//            if (entry.isDirectory()) {
//                continue;
//            }
//            String entryName = entry.getName();
//            if ((ignoredFilenamePattern != null)
//                    && (ignoredFilenamePattern.matcher(entryName).matches())) {
//                continue;
//            }
//            names.add(entryName);
//        }
//        Collections.sort(names);
//
//        boolean firstEntry = true;
//        long offset = 0L;
//
//        // We do the copy in two passes -- first copying all the
//        // entries that are STORED, then copying all the entries that
//        // have any other compression flag (which in practice means
//        // DEFLATED).  This groups all the stored entries together at
//        // the start of the file and makes it easier to do alignment
//        // on them (since only stored entries are aligned).
//
//        List<String> remainingNames = new ArrayList<>(names.size());
//        for (String name : names) {
//            JarEntry inEntry = in.getJarEntry(name);
//            if (inEntry.getMethod() != JarEntry.STORED) {
//                // Defer outputting this entry until we're ready to output compressed entries.
//                remainingNames.add(name);
//                continue;
//            }
//
//            if (!shouldOutputApkEntry(apkSigner, in, inEntry, buffer)) {
//                continue;
//            }
//
//            // Preserve the STORED method of the input entry.
//            JarEntry outEntry = new JarEntry(inEntry);
//            outEntry.setTime(timestamp);
//            // Discard comment and extra fields of this entry to
//            // simplify alignment logic below and for consistency with
//            // how compressed entries are handled later.
//            outEntry.setComment(null);
//            outEntry.setExtra(null);
//
//            int alignment = getStoredEntryDataAlignment(name, defaultAlignment);
//            // Alignment of the entry's data is achieved by adding a data block to the entry's Local
//            // File Header extra field. The data block contains information about the alignment
//            // value and the necessary padding bytes (0x00) to achieve the alignment.  This works
//            // because the entry's data will be located immediately after the extra field.
//            // See ZIP APPNOTE.txt section "4.5 Extensible data fields" for details about the format
//            // of the extra field.
//
//            // 'offset' is the offset into the file at which we expect the entry's data to begin.
//            // This is the value we need to make a multiple of 'alignment'.
//            offset += JarFile.LOCHDR + outEntry.getName().length();
//            if (firstEntry) {
//                // The first entry in a jar file has an extra field of four bytes that you can't get
//                // rid of; any extra data you specify in the JarEntry is appended to these forced
//                // four bytes.  This is JAR_MAGIC in JarOutputStream; the bytes are 0xfeca0000.
//                // See http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6808540
//                // and http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4138619.
//                offset += 4;
//                firstEntry = false;
//            }
//            int extraPaddingSizeBytes = 0;
//            if (alignment > 0) {
//                long paddingStartOffset = offset + ALIGNMENT_ZIP_EXTRA_DATA_FIELD_MIN_SIZE_BYTES;
//                extraPaddingSizeBytes = alignment - (int) (paddingStartOffset % alignment);
//            }
//            byte[] extra =
//                    new byte[ALIGNMENT_ZIP_EXTRA_DATA_FIELD_MIN_SIZE_BYTES + extraPaddingSizeBytes];
//            ByteBuffer extraBuf = ByteBuffer.wrap(extra);
//            extraBuf.order(ByteOrder.LITTLE_ENDIAN);
//            extraBuf.putShort(ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID); // Header ID
//            extraBuf.putShort((short) (2 + extraPaddingSizeBytes)); // Data Size
//            extraBuf.putShort((short) alignment);
//            outEntry.setExtra(extra);
//            offset += extra.length;
//
//            out.putNextEntry(outEntry);
//            ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest =
//                    (apkSigner != null) ? apkSigner.outputJarEntry(name) : null;
//            DataSink entryDataSink =
//                    (inspectEntryRequest != null) ? inspectEntryRequest.getDataSink() : null;
//
//            try (InputStream data = in.getInputStream(inEntry)) {
//                while ((num = data.read(buffer)) > 0) {
//                    out.write(buffer, 0, num);
//                    if (entryDataSink != null) {
//                        entryDataSink.consume(buffer, 0, num);
//                    }
//                    offset += num;
//                }
//            }
//            out.flush();
//            if (inspectEntryRequest != null) {
//                inspectEntryRequest.done();
//            }
//        }
//
//        // Copy all the non-STORED entries.  We don't attempt to
//        // maintain the 'offset' variable past this point; we don't do
//        // alignment on these entries.
//
//        for (String name : remainingNames) {
//            JarEntry inEntry = in.getJarEntry(name);
//            if (!shouldOutputApkEntry(apkSigner, in, inEntry, buffer)) {
//                continue;
//            }
//
//            // Create a new entry so that the compressed len is recomputed.
//            JarEntry outEntry = new JarEntry(name);
//            outEntry.setTime(timestamp);
//            out.putNextEntry(outEntry);
//            ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest =
//                    (apkSigner != null) ? apkSigner.outputJarEntry(name) : null;
//            DataSink entryDataSink =
//                    (inspectEntryRequest != null) ? inspectEntryRequest.getDataSink() : null;
//
//            InputStream data = in.getInputStream(inEntry);
//            while ((num = data.read(buffer)) > 0) {
//                out.write(buffer, 0, num);
//                if (entryDataSink != null) {
//                    entryDataSink.consume(buffer, 0, num);
//                }
//            }
//            out.flush();
//            if (inspectEntryRequest != null) {
//                inspectEntryRequest.done();
//            }
//        }
//    }
//
//    private static boolean shouldOutputApkEntry(
//            ApkSignerEngine apkSigner, JarFile inFile, JarEntry inEntry, byte[] tmpbuf)
//                    throws IOException {
//        if (apkSigner == null) {
//            return true;
//        }
//
//        ApkSignerEngine.InputJarEntryInstructions instructions =
//                apkSigner.inputJarEntry(inEntry.getName());
//        ApkSignerEngine.InspectJarEntryRequest inspectEntryRequest =
//                instructions.getInspectJarEntryRequest();
//        if (inspectEntryRequest != null) {
//            provideJarEntry(inFile, inEntry, inspectEntryRequest, tmpbuf);
//        }
//        switch (instructions.getOutputPolicy()) {
//            case OUTPUT:
//                return true;
//            case SKIP:
//            case OUTPUT_BY_ENGINE:
//                return false;
//            default:
//                throw new RuntimeException(
//                        "Unsupported output policy: " + instructions.getOutputPolicy());
//        }
//    }
//
//    private static void provideJarEntry(
//            JarFile jarFile,
//            JarEntry jarEntry,
//            ApkSignerEngine.InspectJarEntryRequest request,
//            byte[] tmpbuf) throws IOException {
//        DataSink dataSink = request.getDataSink();
//        try (InputStream in = jarFile.getInputStream(jarEntry)) {
//            int chunkSize;
//            while ((chunkSize = in.read(tmpbuf)) > 0) {
//                dataSink.consume(tmpbuf, 0, chunkSize);
//            }
//            request.done();
//        }
//    }
//
//    /**
//     * Returns the multiple (in bytes) at which the provided {@code STORED} entry's data must start
//     * relative to start of file or {@code 0} if alignment of this entry's data is not important.
//     */
//    private static int getStoredEntryDataAlignment(String entryName, int defaultAlignment) {
//        if (defaultAlignment <= 0) {
//            return 0;
//        }
//
//        if (entryName.endsWith(".so")) {
//            // Align .so contents to memory page boundary to enable memory-mapped
//            // execution.
//            return 4096;
//        } else {
//            return defaultAlignment;
//        }
//    }
//
//    private static class WholeFileSignerOutputStream extends FilterOutputStream {
//        private boolean closing = false;
//        private ByteArrayOutputStream footer = new ByteArrayOutputStream();
//        private OutputStream tee;
//
//        public WholeFileSignerOutputStream(OutputStream out, OutputStream tee) {
//            super(out);
//            this.tee = tee;
//        }
//
//        public void notifyClosing() {
//            closing = true;
//        }
//
//        public void finish() throws IOException {
//            closing = false;
//
//            byte[] data = footer.toByteArray();
//            if (data.length < 2)
//                throw new IOException("Less than two bytes written to footer");
//            write(data, 0, data.length - 2);
//        }
//
//        public byte[] getTail() {
//            return footer.toByteArray();
//        }
//
//        @Override
//        public void write(byte[] b) throws IOException {
//            write(b, 0, b.length);
//        }
//
//        @Override
//        public void write(byte[] b, int off, int len) throws IOException {
//            if (closing) {
//                // if the jar is about to close, save the footer that will be written
//                footer.write(b, off, len);
//            }
//            else {
//                // write to both output streams. out is the CMSTypedData signer and tee is the file.
//                out.write(b, off, len);
//                tee.write(b, off, len);
//            }
//        }
//
//        @Override
//        public void write(int b) throws IOException {
//            if (closing) {
//                // if the jar is about to close, save the footer that will be written
//                footer.write(b);
//            }
//            else {
//                // write to both output streams. out is the CMSTypedData signer and tee is the file.
//                out.write(b);
//                tee.write(b);
//            }
//        }
//    }
//
//    private static class CMSSigner implements CMSTypedData {
//        private final JarFile inputJar;
//        private final File publicKeyFile;
//        private final X509Certificate publicKey;
//        private final PrivateKey privateKey;
//        private final int hash;
//        private final long timestamp;
//        private final OutputStream outputStream;
//        private final ASN1ObjectIdentifier type;
//        private WholeFileSignerOutputStream signer;
//
//        // Files matching this pattern are not copied to the output.
//        private static final Pattern STRIP_PATTERN =
//                Pattern.compile("^(META-INF/((.*)[.](SF|RSA|DSA|EC)|com/android/otacert))|("
//                        + Pattern.quote(JarFile.MANIFEST_NAME) + ")$");
//
//        public CMSSigner(JarFile inputJar, File publicKeyFile,
//                         X509Certificate publicKey, PrivateKey privateKey, int hash,
//                         long timestamp, OutputStream outputStream) {
//            this.inputJar = inputJar;
//            this.publicKeyFile = publicKeyFile;
//            this.publicKey = publicKey;
//            this.privateKey = privateKey;
//            this.hash = hash;
//            this.timestamp = timestamp;
//            this.outputStream = outputStream;
//            this.type = new ASN1ObjectIdentifier(CMSObjectIdentifiers.data.getId());
//        }
//
//        /**
//         * This should actually return byte[] or something similar, but nothing
//         * actually checks it currently.
//         */
//        @Override
//        public Object getContent() {
//            return this;
//        }
//
//        @Override
//        public ASN1ObjectIdentifier getContentType() {
//            return type;
//        }
//
//        @Override
//        public void write(OutputStream out) throws IOException {
//            try {
//                signer = new WholeFileSignerOutputStream(out, outputStream);
//                JarOutputStream outputJar = new JarOutputStream(signer);
//
//                copyFiles(inputJar, STRIP_PATTERN, null, outputJar, timestamp, 0);
//                addOtacert(outputJar, publicKeyFile, timestamp);
//
//                signer.notifyClosing();
//                outputJar.close();
//                signer.finish();
//            }
//            catch (Exception e) {
//                throw new IOException(e);
//            }
//        }
//
//        public void writeSignatureBlock(ByteArrayOutputStream temp)
//            throws IOException,
//                   CertificateEncodingException,
//                   OperatorCreationException,
//                   CMSException {
//            SignApk.writeSignatureBlock(this, publicKey, privateKey, hash, temp);
//        }
//
//        public WholeFileSignerOutputStream getSigner() {
//            return signer;
//        }
//    }
//
//    private static void signWholeFile(JarFile inputJar, File publicKeyFile,
//                                      X509Certificate publicKey, PrivateKey privateKey,
//                                      int hash, long timestamp,
//                                      OutputStream outputStream) throws Exception {
//        CMSSigner cmsOut = new CMSSigner(inputJar, publicKeyFile,
//                publicKey, privateKey, hash, timestamp, outputStream);
//
//        ByteArrayOutputStream temp = new ByteArrayOutputStream();
//
//        // put a readable message and a null char at the start of the
//        // archive comment, so that tools that display the comment
//        // (hopefully) show something sensible.
//        // TODO: anything more useful we can put in this message?
//        byte[] message = "signed by SignApk".getBytes("UTF-8");
//        temp.write(message);
//        temp.write(0);
//
//        cmsOut.writeSignatureBlock(temp);
//
//        byte[] zipData = cmsOut.getSigner().getTail();
//
//        // For a zip with no archive comment, the
//        // end-of-central-directory record will be 22 bytes long, so
//        // we expect to find the EOCD marker 22 bytes from the end.
//        if (zipData[zipData.length-22] != 0x50 ||
//            zipData[zipData.length-21] != 0x4b ||
//            zipData[zipData.length-20] != 0x05 ||
//            zipData[zipData.length-19] != 0x06) {
//            throw new IllegalArgumentException("zip data already has an archive comment");
//        }
//
//        int total_size = temp.size() + 6;
//        if (total_size > 0xffff) {
//            throw new IllegalArgumentException("signature is too big for ZIP file comment");
//        }
//        // signature starts this many bytes from the end of the file
//        int signature_start = total_size - message.length - 1;
//        temp.write(signature_start & 0xff);
//        temp.write((signature_start >> 8) & 0xff);
//        // Why the 0xff bytes?  In a zip file with no archive comment,
//        // bytes [-6:-2] of the file are the little-endian offset from
//        // the start of the file to the central directory.  So for the
//        // two high bytes to be 0xff 0xff, the archive would have to
//        // be nearly 4GB in size.  So it's unlikely that a real
//        // commentless archive would have 0xffs here, and lets us tell
//        // an old signed archive from a new one.
//        temp.write(0xff);
//        temp.write(0xff);
//        temp.write(total_size & 0xff);
//        temp.write((total_size >> 8) & 0xff);
//        temp.flush();
//
//        // Signature verification checks that the EOCD header is the
//        // last such sequence in the file (to avoid minzip finding a
//        // fake EOCD appended after the signature in its scan).  The
//        // odds of producing this sequence by chance are very low, but
//        // let's catch it here if it does.
//        byte[] b = temp.toByteArray();
//        for (int i = 0; i < b.length-3; ++i) {
//            if (b[i] == 0x50 && b[i+1] == 0x4b && b[i+2] == 0x05 && b[i+3] == 0x06) {
//                throw new IllegalArgumentException("found spurious EOCD header at " + i);
//            }
//        }
//
//        outputStream.write(total_size & 0xff);
//        outputStream.write((total_size >> 8) & 0xff);
//        temp.writeTo(outputStream);
//    }
//
//    /**
//     * Tries to load a JSE Provider by class name. This is for custom PrivateKey
//     * types that might be stored in PKCS#11-like storage.
//     */
//    private static void loadProviderIfNecessary(String providerClassName) {
//        if (providerClassName == null) {
//            return;
//        }
//
//        final Class<?> klass;
//        try {
//            final ClassLoader sysLoader = ClassLoader.getSystemClassLoader();
//            if (sysLoader != null) {
//                klass = sysLoader.loadClass(providerClassName);
//            } else {
//                klass = Class.forName(providerClassName);
//            }
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//            System.exit(1);
//            return;
//        }
//
//        Constructor<?> constructor = null;
//        for (Constructor<?> c : klass.getConstructors()) {
//            if (c.getParameterTypes().length == 0) {
//                constructor = c;
//                break;
//            }
//        }
//        if (constructor == null) {
//            System.err.println("No zero-arg constructor found for " + providerClassName);
//            System.exit(1);
//            return;
//        }
//
//        final Object o;
//        try {
//            o = constructor.newInstance();
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.exit(1);
//            return;
//        }
//        if (!(o instanceof Provider)) {
//            System.err.println("Not a Provider class: " + providerClassName);
//            System.exit(1);
//        }
//
//        Security.insertProviderAt((Provider) o, 1);
//    }
//
//    private static List<DefaultApkSignerEngine.SignerConfig> createSignerConfigs(
//            PrivateKey[] privateKeys, X509Certificate[] certificates) {
//        if (privateKeys.length != certificates.length) {
//            throw new IllegalArgumentException(
//                    "The number of private keys must match the number of certificates: "
//                            + privateKeys.length + " vs" + certificates.length);
//        }
//        List<DefaultApkSignerEngine.SignerConfig> signerConfigs = new ArrayList<>();
//        String signerNameFormat = (privateKeys.length == 1) ? "CERT" : "CERT%s";
//        for (int i = 0; i < privateKeys.length; i++) {
//            String signerName = String.format(Locale.US, signerNameFormat, (i + 1));
//            DefaultApkSignerEngine.SignerConfig signerConfig =
//                    new DefaultApkSignerEngine.SignerConfig.Builder(
//                            signerName,
//                            privateKeys[i],
//                            Collections.singletonList(certificates[i]))
//                            .build();
//            signerConfigs.add(signerConfig);
//        }
//        return signerConfigs;
//    }
//
//    private static class ZipSections {
//        ByteBuffer beforeCentralDir;
//        ByteBuffer centralDir;
//        ByteBuffer eocd;
//    }
//
//    private static ZipSections findMainZipSections(ByteBuffer apk)
//            throws IOException, ZipFormatException {
//        apk.slice();
//        ApkUtils.ZipSections sections = ApkUtils.findZipSections(DataSources.asDataSource(apk));
//        long centralDirStartOffset = sections.getZipCentralDirectoryOffset();
//        long centralDirSizeBytes = sections.getZipCentralDirectorySizeBytes();
//        long centralDirEndOffset = centralDirStartOffset + centralDirSizeBytes;
//        long eocdStartOffset = sections.getZipEndOfCentralDirectoryOffset();
//        if (centralDirEndOffset != eocdStartOffset) {
//            throw new ZipFormatException(
//                    "ZIP Central Directory is not immediately followed by End of Central Directory"
//                            + ". CD end: " + centralDirEndOffset
//                            + ", EoCD start: " + eocdStartOffset);
//        }
//        apk.position(0);
//        apk.limit((int) centralDirStartOffset);
//        ByteBuffer beforeCentralDir = apk.slice();
//
//        apk.position((int) centralDirStartOffset);
//        apk.limit((int) centralDirEndOffset);
//        ByteBuffer centralDir = apk.slice();
//
//        apk.position((int) eocdStartOffset);
//        apk.limit(apk.capacity());
//        ByteBuffer eocd = apk.slice();
//
//        apk.position(0);
//        apk.limit(apk.capacity());
//
//        ZipSections result = new ZipSections();
//        result.beforeCentralDir = beforeCentralDir;
//        result.centralDir = centralDir;
//        result.eocd = eocd;
//        return result;
//    }
//
//    private static void usage() {
//        System.err.println("Usage: signapk [-w] " +
//                           "[-a <alignment>] " +
//                           "[-providerClass <className>] " +
//                           "[--min-sdk-version <n>] " +
//                           "[--disable-v2] " +
//                           "publickey.x509[.pem] privatekey.pk8 " +
//                           "[publickey2.x509[.pem] privatekey2.pk8 ...] " +
//                           "input.jar output.jar");
//        System.exit(2);
//    }
//
//    public static void main(String[] args) {
//        if (args.length < 4) usage();
//
//        // Install Conscrypt as the highest-priority provider. Its crypto primitives are faster than
//        // the standard or Bouncy Castle ones.
//        Security.insertProviderAt(new OpenSSLProvider(), 1);
//        // Install Bouncy Castle (as the lowest-priority provider) because Conscrypt does not offer
//        // DSA which may still be needed.
//        // TODO: Stop installing Bouncy Castle provider once DSA is no longer needed.
//        Security.addProvider(new BouncyCastleProvider());
//
//        boolean signWholeFile = false;
//        String providerClass = null;
//        int alignment = 4;
//        int minSdkVersion = 0;
//        boolean signUsingApkSignatureSchemeV2 = true;
//
//        int argstart = 0;
//        while (argstart < args.length && args[argstart].startsWith("-")) {
//            if ("-w".equals(args[argstart])) {
//                signWholeFile = true;
//                ++argstart;
//            } else if ("-providerClass".equals(args[argstart])) {
//                if (argstart + 1 >= args.length) {
//                    usage();
//                }
//                providerClass = args[++argstart];
//                ++argstart;
//            } else if ("-a".equals(args[argstart])) {
//                alignment = Integer.parseInt(args[++argstart]);
//                ++argstart;
//            } else if ("--min-sdk-version".equals(args[argstart])) {
//                String minSdkVersionString = args[++argstart];
//                try {
//                    minSdkVersion = Integer.parseInt(minSdkVersionString);
//                } catch (NumberFormatException e) {
//                    throw new IllegalArgumentException(
//                            "--min-sdk-version must be a decimal number: " + minSdkVersionString);
//                }
//                ++argstart;
//            } else if ("--disable-v2".equals(args[argstart])) {
//                signUsingApkSignatureSchemeV2 = false;
//                ++argstart;
//            } else {
//                usage();
//            }
//        }
//
//        if ((args.length - argstart) % 2 == 1) usage();
//        int numKeys = ((args.length - argstart) / 2) - 1;
//        if (signWholeFile && numKeys > 1) {
//            System.err.println("Only one key may be used with -w.");
//            System.exit(2);
//        }
//
//        loadProviderIfNecessary(providerClass);
//
//        String inputFilename = args[args.length-2];
//        String outputFilename = args[args.length-1];
//
//        JarFile inputJar = null;
//        FileOutputStream outputFile = null;
//
//        try {
//            File firstPublicKeyFile = new File(args[argstart+0]);
//
//            X509Certificate[] publicKey = new X509Certificate[numKeys];
//            try {
//                for (int i = 0; i < numKeys; ++i) {
//                    int argNum = argstart + i*2;
//                    publicKey[i] = readPublicKey(new File(args[argNum]));
//                }
//            } catch (IllegalArgumentException e) {
//                System.err.println(e);
//                System.exit(1);
//            }
//
//            // Set all ZIP file timestamps to Jan 1 2009 00:00:00.
//            long timestamp = 1230768000000L;
//            // The Java ZipEntry API we're using converts milliseconds since epoch into MS-DOS
//            // timestamp using the current timezone. We thus adjust the milliseconds since epoch
//            // value to end up with MS-DOS timestamp of Jan 1 2009 00:00:00.
//            timestamp -= TimeZone.getDefault().getOffset(timestamp);
//
//            PrivateKey[] privateKey = new PrivateKey[numKeys];
//            for (int i = 0; i < numKeys; ++i) {
//                int argNum = argstart + i*2 + 1;
//                privateKey[i] = readPrivateKey(new File(args[argNum]));
//            }
//            inputJar = new JarFile(new File(inputFilename), false);  // Don't verify.
//
//            outputFile = new FileOutputStream(outputFilename);
//
//            // NOTE: Signing currently recompresses any compressed entries using Deflate (default
//            // compression level for OTA update files and maximum compession level for APKs).
//            if (signWholeFile) {
//                int digestAlgorithm = getDigestAlgorithmForOta(publicKey[0]);
//                signWholeFile(inputJar, firstPublicKeyFile,
//                        publicKey[0], privateKey[0], digestAlgorithm,
//                        timestamp,
//                        outputFile);
//            } else {
//                try (ApkSignerEngine apkSigner =
//                        new DefaultApkSignerEngine.Builder(
//                                createSignerConfigs(privateKey, publicKey), minSdkVersion)
//                                .setV1SigningEnabled(true)
//                                .setV2SigningEnabled(signUsingApkSignatureSchemeV2)
//                                .setOtherSignersSignaturesPreserved(false)
//                                .build()) {
//                    // We don't preserve the input APK's APK Signing Block (which contains v2
//                    // signatures)
//                    apkSigner.inputApkSigningBlock(null);
//
//                    // Build the output APK in memory, by copying input APK's ZIP entries across
//                    // and then signing the output APK.
//                    ByteArrayOutputStream v1SignedApkBuf = new ByteArrayOutputStream();
//                    JarOutputStream outputJar = new JarOutputStream(v1SignedApkBuf);
//                    // Use maximum compression for compressed entries because the APK lives forever
//                    // on the system partition.
//                    outputJar.setLevel(9);
//                    copyFiles(inputJar, null, apkSigner, outputJar, timestamp, alignment);
//                    ApkSignerEngine.OutputJarSignatureRequest addV1SignatureRequest =
//                            apkSigner.outputJarEntries();
//                    if (addV1SignatureRequest != null) {
//                        addV1Signature(apkSigner, addV1SignatureRequest, outputJar, timestamp);
//                        addV1SignatureRequest.done();
//                    }
//                    outputJar.close();
//                    ByteBuffer v1SignedApk = ByteBuffer.wrap(v1SignedApkBuf.toByteArray());
//                    v1SignedApkBuf.reset();
//                    ByteBuffer[] outputChunks = new ByteBuffer[] {v1SignedApk};
//
//                    ZipSections zipSections = findMainZipSections(v1SignedApk);
//                    ApkSignerEngine.OutputApkSigningBlockRequest addV2SignatureRequest =
//                            apkSigner.outputZipSections(
//                                    DataSources.asDataSource(zipSections.beforeCentralDir),
//                                    DataSources.asDataSource(zipSections.centralDir),
//                                    DataSources.asDataSource(zipSections.eocd));
//                    if (addV2SignatureRequest != null) {
//                        // Need to insert the returned APK Signing Block before ZIP Central
//                        // Directory.
//                        byte[] apkSigningBlock = addV2SignatureRequest.getApkSigningBlock();
//                        // Because the APK Signing Block is inserted before the Central Directory,
//                        // we need to adjust accordingly the offset of Central Directory inside the
//                        // ZIP End of Central Directory (EoCD) record.
//                        ByteBuffer modifiedEocd = ByteBuffer.allocate(zipSections.eocd.remaining());
//                        modifiedEocd.put(zipSections.eocd);
//                        modifiedEocd.flip();
//                        modifiedEocd.order(ByteOrder.LITTLE_ENDIAN);
//                        ApkUtils.setZipEocdCentralDirectoryOffset(
//                                modifiedEocd,
//                                zipSections.beforeCentralDir.remaining() + apkSigningBlock.length);
//                        outputChunks =
//                                new ByteBuffer[] {
//                                        zipSections.beforeCentralDir,
//                                        ByteBuffer.wrap(apkSigningBlock),
//                                        zipSections.centralDir,
//                                        modifiedEocd};
//                        addV2SignatureRequest.done();
//                    }
//
//                    // This assumes outputChunks are array-backed. To avoid this assumption, the
//                    // code could be rewritten to use FileChannel.
//                    for (ByteBuffer outputChunk : outputChunks) {
//                        outputFile.write(
//                                outputChunk.array(),
//                                outputChunk.arrayOffset() + outputChunk.position(),
//                                outputChunk.remaining());
//                        outputChunk.position(outputChunk.limit());
//                    }
//
//                    outputFile.close();
//                    outputFile = null;
//                    apkSigner.outputDone();
//                }
//
//                return;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.exit(1);
//        } finally {
//            try {
//                if (inputJar != null) inputJar.close();
//                if (outputFile != null) outputFile.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//                System.exit(1);
//            }
//        }
//    }
//}
