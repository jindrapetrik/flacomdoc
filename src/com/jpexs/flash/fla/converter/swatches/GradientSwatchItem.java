package com.jpexs.flash.fla.converter.swatches;

import com.jpexs.flash.fla.converter.GradientEntry;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public abstract class GradientSwatchItem extends ExtendedSwatchItem {

    public GradientSwatchItem(GradientEntry... entries) {
        super(entries);
    }

    public GradientSwatchItem(List<GradientEntry> entries, int spreadMethod, boolean interpolationMethodLinearRGB) {
        super(entries, spreadMethod, interpolationMethodLinearRGB);
    }        
}
