package com.jpexs.flash.fla.script;

/**
 * Listener for lexer.
 *
 * @author JPEXS
 */
public interface LexListener {

    public void onLex(ParsedSymbol s);

    public void onPushBack(ParsedSymbol s);
}
