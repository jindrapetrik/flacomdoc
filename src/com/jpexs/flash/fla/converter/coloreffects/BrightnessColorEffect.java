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
package com.jpexs.flash.fla.converter.coloreffects;

import java.awt.Color;

/**
 *
 * @author JPEXS
 */
public class BrightnessColorEffect implements ColorEffectInterface {

    /*
    <color>
        <Color brightness="0.28"/>
    </color>
     */
    private final double brightness;

    public BrightnessColorEffect(double brightness) {
        this.brightness = brightness;
    }

    public int getMultiplier() {
        return (int) Math.round((1 - Math.abs(brightness)) * 256);
    }

    @Override
    public int getRedMultiplier() {
        return getMultiplier();
    }

    @Override
    public int getGreenMultiplier() {
        return getMultiplier();
    }

    @Override
    public int getBlueMultiplier() {
        return getMultiplier();
    }

    @Override
    public int getAlphaMultiplier() {
        return 256;
    }

    public int getOffset() {
        int offset = 0;
        if (brightness > 0) {
            offset = (int) Math.round(brightness * 255);
        }
        return offset;
    }

    @Override
    public int getRedOffset() {
        return getOffset();
    }

    @Override
    public int getGreenOffset() {
        return getOffset();
    }

    @Override
    public int getBlueOffset() {
        return getOffset();
    }

    @Override
    public int getAlphaOffset() {
        return 0;
    }

    @Override
    public int getType() {
        return 0x01;
    }

    @Override
    public int getValuePercent() {
        return (int) Math.round(brightness * 100);
    }

    @Override
    public Color getValueColor() {
        return new Color(0, 0, 0);
    }
}
