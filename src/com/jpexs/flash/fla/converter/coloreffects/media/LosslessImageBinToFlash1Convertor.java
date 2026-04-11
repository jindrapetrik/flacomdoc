/*
 *  Copyright (C) 2010-2026 JPEXS, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.flash.fla.converter.coloreffects.media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads bin/*.dat file of lossless images and produces BufferedImage
 *
 * @author JPEXS
 */
public class LosslessImageBinToFlash1Convertor {

    private final DataInputStream is;

    public LosslessImageBinToFlash1Convertor(InputStream is) {
        this.is = new DataInputStream(is);
    }

    public void convertTo(OutputStream os) throws IOException {
        int major = readEx();       
        int minor = readEx();
        if (major > 0x03 || minor != 0x05) {
            throw new IOException("Invalid image");
        }
        os.write(0x00);
        os.write(minor);
        int rowSize = readUI16();
        writeUI16(os, rowSize);
        int width = readUI16();
        writeUI16(os, width);
        int height = readUI16();
        writeUI16(os, height);
        long frameLeft = readUI32();
        writeUI32(os, frameLeft);
        long frameRight = readUI32();
        writeUI32(os, frameRight);
        long frameTop = readUI32();
        writeUI32(os, frameTop);
        long frameBottom = readUI32();
        writeUI32(os, frameBottom);
        boolean hasAlpha = false;
        if (major == 0x03) {
            int flags = readEx();
            hasAlpha = (flags & 1) == 1;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream uis = is;
        
        if (minor > 1) {
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

                uis = new InflaterInputStream(new ByteArrayInputStream(baos.toByteArray()));            
            }
        }
        
        baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int cnt;
        while ((cnt = uis.read(buf)) > 0) {
            baos.write(buf, 0, cnt);
        }                
        byte[] arr = baos.toByteArray();
        for (int i = 0; i < arr.length; i += 4) {
            os.write(0); //strip alpha data
            os.write(arr[i + 1]);
            os.write(arr[i + 2]);
            os.write(arr[i + 3]);            
        }
    }

    private int readUI16() throws IOException {
        return readEx() + (readEx() << 8);
    }

    private long readUI32() throws IOException {
        return (readEx() + (readEx() << 8) + (readEx() << 16) + (readEx() << 24)) & 0xffffffffL;
    }
    
    private void writeUI16(OutputStream os, int val) throws IOException {
        os.write(val & 0xFF);
        os.write((val >> 8) & 0xFF);
    }
    
    private void writeUI32(OutputStream os, long val) throws IOException {
        os.write((int) (val & 0xFF));
        os.write((int) ((val >> 8) & 0xFF));
        os.write((int) ((val >> 16) & 0xFF));
        os.write((int) ((val >> 24) & 0xFF));
    }
    

    private int readEx() throws IOException {
        int ret = is.read();
        if (ret == -1) {
            throw new IOException("Premature end of the file reached");
        }
        return ret;
    }

    public static void main(String[] args) throws IOException {
        File f = new File("c:\\Dropbox\\Programovani\\JavaSE\\FlaComDoc\\testdata\\flang\\f2\\0010_bitmaps\\Media 1");
        LosslessImageBinToFlash1Convertor r = new LosslessImageBinToFlash1Convertor(new FileInputStream(f));
        r.convertTo(new FileOutputStream("c:\\Dropbox\\Programovani\\JavaSE\\FlaComDoc\\testdata\\m1.bin"));
    }
}
