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
 * Common symbol-instance data and the distinct movie-clip/button extensions.
 */
final class InstanceRecordDecoder {

    private InstanceRecordDecoder() {
    }

    static void symbol(MfcArchive a, FlaRecord r) throws IOException {
        DefaultFlaDecoders.picBase(a, r);
        int v = DefaultFlaDecoders.revision(a, r, "revision", 23);
        r.set("matrixWords", DefaultFlaDecoders.integers(a, 6));
        r.set("firstFrame", a.readUI16());
        if (v >= 2) {
            r.set("loopMode", a.readUI16());
        }
        if (v >= 7) {
            a.readInline(r, "colorTransform", InstanceRecordDecoder::colorTransform);
        } else {
            if (v >= 3) {
                a.readInline(r, "colorTransform", (in, color) -> colorWords(in, color, v >= 5));
            }
            if (v >= 4) {
                r.set("colorEffectType", a.readUI16());
                r.set("colorEffectPercent", a.readI16());
                r.set("colorEffectRgba", a.readUI32());
            }
        }
        if (v >= 6) {
            r.set("legacyName", a.readString());
        }
        r.set("libraryId", a.readUI32());
        if (v == 8 || v == 9) {
            r.set("legacyCustomUISourceFilename", a.readString());
            r.set("legacySymbolDescription", a.readString());
            a.readInline(r, "legacyProperties", PropertyRecordDecoder::list);
            r.set("legacyParametersAreLocked", a.readUI32());
            if (v == 9) {
                r.set("legacyComponentName", a.readString());
            }
        }
        if (v >= 11) {
            int present = a.readUI8();
            r.set("timelineEffectPresent", present);
            if (present != 0) {
                a.readInline(r, "timelineEffect", (in, component) -> TimelineEffectRecordDecoder.read(in, component, v));
            }
        }
        if (v >= 12) {
            int present = a.readUI8();
            r.set("optionalRecordPresent", present);
            if (present != 0) {
                a.readInline(r, "persistentData", DocumentTailRecordDecoder::persistentData);
            }
        }
        if (v >= 14) {
            r.set("isExtensible", a.readUI8());
        }
        if (v >= 15) {
            filters(a, r);
        }
        if (v >= 16) {
            r.set("blendMode", a.readUI8());
        }
        if (v >= 17) {
            r.set("selectedEffectIndex", a.readUI16());
        }
        if (v >= 20) {
            List<Float> matrix = new ArrayList<>();
            r.set("matrix3D", Collections.unmodifiableList(matrix));
            for (int i = 0; i < 16; i++) {
                matrix.add(a.readFloat());
            }
            List<Double> rotations = new ArrayList<>();
            r.set("rotation3D", Collections.unmodifiableList(rotations));
            for (int i = 0; i < 3; i++) {
                rotations.add(a.readDouble());
            }
        }
        if (v >= 21) {
            r.set("centerPoint3D", DefaultFlaDecoders.integers(a, 3));
        }
        if (v >= 22) {
            int count = a.checkedCount(a.readUI16(), 4);
            List<FlaRecord> settings = new ArrayList<>();
            r.set("animationFrameSettings", Collections.unmodifiableList(settings));
            for (int i = 0; i < count; i++) {
                a.readInline("animationFrameSetting", settings::add, (in, setting) -> {
                    setting.set("loopMode", in.readUI16());
                    setting.set("firstFrame", in.readUI16());
                });
            }
        }
        if (v >= 23) {
            r.set("bitmapRenderMode", a.readUI16());
            r.set("bitmapBits32", a.readUI32());
            r.set("bitmapBackgroundColorRgba", a.readUI32());
            r.set("isVisible", a.readUI32());
        }
    }

    private static void colorWords(MfcArchive a, FlaRecord r, boolean alpha) throws IOException {
        if (alpha) {
            r.set("alphaMultiplier", a.readI16());
            r.set("alphaOffset", a.readI16());
        }
        r.set("redMultiplier", a.readI16());
        r.set("redOffset", a.readI16());
        r.set("greenMultiplier", a.readI16());
        r.set("greenOffset", a.readI16());
        r.set("blueMultiplier", a.readI16());
        r.set("blueOffset", a.readI16());
    }

    static void colorTransform(MfcArchive a, FlaRecord r) throws IOException {
        int revision = a.readUI8();
        r.set("revision", revision);
        if (revision != 1) {
            throw new UnsupportedFlaRecordException("Color-transform revision " + revision);
        }
        colorWords(a, r, true);
        r.set("effectType", a.readUI16());
        r.set("effectPercent", a.readI16());
        r.set("effectColorRgba", a.readUI32());
    }

    static void sprite(MfcArchive a, FlaRecord r) throws IOException {
        symbol(a, r);
        int v = DefaultFlaDecoders.revision(a, r, "spriteRevision", 8);
        if (v >= 2) {
            a.readInline(r, "actions", DefaultFlaDecoders::frameActions);
        }
        r.set("instanceName", a.readString());
        if (v >= 3) {
            a.readInline(r, "properties", PropertyRecordDecoder::list);
        }
        if (v >= 6) {
            a.readInline(r, "accessibility", (in, access) -> accessibility(in, access, v >= 7));
        }
        if (v >= 5) {
            r.set("propertyInspectorState", a.readUI32());
        }
        if (v >= 8) {
            a.readInline(r, "componentXml", InstanceRecordDecoder::componentXml);
        }
    }

    static void button(MfcArchive a, FlaRecord r) throws IOException {
        symbol(a, r);
        int v = DefaultFlaDecoders.revision(a, r, "buttonRevision", 11);
        if (v == 2) {
            a.readInline(r, "actions", ActionRecordDecoder::historical);
        }
        if (v == 3 || v == 4) {
            FlaRecord actions = a.readInline(r, "actions", ActionRecordDecoder::legacyList);
            r.set("legacyActionCount", actions.get("count"));
        } else if (v >= 5) {
            a.readInline(r, "actions", DefaultFlaDecoders::frameActions);
        }
        if (v >= 4) {
            r.set("trackAsMenu", a.readUI8());
        }
        if (v == 9 || v == 10) {
            FlaRecord name = a.readInline(r, "historicalInstanceName",
                    (in, value) -> historicalWideString(in, value, v == 9));
            r.set("instanceName", name.get("value"));
        } else if (v >= 6) {
            r.set("instanceName", a.readString());
        }
        if (v >= 8) {
            a.readInline(r, "accessibility", (in, access) -> accessibility(in, access, v >= 11, v == 9));
        }
    }

    static void componentXml(MfcArchive a, FlaRecord r) throws IOException {
        r.set("revision", a.readUI8());
        r.set("uniqueId", a.readUI32());
        r.set("xml", a.readString());
    }

    /**
     * A byte-counted UTF-16 buffer; historical consumers stop at the first zero
     * code unit.
     */
    private static void historicalWideString(MfcArchive a, FlaRecord r, boolean detectByteOrder) throws IOException {
        long length = a.readUI32();
        r.set("byteCount", length);
        BinaryData data = a.readData(length);
        r.set("data", data);
        if ((length & 1) != 0) {
            throw a.error("Odd historical UTF-16 byte count");
        }
        boolean swapped = detectByteOrder && length != 0 && data.unsignedByte(0) == 0;
        r.set("byteSwapped", swapped);
        char[] units = new char[data.size() / 2];
        int count = 0;
        for (int i = 0; i < units.length; i++) {
            int first = data.unsignedByte(2 * i);
            int second = data.unsignedByte(2 * i + 1);
            char unit = (char) (swapped ? (first << 8) | second : first | (second << 8));
            if (unit == 0) {
                break;
            }
            units[count++] = unit;
        }
        r.set("value", new String(units, 0, count));
    }

    static void accessibility(MfcArchive a, FlaRecord r, boolean unicodeContext) throws IOException {
        accessibility(a, r, unicodeContext, false);
    }

    private static void accessibility(MfcArchive a, FlaRecord r, boolean unicodeContext,
            boolean historicalWideStrings) throws IOException {
        long revision = a.readUI32();
        r.set("revision", revision);
        r.set("unicodeContext", unicodeContext);
        if (revision > 2) {
            throw new UnsupportedFlaRecordException("Accessibility revision " + revision);
        }
        if (revision != 0) {
            r.set("flags", a.readUI32());
            if (historicalWideStrings) {
                r.set("historicalWideStrings", true);
                for (String field : new String[]{"name", "description", "shortcut"}) {
                    FlaRecord storage = a.readInline(r, field + "Storage",
                            (in, value) -> historicalWideString(in, value, false));
                    r.set(field, storage.get("value"));
                }
            } else {
                r.set("name", a.readString());
                r.set("description", a.readString());
                r.set("shortcut", a.readString());
            }
            if (unicodeContext && revision >= 2) {
                r.set("tabIndex", a.readString());
                r.set("reservedString", a.readString());
            }
            r.set("forceSimple", a.readUI32());
            r.set("autoLabeling", a.readUI32());
        }
    }

    static void filters(MfcArchive a, FlaRecord r) throws IOException {
        int present = a.readUI8();
        r.set("filtersPresent", present);
        if (present != 0) {
            a.readInline(r, "filters", FilterRecordDecoder::list);
        }
    }
}
