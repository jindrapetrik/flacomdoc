/*
 * Copyright (C) 2026 JPEXS.
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
package com.jpexs.flash.fla.reader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable interpretation of the packed procedural stroke descriptor.
 */
public final class FlaStrokeStyle {

    public enum Kind {
        SOLID, DASHED, DOTTED, RAGGED, STIPPLE, HATCHED, UNKNOWN
    }
    private final int first, second, width;
    private final Kind kind;
    private final Map<String, Integer> parameters;
    private final Double miterLimit;
    private final Integer pixelHinting, scaleMode, capStyle, joinStyle;

    private FlaStrokeStyle(int first, int second, int width, Double miterLimit, FlaRecord record) {
        this.first = first;
        this.second = second;
        this.width = width;
        this.miterLimit = miterLimit;
        pixelHinting = SemanticFields.integer(record, "pixelHinting");
        scaleMode = SemanticFields.integer(record, "scaleMode");
        capStyle = SemanticFields.integer(record, "capStyle");
        joinStyle = SemanticFields.integer(record, "joinStyle");
        int type = first != 0 ? 1 : second & 7;
        kind = type < 6 ? Kind.values()[type] : Kind.UNKNOWN;
        Map<String, Integer> p = new LinkedHashMap<>();
        switch (type) {
            case 1:
                if (first != 0) {
                    p.put("dashLengthTwips", first);
                    p.put("gapLengthTwips", second & 0x7fff);
                }
                break;
            // The native dotted branch shifts the entire unsigned word, including bit 15.
            case 2:
                p.put("dotSpacingTwips", second >>> 3);
                break;
            case 3:
                p.put("pattern", second >>> 3 & 7);
                p.put("waveHeight", second >>> 6 & 3);
                p.put("waveLength", second >>> 8 & 3);
                break;
            case 4:
                p.put("dotSize", second >>> 3 & 3);
                p.put("variation", second >>> 5 & 3);
                p.put("density", second >>> 7 & 3);
                break;
            case 5:
                p.put("hatchThickness", second >>> 3 & 3);
                p.put("space", second >>> 5 & 3);
                p.put("rotate", second >>> 7 & 3);
                p.put("jiggle", second >>> 9 & 3);
                p.put("curve", second >>> 11 & 3);
                p.put("length", second >>> 13 & 3);
                break;
            default:
                break;
        }
        parameters = Collections.unmodifiableMap(p);
    }

    public static FlaStrokeStyle fromRecord(FlaRecord r) {
        Integer a = SemanticFields.integer(r, "styleWord1"), b = SemanticFields.integer(r, "styleWord2"), w = SemanticFields.integer(r, "width");
        if (a == null || b == null || w == null || (a | b | w) < 0 || a > 65535 || b > 65535 || w > 65535) {
            return null;
        }
        Integer m = SemanticFields.integer(r, "miterLimitWord");
        return new FlaStrokeStyle(a, b, w, m == null ? null : m / 256.0, r);
    }

    public Kind getKind() {
        return kind;
    }

    public int getTypeId() {
        return first != 0 ? 1 : second & 7;
    }

    public int getFirstWord() {
        return first;
    }

    public int getSecondWord() {
        return second;
    }

    public int getWidthTwips() {
        return width;
    }

    public double getWidthPixels() {
        return width / 20.0;
    }

    public boolean hasSharpCorners() {
        return (second & 0x8000) != 0;
    }

    /**
     * Categorical values are indices, not distances. Unknown codes remain
     * available.
     */
    public Map<String, Integer> getParameters() {
        return parameters;
    }

    public Integer getPixelHintingFlag() {
        return pixelHinting;
    }

    public Integer getScaleModeId() {
        return scaleMode;
    }

    public Integer getCapStyleId() {
        return capStyle;
    }

    public Integer getJoinStyleId() {
        return joinStyle;
    }

    public Double getMiterLimit() {
        return miterLimit;
    }
}
