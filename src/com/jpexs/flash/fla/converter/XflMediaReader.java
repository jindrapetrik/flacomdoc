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
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Reads shared XFL caches or external images directly into the media model.
 */
final class XflMediaReader {

    final XflReader.Session s;

    XflMediaReader(XflReader.Session s) {
        this.s = s;
    }

    void read() throws IOException {
        for (Element e : children(child(s.document, "media"))) {
            String kind = tag(e);
            if (kind.equals("DOMBitmapItem")) {
                bitmap(e);
            } else if (kind.equals("DOMSoundItem")) {
                sound(e);
            } else if (kind.equals("DOMVideoItem")) {
                video(e);
            } else {
                s.issue(e, "Unsupported library media item omitted");
            }
        }
    }

    FlaRecord descriptor(Element e, String kind) throws IOException {
        String name = s(e, "name", "");
        if (name.isEmpty() || s.media.containsKey(name)) {
            throw new IOException("Empty/duplicate media name: " + name);
        }
        int id = s.media.size() + 1;
        FlaRecord descriptor = r(kind, "mediaBaseRevision", 7, "revision", kind.equals("CMediaBits") ? 4 : kind.equals("CMediaSound") ? 10 : 3, "name", XflReader.Session.baseName(name), "streamName", "M " + id, "mediaId", id, "sourcePath", s(e, "sourceExternalFilepath", s(e, "href", "")), "sourceTime", timestamp(e, "sourceLastImported"), "sourcePlatform", 1L, "libraryMetadata", s.metadata(e));
        s.media.put(name, descriptor);
        s.mediaList.add(descriptor);
        return descriptor;
    }

    byte[] cache(Element e, String attribute) throws IOException {
        String href = s(e, attribute, "");
        return href.isEmpty() ? null : s.resource("bin/" + href, true);
    }

    void bitmap(Element e) throws IOException {
        FlaRecord descriptor = descriptor(e, "CMediaBits");
        byte[] cache = cache(e, "bitmapDataHRef");
        FlaRecord payload;
        if (cache != null) {
            payload = FlaMediaPayloads.bitmap(cache);
        } else {
            String href = s(e, "href", "");
            if (href.isEmpty()) {
                throw new IOException("Bitmap has neither cache nor image: " + s(e, "name", ""));
            }
            byte[] image = s.resource("LIBRARY/" + href, false);
            if (image == null) {
                image = s.resource(href, true);
            }
            payload = decodeImage(image);
        }
        BinaryData encoded = (BinaryData) payload.get("encodedData");
        descriptor.set("encodedByteCount", encoded == null ? 0L : (long) encoded.size());
        descriptor.set("compressionMode", s(e, "compressionType", "lossless").equals("photo") ? 0 : 1);
        descriptor.set("jpegQuality", i(e, "quality", 50));
        descriptor.set("allowSmoothing", b(e, "allowSmoothing", false) ? 1 : 0);
        descriptor.set("useDeblocking", b(e, "useDeblocking", false) ? 1 : 0);
        s.builder.stream((String) descriptor.get("streamName"), FlaStream.Kind.MEDIA, payload);
    }

    static FlaRecord decodeImage(byte[] bytes) throws IOException {
        BufferedImage image;
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported bitmap image");
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 16383 || h > 65535 || (long) w * h > 16 * 1024 * 1024) {
                    throw new IOException("Bitmap dimensions exceed model limits");
                }
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        int width = image.getWidth(), height = image.getHeight();
        byte[] pixels = new byte[width * height * 4];
        boolean alpha = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y), a = argb >>> 24, p = (y * width + x) * 4;
                alpha |= a != 255;
                pixels[p] = (byte) a;
                pixels[p + 1] = (byte) (((argb >>> 16 & 255) * a + 127) / 255);
                pixels[p + 2] = (byte) (((argb >>> 8 & 255) * a + 127) / 255);
                pixels[p + 3] = (byte) (((argb & 255) * a + 127) / 255);
            }
        }
        List<Integer> bounds = Arrays.asList(0, width * 20, 0, height * 20);
        FlaRecord raster = r("raster", "revision", 3, "formatCode", 5, "rowStride", width * 4, "width", width, "height", height, "boundsWords", bounds, "hasAlpha", alpha ? 1 : 0, "pixelData", new BinaryData(pixels));
        return r("bitmapPayload", "raster", raster);
    }

    void sound(Element e) throws IOException {
        FlaRecord d = descriptor(e, "CMediaSound");
        byte[] bytes = cache(e, "soundDataHRef");
        if (bytes == null) {
            throw new IOException("Sound cache missing: " + s(e, "name", ""));
        }
        int flags = soundFormat(s(e, "format", "")), length = i(e, "dataLength", 0);
        if (length < 0 || length > bytes.length) {
            throw new IOException("Invalid encoded sound dataLength");
        }
        if (length > 0) {
            boolean mp3 = s(e, "href", "").toLowerCase(Locale.ROOT).endsWith(".mp3") || bytes.length > 2 && ((bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') || ((bytes[0] & 255) == 255 && (bytes[1] & 224) == 224));
            if (!mp3) {
                throw new IOException("Compressed sound cache codec cannot be determined: " + s(e, "name", ""));
            }
            flags |= 0x20;
        }
        d.set("formatFlags", flags);
        d.set("sampleCount", (long) i(e, "sampleCount", 0));
        d.set("encodedByteCount", (long) length);
        d.set("exportFormat", i(e, "exportFormat", 0));
        d.set("exportBits", i(e, "exportBits", 0));
        d.set("deviceSoundHRef", s(e, "deviceSoundHRef", ""));
        d.set("secondaryFormatFlags", soundFormat(s(e, "cacheFormat", "")));
        d.set("secondarySampleCount", (long) i(e, "cachedSampleCount", 0));
        d.set("importedMP3QualityFlag", b(e, "exportNative", false) ? 1 : 0);
        d.set("use8kSampleRateFlag", b(e, "use8kSampleRate", false) ? 1 : 0);
        if ((flags & 0xF0) == 0) {
            int frameBytes = FlaSoundFormat.fromFlags(flags).getPcmFrameByteCount();
            long expected = ((Number) d.get("sampleCount")).longValue() * frameBytes;
            if (expected >= 0 && expected < bytes.length && bytes.length % frameBytes == 0) {
                // Decoders can retain final codec padding beyond the declared duration.
                s.issue(e, "Sound cache " + s(e, "soundDataHRef", "") + " contains "
                        + (bytes.length - expected) + " extra PCM bytes beyond sampleCount; unused sample frames ignored.");
                bytes = Arrays.copyOf(bytes, (int) expected);
            }
        }
        FlaRecord payload;
        try {
            payload = FlaMediaPayloads.sound(bytes, d);
        } catch (IOException ex) {
            throw new IOException("Invalid sound cache for " + s(e, "name", "")
                    + " (" + s(e, "soundDataHRef", "") + "): " + ex.getMessage(), ex);
        }
        s.builder.stream((String) d.get("streamName"), FlaStream.Kind.MEDIA, payload, new BinaryData(bytes));
    }

    static int soundFormat(String format) {
        int flags = 0;
        if (format.toLowerCase(Locale.ROOT).contains("stereo")) {
            flags |= 1;
        }
        if (format.contains("16bit") || format.contains("16 bit")) {
            flags |= 2;
        }
        if (format.contains("44kHz") || format.contains("44.1kHz")) {
            flags |= 12;
        } else if (format.contains("22kHz") || format.contains("22.05kHz")) {
            flags |= 8;
        } else if (format.contains("11kHz") || format.contains("11.025kHz")) {
            flags |= 4;
        }
        return flags;
    }

    void video(Element e) throws IOException {
        FlaRecord d = descriptor(e, "CMediaVideoStream");
        byte[] bytes = cache(e, "videoDataHRef");
        if (bytes == null) {
            throw new IOException("Video cache missing");
        }
        FlaRecord payload = FlaMediaPayloads.video(bytes);
        s.builder.stream((String) d.get("streamName"), FlaStream.Kind.MEDIA, payload);
    }
}
