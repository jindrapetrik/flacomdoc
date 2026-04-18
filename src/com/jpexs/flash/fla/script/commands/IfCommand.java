package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class IfCommand extends AbstractCommand {

    private final Expression expression;

    public IfCommand(Expression expression) {
        this.expression = expression;
    }

    @Override
    public Expression getArg0() {
        return expression;
    }            
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_IF;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public String toString() {
        return "If (" + expression + ")";
    }
    
}
