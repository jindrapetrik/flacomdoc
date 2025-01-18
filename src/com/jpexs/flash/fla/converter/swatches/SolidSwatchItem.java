package com.jpexs.flash.fla.converter.swatches;

/**
 *
 * @author JPEXS
 */
public class SolidSwatchItem {

    public int red;
    public int green;
    public int blue;
    public int alpha;

    public int hue;
    public int saturation;
    public int brightness;

    public SolidSwatchItem(int red, int green, int blue, int hue, int saturation, int brightness) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.alpha = 255;
    }
    public SolidSwatchItem(int red, int green, int blue, int alpha, int hue, int saturation, int brightness) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.alpha = alpha;
    }

}
