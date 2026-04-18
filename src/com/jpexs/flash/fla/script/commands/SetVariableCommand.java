package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class SetVariableCommand extends AbstractCommand {
   
    private final Expression variableName;

    private final Expression value;

    public SetVariableCommand(Expression variableName, Expression value) {
        this.variableName = variableName;
        this.value = value;
    }
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_SET_VARIABLE;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public Expression getVariableName() {
        return variableName;
    }   
    
    @Override
    public Expression getArg0() {
        return value;
    }            
       
    @Override
    public String toString() {
        String ret = "Set Variable: ";
        ret += variableName;
        ret += " = ";
        ret += value;
        return ret;
    }
    
}
