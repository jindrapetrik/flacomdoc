/*
 * Copyright (C) 2026 JPEXS.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.jpexs.flash.fla.reader;

import java.io.IOException;
import java.util.Collections;

/**
 * Decodes XFL's shared binary media records without a compound-document
 * intermediate.
 */
public final class FlaMediaPayloads {

    private FlaMediaPayloads() {
    }

    public static FlaRecord bitmap(byte[] bytes) throws IOException {
        BinaryData data = new BinaryData(bytes);
        MfcArchive archive = archive(data);
        FlaRecord payload = FlaRecord.create("bitmapPayload");
        int encoded = bytes.length >= 4 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 ? bytes.length - 16 : 0;
        if (encoded < 0) {
            throw new IOException("Truncated JPEG cache");
        }
        BitmapRecordDecoder.payload(archive, payload, encoded);
        finish(archive);
        return payload;
    }

    public static FlaRecord video(byte[] bytes) throws IOException {
        MfcArchive archive = archive(new BinaryData(bytes));
        FlaRecord payload = FlaRecord.create("videoPayload");
        MediaPayloadRecordDecoder.video(archive, payload);
        finish(archive);
        return payload;
    }

    public static FlaRecord sound(byte[] bytes, FlaRecord descriptor) throws IOException {
        MfcArchive archive = archive(new BinaryData(bytes));
        FlaRecord payload = FlaRecord.create("soundPayload");
        MediaPayloadRecordDecoder.sound(archive, payload, descriptor);
        finish(archive);
        return payload;
    }

    private static MfcArchive archive(BinaryData data) {
        return new MfcArchive(data, "XFL media", new FlaReadOptions(), Collections.emptyMap(), 0x47);
    }

    private static void finish(MfcArchive archive) throws IOException {
        if (archive.getRemaining() != 0) {
            throw new IOException("Trailing bytes in XFL media cache");
        }
    }
}
