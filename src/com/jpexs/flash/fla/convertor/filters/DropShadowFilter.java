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
package com.jpexs.flash.fla.convertor.filters;

import com.jpexs.flash.fla.convertor.FlaCs4Writer;
import java.awt.Color;
import java.io.IOException;

/**
 *
 * @author JPEXS
 */
public class DropShadowFilter implements FilterInterface {

    private float blurX = 5;
    private float blurY = 5;
    private float strength = 1;
    private int quality = 1; //1 = low, 2 = medium, 3 = high
    private float angle = 45;
    private float distance = 5;
    private boolean knockout = false;
    private boolean inner = false;
    private boolean hideObject = false;
    private Color color = Color.black;
    private boolean enabled = true;

    public DropShadowFilter() {
    }

    public DropShadowFilter(
            float blurX,
            float blurY,
            float strength,
            int quality,
            float angle,
            float distance,
            boolean knockout,
            boolean inner,
            boolean hideObject,
            Color color,
            boolean enabled
    ) {
        this.blurX = blurX;
        this.blurY = blurY;
        this.strength = strength;
        this.quality = quality;
        this.angle = angle;
        this.distance = distance;
        this.knockout = knockout;
        this.inner = inner;
        this.hideObject = hideObject;
        this.color = color;
        this.enabled = enabled;
    }

    public float getBlurX() {
        return blurX;
    }

    public float getBlurY() {
        return blurY;
    }

    public float getStrength() {
        return strength;
    }

    public int getQuality() {
        return quality;
    }

    public float getAngle() {
        return angle;
    }

    public float getDistance() {
        return distance;
    }

    public boolean isKnockout() {
        return knockout;
    }

    public boolean isInner() {
        return inner;
    }

    public boolean isHideObject() {
        return hideObject;
    }

    public Color getColor() {
        return color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void write(FlaCs4Writer os) throws IOException {
        os.write(new byte[]{
            (byte) 0x00,
            (byte) 0x04, (byte) 0x01,
            (byte) (enabled ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(), (byte) color.getAlpha(),});

        os.writeFloat(distance);

        os.writeFloat(blurX);
        os.writeFloat(blurY);

        os.writeFloat((float) (((double) angle) * FilterInterface.PI / 180));

        os.write(new byte[]{
            (byte) (inner ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) (knockout ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) quality, (byte) 0x00, (byte) 0x00, (byte) 0x00,});

        int strengthPercent = (int) Math.round(strength * 100);

        os.write(new byte[]{
            (byte) (strengthPercent & 0xFF), (byte) ((strengthPercent >> 8) & 0xFF), (byte) 0x00, (byte) 0x00,
            (byte) (hideObject ? 1 : 0),
            (byte) 0x00, (byte) 0x00, (byte) 0x00
        });
    }
}
