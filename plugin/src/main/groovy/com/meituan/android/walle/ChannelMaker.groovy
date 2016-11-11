package com.meituan.android.walle

import com.android.apksigner.core.ApkVerifier
import com.android.apksigner.core.internal.util.ByteBufferDataSource
import com.android.apksigner.core.util.DataSource
import com.android.build.gradle.api.BaseVariant
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.commons.io.FileUtils
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

            DataSource dataSource = new ByteBufferDataSource(byteBuffer);

            ApkVerifier apkVerifier = new ApkVerifier();
            ApkVerifier.Result result = apkVerifier.verify(dataSource, 0);
            if (!result.verified || !result.verifiedUsingV2Scheme) {
                throw new GradleException("${apkFile} has no v2 signature in Apk Signing Block!");
            }
        } catch (IOException ignore) {
            ignore.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fChan);
            IOUtils.closeQuietly(fIn);
        }

        String apkFileName = apkFile.getName();
        if (apkFileName.endsWith(DOT_APK)) {
            apkFileName = apkFileName.substring(0, apkFileName.lastIndexOf(DOT_APK));
        }
        for (int index = 0; index < jsonArray.size(); ++index) {
            jsonObject = jsonArray.get(index);

            def salt = jsonObject.toString();
            println "********* add ID-value ${APK_CHANNEL_BLOCK_ID} : ${salt} to Apk Signing Block.";

            byte[] bytes = salt.bytes;
            byteBuffer = ByteBuffer.allocate(bytes.length);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.put(bytes, 0, bytes.length);
            byteBuffer.flip();

            Map<Integer, ByteBuffer> idValues = new HashMap<Integer, ByteBuffer>();
            idValues.put(APK_CHANNEL_BLOCK_ID, byteBuffer);

            String channelName = "${index}";
            if (jsonObject.has(JSON_CHANNEL_NAME)) {
                channelName = jsonObject.get(JSON_CHANNEL_NAME).getAsString();
            }
            File channelApkFile = new File("${apkFileName}-${channelName}${DOT_APK}", apkFile.parentFile);

            long startTime = System.currentTimeMillis();

            FileUtils.copyFile(apkFile, channelApkFile);
            PayloadWriter.writeIDValuePairs(channelApkFile.absolutePath, idValues);

            println "APK Signature Scheme v2 Channel Maker takes about " + (System.currentTimeMillis() - startTime) + " milliseconds";
        }

    }
}
