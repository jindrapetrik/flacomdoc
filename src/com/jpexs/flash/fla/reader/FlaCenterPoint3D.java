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
 * Immutable stored 3D center in twips. A sentinel in x leaves the whole center
 * unspecified, including any separately retained y/z words.
 */
public final class FlaCenterPoint3D {

    private final int[] words;

    private FlaCenterPoint3D(int[] words) {
        this.words = words.clone();
    }

    public static FlaCenterPoint3D fromWords(int... words) {
        if (words == null || words.length != 3) {
            throw new IllegalArgumentException("A 3D center requires three signed words");
        }
        return new FlaCenterPoint3D(words);
    }

    public int[] getWords() {
        return words.clone();
    }

    public boolean isDefined() {
        return words[0] != Integer.MIN_VALUE;
    }

    public int getXTwips() {
        return words[0];
    }

    public int getYTwips() {
        return words[1];
    }

    public int getZTwips() {
        return words[2];
    }

    public Double getXPixels() {
        return isDefined() ? words[0] / 20.0 : null;
    }

    public Double getYPixels() {
        return isDefined() ? words[1] / 20.0 : null;
    }

    public Double getZPixels() {
        return isDefined() ? words[2] / 20.0 : null;
    }
}
