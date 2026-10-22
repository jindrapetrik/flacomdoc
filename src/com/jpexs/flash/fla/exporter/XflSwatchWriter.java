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
 * Archived color sets and native color-definition upgrade rules.
 */
final class XflSwatchWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflSwatchWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void write(Element document, FlaRecord contents) {
        write(document, record(contents, "swatches"), "swatchLists");
        write(document, record(contents, "extendedSwatches"), "extendedSwatchLists");
    }

    private void write(Element document, FlaRecord source, String tag) {
        if (source == null) {
            return;
        }
        Element lists = child(document, tag);
        colorSet(lists, source);
        for (FlaRecord list : records(source, "lists")) {
            if (list == null) {
                session.issue("SWATCH_LIST", source, "Null color set omitted.");
                continue;
            }
            colorSet(lists, list);
            if (!records(list, "lists").isEmpty()) {
                session.issue("SWATCH_LIST", list, "Native XFL writes only the root color set and its direct child sets; deeper sets remain in the reader model.");
            }
        }
    }

    private void colorSet(Element parent, FlaRecord source) {
        Element colors = child(child(parent, "swatchList"), "swatches");
        for (FlaRecord color : records(source, "colors")) {
            Element item = color == null ? null : item(colors, color);
            if (item == null) {
                session.issue("SWATCH_COLOR", color == null ? source : color, "Incomplete or unsupported swatch omitted.");
            } else {
                colors.appendChild(item);
            }
        }
    }

    private Element item(Element parent, FlaRecord source) {
        if (!source.isComplete() || !(source.get("packedValue") instanceof Number)) {
            return null;
        }
        long rgba = n(source, "packedValue", 0);
        int revision = (int) n(source, "revision", 0);
        int flags = revision < 2 ? 0 : (int) n(source, "flags", -1);
        if (flags == 0) {
            Element item = parent.getOwnerDocument().createElementNS(NS, "SolidSwatchItem");
            color(item, rgba);
            int[] hsb = hsb(rgba);
            String[] names = {"hue", "saturation", "brightness"};
            boolean stored = revision >= 3;
            for (String name : names) {
                stored &= source.get(name) instanceof Number && n(source, name, 65535) <= 256;
            }
            for (int i = 0; i < names.length; i++) {
                long value = stored ? n(source, names[i], 0) : hsb[i];
                if (value > 255) {
                    session.issue("SWATCH_RANGE", source, names[i] + " clamped to the native XFL range.");
                    value = 255;
                }
                item.setAttribute(names[i], Long.toString(value));
            }
            return item;
        }
        if ((flags & 0x10) == 0) {
            return null;
        }
        FlaPaintStyle style = source.getPaintStyle();
        if (style == null || style.getMatrix() == null || style.getStops() == null) {
            return null;
        }
        boolean linear = flags == 16;
        Element item = parent.getOwnerDocument().createElementNS(NS, linear ? "LinearGradientSwatchItem" : "RadialGradientSwatchItem");
        if (style.getSpread() != null) {
            item.setAttribute("spreadMethod", style.getSpread() == FlaPaintStyle.Spread.EXTEND ? "pad" : style.getSpread().name().toLowerCase(Locale.ROOT));
        }
        if (style.isLinearRgb() != null) {
            item.setAttribute("interpolationMethod", style.isLinearRgb() ? "linearRGB" : "rgb");
        }
        if (!linear && style.getFocalRatio() != null) {
            item.setAttribute("focalPointRatio", number(style.getFocalRatio()));
        }
        matrix(item, style.getMatrix());
        for (FlaPaintStyle.Stop stop : style.getStops()) {
            Element e = child(item, "GradientEntry");
            e.setAttribute("ratio", number(stop.getPosition()));
            color(e, stop.getRgbaWord());
        }
        return item;
    }

    /**
     * Integer HSL conversion used when old or invalid HSB cache words are
     * upgraded.
     */
    static int[] hsb(long rgba) {
        int r = (240 * (int) (rgba & 255) + 127) / 255, g = (240 * (int) (rgba >>> 8 & 255) + 127) / 255, b = (240 * (int) (rgba >>> 16 & 255) + 127) / 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), light = (min + max + 1) / 2, delta = max - min;
        if (light == 0 || delta == 0) {
            return new int[]{0, 0, light};
        }
        int divisor = light > 120 ? 480 - min - max : min + max;
        int saturation = (240 * delta + divisor / 2) / divisor;
        int dr = (delta / 2 + 240 * (max - r)) / delta, dg = (delta / 2 + 240 * (max - g)) / delta, db = (delta / 2 + 240 * (max - b)) / delta;
        int hue = r == max ? (g == min ? db + 1200 : 240 - dg) : g == max ? (b == min ? dr + 240 : 720 - db) : (r == min ? dg + 720 : 1200 - dr);
        hue = (hue + 3) / 6;
        if (hue == 240) {
            hue = 0;
        }
        if (saturation == 240) {
            saturation = 239;
        }
        if (light == 240) {
            light = 239;
        }
        return new int[]{hue, saturation, light};
    }
}
