package com.meituan.android.walle;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.util.Map;

public class ChannelReader {

    @Nullable
    public static String getChannel(@NonNull Context context) {
        ChannelInfo channelInfo = getChannelInfo(context);
        if (channelInfo == null) {
            return null;
        }
        return channelInfo.getChannel();
    }

    @Nullable
    public static ChannelInfo getChannelInfo(@NonNull Context context) {
        String apkPath = getApkPath(context);
        if (TextUtils.isEmpty(apkPath)) {
            return null;
        }
        return PayloadReader.getChannelInfo(new File(apkPath));
    }

    @Nullable
    public static String getChannelInfoByKey(@NonNull Context context, @NonNull String key) {
        Map<String, String> channelMap = getChannelInfoMap(context);
        if (channelMap == null) {
            return null;
        }
        return channelMap.get(key);
    }

    @Nullable
    public static Map<String, String> getChannelInfoMap(@NonNull Context context) {
        String apkPath = getApkPath(context);
        if (TextUtils.isEmpty(apkPath)) {
            return null;
        }
        return PayloadReader.getChannelInfoMap(new File(apkPath));
    }

    @Nullable
    private static String getApkPath(@NonNull Context context) {
        String apkPath = null;
        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            if (applicationInfo == null) {
                return null;
            }
            apkPath = applicationInfo.sourceDir;
        } catch (Throwable e) {
        }
        return apkPath;
    }
}
