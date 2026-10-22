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

import com.jpexs.flash.fla.reader.BinaryData;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detached XFL files and an explicit report of unconverted authoring data.
 */
public final class XflExportResult {

    private final Map<String, BinaryData> files;
    private final List<XflExportIssue> issues;
    private final String reportHeader;

    XflExportResult(Map<String, BinaryData> files, List<XflExportIssue> issues) {
        this(files, issues, "Binary FLA to XFL: experimental export\n"
                + "This report covers conversion limitations, not Adobe application validation.\n"
                + "Original document streams remain in the input model; XFL does not contain a full binary FLA backup.\n\n");
    }

    XflExportResult(Map<String, BinaryData> files, List<XflExportIssue> issues, String reportHeader) {
        this.reportHeader = reportHeader;
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    /**
     * Generated or validated project-relative resource paths.
     */
    public Map<String, BinaryData> getFiles() {
        return files;
    }

    public List<XflExportIssue> getIssues() {
        return issues;
    }

    public String getReport() {
        StringBuilder text = new StringBuilder(reportHeader);
        for (XflExportIssue issue : issues) {
            text.append(issue).append('\n');
        }
        return text.toString();
    }

    /**
     * Creates a new directory. An existing target is rejected before any write;
     * I/O failure may leave a partially written new directory.
     */
    public void writeToDirectory(File directory) throws IOException {
        Path root = directory.toPath().toAbsolutePath().normalize();
        Files.createDirectory(root);
        for (Map.Entry<String, BinaryData> entry : files.entrySet()) {
            Path target = root.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue().toByteArray(), StandardOpenOption.CREATE_NEW);
        }
        Files.write(root.resolve("conversion-report.txt"), getReport().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW);
    }

    /**
     * Writes a compressed FLA, completing the ZIP before replacing the
     * destination.
     */
    public void writeToZip(File file) throws IOException {
        Path destination = file.toPath().toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".flacomdoc-", ".fla");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                storedEntry(zip, "mimetype", "application/vnd.adobe.xfl".getBytes(StandardCharsets.US_ASCII));
                BinaryData proxy = files.get("document.xfl");
                if (proxy != null) {
                    storedEntry(zip, "document.xfl", proxy.toByteArray());
                }
                for (Map.Entry<String, BinaryData> entry : files.entrySet()) {
                    if (entry.getKey().equals("mimetype") || entry.getKey().equals("document.xfl")) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().toByteArray());
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("conversion-report.txt"));
                zip.write(getReport().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
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

    private static void storedEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

}
