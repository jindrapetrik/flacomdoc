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

        boolean uni = false;
        RandomAccessFile fis = new RandomAccessFile("testdata\\fla\\f5\\0001_empty_doc\\Contents", "r");
        fis.seek(216); //TODO: enter valid position
        int len = fis.read() + (fis.read() << 8);
        System.out.println("len = " + len);
        for (int i = 0; i < len; i++) {
            if (uni) {
                fis.read();
                fis.read();
                fis.read(); //ff fe ff
            }
            int klen = fis.read();
            byte[] b = new byte[uni ? klen * 2 : klen];
            fis.read(b);
            String key = uni ? new String(b, "UTF-16LE") : new String(b);
            if (uni) {
                fis.read();
                fis.read();
                fis.read(); //ff fe ff
            }
            int vlen = fis.read();
            b = new byte[uni ? vlen * 2 : vlen];
            fis.read(b);
            String val = uni ? new String(b, "UTF-16LE") : new String(b);

            String vale = "\"" + val.replace("\"", "\\\"") + "\"";
            if (key.endsWith("::Width") && val.equals("550")) {
                vale = "\"\" + width";
            }
            if (key.endsWith("::Height") && val.equals("400")) {
                vale = "\"\" + height";
            }
            if (val.startsWith("Untitled-1")) {
                val = val.substring("Untitled-1".length());
                vale = "basePublishName + \"" + val.replace("\"", "\\\"") + "\"";
            }
            System.out.println("propertiesMap.put(\"" + key + "\", " + vale + ");");
        }
        fis.close();
    }
}
