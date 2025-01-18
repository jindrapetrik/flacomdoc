package com.jpexs.flash.fla.converter.swatches;

import com.jpexs.flash.fla.converter.GradientEntry;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public class LinearGradientSwatchItem extends GradientSwatchItem {

    public LinearGradientSwatchItem(GradientEntry... entries) {
        super(entries);
    }
    
    public LinearGradientSwatchItem(List<GradientEntry> entries, int spreadMethod, boolean interpolationMethodLinearRGB) {
        super(entries, spreadMethod, interpolationMethodLinearRGB);
    }

    @Override
    public int getType() {
        return 0x10;
    }
    /*
     //More types:
     public static final int SOLID = 0x0;

     public static final int LINEAR_GRADIENT = 0x10;

     public static final int RADIAL_GRADIENT = 0x12;

     public static final int FOCAL_RADIAL_GRADIENT = 0x13;

     public static final int REPEATING_BITMAP = 0x40;

     public static final int CLIPPED_BITMAP = 0x41;

     public static final int NON_SMOOTHED_REPEATING_BITMAP = 0x42;

     public static final int NON_SMOOTHED_CLIPPED_BITMAP = 0x43;
     */
}
