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
package com.jpexs.flash.fla.script;

import com.jpexs.flash.fla.reader.FlaRecord;
import com.jpexs.flash.fla.script.commands.OnCommand;
import com.jpexs.flash.fla.script.commands.SetPropertyCommand;
import java.util.*;

/**
 * Pure ActionScript reconstruction shared by binary and XML serializers.
 */
public final class FlaActionSource {

    private FlaActionSource() {
    }

    private static long n(FlaRecord r, String key, long fallback) {
        Object v = r == null ? null : r.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    private static String s(FlaRecord r, String key, String fallback) {
        Object v = r == null ? null : r.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    private static FlaRecord record(FlaRecord r, String key) {
        Object v = r == null ? null : r.get(key);
        return v instanceof FlaRecord ? (FlaRecord) v : null;
    }

    @SuppressWarnings("unchecked")
    private static List<FlaRecord> records(FlaRecord r, String key) {
        Object v = r == null ? null : r.get(key);
        return v instanceof List ? (List<FlaRecord>) v : Collections.emptyList();
    }

    public static String reconstruct(FlaRecord actions) {
        if (actions == null) {
            return "";
        }
        String source = s(actions, "source", "");
        if (!source.isEmpty()) {
            return source;
        }
        FlaRecord list = record(actions, "compiledCommands");
        boolean compiled = list != null;
        List<FlaRecord> commands = compiled ? records(list, "commands") : records(actions, "commands");
        if (actions.get("kind") instanceof Number) {
            commands = Collections.singletonList(actions);
        }
        if (compiled) {
            String archived = archivedSource(commands);
            if (archived != null) {
                return archived;
            }
        }
        Deque<Integer> blocks = new ArrayDeque<>();
        StringBuilder result = new StringBuilder();
        for (FlaRecord command : commands) {
            String line = compiled ? compiled(command, blocks) : historical(command, blocks);
            int kind = (int) n(command, "kind", -1);
            if (!compiled && actions.get("kind") instanceof Number && (kind == 8 || kind == 10 || kind == 11 || kind == 12) && n(command, "waitForExtraFrames", -16000) != -16000) {
                line = "ifFrameLoaded (" + frame(command, n(command, "frameNumber", 1) + n(command, "waitForExtraFrames", 0)) + ") {\n" + line + "\n}";
            }
            if (!line.isEmpty()) {
                result.append(line).append('\n');
            }
        }
        if (!blocks.isEmpty()) {
            throw new IllegalArgumentException("Unclosed editor action block.");
        }
        return result.toString();
    }

    public static String archivedSource(List<FlaRecord> commands) {
        Set<Integer> delimiters = new HashSet<>(Arrays.asList(9, 12, 14, 18, 19, 21, 35, 38, 45, 48, 51, 54, 58, 61, 72, 74, 77, 81, 85));
        StringBuilder text = new StringBuilder();
        boolean body = false;
        for (FlaRecord command : commands) {
            if (!(command.get("sourcePrefix") instanceof String) || !(command.get("sourceText") instanceof String)
                    || !(command.get("sourceSuffix") instanceof String) || !command.isComplete()) {
                return null;
            }
            String core = s(command, "sourceText", "");
            if (core.isEmpty()) {
                int type = (int) n(command, "type", -1);
                // An absent closing token is text only when the editor explicitly
                // suppressed it. Otherwise this is an incomplete text cache.
                if (type != 35 && (!delimiters.contains(type) || n(command, "suppressBlockDelimiter", 0) != 1)) {
                    return null;
                }
            }
            body |= !core.isEmpty();
            text.append(s(command, "sourcePrefix", "")).append(core).append(s(command, "sourceSuffix", ""));
        }
        return body ? text.toString() : null;
    }

    public static String historical(FlaRecord r, Deque<Integer> blocks) {
        int kind = (int) n(r, "kind", -1);
        switch (kind) {
            case 0:
                return "";
            case 1:
                return "play();";
            case 2:
                return "stop();";
            case 3:
                return "nextFrame();";
            case 4:
                return "prevFrame();";
            case 5:
                return "nextScene();";
            case 6:
                return "prevScene();";
            case 7:
                return "toggleHighQuality();";
            case 8:
            case 10:
                return go(r, kind == 10);
            case 9:
                return url(r);
            case 11:
            case 12:
                return url(r) + "\n" + go(r, kind == 12);
            case 13:
                return "stopAllSounds();";
            case 14:
                blocks.push(15);
                return "tellTarget (" + expr(r, "target") + ") {";
            case 15:
                return close(blocks, 15);
            case 16:
                return "fscommand(" + expr(r, "arg0") + ", " + expr(r, "arg1") + ");";
            case 17:
                String target = s(r, "target", "");
                String fn = n(r, "loadVariables", 0) != 0 ? "loadVariables" : "loadMovie";
                String destination = target.isEmpty() ? s(r, "arg2", "").isEmpty() ? Long.toString(n(r, "level", 1)) : expr(r, "arg2") : expr(r, "target");
                return fn + (target.isEmpty() ? "Num" : "") + "(" + expr(r, "url") + ", " + destination + method(s(r, "method", "")) + ");";
            case 18:
                blocks.push(19);
                return "ifFrameLoaded (" + frame(r) + ") {";
            case 19:
                return close(blocks, 19);
            case 20:
                List<String> events = new ArrayList<>();
                for (String field : new String[]{"press", "release", "releaseOutside", "rollOver", "rollOut", "dragOver", "dragOut"}) {
                    if (n(r, field, 0) != 0) {
                        events.add(field);
                    }
                }
                if (n(r, "onKeyPress", 0) != 0) {
                    events.add("keyPress " + quote(key((int) n(r, "key", 0))));
                }
                if (events.isEmpty()) {
                    throw new IllegalArgumentException("Empty historical mouse event.");
                }
                blocks.push(21);
                return "on (" + String.join(", ", events) + ") {";
            case 21:
                return close(blocks, 21);
            case 22:
                blocks.push(24);
                return "if (" + expression(r, "arg0") + ") {";
            case 23:
                branch(blocks, 24);
                return "} else {";
            case 24:
                return close(blocks, 24);
            case 25:
                blocks.push(26);
                return "while (" + expression(r, "arg0") + ") {";
            case 26:
                return close(blocks, 26);
            case 28:
                return "call(" + expr(r, "arg0") + ");";
            case 29:
                return "set(" + expr(r, "variableName") + ", " + expr(r, "arg0") + ");";
            case 30:
                int property = (int) n(r, "property", -1);
                if (property < 0 || property >= SetPropertyCommand.PROPERTY_IDENTIFIERS.length) {
                    throw new IllegalArgumentException("Unassigned historical property " + property);
                }
                return "setProperty(" + expr(r, "target") + ", " + SetPropertyCommand.PROPERTY_IDENTIFIERS[property] + ", " + expr(r, "arg0") + ");";
            case 32:
                branch(blocks, 24);
                return "} else if (" + expression(r, "arg0") + ") {";
            case 34:
                if (n(r, "start", 0) == 0) {
                    return "stopDrag();";
                }
                String args = expr(r, "target") + ", " + (n(r, "lockMouseToCenter", 0) != 0);
                if (n(r, "constrainToRectangle", 0) != 0) {
                    for (int i = 0; i < 4; i++) {
                        args += ", " + expression(r, "arg" + i);
                    }
                }
                return "startDrag(" + args + ");";
            case 35:
                return "duplicateMovieClip(" + expr(r, "target") + ", " + expr(r, "arg0") + ", " + expression(r, "depth") + ");";
            case 36:
                return "removeMovieClip(" + expr(r, "target") + ");";
            case 37:
                return "// " + s(r, "arg0", "").replace("\r", "\n").replace("\n", "\n// ");
            case 38:
                return "trace(" + expr(r, "arg0") + ");";
            default:
                throw new IllegalArgumentException("Unassigned historical action kind " + kind);
        }
    }

    public static String compiled(FlaRecord r, Deque<Integer> blocks) {
        int type = (int) n(r, "type", -1);
        if (!blocks.isEmpty() && blocks.peek() == -21) {
            if (type == 21 && n(r, "suppressBlockDelimiter", 0) == 1) {
                blocks.pop();
                return "";
            }
            throw new IllegalArgumentException("Empty while loop requires its suppressed end delimiter.");
        }
        if (n(r, "suppressBlockDelimiter", 0) == 1 && (type == 9 || type == 19 || type == 21 || type == 45 || type == 48 || type == 51 || type == 81)) {
            throw new IllegalArgumentException("Suppressed editor block delimiter has no complete archived statement text.");
        }
        switch (type) {
            case 0:
                long mode = n(r, "navigationMode", -1);
                if (mode >= 3 && mode <= 6) {
                    return new String[]{"nextFrame();", "prevFrame();", "nextScene();", "prevScene();"}[(int) mode - 3];
                }
                if (mode < 0 || mode > 2) {
                    throw new IllegalArgumentException("Unassigned navigation mode " + mode);
                }
                String value = s(r, "frame", "");
                if (mode == 0 && !value.matches("[0-9]+")) {
                    throw new IllegalArgumentException("Numeric navigation frame is not a number.");
                }
                String frame = mode == 1 ? quote(value) : value;
                return (n(r, "play", 0) != 0 ? "gotoAndPlay" : "gotoAndStop") + "(" + (s(r, "scene", "").isEmpty() ? "" : quote(s(r, "scene", "")) + ", ") + frame + ");";
            case 1:
                return "play();";
            case 2:
                return "stop();";
            case 3:
                return "toggleHighQuality();";
            case 4:
                return "stopAllSounds();";
            case 5:
                long method = n(r, "method", 0);
                if (method < 0 || method > 2) {
                    throw new IllegalArgumentException("Unassigned URL method " + method);
                }
                return "getURL(" + param(s(r, "url", ""), n(r, "urlFlags", 0)) + ", " + param(s(r, "window", ""), n(r, "windowFlags", 0)) + (method == 0 ? "" : ", " + quote(method == 1 ? "GET" : "POST")) + ");";
            case 6:
                return "fscommand(" + parameter(r, 1) + ", " + parameter(r, 2) + ");";
            case 7:
                return "loadMovie" + (n(r, "targetMode", 0) == 0 ? "Num" : "") + "(" + parameter(r, 1) + ", "
                        + (n(r, "targetMode", 0) == 0 ? parameterText(r, 2) : parameter(r, 2)) + requestMethod(n(r, "requestMethod", 0)) + ");";
            case 8:
                blocks.push(9);
                return "tellTarget (" + parameter(r, 1) + ") {";
            case 9:
                return close(blocks, 9);
            case 10:
                long frameMode = n(r, "frameMode", 0);
                if (frameMode < 0 || frameMode > 2) {
                    throw new IllegalArgumentException("Unassigned frame-loaded mode " + frameMode);
                }
                blocks.push(19);
                return "ifFrameLoaded (" + (s(r, "scene", "").isEmpty() ? "" : quote(s(r, "scene", "")) + ", ")
                        + (frameMode == 1 ? quote(s(r, "frame", "")) : s(r, "frame", "")) + ") {";
            case 11:
                long flags = n(r, "eventFlags", 0);
                List<String> events = new ArrayList<>();
                if ((flags & 0x100) != 0) {
                    events.add(s(r, "eventString", ""));
                } else {
                    String[] names = {"press", "release", "releaseOutside", "rollOver", "rollOut", "dragOver", "dragOut"};
                    for (int i = 0; i < names.length; i++) {
                        if ((flags & (1 << i)) != 0) {
                            events.add(names[i]);
                        }
                    }
                    if ((flags & 128) != 0) {
                        events.add("keyPress " + quote(key((int) n(r, "keyCode", 0))));
                    }
                    if ((flags & ~255L) != 0) {
                        throw new IllegalArgumentException("Unassigned mouse-event bits.");
                    }
                }
                if (events.isEmpty()) {
                    throw new IllegalArgumentException("Empty mouse event.");
                }
                blocks.push(12);
                return "on (" + String.join(", ", events) + ") {";
            case 12:
                return close(blocks, 12);
            case 13:
                long clipEvents = n(r, "eventFlags", 0);
                if (clipEvents == 0 || (clipEvents & ~511L) != 0) {
                    throw new IllegalArgumentException("Unassigned or empty movie-clip event mask.");
                }
                String[] clipNames = {"load", "enterFrame", "unload", "mouseMove", "mouseDown", "mouseUp", "keyDown", "keyUp", "data"};
                List<String> clipList = new ArrayList<>();
                for (int i = 0; i < clipNames.length; i++) {
                    if ((clipEvents & 1 << i) != 0) {
                        clipList.add(clipNames[i]);
                    }
                }
                blocks.push(14);
                return "onClipEvent (" + String.join(", ", clipList) + ") {";
            case 14:
                return close(blocks, 14);
            case 15:
                blocks.push(18);
                return "if (" + s(r, "condition", "") + ") {";
            case 16:
                branch(blocks, 18);
                return "} else {";
            case 17:
                branch(blocks, 18);
                return "} else if (" + s(r, "condition", "") + ") {";
            case 18:
                return close(blocks, 18);
            case 19:
                return close(blocks, 19);
            case 20:
                if (n(r, "emptyBody", 0) != 0) {
                    blocks.push(-21);
                    return "while (" + s(r, "condition", "") + ");";
                }
                blocks.push(21);
                return "while (" + s(r, "condition", "") + ") {";
            case 21:
                return close(blocks, 21);
            case 22:
                return "call(" + parameter(r, 1) + ");";
            case 23:
                int property = (int) n(r, "propertyIndex", -1);
                if (property < 0 || property >= SetPropertyCommand.PROPERTY_IDENTIFIERS.length) {
                    throw new IllegalArgumentException("Unassigned property selector " + property);
                }
                return "setProperty(" + parameter(r, 1) + ", " + SetPropertyCommand.PROPERTY_IDENTIFIERS[property] + ", " + parameter(r, 2) + ");";
            case 24:
                String assigned = parameter(r, 2);
                if (n(r, "valueQuoteCharacter", 34) == 39 && n(record(r, "parameter1"), "flags", 0) == 0
                        && n(record(r, "parameter2"), "flags", 0) == 0) {
                    assigned = "'" + Escaper.escapeActionScriptString(parameterText(r, 2)) + "'";
                }
                return "set(" + parameter(r, 1) + ", " + assigned + ");";
            case 25:
                return "duplicateMovieClip(" + parameter(r, 1) + ", " + parameter(r, 2) + ", " + s(r, "depth", "") + ");";
            case 26:
                boolean lock = n(r, "lockCenter", 0) != 0,
                 constrain = n(r, "constrain", 0) != 0;
                String drag = parameter(r, 1);
                if (lock || constrain) {
                    drag += ", " + lock;
                }
                if (constrain) {
                    for (String bound : new String[]{"left", "top", "right", "bottom"}) {
                        drag += ", " + s(r, bound, "");
                    }
                }
                return "startDrag(" + drag + ");";
            case 27:
                return "trace(" + parameter(r, 1) + ");";
            case 28:
                String comment = s(r, "comment", "");
                if (n(r, "commentStyle", 0) != 0) {
                    return "/*" + comment + "*/";
                }
                return "// " + comment.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\n// ");
            case 29:
                return "stopDrag();";
            case 30:
                return "removeMovieClip(" + parameter(r, 1) + ");";
            case 31:
                return "unloadMovie" + (n(r, "targetMode", 0) == 0 ? "Num(" + parameterText(r, 1) : "(" + parameter(r, 1)) + ");";
            case 32:
                return "loadVariables" + (n(r, "targetMode", 0) == 0 ? "Num" : "") + "(" + parameter(r, 1) + ", "
                        + (n(r, "targetMode", 0) == 0 ? parameterText(r, 2) : parameter(r, 2)) + requestMethod(n(r, "requestMethod", 0)) + ");";
            case 33:
                return "#include " + quote(s(r, "includePath", ""));
            case 34:
                long bounds = n(r, "boundsMode", 0);
                if (bounds < 0 || bounds > 2) {
                    throw new IllegalArgumentException("Unassigned print bounds " + bounds);
                }
                return "print" + (n(r, "printAsBitmap", 0) == 0 ? "" : "AsBitmap") + (n(r, "targetMode", 0) == 0 ? "Num" : "")
                        + "(" + (n(r, "targetMode", 0) == 0 ? parameterText(r, 1) : parameter(r, 1)) + ", " + quote(new String[]{"bmovie", "bframe", "bmax"}[(int) bounds]) + ");";
            case 36:
                return "delete " + parameter(r, 1) + ";";
            case 37:
                long functionFlags = n(r, "functionFlags", 0);
                String functionName = s(r, "functionSignature", ""),
                 resultType = "";
                int colon = functionName.indexOf(':');
                if (colon >= 0) {
                    resultType = functionName.substring(colon + 1);
                    functionName = functionName.substring(0, colon);
                }
                String function = visibility(functionFlags) + ((functionFlags & 4) != 0 ? "static " : "") + "function "
                        + ((functionFlags & 8) != 0 ? "get " : (functionFlags & 16) != 0 ? "set " : "") + functionName
                        + "(" + s(r, "parameters", "") + ")" + (resultType.isEmpty() ? "" : ":" + resultType);
                if (n(r, "declarationOnly", 0) != 0) {
                    return function + ";";
                }
                blocks.push(38);
                return function + " {";
            case 38:
                return close(blocks, 38);
            case 39:
                long variableFlags = n(r, "variableFlags", 0);
                return ((variableFlags & 4) != 0 ? "static " : "") + ((variableFlags & 128) != 0 ? "protected " : "")
                        + visibility(variableFlags) + "var " + s(r, "declaration", "") + ";";
            case 40:
                return "return " + s(r, "returnValue", "") + ";";
            case 41:
                return s(r, "expression", "") + ";";
            case 42:
                blocks.push(43);
                return "do {";
            case 43:
                close(blocks, 43);
                return "} while (" + s(r, "condition", "") + ");";
            case 44:
                blocks.push(45);
                return n(r, "revision", 0) == 0 ? "for (; " + s(r, "condition", "") + "; ) {"
                        : "for (" + s(r, "initializer", "") + "; " + s(r, "condition", "") + "; " + s(r, "increment", "") + ") {";
            case 45:
                return close(blocks, 45);
            case 46:
                return "break" + label(record(r, "parameter1")) + ";";
            case 47:
                blocks.push(48);
                return "for (" + s(r, "iterator", "") + " in " + s(r, "collection", "") + ") {";
            case 48:
                return close(blocks, 48);
            case 49:
                return "swapDepths(" + parameter(r, 1) + ", " + parameter(r, 2) + ");";
            case 50:
                blocks.push(51);
                return "with (" + s(r, "object", "") + ") {";
            case 51:
                return close(blocks, 51);
            case 52:
                return "continue;";
            case 53:
                blocks.push(54);
                return "switch (" + s(r, "expression", "") + ") {";
            case 54:
                return close(blocks, 54);
            case 55:
            case 56:
                branch(blocks, 54);
                return n(r, "isDefaultCase", type == 56 ? 1 : 0) != 0 ? "default:" : "case " + s(r, "caseExpression", "") + ":";
            case 57:
                blocks.push(58);
                return "#initclip" + (s(r, "initClipIndex", "").isEmpty() ? "" : " " + s(r, "initClipIndex", ""));
            case 58:
                branch(blocks, 58);
                blocks.pop();
                return "#endinitclip";
            case 59:
                return invoke(r);
            case 60:
                if (s(r, "object", "").isEmpty()) {
                    throw new IllegalArgumentException("Method definition has no object expression.");
                }
                blocks.push(61);
                return s(r, "object", "") + "." + s(r, "method", "") + " = function(" + s(r, "parameters", "") + ") {";
            case 61:
                return close(blocks, 61);
            case 62:
            case 79:
                String object = s(r, "object", ""),
                 name = s(r, "method", "");
                if (type == 62 && name.startsWith(".")) {
                    if (object.isEmpty()) {
                        throw new IllegalArgumentException("Relative XML call has no object expression.");
                    }
                    name = name.substring(1);
                }
                return (object.isEmpty() ? "" : object + ".") + name + "(" + s(r, "arguments", "") + ");";
            case 63:
                blocks.push(64);
                return "try {";
            case 64:
                return close(blocks, 64);
            case 65:
                blocks.push(66);
                return "catch (" + s(r, "exception", "") + ") {";
            case 66:
                return close(blocks, 66);
            case 67:
                blocks.push(68);
                return "finally {";
            case 68:
                return close(blocks, 68);
            case 69:
                return "throw " + s(r, "expression", "") + ";";
            case 70:
                return "#strict";
            case 71:
                blocks.push(72);
                return (n(r, "dynamic", 0) != 0 ? "dynamic " : "") + (n(r, "intrinsic", 0) != 0 ? "intrinsic " : "")
                        + "class " + s(r, "name", "") + clause("extends", s(r, "superClass", "")) + clause("implements", s(r, "interfaces", "")) + " {";
            case 72:
                return close(blocks, 72);
            case 73:
                blocks.push(74);
                return "interface " + s(r, "name", "") + clause("extends", s(r, "interfaces", "")) + " {";
            case 74:
                return close(blocks, 74);
            case 75:
                return "import " + s(r, "importName", "") + ";";
            case 76:
                blocks.push(77);
                return "package " + s(r, "packageName", "") + " {";
            case 77:
                return close(blocks, 77);
            case 78:
            case 83:
                String namespace = s(r, "namespaceName", ""),
                 qualifier = s(r, "namespaceQualifier", "");
                if (namespace.isEmpty()) {
                    throw new IllegalArgumentException("Namespace definition has no stored name.");
                }
                return (qualifier.isEmpty() ? "" : qualifier + " ") + (type == 83 ? "config " : "") + "namespace " + namespace
                        + (s(r, "namespaceValue", "").isEmpty() ? "" : " = " + s(r, "namespaceValue", "")) + ";";
            case 80:
                blocks.push(81);
                return "for each (" + s(r, "iterator", "") + " in " + s(r, "collection", "") + ") {";
            case 81:
                return close(blocks, 81);
            case 82:
                return s(record(r, "parameter1"), "text", "") + ":";
            case 84:
                blocks.push(85);
                return s(r, "configuration", "") + " {";
            case 85:
                return close(blocks, 85);
            default:
                throw new IllegalArgumentException("Editor command still needs source reconstruction: " + type);
        }
    }

    private static String label(FlaRecord p) {
        String value = s(p, "text", "");
        return value.isEmpty() ? "" : " " + value;
    }

    private static void branch(Deque<Integer> stack, int type) {
        if (stack.isEmpty() || stack.peek() != type) {
            throw new IllegalArgumentException("Mismatched editor action block " + type);
        }
    }

    private static String close(Deque<Integer> stack, int type) {
        branch(stack, type);
        stack.pop();
        return "}";
    }

    private static String parameter(FlaRecord r, int index) {
        FlaRecord p = record(r, "parameter" + index);
        if (p == null) {
            throw new IllegalArgumentException("Missing editor action parameter.");
        }
        return param(s(p, "text", ""), n(p, "flags", 0));
    }

    private static String parameterText(FlaRecord r, int index) {
        FlaRecord p = record(r, "parameter" + index);
        if (p == null) {
            throw new IllegalArgumentException("Missing numeric editor action parameter.");
        }
        return s(p, "text", "");
    }

    private static String requestMethod(long id) {
        if (id < 0 || id > 2) {
            throw new IllegalArgumentException("Unassigned request method " + id);
        }
        return id == 0 ? "" : ", " + quote(id == 1 ? "GET" : "POST");
    }

    private static String visibility(long flags) {
        return (flags & 1) != 0 ? "public " : (flags & 2) != 0 ? "private " : "";
    }

    private static String clause(String keyword, String text) {
        return text.isEmpty() ? "" : " " + keyword + " " + text;
    }

    private static String invoke(FlaRecord r) {
        String signature = s(r, "invocationSignature", ""), object = s(r, "object", "");
        boolean method = signature.startsWith(".") || signature.startsWith("@");
        String name = signature;
        if (name.startsWith("@")) {
            int dot = name.indexOf('.');
            if (dot < 0) {
                throw new IllegalArgumentException("Qualified invocation has no method name.");
            }
            name = name.substring(dot);
        }
        if (name.startsWith(".")) {
            name = name.substring(1);
        }
        int open = name.indexOf('(');
        if (open >= 0) {
            name = name.substring(0, open);
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Invocation has no function name.");
        }
        int args = signature.indexOf('(');
        boolean hasArguments = args >= 0 && args + 1 < signature.length() && signature.charAt(args + 1) != ')';
        return (method ? param(object, n(r, "objectExpressionFlags", 0)) + "." : "") + name + "(" + (hasArguments ? s(r, "arguments", "") : "") + ");";
    }

    private static String param(String text, long flags) {
        return flags == 0 ? quote(text) : text;
    }

    private static String expr(FlaRecord r, String field) {
        return n(r, field + "IsExpression", 0) != 0 ? s(r, field, "") : quote(s(r, field, ""));
    }

    private static String expression(FlaRecord r, String field) {
        String value = s(r, field, "");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing historical expression " + field);
        }
        return value;
    }

    private static String quote(String value) {
        return "\"" + Escaper.escapeActionScriptString(value) + "\"";
    }

    private static String method(String value) {
        return value.isEmpty() ? "" : ", " + quote(value);
    }

    private static String url(FlaRecord r) {
        return "getURL(" + expr(r, "url") + ", " + expr(r, "window") + method(s(r, "method", "")) + ");";
    }

    private static String frame(FlaRecord r) {
        return frame(r, n(r, "frameNumber", 1));
    }

    public static String frame(FlaRecord r, long numericFrame) {
        String page = s(r, "page", "");
        String value = !s(r, "arg0", "").isEmpty() ? expr(r, "arg0") : !s(r, "label", "").isEmpty() ? quote(s(r, "label", "")) : Long.toString(numericFrame);
        return (page.isEmpty() ? "" : quote(page) + ", ") + value;
    }

    private static String go(FlaRecord r, boolean play) {
        return (play ? "gotoAndPlay" : "gotoAndStop") + "(" + frame(r) + ");";
    }

    private static String key(int code) {
        if (code >= 0 && code < OnCommand.KEYNAMES.length && OnCommand.KEYNAMES[code] != null) {
            return OnCommand.KEYNAMES[code];
        }
        if (code >= 32 && code <= 126) {
            return Character.toString((char) code);
        }
        throw new IllegalArgumentException("Unassigned event key code " + code);
    }
}
