package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class FsCommandCommand extends AbstractCommand {

    private final Expression command;
    private final Expression arguments;

    public FsCommandCommand(Expression command, Expression arguments) {
        this.command = command;
        this.arguments = arguments;        
    }            
    
    @Override
    public int getActionKind() {
        return FLA3_ACTION_FSCOMMAND;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F3;
    }

    @Override
    public Expression getArg0() {
        return command;
    }   

    @Override
    public Expression getArg1() {
        return arguments;
    }        

    @Override
    public String toString() {
        return "FS Command (" + command + ", " + arguments + ")";
    }
    
}
