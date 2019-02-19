package com.meituan.android.walle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public final class PayloadWriter {
    private PayloadWriter() {
        super();
    }

    /**
     * put (id, String) into apk, update if id exists
     * @param apkFile apk file
     * @param id id
     * @param string string content
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final int id, final String string) throws IOException, SignatureNotFoundException {
        put(apkFile, id, string, false);
    }
    /**
     * put (id, String) into apk, update if id exists
     * @param apkFile apk file
     * @param id id
     * @param string string
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final int id, final String string, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        final byte[] bytes = string.getBytes(ApkUtil.DEFAULT_CHARSET);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes, 0, bytes.length);
        byteBuffer.flip();
        put(apkFile, id, byteBuffer, lowMemory);
    }
    /**
     * put (id, buffer) into apk, update if id exists
     *
     * @param apkFile apk file
     * @param id      id
     * @param buffer  buffer
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final int id, final ByteBuffer buffer) throws IOException, SignatureNotFoundException {
        put(apkFile, id, buffer, false);
    }

    /**
     * put (id, buffer) into apk, update if id exists
     * @param apkFile apk file
     * @param id id
     * @param buffer buffer
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void put(final File apkFile, final int id, final ByteBuffer buffer, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        final Map<Integer, ByteBuffer> idValues = new HashMap<Integer, ByteBuffer>();
        idValues.put(id, buffer);
        putAll(apkFile, idValues, lowMemory);
    }
    /**
     * put new idValues into apk, update if id exists
     *
     * @param apkFile  apk file
     * @param idValues id value. NOTE: use unknown IDs. DO NOT use ID that have already been used.  See <a href='https://source.android.com/security/apksigning/v2.html'>APK Signature Scheme v2</a>
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void putAll(final File apkFile, final Map<Integer, ByteBuffer> idValues) throws IOException, SignatureNotFoundException {
        putAll(apkFile, idValues, false);
    }
    /**
     * put new idValues into apk, update if id exists
     *
     * @param apkFile  apk file
     * @param idValues id value. NOTE: use unknown IDs. DO NOT use ID that have already been used.  See <a href='https://source.android.com/security/apksigning/v2.html'>APK Signature Scheme v2</a>
     * @param lowMemory if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void putAll(final File apkFile, final Map<Integer, ByteBuffer> idValues, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        handleApkSigningBlock(apkFile, new ApkSigningBlockHandler() {
            @Override
            public ApkSigningBlock handle(final Map<Integer, ByteBuffer> originIdValues) {
                if (idValues != null && !idValues.isEmpty()) {
                    originIdValues.putAll(idValues);
                }
                final ApkSigningBlock apkSigningBlock = new ApkSigningBlock();
                final Set<Map.Entry<Integer, ByteBuffer>> entrySet = originIdValues.entrySet();
                for (Map.Entry<Integer, ByteBuffer> entry : entrySet) {
                    final ApkSigningPayload payload = new ApkSigningPayload(entry.getKey(), entry.getValue());
                    apkSigningBlock.addPayload(payload);
                }
                return apkSigningBlock;
            }
        }, lowMemory);
    }
    /**
     * remove content by id
     *
     * @param apkFile apk file
     * @param id id
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void remove(final File apkFile, final int id) throws IOException, SignatureNotFoundException {
        remove(apkFile, id, false);
    }
    /**
     * remove content by id
     *
     * @param apkFile apk file
     * @param id id
     * @param lowMemory  if need low memory operation, maybe a little slower
     * @throws IOException
     * @throws SignatureNotFoundException
     */
    public static void remove(final File apkFile, final int id, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        PayloadWriter.handleApkSigningBlock(apkFile, new PayloadWriter.ApkSigningBlockHandler() {
            @Override
            public ApkSigningBlock handle(final Map<Integer, ByteBuffer> originIdValues) {
                final ApkSigningBlock apkSigningBlock = new ApkSigningBlock();
                final Set<Map.Entry<Integer, ByteBuffer>> entrySet = originIdValues.entrySet();
                for (Map.Entry<Integer, ByteBuffer> entry : entrySet) {
                    if (entry.getKey() != id) {
                        final ApkSigningPayload payload = new ApkSigningPayload(entry.getKey(), entry.getValue());
                        apkSigningBlock.addPayload(payload);
                    }
                }
                return apkSigningBlock;
            }
        }, lowMemory);
    }

    interface ApkSigningBlockHandler {
        ApkSigningBlock handle(Map<Integer, ByteBuffer> originIdValues);
    }

    static void handleApkSigningBlock(final File apkFile, final ApkSigningBlockHandler handler, final boolean lowMemory) throws IOException, SignatureNotFoundException {
        RandomAccessFile fIn = null;
        FileChannel fileChannel = null;
        try {
            fIn = new RandomAccessFile(apkFile, "rw");
            fileChannel = fIn.getChannel();
            final long commentLength = ApkUtil.getCommentLength(fileChannel);
            final long centralDirStartOffset = ApkUtil.findCentralDirStartOffset(fileChannel, commentLength);
            // Find the APK Signing Block. The block immediately precedes the Central Directory.
            final Pair<ByteBuffer, Long> apkSigningBlockAndOffset = ApkUtil.findApkSigningBlock(fileChannel, centralDirStartOffset);
            final ByteBuffer apkSigningBlock2 = apkSigningBlockAndOffset.getFirst();
            final long apkSigningBlockOffset = apkSigningBlockAndOffset.getSecond();

            final Map<Integer, ByteBuffer> originIdValues = ApkUtil.findIdValues(apkSigningBlock2);
            // Find the APK Signature Scheme v2 Block inside the APK Signing Block.
            final ByteBuffer apkSignatureSchemeV2Block = originIdValues.get(ApkUtil.APK_SIGNATURE_SCHEME_V2_BLOCK_ID);

            if (apkSignatureSchemeV2Block == null) {
                throw new IOException(
                        "No APK Signature Scheme v2 block in APK Signing Block");
            }

            final boolean needPadding = originIdValues.remove(ApkUtil.VERITY_PADDING_BLOCK_ID) != null;
            final ApkSigningBlock apkSigningBlock = handler.handle(originIdValues);
            // replace VERITY_PADDING_BLOCK with new one
            if (needPadding) {
                // uint64:  size (excluding this field)
                // repeated ID-value pairs:
                //     uint64:           size (excluding this field)
                //     uint32:           ID
                //     (size - 4) bytes: value
                // (extra dummy ID-value for padding to make block size a multiple of 4096 bytes)
                // uint64:  size (same as the one above)
                // uint128: magic

                int blocksSize = 0;
                for (ApkSigningPayload payload : apkSigningBlock.getPayloads()) {
                    blocksSize += payload.getTotalSize();
                }

                int resultSize = 8 + blocksSize + 8 + 16; // size(uint64) + pairs size + size(uint64) + magic(uint128)
                if (resultSize % ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES != 0) {
                    int padding = ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES - 12 // size(uint64) + id(uint32)
                            - (resultSize % ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES);
                    if (padding < 0) {
                        padding += ApkUtil.ANDROID_COMMON_PAGE_ALIGNMENT_BYTES;
                    }
                    final ByteBuffer dummy =  ByteBuffer.allocate(padding).order(ByteOrder.LITTLE_ENDIAN);
                    apkSigningBlock.addPayload(new ApkSigningPayload(ApkUtil.VERITY_PADDING_BLOCK_ID,dummy));
                }
            }

            if (apkSigningBlockOffset != 0 && centralDirStartOffset != 0) {

                // read CentralDir
                fIn.seek(centralDirStartOffset);

                byte[] centralDirBytes = null;
                File tempCentralBytesFile = null;
                // read CentralDir
                if (lowMemory) {
                    tempCentralBytesFile = new File(apkFile.getParent(), UUID.randomUUID().toString());
                    FileOutputStream outStream = null;
                    try {
                        outStream = new FileOutputStream(tempCentralBytesFile);
                        final byte[] buffer = new byte[1024];

                        int len;
                        while ((len = fIn.read(buffer)) > 0){
                            outStream.write(buffer, 0, len);
                        }
                    } finally {
                        if (outStream != null) {
                            outStream.close();
                        }
                    }
                } else {
                    centralDirBytes = new byte[(int) (fileChannel.size() - centralDirStartOffset)];
                    fIn.read(centralDirBytes);
                }

                //update apk sign
                fileChannel.position(apkSigningBlockOffset);
                final long length = apkSigningBlock.writeApkSigningBlock(fIn);

                // update CentralDir
                if (lowMemory) {
                    FileInputStream inputStream = null;
                    try {
                        inputStream = new FileInputStream(tempCentralBytesFile);
                        final byte[] buffer = new byte[1024];

                        int len;
                        while ((len = inputStream.read(buffer)) > 0){
                            fIn.write(buffer, 0, len);
                        }
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        tempCentralBytesFile.delete();
                    }
                } else {
                    // store CentralDir
                    fIn.write(centralDirBytes);
                }
                // update length
                fIn.setLength(fIn.getFilePointer());

                // update CentralDir Offset

                // End of central directory record (EOCD)
                // Offset     Bytes     Description[23]
                // 0            4       End of central directory signature = 0x06054b50
                // 4            2       Number of this disk
                // 6            2       Disk where central directory starts
                // 8            2       Number of central directory records on this disk
                // 10           2       Total number of central directory records
                // 12           4       Size of central directory (bytes)
                // 16           4       Offset of start of central directory, relative to start of archive
                // 20           2       Comment length (n)
                // 22           n       Comment

                fIn.seek(fileChannel.size() - commentLength - 6);
                // 6 = 2(Comment length) + 4 (Offset of start of central directory, relative to start of archive)
                final ByteBuffer temp = ByteBuffer.allocate(4);
                temp.order(ByteOrder.LITTLE_ENDIAN);
                temp.putInt((int) (centralDirStartOffset + length + 8 - (centralDirStartOffset - apkSigningBlockOffset)));
                // 8 = size of block in bytes (excluding this field) (uint64)
                temp.flip();
                fIn.write(temp.array());

            }
        } finally {
            if (fileChannel != null) {
                fileChannel.close();
            }
            if (fIn != null) {
                fIn.close();
            }
        }
    }
}
