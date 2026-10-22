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
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Embeds the archived motion XML, without evaluating or regenerating curves.
 */
final class XflMotionWriter {

    static void motion(BinaryFlaToXflExporter.Session session, Element frame, FlaRecord r) {
        if ((n(r, "keyMode", 0) & 255) != 3) {
            return;
        }
        try {
            String xml = s(r, "motionXml", "");
            java.util.List<FlaXmlElement> roots = FlaXmlElement.parseFragment(xml);
            if (roots.size() != 1 || !"AnimationCore".equals(roots.get(0).getName())) {
                throw new IOException("Expected a single AnimationCore root.");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            String wrapped = "<wrapper xmlns=\"" + NS + "\">" + xml + "</wrapper>";
            Document source = factory.newDocumentBuilder().parse(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));
            Element out = frame.getOwnerDocument().createElementNS(NS, "motionObjectXML");
            for (Node n = source.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
                out.appendChild(frame.getOwnerDocument().importNode(n, true));
            }
            frame.appendChild(out);
            frame.setAttribute("tweenType", "motion object");
            frame.setAttribute("isMotionObject", "true");
            frame.setAttribute("motionTweenRotate", "none");
            frame.setAttribute("motionTweenScale", "false");
            frame.setAttribute("visibleAnimationKeyframes", Long.toString(n(r, "visibleAnimationKeyframes", 0)));
            if (!s(r, "tweenInstanceName", "").isEmpty()) {
                session.attribute(frame, "tweenInstanceName", s(r, "tweenInstanceName", ""), r);
            }
        } catch (Exception ex) {
            session.issue("MOTION_XML", r, "Motion object XML omitted: " + ex.getMessage());
        }
    }
}
