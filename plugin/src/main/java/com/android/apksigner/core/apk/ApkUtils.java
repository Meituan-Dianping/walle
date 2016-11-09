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

package com.android.apksigner.core.apk;

import com.android.apksigner.core.internal.util.Pair;
import com.android.apksigner.core.internal.zip.ZipUtils;
import com.android.apksigner.core.util.DataSource;
import com.android.apksigner.core.zip.ZipFormatException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * APK utilities.
 */
public class ApkUtils {

    private ApkUtils() {}

    /**
     * Finds the main ZIP sections of the provided APK.
     *
     * @throws IOException if an I/O error occurred while reading the APK
     * @throws ZipFormatException if the APK is malformed
     */
    public static ZipSections findZipSections(DataSource apk)
            throws IOException, ZipFormatException {
        Pair<ByteBuffer, Long> eocdAndOffsetInFile =
                ZipUtils.findZipEndOfCentralDirectoryRecord(apk);
        if (eocdAndOffsetInFile == null) {
            throw new ZipFormatException("ZIP End of Central Directory record not found");
        }

        ByteBuffer eocdBuf = eocdAndOffsetInFile.getFirst();
        long eocdOffset = eocdAndOffsetInFile.getSecond();
        if (ZipUtils.isZip64EndOfCentralDirectoryLocatorPresent(apk, eocdOffset)) {
            throw new ZipFormatException("ZIP64 APK not supported");
        }
        eocdBuf.order(ByteOrder.LITTLE_ENDIAN);
        long cdStartOffset = ZipUtils.getZipEocdCentralDirectoryOffset(eocdBuf);
        if (cdStartOffset >= eocdOffset) {
            throw new ZipFormatException(
                    "ZIP Central Directory start offset out of range: " + cdStartOffset
                        + ". ZIP End of Central Directory offset: " + eocdOffset);
        }

        long cdSizeBytes = ZipUtils.getZipEocdCentralDirectorySizeBytes(eocdBuf);
        long cdEndOffset = cdStartOffset + cdSizeBytes;
        if (cdEndOffset > eocdOffset) {
            throw new ZipFormatException(
                    "ZIP Central Directory overlaps with End of Central Directory"
                            + ". CD end: " + cdEndOffset
                            + ", EoCD start: " + eocdOffset);
        }

        int cdRecordCount = ZipUtils.getZipEocdCentralDirectoryTotalRecordCount(eocdBuf);

        return new ZipSections(
                cdStartOffset,
                cdSizeBytes,
                cdRecordCount,
                eocdOffset,
                eocdBuf);
    }

    /**
     * Information about the ZIP sections of an APK.
     */
    public static class ZipSections {
        private final long mCentralDirectoryOffset;
        private final long mCentralDirectorySizeBytes;
        private final int mCentralDirectoryRecordCount;
        private final long mEocdOffset;
        private final ByteBuffer mEocd;

        public ZipSections(
                long centralDirectoryOffset,
                long centralDirectorySizeBytes,
                int centralDirectoryRecordCount,
                long eocdOffset,
                ByteBuffer eocd) {
            mCentralDirectoryOffset = centralDirectoryOffset;
            mCentralDirectorySizeBytes = centralDirectorySizeBytes;
            mCentralDirectoryRecordCount = centralDirectoryRecordCount;
            mEocdOffset = eocdOffset;
            mEocd = eocd;
        }

        /**
         * Returns the start offset of the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public long getZipCentralDirectoryOffset() {
            return mCentralDirectoryOffset;
        }

        /**
         * Returns the size (in bytes) of the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public long getZipCentralDirectorySizeBytes() {
            return mCentralDirectorySizeBytes;
        }

        /**
         * Returns the number of records in the ZIP Central Directory. This value is taken from the
         * ZIP End of Central Directory record.
         */
        public int getZipCentralDirectoryRecordCount() {
            return mCentralDirectoryRecordCount;
        }

        /**
         * Returns the start offset of the ZIP End of Central Directory record. The record extends
         * until the very end of the APK.
         */
        public long getZipEndOfCentralDirectoryOffset() {
            return mEocdOffset;
        }

        /**
         * Returns the contents of the ZIP End of Central Directory.
         */
        public ByteBuffer getZipEndOfCentralDirectory() {
            return mEocd;
        }
    }

    /**
     * Sets the offset of the start of the ZIP Central Directory in the APK's ZIP End of Central
     * Directory record.
     *
     * @param zipEndOfCentralDirectory APK's ZIP End of Central Directory record
     * @param offset offset of the ZIP Central Directory relative to the start of the archive. Must
     *        be between {@code 0} and {@code 2^32 - 1} inclusive.
     */
    public static void setZipEocdCentralDirectoryOffset(
            ByteBuffer zipEndOfCentralDirectory, long offset) {
        ByteBuffer eocd = zipEndOfCentralDirectory.slice();
        eocd.order(ByteOrder.LITTLE_ENDIAN);
        ZipUtils.setZipEocdCentralDirectoryOffset(eocd, offset);
    }
}
