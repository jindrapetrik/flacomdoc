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
package com.jpexs.flash.fla.convertor;

import java.io.RandomAccessFile;

/**
 * Testing class for reading FLA properties.
 *
 * @author JPEXS
 */
public class Reader {

    public static void main(String[] args) throws Exception {
        RandomAccessFile fis = new RandomAccessFile("<TODO:Enter contents file>", "r");
        fis.seek(1234); //TODO: enter valid position
        int len = fis.read() + (fis.read() << 8);
        System.out.println("len = " + len);
        for (int i = 0; i < len; i++) {
            fis.read();
            fis.read();
            fis.read(); //ff fe ff
            int klen = fis.read();
            byte[] b = new byte[klen * 2];
            fis.read(b);
            String key = new String(b, "UTF-16LE");
            fis.read();
            fis.read();
            fis.read(); //ff fe ff
            int vlen = fis.read();
            b = new byte[vlen * 2];
            fis.read(b);
            String val = new String(b, "UTF-16LE");
            System.out.println("propertiesMap.put(\"" + key + "\", \"" + val.replace("\"", "\\\"") + "\");");
        }
        fis.close();
    }
}
