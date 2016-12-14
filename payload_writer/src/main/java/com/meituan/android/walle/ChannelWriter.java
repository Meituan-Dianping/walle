package com.meituan.android.walle;

import com.meituan.android.walle.internal.ApkUtil;
import com.meituan.android.walle.internal.SignatureNotFoundException;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class ChannelWriter {
    /**
     * write channel with channel fixed id
     *
     * @param apkFile apk file
     * @param channel channel
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(File apkFile, String channel) throws IOException, SignatureNotFoundException {
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
    public static void put(File apkFile, String channel, Map<String, String> extraInfo) throws IOException, SignatureNotFoundException {
        Map<String, String> newData = new HashMap<>();
        Map<String, String> existsData = ChannelReader.getMap(apkFile);
        if (existsData != null) {
            newData.putAll(existsData);
        }
        if (extraInfo != null) {
            extraInfo.remove(ChannelReader.CHANNEL_KEY);// can't use
            newData.putAll(extraInfo);
        }
        if (channel != null && channel.length() > 0) {
            newData.put(ChannelReader.CHANNEL_KEY, channel);
        }
        JSONObject jsonObject = new JSONObject(newData);
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
    public static void putRaw(File apkFile, String string) throws IOException, SignatureNotFoundException {
        byte[] bytes = string.getBytes(ApkUtil.DEFAULT_CHARSET);
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
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
    public static void remove(File apkFile) throws IOException, SignatureNotFoundException {
        PayloadWriter.handleApkSigningBlock(apkFile, new PayloadWriter.ApkSigningBlockHandler() {
            @Override
            public ApkSigningBlock handle(Map<Integer, ByteBuffer> originIdValues) {
                ApkSigningBlock apkSigningBlock = new ApkSigningBlock();
                Set<Map.Entry<Integer, ByteBuffer>> entrySet = originIdValues.entrySet();
                for (Map.Entry<Integer, ByteBuffer> entry : entrySet) {
                    if (entry.getKey() != ApkUtil.APK_CHANNEL_BLOCK_ID) {
                        ApkSigningPayload payload = new ApkSigningPayload(entry.getKey(), entry.getValue());
                        apkSigningBlock.addPayload(payload);
                    }
                }
                return apkSigningBlock;
            }
        });
    }

}
