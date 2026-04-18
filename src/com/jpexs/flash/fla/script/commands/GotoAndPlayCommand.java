package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class GotoAndPlayCommand extends AbstractCommand {

    private Expression value = new Expression();
    private String page;
    private int frameNum = NO_FRAME;
    private int waitFrames;
    
    public GotoAndPlayCommand(String page, int frameNum, int waitFrames) {
        this.page = page;
        this.frameNum = frameNum;
        this.waitFrames = waitFrames;
    }
    
    public GotoAndPlayCommand(String page, int frameNum) {
        this(page, frameNum, NO_WAIT);
    }
    
    
    public GotoAndPlayCommand(String page, Expression value, int waitFrames) {
        this.page = page;
        this.value = value;
        this.waitFrames = waitFrames;        
    }
    
    public GotoAndPlayCommand(String page, Expression value) {
        this(page, value, NO_WAIT);
    }
    
    @Override
    public int getActionKind() {
        return FLA1_ACTION_GO_TO_AND_PLAY;
    }    
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        if (!value.isRaw) {
            return FlaFormatVersion.F4;
        }
        return FlaFormatVersion.F1;
    }

    @Override
    public Expression getArg0() {
        return value;
    }        

    @Override
    public int getFrameNum() {
        return frameNum;
    }        

    @Override
    public int getWaitForExtraFrames() {
        return waitFrames;
    }

    @Override
    public String getPage() {
        return page;
    }        
            
    @Override
    public String toString() {
        String ret = "Go to and Play (";
        if (frameNum != NO_FRAME) {
            ret += frameNum;
        } else {
            ret += value;
        }
        if (waitFrames != NO_WAIT) {
            ret += ", waitFrames = " + waitFrames;
        }
        ret += ")";
        return ret;
    }
}
