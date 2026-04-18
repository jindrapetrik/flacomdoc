package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class EndFrameLoadedCommand extends AbstractCommand {
   
    
    @Override
    public int getActionKind() {
        return FLA3_ACTION_END_IF_FRAME_LOADED;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F3;
    }

    @Override
    public String toString() {
        return "End Frame Loaded";
    }        
}
