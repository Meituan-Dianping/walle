package com.meituan.android.walle;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


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
        put(apkFile, channel, null);
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
        putRaw(apkFile, jsonObject.toString());
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
        final byte[] bytes = string.getBytes(ApkUtil.DEFAULT_CHARSET);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes, 0, bytes.length);
        byteBuffer.flip();
        PayloadWriter.put(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID, byteBuffer);
    }

    /**
     * remove channel id content
     *
     * @param apkFile apk file
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void remove(final File apkFile) throws IOException, SignatureNotFoundException {
        PayloadWriter.handleApkSigningBlock(apkFile, new PayloadWriter.ApkSigningBlockHandler() {
            @Override
            public ApkSigningBlock handle(final Map<Integer, ByteBuffer> originIdValues) {
                final ApkSigningBlock apkSigningBlock = new ApkSigningBlock();
                final Set<Map.Entry<Integer, ByteBuffer>> entrySet = originIdValues.entrySet();
                for (Map.Entry<Integer, ByteBuffer> entry : entrySet) {
                    if (entry.getKey() != ApkUtil.APK_CHANNEL_BLOCK_ID) {
                        final ApkSigningPayload payload = new ApkSigningPayload(entry.getKey(), entry.getValue());
                        apkSigningBlock.addPayload(payload);
                    }
                }
                return apkSigningBlock;
            }
        });
    }

}
