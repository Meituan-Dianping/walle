package com.meituan.android.walle;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Map;

public final class PayloadReader {
    private PayloadReader() {
        super();
    }

    /**
     * get string (UTF-8) by id
     *
     * @param apkFile apk file
     * @return null if not found
     */
    public static String getString(final File apkFile, final int id) {
        final byte[] bytes = PayloadReader.get(apkFile, id);
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

    /**
     * get bytes by id <br/>
     *
     * @param apkFile apk file
     * @param id      id
     * @return bytes
     */
    public static byte[] get(final File apkFile, final int id) {
        final Map<Integer, ByteBuffer> idValues = getAll(apkFile);
        if (idValues == null) {
            return null;
        }
        final ByteBuffer byteBuffer = idValues.get(id);
        if (byteBuffer == null) {
            return null;
        }
        return getBytes(byteBuffer);
    }

    /**
     * get data from byteBuffer
     *
     * @param byteBuffer buffer
     * @return useful data
     */
    private static byte[] getBytes(final ByteBuffer byteBuffer) {
        final byte[] array = byteBuffer.array();
        final int arrayOffset = byteBuffer.arrayOffset();
        return Arrays.copyOfRange(array, arrayOffset + byteBuffer.position(),
                arrayOffset + byteBuffer.limit());
    }

    /**
     * get all custom (id, buffer) <br/>
     * Note: get final from byteBuffer, please use {@link PayloadReader#getBytes getBytes}
     *
     * @param apkFile apk file
     * @return all custom (id, buffer)
     */
    private static Map<Integer, ByteBuffer> getAll(final File apkFile) {
        Map<Integer, ByteBuffer> idValues = null;
        try {
            RandomAccessFile randomAccessFile = null;
            FileChannel fileChannel = null;
            try {
                randomAccessFile = new RandomAccessFile(apkFile, "r");
                fileChannel = randomAccessFile.getChannel();
                final ByteBuffer apkSigningBlock2 = ApkUtil.findApkSigningBlock(fileChannel).getFirst();
                idValues = ApkUtil.findIdValues(apkSigningBlock2);
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
