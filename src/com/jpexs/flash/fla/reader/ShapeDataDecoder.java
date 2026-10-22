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
 * Shape-local style tables, delta-coded edges and auxiliary geometry.
 */
final class ShapeDataDecoder {

    private ShapeDataDecoder() {
    }

    static void read(MfcArchive a, FlaRecord r, int mode) throws IOException {
        int revision = a.readUI8();
        r.set("revision", revision);
        r.set("extensionMode", mode);
        if (revision > 5) {
            throw new UnsupportedFlaRecordException("Shape data revision " + revision);
        }
        r.set("metadata", a.readUI32());
        int fills = a.checkedCount(a.readUI16(), 6);
        r.set("fillCount", fills);
        records(a, r, "fills", fills, (in, fill) -> {
            if (revision < 3) {
                fill.set("packedValue", in.readUI32());
                fill.set("legacyValue", in.readUI16());
            } else {
                fill(in, fill, mode);
            }
        });
        int strokes = a.checkedCount(a.readUI16(), mode >= 1 ? 22 : 10);
        r.set("strokeCount", strokes);
        records(a, r, "strokes", strokes, (in, stroke) -> stroke(in, stroke, mode));
        List<FlaRecord> edges = new ArrayList<>();
        r.set("edges", Collections.unmodifiableList(edges));
        if (revision >= 2) {
            edges(a, r, edges, mode);
        }
        if (revision >= 5) {
            int count = a.checkedCount(a.readUI32(), 32 + (mode >= 3 ? 1 : 0) + (mode >= 4 ? 1 : 0));
            r.set("auxiliaryCount", (long) count);
            records(a, r, "auxiliary", count, (in, auxiliary) -> auxiliary(in, auxiliary, mode));
        }
    }

    static void fill(MfcArchive a, FlaRecord r, int mode) throws IOException {
        r.set("packedValue", a.readUI32());
        int flags = a.readUI8();
        r.set("flags", flags);
        r.set("secondaryFlags", a.readUI8());
        // These branches have precedence, rather than being independent optional blocks.
        if ((flags & 0x10) != 0) {
            r.set("matrixWords", words(a, 6));
            int count = a.readUI8();
            r.set("stopCount", count);
            if (count > 15) {
                throw a.error("Gradient stop count exceeds the native style capacity");
            }
            if (mode >= 1) {
                r.set("focalPoint", a.readI32());
                r.set("gradientFlags", a.readUI32());
            }
            a.checkedCount(count, 5);
            records(a, r, "stops", count, (in, stop) -> {
                stop.set("ratio", in.readUI8());
                stop.set("colorRgba", in.readUI32());
            });
        } else if ((flags & 0x20) != 0) {
            r.set("matrixWords", words(a, 6));
            r.set("extensionValue", a.readUI32());
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                values.add(a.readUI16());
            }
            r.set("extensionWords", Collections.unmodifiableList(values));
        } else if ((flags & 0x40) != 0) {
            r.set("matrixWords", words(a, 6));
            r.set("bitmapMediaId", a.readUI16());
        }
    }

    static void stroke(MfcArchive a, FlaRecord r, int mode) throws IOException {
        r.set("colorRgba", a.readUI32());
        r.set("width", a.readUI16());
        int first = a.readUI16();
        int second = a.readUI16();
        r.set("styleWord1", first);
        r.set("styleWord2", second);
        r.set("styleType", first != 0 ? 1 : second & 7);
        r.set("sharpCorners", (second & 0x8000) != 0);
        if (mode >= 1) {
            r.set("pixelHinting", a.readUI8());
            r.set("scaleMode", a.readUI8());
            r.set("capStyle", a.readUI8());
            r.set("joinStyle", a.readUI8());
            r.set("miterLimitWord", a.readUI16());
            a.readInline(r, "paint", (in, paint) -> fill(in, paint, mode));
        }
    }

    private static void edges(MfcArchive a, FlaRecord r, List<FlaRecord> edges, int mode) throws IOException {
        int[] previous = {0, 0};
        int[] styles = {0, 0, 0};
        r.set("firstEdgeControl", a.peekUI8());
        while (a.peekUI8() != 0) {
            a.checkedCount((long) edges.size() + 1, 0);
            a.readInline("edge", edges::add, (in, edge) -> {
                int control = in.readUI8();
                edge.set("control", control);
                if ((control & 0x40) != 0) {
                    for (int i = 0; i < 3; i++) {
                        styles[i] = (control & 0x80) != 0 ? in.readUI8() : in.readUI16();
                    }
                }
                edge.set("strokeIndex", styles[0]);
                edge.set("fillIndex0", styles[1]);
                edge.set("fillIndex1", styles[2]);
                List<Integer> startDelta = vector(in, control & 3);
                edge.set("startDelta", startDelta);
                List<Integer> controlDelta = vector(in, (control >> 2) & 3);
                edge.set("controlDelta", controlDelta);
                List<Integer> endDelta = vector(in, (control >> 4) & 3);
                edge.set("endDelta", endDelta);
                // Integer addition intentionally preserves the archive's signed 32-bit arithmetic.
                int x = previous[0] + startDelta.get(0);
                int y = previous[1] + startDelta.get(1);
                edge.set("startX", x);
                edge.set("startY", y);
                boolean curve = (control & 0x0C) != 0;
                edge.set("curve", curve);
                if (curve) {
                    edge.set("controlX", x + controlDelta.get(0));
                    edge.set("controlY", y + controlDelta.get(1));
                } else if (mode >= 2) {
                    edge.set("lineFlags", in.readUI8());
                }
                previous[0] = x + endDelta.get(0);
                previous[1] = y + endDelta.get(1);
                edge.set("endX", previous[0]);
                edge.set("endY", previous[1]);
            });
        }
        r.set("edgeTerminator", a.readUI8());
    }

    private static List<Integer> vector(MfcArchive a, int mode) throws IOException {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            result.add(mode == 0 ? 0 : mode == 2 ? a.readI32() : a.readI16() << (mode == 3 ? 7 : 0));
        }
        return Collections.unmodifiableList(result);
    }

    private static void auxiliary(MfcArchive a, FlaRecord r, int mode) throws IOException {
        r.set("points", words(a, 8));
        if (mode >= 3) {
            int count = a.checkedCount(a.readUI8(), 10);
            records(a, r, "entries", count, (in, entry) -> {
                entry.set("x", in.readI32());
                entry.set("y", in.readI32());
                entry.set("controlFlag", in.readUI8());
                entry.set("value2", in.readUI8());
            });
        }
        if (mode >= 4) {
            int flags = a.readUI8();
            r.set("flags", flags);
            if ((flags & 1) != 0) {
                r.set("boundaryStartPointTwips", words(a, 2));
            }
            if ((flags & 2) != 0) {
                r.set("boundaryEndPointTwips", words(a, 2));
            }
        }
    }

    private static List<Integer> words(MfcArchive a, int count) throws IOException {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(a.readI32());
        }
        return Collections.unmodifiableList(result);
    }

    private static void records(MfcArchive a, FlaRecord owner, String field, int count,
            FlaRecordDecoder decoder) throws IOException {
        List<FlaRecord> result = new ArrayList<>();
        owner.set(field, Collections.unmodifiableList(result));
        for (int i = 0; i < count; i++) {
            a.readInline(field, result::add, decoder);
        }
    }
}
