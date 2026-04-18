package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class LoadMovieCommand extends AbstractCommand {

    protected int level = 0;
    private final Expression url;
    protected Expression levelOrTarget = new Expression();
    protected boolean isTarget = false;
    private final String variablesMethod;

    public LoadMovieCommand(Expression url, int level, String variablesMethod) {
        this.url = url;
        this.level = level;
        this.variablesMethod = variablesMethod;
        
    }
    
    public LoadMovieCommand(Expression url, Expression levelOrTarget, boolean isTarget, String variablesMethod) {
        this.url = url;
        this.levelOrTarget = levelOrTarget;
        this.isTarget = isTarget;
        this.variablesMethod = variablesMethod;
    }
    
    @Override
    public int getActionKind() {
        return FLA3_ACTION_LOAD_MOVIE;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        if (!url.isRaw || !levelOrTarget.isRaw || !variablesMethod.isEmpty()) {
            return FlaFormatVersion.F4;
        }
        return FlaFormatVersion.F3;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public Expression getTarget() {
        if (!isTarget) {
            return new Expression();
        }
        return levelOrTarget;
    }        

    @Override
    public Expression getArg2() {
        if (isTarget) {
            return new Expression();
        }
        return levelOrTarget;
    }       
    
    @Override
    public String toString() {
        String ret = "Load Movie (";
        ret += url;
        ret += ", ";
        if (level > 0) {
            ret += level;            
        } else {
            ret += levelOrTarget;
        }
        if (!variablesMethod.isEmpty()) {
            ret += ", vars=" + variablesMethod;
        }
        ret += ")";
        return ret;    
    }   
}
