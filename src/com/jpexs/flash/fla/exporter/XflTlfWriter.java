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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Preserves archived TLF markup and its actual direct-child associations.
 */
final class XflTlfWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflTlfWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void text(Element parent, FlaRecord record, int depth) {
        try {
            FlaTlfText text = record.getTlfText();
            if (text == null || text.getMarkup() == null) {
                throw new IOException("Missing TLF markup.");
            }
            // Bound input and reject entities before constructing the namespace-aware DOM.
            List<FlaXmlElement> roots = text.parseMarkup();
            if (roots.size() != 1) {
                throw new IOException("Expected one tlfTextObject root in the archived source field.");
            }
            String xml = text.getMarkup();
            if (xml.startsWith("\ufeff")) {
                xml = xml.substring(1);
            }
            if (xml.startsWith("<?xml ")) {
                xml = xml.substring(xml.indexOf("?>") + 2);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document source = factory.newDocumentBuilder().parse(new ByteArrayInputStream(("<wrapper xmlns=\"" + NS + "\">" + xml + "</wrapper>").getBytes(StandardCharsets.UTF_8)));
            Element markup = null;
            for (Node n = source.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) {
                    markup = (Element) n;
                }
            }
            if (markup == null || !"tlfTextObject".equals(markup.getLocalName()) || !NS.equals(markup.getNamespaceURI()) || markup.getElementsByTagNameNS("http://ns.adobe.com/textLayout/2008", "TextFlow").getLength() != 1) {
                throw new IOException("Expected one namespaced TextFlow in the TLF container.");
            }
            Element output = parent.getOwnerDocument().createElementNS(NS, "DOMTLFText");
            output.setAttribute("selected", Boolean.toString((n(record, "flags", 0) & 2) != 0));
            output.setAttribute("locked", Boolean.toString((n(record, "flags", 0) & 4) != 0));
            output.setAttribute("isInBackground", Boolean.toString((n(record, "flags", 0) & 8) != 0));
            if (!s(record, "identifier", "").isEmpty()) {
                session.attribute(output, "referenceID", s(record, "identifier", ""), record);
            }
            if (text.getLocalizationStringId() != null) {
                session.attribute(output, "localizationStringID", text.getLocalizationStringId(), record);
            }
            if (text.getVariableName() != null) {
                session.attribute(output, "variableName", text.getVariableName(), record);
            }
            FlaBounds bounds = record.getBounds();
            if (bounds != null && bounds.isDefined()) {
                output.setAttribute("left", Integer.toString(bounds.getXMinTwips()));
                output.setAttribute("right", Integer.toString(bounds.getXMaxTwips()));
                output.setAttribute("top", Integer.toString(bounds.getYMinTwips()));
                output.setAttribute("bottom", Integer.toString(bounds.getYMaxTwips()));
            }
            if (record.getMatrix() == null) {
                throw new IOException("Missing TLF placement matrix.");
            }
            matrix(output, record.getMatrix());
            XflObjectWriter.transformPoint(session, output, record);
            // Font name mappings are a generated cache, not either auxiliary string.
            child(output, "tlfFonts");
            child(output, "markup").appendChild(output.getOwnerDocument().importNode(markup, true));
            Element references = output.getOwnerDocument().createElementNS(NS, "markupElements");
            for (FlaRecord relation : records(record(record, "instanceRelations"), "entries")) {
                long type = n(relation, "value", -1);
                if (type == 0 || type == 1) {
                    reference(references, s(relation, "identifier", ""), -1, type == 0 ? "previous" : "next", relation);
                } else {
                    session.issue("TLF_RELATION", relation, "Unknown connection type retained in the reader: " + type);
                }
            }
            List<FlaTlfText.ChildLink> links = text.getChildLinks();
            if (links == null) {
                throw new IOException("Missing TLF child association list.");
            }
            Element children = output.getOwnerDocument().createElementNS(NS, "members");
            Set<FlaRecord> seen = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
            Set<String> identifiers = new HashSet<>();
            for (FlaTlfText.ChildLink link : links) {
                FlaRecord child = link.getChild();
                if (child == null || !record.getChildren().contains(child) || !seen.add(child) || link.getMarkupChildId() == null || link.getIdentifier() == null || link.getIdentifier().isEmpty() || !identifiers.add(link.getIdentifier())) {
                    throw new IOException("Invalid or duplicate direct-child association.");
                }
            }
            for (FlaRecord child : record.getChildren()) {
                if (!s(child, "identifier", "").isEmpty() && !seen.contains(child)) {
                    throw new IOException("A named direct child has no archived association.");
                }
            }
            for (FlaTlfText.ChildLink link : links) {
                FlaRecord child = link.getChild();
                int before = children.getChildNodes().getLength();
                session.element(children, child, depth + 1);
                if (children.getChildNodes().getLength() != before + 1) {
                    throw new IOException("Embedded child could not be exported as one display object.");
                }
                Element display = (Element) children.getLastChild();
                session.attribute(display, "referenceID", link.getIdentifier(), child);
                reference(references, link.getIdentifier(), (int) (long) link.getMarkupChildId(), "inline", child);
            }
            if (references.hasChildNodes()) {
                output.appendChild(references);
            }
            if (children.hasChildNodes()) {
                output.appendChild(children);
            }
            XflObjectWriter object = new XflObjectWriter(session);
            object.placement(output, record);
            object.filters(output, record);
            object.colorEffect(output, record);
            XflPersistentDataWriter.write(session, output, record);
            parent.appendChild(output);
        } catch (Exception ex) {
            session.issue("TLF_DATA", record, "TLF object omitted: " + ex.getMessage());
        }
    }

    private void reference(Element parent, String id, int value, String type, FlaRecord record) throws IOException {
        if (id.isEmpty()) {
            throw new IOException("Empty markup reference identifier.");
        }
        Element item = child(parent, "MarkupElement");
        session.attribute(item, "referenceID", id, record);
        item.setAttribute("markupChildID", Integer.toString(value));
        item.setAttribute("referenceType", type);
    }
}
