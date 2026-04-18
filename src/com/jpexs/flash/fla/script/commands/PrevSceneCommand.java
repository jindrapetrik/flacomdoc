package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class PrevSceneCommand extends AbstractCommand {

    @Override
    public int getActionKind() {
        return FLA1_ACTION_PREV_PAGE;
    }

    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F1;
    }
    
    @Override
    public String toString() {
        return "Go to Previous Scene";
    }       
}
