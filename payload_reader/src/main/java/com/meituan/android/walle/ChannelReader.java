package com.meituan.android.walle;

import com.meituan.android.walle.internal.ApkUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ChannelReader {
    public static final String CHANNEL_KEY = "channel";

    /**
     * easy api for get channel & extra info.<br/>
     * @param apkFile apk file
     * @return null if not found
     */
    public static ChannelInfo get(File apkFile) {
        Map<String, String> result = getMap(apkFile);
        if (result == null) {
            return null;
        }
        String channel = result.get(CHANNEL_KEY);
        result.remove(CHANNEL_KEY);
        return new ChannelInfo(channel, result);
    }
    /**
     * get channel & extra info by map, use {@link ChannelReader#CHANNEL_KEY PayloadReader.CHANNEL_KEY} get channel
     * @param apkFile apk file
     * @return null if not found
     */
    public static Map<String, String> getMap(File apkFile) {
        try {
            String rawString = getRaw(apkFile);
            if (rawString == null) {
                return null;
            }
            JSONObject jsonObject = new JSONObject(rawString);
            Iterator keys =  jsonObject.keys();
            Map<String, String> result = new HashMap<String, String>();
            while(keys.hasNext()) {
                String key = keys.next().toString();
                result.put(key, jsonObject.getString(key));
            }
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
    /**
     * get raw string from channel id
     *
     * @param apkFile apk file
     * @return null if not found
     */
    public static String getRaw(File apkFile) {
        byte[] bytes = PayloadReader.get(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID);
        if (bytes == null) {
            return null;
        }
        try {
            return new String(bytes, ApkUtil.DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
