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
public class NoColorEffect implements ColorEffectInterface {

    @Override
    public int getRedMultiplier() {
        return 256;
    }

    @Override
    public int getGreenMultiplier() {
        return 256;
    }

    @Override
    public int getBlueMultiplier() {
        return 256;
    }

    @Override
    public int getAlphaMultiplier() {
        return 256;
    }

    @Override
    public int getRedOffset() {
        return 0;
    }

    @Override
    public int getGreenOffset() {
        return 0;
    }

    @Override
    public int getBlueOffset() {
        return 0;
    }

    @Override
    public int getAlphaOffset() {
        return 0;
    }

    @Override
    public int getType() {
        return 0x00;
    }

    @Override
    public int getValuePercent() {
        return 0;
    }

    @Override
    public Color getValueColor() {
        return new Color(0xFF, 0xFF, 0xFF);
    }

}
