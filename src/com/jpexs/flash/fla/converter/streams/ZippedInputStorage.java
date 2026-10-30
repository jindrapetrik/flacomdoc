/*
 * Copyright (C) 2024 JPEXS.
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
package com.jpexs.flash.fla.converter.streams;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 *
 * @author JPEXS
 */
public class ZippedInputStorage implements InputStorageInterface {

    private final ZipFile zipFile;
    private final Map<String, byte[]> recoveredFiles = new LinkedHashMap<>();
    private final List<String> recoveredNames = new ArrayList<>();

    public ZippedInputStorage(File flaFile) throws IOException {
        ZipFile archive;
        try {
            archive = new ZipFile(flaFile);
        } catch (ZipException invalidDirectory) {
            // Some FLA archives have an invalid central directory but intact
            // local entries. ZipInputStream also validates each entry's CRC.
            archive = null;
            try {
                readLocalEntries(flaFile);
            } catch (IOException recoveryFailure) {
                recoveryFailure.addSuppressed(invalidDirectory);
                throw recoveryFailure;
            }
        }
        zipFile = archive;
    }

    private void readLocalEntries(File flaFile) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(flaFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    int length;
                    while ((length = zip.read(buffer)) != -1) {
                        data.write(buffer, 0, length);
                    }
                    byte[] bytes = data.toByteArray();
                    byte[] previous = recoveredFiles.get(entry.getName());
                    if (previous != null && !Arrays.equals(previous, bytes)) {
                        throw new ZipException("Conflicting ZIP entries: " + entry.getName());
                    }
                    recoveredFiles.put(entry.getName(), bytes);
                    recoveredNames.add(entry.getName());
                }
                zip.closeEntry();
            }
        }
        if (recoveredFiles.isEmpty()) {
            throw new ZipException("No recoverable ZIP entries in " + flaFile);
        }
    }

    public Collection<String> getFileNames() {
        if (zipFile == null) {
            return new ArrayList<>(recoveredNames);
        }
        List<String> names = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    @Override
    public InputStream readFile(String fileName) throws IOException {
        if (zipFile == null) {
            byte[] bytes = recoveredFiles.get(fileName);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }
        ZipEntry entry = zipFile.getEntry(fileName);
        if (entry == null) {
            return null;
        }
        if (entry.isDirectory()) {
            return null;
        }

        return zipFile.getInputStream(entry);
    }

    @Override
    public boolean fileExists(String fileName) {
        if (zipFile == null) {
            return recoveredFiles.containsKey(fileName);
        }
        ZipEntry entry = zipFile.getEntry(fileName);
        if (entry == null) {
            return false;
        }
        if (entry.isDirectory()) {
            return false;
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        if (zipFile != null) {
            zipFile.close();
        }
        recoveredFiles.clear();
        recoveredNames.clear();
    }
}
