package com.meituan.android.walle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

class ApkSigningPayload {
    private final int id;
    private final ByteBuffer buffer;
    private final int totalSize;

    ApkSigningPayload(final int id, final ByteBuffer buffer) {
        super();
        this.id = id;
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalArgumentException("ByteBuffer byte order must be little endian");
        }
        this.buffer = buffer;
        // assume buffer is not consumed
        this.totalSize = 8 + 4 + buffer.remaining(); // size + id + value
    }

    public int getId() {
        return id;
    }

    public byte[] getByteBuffer() {
        final byte[] array = buffer.array();
        final int arrayOffset = buffer.arrayOffset();
        return Arrays.copyOfRange(array, arrayOffset + buffer.position(),
                arrayOffset + buffer.limit());
    }

    /**
     * Total bytes of this block
     */
    public int getTotalSize() {
        return totalSize;
    }
}
