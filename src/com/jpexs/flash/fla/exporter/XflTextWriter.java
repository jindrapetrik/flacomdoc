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
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Classic text runs, preserving archived faces without requiring installed
 * fonts.
 */
final class XflTextWriter {

    private final BinaryFlaToXflExporter.Session session;
    private Font[] fonts;
    private final Map<String, Element> fontItems = new HashMap<>();

    XflTextWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void fonts(Element root) {
        List<FlaRecord> entries = records(record(session.source.getContents(), "fontLibrary"), "entries");
        if (entries.isEmpty()) {
            return;
        }
        Element list = child(root, "fonts");
        for (FlaRecord r : entries) {
            Element e = child(list, "DOMFontItem");
            session.attribute(e, "name", session.unique(session.fullName(r), r), r);
            String face = face(record(r, "style"));
            session.attribute(e, "font", face, r);
            if (fontItems.putIfAbsent(face, e) != null) {
                session.issue("FONT_BINDING", r, "Several library fonts share a face; embedding metadata resolves to the first matching item.");
            }
            fontItems.put(e.getAttribute("name") + "*", e);
            e.setAttribute("size", Long.toString(n(record(r, "style"), "fontSize", 0) / 20));
            e.setAttribute("id", Long.toString(n(r, "fontId", 0)));
            e.setAttribute("sourceLastImported", Long.toString(n(r, "sourceTime", 0)));
            session.linkage(e, r);
        }
    }

    void text(Element parent, FlaRecord r) {
        long flags = n(r, "textFlags", 0);
        String tag = (flags & 1) == 0 ? "DOMStaticText" : (flags & 2) != 0 ? "DOMDynamicText" : "DOMInputText";
        Element e = child(parent, tag);
        FlaBounds bounds = r.getBounds();
        if (bounds != null && bounds.isDefined()) {
            e.setAttribute("left", number(bounds.getXMinPixels()));
            e.setAttribute("top", number(bounds.getYMinPixels()));
            e.setAttribute("width", number(bounds.getWidthPixels()));
            e.setAttribute("height", number(bounds.getHeightPixels()));
        }
        e.setAttribute("selected", Boolean.toString((n(r, "flags", 0) & 2) != 0));
        e.setAttribute("autoExpand", Boolean.toString(n(r, "autoExpand", 0) != 0));
        e.setAttribute("scrollable", Boolean.toString(n(r, "scrollable", 0) != 0));
        e.setAttribute("includeOutlines", Boolean.toString((flags & 32) != 0));
        if (!tag.equals("DOMInputText")) {
            e.setAttribute("isSelectable", Boolean.toString((flags & 0x10000) != 0 || (flags & 1) != 0 && (flags & 128) == 0));
        }
        if (!tag.equals("DOMStaticText")) {
            session.attribute(e, "name", s(r, "instanceName", ""), r);
            session.attribute(e, "variableName", s(r, "variableName", ""), r);
            e.setAttribute("border", Boolean.toString((flags & 64) != 0));
            e.setAttribute("renderAsHTML", Boolean.toString((flags & 0x8000) != 0 || (flags & 128) != 0));
            e.setAttribute("lineType", (flags & 4) != 0 && tag.equals("DOMInputText") ? "password" : (flags & 16) == 0 ? "single line" : (flags & 8) != 0 ? "multiline" : "multiline no wrap");
        }
        if (tag.equals("DOMInputText")) {
            e.setAttribute("maxCharacters", Long.toString(n(r, "maxCharacters", 0)));
        }
        List<FlaRecord> runs = records(r, "runs");
        FlaRecord first = runs.isEmpty() ? record(r, "defaultStyle") : record(runs.get(0), "style");
        if (first != null) {
            if (!tag.equals("DOMInputText")) {
                e.setAttribute("orientation", n(first, "vertical", 0) == 0 ? "horizontal" : n(first, "rightToLeft", 0) == 0 ? "vertical left to right" : "vertical right to left");
            }
            FlaRecord aa = record(first, "antiAlias");
            long mode = n(aa, "mode", 0);
            String rendering = (flags & 0x20000) != 0 ? "device" : n(first, "bitmapRendering", 0) != 0 ? "bitmap" : mode == 2 ? "customThicknessSharpness" : mode == 1 ? "advanced" : "standard";
            e.setAttribute("fontRenderingMode", rendering);
            if (aa != null) {
                floatAttribute(e, "antiAliasThickness", aa, "thickness");
                floatAttribute(e, "antiAliasSharpness", aa, "sharpness");
            }
        }
        if ((flags & 0x100) != 0 && first != null) {
            Element font = fontItems.get(face(first));
            if (font != null) {
                String chars = s(r, "fontLibraryName", "");
                if (!chars.isEmpty()) {
                    String combined = font.getAttribute("embeddedCharacters");
                    for (int i = 0; i < chars.length();) {
                        int cp = chars.codePointAt(i);
                        String character = new String(Character.toChars(cp));
                        if (!combined.contains(character)) {
                            combined += character;
                        }
                        i += Character.charCount(cp);
                    }
                    session.attribute(font, "embeddedCharacters", combined, r);
                }
                String ranges = s(r, "embedRanges", "");
                if (ranges.isEmpty()) {
                    for (int i = 1; i <= 4; i++) {
                        if ((flags & (0x100L << i)) != 0) {
                            ranges += (ranges.isEmpty() ? "" : "|") + i;
                        }
                    }
                }
                if (!ranges.isEmpty()) {
                    if (ranges.matches("([0-9]+)?(\\|[0-9]+)*")) {
                        Set<String> ids = new LinkedHashSet<>();
                        if (!font.getAttribute("embedRanges").isEmpty()) {
                            Collections.addAll(ids, font.getAttribute("embedRanges").split("\\|"));
                        }
                        Collections.addAll(ids, ranges.split("\\|"));
                        font.setAttribute("embedRanges", String.join("|", ids));
                    } else {
                        session.issue("FONT_EMBED_RANGES", r, "Noncanonical font embed ranges omitted: " + ranges);
                    }
                }
            }
        }
        matrix(e, r.getMatrix());
        XflObjectWriter.transformPoint(session, e, r);
        Element container = child(e, "textRuns");
        for (FlaRecord run : runs) {
            run(container, s(run, "text", ""), record(run, "style"), r);
        }
        if (runs.isEmpty() && first != null) {
            run(container, "", first, r);
        }
        if (!runs.isEmpty() && n(r, "revision", 0) < 5) {
            session.issue("TEXT_LEGACY", r, "Historical text selection/rendering defaults may differ from current XFL.");
        }
        new XflObjectWriter(session).filters(e, r);
        XflObjectWriter.accessibility(session, e, r);
        XflPersistentDataWriter.write(session, e, r);
        if (!s(r, "localizationStringId", "").isEmpty()) {
            session.issue("TEXT_EXTENSION", r, "Classic text localization ID is retained in the reader but is not exported.");
        }
    }

    private void run(Element parent, String value, FlaRecord style, FlaRecord owner) {
        Element run = child(parent, "DOMTextRun");
        child(run, "characters").setTextContent(session.text(value, owner));
        Element a = child(child(run, "textAttrs"), "DOMTextAttrs");
        if (style == null || n(style, "revision", 0) < 2) {
            a.setAttribute("size", "12");
            a.setAttribute("face", "_serif");
            session.issue("TEXT_STYLE_DEFAULT", owner, "Revision-only text style uses XFL defaults; no archived font/size is available.");
            return;
        }
        long size = n(style, "fontSize", 240);
        a.setAttribute("size", Long.toString(size / 20));
        a.setAttribute("bitmapSize", Long.toString(size));
        session.attribute(a, "face", face(style), style);
        long rgba = n(style, "colorRgba", 0xFF000000L);
        // Old text-style loaders force the absent/unused alpha to opaque.
        if (n(style, "revision", 0) < 4) {
            rgba |= 0xFF000000L;
        }
        a.setAttribute("fillColor", rgb(rgba));
        a.setAttribute("alpha", number((rgba >>> 24 & 255) / 255.0));
        a.setAttribute("aliasText", Boolean.toString(n(style, "bitmapRendering", 0) != 0));
        // Historical loaders enable screen spacing regardless of the archived flag.
        a.setAttribute("useScreenSpacing", Boolean.toString(n(style, "revision", 0) < 10 || n(style, "useScreenSpacing", 0) != 0));
        a.setAttribute("autoKern", Boolean.toString(n(style, "autoKern", 1) != 0));
        a.setAttribute("rotation", Boolean.toString(n(style, "rotation", 0) != 0));
        a.setAttribute("alignment", choice(style, "alignment", new String[]{"left", "right", "center", "justify"}));
        a.setAttribute("characterPosition", choice(style, "characterPosition", new String[]{"normal", "superscript", "subscript"}));
        for (String field : new String[]{"lineSpacing", "indent", "leftMargin", "rightMargin", "letterSpacing"}) {
            a.setAttribute(field, number(n(style, field, 0) / 20.0));
        }
        String url = style.get("urlOverride") instanceof String ? s(style, "urlOverride", "") : s(style, "url", "");
        if (!url.isEmpty()) {
            session.attribute(a, "url", url, style);
        }
        if (!s(style, "target", "").isEmpty()) {
            session.attribute(a, "target", s(style, "target", ""), style);
        }
    }

    String face(FlaRecord style) {
        if (style == null) {
            return "_serif";
        }
        String archived = s(style, "fontFace", "");
        if (!archived.isEmpty()) {
            return archived;
        }
        String family = s(style, "fontFamily", "_serif");
        if (fonts == null) {
            try {
                fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            } catch (java.awt.AWTError | InternalError | SecurityException ex) {
                fonts = new Font[0];
                session.issue("FONT_SYSTEM", style, "Installed fonts are unavailable; archived family names are retained.");
            }
        }
        int weight = (n(style, "bold", 0) != 0 ? Font.BOLD : 0) | (n(style, "italic", 0) != 0 ? Font.ITALIC : 0);
        List<String> matches = new ArrayList<>();
        for (Font font : fonts) {
            String name = font.getFontName(Locale.ENGLISH).toLowerCase(Locale.ROOT);
            int actual = (name.contains("bold") ? Font.BOLD : 0) | (name.contains("italic") || name.contains("oblique") ? Font.ITALIC : 0);
            if (font.getFamily(Locale.ENGLISH).equalsIgnoreCase(family) && actual == weight) {
                matches.add(font.getPSName());
            }
        }
        if (!matches.isEmpty()) {
            Collections.sort(matches);
            return matches.get(0);
        }
        session.issue("FONT_FACE", style, "Archived family has no installed matching face: " + family + " (bold=" + ((weight & Font.BOLD) != 0) + ", italic=" + ((weight & Font.ITALIC) != 0) + "). Family name retained.");
        return family;
    }

    private String choice(FlaRecord r, String field, String[] values) {
        long v = n(r, field, 0);
        if (v >= 0 && v < values.length) {
            return values[(int) v];
        }
        session.issue("TEXT_STYLE", r, "Unassigned " + field + " " + v + "; default used.");
        return values[0];
    }

    private void floatAttribute(Element e, String attr, FlaRecord r, String field) {
        Object value = r.get(field);
        if (value instanceof Number && Double.isFinite(((Number) value).doubleValue())) {
            e.setAttribute(attr, number(((Number) value).doubleValue()));
        } else {
            session.issue("TEXT_ANTIALIAS", r, "Nonfinite antialias parameter omitted.");
        }
    }
}
