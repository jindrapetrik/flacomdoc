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
 * Legacy timeline-effect identity, script reference and typed parameters.
 */
final class TimelineEffectRecordDecoder {

    private TimelineEffectRecordDecoder() {
    }

    static void read(MfcArchive a, FlaRecord r, int symbolRevision) throws IOException {
        r.set("effectName", a.readString());
        r.set("groupName", a.readString());
        r.set("symbolType", a.readString());
        if (symbolRevision >= 18) {
            r.set("originalLayerName", a.readString());
        }
        if (symbolRevision >= 19) {
            r.set("effectId", a.readString());
        }
        r.set("sourceScriptFilename", a.readString());
        int count = a.readI32();
        r.set("count", count);
        int size = a.checkedCount(Math.max(0, count), 5);
        List<FlaRecord> parameters = new ArrayList<>();
        r.set("parameters", Collections.unmodifiableList(parameters));
        for (int i = 0; i < size; i++) {
            a.readInline("componentParameter", parameters::add, TimelineEffectRecordDecoder::parameter);
        }
    }

    private static void parameter(MfcArchive a, FlaRecord r) throws IOException {
        r.set("name", a.readString());
        long type = a.readUI32();
        r.set("type", type);
        if (type > 6) {
            throw new UnsupportedFlaRecordException("Symbol component parameter type " + type);
        }
        switch ((int) type) {
            case 0:
            case 4:
                r.set("value", a.readI32());
                break;
            case 1:
            case 6:
                r.set("value", a.readDouble());
                break;
            case 2:
                r.set("value", a.readString());
                break;
            case 3:
                r.set("value", a.readUI32());
                break;
            case 5:
                int count = a.readI32();
                r.set("count", count);
                int size = a.checkedCount(Math.max(0, count), 1);
                List<String> values = new ArrayList<>();
                r.set("values", Collections.unmodifiableList(values));
                for (int i = 0; i < size; i++) {
                    values.add(a.readString());
                }
                r.set("selectedIndex", a.readI32());
                break;
            default:
                throw new AssertionError();
        }
        r.set("minimum", a.readUI32());
        r.set("maximum", a.readUI32());
        r.set("maximumLength", a.readUI32());
        r.set("variableName", a.readString());
        r.set("displayOrder", a.readUI32());
    }
}
