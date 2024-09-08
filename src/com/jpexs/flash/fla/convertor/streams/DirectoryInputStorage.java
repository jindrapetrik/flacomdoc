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
package com.jpexs.flash.fla.convertor.streams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author JPEXS
 */
public class DirectoryInputStorage implements InputStorageInterface {

    private final File inputDirectory;

    public DirectoryInputStorage(File inputDirectory) {
        this.inputDirectory = inputDirectory;
    }

    @Override
    public InputStream readFile(String fileName) throws IOException {
        if (!inputDirectory.toPath().resolve(fileName).toFile().exists()) {
            return null;
        }
        return new FileInputStream(inputDirectory.toPath().resolve(fileName).toFile());
    }

    @Override
    public boolean fileExists(String fileName) {
        return inputDirectory.toPath().resolve(fileName).toFile().exists();
    }

    @Override
    public void close() throws Exception {

    }
}
