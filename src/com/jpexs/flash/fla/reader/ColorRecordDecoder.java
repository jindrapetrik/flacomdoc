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
 * Document swatch lists and independently versioned color definitions.
 */
final class ColorRecordDecoder {

    private ColorRecordDecoder() {
    }

    static void list(MfcArchive a, FlaRecord r) throws IOException {
        int count = a.readCount();
        r.set("colorCount", count);
        a.readObjectList(r, "colors", count);
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 1);
        if (revision >= 1) {
            int children = a.checkedCount(a.readUI8(), 3);
            r.set("childCount", children);
            List<FlaRecord> lists = new ArrayList<>();
            r.set("lists", Collections.unmodifiableList(lists));
            for (int i = 0; i < children; i++) {
                a.readInline("colorList", lists::add, ColorRecordDecoder::list);
            }
        }
    }

    static void color(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 4);
        if (revision < 2) {
            r.set("legacyBytes", a.readData(2));
            r.set("packedValue", a.readUI32());
            r.set("legacyValue", a.readUI32());
            r.set("legacyWord", a.readUI16());
            r.set("legacyMatrixWords", DefaultFlaDecoders.integers(a, 6));
            List<Integer> words = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                words.add(a.readUI16());
            }
            r.set("legacyWords", Collections.unmodifiableList(words));
        } else {
            r.set("packedValue", a.readUI32());
            int flags = a.readUI8();
            r.set("flags", flags);
            r.set("secondaryFlags", a.readUI8());
            if ((flags & 0x10) != 0) {
                r.set("matrixWords", DefaultFlaDecoders.integers(a, 6));
                int stored = a.readUI8();
                r.set("stopCount", stored);
                int count = Math.min(stored, 15);
                r.set("effectiveStopCount", count);
                if (revision >= 4) {
                    r.set("focalPoint", a.readI32());
                    r.set("gradientFlags", a.readUI32());
                }
                a.checkedCount(count, 5);
                List<FlaRecord> stops = new ArrayList<>();
                r.set("stops", Collections.unmodifiableList(stops));
                for (int i = 0; i < count; i++) {
                    a.readInline("colorStop", stops::add, (in, stop) -> {
                        stop.set("ratio", in.readUI8());
                        stop.set("colorRgba", in.readUI32());
                    });
                }
            } else if ((flags & 0x20) != 0) {
                r.set("matrixWords", DefaultFlaDecoders.integers(a, 6));
                r.set("extensionValue", a.readUI32());
                List<Integer> words = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    words.add(a.readUI16());
                }
                r.set("extensionWords", Collections.unmodifiableList(words));
            }
        }
        if (revision >= 1) {
            r.set("hue", a.readUI16());
            r.set("saturation", a.readUI16());
            r.set("brightness", a.readUI16());
        }
    }
}
