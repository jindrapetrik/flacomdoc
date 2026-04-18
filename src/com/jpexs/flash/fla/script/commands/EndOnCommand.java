package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class EndOnCommand extends AbstractCommand {
   
    
    @Override
    public int getActionKind() {
        return FLA3_ACTION_END_ON;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F3;
    }

    @Override
    public String toString() {
        return "End On";
    }        
}
