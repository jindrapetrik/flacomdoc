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

import com.jpexs.flash.fla.reader.FlaRecord;
import java.io.IOException;
import java.util.*;

/**
 * Shape-local binary tables and fixed-point geometry, without an XML path.
 */
final class BinaryShapeWriter {

    private final BinaryArchiveWriter w;

    BinaryShapeWriter(BinaryArchiveWriter w) {
        this.w = w;
    }

    void shape(FlaRecord r, int mode) throws IOException {
        int v = w.revision(r, "revision", w.target().shapeDataRevision);
        w.value(r, "extensionMode");
        List<FlaRecord> fills = w.records(r, "fills"), strokes = w.records(r, "strokes"), edges = w.records(r, "edges");
        w.u32(w.n(r, "metadata", edges.size()));
        w.u16(fills.size());
        for (FlaRecord f : fills) {
            if (v < 3) {
                w.u32(color(f, "packedValue", v));
                w.u16(f, "legacyValue");
            } else {
                fill(f, mode, v);
            }
        }
        w.u16(strokes.size());
        for (FlaRecord s : strokes) {
            stroke(s, mode, v);
        }
        if (v >= 2) {
            int x = 0, y = 0;
            for (FlaRecord edge : edges) {
                boolean curve = Boolean.TRUE.equals(w.value(edge, "curve"));
                int sx = (int) w.n(edge, "startX", x), sy = (int) w.n(edge, "startY", y);
                int ex = (int) w.n(edge, "endX", sx), ey = (int) w.n(edge, "endY", sy);
                w.u8(0x40 | 2 | (curve ? 8 : 0) | 32);
                w.u16(edge, "strokeIndex");
                w.u16(edge, "fillIndex0");
                w.u16(edge, "fillIndex1");
                w.u32(sx - x);
                w.u32(sy - y);
                if (curve) {
                    w.u32(w.n(edge, "controlX", sx) - sx);
                    w.u32(w.n(edge, "controlY", sy) - sy);
                }
                w.u32(ex - sx);
                w.u32(ey - sy);
                if (!curve && mode >= 2) {
                    w.u8(edge, "lineFlags");
                }
                for (String f : Arrays.asList("control", "startDelta", "controlDelta", "endDelta")) {
                    w.value(edge, f);
                }
                x = ex;
                y = ey;
            }
            w.u8(0);
            w.value(r, "edgeTerminator");
            w.value(r, "firstEdgeControl");
        }
        if (v >= 5) {
            List<FlaRecord> cubics = w.records(r, "auxiliary");
            w.u32(cubics.size());
            for (FlaRecord c : cubics) {
                w.words(c, "points", 8);
                if (mode >= 3) {
                    List<FlaRecord> entries = w.records(c, "entries");
                    if (entries.size() > 255) {
                        throw new IOException("Too many cubic approximation points");
                    }
                    w.u8(entries.size());
                    for (FlaRecord p : entries) {
                        w.u32(p, "x");
                        w.u32(p, "y");
                        w.u8(p, "controlFlag");
                        w.u8(p, "value2");
                    }
                }
                if (mode >= 4) {
                    int flags = (int) w.n(c, "flags", 0);
                    w.u8(flags);
                    if ((flags & 1) != 0) {
                        w.words(c, "boundaryStartPointTwips", 2);
                    }
                    if ((flags & 2) != 0) {
                        w.words(c, "boundaryEndPointTwips", 2);
                    }
                }
            }
        }
    }

    private long color(FlaRecord r, String field, int revision) {
        long c = w.n(r, field, 0xFF000000L);
        // Pre-alpha shape revisions interpret the high byte as opaque.
        if (revision >= 4 && w.session.source.getContentsRevision() < 0x20) {
            c |= 0xFF000000L;
        }
        return c;
    }

    void fill(FlaRecord r, int mode, int revision) {
        int flags = (int) w.n(r, "flags", 0);
        long packed = color(r, "packedValue", revision);
        if ((flags & 0x40) != 0 && w.target() == FlaFormatVersion.F1) {
            // F1 has inline bitmap instances, but no media-ID fill table.
            w.issue("BITMAP_FILL", r, "bitmapMediaId", "Bitmap fill saved as a solid color because FutureSplash has no bitmap-fill media references.");
            flags = 0;
            packed |= 0xFF000000L;
        }
        if ((flags & 0x10) != 0 && w.target().ordinal() <= FlaFormatVersion.F4.ordinal()
                && w.session.source.getContentsRevision() > FlaFormatVersion.F4.contentsRevision) {
            // Legacy gradient rendering also tests the header alpha; newer files
            // can leave it zero and store opacity only in the gradient stops.
            packed |= 0xFF000000L;
        }
        w.u32(packed);
        w.u8(flags);
        w.u8(r, "secondaryFlags");
        if ((flags & 0x10) != 0) {
            w.words(r, "matrixWords", 6, true);
            List<FlaRecord> stops = w.records(r, "stops");
            w.u8(Math.min(15, stops.size()));
            if (mode >= 1) {
                w.u32(r, "focalPoint");
                w.u32(r, "gradientFlags");
            }
            for (int i = 0; i < Math.min(15, stops.size()); i++) {
                FlaRecord s = stops.get(i);
                w.u8(s, "ratio");
                w.u32(color(s, "colorRgba", revision));
            }
        } else if ((flags & 0x20) != 0) {
            w.words(r, "matrixWords", 6, true);
            w.u32(r, "extensionValue");
            List<?> words = w.list(r, "extensionWords");
            for (int i = 0; i < 4; i++) {
                w.u16(i < words.size() ? ((Number) words.get(i)).longValue() : 0);
            }
        } else if ((flags & 0x40) != 0) {
            w.words(r, "matrixWords", 6, true);
            w.u16(r, "bitmapMediaId");
        }
    }

    void stroke(FlaRecord r, int mode, int revision) {
        FlaRecord paint = w.record(r, "paint");
        long rgba = color(r, "colorRgba", revision);
        if (mode == 0 && paint != null && (w.n(paint, "flags", 0) & 0x70) != 0) {
            // The outer color is only a placeholder for textured strokes in
            // newer files. It must become visible when their paint is omitted.
            rgba |= 0xFF000000L;
            w.issue("STROKE_PAINT", r, "paint", "Unsupported stroke paint replaced with an opaque solid stroke using its base RGB color.");
        }
        w.u32(rgba);
        w.u16(r, "width");
        w.u16(r, "styleWord1");
        w.u16(r, "styleWord2");
        w.value(r, "styleType");
        w.value(r, "sharpCorners");
        if (mode >= 1) {
            w.u8(r, "pixelHinting");
            w.u8(r, "scaleMode");
            w.u8(r, "capStyle");
            w.u8(r, "joinStyle");
            w.u16(r, "miterLimitWord");
            if (paint == null) {
                w.u32(w.n(r, "colorRgba", 0xFF000000L));
                w.u16(0);
            } else {
                fill(paint, mode, revision);
            }
        }
    }

    void morph(FlaRecord r, String kind) throws IOException {
        switch (kind) {
            case "CPicMorphShape":
                int v = w.revision(r, "revision", w.target().ordinal() >= FlaFormatVersion.F8.ordinal() ? 2 : 1);
                w.words(r, "matrixWordsA", 6, true);
                w.words(r, "matrixWordsB", 6, true);
                if (v >= 2) {
                    w.u32(r, "extensionValue1");
                    w.u32(r, "acceleration");
                }
                w.countedObjects(r, "segments");
                w.countedObjects(r, "hints");
                List<FlaRecord> fills = w.records(r, "fills"),
                 strokes = w.records(r, "strokes");
                w.u16(fills.size());
                for (FlaRecord f : fills) {
                    fill(f, 0, 4);
                }
                w.u16(strokes.size());
                for (FlaRecord s : strokes) {
                    stroke(s, 0, 4);
                }
                break;
            case "CMorphSegment":
                w.dwords(r, "strokeIndex1", "strokeIndex2", "fillIndex1", "fillIndex2");
                w.words(r, "startPointA", 2);
                w.words(r, "startPointB", 2);
                w.countedObjects(r, "curves");
                break;
            case "CMorphCurve":
                for (String f : Arrays.asList("controlPointA", "anchorPointA", "controlPointB", "anchorPointB")) {
                    w.words(r, f, 2);
                }
                w.u32(r, "lineFlags");
                break;
            case "CMorphHintItem":
                w.words(r, "pointA", 2);
                w.words(r, "pointB", 2);
                w.u32(r, "index");
                break;
            default:
                throw new IOException("Unknown morph record " + kind);
        }
    }
}
