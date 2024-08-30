/*
 * Copyright (C) 2024 JPEXS.
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
package com.jpexs.flash.fla.convertor;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author JPEXS
 */
public abstract class AbstractGenerator {

    protected static Element getFirstSubElement(Node n) {
        NodeList list = n.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if (sn instanceof Element) {
                return (Element) sn;
            }
        }
        return null;
    }

    protected static Element getSubElementByName(Node n, String name) {
        NodeList list = n.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if ((sn instanceof Element) && name.equals(sn.getNodeName())) {
                return (Element) sn;
            }
        }
        return null;
    }

    protected static List<Element> getAllSubElements(Node n) {
        NodeList list = n.getChildNodes();
        List<Element> ret = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if (sn instanceof Element) {
                ret.add((Element) sn);
            }
        }
        return ret;
    }

    protected static List<Element> getAllSubElementsByName(Node n, String name) {
        NodeList list = n.getChildNodes();
        List<Element> ret = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if ((sn instanceof Element) && name.equals(sn.getNodeName())) {
                ret.add((Element) sn);
            }
        }
        return ret;
    }

    protected static Color parseColorWithAlpha(Node node) {
        return parseColorWithAlpha(node, Color.black, "color", "alpha");
    }

    protected static Color parseColorWithAlpha(Node node, Color defaultColor) {
        return parseColorWithAlpha(node, defaultColor, "color", "alpha");
    }

    protected static Color parseColorWithAlpha(Node node, Color defaultColor, String colorAttributeName, String alphaAttributeName) {
        Color color = defaultColor;
        Node colorAttr = node.getAttributes().getNamedItem(colorAttributeName);
        if (colorAttr != null) {
            color = parseColor(colorAttr.getTextContent());
        }
        Node alphaAttr = node.getAttributes().getNamedItem(alphaAttributeName);
        if (alphaAttr != null) {
            double alphaD = Double.parseDouble(alphaAttr.getTextContent());
            int alpha255 = (int) Math.round(alphaD * 255);
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha255);
        }

        return color;
    }

    protected static Color parseColor(String value) {
        Pattern pat = Pattern.compile("^#([a-fA-F0-9]{6})$");
        Matcher m = pat.matcher(value);
        if (m.matches()) {
            int rgb = Integer.parseInt(m.group(1), 16);
            return new Color(rgb);
        }
        throw new IllegalArgumentException("Invalid color");
    }

    protected static Matrix parseMatrix(Element matrixElement) {
        if (matrixElement == null) {
            return new Matrix();
        }
        matrixElement = getSubElementByName(matrixElement, "Matrix");
        if (matrixElement == null) {
            return new Matrix();
        }
        double a = 1;
        double b = 0;
        double c = 0;
        double d = 1;
        double tx = 0;
        double ty = 0;

        if (matrixElement.hasAttribute("a")) {
            a = Double.parseDouble(matrixElement.getAttribute("a"));
        }
        if (matrixElement.hasAttribute("b")) {
            b = Double.parseDouble(matrixElement.getAttribute("b"));
        }
        if (matrixElement.hasAttribute("c")) {
            c = Double.parseDouble(matrixElement.getAttribute("c"));
        }
        if (matrixElement.hasAttribute("d")) {
            d = Double.parseDouble(matrixElement.getAttribute("d"));
        }
        if (matrixElement.hasAttribute("tx")) {
            tx = Double.parseDouble(matrixElement.getAttribute("tx"));
        }
        if (matrixElement.hasAttribute("ty")) {
            ty = Double.parseDouble(matrixElement.getAttribute("ty"));
        }
        return new Matrix(a, b, c, d, tx, ty);
    }

    protected static void useClass(String className, FlaCs4Writer os, List<String> definedClasses) throws IOException {
        if (definedClasses.contains(className)) {
            os.write(1 + 2 * definedClasses.indexOf(className));
            os.write(0x80);
        } else {
            os.write(0xFF, 0xFF, 0x01, 0x00);
            os.writeLenAsciiString(className);
            definedClasses.add(className);
        }
        os.write(0x05);
    }
}