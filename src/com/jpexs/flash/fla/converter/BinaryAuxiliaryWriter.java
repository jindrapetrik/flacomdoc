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
import java.io.IOException;
import java.util.*;

/**
 * Legacy screen, font, sound, video and clipboard objects.
 */
final class BinaryAuxiliaryWriter {

    final BinaryArchiveWriter w;

    BinaryAuxiliaryWriter(BinaryArchiveWriter w) {
        this.w = w;
    }

    void write(FlaRecord r, String type) throws IOException {
        BinaryObjectWriter objects = new BinaryObjectWriter(w);
        if (type.equals("CCommandObj")) {
            objects.base(r, w.target().picGroupBaseRevision);
            int v = w.revision(r, "revision", 1);
            w.words(r, "matrixWords", 6, true);
            w.words(r, "bounds", 4);
            w.string(r, "command");
            if (v >= 1) {
                w.string(r, "displayLabel");
            }
            return;
        }
        if (type.equals("CPicScreen")) {
            objects.symbol(r, "CPicSprite");
            int v = w.revision(r, "screenRevision", 3);
            w.dwords(r, "hidden", "reservedScreenValue");
            if (v >= 3) {
                w.u32(r, "autoRegUpdate");
            }
            w.string(r, "screenName");
            return;
        }
        if (type.equals("CPicFont")) {
            objects.base(r, w.target().picGroupBaseRevision);
            int mode = (int) w.n(r, "fontReferenceMode", 0);
            w.u8(mode);
            if (mode != 0) {
                w.u16(r, "fontId");
            } else {
                new BinaryLibraryWriter(w).font(w.record(r, "font"));
            }
            return;
        }
        if (type.equals("CPicSoundCreator")) {
            w.u8(r, "revision");
            w.u16(r, "mediaId");
            return;
        }
        if (type.equals("CMediaVideo")) {
            new BinaryLibraryWriter(w).media(r, "CMediaVideoStream");
            w.u8(r, "revision");
            return;
        }
        objects.base(r, w.target().picGroupBaseRevision);
        if (type.equals("CPicVideo") || type.equals("CPicTempClipboardObj")) {
            w.u8(r, "revision");
            w.words(r, "matrixWords", 6, true);
            if (type.equals("CPicVideo")) {
                w.u16(r, "mediaId");
            }
            return;
        }
        throw new IOException("Unsupported auxiliary object: " + type);
    }
}
