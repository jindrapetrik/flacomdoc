package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class CommentCommand extends AbstractCommand {

    private final Expression expression;

    public CommentCommand(Expression expression) {
        this.expression = expression;
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
    public int getActionKind() {
        return FLA4_ACTION_COMMENT;
    }

    @Override
    public String toString() {
        return "Comment: " + expression;
    }
    
}
