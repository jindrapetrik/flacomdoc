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
package com.jpexs.flash.fla.exporter;

/**
 * A conversion limitation, located in the original logical binary stream.
 */
public final class XflExportIssue {

    private final String code, streamPath, message;
    private final int offset;

    XflExportIssue(String code, String streamPath, int offset, String message) {
        this.code = code;
        this.streamPath = streamPath;
        this.offset = offset;
        this.message = message;
    }

    public String getCode() {
        return code;
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
        return code + " [" + streamPath + ":" + offset + "]: " + message;
    }
}
