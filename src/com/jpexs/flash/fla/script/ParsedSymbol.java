package com.jpexs.flash.fla.script;

/**
 * ActionScript 1/2 parsed symbol.
 *
 * @author JPEXS
 */
public class ParsedSymbol {

    /**
     * Position (characters) in source text
     */
    public int position;
    
    public SymbolGroup group;

    public Object value;

    public SymbolType type;

    public ParsedSymbol(int position, SymbolGroup group, SymbolType type) {
        this.position = position;
        this.group = group;
        this.type = type;
        this.value = null;
    }

    public ParsedSymbol(int position, SymbolGroup group, SymbolType type, Object value) {
        this.position = position;
        this.group = group;
        this.type = type;
        this.value = value;
    }  
    
    @Override
    public String toString() {
        return group.toString() + " " + type.toString() + " " + (value != null ? value.toString() : "");
    }

    public boolean isType(Object... types) {
        for (Object t : types) {
            if (t instanceof SymbolGroup) {
                if (group == t) {
                    return true;
                }
            }
            if (t instanceof SymbolType) {
                if (type == t) {
                    return true;
                }
            }
        }
        return false;
    }
}
