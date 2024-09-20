package com.jpexs.flash.fla.converter.swatches;

import com.jpexs.flash.fla.converter.GradientEntry;

/**
 *
 * @author JPEXS
 */
public class RadialGradientSwatchItem extends GradientSwatchItem {

    public RadialGradientSwatchItem(GradientEntry... entries) {
        super(entries);
    }

    @Override
    public int getType() {
        return 0x12;
    }

}
