package com.meituan.android.walle;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.util.Map;

public final class WalleChannelReader {
    private WalleChannelReader() {
        super();
    }

    /**
     * get channel
     *
     * @param context context
     * @return channel, null if not fount
     */
    @Nullable
    public static String getChannel(@NonNull final Context context) {
        return getChannel(context, null);
    }

    /**
     * get channel or default
     *
     * @param context context
     * @param defaultChannel default channel
     * @return channel, default if not fount
     */
    @Nullable
    public static String getChannel(@NonNull final Context context, @NonNull final String defaultChannel) {
        final ChannelInfo channelInfo = getChannelInfo(context);
        if (channelInfo == null) {
            return defaultChannel;
        }
        return channelInfo.getChannel();
    }

    /**
     * get channel info (include channle & extraInfo)
     *
     * @param context context
     * @return channel info
     */
    @Nullable
    public static ChannelInfo getChannelInfo(@NonNull final Context context) {
        final String apkPath = getApkPath(context);
        if (TextUtils.isEmpty(apkPath)) {
            return null;
        }
        return ChannelReader.get(new File(apkPath));
    }

    /**
     * get value by key
     *
     * @param context context
     * @param key     the key you store
     * @return value
     */
    @Nullable
    public static String get(@NonNull final Context context, @NonNull final String key) {
        final Map<String, String> channelMap = getChannelInfoMap(context);
        if (channelMap == null) {
            return null;
        }
        return channelMap.get(key);
    }

    /**
     * get all channl info with map
     *
     * @param context context
     * @return map
     */
    @Nullable
    public static Map<String, String> getChannelInfoMap(@NonNull final Context context) {
        final String apkPath = getApkPath(context);
        if (TextUtils.isEmpty(apkPath)) {
            return null;
        }
        return ChannelReader.getMap(new File(apkPath));
    }

    @Nullable
    private static String getApkPath(@NonNull final Context context) {
        String apkPath = null;
        try {
            final ApplicationInfo applicationInfo = context.getApplicationInfo();
            if (applicationInfo == null) {
                return null;
            }
            apkPath = applicationInfo.sourceDir;
        } catch (Throwable e) {
        }
        return apkPath;
    }
}
