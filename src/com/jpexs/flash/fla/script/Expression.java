package com.jpexs.flash.fla.script;

/**
 *
 * @author JPEXS
 */
public class Expression {
    public String value;
    public boolean isRaw;
    public boolean isNumeric;

    public Expression() {
        isRaw = true;
        value = "";
    }
    
    public Expression(String value) {
        this.value = value;
        isRaw = false;
    }
    
    public Expression(String value, boolean isRaw) {
        this.value = value;
        this.isRaw = isRaw;
        this.isNumeric = false;
    }

    public Expression(String value, boolean isRaw, boolean isNumeric) {
        this.value = value;
        this.isRaw = isRaw;
        this.isNumeric = isNumeric;
    }

    
    @Override
    public String toString() {
        if (isRaw && !isNumeric) {
            return "\"" + Escaper.escapeActionScriptString(value) +  "\"";
        }
        return value;
    }        
    
    public String toStringNumeric() {
        return value;        
    }
    
    public boolean isEmpty() {
        return value.isEmpty();
    }   
}
