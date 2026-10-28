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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Component declarations and per-instance Inspectable values.
 */
final class XflComponentReader {

    private static final List<String> TYPES = Arrays.asList("", "Default", "Array", "Object", "List", "String", "Number", "Boolean", "Font Name", "Color", "Web Service Url", "Web Service Operation", "Collection Item", "Collection", "Category", "Class", "Video Cue Points", "Video Skin", "Video Content Path", "Video Preview");
    private final XflReader.Session session;

    XflComponentReader(XflReader.Session session) {
        this.session = session;
    }

    FlaRecord component(Element e) throws IOException {
        if (!tag(e).equals("DOMComponentItem") && !tag(e).equals("DOMCompiledClipItem") && child(e, "parametersAsXML") == null
                && child(e, "classProperties") == null && !e.hasAttribute("livePreviewScmHRef")) {
            return null;
        }
        Element classProperties = child(e, "classProperties");
        FlaRecord c = r("component", "revision", XflReader.Session.V.documentPageComponentMetadataRevision,
                "componentEnabled", 1L, "tooltip", s(e, "tooltip", ""), "customIconId", i(e, "customIconID", 0),
                "parametersAreLocked", b(e, "parametersAreLocked", true) ? 1L : 0L,
                "descriptionType", choice(e, "descriptionType", 0, "none", "reference", "text"),
                "actionScriptClass", s(e, "actionscriptClass", ""), "symbolDescription", s(e, "symbolDescription", ""),
                "properties", parameters(e), "classPropertiesXml", classProperties == null ? "" : classProperties.getTextContent(),
                "movies", Arrays.asList(movie(e, "swfScm"), movie(e, "livePreviewScm"), movie(e, "customUIScm")),
                "editFrameIndex", i(e, "editFrameIndex", 0),
                "requiredMinimumPlayerVersionName", s(e, "requiredMinimumPlayerVersionName", ""),
                "requiredMinimumPlayerVersion", i(e, "requiredMinimumPlayerVersion", 0),
                "requiredMinimumASVersion", i(e, "requiredMinimumASVersion", 0));
        if (e.hasAttribute("livePreview11ScmHRef")) {
            c.set("livePreview11Movie", movie(e, "livePreview11Scm"));
        }
        FlaRecord icon = new XflCompiledClipReader(session).icon(nested(e, "customIcon", "CustomIcon"));
        if (icon != null) {
            c.set("embeddedMedia", icon);
        }
        return c;
    }

    private FlaRecord movie(Element e, String prefix) throws IOException {
        String href = s(e, prefix + "HRef", "");
        byte[] data = href.isEmpty() ? new byte[0] : session.resource("bin/" + href, true);
        return r("smartClipMovie", "revision", 2, "name", s(e, prefix + "SourceFilename", ""),
                "data", new BinaryData(data), "byteCount", data.length, "reserved", 0L);
    }

    FlaRecord parameters(Element parent) throws IOException {
        Element xml = child(parent, "parametersAsXML");
        if (xml == null) {
            return XflObjectReader.properties(child(parent, "parameters"));
        }
        Element root = parse(("<parameters>" + xml.getTextContent() + "</parameters>").getBytes(StandardCharsets.UTF_8), "parametersAsXML").getDocumentElement();
        List<FlaRecord> entries = new ArrayList<>();
        Set<String> variables = new HashSet<>();
        for (Element p : children(root, "property")) {
            Element inspect = child(p, "Inspectable");
            if (inspect == null) {
                throw new IOException("Component property has no Inspectable declaration");
            }
            String variable = s(inspect, "variable", s(p, "id", ""));
            // Native MX framework assets contain an anonymous, empty default
            // parameter. Preserve it as well as named public parameters.
            if (!variable.isEmpty() && !variables.add(variable)) {
                throw new IOException("Duplicate component parameter: " + variable);
            }
            int type = TYPES.indexOf(s(inspect, "type", "Default"));
            if (type < 1 || type == 13) {
                throw new IOException("Unsupported Inspectable parameter type: " + s(inspect, "type", ""));
            }
            String text = s(inspect, "defaultValue", "");
            List<FlaRecord> values = new ArrayList<>();
            int selected = 0;
            boolean named = type == 3 || type == 16;
            if (type == 2 || named || type == 4) {
                List<String> tokens = split(type == 4 ? s(inspect, "enumeration", "") : text);
                for (int i = 0; i < tokens.size(); i++) {
                    String token = tokens.get(i), name = "";
                    if (type == 3) {
                        int colon = token.indexOf(':');
                        if (colon < 0) {
                            throw new IOException("Object component parameter requires name:value entries");
                        }
                        name = token.substring(0, colon);
                        token = token.substring(colon + 1);
                    } else if (type == 16) {
                        name = token;
                        if (++i >= tokens.size()) {
                            throw new IOException("Odd cue-point parameter list");
                        }
                        token = tokens.get(i);
                    }
                    values.add(r("propertyValueEntry", "name", decode(name), "value", decode(token)));
                }
                if (type == 4) {
                    selected = -1;
                    for (int i = 0; i < values.size(); i++) {
                        if (text.equals(values.get(i).get("value"))) {
                            selected = i;
                            break;
                        }
                    }
                    if (selected < 0) {
                        selected = values.size();
                        if (!text.isEmpty()) {
                            values.add(r("propertyValueEntry", "value", text));
                        }
                    }
                }
            } else {
                values.add(r("propertyValueEntry", "value", text));
            }
            UUID uuid = UUID.nameUUIDFromBytes(variable.getBytes(StandardCharsets.UTF_8));
            byte[] id = ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
            entries.add(r("property", "revision", 3, "name", s(inspect, "name", variable), "variableName", variable,
                    "identifier", r("identifier", "revision", 0, "data", new BinaryData(id)),
                    "category", s(inspect, "category", ""), "verbosity", i(inspect, "verbose", 0),
                    "value", r("value", "revision", 1, "type", type, "entries", values,
                            "selectedIndex", selected, "namedEntries", named ? 1L : 0L)));
        }
        return r("properties", "revision", 2, "entries", entries, "parametersAreNew", b(parent, "parametersAreNew", false) ? 1L : 0L);
    }

    private static List<String> split(String text) {
        return text.isEmpty() ? Collections.emptyList() : Arrays.asList(text.split(",", -1));
    }

    private static String decode(String text) {
        return text.replace("&#2C", ",");
    }
}
