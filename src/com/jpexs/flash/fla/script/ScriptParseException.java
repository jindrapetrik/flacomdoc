package com.jpexs.flash.fla.script;


/**
 * Exception for action parsing errors
 *
 * @author JPEXS
 */
public class ScriptParseException extends ParseException {

    /**
     * Constructs a new parse exception.
     * @param text Text of the exception
     * @param line Line number where the exception occurred
     */
    public ScriptParseException(String text, long line) {
        super(text, line);
    }
    
    /**
     * Constructs a new parse exception.
     * @param text Text of the exception
     * @param line Line number where the exception occurred
     * @param position Position where the exception occurred
     */
    public ScriptParseException(String text, long line, long position) {
        super(text, line, position);
    }
}
