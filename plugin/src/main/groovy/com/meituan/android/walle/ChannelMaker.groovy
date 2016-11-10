package com.meituan.android.walle

import com.android.apksigner.core.ApkVerifier
import com.android.apksigner.core.apk.ApkUtils
import com.android.apksigner.core.internal.apk.v2.V2SchemeVerifier
import com.android.apksigner.core.internal.util.ByteBufferDataSource
import com.android.apksigner.core.internal.util.Pair
import com.android.apksigner.core.util.DataSource
import com.android.build.gradle.api.BaseVariant
import com.google.gson.JsonObject
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class ChannelMaker extends DefaultTask {
    public BaseVariant variant;
    public Project project;
    public File apkFile;

    public void setup() {
        description "Make Multi-Channel"
        group "Package"
    }

    @TaskAction
    public void packaging() {
        if (apkFile == null || !apkFile.exists()) {
            throw new GradleException("${apkFile} is not existed!");
        }

        ApkVerifier.Result result = verifyV2SignatureScheme(apkFile);
        if (!result.verified || !result.verifiedUsingV2Scheme) {
            throw new GradleException("${apkFile} has no v2 signature in Apk Signing Block!");
        }

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("channel", (variant.flavorName != null && variant.flavorName.length() > 0) ? variant.flavorName : 'undefined');
        jsonObject.addProperty("buildType:", variant.buildType.name);
        jsonObject.addProperty("timestamp", System.currentTimeMillis());

        long startTime = System.currentTimeMillis();
        addSaltForV2SignatureScheme(apkFile, jsonObject);
        println "APK Signature Scheme v2 Channel Maker takes about " + (System.currentTimeMillis() - startTime) + " milliseconds";
    }

    ApkVerifier.Result verifyV2SignatureScheme(File apkFile) {
        FileInputStream fIn;
        FileChannel fChan;
        long fSize;
        ByteBuffer byteBuffer;

        ApkVerifier.Result result = new ApkVerifier.Result();
        try {
            fIn = new FileInputStream(apkFile);
            fChan = fIn.getChannel();
            fSize = fChan.size();
            byteBuffer = ByteBuffer.allocate((int) fSize);
            fChan.read(byteBuffer);
            byteBuffer.rewind();

            ApkVerifier apkVerifier = new ApkVerifier();

            result = apkVerifier.verify(new ByteBufferDataSource(byteBuffer), 0);
        } catch (IOException ignore) {
            ignore.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fChan);
            IOUtils.closeQuietly(fIn);
        }

        return result;
    }

    void addSaltForV2SignatureScheme(File apkFile, JsonObject jsonObject) {
        FileInputStream fIn;
        FileChannel fChan;
        long fSize;
        ByteBuffer byteBuffer;

        ApkSigningBlock apkSigningBlock = new ApkSigningBlock();
        long centralDirStartOffset = 0;
        long apkSigningBlockOffset = 0;
        try {
            fIn = new FileInputStream(apkFile);
            fChan = fIn.getChannel();
            fSize = fChan.size();
            byteBuffer = ByteBuffer.allocate((int) fSize);
            fChan.read(byteBuffer);
            byteBuffer.rewind();

            byte[] zipData = byteBuffer.array();

            // For a zip with no archive comment, the
            // end-of-central-directory record will be 22 bytes long, so
            // we expect to find the EOCD marker 22 bytes from the end.
            if (zipData[zipData.length - 22] != 0x50 ||
                    zipData[zipData.length - 21] != 0x4b ||
                    zipData[zipData.length - 20] != 0x05 ||
                    zipData[zipData.length - 19] != 0x06) {
                throw new IllegalArgumentException("zip data already has an archive comment");
            }

            V2SchemeVerifier.Result result = new V2SchemeVerifier.Result();

            DataSource apk = new ByteBufferDataSource(byteBuffer);
            ApkUtils.ZipSections zipSections = ApkUtils.findZipSections(apk);

            centralDirStartOffset = zipSections.getZipCentralDirectoryOffset();
            long centralDirEndOffset =
                    centralDirStartOffset + zipSections.getZipCentralDirectorySizeBytes();
            long eocdStartOffset = zipSections.getZipEndOfCentralDirectoryOffset();
            if (centralDirEndOffset != eocdStartOffset) {
                throw new V2SchemeVerifier.SignatureNotFoundException(
                        "ZIP Central Directory is not immediately followed by End of Central Directory"
                                + ". CD end: " + centralDirEndOffset
                                + ", EoCD start: " + eocdStartOffset);
            }

            // Find the APK Signing Block. The block immediately precedes the Central Directory.
            ByteBuffer eocd = zipSections.getZipEndOfCentralDirectory();
            Pair<ByteBuffer, Long> apkSigningBlockAndOffset =
                    V2SchemeVerifier.findApkSigningBlock(apk, centralDirStartOffset);
            ByteBuffer apkSigningBlock2 = apkSigningBlockAndOffset.getFirst();
            apkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();

            // Find the APK Signature Scheme v2 Block inside the APK Signing Block.
            ByteBuffer apkSignatureSchemeV2Block =
                    V2SchemeVerifier.findApkSignatureSchemeV2Block(apkSigningBlock2, result);

            ApkSigningPayload payload = new ApkSigningPayload(V2SchemeVerifier.APK_SIGNATURE_SCHEME_V2_BLOCK_ID, apkSignatureSchemeV2Block);
            apkSigningBlock.addPayload(payload);
        } catch (IOException ignore) {
            ignore.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fChan);
            IOUtils.closeQuietly(fIn);
        }

        def salt = jsonObject.toString();
        println "********* add ID-value ${salt} to Apk Signing Block.";

        byte[] bytes = salt.bytes;
        ByteBuffer byteBuffer1 = ByteBuffer.allocate(bytes.length);
        byteBuffer1.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer1.put(bytes, 0, bytes.length);
        byteBuffer1.flip();
        ApkSigningPayload payload = new ApkSigningPayload(0xff01, byteBuffer1);
        apkSigningBlock.addPayload(payload);

        if (!apkSigningBlock.getPayloads().isEmpty() && apkSigningBlockOffset != 0 && centralDirStartOffset != 0) {
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = new RandomAccessFile(apkFile, "rw");

                randomAccessFile.seek(centralDirStartOffset);
                // 读取CentralDir
                byte[] centralDirBytes = new byte[randomAccessFile.getChannel().size() - centralDirStartOffset];
                randomAccessFile.read(centralDirBytes);

                randomAccessFile.setLength(apkSigningBlockOffset);
                randomAccessFile.seek(apkSigningBlockOffset);

                long length = apkSigningBlock.writeApkSigningBlock(randomAccessFile);

                // 存储CentralDir
                randomAccessFile.write(centralDirBytes);

                // 更新CentralDir Offset

                // End of central directory record (EOCD)
                // Offset	Bytes	Description[23]
                // 0	        4	    End of central directory signature = 0x06054b50
                // 4	        2	    Number of this disk
                // 6	        2	    Disk where central directory starts
                // 8	        2	    Number of central directory records on this disk
                // 10	        2	    Total number of central directory records
                // 12	        4	    Size of central directory (bytes)
                // 16	        4	    Offset of start of central directory, relative to start of archive
                // 20	        2	    Comment length (n)
                // 22	        n	    Comment

                randomAccessFile.seek(randomAccessFile.getChannel().size() - 6);
                ByteBuffer temp = ByteBuffer.allocate(Integer.BYTES);
                temp.order(ByteOrder.LITTLE_ENDIAN);
                temp.putInt((int) (centralDirStartOffset + length + 8 - (centralDirStartOffset - apkSigningBlockOffset)));
                temp.flip();
                randomAccessFile.write(temp.array());

            } catch (IOException ignore) {
                ignore.printStackTrace();
            } finally {
                IOUtils.closeQuietly(randomAccessFile);
            }
        }
    }
}
