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

import com.jpexs.flash.fla.convertor.coloreffects.ColorEffectInterface;
import com.jpexs.flash.fla.convertor.coloreffects.NoColorEffect;
import com.jpexs.flash.fla.convertor.filters.FilterInterface;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Writes page file of Flash CS4 FLA file.
 *
 * @author JPEXS
 */
public class FlaCs4Writer {

    public static int FLAG_EDGE_HAS_STYLES = 128 + 64;

    public static int FLAG_EDGE_FROM_FLOAT = 2;
    public static int FLAG_EDGE_FROM_SHORT = 1 + 2;
    public static int FLAG_EDGE_FROM_BYTE = 1;

    public static int FLAG_EDGE_CONTROL_FLOAT = 8;
    public static int FLAG_EDGE_CONTROL_SHORT = 4 + 8;
    public static int FLAG_EDGE_CONTROL_BYTE = 4;

    public static int FLAG_EDGE_TO_FLOAT = 32;
    public static int FLAG_EDGE_TO_SHORT = 16 + 32;
    public static int FLAG_EDGE_TO_BYTE = 16;

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

    public static final int SYMBOLTYPE_SPRITE = 0;
    public static final int SYMBOLTYPE_BUTTON = 1;
    public static final int SYMBOLTYPE_GRAPHIC = 2;

    public static final int LOOPMODE_LOOP = 1;
    public static final int LOOPMODE_PLAY_ONCE = 2;
    public static final int LOOPMODE_SINGLE_FRAME = 3;

    public static final int LAYERTYPE_LAYER = 0;
    public static final int LAYERTYPE_GUIDE = 1;
    public static final int LAYERTYPE_FOLDER = 3;

    private String x = "0";
    private String y = "0";
    private int strokeStyle = 0;
    private int fillStyle0 = 0;
    private int fillStyle1 = 0;
    private boolean stylesChanged = false;
    private boolean moved = false;
    private String moveX = null;
    private String moveY = null;

    private static final Logger logger = Logger.getLogger(FlaCs4Writer.class.getName());

    private OutputStream os;

    public FlaCs4Writer(OutputStream os) {
        this.os = os;
    }

    /**
     *
     * @param nextLayerId 1-based
     * @param nextFolderId 1-based
     * @param activeFrame 0-based
     * @throws IOException
     */
    public void writePageFooter(int nextLayerId, int nextFolderId, int activeFrame) throws IOException {
        write(
                0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00,
                0x80, 0x00, 0x00, 0x07, nextLayerId, 0x00, nextFolderId, 0x00, activeFrame, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    }

    public void writeBasicLayer(int layerNum) throws IOException {
        Random rnd = new Random();
        writeLayer(1,
                KEYMODE_STANDARD,
                "Layer " + layerNum,
                false,
                false,
                false,
                new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)),
                false,
                LAYERTYPE_LAYER,
                1);
    }

    public void writeFloat(float val) throws IOException {
        int v = Float.floatToIntBits(val);
        write(v & 0xFF);
        write((v >> 8) & 0xFF);
        write((v >> 16) & 0xFF);
        write((v >> 24) & 0xFF);
    }

    public void writeSymbolInstance(
            Matrix placeMatrix,
            double centerPoint3DX,
            double centerPoint3DY,
            double transformationPointX,
            double transformationPointY,
            String instanceName,
            ColorEffectInterface colorEffect,
            int librarySymbolId,
            int oldCopiedComponentPath,
            int blendMode,
            boolean cacheAsBitmap,
            List<FilterInterface> filters,
            int symbolType,
            boolean trackAsMenu,
            int loop,
            int firstFrame,
            String actionScript
    ) throws IOException {

        int symbolInstanceId = generateRandomId();

        long centerPoint3DXLong = Math.round(centerPoint3DX * 20);
        long centerPoint3DYLong = Math.round(centerPoint3DY * 20);

        Point2D transformationPoint = new Point2D.Double(transformationPointX, transformationPointY);
        Point2D transformationPointTransformed = placeMatrix.transform(transformationPoint);

        long tptX = Math.round(transformationPointTransformed.getX() * 20);
        long tptY = Math.round(transformationPointTransformed.getY() * 20);
        /*
        keyframe begin:
        0x00, 0x00, 
        0x00, 0x00, 0x00,  0x80,
        0x00, 0x00, 0x00,  0x80, 
        0x00, 0x00, 0x06,  - is this some kind of type identifier?
        
        ...
         */
        write(
                (actionScript.isEmpty() ? 0x00 : 0x02), 0x00, 0x00,
                (int) (tptX & 0xFF), (int) ((tptX >> 8) & 0xFF), (int) ((tptX >> 16) & 0xFF), (int) ((tptX >> 24) & 0xFF),
                (int) (tptY & 0xFF), (int) ((tptY >> 8) & 0xFF), (int) ((tptY >> 16) & 0xFF), (int) ((tptY >> 24) & 0xFF),
                0x00, (cacheAsBitmap ? 1 : 0), 0x16
        );
        writeMatrix(placeMatrix);

        write((firstFrame & 0xFF), ((firstFrame >> 8) & 0xFF));
        if (symbolType == SYMBOLTYPE_SPRITE) {
            write(0x02);
        } else if (symbolType == SYMBOLTYPE_BUTTON) {
            write(0x00);
        } else if (symbolType == SYMBOLTYPE_GRAPHIC) {
            switch (loop) {
                case LOOPMODE_LOOP:
                    write(0x00);
                    break;
                case LOOPMODE_PLAY_ONCE:
                    write(0x01);
                    break;
                case LOOPMODE_SINGLE_FRAME:
                    write(0x02);
                    break;
            }
        }

        write(0x00, 0x01);

        if (colorEffect == null) {
            colorEffect = new NoColorEffect();
        }

        int redMultiplier = colorEffect.getRedMultiplier();
        int greenMultiplier = colorEffect.getGreenMultiplier();
        int blueMultiplier = colorEffect.getBlueMultiplier();
        int alphaMultiplier = colorEffect.getAlphaMultiplier();
        int redOffset = colorEffect.getRedOffset();
        int greenOffset = colorEffect.getGreenOffset();
        int blueOffset = colorEffect.getBlueOffset();
        int alphaOffset = colorEffect.getAlphaOffset();
        Color effectColor = colorEffect.getValueColor();

        write(
                (alphaMultiplier & 0xFF), ((alphaMultiplier >> 8) & 0xFF), (alphaOffset & 0xFF), ((alphaOffset >> 8) & 0xFF),
                (redMultiplier & 0xFF), ((redMultiplier >> 8) & 0xFF), (redOffset & 0xFF), ((redOffset >> 8) & 0xFF),
                (greenMultiplier & 0xFF), ((greenMultiplier >> 8) & 0xFF), (greenOffset & 0xFF), ((greenOffset >> 8) & 0xFF),
                (blueMultiplier & 0xFF), ((blueMultiplier >> 8) & 0xFF), (blueOffset & 0xFF), ((blueOffset >> 8) & 0xFF),
                colorEffect.getType(), 0x00, colorEffect.getValuePercent(), 0x00,
                effectColor.getRed(), effectColor.getGreen(), effectColor.getBlue(), effectColor.getAlpha()
        );

        write(
                0xFF, 0xFE, 0xFF, 0x00, //some string
                librarySymbolId, 0x00, 0x00, 0x00, //FIXME? this is probably a long val
                0x00, 0x00, 0x00
        );

        if (!filters.isEmpty()) {
            write(0x01,
                    filters.size(), 0x00, 0x00, 0x00);
            for (FilterInterface filter : filters) {
                filter.write(this);
            }
        } else {
            write(0x00);
        }

        write(
                blendMode,
                (filters.isEmpty() ? 0 : filters.size() - 1), //WTF?
                0x00, 0x00, 0x00, 0x80, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x3F, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x80, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);

        if (symbolType != SYMBOLTYPE_SPRITE) {
            write(
                    0x00, 0x00, 0x00, 0x80,
                    0x00, 0x00, 0x00, 0x80);
        } else {
            write(
                    (int) (centerPoint3DXLong & 0xFF), (int) ((centerPoint3DXLong >> 8) & 0xFF), (int) ((centerPoint3DXLong >> 16) & 0xFF), (int) ((centerPoint3DXLong >> 24) & 0xFF),
                    (int) (centerPoint3DYLong & 0xFF), (int) ((centerPoint3DYLong >> 8) & 0xFF), (int) ((centerPoint3DYLong >> 16) & 0xFF), (int) ((centerPoint3DYLong >> 24) & 0xFF)
            );
        }

        write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00);

        if (symbolType == SYMBOLTYPE_GRAPHIC) {
            return;
        }

        write((symbolType == SYMBOLTYPE_BUTTON ? 0x0B : 0x08), 0x05, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00,
                (symbolInstanceId & 0xFF), ((symbolInstanceId >> 8) & 0xFF),
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0xFE, 0xFF);
        writeLenUnicodeString(actionScript);
        if (symbolType == SYMBOLTYPE_BUTTON) {
            write((int) (trackAsMenu ? 1 : 0));
        }
        write(0xFF, 0xFE, 0xFF);

        writeLenUnicodeString(instanceName);

        if (symbolType == SYMBOLTYPE_BUTTON) {
            write(0x00, 0x00, 0x00, 0x00);
            return;
        }
        write(0x02, 0x00, 0x00, 0x00, 0x00,
                0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01,
                0x00 /*something, but it resets after resaving FLA*/, 0x00, 0x00, 0x00,
                0xFF, 0xFE, 0xFF
        );
        String componentTxt = "<component metaDataFetched='true' schemaUrl='' schemaOperation='' sceneRootLabel='Scene 1' oldCopiedComponentPath='" + oldCopiedComponentPath + "'>\n</component>\n";
        writeLenUnicodeString(componentTxt);
    }

    public void beginShape() {
        stylesChanged = true;
        moved = false;
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
        edges = edges.replaceAll("([^ ])([!\\[\\|])", "$1 $2");
        edges = edges.replaceAll("([!\\[\\|])([^ ])", "$1 $2");

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
                        if (i + 2 >= parts.length) {
                            throw new IllegalArgumentException("| requires two arguments");
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
        edges = edges.replaceAll("([^ ])([!\\[\\|])", "$1 $2");
        edges = edges.replaceAll("([!\\[\\|])([^ ])", "$1 $2");

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
            try {
                moveTo(
                        parts[0],
                        parts[1]
                );
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("! has invalid arguments: " + parts[1] + ", " + parts[2]);
            }

            for (int i = 2; i < parts.length; i++) {
                switch (parts[i]) {
                    case "|":
                        if (i + 2 >= parts.length) {
                            throw new IllegalArgumentException("| requires two arguments");
                        }
                        try {
                            lineTo(
                                    parts[i + 1],
                                    parts[i + 2]
                            );
                        } catch (NumberFormatException nfe) {
                            throw new IllegalArgumentException("| has invalid arguments: " + parts[1] + ", " + parts[2]);
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
                            throw new IllegalArgumentException("[ has invalid arguments: " + parts[1] + ", " + parts[2] + ", " + parts[3] + ", " + parts[4]);
                        }
                        i += 4;
                        break;
                }
            }
        }
    }

    public void writeEdge(String fromX, String fromY, String toX, String toY, String controlX, String controlY) throws IOException {
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
        }
        logger.log(Level.FINE, "writing type 0x{0} ({1}{2}{3})", new Object[]{String.format("%02X", type), stylesChanged ? "style + " : "", fromX != null && fromY != null && !(fromX.equals("0") && fromY.equals("0")) ? "move + " : "", controlX != null ? "curve" : "straight"});
        write(type);
        if (stylesChanged) {
            logger.log(Level.FINE, "writing style 0x{0} 0x{1} 0x{2}", new Object[]{String.format("%02X", strokeStyle), String.format("%02X", fillStyle0), String.format("%02X", fillStyle1)});
            write(strokeStyle);
            write(fillStyle0);
            write(fillStyle1);
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
        if (controlX == null) {
            logger.fine("writing end 0x00");
            write(0x00);
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

    public void moveTo(String x, String y) {
        moved = true;
        this.moveX = x;
        this.moveY = y;
    }

    private double parseEdge(String edge) {
        Pattern doubleHexPattern = Pattern.compile("#(?<before>[a-fA-F0-9]+){1,8}(\\.(?<after>[0-9a-fA-F]{2}))?");
        Matcher m = doubleHexPattern.matcher(edge);
        if (m.matches()) {
            String before = m.group("before");
            String after = m.group("after");
            int afterInt = 0;
            if (after != null) {
                afterInt = Integer.parseInt(after, 16);
            }
            int beforeInt = Integer.parseInt(before, 16);
            return beforeInt + afterInt / 256.0;
        }
        if (edge.contains("S")) {
            edge = edge.substring(0, edge.indexOf("S"));
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
        return "#" + Long.toHexString(integerPart).toUpperCase() + "." + String.format("%02X", fractionalPart256);
    }

    private String deltaEdge(String v1, String v2) {
        return numEdgeToString(parseEdge(v1) - parseEdge(v2));
    }

    public void lineTo(String x2, String y2) throws IOException {
        String newX = moved ? moveX : this.x;
        String newY = moved ? moveY : this.y;
        writeEdge(
                moved ? deltaEdge(moveX, this.x) : null,
                moved ? deltaEdge(moveY, this.y) : null,
                deltaEdge(x2, newX),
                deltaEdge(y2, newY),
                null,
                null);
        this.x = x2;
        this.y = y2;
    }

    public void curveTo(String controlX, String controlY, String anchorX, String anchorY) throws IOException {
        String newX = moved ? moveX : this.x;
        String newY = moved ? moveY : this.y;
        writeEdge(
                moved ? deltaEdge(moveX, this.x) : null,
                moved ? deltaEdge(moveY, this.y) : null,
                deltaEdge(anchorX, newX),
                deltaEdge(anchorY, newY),
                deltaEdge(controlX, newX),
                deltaEdge(controlY, newY)
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
        write(
                bitmapId, 0x00
        );
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

        write(
                0x00, 0x00, 0x00, 0x00 /*this is sometimes 0xFF*/, type, 0x00);
        writeMatrix(gradientMatrix);
        write(
                colors.length,
                (int) Math.round(focalRatio * 256), 0x00, 0x00, 0x00, (flow + (linearRgb ? 1 : 0)), 0x00, 0x00, 0x00
        );
        for (int i = 0; i < colors.length; i++) {
            write((int) Math.round(stopPos[i] * 255), colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), colors[i].getAlpha());
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
        write(
                lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), lineColor.getAlpha(),
                (strokeWidthTwips & 0xFF), ((strokeWidthTwips >> 8) & 0xFF),
                (styleParam1 & 0xFF), ((styleParam1 >> 8) & 0xFF),
                (styleParam2 & 0xFF), ((styleParam2 >> 8) & 0xFF),
                (pixelHinting ? 1 : 0), scaleMode, capStyle,
                joinStyle, (int) ((miter - Math.floor(miter)) * 256), (int) Math.floor(miter));
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
                0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x06, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05);
    }

    private int generateRandomId() {
        return ('X' << 8) + 'X';
        //Random rnd = new Random();
        //return rnd.nextInt(0x10000);
    }

    public void writeKeyFrameEnd(int duration, int keyMode, String actionScript) throws IOException {
        int frameId = generateRandomId();

        write(
                0x00, 0x00, 0x00, 0x00, 0x00,
                0x1D, duration,
                0x00, (keyMode & 0xFF), ((keyMode >> 8) & 0xFF), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF,
                0xFF, 0xFF, 0x3F, 0xFF, 0xFF, 0xFF, 0xFE, 0xFF, 0x00, 0x05, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, ((frameId >> 8) & 0xFF), (frameId & 0xFF),
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0xFE, 0xFF);
        writeLenUnicodeString(actionScript);
        write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFE, 0xFF, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    }

    public void writeKeyFrame(int frameLen, int keyMode) throws IOException {

        writeKeyFrameMiddle();
        int numEdges = 0;
        int numFillStyles = 0;
        int numStrokeStyles = 0;

        write(numEdges, 0x00, 0x00, 0x00);
        write(numFillStyles, 0x00);
        //place fillstyles here        
        write(numStrokeStyles, 0x00);
        //place stroke styles here
        writeKeyFrameEnd(frameLen, keyMode, "");
    }

    public void writeLayer(
            int frameLen,
            int keyMode,
            String layerName,
            boolean selectedLayer,
            boolean hiddenLayer,
            boolean lockedLayer,
            Color layerColor,
            boolean showOutlines,
            int layerType,
            int heightMultiplier
    ) throws IOException {

        /*writeKeyFrame(1, KEYMODE_STANDARD);
        writeKeyFrameSeparator();
        writeKeyFrame(1, KEYMODE_STANDARD);
        writeKeyFrameSeparator();
        writeKeyFrame(1, KEYMODE_STANDARD);*/
        writeKeyFrame(1, KEYMODE_STANDARD);
        writeLayerEnd(layerName, selectedLayer, hiddenLayer, lockedLayer, layerColor, showOutlines, layerType, heightMultiplier);

    }

    public void writeLayerEnd(
            String layerName,
            boolean selectedLayer,
            boolean hiddenLayer,
            boolean lockedLayer,
            Color layerColor,
            boolean showOutlines,
            int layerType,
            int heightMultiplier
    ) throws IOException {
        write(
                0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00, 0x80, 0x00,
                0x00, 0x0D, 0xFF, 0xFE, 0xFF
        );

        writeLenUnicodeString(layerName);
        write(selectedLayer ? 1 : 0);
        write(hiddenLayer ? 1 : 0);
        write(lockedLayer ? 1 : 0);
        write(0xFF, 0xFF, 0xFF, 0xFF);
        write(layerColor.getRed());
        write(layerColor.getGreen());
        write(layerColor.getBlue());
        write(0xFF);
        write(showOutlines ? 1 : 0);
        write(0x00, 0x00, 0x00, heightMultiplier, 0x00, 0x00, 0x00);
        write(layerType);
    }

    public void writeLayerEnd2(int parentLayerIndex, boolean open, boolean autoNamed) throws IOException {
        if (parentLayerIndex > -1) {
            write(7 + parentLayerIndex);
        } else {
            write(0x00);
        }
        write(0x00);
        write(open ? 1 : 0);
        write(autoNamed ? 1 : 0);
        write(0x00);
    }

    public void writeLenUnicodeString(String s) throws IOException {
        write(s.length());
        write(s.getBytes("UTF-16LE"));
    }

    public void writeDoubleLenUnicodeString(String s) throws IOException {
        writeUI16(s.length());
        write(s.getBytes("UTF-16LE"));
    }

    public void writeLenAsciiString(String s) throws IOException {
        byte[] sbytes = s.getBytes("UTF-8");
        write(sbytes.length);
        write(0);
        write(sbytes);
    }

    public void writeEndParentLayer(int parentLayerIndex) throws IOException {
        write(7 + parentLayerIndex);
        write(0);
    }

    public void write(byte[] bytes) throws IOException {
        os.write(bytes);
    }

    public void write(int... values) throws IOException {
        for (int i : values) {
            os.write(i);
        }
    }

    public void writeUI16(int value) throws IOException {
        write(value & 0xFF);
        write((value >> 8) & 0xFF);
    }

    public void writeItemID(String itemID) throws IOException {
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

}
