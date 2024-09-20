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
public class GlowFilter implements FilterInterface {

    private float blurX = 5;
    private float blurY = 5;
    private Color color = Color.red;
    private boolean inner = false;
    private boolean knockout = false;
    private int quality = 1;
    private float strength = 1;
    private boolean enabled = true;

    public GlowFilter() {
    }

    public GlowFilter(
            float blurX,
            float blurY,
            Color color,
            boolean inner,
            boolean knockout,
            int quality,
            float strenght,
            boolean enabled
    ) {
        this.blurX = blurX;
        this.blurY = blurY;
        this.color = color;
        this.inner = inner;
        this.knockout = knockout;
        this.quality = quality;
        this.strength = strenght;
        this.enabled = enabled;
    }

    public float getBlurX() {
        return blurX;
    }

    public float getBlurY() {
        return blurY;
    }

    public Color getColor() {
        return color;
    }

    public boolean isInner() {
        return inner;
    }

    public boolean isKnockout() {
        return knockout;
    }

    public int getQuality() {
        return quality;
    }

    public float getStrenght() {
        return strength;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void write(FlaWriter os) throws IOException {
        os.write(new byte[]{
            (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x01,
            (byte) (enabled ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(), (byte) color.getAlpha(),
            (byte) 0x00, (byte) 0x00, (byte) 0xA0, (byte) 0x40, //5f
        });
        os.writeFloat(blurX);
        os.writeFloat(blurY);

        int strengthPercent = (int) Math.round(strength * 100);
        os.write(new byte[]{
            (byte) 0xDB, (byte) 0x0F, (byte) 0x49, (byte) 0x3F, //45 deg
            (byte) (inner ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) (knockout ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) quality, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) (strengthPercent & 0xFF), (byte) ((strengthPercent >> 8) & 0xFF), (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
        });
    }

}
