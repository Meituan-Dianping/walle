package com.meituan.android.walle;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public final class ChannelWriter {
    private ChannelWriter() {
        super();
    }

    /**
     * write channel with channel fixed id
     *
     * @param apkFile apk file
     * @param channel channel
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final String channel) throws IOException, SignatureNotFoundException {
        put(apkFile, channel, false);
    }
    /**
     * write channel with channel fixed id
     *
     * @param apkFile apk file
     * @param channel channel
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final String channel, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        put(apkFile, channel, null, lowMemory);
    }
    /**
     * write channel & extra info with channel fixed id
     *
     * @param apkFile   apk file
     * @param channel   channel （nullable)
     * @param extraInfo extra info (don't use {@link ChannelReader#CHANNEL_KEY PayloadReader.CHANNEL_KEY} as your key)
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final String channel, final Map<String, String> extraInfo) throws IOException, SignatureNotFoundException {
        put(apkFile, channel, extraInfo, false);
    }
    /**
     * write channel & extra info with channel fixed id
     *
     * @param apkFile   apk file
     * @param channel   channel （nullable)
     * @param extraInfo extra info (don't use {@link ChannelReader#CHANNEL_KEY PayloadReader.CHANNEL_KEY} as your key)
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final String channel, final Map<String, String> extraInfo, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        final Map<String, String> newData = new HashMap<String, String>();
        final Map<String, String> existsData = ChannelReader.getMap(apkFile);
        if (existsData != null) {
            newData.putAll(existsData);
        }
        if (extraInfo != null) {
            // can't use
            extraInfo.remove(ChannelReader.CHANNEL_KEY);
            newData.putAll(extraInfo);
        }
        if (channel != null && channel.length() > 0) {
            newData.put(ChannelReader.CHANNEL_KEY, channel);
        }
        final JSONObject jsonObject = new JSONObject(newData);
        putRaw(apkFile, jsonObject.toString(), lowMemory);
    }
    /**
     * write custom content with channel fixed id <br/>
     * NOTE: {@link ChannelReader#get(File)}  and {@link ChannelReader#getMap(File)}  may be affected
     *
     * @param apkFile apk file
     * @param string  custom content
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void putRaw(final File apkFile, final String string) throws IOException, SignatureNotFoundException {
        putRaw(apkFile, string, false);
    }
    /**
     * write custom content with channel fixed id<br/>
     * NOTE: {@link ChannelReader#get(File)}  and {@link ChannelReader#getMap(File)}  may be affected
     *
     * @param apkFile apk file
     * @param string  custom content
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void putRaw(final File apkFile, final String string, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        PayloadWriter.put(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID, string, lowMemory);
    }
    /**
     * remove channel id content
     *
     * @param apkFile apk file
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void remove(final File apkFile) throws IOException, SignatureNotFoundException {
        remove(apkFile, false);
    }
    /**
     * remove channel id content
     *
     * @param apkFile apk file
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void remove(final File apkFile, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        PayloadWriter.remove(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID, lowMemory);
    }
}
