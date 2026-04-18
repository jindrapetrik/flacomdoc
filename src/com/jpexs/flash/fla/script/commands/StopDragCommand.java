package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class StopDragCommand extends AbstractCommand {
   
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_START_DRAG;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public boolean isStart() {
        return false;
    }        

    @Override
    public String toString() {
        return "Stop Drag";
    }        
}
