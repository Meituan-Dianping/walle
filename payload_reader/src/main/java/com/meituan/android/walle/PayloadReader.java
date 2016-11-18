package com.meituan.android.walle;

import com.meituan.android.walle.internal.ApkUtil;
import com.meituan.android.walle.internal.SignatureNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class PayloadReader {
    public static final String CHANNEL_KEY = "channel";

    /**
     * easy api for get channel & extra info.<br/>
     * pair first: channel <br/>
     * pair second: extra info
     * @param apkFile apk file
     * @return null if not found
     */
    public static ChannelInfo getChannelInfo(File apkFile) {
        Map<String, String> result = getChannelInfoMap(apkFile);
        if (result == null) {
            return null;
        }
        String channel = result.get(CHANNEL_KEY);
        result.remove(CHANNEL_KEY);
        return new ChannelInfo(channel, result);
    }
    /**
     * get channel & extra info by map, use {@link PayloadReader#CHANNEL_KEY PayloadReader.CHANNEL_KEY} get channel
     * @param apkFile apk file
     * @return null if not found
     */
    public static Map<String, String> getChannelInfoMap(File apkFile) {
        try {
            ByteBuffer byteBuffer = get(apkFile, ApkUtil.APK_CHANNEL_BLOCK_ID);
            if (byteBuffer == null) {
                return null;
            }

            byte[] bytes = getBytes(byteBuffer);

            String channelData = new String(bytes, ApkUtil.DEFAULT_CHARSET);
            String[] info = channelData.split(";");
            Map<String, String> result = new HashMap<>(info.length);
            for (String s : info) {
                String[] keyValues = s.split("=");
                if (keyValues.length == 2) {
                    result.put(keyValues[0], keyValues[1]);
                }
            }
            return result;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * get data from byteBuffer
     * @param byteBuffer buffer
     * @return useful data
     */
    public static byte[] getBytes(ByteBuffer byteBuffer) {
        final byte[] array = byteBuffer.array();
        final int arrayOffset = byteBuffer.arrayOffset();
        return Arrays.copyOfRange(array, arrayOffset + byteBuffer.position(),
                arrayOffset + byteBuffer.limit());
    }

    /**
     * get custom buffer by id <br/>
     * Note: get final from byteBuffer, please use {@link PayloadReader#getBytes getBytes}
     * @param apkFile apk file
     * @param id id
     * @return buffer
     */
    public static ByteBuffer get(File apkFile, int id) {
        Map<Integer, ByteBuffer> idValues = getAll(apkFile);
        if (idValues == null) {
            return null;
        }
        return idValues.get(id);
    }

    /**
     * get all custom (id, buffer) <br/>
     * NOTE 1: exclude {@link ApkUtil#APK_SIGNATURE_SCHEME_V2_BLOCK_ID APK_SIGNATURE_SCHEME_V2_BLOCK_ID} <br/>
     * Note 2: get final from byteBuffer, please use {@link PayloadReader#getBytes getBytes}
     * @param apkFile apk file
     * @return all custom (id, buffer)
     */
    public static Map<Integer, ByteBuffer> getAll(File apkFile) {
        Map<Integer, ByteBuffer> idValues = null;
        try {
            RandomAccessFile randomAccessFile = null;
            FileChannel fileChannel = null;
            try {
                randomAccessFile = new RandomAccessFile(apkFile, "r");
                fileChannel = randomAccessFile.getChannel();
                boolean hasComment = ApkUtil.checkComment(fileChannel);
                if (hasComment) {
                    throw new IllegalArgumentException("zip data already has an archive comment");
                }
                ByteBuffer apkSigningBlock2 = ApkUtil.findApkSigningBlock(fileChannel).getFirst();
                idValues = ApkUtil.findIdValues(apkSigningBlock2);
                idValues.remove(ApkUtil.APK_SIGNATURE_SCHEME_V2_BLOCK_ID);
            } catch (IOException ignore) {
            } finally {
                try {
                    if (fileChannel != null) {
                        fileChannel.close();
                    }
                } catch (IOException ignore) {
                }
                try {
                    if (randomAccessFile != null) {
                        randomAccessFile.close();
                    }
                } catch (IOException ignore) {
                }
            }
        } catch (SignatureNotFoundException ignore) {
        }

        return idValues;
    }


}
