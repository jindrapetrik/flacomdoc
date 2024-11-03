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
package com.jpexs.flash.fla.converter;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Writes FLA data
 *
 * @author JPEXS
 */
public class FlaWriter {

    public static int EDGESELECTION_FILL0 = 1;
    public static int EDGESELECTION_FILL1 = 2;
    public static int EDGESELECTION_STROKE = 4;

    public static int FLAG_EDGE_NO_SELECTION = 128;
    public static int FLAG_EDGE_HAS_STYLES = 64;

    public static int FLAG_EDGE_FROM_FLOAT = 2;
    public static int FLAG_EDGE_FROM_SHORT = 1 + 2;
    public static int FLAG_EDGE_FROM_BYTE = 1;

    public static int FLAG_EDGE_FROM_MASK = FLAG_EDGE_FROM_SHORT;

    public static int FLAG_EDGE_CONTROL_FLOAT = 8;
    public static int FLAG_EDGE_CONTROL_SHORT = 4 + 8;
    public static int FLAG_EDGE_CONTROL_BYTE = 4;

    public static int FLAG_EDGE_TO_FLOAT = 32;
    public static int FLAG_EDGE_TO_SHORT = 16 + 32;
    public static int FLAG_EDGE_TO_BYTE = 16;

    public static int FLAG_EDGE_TO_MASK = FLAG_EDGE_TO_SHORT;

    public static int KEYMODE_STANDARD = 9728;

    public static int SCALEMODE_NORMAL = 0;
    public static int SCALEMODE_HORIZONTAL = 1;
    public static int SCALEMODE_VERTICAL = 2;
    public static int SCALEMODE_NONE = 3;

    public static int CAPSTYLE_NONE = 0;
    public static int CAPSTYLE_ROUND = 1;
    public static int CAPSTYLE_SQUARE = 2;

    public static int JOINSTYLE_MITER = 0;
    public static int JOINSTYLE_ROUND = 1;
    public static int JOINSTYLE_BEVEL = 2;

    public static final int FLOW_EXTEND = 0;
    public static final int FLOW_REFLECT = 4;
    public static final int FLOW_REPEAT = 8;

    public static final int FILLTYPE_LINEAR_GRADIENT = 0x10;
    public static final int FILLTYPE_RADIAL_GRADIENT = 0x12;

    public static final int FILLTYPE_BITMAP = 0x40;
    public static final int FILLTYPE_CLIPPED_BITMAP = 0x41;
    public static final int FILLTYPE_NON_SMOOTHED_BITMAP = 0x42;
    public static final int FILLTYPE_NON_SMOOTHED_CLIPPED_BITMAP = 0x43;

    public static final int SYMBOLTYPE_MOVIE_CLIP = 0;
    public static final int SYMBOLTYPE_BUTTON = 1;
    public static final int SYMBOLTYPE_GRAPHIC = 2;

    public static final int LOOPMODE_LOOP = 1;
    public static final int LOOPMODE_PLAY_ONCE = 2;
    public static final int LOOPMODE_SINGLE_FRAME = 3;

    public static final int LAYERTYPE_LAYER = 0;
    public static final int LAYERTYPE_GUIDE = 1;
    public static final int LAYERTYPE_GUIDED = 2;
    public static final int LAYERTYPE_FOLDER = 3;
    public static final int LAYERTYPE_MASK = 4;

    private String x = "0";
    private String y = "0";
    private int strokeStyle = 0;
    private int fillStyle0 = 0;
    private int fillStyle1 = 0;
    private boolean stylesChanged = false;
    private boolean moved = false;
    private String moveX = null;
    private String moveY = null;
    private int edgeSelection = 0;

    private static final Logger logger = Logger.getLogger(FlaWriter.class.getName());

    private OutputStream os;

    private boolean debugRandom = false;
    private final FlaFormatVersion flaFormatVersion;

    private long pos = 0;

    private String title = "";

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDebugRandom(boolean debugRandom) {
        this.debugRandom = debugRandom;
    }

    public boolean isDebugRandom() {
        return debugRandom;
    }

    public FlaWriter(OutputStream os, FlaFormatVersion flaFormatVersion) {
        this.os = os;
        this.flaFormatVersion = flaFormatVersion;
    }

    public void writeFloat(float val) throws IOException {
        writeUI32(Float.floatToIntBits(val));
    }

    public void writeDouble(double val) throws IOException {
        writeUI64(Double.doubleToLongBits(val));
    }

    public void beginShape() {
        stylesChanged = true;
        moved = false;
        x = "0";
        y = "0";
        edgeSelection = 0;
    }

    public void setStrokeStyle(int strokeStyle) {
        if (this.strokeStyle != strokeStyle) {
            stylesChanged = true;
        }
        this.strokeStyle = strokeStyle;
    }

    public void setFillStyle0(int fillStyle0) {
        if (this.fillStyle0 != fillStyle0) {
            stylesChanged = true;
        }
        this.fillStyle0 = fillStyle0;
    }

    public void setFillStyle1(int fillStyle1) {
        if (this.fillStyle1 != fillStyle1) {
            stylesChanged = true;
        }
        this.fillStyle1 = fillStyle1;
    }

    public static int getEdgesCount(String edges) throws IOException {
        edges = edges.replaceAll("[ \r\n\t\f]+", " ");
        edges = edges.replaceAll("([^ ])([!\\[\\|/])", "$1 $2");
        edges = edges.replaceAll("([!\\[\\|/])([^ ])", "$1 $2");

        edges = edges.trim();

        if (edges.isEmpty()) {
            return 0;
        }

        if (!edges.startsWith("!")) {
            throw new IllegalArgumentException("edges must start with !");
        }

        String[] moveToParts = edges.split("!", -1);

        int totalEdges = 0;

        for (int m = 1 /*first always empty*/; m < moveToParts.length; m++) {
            String moveToPart = moveToParts[m].trim();
            String[] parts = moveToPart.split(" ", -1);

            if (1 >= parts.length) {
                throw new IllegalArgumentException("! requires two arguments");
            }

            for (int i = 2; i < parts.length; i++) {
                switch (parts[i]) {
                    case "|":
                    case "/":
                        if (i + 2 >= parts.length) {
                            throw new IllegalArgumentException(parts[i] + " requires two arguments");
                        }
                        i += 2;
                        totalEdges++;
                        break;
                    case "[":
                        if (i + 4 >= parts.length) {
                            throw new IllegalArgumentException("[ requires four arguments");
                        }
                        i += 4;
                        totalEdges++;
                        break;
                }
            }
        }

        return totalEdges;
    }

    public void writeEdges(String edges, int strokeStyle, int fillStyle0, int fillStyle1) throws IOException {
        setStrokeStyle(strokeStyle);
        setFillStyle0(fillStyle0);
        setFillStyle1(fillStyle1);

        edges = edges.replaceAll("[ \r\n\t\f]+", " ");
        edges = edges.replaceAll("([^ ])([!\\[\\|/])", "$1 $2");
        edges = edges.replaceAll("([!\\[\\|/])([^ ])", "$1 $2");

        edges = edges.trim();

        if (edges.isEmpty()) {
            return;
        }
        if (!edges.startsWith("!")) {
            throw new IllegalArgumentException("edges must start with !");
        }

        String[] moveToParts = edges.split("!", -1);

        for (int m = 1 /*first always empty*/; m < moveToParts.length; m++) {
            String moveToPart = moveToParts[m].trim();
            String[] parts = moveToPart.split(" ", -1);

            if (1 >= parts.length) {
                throw new IllegalArgumentException("! requires two arguments");
            }

            int selection = 0;
            if (parts[1].contains("S")) {
                selection = Integer.parseInt(parts[1].substring(parts[1].indexOf("S") + 1));
                parts[1] = parts[1].substring(0, parts[1].indexOf("S"));
            }

            try {
                moveTo(
                        selection,
                        parts[0],
                        parts[1]
                );
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("! has invalid arguments: " + parts[0] + ", " + parts[1]);
            }

            for (int i = 2; i < parts.length; i++) {
                switch (parts[i]) {
                    case "|":
                    case "/":
                        if (i + 2 >= parts.length) {
                            throw new IllegalArgumentException(parts[i] + " requires two arguments");
                        }
                        try {
                            lineTo(
                                    parts[i + 1],
                                    parts[i + 2],
                                    parts[i].equals("/")
                            );
                        } catch (NumberFormatException nfe) {
                            throw new IllegalArgumentException(parts[i] + " has invalid arguments: " + parts[i + 1] + ", " + parts[i + 2]);
                        }
                        i += 2;
                        break;
                    case "[":
                        if (i + 4 >= parts.length) {
                            throw new IllegalArgumentException("[ requires four arguments");
                        }

                        try {
                            curveTo(
                                    parts[i + 1],
                                    parts[i + 2],
                                    parts[i + 3],
                                    parts[i + 4]
                            );
                        } catch (NumberFormatException nfe) {
                            throw new IllegalArgumentException("[ has invalid arguments: " + parts[i + 1] + ", " + parts[i + 2] + ", " + parts[i + 3] + ", " + parts[i + 4]);
                        }
                        i += 4;
                        break;
                }
            }
        }
    }

    public void writeEdge(int selection, String fromX, String fromY, String toX, String toY, String controlX, String controlY, boolean generalLine) throws IOException {
        int type = 0;
        if (controlX != null && controlY != null) {
            if (fitsXYByte(controlX, controlY)) {
                type |= FLAG_EDGE_CONTROL_BYTE;
            } else if (fitsXYShort(controlX, controlY)) {
                type |= FLAG_EDGE_CONTROL_SHORT;
            } else {
                type |= FLAG_EDGE_CONTROL_FLOAT;
            }
        }
        if (fromX != null && fromY != null && !(fromX.equals("0") && fromY.equals("0"))) {
            if (fitsXYByte(fromX, fromY)) {
                type |= FLAG_EDGE_FROM_BYTE;
            } else if (fitsXYShort(fromX, fromY)) {
                type |= FLAG_EDGE_FROM_SHORT;
            } else {
                type |= FLAG_EDGE_FROM_FLOAT;
            }
        }

        if (fitsXYByte(toX, toY)) {
            type |= FLAG_EDGE_TO_BYTE;
        } else if (fitsXYShort(toX, toY)) {
            type |= FLAG_EDGE_TO_SHORT;
        } else {
            type |= FLAG_EDGE_TO_FLOAT;
        }

        if (stylesChanged) {
            type |= FLAG_EDGE_HAS_STYLES;

            if (selection == 0) {
                type |= FLAG_EDGE_NO_SELECTION;
            }
        }
        logger.log(Level.FINE, "writing type 0x{0} ({1}{2}{3})", new Object[]{String.format("%02X", type), stylesChanged ? "style + " : "", fromX != null && fromY != null && !(fromX.equals("0") && fromY.equals("0")) ? "move + " : "", controlX != null ? "curve" : "straight"});
        write(type);
        if (stylesChanged) {
            logger.log(Level.FINE, "writing style 0x{0} 0x{1} 0x{2}", new Object[]{String.format("%02X", strokeStyle), String.format("%02X", fillStyle0), String.format("%02X", fillStyle1)});
            write(strokeStyle);
            if (selection != 0) {
                if ((selection & EDGESELECTION_STROKE) == EDGESELECTION_STROKE) {
                    write(0x80);
                } else {
                    write(0x00);
                }
            }
            write(fillStyle0);
            if (selection != 0) {
                if ((selection & EDGESELECTION_FILL0) == EDGESELECTION_FILL0) {
                    write(0x80);
                } else {
                    write(0x00);
                }
            }
            write(fillStyle1);
            if (selection != 0) {
                if ((selection & EDGESELECTION_FILL1) == EDGESELECTION_FILL1) {
                    write(0x80);
                } else {
                    write(0x00);
                }
            }
            stylesChanged = false;
        }
        if (fromX != null && fromY != null && !(fromX.equals("0") && fromY.equals("0"))) {
            writeXY(fromX, fromY);
        }
        if (controlX != null && controlY != null) {
            writeXY(controlX, controlY);
        }
        if (toX != null && toY != null) {
            writeXY(toX, toY);
        }
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
            if (controlX == null) {
                logger.log(Level.FINE, "writing generalLineFlag {0}", generalLine ? 1 : 0);
                write(generalLine ? 1 : 0);
            }
        }
        moved = false;
    }

    /**
     * 8 bits for fraction, 8 bits for value
     *
     * @param x
     * @param y
     * @return
     */
    public boolean fitsXYByte(String x, String y) {
        double vx = parseEdge(x);
        double vy = parseEdge(y);

        long integerX = (long) Math.floor(vx);
        long integerY = (long) Math.floor(vy);
        int fractX = (int) Math.floor((vx - integerX) * 256);
        int fractY = (int) Math.floor((vy - integerY) * 256);

        if (integerX >= Byte.MIN_VALUE
                && integerX <= Byte.MAX_VALUE
                && integerY >= Byte.MIN_VALUE
                && integerY <= Byte.MAX_VALUE) {
            return true;
        }
        return false;
    }

    /**
     * 1 bit for fraction, 15 bit for integer part
     *
     * @param x
     * @param y
     * @return
     */
    public boolean fitsXYShort(String x, String y) {

        double vx = parseEdge(x);
        double vy = parseEdge(y);

        long integerX = (long) Math.floor(vx);
        long integerY = (long) Math.floor(vy);
        int fractX = (int) Math.floor((vx - integerX) * 256);
        int fractY = (int) Math.floor((vy - integerY) * 256);

        try {

            int nBits = 15;
            int min = -(1 << (nBits - 1));
            int max = (1 << (nBits - 1)) - 1;

            if (integerX >= min
                    && integerX <= max
                    && integerY >= min
                    && integerY <= max
                    && (fractX == 0 || fractX == 128)
                    && (fractY == 0 || fractY == 128)) {
                return true;
            }
        } catch (NumberFormatException nfe) {
            //ignore            
        }
        return false;
    }

    public void writeXY(String x, String y) throws IOException {

        double vx = parseEdge(x);
        double vy = parseEdge(y);

        int integerX = (int) Math.floor(vx);
        int integerY = (int) Math.floor(vy);
        int fractX = (int) Math.floor((vx - integerX) * 256);
        int fractY = (int) Math.floor((vy - integerY) * 256);

        if (integerX >= Byte.MIN_VALUE
                && integerX <= Byte.MAX_VALUE
                && integerY >= Byte.MIN_VALUE
                && integerY <= Byte.MAX_VALUE) {
            logger.log(Level.FINE, "writing as byte 0x00 0X{0} 0x00 0x{1} ({2,number,#}, {3,number,#})", new Object[]{String.format("%02X", integerX & 0xFF), String.format("%02X", integerY & 0xFF), integerX, integerY});
            write(fractX);
            write(integerX);
            write(fractY);
            write(integerY);
            return;
        }

        int shortNBits = 15;
        int minShort = -(1 << (shortNBits - 1));
        int maxShort = (1 << (shortNBits - 1)) - 1;
        if (integerX >= minShort
                && integerX <= maxShort
                && integerY >= minShort
                && integerY <= maxShort
                && (fractX == 0 || fractX == 0x80)
                && (fractY == 0 || fractY == 0x80)) {
            logger.log(Level.FINE, "writing as short 0x{0} 0x{1} 0x{2} 0x{3} ({4,number,#}, {5,number,#})", new Object[]{String.format("%02X", (integerX << 1) & 0xFF), String.format("%02X", (integerX >> 7) & 0xFF), String.format("%02X", (integerY << 1) & 0xFF), String.format("%02X", (integerY >> 7) & 0xFF), integerX, integerY});

            integerX = (int) Math.floor(vx * 2);
            integerY = (int) Math.floor(vy * 2);

            write(integerX & 0xFF);
            write((integerX >> 8) & 0xFF);

            write(integerY & 0xFF);
            write((integerY >> 8) & 0xFF);

            return;
        }

        int intNBits = 24;
        int minInt = -(1 << (intNBits - 1));
        int maxInt = (1 << (intNBits - 1)) - 1;

        if (integerX < minInt
                || integerX > maxInt
                || integerY < minInt
                || integerY > maxInt) {
            throw new NumberFormatException("cannot store XY values: " + x + ", " + y);
        }

        logger.log(Level.FINE, "writing as fract 0x{0} 0x{1} 0x{2} 0x{3} 0x{4} 0x{5} 0x{6} 0x{7} ({8,number,#}.{9,number,#},{10,number,#}.{11,number,#} )", new Object[]{String.format("%02X", fractX), String.format("%02X", integerX & 0xFF), String.format("%02X", (integerX >> 8) & 0xFF), String.format("%02X", (integerX >> 16) & 0xFF), String.format("%02X", fractY), String.format("%02X", integerY & 0xFF), String.format("%02X", (integerY >> 8) & 0xFF), String.format("%02X", (integerY >> 16) & 0xFF), integerX, fractX, integerY, fractY});

        write(fractX);
        write(integerX & 0xFF);
        write((integerX >> 8) & 0xFF);
        write((integerX >> 16) & 0xFF);

        write(fractY);
        write(integerY & 0xFF);
        write((integerY >> 8) & 0xFF);
        write((integerY >> 16) & 0xFF);
    }

    public void moveTo(int selection, String x, String y) {
        moved = true;
        this.moveX = x;
        this.moveY = y;
        this.edgeSelection = selection;
    }

    public Point2D parsePoint(String pointData) {
        if (pointData.isEmpty()) {
            return new Point2D.Double(0, 0);
        }
        String[] parts = pointData.split(",", -1);
        if (parts.length != 2) {
            return null;
        }
        return new Point2D.Double(parseEdge(parts[0].trim()), parseEdge(parts[1].trim()));
    }

    private double parseEdge(String edge) {
        Pattern doubleHexPattern = Pattern.compile("#(?<before>[a-fA-F0-9]+){1,6}(\\.(?<after>[0-9a-fA-F]{1,2}))?");
        Matcher m = doubleHexPattern.matcher(edge);
        if (m.matches()) {
            String before = m.group("before");
            String after = m.group("after");
            int afterInt = 0;
            if (after != null) {
                if (after.length() == 1) {
                    after += "0";
                }
                afterInt = Integer.parseInt(after, 16);
            }
            int beforeInt = Integer.parseInt(before, 16);
            beforeInt = (beforeInt << 8) >> 8; //sign extend
            return beforeInt + afterInt / 256.0;
        }
        return Double.parseDouble(edge);
    }

    private String numEdgeToString(double value) {
        if (value == Math.floor(value)) {
            long lval = (long) value;
            return "" + lval;
        }
        long integerPart = (long) Math.floor(value);
        double fractionalPart = value - integerPart;
        int fractionalPart256 = (int) Math.floor(fractionalPart * 256);
        String h = Long.toHexString(integerPart).toUpperCase();
        if (h.length() > 6) {
            h = h.substring(h.length() - 6, h.length());
        }
        return "#" + h + "." + String.format("%02X", fractionalPart256);
    }

    private String deltaEdge(String v1, String v2) {
        return numEdgeToString(parseEdge(v1) - parseEdge(v2));
    }

    public void lineTo(String x2, String y2, boolean generalLine) throws IOException {
        String newX = moved ? moveX : this.x;
        String newY = moved ? moveY : this.y;
        writeEdge(
                moved ? edgeSelection : 0,
                moved ? deltaEdge(moveX, this.x) : null,
                moved ? deltaEdge(moveY, this.y) : null,
                deltaEdge(x2, newX),
                deltaEdge(y2, newY),
                null,
                null,
                generalLine);
        this.x = x2;
        this.y = y2;
    }

    public void curveTo(String controlX, String controlY, String anchorX, String anchorY) throws IOException {
        String newX = moved ? moveX : this.x;
        String newY = moved ? moveY : this.y;
        writeEdge(
                moved ? edgeSelection : 0,
                moved ? deltaEdge(moveX, this.x) : null,
                moved ? deltaEdge(moveY, this.y) : null,
                deltaEdge(anchorX, newX),
                deltaEdge(anchorY, newY),
                deltaEdge(controlX, newX),
                deltaEdge(controlY, newY),
                false
        );
        this.x = anchorX;
        this.y = anchorY;
    }

    public void writeMatrix(Matrix v) throws IOException {
        writeMatrix(v.a, v.b, v.c, v.d, v.tx, v.ty);
    }

    public void writeMatrix(
            double a,
            double b,
            double c,
            double d,
            double tx,
            double ty) throws IOException {
        double multiplier = 1.52587890625e-5;
        a /= multiplier;
        b /= multiplier;
        c /= multiplier;
        d /= multiplier;

        tx *= 20;
        ty *= 20;

        long aLong = (long) Math.round(a);
        long bLong = (long) Math.round(b);
        long cLong = (long) Math.round(c);
        long dLong = (long) Math.round(d);
        long txLong = (long) Math.round(tx);
        long tyLong = (long) Math.round(ty);

        if (debugRandom) {
            for (int i = 0; i < 6 * 4; i++) {
                write('X');
            }
            return;
        }

        write(
                (int) (aLong & 0xFF), (int) ((aLong >> 8) & 0xFF), (int) ((aLong >> 16) & 0xFF), (int) ((aLong >> 24) & 0xFF),
                (int) (bLong & 0xFF), (int) ((bLong >> 8) & 0xFF), (int) ((bLong >> 16) & 0xFF), (int) ((bLong >> 24) & 0xFF),
                (int) (cLong & 0xFF), (int) ((cLong >> 8) & 0xFF), (int) ((cLong >> 16) & 0xFF), (int) ((cLong >> 24) & 0xFF),
                (int) (dLong & 0xFF), (int) ((dLong >> 8) & 0xFF), (int) ((dLong >> 16) & 0xFF), (int) ((dLong >> 24) & 0xFF),
                (int) (txLong & 0xFF), (int) ((txLong >> 8) & 0xFF), (int) ((txLong >> 16) & 0xFF), (int) ((txLong >> 24) & 0xFF),
                (int) (tyLong & 0xFF), (int) ((tyLong >> 8) & 0xFF), (int) ((tyLong >> 16) & 0xFF), (int) ((tyLong >> 24) & 0xFF));
    }

    public void writeBitmapFill(
            int type,
            Matrix bitmapMatrix,
            int bitmapId
    ) throws IOException {
        write(
                0xFF, 0x00, 0x00, 0xFF,
                type,
                0x00);
        writeMatrix(bitmapMatrix);
        if (debugRandom) {
            write('X', 'X');
        } else {
            writeUI16(bitmapId);
        }
    }

    public void writeGradientFill(
            Color[] colors,
            double stopPos[],
            int type,
            boolean linearRgb,
            int flow,
            Matrix gradientMatrix,
            double focalRatio
    ) throws IOException {

        write(0x00, 0x00, 0x00, debugRandom ? 'U' : 0x00);
        write(type, 0x00);
        writeMatrix(gradientMatrix);
        write(colors.length);
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            write((int) Math.round(focalRatio * 255), 0x00,
                    0x00, 0x00, (flow + (linearRgb ? 1 : 0)), 0x00, 0x00, 0x00
            );
        }
        for (int i = 0; i < colors.length; i++) {
            write((int) Math.round(stopPos[i] * 255), colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(),
                    debugRandom ? 'X' : colors[i].getAlpha() //rounding errors
            );
        }
    }

    public void writeSolidFill(Color fillColor) throws IOException {
        write(
                fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), fillColor.getAlpha());
        write(0x00);
        write(0x00);
    }

    public void writeSolidStroke(
            Color lineColor,
            int strokeWidthTwips
    ) throws IOException {
        writeSolidStroke(lineColor,
                strokeWidthTwips,
                false,
                SCALEMODE_NORMAL,
                CAPSTYLE_ROUND,
                JOINSTYLE_ROUND,
                3,
                0,
                0);
    }

    public void writeStrokeBegin(Color lineColor,
            double strokeWidth,
            boolean pixelHinting,
            int scaleMode,
            int capStyle,
            int joinStyle,
            float miter,
            int styleParam1,
            int styleParam2) throws IOException {
        /*
        Style: Dashed
        styleParam1 dash len
        styleParam2 space len
        
        Other styles use only styleParam2.       
        
        Style: Dotted = 0x10 * dottedParam * 10 + 0x02
        
        
        Style: Ragged = 0x100 * waveLength + 0x08 * pattern + 0x40 * waveHeight + 0x03
        
            Pattern: solid, simple, random, dotted, randomDotted, trippleDotted, randomTrippleDotted
            Wave height: flat, wavy, veryWavy, wild
            Wave length: veryShort, short, medium, long


        Style: Stipple = 0x08 * dotSize + 0x20 * dotVariation + 0x80 * density + 0x04
        
            Dot size: tiny, small, medium, large
            Dot variation: oneSize, smallVariation, variedSizes, randomSizes
            Density: veryDense, dense, sparse, verySparse

        Style: Hatched = 
            	0x08 * thickness + 
                0x20 * space +
                0x200 * jiggle +
                0x80 * rotate +
                0x800 * curve +
                0x2000 * length +
                0x05
        
            Thickness: hairline, thin, medium, thick
            Space: veryClose, close, distant, veryDistant
            Jiggle: none, bounce, loose, wild
            Rotate: none, slight, medium, free
            Curve: straight, slightCurve, mediumCurve, veryCurved
            Length: equal, slightVariation, mediumVariation, random
        
        All styles: +0x8000 when shape corners
         */

        int strokeWidthTwips = (int) Math.round(strokeWidth * 20);
        /*if (strokeWidthTwips == 2 && lineColor.getAlpha() == 0) {
            strokeWidthTwips = 0; //??
        }*/
        write(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), lineColor.getAlpha());
        write((strokeWidthTwips & 0xFF), ((strokeWidthTwips >> 8) & 0xFF));
        write((styleParam1 & 0xFF), ((styleParam1 >> 8) & 0xFF),
                (styleParam2 & 0xFF), ((styleParam2 >> 8) & 0xFF));
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            write((pixelHinting ? 1 : 0), scaleMode, capStyle,
                    joinStyle, (int) ((miter - Math.floor(miter)) * 256), (int) Math.floor(miter));
        }
    }

    public void writeSolidStroke(
            Color lineColor,
            double strokeWidth,
            boolean pixelHinting,
            int scaleMode,
            int capStyle,
            int joinStyle,
            float miter,
            int styleParam1,
            int styleParam2
    ) throws IOException {

        writeStrokeBegin(lineColor, strokeWidth, pixelHinting, scaleMode, capStyle, joinStyle, miter, styleParam1, styleParam2);
        writeSolidFill(lineColor);
    }

    public void writeKeyFrameMiddle() throws IOException {
        write(
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x80, //tp
                0x00, 0x00, 0x00, 0x80, //tp
                0x00, 0x00, 0x06,
                //matrix
                0x00, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x05);
    }

    public int generateRandomId() {
        if (debugRandom) {
            return ('X' << 8) + 'X';
        } else {
            Random rnd = new Random();
            return rnd.nextInt(0x10000);
        }
    }

    public void writeBomString(String s) throws IOException {
        if (flaFormatVersion.isUnicode()) {
            write(0xFF, 0xFE, 0xFF);
        }
        writeString(s);
    }

    public void writeString(String s) throws IOException {
        byte[] b = s.getBytes();
        int len = flaFormatVersion.isUnicode() ? s.length() : b.length;
        if (len < 0xFF) {
            write(len);
        } else if (len < 0xFFFF) {
            write(0xFF);
            writeUI16(len);
        } else {
            write(0xFF);
            write(0xFF);
            write(0xFF);
            writeUI32(len);
        }
        if (flaFormatVersion.isUnicode()) {
            write(s.getBytes("UTF-16LE"));
        } else {
            write(b);
        }
    }

    public void writeEndParentLayer(int parentLayerIndex) throws IOException {
        write(parentLayerIndex);
        write(0);
    }

    public void write(byte[] bytes) throws IOException {
        os.write(bytes);
        pos += bytes.length;
    }

    public void write(int... values) throws IOException {
        for (int i : values) {
            if (i > 255) {
                throw new IllegalArgumentException("Attempt to write larger value than 255 as byte");
            }
            os.write(i);
            pos++;
        }
    }

    public void writeEncodedUI(int value) throws IOException {
        if (value >= 0x7FFF) {
            write(0xFF, 0x7F);
            writeUI32(value);
            return;
        }
        writeUI16(value);
    }

    public void writeUI16(int value) throws IOException {
        if (value > 0xFFFF) {
            throw new IllegalArgumentException("Attempt to write larger value than 0xFFFF as UI16");
        }
        write(value & 0xFF);
        write((value >> 8) & 0xFF);
    }

    public void writeItemID(String itemID) throws IOException {
        if (debugRandom) { // || ("XXXXXXXX-XXXXXXXX".equals(itemID))) {
            write('X', 'X', 'X', 'X', 'X', 'X', 'X', 'X');
            return;
        }
        Pattern itemIdPattern = Pattern.compile("^(?<hi>[a-f0-9]{8})-(?<lo>[a-f0-9]{8})$");
        Matcher m = itemIdPattern.matcher(itemID);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid itemID supplied: " + itemID);
        }
        Long itemIDHigh = Long.parseLong(m.group("hi"), 16);
        Long itemIDLow = Long.parseLong(m.group("lo"), 16);

        writeUI32(itemIDHigh);
        writeUI32(itemIDLow);
    }

    public void writeUI32(long value) throws IOException {
        write((int) (value & 0xFF));
        write((int) ((value >> 8) & 0xFF));
        write((int) ((value >> 16) & 0xFF));
        write((int) ((value >> 24) & 0xFF));
    }

    public void writeUI64(long value) throws IOException {
        write((int) (value & 0xFF));
        write((int) ((value >> 8) & 0xFF));
        write((int) ((value >> 16) & 0xFF));
        write((int) ((value >> 24) & 0xFF));
        write((int) ((value >> 32) & 0xFF));
        write((int) ((value >> 40) & 0xFF));
        write((int) ((value >> 48) & 0xFF));
        write((int) ((value >> 56) & 0xFF));
    }

    private void writePointPart(double val) throws IOException {
        int fullPart = (int) Math.floor(val);
        int fraction = (int) Math.round((val - fullPart) * 256);
        write(fraction);
        write(fullPart & 0xFF);
        write((fullPart >> 8) & 0xFF);
        write((fullPart >> 16) & 0xFF);
    }

    public void writePoint(Point2D point) throws IOException {
        writePointPart(point.getX());
        writePointPart(point.getY());
    }

    public long getPos() {
        return pos;
    }

    public void writeDebugNote(String note) throws IOException {
        write('!');
        write(note.getBytes("UTF-8"));
    }
}
