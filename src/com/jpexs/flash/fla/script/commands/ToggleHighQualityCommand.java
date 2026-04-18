package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class ToggleHighQualityCommand extends AbstractCommand {

    @Override
    public int getActionKind() {
        return FLA1_ACTION_TOGGLE_HIGH_QUALITY;
    }    
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F1;
    }

    @Override
    public String toString() {
        return "Toggle High Quality";
    }       
}
