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
 * Serializers for historical commands and later action-source containers.
 */
final class ActionRecordDecoder {

    private ActionRecordDecoder() {
    }

    static void historical(MfcArchive a, FlaRecord r) throws IOException {
        int v = DefaultFlaDecoders.revision(a, r, "revision", 10);
        r.set("kind", a.readUI8());
        strings(a, r, "url", "window", "page");
        r.set("frameNumber", a.readUI16());
        if (v >= 1) {
            r.set("waitForExtraFrames", a.readI16());
        }
        if (v >= 2) {
            strings(a, r, "label");
        }
        if (v >= 3) {
            strings(a, r, "arg0", "arg1", "target");
            r.set("level", a.readI32());
        }
        if (v >= 4) {
            words(a, r, "release", "press", "releaseOutside", "rollOver", "rollOut", "dragOver", "dragOut");
        }
        if (v >= 5) {
            words(a, r, "property");
            strings(a, r, "reservedString1", "reservedString2", "depth", "variableName");
        }
        if (v >= 6) {
            strings(a, r, "method");
            words(a, r, "loadVariables");
        }
        if (v >= 7) {
            strings(a, r, "arg2", "arg3");
            words(a, r, "variableNameIsExpression", "urlIsExpression", "arg0IsExpression",
                    "arg1IsExpression", "arg2IsExpression", "arg3IsExpression", "windowIsExpression",
                    "targetIsExpression", "depthIsExpression", "start", "constrainToRectangle", "lockMouseToCenter");
        }
        if (v >= 8) {
            words(a, r, "onKeyPress", "key");
        }
        if (v >= 9) {
            long length = a.readUI32();
            r.set("extraByteCount", length);
            r.set("extraData", a.readData(length));
        }
    }

    static void legacyList(MfcArchive a, FlaRecord r) throws IOException {
        long count = a.readUI32();
        r.set("count", count);
        int size = a.checkedCount(count, 7);
        List<FlaRecord> commands = new ArrayList<>();
        r.set("commands", Collections.unmodifiableList(commands));
        for (int i = 0; i < size; i++) {
            a.readInline("historicalAction", commands::add, ActionRecordDecoder::historical);
        }
    }

    static void source(MfcArchive a, FlaRecord r) throws IOException {
        long v = a.readUI32();
        r.set("revision", v);
        if (v > 5) {
            throw new UnsupportedFlaRecordException("Frame action-source revision " + v);
        }
        long mode = a.readUI32();
        r.set("mode", mode);
        if (v >= 1) {
            words(a, r, "identifier");
            int count = a.checkedCount(a.readUI32(), 4);
            List<Long> indices = new ArrayList<>();
            r.set("breakpointIndices", Collections.unmodifiableList(indices));
            for (int i = 0; i < count; i++) {
                indices.add(a.readUI32());
            }
        }
        if (v >= 5) {
            int count = a.checkedCount(a.readUI32(), 4);
            r.set("codeCollapseWordCount", count);
            List<Integer> words = new ArrayList<>();
            List<FlaRecord> ranges = new ArrayList<>();
            r.set("codeCollapseRangeWords", Collections.unmodifiableList(words));
            r.set("codeCollapseRanges", Collections.unmodifiableList(ranges));
            for (int i = 0; i < count; i += 2) {
                final boolean hasLength = i + 1 < count;
                a.readInline("codeCollapseRange", ranges::add, (in, range) -> {
                    int offset = in.readI32();
                    range.set("offset", offset);
                    words.add(offset);
                    if (hasLength) {
                        int length = in.readI32();
                        range.set("length", length);
                        words.add(length);
                    }
                });
            }
        }
        if (mode == 1) {
            if (v == 3) {
                // This variant uses a CString length, but its data is always UTF-16LE.
                MfcArchive.StringHeader header = a.readStringHeader();
                BinaryData data = a.readData(header.getByteLength());
                r.set("sourceData", data);
                r.set("sourceLength", header.getLength());
                r.set("sourceUnicodeMarker", header.isUnicode());
                char[] units = new char[data.size() / 2];
                for (int i = 0; i < units.length; i++) {
                    units[i] = (char) (data.unsignedByte(2 * i) | (data.unsignedByte(2 * i + 1) << 8));
                }
                r.set("source", new String(units));
            } else {
                r.set("source", a.readString());
            }
        } else if (mode == 0) {
            a.readInline(r, "compiledCommands", CompiledActionRecordDecoder::list);
        } else if (mode != 2) {
            throw new UnsupportedFlaRecordException("Action-source mode " + mode);
        }
    }

    private static void strings(MfcArchive a, FlaRecord r, String... names) throws IOException {
        for (String name : names) {
            r.set(name, a.readString());
        }
    }

    private static void words(MfcArchive a, FlaRecord r, String... names) throws IOException {
        for (String name : names) {
            r.set(name, a.readUI32());
        }
    }
}
