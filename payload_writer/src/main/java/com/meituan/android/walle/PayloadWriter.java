package com.meituan.android.walle;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public final class PayloadWriter {
    private PayloadWriter() {
        super();
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
        final Map<Integer, ByteBuffer> idValues = new HashMap<Integer, ByteBuffer>();
        idValues.put(id, buffer);
        putAll(apkFile, idValues);
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
        });
    }

    interface ApkSigningBlockHandler {
        ApkSigningBlock handle(Map<Integer, ByteBuffer> originIdValues);
    }

    static void handleApkSigningBlock(final File apkFile, final ApkSigningBlockHandler handler) throws IOException, SignatureNotFoundException {
        RandomAccessFile fIn = null;
        FileChannel fileChannel = null;
        try {
            fIn = new RandomAccessFile(apkFile, "rw");
            fileChannel = fIn.getChannel();
            final boolean hasComment = ApkUtil.checkComment(fileChannel);
            if (hasComment) {
                throw new IOException("zip data already has an archive comment");
            }

            final long centralDirStartOffset = ApkUtil.findCentralDirStartOffset(fileChannel);
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


            final ApkSigningBlock apkSigningBlock = handler.handle(originIdValues);

            if (apkSigningBlockOffset != 0 && centralDirStartOffset != 0) {

                // read CentralDir
                fIn.seek(centralDirStartOffset);
                final byte[] centralDirBytes = new byte[(int) (fileChannel.size() - centralDirStartOffset)];
                fIn.read(centralDirBytes);

                fileChannel.position(apkSigningBlockOffset);

                final long length = apkSigningBlock.writeApkSigningBlock(fIn);

                // store CentralDir
                fIn.write(centralDirBytes);
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

                fIn.seek(fileChannel.size() - 6);
                // 6 = 2(Comment length) + 4 (Offset of start of central directory, relative to start of archive)
                final ByteBuffer temp = ByteBuffer.allocate(4);
                temp.order(ByteOrder.LITTLE_ENDIAN);
                temp.putInt((int) (centralDirStartOffset + length + 8 - (centralDirStartOffset - apkSigningBlockOffset)));
                // 8 = size of block in bytes (excluding this field) (uint64)
                temp.flip();
                fIn.write(temp.array());

            }
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
    }
}
