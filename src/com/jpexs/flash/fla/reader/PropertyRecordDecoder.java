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
 * Component-property lists, property metadata and string-backed typed values.
 */
final class PropertyRecordDecoder {

    private PropertyRecordDecoder() {
    }

    static void list(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 2);
        int count = a.checkedCount(a.readUI32(), 0);
        r.set("count", count);
        List<FlaRecord> entries = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(entries));
        for (int i = 0; i < count; i++) {
            a.readInline("property", entries::add, revision == 0
                    ? PropertyRecordDecoder::legacyProperty : PropertyRecordDecoder::property);
        }
        if (revision >= 2) {
            r.set("parametersAreNew", a.readUI32());
        }
    }

    private static void legacyProperty(MfcArchive a, FlaRecord r) throws IOException {
        r.set("type", a.readUI32());
        r.set("name", a.readString());
        r.set("value", a.readString());
    }

    static void property(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 3);
        r.set("name", a.readString());
        a.readInline(r, "value", PropertyRecordDecoder::value);
        if (revision >= 1) {
            r.set("variableName", a.readString());
            a.readInline(r, "identifier", (in, id) -> {
                id.set("revision", in.readUI8());
                id.set("data", in.readData(16));
            });
        }
        if (revision >= 3) {
            r.set("category", a.readString());
            r.set("verbosity", a.readUI32());
        }
    }

    static void value(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 8);
        int type = a.readUI16();
        r.set("type", type);
        if (revision >= 8 && type == 17) {
            r.set("selectionWord", a.readUI16());
        }
        int count = a.checkedCount(a.readUI32(), 0);
        r.set("count", count);
        r.set("selectedIndex", a.readUI32());
        long named = 0;
        if (revision >= 1) {
            named = a.readUI32();
            r.set("namedEntries", named);
        }
        List<FlaRecord> entries = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(entries));
        final boolean hasNames = named != 0;
        for (int i = 0; i < count; i++) {
            a.readInline("propertyValueEntry", entries::add, (in, entry) -> {
                if (hasNames) {
                    entry.set("name", in.readString());
                }
                entry.set("value", in.readString());
                if (revision >= 6) {
                    entry.set("type", in.readUI16());
                }
            });
        }
        if (type == 13 && revision >= 4) {
            a.readInline(r, "table", (in, table) -> table(in, table, revision));
        }
        if (revision >= 8) {
            long length = a.readUI32();
            r.set("dataLength", length);
            r.set("data", a.readData(length));
        }
    }

    private static void table(MfcArchive a, FlaRecord r, int revision) throws IOException {
        r.set("collectionClass", a.readString());
        r.set("collectionItemClass", a.readString());
        if (revision >= 5) {
            // Metadata reconstruction promotes this property to the first column.
            r.set("firstColumnName", a.readString());
        }
        int columns = a.checkedCount(a.readUI32(), 0);
        r.set("columnCount", columns);
        List<FlaRecord> definitions = new ArrayList<>();
        r.set("columns", Collections.unmodifiableList(definitions));
        for (int i = 0; i < columns; i++) {
            a.readInline("propertyTableColumn", definitions::add, (in, column) -> {
                column.set("name", in.readString());
                column.set("type", in.readUI16());
                column.set("defaultValue", in.readString());
                column.set("enumeration", in.readString());
            });
        }
        int rows = a.checkedCount(a.readUI32(), 0);
        r.set("rowCount", rows);
        a.checkedCount((long) rows * columns, 1);
        List<FlaRecord> values = new ArrayList<>();
        r.set("rows", Collections.unmodifiableList(values));
        for (int i = 0; i < rows; i++) {
            a.readInline("propertyTableRow", values::add, (in, row) -> {
                List<String> cells = new ArrayList<>();
                row.set("cells", Collections.unmodifiableList(cells));
                for (int j = 0; j < columns; j++) {
                    cells.add(in.readString());
                }
            });
        }
    }
}
