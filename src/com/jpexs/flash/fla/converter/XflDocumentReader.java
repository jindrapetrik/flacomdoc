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
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.w3c.dom.*;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Document settings, palettes, library metadata and publish profiles.
 */
final class XflDocumentReader {

    final XflReader.Session s;

    XflDocumentReader(XflReader.Session s) {
        this.s = s;
    }

    void settings() throws IOException {
        Element d = s.document;
        FlaRecord c = s.contents;
        for (String key : new String[]{"gridSnapTo", "objectsSnapTo", "guidesLocked", "guidesSnapTo", "snapAlign", "snapAlignHorizontalCenter", "snapAlignVerticalCenter"}) {
            c.set(key, b(d, key, false) ? 1L : 0L);
        }
        for (String key : new String[]{"snapAlignBorderSpacing", "snapAlignHorizontalSpacing", "snapAlignVerticalSpacing", "viewAngle3D"}) {
            c.set(key, d(d, key, key.equals("viewAngle3D") ? 55 : 0));
        }
        c.set("timelineLabelWidth", i(d, "timelineLabelWidth", -1));
        c.set("gridColorRgba", rgba(d, "gridColor", "gridAlpha", 0xffccccccL));
        c.set("guidesColorRgba", rgba(d, "guidesColor", "guidesAlpha", 0xff00ff00L));
        c.set("gridSpacingXFixed16Twips", Math.round(d(d, "gridSpacingX", 18) * 20 * 65536));
        c.set("gridSpacingYFixed16Twips", Math.round(d(d, "gridSpacingY", 18) * 20 * 65536));
        c.set("sharedLibraryUrl", s(d, "sharedLibraryURL", ""));
        c.set("gridDisplayMode", b(d, "gridVisible", false) ? 3 : 0);
        c.set("guidesVisible", b(d, "guidesVisible", true) ? 1 : 0);
        c.set("rulerUnitType", choice(d, "rulerUnitType", 5, "inches", "decimal inches", "points", "centimeters", "millimeters", "pixels"));
        c.set("previewAndRulerFlags", new BinaryData(new byte[]{(byte) choice(d, "previewMode", 3, "outlines", "fast", "anti alias", "anti alias text", "full"), (byte) (b(d, "rulerVisible", false) ? 1 : 0), 0}));
        String[] options = {"viewOptionsAnimationControlVisible", "viewOptionsButtonsActive", "viewOptionsPasteBoardView", "viewOptionsLivePreview", "playOptionsPlayLoop", "playOptionsPlayPages", "playOptionsPlayFrameActions", "playOptionsPlaySounds"};
        int play = 0;
        for (int n = 0; n < options.length; n++) {
            if (b(d, options[n], n == 0 || n == 2 || n == 3 || n == 7)) {
                play |= 1 << n;
            }
        }
        c.set("viewPlayAndPrinterFlags", new BinaryData(new byte[]{(byte) play, 0}));
        c.set("gridSnapAccuracy", (long) choice(d, "gridSnapAccuracy", 1, "Must be close", "Normal", "Can be distant", "Always snap"));
        c.set("guidesSnapAccuracy", (long) choice(d, "guidesSnapAccuracy", 1, "Must be close", "Normal", "Can be distant"));
        c.set("pixelSnap", b(d, "pixelSnap", false) ? 1L : 0L);
        c.set("persistentData", XflObjectReader.persistent(d));
        c.set("vanishingPointTwips", Arrays.asList(twips(d(d, "vanishingPoint3DX", d(d, "width", 550) / 2)), twips(d(d, "vanishingPoint3DY", d(d, "height", 400) / 2))));
        c.set("accessibility", XflObjectReader.accessibility(d));
        for (String[] entry : new String[][]{{"metadataXml", "META-INF/metadata.xml"}, {"mobileSettingsXml", "MobileSettings.xml"}}) {
            byte[] data = s.resource(entry[1], false);
            if (data != null && data.length > 0) {
                parse(data, entry[1]);
                c.set(entry[0], new String(data, StandardCharsets.UTF_8));
            }
        }
        palettes();
        folders();
        fonts();
        publish();
    }

    void palettes() throws IOException {
        s.contents.set("swatches", palette(child(s.document, "swatchLists")));
        s.contents.set("extendedSwatches", palette(child(s.document, "extendedSwatchLists")));
    }

    @SuppressWarnings("unchecked")
    FlaRecord palette(Element parent) throws IOException {
        List<FlaRecord> colors = new ArrayList<>(), lists = new ArrayList<>();
        for (Element e : children(parent)) {
            String tag = tag(e);
            if (tag.endsWith("SwatchItem")) {
                Element paint = e;
                if (!tag.equals("SolidSwatchItem")) {
                    paint = nested(e, "swatch", tag.startsWith("Linear") ? "LinearGradient" : "RadialGradient");
                }
                FlaRecord color = new XflShapeReader(s).paint(paint == null ? e : paint);
                FlaRecord swatch = r("CColorDef");
                color.getFields().forEach(swatch::set);
                swatch.set("revision", 4);
                for (String key : new String[]{"hue", "saturation", "brightness"}) {
                    swatch.set(key, i(e, key, 65535));
                }
                colors.add(swatch);
            } else if (tag.equals("swatchList") || tag.equals("swatches")) {
                FlaRecord nested = palette(e);                
                colors.addAll((List<FlaRecord>) nested.get("colors"));
                lists.addAll((List<FlaRecord>) nested.get("lists"));
            } else {
                s.issue(e, "Unknown swatch entry");
            }
        }
        return r("CColorList", "revision", 1, "colors", colors, "lists", lists);
    }

    void folders() throws IOException {
        List<FlaRecord> entries = new ArrayList<>();
        for (Element e : children(child(s.document, "folders"), "DOMFolderItem")) {
            entries.add(r("folder", "revision", 4, "name", XflReader.Session.baseName(s(e, "name", "")), "libraryMetadata", s.metadata(e)));
        }
        s.contents.set("libraryFolders", r("libraryFolders", "header", 0L, "entries", entries));
    }

    void fonts() throws IOException {
        List<FlaRecord> entries = new ArrayList<>();
        int id = 0;
        for (Element e : children(child(s.document, "fonts"), "DOMFontItem")) {
            FlaRecord style = new XflTextReader(s).style(e, e);
            entries.add(r("font", "revision", 3, "name", XflReader.Session.baseName(s(e, "name", "")), "fontId", i(e, "id", ++id), "sourceTime", timestamp(e, "sourceLastImported"), "style", style, "libraryMetadata", s.metadata(e)));
        }
        s.contents.set("fontLibrary", r("fontLibrary", "header", 0L, "entries", entries));
    }

    void publish() throws IOException {
        Element root = s.load("PublishSettings.xml", false);
        if (root == null) {
            return;
        }
        List<FlaRecord> profiles = new ArrayList<>();
        FlaRecord current = null;
        List<Element> profileElements = new ArrayList<>();
        collect(root, "flash_profile", profileElements);
        for (Element profile : profileElements) {
            List<FlaRecord> pairs = new ArrayList<>();
            pairs.add(r("entry", "key", "PublishProfileProperties::name", "value", s(profile, "name", "Default")));
            for (Element group : children(profile)) {
                for (Element property : children(group)) {
                    if (!children(property).isEmpty()) {
                        s.issue(property, "Structured publish setting is not represented as a scalar property");
                        continue;
                    }
                    String namespace = tag(group).equals("PublishFlashProperties") ? "Vector" : tag(group);
                    String key = tag(property), value = property.getTextContent();
                    if (namespace.equals("Vector")) {
                        key = nativePublishProperty(key);
                        if (key.equals("RSLPreloaderMethod")) {
                            if (value.equals("wrap")) {
                                value = "0";
                            } else if (value.equals("event")) {
                                value = "1";
                            }
                        } else if (key.equals("DefaultLibraryLinkage")) {
                            if (value.equals("rsl")) {
                                value = "0";
                            } else if (value.equals("merge")) {
                                value = "1";
                            }
                        }
                    }
                    pairs.add(r("entry", "key", namespace + "::" + key, "value", value));
                }
            }
            FlaRecord record = r("strings", "revision", 0, "entries", pairs);
            profiles.add(record);
            if (b(profile, "current", false)) {
                current = record;
            }
        }
        // The native loader selects the last stored profile.
        if (current != null) {
            profiles.remove(current);
            profiles.add(current);
        }
        s.contents.set("publishSettings", r("publishSettings", "profiles", profiles));
    }

    static String nativePublishProperty(String xmlName) {
        for (String nativeName : new String[]{"AS3 Package Paths", "AS3 Library Paths", "AS3 External Library Paths", "AS3 Config Const",
            "Debugging Permitted", "Debugging Password", "External Player", "Compress Movie", "Package Paths", "Invisible Layer",
            "Override Sounds", "Package Export Frame", "Export Swc", "Omit Trace Actions", "Event Format", "Stream Compress", "Event Compress", "Stream Format"}) {
            if (nativeName.replace(" ", "").equals(xmlName)) {
                return nativeName;
            }
        }
        return xmlName;
    }

    static void collect(Element root, String tag, List<Element> result) {
        if (tag(root).equals(tag)) {
            result.add(root);
        }
        for (Element c : children(root)) {
            collect(c, tag, result);
        }
    }
}
