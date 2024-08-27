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

    public static int SCALE_MODE_NORMAL = 0;
    public static int SCALE_MODE_HORIZONTAL = 1;
    public static int SCALE_MODE_VERTICAL = 2;
    public static int SCALE_MODE_NONE = 3;

    public static int CAP_STYLE_NONE = 0;
    public static int CAP_STYLE_ROUND = 1;
    public static int CAP_STYLE_SQUARE = 2;

    public static int JOIN_STYLE_MITER = 0;
    public static int JOIN_STYLE_ROUND = 1;
    public static int JOIN_STYLE_BEVEL = 2;

    public static final int FLOW_EXTEND = 0;
    public static final int FLOW_REFLECT = 4;
    public static final int FLOW_REPEAT = 8;

    public static final int TYPE_LINEAR_GRADIENT = 0x10;
    public static final int TYPE_RADIAL_GRADIENT = 0x12;

    public static final int TYPE_BITMAP = 0x40;
    public static final int TYPE_CLIPPED_BITMAP = 0x41;
    public static final int TYPE_NON_SMOOTHED_BITMAP = 0x42;
    public static final int TYPE_NON_SMOOTHED_CLIPPED_BITMAP = 0x43;

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

    public void writePageHeader() throws IOException {
        os.write(new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, 0x01, 0x00});
        writeLenAsciiString("CPicPage");
        os.write(new byte[]{0x05, 0x00, (byte) 0xFF, (byte) 0xFF, 0x01, 0x00});
        writeLenAsciiString("CPicLayer");
        os.write(new byte[]{0x05, 0x00, (byte) 0xFF, (byte) 0xFF, 0x01, 0x00});
        writeLenAsciiString("CPicFrame");
        os.write(new byte[]{0x05, 0x00, (byte) 0xFF, (byte) 0xFF, 0x01, 0x00});
        writeLenAsciiString("CPicSprite");
        //this seems to be some kind of list of required classes,
        //each "require" is placed on the place where it is first used
        //like when you use SymbolInstance in frame 2 and not in frame 1, then it is required later
    }

    /**
     *
     * @param nextLayerId 1-based
     * @param nextFolderId 1-based
     * @param activeFrame 0-based
     * @throws IOException
     */
    public void writePageFooter(int nextLayerId, int nextFolderId, int activeFrame) throws IOException {
        os.write(new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x00, 0x00, 0x00,
            (byte) 0x80, 0x00, 0x00, 0x07, (byte) nextLayerId, 0x00, (byte) nextFolderId, 0x00, (byte) activeFrame, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,});
    }

    public void writeLayerSeparator() throws IOException {
        os.write(new byte[]{0x03, (byte) 0x80, 0x05, 0x00, 0x05, (byte) 0x80});
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
                true);
    }

    public void writeKeyFrameSeparator() throws IOException {
        os.write(new byte[]{0x05, (byte) 0x80});
    }

    public void writeSymbolInstanceSeparator() throws IOException {
        os.write(new byte[]{0x07, (byte) 0x80, 0x05, 0x00});
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
            int index,
            int totalCount
    ) throws IOException {

        Random rnd = new Random();
        int symbolInstanceId = rnd.nextInt(0x10000);

        long centerPoint3DXLong = Math.round(centerPoint3DX * 20);
        long centerPoint3DYLong = Math.round(centerPoint3DY * 20);

        Point2D transformationPoint = new Point2D.Double(transformationPointX, transformationPointY);
        Point2D transformationPointTransformed = placeMatrix.transform(transformationPoint);

        long tptX = Math.round(transformationPointTransformed.getX() * 20);
        long tptY = Math.round(transformationPointTransformed.getY() * 20);
        /*
        keyframe begin:
        0x00, 0x00, 
        0x00, 0x00, 0x00, (byte) 0x80,
        0x00, 0x00, 0x00, (byte) 0x80, 
        0x00, 0x00, 0x06,  - is this some kind of type identifier?
        
        ...
         */
        os.write(new byte[]{
            0x00, 0x00,
            (byte) (tptX & 0xFF), (byte) ((tptX >> 8) & 0xFF), (byte) ((tptX >> 16) & 0xFF), (byte) ((tptX >> 24) & 0xFF),
            (byte) (tptY & 0xFF), (byte) ((tptY >> 8) & 0xFF), (byte) ((tptY >> 16) & 0xFF), (byte) ((tptY >> 24) & 0xFF),
            0x00, 0x00, 0x16
        });
        writeMatrix(placeMatrix);

        os.write(new byte[]{0x00, 0x00, 0x02, 0x00, 0x01,});

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

        os.write(new byte[]{
            (byte) (alphaMultiplier & 0xFF), (byte) ((alphaMultiplier >> 8) & 0xFF), (byte) (alphaOffset & 0xFF), (byte) ((alphaOffset >> 8) & 0xFF),
            (byte) (redMultiplier & 0xFF), (byte) ((redMultiplier >> 8) & 0xFF), (byte) (redOffset & 0xFF), (byte) ((redOffset >> 8) & 0xFF),
            (byte) (greenMultiplier & 0xFF), (byte) ((greenMultiplier >> 8) & 0xFF), (byte) (greenOffset & 0xFF), (byte) ((greenOffset >> 8) & 0xFF),
            (byte) (blueMultiplier & 0xFF), (byte) ((blueMultiplier >> 8) & 0xFF), (byte) (blueOffset & 0xFF), (byte) ((blueOffset >> 8) & 0xFF),
            (byte) colorEffect.getType(), (byte) 0x00, (byte) colorEffect.getValuePercent(), (byte) 0x00,
            (byte) effectColor.getRed(), (byte) effectColor.getGreen(), (byte) effectColor.getBlue(), (byte) effectColor.getAlpha()
        });

        os.write(new byte[]{
            (byte) 0xFF, (byte) 0xFE, (byte) 0xFF, 0x00, //some string
            (byte) librarySymbolId, 0x00, 0x00, 0x00, //this is probably a long val
            0x00, 0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x3F, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            (byte) 0x80, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,});

        os.write(new byte[]{
            (byte) (centerPoint3DXLong & 0xFF), (byte) ((centerPoint3DXLong >> 8) & 0xFF), (byte) ((centerPoint3DXLong >> 16) & 0xFF), (byte) ((centerPoint3DXLong >> 24) & 0xFF),
            (byte) (centerPoint3DYLong & 0xFF), (byte) ((centerPoint3DYLong >> 8) & 0xFF), (byte) ((centerPoint3DYLong >> 16) & 0xFF), (byte) ((centerPoint3DYLong >> 24) & 0xFF)
        });

        os.write(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08, 0x05, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x00,
            (byte) (symbolInstanceId & 0xFF), (byte) ((symbolInstanceId >> 8) & 0xFF),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xFE, (byte) 0xFF, 0x00, //some string
            (byte) 0xFF, (byte) 0xFE, (byte) 0xFF});

        writeLenUnicodeString(instanceName);

        os.write(new byte[]{0x02, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
            (byte) index, 0x00, 0x00, 0x00, //this should probably be long value
            (byte) 0xFF, (byte) 0xFE, (byte) 0xFF}
        );
        String componentTxt = "<component metaDataFetched='true' schemaUrl='' schemaOperation='' sceneRootLabel='Scene 1' oldCopiedComponentPath='" + (totalCount - index) + "'>\n</component>\n";
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
        os.write(type);
        if (stylesChanged) {
            logger.log(Level.FINE, "writing style 0x{0} 0x{1} 0x{2}", new Object[]{String.format("%02X", strokeStyle), String.format("%02X", fillStyle0), String.format("%02X", fillStyle1)});
            os.write(strokeStyle);
            os.write(fillStyle0);
            os.write(fillStyle1);
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
            os.write(0x00);
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
            os.write(fractX);
            os.write((byte) integerX);
            os.write(fractY);
            os.write((byte) integerY);
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

            os.write(integerX & 0xFF);
            os.write((integerX >> 8) & 0xFF);

            os.write(integerY & 0xFF);
            os.write((integerY >> 8) & 0xFF);

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

        os.write(fractX);
        os.write(integerX & 0xFF);
        os.write((integerX >> 8) & 0xFF);
        os.write((integerX >> 16) & 0xFF);

        os.write(fractY);
        os.write(integerY & 0xFF);
        os.write((integerY >> 8) & 0xFF);
        os.write((integerY >> 16) & 0xFF);
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

        os.write(new byte[]{
            (byte) (aLong & 0xFF), (byte) ((aLong >> 8) & 0xFF), (byte) ((aLong >> 16) & 0xFF), (byte) ((aLong >> 24) & 0xFF),
            (byte) (bLong & 0xFF), (byte) ((bLong >> 8) & 0xFF), (byte) ((bLong >> 16) & 0xFF), (byte) ((bLong >> 24) & 0xFF),
            (byte) (cLong & 0xFF), (byte) ((cLong >> 8) & 0xFF), (byte) ((cLong >> 16) & 0xFF), (byte) ((cLong >> 24) & 0xFF),
            (byte) (dLong & 0xFF), (byte) ((dLong >> 8) & 0xFF), (byte) ((dLong >> 16) & 0xFF), (byte) ((dLong >> 24) & 0xFF),
            (byte) (txLong & 0xFF), (byte) ((txLong >> 8) & 0xFF), (byte) ((txLong >> 16) & 0xFF), (byte) ((txLong >> 24) & 0xFF),
            (byte) (tyLong & 0xFF), (byte) ((tyLong >> 8) & 0xFF), (byte) ((tyLong >> 16) & 0xFF), (byte) ((tyLong >> 24) & 0xFF),});
    }

    public void writeBitmapFill(
            int type,
            Matrix bitmapMatrix,
            int bitmapId
    ) throws IOException {
        os.write(new byte[]{
            (byte) 0xFF, 0x00, 0x00, (byte) 0xFF,
            (byte) type,
            0x00});
        writeMatrix(bitmapMatrix);
        os.write(new byte[]{
            (byte) bitmapId, 0x00
        });
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

        os.write(new byte[]{
            0x00, 0x00, 0x00, 0x00 /*this is sometimes 0xFF*/, (byte) type, 0x00});
        writeMatrix(gradientMatrix);
        os.write(new byte[]{
            (byte) colors.length,
            (byte) Math.round(focalRatio * 256), 0x00, 0x00, 0x00, (byte) (flow + (linearRgb ? 1 : 0)), 0x00, 0x00, 0x00
        });
        for (int i = 0; i < colors.length; i++) {
            os.write(new byte[]{(byte) Math.round(stopPos[i] * 255), (byte) colors[i].getRed(), (byte) colors[i].getGreen(), (byte) colors[i].getBlue(), (byte) colors[i].getAlpha()});
        }
    }

    public void writeSolidFill(Color fillColor) throws IOException {
        os.write(new byte[]{
            (byte) fillColor.getRed(), (byte) fillColor.getGreen(), (byte) fillColor.getBlue(), (byte) fillColor.getAlpha()});
        os.write(0x00);
        os.write(0x00);
    }

    public void writeSolidStroke(
            Color lineColor,
            int strokeWidthTwips
    ) throws IOException {
        writeSolidStroke(
                lineColor,
                strokeWidthTwips,
                false,
                SCALE_MODE_NORMAL,
                CAP_STYLE_ROUND,
                JOIN_STYLE_ROUND,
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
        os.write(new byte[]{
            (byte) lineColor.getRed(), (byte) lineColor.getGreen(), (byte) lineColor.getBlue(), (byte) lineColor.getAlpha(),
            (byte) (strokeWidthTwips & 0xFF), (byte) ((strokeWidthTwips >> 8) & 0xFF),
            (byte) (styleParam1 & 0xFF), (byte) ((styleParam1 >> 8) & 0xFF),
            (byte) (styleParam2 & 0xFF), (byte) ((styleParam2 >> 8) & 0xFF),
            (byte) (pixelHinting ? 1 : 0), (byte) scaleMode, (byte) capStyle,
            (byte) joinStyle, (byte) ((miter - Math.floor(miter)) * 256), (byte) Math.floor(miter)});
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

    public void writeKeyFrameBegin() throws IOException {
        os.write(new byte[]{
            0x05, 0x00,});
    }

    public void writeKeyFrameMiddle() throws IOException {
        os.write(new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x00, 0x00, 0x00, (byte) 0x80, 0x00, 0x00, 0x06, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05});
    }

    public void writeKeyFrameEnd(int duration, int keyMode) throws IOException {
        Random rnd = new Random();
        int frameId = rnd.nextInt(0x10000);

        os.write(new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x1D, (byte) duration,
            0x00, (byte) (keyMode & 0xFF), (byte) ((keyMode >> 8) & 0xFF), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, 0x3F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFE, (byte) 0xFF, 0x00, 0x05, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x00, (byte) ((frameId >> 8) & 0xFF), (byte) (frameId & 0xFF),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFE, (byte) 0xFF,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFE, (byte) 0xFF, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,});
    }

    public void writeKeyFrame(int frameLen, int keyMode) throws IOException {

        writeKeyFrameBegin();
        writeKeyFrameMiddle();
        int numEdges = 0;
        int numFillStyles = 0;
        int numStrokeStyles = 0;

        os.write(new byte[]{(byte) numEdges, 0x00, 0x00, 0x00});
        os.write(new byte[]{(byte) numFillStyles, 0x00});
        //place fillstyles here        
        os.write(new byte[]{(byte) numStrokeStyles, 0x00});
        //place stroke styles here
        writeKeyFrameEnd(frameLen, keyMode);
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
            boolean originalLayerName
    ) throws IOException {

        /*writeKeyFrame(1, KEYMODE_STANDARD);
        writeKeyFrameSeparator();
        writeKeyFrame(1, KEYMODE_STANDARD);
        writeKeyFrameSeparator();
        writeKeyFrame(1, KEYMODE_STANDARD);*/
        writeKeyFrame(1, KEYMODE_STANDARD);
        writeLayerEnd(layerName, selectedLayer, hiddenLayer, lockedLayer, layerColor, showOutlines, originalLayerName);

    }

    public void writeLayerEnd(
            String layerName,
            boolean selectedLayer,
            boolean hiddenLayer,
            boolean lockedLayer,
            Color layerColor,
            boolean showOutlines,
            boolean originalLayerName
    ) throws IOException {
        os.write(new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x00, 0x00, 0x00, (byte) 0x80,
            0x00, 0x00, 0x0D, (byte) 0xFF, (byte) 0xFE, (byte) 0xFF
        });

        writeLenUnicodeString(layerName);
        os.write(selectedLayer ? 1 : 0);
        os.write(hiddenLayer ? 1 : 0);
        os.write(lockedLayer ? 1 : 0);
        os.write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        os.write(layerColor.getRed());
        os.write(layerColor.getGreen());
        os.write(layerColor.getBlue());
        os.write(0xFF);
        os.write(showOutlines ? 1 : 0);
        os.write(new byte[]{0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00});
        os.write(0x00);
    }

    public void writeLayerEnd2(int parentLayerIndex) throws IOException {
        if (parentLayerIndex > -1) {
            os.write(7 + parentLayerIndex);
        } else {
            os.write(0x00);
        }
        os.write(0x00);
        os.write(new byte[]{0x01});
        //os.write(originalLayerName ? 1 : 0);
        os.write(0x01);
        os.write(0x00);
    }

    public void writeLenUnicodeString(String s) throws IOException {
        os.write(s.length());
        os.write(s.getBytes("UTF-16LE"));
    }

    public void writeLenAsciiString(String s) throws IOException {
        byte[] sbytes = s.getBytes("UTF-8");
        os.write(sbytes.length);
        os.write(0);
        os.write(sbytes);
    }

    public void createBasicEmptyFolder(int folderIndex) throws IOException {
        createBasicEmptyFolder(folderIndex, -1);
    }

    public void createBasicEmptyFolder(int folderIndex, int parentLayerIndex) throws IOException {
        Random rnd = new Random();
        createFolder(
                "Folder " + folderIndex,
                false,
                false,
                false,
                new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)),
                false,
                true,
                true,
                parentLayerIndex
        );
    }

    public void createBasicFolder(int folderIndex) throws IOException {
        createBasicFolder(folderIndex, -1);
    }

    public void createBasicFolder(int folderIndex, int parentLayerIndex) throws IOException {
        Random rnd = new Random();
        createFolder(
                "Folder " + folderIndex,
                false,
                false,
                false,
                new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)),
                false,
                true,
                false,
                parentLayerIndex
        );
    }

    public void createFolder(
            String folderName,
            boolean selectedLayer,
            boolean hiddenLayer,
            boolean lockedLayer,
            Color layerColor,
            boolean showOutlines,
            boolean expanded,
            boolean empty,
            int parentLayerIndex
    ) throws IOException {
        //0x03, (byte) 0x80, 0x05, 0x00,
        os.write(new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x00, 0x00, 0x00, (byte) 0x80, 0x00,
            0x00, 0x0D, (byte) 0xFF, (byte) 0xFE, (byte) 0xFF
        });
        writeLenUnicodeString(folderName);
        os.write(selectedLayer ? 1 : 0);
        os.write(hiddenLayer ? 1 : 0);
        os.write(lockedLayer ? 1 : 0);
        os.write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        os.write(layerColor.getRed());
        os.write(layerColor.getGreen());
        os.write(layerColor.getBlue());
        os.write(0xFF);
        os.write(showOutlines ? 1 : 0);

        os.write(new byte[]{0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x03});
        if (parentLayerIndex > -1) {
            os.write(7 + parentLayerIndex);
        } else {
            os.write(0x00);
        }
        os.write(0x00);
        os.write(expanded ? 1 : 0);
        os.write(0x01);

        os.write(0x00);
        if (!empty) {
            os.write(0x01);
            os.write(0x01);
            os.write(0x00);
        }
    }

    public void writeEndParentLayer(int parentLayerIndex) throws IOException {
        os.write(7 + parentLayerIndex);
        os.write(0);
    }

    public void write(byte[] bytes) throws IOException {
        os.write(bytes);
    }

}
