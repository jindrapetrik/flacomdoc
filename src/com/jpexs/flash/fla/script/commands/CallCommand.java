package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class CallCommand extends AbstractCommand {

    private final Expression frame;

    public CallCommand(Expression frame) {
        this.frame = frame;
    }

    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }
    
    @Override
    public Expression getArg0() {
        return frame;
    }
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_CALL;
    }

    @Override
    public String toString() {
        return "Call (" + frame + ")";
    }
    
}
