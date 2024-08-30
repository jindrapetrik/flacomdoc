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

import com.jpexs.flash.fla.convertor.coloreffects.AdvancedColorEffect;
import com.jpexs.flash.fla.convertor.coloreffects.AlphaColorEffect;
import com.jpexs.flash.fla.convertor.coloreffects.BrightnessColorEffect;
import com.jpexs.flash.fla.convertor.coloreffects.ColorEffectInterface;
import com.jpexs.flash.fla.convertor.coloreffects.NoColorEffect;
import com.jpexs.flash.fla.convertor.coloreffects.TintColorEffect;
import com.jpexs.flash.fla.convertor.filters.AdjustColorFilter;
import com.jpexs.flash.fla.convertor.filters.BevelFilter;
import com.jpexs.flash.fla.convertor.filters.BlurFilter;
import com.jpexs.flash.fla.convertor.filters.DropShadowFilter;
import com.jpexs.flash.fla.convertor.filters.FilterInterface;
import com.jpexs.flash.fla.convertor.filters.GlowFilter;
import com.jpexs.flash.fla.convertor.filters.GradientBevelFilter;
import com.jpexs.flash.fla.convertor.filters.GradientGlowFilter;
import com.jpexs.helpers.Reference;
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author JPEXS
 */
public class PageGenerator extends AbstractGenerator {

    private static void handleFill(Node fillStyleVal, FlaCs4Writer fg) throws IOException {
        switch (fillStyleVal.getNodeName()) {
            case "SolidColor":
                Color color = parseColorWithAlpha(fillStyleVal);
                fg.writeSolidFill(color);
                break;
            case "LinearGradient":
            case "RadialGradient": {
                Matrix gradientMatrix = parseMatrix(getSubElementByName(fillStyleVal, "matrix"));

                double focalPointRatio = 0;

                if ("RadialGradient".equals(fillStyleVal.getNodeName())) {
                    Node focalPointRatioAttr = fillStyleVal.getAttributes().getNamedItem("focalPointRatio");
                    if (focalPointRatioAttr != null) {
                        focalPointRatio = Double.parseDouble(focalPointRatioAttr.getTextContent());
                    }
                }

                List<Element> gradientEntries = getAllSubElementsByName(fillStyleVal, "GradientEntry");
                Color colors[] = new Color[gradientEntries.size()];
                double ratios[] = new double[gradientEntries.size()];
                for (int en = 0; en < gradientEntries.size(); en++) {
                    Node gradientEntry = gradientEntries.get(en);
                    colors[en] = parseColorWithAlpha(gradientEntry);
                    Node ratioAttr = gradientEntry.getAttributes().getNamedItem("ratio");
                    double ratio = 0;
                    if (ratioAttr != null) {
                        ratio = Double.parseDouble(ratioAttr.getTextContent());
                    }
                    ratios[en] = ratio;
                }

                boolean linearRGB = false;
                Node interpolationMethodAttr = fillStyleVal.getAttributes().getNamedItem("interpolationMethod");
                if (interpolationMethodAttr != null) {
                    if ("linearRGB".equals(interpolationMethodAttr.getTextContent())) {
                        linearRGB = true;
                    }
                }

                int spreadMethod = FlaCs4Writer.FLOW_EXTEND;
                Node spreadMethodAttr = fillStyleVal.getAttributes().getNamedItem("spreadMethod");
                if (spreadMethodAttr != null) {
                    if ("reflect".equals(spreadMethodAttr.getTextContent())) {
                        spreadMethod = FlaCs4Writer.FLOW_REFLECT;
                    }
                    if ("repeat".equals(spreadMethodAttr.getTextContent())) {
                        spreadMethod = FlaCs4Writer.FLOW_REPEAT;
                    }
                }

                int type = FlaCs4Writer.TYPE_LINEAR_GRADIENT;
                if ("RadialGradient".equals(fillStyleVal.getNodeName())) {
                    type = FlaCs4Writer.TYPE_RADIAL_GRADIENT;
                }
                fg.writeGradientFill(colors, ratios, type, linearRGB, spreadMethod, gradientMatrix, focalPointRatio);
            }
            break;
            case "BitmapFill": {
                Node bitmapPathAttr = fillStyleVal.getAttributes().getNamedItem("bitmapPath");
                if (bitmapPathAttr != null) {
                    String bitmapPath = bitmapPathAttr.getTextContent(); //assuming attribute set
                    Node mediaNode = getSubElementByName(fillStyleVal.getOwnerDocument().getDocumentElement(), "media");
                    if (mediaNode != null) {
                        List<Element> mediaElements = getAllSubElements(mediaNode);
                        int mediaId = 0;
                        for (Element e : mediaElements) {
                            mediaId++;
                            if ("DOMBitmapItem".equals(e.getNodeName())) {
                                String name = e.getAttribute("name");
                                if (name != null) {
                                    if (bitmapPath.equals(name)) {
                                        Matrix bitmapMatrix = parseMatrix(getSubElementByName(fillStyleVal, "matrix"));

                                        boolean bitmapIsClipped = false;
                                        Node bitmapIsClippedAttr = fillStyleVal.getAttributes().getNamedItem("bitmapIsClippe");
                                        if (bitmapIsClippedAttr != null) {
                                            bitmapIsClipped = "true".equals(bitmapIsClippedAttr.getTextContent());
                                        }

                                        boolean allowSmoothing = "true".equals(e.getAttribute("allowSmoothing"));

                                        int type;
                                        if (allowSmoothing) {
                                            if (bitmapIsClipped) {
                                                type = FlaCs4Writer.TYPE_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaCs4Writer.TYPE_BITMAP;
                                            }
                                        } else {
                                            if (bitmapIsClipped) {
                                                type = FlaCs4Writer.TYPE_NON_SMOOTHED_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaCs4Writer.TYPE_NON_SMOOTHED_BITMAP;
                                            }
                                        }

                                        fg.writeBitmapFill(type, bitmapMatrix, mediaId);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            break;
        }
    }

    private static int getAttributeAsInt(Node node, String attributeName, List<String> allowedValues, String defaultValue) {
        Node attr = node.getAttributes().getNamedItem(attributeName);
        if (attr != null) {
            int index = allowedValues.indexOf(attr.getTextContent());
            if (index > -1) {
                return index;
            }
        }
        return allowedValues.indexOf(defaultValue);
    }

    private void handleParentLayer(
            Element layer,
            FlaCs4Writer fg,
            Map<Integer, Integer> layerIndexToRevLayerIndex,
            boolean isEmpty,
            List<String> definedClasses,
            Reference<Integer> copiedComponentPathRef,
            Reference<Integer> totalFramesCountRef) throws IOException {
        int parentLayerIndex = -1;
        if (layer.hasAttribute("parentLayerIndex")) {
            parentLayerIndex = Integer.parseInt(layer.getAttribute("parentLayerIndex"));
        }
        boolean autoNamed = true;
        if (layer.hasAttribute("autoNamed")) {
            autoNamed = !"false".equals(layer.getAttribute("autoNamed"));
        }
        boolean open = true;
        if (layer.hasAttribute("open")) {
            open = !"false".equals(layer.getAttribute("open"));
        }
        writeLayerContents(layer, fg, definedClasses, copiedComponentPathRef, totalFramesCountRef);

        int reverseParentLayerIndex = parentLayerIndex == -1 ? -1 : layerIndexToRevLayerIndex.get(parentLayerIndex);
        fg.writeLayerEnd2(reverseParentLayerIndex, open, autoNamed);
        if (!isEmpty) {
            fg.write(0x01);
            fg.write(0x01);
            fg.write(0x00);
        }
    }

    private static List<GradientEntry> parseGradientEntries(Element element) {
        List<GradientEntry> ret = new ArrayList<>();
        List<Element> entries = getAllSubElementsByName(element, "GradientEntry");
        for (Element entry : entries) {
            Color color = parseColorWithAlpha(entry);
            float ratio = 0;
            if (entry.hasAttribute("ratio")) {
                ratio = Float.parseFloat(entry.getAttribute("ratio"));
            }
            ret.add(new GradientEntry(color, ratio));
        }
        return ret;
    }

    private void writeLayerContents(
            Element layer,
            FlaCs4Writer fg,
            List<String> definedClasses,
            Reference<Integer> copiedComponentPathRef,
            Reference<Integer> totalFramesCountRef
    ) throws IOException {
        useClass("CPicLayer", fg, definedClasses);
        fg.write(0x00);

        int layerType = FlaCs4Writer.LAYERTYPE_LAYER;
        if (layer.hasAttribute("layerType")) {
            String layerTypeStr = layer.getAttribute("layerType");
            switch (layerTypeStr) {
                case "folder":
                    layerType = FlaCs4Writer.LAYERTYPE_FOLDER;
                    break;
                case "guide":
                    layerType = FlaCs4Writer.LAYERTYPE_GUIDE;
                    break;
            }
        }

        Node framesNode = getSubElementByName(layer, "frames");
        if (framesNode == null) {
        } else {
            List<Element> frames = getAllSubElementsByName(framesNode, "DOMFrame");
            for (int f = 0; f < frames.size(); f++) {
                useClass("CPicFrame", fg, definedClasses);
                /*if (totalFramesCount > 0) {
                                fg.writeKeyFrameSeparator();
                            }*/
                fg.write(0x00);
                totalFramesCountRef.setVal(totalFramesCountRef.getVal() + 1);
                Node frame = frames.get(f);
                Node elementsNode = getSubElementByName(frame, "elements");

                List<Element> symbolInstances = getAllSubElementsByName(elementsNode, "DOMSymbolInstance");
                for (int symbolInstanceIndex = 0; symbolInstanceIndex < symbolInstances.size(); symbolInstanceIndex++) {

                    Element symbolInstance = symbolInstances.get(symbolInstanceIndex);

                    if (!symbolInstance.hasAttribute("libraryItemName")) {
                        //nothing we can do
                        continue;
                    }

                    String libraryItemName = symbolInstance.getAttribute("libraryItemName");
                    Element symbolsElement = getSubElementByName(symbolInstance.getOwnerDocument().getDocumentElement(), "symbols");
                    if (symbolsElement == null) {
                        //nothing we can do                                    
                        continue;
                    }
                    List<Element> includes = getAllSubElementsByName(symbolsElement, "Include");

                    //Find index in library
                    int libraryItemIndex = -1;
                    for (int e = 0; e < includes.size(); e++) {
                        Element include = includes.get(e);
                        if (!include.hasAttribute("href")) {
                            continue;
                        }
                        String href = include.getAttribute("href");
                        String nameNoXml = href;
                        if (nameNoXml.endsWith(".xml")) {
                            nameNoXml = nameNoXml.substring(0, nameNoXml.length() - 4);
                        }
                        if (nameNoXml.equals(libraryItemName)) {
                            //libraryItemIndex = e;
                            //FIXME: Need to really determine the symbol file
                            libraryItemIndex = 'X';
                            break;
                        }
                    }
                    if (libraryItemIndex == -1) {
                        //nothing we can do 
                        continue;
                    }

                    int symbolType = FlaCs4Writer.SYMBOLTYPE_SPRITE;

                    if (symbolInstance.hasAttribute("symbolType")) {
                        switch (symbolInstance.getAttribute("symbolType")) {
                            case "sprite": //?? is this correct default value ??
                                symbolType = FlaCs4Writer.SYMBOLTYPE_SPRITE;
                                break;
                            case "button":
                                symbolType = FlaCs4Writer.SYMBOLTYPE_BUTTON;
                                break;
                            case "graphic":
                                symbolType = FlaCs4Writer.SYMBOLTYPE_GRAPHIC;
                                break;
                        }
                    }

                    boolean trackAsMenu = false;
                    int loop = FlaCs4Writer.LOOPMODE_LOOP;
                    int firstFrame = 0; //zero-based

                    if (symbolType == FlaCs4Writer.SYMBOLTYPE_BUTTON) {
                        useClass("CPicButton", fg, definedClasses);
                        if (symbolInstance.hasAttribute("trackAsMenu")) {
                            trackAsMenu = "true".equals(symbolInstance.getAttribute("trackAsMenu"));
                        }
                    } else if (symbolType == FlaCs4Writer.SYMBOLTYPE_GRAPHIC) {
                        useClass("CPicSymbol", fg, definedClasses);

                        if (symbolInstance.hasAttribute("loop")) {
                            switch (symbolInstance.getAttribute("loop")) {
                                case "loop":
                                    loop = FlaCs4Writer.LOOPMODE_LOOP;
                                    break;
                                case "play once":
                                    loop = FlaCs4Writer.LOOPMODE_PLAY_ONCE;
                                    break;
                                case "single frame":
                                    loop = FlaCs4Writer.LOOPMODE_SINGLE_FRAME;
                                    break;
                            }
                        }
                        if (symbolInstance.hasAttribute("firstFrame")) {
                            firstFrame = Integer.parseInt(symbolInstance.getAttribute("firstFrame"));
                        }
                    } else {
                        useClass("CPicSprite", fg, definedClasses);
                    }

                    /*if (totalSymbolInstancesCount > 0) {
                                    fg.writeSymbolInstanceSeparator();
                                } */
                    String instanceName = "";
                    if (symbolType != FlaCs4Writer.SYMBOLTYPE_GRAPHIC && symbolInstance.hasAttribute("name")) {
                        instanceName = symbolInstance.getAttribute("name");
                    }

                    //Note: default values are not zero - they are recalculated when not present.
                    //It is actually needed to calculate center of the shape, I don't know how...
                    double centerPoint3DX = 0;
                    if (symbolInstance.hasAttribute("centerPoint3DX")) {
                        centerPoint3DX = Double.parseDouble(symbolInstance.getAttribute("centerPoint3DX"));
                    }
                    double centerPoint3DY = 0;
                    if (symbolInstance.hasAttribute("centerPoint3DY")) {
                        centerPoint3DY = Double.parseDouble(symbolInstance.getAttribute("centerPoint3DY"));
                    }

                    Matrix placeMatrix = parseMatrix(getSubElementByName(symbolInstance, "matrix"));
                    double transformationPointX = 0;
                    double transformationPointY = 0;
                    Element transformationPointElement = getSubElementByName(symbolInstance, "transformationPoint");
                    if (transformationPointElement != null) {
                        Element pointElement = getSubElementByName(transformationPointElement, "Point");
                        if (pointElement.hasAttribute("x")) {
                            transformationPointX = Double.parseDouble(pointElement.getAttribute("x"));
                        }
                        if (pointElement.hasAttribute("y")) {
                            transformationPointY = Double.parseDouble(pointElement.getAttribute("y"));
                        }
                    }

                    ColorEffectInterface colorEffect = new NoColorEffect();

                    Element colorElement = getSubElementByName(symbolInstance, "color");
                    if (colorElement != null) {
                        colorElement = getSubElementByName(colorElement, "Color");
                        if (colorElement != null) {
                            if (colorElement.hasAttribute("brightness")) {
                                double brightness = Double.parseDouble(colorElement.getAttribute("brightness"));
                                colorEffect = new BrightnessColorEffect(brightness);
                            } else if (colorElement.hasAttribute("tintColor") || colorElement.hasAttribute("tintMultiplier")) {
                                Color tintColor = Color.black;
                                if (colorElement.hasAttribute("tintColor")) {
                                    tintColor = parseColor(colorElement.getAttribute("tintColor"));
                                }
                                double tintMultiplier = 0;
                                if (colorElement.hasAttribute("tintMultiplier")) {
                                    tintMultiplier = Double.parseDouble(colorElement.getAttribute("tintMultiplier"));
                                }
                                colorEffect = new TintColorEffect(tintMultiplier, tintColor);
                            } else if ( //no Alpha offset - to not be mismatched as Alpha color effect
                                    colorElement.hasAttribute("redMultiplier")
                                    || colorElement.hasAttribute("greenMultiplier")
                                    || colorElement.hasAttribute("blueMultiplier")
                                    || colorElement.hasAttribute("alphaOffset")
                                    || colorElement.hasAttribute("redOffset")
                                    || colorElement.hasAttribute("greenOffset")
                                    || colorElement.hasAttribute("blueOffset")) {
                                double alphaMultiplier = 1.0;
                                double redMultiplier = 1.0;
                                double greenMultiplier = 1.0;
                                double blueMultiplier = 1.0;
                                int alphaOffset = 0;
                                int redOffset = 0;
                                int greenOffset = 0;
                                int blueOffset = 0;

                                if (colorElement.hasAttribute("alphaMultiplier")) {
                                    alphaMultiplier = Double.parseDouble(colorElement.getAttribute("alphaMultiplier"));
                                }
                                if (colorElement.hasAttribute("redMultiplier")) {
                                    redMultiplier = Double.parseDouble(colorElement.getAttribute("redMultiplier"));
                                }
                                if (colorElement.hasAttribute("greenMultiplier")) {
                                    greenMultiplier = Double.parseDouble(colorElement.getAttribute("greenMultiplier"));
                                }
                                if (colorElement.hasAttribute("blueMultiplier")) {
                                    blueMultiplier = Double.parseDouble(colorElement.getAttribute("blueMultiplier"));
                                }
                                if (colorElement.hasAttribute("alphaOffset")) {
                                    alphaOffset = Integer.parseInt(colorElement.getAttribute("alphaOffset"));
                                }
                                if (colorElement.hasAttribute("redOffset")) {
                                    redOffset = Integer.parseInt(colorElement.getAttribute("redOffset"));
                                }
                                if (colorElement.hasAttribute("greenOffset")) {
                                    greenOffset = Integer.parseInt(colorElement.getAttribute("greenOffset"));
                                }
                                if (colorElement.hasAttribute("blueOffset")) {
                                    blueOffset = Integer.parseInt(colorElement.getAttribute("blueOffset"));
                                }
                                colorEffect = new AdvancedColorEffect(alphaMultiplier, redMultiplier, greenMultiplier, blueMultiplier, alphaOffset, redOffset, greenOffset, blueOffset);
                            } else if (colorElement.hasAttribute("alphaMultiplier")) {
                                double alphaMultiplier = Double.parseDouble(colorElement.getAttribute("alphaMultiplier"));
                                colorEffect = new AlphaColorEffect(alphaMultiplier);
                            }
                        }
                    }

                    //TODO: attribute "symbolType" aka "instance behavior"
                    //Order in CS5: normal, layer, darken, multiply, lighten, screen, overlay, hardlight, add, subtract, difference, invert, alpha, erase
                    int blendMode = getAttributeAsInt(symbolInstance, "blendMode",
                            Arrays.asList(
                                    "",
                                    "normal",
                                    "layer",
                                    "multiply",
                                    "screen",
                                    "lighten",
                                    "darken",
                                    "difference",
                                    "add",
                                    "subtract",
                                    "invert",
                                    "alpha",
                                    "erase",
                                    "overlay",
                                    "hardlight"
                            ), "normal");

                    boolean cacheAsBitmap = false;
                    if (symbolInstance.hasAttribute("cacheAsBitmap")) {
                        cacheAsBitmap = "true".equals(symbolInstance.getAttribute("cacheAsBitmap"));
                    }

                    List<FilterInterface> filterList = new ArrayList<>();
                    Element filtersElement = getSubElementByName(symbolInstance, "filters");
                    if (filtersElement != null) {
                        List<Element> filters = getAllSubElements(filtersElement);
                        for (Element filter : filters) {
                            boolean enabled = true;
                            if (filter.hasAttribute("isEnabled")) {
                                enabled = !"false".equals(filter.getAttribute("isEnabled"));
                            }
                            switch (filter.getNodeName()) {
                                case "DropShadowFilter": {
                                    float blurX = 5;
                                    float blurY = 5;
                                    float strength = 1;
                                    int quality = 1; //low
                                    float angle = 45;
                                    float distance = 5;
                                    boolean knockout = false;
                                    boolean inner = false;
                                    boolean hideObject = false;
                                    Color color = Color.black;

                                    if (filter.hasAttribute("blurX")) {
                                        blurX = Float.parseFloat(filter.getAttribute("blurX"));
                                    }
                                    if (filter.hasAttribute("blurY")) {
                                        blurY = Float.parseFloat(filter.getAttribute("blurY"));
                                    }
                                    if (filter.hasAttribute("strength")) {
                                        strength = Float.parseFloat(filter.getAttribute("strength"));
                                    }
                                    if (filter.hasAttribute("quality")) {
                                        quality = Integer.parseInt(filter.getAttribute("quality"));
                                    }
                                    if (filter.hasAttribute("angle")) {
                                        angle = Float.parseFloat(filter.getAttribute("angle"));
                                    }
                                    if (filter.hasAttribute("distance")) {
                                        distance = Float.parseFloat(filter.getAttribute("distance"));
                                    }
                                    if (filter.hasAttribute("knockout")) {
                                        knockout = "true".equals(filter.getAttribute("knockout"));
                                    }
                                    if (filter.hasAttribute("inner")) {
                                        inner = "true".equals(filter.getAttribute("inner"));
                                    }
                                    if (filter.hasAttribute("hideObject")) {
                                        hideObject = "true".equals(filter.getAttribute("hideObject"));
                                    }
                                    color = parseColorWithAlpha(filter, color);
                                    filterList.add(new DropShadowFilter(blurX, blurY, strength, quality, angle, distance, knockout, inner, hideObject, color, enabled));
                                }
                                break;
                                case "BlurFilter": {
                                    float blurX = 5;
                                    float blurY = 5;
                                    int quality = 1;
                                    if (filter.hasAttribute("blurX")) {
                                        blurX = Float.parseFloat(filter.getAttribute("blurX"));
                                    }
                                    if (filter.hasAttribute("blurY")) {
                                        blurY = Float.parseFloat(filter.getAttribute("blurY"));
                                    }
                                    if (filter.hasAttribute("quality")) {
                                        quality = Integer.parseInt(filter.getAttribute("quality"));
                                    }
                                    filterList.add(new BlurFilter(blurX, blurY, quality, enabled));
                                }
                                break;
                                case "GlowFilter": {
                                    float blurX = 5;
                                    float blurY = 5;
                                    Color color = Color.red;
                                    boolean inner = false;
                                    boolean knockout = false;
                                    int quality = 1;
                                    float strength = 1;

                                    if (filter.hasAttribute("blurX")) {
                                        blurX = Float.parseFloat(filter.getAttribute("blurX"));
                                    }
                                    if (filter.hasAttribute("blurY")) {
                                        blurY = Float.parseFloat(filter.getAttribute("blurY"));
                                    }
                                    if (filter.hasAttribute("strength")) {
                                        strength = Float.parseFloat(filter.getAttribute("strength"));
                                    }
                                    color = parseColorWithAlpha(filter, color);
                                    if (filter.hasAttribute("inner")) {
                                        inner = "true".equals(filter.getAttribute("inner"));
                                    }
                                    if (filter.hasAttribute("knockout")) {
                                        knockout = "true".equals(filter.getAttribute("knockout"));
                                    }
                                    if (filter.hasAttribute("quality")) {
                                        quality = Integer.parseInt(filter.getAttribute("quality"));
                                    }

                                    filterList.add(new GlowFilter(blurX, blurY, color, inner, knockout, quality, strength, enabled));
                                }
                                break;
                                case "BevelFilter": {
                                    float blurX = 5;
                                    float blurY = 5;
                                    float strength = 1;
                                    int quality = 1;
                                    Color shadowColor = Color.black;
                                    Color highlightColor = Color.white;
                                    float angle = 45;
                                    float distance = 5;
                                    boolean knockout = false;
                                    int type = BevelFilter.TYPE_INNER;

                                    if (filter.hasAttribute("blurX")) {
                                        blurX = Float.parseFloat(filter.getAttribute("blurX"));
                                    }
                                    if (filter.hasAttribute("blurY")) {
                                        blurY = Float.parseFloat(filter.getAttribute("blurY"));
                                    }
                                    if (filter.hasAttribute("strength")) {
                                        strength = Float.parseFloat(filter.getAttribute("strength"));
                                    }
                                    if (filter.hasAttribute("quality")) {
                                        quality = Integer.parseInt(filter.getAttribute("quality"));
                                    }
                                    shadowColor = parseColorWithAlpha(filter, shadowColor, "shadowColor", "shadowAlpha");
                                    highlightColor = parseColorWithAlpha(filter, highlightColor, "highlightColor", "highlightAlpha");
                                    if (filter.hasAttribute("angle")) {
                                        angle = Float.parseFloat(filter.getAttribute("angle"));
                                    }
                                    if (filter.hasAttribute("distance")) {
                                        distance = Float.parseFloat(filter.getAttribute("distance"));
                                    }
                                    if (filter.hasAttribute("knockout")) {
                                        knockout = "true".equals(filter.getAttribute("knockout"));
                                    }
                                    if (filter.hasAttribute("type")) {
                                        switch (filter.getAttribute("type")) {
                                            case "inner":
                                                type = BevelFilter.TYPE_INNER;
                                                break;
                                            case "outer":
                                                type = BevelFilter.TYPE_OUTER;
                                                break;
                                            case "full":
                                                type = BevelFilter.TYPE_FULL;
                                                break;
                                        }
                                    }

                                    filterList.add(new BevelFilter(blurX, blurY, strength, quality, shadowColor, highlightColor, angle, distance, knockout, type, enabled));
                                }
                                break;
                                case "GradientGlowFilter":
                                case "GradientBevelFilter": {
                                    float blurX = 5;
                                    float blurY = 5;
                                    float strength = 1;
                                    int quality = 1;
                                    float angle = 45;
                                    float distance = 5;
                                    boolean knockout = false;
                                    int type = "GradientGlowFilter".equals(filter.getNodeName()) ? GradientGlowFilter.TYPE_OUTER : GradientBevelFilter.TYPE_INNER;
                                    List<GradientEntry> gradientEntries = new ArrayList<>();

                                    if (filter.hasAttribute("blurX")) {
                                        blurX = Float.parseFloat(filter.getAttribute("blurX"));
                                    }
                                    if (filter.hasAttribute("blurY")) {
                                        blurY = Float.parseFloat(filter.getAttribute("blurY"));
                                    }
                                    if (filter.hasAttribute("strength")) {
                                        strength = Float.parseFloat(filter.getAttribute("strength"));
                                    }
                                    if (filter.hasAttribute("quality")) {
                                        quality = Integer.parseInt(filter.getAttribute("quality"));
                                    }
                                    if (filter.hasAttribute("angle")) {
                                        angle = Float.parseFloat(filter.getAttribute("angle"));
                                    }
                                    if (filter.hasAttribute("distance")) {
                                        distance = Float.parseFloat(filter.getAttribute("distance"));
                                    }
                                    if (filter.hasAttribute("knockout")) {
                                        knockout = "true".equals(filter.getAttribute("knockout"));
                                    }
                                    if (filter.hasAttribute("type")) {
                                        switch (filter.getAttribute("type")) {
                                            case "inner":
                                                type = GradientGlowFilter.TYPE_INNER;
                                                break;
                                            case "outer":
                                                type = GradientGlowFilter.TYPE_OUTER;
                                                break;
                                            case "full":
                                                type = GradientGlowFilter.TYPE_FULL;
                                                break;
                                        }
                                    }
                                    gradientEntries = parseGradientEntries(filter);

                                    if ("GradientGlowFilter".equals(filter.getNodeName())) {
                                        filterList.add(new GradientGlowFilter(blurX, blurY, strength, quality, angle, distance, knockout, type, gradientEntries, enabled));
                                    } else {
                                        filterList.add(new GradientBevelFilter(blurX, blurY, strength, quality, angle, distance, knockout, type, gradientEntries, enabled));
                                    }

                                }
                                break;
                                case "AdjustColorFilter": {
                                    //brightness="-50" contrast="75" saturation="50" hue="180"
                                    float brightness = 0;
                                    float contrast = 0;
                                    float saturation = 0;
                                    float hue = 0;
                                    if (filter.hasAttribute("brightness")) {
                                        brightness = Float.parseFloat(filter.getAttribute("brightness"));
                                    }
                                    if (filter.hasAttribute("contrast")) {
                                        contrast = Float.parseFloat(filter.getAttribute("contrast"));
                                    }
                                    if (filter.hasAttribute("saturation")) {
                                        saturation = Float.parseFloat(filter.getAttribute("saturation"));
                                    }
                                    if (filter.hasAttribute("hue")) {
                                        hue = Float.parseFloat(filter.getAttribute("hue"));
                                    }

                                    filterList.add(new AdjustColorFilter(brightness, contrast, saturation, hue, enabled));
                                }
                                break;
                            }
                        }
                    }

                    String actionScript = "";

                    Element actionscriptElement = getSubElementByName(symbolInstance, "Actionscript");
                    if (actionscriptElement != null) {
                        Element scriptElement = getSubElementByName(actionscriptElement, "script");
                        if (scriptElement != null) {
                            actionScript = scriptElement.getTextContent();
                        }
                    }

                    if (symbolType == FlaCs4Writer.SYMBOLTYPE_SPRITE) {
                        copiedComponentPathRef.setVal(copiedComponentPathRef.getVal() + 1);
                    }
                    fg.writeSymbolInstance(
                            placeMatrix,
                            centerPoint3DX,
                            centerPoint3DY,
                            transformationPointX,
                            transformationPointY,
                            instanceName,
                            colorEffect,
                            libraryItemIndex,
                            symbolType == FlaCs4Writer.SYMBOLTYPE_SPRITE ? copiedComponentPathRef.getVal() : 0,
                            blendMode,
                            cacheAsBitmap,
                            filterList,
                            symbolType,
                            trackAsMenu,
                            loop,
                            firstFrame,
                            actionScript
                    );

                }
                fg.writeKeyFrameMiddle();
                if (elementsNode != null) {
                    NodeList elements = elementsNode.getChildNodes();
                    boolean emptyFrame = true;
                    for (int e = 0; e < elements.getLength(); e++) {
                        Node element = elements.item(e);
                        if ("DOMShape".equals(element.getNodeName())) {
                            emptyFrame = false;
                            Node fillsNode = getSubElementByName(element, "fills");
                            List<Element> fillStyles = new ArrayList<>();
                            if (fillsNode != null) {
                                fillStyles = getAllSubElementsByName(fillsNode, "FillStyle");
                            }

                            Comparator<Node> indexComparator = new Comparator<>() {
                                @Override
                                public int compare(Node o1, Node o2) {
                                    Node indexAttr1Node = o1.getAttributes().getNamedItem("index");
                                    int index1 = 0;
                                    if (indexAttr1Node != null) {
                                        index1 = Integer.parseInt(indexAttr1Node.getTextContent());
                                    }
                                    Node indexAttr2Node = o2.getAttributes().getNamedItem("index");
                                    int index2 = 0;
                                    if (indexAttr2Node != null) {
                                        index2 = Integer.parseInt(indexAttr2Node.getTextContent());
                                    }
                                    return index1 - index2;
                                }
                            };

                            fillStyles.sort(indexComparator);
                            Node strokesNode = getSubElementByName(element, "strokes");
                            List<Element> strokeStyles = new ArrayList<>();
                            if (strokesNode != null) {
                                strokeStyles = getAllSubElementsByName(strokesNode, "StrokeStyle");
                            }
                            strokeStyles.sort(indexComparator);

                            Node edgesNode = getSubElementByName(element, "edges");
                            List<Element> edges = new ArrayList<>();
                            if (edgesNode != null) {
                                edges = getAllSubElementsByName(edgesNode, "Edge");
                            }

                            int totalEdgeCount = 0;

                            for (Node edge : edges) {
                                Node edgesAttrNode = edge.getAttributes().getNamedItem("edges");
                                if (edgesAttrNode != null) {
                                    String edgesAttr = edgesAttrNode.getTextContent();
                                    totalEdgeCount += FlaCs4Writer.getEdgesCount(edgesAttr);
                                }
                            }

                            fg.write(totalEdgeCount, 0x00, 0x00, 0x00);
                            fg.write(fillStyles.size(), 0x00);
                            for (Node fillStyle : fillStyles) {
                                Node fillStyleVal = getFirstSubElement(fillStyle);
                                handleFill(fillStyleVal, fg);
                            }
                            fg.write(strokeStyles.size(), 0x00);
                            for (Node strokeStyle : strokeStyles) {
                                Node strokeStyleVal = getFirstSubElement(strokeStyle);
                                int scaleMode = FlaCs4Writer.SCALE_MODE_NONE;

                                double weight = 1.0;
                                Node weightAttr = strokeStyleVal.getAttributes().getNamedItem("weight");
                                if (weightAttr != null) {
                                    weight = Double.parseDouble(weightAttr.getTextContent());
                                }

                                float miterLimit = 3f;
                                Node miterLimitAttr = strokeStyleVal.getAttributes().getNamedItem("miterLimit");
                                if (miterLimitAttr != null) {
                                    miterLimit = Float.parseFloat(miterLimitAttr.getTextContent());
                                }

                                int styleParam1 = 0;
                                int styleParam2 = 0;

                                int joints = 0;
                                int caps = 0;
                                boolean pixelHinting = false;
                                Node pixelHintingAttr = strokeStyleVal.getAttributes().getNamedItem("pixelHinting");
                                if (pixelHintingAttr != null) {
                                    if ("true".equals(pixelHintingAttr.getTextContent())) {
                                        pixelHinting = true;
                                    }
                                }

                                switch (strokeStyleVal.getNodeName()) {
                                    case "SolidStroke":
                                        styleParam1 = 0;
                                        styleParam2 = 0;
                                        joints = FlaCs4Writer.JOIN_STYLE_ROUND;
                                        caps = FlaCs4Writer.CAP_STYLE_ROUND;

                                        Node scaleModeAttr = strokeStyleVal.getAttributes().getNamedItem("scaleMode");
                                        if (scaleModeAttr != null) {
                                            if ("normal".equals(scaleModeAttr.getTextContent())) {
                                                scaleMode = FlaCs4Writer.SCALE_MODE_NORMAL;
                                            }
                                            if ("horizontal".equals(scaleModeAttr.getTextContent())) {
                                                scaleMode = FlaCs4Writer.SCALE_MODE_HORIZONTAL;
                                            }
                                            if ("vertical".equals(scaleModeAttr.getTextContent())) {
                                                scaleMode = FlaCs4Writer.SCALE_MODE_VERTICAL;
                                            }
                                        }

                                        Node capsAttr = strokeStyleVal.getAttributes().getNamedItem("caps");
                                        if (capsAttr != null) {
                                            if ("none".equals(capsAttr.getTextContent())) {
                                                caps = FlaCs4Writer.CAP_STYLE_NONE;
                                            }
                                            if ("square".equals(capsAttr.getTextContent())) {
                                                caps = FlaCs4Writer.CAP_STYLE_SQUARE;
                                            }
                                        }

                                        Node jointsAttr = strokeStyleVal.getAttributes().getNamedItem("joints");
                                        if (jointsAttr != null) {
                                            if ("bevel".equals(jointsAttr.getTextContent())) {
                                                joints = FlaCs4Writer.JOIN_STYLE_BEVEL;
                                            }
                                            if ("miter".equals(jointsAttr.getTextContent())) {
                                                joints = FlaCs4Writer.JOIN_STYLE_MITER;
                                            }
                                        }
                                        break;
                                    case "DashedStroke":
                                        double dash1 = 6;
                                        double dash2 = 6;
                                        Node dash1Attr = strokeStyleVal.getAttributes().getNamedItem("dash1");
                                        if (dash1Attr != null) {
                                            dash1 = Double.parseDouble(dash1Attr.getTextContent());
                                        }
                                        Node dash2Attr = strokeStyleVal.getAttributes().getNamedItem("dash2");
                                        if (dash2Attr != null) {
                                            dash2 = Double.parseDouble(dash2Attr.getTextContent());
                                        }

                                        if (dash1 < 0.25 || dash1 > 300.0) {
                                            throw new IllegalArgumentException("DashedStroke.dash1 is invalid");
                                        }
                                        if (dash2 < 0.25 || dash2 > 300.0) {
                                            throw new IllegalArgumentException("DashedStroke.dash2 is invalid");
                                        }

                                        styleParam1 = (int) Math.round(dash1 * 20);
                                        styleParam2 = (int) Math.round(dash2 * 20);
                                        break;
                                    case "DottedStroke":
                                        double dotSpace = 3;
                                        Node dotSpaceAttr = strokeStyleVal.getAttributes().getNamedItem("dotSpace");
                                        if (dotSpaceAttr != null) {
                                            dotSpace = Double.parseDouble(dotSpaceAttr.getTextContent());
                                        }
                                        if (dotSpace < 0.0 || dotSpace > 300.0) {
                                            throw new IllegalArgumentException("DottedStroke.dotSpace is invalid");
                                        }
                                        styleParam2 = (int) (0x10 * Math.round(dotSpace * 10) + 0x02);
                                        break;
                                    case "RaggedStroke":
                                        int pattern = getAttributeAsInt(strokeStyleVal, "pattern", Arrays.asList("solid", "simple", "random", "dotted", "random dotted", "triple dotted", "random tripple dotted"), "simple");
                                        int waveHeight = getAttributeAsInt(strokeStyleVal, "waveHeight", Arrays.asList("flat", "wavy", "very wavy", "wild"), "wavy");
                                        int waveLength = getAttributeAsInt(strokeStyleVal, "waveLength", Arrays.asList("very short", "short", "medium", "long"), "short");
                                        styleParam2 = 0x08 * pattern + 0x40 * waveHeight + 0x100 * waveLength + 0x03;
                                        break;
                                    case "StippleStroke":
                                        int dotSize = getAttributeAsInt(strokeStyleVal, "dotSize", Arrays.asList("tiny", "small", "medium", "large"), "small");
                                        int variation = getAttributeAsInt(strokeStyleVal, "variation", Arrays.asList("one size", "small variation", "varied sizes", "random sizes"), "varied sizes");
                                        int density = getAttributeAsInt(strokeStyleVal, "density", Arrays.asList("very dense", "dense", "sparse", "very sparse"), "sparse");

                                        styleParam2 = 0x08 * dotSize + 0x20 * variation + 0x80 * density + 0x04;
                                        break;
                                    case "HatchedStroke":
                                        int hatchThickness = getAttributeAsInt(strokeStyleVal, "hatchThickness", Arrays.asList("hairline", "thin", "medium", "thick"), "hairline");
                                        int space = getAttributeAsInt(strokeStyleVal, "space", Arrays.asList("very close", "close", "distant", "very distant"), "distant");
                                        int jiggle = getAttributeAsInt(strokeStyleVal, "jiggle", Arrays.asList("none", "bounce", "loose", "wild"), "none");
                                        int rotate = getAttributeAsInt(strokeStyleVal, "rotate", Arrays.asList("none", "slight", "medium", "free"), "none");
                                        int curve = getAttributeAsInt(strokeStyleVal, "curve", Arrays.asList("straight", "slight curve", "medium curve", "very curved"), "straight");
                                        int length = getAttributeAsInt(strokeStyleVal, "length", Arrays.asList("equal", "slight variation", "medium variation", "random"), "equal");

                                        styleParam2 = 0x08 * hatchThickness
                                                + 0x20 * space
                                                + 0x200 * jiggle
                                                + 0x80 * rotate
                                                + 0x800 * curve
                                                + 0x2000 * length
                                                + 0x05;
                                        break;
                                }

                                Node sharpCornersAttr = strokeStyleVal.getAttributes().getNamedItem("sharpCorners");
                                if (sharpCornersAttr != null) {
                                    if ("true".equals(sharpCornersAttr.getTextContent())) {
                                        styleParam2 += 0x8000;
                                    }
                                }

                                Node fill = getSubElementByName(strokeStyleVal, "fill");
                                if (fill != null) {
                                    Node fillStyleVal = getFirstSubElement(fill);
                                    Color baseColor = Color.black;
                                    if ("SolidColor".equals(fillStyleVal.getNodeName())) {
                                        baseColor = parseColorWithAlpha(fillStyleVal);
                                    }

                                    fg.writeStrokeBegin(baseColor, weight, pixelHinting, scaleMode, caps, joints, miterLimit, styleParam1, styleParam2);
                                    handleFill(fillStyleVal, fg);
                                }
                            }
                            fg.beginShape();
                            for (Node edge : edges) {
                                int strokeStyle = 0;
                                int fillStyle0 = 0;
                                int fillStyle1 = 0;
                                Node strokeStyleAttr = edge.getAttributes().getNamedItem("strokeStyle");
                                if (strokeStyleAttr != null) {
                                    strokeStyle = Integer.parseInt(strokeStyleAttr.getTextContent());
                                }
                                Node fillStyle0StyleAttr = edge.getAttributes().getNamedItem("fillStyle0");
                                if (fillStyle0StyleAttr != null) {
                                    fillStyle0 = Integer.parseInt(fillStyle0StyleAttr.getTextContent());
                                }
                                Node fillStyle1StyleAttr = edge.getAttributes().getNamedItem("fillStyle1");
                                if (fillStyle1StyleAttr != null) {
                                    fillStyle1 = Integer.parseInt(fillStyle1StyleAttr.getTextContent());
                                }
                                Node edgesAttrNode = edge.getAttributes().getNamedItem("edges");
                                if (edgesAttrNode != null) {
                                    String edgesStr = edgesAttrNode.getTextContent();
                                    fg.writeEdges(edgesStr, strokeStyle, fillStyle0, fillStyle1);
                                }
                            }

                        }
                    }

                    if (emptyFrame) {
                        fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                    }
                    int keyMode = FlaCs4Writer.KEYMODE_STANDARD;
                    Node keyModeAttr = frame.getAttributes().getNamedItem("keyMode");
                    if (keyModeAttr != null) {
                        keyMode = Integer.parseInt(keyModeAttr.getTextContent());
                    }

                    int duration = 1;
                    Node durationAttr = frame.getAttributes().getNamedItem("duration");
                    if (durationAttr != null) {
                        duration = Integer.parseInt(durationAttr.getTextContent());
                    }

                    String actionScript = "";

                    Element actionscriptElement = getSubElementByName(frame, "Actionscript");
                    if (actionscriptElement != null) {
                        Element scriptElement = getSubElementByName(actionscriptElement, "script");
                        if (scriptElement != null) {
                            actionScript = scriptElement.getTextContent();
                        }
                    }

                    fg.writeKeyFrameEnd(duration, keyMode, actionScript);
                }
            }
        }

        {
            String layerName = "Layer";
            Node layerNameAttr = layer.getAttributes().getNamedItem("name");
            if (layerNameAttr != null) {
                layerName = layerNameAttr.getTextContent();
            }

            Node outlineAttr = layer.getAttributes().getNamedItem("outline");
            Node useOutlineViewAttr = layer.getAttributes().getNamedItem("useOutlineView");

            boolean showOutlines = false;
            if (outlineAttr != null && useOutlineViewAttr != null) {
                showOutlines = "true".equals(outlineAttr.getTextContent())
                        && "true".equals(useOutlineViewAttr.getTextContent());
            }

            Color color = Color.BLACK;
            Node colorAttr = layer.getAttributes().getNamedItem("color");
            if (colorAttr != null) {
                color = parseColor(colorAttr.getTextContent());
            }

            boolean hiddenLayer = false;
            Node visibleAttr = layer.getAttributes().getNamedItem("visible");
            if (visibleAttr != null) {
                hiddenLayer = "false".equals(visibleAttr.getTextContent());
            }

            boolean lockedLayer = false;
            Node lockedAttr = layer.getAttributes().getNamedItem("locked");
            if (lockedAttr != null) {
                lockedLayer = "true".equals(lockedAttr.getTextContent());
            }

            boolean isSelected = false; //Note: how is this different from "current" attribute
            Node isSelectedAttr = layer.getAttributes().getNamedItem("isSelected");
            if (isSelectedAttr != null) {
                isSelected = "true".equals(isSelectedAttr.getTextContent());
            }

            int heightMultiplier = 1;
            if (layer.hasAttribute("heightMultiplier")) {
                heightMultiplier = Integer.parseInt(layer.getAttribute("heightMultiplier"));
            }

            fg.writeLayerEnd(layerName,
                    isSelected,
                    hiddenLayer,
                    lockedLayer,
                    color,
                    showOutlines,
                    layerType,
                    heightMultiplier);
        }
    }

    private static Map<Integer, Integer> calculateReverseParentLayerIndices(List<Element> layers) {
        Map<Integer, Integer> layerIndexToRevLayerIndex = new HashMap<>();
        Map<Integer, Integer> folderToEndLayer = new HashMap<>();

        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            Element layer = layers.get(layerIndex);
            boolean canHaveSubLayers = false;
            if (layer.hasAttribute("layerType")) {
                String layerTypeStr = layer.getAttribute("layerType");
                switch (layerTypeStr) {
                    case "folder":
                    case "guide":
                        canHaveSubLayers = true;
                        break;
                }
            }

            if (canHaveSubLayers) {
                Stack<Integer> subs = new Stack<>();
                subs.push(layerIndex);
                int endLayer = layers.size();
                for (int layerIndex2 = layerIndex + 1; layerIndex2 < layers.size(); layerIndex2++) {
                    Element layer2 = layers.get(layerIndex2);
                    boolean canHaveSubLayers2 = false;
                    if (layer2.hasAttribute("layerType")) {
                        String layerTypeStr = layer2.getAttribute("layerType");
                        switch (layerTypeStr) {
                            case "folder":
                            case "guide":
                                canHaveSubLayers2 = true;
                                break;
                        }
                    }

                    int parentLayerIndex2 = -1;
                    Node parentLayerIndexAttr2 = layer2.getAttributes().getNamedItem("parentLayerIndex");
                    if (parentLayerIndexAttr2 != null) {
                        parentLayerIndex2 = Integer.parseInt(parentLayerIndexAttr2.getTextContent());
                    }
                    if (parentLayerIndex2 > -1) {
                        while (subs.contains(parentLayerIndex2) && subs.peek() != parentLayerIndex2) {
                            subs.pop();
                        }
                        if (!subs.contains(parentLayerIndex2)) {
                            endLayer = layerIndex2;
                            break;
                        }
                    } else {
                        endLayer = layerIndex2;
                        break;
                    }
                    if (canHaveSubLayers2) {
                        subs.push(layerIndex2);
                    }
                }

                folderToEndLayer.put(layerIndex, endLayer);
            }
        }

        for (int folderLayerIndex : folderToEndLayer.keySet()) {
            int endLayer = folderToEndLayer.get(folderLayerIndex);
            int reverseParent = 0;
            for (int layerIndex3 = endLayer; layerIndex3 < layers.size(); layerIndex3++) {
                Element layer3 = layers.get(layerIndex3);
                boolean isFolder = false;
                if (layer3.hasAttribute("layerType")) {
                    String layerTypeStr = layer3.getAttribute("layerType");
                    isFolder = "folder".equals(layerTypeStr);
                }
                if (isFolder) {
                    reverseParent++;
                } else {
                    reverseParent += 2;
                }
            }

            Element layer3 = layers.get(folderLayerIndex);
            while (layer3.hasAttribute("parentLayerIndex")) {
                reverseParent++;
                int parentLayerIndex = Integer.parseInt(layer3.getAttribute("parentLayerIndex"));
                if (parentLayerIndex >= 0 && parentLayerIndex < layers.size()) {
                    layer3 = layers.get(parentLayerIndex);
                } else {
                    break;
                }
            }

            layerIndexToRevLayerIndex.put(folderLayerIndex, reverseParent);
        }
        return layerIndexToRevLayerIndex;
    }

    private void generatePageFile(InputStream is, OutputStream os) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = factory.newDocumentBuilder();

        Document doc = docBuilder.parse(is);

        FlaCs4Writer fg = new FlaCs4Writer(os);

        List<String> definedClasses = new ArrayList<>();

        fg.write(0x01);
        useClass("CPicPage", fg, definedClasses);
        fg.write(0x00);

        int nextLayerId = 1;
        int nextFolderId = 1;
        Reference<Integer> copiedComponentPathRef = new Reference<>(0);
        Reference<Integer> totalFramesCountRef = new Reference<>(0);

        NodeList timeLines = doc.getElementsByTagName("DOMTimeline");
        for (int i = 0; i < timeLines.getLength(); i++) {
            Node n = timeLines.item(i);
            Node layersNode = getSubElementByName(n, "layers");
            if (layersNode != null) {
                List<Element> layers = getAllSubElementsByName(layersNode, "DOMLayer");

                Map<Integer, Integer> layerIndexToRevLayerIndex = calculateReverseParentLayerIndices(layers);
                Stack<Integer> openedParentLayers = new Stack<>();

                for (int layerIndex = layers.size() - 1; layerIndex >= 0; layerIndex--) {
                    Element layer = layers.get(layerIndex);

                    boolean autoNamed = true;
                    if (layer.hasAttribute("autoNamed")) {
                        autoNamed = !"false".equals(layer.getAttribute("autoNamed"));
                    }

                    boolean canHaveSubLayers = false;
                    if (layer.hasAttribute("layerType")) {
                        String layerTypeStr = layer.getAttribute("layerType");
                        switch (layerTypeStr) {
                            case "folder":
                            case "guide":
                                canHaveSubLayers = true;
                                break;
                        }
                    }

                    if (canHaveSubLayers) {
                        nextFolderId++;

                        boolean noSubLayers = true;
                        for (int layerIndex2 = layerIndex + 1; layerIndex2 < layers.size(); layerIndex2++) {
                            Node layer2 = layers.get(layerIndex2);
                            int parentLayerIndex2 = -1;
                            Node parentLayerIndexAttr2 = layer2.getAttributes().getNamedItem("parentLayerIndex");
                            if (parentLayerIndexAttr2 != null) {
                                parentLayerIndex2 = Integer.parseInt(parentLayerIndexAttr2.getTextContent());
                            }
                            if (parentLayerIndex2 == layerIndex) {
                                noSubLayers = false;
                                break;
                            }
                        }
                        if (noSubLayers) {
                            handleParentLayer(layer, fg, layerIndexToRevLayerIndex, true, definedClasses, copiedComponentPathRef, totalFramesCountRef);
                        }

                        continue;
                    }

                    int parentLayerIndex = -1;
                    if (layer.hasAttribute("parentLayerIndex")) {
                        parentLayerIndex = Integer.parseInt(layer.getAttribute("parentLayerIndex"));
                    }

                    nextLayerId++;

                    writeLayerContents(layer, fg, definedClasses, copiedComponentPathRef, totalFramesCountRef);
                    if (parentLayerIndex > -1) {
                        int reverseParentLayerIndex = layerIndexToRevLayerIndex.get(parentLayerIndex);
                        if (openedParentLayers.contains(parentLayerIndex)) {
                            fg.writeLayerEnd2(reverseParentLayerIndex, true, autoNamed);
                            if (parentLayerIndex == layerIndex - 1) {
                                fg.writeEndParentLayer(reverseParentLayerIndex);
                                openedParentLayers.pop();
                            }
                        } else {
                            Element parentLayer = layers.get(parentLayerIndex);
                            handleParentLayer(parentLayer, fg, layerIndexToRevLayerIndex, false, definedClasses, copiedComponentPathRef, totalFramesCountRef);
                            if (parentLayerIndex == layerIndex - 1) {
                                fg.write(7 + reverseParentLayerIndex, 0x00);
                            } else {
                                openedParentLayers.push(parentLayerIndex);
                            }
                        }
                    } else {
                        fg.writeLayerEnd2(-1, true, autoNamed);
                    }
                }
            }
        }
        fg.writePageFooter(nextLayerId, nextFolderId, 0);
    }

    public void generatePageFile(File inputFile, File outputFile) throws FileNotFoundException, IOException, SAXException, ParserConfigurationException {
        try (FileInputStream fis = new FileInputStream(inputFile); FileOutputStream fos = new FileOutputStream(outputFile)) {
            generatePageFile(fis, fos);
        }
    }

}
