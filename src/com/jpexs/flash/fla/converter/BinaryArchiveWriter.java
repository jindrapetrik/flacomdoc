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

import com.jpexs.flash.fla.reader.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/**
 * Little-endian archive with a fresh, stream-local MFC reference table.
 */
final class BinaryArchiveWriter {

    final BinaryFlaConverter.Session session;
    final String path;
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final Map<String, Integer> classes = new LinkedHashMap<>();
    private final IdentityHashMap<FlaRecord, Integer> objects = new IdentityHashMap<>();
    private int nextReference = 1;
    private int depth;

    BinaryArchiveWriter(BinaryFlaConverter.Session session, String path) {
        this.session = session;
        this.path = path;
    }

    FlaFormatVersion target() {
        return session.target;
    }

    void u8(long n) {
        bytes.write((int) n);
    }

    void u16(long n) {
        u8(n);
        u8(n >>> 8);
    }

    void u32(long n) {
        u16(n);
        u16(n >>> 16);
    }

    void f32(double n) {
        u32(Float.floatToRawIntBits((float) n));
    }

    void f64(double n) {
        long bits = Double.doubleToRawLongBits(n);
        u32(bits);
        u32(bits >>> 32);
    }

    void raw(BinaryData data) {
        if (data != null) {
            raw(data.toByteArray());
        }
    }

    void raw(byte[] data) {
        bytes.write(data, 0, data.length);
    }

    void zeros(int count) {
        for (int i = 0; i < count; i++) {
            u8(0);
        }
    }

    void count(int n) {
        if (n < 0xFFFF) {
            u16(n);
        } else {
            u16(0xFFFF);
            u32(n);
        }
    }

    Object value(FlaRecord r, String field) {
        if (r == null) {
            return null;
        }
        session.used.computeIfAbsent(r, k -> new LinkedHashSet<>()).add(field);
        session.locations.putIfAbsent(r, path);
        Map<String, Object> override = session.overrides.get(r);
        return override != null && override.containsKey(field) ? override.get(field) : r.get(field);
    }

    long n(FlaRecord r, String field, long fallback) {
        Object v = value(r, field);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    double d(FlaRecord r, String field, double fallback) {
        Object v = value(r, field);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    String s(FlaRecord r, String field) {
        Object v = value(r, field);
        return v instanceof String ? (String) v : "";
    }

    FlaRecord record(FlaRecord r, String field) {
        Object v = value(r, field);
        return v instanceof FlaRecord ? (FlaRecord) v : null;
    }

    @SuppressWarnings("unchecked")
    List<?> list(FlaRecord r, String field) {
        Object v = value(r, field);
        return v instanceof List ? (List<?>) v : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    List<FlaRecord> records(FlaRecord r, String field) {
        return (List<FlaRecord>) (List<?>) list(r, field);
    }

    BinaryData data(FlaRecord r, String field) {
        Object v = value(r, field);
        return v instanceof BinaryData ? (BinaryData) v : null;
    }

    void u8(FlaRecord r, String field) {
        u8(n(r, field, 0));
    }

    void u16(FlaRecord r, String field) {
        u16(n(r, field, 0));
    }

    void u32(FlaRecord r, String field) {
        u32(n(r, field, 0));
    }

    void string(FlaRecord r, String field) {
        string(s(r, field));
    }

    void strings(FlaRecord r, String... fields) {
        for (String f : fields) {
            string(r, f);
        }
    }

    void dwords(FlaRecord r, String... fields) {
        for (String f : fields) {
            u32(r, f);
        }
    }

    void words(FlaRecord r, String field, int count) {
        words(r, field, count, false);
    }

    void words(FlaRecord r, String field, int count, boolean matrix) {
        List<?> values = list(r, field);
        for (int i = 0; i < count; i++) {
            Object v = i < values.size() ? values.get(i) : null;
            u32(v instanceof Number ? ((Number) v).longValue() : matrix && (i == 0 || i == 3) ? 65536 : 0);
        }
    }

    void fixedData(FlaRecord r, String field, int count) {
        BinaryData data = data(r, field);
        if (data == null) {
            zeros(count);
        } else {
            byte[] b = data.toByteArray();
            raw(Arrays.copyOf(b, count));
        }
    }

    int revision(FlaRecord r, String field, int maximum) {
        int source = (int) n(r, field, maximum);
        int result = Math.min(Math.max(0, source), maximum);
        if (source > maximum) {
            issue("RECORD_REVISION", r, field, "Revision " + source + " reduced to " + maximum + ".");
        }
        u8(result);
        return result;
    }

    void issue(String code, FlaRecord record, String field, String message) {
        session.issue(code, path, record, field, message);
    }

    byte[] textBytes(String text, boolean unicode) {
        if (unicode) {
            byte[] out = new byte[text.length() * 2];
            for (int i = 0; i < text.length(); i++) {
                out[2 * i] = (byte) text.charAt(i);
                out[2 * i + 1] = (byte) (text.charAt(i) >>> 8);
            }
            return out;
        }
        if (!session.charset.newEncoder().canEncode(text)) {
            issue("CHARSET", null, "", "Characters replaced while encoding " + session.charset.name() + ".");
        }
        return text.getBytes(session.charset);
    }

    void length(long n) {
        if (n < 0xFF) {
            u8(n);
        } else if (n < 0xFFFE) {
            u8(0xFF);
            u16(n);
        } else {
            u8(0xFF);
            u16(0xFFFF);
            u32(n);
        }
    }

    void string(String text) {
        string(text, target().unicodeStrings);
    }

    void string(String text, boolean unicode) {
        byte[] data = textBytes(text, unicode);
        if (unicode) {
            u8(0xFF);
            u16(0xFFFE);
        }
        length(unicode ? text.length() : data.length);
        raw(data);
    }

    void streamName(String text, boolean unicode) throws IOException {
        byte[] data = textBytes(text, unicode);
        int count = unicode ? text.length() : data.length;
        if (count < 1 || count > 31 || !text.matches("[a-zA-Z0-9 ]+")) {
            throw new IOException("Invalid binary stream name: " + text);
        }
        u8(count);
        raw(data);
    }

    void tag(int id, boolean type) {
        if (id < 0x7FFF) {
            u16(id | (type ? 0x8000 : 0));
        } else {
            u16(0x7FFF);
            u32((long) id | (type ? 0x80000000L : 0));
        }
    }

    void object(FlaRecord r) throws IOException {
        if (r == null || !session.supports(r)) {
            u16(0);
            return;
        }
        Integer ref = objects.get(r);
        if (ref != null) {
            tag(ref, false);
            return;
        }
        if (++depth > 256) {
            throw new IOException("Binary object nesting exceeds 256");
        }
        try {
            String name = session.outputClass(r);
            Integer type = classes.get(name);
            if (type == null) {
                u16(0xFFFF);
                u16(r.getSchema());
                byte[] className = name.getBytes(StandardCharsets.US_ASCII);
                u16(className.length);
                raw(className);
                classes.put(name, nextReference++);
            } else {
                tag(type, true);
            }
            objects.put(r, nextReference++);
            new BinaryObjectWriter(this).write(r, name);
        } finally {
            depth--;
        }
    }

    List<FlaRecord> supported(List<FlaRecord> source) {
        List<FlaRecord> result = new ArrayList<>();
        for (FlaRecord r : source) {
            if (r != null && session.supports(r)) {
                result.add(r);
            }
        }
        return result;
    }

    void objects(FlaRecord owner, String field) throws IOException {
        for (FlaRecord r : supported(records(owner, field))) {
            object(r);
        }
        object(null);
    }

    void countedObjects(FlaRecord owner, String field) throws IOException {
        List<FlaRecord> items = supported(records(owner, field));
        count(items.size());
        for (FlaRecord r : items) {
            object(r);
        }
    }

    BinaryData finish() {
        return new BinaryData(bytes.toByteArray());
    }
}
