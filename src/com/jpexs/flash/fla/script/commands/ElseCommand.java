package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class ElseCommand extends AbstractCommand {
   
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_ELSE;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public String toString() {
        return "Else";
    }        
}
