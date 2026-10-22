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
 * Placed compiled clips and their separate library media descriptors.
 */
final class SwfRecordDecoder {

    private SwfRecordDecoder() {
    }

    static void instance(MfcArchive a, FlaRecord r) throws IOException {
        DefaultFlaDecoders.picBase(a, r);
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 7);
        r.set("matrixWords", DefaultFlaDecoders.integers(a, 6));
        long present = a.readUI32();
        r.set("mediaPresent", present);
        if (present != 0) {
            r.set("mediaId", a.readUI16());
        }
        if (revision >= 2) {
            a.readInline(r, "actions", ActionRecordDecoder::source);
        }
        if (revision >= 3) {
            r.set("instanceName", a.readString());
        }
        if (revision >= 4) {
            a.readInline(r, "properties", PropertyRecordDecoder::list);
        }
        if (revision >= 5) {
            a.readInline(r, "accessibility", (in, access) -> InstanceRecordDecoder.accessibility(in, access, true));
        }
        if (revision >= 6) {
            a.readInline(r, "componentXml", InstanceRecordDecoder::componentXml);
        }
        if (revision >= 7) {
            r.set("reservedByte", a.readUI8());
        }
    }

    static void media(MfcArchive a, FlaRecord r) throws IOException {
        DefaultFlaDecoders.mediaBase(a, r);
        a.readInline(r, "component", DefaultFlaDecoders::component);
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 5);
        if (revision >= 1) {
            r.set("sourceName", a.readString());
        }
        if (revision >= 2) {
            r.set("boundsWords", DefaultFlaDecoders.integers(a, 4));
        }
        if (revision >= 3) {
            a.readInline(r, "metadata", SwfRecordDecoder::metadata);
        }
        if (revision >= 4) {
            r.set("legacyValue", a.readUI32());
            r.set("playerVersion", a.readUI32());
            r.set("actionScriptVersion", a.readUI32());
        }
        if (revision >= 5) {
            long present = a.readUI32();
            r.set("sharedRecordPresent", present);
            if (present != 0) {
                FlaRecord shared = a.readInline(r, "sharedLibrary", DocumentTailRecordDecoder::sharedLibrary);
                // This branch obtains the export name from the embedded record.
                r.set("exportName", shared.get("name"));
            } else {
                r.set("exportName", a.readString());
            }
        }
    }

    /**
     * Inline recursive SWC dependency metadata, not MFC class/object
     * definitions.
     */
    static void metadata(MfcArchive a, FlaRecord r) throws IOException {
        int revision = DefaultFlaDecoders.revision(a, r, "revision", 4);
        r.set("name", a.readString());
        r.set("implementation", a.readString());
        r.set("version", a.readUI32());
        if (revision == 1) {
            r.set("isTopLevel", a.readUI8());
        } else if (revision >= 2) {
            r.set("isTopLevel", a.readUI32());
        }
        if (revision == 2) {
            entries(a, r, "legacyChildren", false);
        } else if (revision >= 3) {
            entries(a, r, "assetMap", true);
            entries(a, r, "includesList", false);
            entries(a, r, "exportAfterList", false);
        }
        if (revision >= 4) {
            r.set("intrinsicClass", a.readString());
            r.set("intrinsicScript", a.readString());
        }
    }

    private static void entries(MfcArchive a, FlaRecord r, String field, boolean named) throws IOException {
        long count = a.readUI32();
        r.set(field + "Count", count);
        int size = a.checkedCount(count, named ? 8 : 7);
        List<FlaRecord> records = new ArrayList<>();
        r.set(field, Collections.unmodifiableList(records));
        for (int i = 0; i < size; i++) {
            a.readInline("swfMetadataEntry", records::add, (in, entry) -> {
                if (named) {
                    entry.set("key", in.readString());
                }
                in.readInline(entry, "metadata", SwfRecordDecoder::metadata);
            });
        }
    }
}
