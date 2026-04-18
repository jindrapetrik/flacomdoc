package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class BeginTellTargetCommand extends AbstractCommand {

    private final Expression target;

    public BeginTellTargetCommand(Expression target) {
        this.target = target;
    }        

    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F3;
    }            
    
    @Override
    public int getActionKind() {
        return FLA3_ACTION_BEGIN_TELL_TARGET;
    }

    @Override
    public Expression getTarget() {
        return target;
    }        

    @Override
    public String toString() {
        return "Begin Tell Target (" + target + ")";
    }        
}
