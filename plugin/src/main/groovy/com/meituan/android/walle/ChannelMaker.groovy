package com.meituan.android.walle

import com.android.apksigner.core.ApkVerifier
import com.android.apksigner.core.apk.ApkUtils
import com.android.apksigner.core.internal.apk.v2.V2SchemeVerifier
import com.android.apksigner.core.internal.util.ByteBufferDataSource
import com.android.apksigner.core.internal.util.Pair
import com.android.apksigner.core.util.DataSource
import com.android.build.gradle.api.BaseVariant
import com.google.gson.JsonArray
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
    private static final int APK_CHANNEL_BLOCK_ID = 0x71777777;

    private static final String JSON_CHANNEL_NAME = "channel";
    private static final String DOT_APK = ".apk";

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

        FileInputStream fIn;
        FileChannel fChan;
        long fSize;
        ByteBuffer byteBuffer;

        JsonArray jsonArray = new JsonArray();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("channel", (variant.flavorName != null && variant.flavorName.length() > 0) ? variant.flavorName : 'undefined');
        jsonObject.addProperty("buildType:", variant.buildType.name);
        jsonObject.addProperty("timestamp", System.currentTimeMillis());
        jsonArray.add(jsonObject);

        try {
            fIn = new FileInputStream(apkFile);
            fChan = fIn.getChannel();
            fSize = fChan.size();
            byteBuffer = ByteBuffer.allocate((int) fSize);
            fChan.read(byteBuffer);
            byteBuffer.rewind();

            byte[] zipData = byteBuffer.array();

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
            // For a zip with no archive comment, the
            // end-of-central-directory record will be 22 bytes long, so
            // we expect to find the EOCD marker 22 bytes from the end.
            if (zipData[zipData.length - 22] != 0x50 ||
                    zipData[zipData.length - 21] != 0x4b ||
                    zipData[zipData.length - 20] != 0x05 ||
                    zipData[zipData.length - 19] != 0x06) {
                throw new IllegalArgumentException("zip data already has an archive comment");
            }

            DataSource dataSource = new ByteBufferDataSource(byteBuffer);

            ApkVerifier apkVerifier = new ApkVerifier();
            ApkVerifier.Result result = apkVerifier.verify(dataSource, 0);
            if (!result.verified || !result.verifiedUsingV2Scheme) {
                throw new GradleException("${apkFile} has no v2 signature in Apk Signing Block!");
            }

            long startTime = System.currentTimeMillis();
            addSaltForV2SignatureScheme(dataSource, jsonArray);
            println "APK Signature Scheme v2 Channel Maker takes about " + (System.currentTimeMillis() - startTime) + " milliseconds";

        } catch (IOException ignore) {
            ignore.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fChan);
            IOUtils.closeQuietly(fIn);
        }
    }

    void addSaltForV2SignatureScheme(DataSource apk, JsonArray jsonArray) {

        ApkSigningBlock apkSigningBlock = new ApkSigningBlock();
        V2SchemeVerifier.Result result = new V2SchemeVerifier.Result();

        ApkUtils.ZipSections zipSections = ApkUtils.findZipSections(apk);

        long centralDirStartOffset = zipSections.getZipCentralDirectoryOffset();
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
        long apkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();

        Map<Integer, ByteBuffer> idValues = findIdValues(apkSigningBlock2, result);
        // Find the APK Signature Scheme v2 Block inside the APK Signing Block.
        ByteBuffer apkSignatureSchemeV2Block = idValues.get(V2SchemeVerifier.APK_SIGNATURE_SCHEME_V2_BLOCK_ID);

        ApkSigningPayload payload = new ApkSigningPayload(V2SchemeVerifier.APK_SIGNATURE_SCHEME_V2_BLOCK_ID, apkSignatureSchemeV2Block);
        apkSigningBlock.addPayload(payload);


        if (!apkSigningBlock.getPayloads().isEmpty() && apkSigningBlockOffset != 0 && centralDirStartOffset != 0) {
            String apkFileName = apkFile.getName();
            if (apkFileName.endsWith(DOT_APK)) {
                apkFileName = apkFileName.substring(0, apkFileName.lastIndexOf(DOT_APK));
            }
            for (int index = 0; index < jsonArray.size(); ++index) {
                JsonObject jsonObject = jsonArray.get(index);

                def salt = jsonObject.toString();
                println "********* add ID-value ${APK_CHANNEL_BLOCK_ID} : ${salt} to Apk Signing Block.";

                byte[] bytes = salt.bytes;
                ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                byteBuffer.put(bytes, 0, bytes.length);
                byteBuffer.flip();
                payload = new ApkSigningPayload(APK_CHANNEL_BLOCK_ID, byteBuffer);
                apkSigningBlock.addPayload(payload);

                RandomAccessFile randomAccessFile = null;
                try {
                    String channelName = "${index}";
                    if (jsonObject.has(JSON_CHANNEL_NAME)) {
                        channelName = jsonObject.get(JSON_CHANNEL_NAME).getAsString();
                    }
                    File channelApkFile = new File("${apkFileName}-${channelName}${DOT_APK}", apkFile.parentFile);

                    randomAccessFile = new RandomAccessFile(channelApkFile, "rw");

                    randomAccessFile.write(apk.getByteBuffer((long)0, (int)apk.size()).array());

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

                    randomAccessFile.seek(randomAccessFile.getChannel().size() - 6); // 6 = 2(Comment length) + 4 (Offset of start of central directory, relative to start of archive)
                    ByteBuffer temp = ByteBuffer.allocate(Integer.BYTES);
                    temp.order(ByteOrder.LITTLE_ENDIAN);
                    temp.putInt((int) (centralDirStartOffset + length + 8 - (centralDirStartOffset - apkSigningBlockOffset))); // 8 = size of block in bytes (excluding this field) (uint64)
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

    public static Map<Integer, ByteBuffer> findIdValues(
            ByteBuffer apkSigningBlock,
            V2SchemeVerifier.Result result) throws V2SchemeVerifier.SignatureNotFoundException {
        V2SchemeVerifier.checkByteOrderLittleEndian(apkSigningBlock);
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes pairs
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic
        ByteBuffer pairs = V2SchemeVerifier.sliceFromTo(apkSigningBlock, 8, apkSigningBlock.capacity() - 24);

        Map<Integer, ByteBuffer> idValues = new HashMap<Integer, ByteBuffer>();

        int entryCount = 0;
        while (pairs.hasRemaining()) {
            entryCount++;
            if (pairs.remaining() < 8) {
                throw new V2SchemeVerifier.SignatureNotFoundException(
                        "Insufficient data to read size of APK Signing Block entry #" + entryCount);
            }
            long lenLong = pairs.getLong();
            if ((lenLong < 4) || (lenLong > Integer.MAX_VALUE)) {
                throw new V2SchemeVerifier.SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount
                                + " size out of range: " + lenLong);
            }
            int len = (int) lenLong;
            int nextEntryPos = pairs.position() + len;
            if (len > pairs.remaining()) {
                throw new V2SchemeVerifier.SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount + " size out of range: " + len
                                + ", available: " + pairs.remaining());
            }
            int id = pairs.getInt();
            if (id == V2SchemeVerifier.APK_SIGNATURE_SCHEME_V2_BLOCK_ID) {
                idValues.put(id, V2SchemeVerifier.getByteBuffer(pairs, len - 4));
            } else {
                idValues.put(id, V2SchemeVerifier.getByteBuffer(pairs, len - 4));
            }
            result.addWarning(ApkVerifier.Issue.APK_SIG_BLOCK_UNKNOWN_ENTRY_ID, id);
            pairs.position(nextEntryPos);
        }

        if (idValues.isEmpty()) {
            throw new V2SchemeVerifier.SignatureNotFoundException(
                    "No APK Signature Scheme v2 block in APK Signing Block");
        } else {
            return idValues;
        }
    }
}
