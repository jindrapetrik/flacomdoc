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
package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.reader.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * Media stream framing and conservative lowering for historical targets.
 */
final class BinaryMediaWriter {

    private final BinaryFlaConverter.Session session;

    BinaryMediaWriter(BinaryFlaConverter.Session session) {
        this.session = session;
    }

    void prepare() {
        if (session.target.mediaSoundRevision >= 6) {
            return;
        }
        for (BinaryFlaDocument.Media media : session.source.getMedia()) {
            FlaRecord d = media.getDescriptor();
            if (!d.getClassName().equals("CMediaSound")) {
                continue;
            }
            int revision = num(d, "revision", 0), format = num(d, "formatFlags", 0) + (revision < 5 ? 4 : 0);
            if ((format & 0xF0) == 0) {
                continue;
            }
            FlaRecord root = media.getStream() == null ? null : media.getStream().getRoot(), secondary = rec(root, "secondaryAudio");
            BinaryData data = bin(secondary, "data");
            int samples = num(d, "secondarySampleCount", 0), sf = num(d, "secondaryFormatFlags", 0);
            long length = (long) samples * (1 + ((sf & 2) != 0 ? 1 : 0)) * (1 + ((sf & 1) != 0 ? 1 : 0));
            if (data != null && samples > 0 && data.size() == length) {
                Map<String, Object> changes = new LinkedHashMap<>();
                changes.put("formatFlags", sf - (revision < 5 ? 4 : 0));
                changes.put("sampleCount", (long) samples);
                changes.put("encodedByteCount", 0L);
                session.overrides.put(d, changes);
                session.replacementMedia.put(media.getStream().getPath(), data);
                session.issue("SOUND_PCM_FALLBACK", media.getStream().getPath(), d, "formatFlags", "Stored PCM buffer replaces compressed audio for the older target.");
            } else {
                session.unavailableMedia.add(num(d, "mediaId", 0));
                session.dropped.add(d);
                session.issue("SOUND_OMITTED", media.getStream() == null ? "" : media.getStream().getPath(), d, "", "Compressed sound has no PCM fallback for this target.");
            }
        }
    }

    void writeStreams() throws IOException {
        for (BinaryFlaDocument.Media media : session.source.getMedia()) {
            FlaStream stream = media.getStream();
            FlaRecord descriptor = media.getDescriptor();
            if (stream == null || session.target.contentsRevision < 0x11 || !session.supports(descriptor)) {
                continue;
            }
            BinaryData replacement = session.replacementMedia.get(stream.getPath());
            if (replacement != null) {
                session.streams.put(stream.getPath(), replacement);
                continue;
            }
            String kind = descriptor.getClassName();
            BinaryArchiveWriter w = new BinaryArchiveWriter(session, stream.getPath());
            if (kind.equals("CMediaBits")) {
                bitmapPayload(w, stream.getRoot(), descriptor);
            } else if (kind.equals("CMediaVideoStream")) {
                video(w, stream.getRoot());
            } else {
                w.raw(stream.getData());
            }
            session.streams.put(stream.getPath(), w.finish());
        }
    }

    void bitmapPayload(BinaryArchiveWriter w, FlaRecord payload, FlaRecord descriptor) throws IOException {
        BinaryData encoded = w.data(payload, "encodedData");
        if (encoded != null && w.target().mediaBitsRevision >= 3) {
            w.raw(encoded);
            w.words(payload, "boundsWords", 4);
        } else if (encoded != null) {
            raster(w, jpegRaster(w, encoded, w.list(payload, "boundsWords")));
            w.issue("BITMAP_RASTERIZED", descriptor, "encodedData", "JPEG decoded to a raster for the older bitmap record.");
        } else {
            raster(w, w.record(payload, "raster"));
        }
    }

    void inlineBitmap(BinaryArchiveWriter w, FlaRecord instance) throws IOException {
        FlaRecord raster = w.record(instance, "raster");
        if (raster == null) {
            int id = (int) w.n(instance, "mediaId", 0);
            for (BinaryFlaDocument.Media media : session.source.getMedia()) {
                if (num(media.getDescriptor(), "mediaId", -1) == id && media.getStream() != null) {
                    FlaRecord payload = media.getStream().getRoot();
                    raster = rec(payload, "raster");
                    BinaryData encoded = bin(payload, "encodedData");
                    if (raster == null && encoded != null) {
                        raster = jpegRaster(w, encoded, w.list(payload, "boundsWords"));
                    }
                    break;
                }
            }
        }
        if (raster == null) {
            w.issue("BITMAP_PLACEHOLDER", instance, "raster", "Missing bitmap replaced with a one-pixel raster.");
        }
        raster(w, raster);
    }

    void raster(BinaryArchiveWriter w, FlaRecord raster) throws IOException {
        // FutureSplash reads raw pixels even when the revision byte is nonzero.
        int maximum = w.target() == FlaFormatVersion.F1 ? 0 : w.target().ordinal() < FlaFormatVersion.F5.ordinal() ? 2 : 3;
        int revision = w.revision(raster, "revision", maximum);
        int format = (int) w.n(raster, "formatCode", 5), width = (int) w.n(raster, "width", 1), height = (int) w.n(raster, "height", 1), stride = (int) w.n(raster, "rowStride", 4);
        if (width < 1 || height < 1 || width > 65535 || height > 65535 || stride < 1 || stride > 65535 || (long) stride * height > 64L * 1024 * 1024) {
            throw new IOException("Invalid or excessive bitmap dimensions");
        }
        w.u8(format);
        w.u16(stride);
        w.u16(width);
        w.u16(height);
        if (raster == null) {
            for (int n : new int[]{0, width * 20, 0, height * 20}) {
                w.u32(n);
            }
        } else {
            w.words(raster, "boundsWords", 4);
        }
        boolean alpha = w.n(raster, "hasAlpha", 0) != 0;
        if (revision >= 3) {
            w.u8(alpha ? 1 : 0);
        }
        int sourceRevision = num(raster, "revision", 3);
        if (format < 4) {
            List<FlaRecord> palette = w.records(raster, "palette");
            w.u16(palette.size());
            for (FlaRecord entry : palette) {
                List<?> components = w.list(entry, "components");
                int skip = sourceRevision >= 3 ? 1 : 0;
                int a = skip == 1 ? number(components, 0) : 255;
                if (revision >= 3) {
                    w.u8(sourceRevision >= 3 ? a : 255);
                    for (int i = skip; i < components.size(); i++) {
                        w.u8(sourceRevision >= 3 ? number(components, i) : number(components, i) >>> 8);
                    }
                } else {
                    for (int i = skip; i < components.size(); i++) {
                        int c = sourceRevision >= 3 ? number(components, i) : number(components, i) >>> 8;
                        if (alpha) {
                            c = Math.min(255, c + 255 - a);
                        }
                        w.u16(c * 257);
                    }
                }
            }
        }
        byte[] pixels = raster == null ? new byte[]{0, (byte) 255, (byte) 255, (byte) 255} : pixels(w, raster, stride * height);
        if (alpha && revision < 3) {
            w.issue("BITMAP_ALPHA", raster, "hasAlpha", "Transparency flattened onto white for the older raster.");
            if (format == 5) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int p = y * stride + 4 * x, a = pixels[p] & 255;
                        for (int c = 1; c <= 3; c++) {
                            pixels[p + c] = (byte) Math.min(255, (pixels[p + c] & 255) + 255 - a);
                        }
                        pixels[p] = (byte) 255;
                    }
                }
            }
        }
        if (revision == 1) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream out = new DeflaterOutputStream(compressed)) {
                out.write(pixels);
            }
            byte[] data = compressed.toByteArray();
            for (int i = 0; i < data.length; i += 65535) {
                int n = Math.min(65535, data.length - i);
                w.u16(n);
                w.raw(Arrays.copyOfRange(data, i, i + n));
            }
            w.u16(0);
        } else {
            if (revision >= 2) {
                w.u8(0);
            }
            w.raw(pixels);
        }
        if (raster != null) {
            for (String field : Arrays.asList("compressed", "isCompressed", "expectedByteCount", "compressedByteCount", "chunkTerminator")) {
                w.value(raster, field);
            }
        }
    }

    private byte[] pixels(BinaryArchiveWriter w, FlaRecord raster, int expected) throws IOException {
        BinaryData raw = w.data(raster, "pixelData");
        if (raw != null) {
            if (raw.size() != expected) {
                throw new IOException("Wrong raster byte count");
            }
            return raw.toByteArray();
        }
        List<FlaRecord> chunks = w.records(raster, "chunks");
        List<InputStream> inputs = new ArrayList<>();
        for (FlaRecord chunk : chunks) {
            BinaryData data = w.data(chunk, "data");
            if (data != null) {
                inputs.add(data.openStream());
            }
        }
        byte[] pixels = new byte[expected];
        Inflater inflater = new Inflater();
        try (InputStream compressed = new SequenceInputStream(Collections.enumeration(inputs)); InflaterInputStream input = new InflaterInputStream(compressed, inflater)) {
            int offset = 0;
            while (offset < pixels.length) {
                int n = input.read(pixels, offset, pixels.length - offset);
                if (n < 0) {
                    throw new IOException("Truncated bitmap compression");
                }
                offset += n;
            }
            if (input.read() != -1 || !inflater.finished()) {
                throw new IOException("Bitmap exceeds expected dimensions");
            }
        } finally {
            inflater.end();
        }
        return pixels;
    }

    private FlaRecord jpegRaster(BinaryArchiveWriter w, BinaryData encoded, List<?> bounds) throws IOException {
        BufferedImage image = ImageIO.read(encoded.openStream());
        if (image == null || (long) image.getWidth() * image.getHeight() > 16L * 1024 * 1024) {
            throw new IOException("Invalid or excessive JPEG bitmap");
        }
        int width = image.getWidth(), height = image.getHeight();
        FlaRecord r = FlaRecord.create("raster");
        r.set("revision", 3);
        r.set("formatCode", 5);
        r.set("width", width);
        r.set("height", height);
        r.set("rowStride", width * 4);
        r.set("boundsWords", bounds);
        byte[] pixels = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = image.getRGB(x, y), p = (y * width + x) * 4;
                pixels[p] = (byte) 255;
                pixels[p + 1] = (byte) (c >>> 16);
                pixels[p + 2] = (byte) (c >>> 8);
                pixels[p + 3] = (byte) c;
            }
        }
        r.set("pixelData", new BinaryData(pixels));
        return r;
    }

    private void video(BinaryArchiveWriter w, FlaRecord r) {
        int v = w.revision(r, "revision", w.target().ordinal() <= FlaFormatVersion.MX2004.ordinal() ? 2 : 3);
        w.u32(r, "videoType");
        for (String field : Arrays.asList("frameRate", "audioSampleRate")) {
            w.f64(w.d(r, field, 0));
        }
        w.dwords(r, "audioBitsPerSample", "audioChannels", "videoWidth", "videoHeight");
        w.f64(w.d(r, "durationSeconds", 0));
        w.u32(r, "isSpecial");
        w.u8(r, "importAudio");
        w.u8(r, "syncVideo");
        w.dwords(r, "dropRatioIndex", "quality");
        w.f64(w.d(r, "scaleRatio", 1));
        w.u32(r, "keyFrameInterval");
        List<FlaRecord> tracks = w.records(r, "tracks");
        for (int i = 0; i < 2; i++) {
            FlaRecord track = i < tracks.size() ? tracks.get(i) : null;
            w.u32(track, "kind");
            for (FlaRecord entry : w.records(track, "indices")) {
                w.u8(w.n(entry, "marker", 1));
                w.dwords(entry, "offsetBytes", "byteCount", "frameCount", "flags");
            }
            w.u8(0);
            BinaryData d = w.data(track, "data");
            w.u32(d == null ? 0 : d.size());
            w.raw(d);
        }
        if (v >= 3) {
            w.u32(r, "hasBundledDeviceVideo");
            w.string(r, "bundledSourceFilepath");
            FlaRecord bundled = w.record(r, "bundledDeviceVideo");
            BinaryData d = w.data(bundled, "data");
            w.u32(d == null ? 0 : d.size());
            w.raw(d);
        }
    }

    static int num(FlaRecord r, String key, int fallback) {
        Object n = r == null ? null : r.get(key);
        return n instanceof Number ? ((Number) n).intValue() : fallback;
    }

    static FlaRecord rec(FlaRecord r, String key) {
        Object n = r == null ? null : r.get(key);
        return n instanceof FlaRecord ? (FlaRecord) n : null;
    }

    static BinaryData bin(FlaRecord r, String key) {
        Object n = r == null ? null : r.get(key);
        return n instanceof BinaryData ? (BinaryData) n : null;
    }

    private int number(List<?> items, int i) {
        return i < items.size() && items.get(i) instanceof Number ? ((Number) items.get(i)).intValue() : 0;
    }
}
