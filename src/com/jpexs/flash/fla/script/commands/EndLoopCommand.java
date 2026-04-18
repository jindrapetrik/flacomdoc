package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class EndLoopCommand extends AbstractCommand {
   
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_END_WHILE;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public String toString() {
        return "End Loop";
    }        
}
