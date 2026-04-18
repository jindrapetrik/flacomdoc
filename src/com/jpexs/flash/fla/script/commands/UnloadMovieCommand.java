package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;

/**
 *
 * @author JPEXS
 */
public class UnloadMovieCommand extends LoadMovieCommand {
    
    public UnloadMovieCommand(int level) {
        super(new Expression(), level, "");
    }    

    public UnloadMovieCommand(Expression levelOrTarget, boolean isTarget) {
        super(new Expression(), levelOrTarget, isTarget, "");
    }        

    @Override
    public String toString() {
        String ret = "Unload Movie (";
        if (level > 0) {
            ret += level;            
        } else {
            ret += levelOrTarget;
        }
        ret += ")";
        return ret;    
    }   
}
