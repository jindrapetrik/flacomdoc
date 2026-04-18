package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class PlayCommand extends AbstractCommand {

    @Override
    public int getActionKind() {
        return FLA1_ACTION_PLAY;
    }

    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F1;
    }
    
    @Override
    public String toString() {
        return "Play";
    }        
}
