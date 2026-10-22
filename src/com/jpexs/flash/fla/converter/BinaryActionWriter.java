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
package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.script.*;
import com.jpexs.flash.fla.script.commands.AbstractCommand;
import java.io.IOException;
import java.util.*;

/**
 * Binary action containers and historical command lowering.
 */
final class BinaryActionWriter {

    final BinaryArchiveWriter w;

    BinaryActionWriter(BinaryArchiveWriter w) {
        this.w = w;
    }

    void source(FlaRecord r, int maximum) throws IOException {
        int v = (int) Math.min(w.n(r, "revision", maximum), maximum);
        w.u32(v);
        FlaRecord compiled = w.record(r, "compiledCommands");
        boolean retainCompiled = compiled != null && compiled.isComplete() && !modified(r, Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>())) && w.session.source.getContentsRevision() <= w.target().contentsRevision
                && w.session.charset.equals(w.session.source.getAnsiCharset());
        String text = "";
        if (!retainCompiled) {
            text = reconstruct(r);
        }
        w.value(r, "source");
        w.value(r, "sourceData");
        w.value(r, "sourceUnicodeMarker");
        w.value(r, "sourceLength");
        w.value(r, "codeCollapseRanges");
        if (!retainCompiled && compiled != null) {
            w.issue("SCRIPT_RECONSTRUCTED", r, "compiledCommands", "Source retained; compiled editor representation replaced.");
            markTree(compiled, Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()));
        }
        if (r != null && (r.get("commands") instanceof List || r.get("kind") instanceof Number)) {
            w.issue("SCRIPT_RECONSTRUCTED", r, "commands", "Historical actions replaced by their source representation.");
            markTree(r, Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()));
        }
        w.u32(retainCompiled ? 0 : 1);
        w.value(r, "mode");
        boolean retainPositions = retainCompiled || r != null && r.get("source") instanceof String;
        if (v >= 1) {
            w.u32(r, "identifier");
            List<?> breakpoints = w.list(r, "breakpointIndices");
            if (!retainPositions && !breakpoints.isEmpty()) {
                w.issue("SCRIPT_EDITOR_METADATA", r, "breakpointIndices", "Positions omitted after source reconstruction.");
                breakpoints = Collections.emptyList();
            }
            w.u32(breakpoints.size());
            for (Object n : breakpoints) {
                w.u32(((Number) n).longValue());
            }
        }
        if (v >= 5) {
            List<?> ranges = w.list(r, "codeCollapseRangeWords");
            if (!retainPositions && !ranges.isEmpty()) {
                w.issue("SCRIPT_EDITOR_METADATA", r, "codeCollapseRangeWords", "Positions omitted after source reconstruction.");
                ranges = Collections.emptyList();
            }
            w.u32(ranges.size());
            for (Object n : ranges) {
                w.u32(((Number) n).longValue());
            }
        }
        if (retainCompiled) {
            w.raw(compiled.getConsumedData());
            markTree(compiled, Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()));
        } else if (v == 3) {
            w.length(text.length() * 2L);
            w.raw(w.textBytes(text, true));
        } else {
            w.string(text);
        }
    }

    private boolean modified(FlaRecord r, Set<FlaRecord> visited) {
        if (r == null || !visited.add(r)) {
            return false;
        }
        if (r.isModified()) {
            return true;
        }
        for (Object value : r.getFields().values()) {
            if (value instanceof FlaRecord && modified((FlaRecord) value, visited)) {
                return true;
            }
            if (value instanceof List) {
                for (Object entry : (List<?>) value) {
                    if (entry instanceof FlaRecord && modified((FlaRecord) entry, visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void markTree(FlaRecord r, Set<FlaRecord> visited) {
        if (!visited.add(r)) {
            return;
        }
        for (String key : r.getFields().keySet()) {
            Object v = w.value(r, key);
            if (v instanceof FlaRecord) {
                markTree((FlaRecord) v, visited);
            } else if (v instanceof List) {
                for (Object entry : (List<?>) v) {
                    if (entry instanceof FlaRecord) {
                        markTree((FlaRecord) entry, visited);
                    }
                }
            }
        }
    }

    private String reconstruct(FlaRecord r) {
        try {
            return FlaActionSource.reconstruct(r);
        } catch (IllegalArgumentException ex) {
            w.issue("SCRIPT_OMITTED", r, "actions", ex.getMessage());
            return "";
        }
    }

    void historical(FlaRecord r, boolean single) throws IOException {
        int maximum = w.target() == FlaFormatVersion.F1 ? 0 : w.target() == FlaFormatVersion.F2 ? 1 : w.target() == FlaFormatVersion.F3 ? 4 : 9;
        List<FlaRecord> commands = new ArrayList<>();
        if (r != null && r.get("kind") instanceof Number) {
            commands.add(r);
        } else {
            commands.addAll(w.records(r, "commands"));
        }
        if (commands.isEmpty() && !reconstruct(r).isEmpty()) {
            try {
                List<AbstractCommand> parsed = new ScriptParser(w.target()).parseComplete(reconstruct(r));
                for (AbstractCommand c : parsed) {
                    commands.add(command(c));
                }
            } catch (ScriptParseException ex) {
                w.issue("SCRIPT_OMITTED", r, "actions", ex.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Script conversion interrupted", ex);
            }
        }
        int largest = w.target() == FlaFormatVersion.F1 ? 12 : w.target() == FlaFormatVersion.F2 ? 13 : w.target() == FlaFormatVersion.F3 ? 21 : 38;
        for (FlaRecord command : commands) {
            if (w.n(command, "kind", 0) > largest) {
                w.issue("SCRIPT_OMITTED", r, "actions", "Command is not supported by the target language.");
                commands.clear();
                break;
            }
        }
        if (single) {
            if (commands.size() > 1) {
                w.issue("SCRIPT_TRUNCATED", r, "actions", "Target stores only one historical action.");
            }
            historicalCommand(commands.isEmpty() ? null : commands.get(0), maximum);
        } else {
            w.u32(commands.size());
            for (FlaRecord c : commands) {
                historicalCommand(c, maximum);
            }
        }
    }

    private void historicalCommand(FlaRecord r, int maximum) {
        int v = w.revision(r, "revision", maximum);
        w.u8(r, "kind");
        w.strings(r, "url", "window", "page");
        w.u16(w.n(r, "frameNumber", 1));
        if (v >= 1) {
            w.u16(w.n(r, "waitForExtraFrames", -16000));
        }
        if (v >= 2) {
            w.string(r, "label");
        }
        if (v >= 3) {
            w.strings(r, "arg0", "arg1", "target");
            w.u32(w.n(r, "level", 1));
        }
        if (v >= 4) {
            w.dwords(r, "release", "press", "releaseOutside", "rollOver", "rollOut", "dragOver", "dragOut");
        }
        if (v >= 5) {
            w.u32(w.n(r, "property", -1));
            w.strings(r, "reservedString1", "reservedString2", "depth", "variableName");
        }
        if (v >= 6) {
            w.string(r, "method");
            w.u32(r, "loadVariables");
        }
        if (v >= 7) {
            w.strings(r, "arg2", "arg3");
            w.dwords(r, "variableNameIsExpression", "urlIsExpression", "arg0IsExpression", "arg1IsExpression", "arg2IsExpression", "arg3IsExpression", "windowIsExpression", "targetIsExpression", "depthIsExpression", "start", "constrainToRectangle", "lockMouseToCenter");
        }
        if (v >= 8) {
            w.u32(r, "onKeyPress");
            w.u32(w.n(r, "key", -1));
        }
        if (v >= 9) {
            BinaryData d = w.data(r, "extraData");
            w.u32(d == null ? 0 : d.size());
            w.raw(d);
        }
    }

    private FlaRecord command(AbstractCommand c) {
        FlaRecord r = FlaRecord.create("historicalAction");
        r.set("kind", c.getActionKind());
        r.set("url", c.getUrl().value);
        r.set("window", c.getWindow().value);
        r.set("page", c.getPage());
        r.set("frameNumber", c.getFrameNum());
        r.set("waitForExtraFrames", c.getWaitForExtraFrames());
        r.set("label", c.getLabel());
        r.set("arg0", c.getArg0().value);
        r.set("arg1", c.getArg1().value);
        r.set("arg2", c.getArg2().value);
        r.set("arg3", c.getArg3().value);
        r.set("target", c.getTarget().value);
        r.set("level", c.getLevel());
        r.set("depth", c.getDepth().value);
        r.set("variableName", c.getVariableName().value);
        r.set("method", c.getMethod());
        r.set("property", c.getProperty());
        r.set("key", c.getKey());
        String[] fields = {"release", "press", "releaseOutside", "rollOver", "rollOut", "dragOver", "dragOut", "loadVariables", "onKeyPress", "start", "constrainToRectangle", "lockMouseToCenter"};
        boolean[] values = {c.onRelease(), c.onPress(), c.onReleaseOutside(), c.onRollOver(), c.onRollOut(), c.onDragOver(), c.onDragOut(), c.doesLoadVariables(), c.onKeyPress(), c.isStart(), c.hasConstrainToRectangle(), c.hasLockMouseToCenter()};
        for (int i = 0; i < fields.length; i++) {
            r.set(fields[i], values[i] ? 1 : 0);
        }
        String[] names = {"variableName", "url", "arg0", "arg1", "arg2", "arg3", "window", "target", "depth"};
        Expression[] expressions = {c.getVariableName(), c.getUrl(), c.getArg0(), c.getArg1(), c.getArg2(), c.getArg3(), c.getWindow(), c.getTarget(), c.getDepth()};
        for (int i = 0; i < names.length; i++) {
            r.set(names[i] + "IsExpression", expressions[i].isRaw ? 0 : 1);
        }
        return r;
    }
}
