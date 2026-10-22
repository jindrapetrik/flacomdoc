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
import java.util.*;

/**
 * Typed filter payloads; their revisions are independent of the owner.
 */
final class BinaryFilterWriter {

    final BinaryArchiveWriter w;

    BinaryFilterWriter(BinaryArchiveWriter w) {
        this.w = w;
    }

    void filter(FlaRecord r) {
        int type = (int) w.n(r, "type", 0);
        w.u8(type);
        w.value(r, "kind");
        switch (type) {
            case 0:
                shadow(r);
                break;
            case 1:
                blur(r);
                break;
            case 2:
                glow(r);
                break;
            case 3:
                bevel(r);
                break;
            case 4:
                gradient(r);
                break;
            case 6:
                if (w.revision(r, "revision", 1) != 0) {
                    state(w.record(r, "state"));
                    floats(r, "brightness", "contrast", "saturation", "hue");
                }
                break;
            case 7:
                if (w.revision(r, "revision", 1) != 0) {
                    gradient(w.record(r, "gradient"));
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown filter type " + type);
        }
    }

    void floats(FlaRecord r, String... fields) {
        for (String f : fields) {
            w.f32(w.d(r, f, 0));
        }
    }

    void state(FlaRecord r) {
        if (w.revision(r, "revision", 1) != 0) {
            w.u32(w.n(r, "enabled", 1));
        }
    }

    void shadow(FlaRecord r) {
        int v = w.revision(r, "revision", 4);
        if (v == 1 || v == 2) {
            floats(r, "angleRadians");
            w.u32(r, "colorRgba");
            floats(r, "blurX", "distance");
            w.dwords(r, "inner", "knockout");
            if (v == 2) {
                state(w.record(r, "state"));
                w.u32(r, "quality");
            }
        } else if (v >= 3) {
            state(w.record(r, "state"));
            w.u32(r, "colorRgba");
            floats(r, "distance", "blurX", "blurY", "angleRadians");
            w.dwords(r, "inner", "knockout", "quality", "strengthPercent");
            if (v >= 4) {
                w.u32(r, "hideObject");
            }
        }
    }

    void blur(FlaRecord r) {
        int v = w.revision(r, "revision", 3);
        if (v == 1) {
            w.u32(r, "legacyColorRgba");
            floats(r, "blurX", "blurY");
        } else if (v == 2) {
            floats(r, "blurX", "blurY");
            state(w.record(r, "state"));
            w.u32(r, "quality");
        } else if (v >= 3) {
            shadow(w.record(r, "settings"));
        }
    }

    void glow(FlaRecord r) {
        int v = w.revision(r, "revision", 3);
        if (v == 1 || v == 2) {
            w.u32(r, "colorRgba");
            floats(r, "blurX");
            w.dwords(r, "inner", "knockout");
            if (v == 2) {
                state(w.record(r, "state"));
                w.u32(r, "quality");
            }
        } else if (v >= 3) {
            shadow(w.record(r, "settings"));
        }
    }

    void bevel(FlaRecord r) {
        int v = w.revision(r, "revision", 3);
        if (v == 2) {
            floats(r, "angleRadians");
            w.u32(r, "colorRgba");
            floats(r, "blurX", "distance");
            w.dwords(r, "inner", "knockout");
            state(w.record(r, "state"));
            w.u32(r, "quality");
            w.u8(r, "legacyByte");
            w.dwords(r, "highlightColorRgba", "legacyWord", "full");
        } else if (v == 3) {
            shadow(w.record(r, "settings"));
            w.dwords(r, "highlightColorRgba", "full");
        }
    }

    void gradient(FlaRecord r) {
        if (w.revision(r, "revision", 1) == 0) {
            return;
        }
        shadow(w.record(r, "settings"));
        List<FlaRecord> stops = w.records(r, "stops");
        w.u32(stops.size());
        w.dwords(r, "gradientValue", "full");
        for (FlaRecord s : stops) {
            w.dwords(s, "ratio", "colorRgba");
        }
    }
}
