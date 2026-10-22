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

import com.jpexs.flash.fla.converter.streams.CfbOutputStorage;
import com.jpexs.flash.fla.converter.streams.OutputStorageInterface;
import com.jpexs.flash.fla.reader.BinaryData;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Detached binary streams and the conversion report. No XML intermediate.
 */
public final class BinaryFlaConversionResult {

    private final FlaFormatVersion version;
    private final Map<String, BinaryData> streams;
    private final List<BinaryFlaConversionIssue> issues;

    BinaryFlaConversionResult(FlaFormatVersion version, Map<String, BinaryData> streams, List<BinaryFlaConversionIssue> issues) {
        this.version = version;
        this.streams = Collections.unmodifiableMap(new LinkedHashMap<>(streams));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public FlaFormatVersion getTargetVersion() {
        return version;
    }

    public Map<String, BinaryData> getStreams() {
        return streams;
    }

    public List<BinaryFlaConversionIssue> getIssues() {
        return issues;
    }

    public String getReport() {
        StringBuilder text = new StringBuilder("Binary FLA target: ").append(version).append('\n');
        for (BinaryFlaConversionIssue issue : issues) {
            text.append(issue).append('\n');
        }
        return text.toString();
    }

    /**
     * Writes streams without closing caller-owned storage.
     */
    public void writeTo(OutputStorageInterface storage) throws IOException {
        for (Map.Entry<String, BinaryData> entry : streams.entrySet()) {
            storage.storeData(entry.getKey(), entry.getValue().toByteArray());
        }
    }

    /**
     * Builds the complete container before replacing the destination.
     */
    public void writeToFile(File file) throws IOException {
        Path destination = file.toPath().toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".flacomdoc-", ".fla");
        try {
            CfbOutputStorage storage = new CfbOutputStorage(temporary.toFile());
            try {
                writeTo(storage);
            } finally {
                try {
                    storage.close();
                } catch (Exception ex) {
                    throw new IOException("Cannot finish FLA container", ex);
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
