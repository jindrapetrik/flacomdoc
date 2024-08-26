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
public class AdvancedColorEffect implements ColorEffectInterface {

    /*
    <color>
        <Color alphaMultiplier="0.8984375" redMultiplier="0.80078125" blueMultiplier="0.6015625" greenMultiplier="0.69921875" alphaOffset="17" redOffset="34" blueOffset="68" greenOffset="51"/>
    </color>
     */
    private final double alphaMultiplier;
    private final double redMultiplier;
    private final double greenMultiplier;
    private final double blueMultiplier;
    private final int alphaOffset;
    private final int redOffset;
    private final int greenOffset;
    private final int blueOffset;

    public AdvancedColorEffect(
            double alphaMultiplier,
            double redMultiplier,
            double greenMultiplier,
            double blueMultiplier,
            int alphaOffset,
            int redOffset,
            int greenOffset,
            int blueOffset
    ) {
        this.alphaMultiplier = alphaMultiplier;
        this.redMultiplier = redMultiplier;
        this.greenMultiplier = greenMultiplier;
        this.blueMultiplier = blueMultiplier;
        this.alphaOffset = alphaOffset;
        this.redOffset = redOffset;
        this.greenOffset = greenOffset;
        this.blueOffset = blueOffset;

    }

    @Override
    public int getRedMultiplier() {
        return (int) Math.round(redMultiplier * 256);
    }

    @Override
    public int getGreenMultiplier() {
        return (int) Math.round(greenMultiplier * 256);
    }

    @Override
    public int getBlueMultiplier() {
        return (int) Math.round(blueMultiplier * 256);
    }

    @Override
    public int getAlphaMultiplier() {
        return (int) Math.round(alphaMultiplier * 256);
    }

    @Override
    public int getRedOffset() {
        return redOffset;
    }

    @Override
    public int getGreenOffset() {
        return greenOffset;
    }

    @Override
    public int getBlueOffset() {
        return blueOffset;
    }

    @Override
    public int getAlphaOffset() {
        return alphaOffset;
    }

    @Override
    public int getType() {
        return 0x03;
    }

    @Override
    public int getValuePercent() {
        return 0x64; //??
    }

    @Override
    public Color getValueColor() {
        return new Color(0, 0, 0);
    }
}
