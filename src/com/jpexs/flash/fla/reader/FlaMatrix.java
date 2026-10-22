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
 * Immutable affine transform: signed 16.16 coefficients and twip translations.
 */
public final class FlaMatrix {

    private final int[] words;

    private FlaMatrix(int[] words) {
        this.words = words.clone();
    }

    /**
     * Archive order is a, b, c, d, tx, ty; no native integer rounding is
     * applied.
     */
    public static FlaMatrix fromWords(int... words) {
        if (words == null || words.length != 6) {
            throw new IllegalArgumentException("A matrix requires six signed words");
        }
        return new FlaMatrix(words);
    }

    public int[] getWords() {
        return words.clone();
    }

    public double getA() {
        return words[0] / 65536.0;
    }

    public double getB() {
        return words[1] / 65536.0;
    }

    public double getC() {
        return words[2] / 65536.0;
    }

    public double getD() {
        return words[3] / 65536.0;
    }

    public int getTxTwips() {
        return words[4];
    }

    public int getTyTwips() {
        return words[5];
    }

    public double getTxPixels() {
        return words[4] / 20.0;
    }

    public double getTyPixels() {
        return words[5] / 20.0;
    }

    /**
     * Mathematical pixel-coordinate transform; returns a new {x, y} array.
     */
    public double[] transformPixels(double x, double y) {
        return new double[]{getA() * x + getC() * y + getTxPixels(),
            getB() * x + getD() * y + getTyPixels()};
    }
}
