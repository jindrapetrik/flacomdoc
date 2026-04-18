package com.jpexs.flash.fla.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Bufferer for lexer.
 *
 * @author JPEXS
 */
public class LexBufferer implements LexListener {

    private final List<ParsedSymbol> items = new ArrayList<>();

    @Override
    public void onLex(ParsedSymbol s) {
        items.add(s);
    }

    @Override
    public void onPushBack(ParsedSymbol s) {
        if (items.isEmpty()) {
            return;
        }
        if (items.get(items.size() - 1) == s) {
            items.remove(items.size() - 1);
        }
    }

    public void pushAllBack(ScriptLexer lexer) {
        for (int i = items.size() - 1; i >= 0; i--) {
            lexer.pushback(items.get(i));
        }
        items.clear();
    }
}
