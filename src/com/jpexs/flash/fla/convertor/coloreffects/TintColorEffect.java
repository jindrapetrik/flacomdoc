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
package com.jpexs.flash.fla.convertor.coloreffects;

import java.awt.Color;

/**
 *
 * @author JPEXS
 */
public class TintColorEffect implements ColorEffectInterface {

    /*
    <color>
        <Color tintMultiplier="0.5" tintColor="#FFFFFF"/>
    </color>
     */
    private final double tintMultiplier;

    private final Color tintColor;

    public TintColorEffect(double tintMultiplier, Color tintColor) {
        this.tintMultiplier = tintMultiplier;
        this.tintColor = tintColor;
    }

    public int getMultiplier() {
        return (int) Math.round((1 - tintMultiplier) * 256);
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

    @Override
    public int getRedOffset() {
        return (int) Math.round(tintColor.getRed() * tintMultiplier);
    }

    @Override
    public int getGreenOffset() {
        return (int) Math.round(tintColor.getGreen() * tintMultiplier);
    }

    @Override
    public int getBlueOffset() {
        return (int) Math.round(tintColor.getBlue() * tintMultiplier);
    }

    @Override
    public int getAlphaOffset() {
        return 0;
    }

    @Override
    public int getType() {
        return 0x02;
    }

    @Override
    public int getValuePercent() {
        return (int) Math.round(tintMultiplier * 100);
    }

    @Override
    public Color getValueColor() {
        return tintColor;
    }
}
