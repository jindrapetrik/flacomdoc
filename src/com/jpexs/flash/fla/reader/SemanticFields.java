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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checked access for optional interpretation views.
 */
final class SemanticFields {

    private SemanticFields() {
    }

    static String nativeStringKey(String name) {
        if (name == null) {
            return null;
        }
        int end = name.indexOf('\0');
        return end < 0 ? name : name.substring(0, end);
    }

    static Integer integer(FlaRecord r, String key) {
        Object v = r.get(key);
        if (!(v instanceof Integer) && !(v instanceof Long)) {
            return null;
        }
        long n = ((Number) v).longValue();
        return n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE ? (int) n : null;
    }

    static Long unsigned(FlaRecord r, String key) {
        Object v = r.get(key);
        if (!(v instanceof Integer) && !(v instanceof Long)) {
            return null;
        }
        long n = ((Number) v).longValue();
        return n >= 0 && n <= 0xffffffffL ? n : null;
    }

    static int[] words(FlaRecord r, String key, int count) {
        Object v = r.get(key);
        if (!(v instanceof List) || ((List<?>) v).size() != count) {
            return null;
        }
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            Object n = ((List<?>) v).get(i);
            if (!(n instanceof Integer)) {
                return null;
            }
            result[i] = (Integer) n;
        }
        return result;
    }

    static List<FlaRecord> records(FlaRecord r, String key) {
        Object v = r.get(key);
        if (!(v instanceof List)) {
            return null;
        }
        List<FlaRecord> result = new ArrayList<>();
        for (Object n : (List<?>) v) {
            if (!(n instanceof FlaRecord)) {
                return null;
            }
            result.add((FlaRecord) n);
        }
        return Collections.unmodifiableList(result);
    }

    static List<String> strings(FlaRecord r, String key) {
        Object v = r.get(key);
        if (!(v instanceof List)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object n : (List<?>) v) {
            if (!(n instanceof String)) {
                return null;
            }
            result.add((String) n);
        }
        return Collections.unmodifiableList(result);
    }
}
