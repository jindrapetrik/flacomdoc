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
public class AdjustColorFilter implements FilterInterface {

    private float brightness = 0;
    private float contrast = 0;
    private float saturation = 0;
    private float hue = 0;
    private boolean enabled = true;

    public AdjustColorFilter(
            float brightness,
            float contrast,
            float saturation,
            float hue,
            boolean enabled
    ) {
        this.brightness = brightness;
        this.contrast = contrast;
        this.saturation = saturation;
        this.hue = hue;
        this.enabled = enabled;
    }

    public float getBrightness() {
        return brightness;
    }

    public float getContrast() {
        return contrast;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getHue() {
        return hue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void write(FlaCs4Writer os) throws IOException {
        os.write(new byte[]{
            (byte) 0x06,
            (byte) 0x01, (byte) 0x01,
            (byte) (enabled ? 1 : 0), (byte) 0x00, (byte) 0x00, (byte) 0x00,});
        os.writeFloat(brightness);
        os.writeFloat(contrast);
        os.writeFloat(saturation);
        os.writeFloat(hue);
    }
}
