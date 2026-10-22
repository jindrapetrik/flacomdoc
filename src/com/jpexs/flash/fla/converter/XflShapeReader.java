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
import java.util.regex.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Builds style tables and absolute fixed-24.8 edge records from XFL paths.
 */
final class XflShapeReader {

    final XflReader.Session s;

    XflShapeReader(XflReader.Session s) {
        this.s = s;
    }

    static FlaRecord empty() {
        return r("shapeData", "revision", 5, "extensionMode", 3, "fills", new ArrayList<FlaRecord>(), "strokes", new ArrayList<FlaRecord>(), "edges", new ArrayList<FlaRecord>(), "auxiliary", new ArrayList<FlaRecord>());
    }

    FlaRecord shape(Element e) throws IOException {
        FlaRecord record = r("CPicShape", "shapeRevision", XflReader.Session.V.picShapeRevision, "shapeData", data(e));
        placement(record, e);
        record.set("drawingObjectFlags", b(e, "isDrawingObject", false) ? 1 : 0);
        return record;
    }

    @SuppressWarnings("unchecked")
    FlaRecord data(Element e) throws IOException {
        FlaRecord data = empty();
        List<FlaRecord> fills = (List<FlaRecord>) data.get("fills"), strokes = (List<FlaRecord>) data.get("strokes"), edges = (List<FlaRecord>) data.get("edges");
        Map<Integer, Integer> fi = new HashMap<>(), si = new HashMap<>();
        for (Element f : children(child(e, "fills"), "FillStyle")) {
            int index = i(f, "index", fills.size() + 1);
            if (index <= 0 || fi.put(index, fills.size() + 1) != null) {
                throw new IOException("Duplicate/invalid fill index");
            }
            fills.add(paint(first(f)));
        }
        for (Element st : children(child(e, "strokes"), "StrokeStyle")) {
            int index = i(st, "index", strokes.size() + 1);
            if (index <= 0 || si.put(index, strokes.size() + 1) != null) {
                throw new IOException("Duplicate/invalid stroke index");
            }
            strokes.add(stroke(first(st)));
        }
        for (Element edge : children(child(e, "edges"), "Edge")) {
            int stroke = index(si, i(edge, "strokeStyle", 0)), fill0 = index(fi, i(edge, "fillStyle0", 0)), fill1 = index(fi, i(edge, "fillStyle1", 0));
            edges.addAll(path(s(edge, "edges", ""), stroke, fill0, fill1));
            if (edge.hasAttribute("cubics")) {
                ((List<FlaRecord>) data.get("auxiliary")).add(cubic(edge.getAttribute("cubics")));
            }
        }
        data.set("metadata", (long) edges.size());
        return data;
    }

    static Element first(Element e) throws IOException {
        List<Element> list = children(e);
        if (list.size() != 1) {
            throw new IOException("Expected one paint/stroke element in " + (e == null ? "missing element" : tag(e)));
        }
        return list.get(0);
    }

    int index(Map<Integer, Integer> map, int id) throws IOException {
        if (id == 0) {
            return 0;
        }
        Integer n = map.get(id);
        if (n == null) {
            throw new IOException("Edge references missing style " + id);
        }
        return n;
    }

    FlaRecord paint(Element e) throws IOException {
        if (e == null) {
            throw new IOException("Missing fill paint");
        }
        String name = tag(e);
        FlaRecord r = r("fills", "flags", 0, "secondaryFlags", 0, "packedValue", color(e, 0xff000000L));
        if (name.equals("SolidColor") || name.equals("SolidSwatchItem")) {
            return r;
        }
        if (name.equals("LinearGradient") || name.equals("RadialGradient") || name.equals("LinearGradientSwatchItem") || name.equals("RadialGradientSwatchItem")) {
            r.set("flags", name.startsWith("Linear") ? 16 : 18);
            r.set("packedValue", 0L);
            r.set("matrixWords", matrix(e));
            r.set("focalPoint", scaled(d(e, "focalPointRatio", 0), 255));
            int spread = choice(e, "spreadMethod", 0, "pad", "reflect", "repeat");
            r.set("gradientFlags", (long) (spread * 4 + (s(e, "interpolationMethod", "rgb").equals("linearRGB") ? 1 : 0)));
            List<FlaRecord> stops = new ArrayList<>();
            for (Element entry : children(e, "GradientEntry")) {
                double ratio = d(entry, "ratio", 0);
                if (ratio < 0 || ratio > 1) {
                    throw new IOException("Gradient ratio outside 0..1");
                }
                stops.add(r("stops", "ratio", (int) Math.round(ratio * 255), "colorRgba", color(entry, 0xff000000L)));
            }
            if (stops.size() < 1 || stops.size() > 15) {
                throw new IOException("Binary gradients require 1..15 stops");
            }
            r.set("stops", stops);
            return r;
        }
        if (name.equals("BitmapFill")) {
            String path = s(e, "bitmapPath", "");
            FlaRecord bitmap = s.media.get(path);
            if (bitmap == null || !bitmap.getClassName().equals("CMediaBits")) {
                throw new IOException("Unknown bitmap fill " + path);
            }
            r.set("packedValue", 0xff0000ffL);
            r.set("flags", 64 + (b(e, "bitmapIsClipped", false) ? 1 : 0) + (((Number) bitmap.get("allowSmoothing")).intValue() == 0 ? 2 : 0));
            r.set("bitmapMediaId", ((Number) bitmap.get("mediaId")).intValue());
            r.set("matrixWords", matrix(e));
            return r;
        }
        throw new IOException("Unsupported XFL paint " + name);
    }

    FlaRecord stroke(Element e) throws IOException {
        String name = tag(e);
        int a = 0, z = b(e, "sharpCorners", false) ? 0x8000 : 0;
        switch (name) {
            case "SolidStroke":
                break;
            case "DashedStroke":
                a = twips(d(e, "dash1", 6));
                z |= twips(d(e, "dash2", 6));
                break;
            case "DottedStroke":
                z |= 2 | scaled(d(e, "dotSpace", 3), 10) << 4;
                break;
            case "RaggedStroke":
                z |= 3 | choice(e, "pattern", 1, "solid", "simple", "random", "dotted", "random dotted", "triple dotted", "random triple dotted") << 3 | choice(e, "waveHeight", 1, "flat", "wavy", "very wavy", "wild") << 6 | choice(e, "waveLength", 1, "very short", "short", "medium", "long") << 8;
                break;
            case "StippleStroke":
                z |= 4 | choice(e, "dotSize", 1, "tiny", "small", "medium", "large") << 3 | choice(e, "variation", 2, "one size", "small variation", "varied sizes", "random sizes") << 5 | choice(e, "density", 2, "very dense", "dense", "sparse", "very sparse") << 7;
                break;
            case "HatchedStroke":
                z |= 5 | choice(e, "hatchThickness", 0, "hairline", "thin", "medium", "thick") << 3 | choice(e, "space", 2, "very close", "close", "distant", "very distant") << 5 | choice(e, "rotate", 0, "none", "slight", "medium", "free") << 7 | choice(e, "jiggle", 0, "none", "bounce", "loose", "wild") << 9 | choice(e, "curve", 0, "straight", "slight curve", "medium curve", "very curved") << 11 | choice(e, "length", 0, "equal", "slight variation", "medium variation", "random") << 13;
                break;
            default:
                throw new IOException("Unsupported stroke " + name);
        }
        FlaRecord paint = paint(first(child(e, "fill")));
        long color = ((Number) paint.get("packedValue")).longValue();
        FlaRecord r = r("strokes", "width", twips(d(e, "weight", 1)), "styleWord1", a, "styleWord2", z, "colorRgba", color, "paint", paint, "pixelHinting", b(e, "pixelHinting", false) ? 1 : 0, "scaleMode", choice(e, "scaleMode", 0, "normal", "horizontal", "vertical", "none"), "capStyle", choice(e, "caps", 1, "none", "round", "square"), "joinStyle", choice(e, "joints", 1, "miter", "round", "bevel"), "miterLimitWord", scaled(d(e, "miterLimit", 3), 256));
        return r;
    }

    static int coord(String value) {
        if (value.startsWith("#")) {
            String[] p = value.substring(1).split("\\.", -1);
            long whole = Long.parseLong(p[0], 16);
            if (whole > 0x7fffff) {
                whole -= 0x1000000;
            }
            String f = p.length > 1 ? p[1] : "";
            if (f.length() > 2) {
                throw new IllegalArgumentException("Edge hex fraction exceeds 8 bits");
            }
            long v = whole * 256 + (f.isEmpty() ? 0 : Integer.parseInt((f + "00").substring(0, 2), 16));
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Edge coordinate out of range");
            }
            return (int) v;
        }
        return new BigDecimal(value).multiply(BigDecimal.valueOf(256)).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
    }

    static List<FlaRecord> path(String text, int stroke, int fill0, int fill1) throws IOException {
        text = text.replaceAll("([!|/\\[\\]])", " $1 ").trim();
        List<FlaRecord> result = new ArrayList<>();
        if (text.isEmpty()) {
            return result;
        }
        String[] t = text.split("\\s+");
        int at = 0, x = 0, y = 0, selection = 0;
        boolean moved = false;
        try {
            while (at < t.length) {
                String cmd = t[at++];
                if (cmd.equals("!")) {
                    x = coord(t[at++]);
                    String yv = t[at++];
                    int n = yv.indexOf('S');
                    selection = n < 0 ? 0 : Integer.parseInt(yv.substring(n + 1));
                    y = coord(n < 0 ? yv : yv.substring(0, n));
                    moved = true;
                    continue;
                }
                if (!moved) {
                    throw new IOException("XFL edge must begin with move");
                }
                boolean curve = cmd.equals("[");
                if (!curve && !cmd.equals("|") && !cmd.equals("/")) {
                    throw new IOException("Unknown edge command " + cmd);
                }
                int cx = 0, cy = 0;
                if (curve) {
                    cx = coord(t[at++]);
                    cy = coord(t[at++]);
                }
                int ex = coord(t[at++]), ey = coord(t[at++]);
                FlaRecord edge = r("edges", "startX", x, "startY", y, "endX", ex, "endY", ey, "curve", curve, "lineFlags", cmd.equals("/") ? 1 : 0, "strokeIndex", stroke == 0 ? 0 : stroke | ((selection & 4) != 0 ? 0x8000 : 0), "fillIndex0", fill0 == 0 ? 0 : fill0 | ((selection & 1) != 0 ? 0x8000 : 0), "fillIndex1", fill1 == 0 ? 0 : fill1 | ((selection & 2) != 0 ? 0x8000 : 0));
                if (curve) {
                    edge.set("controlX", cx);
                    edge.set("controlY", cy);
                }
                result.add(edge);
                x = ex;
                y = ey;
            }
        } catch (IndexOutOfBoundsException | NumberFormatException | ArithmeticException ex) {
            throw new IOException("Invalid XFL edge path: " + text, ex);
        }
        return result;
    }
    private static final Pattern CUBICS_PATTERN = Pattern.compile("^!(?<mx>-?[0-9]+) +(?<my>-?[0-9]+) *\\(((?<pBCPx>-?[0-9]+) *, *(?<pBCPy>-?[0-9]+))? *; *(?<x1>-?[0-9]+),(?<y1>-?[0-9]+) +(?<x2>-?[0-9]+),(?<y2>-?[0-9]+) +(?<ex>-?[0-9]+),(?<ey>-?[0-9]+) *(?<xy>([QqPp]? *-?[0-9]+ +-?[0-9]+)*) *\\)((?<nBCPx>-?[0-9]+) *, *(?<nBCPy>-?[0-9]+))? *; *$");
    private static final Pattern CUBICS_XY_PATTERN = Pattern.compile("(?<letter>[QqPp]?) *(?<x>-?[0-9]+) +(?<y>-?[0-9]+)");

    static FlaRecord cubic(String text) throws IOException {
        Matcher m = CUBICS_PATTERN.matcher(text.replace("\r", "").replace("\n", "").replace('\t', ' '));
        if (!m.matches()) {
            throw new IOException("Invalid XFL cubic: " + text);
        }
        List<Integer> points = new ArrayList<>();
        for (String key : new String[]{"mx", "my", "x1", "y1", "x2", "y2", "ex", "ey"}) {
            points.add(Integer.parseInt(m.group(key)));
        }
        List<FlaRecord> entries = new ArrayList<>();
        Matcher xy = CUBICS_XY_PATTERN.matcher(m.group("xy"));
        String last = "q";
        while (xy.find()) {
            String letter = xy.group("letter");
            if (letter.isEmpty()) {
                letter = last;
            }
            last = letter;
            entries.add(r("entries", "x", Integer.parseInt(xy.group("x")), "y", Integer.parseInt(xy.group("y")), "controlFlag", Character.isUpperCase(letter.charAt(0)) ? 1 : 0, "value2", letter.equalsIgnoreCase("p") ? 1 : 0));
        }
        FlaRecord result = r("auxiliary", "points", points, "entries", entries);
        int flags = 0;
        if (m.group("pBCPx") != null) {
            flags |= 1;
            result.set("boundaryStartPointTwips", Arrays.asList(Integer.parseInt(m.group("pBCPx")), Integer.parseInt(m.group("pBCPy"))));
        }
        if (m.group("nBCPx") != null) {
            flags |= 2;
            result.set("boundaryEndPointTwips", Arrays.asList(Integer.parseInt(m.group("nBCPx")), Integer.parseInt(m.group("nBCPy"))));
        }
        result.set("flags", flags);
        return result;
    }

    @SuppressWarnings("unchecked")
    static void merge(FlaRecord into, FlaRecord shape) {
        FlaRecord from = (FlaRecord) shape.get("shapeData");
        List<FlaRecord> f = (List<FlaRecord>) into.get("fills"), st = (List<FlaRecord>) into.get("strokes"), edges = (List<FlaRecord>) into.get("edges");
        int fo = f.size(), so = st.size();
        f.addAll((List<FlaRecord>) from.get("fills"));
        st.addAll((List<FlaRecord>) from.get("strokes"));
        List<Integer> m = (List<Integer>) shape.get("matrixWords");
        for (FlaRecord edge : (List<FlaRecord>) from.get("edges")) {
            FlaRecord copy = r("edges");
            edge.getFields().forEach(copy::set);
            for (String p : new String[]{"start", "end", "control"}) {
                if (edge.get(p + "X") != null) {
                    int x = ((Number) edge.get(p + "X")).intValue(), y = ((Number) edge.get(p + "Y")).intValue();
                    copy.set(p + "X", scaled((m.get(0) / 65536.0 * x + m.get(2) / 65536.0 * y) + m.get(4) * 256.0, 1));
                    copy.set(p + "Y", scaled((m.get(1) / 65536.0 * x + m.get(3) / 65536.0 * y) + m.get(5) * 256.0, 1));
                }
            }
            for (String key : new String[]{"strokeIndex", "fillIndex0", "fillIndex1"}) {
                int v = ((Number) edge.get(key)).intValue();
                if ((v & 0x7fff) != 0) {
                    copy.set(key, (v & 0x8000) | ((v & 0x7fff) + (key.equals("strokeIndex") ? so : fo)));
                }
            }
            edges.add(copy);
        }
        ((List<FlaRecord>) into.get("auxiliary")).addAll((List<FlaRecord>) from.get("auxiliary"));
        into.set("metadata", (long) edges.size());
    }
}
