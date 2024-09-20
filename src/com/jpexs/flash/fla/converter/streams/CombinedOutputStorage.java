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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows writing to more than one places simultaneously.
 *
 * @author JPEXS
 */
public class CombinedOutputStorage implements OutputStorageInterface {

    private final OutputStorageInterface[] storages;

    public CombinedOutputStorage(OutputStorageInterface... storages) {
        this.storages = storages;
    }

    @Override
    public OutputStream getOutputStream(String fileName) throws IOException {
        List<OutputStream> oss = new ArrayList<>();
        for (OutputStorageInterface storage : storages) {
            oss.add(storage.getOutputStream(fileName));
        }

        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                for (OutputStream os : oss) {
                    os.write(b);
                }
            }

            @Override
            public void write(byte[] b) throws IOException {
                for (OutputStream os : oss) {
                    os.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                for (OutputStream os : oss) {
                    os.write(b, off, len);
                }
            }

            @Override
            public void close() throws IOException {
                for (OutputStream os : oss) {
                    os.close();
                }
            }
        };
    }

    @Override
    public void storeData(String fileName, byte[] data) throws IOException {
        for (OutputStorageInterface storage : storages) {
            storage.storeData(fileName, data);
        }
    }

    @Override
    public void storeFile(String fileName, File file) throws IOException {
        for (OutputStorageInterface storage : storages) {
            storage.storeFile(fileName, file);
        }
    }

    @Override
    public void close() throws Exception {
        for (OutputStorageInterface storage : storages) {
            storage.close();
        }
    }

}
