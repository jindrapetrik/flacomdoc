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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Just a test class for reading edges from a file. For debugging purposes. Not
 * really useful.
 *
 * @author JPEXS
 */
public class EdgeReader {

    private static float readShort(InputStream is) throws IOException {
        int v1 = is.read();
        int v2 = is.read();
        int v = (v2 << 8) + v1;
        v = (v << 16) >> 16;
        return v / 2.0f;
    }

    private static float readByte(InputStream is) throws IOException {
        int fract = is.read();
        int integer = is.read();
        integer = (integer << 24) >> 24;

        return integer + fract / 256.0f;
    }

    private static float readFloat(InputStream is) throws IOException {
        int fract = is.read();
        int v1 = is.read();
        int v2 = is.read();
        int v3 = is.read();
        int v = (v3 << 16) + (v2 << 8) + v1;
        v = (v << 8) >> 8;
        return v + fract / 256.0f;
    }

    private static String doubleToStr(double v) {
        String s = "" + v;
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    public static void main(String[] args) throws IOException {
        File file = new File("c:\\Dropbox\\Programovani\\JavaSE\\FlaComDoc\\testdata\\fla\\cs3\\0003_fills\\P 2 1726169870");
        FileInputStream fis = new FileInputStream(file);
        fis.skip(515);
        double x = 0;
        double y = 0;
        boolean first = true;
        StringBuilder sb = new StringBuilder();
        List<String> edges = new ArrayList<>();
        while (fis.available() > 0) {
            int flags = fis.read();

            if ((flags & FlaWriter.FLAG_EDGE_NO_SELECTION) == FlaWriter.FLAG_EDGE_NO_SELECTION) {
                int stroke = fis.read();
                int fs0 = fis.read();
                int fs1 = fis.read();
                if (!first) {
                    for (String e : edges) {
                        System.out.print(e);
                    }
                    edges.clear();

                    System.out.println("\"/>");
                }
                //System.out.println(sb.toString());
                sb = new StringBuilder();
                first = false;
                System.out.print("<Edge ");
                if (fs0 != 0) {
                    System.out.print("fillStyle0=\"" + fs0 + "\" ");
                }
                if (fs1 != 0) {
                    System.out.print("fillStyle1=\"" + fs1 + "\" ");
                }

                if (stroke != 0) {
                    System.out.print("strokeStyle=\"" + stroke + "\" ");
                }

                System.out.print("edges=\"");
            }
            sb.append("0x").append(Integer.toHexString(flags)).append(" ");
            if ((flags & FlaWriter.FLAG_EDGE_FROM_SHORT) == FlaWriter.FLAG_EDGE_FROM_SHORT) {
                x += readShort(fis);
                y += readShort(fis);
                sb.append(" from short ").append(doubleToStr(x)).append(" ").append(doubleToStr(y));

                //System.out.print("!" + doubleToStr(x) + " " + doubleToStr(y));
            } else if ((flags & FlaWriter.FLAG_EDGE_FROM_BYTE) == FlaWriter.FLAG_EDGE_FROM_BYTE) {
                x += readByte(fis);
                y += readByte(fis);
                sb.append(" from byte ").append(doubleToStr(x)).append(" ").append(doubleToStr(y));

                //System.out.print("!" + doubleToStr(x) + " " + doubleToStr(y));
            } else if ((flags & FlaWriter.FLAG_EDGE_FROM_FLOAT) == FlaWriter.FLAG_EDGE_FROM_FLOAT) {
                x += readFloat(fis);
                y += readFloat(fis);
                sb.append(" from float ").append(doubleToStr(x)).append(" ").append(doubleToStr(y));
            } else {
                sb.append(" from none");
            }

            //if ((flags & FlaWriter.FLAG_EDGE_FROM_MASK) > 0) {
            edges.add("!" + doubleToStr(x) + " " + doubleToStr(y));
            //}

            boolean hasControl = false;
            if ((flags & FlaWriter.FLAG_EDGE_CONTROL_SHORT) == FlaWriter.FLAG_EDGE_CONTROL_SHORT) {
                double cx = x + readShort(fis);
                double cy = y + readShort(fis);
                edges.add("[" + doubleToStr(cx) + " " + doubleToStr(cy));
                hasControl = true;
                sb.append(" control short ").append(doubleToStr(cx)).append(" ").append(doubleToStr(cy));

            } else if ((flags & FlaWriter.FLAG_EDGE_CONTROL_BYTE) == FlaWriter.FLAG_EDGE_CONTROL_BYTE) {
                double cx = x + readByte(fis);
                double cy = y + readByte(fis);
                edges.add("[" + doubleToStr(cx) + " " + doubleToStr(cy));
                hasControl = true;
                sb.append(" control byte ").append(doubleToStr(cx)).append(" ").append(doubleToStr(cy));
            } else if ((flags & FlaWriter.FLAG_EDGE_CONTROL_FLOAT) == FlaWriter.FLAG_EDGE_CONTROL_FLOAT) {
                double cx = x + readFloat(fis);
                double cy = y + readFloat(fis);
                edges.add("[" + doubleToStr(cx) + " " + doubleToStr(cy));
                hasControl = true;
                sb.append(" control float ").append(doubleToStr(cx)).append(" ").append(doubleToStr(cy));
            } else {
                sb.append(" control none");
            }

            if ((flags & FlaWriter.FLAG_EDGE_TO_SHORT) == FlaWriter.FLAG_EDGE_TO_SHORT) {
                x += readShort(fis);
                y += readShort(fis);
                String prefix = "";
                if (!hasControl) {
                    prefix = "|";
                }
                edges.add(prefix + doubleToStr(x) + " " + doubleToStr(y));
                sb.append(" to short ").append(doubleToStr(x)).append(" ").append(doubleToStr(y));
            } else if ((flags & FlaWriter.FLAG_EDGE_TO_BYTE) == FlaWriter.FLAG_EDGE_TO_BYTE) {
                x += readByte(fis);
                y += readByte(fis);
                String prefix = "";
                if (!hasControl) {
                    prefix = "|";
                }
                edges.add(prefix + doubleToStr(x) + " " + doubleToStr(y));
                sb.append(" to byte ").append(doubleToStr(x)).append(" ").append(doubleToStr(y));
            } else if ((flags & FlaWriter.FLAG_EDGE_TO_FLOAT) == FlaWriter.FLAG_EDGE_TO_FLOAT) {
                x += readFloat(fis);
                y += readFloat(fis);
                String prefix = "";
                if (!hasControl) {
                    prefix = "|";
                }
                edges.add(prefix + doubleToStr(x) + " " + doubleToStr(y));
                sb.append(" to float ").append(doubleToStr(x)).append(" ").append(doubleToStr(y));
            } else {
                sb.append(" to none");
            }
            sb.append(";");
            if (!hasControl) {
                int generalLineFlag = fis.read();
                if (generalLineFlag == 1 && !edges.isEmpty()) {
                    String e = edges.get(edges.size() - 1);
                    e = "/" + e.substring(1);
                    edges.set(edges.size() - 1, e);
                }
            }
        }

        fis.close();
    }
}
