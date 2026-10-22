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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA
 */
package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.io.IOException;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;
import static com.jpexs.flash.fla.exporter.XflIkWriter.*;

/**
 * Paints in the archived skin-contour XML use authoring API property names.
 */
final class XflIkPaintWriter {

    private final BinaryFlaToXflExporter.Session session;
    private final FlaRecord owner;

    XflIkPaintWriter(BinaryFlaToXflExporter.Session session, FlaRecord owner) {
        this.session = session;
        this.owner = owner;
    }

    void paint(Element parent, FlaXmlElement input) throws IOException {
        require(input != null, "Missing contour fill.");
        String style = input.getAttribute("style");
        if ("noFill".equals(style)) {
            return;
        }
        if ("solid".equals(style)) {
            color(child(parent, "SolidColor"), input.getAttribute("color"));
            return;
        }
        if ("linearGradient".equals(style) || "radialGradient".equals(style)) {
            Element gradient = child(parent, "linearGradient".equals(style) ? "LinearGradient" : "RadialGradient");
            String overflow = input.getAttribute("overflow");
            require(Arrays.asList("extend", "reflect", "repeat").contains(overflow), "Unknown contour gradient spread.");
            gradient.setAttribute("spreadMethod", overflow.equals("extend") ? "pad" : overflow);
            String linear = input.getAttribute("linearRGB");
            require(Arrays.asList("true", "false").contains(linear), "Invalid contour gradient interpolation.");
            gradient.setAttribute("interpolationMethod", linear.equals("true") ? "linearRGB" : "rgb");
            if (style.equals("radialGradient")) {
                gradient.setAttribute("focalPointRatio", number((int) Math.max(-255, Math.min(255, value(input, "focalPoint"))) / 255.0));
            }
            matrix(gradient, input.getFirstChild("Matrix"));
            String positions = input.getAttribute("posArray"), colors = input.getAttribute("colorArray");
            require(positions != null && colors != null, "Missing contour gradient stops.");
            String[] ps = positions.split(",", -1), cs = colors.split(",", -1);
            require(ps.length == cs.length && ps.length >= 1 && ps.length <= 15, "Invalid contour gradient stop count.");
            for (int i = 0; i < ps.length; i++) {
                double position = parse(ps[i]);
                require(position >= 0 && position <= 255, "Contour gradient stop exceeds the byte range.");
                Element entry = child(gradient, "GradientEntry");
                entry.setAttribute("ratio", number((int) position / 255.0));
                color(entry, cs[i]);
            }
            return;
        }
        if ("bitmap".equals(style)) {
            String path = input.getAttribute("bitmapPath");
            XflBitmapWriter.Bitmap bitmap = null;
            for (BinaryFlaDocument.Media media : session.source.getMedia()) {
                FlaRecord descriptor = media.getDescriptor();
                if (session.fullName(descriptor).equals(path)) {
                    XflBitmapWriter.Bitmap candidate = session.bitmaps.get(n(descriptor, "mediaId", -1));
                    require(bitmap == null, "Ambiguous contour bitmap path.");
                    bitmap = candidate;
                }
            }
            require(bitmap != null, "Contour bitmap path does not resolve to an exported bitmap.");
            Element fill = child(parent, "BitmapFill");
            session.attribute(fill, "bitmapPath", bitmap.name, owner);
            String clipped = input.getAttribute("bitmapIsClipped");
            require(Arrays.asList("true", "false").contains(clipped), "Invalid contour bitmap clipping.");
            fill.setAttribute("bitmapIsClipped", clipped);
            matrix(fill, input.getFirstChild("Matrix"));
            return;
        }
        throw new IOException("Unknown contour fill style " + style);
    }

    void stroke(Element parent, FlaXmlElement input) throws IOException {
        require(input != null, "Missing contour stroke.");
        String style = input.getAttribute("style");
        if ("noStroke".equals(style)) {
            return;
        }
        String[] styles = {"solid", "dashed", "dotted", "ragged", "stipple", "hatched"}, tags = {"SolidStroke", "DashedStroke", "DottedStroke", "RaggedStroke", "StippleStroke", "HatchedStroke"};
        int kind = Arrays.asList(styles).indexOf(style);
        require(kind >= 0, "Unknown contour stroke style.");
        Element output = child(parent, tags[kind]);
        double weight = value(input, "thickness");
        require(weight > 0 && weight * 20 <= 65535, "Invalid contour stroke width.");
        output.setAttribute("weight", number((int) (weight * 20) / 20.0));
        if (kind == 0) {
            for (String key : Arrays.asList("capType", "joinType")) {
                String option = input.getAttribute(key);
                require((key.equals("capType") ? Arrays.asList("round", "square", "none") : Arrays.asList("round", "bevel", "miter")).contains(option), "Invalid contour stroke cap/join.");
                output.setAttribute(key.equals("capType") ? "caps" : "joints", option);
            }
        }
        String scale = input.getAttribute("scaleType");
        require(Arrays.asList("normal", "none", "horizontal", "vertical").contains(scale), "Unknown contour stroke scale.");
        output.setAttribute("scaleMode", scale);
        for (String flag : Arrays.asList("strokeHinting", "breakAtCorners")) {
            String v = input.getAttribute(flag);
            require(Arrays.asList("true", "false").contains(v), "Invalid contour stroke flag.");
            output.setAttribute(flag.equals("strokeHinting") ? "pixelHinting" : "sharpCorners", v);
        }
        if (kind == 0) {
            double miter = value(input, "miterLimit");
            require(miter >= 1 && miter * 256 <= 65535, "Invalid contour miter limit.");
            output.setAttribute("miterLimit", number((int) (miter * 256) / 256.0));
        }
        switch (kind) {
            case 1:
                distance(output, input, "dash1");
                distance(output, input, "dash2");
                break;
            case 2:
                distance(output, input, "dotSpace");
                break;
            case 3:
                option(output, input, "pattern", "solid", "simple", "random", "dotted", "random dotted", "triple dotted", "random triple dotted");
                option(output, input, "waveHeight", "flat", "wavy", "very wavy", "wild");
                option(output, input, "waveLength", "very short", "short", "medium", "long");
                break;
            case 4:
                option(output, input, "dotSize", "tiny", "small", "medium", "large");
                option(output, input, "variation", "one size", "small variation", "varied sizes", "random sizes");
                option(output, input, "density", "very dense", "dense", "sparse", "very sparse");
                break;
            case 5:
                option(output, input, "hatchThickness", "hairline", "thin", "medium", "thick");
                option(output, input, "space", "very close", "close", "distant", "very distant");
                option(output, input, "rotate", "none", "slight", "medium", "free");
                option(output, input, "jiggle", "none", "bounce", "loose", "wild");
                option(output, input, "curve", "straight", "slight curve", "medium curve", "very curved");
                option(output, input, "length", "equal", "slight variation", "medium variation", "random");
                break;
            default:
                break;
        }
        color(child(child(output, "fill"), "SolidColor"), input.getAttribute("color"));
    }

    private static void option(Element output, FlaXmlElement input, String key, String... values) throws IOException {
        String v = input.getAttribute(key);
        require(Arrays.asList(values).contains(v), "Invalid contour stroke option " + key);
        output.setAttribute(key, v);
    }

    private static void distance(Element output, FlaXmlElement input, String key) throws IOException {
        double v = value(input, key);
        require(v >= 0 && v <= 3276, "Invalid contour stroke distance.");
        output.setAttribute(key, Integer.toString((int) v));
    }

    private static void color(Element output, String value) throws IOException {
        require(value != null && value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"), "Invalid contour color.");
        output.setAttribute("color", value.substring(0, 7));
        if (value.length() == 9) {
            output.setAttribute("alpha", number(Integer.parseInt(value.substring(7), 16) / 255.0));
        }
    }

    private static void matrix(Element output, FlaXmlElement input) throws IOException {
        require(input != null, "Missing contour paint matrix.");
        Element matrix = child(child(output, "matrix"), "Matrix");
        for (String key : Arrays.asList("a", "b", "c", "d", "tx", "ty")) {
            double v = value(input, key), scale = key.length() == 1 ? 65536 : 20;
            require(v * scale >= Integer.MIN_VALUE && v * scale <= Integer.MAX_VALUE, "Contour paint matrix exceeds native range.");
            matrix.setAttribute(key, number(Math.round(v * scale) / scale));
        }
    }
}
