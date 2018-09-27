package com.meituan.android.walle;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * https://source.android.com/security/apksigning/v2.html
 * https://en.wikipedia.org/wiki/Zip_(file_format)
 */
class ApkSigningBlock {
    // The format of the APK Signing Block is as follows (all numeric fields are little-endian):

    // .size of block in bytes (excluding this field) (uint64)
    // .Sequence of uint64-length-prefixed ID-value pairs:
    //   *ID (uint32)
    //   *value (variable-length: length of the pair - 4 bytes)
    // .size of block in bytes—same as the very first field (uint64)
    // .magic “APK Sig Block 42” (16 bytes)

    // FORMAT:
    // OFFSET       DATA TYPE  DESCRIPTION
    // * @+0  bytes uint64:    size in bytes (excluding this field)
    // * @+8  bytes payload
    // * @-24 bytes uint64:    size in bytes (same as the one above)
    // * @-16 bytes uint128:   magic

    // payload 有 8字节的大小，4字节的ID，还有payload的内容组成

    private final List<ApkSigningPayload> payloads;

    ApkSigningBlock() {
        super();

        payloads = new ArrayList<ApkSigningPayload>();
    }

    public final List<ApkSigningPayload> getPayloads() {
        return payloads;
    }

    public void addPayload(final ApkSigningPayload payload) {
        payloads.add(payload);
    }

    public long writeApkSigningBlock(final DataOutput dataOutput) throws IOException {
        long length = 24; // 24 = 8(size of block in bytes—same as the very first field (uint64)) + 16 (magic “APK Sig Block 42” (16 bytes))
        for (int index = 0; index < payloads.size(); ++index) {
            final ApkSigningPayload payload = payloads.get(index);
            final byte[] bytes = payload.getByteBuffer();
            length += 12 + bytes.length; // 12 = 8(uint64-length-prefixed) + 4 (ID (uint32))
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(8); // Long.BYTES
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(length);
        byteBuffer.flip();
        dataOutput.write(byteBuffer.array());

        for (int index = 0; index < payloads.size(); ++index) {
            final ApkSigningPayload payload = payloads.get(index);
            final byte[] bytes = payload.getByteBuffer();

            byteBuffer = ByteBuffer.allocate(8); // Long.BYTES
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.putLong(bytes.length + (8 - 4)); // Long.BYTES - Integer.BYTES
            byteBuffer.flip();
            dataOutput.write(byteBuffer.array());

            byteBuffer = ByteBuffer.allocate(4); // Integer.BYTES
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.putInt(payload.getId());
            byteBuffer.flip();
            dataOutput.write(byteBuffer.array());

            dataOutput.write(bytes);
        }

        byteBuffer = ByteBuffer.allocate(8); // Long.BYTES
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(length);
        byteBuffer.flip();
        dataOutput.write(byteBuffer.array());

        byteBuffer = ByteBuffer.allocate(8); // Long.BYTES
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(ApkUtil.APK_SIG_BLOCK_MAGIC_LO);
        byteBuffer.flip();
        dataOutput.write(byteBuffer.array());

        byteBuffer = ByteBuffer.allocate(8); // Long.BYTES
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(ApkUtil.APK_SIG_BLOCK_MAGIC_HI);
        byteBuffer.flip();
        dataOutput.write(byteBuffer.array());

        return length;
    }
}
