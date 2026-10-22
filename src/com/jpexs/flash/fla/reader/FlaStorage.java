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
 * A CFB storage, including nested storages and streams with their original
 * names.
 */
public final class FlaStorage {

    private final String name;
    private final String path;
    private final long directoryId;
    private final byte[] classId;
    private final long stateBits;
    private final List<FlaStorage> storages = new ArrayList<>();
    private final List<FlaStream> streams = new ArrayList<>();

    FlaStorage(String name, String path, long directoryId, byte[] classId, long stateBits) {
        this.name = name;
        this.path = path;
        this.directoryId = directoryId;
        this.classId = classId.clone();
        this.stateBits = stateBits;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getDirectoryId() {
        return directoryId;
    }

    public byte[] getClassId() {
        return classId.clone();
    }

    public long getStateBits() {
        return stateBits;
    }

    public List<FlaStorage> getStorages() {
        return Collections.unmodifiableList(storages);
    }

    public List<FlaStream> getStreams() {
        return Collections.unmodifiableList(streams);
    }

    void add(FlaStorage storage) {
        storages.add(storage);
    }

    void add(FlaStream stream) {
        streams.add(stream);
    }
}
