package com.jpexs.flash.fla.converter.swatches;

import com.jpexs.flash.fla.converter.GradientEntry;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public class RadialGradientSwatchItem extends GradientSwatchItem {

    public RadialGradientSwatchItem(GradientEntry... entries) {
        super(entries);
    }

    public RadialGradientSwatchItem(List<GradientEntry> entries, int spreadMethod, boolean interpolationMethodLinearRGB) {
        super(entries, spreadMethod, interpolationMethodLinearRGB);
    }  
    
    @Override
    public int getType() {
        return 0x12;
    }

}
