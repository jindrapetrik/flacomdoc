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
import java.awt.Font;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import java.util.logging.Logger;

/**
 *
 * @author JPEXS
 */
public class PageGenerator extends AbstractGenerator {

    protected void useClass(String className, int version, FlaCs4Writer os, List<String> definedClasses) throws IOException {
        if (definedClasses.contains(className)) {
            os.write(1 + 2 * definedClasses.indexOf(className));
            os.write(0x80);
        } else {
            os.write(0xFF, 0xFF, 0x01, 0x00);
            os.writeLenAsciiString(className);
            definedClasses.add(className);
        }
        os.write(version);
    }

    private void handleFill(Node fillStyleVal, FlaCs4Writer fg) throws IOException {
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

                int type = FlaCs4Writer.FILLTYPE_LINEAR_GRADIENT;
                if ("RadialGradient".equals(fillStyleVal.getNodeName())) {
                    type = FlaCs4Writer.FILLTYPE_RADIAL_GRADIENT;
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
                                                type = FlaCs4Writer.FILLTYPE_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaCs4Writer.FILLTYPE_BITMAP;
                                            }
                                        } else {
                                            if (bitmapIsClipped) {
                                                type = FlaCs4Writer.FILLTYPE_NON_SMOOTHED_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaCs4Writer.FILLTYPE_NON_SMOOTHED_BITMAP;
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

    private List<GradientEntry> parseGradientEntries(Element element) {
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

    protected void handleBitmapInstance(Element bitmapInstance,
            FlaCs4Writer fg,
            List<String> definedClasses) throws IOException {
        if (!bitmapInstance.hasAttribute("libraryItemName")) {
            return;
        }

        String libraryItemName = bitmapInstance.getAttribute("libraryItemName");

        Element mediaElement = getSubElementByName(bitmapInstance.getOwnerDocument().getDocumentElement(), "media");
        if (mediaElement == null) {
            return;
        }
        List<Element> domBitmapItems = getAllSubElementsByName(mediaElement, "DOMBitmapItem");
        int bitmapId = 0;
        for (int i = 0; i < domBitmapItems.size(); i++) {
            Element domBitmapItem = domBitmapItems.get(i);
            if (domBitmapItem.hasAttribute("name")) {
                if (libraryItemName.equals(domBitmapItem.getAttribute("name"))) {
                    bitmapId = i + 1;
                    break;
                }
            }
        }

        if (bitmapId == 0) {
            return;
        }

        boolean selected = false;
        if (bitmapInstance.hasAttribute("selected")) {
            selected = "true".equals(bitmapInstance.getAttribute("selected"));
        }

        useClass("CPicBitmap", 0x05, fg, definedClasses);
        Matrix placeMatrix = parseMatrix(getSubElementByName(bitmapInstance, "matrix"));

        fg.write(selected ? 0x02 : 0, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x80,
                0x00, 0x00, 0x00, 0x80,
                0x00, 0x00, 0x02);
        fg.writeMatrix(placeMatrix);
        fg.writeUI16(bitmapId);
        fg.write(0x00);
    }

    protected void handleElements(Element elementsNode,
            FlaCs4Writer fg,
            List<String> definedClasses,
            Reference<Integer> copiedComponentPathRef) throws IOException {
        if (elementsNode == null) {
            return;
        }
        List<Element> instances = getAllSubElements(elementsNode);
        for (int instanceIndex = 0; instanceIndex < instances.size(); instanceIndex++) {
            Element instance = instances.get(instanceIndex);
            switch (instance.getTagName()) {
                case "DOMSymbolInstance":
                    handleSymbolInstance(instance, fg, definedClasses, copiedComponentPathRef);
                    break;
                case "DOMBitmapInstance":
                    handleBitmapInstance(instance, fg, definedClasses);
                    break;
                case "DOMStaticText":
                case "DOMDynamicText":
                case "DOMInputText":
                    handleText(instance, fg, definedClasses);
                    break;
                case "DOMTLFText":
                    Logger.getLogger(PageGenerator.class.getName()).warning("DOMTLFText element is not supported");
                    break;
            }
        }
    }

    protected void handleSymbolInstance(
            Element symbolInstance,
            FlaCs4Writer fg,
            List<String> definedClasses,
            Reference<Integer> copiedComponentPathRef
    ) throws IOException {

        if (!symbolInstance.hasAttribute("libraryItemName")) {
            //nothing we can do
            return;
        }

        String libraryItemName = symbolInstance.getAttribute("libraryItemName");
        Element symbolsElement = getSubElementByName(symbolInstance.getOwnerDocument().getDocumentElement(), "symbols");
        if (symbolsElement == null) {
            //nothing we can do                                    
            return;
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
                libraryItemIndex = e + 1;
                break;
            }
        }
        if (libraryItemIndex == -1) {
            //nothing we can do 
            return;
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
            useClass("CPicButton", 5, fg, definedClasses);
            if (symbolInstance.hasAttribute("trackAsMenu")) {
                trackAsMenu = "true".equals(symbolInstance.getAttribute("trackAsMenu"));
            }
        } else if (symbolType == FlaCs4Writer.SYMBOLTYPE_GRAPHIC) {
            useClass("CPicSymbol", 5, fg, definedClasses);

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
            useClass("CPicSprite", 5, fg, definedClasses);
        }

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

        boolean selected = false;
        if (symbolInstance.hasAttribute("selected")) {
            selected = "true".equals(symbolInstance.getAttribute("selected"));
        }

        fg.writeSymbolInstance(
                selected,
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

    private void handleText(Element element, FlaCs4Writer fg, List<String> definedClasses) throws IOException {
        if ("DOMStaticText".equals(element.getTagName())
                || "DOMDynamicText".equals(element.getTagName())
                || "DOMInputText".equals(element.getTagName())) {
            useClass("CPicText", 5, fg, definedClasses);

            boolean isDynamic = "DOMDynamicText".equals(element.getTagName());
            boolean isInput = "DOMInputText".equals(element.getTagName());

            boolean isStatic = "DOMStaticText".equals(element.getTagName());

            String instanceName = "";
            if (!isStatic && element.hasAttribute("name")) {
                instanceName = element.getAttribute("name");
            }

            /*
                                        <DOMStaticText width="158.95" height="13.45">
                            <!-- optional:
                            fontRenderingMode="device" , "bitmap", "standard", "customThicknessSharpness"
                            isSelectable="false"
                            orientation="vertical right to left", "vertical left to right"
                            
                            if customThicknessSharpness then 
                            float attributes antiAliasSharpness, antiAliasThickness
                            
                            for dynamic:
                            renderAsHTML="true"
                            border="true"
                            lineType="multiline","multiline no wrap"
                            
                            for AS1/2 dynamic:
                            variableName="xyz"
                            
                            for input:
                            maxCharacters="1234"
                            -->
                                             <matrix>
                                                  <Matrix tx="182" ty="132.25"/>
                                             </matrix>
                                             <textRuns>
                                                  <DOMTextRun>
                                                       <characters>ABC</characters>
                                                       <textAttrs>
                                                            <DOMTextAttrs aliasText="false" rotation="true" alpha="0.8" lineSpacing="1.65" bitmapSize="240" face="TimesNewRomanPSMT" fillColor="#123456"/>
                                                            <!-- 
                                                            optional: autoKern="false" 
                                                                    letterSpacing="2.5"
                                                                    alignment="center","right","justify"
                                                                    characterPosition="superscript", "subscript" - if not selectable
                                                                    indent="20"
                                                                    leftMargin="20"
                                                                    target="bagr" 
                                                                    url="http://www.google.com"
                            -->
                                                       </textAttrs>
                                                  </DOMTextRun>
                                             </textRuns>
                                             <filters>
                                                  <DropShadowFilter/>
                                             </filters>
                                        </DOMStaticText>
             */
            int maxCharacters = 0;
            if (isInput && element.hasAttribute("maxCharacters")) {
                maxCharacters = Integer.parseInt(element.getAttribute("maxCharacters"));
            }

            boolean renderAsHTML = false;
            boolean border = false;

            boolean multiline = false;
            boolean wrap = false;
            boolean password = false;
            String variableName = "";
            if (isDynamic || isInput) {
                if (element.hasAttribute("renderAsHTML")) {
                    renderAsHTML = "true".equals(element.getAttribute("renderAsHTML"));
                }
                if (element.hasAttribute("border")) {
                    border = "true".equals(element.getAttribute("border"));
                }
                if (element.hasAttribute("lineType")) {
                    switch (element.getAttribute("lineType")) {
                        case "multiline":
                            multiline = true;
                            wrap = true;
                            break;
                        case "multiline no wrap":
                            multiline = true;
                            break;
                        case "password":
                            if (isInput) {
                                password = true;
                            }
                            break;
                    }
                }
                if (element.hasAttribute("variableName")) {
                    variableName = element.getAttribute("variableName");
                }
            }

            final int FONTRENDERING_DEVICE = 0;
            final int FONTRENDERING_BITMAP = 1;
            final int FONTRENDERING_STANDARD = 2;
            final int FONTRENDERING_DEFAULT = 3;
            final int FONTRENDERING_CUSTOM = 4;

            int fontRenderingMode = FONTRENDERING_DEFAULT;
            boolean isSelectable = true;
            Matrix matrix = new Matrix();

            float left = 0f;
            float width = 0f;
            float top = 0f;
            float height = 0f;

            float antiAliasSharpness = 0f;
            float antiAliasThickness = 0f;

            if (element.hasAttribute("antiAliasSharpness")) {
                antiAliasSharpness = Float.parseFloat(element.getAttribute("antiAliasSharpness"));
            }
            if (element.hasAttribute("antiAliasThickness")) {
                antiAliasThickness = Float.parseFloat(element.getAttribute("antiAliasThickness"));
            }

            if (element.hasAttribute("fontRenderingMode")) {
                switch (element.getAttribute("fontRenderingMode")) {
                    case "device":
                        fontRenderingMode = FONTRENDERING_DEVICE;
                        break;
                    case "bitmap":
                        fontRenderingMode = FONTRENDERING_BITMAP;
                        break;
                    case "standard":
                        fontRenderingMode = FONTRENDERING_STANDARD;
                        break;
                    case "customThicknessSharpness":
                        fontRenderingMode = FONTRENDERING_CUSTOM;
                        break;
                }
            }

            if (element.hasAttribute("left")) {
                left = Float.parseFloat(element.getAttribute("left"));
            }
            if (element.hasAttribute("width")) {
                width = Float.parseFloat(element.getAttribute("width"));
            }
            if (element.hasAttribute("top")) {
                top = Float.parseFloat(element.getAttribute("top"));
            }
            if (element.hasAttribute("height")) {
                height = Float.parseFloat(element.getAttribute("height"));
            }
            matrix = parseMatrix(getSubElementByName(element, "matrix"));

            if (!isInput && element.hasAttribute("isSelectable")) {
                isSelectable = !"false".equals(element.getAttribute("isSelectable"));
            }

            boolean vertical = false;
            boolean rightToLeft = false;
            if (!isInput && element.hasAttribute("orientation")) {
                switch (element.getAttribute("orientation")) {
                    case "vertical right to left":
                        vertical = true;
                        rightToLeft = true;
                        break;
                    case "vertical left to right":
                        vertical = true;
                        break;
                }
            }

            boolean selected = false;
            if (element.hasAttribute("selected")) {
                selected = "true".equals(element.getAttribute("selected"));
            }

            //orientation="vertical right to left", "vertical left to right"
            //fontRenderingMode="device" , "bitmap", "standard", "customThicknessSharpness"
            fg.write(selected ? 0x02 : 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x80,
                    0x00, 0x00, 0x00, 0x80,
                    0x00, 0x00, 0x0E);
            fg.writeMatrix(matrix);
            fg.writeUI32((int) Math.round(left * 20));
            fg.writeUI32((int) Math.round((left + width) * 20));
            fg.writeUI32((int) Math.round(top * 20));
            fg.writeUI32((int) Math.round((top + height) * 20));

            fg.write(
                    0x00, 0x00,
                    (!isStatic ? 0x01 : 0)
                    + (isDynamic ? 0x02 : 0)
                    + (password ? 0x04 : 0)
                    + (border ? 0x40 : 0)
                    + (wrap ? 0x08 : 0)
                    + (multiline ? 0x10 : 0)
            );

            Element fontsElement = getSubElementByName(element.getOwnerDocument().getDocumentElement(), "fonts");
            List<Element> domFontItems = new ArrayList<>();

            if (fontsElement != null) {
                domFontItems = getAllSubElementsByName(fontsElement, "DOMFontItem");
            }

            Element textRunsElement = getSubElementByName(element, "textRuns");
            List<Element> domTextRuns = new ArrayList<>();
            if (textRunsElement != null) {
                domTextRuns = getAllSubElementsByName(textRunsElement, "DOMTextRun");
            }

            List<String> allEmbedRanges = new ArrayList<>();
            int embedFlag = 0;
            String embeddedCharacters = "";
            for (Element textRun : domTextRuns) {
                Element textAttrsElement = getSubElementByName(textRun, "textAttrs");
                if (textAttrsElement == null) {
                    continue;
                }
                Element domTextAttrs = getSubElementByName(textAttrsElement, "DOMTextAttrs");
                if (domTextAttrs == null) {
                    continue;
                }
                if (domTextAttrs.hasAttribute("face")) {
                    String face = domTextAttrs.getAttribute("face");

                    for (Element domFontItem : domFontItems) {
                        if (domFontItem.hasAttribute("font")) {
                            String fontPsName = domFontItem.getAttribute("font");
                            if (fontPsName.equals(face)) {
                                embedFlag |= 1;
                                if (domFontItem.hasAttribute("embeddedCharacters")) {
                                    embeddedCharacters = domFontItem.getAttribute("embeddedCharacters");
                                    embedFlag |= 0x20;
                                }
                                if (domFontItem.hasAttribute("embedRanges")) {
                                    String embedRanges = domFontItem.getAttribute("embedRanges");
                                    String[] rangesParts = embedRanges.split("\\|", -1);
                                    for (String part : rangesParts) {
                                        int rangeId = Integer.parseInt(part);
                                        if (rangeId >= 1 && rangeId <= 4) {
                                            embedFlag |= (1 << rangeId);
                                        }
                                        if (!allEmbedRanges.contains(part)) {
                                            allEmbedRanges.add(part);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            fg.write((renderAsHTML ? 0x80 : 0) + embedFlag);
            if (!isStatic) {
                fg.write(0);
            } else {
                fg.write(((fontRenderingMode == FONTRENDERING_DEVICE ? 0x02 : 0)
                        + (isSelectable ? 0x01 : 0)));
            }
            fg.write(0x00);
            fg.writeUI16(maxCharacters);
            fg.write(0xFF, 0xFE, 0xFF);
            fg.writeLenUnicodeString(variableName);
            if (!embeddedCharacters.isEmpty()) {
                fg.write(0xFF, 0xFE, 0xFF);
                fg.writeLenUnicodeString(embeddedCharacters);
            }
            fg.write(0x00);

            /*
                            <DOMTextRun>
                                                       <characters>ABC</characters>
                                                       <textAttrs>
                                                            <DOMTextAttrs 
                            aliasText="false"  ???
                            rotation="true"    ???
                            alpha="0.8"
                            lineSpacing="1.65"
                            bitmapSize="240"
                            face="TimesNewRomanPSMT" 
                            fillColor="#123456"
                            />
                                                            <!-- 
                                                            optional: autoKern="false" 
                                                                    letterSpacing="2.5"
                                                                    alignment="center","right","justify"
                                                                    characterPosition="superscript", "subscript" - if not selectable
                                                                    indent="20"
                                                                    leftMargin="20"
                                                                    target="bagr" 
                                                                    url="http://www.google.com"
                            -->
                                                       </textAttrs>
                                                  </DOMTextRun>
             */
            List<FilterInterface> filters = new ArrayList<>();

            for (Element textRun : domTextRuns) {
                String characters = "";

                Element charactersElement = getSubElementByName(textRun, "characters");
                if (charactersElement != null) {
                    characters = charactersElement.getTextContent();
                }

                Element textAttrsElement = getSubElementByName(textRun, "textAttrs");
                if (textAttrsElement == null) {
                    continue;
                }
                Element domTextAttrs = getSubElementByName(textAttrsElement, "DOMTextAttrs");
                if (domTextAttrs == null) {
                    continue;
                }

                String face = "TimesNewRomanPSMT"; //?

                if (domTextAttrs.hasAttribute("face")) {
                    face = domTextAttrs.getAttribute("face");
                }

                Font font = psNameToFontName.get(face);
                String fontFamily = "";
                boolean bold = false;
                boolean italic = false;
                if (font != null) {
                    fontFamily = font.getFamily();
                    String fontNameLowercase = font.getFontName(Locale.US).toLowerCase();
                    bold = fontNameLowercase.contains("bold");
                    italic = fontNameLowercase.contains("italic") || fontNameLowercase.contains("oblique");
                }

                Color fillColor = parseColorWithAlpha(domTextAttrs, Color.red, "fillColor", "alpha");
                float size = 12f;

                if (domTextAttrs.hasAttribute("size")) {
                    size = Float.parseFloat(domTextAttrs.getAttribute("size"));
                }

                int bitmapSize = (int) Math.round(size * 20);
                float lineSpacing = 1.65f;

                if (domTextAttrs.hasAttribute("lineSpacing")) {
                    lineSpacing = Float.parseFloat(domTextAttrs.getAttribute("lineSpacing"));
                }

                float letterSpacing = 0f;

                if (domTextAttrs.hasAttribute("letterSpacing")) {
                    letterSpacing = Float.parseFloat(domTextAttrs.getAttribute("letterSpacing"));
                }

                boolean autoKern = true;

                if (domTextAttrs.hasAttribute("autoKern")) {
                    autoKern = !"false".equals(domTextAttrs.getAttribute("autoKern"));
                }

                final int ALIGN_LEFT = 0;
                final int ALIGN_RIGHT = 1;
                final int ALIGN_CENTER = 2;
                final int ALIGN_JUSTIFY = 3;

                int alignment = ALIGN_LEFT;

                if (domTextAttrs.hasAttribute("alignment")) {
                    switch (domTextAttrs.getAttribute("alignment")) {
                        case "right":
                            alignment = ALIGN_RIGHT;
                            break;
                        case "center":
                            alignment = ALIGN_CENTER;
                            break;
                        case "justify":
                            alignment = ALIGN_JUSTIFY;
                            break;
                    }
                }

                if (fontRenderingMode == FONTRENDERING_DEVICE) {
                    autoKern = false;
                }

                final int CHARACTERPOSITION_NORMAL = 0;
                final int CHARACTERPOSITION_SUPERSCRIPT = 1;
                final int CHARACTERPOSITION_SUBSCRIPT = 2;

                int characterPosition = CHARACTERPOSITION_NORMAL;

                if (!isSelectable && domTextAttrs.hasAttribute("characterPosition")) {
                    switch (domTextAttrs.getAttribute("characterPosition")) {
                        case "superscript":
                            characterPosition = CHARACTERPOSITION_SUPERSCRIPT;
                            break;
                        case "subscript":
                            characterPosition = CHARACTERPOSITION_SUBSCRIPT;
                            break;
                    }
                }

                float indent = 0f;

                if (domTextAttrs.hasAttribute("indent")) {
                    indent = Float.parseFloat(domTextAttrs.getAttribute("indent"));
                }

                float leftMargin = 0f;

                if (domTextAttrs.hasAttribute("leftMargin")) {
                    leftMargin = Float.parseFloat(domTextAttrs.getAttribute("leftMargin"));
                }

                float rightMargin = 0f;

                if (domTextAttrs.hasAttribute("rightMargin")) {
                    rightMargin = Float.parseFloat(domTextAttrs.getAttribute("rightMargin"));
                }

                String url = "";
                String target = "";

                if (domTextAttrs.hasAttribute("url")) {
                    url = domTextAttrs.getAttribute("url");
                    if (domTextAttrs.hasAttribute("target")) {
                        target = domTextAttrs.getAttribute("target");
                    }
                }

                fg.writeUI16(characters.length());
                fg.write(0x0F);
                fg.writeUI16(bitmapSize);
                fg.write(0xFF, 0xFE, 0xFF);
                fg.writeLenUnicodeString(fontFamily);
                fg.write(0xFF, 0xFE, 0xFF);
                fg.writeLenUnicodeString(face);

                fg.write(0x00, 0x00, 0x00, 0x40);
                fg.write(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), fillColor.getAlpha());

                /*
                        FIXME!!!
                        I don't know how to calculate following (two) values,
                        it is somehow bases on font family.
                        
                        Here are sample values for some familys (hex, decimal, binary)
                        
                        Times New Roman 0x12	18	0001 0010	
                        Arial 0x22		34	0010 0010	
                        Calibri 0x22		34	0010 0010	
                        Comic sans 0x42		66	0100 0010	
                        Courier new 0x31	49	0011 0001
                        Lucida console 0x31	49	0011 0001	
                        Tahoma 0x22		34	0010 0010
                        Georgia 0x22		34	0010 0010
                        Webdings 0x12 0x02	18/530	0001 0010
                        Wingdings 0x02 0x02	2/514	0000 0010
                        Verdana	0x22		34	0010 0010
                        Impact 0x22		34	0010 0010

                 */
                fg.write(0x12,
                        0x00);

                fg.write(
                        bold ? 1 : 0,
                        italic ? 1 : 0,
                        0x00,
                        autoKern ? 1 : 0,
                        characterPosition,
                        alignment
                );
                fg.writeUI16((int) Math.round(lineSpacing * 20));
                fg.writeUI16((int) Math.round(indent * 20));
                fg.writeUI16((int) Math.round(leftMargin * 20));
                fg.writeUI16((int) Math.round(rightMargin * 20));
                fg.writeUI16((int) Math.round(letterSpacing * 20));
                fg.write(0xFF, 0xFE, 0xFF);
                fg.writeLenUnicodeString(url);

                fg.write(vertical ? 1 : 0,
                        rightToLeft ? 1 : 0,
                        fontRenderingMode == FONTRENDERING_CUSTOM
                                ? 1 : 0
                );
                fg.write(
                        fontRenderingMode == FONTRENDERING_BITMAP ? 1 : 0,
                        0xFF, 0xFE, 0xFF);
                fg.writeLenUnicodeString(target);
                fg.write(0x02);

                switch (fontRenderingMode) {
                    case FONTRENDERING_DEFAULT:
                        fg.write(1);
                        break;
                    case FONTRENDERING_CUSTOM:
                        fg.write(2);
                        break;
                    default:
                        fg.write(0);
                        break;
                }
                fg.writeFloat(antiAliasThickness);
                fg.writeFloat(antiAliasSharpness);
                fg.write(0xFF, 0xFE, 0xFF);
                fg.writeLenUnicodeString(url);
                fg.write(characters.getBytes("UTF-16LE"));
            }

            fg.write(0x00, 0x00,
                    0xFF, 0xFE, 0xFF);
            fg.writeLenUnicodeString(instanceName);
            fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0xFF, 0xFE, 0xFF, 0x00,
                    0xFF, 0xFE, 0xFF);
            fg.writeLenUnicodeString(String.join("|", allEmbedRanges));
            if (!filters.isEmpty()) {
                fg.write(0x01);
                fg.writeUI32(filters.size()); //Is it really 4 bytes long?
                for (FilterInterface filter : filters) {
                    filter.write(fg);
                }
            }
            fg.write(0x00, 0x00, 0x00);
        }
    }

    private void writeLayerContents(
            Element layer,
            FlaCs4Writer fg,
            List<String> definedClasses,
            Reference<Integer> copiedComponentPathRef,
            Reference<Integer> totalFramesCountRef
    ) throws IOException {
        useClass("CPicLayer", 5, fg, definedClasses);
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
                useClass("CPicFrame", 5, fg, definedClasses);
                fg.write(0x00);
                totalFramesCountRef.setVal(totalFramesCountRef.getVal() + 1);
                Node frame = frames.get(f);
                Element elementsNode = getSubElementByName(frame, "elements");

                handleElements(elementsNode, fg, definedClasses, copiedComponentPathRef);
                List<Element> elements = new ArrayList<>();
                if (elementsNode != null) {
                    elements = getAllSubElements(elementsNode);
                }
                fg.writeKeyFrameMiddle();
                boolean emptyFrame = true;
                for (int e = 0; e < elements.size(); e++) {
                    Element element = elements.get(e);
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
                            int scaleMode = FlaCs4Writer.SCALEMODE_NONE;

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
                                    joints = FlaCs4Writer.JOINSTYLE_ROUND;
                                    caps = FlaCs4Writer.CAPSTYLE_ROUND;

                                    Node scaleModeAttr = strokeStyleVal.getAttributes().getNamedItem("scaleMode");
                                    if (scaleModeAttr != null) {
                                        if ("normal".equals(scaleModeAttr.getTextContent())) {
                                            scaleMode = FlaCs4Writer.SCALEMODE_NORMAL;
                                        }
                                        if ("horizontal".equals(scaleModeAttr.getTextContent())) {
                                            scaleMode = FlaCs4Writer.SCALEMODE_HORIZONTAL;
                                        }
                                        if ("vertical".equals(scaleModeAttr.getTextContent())) {
                                            scaleMode = FlaCs4Writer.SCALEMODE_VERTICAL;
                                        }
                                    }

                                    Node capsAttr = strokeStyleVal.getAttributes().getNamedItem("caps");
                                    if (capsAttr != null) {
                                        if ("none".equals(capsAttr.getTextContent())) {
                                            caps = FlaCs4Writer.CAPSTYLE_NONE;
                                        }
                                        if ("square".equals(capsAttr.getTextContent())) {
                                            caps = FlaCs4Writer.CAPSTYLE_SQUARE;
                                        }
                                    }

                                    Node jointsAttr = strokeStyleVal.getAttributes().getNamedItem("joints");
                                    if (jointsAttr != null) {
                                        if ("bevel".equals(jointsAttr.getTextContent())) {
                                            joints = FlaCs4Writer.JOINSTYLE_BEVEL;
                                        }
                                        if ("miter".equals(jointsAttr.getTextContent())) {
                                            joints = FlaCs4Writer.JOINSTYLE_MITER;
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

    private void generatePageFile(Element domTimeLine, OutputStream os) throws SAXException, IOException, ParserConfigurationException {
        FlaCs4Writer fg = new FlaCs4Writer(os);

        List<String> definedClasses = new ArrayList<>();

        fg.write(0x01);
        useClass("CPicPage", 5, fg, definedClasses);
        fg.write(0x00);

        int nextLayerId = 1;
        int nextFolderId = 1;
        Reference<Integer> copiedComponentPathRef = new Reference<>(0);
        Reference<Integer> totalFramesCountRef = new Reference<>(0);

        Node layersNode = getSubElementByName(domTimeLine, "layers");
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
        fg.writePageFooter(nextLayerId, nextFolderId, 0);
    }

    public void generatePageFile(Element domTimeline, File outputFile) throws FileNotFoundException, IOException, SAXException, ParserConfigurationException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            generatePageFile(domTimeline, fos);
        }
    }

}
