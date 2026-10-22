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

import com.jpexs.flash.fla.reader.*;
import java.io.IOException;
import java.util.*;
import org.w3c.dom.*;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Component library metadata, archived previews and inspectable parameters.
 */
final class XflComponentWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflComponentWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    static boolean isComponent(FlaRecord descriptor) {
        if (n(descriptor, "symbolType", 2) != 2) {
            return false;
        }
        FlaRecord c = record(descriptor, "component");
        if (c == null) {
            if (!legacyDescriptor(descriptor)) {
                return false;
            }
            if (n(descriptor, "legacyComponentEnabled", 0) != 0 || !records(record(descriptor, "properties"), "entries").isEmpty()) {
                return true;
            }
            List<FlaRecord> movies = records(descriptor, "legacyMovies");
            if (movies.isEmpty()) {
                return !s(descriptor, "legacyCustomUISourceFilename", "").isEmpty();
            }
            for (FlaRecord movie : movies) {
                if (hasMovie(movie)) {
                    return true;
                }
            }
            return false;
        }
        if (n(c, "componentEnabled", 0) != 0 || !s(c, "classPropertiesXml", "").isEmpty()
                || !records(record(c, "properties"), "entries").isEmpty()) {
            return true;
        }
        List<FlaRecord> movies = records(c, "movies");
        // A primary compiled movie alone does not make an ordinary symbol a component.
        for (int i = 1; i < movies.size(); i++) {
            if (hasMovie(movies.get(i))) {
                return true;
            }
        }
        return hasMovie(record(c, "livePreview11Movie"));
    }

    private static boolean legacyDescriptor(FlaRecord descriptor) {
        long revision = n(descriptor, "revision", -1);
        return revision >= 10 && revision < 18;
    }

    private static boolean hasMovie(FlaRecord movie) {
        return n(movie, "byteCount", 0) > 0 || !s(movie, "name", "").isEmpty();
    }

    void itemAttributes(Element item, FlaRecord descriptor, int ordinal) {
        FlaRecord c = record(descriptor, "component");
        if (c == null && legacyDescriptor(descriptor)) {
            legacyAttributes(item, descriptor, ordinal);
            return;
        }
        session.attribute(item, "tooltip", s(c, "tooltip", ""), c);
        session.attribute(item, "actionscriptClass", s(c, "actionScriptClass", ""), c);
        if (!s(c, "symbolDescription", "").isEmpty()) {
            session.attribute(item, "symbolDescription", s(c, "symbolDescription", ""), c);
        }
        if (!s(c, "requiredMinimumPlayerVersionName", "").isEmpty()) {
            session.attribute(item, "requiredMinimumPlayerVersionName", s(c, "requiredMinimumPlayerVersionName", ""), c);
        }
        item.setAttribute("customIconID", Long.toString(n(c, "customIconId", 0)));
        String[] attrs = {"editFrameIndex", "requiredMinimumPlayerVersion", "requiredMinimumASVersion"};
        String[] fields = {"editFrameIndex", "requiredMinimumPlayerVersion", "requiredMinimumASVersion"};
        for (int i = 0; i < attrs.length; i++) {
            if (c.get(fields[i]) instanceof Number) {
                item.setAttribute(attrs[i], Long.toString(n(c, fields[i], 0)));
            }
        }
        item.setAttribute("parametersAreLocked", Boolean.toString(n(c, "parametersAreLocked", 1) != 0));
        descriptionType(item, c, "descriptionType");
        FlaRecord props = record(c, "properties");
        if (props != null && props.get("parametersAreNew") instanceof Number) {
            item.setAttribute("parametersAreNew", Boolean.toString(n(props, "parametersAreNew", 0) != 0));
        }
        List<FlaRecord> movies = records(c, "movies");
        // Native archive order: primary compiled movie, live preview, Custom UI.
        for (int i = 0; i < movies.size(); i++) {
            FlaRecord movie = movies.get(i);
            Object data = movie.get("data");
            String attr = i == 0 ? "swfScm" : i == 1 ? "livePreviewScm" : "customUIScm";
            if (!s(movie, "name", "").isEmpty()) {
                session.attribute(item, attr + "SourceFilename", s(movie, "name", ""), movie);
            }
            if (!(data instanceof BinaryData) || ((BinaryData) data).size() == 0) {
                continue;
            }
            String path = "component-" + ordinal + "-" + i + ".swf";
            item.setAttribute(attr + "HRef", path);
            session.files.put("bin/" + path, (BinaryData) data);
        }
        FlaRecord extension = record(c, "livePreview11Movie");
        if (extension != null && !s(extension, "name", "").isEmpty()) {
            session.attribute(item, "livePreview11ScmSourceFilename", s(extension, "name", ""), extension);
        }
        if (extension != null && extension.get("data") instanceof BinaryData && ((BinaryData) extension.get("data")).size() > 0) {
            String path = "component-" + ordinal + "-11.swf";
            item.setAttribute("persistLivePreview11", "true");
            item.setAttribute("livePreview11ScmHRef", path);
            session.attribute(item, "livePreview11ScmSourceFilename", s(extension, "name", ""), extension);
            session.files.put("bin/" + path, (BinaryData) extension.get("data"));
        }
    }

    void children(Element item, FlaRecord descriptor, int ordinal) throws IOException {
        FlaRecord c = record(descriptor, "component");
        if (c == null && legacyDescriptor(descriptor)) {
            parameters(item, record(descriptor, "properties"));
            return;
        }
        parameters(item, record(c, "properties"));
        if (!s(c, "classPropertiesXml", "").isEmpty()) {
            child(item, "classProperties").setTextContent(session.text(s(c, "classPropertiesXml", ""), c));
        }
        List<FlaRecord> tasks = records(c, "tasks");
        if (!tasks.isEmpty()) {
            Element container = child(item, "tasks");
            for (FlaRecord task : tasks) {
                if (task == null || !task.isComplete()) {
                    session.issue("COMPONENT_TASK", c, "Incomplete component task omitted.");
                    continue;
                }
                Element out = child(container, "Task");
                session.attribute(out, "description", s(task, "description", ""), task);
                session.attribute(out, "script", s(task, "script", ""), task);
            }
        }
        FlaRecord raster = record(record(record(c, "embeddedMedia"), "payload"), "raster");
        if (raster != null) try {
            session.clipWriter.customIcon(item, raster, "component-icon-" + ordinal + ".dat");
        } catch (IOException ex) {
            session.issue("COMPONENT_ICON", raster, ex.getMessage());
        }
    }

    private void descriptionType(Element item, FlaRecord record, String field) {
        if (!(record.get(field) instanceof Number)) {
            return;
        }
        long type = n(record, field, 0);
        String[] types = {"none", "reference", "text"};
        if (type >= 0 && type < types.length) {
            item.setAttribute("descriptionType", types[(int) type]);
        } else {
            session.issue("COMPONENT_DESCRIPTION_TYPE", record, "Unassigned component description type " + type);
        }
    }

    private void legacyAttributes(Element item, FlaRecord descriptor, int ordinal) {
        session.attribute(item, "tooltip", s(descriptor, "legacyComponentTooltip", ""), descriptor);
        // The native loader discards icon selectors outside its 25 built-in icons.
        long icon = n(descriptor, "legacyComponentIconId", 0);
        item.setAttribute("customIconID", Long.toString(icon >= 25 ? 0 : icon));
        FlaRecord properties = record(descriptor, "properties");
        item.setAttribute("parametersAreLocked", Boolean.toString(n(descriptor, "legacyParametersAreLocked", 1) != 0));
        if (descriptor.get("legacyDescriptionType") instanceof Number) {
            descriptionType(item, descriptor, "legacyDescriptionType");
        } else {
            item.setAttribute("descriptionType", s(descriptor, "legacySymbolDescription", "").isEmpty() ? "none" : "text");
        }
        if (!s(descriptor, "legacySymbolDescription", "").isEmpty()) {
            session.attribute(item, "symbolDescription", s(descriptor, "legacySymbolDescription", ""), descriptor);
        }
        if (properties != null && properties.get("parametersAreNew") instanceof Number) {
            item.setAttribute("parametersAreNew", Boolean.toString(n(properties, "parametersAreNew", 0) != 0));
        }
        List<FlaRecord> movies = records(descriptor, "legacyMovies");
        if (movies.isEmpty()) {
            if (!s(descriptor, "legacyCustomUISourceFilename", "").isEmpty()) {
                session.attribute(item, "customUIScmSourceFilename", s(descriptor, "legacyCustomUISourceFilename", ""), descriptor);
            }
        } else {
            // Old descriptor order is Custom UI, live preview. It differs from
            // the three slots of the later component record.
            for (int i = 0; i < movies.size(); i++) {
                FlaRecord movie = movies.get(i);
                String attr = i == 0 ? "customUIScm" : "livePreviewScm";
                if (!s(movie, "name", "").isEmpty()) {
                    session.attribute(item, attr + "SourceFilename", s(movie, "name", ""), movie);
                }
                Object data = movie.get("data");
                if (data instanceof BinaryData && ((BinaryData) data).size() > 0) {
                    String path = "component-" + ordinal + "-legacy-" + i + ".swf";
                    item.setAttribute(attr + "HRef", path);
                    session.files.put("bin/" + path, (BinaryData) data);
                }
            }
        }
    }

    void parameters(Element parent, FlaRecord properties) {
        List<FlaRecord> entries = records(properties, "entries");
        if (entries.isEmpty()) {
            return;
        }
        try {
            Document d = document("parameters");
            for (FlaRecord p : entries) {
                FlaRecord value = record(p, "value");
                boolean legacy = n(properties, "revision", -1) == 0 && p.get("value") instanceof String;
                int type = (int) n(legacy ? p : value, "type", -1);
                String[] names = {null, "Default", "Array", "Object", "List", "String", "Number", "Boolean", "Font Name", "Color", "Web Service Url", "Web Service Operation", "Collection Item", "Collection", "Category", "Class", "Video Cue Points", "Video Skin", "Video Content Path", "Video Preview"};
                if (!p.isComplete() || !legacy && (value == null || !value.isComplete()) || type < 1 || type >= names.length || n(value, "dataLength", 0) != 0 && type != 19) {
                    throw new IOException("Unsupported or incomplete component parameter type " + type);
                }
                List<FlaRecord> values = records(value, "entries");
                Element property = d.createElement("property");
                d.getDocumentElement().appendChild(property);
                String variable = s(p, "variableName", s(p, "name", ""));
                if (variable.isEmpty()) {
                    variable = s(p, "name", "");
                }
                property.setAttribute("id", session.text(variable, p));
                Element inspect = d.createElement("Inspectable");
                property.appendChild(inspect);
                inspect.setAttribute("name", session.text(s(p, "name", ""), p));
                inspect.setAttribute("variable", session.text(variable, p));
                inspect.setAttribute("category", session.text(s(p, "category", ""), p));
                inspect.setAttribute("verbose", Long.toString(n(p, "verbosity", 0)));
                inspect.setAttribute("type", names[type]);
                if (type == 19 && n(value, "dataLength", 0) != 0) {
                    session.issue("COMPONENT_PARAMETER_CACHE", value, "Archived video preview cache is not part of native Inspectable XML; parameter value retained.");
                }
                inspect.setAttribute("defaultValue", session.text(legacy ? legacyValue(p, type) : defaultValue(value), p));
                if (legacy && (type == 2 || type == 3 || type == 4 || type == 13 || type == 16) && !s(p, "value", "").isEmpty()) {
                    session.issue("COMPONENT_PARAMETER_UPGRADE", p, "Native R0 loading resets this compound parameter to an empty value; original text remains in the input model.");
                }
                if (type == 4) {
                    long selected = n(value, "selectedIndex", 0);
                    inspect.setAttribute("enumeration", session.text(selected < values.size() ? joined(values, false, false) : "", p));
                }
            }
            // Only serialize the fragment's children; the helper root is not XFL.
            javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter text = new java.io.StringWriter();
            for (Node n = d.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
                t.transform(new javax.xml.transform.dom.DOMSource(n), new javax.xml.transform.stream.StreamResult(text));
            }
            child(parent, "parametersAsXML").setTextContent(text.toString());
        } catch (Exception ex) {
            session.issue("COMPONENT_PARAMETERS", properties, "Complete parameter list omitted: " + ex.getMessage());
        }
    }

    private static String legacyValue(FlaRecord property, int type) throws IOException {
        String value = s(property, "value", "");
        // Early lists store one textual value; the native loader normalizes
        // scalar number/boolean/color types before generating Inspectable XML.
        // The R0 loader resets the typed value before assigning its text.
        // Compound setters do not parse that text; lists have no entries to
        // select. Match the native upgrade, retaining the raw text in the model.
        if (type == 2 || type == 3 || type == 4 || type == 16) {
            return "";
        }
        if (type == 13) {
            return ", , , 0, 0";
        }
        if (type == 6 && !value.trim().matches("[+0-9.,-]+")) {
            return "0";
        }
        if (type == 7 && !value.equals("true") && !value.equals("false")) {
            return "false";
        }
        if (type == 9) {
            if (!value.startsWith("#")) {
                value = "#" + value;
            }
            if (value.length() < 7 || !value.substring(1, 7).matches("[0-9a-fA-F]{6}")) {
                return "#000000";
            }
            return value.substring(0, 7);
        }
        return value;
    }

    private static String defaultValue(FlaRecord value) throws IOException {
        int type = (int) n(value, "type", -1);
        List<FlaRecord> values = records(value, "entries");
        if (type == 2 || type == 3 || type == 16) {
            return joined(values, type == 3, type == 16);
        }
        if (type == 13) {
            return collection(record(value, "table"));
        }
        long selected = type == 4 ? n(value, "selectedIndex", 0) : 0;
        if (type != 4 && values.size() > 1) {
            throw new IOException("Scalar component parameter has multiple values.");
        }
        return selected >= 0 && selected < values.size() ? s(values.get((int) selected), "value", "") : "";
    }

    private static String joined(List<FlaRecord> values, boolean object, boolean pairs) throws IOException {
        StringJoiner text = new StringJoiner(",");
        for (FlaRecord value : values) {
            String item = listValue(s(value, "value", ""));
            if (object) {
                item = listValue(s(value, "name", "")) + ":" + item;
            } else if (pairs) {
                text.add(listValue(s(value, "name", "")));
            }
            text.add(item);
        }
        return text.toString();
    }

    /**
     * The comma token is decoded after the embedded Inspectable XML is parsed.
     */
    private static String listValue(String value) throws IOException {
        if (value.contains("&#2C")) {
            throw new IOException("Parameter value contains the reserved native comma token.");
        }
        return value.replace(",", "&#2C");
    }

    private static String collection(FlaRecord table) throws IOException {
        if (table == null || !table.isComplete()) {
            throw new IOException("Incomplete component collection table.");
        }
        StringBuilder text = new StringBuilder();
        for (String field : Arrays.asList("collectionClass", "collectionItemClass", "firstColumnName")) {
            String header = s(table, field, "");
            if (header.contains(",")) {
                throw new IOException("Collection header contains a native field separator.");
            }
            text.append(header).append(", ");
        }
        List<FlaRecord> columns = records(table, "columns"), rows = records(table, "rows");
        text.append(columns.size());
        for (FlaRecord column : columns) {
            String name = s(column, "name", ""), defaultValue = s(column, "defaultValue", "");
            if (name.contains(",") || defaultValue.contains(",")) {
                throw new IOException("Collection column contains a native field separator.");
            }
            text.append(", ").append(name).append(", ").append(n(column, "type", 0)).append(", ").append(defaultValue)
                    .append(", ").append(s(column, "enumeration", "").replace(',', '|'));
        }
        text.append(", ").append(rows.size());
        for (FlaRecord row : rows) {
            Object cells = row.get("cells");
            if (!(cells instanceof List) || ((List<?>) cells).size() != columns.size()) {
                throw new IOException("Incomplete collection row.");
            }
            for (Object cell : (List<?>) cells) {
                if (!(cell instanceof String)) {
                    throw new IOException("Non-string collection cell.");
                }
                text.append(", ").append(listValue((String) cell));
            }
        }
        return text.toString();
    }

}
