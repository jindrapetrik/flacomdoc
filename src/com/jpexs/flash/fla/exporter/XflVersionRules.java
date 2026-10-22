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

import com.jpexs.flash.fla.reader.BinaryData;
import java.io.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * Version differences verified against native XFL and Adobe release notes.
 */
final class XflVersionRules {

    private static final String NS = "http://ns.adobe.com/xfl/2008/";

    static Map<String, BinaryData> convert(Map<String, BinaryData> input, XflFormatVersion target, List<XflExportIssue> issues) throws IOException {
        Map<String, BinaryData> files = new LinkedHashMap<>(input);
        for (Map.Entry<String, BinaryData> file : input.entrySet()) {
            String path = file.getKey();
            boolean publish = path.equals("PublishSettings.xml");
            if (!publish && !path.equals("DOMDocument.xml") && !(path.startsWith("LIBRARY/") && path.endsWith(".xml"))) {
                continue;
            }
            if (file.getValue().size() == 0 && publish) {
                continue;
            }
            Document xml = parse(file.getValue(), path);
            boolean changed = visit(xml.getDocumentElement(), path, target, issues, publish, files);
            if (path.equals("DOMDocument.xml")) {
                Element root = xml.getDocumentElement();
                if (!target.getXflVersion().equals(root.getAttribute("xflVersion"))) {
                    root.setAttribute("xflVersion", target.getXflVersion());
                    // These describe the saving application, not the original author.
                    for (String key : Arrays.asList("creatorInfo", "versionInfo", "majorVersion", "buildNumber")) {
                        root.removeAttribute(key);
                    }
                    root.setAttribute("creatorInfo", "FlaComDoc");
                    root.setAttribute("majorVersion", target == XflFormatVersion.CS6 ? "12" : "11");
                    changed = true;
                }
            }
            if (changed) {
                files.put(path, new BinaryData(serialize(xml)));
            }
        }
        return files;
    }

    private static boolean visit(Element e, String path, XflFormatVersion target, List<XflExportIssue> issues,
            boolean publish, Map<String, BinaryData> files) throws IOException {
        boolean changed = false;
        String tag = name(e);
        if (publish) {
            if (tag.equals("PublishFlashProperties") && target != XflFormatVersion.CS6) {
                changed |= publish(e, path, target, issues);
            }
            if (tag.equals("PublishHtmlProperties") && target != XflFormatVersion.CS6) {
                // SWFObject's direct wmode was introduced with Stage3D in CS6.
                Element mode = child(e, "WindowMode");
                if (mode != null && mode.getTextContent().trim().equalsIgnoreCase("direct")) {
                    mode.setTextContent("0");
                    issue(issues, path, "Direct HTML window mode replaced with window mode for " + target + ".");
                    changed = true;
                }
            }
        } else if (xfl(e)) {
            if (tag.equals("Include") && e.hasAttribute("href")) {
                String resource = XflVersionConverter.checkedPath("LIBRARY/" + e.getAttribute("href"));
                if (!files.containsKey(resource)) {
                    throw new IOException("Missing XFL symbol resource: " + resource);
                }
            }
            if (target == XflFormatVersion.CS5) {
                if (tag.equals("DOMDocument")) {
                    changed |= removeAttributes(e, path, issues, "autoSaveEnabled", "autoSaveHasPrompted");
                }
                if (tag.equals("DOMSymbolInstance") || tag.equals("DOMCompiledClipInstance")) {
                    if (truth(e.getAttribute("exportAsBitmap"))) {
                        e.setAttribute("cacheAsBitmap", "true");
                        issue(issues, path, tag + ": export-as-bitmap replaced with runtime bitmap caching; rasterization timing differs.");
                        changed = true;
                    }
                    if (e.hasAttribute("isVisible") && !truth(e.getAttribute("isVisible"))) {
                        issue(issues, path, tag + ": invisible instance becomes visible in CS5; color effects and alpha are retained.");
                    }
                    changed |= removeAttributes(e, path, issues, "isVisible", "exportAsBitmap", "bits32");
                    Element background = child(e, "MatteColor");
                    if (background != null) {
                        e.removeChild(background);
                        issue(issues, path, tag + ": opaque bitmap background removed; CS5 does not support it.");
                        changed = true;
                    }
                }
                if (tag.equals("IKNode") || tag.equals("ChildNode")) {
                    Element pins = child(e, "pins");
                    if (pins != null) {
                        e.removeChild(pins);
                        issue(issues, path, tag + ": IK pins removed; poses remain, but bone editing constraints can differ.");
                        changed = true;
                    }
                }
            }
        }
        // Snapshot children: conversion may remove nodes while traversing.
        for (Element c : children(e)) {
            changed |= visit(c, path, target, issues, publish, files);
        }
        return changed;
    }

    private static boolean publish(Element e, String path, XflFormatVersion target, List<XflExportIssue> issues) {
        boolean changed = false;
        Element compression = child(e, "CompressionType");
        if (compression != null) {
            e.removeChild(compression);
            issue(issues, path, "CompressionType removed for " + target + "; enabled SWF compression uses zlib instead of LZMA.");
            changed = true;
        }
        int maximum = target == XflFormatVersion.CS5 ? 10 : 11;
        Element version = child(e, "Version"), player = child(e, "ExternalPlayer");
        String external = player == null ? "" : player.getTextContent().trim();
        if (version != null && (external.isEmpty() || external.startsWith("FlashPlayer"))) {
            try {
                if (Integer.parseInt(version.getTextContent().trim()) > maximum) {
                    version.setTextContent(Integer.toString(maximum));
                    if (player != null) {
                        player.setTextContent(target == XflFormatVersion.CS5 ? "FlashPlayer10" : "FlashPlayer10.2");
                    }
                    issue(issues, path, "Flash Player publish target lowered to SWF " + maximum + "; ActionScript APIs are not rewritten.");
                    changed = true;
                }
            } catch (NumberFormatException ex) {
                issue(issues, path, "Unrecognized SWF publish version retained: " + version.getTextContent());
            }
        } else if (!external.isEmpty() && !external.startsWith("FlashPlayer")) {
            issue(issues, path, "External publish target " + external + " requires a matching target-version SDK/plugin; application code is not rewritten.");
        }
        return changed;
    }

    private static boolean removeAttributes(Element e, String path, List<XflExportIssue> issues, String... names) {
        boolean changed = false;
        for (String name : names) {
            if (e.hasAttribute(name)) {
                e.removeAttribute(name);
                issue(issues, path, XflVersionRules.name(e) + " @" + name + " removed for CS5 (requires CS5.5).");
                changed = true;
            }
        }
        return changed;
    }

    private static void issue(List<XflExportIssue> issues, String path, String message) {
        issues.add(new XflExportIssue("TARGET_VERSION", path, 0, message));
    }

    static Document parse(BinaryData data, String path) throws IOException {
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
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(data.toByteArray()));
        } catch (ParserConfigurationException | SAXException ex) {
            throw new IOException("Invalid XFL XML in " + path, ex);
        }
    }

    private static byte[] serialize(Document document) throws IOException {
        try {
            TransformerFactory f = TransformerFactory.newInstance();
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = f.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(new DOMSource(document), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException ex) {
            throw new IOException("Cannot serialize XFL", ex);
        }
    }

    static String name(Node e) {
        return e.getLocalName() == null ? e.getNodeName() : e.getLocalName();
    }

    static boolean xfl(Element e) {
        return NS.equals(e.getNamespaceURI()) || e.getNamespaceURI() == null;
    }

    private static boolean truth(String value) {
        return value.equals("true") || value.equals("1");
    }

    private static List<Element> children(Element e) {
        List<Element> result = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private static Element child(Element e, String name) {
        for (Element c : children(e)) {
            if (name(c).equals(name) && Objects.equals(c.getNamespaceURI(), e.getNamespaceURI())) {
                return c;
            }
        }
        return null;
    }

}
