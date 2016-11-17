package com.meituan.android.walle

import com.android.apksigner.core.ApkVerifier
import com.android.apksigner.core.internal.util.ByteBufferDataSource
import com.android.apksigner.core.util.DataSource
import com.android.build.gradle.api.BaseVariant
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ChannelMaker extends DefaultTask {

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

        String channel = (variant.flavorName != null && variant.flavorName.length() > 0) ? variant.flavorName : 'undefined'
        Map<String, String> extraInfo = new HashMap<>()
        extraInfo.put("buildType", variant.buildType.name)
        extraInfo.put("timestamp", "" + System.currentTimeMillis())

        checkV2Signature()

        String apkFileName = apkFile.getName();
        if (apkFileName.endsWith(DOT_APK)) {
            apkFileName = apkFileName.substring(0, apkFileName.lastIndexOf(DOT_APK));
        }
        File channelApkFile = new File("${apkFileName}-${channel}${DOT_APK}", apkFile.parentFile);

        long startTime = System.currentTimeMillis();

        FileUtils.copyFile(apkFile, channelApkFile);
        PayloadWriter.putChannel(channelApkFile, channel, extraInfo)

        println "APK Signature Scheme v2 Channel Maker takes about " + (System.currentTimeMillis() - startTime) + " milliseconds";

    }

    private void checkV2Signature() {
        FileInputStream fIn;
        FileChannel fChan;
        try {
            fIn = new FileInputStream(apkFile);
            fChan = fIn.getChannel();
            long fSize = fChan.size();
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) fSize);
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
    }
}
