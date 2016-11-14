package com.meituan.android.walle;

import com.meituan.android.walle.internal.Pair;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class PayloadWriter {
    static final long APK_SIG_BLOCK_MAGIC_HI = 0x3234206b636f6c42L;
    static final long APK_SIG_BLOCK_MAGIC_LO = 0x20676953204b5041L;
    private static final int APK_SIG_BLOCK_MIN_SIZE = 32;

    public static final int APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a;

    public static boolean write(String apkFile, int id, ByteBuffer buffer) {
        Map<Integer, ByteBuffer> idValues = new HashMap<Integer, ByteBuffer>();
        idValues.put(id, buffer);
        return writeIDValuePairs(apkFile, idValues);
    }

    public static boolean writeIDValuePairs(String apkFile, Map<Integer, ByteBuffer> idValues) {
        if (idValues == null || idValues.isEmpty()) {
            return false;
        }
        RandomAccessFile fIn = null;
        FileChannel fileChannel = null;

        try {
            fIn = new RandomAccessFile(apkFile, "rw");
            fileChannel = fIn.getChannel();
            checkIfHasComment(fileChannel);

            // End of central directory record (EOCD)
            // Offset	Bytes	Description[23]
            // 0	        4	    End of central directory signature = 0x06054b50
            // 4	        2	    Number of this disk
            // 6	        2	    Disk where central directory starts
            // 8	        2	    Number of central directory records on this disk
            // 10	        2	    Total number of central directory records
            // 12	        4	    Size of central directory (bytes)
            // 16	        4	    Offset of start of central directory, relative to start of archive
            // 20	        2	    Comment length (n)
            // 22	        n	    Comment
            // For a zip with no archive comment, the
            // end-of-central-directory record will be 22 bytes long, so
            // we expect to find the EOCD marker 22 bytes from the end.
            ByteBuffer zipEndOfCentralDirectory = ByteBuffer.allocate(4);
            zipEndOfCentralDirectory.order(ByteOrder.LITTLE_ENDIAN);
            fileChannel.position(fileChannel.size() - 6);
            // 6 = 2 (Comment length) + 4 (Offset of start of central directory, relative to start of archive)
            fileChannel.read(zipEndOfCentralDirectory);
            long centralDirStartOffset = zipEndOfCentralDirectory.getInt(0);

            // Find the APK Signing Block. The block immediately precedes the Central Directory.
            Pair<ByteBuffer, Long> apkSigningBlockAndOffset =
                    findApkSigningBlock(fileChannel, centralDirStartOffset);
            ByteBuffer apkSigningBlock2 = apkSigningBlockAndOffset.getFirst();
            long apkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();

            Map<Integer, ByteBuffer> originIdValues = findIdValues(apkSigningBlock2);
            // Find the APK Signature Scheme v2 Block inside the APK Signing Block.
            ByteBuffer apkSignatureSchemeV2Block = originIdValues.get(APK_SIGNATURE_SCHEME_V2_BLOCK_ID);

            if (apkSignatureSchemeV2Block == null) {
                throw new SignatureNotFoundException(
                        "No APK Signature Scheme v2 block in APK Signing Block");
            }

            ApkSigningBlock apkSigningBlock = new ApkSigningBlock();
            ApkSigningPayload payload = new ApkSigningPayload(APK_SIGNATURE_SCHEME_V2_BLOCK_ID, apkSignatureSchemeV2Block);
            apkSigningBlock.addPayload(payload);

            Set<Map.Entry<Integer, ByteBuffer>> entrySet = idValues.entrySet();
            Iterator<Map.Entry<Integer, ByteBuffer>> iterator = entrySet.iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, ByteBuffer> entry = iterator.next();
                payload = new ApkSigningPayload(entry.getKey(), entry.getValue());
                apkSigningBlock.addPayload(payload);
            }

            if (apkSigningBlockOffset != 0 && centralDirStartOffset != 0) {

                // 读取CentralDir
                fIn.seek(centralDirStartOffset);
                byte[] centralDirBytes = new byte[(int) (fileChannel.size() - centralDirStartOffset)];
                fIn.read(centralDirBytes);

                fileChannel.position(apkSigningBlockOffset);

                long length = apkSigningBlock.writeApkSigningBlock(fIn);

                // 存储CentralDir
                fIn.write(centralDirBytes);

                // 更新CentralDir Offset

                // End of central directory record (EOCD)
                // Offset	Bytes	Description[23]
                // 0	        4	    End of central directory signature = 0x06054b50
                // 4	        2	    Number of this disk
                // 6	        2	    Disk where central directory starts
                // 8	        2	    Number of central directory records on this disk
                // 10	        2	    Total number of central directory records
                // 12	        4	    Size of central directory (bytes)
                // 16	        4	    Offset of start of central directory, relative to start of archive
                // 20	        2	    Comment length (n)
                // 22	        n	    Comment

                fIn.seek(fileChannel.size() - 6);
                // 6 = 2(Comment length) + 4 (Offset of start of central directory, relative to start of archive)
                ByteBuffer temp = ByteBuffer.allocate(4);
                temp.order(ByteOrder.LITTLE_ENDIAN);
                temp.putInt((int) (centralDirStartOffset + length + 8 - (centralDirStartOffset - apkSigningBlockOffset)));
                // 8 = size of block in bytes (excluding this field) (uint64)
                temp.flip();
                fIn.write(temp.array());

                return true;
            }
        } catch (IOException ignore) {
            ignore.printStackTrace();
        } catch (SignatureNotFoundException ignore) {
        } finally {
            try {
                if (fileChannel != null) {
                    fileChannel.close();
                }
                if (fIn != null) {
                    fIn.close();
                }
            } catch (IOException e) {
            }
        }
        return false;
    }

    // End of central directory record (EOCD)
    // Offset	Bytes	Description[23]
    // 0	        4	    End of central directory signature = 0x06054b50
    // 4	        2	    Number of this disk
    // 6	        2	    Disk where central directory starts
    // 8	        2	    Number of central directory records on this disk
    // 10	        2	    Total number of central directory records
    // 12	        4	    Size of central directory (bytes)
    // 16	        4	    Offset of start of central directory, relative to start of archive
    // 20	        2	    Comment length (n)
    // 22	        n	    Comment
    // For a zip with no archive comment, the
    // end-of-central-directory record will be 22 bytes long, so
    // we expect to find the EOCD marker 22 bytes from the end.
    private
    static void checkIfHasComment(FileChannel fileChannel) throws IOException, IllegalArgumentException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        fileChannel.position(fileChannel.size() - 22);
        fileChannel.read(byteBuffer);

        if (byteBuffer.get(0) != 0x50 ||
                byteBuffer.get(1) != 0x4b ||
                byteBuffer.get(2) != 0x05 ||
                byteBuffer.get(3) != 0x06) {
            throw new IllegalArgumentException("zip data already has an archive comment");
        }
    }

    private static Pair<ByteBuffer, Long> findApkSigningBlock(
            FileChannel fileChannel, long centralDirOffset) throws IOException, SignatureNotFoundException {
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
        fileChannel.position(centralDirOffset - 24);
        ByteBuffer footer = ByteBuffer.allocate(24);
        fileChannel.read(footer);
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
        fileChannel.position(apkSigBlockOffset);
        ByteBuffer apkSigBlock = ByteBuffer.allocate(totalSize);
        fileChannel.read(apkSigBlock);
        apkSigBlock.order(ByteOrder.LITTLE_ENDIAN);
        long apkSigBlockSizeInHeader = apkSigBlock.getLong(0);
        if (apkSigBlockSizeInHeader != apkSigBlockSizeInFooter) {
            throw new SignatureNotFoundException(
                    "APK Signing Block sizes in header and footer do not match: "
                            + apkSigBlockSizeInHeader + " vs " + apkSigBlockSizeInFooter);
        }
        return Pair.of(apkSigBlock, apkSigBlockOffset);
    }

    private
    static Map<Integer, ByteBuffer> findIdValues(ByteBuffer apkSigningBlock) throws SignatureNotFoundException {
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

        return idValues;
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
