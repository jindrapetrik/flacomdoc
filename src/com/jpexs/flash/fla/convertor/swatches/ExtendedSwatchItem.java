package com.jpexs.flash.fla.convertor.swatches;

import com.jpexs.flash.fla.convertor.GradientEntry;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public abstract class ExtendedSwatchItem {

    public List<GradientEntry> entries;

    public ExtendedSwatchItem(GradientEntry... entries) {
        this.entries = Arrays.asList(entries);
    }

    public abstract int getType();
}
