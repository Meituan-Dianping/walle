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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ChannelMaker extends DefaultTask {

    private static final String DOT_APK = ".apk";

    @Input
    public BaseVariant variant;
    @Input
    public Project targetProject;
    @Input
    public File apkFile;

    public void setup() {
        description "Make Multi-Channel"
        group "Package"
    }

    private static final String PROPERTY_CHANNEL_FILE = 'channelFile'
    private static final String PROPERTY_CHANNEL_LIST = 'channelList'
    private static final String PROPERTY_EXTRA_INFO = 'extraInfo'

    @TaskAction
    public void packaging() {
        boolean hasChannelList = targetProject.hasProperty(PROPERTY_CHANNEL_LIST)
        boolean hasChannelFile = targetProject.hasProperty(PROPERTY_CHANNEL_FILE)
        boolean hasExtraInfo = targetProject.hasProperty(PROPERTY_EXTRA_INFO)
        if (!hasChannelFile && !hasChannelList) {
            return;
        }
        if (apkFile == null || !apkFile.exists()) {
            throw new GradleException("${apkFile} is not existed!");
        }

        checkV2Signature()

        def channelList = new ArrayList<String>()

        if(hasChannelList){
            def channelListProperty = targetProject.getProperties().get(PROPERTY_CHANNEL_LIST)
            if (channelListProperty != null && channelListProperty.trim().length() != 0) {
                channelList.addAll(channelListProperty.split(",").collect{it.trim()})
            }
        }
        if (hasChannelFile) {
            def channelFileProperty = targetProject.getProperties().get(PROPERTY_CHANNEL_FILE)
            channelList.addAll(getChannelListFromFile(targetProject, channelFileProperty))
        }
        def extraInfo = null
        if (hasExtraInfo) {
            def keyValues = targetProject.getProperties().get(PROPERTY_EXTRA_INFO).split(",").collect{it.trim()}
            extraInfo = keyValues.findAll{it.split(":").size() == 2}.collectEntries ( [:] ) { keyValue ->
                def data = keyValue.split(":")
                [data[0], data[1]]
            }
        }
        long startTime = System.currentTimeMillis();

        channelList.each {channel -> generateChannelApk(channel, extraInfo)}

        targetProject.logger.lifecycle("APK Signature Scheme v2 Channel Maker takes about " + (System.currentTimeMillis() - startTime) + " milliseconds");
    }

    static def getChannelListFromFile(Project project, channelFileProperty) {
        def channelList = []
        if (channelFileProperty == null || channelFileProperty.trim().length() == 0) {
            return channelList
        }
        def channelFile = new File(channelFileProperty.trim())

        if (!channelFile.exists()) {
            project.logger.warn("channel file does not exist")
            return channelList
        } else {
             channelFile.eachLine { line ->
                def lineTrim = line.trim()
                if(lineTrim.length() != 0 && !lineTrim.startsWith("#")) {
                    def channel = line.split("#").first().trim()
                    if (channel.length() != 0)
                        channelList.add(channel)
                }
            }
        }
        return channelList
    }

    def generateChannelApk(channel, extraInfo) {
        String apkFileName = apkFile.getName();
        if (apkFileName.endsWith(DOT_APK)) {
            apkFileName = apkFileName.substring(0, apkFileName.lastIndexOf(DOT_APK));
        }
        File channelApkFile = new File("${apkFileName}-${channel}${DOT_APK}", apkFile.parentFile);
        FileUtils.copyFile(apkFile, channelApkFile);
        ChannelWriter.put(channelApkFile, channel, extraInfo)
    }

    def checkV2Signature() {
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
