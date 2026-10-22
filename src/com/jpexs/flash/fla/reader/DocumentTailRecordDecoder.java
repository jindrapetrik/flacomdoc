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
 * Direct archive records shared by document, library and instance extensions.
 */
final class DocumentTailRecordDecoder {

    private DocumentTailRecordDecoder() {
    }

    static void fonts(MfcArchive a, FlaRecord r) throws IOException {
        r.set("header", a.readUI32());
        long count = a.readUI32();
        r.set("count", count);
        int size = a.checkedCount(count, 1);
        List<FlaRecord> entries = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(entries));
        for (int i = 0; i < size; i++) {
            a.readInline("libraryFont", entries::add, DocumentTailRecordDecoder::font);
        }
    }

    static void font(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 3);
        r.set("name", a.readString());
        r.set("fontId", a.readUI16());
        r.set("sourceTime", a.readUI32());
        a.readInline(r, "style", TextRecordDecoder::style);
        a.readInline(r, "libraryMetadata", DefaultFlaDecoders::libraryMetadata);
        if (revision >= 3) {
            r.set("fontFlags", a.readUI32());
        }
    }

    static void sharedLibraries(MfcArchive a, FlaRecord r) throws IOException {
        DefaultFlaDecoders.revision(a, r, "revision", 0);
        long count = a.readUI32();
        r.set("count", count);
        int size = a.checkedCount(count, 1);
        List<FlaRecord> entries = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(entries));
        for (int i = 0; i < size; i++) {
            a.readInline("sharedLibrary", entries::add, DocumentTailRecordDecoder::sharedLibrary);
        }
    }

    static void sharedLibrary(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 2);
        r.set("name", a.readString());
        long bytes = a.readUI32();
        r.set("byteCount", bytes);
        r.set("data", a.readData(bytes));
        if (revision >= 1) {
            stringList(a, r, "names");
        }
        if (revision == 1) {
            stringList(a, r, "legacyNames");
        }
    }

    private static void stringList(MfcArchive a, FlaRecord r, String field) throws IOException {
        long count = a.readUI32();
        r.set(field + "Count", count);
        int size = a.checkedCount(count, 1);
        List<String> values = new ArrayList<>();
        r.set(field, Collections.unmodifiableList(values));
        for (int i = 0; i < size; i++) {
            values.add(a.readString());
        }
    }

    static void persistentData(MfcArchive a, FlaRecord r) throws IOException {
        r.set("header", a.readUI32());
        int count = a.readI32();
        r.set("count", count);
        // The native container treats nonpositive signed counts as empty.
        int size = a.checkedCount(Math.max(0, count), 1);
        List<FlaRecord> entries = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(entries));
        for (int i = 0; i < size; i++) {
            a.readInline("persistentValue", entries::add, DocumentTailRecordDecoder::persistentValue);
        }
    }

    private static void persistentValue(MfcArchive a, FlaRecord r) throws IOException {
        int type = a.readUI8();
        r.set("type", type);
        if (type != 0 && type != 1 && type != 3 && type != 4 && type != 5 && type != 7) {
            throw new UnsupportedFlaRecordException("Persistent-data value type " + type);
        }
        r.set("name", a.readString());
        switch (type) {
            case 0:
                r.set("value", a.readI32());
                break;
            case 3:
                r.set("value", a.readDouble());
                break;
            case 5:
                r.set("value", a.readString());
                break;
            default:
                long count = a.readUI32();
                r.set("count", count);
                int size = a.checkedCount(count, type == 1 ? 4 : type == 4 ? 8 : 1);
                if (type == 7) {
                    r.set("data", a.readData(size));
                } else {
                    List<Number> values = new ArrayList<>();
                    r.set("values", Collections.unmodifiableList(values));
                    for (int i = 0; i < size; i++) {
                        if (type == 1) {
                            values.add(a.readI32());
                        } else {
                            values.add(a.readDouble());
                        }
                    }
                }
                break;
        }
    }
}
