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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named persistent values; the ordered entries retain duplicate keys.
 */
public final class FlaPersistentData {

    public static final class Entry {

        private final String name;
        private final Integer type;
        private final Object value;

        private Entry(FlaRecord r) {
            name = r.get("name") instanceof String ? (String) r.get("name") : null;
            type = SemanticFields.integer(r, "type");
            Object v = r.getFields().containsKey("values") ? r.get("values") : r.getFields().containsKey("data") ? r.get("data") : r.get("value");
            value = v instanceof List ? Collections.unmodifiableList(new ArrayList<>((List<?>) v)) : v;
        }

        public String getName() {
            return name;
        }

        public Integer getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }
    }
    private final List<Entry> entries;
    private final Map<String, Entry> byName;

    private FlaPersistentData(List<FlaRecord> records) {
        List<Entry> e = new ArrayList<>();
        Map<String, Entry> names = new LinkedHashMap<>();
        for (FlaRecord r : records) {
            Entry v = new Entry(r);
            e.add(v);
            if (v.name != null) {
                names.put(SemanticFields.nativeStringKey(v.name), v);
            }
        }
        entries = Collections.unmodifiableList(e);
        byName = Collections.unmodifiableMap(names);
    }

    public static FlaPersistentData fromRecord(FlaRecord r) {
        if (r == null || !"persistentData".equals(r.getClassName())) {
            return null;
        }
        List<FlaRecord> records = SemanticFields.records(r, "entries");
        return records == null ? null : new FlaPersistentData(records);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Native map lookup uses the last value of a duplicate key.
     */
    public Map<String, Entry> getByName() {
        return byName;
    }

    public Object get(String name) {
        Entry e = byName.get(SemanticFields.nativeStringKey(name));
        return e == null ? null : e.value;
    }

    public String getString(String name) {
        Object v = get(name);
        return v instanceof String ? (String) v : null;
    }

    public List<FlaXmlElement> getXml(String name) throws IOException {
        String v = getString(name);
        return v == null ? null : FlaXmlElement.parseFragment(v);
    }

    public FlaInverseKinematics getInverseKinematics() {
        return FlaInverseKinematics.fromPersistentData(this);
    }
}
