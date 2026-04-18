package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class RemoveMovieClipCommand extends AbstractCommand {

    private final Expression target;

    public RemoveMovieClipCommand(Expression expression) {
        this.target = expression;
    }
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_REMOVE_MOVIE_CLIP;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public Expression getTarget() {
        return target;
    }                
    
    @Override
    public String toString() {
        return "Remove Movie Clip (" + target + ")";
    }
    
}
