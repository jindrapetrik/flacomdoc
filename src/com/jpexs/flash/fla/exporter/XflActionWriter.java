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
package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.FlaRecord;
import static com.jpexs.flash.fla.script.FlaActionSource.*;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Reconstructs supported editor actions atomically; never executes expressions.
 */
final class XflActionWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflActionWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void actions(Element parent, FlaRecord actions) {
        if (actions == null) {
            return;
        }
        String source = s(actions, "source", "");
        if (!source.isEmpty()) {
            emit(parent, source, actions);
            return;
        }
        List<FlaRecord> commands = records(actions, "commands");
        boolean compiled = false;
        FlaRecord list = record(actions, "compiledCommands");
        if (list != null) {
            commands = records(list, "commands");
            compiled = true;
        }
        if (actions.get("kind") instanceof Number) {
            commands = Collections.singletonList(actions);
        }
        if (commands.isEmpty()) {
            return;
        }
        if (compiled) {
            String archived = archivedSource(commands);
            if (archived != null) {
                boolean event = commands.stream().anyMatch(c -> n(c, "type", -1) == 11 || n(c, "type", -1) == 13);
                if (parent.getTagName().equals("DOMSymbolInstance") && parent.getAttribute("symbolType").equals("button") && !event) {
                    archived = "on (release) {\n" + archived + "\n}\n";
                }
                emit(parent, archived, actions);
                return;
            }
        }
        Deque<Integer> blocks = new ArrayDeque<>();
        StringBuilder out = new StringBuilder();
        try {
            boolean hasEvent = false;
            for (FlaRecord command : commands) {
                String line = compiled ? compiled(command, blocks) : historical(command, blocks);
                int kind = (int) n(command, "kind", -1);
                if (!compiled && actions.get("kind") instanceof Number && (kind == 8 || kind == 10 || kind == 11 || kind == 12)
                        && n(command, "waitForExtraFrames", -16000) != -16000) {
                    line = "ifFrameLoaded (" + frame(command, n(command, "frameNumber", 1) + n(command, "waitForExtraFrames", 0)) + ") {\n" + line + "\n}";
                }
                if (line.startsWith("on (") || line.startsWith("onClipEvent (")) {
                    hasEvent = true;
                }
                if (!line.isEmpty()) {
                    out.append(line).append('\n');
                }
            }
            if (!blocks.isEmpty()) {
                throw new IllegalArgumentException("Unclosed editor action block.");
            }
            if (out.length() == 0) {
                return;
            }
            source = out.toString();
            if (parent.getTagName().equals("DOMSymbolInstance") && parent.getAttribute("symbolType").equals("button") && !hasEvent) {
                source = "on (release) {\n" + source + "}\n";
            }
            emit(parent, source, actions);
        } catch (IllegalArgumentException ex) {
            session.issue(compiled ? "COMPILED_SCRIPT" : "HISTORICAL_SCRIPT", actions, "Action source omitted atomically: " + ex.getMessage());
        }
    }

    /**
     * Complete editor text consists of prefix, body and suffix, in that order.
     * Keep it verbatim: suppressed delimiters and unbraced statements cannot be
     * reconstructed from a delimiter flag alone. Older lists use the templates.
     */
    private void emit(Element parent, String source, FlaRecord record) {
        Element actionscript = child(parent, "Actionscript");
        if (record.get("source") instanceof String) {
            integerListAttribute(actionscript, "breakpoints", record, "breakpointIndices");
            integerListAttribute(actionscript, "offsets", record, "codeCollapseRangeWords");
        }
        cdata(actionscript, "script", session.text(source, record));
    }

    private void integerListAttribute(Element out, String attribute, FlaRecord record, String field) {
        Object value = record.get(field);
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            return;
        }
        List<?> values = (List<?>) value;
        if ("offsets".equals(attribute) && (values.size() & 1) != 0) {
            session.issue("ACTION_EDITOR_METADATA", record, "Unpaired code-collapse offset/length words retained in the reader.");
            return;
        }
        StringJoiner text = new StringJoiner(",");
        for (Object item : values) {
            if (!(item instanceof Number)) {
                session.issue("ACTION_EDITOR_METADATA", record, "Invalid action editor metadata omitted as a whole.");
                return;
            }
            text.add(Integer.toString(((Number) item).intValue()));
        }
        out.setAttribute(attribute, text.toString());
    }

}
