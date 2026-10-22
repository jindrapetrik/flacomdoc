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

import com.jpexs.flash.fla.reader.FlaMatrix;
import com.jpexs.flash.fla.reader.FlaRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class XflXml {

    static final String NS = "http://ns.adobe.com/xfl/2008/";

    private XflXml() {
    }

    static Document document(String rootName) throws IOException {
        try {
            Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            d.appendChild(d.createElementNS(NS, rootName));
            return d;
        } catch (ParserConfigurationException ex) {
            throw new IOException("Cannot create XFL XML", ex);
        }
    }

    static Element child(Element parent, String name) {
        Element e = parent.getOwnerDocument().createElementNS(NS, name);
        parent.appendChild(e);
        return e;
    }

    static byte[] serialize(Document d) throws IOException {
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            // Pretty-printing can introduce significant whitespace between TLF spans.
            t.setOutputProperty(OutputKeys.INDENT, d.getElementsByTagNameNS("http://ns.adobe.com/textLayout/2008", "TextFlow").getLength() == 0 ? "yes" : "no");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(new DOMSource(d), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException ex) {
            throw new IOException("Cannot serialize XFL XML", ex);
        }
    }

    static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String integerPointCoordinate(int value) {
        return "#" + Integer.toHexString(value >>> 8 & 0xFFFFFF).toUpperCase(Locale.ROOT)
                + "." + String.format(Locale.ROOT, "%02X", value & 255);
    }

    static long n(FlaRecord r, String field, long fallback) {
        Object v = r == null ? null : r.get(field);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    static String s(FlaRecord r, String field, String fallback) {
        Object v = r == null ? null : r.get(field);
        return v instanceof String ? (String) v : fallback;
    }

    static FlaRecord record(FlaRecord r, String field) {
        Object v = r == null ? null : r.get(field);
        return v instanceof FlaRecord ? (FlaRecord) v : null;
    }

    @SuppressWarnings("unchecked")
    static List<FlaRecord> records(FlaRecord r, String field) {
        Object v = r == null ? null : r.get(field);
        return v instanceof List ? (List<FlaRecord>) v : Collections.emptyList();
    }

    static String rgb(long rgba) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", rgba & 255, rgba >>> 8 & 255, rgba >>> 16 & 255);
    }

    static void color(Element element, long rgba) {
        element.setAttribute("color", rgb(rgba));
        element.setAttribute("alpha", number((rgba >>> 24 & 255) / 255.0));
    }

    static void matrix(Element parent, FlaMatrix m) {
        if (m == null) {
            return;
        }
        Element e = child(child(parent, "matrix"), "Matrix");
        e.setAttribute("a", number(m.getA()));
        e.setAttribute("b", number(m.getB()));
        e.setAttribute("c", number(m.getC()));
        e.setAttribute("d", number(m.getD()));
        e.setAttribute("tx", number(m.getTxPixels()));
        e.setAttribute("ty", number(m.getTyPixels()));
    }

    static String validXml(String s) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean valid = cp == 9 || cp == 10 || cp == 13 || cp >= 0x20 && cp <= 0xD7FF
                    || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF;
            result.appendCodePoint(valid ? cp : 0xFFFD);
        }
        return result.toString();
    }
}
