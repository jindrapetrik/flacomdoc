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
package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.BinaryData;
import com.jpexs.flash.fla.reader.FlaBounds;
import com.jpexs.flash.fla.reader.FlaRecord;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Native bitmap payloads remain the authoring source; external images are
 * detached files.
 */
final class XflBitmapWriter {

    static final long MAX_PIXELS = 16L * 1024 * 1024;
    static final long MAX_RASTER_BYTES = 64L * 1024 * 1024;

    static final class Bitmap {

        final String name;
        final boolean smoothed;

        Bitmap(String name, boolean smoothed) {
            this.name = name;
            this.smoothed = smoothed;
        }
    }

    private final BinaryFlaToXflExporter.Session session;

    XflBitmapWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    Bitmap write(Element media, FlaRecord descriptor, FlaRecord payload, FlaRecord raster,
            String logicalName, int ordinal) throws IOException {
        if (payload == null || !payload.isComplete() || raster != null && !raster.isComplete()) {
            throw new IOException("Incomplete bitmap payload");
        }
        byte[] image, dat;
        boolean jpeg = raster == null;
        FlaBounds bounds;
        if (jpeg) {
            Object encoded = payload.get("encodedData");
            if (!(encoded instanceof BinaryData)) {
                throw new IOException("Missing encoded image");
            }
            image = ((BinaryData) encoded).toByteArray();
            jpegDimensions(image);
            bounds = payload.getBounds();
            dat = payload.getConsumedData().toByteArray();
        } else {
            int width = checkedInt(raster, "width"), height = checkedInt(raster, "height");
            dimensions(width, height);
            int format = checkedInt(raster, "formatCode"), stride = checkedInt(raster, "rowStride");
            if (format < 0 || format > 5) {
                throw new IOException("Unsupported raster format " + format);
            }
            int depth = format == 5 ? 32 : 1 << format;
            long minimum = ((long) width * depth + 7) / 8;
            long length = (long) stride * height;
            if (stride < minimum || length > MAX_RASTER_BYTES) {
                throw new IOException("Invalid stride or raster exceeds decoded byte limit");
            }
            byte[] pixels = pixels(raster, (int) length);
            int revision = checkedInt(raster, "revision");
            boolean alpha = revision >= 3 && n(raster, "hasAlpha", 0) != 0;
            int[] palette = format < 4 ? palette(raster, revision, alpha) : null;
            BufferedImage decoded = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            int[] argb = ((DataBufferInt) decoded.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < height; y++) {
                int row = y * stride;
                for (int x = 0; x < width; x++) {
                    int color;
                    if (format == 5) {
                        int p = row + x * 4;
                        int a = alpha ? pixels[p] & 255 : 255;
                        color = a << 24 | (pixels[p + 1] & 255) << 16 | (pixels[p + 2] & 255) << 8 | pixels[p + 3] & 255;
                    } else if (format == 4) {
                        int p = row + x * 2;
                        color = rgb555((pixels[p] & 255) << 8 | pixels[p + 1] & 255);
                    } else {
                        int bit = x * depth;
                        int index = (pixels[row + bit / 8] >>> (8 - depth - bit % 8)) & ((1 << depth) - 1);
                        if (index >= palette.length) {
                            throw new IOException("Pixel refers to a missing palette entry");
                        }
                        color = palette[index];
                    }
                    int a = color >>> 24;
                    if ((color >>> 16 & 255) > a || (color >>> 8 & 255) > a || (color & 255) > a) {
                        throw new IOException("Archived alpha channels exceed premultiplied bounds; no straight-alpha or matte selector is stored");
                    }
                    argb[y * width + x] = color;
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(png)) {
                if (!ImageIO.write(decoded, "png", output)) {
                    throw new IOException("JDK PNG writer is unavailable");
                }
            }
            image = png.toByteArray();
            bounds = raster.getBounds();
            // R3 already has the XFL bin layout. Preserve its exact chunking,
            // padding, palette and premultiplied pixels instead of recompressing.
            dat = revision == 3 ? raster.getConsumedData().toByteArray()
                    : normalizedRaster(raster, pixels, palette, format, stride, width, height);
        }
        if (bounds == null || !bounds.isDefined()) {
            throw new IOException("Bitmap frame bounds are unavailable");
        }
        // Validate and decode everything before reserving a name or adding files.
        String name = session.unique(logicalName, descriptor);
        String imageName = "bitmap-" + ordinal + (jpeg ? ".jpg" : ".png");
        // Cached media stems must contain only ASCII letters, digits or spaces.
        // The native loader replaces other characters before looking up the file.
        String dataName = "bitmap" + ordinal + ".dat";
        Element item = child(media, "DOMBitmapItem");
        session.attribute(item, "name", name, descriptor);
        item.setAttribute("href", imageName);
        item.setAttribute("bitmapDataHRef", dataName);
        item.setAttribute("frameLeft", Integer.toString(bounds.getXMinTwips()));
        item.setAttribute("frameRight", Integer.toString(bounds.getXMaxTwips()));
        item.setAttribute("frameTop", Integer.toString(bounds.getYMinTwips()));
        item.setAttribute("frameBottom", Integer.toString(bounds.getYMaxTwips()));
        long compression = n(descriptor, "compressionMode", jpeg ? 0 : 1), quality = n(descriptor, "jpegQuality", 80);
        if (quality < 0 || quality > 100) {
            session.issue("BITMAP_QUALITY", descriptor, "Out-of-range JPEG quality replaced with 80.");
            quality = 80;
        }
        if (compression < 0 || compression > 2) {
            session.issue("BITMAP_COMPRESSION", descriptor, "Unknown export compression; photo export defaults apply.");
        }
        boolean smoothed = n(descriptor, "allowSmoothing", 0) != 0;
        item.setAttribute("quality", Long.toString(quality));
        item.setAttribute("compressionType", compression == 1 ? "lossless" : "photo");
        item.setAttribute("originalCompressionType", jpeg ? "photo" : "lossless");
        item.setAttribute("useImportedJPEGData", Boolean.toString(compression == 0));
        item.setAttribute("isJPEG", Boolean.toString(jpeg));
        item.setAttribute("allowSmoothing", Boolean.toString(smoothed));
        item.setAttribute("useDeblocking", Boolean.toString(n(descriptor, "useDeblocking", 0) != 0));
        item.setAttribute("externalFileSize", Integer.toString(image.length));
        item.setAttribute("sourceLastImported", Long.toString(n(descriptor, "sourceTime", 0)));
        if (descriptor == null || descriptor.get("sourceTime") == null) {
            session.issue("BITMAP_SOURCE_TIME", descriptor == null ? raster : descriptor,
                    "Historical bitmap has no import timestamp; zero was used.");
        }
        if (descriptor != null) {
            String sourcePath = s(descriptor, "sourcePath", s(descriptor, "legacySourcePath", ""));
            if (!sourcePath.isEmpty()) {
                session.attribute(item, "sourceExternalFilepath", sourcePath, descriptor);
            }
            session.linkage(item, descriptor);
            session.mediaSourcePlatform(item, descriptor);
        }
        session.files.put("LIBRARY/" + imageName, new BinaryData(image));
        session.files.put("bin/" + dataName, new BinaryData(dat));
        return new Bitmap(name, smoothed);
    }

    private static int checkedInt(FlaRecord r, String field) throws IOException {
        Object value = r.get(field);
        if (!(value instanceof Number)) {
            throw new IOException("Missing bitmap " + field);
        }
        long number = ((Number) value).longValue();
        if (number < 0 || number > Integer.MAX_VALUE) {
            throw new IOException("Invalid bitmap " + field);
        }
        return (int) number;
    }

    private static void dimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            throw new IOException("Invalid dimensions or bitmap exceeds 16777216 pixel limit");
        }
    }

    private static void jpegDimensions(byte[] image) throws IOException {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unrecognized encoded bitmap");
            }
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("JPEG")) {
                    throw new IOException("Encoded bitmap is not a supported JPEG");
                }
                reader.setInput(input, true, true);
                dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    static byte[] pixels(FlaRecord raster, int count) throws IOException {
        if (!Boolean.TRUE.equals(raster.get("isCompressed"))) {
            Object value = raster.get("pixelData");
            if (!(value instanceof BinaryData) || ((BinaryData) value).size() != count) {
                throw new IOException("Incorrect raw bitmap byte count");
            }
            return ((BinaryData) value).toByteArray();
        }
        List<InputStream> chunks = new ArrayList<>();
        for (FlaRecord chunk : records(raster, "chunks")) {
            Object value = chunk.get("data");
            if (!(value instanceof BinaryData)) {
                throw new IOException("Missing compressed bitmap chunk");
            }
            chunks.add(((BinaryData) value).openStream());
        }
        Inflater inflater = new Inflater();
        try (InputStream compressed = new SequenceInputStream(Collections.enumeration(chunks)); InflaterInputStream input = new InflaterInputStream(compressed, inflater)) {
            byte[] pixels = new byte[count];
            int offset = 0;
            while (offset < count) {
                int read = input.read(pixels, offset, count - offset);
                if (read < 0) {
                    throw new IOException("Truncated inflated bitmap");
                }
                offset += read;
            }
            if (input.read() != -1 || !inflater.finished()) {
                throw new IOException("Inflated bitmap byte count differs from stride times height");
            }
            if (inflater.getRemaining() != 0 || compressed.read() != -1) {
                throw new IOException("Trailing bytes after the bitmap zlib stream");
            }
            return pixels;
        } finally {
            inflater.end();
        }
    }

    // Channel expansion follows the archived raster renderer's 32-entry table.
    private static final int[] RGB5 = {0, 15, 23, 31, 39, 47, 55, 63, 71, 79, 87, 95, 95, 111, 119, 127,
        135, 143, 151, 159, 167, 175, 183, 191, 199, 207, 215, 223, 223, 239, 247, 255};

    private static int rgb555(int word) {
        return 0xFF000000 | RGB5[word >>> 10 & 31] << 16 | RGB5[word >>> 5 & 31] << 8 | RGB5[word & 31];
    }

    static int[] palette(FlaRecord raster, int revision, boolean alpha) throws IOException {
        List<FlaRecord> entries = records(raster, "palette");
        if (entries.isEmpty() || entries.size() > 256) {
            throw new IOException("Missing or oversized bitmap palette");
        }
        int[] colors = new int[entries.size()];
        for (int i = 0; i < colors.length; i++) {
            Object value = entries.get(i).get("components");
            if (!(value instanceof List) || ((List<?>) value).size() != (revision >= 3 ? 4 : 3)) {
                throw new IOException("Incomplete palette entry");
            }
            List<?> words = (List<?>) value;
            int color = alpha ? 0 : 0xFF000000;
            for (int channel = 0; channel < words.size(); channel++) {
                if (!(words.get(channel) instanceof Number)) {
                    throw new IOException("Invalid palette component");
                }
                int word = ((Number) words.get(channel)).intValue();
                int component = revision >= 3 ? word & 255 : word >>> 8 & 255;
                int shift = revision >= 3 ? 24 - channel * 8 : 16 - channel * 8;
                if (channel != 0 || alpha || revision < 3) {
                    color |= component << shift;
                }
            }
            colors[i] = color;
        }
        return colors;
    }

    private static byte[] normalizedRaster(FlaRecord raster, byte[] pixels, int[] palette,
            int format, int stride, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(3);
        out.write(format);
        u16(out, stride);
        u16(out, width);
        u16(out, height);
        FlaBounds bounds = raster.getBounds();
        if (bounds == null) {
            throw new IOException("Missing raster bounds");
        }
        for (int word : bounds.getWords()) {
            for (int i = 0; i < 4; i++) {
                out.write(word >>> (i * 8) & 255);
            }
        }
        out.write(0); // Historical images are loaded as opaque.
        if (format < 4) {
            u16(out, palette.length);
            for (int color : palette) {
                for (int shift = 24; shift >= 0; shift -= 8) {
                    out.write(color >>> shift & 255);
                }
            }
        } else if (format == 5) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * stride + x * 4] = (byte) 255;
                }
            }
        }
        out.write(0); // R3 raw pixel data, with the original row padding.
        out.write(pixels);
        return out.toByteArray();
    }

    private static void u16(ByteArrayOutputStream out, int value) {
        out.write(value & 255);
        out.write(value >>> 8 & 255);
    }
}
