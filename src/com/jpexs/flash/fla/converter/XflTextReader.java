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
import java.awt.*;
import java.util.*;
import java.util.List;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Classic text, Unicode runs and SFNT-derived font classifications.
 */
final class XflTextReader {

    final XflReader.Session s;

    XflTextReader(XflReader.Session s) {
        this.s = s;
    }

    private static final class Fonts {

        static final Map<String, Font> ALL = load();

        static Map<String, Font> load() {
            Map<String, Font> result = new HashMap<>();
            try {
                for (Font f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
                    result.put(f.getPSName(), f);
                }
            } catch (AWTError | InternalError | SecurityException ex) {
            }
            return result;
        }
    }

    FlaRecord read(Element e) {
        FlaRecord r = r("CPicText", "revision", XflReader.Session.V.picTextRevision);
        placement(r, e);
        int left = twips(d(e, "left", 0)), top = twips(d(e, "top", 0));
        r.set("bounds", Arrays.asList(left, left + twips(d(e, "width", 0)), top, top + twips(d(e, "height", 0))));
        r.set("autoExpand", b(e, "autoExpand", false) ? 1 : 0);
        int flags = 0;
        if (!tag(e).equals("DOMStaticText")) {
            flags |= 1;
        }
        if (tag(e).equals("DOMDynamicText")) {
            flags |= 2;
        }
        if (b(e, "isSelectable", true)) {
            flags |= 0x10000;
        } else if (tag(e).equals("DOMDynamicText")) {
            flags |= 128;
        }
        if (b(e, "border", false)) {
            flags |= 64;
        }
        if (b(e, "includeOutlines", false)) {
            flags |= 32;
        }
        if (s(e, "fontRenderingMode", "").equals("device")) {
            flags |= 0x20000;
        }
        String line = s(e, "lineType", "single line");
        if (line.equals("multiline")) {
            flags |= 16 | 8;
        }
        if (line.equals("multiline no wrap")) {
            flags |= 16;
        }
        if (line.equals("password")) {
            flags |= 4;
        }
        if (b(e, "renderAsHTML", false)) {
            flags |= 0x8000;
        }
        r.set("textFlags", (long) flags);
        r.set("maxCharacters", i(e, "maxCharacters", 0));
        r.set("variableName", s(e, "variableName", ""));
        r.set("instanceName", s(e, "name", ""));
        r.set("scrollable", b(e, "scrollable", false) ? 1L : 0L);
        List<FlaRecord> runs = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Element run : children(child(e, "textRuns"), "DOMTextRun")) {
            Element chars = child(run, "characters");
            String value = chars == null ? "" : chars.getTextContent();
            runs.add(r("runs", "text", value, "style", style(nested(run, "textAttrs", "DOMTextAttrs"), e)));
            text.append(value);
        }
        Set<String> ranges = new LinkedHashSet<>();
        StringBuilder embedded = new StringBuilder();
        for (Element run : children(child(e, "textRuns"), "DOMTextRun")) {
            Element item = fontItem(s(nested(run, "textAttrs", "DOMTextAttrs"), "face", ""));
            if (item == null) {
                continue;
            }
            flags |= 0x100;
            String chars = s(item, "embeddedCharacters", "");
            if (!chars.isEmpty()) {
                flags |= 0x2000;
                embedded.append(chars);
            }
            for (String range : s(item, "embedRanges", "").split("\\|")) {
                if (range.isEmpty()) {
                    continue;
                }
                int n = Integer.parseInt(range);
                if (n >= 1 && n <= 4) {
                    flags |= 0x100 << n;
                }
                ranges.add(range);
            }
        }
        if (text.length() == 0) {
            flags |= 0x4000;
            r.set("defaultStyle", runs.isEmpty() ? style(null, e) : runs.get(0).get("style"));
        }
        r.set("textFlags", (long) flags);
        r.set("fontLibraryName", embedded.toString());
        r.set("embedRanges", String.join("|", ranges));
        r.set("embedVariantGlyphs", b(e, "embedVariantGlyphs", false) ? 1 : 0);
        r.set("runs", runs);
        r.set("text", text.toString());
        r.set("accessibility", XflObjectReader.accessibility(e));
        return r;
    }

    Element fontItem(String face) {
        for (Element item : children(child(s.document, "fonts"), "DOMFontItem")) {
            if (face.equals(s(item, "font", "")) || face.equals(s(item, "name", "") + "*")) {
                return item;
            }
        }
        return null;
    }

    FlaRecord style(Element e, Element owner) {
        String face = s(e, "face", s(e, "font", "TimesNewRomanPSMT"));
        Element item = fontItem(face);
        if (item != null) {
            face = s(item, "font", face);
        }
        Font f = Fonts.ALL.get(face);
        String family = f == null ? face : f.getFamily(Locale.ENGLISH);
        String lower = face.toLowerCase(Locale.ROOT);
        boolean bold = f == null ? lower.contains("bold") : f.getFontName(Locale.ENGLISH).toLowerCase(Locale.ROOT).contains("bold"), italic = f == null ? lower.contains("italic") || lower.contains("oblique") : f.getFontName(Locale.ENGLISH).toLowerCase(Locale.ROOT).matches(".*(italic|oblique).*");
        FontMetadata metadata = SystemFontFiles.getInstance().getMetadata(face);
        FlaRecord r = r("style", "revision", XflReader.Session.V.textRunStyleRevision, "fontSize", i(e, "bitmapSize", twips(d(e, "size", 12))), "fontFamily", family, "fontFace", face, "colorRgba", rgba(e, "fillColor", "alpha", 0xff000000L), "pitchAndFamily", metadata.getPitchAndFamily(), "charSet", metadata.getCharSet(), "bold", b(e, "bold", bold) ? 1 : 0, "italic", b(e, "italic", italic) ? 1 : 0, "useScreenSpacing", b(e, "useScreenSpacing", false) ? 1 : 0, "autoKern", b(e, "autoKern", true) ? 1 : 0, "characterPosition", choice(e, "characterPosition", 0, "normal", "superscript", "subscript"), "alignment", choice(e, "alignment", 0, "left", "right", "center", "justify"), "url", s(e, "url", ""), "target", s(e, "target", ""));
        for (String key : new String[]{"lineSpacing", "indent", "leftMargin", "rightMargin", "letterSpacing"}) {
            r.set(key, twips(d(e, key, 0)));
        }
        String orientation = s(owner, "orientation", "horizontal");
        r.set("vertical", orientation.startsWith("vertical") ? 1 : 0);
        r.set("rightToLeft", orientation.endsWith("right to left") ? 1 : 0);
        r.set("rotation", b(e, "rotation", false) ? 1 : 0);
        r.set("bitmapRendering", b(e, "aliasText", s(owner, "fontRenderingMode", "").equals("bitmap")) ? 1 : 0);
        String mode = s(owner, "fontRenderingMode", "standard");
        r.set("antiAlias", r("antiAlias", "revision", 2, "mode", mode.equals("advanced") ? 1 : mode.equals("customThicknessSharpness") ? 2 : 0, "thickness", d(owner, "antiAliasThickness", 0), "sharpness", d(owner, "antiAliasSharpness", 0)));
        return r;
    }
}
