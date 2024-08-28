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
import java.io.IOException;

/**
 *
 * @author JPEXS
 */
public class BlurFilter implements FilterInterface {

    private float blurX = 5;
    private float blurY = 5;
    private int quality = 1; //1 = low, 2 = medium, 3 = high
    private boolean enabled = true;

    public BlurFilter() {
    }

    public BlurFilter(float blurX, float blurY, int quality, boolean enabled) {
        this.blurX = blurX;
        this.blurY = blurY;
        this.quality = quality;
        this.enabled = enabled;
    }

    public float getBlurX() {
        return blurX;
    }

    public float getBlurY() {
        return blurY;
    }

    public int getQuality() {
        return quality;
    }

    @Override
    public void write(FlaCs4Writer os) throws IOException {
        os.write(new byte[]{
            (byte) 0x01, (byte) 0x03,
            (byte) 0x04, (byte) 0x01,
            (byte) (enabled ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0xA0, (byte) 0x40, //5f
        });
        os.writeFloat(blurX);
        os.writeFloat(blurY);
        os.write(new byte[]{
            (byte) 0xDB, (byte) 0x0F, (byte) 0x49, (byte) 0x3F, //45 deg
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) quality, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x64, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00
        });

    }
}
