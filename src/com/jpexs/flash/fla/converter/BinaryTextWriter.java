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
import java.io.IOException;
import java.util.*;

/**
 * Classic text strings, character runs and target-specific style revisions.
 */
final class BinaryTextWriter {

    final BinaryArchiveWriter w;

    BinaryTextWriter(BinaryArchiveWriter w) {
        this.w = w;
    }

    void text(FlaRecord r) throws IOException {
        new BinaryObjectWriter(w).base(r, w.target().picTextBaseRevision);
        int v = w.revision(r, "revision", w.target().picTextRevision);
        w.words(r, "matrixWords", 6, true);
        w.words(r, "bounds", 4);
        w.u8(r, "autoExpand");
        if (v > 2) {
            w.u8(r, "reservedTextByte");
        }
        long flags = w.n(r, "textFlags", 0);
        if (v >= 4) {
            if (v == 4) {
                w.u16(flags);
            } else {
                w.u32(flags);
            }
            w.u16(r, "maxCharacters");
            w.string(r, "variableName");
            if ((flags & 0x2000) != 0) {
                w.string(r, "fontLibraryName");
            }
        }
        if (v >= 14) {
            w.u8(r, "embedVariantGlyphs");
        }
        if (v >= 4 && (flags & 0x4000) != 0) {
            style(w.record(r, "defaultStyle"), w.target().textRunStyleRevision);
        }
        List<FlaRecord> runs = w.records(r, "runs");
        w.value(r, "text");
        for (FlaRecord run : runs) {
            FlaRecord style = w.record(run, "style");
            int rv = (int) Math.min(w.n(style, "revision", w.target().textRunStyleRevision), w.target().textRunStyleRevision);
            boolean unicode = rv >= 10;
            String text = w.s(run, "text");
            byte[] data = w.textBytes(text, unicode);
            int units = unicode ? data.length / 2 : data.length;
            // Byte encodings can expand a character; split by stored units, not Java chars.
            for (int offset = 0; offset < units;) {
                int length = Math.min(514, units - offset);
                if (unicode && offset + length < units && length > 1 && Character.isHighSurrogate(text.charAt(offset + length - 1))) {
                    length--;
                }
                w.u16(length);
                style(style, rv);
                w.raw(Arrays.copyOfRange(data, offset * (unicode ? 2 : 1), (offset + length) * (unicode ? 2 : 1)));
                offset += length;
            }
            w.value(run, "charactersData");
            w.value(run, "unicode");
        }
        w.u16(0);
        w.value(r, "runTerminator");
        if (v >= 6) {
            w.string(r, "instanceName");
        }
        if (v >= 9) {
            new BinaryLibraryWriter(w).accessibility(w.record(r, "accessibility"), v >= 10);
        }
        if (v >= 8) {
            w.u32(r, "scrollable");
        }
        if (v >= 11) {
            w.string(r, "localizationStringId");
        }
        if (v >= 12) {
            w.string(r, "embedRanges");
        }
        if (v >= 13) {
            new BinaryLibraryWriter(w).filters(r);
            w.u16(w.n(r, "selectedEffectIndex", 65535));
        }
    }

    void style(FlaRecord r, int maximum) {
        int v = w.revision(r, "revision", maximum);
        if (v < 2) {
            return;
        }
        w.u16(w.n(r, "fontSize", 240));
        compact(w.s(r, "fontFamily"), v);
        if (v >= 14) {
            compact(w.s(r, "fontFace"), v);
            w.u32(r, "fontFlags");
        }
        w.u32(w.n(r, "colorRgba", 0xFF000000L));
        w.u8(r, "pitchAndFamily");
        w.u8(r, "charSet");
        w.u8(r, "bold");
        if (v == 2) {
            w.u8(w.n(r, "italic", 0) | w.n(r, "useScreenSpacing", 0) << 1 | (w.n(r, "autoKern", 1) == 0 ? 4 : 0) | w.n(r, "characterPosition", 0) << 3);
        } else {
            w.u8(r, "italic");
            w.u8(r, "useScreenSpacing");
            w.u8(w.n(r, "autoKern", 1));
            w.u8(r, "characterPosition");
        }
        w.u8(r, "alignment");
        for (String field : Arrays.asList("lineSpacing", "indent", "leftMargin", "rightMargin", "letterSpacing")) {
            w.u16(r, field);
        }
        if (v >= 5) {
            compact(w.s(r, "url"), v);
        }
        if (v >= 6) {
            w.u8(r, "vertical");
            w.u8(r, "rightToLeft");
            w.u8(r, "rotation");
        }
        if (v >= 8) {
            w.u8(r, "bitmapRendering");
        }
        if (v >= 7) {
            compact(w.s(r, "target"), v);
        }
        if (v >= 11) {
            FlaRecord aa = w.record(r, "antiAlias");
            int av = w.revision(aa, "revision", 2);
            w.u8(aa, "mode");
            if (av == 1) {
                w.words(aa, "legacyWords", 3);
            }
            w.f32(w.d(aa, "thickness", 0));
            w.f32(w.d(aa, "sharpness", 0));
        }
        if (v >= 12) {
            compact(w.s(r, "urlOverride"), v);
        }
        if (v == 13) {
            compact(w.s(r, "legacyFontName"), v);
            w.u32(r, "legacyFontValue");
        }
    }

    private void compact(String text, int revision) {
        if (revision >= 15) {
            w.string(text);
            return;
        }
        boolean unicode = revision >= 10;
        byte[] bytes = w.textBytes(text, unicode);
        int unit = unicode ? 2 : 1;
        int count = Math.min(255, bytes.length / unit);
        if (count < bytes.length / unit) {
            w.issue("STYLE_STRING_TRUNCATED", null, "", "Text-style string reduced to 255 stored units.");
        }
        w.u8(count);
        w.raw(Arrays.copyOf(bytes, count * unit));
    }
}
