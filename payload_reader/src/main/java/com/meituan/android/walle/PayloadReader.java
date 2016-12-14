package com.meituan.android.walle;

import com.meituan.android.walle.internal.ApkUtil;
import com.meituan.android.walle.internal.SignatureNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Map;


public class PayloadReader {
    /**
     * get bytes by id <br/>
     *
     * @param apkFile apk file
     * @param id id
     * @return bytes
     */
    public static byte[] get(File apkFile, int id) {
        Map<Integer, ByteBuffer> idValues = getAll(apkFile);
        if (idValues == null) {
            return null;
        }
        ByteBuffer byteBuffer = idValues.get(id);
        if (byteBuffer == null) {
            return null;
        }
        return getBytes(byteBuffer);
    }

    /**
     * get data from byteBuffer
     * @param byteBuffer buffer
     * @return useful data
     */
    private static byte[] getBytes(ByteBuffer byteBuffer) {
        final byte[] array = byteBuffer.array();
        final int arrayOffset = byteBuffer.arrayOffset();
        return Arrays.copyOfRange(array, arrayOffset + byteBuffer.position(),
                arrayOffset + byteBuffer.limit());
    }
    /**
     * get all custom (id, buffer) <br/>
     * Note: get final from byteBuffer, please use {@link PayloadReader#getBytes getBytes}
     * @param apkFile apk file
     * @return all custom (id, buffer)
     */
    private static Map<Integer, ByteBuffer> getAll(File apkFile) {
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
