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
 * Immutable geometric bounds in archive order xmin, xmax, ymin, ymax.
 * Coordinates are twips; a sentinel in xmin makes the entire bounds undefined.
 */
public final class FlaBounds {

    private final int[] words;

    private FlaBounds(int[] words) {
        this.words = words.clone();
    }

    public static FlaBounds fromWords(int... words) {
        if (words == null || words.length != 4) {
            throw new IllegalArgumentException("Bounds require four signed words");
        }
        return new FlaBounds(words);
    }

    public int[] getWords() {
        return words.clone();
    }

    public boolean isDefined() {
        return words[0] != Integer.MIN_VALUE;
    }

    public int getXMinTwips() {
        return words[0];
    }

    public int getXMaxTwips() {
        return words[1];
    }

    public int getYMinTwips() {
        return words[2];
    }

    public int getYMaxTwips() {
        return words[3];
    }

    public Double getXMinPixels() {
        return isDefined() ? words[0] / 20.0 : null;
    }

    public Double getXMaxPixels() {
        return isDefined() ? words[1] / 20.0 : null;
    }

    public Double getYMinPixels() {
        return isDefined() ? words[2] / 20.0 : null;
    }

    public Double getYMaxPixels() {
        return isDefined() ? words[3] / 20.0 : null;
    }

    /**
     * Null for undefined bounds; inverted stored ranges are not sorted or
     * clamped.
     */
    public Long getWidthTwips() {
        return isDefined() ? (long) words[1] - words[0] : null;
    }

    public Long getHeightTwips() {
        return isDefined() ? (long) words[3] - words[2] : null;
    }

    public Double getWidthPixels() {
        return isDefined() ? getWidthTwips() / 20.0 : null;
    }

    public Double getHeightPixels() {
        return isDefined() ? getHeightTwips() / 20.0 : null;
    }
}
