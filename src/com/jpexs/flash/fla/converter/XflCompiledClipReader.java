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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA
 */
package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.reader.*;
import java.io.IOException;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Compiled component media, recursive SWC metadata and raw custom icons.
 */
final class XflCompiledClipReader {

    private final XflReader.Session session;

    XflCompiledClipReader(XflReader.Session session) {
        this.session = session;
    }

    void item(XflMediaReader media, Element e) throws IOException {
        FlaRecord component = new XflComponentReader(session).component(e);
        @SuppressWarnings("unchecked")
        List<FlaRecord> movies = (List<FlaRecord>) component.get("movies");
        BinaryData movie = (BinaryData) movies.get(0).get("data");
        byte[] header = movie.slice(0, Math.min(8, movie.size())).toByteArray();
        if (header.length < 8 || (header[0] != 'F' && header[0] != 'C' && header[0] != 'Z') || header[1] != 'W' || header[2] != 'S') {
            throw new IOException("Compiled clip has no valid primary SWF: " + s(e, "name", ""));
        }
        FlaRecord descriptor = media.descriptor(e, "CSwfPage");
        descriptor.set("revision", 5);
        descriptor.set("component", component);
        descriptor.set("sourceName", s(e, "rootSymbolLinkageID", s(e, "actionscriptClass", s(e, "name", ""))));
        descriptor.set("boundsWords", Arrays.asList(i(e, "movieRectRight", 0), i(e, "movieRectLeft", 0), i(e, "movieRectBottom", 0), i(e, "movieRectTop", 0)));
        descriptor.set("playerVersion", i(e, "playerVersion", header[3] & 255));
        descriptor.set("actionScriptVersion", i(e, "actionscriptVersion", 2));
        descriptor.set("metadata", metadata(child(e, "SwcItem"), (String) descriptor.get("sourceName"), 0));
        descriptor.set("exportName", s(e, "rootSymbolLinkageID", ""));
        session.builder.stream((String) descriptor.get("streamName"), FlaStream.Kind.MEDIA, r("swfPagePayload"));
    }

    private FlaRecord metadata(Element e, String fallback, int depth) throws IOException {
        if (depth > 128) {
            throw new IOException("Excessive SWC metadata nesting");
        }
        FlaRecord result = r("swfMetadata", "revision", 4, "name", s(e, "name", fallback),
                "implementation", s(e, "implementation", ""), "version", timestamp(e, "version"),
                "isTopLevel", b(e, "isTopLevel", false) ? 1L : 0L,
                "intrinsicClass", s(e, "intrinsicClass", ""), "intrinsicScript", s(e, "intrinsicScript", ""));
        for (String list : Arrays.asList("assetMap", "includesList", "exportAfterList")) {
            List<FlaRecord> entries = new ArrayList<>();
            for (Element entry : children(child(e, list))) {
                boolean asset = list.equals("assetMap");
                Element nested = asset ? child(entry, "SwcItem") : entry;
                if (nested == null) {
                    throw new IOException("SWC asset has no SwcItem");
                }
                entries.add(r("swfMetadataEntry", "key", s(entry, "key", ""), "metadata", metadata(nested, "", depth + 1)));
            }
            result.set(list, entries);
        }
        return result;
    }

    FlaRecord icon(Element e) throws IOException {
        if (e == null) {
            return null;
        }
        int width = i(e, "width", 0), height = i(e, "height", 0), stride = i(e, "rowByteCount", 0), depth = i(e, "colorDepth", 32);
        int format = Arrays.asList(1, 2, 4, 8, 16, 32).indexOf(depth);
        long length = (long) stride * height;
        if (format < 0 || width < 1 || height < 1 || width > 65535 || height > 65535
                || stride < ((long) width * depth + 7) / 8 || stride > 65535 || length > 64L * 1024 * 1024) {
            throw new IOException("Invalid custom icon dimensions or color depth");
        }
        byte[] pixels = session.resource("bin/" + s(e, "href", ""), true);
        if (pixels.length != length || depth >= 16 && stride % (depth / 8) != 0) {
            throw new IOException("Invalid custom icon pixel data length");
        }
        if (depth == 32) {
            for (int n = 0; n < pixels.length; n += 4) {
                byte blue = pixels[n], green = pixels[n + 1], red = pixels[n + 2], alpha = pixels[n + 3];
                pixels[n] = alpha;
                pixels[n + 1] = red;
                pixels[n + 2] = green;
                pixels[n + 3] = blue;
            }
        } else if (depth == 16) {
            for (int n = 0; n < pixels.length; n += 2) {
                byte first = pixels[n];
                pixels[n] = pixels[n + 1];
                pixels[n + 1] = first;
            }
        }
        List<FlaRecord> palette = new ArrayList<>();
        for (Element color : children(child(e, "colorTable"), "ColorItem")) {
            long value = rgba(color, "color", "alpha", 0xff000000L);
            palette.add(r("paletteEntry", "components", Arrays.asList((int) (value >>> 24 & 255), (int) (value & 255), (int) (value >>> 8 & 255), (int) (value >>> 16 & 255))));
        }
        if (depth < 16 && (palette.isEmpty() || palette.size() > 1 << depth)) {
            throw new IOException("Invalid custom icon palette");
        }
        FlaRecord raster = r("raster", "revision", 3, "formatCode", format, "rowStride", stride, "width", width, "height", height,
                "boundsWords", Arrays.asList(i(e, "frameLeft", 0), i(e, "frameRight", width * 20), i(e, "frameTop", 0), i(e, "frameBottom", height * 20)),
                "hasAlpha", b(e, "isTransparent", false) ? 1L : 0L, "palette", palette, "pixelData", new BinaryData(pixels));
        return r("CMediaBits", "mediaBaseRevision", 7, "revision", 4, "name", "", "streamName", "M 0", "mediaId", 0,
                "payload", r("bitmapPayload", "raster", raster));
    }
}
