package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public abstract class AbstractCommand {
    
    public static final int NO_WAIT = -16000;
    public static final int NO_FRAME = -1;
    public static final int NO_PROPERTY = -1;
    public static final int NO_KEY = -1;
        
    
    
    public static final int FLA1_ACTION_NONE = 0;
    public static final int FLA1_ACTION_PLAY = 1;
    public static final int FLA1_ACTION_STOP = 2;

    //Button only:
    public static final int FLA1_ACTION_NEXT_FRAME = 3;
    public static final int FLA1_ACTION_PREV_FRAME = 4;
    public static final int FLA1_ACTION_NEXT_PAGE = 5;
    public static final int FLA1_ACTION_PREV_PAGE = 6;
    public static final int FLA1_ACTION_TOGGLE_HIGH_QUALITY = 7;
    // /end button only

    public static final int FLA1_ACTION_GO_TO = 8; // (page, frameNum, label)
    public static final int FLA1_ACTION_GET_URL = 9; // (url, window)  F4: +method for loadVars
    public static final int FLA1_ACTION_GO_TO_AND_PLAY = 0xA; //(page, frameNum, label)
    public static final int FLA1_ACTION_GET_URL_GO_TO = 0xB; //(page, window, frameNum, url, label)
    public static final int FLA1_ACTION_GET_URL_GO_TO_PLAY = 0xC; // (page, window, frameNum, url, label)

    public static final int FLA2_ACTION_STOP_ALL_SOUNDS = 0xD;

    public static final int FLA3_ACTION_BEGIN_TELL_TARGET = 0xE; // (target)
    public static final int FLA3_ACTION_END_TELL_TARGET = 0xF;

    public static final int FLA3_ACTION_FSCOMMAND = 0x10; // (arg0, arg1)
    public static final int FLA3_ACTION_LOAD_MOVIE = 0x11; // (url)  F4: (url, arg2=level, target)
    public static final int FLA3_ACTION_IF_FRAME_LOADED = 0x12; //(frameNum, label), F4: can also be (arg0)
    public static final int FLA3_ACTION_END_IF_FRAME_LOADED = 0x13;
    public static final int FLA3_ACTION_ON = 0x14;
    public static final int FLA3_ACTION_END_ON = 0x15;
    public static final int FLA4_ACTION_IF = 0x16; // (arg0)
    public static final int FLA4_ACTION_ELSE = 0x17;
    public static final int FLA4_ACTION_END_IF = 0x18;
    public static final int FLA4_ACTION_WHILE = 0x19;
    public static final int FLA4_ACTION_END_WHILE = 0x1A;
    public static final int FLA4_ACTION_CALL = 0x1C; // (arg0)
    public static final int FLA4_ACTION_SET_VARIABLE = 0x1D; // (variableName, arg0 = value)
    public static final int FLA4_ACTION_SET_PROPERTY = 0x1E; //(arg0 = value, target)
    public static final int FLA4_ACTION_ELSE_IF = 0x20; // (arg0)
    public static final int FLA4_ACTION_START_DRAG = 0x22; // (tar, rect = [arg0 = rectLeft, arg1 = rectTop, arg2 = rectRight, arg3 =rectBottom]), start=false for stopDrag
    public static final int FLA4_ACTION_DUPLICATE_MOVIE_CLIP = 0x23; // (arg0 = newName, target, depth)
    public static final int FLA4_ACTION_REMOVE_MOVIE_CLIP = 0x24; // (target)
    public static final int FLA4_ACTION_COMMENT = 0x25; // (arg0)
    public static final int FLA4_ACTION_TRACE = 0x26; // (arg0)


    
    
    public abstract int getActionKind();      
    
    public abstract FlaFormatVersion getMinFlaVersion();
    
    public String defaultPage = "";
    public Expression defaultUrl = new Expression();    
    public int defaultFrameNum = 1;
    public Expression defaultWindow = new Expression();
    public int defaultLevel = 1;
    public int defaultProperty = NO_PROPERTY;
    public int defaultKey = NO_KEY;
    
    public Expression getUrl() {
        return defaultUrl;
    }
    
    public Expression getWindow() {
        return defaultWindow;
    }
    
    public String getPage() {
        return defaultPage;
    }
    
    public int getFrameNum() {
        return defaultFrameNum;        
    }
    
    public int getWaitForExtraFrames() {
        return NO_WAIT;
    }
    
    public String getLabel() {
        return "";
    }
    
    public Expression getArg0() {
        return new Expression();
    }
    
    public Expression getArg1() {
        return new Expression();
    }
    
    public Expression getTarget() {
        return new Expression();
    }
    
    public int getLevel() {
        return defaultLevel;
    }
    
    public boolean onPress() {
        return false;
    }
    
    public boolean onRelease() {
        return false;
    }
    
    public boolean onReleaseOutside() {
        return false;
    }
    
    public boolean onRollOver() {
        return false;
    }
    
    public boolean onRollOut() {
        return false;
    }
    
    public boolean onDragOver() {
        return false;
    }
    
    public boolean onDragOut() {
        return false;
    }
    
    
    public int getProperty() {
        return defaultProperty;
    }
    
    public Expression getVariableName() {
        return new Expression();
    }
    
    public Expression getDepth() {
        return new Expression();
    }
    
    public boolean depthIsExpression() {
        return false;
    }
    
    public Expression getArg2() {
        return new Expression();
    }
    
    public Expression getArg3() {
        return new Expression();
    }    
    
    public boolean hasLockMouseToCenter() {
        return false;
    }
    
    public boolean isStart() {
        return true;
    }
    
    public boolean hasConstrainToRectangle() {
        return false;
    }
    
    public String getMethod() {
        return "";
    }
    
    public boolean onKeyPress() {
        return false;
    }        
    
    public int getKey() {
        return defaultKey;
    }
    
    public boolean doesLoadVariables() {
        return false;
    }      
}
