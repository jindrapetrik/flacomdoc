package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class IfFrameIsLoadedCommand extends AbstractCommand {

    private int frameNum = NO_FRAME;
    private Expression frame = new Expression();

    public IfFrameIsLoadedCommand(int frameNum) {
        this.frameNum = frameNum;
    }
    
    public IfFrameIsLoadedCommand(Expression frame) {
        this.frame = frame;        
    }

    @Override
    public int getActionKind() {
        return FLA3_ACTION_IF_FRAME_LOADED;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        if (!frame.isRaw) {
            return FlaFormatVersion.F4;
        }
        return FlaFormatVersion.F3;
    }
    
    @Override
    public int getFrameNum() {
        return frameNum;
    }        

    @Override
    public Expression getArg0() {
        return frame;
    }            
        
    @Override
    public String toString() {
        String ret = "If Frame Is Loaded (";
        if (frameNum != NO_FRAME) {
            ret += frameNum;
        } else {
            ret += frame;
        }
        ret += ")";        
        return ret;
    }        
}
