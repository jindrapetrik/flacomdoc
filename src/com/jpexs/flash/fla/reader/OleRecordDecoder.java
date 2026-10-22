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

/**
 * OLE client-item archive fields in the normal compound-document context.
 */
final class OleRecordDecoder {

    private OleRecordDecoder() {
    }

    static void item(MfcArchive a, FlaRecord r) throws IOException {
        long signature = a.readUI32();
        r.set("signature", signature);
        if (signature != 0x100) {
            throw a.error("Invalid OLE client-item signature");
        }
        r.set("itemNumber", a.readUI32());
        r.set("value1", a.readUI32());
        r.set("monikerPresent", a.readUI16());
        r.set("drawAspect", a.readUI32());
        // Compound-document storage holds the OLE data outside this archive.
        r.set("revision", a.readUI8());
        r.set("oleItemId", a.readUI16());
        a.readObjectInto(r, "documentPage");
    }
}
