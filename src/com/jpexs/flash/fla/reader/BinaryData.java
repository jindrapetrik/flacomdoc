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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Immutable bytes, with inexpensive slices sharing the same backing storage.
 */
public final class BinaryData {

    private final byte[] bytes;
    private final int offset;
    private final int length;

    public BinaryData(byte[] bytes) {
        this(bytes.clone(), 0, bytes.length);
    }

    private BinaryData(byte[] bytes, int offset, int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
    }

    static BinaryData takeOwnership(byte[] bytes) {
        return new BinaryData(bytes, 0, bytes.length);
    }

    public int size() {
        return length;
    }

    public BinaryData slice(int start, int size) {
        if (start < 0 || size < 0 || start > length || size > length - start) {
            throw new IndexOutOfBoundsException("Invalid binary data range");
        }
        return new BinaryData(bytes, offset + start, size);
    }

    public byte[] toByteArray() {
        return Arrays.copyOfRange(bytes, offset, offset + length);
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(bytes, offset, length);
    }

    public ByteBuffer asReadOnlyBuffer() {
        return ByteBuffer.wrap(bytes, offset, length).slice().asReadOnlyBuffer();
    }

    int unsignedByte(int position) {
        if (position < 0 || position >= length) {
            throw new IndexOutOfBoundsException();
        }
        return bytes[offset + position] & 255;
    }
}
