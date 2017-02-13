package com.meituan.android.walle

import org.gradle.api.Project

class Extension {
    static final String DEFAULT_APK_FILE_NAME_TEMPLATE = '${appName}-${buildType}-${channel}.apk'

    /**
     *  apk output dir
     *  default value: null, the channels' apk will output in '${project}/build/output/apk' folder
     */
    File apkOutputFolder

    /**
     * file name template string
     *
     * Available vars:
     * 1. projectName
     * 2. appName
     * 3. packageName
     * 4. buildType
     * 5. channel
     * 6. versionName
     * 7. versionCode
     * 8. buildTime
     * 9. fileSHA1
     *
     * default value: '${appName}-${buildType}-${channel}.apk'
     *
     */
    String apkFileNameFormat

    String channelList;

    String channelFile;

    /**
     * In the form of key: value and separate by comma, for example: extraInfo=buildtime:20161212,hash:xxxxxxx
     */
    String extraInfo;

    Extension(Project project) {
        apkOutputFolder = null;
        apkFileNameFormat = DEFAULT_APK_FILE_NAME_TEMPLATE;
    }

    public static Extension getConfig(Project project) {
        Extension config =
                project.getExtensions().findByType(Extension.class);
        if (config == null) {
            config = new Extension();
        }
        return config;
    }
}
