package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class SetPropertyCommand extends AbstractCommand {

    public static final int PROPERTY_X_POSITION = 0;
    public static final int PROPERTY_Y_POSITION = 1;
    public static final int PROPERTY_X_SCALE = 2;
    public static final int PROPERTY_Y_SCALE = 3;
    public static final int PROPERTY_ALPHA = 6;
    public static final int PROPERTY_VISIBILITY = 7;
    public static final int PROPERTY_ROTATION = 10;
    public static final int PROPERTY_NAME = 13;
    public static final int PROPERTY_HIGH_QUALITY = 16; //no target
    public static final int PROPERTY_SHOW_FOCUS_RECTANGLE = 17; //no target
    public static final int PROPERTY_SOUND_BUFFER_TIME = 18; //no target
    
    
    private static final String[] PROPERTY_NAMES = new String[] {
        "X Position",
        "Y Position",
        "X Scale",
        "Y Scale",
        "",
        "",
        "Alpha",
        "Visibility",
        "",
        "",
        "Rotation",
        "",
        "",
        "Name",
        "",
        "",
        "High quality",
        "Show focus rectangle",
        "Sound buffer time"
    };
    
    public static final String[] PROPERTY_IDENTIFIERS = new String[]{
        "_X",
        "_Y",
        "_xscale",
        "_yscale",
        "_currentframe",
        "_totalframes",
        "_alpha",
        "_visible",
        "_width",
        "_height",
        "_rotation",
        "_target",
        "_framesloaded",
        "_name",
        "_droptarget",
        "_url",
        "_highquality",
        "_focusrect",
        "_soundbuftime",
        "_quality",
        "_xmouse",
        "_ymouse"
    };
    
    private final int property;

    private final Expression target;

    private final Expression value;

    public SetPropertyCommand(int property, Expression target, Expression value) {
        this.property = property;
        this.target = target;
        this.value = value;
    }

    @Override
    public int getActionKind() {
        return FLA4_ACTION_SET_PROPERTY;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }
    
    @Override
    public int getProperty() {
        return property;
    }        

    @Override
    public Expression getTarget() {
        return target;
    }    
    
    @Override
    public Expression getArg0() {
        return value;
    }            
        
    @Override
    public String toString() {
        String ret = "Set Property (";
        if (!target.isEmpty()) {
            ret += target;
            ret += ", ";
        }
        if (PROPERTY_NAMES.length < property) {
            ret += PROPERTY_NAMES[property];
        }
        ret += ") = ";
        ret += value;
        return ret;
    }
    
}
