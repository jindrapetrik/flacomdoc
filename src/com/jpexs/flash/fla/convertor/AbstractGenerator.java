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

import com.jpexs.helpers.Reference;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    protected boolean debugRandom = false;

    protected static final Map<String, Font> psNameToFontName = new HashMap<>();

    static {
        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();

        for (Font font : fonts) {
            psNameToFontName.put(font.getPSName(), font);
        }
    }

    public void setDebugRandom(boolean debugRandom) {
        this.debugRandom = debugRandom;
    }

    protected Element getFirstSubElement(Node n) {
        NodeList list = n.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if (sn instanceof Element) {
                return (Element) sn;
            }
        }
        return null;
    }

    protected Element getSubElementByName(Node n, String name) {
        if (n == null) {
            return null;
        }
        NodeList list = n.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if ((sn instanceof Element) && name.equals(sn.getNodeName())) {
                return (Element) sn;
            }
        }
        return null;
    }

    protected List<Element> getAllSubElements(Node n) {
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

    protected List<Element> getAllSubElementsByName(Node n, String name) {
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

    protected Color parseColorWithAlpha(Node node) {
        return parseColorWithAlpha(node, Color.black, "color", "alpha");
    }

    protected Color parseColorWithAlpha(Node node, Color defaultColor) {
        return parseColorWithAlpha(node, defaultColor, "color", "alpha");
    }

    protected Color parseColorWithAlpha(Node node, Color defaultColor, String colorAttributeName, String alphaAttributeName) {
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

    protected Color parseColor(String value) {
        Pattern pat = Pattern.compile("^#([a-fA-F0-9]{6})$");
        Matcher m = pat.matcher(value);
        if (m.matches()) {
            int rgb = Integer.parseInt(m.group(1), 16);
            return new Color(rgb);
        }
        throw new IllegalArgumentException("Invalid color");
    }

    protected Matrix parseMatrix(Element matrixElement) {
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

    protected int getAttributeAsInt(Node node, String attributeName, List<String> allowedValues, String defaultValue) {
        Node attr = node.getAttributes().getNamedItem(attributeName);
        if (attr != null) {
            int index = allowedValues.indexOf(attr.getTextContent());
            if (index > -1) {
                return index;
            }
        }
        return allowedValues.indexOf(defaultValue);
    }

    protected void writeAccessibleData(FlaWriter fg, Element element, boolean mainDocument) throws IOException {
        boolean hasAccessibleData = false;
        if (element.hasAttribute("hasAccessibleData")) {
            hasAccessibleData = "true".equals(element.getAttribute("hasAccessibleData"));
        }

        if (!hasAccessibleData) {
            if (mainDocument) {
                fg.write(0);
            }
            return;
        }

        boolean silent = false; //"Make object accessible" checkbox (inverted)
        String description = "";
        String tabIndex = "";
        String accName = "";
        String shortcut = "";
        boolean autoLabeling = true;
        boolean forceSimple = false; //"Make child objects accessible" checkbox (inverted)
        if (element.hasAttribute("silent")) {
            silent = "true".equals(element.getAttribute("silent"));
        }
        if (element.hasAttribute("description")) {
            description = element.getAttribute("description");
        }
        if (element.hasAttribute("tabIndex")) {
            tabIndex = element.getAttribute("tabIndex");
        }
        if (element.hasAttribute("accName")) {
            accName = element.getAttribute("accName");
        }
        if (element.hasAttribute("shortcut")) {
            shortcut = element.getAttribute("shortcut");
        }
        if (element.hasAttribute("forceSimple")) {
            forceSimple = "true".equals(element.getAttribute("forceSimple"));
        }
        if (element.hasAttribute("autoLabeling")) {
            autoLabeling = !"false".equals(element.getAttribute("autoLabeling"));
        }
        fg.write(0x02, 0x00);
        fg.write(0x00, 0x00, silent ? 1 : 0, 0x00, 0x00, 0x00);
        fg.write(0xFF, 0xFE, 0xFF);
        fg.writeLenUnicodeString(accName);
        fg.write(0xFF, 0xFE, 0xFF);
        fg.writeLenUnicodeString(description);
        fg.write(0xFF, 0xFE, 0xFF);
        fg.writeLenUnicodeString(shortcut);
        fg.write(0xFF, 0xFE, 0xFF);
        fg.writeLenUnicodeString(tabIndex);
        fg.write(0xFF, 0xFE, 0xFF, 0x00);
        fg.write(forceSimple ? 1 : 0, 0x00, 0x00, 0x00);
        if (mainDocument) {
            fg.write(autoLabeling ? 0 : 1);
        }
    }

    protected void useClass(String className, int defineNum, FlaWriter os,
            Map<String, Integer> definedClasses,
            Reference<Integer> totalObjectCount
    ) throws IOException {
        if (definedClasses.containsKey(className)) {
            os.write(definedClasses.get(className));
            os.write(0x80);
        } else {
            os.write(0xFF, 0xFF, defineNum, 0x00);
            os.writeLenAsciiString(className);
            definedClasses.put(className, 1 + definedClasses.size() + totalObjectCount.getVal());
        }
        totalObjectCount.setVal(totalObjectCount.getVal() + 1);
    }

    protected void useClass(String className, FlaWriter os,
            Map<String, Integer> definedClasses,
            Reference<Integer> totalObjectCount
    ) throws IOException {
        useClass(className, 1, os, definedClasses, totalObjectCount);
    }

    protected List<Element> getSymbols(Element document) {
        Element symbolsElement = getSubElementByName(document, "symbols");
        if (symbolsElement == null) {
            return new ArrayList<>();
        }
        List<Element> includes = getAllSubElementsByName(symbolsElement, "Include");
        return includes;
    }
}
