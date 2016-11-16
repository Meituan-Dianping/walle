package com.meituan.android.walle;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.meituan.android.walle.internal.Pair;

import java.io.File;
import java.util.Map;

public class ChannelReader {
    @Nullable
    public static Pair<String, Map<String, String>> getChannelInfo(@NonNull Context context) {
        String apkPath = null;
        try {
            ApplicationInfo applicationInfo = getApplicationInfo(context.getApplicationContext());
            if (applicationInfo == null) {
                return null;
            }
            apkPath = applicationInfo.sourceDir;
        } catch (Throwable e) {
        }

        if (TextUtils.isEmpty(apkPath)) {
            return null;
        }
        return PayloadReader.getChannel(new File(apkPath));
    }

    private static ApplicationInfo getApplicationInfo(Context context)
            throws PackageManager.NameNotFoundException {
        PackageManager pm;
        String packageName;
        try {
            pm = context.getPackageManager();
            packageName = context.getPackageName();
        } catch (RuntimeException e) {
            /* Ignore those exceptions so that we don't break tests relying on Context like
             * a android.test.mock.MockContext or a android.content.ContextWrapper with a null
             * base Context.
             */
//            Log.w(TAG, "Failure while trying to obtain ApplicationInfo from Context. " +
//                    "Must be running in test mode. Skip patching.", e);
            return null;
        }
        if (pm == null || packageName == null) {
            // This is most likely a mock context, so just return without patching.
            return null;
        }
        ApplicationInfo applicationInfo =
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        return applicationInfo;
    }
}
