/*
 * Copyright (C) 2026 JPEXS.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.jpexs.flash.fla.reader;

/**
 * Authoring-command IDs and their block delimiters; distinct from SWF opcodes.
 */
public enum FlaCommandKind {
    GOTO(0, "Goto"),
    PLAY(1, "Play"),
    STOP(2, "Stop"),
    TOGGLE_QUALITY(3, "ToggleQuality"),
    STOP_ALL_SOUNDS(4, "StopAllSounds"),
    GET_URL(5, "GetURL"),
    FSCOMMAND(6, "FSCommand"),
    LOAD_MOVIE(7, "LoadMovie"),
    TELL_TARGET(8, "TellTarget"),
    END_TELL_TARGET(9, "EndTellTarget"),
    IF_FRAME_LOADED(10, "IfFrameLoaded"),
    ON_MOUSE_EVENT(11, "OnMouseEvent"),
    END_ON_MOUSE_EVENT(12, "EndOnMouseEvent"),
    ON_SPRITE_EVENT(13, "OnSpriteEvent"),
    END_ON_SPRITE_EVENT(14, "EndOnSpriteEvent"),
    IF(15, "If"),
    ELSE(16, "Else"),
    ELSE_IF(17, "ElseIf"),
    END_IF(18, "EndIf"),
    END_IF_FRAME_LOADED(19, "EndIfFrameLoaded"),
    LOOP(20, "Loop"),
    END_LOOP(21, "EndLoop"),
    CALL(22, "Call"),
    SET_PROPERTY(23, "SetProperty"),
    SET_VARIABLE(24, "SetVariable"),
    CLONE_SPRITE(25, "CloneSprite"),
    START_DRAG(26, "StartDrag"),
    TRACE(27, "Trace"),
    COMMENT(28, "Comment"),
    STOP_DRAG(29, "StopDrag"),
    REMOVE_SPRITE(30, "RemoveSprite"),
    UNLOAD_MOVIE(31, "UnloadMovie"),
    LOAD_VARIABLES(32, "LoadVariables"),
    INCLUDE(33, "Include"),
    PRINT(34, "Print"),
    DELETE(36, "Delete"),
    FUNCTION(37, "Function"),
    END_FUNCTION(38, "EndFunction"),
    VAR(39, "Var"),
    RETURN(40, "Return"),
    EVALUATE(41, "Evaluate"),
    DO_WHILE(42, "DoWhile"),
    END_DO_WHILE(43, "EndDoWhile"),
    FOR(44, "For"),
    END_FOR(45, "EndFor"),
    BREAK(46, "Break"),
    FOR_IN(47, "ForIn"),
    END_FOR_IN(48, "EndForIn"),
    SWAP_DEPTHS(49, "SwapDepths"),
    WITH(50, "With"),
    END_WITH(51, "EndWith"),
    CONTINUE(52, "Continue"),
    SWITCH(53, "Switch"),
    END_SWITCH(54, "EndSwitch"),
    CASE(55, "Case"),
    DEFAULT(56, "Default"),
    INIT_CLIP(57, "InitClip"),
    END_CLIP(58, "EndClip"),
    INVOKE(59, "Invoke"),
    METHOD(60, "Method"),
    END_METHOD(61, "EndMethod"),
    CALL_XMLFUNCTION(62, "CallXMLFunction"),
    TRY(63, "Try"),
    END_TRY(64, "EndTry"),
    CATCH(65, "Catch"),
    END_CATCH(66, "EndCatch"),
    FINALLY(67, "Finally"),
    END_FINALLY(68, "EndFinally"),
    THROW(69, "Throw"),
    STRICT(70, "Strict"),
    CLASS(71, "Class"),
    END_CLASS(72, "EndClass"),
    INTERFACE(73, "Interface"),
    END_INTERFACE(74, "EndInterface"),
    IMPORT(75, "Import"),
    PACKAGE_DEFINITION(76, "PackageDefinition"),
    END_PACKAGE_DEFINITION(77, "EndPackageDefinition"),
    NAMESPACE_DEFINITION(78, "NamespaceDefinition"),
    CALL_FUNCTION_NO_XML(79, "CallFunctionNoXML"),
    FOR_EACH_IN(80, "ForEachIn"),
    END_FOR_EACH_IN(81, "EndForEachIn"),
    LABEL(82, "Label"),
    CONFIG_NAMESPACE_DEFINITION(83, "ConfigNamespaceDefinition"),
    COND_COMP(84, "CondComp"),
    END_COND_COMP(85, "EndCondComp");
    private final int id;
    private final String editorName;

    FlaCommandKind(int id, String editorName) {
        this.id = id;
        this.editorName = editorName;
    }

    public int getId() {
        return id;
    }

    public String getEditorName() {
        return editorName;
    }

    public static FlaCommandKind fromId(long id) {
        for (FlaCommandKind k : values()) {
            if (k.id == id) {
                return k;
            }
        }
        return null;
    }
    private static final int[][] BLOCKS = {
        {8, 9}, {10, 19}, {11, 12}, {13, 14}, {15, 18}, {20, 21}, {37, 38}, {42, 43},
        {44, 45}, {47, 48}, {50, 51}, {53, 54}, {57, 58}, {60, 61}, {63, 64}, {65, 66},
        {67, 68}, {71, 72}, {73, 74}, {76, 77}, {80, 81}, {84, 85}
    };

    public FlaCommandKind getClosingKind() {
        for (int[] pair : BLOCKS) {
            if (pair[0] == id) {
                return fromId(pair[1]);
            }
        }
        return null;
    }

    public FlaCommandKind getOpeningKind() {
        for (int[] pair : BLOCKS) {
            if (pair[1] == id) {
                return fromId(pair[0]);
            }
        }
        return null;
    }

    public boolean isBranchMarker() {
        return id == 16 || id == 17 || id == 55 || id == 56;
    }
}
