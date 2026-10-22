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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the document's embedded library names and membership lists.
 */
public final class FlaSharedLibraries {

    public static final class Entry {

        private final String name;
        private final BinaryData data;
        private final List<String> members, legacyNames;
        private final FlaRecord source;

        private Entry(FlaRecord r) {
            source = r;
            name = r.get("name") instanceof String ? (String) r.get("name") : null;
            data = r.get("data") instanceof BinaryData ? (BinaryData) r.get("data") : null;
            members = SemanticFields.strings(r, "names");
            legacyNames = SemanticFields.strings(r, "legacyNames");
        }

        public String getName() {
            return name;
        }

        public BinaryData getData() {
            return data;
        }

        /**
         * Null when this revision has no stored membership list.
         */
        public List<String> getMembers() {
            return members;
        }

        public List<String> getLegacyNames() {
            return legacyNames;
        }

        public FlaRecord getSourceRecord() {
            return source;
        }

        public Boolean containsMember(String member) {
            if (members == null) {
                return null;
            }
            String key = SemanticFields.nativeStringKey(member);
            for (String name : members) {
                if (SemanticFields.nativeStringKey(name).equals(key)) {
                    return true;
                }
            }
            return false;
        }
    }
    private final List<Entry> entries;
    private final Map<String, Entry> byName;
    private final List<Entry> ignored;

    private FlaSharedLibraries(List<FlaRecord> records) {
        List<Entry> all = new ArrayList<>(), rejected = new ArrayList<>();
        Map<String, Entry> names = new LinkedHashMap<>();
        for (FlaRecord r : records) {
            Entry e = new Entry(r);
            all.add(e);
            String key = SemanticFields.nativeStringKey(e.name);
            if (key == null || key.isEmpty() || names.containsKey(key)) {
                rejected.add(e);
            } else {
                names.put(key, e);
            }
        }
        entries = Collections.unmodifiableList(all);
        ignored = Collections.unmodifiableList(rejected);
        byName = Collections.unmodifiableMap(names);
    }

    public static FlaSharedLibraries fromCollection(FlaRecord r) {
        if (r == null) {
            return null;
        }
        List<FlaRecord> entries = SemanticFields.records(r, "entries");
        return entries == null ? null : new FlaSharedLibraries(entries);
    }

    /**
     * Every serialized entry, including empty and duplicate names.
     */
    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Native collection policy: ignore empty names and retain the first
     * duplicate.
     */
    public Map<String, Entry> getByName() {
        return byName;
    }

    public List<Entry> getIgnoredEntries() {
        return ignored;
    }

    public Entry find(String name) {
        return byName.get(SemanticFields.nativeStringKey(name));
    }

    public List<Entry> findMember(String member) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : byName.values()) {
            if (Boolean.TRUE.equals(e.containsMember(member))) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * The clip's directly stored entry, distinct from the Contents collection.
     */
    public static Entry getEmbeddedLibrary(FlaRecord clip) {
        if (!"CSwfPage".equals(clip.getClassName())) {
            return null;
        }
        Object value = clip.get("sharedLibrary");
        return value instanceof FlaRecord ? new Entry((FlaRecord) value) : null;
    }

    /**
     * Name-based binding for a clip whose shared entry is held in Contents.
     */
    public Entry findForClip(FlaRecord clip) {
        if (!"CSwfPage".equals(clip.getClassName())) {
            return null;
        }
        Object name = clip.get("exportName");
        return name instanceof String ? find((String) name) : null;
    }
}
