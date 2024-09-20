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
package com.jpexs.flash.fla.converter.filters;

import com.jpexs.flash.fla.converter.FlaWriter;
import java.awt.Color;
import java.io.IOException;

/**
 *
 * @author JPEXS
 */
public class BevelFilter implements FilterInterface {

    private float blurX = 5;
    private float blurY = 5;
    private float strength = 1;
    private int quality = 1;
    private Color shadowColor = Color.black;
    private Color highlightColor = Color.white;
    private float angle = 45;
    private float distance = 5;
    private boolean knockout = false;
    private int type = TYPE_INNER;
    private boolean enabled = true;

    public static final int TYPE_INNER = 1;
    public static final int TYPE_OUTER = 2;
    public static final int TYPE_FULL = 3;

    public BevelFilter(
            float blurX,
            float blurY,
            float strength,
            int quality,
            Color shadowColor,
            Color highlightColor,
            float angle,
            float distance,
            boolean knockout,
            int type,
            boolean enabled
    ) {
        this.blurX = blurX;
        this.blurY = blurY;
        this.strength = strength;
        this.quality = quality;
        this.shadowColor = shadowColor;
        this.highlightColor = highlightColor;
        this.angle = angle;
        this.distance = distance;
        this.knockout = knockout;
        this.type = type;
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

    public Color getShadowColor() {
        return shadowColor;
    }

    public Color getHighlightColor() {
        return highlightColor;
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

    public int getType() {
        return type;
    }

    @Override
    public void write(FlaWriter os) throws IOException {
        os.write(new byte[]{
            (byte) 0x03, (byte) 0x03,
            (byte) 0x04, (byte) 0x01,
            (byte) (enabled ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) shadowColor.getRed(), (byte) shadowColor.getGreen(), (byte) shadowColor.getBlue(), (byte) shadowColor.getAlpha(),});
        os.writeFloat(distance);
        os.writeFloat(blurX);
        os.writeFloat(blurY);
        os.writeFloat((float) (((double) angle) * FilterInterface.PI / 180));

        int strengthPercent = (int) Math.round(strength * 100);
        os.write(new byte[]{
            (byte) (type == TYPE_INNER ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) (knockout ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) quality, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) (strengthPercent & 0xFF), (byte) ((strengthPercent >> 8) & 0xFF), (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) highlightColor.getRed(), (byte) highlightColor.getGreen(), (byte) highlightColor.getBlue(), (byte) highlightColor.getAlpha(),
            (byte) (type == TYPE_FULL ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00
        });
    }

}
