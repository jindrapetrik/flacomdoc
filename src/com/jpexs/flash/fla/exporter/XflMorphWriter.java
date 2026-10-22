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
import java.util.List;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Stored shape-tween correspondence; points are whole twips in the archive.
 */
final class XflMorphWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflMorphWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void morph(Element frame, FlaRecord r, FlaRecord sourceFrame) {
        if (r == null) {
            return;
        }
        Element out = child(frame, "MorphShape");
        String[] mappings = {"distributive", "angular vertex", "random distribution", "linear walk"};
        // Mapping belongs to the parent frame, independently of morph easing.
        long mapping = n(sourceFrame, "shapeTweenBlend", 0);
        if (r.get("acceleration") instanceof Number) {
            long acceleration = (int) n(r, "acceleration", 0);
            long normalized = Math.max(-100, Math.min(100, acceleration));
            if (normalized != acceleration) {
                session.issue("MORPH_ACCELERATION", r, "Morph acceleration clamped to the native XFL range.");
            }
            out.setAttribute("acceleration", Long.toString(normalized));
        }
        if (mapping >= 0 && mapping < mappings.length) {
            out.setAttribute("mappingType", mappings[(int) mapping]);
        } else {
            session.issue("MORPH_MAPPING", r, "Unassigned morph mapping " + mapping);
        }
        Element segments = child(out, "morphSegments");
        for (FlaRecord segment : records(r, "segments")) {
            if (segment == null) {
                session.issue("MORPH_SEGMENT", r, "Null morph segment omitted.");
                continue;
            }
            Element e = child(segments, "MorphSegment");
            point(e, "startPointA", segment);
            point(e, "startPointB", segment);
            for (String field : new String[]{"strokeIndex1", "strokeIndex2", "fillIndex1", "fillIndex2"}) {
                e.setAttribute(field, Long.toString(n(segment, field, -1)));
            }
            for (FlaRecord curve : records(segment, "curves")) {
                if (curve == null) {
                    session.issue("MORPH_CURVE", segment, "Null morph curve omitted.");
                    continue;
                }
                Element c = child(e, "MorphCurves");
                for (String field : new String[]{"controlPointA", "anchorPointA", "controlPointB", "anchorPointB"}) {
                    point(c, field, curve);
                }
                c.setAttribute("isLine", Boolean.toString(n(curve, "lineFlags", 0) != 0));
            }
        }
        Element hints = child(out, "morphHintsList");
        for (FlaRecord hint : records(r, "hints")) {
            if (hint == null || !hint.isComplete() || !validPoint(hint.get("pointA")) || !validPoint(hint.get("pointB")) || !(hint.get("index") instanceof Number)) {
                session.issue("MORPH_HINTS", hint == null ? r : hint, "Incomplete morph hint omitted; correspondence curves retained.");
                continue;
            }
            Element e = child(hints, "MorphHint");
            hintPoint(e, "startPoint", hint.get("pointA"), hint);
            hintPoint(e, "endPoint", hint.get("pointB"), hint);
            e.setAttribute("key", Integer.toString((int) n(hint, "index", -1)));
        }
    }

    private static void point(Element e, String field, FlaRecord r) {
        Object value = r.get(field);
        if (!(value instanceof List) || ((List<?>) value).size() != 2) {
            return;
        }
        List<?> pair = (List<?>) value;
        e.setAttribute(field, integerPointCoordinate(((Number) pair.get(0)).intValue()) + ", " + integerPointCoordinate(((Number) pair.get(1)).intValue()));
    }

    private static boolean validPoint(Object value) {
        if (!(value instanceof List) || ((List<?>) value).size() != 2) {
            return false;
        }
        for (Object coordinate : (List<?>) value) {
            if (!(coordinate instanceof Integer)) {
                return false;
            }
        }
        return true;
    }

    private void hintPoint(Element element, String attribute, Object value, FlaRecord hint) {
        List<?> pair = (List<?>) value;
        int x = (Integer) pair.get(0), y = (Integer) pair.get(1);
        // Native XML integer-point conversion clamps both components to this range.
        int cx = Math.max(-0x1C20000, Math.min(0x1C20000, x)), cy = Math.max(-0x1C20000, Math.min(0x1C20000, y));
        if (cx != x || cy != y) {
            session.issue("MORPH_HINT_RANGE", hint, "Morph hint coordinate clamped to the native XFL point range.");
        }
        element.setAttribute(attribute, integerPointCoordinate(cx) + ", " + integerPointCoordinate(cy));
    }
}
