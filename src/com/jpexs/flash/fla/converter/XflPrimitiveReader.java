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
 * Primitive editing parameters plus renderable quadratic fallback geometry.
 */
final class XflPrimitiveReader {

    final XflReader.Session s;

    XflPrimitiveReader(XflReader.Session s) {
        this.s = s;
    }

    @SuppressWarnings("unchecked")
    FlaRecord read(Element e) throws IOException {
        boolean oval = tag(e).equals("DOMOvalObject");
        FlaRecord data = XflShapeReader.empty();
        List<FlaRecord> fills = (List<FlaRecord>) data.get("fills"), strokes = (List<FlaRecord>) data.get("strokes"), edges = (List<FlaRecord>) data.get("edges");
        XflShapeReader shapes = new XflShapeReader(s);
        Element fill = child(e, "fill"), stroke = child(e, "stroke");
        if (fill != null) {
            fills.add(shapes.paint(XflShapeReader.first(fill)));
        }
        if (stroke != null) {
            strokes.add(shapes.stroke(XflShapeReader.first(stroke)));
        }
        double x = d(e, "x", 0) * 20, y = d(e, "y", 0) * 20, w = d(e, "objectWidth", 0) * 20, h = d(e, "objectHeight", 0) * 20;
        int fi = fills.isEmpty() ? 0 : 1, si = strokes.isEmpty() ? 0 : 1;
        if (oval) {
            double start = d(e, "startAngle", 0), end = d(e, "endAngle", 360);
            if (end == start) {
                end = start + 360;
            }
            int count = Math.max(1, (int) Math.ceil(Math.abs(end - start) / 45));
            double a = Math.toRadians(start), ex = x + w / 2 + w / 2 * Math.cos(a), ey = y + h / 2 + h / 2 * Math.sin(a);
            for (int n = 1; n <= count; n++) {
                double b = Math.toRadians(start + (end - start) * n / count), mid = (a + b) / 2, k = 1 / Math.cos((b - a) / 2), nx = x + w / 2 + w / 2 * Math.cos(b), ny = y + h / 2 + h / 2 * Math.sin(b);
                edges.add(edge(ex, ey, nx, ny, x + w / 2 + w / 2 * k * Math.cos(mid), y + h / 2 + h / 2 * k * Math.sin(mid), true, fi, si));
                ex = nx;
                ey = ny;
                a = b;
            }
        } else {
            double[][] p = {{x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}, {x, y}};
            for (int n = 0; n < 4; n++) {
                edges.add(edge(p[n][0], p[n][1], p[n + 1][0], p[n + 1][1], 0, 0, false, fi, si));
            }
        }
        FlaRecord result = r(oval ? "CPicOval" : "CPicRect", "revision", 3, "shapeRevision", XflReader.Session.V.picShapeRevision, "shapeData", data, "primitiveFlags", b(e, oval ? "closePath" : "lockFlag", true) ? 1 : 0);
        placement(result, e);
        if (oval) {
            result.set("bounds", Arrays.asList((int) Math.round(x), (int) Math.round(x + w), (int) Math.round(y), (int) Math.round(y + h)));
            result.set("startAngleDegrees", d(e, "startAngle", 0));
            result.set("endAngleDegrees", d(e, "endAngle", 360));
            result.set("innerRadiusPercent", d(e, "innerRadius", 0));
            if (d(e, "innerRadius", 0) != 0) {
                s.issue(e, "Oval inner-radius fallback geometry is not imported yet");
            }
        } else {
            List<Integer> radii = new ArrayList<>();
            for (String key : new String[]{"topLeftRadius", "topRightRadius", "bottomLeftRadius", "bottomRightRadius"}) {
                radii.add(twips(d(e, key, 0)));
            }
            result.set("cornerRadii", radii);
            for (int radius : radii) {
                if (radius != 0) {
                    s.issue(e, "Rounded-rectangle fallback uses square corners; editing radii retained");
                    break;
                }
            }
        }
        data.set("metadata", (long) edges.size());
        return result;
    }

    static FlaRecord edge(double x, double y, double ex, double ey, double cx, double cy, boolean curve, int fill, int stroke) {
        FlaRecord result = r("edges", "startX", scaled(x, 256), "startY", scaled(y, 256), "endX", scaled(ex, 256), "endY", scaled(ey, 256), "curve", curve, "lineFlags", 0, "fillIndex0", 0, "fillIndex1", fill, "strokeIndex", stroke);
        if (curve) {
            result.set("controlX", scaled(cx, 256));
            result.set("controlY", scaled(cy, 256));
        }
        return result;
    }
}
