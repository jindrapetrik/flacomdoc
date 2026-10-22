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
import org.w3c.dom.*;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Retains editable rectangle/oval parameters and their single fill and stroke.
 */
final class XflPrimitiveWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflPrimitiveWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void primitive(Element parent, FlaRecord r) {
        FlaRecord data = record(r, "shapeData");
        boolean oval = "CPicOval".equals(r.getClassName());
        if (!r.isComplete() || data == null || !data.isComplete()) {
            session.issue("PARTIAL_SHAPE", r, "Incomplete primitive omitted.");
            return;
        }
        // Render to a detached holder so unsupported paints never leave partial XML.
        Element holder = parent.getOwnerDocument().createElementNS(NS, "holder");
        new XflShapeWriter(session).shape(holder, r, false);
        if (!holder.hasChildNodes()) {
            return;
        }
        Element shape = (Element) holder.getFirstChild();
        Set<Integer> fills = new LinkedHashSet<>(), strokes = new LinkedHashSet<>();
        List<FlaRecord> edges = records(data, "edges");
        for (FlaRecord e : edges) {
            add(fills, n(e, "fillIndex0", 0));
            add(fills, n(e, "fillIndex1", 0));
            add(strokes, n(e, "strokeIndex", 0));
        }
        if (fills.size() > 1 || strokes.size() > 1) {
            session.issue("PRIMITIVE_STYLES", r, "Primitive has multiple active paints; preserved as an ordinary shape.");
            parent.appendChild(shape);
            return;
        }
        double[] b;
        if (oval) {
            FlaBounds bounds = r.getBounds();
            if (bounds == null || !bounds.isDefined()) {
                fallback(parent, shape, r, "Missing oval bounds.");
                return;
            }
            b = new double[]{bounds.getXMinPixels(), bounds.getYMinPixels(), bounds.getXMaxPixels(), bounds.getYMaxPixels()};
        } else {
            b = bounds(edges);
        }
        if (b == null || b[2] < b[0] || b[3] < b[1]) {
            fallback(parent, shape, r, "Invalid primitive bounds.");
            return;
        }
        Element out = parent.getOwnerDocument().createElementNS(NS, oval ? "DOMOvalObject" : "DOMRectangleObject");
        out.setAttribute("x", number(b[0]));
        out.setAttribute("y", number(b[1]));
        out.setAttribute("objectWidth", number(b[2] - b[0]));
        out.setAttribute("objectHeight", number(b[3] - b[1]));
        out.setAttribute("selected", Boolean.toString((n(r, "flags", 0) & 2) != 0));
        out.setAttribute(oval ? "closePath" : "lockFlag", Boolean.toString((n(r, "primitiveFlags", 0) & 1) != 0));
        if (oval) {
            for (String field : new String[]{"startAngleDegrees", "endAngleDegrees", "innerRadiusPercent"}) {
                Object v = r.get(field);
                if (!(v instanceof Number) || !Double.isFinite(((Number) v).doubleValue())) {
                    fallback(parent, shape, r, "Nonfinite oval parameters.");
                    return;
                }
                out.setAttribute(field.equals("innerRadiusPercent") ? "innerRadius" : field.replace("Degrees", ""), number(((Number) v).doubleValue()));
            }
        } else {
            Object radii = r.get("cornerRadii");
            if (!(radii instanceof List) || ((List<?>) radii).size() != 4) {
                fallback(parent, shape, r, "Missing rectangle radii.");
                return;
            }
            String[] names = {"topLeftRadius", "topRightRadius", "bottomLeftRadius", "bottomRightRadius"};
            for (int i = 0; i < 4; i++) {
                out.setAttribute(names[i], number(((Number) ((List<?>) radii).get(i)).doubleValue() / 20));
            }
        }
        matrix(out, r.getMatrix());
        XflObjectWriter.transformPoint(session, out, r);
        copyPaint(out, shape, fills, "FillStyle", "fill");
        copyPaint(out, shape, strokes, "StrokeStyle", "stroke");
        if ((n(r, "flags", 0) & 4) != 0) {
            session.issue("OBJECT_LOCK", r, "Primitive lock flag is not exported yet.");
        }
        if ((n(r, "primitiveFlags", 0) & ~1L) != 0) {
            session.issue("PRIMITIVE_FLAGS", r, "Unknown primitive flag bits retained only in the binary source.");
        }
        parent.appendChild(out);
    }

    private void fallback(Element parent, Element shape, FlaRecord r, String reason) {
        session.issue("PRIMITIVE_GEOMETRY", r, reason + " Rendered shape preserved.");
        parent.appendChild(shape);
    }

    private static void add(Set<Integer> values, long value) {
        int index = (int) value & 0x7fff;
        if (index != 0) {
            values.add(index);
        }
    }

    private static void copyPaint(Element out, Element shape, Set<Integer> indexes, String tag, String container) {
        if (indexes.isEmpty()) {
            return;
        }
        int index = indexes.iterator().next();
        NodeList list = shape.getElementsByTagNameNS(NS, tag);
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            if (Integer.toString(index).equals(e.getAttribute("index"))) {
                Element target = child(out, container);
                for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
                    target.appendChild(n.cloneNode(true));
                }
                return;
            }
        }
    }

    private static double[] bounds(List<FlaRecord> edges) {
        if (edges.isEmpty()) {
            return null;
        }
        double[] b = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (FlaRecord e : edges) {
            for (int axis = 0; axis < 2; axis++) {
                String a = axis == 0 ? "X" : "Y";
                double p = n(e, "start" + a, 0) / 5120.0, q = n(e, "end" + a, 0) / 5120.0;
                b[axis] = Math.min(b[axis], Math.min(p, q));
                b[axis + 2] = Math.max(b[axis + 2], Math.max(p, q));
                if (Boolean.TRUE.equals(e.get("curve"))) {
                    double c = n(e, "control" + a, 0) / 5120.0, denominator = p - 2 * c + q;
                    if (denominator != 0) {
                        double t = (p - c) / denominator;
                        if (t > 0 && t < 1) {
                            double v = (1 - t) * (1 - t) * p + 2 * t * (1 - t) * c + t * t * q;
                            b[axis] = Math.min(b[axis], v);
                            b[axis + 2] = Math.max(b[axis + 2], v);
                        }
                    }
                }
            }
        }
        return b;
    }
}
