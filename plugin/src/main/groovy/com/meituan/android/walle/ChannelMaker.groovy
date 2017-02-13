package com.meituan.android.walle

import com.android.apksigner.core.ApkVerifier
import com.android.apksigner.core.internal.util.ByteBufferDataSource
import com.android.apksigner.core.util.DataSource
import com.android.build.gradle.api.BaseVariant
import com.google.common.base.Charsets
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import groovy.text.SimpleTemplateEngine
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat

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
        if (apkFile == null || !apkFile.exists()) {
            throw new GradleException("${apkFile} is not existed!");
        }
        Extension extension = Extension.getConfig(targetProject);

        def channelList = getChannelList(extension)
        if (channelList.isEmpty()) {
            return;
        }
        def extraInfo = getChannelExtraInfo(extension)

        checkV2Signature()

        long startTime = System.currentTimeMillis();

        File channelOutputFolder = apkFile.parentFile;
        if (extension.apkOutputFolder instanceof File) {
            channelOutputFolder = extension.apkOutputFolder;
            if (!channelOutputFolder.parentFile.exists()) {
                channelOutputFolder.parentFile.mkdirs();
            }
        }
        def nameVariantMap = [
                'appName'    : targetProject.name,
                'projectName': targetProject.rootProject.name,
                'buildType'  : variant.buildType.name,
                'versionName': variant.versionName,
                'versionCode': variant.versionCode,
                'packageName': variant.applicationId,
                'fileSHA1'   : getFileHash(apkFile)
        ]

        channelList.each { channel -> generateChannelApk(channelOutputFolder, nameVariantMap, channel, extraInfo) }

        targetProject.logger.lifecycle("APK Signature Scheme v2 Channel Maker takes about " + (System.currentTimeMillis() - startTime) + " milliseconds");
    }

    List<String> getChannelList(Extension extension) {
        def channelList = new ArrayList<String>()

        String channelListProperty;
        String channelFileProperty;

        boolean hasChannelList = targetProject.hasProperty(PROPERTY_CHANNEL_LIST)
        if (!hasChannelList) {
            if (extension.channelList != null && extension.channelList.length() > 0) {
                channelListProperty = extension.channelList
                hasChannelList = true;
            }
        } else {
            channelListProperty = targetProject.getProperties().get(PROPERTY_CHANNEL_LIST);
        }

        boolean hasChannelFile = targetProject.hasProperty(PROPERTY_CHANNEL_FILE)
        if (!hasChannelFile) {
            if (extension.channelFile != null && extension.channelFile.length() > 0) {
                channelFileProperty = extension.channelFile;
                hasChannelList = true;
            }
        } else {
            channelFileProperty = targetProject.getProperties().get(PROPERTY_CHANNEL_FILE);
        }

        if (!hasChannelFile && !hasChannelList) {
            return channelList;
        }

        if (channelListProperty != null && channelListProperty.trim().length() > 0) {
            channelList.addAll(channelListProperty.split(",").collect { it.trim() })
        }
        if (channelFileProperty != null && channelFileProperty.trim().length() > 0) {
            channelList.addAll(getChannelListFromFile(targetProject, channelFileProperty))
        }

        return channelList;
    }

    def getChannelExtraInfo(Extension extension) {
        boolean hasExtraInfo = targetProject.hasProperty(PROPERTY_EXTRA_INFO)
        def extraInfo = null

        String extraString;
        if (!hasExtraInfo) {
            extraString = extension.extraInfo;
        } else {
            extraString = targetProject.getProperties().get(PROPERTY_EXTRA_INFO)
        }

        if (extraString != null && extraString.trim().length() > 0) {
            def keyValues = extraString.split(",").collect {
                it.trim()
            }
            extraInfo = keyValues.findAll {
                it.split(":").size() == 2
            }.collectEntries([:]) { keyValue ->
                def data = keyValue.split(":")
                [data[0], data[1]]
            }
        }

        return extraInfo;
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
                if (lineTrim.length() != 0 && !lineTrim.startsWith("#")) {
                    def channel = line.split("#").first().trim()
                    if (channel.length() != 0)
                        channelList.add(channel)
                }
            }
        }
        return channelList
    }

    def generateChannelApk(File channelOutputFolder, Map nameVariantMap, channel, extraInfo) {
        Extension extension = Extension.getConfig(targetProject);

        def buildTime = new SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date());
        nameVariantMap.put("buildTime", buildTime);
        nameVariantMap.put('channel', channel);

        String fileName = apkFile.getName();
        if (fileName.endsWith(DOT_APK)) {
            fileName = fileName.substring(0, fileName.lastIndexOf(DOT_APK));
        }

        String apkFileName = "${fileName}-${channel}${DOT_APK}";
        if (extension.apkFileNameFormat != null && extension.apkFileNameFormat.length() > 0) {
            apkFileName = new SimpleTemplateEngine().createTemplate(extension.apkFileNameFormat).make(nameVariantMap).toString()
        };

        File channelApkFile = new File(apkFileName, channelOutputFolder);
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

    private static String getFileHash(File file) throws IOException {
        HashCode hashCode;
        HashFunction hashFunction = Hashing.sha1();
        if (file.isDirectory()) {
            hashCode = hashFunction.hashString(file.getPath(), Charsets.UTF_16LE);
        } else {
            hashCode = Files.hash(file, hashFunction);
        }
        return hashCode.toString();
    }
}
