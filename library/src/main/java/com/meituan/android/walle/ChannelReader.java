package com.meituan.android.walle;

import android.apksigner.core.apk.ApkUtils;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.apksigner.core.internal.util.ByteBufferDataSource;
import com.android.apksigner.core.internal.util.Pair;
import com.android.apksigner.core.util.DataSource;
import com.android.apksigner.core.zip.ZipFormatException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ChannelReader {
    private static final int APK_CHANNEL_BLOCK_ID = 0x71777777;
    private static final long APK_SIG_BLOCK_MAGIC_HI = 0x3234206b636f6c42L;
    private static final long APK_SIG_BLOCK_MAGIC_LO = 0x20676953204b5041L;
    private static final int APK_SIG_BLOCK_MIN_SIZE = 32;

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

            FileInputStream fIn = null;
            FileChannel fChan = null;
            long fSize;
            ByteBuffer byteBuffer;

            try {
                fIn = new FileInputStream(new File(apkPath));
                fChan = fIn.getChannel();
                fSize = fChan.size();
                // FIXME: 这里直接申请这么多太浪费了，需要考虑使用RandomAccessFile来读取一小部分数据
                byteBuffer = ByteBuffer.allocate((int) fSize);
                fChan.read(byteBuffer);
                byteBuffer.rewind();


                DataSource apk = new ByteBufferDataSource(byteBuffer);
                ApkUtils.ZipSections zipSections = ApkUtils.findZipSections(apk);

                long centralDirStartOffset = zipSections.getZipCentralDirectoryOffset();
                long centralDirEndOffset =
                        centralDirStartOffset + zipSections.getZipCentralDirectorySizeBytes();
                long eocdStartOffset = zipSections.getZipEndOfCentralDirectoryOffset();
                if (centralDirEndOffset != eocdStartOffset) {
                    return jsonObject;
                }

                // Find the APK Signing Block. The block immediately precedes the Central Directory.
                ByteBuffer eocd = zipSections.getZipEndOfCentralDirectory();
                Pair<ByteBuffer, Long> apkSigningBlockAndOffset =
                        findApkSigningBlock(apk, centralDirStartOffset);
                ByteBuffer apkSigningBlock2 = apkSigningBlockAndOffset.getFirst();

                Map<Integer, ByteBuffer> idValues = findIdValues(apkSigningBlock2);
                ByteBuffer channelBlock = idValues.get(APK_CHANNEL_BLOCK_ID);

                final byte[] array = channelBlock.array();
                final int arrayOffset = channelBlock.arrayOffset();
                byte[] bytes = Arrays.copyOfRange(array, arrayOffset + channelBlock.position(),
                        arrayOffset + channelBlock.limit());

                jsonObject = new JSONObject(new String(bytes));
            } catch (IOException ignore) {
            } catch (JSONException ignore) {
            } finally {
                try {
                    if (fChan != null) {
                        fChan.close();
                        fChan = null;
                    }
                } catch (IOException ignore) {
                }
                try {
                    if (fIn != null) {
                        fIn.close();
                        fIn = null;
                    }
                } catch (IOException ignore) {
                }
            }
        } catch (ZipFormatException ignore) {
        } catch (SignatureNotFoundException ignore) {
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

    private static Pair<ByteBuffer, Long> findApkSigningBlock(
            DataSource apk, long centralDirOffset) throws IOException, SignatureNotFoundException {
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes payload
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic

        if (centralDirOffset < APK_SIG_BLOCK_MIN_SIZE) {
            throw new SignatureNotFoundException(
                    "APK too small for APK Signing Block. ZIP Central Directory offset: "
                            + centralDirOffset);
        }
        // Read the magic and offset in file from the footer section of the block:
        // * uint64:   size of block
        // * 16 bytes: magic
        ByteBuffer footer = apk.getByteBuffer(centralDirOffset - 24, 24);
        footer.order(ByteOrder.LITTLE_ENDIAN);
        if ((footer.getLong(8) != APK_SIG_BLOCK_MAGIC_LO)
                || (footer.getLong(16) != APK_SIG_BLOCK_MAGIC_HI)) {
            throw new SignatureNotFoundException(
                    "No APK Signing Block before ZIP Central Directory");
        }
        // Read and compare size fields
        long apkSigBlockSizeInFooter = footer.getLong(0);
        if ((apkSigBlockSizeInFooter < footer.capacity())
                || (apkSigBlockSizeInFooter > Integer.MAX_VALUE - 8)) {
            throw new SignatureNotFoundException(
                    "APK Signing Block size out of range: " + apkSigBlockSizeInFooter);
        }
        int totalSize = (int) (apkSigBlockSizeInFooter + 8);
        long apkSigBlockOffset = centralDirOffset - totalSize;
        if (apkSigBlockOffset < 0) {
            throw new SignatureNotFoundException(
                    "APK Signing Block offset out of range: " + apkSigBlockOffset);
        }
        ByteBuffer apkSigBlock = apk.getByteBuffer(apkSigBlockOffset, totalSize);
        apkSigBlock.order(ByteOrder.LITTLE_ENDIAN);
        long apkSigBlockSizeInHeader = apkSigBlock.getLong(0);
        if (apkSigBlockSizeInHeader != apkSigBlockSizeInFooter) {
            throw new SignatureNotFoundException(
                    "APK Signing Block sizes in header and footer do not match: "
                            + apkSigBlockSizeInHeader + " vs " + apkSigBlockSizeInFooter);
        }
        return Pair.of(apkSigBlock, apkSigBlockOffset);
    }

    private static Map<Integer, ByteBuffer> findIdValues(ByteBuffer apkSigningBlock) throws SignatureNotFoundException {
        checkByteOrderLittleEndian(apkSigningBlock);
        // FORMAT:
        // OFFSET       DATA TYPE  DESCRIPTION
        // * @+0  bytes uint64:    size in bytes (excluding this field)
        // * @+8  bytes pairs
        // * @-24 bytes uint64:    size in bytes (same as the one above)
        // * @-16 bytes uint128:   magic
        ByteBuffer pairs = sliceFromTo(apkSigningBlock, 8, apkSigningBlock.capacity() - 24);

        Map<Integer, ByteBuffer> idValues = new HashMap<Integer, ByteBuffer>();

        int entryCount = 0;
        while (pairs.hasRemaining()) {
            entryCount++;
            if (pairs.remaining() < 8) {
                throw new SignatureNotFoundException(
                        "Insufficient data to read size of APK Signing Block entry #" + entryCount);
            }
            long lenLong = pairs.getLong();
            if ((lenLong < 4) || (lenLong > Integer.MAX_VALUE)) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount
                                + " size out of range: " + lenLong);
            }
            int len = (int) lenLong;
            int nextEntryPos = pairs.position() + len;
            if (len > pairs.remaining()) {
                throw new SignatureNotFoundException(
                        "APK Signing Block entry #" + entryCount + " size out of range: " + len
                                + ", available: " + pairs.remaining());
            }
            int id = pairs.getInt();
            idValues.put(id, getByteBuffer(pairs, len - 4));

            pairs.position(nextEntryPos);
        }

        if (idValues.isEmpty()) {
            throw new SignatureNotFoundException(
                    "No APK Signature Scheme v2 block in APK Signing Block");
        } else {
            return idValues;
        }
    }

    /**
     * Returns new byte buffer whose content is a shared subsequence of this buffer's content
     * between the specified start (inclusive) and end (exclusive) positions. As opposed to
     * {@link ByteBuffer#slice()}, the returned buffer's byte order is the same as the source
     * buffer's byte order.
     */
    private static ByteBuffer sliceFromTo(ByteBuffer source, int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end < start: " + end + " < " + start);
        }
        int capacity = source.capacity();
        if (end > source.capacity()) {
            throw new IllegalArgumentException("end > capacity: " + end + " > " + capacity);
        }
        int originalLimit = source.limit();
        int originalPosition = source.position();
        try {
            source.position(0);
            source.limit(end);
            source.position(start);
            ByteBuffer result = source.slice();
            result.order(source.order());
            return result;
        } finally {
            source.position(0);
            source.limit(originalLimit);
            source.position(originalPosition);
        }
    }

    /**
     * Relative <em>get</em> method for reading {@code size} number of bytes from the current
     * position of this buffer.
     * <p>
     * <p>This method reads the next {@code size} bytes at this buffer's current position,
     * returning them as a {@code ByteBuffer} with start set to 0, limit and capacity set to
     * {@code size}, byte order set to this buffer's byte order; and then increments the position by
     * {@code size}.
     */
    private static ByteBuffer getByteBuffer(ByteBuffer source, int size)
            throws BufferUnderflowException {
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size);
        }
        int originalLimit = source.limit();
        int position = source.position();
        int limit = position + size;
        if ((limit < position) || (limit > originalLimit)) {
            throw new BufferUnderflowException();
        }
        source.limit(limit);
        try {
            ByteBuffer result = source.slice();
            result.order(source.order());
            source.position(limit);
            return result;
        } finally {
            source.limit(originalLimit);
        }
    }

    private static void checkByteOrderLittleEndian(ByteBuffer buffer) {
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer byte order must be little endian");
        }
    }

    private static class SignatureNotFoundException extends Exception {
        private static final long serialVersionUID = 1L;

        public SignatureNotFoundException(String message) {
            super(message);
        }

        public SignatureNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
