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

/**
 * Why structured decoding stopped; the original stream remains available in
 * full.
 */
public final class FlaParseIssue {

    public enum Kind {
        UNSUPPORTED, MALFORMED
    }
    private final Kind kind;
    private final String streamPath;
    private final int offset;
    private final String message;

    FlaParseIssue(Kind kind, String streamPath, int offset, String message) {
        this.kind = kind;
        this.streamPath = streamPath;
        this.offset = offset;
        this.message = message;
    }

    public Kind getKind() {
        return kind;
    }

    public String getStreamPath() {
        return streamPath;
    }

    public int getOffset() {
        return offset;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return kind + " " + streamPath + " @0x" + Integer.toHexString(offset) + ": " + message;
    }
}
