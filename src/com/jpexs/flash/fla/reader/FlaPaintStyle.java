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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paint classification, without applying a renderer's texture normalization.
 */
public final class FlaPaintStyle {

    public enum Kind {
        SOLID, LINEAR_GRADIENT, RADIAL_GRADIENT, GRADIENT_UNKNOWN, EXTENDED, BITMAP
    }

    public enum Spread {
        EXTEND, REFLECT, REPEAT
    }

    public static final class Stop {

        private final int ratio;
        private final long rgba;

        private Stop(int ratio, long rgba) {
            this.ratio = ratio;
            this.rgba = rgba;
        }

        public int getRatioByte() {
            return ratio;
        }

        public double getPosition() {
            return ratio / 255.0;
        }

        public long getRgbaWord() {
            return rgba;
        }

        public int getRed() {
            return (int) rgba & 255;
        }

        public int getGreen() {
            return (int) (rgba >>> 8) & 255;
        }

        public int getBlue() {
            return (int) (rgba >>> 16) & 255;
        }

        public double getAlpha() {
            return (rgba >>> 24) / 255.0;
        }
    }
    private final Kind kind;
    private final int flags, secondaryFlags;
    private final long rgba;
    private final FlaMatrix matrix;
    private final Long gradientFlags;
    private final Integer focalPoint, bitmapMediaId;
    private final List<Stop> stops;

    private FlaPaintStyle(FlaRecord r, int flags, int secondaryFlags, long rgba) {
        this.flags = flags;
        this.secondaryFlags = secondaryFlags;
        this.rgba = rgba;
        kind = (flags & 0x10) != 0 ? flags == 0x10 ? Kind.LINEAR_GRADIENT : flags == 0x12 || flags == 0x13 ? Kind.RADIAL_GRADIENT : Kind.GRADIENT_UNKNOWN
                : (flags & 0x20) != 0 ? Kind.EXTENDED : (flags & 0x40) != 0 ? Kind.BITMAP : Kind.SOLID;
        matrix = r.getMatrix();
        gradientFlags = SemanticFields.unsigned(r, "gradientFlags");
        focalPoint = SemanticFields.integer(r, "focalPoint");
        bitmapMediaId = SemanticFields.integer(r, "bitmapMediaId");
        List<Stop> parsed = new ArrayList<>();
        List<FlaRecord> records = SemanticFields.records(r, "stops");
        boolean valid = records != null;
        if (valid) {
            for (FlaRecord stop : records) {
                Integer ratio = SemanticFields.integer(stop, "ratio");
                Long color = SemanticFields.unsigned(stop, "colorRgba");
                if (ratio == null || ratio < 0 || ratio > 255 || color == null) {
                    valid = false;
                    break;
                }
                parsed.add(new Stop(ratio, color));
            }
        }
        stops = valid ? Collections.unmodifiableList(parsed) : null;
    }

    public static FlaPaintStyle fromRecord(FlaRecord r) {
        Integer f = SemanticFields.integer(r, "flags"), s = SemanticFields.integer(r, "secondaryFlags");
        Long c = SemanticFields.unsigned(r, "packedValue");
        return f == null || s == null || c == null || f < 0 || f > 255 || s < 0 || s > 255 ? null : new FlaPaintStyle(r, f, s, c);
    }

    public Kind getKind() {
        return kind;
    }

    public int getFlags() {
        return flags;
    }

    public int getSecondaryFlags() {
        return secondaryFlags;
    }

    public long getRgbaWord() {
        return rgba;
    }

    public FlaMatrix getMatrix() {
        return matrix;
    }

    public Integer getBitmapMediaId() {
        return bitmapMediaId;
    }

    public Boolean isBitmapClipped() {
        return kind == Kind.BITMAP ? (flags & 1) != 0 : null;
    }

    public Boolean isBitmapSmoothed() {
        return kind == Kind.BITMAP ? (flags & 2) == 0 : null;
    }

    /**
     * Null when this revision has no stored gradient extension.
     */
    public Long getGradientFlags() {
        return gradientFlags;
    }

    public Spread getSpread() {
        return gradientFlags == null ? null : (gradientFlags & 4) != 0 ? Spread.REFLECT : (gradientFlags & 8) != 0 ? Spread.REPEAT : Spread.EXTEND;
    }

    public Boolean isLinearRgb() {
        return gradientFlags == null ? null : (gradientFlags & 1) != 0;
    }

    public Integer getStoredFocalPoint() {
        return focalPoint;
    }

    /**
     * Native radial export uses the signed low 16 bits of the stored dword.
     */
    public Double getFocalRatio() {
        return focalPoint == null ? null : (short) (int) focalPoint / 255.0;
    }

    /**
     * Null for an unavailable or malformed list; empty is a decoded empty list.
     */
    public List<Stop> getStops() {
        return stops;
    }
}
