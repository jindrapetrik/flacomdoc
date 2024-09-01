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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;

/**
 *
 * @author JPEXS
 */
public class LosslessImageReader {

    private final DataInputStream is;

    public LosslessImageReader(InputStream is) {
        this.is = new DataInputStream(is);
    }

    public BufferedImage readImage() throws IOException {
        int sign1 = readEx();
        int sign2 = readEx();
        if (sign1 != 0x03 || sign2 != 0x05) {
            throw new IOException("Invalid image");
        }
        int rowSize = readUI16();
        int width = readUI16();
        int height = readUI16();
        long frameLeft = readUI32();
        long frameRight = readUI32();
        long frameTop = readUI32();
        long frameBottom = readUI32();
        int flags = readEx();

        boolean hasAlpha = (flags & 1) == 1;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int variant = readEx();
        if (variant == 1) { //compressed
            while (true) {
                int chunkLen = readUI16();
                if (chunkLen == 0) {
                    break;
                }
                byte[] chunk = new byte[chunkLen];
                is.readFully(chunk);
                baos.write(chunk);
            }
        }

        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(baos.toByteArray()));
        byte[] buf = new byte[4096];
        int cnt;
        while ((cnt = iis.read(buf)) > 0) {
            baos2.write(buf, 0, cnt);
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        byte[] data = baos2.toByteArray();
        int pos = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = data[pos++] & 0xFF;
                int b = data[pos++] & 0xFF;
                int g = data[pos++] & 0xFF;
                int r = data[pos++] & 0xFF;

                int rgba = r + (g << 8) + (b << 16) + (a << 24);
                img.setRGB(x, y, rgba);
            }
        }
        return img;
    }

    private int readUI16() throws IOException {
        return readEx() + (readEx() << 8);
    }

    private long readUI32() throws IOException {
        return (readEx() + (readEx() << 8) + (readEx() << 16) + (readEx() << 24)) & 0xffffffffL;
    }

    //This is actually not UI64, it is SI664
    private long readUI64() throws IOException {
        return readUI32() + (readUI32() << 32);
    }

    private int readEx() throws IOException {
        int ret = is.read();
        if (ret == -1) {
            throw new IOException("Premature end of the file reached");
        }
        return ret;
    }

    public static void main(String[] args) throws IOException {
        File f = new File("in.bin");
        LosslessImageReader r = new LosslessImageReader(new FileInputStream(f));
        BufferedImage i = r.readImage();
        ImageIO.write(i, "PNG", new File("out.png"));
    }
}
