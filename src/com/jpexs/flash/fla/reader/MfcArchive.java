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

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Little-endian MFC archive, with one shared class/object table per logical
 * stream.
 */
public final class MfcArchive {

    private final BinaryData data;
    private final String streamPath;
    private final FlaReadOptions options;
    private final Map<String, FlaRecordDecoder> decoders;
    private final List<Object> references = new ArrayList<>();
    private final List<FlaRecord> objects = new ArrayList<>();
    private final int contentsRevision;
    private int position;
    private int depth;

    public MfcArchive(BinaryData data, String streamPath, FlaReadOptions options,
            Map<String, FlaRecordDecoder> decoders, int contentsRevision) {
        this.data = data;
        this.streamPath = streamPath;
        this.options = options;
        this.decoders = decoders;
        this.contentsRevision = contentsRevision;
        references.add(null);
    }

    public int getPosition() {
        return position;
    }

    public int getRemaining() {
        return data.size() - position;
    }

    public int getContentsRevision() {
        return contentsRevision;
    }

    public Charset getAnsiCharset() {
        return options.getAnsiCharset();
    }

    public List<FlaRecord> getObjects() {
        return Collections.unmodifiableList(objects);
    }

    public IOException error(String message) {
        return new IOException(streamPath + " @0x" + Integer.toHexString(position) + ": " + message);
    }

    private void require(int count) throws IOException {
        if (count < 0 || count > getRemaining()) {
            throw new EOFException(streamPath + " @0x" + Integer.toHexString(position) + ": truncated archive");
        }
    }

    public int readUI8() throws IOException {
        require(1);
        return data.unsignedByte(position++);
    }

    /**
     * Inspects the next byte without advancing the archive cursor.
     */
    public int peekUI8() throws IOException {
        require(1);
        return data.unsignedByte(position);
    }

    public int peekUI16() throws IOException {
        require(2);
        return data.unsignedByte(position) | (data.unsignedByte(position + 1) << 8);
    }

    public int readUI16() throws IOException {
        require(2);
        return readUI8() | (readUI8() << 8);
    }

    public int readI16() throws IOException {
        return (short) readUI16();
    }

    public long readUI32() throws IOException {
        require(4);
        return (long) readUI16() | ((long) readUI16() << 16);
    }

    public int readI32() throws IOException {
        return (int) readUI32();
    }

    public long readI64() throws IOException {
        require(8);
        return readUI32() | (readUI32() << 32);
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readI32());
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readI64());
    }

    public BinaryData readData(int count) throws IOException {
        require(count);
        BinaryData result = data.slice(position, count);
        position += count;
        return result;
    }

    public BinaryData readData(long count) throws IOException {
        if (count < 0 || count > getRemaining()) {
            throw error("Invalid byte count " + count);
        }
        return readData((int) count);
    }

    /**
     * MFC archive count: a word, or FFFF followed by a dword.
     */
    public int readCount() throws IOException {
        int count = readUI16();
        return checkedCount(count == 0xFFFF ? readUI32() : count, 0);
    }

    public int checkedCount(long count, int minimumEntryBytes) throws IOException {
        if (count < 0 || count > options.getMaxObjects()
                || (minimumEntryBytes > 0 && count > getRemaining() / minimumEntryBytes)) {
            throw error("Invalid or excessive collection count " + count);
        }
        return (int) count;
    }

    /**
     * Reads either an ANSI or a UTF-16LE MFC CString, including extended
     * lengths.
     */
    public String readString() throws IOException {
        StringHeader header = readStringHeader();
        return new String(readData(header.getByteLength()).toByteArray(),
                header.isUnicode() ? StandardCharsets.UTF_16LE : options.getAnsiCharset());
    }

    /**
     * Reads a CString length and encoding marker without consuming its data.
     */
    public StringHeader readStringHeader() throws IOException {
        long length = stringLength();
        boolean unicode = length == -1;
        if (unicode) {
            length = stringLength();
            if (length == -1) {
                throw error("Repeated Unicode string marker");
            }
        }
        if (length == 0xFFFFFFFFL) {
            length = 0;
        }
        return new StringHeader(length, unicode);
    }

    /**
     * The archive length counts bytes for ANSI and code units for Unicode.
     */
    public static final class StringHeader {

        private final long length;
        private final boolean unicode;

        private StringHeader(long length, boolean unicode) {
            this.length = length;
            this.unicode = unicode;
        }

        public long getLength() {
            return length;
        }

        public boolean isUnicode() {
            return unicode;
        }

        public long getByteLength() {
            return length * (unicode ? 2 : 1);
        }
    }

    private long stringLength() throws IOException {
        int small = readUI8();
        if (small < 0xFF) {
            return small;
        }
        int medium = readUI16();
        if (medium == 0xFFFE) {
            return -1;
        }
        return medium == 0xFFFF ? readUI32() : medium;
    }

    /**
     * Compact stream names have no CString Unicode marker, even in Unicode
     * revisions.
     */
    public String readStreamName(boolean unicode, boolean allowTrailingNul) throws IOException {
        int length = readUI8();
        if (length < 1 || length > 31) {
            throw error("Invalid compact stream-name length");
        }
        String value = new String(readData(length * (unicode ? 2 : 1)).toByteArray(),
                unicode ? StandardCharsets.UTF_16LE : options.getAnsiCharset());
        if (allowTrailingNul && value.endsWith("\0")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw error("Empty compact stream name");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
                    && !(c >= '0' && c <= '9') && c != ' ') {
                throw error("Invalid compact stream name");
            }
        }
        return value;
    }

    public String readZeroTerminatedIdentifier(int maxLength) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i <= maxLength; i++) {
            int value = readUI8();
            if (value == 0) {
                return result.toString();
            }
            if (i == maxLength || value > 127) {
                throw error("Invalid object identifier");
            }
            result.append((char) value);
        }
        throw error("Unterminated object identifier");
    }

    public FlaRecord readObject() throws IOException {
        return readObject(value -> {
        });
    }

    /**
     * Stores even a partially decoded child before entering its serializer.
     */
    public FlaRecord readObjectInto(FlaRecord owner, String field) throws IOException {
        return readObject(value -> owner.set(field, value));
    }

    public List<FlaRecord> readObjectList(FlaRecord owner, String field) throws IOException {
        List<FlaRecord> result = new ArrayList<>();
        owner.set(field, Collections.unmodifiableList(result));
        while (true) {
            FlaRecord object = readObject(value -> {
                if (value != null) {
                    result.add(value);
                }
            });
            if (object == null) {
                return Collections.unmodifiableList(result);
            }
            if (result.size() > options.getMaxObjects()) {
                throw error("Object list exceeds read limit");
            }
        }
    }

    /**
     * A fixed number of object pointers; null entries are values, not
     * terminators.
     */
    public List<FlaRecord> readObjectList(FlaRecord owner, String field, int count) throws IOException {
        checkedCount(count, 0);
        List<FlaRecord> result = new ArrayList<>();
        List<FlaRecord> view = Collections.unmodifiableList(result);
        owner.set(field, view);
        for (int i = 0; i < count; i++) {
            readObject(result::add);
        }
        return view;
    }

    private FlaRecord readObject(Consumer<FlaRecord> target) throws IOException {
        int start = position;
        int smallTag = readUI16();
        long tag = smallTag == 0x7FFF ? readUI32()
                : ((smallTag & 0x8000) != 0 ? 0x80000000L | (smallTag & 0x7FFF) : smallTag);
        ClassInfo type;
        if (smallTag == 0xFFFF) {
            int schema = readUI16();
            int length = readUI16();
            if (length == 0 || length > 63) {
                throw error("Invalid MFC class-name length");
            }
            String name = new String(readData(length).toByteArray(), StandardCharsets.US_ASCII);
            for (int i = 0; i < name.length(); i++) {
                if (name.charAt(i) < 33 || name.charAt(i) > 126) {
                    throw error("Invalid MFC class name");
                }
            }
            type = new ClassInfo(name, schema);
            addReference(type);
        } else if ((tag & 0x80000000L) != 0) {
            Object ref = reference(tag & 0x7FFFFFFFL);
            if (!(ref instanceof ClassInfo)) {
                throw error("MFC class tag refers to an object or null");
            }
            type = (ClassInfo) ref;
        } else {
            Object ref = reference(tag);
            if (ref != null && !(ref instanceof FlaRecord)) {
                throw error("MFC object tag refers to a class");
            }
            target.accept((FlaRecord) ref);
            return (FlaRecord) ref;
        }
        if (depth >= options.getMaxDepth()) {
            throw error("MFC object nesting exceeds read limit");
        }
        FlaRecord record = new FlaRecord(type.name, type.schema, references.size(), start, position, data);
        addReference(record);
        objects.add(record);
        target.accept(record);
        FlaRecordDecoder decoder = decoders.get(type.name);
        boolean complete = false;
        depth++;
        try {
            if (decoder == null) {
                throw new UnsupportedFlaRecordException("No payload decoder for " + type.name);
            }
            decoder.read(this, record);
            complete = true;
            return record;
        } finally {
            depth--;
            record.finish(position, complete);
        }
    }

    /**
     * A nested serializer without an MFC object envelope or object ID.
     */
    public FlaRecord readInline(FlaRecord owner, String field, FlaRecordDecoder decoder) throws IOException {
        return readInline(field, value -> owner.set(field, value), decoder);
    }

    /**
     * Attaches an inline record before decoding, including incomplete
     * collection entries.
     */
    public FlaRecord readInline(String name, Consumer<FlaRecord> target, FlaRecordDecoder decoder) throws IOException {
        if (depth >= options.getMaxDepth()) {
            throw error("Inline record nesting exceeds read limit");
        }
        FlaRecord record = new FlaRecord(name, 0, 0, position, position, data);
        target.accept(record);
        boolean complete = false;
        depth++;
        try {
            decoder.read(this, record);
            complete = true;
            return record;
        } finally {
            depth--;
            record.finish(position, complete);
        }
    }

    private Object reference(long id) throws IOException {
        if (id < 0 || id >= references.size()) {
            throw error("Invalid MFC reference " + id);
        }
        return references.get((int) id);
    }

    private void addReference(Object value) throws IOException {
        if (references.size() > options.getMaxObjects()) {
            throw error("MFC reference table exceeds read limit");
        }
        references.add(value);
    }

    private static final class ClassInfo {

        final String name;
        final int schema;

        ClassInfo(String name, int schema) {
            this.name = name;
            this.schema = schema;
        }
    }
}
