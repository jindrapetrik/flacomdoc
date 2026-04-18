package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class GetUrlGotoAndPlay extends AbstractCommand {

    private final String url;
    private final String window;
    private final String page;
    private final int frameNum;
    private final int waitFrames;

    public GetUrlGotoAndPlay(String url, String window, String page, int frameNum, int waitFrames) {
        this.url = url;
        this.window = window; 
        this.page = page;
        this.frameNum = frameNum;
        this.waitFrames = waitFrames;
    }

    public GetUrlGotoAndPlay(String url, String window, String page, int frameNum) {
        this(url, window, page, frameNum, NO_WAIT);
    }    
    
    @Override
    public int getActionKind() {
        return FLA1_ACTION_GET_URL_GO_TO_PLAY;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F1;
    }

    @Override
    public Expression getUrl() {
        return new Expression(url, true);
    }        

    @Override
    public Expression getWindow() {
        return new Expression(window, true);
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
        String ret = "Get URL, Goto And Play (";
        ret += url;
        ret += ", ";
        ret += frameNum;
        if (!window.isEmpty()) {
            ret += ", window = " + window;
        }
        if (waitFrames != NO_WAIT) {
            ret += ", waitFrames = " + waitFrames;
        }
        ret += ")";
        return ret;
    }        
}
