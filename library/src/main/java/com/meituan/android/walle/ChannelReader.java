package com.meituan.android.walle;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

public class ChannelReader {
    private static final int APK_CHANNEL_BLOCK_ID = 0x71777777;

    @Nullable
    public static JSONObject getChannelInfo(@NonNull Context context) {
        JSONObject jsonObject = null;

        try {
            String apkPath = null;
            try {
                ApplicationInfo applicationInfo = getApplicationInfo(context.getApplicationContext());
                if (applicationInfo == null) {
                    return jsonObject;
                }
                apkPath = applicationInfo.sourceDir;
            } catch (Throwable e) {
            }

            if (TextUtils.isEmpty(apkPath)) {
                return jsonObject;
            }


            ByteBuffer channelBlock = PayloadReader.read(apkPath, APK_CHANNEL_BLOCK_ID);

            if (channelBlock == null) {
                return null;
            }

            final byte[] array = channelBlock.array();
            final int arrayOffset = channelBlock.arrayOffset();
            byte[] bytes = Arrays.copyOfRange(array, arrayOffset + channelBlock.position(),
                    arrayOffset + channelBlock.limit());

            jsonObject = new JSONObject(new String(bytes));
        } catch (JSONException ignore) {
        } finally {

        }

        return jsonObject;
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
