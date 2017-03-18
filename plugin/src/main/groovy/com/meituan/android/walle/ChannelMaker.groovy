package com.meituan.android.walle

import com.android.apksigner.core.ApkVerifier
import com.android.apksigner.core.internal.util.ByteBufferDataSource
import com.android.apksigner.core.util.DataSource
import com.android.build.FilterData
import com.android.build.gradle.api.BaseVariant
import com.google.common.base.Charsets
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.google.gson.Gson
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

    public void setup() {
        description "Make Multi-Channel"
        group "Package"
    }

    @TaskAction
    public void packaging() {
        Extension extension = Extension.getConfig(targetProject);

        long startTime = System.currentTimeMillis();

        def iterator = variant.outputs.iterator();
        while (iterator.hasNext()) {
            def it = iterator.next();
            def apkFile = it.outputFile
            def apiIdentifier = null;
            if (!it.outputs[0].filters.isEmpty()) {
                def tempIterator = it.outputs[0].filters.iterator();
                while (tempIterator.hasNext()) {
                    FilterData filterData = tempIterator.next();
                    if (filterData.filterType == "ABI") {
                        apiIdentifier = filterData.identifier
                        break;
                    }
                }
            }
            if (apkFile == null || !apkFile.exists()) {
                throw new GradleException("${apkFile} is not existed!");
            }

            checkV2Signature(apkFile)

            File channelOutputFolder = apkFile.parentFile;
            if (extension.apkOutputFolder instanceof File) {
                channelOutputFolder = extension.apkOutputFolder;
                if (!channelOutputFolder.parentFile.exists()) {
                    channelOutputFolder.parentFile.mkdirs();
                }
            }

            if (apiIdentifier != null && apiIdentifier.length() > 0) {
                channelOutputFolder = new File(channelOutputFolder, apiIdentifier);
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
                    'fileSHA1'   : getFileHash(apkFile),
                    'flavorName' : variant.flavorName
            ]

            if (extension.configFile instanceof File) {
                if (!extension.configFile.exists()) {
                    project.logger.warn("config file does not exist")
                    return
                }
                WalleConfig config = new Gson().fromJson(new FileReader(extension.configFile), WalleConfig.class)
                def defaultExtraInfo = config.getDefaultExtraInfo()
                config.getChannelInfoList().each { channelInfo ->
                    def extraInfo = channelInfo.extraInfo
                    if (extraInfo == null) {
                        extraInfo = defaultExtraInfo
                    }
                    generateChannelApk(apkFile, channelOutputFolder, nameVariantMap, channelInfo.channel, extraInfo, channelInfo.alias)
                }
            } else if (extension.channelFile instanceof File) {
                if (!extension.channelFile.exists()) {
                    project.logger.warn("channel file does not exist")
                    return
                }

                getChannelListFromFile(extension.channelFile).each { channel -> generateChannelApk(apkFile, channelOutputFolder, nameVariantMap, channel, null, null) }
            }
        }

        targetProject.logger.lifecycle("APK Signature Scheme v2 Channel Maker takes about " + (
                System.currentTimeMillis() - startTime) + " milliseconds");
    }

    static def getChannelListFromFile(File channelFile) {
        def channelList = []
        channelFile.eachLine { line ->
                def lineTrim = line.trim()
                if (lineTrim.length() != 0 && !lineTrim.startsWith("#")) {
                    def channel = line.split("#").first().trim()
                    if (channel.length() != 0)
                        channelList.add(channel)
                }
            }
        return channelList
    }

    def generateChannelApk(File apkFile, File channelOutputFolder, Map nameVariantMap, channel, extraInfo, alias) {
        Extension extension = Extension.getConfig(targetProject);

        def buildTime = new SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date());
        def channelName = alias == null ? channel : alias
        nameVariantMap.put("buildTime", buildTime);
        nameVariantMap.put('channel', channelName);

        String fileName = apkFile.getName();
        if (fileName.endsWith(DOT_APK)) {
            fileName = fileName.substring(0, fileName.lastIndexOf(DOT_APK));
        }

        String apkFileName = "${fileName}-${channelName}${DOT_APK}";
        if (extension.apkFileNameFormat != null && extension.apkFileNameFormat.length() > 0) {
            apkFileName = new SimpleTemplateEngine().createTemplate(extension.apkFileNameFormat).make(nameVariantMap).toString()
        };

        File channelApkFile = new File(apkFileName, channelOutputFolder);
        FileUtils.copyFile(apkFile, channelApkFile);
        ChannelWriter.put(channelApkFile, channel, extraInfo)
    }

    def checkV2Signature(File apkFile) {
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
