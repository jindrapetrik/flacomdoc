package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class DuplicateMovieClipCommand extends AbstractCommand {
    private final Expression target;
    private final Expression newName;
    private final Expression depth;

    public DuplicateMovieClipCommand(Expression target, Expression newName, Expression depth) {
        this.target = target;
        this.newName = newName;
        this.depth = depth;
    }
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_DUPLICATE_MOVIE_CLIP;
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
    public Expression getDepth() {
        return depth;
    }

    @Override
    public Expression getArg0() {
        return newName;
    }                        
    
     

    @Override
    public String toString() {
        String ret = "Duplicate Movie Clip (";
        ret += target;
        ret += ", ";
        ret += newName;
        ret += ", ";
        ret += depth.toStringNumeric();
        ret += ")";
        return ret;    
    }
    
    
}
