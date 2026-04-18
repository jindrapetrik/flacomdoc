package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class LoopWhileCommand extends AbstractCommand {

    private final Expression expression;

    public LoopWhileCommand(Expression expression) {
        this.expression = expression;
    }

    @Override
    public int getActionKind() {
        return FLA4_ACTION_WHILE;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public Expression getArg0() {
        return expression;
    }                
    
    @Override
    public String toString() {
        return "Loop While (" + expression + ")";
    }
    
}
