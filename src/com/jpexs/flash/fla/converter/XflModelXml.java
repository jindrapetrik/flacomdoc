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
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * XML and unit conversions shared by the semantic XFL readers.
 */
final class XflModelXml {

    private XflModelXml() {
    }

    static String tag(Node n) {
        return n.getLocalName() == null ? n.getNodeName() : n.getLocalName();
    }

    static List<Element> children(Element e) {
        List<Element> result = new ArrayList<>();
        if (e != null) {
            for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) {
                    result.add((Element) n);
                }
            }
        }
        return result;
    }

    static List<Element> children(Element e, String name) {
        List<Element> result = new ArrayList<>();
        for (Element c : children(e)) {
            if (tag(c).equals(name)) {
                result.add(c);
            }
        }
        return result;
    }

    static Element child(Element e, String name) {
        for (Element c : children(e)) {
            if (tag(c).equals(name)) {
                return c;
            }
        }
        return null;
    }

    static Element nested(Element e, String a, String b) {
        return child(child(e, a), b);
    }

    static String s(Element e, String key, String fallback) {
        return e != null && e.hasAttribute(key) ? e.getAttribute(key) : fallback;
    }

    static double d(Element e, String key, double fallback) {
        String v = s(e, key, "");
        if (v.isEmpty()) {
            return fallback;
        }
        try {
            double n = Double.parseDouble(v);
            if (!Double.isFinite(n)) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + tag(e) + ".@" + key + ": " + v, ex);
        }
    }

    static int i(Element e, String key, int fallback) {
        double n = d(e, key, fallback);
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE || n != Math.rint(n)) {
            throw new IllegalArgumentException("Invalid integer " + key);
        }
        return (int) n;
    }

    static long timestamp(Element e, String key) {
        double value = d(e, key, 0);
        if (value < 0 || value > 0xffffffffL) {
            throw new IllegalArgumentException("Timestamp outside UI32 range");
        }
        return Math.round(value);
    }

    static boolean b(Element e, String key, boolean fallback) {
        String v = s(e, key, "");
        if (v.isEmpty()) {
            return fallback;
        }
        if (v.equals("true") || v.equals("1")) {
            return true;
        }
        if (v.equals("false") || v.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean " + key + ": " + v);
    }

    static int choice(Element e, String key, int fallback, String... choices) {
        String v = s(e, key, "");
        if (v.isEmpty()) {
            return fallback;
        }
        for (int n = 0; n < choices.length; n++) {
            if (v.equals(choices[n])) {
                return n;
            }
        }
        throw new IllegalArgumentException("Unsupported " + tag(e) + ".@" + key + ": " + v);
    }

    static int scaled(double n, double scale) {
        double v = n * scale;
        if (!Double.isFinite(v) || v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Coordinate outside signed 32-bit range");
        }
        return (int) Math.round(v);
    }

    static int twips(double n) {
        return scaled(n, 20);
    }

    static long rgba(Element e, String color, String alpha, long fallback) {
        String v = s(e, color, "");
        long rgb = v.isEmpty() ? fallback & 0xffffff : Long.parseLong(v.startsWith("#") ? v.substring(1) : v, 16);
        if (rgb < 0 || rgb > 0xffffff) {
            throw new IllegalArgumentException("Invalid RGB " + v);
        }
        double a = d(e, alpha, (fallback >>> 24 & 255) / 255.0);
        if (a < 0 || a > 1) {
            throw new IllegalArgumentException("Alpha outside 0..1");
        }
        return (Math.round(a * 255) << 24) | (rgb & 255) << 16 | (rgb & 0xff00) | (rgb >>> 16 & 255);
    }

    // Input fallback is conventional ARGB, output is the FLA low-byte-red word.
    static long color(Element e, long fallback) {
        return rgba(e, "color", "alpha", fallback);
    }

    static FlaRecord r(String type, Object... fields) {
        FlaRecord r = FlaRecord.create(type);
        for (int n = 0; n < fields.length; n += 2) {
            r.set((String) fields[n], fields[n + 1]);
        }
        return r;
    }

    static List<Integer> matrix(Element owner) {
        Element m = nested(owner, "matrix", "Matrix");
        return Arrays.asList(scaled(d(m, "a", 1), 65536), scaled(d(m, "b", 0), 65536), scaled(d(m, "c", 0), 65536), scaled(d(m, "d", 1), 65536), twips(d(m, "tx", 0)), twips(d(m, "ty", 0)));
    }

    static void placement(FlaRecord r, Element e) {
        r.set("baseRevision", FlaFormatVersion.CS4.picGroupBaseRevision);
        r.set("flags", (b(e, "isFloating", false) ? 1 : 0) | (b(e, "selected", false) ? 2 : 0) | (b(e, "locked", false) ? 4 : 0));
        r.set("matrixWords", matrix(e));
        Element p = nested(e, "transformationPoint", "Point");
        r.set("transformationPointX", p == null ? Integer.MIN_VALUE : twips(d(p, "x", 0)));
        r.set("transformationPointY", p == null ? Integer.MIN_VALUE : twips(d(p, "y", 0)));
    }

    static byte[] bytes(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = source.read(buf)) != -1;) {
                if (out.size() > 256 * 1024 * 1024 - n) {
                    throw new IOException("XFL resource exceeds 256 MiB");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    static Document parse(byte[] data, String path) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void error(org.xml.sax.SAXParseException ex) throws SAXException {
                    throw ex;
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException ex) throws SAXException {
                    throw ex;
                }
            });
            return builder.parse(new ByteArrayInputStream(data));
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException ex) {
            throw new IOException("Invalid XFL XML " + path + ": " + ex.getMessage(), ex);
        }
    }

    static String xml(Node node) throws IOException {
        if (node == null) {
            return "";
        }
        try {
            TransformerFactory f = TransformerFactory.newInstance();
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = f.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter out = new StringWriter();
            t.transform(new DOMSource(node), new StreamResult(out));
            return out.toString();
        } catch (TransformerException ex) {
            throw new IOException(ex);
        }
    }

    static String inner(Element e) throws IOException {
        StringBuilder ret = new StringBuilder();
        if (e != null) {
            for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
                ret.append(xml(n));
            }
        }
        return ret.toString();
    }
}
