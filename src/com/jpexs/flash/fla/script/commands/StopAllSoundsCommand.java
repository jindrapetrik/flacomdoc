package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class StopAllSoundsCommand extends AbstractCommand {

    @Override
    public int getActionKind() {
        return FLA2_ACTION_STOP_ALL_SOUNDS;
    }

    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F2;
    }
    
    @Override
    public String toString() {
        return "Stop All Sounds";
    }

    
}
