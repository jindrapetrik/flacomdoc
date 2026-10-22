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
 * Typed authoring commands stored by compiled-mode action containers.
 */
final class CompiledActionRecordDecoder {

    private CompiledActionRecordDecoder() {
    }

    static void list(MfcArchive a, FlaRecord r) throws IOException {
        long revision = a.readUI32();
        r.set("revision", revision);
        int count = a.readI32();
        r.set("count", count);
        int size = a.checkedCount(Math.max(0, count), 4);
        List<FlaRecord> commands = new ArrayList<>();
        r.set("commands", Collections.unmodifiableList(commands));
        for (int i = 0; i < size; i++) {
            a.readInline("compiledCommand", commands::add, (in, command) -> command(in, command, revision));
        }
    }

    private static void command(MfcArchive a, FlaRecord r, long listRevision) throws IOException {
        long type = a.readUI32();
        r.set("type", type);
        if (isSimple(type)) {
            boolean present = listRevision != 0;
            r.set("bodyPresent", present);
            if (present) {
                long revision = revision(a, r);
                if (revision >= 2) {
                    metadata(a, r);
                }
            }
        } else if (type == 0) {
            r.set("bodyPresent", true);
            long revision = revision(a, r);
            r.set("navigationMode", a.readUI32());
            r.set("scene", a.readString());
            r.set("frame", a.readString());
            r.set("play", a.readUI32());
            if (revision >= 2) {
                metadata(a, r);
            }
        } else if (type == 5) {
            r.set("bodyPresent", true);
            long revision = revision(a, r);
            r.set("url", a.readString());
            r.set("window", a.readString());
            r.set("urlFlags", a.readUI32());
            r.set("windowFlags", a.readUI32());
            r.set("method", a.readUI32());
            if (revision >= 2) {
                metadata(a, r);
            }
        } else if (type == 11 || type == 13) {
            r.set("bodyPresent", true);
            long revision = revision(a, r);
            r.set("eventFlags", a.readUI32());
            if (type == 11) {
                r.set("keyCode", a.readUI32());
                if (revision >= 1) {
                    r.set("eventString", a.readString());
                }
            }
            if (revision >= 2) {
                metadata(a, r);
            }
        } else {
            ExtendedCompiledActionRecordDecoder.read(a, r, type, listRevision);
        }
    }

    private static boolean isSimple(long type) {
        if (type > Integer.MAX_VALUE) {
            return false;
        }
        switch ((int) type) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 9:
            case 12:
            case 14:
            case 16:
            case 18:
            case 19:
            case 21:
            case 29:
            case 38:
            case 42:
            case 45:
            case 48:
            case 51:
            case 52:
            case 54:
            case 58:
            case 61:
            case 64:
            case 66:
            case 68:
            case 70:
            case 72:
            case 74:
            case 81:
            case 85:
                return true;
            default:
                return false;
        }
    }

    private static long revision(MfcArchive a, FlaRecord r) throws IOException {
        return revision(a, r, 2);
    }

    static long revision(MfcArchive a, FlaRecord r, int maximum) throws IOException {
        long revision = a.readUI32();
        r.set("revision", revision);
        if (revision > maximum) {
            throw new UnsupportedFlaRecordException("Compiled action revision " + revision);
        }
        return revision;
    }

    static void metadata(MfcArchive a, FlaRecord r) throws IOException {
        r.set("sourcePrefix", a.readString());
        r.set("sourceText", a.readString());
        r.set("sourceSuffix", a.readString());
        r.set("value40", a.readUI32());
        r.set("suppressBlockDelimiter", a.readUI32());
    }
}
