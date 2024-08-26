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
package com.jpexs.helpers;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.swing.JFrame;

/**
 *
 * @author JPEXS
 */
public class HexPrint extends JFrame {

    public static void printHex(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        DataInputStream dais = new DataInputStream(fis);
        byte buf[] = new byte[16];
        long addr = 0;
        int len = 16;
        while (fis.available() >= 16) {
            dais.readFully(buf);
            String addrStr = String.format("%1$04X", addr);
            System.out.print(addrStr + ": ");
            for (int i = 0; i < len; i++) {
                if (i == 8) {
                    System.out.print("| ");
                }
                System.out.print(String.format("%1$02X", buf[i] & 0xff) + " ");
            }
            System.out.print("| ");
            for (int i = 0; i < len; i++) {
                if ((buf[i] & 0xff) < 0x20) {
                    System.out.print(" ");
                } else {
                    System.out.print("" + (char) (buf[i] & 0xff));
                }
            }
            System.out.println();
            addr += 16;
        }
        if (fis.available() > 0) {
            len = fis.available();
            dais.readFully(buf, 0, len);
            String addrStr = String.format("%1$04X", addr);
            System.out.print(addrStr + ": ");
            for (int i = 0; i < len; i++) {
                if (i == 8) {
                    System.out.print("| ");
                }
                System.out.print(String.format("%1$02X", buf[i] & 0xff) + " ");
            }
            for (int i = len; i < 16; i++) {
                if (i == 8) {
                    System.out.print("| ");
                }
                System.out.print("   ");
            }
            System.out.print("| ");
            for (int i = 0; i < len; i++) {
                if ((buf[i] & 0xff) < 0x20) {
                    System.out.print(" ");
                } else {
                    System.out.print("" + (char) (buf[i] & 0xff));
                }
            }
            System.out.println();
        }
        fis.close();
    }

    /**
     * Converts and prints String in format 00 00 00 01 00 00 | 00 03 00 00 (01)
     * 01 to list of bytes.
     *
     * @param s
     */
    private static void convert(String s) {
        s = s.replace(" | ", " ").replace("(", "").replace(")", "").replace(" # ", " ").replace("#", "");
        String parts[] = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            int val = Integer.parseInt(p, 16);
            if (val > 127) {
                sb.append("(byte) ");
            }
            sb.append("0x").append(p).append(", ");
        }
        System.out.println(sb);
    }

    /**
     * Converts and prints string in format 0100000000001000CC0C00000 to list of
     * bytes.
     *
     * @param s
     */
    private static void convert2(String s) {
        s = s.replaceAll("[\r\n ]+", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 2) {
            String v = s.substring(i, i + 2);
            int val = Integer.parseInt(v, 16);
            if (val > 127) {
                sb.append("(byte) ");
            }
            sb.append("0x").append(v).append(",");
            if (i > 0 && (i % (16 * 2) == 0)) {
                sb.append("\r\n");
            }
        }
        System.out.println(sb);
    }

    public static void main(String[] args) {
        String t = "12 34 56";
        convert2(t);
    }
}
