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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Descriptor-sized audio buffers and the versioned two-track video wrapper.
 */
final class MediaPayloadRecordDecoder {

    private MediaPayloadRecordDecoder() {
    }

    static void sound(MfcArchive a, FlaRecord r, FlaRecord descriptor) throws IOException {
        int revision = number(descriptor, "revision").intValue();
        int format = number(descriptor, "formatFlags").intValue() + (revision < 5 ? 4 : 0);
        r.set("normalizedFormatFlags", format);
        long samples = number(descriptor, "sampleCount").longValue();
        int encoded = number(descriptor, "encodedByteCount").intValue();
        long length = (format & 0xF0) != 0 && encoded > 0 ? encoded : pcmSize(format, samples);
        a.readInline(r, "primaryAudio", (in, audio) -> audioBuffer(in, audio, length));
        if ((format & 0xF0) != 0) {
            r.set("secondaryMarker", a.readUI8());
            int secondaryFormat = number(descriptor, "secondaryFormatFlags").intValue();
            long secondaryLength = pcmSize(secondaryFormat, number(descriptor, "secondarySampleCount").longValue());
            a.readInline(r, "secondaryAudio", (in, audio) -> audioBuffer(in, audio, secondaryLength));
        }
    }

    private static Number number(FlaRecord record, String field) {
        Object value = record.get(field);
        return value instanceof Number ? (Number) value : 0;
    }

    private static long pcmSize(int format, long samples) {
        return samples * (1 + ((format & 2) != 0 ? 1 : 0)) * (1 + ((format & 1) != 0 ? 1 : 0));
    }

    private static void audioBuffer(MfcArchive a, FlaRecord r, long length) throws IOException {
        r.set("expectedByteCount", length);
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw a.error("Invalid audio buffer byte count " + length);
        }
        // The native audio loader accepts a short CArchive::Read at the stream end.
        long actual = Math.min(length, a.getRemaining());
        r.set("shortRead", actual != length);
        buffer(a, r, actual);
    }

    private static void buffer(MfcArchive a, FlaRecord r, long length) throws IOException {
        r.set("byteCount", length);
        r.set("data", a.readData(length));
    }

    static void video(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 3);
        r.set("videoType", a.readUI32());
        r.set("frameRate", a.readDouble());
        r.set("audioSampleRate", a.readDouble());
        r.set("audioBitsPerSample", a.readI32());
        r.set("audioChannels", a.readI32());
        r.set("videoWidth", a.readI32());
        r.set("videoHeight", a.readI32());
        r.set("durationSeconds", a.readDouble());
        r.set("isSpecial", a.readUI32());
        r.set("importAudio", a.readUI8());
        r.set("syncVideo", a.readUI8());
        r.set("dropRatioIndex", a.readUI32());
        r.set("quality", a.readI32());
        r.set("scaleRatio", a.readDouble());
        r.set("keyFrameInterval", a.readUI32());
        List<FlaRecord> tracks = new ArrayList<>();
        r.set("tracks", Collections.unmodifiableList(tracks));
        for (int i = 0; i < 2; i++) {
            a.readInline("videoTrack", tracks::add, (in, track) -> {
                track.set("kind", in.readUI32());
                List<FlaRecord> indices = new ArrayList<>();
                track.set("indices", Collections.unmodifiableList(indices));
                while (true) {
                    int marker = in.readUI8();
                    if (marker == 0) {
                        track.set("terminator", marker);
                        break;
                    }
                    in.checkedCount((long) indices.size() + 1, 0);
                    in.readInline("videoIndex", indices::add, (entryIn, entry) -> {
                        entry.set("marker", marker);
                        // Packet location and duration are four independent DWORDs.
                        entry.set("offsetBytes", entryIn.readI32());
                        entry.set("byteCount", entryIn.readI32());
                        entry.set("frameCount", entryIn.readI32());
                        entry.set("flags", entryIn.readI32());
                    });
                }
                buffer(in, track, in.readUI32());
            });
        }
        if (revision >= 3) {
            r.set("hasBundledDeviceVideo", a.readUI32());
            r.set("bundledSourceFilepath", a.readString());
            a.readInline(r, "bundledDeviceVideo", (in, bundled) -> buffer(in, bundled, in.readUI32()));
        }
    }
}
