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

import com.jpexs.flash.fla.reader.FlaPaintStyle;
import com.jpexs.flash.fla.reader.FlaRecord;
import com.jpexs.flash.fla.reader.FlaStrokeStyle;
import java.util.List;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Emits one shape atomically: unsupported styles never leave dangling indexes.
 */
final class XflShapeWriter {

    private final BinaryFlaToXflExporter.Session session;
    private boolean historicalAlpha;

    XflShapeWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void shape(Element parent, FlaRecord owner, boolean floating) {
        FlaRecord data = record(owner, "shapeData");
        List<FlaRecord> edges = records(data, "edges");
        if (edges.isEmpty()) {
            return;
        }
        if (!data.isComplete()) {
            session.issue("PARTIAL_SHAPE", data, "Incomplete shape omitted.");
            return;
        }
        historicalAlpha = n(data, "revision", 0) < 4;
        Element shape = parent.getOwnerDocument().createElementNS(NS, "DOMShape");
        shape.setAttribute("selected", Boolean.toString((n(owner, "flags", 0) & 2) != 0));
        if ((n(owner, "drawingObjectFlags", 0) & 1) != 0) {
            shape.setAttribute("isDrawingObject", "true");
        }
        if (floating) {
            shape.setAttribute("isFloating", "true");
            if ((n(owner, "flags", 0) & 4) != 0) {
                session.issue("OBJECT_LOCK", owner, "Drawing-object lock flag is not exported yet.");
            }
        }
        matrix(shape, owner.getMatrix());
        List<FlaRecord> fills = records(data, "fills"), strokes = records(data, "strokes");
        Element fillList = fills.isEmpty() ? null : child(shape, "fills");
        for (int i = 0; i < fills.size(); i++) {
            Element fill = child(fillList, "FillStyle");
            fill.setAttribute("index", Integer.toString(i + 1));
            if (!paint(fill, fills.get(i), null)) {
                return;
            }
        }
        Element strokeList = strokes.isEmpty() ? null : child(shape, "strokes");
        for (int i = 0; i < strokes.size(); i++) {
            FlaRecord r = strokes.get(i);
            FlaStrokeStyle style = r.getStrokeStyle();
            if (style == null || style.getKind() == FlaStrokeStyle.Kind.UNKNOWN) {
                session.issue("STROKE_STYLE", r, "Shape omitted: procedural/unknown stroke is not exported yet.");
                return;
            }
            Element entry = child(strokeList, "StrokeStyle");
            entry.setAttribute("index", Integer.toString(i + 1));
            String[] strokeTags = {"SolidStroke", "DashedStroke", "DottedStroke", "RaggedStroke", "StippleStroke", "HatchedStroke"};
            Element stroke = child(entry, strokeTags[style.getKind().ordinal()]);
            if (!procedural(stroke, style, r)) {
                return;
            }
            stroke.setAttribute("weight", number(style.getWidthPixels()));
            stroke.setAttribute("sharpCorners", Boolean.toString(style.hasSharpCorners()));
            stroke.setAttribute("pixelHinting", Boolean.toString(style.getPixelHintingFlag() != null && style.getPixelHintingFlag() != 0));
            if (!option(stroke, "scaleMode", style.getScaleModeId(), new String[]{"normal", "horizontal", "vertical", "none"}, r)) {
                return;
            }
            if (style.getKind() == FlaStrokeStyle.Kind.SOLID
                    && (!option(stroke, "caps", style.getCapStyleId(), new String[]{"none", "round", "square"}, r)
                    || !option(stroke, "joints", style.getJoinStyleId(), new String[]{"miter", "round", "bevel"}, r))) {
                return;
            }
            if (style.getKind() == FlaStrokeStyle.Kind.SOLID && style.getMiterLimit() != null) {
                stroke.setAttribute("miterLimit", number(style.getMiterLimit()));
            }
            Element fill = child(stroke, "fill");
            FlaRecord paint = record(r, "paint");
            long outerColor = n(r, "colorRgba", 0);
            if (historicalAlpha && (outerColor >>> 24 & 255) == 0) {
                outerColor |= 0xFF000000L;
            }
            if (paint == null) {
                color(child(fill, "SolidColor"), outerColor);
            } else if (!paint(fill, paint, outerColor)) {
                return;
            }
        }
        Element edgeList = child(shape, "edges");
        for (FlaRecord r : edges) {
            long rawStroke = n(r, "strokeIndex", 0), rawFill0 = n(r, "fillIndex0", 0), rawFill1 = n(r, "fillIndex1", 0);
            // Bit 15 is contour selection, not part of the style-table index.
            long stroke = rawStroke & 0x7FFF, fill0 = rawFill0 & 0x7FFF, fill1 = rawFill1 & 0x7FFF;
            if (stroke < 0 || stroke > strokes.size() || fill0 < 0 || fill0 > fills.size() || fill1 < 0 || fill1 > fills.size()) {
                session.issue("STYLE_INDEX", r, "Shape omitted: edge refers to a missing fill/stroke style.");
                return;
            }
            Element e = child(edgeList, "Edge");
            e.setAttribute("strokeStyle", Long.toString(stroke));
            e.setAttribute("fillStyle0", Long.toString(fill0));
            e.setAttribute("fillStyle1", Long.toString(fill1));
            StringBuilder path = new StringBuilder("!").append(point(r, "start"));
            int selection = ((rawStroke & 0x8000) != 0 ? 4 : 0)
                    | ((rawFill0 & 0x8000) != 0 ? 1 : 0) | ((rawFill1 & 0x8000) != 0 ? 2 : 0);
            if (selection != 0) {
                path.append('S').append(selection);
            }
            if (Boolean.TRUE.equals(r.get("curve"))) {
                path.append('[').append(point(r, "control")).append(' ');
            } else {
                path.append(n(r, "lineFlags", 0) == 1 ? '/' : '|');
                if (n(r, "lineFlags", 0) > 1) {
                    session.issue("LINE_FLAGS", r, "Unknown line flag; emitted as a straight edge.");
                }
            }
            path.append(point(r, "end"));
            e.setAttribute("edges", path.toString());
        }
        for (FlaRecord cubic : records(data, "auxiliary")) {
            com.jpexs.flash.fla.reader.FlaCubic c = cubic.getCubic();
            if (c == null || (cubic.get("entries") != null && c.getApproximation() == null)
                    || (c.getFlags() != null && ((c.getFlags() & 1) != 0 && c.getBoundaryStart() == null
                    || (c.getFlags() & 2) != 0 && c.getBoundaryEnd() == null))) {
                session.issue("CUBIC_DATA", cubic, "Incomplete cubic editing data omitted; rendered edges retained.");
                continue;
            }
            java.util.List<com.jpexs.flash.fla.reader.FlaPoint2D> points = c.getPoints();
            StringBuilder value = new StringBuilder("!").append(twips(points.get(0), " ")).append('(');
            if (c.getFlags() != null && (c.getFlags() & 1) != 0) {
                value.append(twips(c.getBoundaryStart(), ","));
            }
            value.append(';');
            for (int i = 1; i < 4; i++) {
                if (i > 1) {
                    value.append(' ');
                }
                value.append(twips(points.get(i), ","));
            }
            // Older archives store only the control net. XFL permits an empty point list.
            if (c.getApproximation() != null) {
                for (com.jpexs.flash.fla.reader.FlaCubic.ApproximationPoint point : c.getApproximation()) {
                    char command = point.getSecondaryFlag() != 0
                            ? (point.isControl() ? 'P' : 'p') : (point.isControl() ? 'Q' : 'q');
                    value.append(command).append(twips(point.getPoint(), " "));
                }
            }
            value.append(')');
            if (c.getFlags() != null && (c.getFlags() & 2) != 0) {
                value.append(twips(c.getBoundaryEnd(), ","));
            }
            value.append(';');
            child(edgeList, "Edge").setAttribute("cubics", value.toString());
        }
        parent.appendChild(shape);
    }

    private static String twips(com.jpexs.flash.fla.reader.FlaPoint2D p, String separator) {
        return p.getRawX() + separator + p.getRawY();
    }

    private boolean procedural(Element e, FlaStrokeStyle style, FlaRecord r) {
        java.util.Map<String, Integer> p = style.getParameters();
        switch (style.getKind()) {
            case DASHED:
                if (!p.containsKey("dashLengthTwips")) {
                    session.issue("STROKE_STYLE", r, "Dashed stroke has no stored dash length.");
                    return false;
                }
                e.setAttribute("dash1", number(p.get("dashLengthTwips") / 20.0));
                e.setAttribute("dash2", number(p.get("gapLengthTwips") / 20.0));
                break;
            case DOTTED:
                e.setAttribute("dotSpace", number(p.get("dotSpacingTwips") / 20.0));
                break;
            case RAGGED:
                if (!option(e, "pattern", p.get("pattern"), new String[]{"solid", "simple", "random", "dotted", "random dotted", "triple dotted", "random triple dotted"}, r)) {
                    return false;
                }
                option(e, "waveHeight", p.get("waveHeight"), new String[]{"flat", "wavy", "very wavy", "wild"}, r);
                option(e, "waveLength", p.get("waveLength"), new String[]{"very short", "short", "medium", "long"}, r);
                break;
            case STIPPLE:
                option(e, "dotSize", p.get("dotSize"), new String[]{"tiny", "small", "medium", "large"}, r);
                option(e, "variation", p.get("variation"), new String[]{"one size", "small variation", "varied sizes", "random sizes"}, r);
                option(e, "density", p.get("density"), new String[]{"very dense", "dense", "sparse", "very sparse"}, r);
                break;
            case HATCHED:
                option(e, "hatchThickness", p.get("hatchThickness"), new String[]{"hairline", "thin", "medium", "thick"}, r);
                option(e, "space", p.get("space"), new String[]{"very close", "close", "distant", "very distant"}, r);
                option(e, "rotate", p.get("rotate"), new String[]{"none", "slight", "medium", "free"}, r);
                option(e, "jiggle", p.get("jiggle"), new String[]{"none", "bounce", "loose", "wild"}, r);
                option(e, "curve", p.get("curve"), new String[]{"straight", "slight curve", "medium curve", "very curved"}, r);
                option(e, "length", p.get("length"), new String[]{"equal", "slight variation", "medium variation", "random"}, r);
                break;
            default:
                break;
        }
        return true;
    }

    private boolean option(Element e, String name, Integer value, String[] names, FlaRecord r) {
        if (value == null) {
            return true;
        }
        if (value < 0 || value >= names.length) {
            session.issue("STROKE_OPTION", r, "Shape omitted: unknown stroke " + name + " " + value);
            return false;
        }
        e.setAttribute(name, names[value]);
        return true;
    }

    private String point(FlaRecord r, String name) {
        // XFL edge coordinates use twips, including eight fractional bits.
        return coordinate((int) n(r, name + "X", 0)) + " " + coordinate((int) n(r, name + "Y", 0));
    }

    private String coordinate(int word) {
        if ((word & 255) == 0) {
            return Integer.toString(word >> 8);
        }
        return "#" + Integer.toHexString((word >> 8) & 0xFFFFFF).toUpperCase(java.util.Locale.ROOT)
                + "." + String.format(java.util.Locale.ROOT, "%02X", word & 255);
    }

    private boolean paint(Element parent, FlaRecord record, Long overrideRgba) {
        FlaPaintStyle style = record.getPaintStyle();
        if (style == null && record.get("legacyValue") instanceof Number && record.get("packedValue") instanceof Number) {
            color(child(parent, "SolidColor"), n(record, "packedValue", 0) | 0xFF000000L);
            return true;
        }
        if (style == null) {
            session.issue("PAINT_STYLE", record, "Shape omitted: historical or incomplete paint is not exported yet.");
            return false;
        }
        switch (style.getKind()) {
            case SOLID:
                color(child(parent, "SolidColor"), overrideRgba == null ? historicalAlpha && style.getFlags() == 0 ? style.getRgbaWord() | 0xFF000000L : style.getRgbaWord() : overrideRgba);
                break;
            case LINEAR_GRADIENT:
            case RADIAL_GRADIENT:
                if (style.getMatrix() == null || style.getStops() == null) {
                    session.issue("PAINT_STYLE", record, "Shape omitted: incomplete gradient.");
                    return false;
                }
                Element gradient = child(parent, style.getKind() == FlaPaintStyle.Kind.LINEAR_GRADIENT ? "LinearGradient" : "RadialGradient");
                if (style.getFocalRatio() != null && style.getKind() == FlaPaintStyle.Kind.RADIAL_GRADIENT) {
                    gradient.setAttribute("focalPointRatio", number(style.getFocalRatio()));
                }
                if (style.getSpread() != null) {
                    gradient.setAttribute("spreadMethod", style.getSpread() == FlaPaintStyle.Spread.EXTEND
                            ? "pad" : style.getSpread().name().toLowerCase(java.util.Locale.ROOT));
                }
                if (style.isLinearRgb() != null) {
                    gradient.setAttribute("interpolationMethod", style.isLinearRgb() ? "linearRGB" : "rgb");
                }
                matrix(gradient, style.getMatrix());
                for (FlaPaintStyle.Stop stop : style.getStops()) {
                    Element entry = child(gradient, "GradientEntry");
                    entry.setAttribute("ratio", number(stop.getPosition()));
                    color(entry, historicalAlpha ? stop.getRgbaWord() | 0xFF000000L : stop.getRgbaWord());
                }
                break;
            case BITMAP:
                XflBitmapWriter.Bitmap bitmap = style.getBitmapMediaId() == null ? null
                        : session.bitmaps.get(style.getBitmapMediaId().longValue());
                if (bitmap == null || style.getMatrix() == null) {
                    session.issue("MISSING_BITMAP", record, "Shape omitted: bitmap fill refers to an unavailable bitmap or matrix.");
                    return false;
                }
                bitmap = session.bitmapSmoothing(bitmap, style.isBitmapSmoothed(), record);
                Element fill = child(parent, "BitmapFill");
                session.attribute(fill, "bitmapPath", bitmap.name, record);
                fill.setAttribute("bitmapIsClipped", style.isBitmapClipped().toString());
                matrix(fill, style.getMatrix());
                break;
            default:
                session.issue("PAINT_STYLE", record, "Shape omitted: " + style.getKind() + " paint is not exported yet.");
                return false;
        }
        return true;
    }
}
