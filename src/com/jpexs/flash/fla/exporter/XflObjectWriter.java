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
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Shared placement and graphic effect properties.
 */
final class XflObjectWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflObjectWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    static void transformPoint(BinaryFlaToXflExporter.Session s, Element e, FlaRecord r) {
        long x = n(r, "transformationPointX", Integer.MIN_VALUE), y = n(r, "transformationPointY", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
            return;
        }
        FlaMatrix m = r.getMatrix();
        double px = x / 20.0, py = y / 20.0;
        if (m != null) {
            double det = m.getA() * m.getD() - m.getB() * m.getC();
            if (det == 0) {
                s.issue("TRANSFORMATION_POINT", r, "Singular placement matrix prevents recovery of local transformation point.");
                return;
            }
            double dx = px - m.getTxPixels(), dy = py - m.getTyPixels();
            px = (m.getD() * dx - m.getC() * dy) / det;
            py = (m.getA() * dy - m.getB() * dx) / det;
        }
        Element point = child(child(e, "transformationPoint"), "Point");
        point.setAttribute("x", number(px));
        point.setAttribute("y", number(py));
    }

    static void accessibility(BinaryFlaToXflExporter.Session s, Element e, FlaRecord r) {
        FlaRecord a = record(r, "accessibility");
        if (a == null || n(a, "revision", 0) == 0) {
            return;
        }
        e.setAttribute("hasAccessibleData", "true");
        e.setAttribute("silent", Boolean.toString(n(a, "flags", 0) != 0));
        e.setAttribute("forceSimple", Boolean.toString(n(a, "forceSimple", 0) != 0));
        e.setAttribute("autoLabeling", Boolean.toString(n(a, "autoLabeling", 0) == 0));
        s.attribute(e, "accName", s(a, "name", ""), a);
        for (String field : new String[]{"description", "shortcut", "tabIndex"}) {
            if (a.get(field) instanceof String) {
                s.attribute(e, field, s(a, field, ""), a);
            }
        }
    }

    void placement(Element e, FlaRecord r) {
        long blend = n(r, "blendMode", 1);
        String[] modes = {"normal", "normal", "layer", "multiply", "screen", "lighten", "darken", "difference", "add", "subtract", "invert", "alpha", "erase", "overlay", "hardlight"};
        if (blend >= 0 && blend < modes.length) {
            e.setAttribute("blendMode", modes[(int) blend]);
        } else {
            session.issue("BLEND_MODE", r, "Unassigned blend mode " + blend);
        }
        boolean cache = n(r, "cacheAsBitmap", 0) != 0;
        if (r.get("bitmapRenderMode") instanceof Number) {
            long mode = n(r, "bitmapRenderMode", 0);
            if (mode >= 0 && mode <= 2) {
                cache = mode == 1;
                if (session.version.supportsSymbolDisplaySettings()) {
                    e.setAttribute("exportAsBitmap", Boolean.toString(mode == 2));
                } else if (mode == 2) {
                    session.issue("TARGET_VERSION", r, "Export-as-bitmap setting requires Flash CS5.5 or later.");
                }
            } else {
                session.issue("BITMAP_RENDER_MODE", r, "Unassigned bitmap render mode " + mode);
            }
        }
        e.setAttribute("cacheAsBitmap", Boolean.toString(cache));
        if (session.version.supportsSymbolDisplaySettings() && r.get("bitmapBits32") instanceof Number) {
            e.setAttribute("bits32", Boolean.toString(n(r, "bitmapBits32", 1) != 0));
        }
        if (!session.version.supportsSymbolDisplaySettings()
                && (r.get("isVisible") instanceof Number && n(r, "isVisible", 1) == 0
                || r.get("bitmapBits32") instanceof Number && n(r, "bitmapBits32", 1) == 0)) {
            session.issue("TARGET_VERSION", r, "Symbol visibility/bitmap background setting requires Flash CS5.5 or later.");
        }
        if (session.version.supportsSymbolDisplaySettings() && r.get("isVisible") instanceof Number) {
            e.setAttribute("isVisible", Boolean.toString(n(r, "isVisible", 1) != 0));
        }
        FlaMatrix3D m = r.getMatrix3D();
        if (m != null) {
            StringBuilder value = new StringBuilder();
            for (float f : m.getColumnMajorValues()) {
                if (!Float.isFinite(f)) {
                    session.issue("MATRIX_3D", r, "Nonfinite 3D matrix omitted.");
                    value.setLength(0);
                    break;
                }
                if (value.length() != 0) {
                    value.append(' ');
                }
                value.append(number(f));
            }
            if (value.length() != 0) {
                e.setAttribute("matrix3D", value.toString());
            }
        }
        Object rotations = r.get("rotation3D");
        if (rotations instanceof List && ((List<?>) rotations).size() == 3) {
            String[] axes = {"rotationX", "rotationY", "rotationZ"};
            for (int i = 0; i < 3; i++) {
                double v = ((Number) ((List<?>) rotations).get(i)).doubleValue();
                if (Double.isFinite(v)) {
                    e.setAttribute(axes[i], number(v));
                } else {
                    session.issue("MATRIX_3D", r, "Nonfinite 3D rotation omitted.");
                }
            }
        }
        FlaCenterPoint3D center = r.getCenterPoint3D();
        if (center != null && center.isDefined()) {
            int[] words = center.getWords();
            String[] axes = {"centerPoint3DX", "centerPoint3DY", "centerPoint3DZ"};
            for (int i = 0; i < 3; i++) {
                e.setAttribute(axes[i], number(words[i] / 20.0));
            }
        }
        accessibility(session, e, r);
    }

    void colorEffect(Element parent, FlaRecord instance) {
        FlaRecord transform = record(instance, "colorTransform");
        if (transform == null) {
            return;
        }
        boolean modern = transform.get("effectType") instanceof Number;
        boolean hasMode = modern || instance.get("colorEffectType") instanceof Number;
        long type = hasMode ? modern ? n(transform, "effectType", 0) : n(instance, "colorEffectType", 0) : 3;
        if (type == 0) {
            return;
        }
        Element color = child(child(parent, "color"), "Color");
        if (type == 1 || type == 2) {
            double percent = (modern ? n(transform, "effectPercent", 0) : n(instance, "colorEffectPercent", 0)) / 100.0;
            double value = Math.max(type == 1 ? -1 : 0, Math.min(1, percent));
            if (value != percent) {
                session.issue("COLOR_EFFECT_RANGE", transform, "Color effect percentage clamped to the native XFL range.");
            }
            if (type == 1) {
                color.setAttribute("brightness", number(value));
            } else {
                color.setAttribute("tintMultiplier", number(value));
                color.setAttribute("tintColor", rgb(modern ? n(transform, "effectColorRgba", 0) : n(instance, "colorEffectRgba", 0)));
            }
        } else if (type == 4) {
            // Alpha mode uses the stored 8.8 multiplier, not the rounded percent cache.
            double alpha = n(transform, "alphaMultiplier", 256) / 256.0;
            double bounded = Math.max(0, Math.min(1, alpha));
            if (alpha != bounded) {
                session.issue("COLOR_EFFECT_RANGE", transform, "Alpha multiplier clamped to the native XFL range.");
            }
            color.setAttribute("alphaMultiplier", number(bounded));
        } else {
            for (String channel : new String[]{"red", "green", "blue", "alpha"}) {
                color.setAttribute(channel + "Multiplier", number(n(transform, channel + "Multiplier", 256) / 256.0));
                color.setAttribute(channel + "Offset", Long.toString(n(transform, channel + "Offset", 0)));
            }
        }
    }

    void filters(Element e, FlaRecord r) {
        List<FlaRecord> filters = records(record(r, "filters"), "entries");
        Element list = null;
        for (FlaRecord filter : filters) {
            Element out = e.getOwnerDocument().createElementNS(NS, "pending");
            try {
                out = filter(out, filter);
            } catch (IllegalArgumentException ex) {
                session.issue("FILTER_VALUE", filter, ex.getMessage());
                continue;
            }
            if (out == null) {
                continue;
            }
            if (list == null) {
                list = child(e, "filters");
            }
            list.appendChild(out);
        }
    }

    private Element filter(Element holder, FlaRecord r) {
        String kind = s(r, "kind", "");
        String[] kinds = {"dropShadow", "blur", "glow", "bevel", "gradientGlow", "adjustColor", "gradientBevel"};
        String[] tags = {"DropShadowFilter", "BlurFilter", "GlowFilter", "BevelFilter", "GradientGlowFilter", "AdjustColorFilter", "GradientBevelFilter"};
        int index = Arrays.asList(kinds).indexOf(kind);
        if (index < 0 || n(r, "revision", 0) == 0) {
            session.issue("FILTER_REVISION", r, "Filter has no archived settings: " + kind);
            return null;
        }
        FlaRecord g = kind.equals("gradientBevel") ? record(r, "gradient") : r;
        if (g == null) {
            throw new IllegalArgumentException("Missing gradient-bevel settings.");
        }
        FlaRecord settings = record(g, "settings");
        if (settings == null) {
            settings = g;
        }
        Element e = child(holder, tags[index]);
        FlaRecord state = record(settings, "state");
        if (state == null) {
            state = record(r, "state");
        }
        e.setAttribute("isEnabled", Boolean.toString(n(state, "enabled", 1) != 0));
        if (kind.equals("adjustColor")) {
            for (String field : new String[]{"brightness", "contrast", "saturation", "hue"}) {
                scalar(e, field, r, field, 0);
            }
            return e;
        }
        for (String field : new String[]{"blurX", "blurY"}) {
            scalar(e, field, settings, settings.get(field) == null ? "blurX" : field, 5);
        }
        e.setAttribute("quality", Long.toString(n(settings, "quality", 1)));
        if (!kind.equals("blur")) {
            e.setAttribute("strength", number(n(settings, "strengthPercent", 100) / 100.0));
            e.setAttribute("knockout", Boolean.toString(n(settings, "knockout", 0) != 0));
            if (kind.equals("dropShadow") || kind.equals("glow")) {
                e.setAttribute("inner", Boolean.toString(n(settings, "inner", 0) != 0));
            }
            if (kind.equals("dropShadow")) {
                e.setAttribute("hideObject", Boolean.toString(n(settings, "hideObject", 0) != 0));
            }
            if (!kind.equals("glow")) {
                double angle = scalarValue(settings, "angleRadians", Math.PI / 4) * 180 / Math.PI;
                e.setAttribute("angle", number(angle));
                scalar(e, "distance", settings, "distance", 5);
            }
            if (kind.equals("bevel") || kind.startsWith("gradient")) {
                e.setAttribute("type", n(g, "full", 0) != 0 ? "full" : n(settings, "inner", 0) != 0 ? "inner" : "outer");
            }
            if (kind.equals("bevel")) {
                rgba(e, "shadowColor", "shadowAlpha", n(settings, "colorRgba", 0xFF000000L));
                rgba(e, "highlightColor", "highlightAlpha", n(g, "highlightColorRgba", 0xFFFFFFFFL));
            } else if (kind.startsWith("gradient")) {
                for (FlaRecord stop : records(g, "stops")) {
                    Element entry = child(e, "GradientEntry");
                    entry.setAttribute("ratio", number(n(stop, "ratio", 0) / 255.0));
                    color(entry, n(stop, "colorRgba", 0));
                }
            } else {
                color(e, n(settings, "colorRgba", 0xFF000000L));
            }
        }
        return e;
    }

    private static void rgba(Element e, String color, String alpha, long rgba) {
        e.setAttribute(color, rgb(rgba));
        e.setAttribute(alpha, number((rgba >>> 24 & 255) / 255.0));
    }

    private static double scalarValue(FlaRecord r, String field, double fallback) {
        Object value = r.get(field);
        double v = value instanceof Number ? ((Number) value).doubleValue() : fallback;
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException("Nonfinite filter " + field);
        }
        return v;
    }

    private static void scalar(Element e, String attr, FlaRecord r, String field, double fallback) {
        e.setAttribute(attr, number(scalarValue(r, field, fallback)));
    }
}
