package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author JPEXS
 */
public class OnCommand extends AbstractCommand {

    private final boolean onPress;
    private final boolean onRelease;
    private final boolean onReleaseOutside;
    private final boolean onRollOver;
    private final boolean onRollOut;
    private final boolean onDragOver;
    private final boolean onDragOut;
    private final boolean onKeyPress;
    private final int key;
    
    
    public static final String[] KEYNAMES = {
        null,
        "<Left>",
        "<Right>",
        "<Home>",
        "<End>",
        "<Insert>",
        "<Delete>",
        null,
        "<Backspace>",
        null,
        null,
        null,
        null,
        "<Enter>",
        "<Up>",
        "<Down>",
        "<PageUp>",
        "<PageDown>",
        "<Tab>",
        "<Escape>",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "<Space>"
    };

    public OnCommand(boolean onPress, boolean onRelease, boolean onReleaseOutside, boolean onRollOver, boolean onRollOut, boolean onDragOver, boolean onDragOut) {
        this.onPress = onPress;
        this.onRelease = onRelease;
        this.onReleaseOutside = onReleaseOutside;
        this.onRollOver = onRollOver;
        this.onRollOut = onRollOut;
        this.onDragOver = onDragOver;
        this.onDragOut = onDragOut;
        this.onKeyPress = false;
        this.key = NO_KEY;
    }

    public OnCommand(boolean onPress, boolean onRelease, boolean onReleaseOutside, boolean onRollOver, boolean onRollOut, boolean onDragOver, boolean onDragOut, int key) {
        this.onPress = onPress;
        this.onRelease = onRelease;
        this.onReleaseOutside = onReleaseOutside;
        this.onRollOver = onRollOver;
        this.onRollOut = onRollOut;
        this.onDragOver = onDragOver;
        this.onDragOut = onDragOut;
        this.key = key;
        this.onKeyPress = true;
    }
    
    @Override
    public int getActionKind() {
        return FLA3_ACTION_ON;
    }    
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        if (onKeyPress) {
            return FlaFormatVersion.F4;
        }
        return FlaFormatVersion.F3;
    }
    
     /**
     * Converts string to key code
     *
     * @param str String representation of key code
     * @return Key code
     */
    public static Integer stringToKey(String str) {
        for (int i = 0; i < KEYNAMES.length; i++) {
            if (KEYNAMES[i] != null) {
                if (str.equals(KEYNAMES[i])) {
                    return i;
                }
            }
        }
        if (str.length() == 1) {
            return (int) str.charAt(0);
        }
        return null;
    }

    @Override
    public boolean onPress() {
        return onPress;
    }

    @Override
    public boolean onRelease() {
        return onRelease;
    }

    @Override
    public boolean onReleaseOutside() {
        return onReleaseOutside;        
    }        

    @Override
    public boolean onRollOver() {
        return onRollOver;        
    }

    @Override
    public boolean onRollOut() {
        return onRollOut;
    }

    @Override
    public boolean onDragOver() {
        return onDragOver;
    }

    @Override
    public boolean onDragOut() {
        return onDragOut;
    }

    @Override
    public boolean onKeyPress() {
        return onKeyPress;
    }

    @Override
    public int getKey() {
        return key;
    }    

    @Override
    public String toString() {
        String ret = "On (";
        List<String> events = new ArrayList<>();
        if (onPress) {
            events.add("Press");
        }
        if (onRelease) {
            events.add("Release");
        }
        if (onReleaseOutside) {
            events.add("Release Outside");
        }
        if (onRollOver) {
            events.add("Roll Over");
        }
        if (onRollOut) {
            events.add("Roll Out");
        }
        if (onDragOver) {
            events.add("Drag Over");
        }
        if (onDragOut) {
            events.add("Drag Out");
        }
        if (onKeyPress) {
            String keyName;
            if (KEYNAMES.length < key && KEYNAMES[key] != null) {
                keyName = KEYNAMES[key];
            } else {
                keyName = "" + (char) key;
            }
            events.add("Key: " + keyName);
        }
        ret += String.join(", ", events);
        ret += ")";
        return ret;    
    }        
}
