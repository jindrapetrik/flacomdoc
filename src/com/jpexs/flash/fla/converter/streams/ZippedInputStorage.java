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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author JPEXS
 */
public class ZippedInputStorage implements InputStorageInterface {

    private final ZipFile zipFile;

    public ZippedInputStorage(File flaFile) throws IOException {
        zipFile = new ZipFile(flaFile);
    }

    @Override
    public InputStream readFile(String fileName) throws IOException {
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
    public void close() throws Exception {
        zipFile.close();
    }
}
