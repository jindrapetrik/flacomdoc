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

import com.jpexs.cfb.CompoundFileBinary;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author JPEXS
 */
public class CfbOutputStorage implements OutputStorageInterface {

    private CompoundFileBinary cfb;

    public CfbOutputStorage(File cfbFile) throws IOException {
        cfb = new CompoundFileBinary(cfbFile, true);
    }

    @Override
    public OutputStream getOutputStream(String fileName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return new OutputStream() {
            private boolean closed = false;

            @Override
            public void write(int b) throws IOException {
                baos.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                baos.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                baos.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                if (!closed) {
                    cfb.addFile(fileName, baos.toByteArray());
                }
                closed = true;
            }
        };
    }

    @Override
    public void storeData(String fileName, byte[] data) throws IOException {
        cfb.addFile(fileName, data);
    }

    @Override
    public void storeFile(String fileName, File file) throws IOException {
        cfb.addFile(fileName, file);
    }

    @Override
    public void close() throws Exception {
        cfb.close();
    }

}
