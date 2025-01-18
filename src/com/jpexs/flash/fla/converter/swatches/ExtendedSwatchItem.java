package com.jpexs.flash.fla.converter.swatches;

import com.jpexs.flash.fla.converter.GradientEntry;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public abstract class ExtendedSwatchItem {

    public List<GradientEntry> entries;
    public int spreadMethod;
    public boolean interpolationMethodLinearRGB;

    public ExtendedSwatchItem(List<GradientEntry> entries, int spreadMethod, boolean interpolationMethodLinearRGB) {
        this.entries = entries;
        this.spreadMethod = spreadMethod;
        this.interpolationMethodLinearRGB = interpolationMethodLinearRGB;
    }
    
    public ExtendedSwatchItem(GradientEntry... entries) {
        this.entries = Arrays.asList(entries);
    }

    public abstract int getType();
}
