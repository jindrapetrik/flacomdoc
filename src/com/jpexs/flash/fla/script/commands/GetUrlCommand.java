package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class GetUrlCommand extends AbstractCommand {

    private final Expression url;
    private final Expression window;
    private final String sendVariablesMethod;

    public GetUrlCommand(Expression url, Expression window, String sendVariablesMethod) {
        this.url = url;
        this.window = window;
        this.sendVariablesMethod = sendVariablesMethod;        
    }
    
    public GetUrlCommand(Expression url, Expression window) {
        this(url, window, "");
    }

    public GetUrlCommand(Expression url) {
        this(url, new Expression(), "");
    }        
    
    @Override
    public int getActionKind() {
        return FLA1_ACTION_GET_URL;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        if (!url.isRaw || !window.isRaw || !sendVariablesMethod.isEmpty()) {
            return FlaFormatVersion.F4;
        }
        return FlaFormatVersion.F1;
    }

    @Override
    public Expression getUrl() {
        return url;
    }        

    @Override
    public Expression getWindow() {
        return window;
    }        

    @Override
    public String getMethod() {
        return sendVariablesMethod;
    }

    @Override
    public String toString() {
        String ret = "Get URL (";
        ret += url;
        if (!window.isEmpty()) {
            ret += ", window = " + window;
        }
        if (!sendVariablesMethod.isEmpty()) {
            ret += ", vars = " + sendVariablesMethod;
        }
        ret += ")";
        return ret;
    }        
}
