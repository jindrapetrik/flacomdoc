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
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Object identity and shared instance metadata, independent of binary layout.
 */
final class XflObjectReader {

    final XflReader.Session s;

    XflObjectReader(XflReader.Session s) {
        this.s = s;
    }

    FlaRecord object(Element e) throws IOException {
        String name = tag(e);
        FlaRecord result;
        switch (name) {
            case "DOMShape":
                return new XflShapeReader(s).shape(e);
            case "DOMGroup":
                result = r("CPicObj");
                placement(result, e);
                List<FlaRecord> members = new ArrayList<>();
                for (Element member : children(child(e, "members"))) {
                    FlaRecord m = object(member);
                    if (m != null) {
                        members.add(m);
                    }
                }
                result.set("children", members);
                return result;
            case "DOMStaticText":
            case "DOMDynamicText":
            case "DOMInputText":
                result = new XflTextReader(s).read(e);
                break;
            case "DOMSymbolInstance":
            case "DOMComponentInstance":
                String library = s(e, "libraryItemName", "");
                FlaRecord descriptor = s.symbols.get(library);
                if (descriptor == null) {
                    throw new IOException("Unknown symbol instance: " + library);
                }
                String kind = s(e, "symbolType", "movie clip");
                result = r(kind.equals("graphic") ? "CPicSymbol" : kind.equals("button") ? "CPicButton" : "CPicSprite", "revision", XflReader.Session.V.picSymbolRevision, "spriteRevision", XflReader.Session.V.picSpriteRevision, "buttonRevision", XflReader.Session.V.picButtonRevision, "libraryId", descriptor.get("libraryId"), "firstFrame", i(e, "firstFrame", 0), "loopMode", choice(e, "loop", 0, "loop", "play once", "single frame"), "instanceName", s(e, "name", ""), "trackAsMenu", b(e, "trackAsMenu", false) ? 1 : 0);
                placement(result, e);
                result.set("actions", actions(e));
                color(result, e);
                result.set("blendMode", choice(e, "blendMode", 1, "", "normal", "layer", "multiply", "screen", "lighten", "darken", "difference", "add", "subtract", "invert", "alpha", "erase", "overlay", "hardlight"));
                result.set("cacheAsBitmap", b(e, "cacheAsBitmap", false) ? 1 : 0);
                String m = s(e, "matrix3D", "");
                if (!m.isEmpty()) {
                    String[] values = m.trim().split("[\\s,]+");
                    if (values.length != 16) {
                        throw new IOException("matrix3D requires 16 values");
                    }
                    List<Float> matrix = new ArrayList<>();
                    for (String v : values) {
                        matrix.add(Float.valueOf(v.trim()));
                    }
                    result.set("matrix3D", matrix);
                }
                result.set("rotation3D", Arrays.asList(d(e, "rotationX", 0), d(e, "rotationY", 0), d(e, "rotationZ", 0)));
                result.set("centerPoint3D", Arrays.asList(twips(d(e, "centerPoint3DX", 0)), twips(d(e, "centerPoint3DY", 0)), twips(d(e, "centerPoint3DZ", 0))));
                result.set("properties", new XflComponentReader(s).parameters(e));
                break;
            case "DOMBitmapInstance":
            case "DOMVideoInstance":
                FlaRecord media = s.media.get(s(e, "libraryItemName", ""));
                if (media == null) {
                    throw new IOException("Unknown media instance: " + s(e, "libraryItemName", ""));
                }
                result = r(name.equals("DOMBitmapInstance") ? "CPicBitmap" : "CPicVideoStream", "revision", name.equals("DOMBitmapInstance") ? XflReader.Session.V.picBitmapRevision : XflReader.Session.V.picVideoStreamRevision, "mediaId", ((Number) media.get("mediaId")).intValue(), "mediaPresent", 1, "instanceName", s(e, "name", ""), "bounds", Arrays.asList(twips(d(e, "frameLeft", 0)), twips(d(e, "frameRight", 0)), twips(d(e, "frameTop", 0)), twips(d(e, "frameBottom", 0))));
                placement(result, e);
                break;
            case "DOMRectangleObject":
            case "DOMOvalObject":
                return new XflPrimitiveReader(s).read(e);
            case "DOMCompiledClipInstance":
                FlaRecord compiled = s.media.get(s(e, "libraryItemName", ""));
                if (compiled == null || !compiled.getClassName().equals("CSwfPage")) {
                    throw new IOException("Unknown compiled clip instance: " + s(e, "libraryItemName", ""));
                }
                Element bindings = child(e, "dataBindingXML");
                result = r("CPicSwf", "revision", 7, "mediaId", compiled.get("mediaId"), "mediaPresent", 1L,
                        "instanceName", s(e, "name", ""), "actions", actions(e),
                        "properties", new XflComponentReader(s).parameters(e),
                        "componentXml", r("componentXml", "revision", 0, "uniqueId", timestamp(e, "uniqueID"),
                                "xml", bindings == null ? "" : bindings.getTextContent()));
                placement(result, e);
                break;
            case "DOMTLFText":
                s.issue(e, "XFL import of this object is not implemented yet; omitted");
                return null;
            default:
                s.issue(e, "Unsupported display object omitted");
                return null;
        }
        result.set("filters", r("filters", "entries", filters(e)));
        result.set("accessibility", accessibility(e));
        result.set("persistentData", persistent(e));
        return result;
    }

    static FlaRecord actions(Element e) {
        Element script = nested(e, "Actionscript", "script");
        return script == null ? null : r("actions", "revision", 4, "source", script.getTextContent(), "mode", 1L);
    }

    static FlaRecord accessibility(Element e) {
        boolean enabled = b(e, "hasAccessibleData", false) || e.hasAttribute("accName") || e.hasAttribute("description");
        return r("accessibility", "revision", enabled ? 2 : 0, "flags", b(e, "silent", false) ? 1L : 0L, "name", s(e, "accName", ""), "description", s(e, "description", ""), "shortcut", s(e, "shortcut", ""), "tabIndex", s(e, "tabIndex", ""), "forceSimple", b(e, "forceSimple", false) ? 1L : 0L, "autoLabeling", b(e, "autoLabeling", true) ? 0L : 1L);
    }

    static FlaRecord persistent(Element e) {
        List<FlaRecord> entries = new ArrayList<>();
        for (Element pd : children(child(e, "persistentData"), "PD")) {
            String type = s(pd, "t", "s"), text = s(pd, "v", "");
            int id = type.equals("i") ? 0 : type.equals("I") ? 1 : type.equals("d") ? 3 : type.equals("D") ? 4 : type.equals("s") ? 5 : type.equals("B") ? 7 : -1;
            if (id < 0) {
                throw new IllegalArgumentException("Unknown persistent-data type " + type);
            }
            FlaRecord entry = r("entry", "name", s(pd, "n", ""), "type", id);
            if (id == 0) {
                entry.set("value", persistentInteger(text));
            } else if (id == 3) {
                entry.set("value", Double.parseDouble(text));
            } else if (id == 5) {
                entry.set("value", text);
            } else if (id == 7) {
                if ((text.length() & 1) != 0) {
                    throw new IllegalArgumentException("Odd persistent binary hex length");
                }
                byte[] bytes = new byte[text.length() / 2];
                for (int n = 0; n < bytes.length; n++) {
                    bytes[n] = (byte) Integer.parseInt(text.substring(n * 2, n * 2 + 2), 16);
                }
                entry.set("data", new BinaryData(bytes));
            } else {
                List<Number> values = new ArrayList<>();
                if (!text.isEmpty()) {
                    for (String v : text.split(",")) {
                        if (id == 1) {
                            values.add(persistentInteger(v.trim()));
                        } else {
                            values.add(Double.parseDouble(v.trim()));
                        }
                    }
                }
                entry.set("values", values);
            }
            entries.add(entry);
        }
        return entries.isEmpty() ? null : r("persistentData", "header", 0L, "entries", entries);
    }

    static int persistentInteger(String text) {
        long value = Long.parseLong(text);
        if (value < Integer.MIN_VALUE || value > 0xffffffffL) {
            throw new IllegalArgumentException("Persistent integer outside 32-bit range");
        }
        return (int) value;
    }

    static FlaRecord properties(Element e) {
        List<FlaRecord> entries = new ArrayList<>();
        for (Element p : children(e)) {
            String value = s(p, "value", p.getTextContent());
            entries.add(r("property", "name", s(p, "name", ""), "type", 0, "value", value));
        }
        return r("properties", "revision", 0, "entries", entries);
    }

    static void color(FlaRecord result, Element e) {
        Element c = nested(e, "color", "Color");
        if (c == null) {
            return;
        }
        FlaRecord transform = r("colorTransform", "revision", 1, "effectType", 3, "effectPercent", 0, "effectColorRgba", 0L);
        int effect = 3, percent = 0;
        double brightness = d(c, "brightness", 0), tint = d(c, "tintMultiplier", 0);
        boolean hasBrightness = c.hasAttribute("brightness"), hasTint = c.hasAttribute("tintColor");
        long tintColor = rgba(c, "tintColor", "tintAlpha", 0xff000000L);
        for (String channel : new String[]{"red", "green", "blue", "alpha"}) {
            double mult = d(c, channel + "Multiplier", 1), offset = d(c, channel + "Offset", 0);
            if (hasBrightness && !channel.equals("alpha")) {
                mult = 1 - Math.abs(brightness);
                offset = brightness > 0 ? 255 * brightness : 0;
            }
            if (hasTint && !channel.equals("alpha")) {
                int shift = channel.equals("red") ? 0 : channel.equals("green") ? 8 : 16;
                mult = 1 - tint;
                offset = (tintColor >>> shift & 255) * tint;
            }
            transform.set(channel + "Multiplier", scaled(mult, 256));
            transform.set(channel + "Offset", scaled(offset, 1));
        }
        if (hasBrightness) {
            effect = 1;
            percent = scaled(brightness, 100);
        } else if (hasTint) {
            effect = 2;
            percent = scaled(tint, 100);
        } else if (c.hasAttribute("alphaMultiplier") && c.getAttributes().getLength() == 1) {
            effect = 4;
            percent = scaled(d(c, "alphaMultiplier", 1), 100);
        }
        transform.set("effectType", effect);
        transform.set("effectPercent", percent);
        transform.set("effectColorRgba", tintColor);
        result.set("colorTransform", transform);
        result.set("colorEffectType", effect);
        result.set("colorEffectPercent", percent);
        result.set("colorEffectRgba", tintColor);
    }

    List<FlaRecord> filters(Element e) throws IOException {
        List<FlaRecord> result = new ArrayList<>();
        for (Element f : children(child(e, "filters"))) {
            String name = tag(f);
            int type = Arrays.asList("DropShadowFilter", "BlurFilter", "GlowFilter", "BevelFilter", "GradientGlowFilter", "", "AdjustColorFilter", "GradientBevelFilter").indexOf(name);
            if (type < 0) {
                s.issue(f, "Unsupported filter");
                continue;
            }
            FlaRecord state = r("state", "revision", 1, "enabled", b(f, "isEnabled", true) ? 1L : 0L);
            FlaRecord settings = r("settings", "revision", 4, "state", state, "colorRgba", XflModelXml.color(f, 0xff000000L), "blurX", d(f, "blurX", 5), "blurY", d(f, "blurY", 5), "distance", d(f, "distance", 5), "angleRadians", Math.toRadians(d(f, "angle", 45)), "inner", b(f, "inner", s(f, "type", "outer").equals("inner")) ? 1L : 0L, "knockout", b(f, "knockout", false) ? 1L : 0L, "quality", (long) i(f, "quality", 1), "strengthPercent", Math.round(d(f, "strength", 1) * 100), "hideObject", b(f, "hideObject", false) ? 1L : 0L);
            FlaRecord filter = r("filter", "type", type, "revision", type == 0 ? 4 : type <= 3 ? 3 : 1, "settings", settings, "state", state);
            if (type == 0) {
                settings.getFields().forEach(filter::set);
            }
            if (type == 3) {
                settings.set("colorRgba", rgba(f, "shadowColor", "shadowAlpha", 0xff000000L));
                filter.set("highlightColorRgba", rgba(f, "highlightColor", "highlightAlpha", 0xffffffffL));
                filter.set("full", s(f, "type", "outer").equals("full") ? 1L : 0L);
            }
            if (type == 4 || type == 7) {
                List<FlaRecord> stops = new ArrayList<>();
                for (Element stop : children(f, "GradientEntry")) {
                    stops.add(r("stops", "ratio", Math.round(d(stop, "ratio", 0) * 255), "colorRgba", XflModelXml.color(stop, 0xff000000L)));
                }
                FlaRecord g = r("gradient", "revision", 1, "settings", settings, "stops", stops, "full", s(f, "type", "outer").equals("full") ? 1L : 0L);
                if (type == 7) {
                    filter.set("gradient", g);
                } else {
                    g.getFields().forEach(filter::set);
                }
            }
            if (type == 6) {
                for (String key : new String[]{"brightness", "contrast", "saturation", "hue"}) {
                    filter.set(key, d(f, key, 0));
                }
            }
            result.add(filter);
        }
        return result;
    }
}
