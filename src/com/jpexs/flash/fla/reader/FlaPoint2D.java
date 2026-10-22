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

/**
 * A signed archive point with an explicit binary fraction of a twip.
 */
public final class FlaPoint2D {

    private final int x, y, fractionalBits;

    private FlaPoint2D(int x, int y, int fractionalBits) {
        this.x = x;
        this.y = y;
        this.fractionalBits = fractionalBits;
    }

    public static FlaPoint2D fromTwips(int x, int y) {
        return new FlaPoint2D(x, y, 0);
    }

    public static FlaPoint2D fromEdgeWords(int x, int y) {
        return new FlaPoint2D(x, y, 8);
    }

    public int getRawX() {
        return x;
    }

    public int getRawY() {
        return y;
    }

    public int getFractionalBits() {
        return fractionalBits;
    }

    public double getXTwips() {
        return x / (double) (1 << fractionalBits);
    }

    public double getYTwips() {
        return y / (double) (1 << fractionalBits);
    }

    public double getXPixels() {
        return getXTwips() / 20;
    }

    public double getYPixels() {
        return getYTwips() / 20;
    }

    static FlaPoint2D read(FlaRecord r, String field) {
        int[] w = SemanticFields.words(r, field, 2);
        return w == null ? null : fromTwips(w[0], w[1]);
    }
}
