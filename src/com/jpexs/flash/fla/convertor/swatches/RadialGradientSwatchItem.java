package com.jpexs.flash.fla.convertor.swatches;

import com.jpexs.flash.fla.convertor.GradientEntry;

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
