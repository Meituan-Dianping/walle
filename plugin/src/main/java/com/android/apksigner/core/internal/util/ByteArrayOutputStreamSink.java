/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apksigner.core.internal.util;

import com.android.apksigner.core.util.DataSink;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Data sink which stores all input data into an internal {@link ByteArrayOutputStream}, thus
 * accepting an arbitrary amount of data.
 */
public class ByteArrayOutputStreamSink implements DataSink {

    private final ByteArrayOutputStream mBuf = new ByteArrayOutputStream();

    @Override
    public void consume(byte[] buf, int offset, int length) {
        mBuf.write(buf, offset, length);
    }

    @Override
    public void consume(ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            return;
        }

        if (buf.hasArray()) {
            mBuf.write(
                    buf.array(),
                    buf.arrayOffset() + buf.position(),
                    buf.remaining());
            buf.position(buf.limit());
        } else {
            byte[] tmp = new byte[buf.remaining()];
            buf.get(tmp);
            mBuf.write(tmp, 0, tmp.length);
        }
    }

    /**
     * Returns the data received so far.
     */
    public byte[] getData() {
        return mBuf.toByteArray();
    }
}
