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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Immutable XML element view for stored authoring fragments, including unknown
 * attributes.
 */
public final class FlaXmlElement {

    private final String name, text;
    private final Map<String, String> attributes;
    private final List<FlaXmlElement> children;

    private FlaXmlElement(Builder b) {
        name = b.name;
        text = b.text.toString();
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(b.attributes));
        children = Collections.unmodifiableList(new ArrayList<>(b.children));
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return text;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public List<FlaXmlElement> getChildren() {
        return children;
    }

    public List<FlaXmlElement> getChildren(String elementName) {
        List<FlaXmlElement> result = new ArrayList<>();
        for (FlaXmlElement e : children) {
            if (elementName.equals(e.name)) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public FlaXmlElement getFirstChild(String elementName) {
        for (FlaXmlElement e : children) {
            if (elementName.equals(e.name)) {
                return e;
            }
        }
        return null;
    }

    public Double getNumber(String key) {
        String value = attributes.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Parse one or more root elements. No file, network, entity or DTD
     * resolution.
     */
    public static List<FlaXmlElement> parseFragment(String xml) throws IOException {
        if (xml == null) {
            throw new NullPointerException("xml");
        }
        if (xml.length() > 16 * 1024 * 1024) {
            throw new IOException("Authoring XML exceeds the interpretation limit");
        }
        String content = xml;
        if (content.startsWith("\ufeff")) {
            content = content.substring(1);
        }
        if (content.startsWith("<?xml ")) {
            int end = content.indexOf("?>");
            if (end < 0) {
                throw new IOException("Unterminated XML declaration");
            }
            content = content.substring(end + 2);
        }
        final Deque<Builder> stack = new ArrayDeque<>();
        final List<FlaXmlElement> roots = new ArrayList<>();
        final int[] count = {0};
        DefaultHandler handler = new DefaultHandler() {
            @Override
            public void startElement(String uri, String local, String qName, Attributes attrs) throws SAXException {
                if (stack.size() >= 129 || ++count[0] > 200001) {
                    throw new SAXException("Authoring XML nesting/element limit exceeded");
                }
                Builder b = new Builder(qName);
                for (int i = 0; i < attrs.getLength(); i++) {
                    b.attributes.put(attrs.getQName(i), attrs.getValue(i));
                }
                stack.push(b);
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                stack.peek().text.append(ch, start, length);
            }

            @Override
            public void endElement(String uri, String local, String qName) {
                FlaXmlElement e = new FlaXmlElement(stack.pop());
                if (stack.isEmpty()) {
                    roots.addAll(e.children);
                } else {
                    stack.peek().children.add(e);
                }
            }

            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
                throw new SAXException("External XML entities are disabled");
            }

            @Override
            public void error(SAXParseException ex) throws SAXException {
                throw ex;
            }

            @Override
            public void fatalError(SAXParseException ex) throws SAXException {
                throw ex;
            }
        };
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(false);
            f.setXIncludeAware(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            org.xml.sax.XMLReader reader = f.newSAXParser().getXMLReader();
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.setEntityResolver(handler);
            reader.parse(new InputSource(new StringReader("<flaFragment>" + content + "</flaFragment>")));
            return Collections.unmodifiableList(roots);
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException ex) {
            throw new IOException("Cannot interpret stored authoring XML: " + ex.getMessage(), ex);
        }
    }

    private static final class Builder {

        final String name;
        final StringBuilder text = new StringBuilder();
        final Map<String, String> attributes = new LinkedHashMap<>();
        final List<FlaXmlElement> children = new ArrayList<>();

        Builder(String name) {
            this.name = name;
        }
    }
}
