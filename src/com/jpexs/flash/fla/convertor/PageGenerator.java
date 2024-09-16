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
import com.jpexs.flash.fla.convertor.streams.OutputStorageInterface;
import com.jpexs.helpers.Reference;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

/**
 *
 * @author JPEXS
 */
public class PageGenerator extends AbstractGenerator {

    //https://stackoverflow.com/questions/4077200/whats-the-meaning-of-the-non-numerical-values-in-the-xfls-edge-definition
    private static final Pattern CUBICS_PATTERN = Pattern.compile("^!(?<mx>[0-9]+) +(?<my>[0-9]+) *\\(((?<pBCPx>[0-9]+) *, *(?<pBCPy>[0-9]+))? *; *(?<x1>[0-9]+),(?<y1>[0-9]+) +(?<x2>[0-9]+),(?<y2>[0-9]+) +(?<ex>[0-9]+),(?<ey>[0-9]+) *(?<xy>([QqPp]? *[0-9]+ +[0-9]+)+) *\\)((?<nBCPx>[0-9]+) *, *(?<nBCPy>[0-9]+))? *; *$");
    private static final Pattern CUBICS_XY_PATTERN = Pattern.compile("(?<letter>[QqPp]?) *(?<x>[0-9]+) +(?<y>[0-9]+)");

    public PageGenerator(FlaFormatVersion flaFormatVersion) {
        super(flaFormatVersion);
    }

    /*protected void useClass(String className, FlaWriter os, Map<String, Integer> definedClasses,            Reference<Integer> totalObjectCount) throws IOException {
        if (definedClasses.contains(className)) {
            os.write(1 + 2 * definedClasses.indexOf(className));
            os.write(0x80);
        } else {
            os.write(0xFF, 0xFF, 0x01, 0x00);
            os.writeAsciiOnlyString(className);
            definedClasses.add(className);
        }
    }*/
    private void handleFill(Node fillStyleVal, FlaWriter fg) throws IOException {
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

                int spreadMethod = FlaWriter.FLOW_EXTEND;
                Node spreadMethodAttr = fillStyleVal.getAttributes().getNamedItem("spreadMethod");
                if (spreadMethodAttr != null) {
                    if ("reflect".equals(spreadMethodAttr.getTextContent())) {
                        spreadMethod = FlaWriter.FLOW_REFLECT;
                    }
                    if ("repeat".equals(spreadMethodAttr.getTextContent())) {
                        spreadMethod = FlaWriter.FLOW_REPEAT;
                    }
                }

                int type = FlaWriter.FILLTYPE_LINEAR_GRADIENT;
                if ("RadialGradient".equals(fillStyleVal.getNodeName())) {
                    type = FlaWriter.FILLTYPE_RADIAL_GRADIENT;
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
                                        Node bitmapIsClippedAttr = fillStyleVal.getAttributes().getNamedItem("bitmapIsClipped");
                                        if (bitmapIsClippedAttr != null) {
                                            bitmapIsClipped = "true".equals(bitmapIsClippedAttr.getTextContent());
                                        }

                                        boolean allowSmoothing = "true".equals(e.getAttribute("allowSmoothing"));

                                        int type;
                                        if (allowSmoothing) {
                                            if (bitmapIsClipped) {
                                                type = FlaWriter.FILLTYPE_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaWriter.FILLTYPE_BITMAP;
                                            }
                                        } else {
                                            if (bitmapIsClipped) {
                                                type = FlaWriter.FILLTYPE_NON_SMOOTHED_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaWriter.FILLTYPE_NON_SMOOTHED_BITMAP;
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

    /*
    private void handleParentLayer(
            Element layer,
            FlaWriter fg,
            Map<Integer, Integer> layerIndexToRevLayerIndex,
            boolean isEmpty,
            Map<String, Integer> definedClasses, Reference<Integer> totalObjectCount,
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
        writeLayerContents(layer, fg, definedClasses, totalObjectCount, copiedComponentPathRef, totalFramesCountRef);

        int reverseParentLayerIndex = parentLayerIndex == -1 ? -1 : layerIndexToRevLayerIndex.get(parentLayerIndex);
        fg.writeLayerEnd2(reverseParentLayerIndex, open, autoNamed, 0);
        if (!isEmpty) {
            fg.write(0x01);
            fg.write(0x01);
            fg.write(0x00);
        }
    }*/
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

    protected void handleVideoInstance(Element videoInstance,
            FlaWriter fg,
            Map<String, Integer> definedClasses,
            Reference<Integer> totalObjectCount) throws IOException {
        useClass("CPicVideoStream", fg, definedClasses, totalObjectCount);
        fg.write(flaFormatVersion.getVideoStreamVersion());
        instanceHeader(videoInstance, fg, flaFormatVersion.getVideoType(), true);

        long frameLeft = 0;
        if (videoInstance.hasAttribute("frameLeft")) {
            frameLeft = Long.parseLong(videoInstance.getAttribute("frameLeft"));
        }
        long frameRight = 0;
        if (videoInstance.hasAttribute("frameRight")) {
            frameRight = Long.parseLong(videoInstance.getAttribute("frameRight"));
        }
        long frameTop = 0;
        if (videoInstance.hasAttribute("frameTop")) {
            frameTop = Long.parseLong(videoInstance.getAttribute("frameTop"));
        }
        long frameBottom = 0;
        if (videoInstance.hasAttribute("frameBottom")) {
            frameBottom = Long.parseLong(videoInstance.getAttribute("frameBottom"));
        }

        String name = "";
        if (videoInstance.hasAttribute("name")) {
            name = videoInstance.getAttribute("name");
        }

        String libraryItemName = videoInstance.getAttribute("libraryItemName");

        Element mediaElement = getSubElementByName(videoInstance.getOwnerDocument().getDocumentElement(), "media");
        if (mediaElement == null) {
            return;
        }

        List<Element> mediaItems = getAllSubElements(mediaElement);
        int videoId = 0;
        for (int i = 0; i < mediaItems.size(); i++) {
            Element mediaItem = mediaItems.get(i);
            if ("DOMVideoItem".equals(mediaItem.getTagName()) && mediaItem.hasAttribute("name")) {
                if (libraryItemName.equals(mediaItem.getAttribute("name"))) {
                    videoId = i + 1;
                    break;
                }
            }
        }

        fg.writeUI32(frameLeft);
        fg.writeUI32(frameRight);
        fg.writeUI32(frameTop);
        fg.writeUI32(frameBottom);
        fg.write(0x00);
        fg.writeBomString("");
        fg.writeBomString(name);
        fg.write(0x01, 0x00, 0x00, 0x00);
        fg.writeUI16(videoId);
    }

    protected void handleBitmapInstance(Element bitmapInstance,
            FlaWriter fg,
            Map<String, Integer> definedClasses, Reference<Integer> totalObjectCount
    ) throws IOException {
        if (!bitmapInstance.hasAttribute("libraryItemName")) {
            return;
        }

        String libraryItemName = bitmapInstance.getAttribute("libraryItemName");

        Element mediaElement = getSubElementByName(bitmapInstance.getOwnerDocument().getDocumentElement(), "media");
        if (mediaElement == null) {
            return;
        }
        List<Element> mediaItems = getAllSubElements(mediaElement);
        int bitmapId = 0;
        for (int i = 0; i < mediaItems.size(); i++) {
            Element mediaItem = mediaItems.get(i);
            if ("DOMBitmapItem".equals(mediaItem.getTagName()) && mediaItem.hasAttribute("name")) {
                if (libraryItemName.equals(mediaItem.getAttribute("name"))) {
                    bitmapId = i + 1;
                    break;
                }
            }
        }

        if (bitmapId == 0) {
            return;
        }

        useClass("CPicBitmap", fg, definedClasses, totalObjectCount);
        fg.write(flaFormatVersion.getBitmapVersion());
        instanceHeader(bitmapInstance, fg, flaFormatVersion.getBitmapType(), true);
        fg.writeUI16(bitmapId);

        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            fg.write(0x00);
        }
    }

    protected void handleGroup(Element element,
            FlaWriter fg,
            Map<String, Integer> definedClasses,
            Reference<Integer> totalObjectCount,
            Reference<Integer> copiedComponentPathRef,
            boolean motionTweenEnd
    ) throws IOException {
        Element membersElement = getSubElementByName(element, "members");
        if (membersElement != null) {
            List<Element> members = getAllSubElements(membersElement);
            useClass("CPicShape", fg, definedClasses, totalObjectCount);
            fg.write(flaFormatVersion.getGroupVersion());
            boolean selected = false;
            if (element.hasAttribute("selected")) {
                selected = "true".equals(element.getAttribute("selected"));
            }
            boolean locked = false;
            if (element.hasAttribute("locked")) {
                locked = "true".equals(element.getAttribute("locked"));
            }
            fg.write((selected ? 0x02 : 0x00) + (locked ? 0x04 : 0x00));
            handleElements(members, fg, definedClasses, totalObjectCount, copiedComponentPathRef, motionTweenEnd);
        }
    }

    protected void handleElements(List<Element> elements,
            FlaWriter fg,
            Map<String, Integer> definedClasses, Reference<Integer> totalObjectCount,
            Reference<Integer> copiedComponentPathRef,
            boolean motionTweenEnd
    ) throws IOException {
        for (int instanceIndex = 0; instanceIndex < elements.size(); instanceIndex++) {
            Element element = elements.get(instanceIndex);
            switch (element.getTagName()) {
                case "DOMSymbolInstance":
                    handleSymbolInstance(element, fg, definedClasses, totalObjectCount, copiedComponentPathRef, motionTweenEnd);
                    break;
                case "DOMBitmapInstance":
                    handleBitmapInstance(element, fg, definedClasses, totalObjectCount);
                    break;
                case "DOMVideoInstance":
                    handleVideoInstance(element, fg, definedClasses, totalObjectCount);
                    break;
                case "DOMStaticText":
                case "DOMDynamicText":
                case "DOMInputText":
                    handleText(element, fg, definedClasses, totalObjectCount);
                    break;
                case "DOMTLFText":
                    Logger.getLogger(PageGenerator.class.getName()).warning("DOMTLFText element is not supported");
                    break;
                case "DOMGroup":
                    handleGroup(element, fg, definedClasses, totalObjectCount, copiedComponentPathRef, motionTweenEnd);
                    break;
            }
        }

        boolean hasShape = false;
        for (int e = 0; e < elements.size(); e++) {
            Element element = elements.get(e);
            if ("DOMShape".equals(element.getNodeName())) {
                handleShape(element, fg, false, definedClasses, totalObjectCount);
                hasShape = true;
                break;
            }
        }

        if (!hasShape) {
            instanceHeader(null, fg, flaFormatVersion.getShapeType(), false);
            fg.write(0x05);
            fg.writeUI32(0); //totalEdgeCount
            fg.write(0x00, 0x00); //fillStyleCount
            fg.write(0x00, 0x00); //strokeStyleCount
            fg.write(0x00); //??
            fg.writeUI32(0); //totalCubicsCount                                    
        }
    }

    protected List<FilterInterface> parseFilters(Element filtersElement) {
        List<FilterInterface> filterList = new ArrayList<>();
        if (filtersElement == null) {
            return filterList;
        }
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
                            /*case "inner":
                                type = BevelFilter.TYPE_INNER;
                                break;*/
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
                    int type = "GradientGlowFilter".equals(filter.getNodeName()) ? GradientGlowFilter.TYPE_INNER : GradientBevelFilter.TYPE_INNER;

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
                            /*case "inner":
                                type = GradientGlowFilter.TYPE_INNER;
                                break;*/
                            case "outer":
                                type = GradientGlowFilter.TYPE_OUTER;
                                break;
                            case "full":
                                type = GradientGlowFilter.TYPE_FULL;
                                break;
                        }
                    }
                    List<GradientEntry> gradientEntries = parseGradientEntries(filter);

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
        return filterList;
    }

    protected void handleSymbolInstance(
            Element symbolInstance,
            FlaWriter fg,
            Map<String, Integer> definedClasses, Reference<Integer> totalObjectCount,
            Reference<Integer> copiedComponentPathRef,
            boolean motionTweenEnd
    ) throws IOException {

        if (!symbolInstance.hasAttribute("libraryItemName")) {
            //nothing we can do
            return;
        }

        String libraryItemName = symbolInstance.getAttribute("libraryItemName");
        List<Element> includes = getSymbols(symbolInstance.getOwnerDocument().getDocumentElement());

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

        int symbolType = FlaWriter.SYMBOLTYPE_MOVIE_CLIP;

        if (symbolInstance.hasAttribute("symbolType")) {
            switch (symbolInstance.getAttribute("symbolType")) {
                /*case "movie clip":
                    symbolType = FlaWriter.SYMBOLTYPE_MOVIE_CLIP;
                    break;*/
                case "button":
                    symbolType = FlaWriter.SYMBOLTYPE_BUTTON;
                    break;
                case "graphic":
                    symbolType = FlaWriter.SYMBOLTYPE_GRAPHIC;
                    break;
            }
        }

        boolean trackAsMenu = false;
        int loop = FlaWriter.LOOPMODE_LOOP;
        int firstFrame = 0; //zero-based

        if (symbolType == FlaWriter.SYMBOLTYPE_BUTTON) {
            useClass("CPicButton", fg, definedClasses, totalObjectCount);
            if (symbolInstance.hasAttribute("trackAsMenu")) {
                trackAsMenu = "true".equals(symbolInstance.getAttribute("trackAsMenu"));
            }
        } else if (symbolType == FlaWriter.SYMBOLTYPE_GRAPHIC) {
            useClass("CPicSymbol", fg, definedClasses, totalObjectCount);

            if (symbolInstance.hasAttribute("loop")) {
                switch (symbolInstance.getAttribute("loop")) {
                    case "loop":
                        loop = FlaWriter.LOOPMODE_LOOP;
                        break;
                    case "play once":
                        loop = FlaWriter.LOOPMODE_PLAY_ONCE;
                        break;
                    case "single frame":
                        loop = FlaWriter.LOOPMODE_SINGLE_FRAME;
                        break;
                }
            }
            if (symbolInstance.hasAttribute("firstFrame")) {
                firstFrame = Integer.parseInt(symbolInstance.getAttribute("firstFrame"));
            }
        } else {
            useClass("CPicSprite", fg, definedClasses, totalObjectCount);
        }

        String instanceName = "";
        if (symbolType != FlaWriter.SYMBOLTYPE_GRAPHIC && symbolInstance.hasAttribute("name")) {
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

        double centerPoint3DZ = 0;
        if (symbolInstance.hasAttribute("centerPoint3DZ")) {
            centerPoint3DZ = Double.parseDouble(symbolInstance.getAttribute("centerPoint3DZ"));
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

        List<FilterInterface> filters = parseFilters(getSubElementByName(symbolInstance, "filters"));

        String actionScript = "";

        Element actionscriptElement = getSubElementByName(symbolInstance, "Actionscript");
        if (actionscriptElement != null) {
            Element scriptElement = getSubElementByName(actionscriptElement, "script");
            if (scriptElement != null) {
                actionScript = scriptElement.getTextContent();
            }
        }

        if (symbolType == FlaWriter.SYMBOLTYPE_MOVIE_CLIP) {
            copiedComponentPathRef.setVal(copiedComponentPathRef.getVal() + 1);
        }

        int symbolInstanceId = fg.generateRandomId();

        long centerPoint3DXLong = Math.round(centerPoint3DX * 20);
        long centerPoint3DYLong = Math.round(centerPoint3DY * 20);
        long centerPoint3DZLong = Math.round(centerPoint3DZ * 20);

        fg.write(flaFormatVersion.getSpriteVersion());
        instanceHeader(symbolInstance, fg, flaFormatVersion.getSymbolType(), true);

        fg.write((firstFrame & 0xFF), ((firstFrame >> 8) & 0xFF));
        switch (symbolType) {
            case FlaWriter.SYMBOLTYPE_MOVIE_CLIP:
                fg.write(0x02);
                break;
            case FlaWriter.SYMBOLTYPE_BUTTON:
                fg.write(0x00);
                break;
            case FlaWriter.SYMBOLTYPE_GRAPHIC:
                switch (loop) {
                    case FlaWriter.LOOPMODE_LOOP:
                        fg.write(0x00);
                        break;
                    case FlaWriter.LOOPMODE_PLAY_ONCE:
                        fg.write(0x01);
                        break;
                    case FlaWriter.LOOPMODE_SINGLE_FRAME:
                        fg.write(0x02);
                        break;
                }
                break;
        }

        fg.write(0x00, 0x01);

        int redMultiplier = colorEffect.getRedMultiplier();
        int greenMultiplier = colorEffect.getGreenMultiplier();
        int blueMultiplier = colorEffect.getBlueMultiplier();
        int alphaMultiplier = colorEffect.getAlphaMultiplier();
        int redOffset = colorEffect.getRedOffset();
        int greenOffset = colorEffect.getGreenOffset();
        int blueOffset = colorEffect.getBlueOffset();
        int alphaOffset = colorEffect.getAlphaOffset();
        Color effectColor = colorEffect.getValueColor();

        fg.write(
                (alphaMultiplier & 0xFF), ((alphaMultiplier >> 8) & 0xFF), (alphaOffset & 0xFF), ((alphaOffset >> 8) & 0xFF),
                (redMultiplier & 0xFF), ((redMultiplier >> 8) & 0xFF), (redOffset & 0xFF), ((redOffset >> 8) & 0xFF),
                (greenMultiplier & 0xFF), ((greenMultiplier >> 8) & 0xFF), (greenOffset & 0xFF), ((greenOffset >> 8) & 0xFF),
                (blueMultiplier & 0xFF), ((blueMultiplier >> 8) & 0xFF), (blueOffset & 0xFF), ((blueOffset >> 8) & 0xFF),
                colorEffect.getType(), 0x00);
        fg.writeUI16(colorEffect.getValuePercent());
        fg.write(effectColor.getRed(), effectColor.getGreen(), effectColor.getBlue(), effectColor.getAlpha()
        );

        fg.writeBomString("");
        fg.write(debugRandom ? 'X' : libraryItemIndex, 0x00, 0x00, 0x00 //FIXME? this is probably a long val                
        );

        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            fg.write(0x00, 0x00, 0x00);
        }
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            if (!filters.isEmpty()) {
                fg.write(0x01,
                        filters.size(), 0x00, 0x00, 0x00);

                for (FilterInterface filter : filters) {
                    filter.write(fg);
                }
            } else {
                fg.write(0x00);
            }

            fg.write(
                    blendMode,
                    0x00, //??
                    0x00);
        }

        if (flaFormatVersion == FlaFormatVersion.CS4) {
            float[] matrix3D = new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
            };
            if (symbolInstance.hasAttribute("matrix3D")) {
                String matrix3DStr = symbolInstance.getAttribute("matrix3D");
                String[] matrixParts = matrix3DStr.trim().split(" ", -1);
                if (matrixParts.length != 16) {
                    Logger.getLogger(PageGenerator.class.getName()).warning("matrix3D attribute has incorrect number of parts");
                } else {
                    for (int i = 0; i < 16; i++) {
                        matrix3D[i] = Float.parseFloat(matrixParts[i]);
                    }
                }
            }

            for (int i = 0; i < 16; i++) {
                fg.writeFloat(matrix3D[i]);
            }

            double rotationX = 0;
            if (symbolInstance.hasAttribute("rotationX")) {
                rotationX = Double.parseDouble(symbolInstance.getAttribute("rotationX"));
            }
            double rotationY = 0;
            if (symbolInstance.hasAttribute("rotationY")) {
                rotationY = Double.parseDouble(symbolInstance.getAttribute("rotationY"));
            }
            double rotationZ = 0;
            if (symbolInstance.hasAttribute("rotationZ")) {
                rotationZ = Double.parseDouble(symbolInstance.getAttribute("rotationZ"));
            }

            fg.writeDouble(rotationX);
            fg.writeDouble(rotationY);
            fg.writeDouble(rotationZ);

            if (symbolType != FlaWriter.SYMBOLTYPE_MOVIE_CLIP) {
                fg.write(
                        0x00, 0x00, 0x00, 0x80,
                        0x00, 0x00, 0x00, 0x80);
            } else {
                fg.write(
                        (int) (centerPoint3DXLong & 0xFF), (int) ((centerPoint3DXLong >> 8) & 0xFF), (int) ((centerPoint3DXLong >> 16) & 0xFF), (int) ((centerPoint3DXLong >> 24) & 0xFF),
                        (int) (centerPoint3DYLong & 0xFF), (int) ((centerPoint3DYLong >> 8) & 0xFF), (int) ((centerPoint3DYLong >> 16) & 0xFF), (int) ((centerPoint3DYLong >> 24) & 0xFF)
                );
            }

            fg.write(
                    (int) (centerPoint3DZLong & 0xFF), (int) ((centerPoint3DZLong >> 8) & 0xFF), (int) ((centerPoint3DZLong >> 16) & 0xFF), (int) ((centerPoint3DZLong >> 24) & 0xFF)
            );

            fg.write(0x00, 0x00);
        }

        if (symbolType == FlaWriter.SYMBOLTYPE_GRAPHIC) {
            return;
        }

        fg.write((symbolType == FlaWriter.SYMBOLTYPE_BUTTON ? flaFormatVersion.getButtonVersion() : flaFormatVersion.getSpriteVersionG()),
                flaFormatVersion.getSpriteVersionB(),
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00,
                (symbolInstanceId & 0xFF), ((symbolInstanceId >> 8) & 0xFF));
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
            fg.write(0x00, 0x00, 0x00, 0x00);
        }
        fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        fg.writeBomString(actionScript);
        if (symbolType == FlaWriter.SYMBOLTYPE_BUTTON) {
            fg.write((int) (trackAsMenu ? 1 : 0));
        }
        fg.writeBomString(instanceName);

        if (symbolType == FlaWriter.SYMBOLTYPE_BUTTON) {
            fg.write(0x00, 0x00, 0x00, 0x00);
            return;
        }
        fg.write(0x02, 0x00, 0x00, 0x00, 0x00,
                flaFormatVersion == FlaFormatVersion.CS4 ? 0x01 : (motionTweenEnd || !actionScript.isEmpty() ? 1 : 0), //?? magic
                0x00,
                0x00,
                0x00);
        writeAccessibleData(fg, symbolInstance, false);
        fg.write(0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00
        );
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            fg.write(0x01, //?
                    0x00 /*something, but it resets after resaving FLA*/, 0x00, 0x00, 0x00);
            String componentTxt = "<component metaDataFetched='true' schemaUrl='' schemaOperation='' sceneRootLabel='Scene 1' oldCopiedComponentPath='" + copiedComponentPathRef.getVal() + "'>\n</component>\n";
            fg.writeBomString(componentTxt);
        }
    }

    private void instanceHeader(Element element, FlaWriter fg, int instanceType, boolean isInstance) throws IOException {

        Matrix placeMatrix = parseMatrix(getSubElementByName(element, "matrix"));

        boolean selected = false;
        if (element != null && element.hasAttribute("selected")) {
            selected = "true".equals(element.getAttribute("selected"));
        }

        boolean locked = false;
        if (element != null && element.hasAttribute("locked")) {
            locked = "true".equals(element.getAttribute("locked"));
        }

        boolean cacheAsBitmap = false;
        if (element != null && "DOMSymbolInstance".equals(element.getTagName()) && element.hasAttribute("cacheAsBitmap")) {
            cacheAsBitmap = "true".equals(element.getAttribute("cacheAsBitmap"));
        }

        Double transformationPointX = null;
        Double transformationPointY = null;
        Element transformationPointElement = getSubElementByName(element, "transformationPoint");
        if (transformationPointElement != null) {
            Element pointElement = getSubElementByName(transformationPointElement, "Point");
            if (pointElement.hasAttribute("x")) {
                transformationPointX = Double.valueOf(pointElement.getAttribute("x"));
            }
            if (pointElement.hasAttribute("y")) {
                transformationPointY = Double.valueOf(pointElement.getAttribute("y"));
            }
        }

        if (isInstance) {
            fg.write((selected ? 0x02 : 0x00) + (locked ? 0x04 : 0x00));
        }
        fg.write(0x00, 0x00);
        if (transformationPointX == null) {
            fg.write(0x00, 0x00, 0x00, 0x80);
            fg.write(0x00, 0x00, 0x00, 0x80);
        } else {
            Point2D transformationPoint = new Point2D.Double(transformationPointX, transformationPointY);
            Point2D transformationPointTransformed = placeMatrix.transform(transformationPoint);

            long tptX = Math.round(transformationPointTransformed.getX() * 20);
            long tptY = Math.round(transformationPointTransformed.getY() * 20);

            if (debugRandom) {
                //There are rounding errors:
                //Sample: round((2.11505126953125 * 15.95 -0.880615234375 * 32.05 + 339.4) * 20) should be 6899, but is 6898            
                fg.write('X', 'X', 'X', 'X');
                fg.write('X', 'X', 'X', 'X');
            } else {
                fg.write(
                        (int) (tptX & 0xFF), (int) ((tptX >> 8) & 0xFF), (int) ((tptX >> 16) & 0xFF), (int) ((tptX >> 24) & 0xFF),
                        (int) (tptY & 0xFF), (int) ((tptY >> 8) & 0xFF), (int) ((tptY >> 16) & 0xFF), (int) ((tptY >> 24) & 0xFF)
                );
            }
        }
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            fg.write(0x00, cacheAsBitmap ? 1 : 0);
        }
        fg.write(instanceType);
        fg.writeMatrix(placeMatrix);
    }

    static int textCount = 0;

    private void handleText(Element element, FlaWriter fg, Map<String, Integer> definedClasses, Reference<Integer> totalObjectCount) throws IOException {
        if ("DOMStaticText".equals(element.getTagName())
                || "DOMDynamicText".equals(element.getTagName())
                || "DOMInputText".equals(element.getTagName())) {
            useClass("CPicText", fg, definedClasses, totalObjectCount);

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
                        /*case "single line":
                            multiline = false;
                            wrap = false;
                            break;*/
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

            if (!isInput && element.hasAttribute("isSelectable")) {
                isSelectable = !"false".equals(element.getAttribute("isSelectable"));
            }

            boolean vertical = false;
            boolean rightToLeft = false;
            if (!isInput && element.hasAttribute("orientation")) {
                switch (element.getAttribute("orientation")) {
                    /*case "horizontal":
                        vertical = false;
                        rightToLeft = false;
                        break;*/
                    case "vertical right to left":
                        vertical = true;
                        rightToLeft = true;
                        break;
                    case "vertical left to right":
                        vertical = true;
                        break;
                }
            }
            boolean scrollable = false;
            if (element.hasAttribute("scrollable")) {
                scrollable = "true".equals(element.getAttribute("scrollable"));
            }

            //orientation="vertical right to left", "vertical left to right"
            //fontRenderingMode="device" , "bitmap", "standard", "customThicknessSharpness"
            fg.write(flaFormatVersion.getTextVersionC());
            instanceHeader(element, fg, flaFormatVersion.getTextVersion(), true);
            fg.writeUI32((int) Math.round(left * 20));
            fg.writeUI32((int) Math.round((left + width) * 20));
            fg.writeUI32((int) Math.round(top * 20));
            fg.writeUI32((int) Math.round((top + height) * 20));

            boolean autoExpand = false;
            if (element.hasAttribute("autoExpand")) {
                autoExpand = "true".equals(element.getAttribute("autoExpand"));
            }

            fg.write(
                    autoExpand ? 0x01 : 0, 0x00
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

            int textFlags = (!isStatic ? 0x01 : 0)
                    + (isDynamic ? 0x02 : 0)
                    + (password ? 0x04 : 0)
                    + (border ? 0x40 : 0)
                    + (wrap ? 0x08 : 0)
                    + (multiline ? 0x10 : 0);

            //Only single font per text (?)
            if (flaFormatVersion.ordinal() < FlaFormatVersion.MX2004.ordinal()) {
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
                                    if (domFontItem.hasAttribute("embeddedCharacters")) {
                                        textFlags |= 0x20;
                                    }
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }

            }

            fg.write(textFlags);

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

                                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                                    embedFlag |= 1;
                                } else {
                                    if (domFontItem.hasAttribute("linkageExportForAS") 
                                            && "true".equals(domFontItem.getAttribute("linkageExportForAS"))) {
                                        embedFlag |= 1;
                                    }
                                }
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
                int flags = 0;
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()
                        && fontRenderingMode == FONTRENDERING_DEVICE) {
                    flags |= 0x02;
                }
                if (isSelectable) {
                    flags |= 0x01;
                }
                fg.write(flags);
            }
            fg.write(0x00);
            fg.writeUI16(maxCharacters);
            fg.writeBomString(variableName);
            if (!embeddedCharacters.isEmpty()) {
                fg.writeBomString(embeddedCharacters);
            }
            if (flaFormatVersion == FlaFormatVersion.CS4) {
                fg.write(0x00);
            }

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
            List<FilterInterface> filters = parseFilters(getSubElementByName(element, "filters"));

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

                Color fillColor = parseColorWithAlpha(domTextAttrs, Color.black, "fillColor", "alpha");
                float size = 12f;

                if (domTextAttrs.hasAttribute("size")) {
                    size = Float.parseFloat(domTextAttrs.getAttribute("size"));
                }

                int bitmapSize = (int) Math.round(size * 20);
                float lineSpacing = 2f;

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

                boolean rotation = false;
                if (domTextAttrs.hasAttribute("rotation")) {
                    rotation = "true".equals(domTextAttrs.getAttribute("rotation"));
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
                fg.write(flaFormatVersion.getTextVersionB());
                fg.writeUI16(bitmapSize);
                if (flaFormatVersion == FlaFormatVersion.CS4) {
                    fg.writeBomString(fontFamily);
                } else {
                    fg.writeString(fontFamily);
                }

                if (flaFormatVersion == FlaFormatVersion.CS4) {
                    fg.writeBomString(face);
                    fg.write(0x00, 0x00, 0x00, 0x40);
                }
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
                fg.write(debugRandom ? 'U' : 0x12,
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
                if (flaFormatVersion == FlaFormatVersion.CS4) {
                    fg.writeBomString(url);
                } else {
                    fg.writeString(url);
                }

                fg.write(vertical ? 1 : 0);
                fg.write(rightToLeft ? 1 : 0);
                fg.write(rotation ? 1 : 0);

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                    fg.write(fontRenderingMode == FONTRENDERING_BITMAP ? 1 : 0);
                }
                if (flaFormatVersion == FlaFormatVersion.CS4) {
                    fg.writeBomString(target);
                } else {
                    fg.writeString(target);
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
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
                    if (flaFormatVersion == FlaFormatVersion.CS4
                            || fontRenderingMode != FONTRENDERING_DEVICE) {
                        fg.writeFloat(antiAliasThickness);
                        fg.writeFloat(antiAliasSharpness);
                    } else {
                        fg.write(0, 0, 0, 0);
                        fg.write(0, 0, 0, 0);
                    }
                    if (flaFormatVersion == FlaFormatVersion.CS4) {
                        fg.writeBomString(url);
                    } else {
                        fg.writeString(url);
                    }
                }
                if (flaFormatVersion.isUnicode()) {
                    fg.write(characters.getBytes("UTF-16LE"));
                } else {
                    fg.write(characters.getBytes());
                }
            }

            fg.write(0x00, 0x00);
            fg.writeBomString(instanceName);
            writeAccessibleData(fg, element, false);
            fg.write(0x00, 0x00, 0x00, 0x00,
                    scrollable ? 1 : 0,
                    0x00, 0x00, 0x00
            );
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                fg.writeBomString("");
                fg.writeBomString(String.join("|", allEmbedRanges));
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                if (!filters.isEmpty()) {
                    fg.write(0x01);
                    fg.writeUI32(filters.size()); //Is it really 4 bytes long?
                    for (FilterInterface filter : filters) {
                        filter.write(fg);
                    }
                } else {
                    fg.write(0x00);
                }
                fg.write(0x00, 0x00);
            }
        }
    }

    private void writeMorphStrokeStylePart(FlaWriter fg, Element strokeStyleElement) throws IOException {
        if (strokeStyleElement == null) {
            fg.write(0x00, 0x00, 0x00, 0x00);
            fg.write(0x00, 0x00, 0x00, 0x00);
            fg.writeUI16(0);
            return;
        }
        Element element = getFirstSubElement(strokeStyleElement);
        Element fill = getSubElementByName(element, "fill");
        Element fillElement = getFirstSubElement(fill);
        if ("SolidColor".equals(fillElement.getTagName())) {
            Color color = parseColorWithAlpha(fillElement);
            fg.write(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        } else {
            fg.write(0x00, 0x00, 0x00, debugRandom ? 'U' : 0x00);
        }
        float weight = 1f;
        if (element.hasAttribute("weight")) {
            weight = Float.parseFloat(element.getAttribute("weight"));
        }
        fg.writeUI32(Math.round(weight * 20));
        fg.writeUI16(0);
    }

    private void writeMorphFillStylePart(FlaWriter fg, Element fillStyleElement) throws IOException {
        Element element = getFirstSubElement(fillStyleElement);
        switch (element.getTagName()) {
            case "SolidColor": {
                Color color = parseColorWithAlpha(element);
                fg.write(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                fg.writeUI16(0);
            }
            break;
            case "LinearGradient":
            case "RadialGradient": {
                Matrix matrix = parseMatrix(getSubElementByName(element, "matrix"));
                List<Element> gradientEntries = getAllSubElementsByName(element, "GradientEntry");
                fg.write(0x00, 0x00, 0x00, debugRandom ? 'U' : 0x00);
                if ("LinearGradient".equals(element.getTagName())) {
                    fg.writeUI16(FlaWriter.FILLTYPE_LINEAR_GRADIENT);
                } else {
                    fg.writeUI16(FlaWriter.FILLTYPE_RADIAL_GRADIENT);
                }
                fg.writeMatrix(matrix);
                fg.write(gradientEntries.size());
                for (Element gradEntry : gradientEntries) {
                    Color color = parseColorWithAlpha(gradEntry);
                    float ratio = 0f;
                    if (gradEntry.hasAttribute("ratio")) {
                        ratio = Float.parseFloat(gradEntry.getAttribute("ratio"));
                    }
                    fg.write(Math.round(ratio * 255));
                    fg.write(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                }
            }
            break;
            case "BitmapFill": {
                Node bitmapPathAttr = element.getAttributes().getNamedItem("bitmapPath");
                if (bitmapPathAttr != null) {
                    String bitmapPath = bitmapPathAttr.getTextContent(); //assuming attribute set
                    Node mediaNode = getSubElementByName(element.getOwnerDocument().getDocumentElement(), "media");
                    if (mediaNode != null) {
                        List<Element> mediaElements = getAllSubElements(mediaNode);
                        int mediaId = 0;
                        for (Element e : mediaElements) {
                            mediaId++;
                            if ("DOMBitmapItem".equals(e.getNodeName())) {
                                String name = e.getAttribute("name");
                                if (name != null) {
                                    if (bitmapPath.equals(name)) {
                                        Matrix bitmapMatrix = parseMatrix(getSubElementByName(element, "matrix"));

                                        boolean bitmapIsClipped = false;
                                        Node bitmapIsClippedAttr = element.getAttributes().getNamedItem("bitmapIsClipped");
                                        if (bitmapIsClippedAttr != null) {
                                            bitmapIsClipped = "true".equals(bitmapIsClippedAttr.getTextContent());
                                        }

                                        boolean allowSmoothing = "true".equals(e.getAttribute("allowSmoothing"));

                                        int type;
                                        if (allowSmoothing) {
                                            if (bitmapIsClipped) {
                                                type = FlaWriter.FILLTYPE_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaWriter.FILLTYPE_BITMAP;
                                            }
                                        } else {
                                            if (bitmapIsClipped) {
                                                type = FlaWriter.FILLTYPE_NON_SMOOTHED_CLIPPED_BITMAP;
                                            } else {
                                                type = FlaWriter.FILLTYPE_NON_SMOOTHED_BITMAP;
                                            }
                                        }

                                        fg.write(0xFF, 0x00, 0x00, 0xFF);
                                        fg.write(type, 0x00);
                                        fg.writeMatrix(bitmapMatrix);
                                        fg.writeUI16(mediaId);
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

    private void handleShape(Element element, FlaWriter fg, boolean inGroup, Map<String, Integer> definedClasses, Reference<Integer> totalObjectCount) throws IOException {
        instanceHeader(element, fg, flaFormatVersion.getShapeType(), false);
        fg.write(0x05);
        Node fillsNode = getSubElementByName(element, "fills");
        List<Element> fillStyles = new ArrayList<>();
        if (fillsNode != null) {
            fillStyles = getAllSubElementsByName(fillsNode, "FillStyle");
        }

        Comparator<Node> indexComparator = new Comparator<Node>() {
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

        for (Element edge : edges) {
            if (edge.hasAttribute("edges")) {
                totalEdgeCount += FlaWriter.getEdgesCount(edge.getAttribute("edges"));
            }
        }

        fg.writeUI32(totalEdgeCount);
        fg.write(fillStyles.size(), 0x00);
        for (Node fillStyle : fillStyles) {
            Node fillStyleVal = getFirstSubElement(fillStyle);
            handleFill(fillStyleVal, fg);
        }
        fg.write(strokeStyles.size(), 0x00);
        for (Node strokeStyle : strokeStyles) {
            Node strokeStyleVal = getFirstSubElement(strokeStyle);
            int scaleMode = FlaWriter.SCALEMODE_NONE;

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

            int joints = FlaWriter.JOINSTYLE_ROUND;
            int caps = FlaWriter.CAPSTYLE_ROUND;
            boolean pixelHinting = false;
            Node pixelHintingAttr = strokeStyleVal.getAttributes().getNamedItem("pixelHinting");
            if (pixelHintingAttr != null) {
                if ("true".equals(pixelHintingAttr.getTextContent())) {
                    pixelHinting = true;
                }
            }

            Node scaleModeAttr = strokeStyleVal.getAttributes().getNamedItem("scaleMode");
            if (scaleModeAttr != null) {
                if ("normal".equals(scaleModeAttr.getTextContent())) {
                    scaleMode = FlaWriter.SCALEMODE_NORMAL;
                }
                if ("horizontal".equals(scaleModeAttr.getTextContent())) {
                    scaleMode = FlaWriter.SCALEMODE_HORIZONTAL;
                }
                if ("vertical".equals(scaleModeAttr.getTextContent())) {
                    scaleMode = FlaWriter.SCALEMODE_VERTICAL;
                }
            }

            switch (strokeStyleVal.getNodeName()) {
                case "SolidStroke":
                    styleParam1 = 0;
                    styleParam2 = 0;

                    Node capsAttr = strokeStyleVal.getAttributes().getNamedItem("caps");
                    if (capsAttr != null) {
                        if ("none".equals(capsAttr.getTextContent())) {
                            caps = FlaWriter.CAPSTYLE_NONE;
                        }
                        if ("square".equals(capsAttr.getTextContent())) {
                            caps = FlaWriter.CAPSTYLE_SQUARE;
                        }
                    }

                    Node jointsAttr = strokeStyleVal.getAttributes().getNamedItem("joints");
                    if (jointsAttr != null) {
                        if ("bevel".equals(jointsAttr.getTextContent())) {
                            joints = FlaWriter.JOINSTYLE_BEVEL;
                        }
                        if ("miter".equals(jointsAttr.getTextContent())) {
                            joints = FlaWriter.JOINSTYLE_MITER;
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
                Color baseColor = new Color(0x00, 0x00, 0x00, debugRandom ? 'U' : 0x00);
                if ("SolidColor".equals(fillStyleVal.getNodeName())) {
                    baseColor = parseColorWithAlpha(fillStyleVal);
                } else if ("BitmapFill".equals(fillStyleVal.getNodeName())) {
                    baseColor = Color.red;
                }

                fg.writeStrokeBegin(baseColor, weight, pixelHinting, scaleMode, caps, joints, miterLimit, styleParam1, styleParam2);
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                    handleFill(fillStyleVal, fg);
                }
            }
        }
        fg.beginShape();
        for (Element edge : edges) {
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

        fg.write(0x00); //?

        int totalCubicsCount = 0;

        for (Element edge : edges) {
            if (edge.hasAttribute("cubics")) {
                totalCubicsCount++;
            }
        }

        fg.writeUI32(totalCubicsCount);
        for (Element edge : edges) {
            if (edge.hasAttribute("cubics")) {
                String cubics = edge.getAttribute("cubics");
                Matcher cubicsMatcher = CUBICS_PATTERN.matcher(cubics);
                if (!cubicsMatcher.matches()) {
                    Logger.getLogger(PageGenerator.class.getName()).warning("Cubics pattern does not match for input string " + cubics);
                    continue;
                }
                int mx = Integer.parseInt(cubicsMatcher.group("mx"));
                int my = Integer.parseInt(cubicsMatcher.group("my"));
                int x1 = Integer.parseInt(cubicsMatcher.group("x1"));
                int y1 = Integer.parseInt(cubicsMatcher.group("y1"));
                int x2 = Integer.parseInt(cubicsMatcher.group("x2"));
                int y2 = Integer.parseInt(cubicsMatcher.group("y2"));
                int ex = Integer.parseInt(cubicsMatcher.group("ex"));
                int ey = Integer.parseInt(cubicsMatcher.group("ey"));

                Integer pBCPx = null;
                if (cubicsMatcher.group("pBCPx") != null) {
                    pBCPx = Integer.parseInt(cubicsMatcher.group("pBCPx"));
                }
                Integer pBCPy = null;
                if (cubicsMatcher.group("pBCPy") != null) {
                    pBCPy = Integer.parseInt(cubicsMatcher.group("pBCPy"));
                }
                Integer nBCPx = null;
                if (cubicsMatcher.group("nBCPx") != null) {
                    nBCPx = Integer.parseInt(cubicsMatcher.group("nBCPx"));
                }
                Integer nBCPy = null;
                if (cubicsMatcher.group("nBCPy") != null) {
                    nBCPy = Integer.parseInt(cubicsMatcher.group("nBCPy"));
                }

                String xy = cubicsMatcher.group("xy");
                Matcher m2 = CUBICS_XY_PATTERN.matcher(xy);
                List<String> letterList = new ArrayList<>();
                List<Integer> xList = new ArrayList<>();
                List<Integer> yList = new ArrayList<>();
                String lastLetter = "q";
                while (m2.find()) {
                    xList.add(Integer.parseInt(m2.group("x")));
                    yList.add(Integer.parseInt(m2.group("y")));
                    String letter = m2.group("letter");
                    if (letter == null || letter.isEmpty()) {
                        letter = lastLetter;
                    }
                    lastLetter = letter;
                    letterList.add(letter);
                }

                fg.writeUI32(mx);
                fg.writeUI32(my);
                fg.writeUI32(x1);
                fg.writeUI32(y1);
                fg.writeUI32(x2);
                fg.writeUI32(y2);
                fg.writeUI32(ex);
                fg.writeUI32(ey);

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                    fg.write(letterList.size());
                    for (int i = 0; i < letterList.size(); i++) {
                        fg.writeUI32(xList.get(i));
                        fg.writeUI32(yList.get(i));
                        switch (letterList.get(i)) {
                            case "Q":
                                fg.write(0x01, 0x00);
                                break;
                            case "q":
                                fg.write(0x00, 0x00);
                                break;
                            case "P":
                                fg.write(0x01, 0x01);
                                break;
                            case "p":
                                fg.write(0x00, 0x01);
                                break;
                        }
                    }

                    int pnFlags = 0;
                    if (pBCPx != null) {
                        pnFlags |= 1;
                    }
                    if (nBCPx != null) {
                        pnFlags |= 2;
                    }
                    fg.write(pnFlags);
                    if (pBCPx != null) {
                        fg.writeUI32(pBCPx);
                        fg.writeUI32(pBCPy);
                    }
                    if (nBCPx != null) {
                        fg.writeUI32(nBCPx);
                        fg.writeUI32(nBCPy);
                    }
                }
            }
        }
    }

    private void writeLayerContents(
            Element layer,
            FlaWriter fg,
            Map<String, Integer> definedClasses, Reference<Integer> totalObjectCount,
            Reference<Integer> copiedComponentPathRef,
            Reference<Integer> totalFramesCountRef
    ) throws IOException {
        useClass("CPicLayer", fg, definedClasses, totalObjectCount);
        fg.write(flaFormatVersion.getLayerVersion());
        fg.write(0x00);

        int layerType = FlaWriter.LAYERTYPE_LAYER;
        if (layer.hasAttribute("layerType")) {
            String layerTypeStr = layer.getAttribute("layerType");
            switch (layerTypeStr) {
                case "folder":
                    layerType = FlaWriter.LAYERTYPE_FOLDER;
                    break;
                case "mask":
                    layerType = FlaWriter.LAYERTYPE_MASK;
                    break;
                case "guide":
                    layerType = FlaWriter.LAYERTYPE_GUIDE;
                    break;
            }
        }

        Node framesNode = getSubElementByName(layer, "frames");
        if (framesNode == null) {
        } else {
            List<Element> frames = getAllSubElementsByName(framesNode, "DOMFrame");
            String prevTweenType = "";
            for (int f = 0; f < frames.size(); f++) {
                useClass("CPicFrame", fg, definedClasses, totalObjectCount);
                fg.write(flaFormatVersion.getFrameVersion());
                fg.write(0x00);
                totalFramesCountRef.setVal(totalFramesCountRef.getVal() + 1);
                Element frame = frames.get(f);
                Element elementsNode = getSubElementByName(frame, "elements");

                String tweenType = frame.getAttribute("tweenType");

                List<Element> elements = new ArrayList<>();
                if (elementsNode != null) {
                    elements = getAllSubElements(elementsNode);
                }

                handleElements(elements, fg, definedClasses, totalObjectCount, copiedComponentPathRef, prevTweenType.equals("motion"));

                prevTweenType = tweenType;

                int keyMode = FlaWriter.KEYMODE_STANDARD;
                if (frame.hasAttribute("keyMode")) {
                    keyMode = Integer.parseInt(frame.getAttribute("keyMode"));
                }

                if (flaFormatVersion.ordinal() <= FlaFormatVersion.F8.ordinal()) {
                    keyMode = keyMode & ~0x2000;
                }

                int duration = 1;
                if (frame.hasAttribute("duration")) {
                    duration = Integer.parseInt(frame.getAttribute("duration"));
                }

                String actionScript = "";

                Element actionscriptElement = getSubElementByName(frame, "Actionscript");
                if (actionscriptElement != null) {
                    Element scriptElement = getSubElementByName(actionscriptElement, "script");
                    if (scriptElement != null) {
                        actionScript = scriptElement.getTextContent();
                        if (!actionScript.isEmpty() 
                                && flaFormatVersion.ordinal() <= FlaFormatVersion.MX.ordinal()
                                && ! actionScript.endsWith("\n")
                                ) {
                            actionScript += "\n";
                        }
                    }
                }

                int acceleration = 0;
                if (frame.hasAttribute("acceleration")) {
                    acceleration = Integer.parseInt(frame.getAttribute("acceleration"));
                }
                String name = "";
                if (frame.hasAttribute("name")) {
                    name = frame.getAttribute("name");
                }
                boolean comment = false;
                boolean anchor = false;
                if (frame.hasAttribute("labelType")) {
                    switch (frame.getAttribute("labelType")) {
                        case "comment":
                            comment = true;
                            break;
                        case "anchor":
                            anchor = true;
                            break;
                    }
                }
                int motionTweenRotate = 0;
                if (frame.hasAttribute("motionTweenRotate")) {
                    switch (frame.getAttribute("motionTweenRotate")) {
                        case "clockwise":
                            motionTweenRotate = 1;
                            break;
                        case "counter-clockwise":
                            motionTweenRotate = 2;
                            break;
                        case "none":
                            break;
                        case "auto":
                            break;
                    }
                }
                int motionTweenRotateTimes = 0;
                if (motionTweenRotate != 0 && frame.hasAttribute("motionTweenRotateTimes")) {
                    motionTweenRotateTimes = Integer.parseInt(frame.getAttribute("motionTweenRotateTimes"));
                }

                //motionTweenOrientToPath, motionTweenScale, motionTweenSnap, motionTweenSync
                //and also motionTweenRotate=none/auto
                //atributes are part of the keymode
                int frameId = fg.generateRandomId();

                fg.write(flaFormatVersion.getFrameVersionB(), duration,
                        0x00);

                /*
        KEYMODES:
        
        normal keymode:
        0x2600

        classic tween keymode:
        0x4001 +
                0x0100	motionTweenOrientToPath = true
                0x0200	motionTweenScale = true
                0x0400	motionTweenRotate <> none
                0x0800	motionTweenSync = true
                0x1000	motionTweenSnap = true
        (default: only motionTweenSnap = true)

        shape tween keymode:
        0x5602

        motion tween keymode:
        0x2003 +
                0x0800	motionTweenSync "Sync graphic symbols"
        
                 */
                fg.writeUI16(keyMode);
                fg.writeUI16(acceleration);

                int soundId = 0;
                if (frame.hasAttribute("soundName")) {
                    String soundName = frame.getAttribute("soundName");

                    Element mediaEl = getSubElementByName(frame.getOwnerDocument().getDocumentElement(), "media");
                    if (mediaEl != null) {
                        List<Element> media = getAllSubElements(mediaEl);
                        for (int i = 0; i < media.size(); i++) {
                            Element mediaItem = media.get(i);
                            if (mediaItem.hasAttribute("name")) {
                                String mediaName = mediaItem.getAttribute("name");
                                if (soundName.equals(mediaName)) {
                                    soundId = i + 1;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (soundId > 0) {
                    fg.writeUI16(soundId);

                    Element soundEnvelope = getSubElementByName(frame, "SoundEnvelope");
                    if (soundEnvelope == null) {
                        fg.writeUI16(1);
                        fg.writeUI32(0);
                        fg.writeUI16(0x8000);
                        fg.writeUI16(0x8000);
                    } else {
                        List<Element> soundEnvelopePoints = getAllSubElementsByName(soundEnvelope, "SoundEnvelopePoint");
                        fg.writeUI16(soundEnvelopePoints.size());
                        for (Element soundEnvelopePoint : soundEnvelopePoints) {
                            long mark44 = 0;
                            if (soundEnvelopePoint.hasAttribute("mark44")) {
                                mark44 = Long.parseLong(soundEnvelopePoint.getAttribute("mark44"));
                            }
                            int level0 = 0;
                            if (soundEnvelopePoint.hasAttribute("level0")) {
                                level0 = Integer.parseInt(soundEnvelopePoint.getAttribute("level0"));
                            }
                            int level1 = 0;
                            if (soundEnvelopePoint.hasAttribute("level1")) {
                                level1 = Integer.parseInt(soundEnvelopePoint.getAttribute("level1"));
                            }
                            fg.writeUI32(mark44);
                            fg.writeUI16(level0);
                            fg.writeUI16(level1);
                        }
                    }
                } else {
                    fg.write(0x00, 0x00, 0x00, 0x00);
                }
                long inPoint44 = 0;
                if (soundId > 0 && frame.hasAttribute("inPoint44")) {
                    inPoint44 = Long.parseLong(frame.getAttribute("inPoint44"));
                }
                long outPoint44 = 0x3FFFFFFF;
                if (soundId > 0 && frame.hasAttribute("outPoint44")) {
                    outPoint44 = Long.parseLong(frame.getAttribute("outPoint44"));
                }
                int soundZoomLevel = -1;
                if (frame.hasAttribute("soundZoomLevel")) {
                    soundZoomLevel = Integer.parseInt(frame.getAttribute("soundZoomLevel"));
                }

                int soundSync = 0;
                if (frame.hasAttribute("soundSync")) {
                    switch (frame.getAttribute("soundSync")) {
                        case "start":
                            soundSync = 1;
                            break;
                        case "stop":
                            soundSync = 2;
                            break;
                        case "stream":
                            soundSync = 3;
                            break;
                    }
                }

                int soundLoop = 1;
                if (frame.hasAttribute("soundLoop")) {
                    soundLoop = Integer.parseInt(frame.getAttribute("soundLoop"));
                }

                boolean loop = false;
                if (frame.hasAttribute("soundLoopMode")) {
                    loop = "loop".equals(frame.getAttribute("soundLoopMode"));
                }
                if (loop) {
                    soundLoop = 32767;
                }

                fg.writeUI16(soundLoop);
                fg.write(soundSync);
                fg.writeUI32(inPoint44);
                fg.writeUI32(outPoint44);
                fg.writeUI16(soundZoomLevel);

                fg.writeBomString(name);
                fg.write(flaFormatVersion.getFrameVersionC(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                        0x00, ((frameId >> 8) & 0xFF), (frameId & 0xFF));
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                    fg.write(0x00, 0x00, 0x00, 0x00);
                }
                fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                fg.writeBomString(actionScript);
                fg.write(motionTweenRotate, 0x00, 0x00, 0x00);
                fg.writeUI16(motionTweenRotateTimes);
                fg.write(0x00, 0x00,
                        comment ? 1 : 0, 0x00, 0x00, 0x00
                );

                Element morphShape = getSubElementByName(frame, "MorphShape");
                if (morphShape == null) {
                    fg.write(0x00, 0x00);
                } else {
                    useClass("CPicMorphShape", fg, definedClasses, totalObjectCount);

                    fg.write(
                            0x02, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x00);

                    
                    List<Element> fillStyles1 = new ArrayList<>();
                    List<Element> fillStyles2 = new ArrayList<>();
                    List<Element> strokeStyles1 = new ArrayList<>();
                    List<Element> strokeStyles2 = new ArrayList<>();
                    if (f + 1 < frames.size()) {
                        Element nextFrame = frames.get(f + 1);
                        Element domShape1 = getSubElementByName(elementsNode, "DOMShape");
                        Element nextFrameElementsNode = getSubElementByName(nextFrame, "elements");
                        if (domShape1 != null && nextFrameElementsNode != null) {
                            Element domShape2 = getSubElementByName(nextFrameElementsNode, "DOMShape");
                            if (domShape2 != null) {
                                Element fills1 = getSubElementByName(domShape1, "fills");
                                Element fills2 = getSubElementByName(domShape2, "fills");
                                Element strokes1 = getSubElementByName(domShape1, "strokes");
                                Element strokes2 = getSubElementByName(domShape2, "strokes");

                                if (fills1 != null) {
                                    fillStyles1 = getAllSubElementsByName(fills1, "FillStyle");
                                }
                                if (fills2 != null) {
                                    fillStyles2 = getAllSubElementsByName(fills2, "FillStyle");
                                }

                                if (strokes1 != null) {
                                    strokeStyles1 = getAllSubElementsByName(strokes1, "StrokeStyle");
                                }
                                if (strokes2 != null) {
                                    strokeStyles2 = getAllSubElementsByName(strokes2, "StrokeStyle");
                                }
                            }
                        }
                    }
                    
                    Map<Integer, Integer> fillIndex1Map = new HashMap<>();
                    Map<Integer, Integer> fillIndex2Map = new HashMap<>();
                    Map<Integer, Integer> strokeIndex1Map = new HashMap<>();
                    Map<Integer, Integer> strokeIndex2Map = new HashMap<>();
                    
                    List<Element> fills = new ArrayList<>();
                    List<Element> bothFills = new ArrayList<>();                    
                    List<Element> strokes = new ArrayList<>();
                    List<Element> bothStrokes = new ArrayList<>();

                    
                        
                    if (!fillStyles1.isEmpty()) {
                       
                        int fillStyleIndex = 0;
                        int maxNumFills = Math.max(fillStyles1.size(), fillStyles2.size());
                        for (int i = 0; i < maxNumFills; i++) {
                            Element fill1 = fillStyles1.size() > i ? fillStyles1.get(i) : null;
                            Element fill2 = fillStyles2.size() > i ? fillStyles2.get(i) : null;                            
                            bothFills.add(fill1);
                            bothFills.add(fill2);
                            fills.add(fill1);      
                            fillIndex1Map.put(fills.indexOf(fill1), bothFills.indexOf(fill1));
                            
                            if (!areElementsEqual(fill1, fill2) || flaFormatVersion.ordinal() <= FlaFormatVersion.MX.ordinal()) {
                                fills.add(fill2);   
                                fillIndex2Map.put(fills.indexOf(fill2), bothFills.indexOf(fill2));  
                            } else {
                                fillIndex2Map.put(fills.indexOf(fill1), bothFills.indexOf(fill2));  
                            }
                            
                            
                                                     
                        }
                    }
                    if (strokeStyles1.isEmpty() && strokeStyles2.isEmpty()) {
                        strokeIndex1Map.put(0, 0);
                        strokeIndex2Map.put(0, 1);
                    } else {
                        int maxNumStrokes = Math.max(strokeStyles1.size(), strokeStyles2.size());
                        for (int i = 0; i < maxNumStrokes; i++) {
                            Element stroke1 = strokeStyles1.size() > i ? strokeStyles1.get(i) : null;
                            Element stroke2 = strokeStyles2.size() > i ? strokeStyles2.get(i) : null;
                            strokes.add(stroke1);
                            bothStrokes.add(stroke1);
                            bothStrokes.add(stroke2);
                            strokeIndex1Map.put(strokes.indexOf(stroke1), bothStrokes.indexOf(stroke1));
                            
                            if (!areElementsEqual(stroke1, stroke2) || flaFormatVersion.ordinal() <= FlaFormatVersion.MX.ordinal()) {
                                strokes.add(stroke2);
                                strokeIndex2Map.put(strokes.indexOf(stroke2), bothStrokes.indexOf(stroke2));  
                            } else {
                                strokeIndex2Map.put(strokes.indexOf(stroke1), bothStrokes.indexOf(stroke2));  
                            }                            
                        }                        
                    }
                    
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                        for (int key: fillIndex1Map.keySet()) {
                            fillIndex1Map.put(key, key);
                        }
                        for (int key: fillIndex2Map.keySet()) {
                            fillIndex2Map.put(key, key);
                        }
                        for (int key: strokeIndex1Map.keySet()) {
                            strokeIndex1Map.put(key, key);
                        }
                        for (int key: strokeIndex2Map.keySet()) {
                            strokeIndex2Map.put(key, key);
                        }
                    }
                    
                    Element morphSegmentsElement = getSubElementByName(morphShape, "morphSegments");
                    List<Element> morphSegments = getAllSubElementsByName(morphSegmentsElement, "MorphSegment");
                    fg.writeUI16(morphSegments.size());
                    for (Element morphSegment : morphSegments) {
                        useClass("CMorphSegment", fg, definedClasses, totalObjectCount);

                        Point2D startpointA = fg.parsePoint(morphSegment.getAttribute("startPointA"));
                        Point2D startpointB = fg.parsePoint(morphSegment.getAttribute("startPointB"));
                        int strokeIndex1 = -1;
                        if (morphSegment.hasAttribute("strokeIndex1")) {
                            strokeIndex1 = Integer.parseInt(morphSegment.getAttribute("strokeIndex1"));
                        }
                        int strokeIndex2 = -1;
                        if (morphSegment.hasAttribute("strokeIndex2")) {
                            strokeIndex2 = Integer.parseInt(morphSegment.getAttribute("strokeIndex2"));
                        }
                        int fillIndex1 = -1;
                        if (morphSegment.hasAttribute("fillIndex1")) {
                            fillIndex1 = Integer.parseInt(morphSegment.getAttribute("fillIndex1"));
                        }
                        int fillIndex2 = -1;
                        if (morphSegment.hasAttribute("fillIndex2")) {
                            fillIndex2 = Integer.parseInt(morphSegment.getAttribute("fillIndex2"));
                        }

                        
                        /*if (strokeIndex1 != -1) {
                            strokeIndex1 = strokeIndex1Map.get(strokeIndex1);
                        }
                        if (strokeIndex2 != -1) {
                            strokeIndex2 = strokeIndex2Map.get(strokeIndex2);
                        }
                        if (fillIndex1 != -1) {
                            fillIndex1 = fillIndex1Map.get(fillIndex1);
                        }
                        if (fillIndex2 != -1) {                            
                            fillIndex2 = fillIndex2Map.get(fillIndex2);
                        }*/
                        
                        fg.writeUI32(strokeIndex1);
                        fg.writeUI32(strokeIndex2);
                        fg.writeUI32(fillIndex1);
                        fg.writeUI32(fillIndex2);
                        fg.writePoint(startpointA);
                        fg.writePoint(startpointB);

                        List<Element> morphCurvesList = getAllSubElementsByName(morphSegment, "MorphCurves");
                        fg.writeUI16(morphCurvesList.size());
                        for (Element morphCurves : morphCurvesList) {
                            useClass("CMorphCurve", fg, definedClasses, totalObjectCount);
                            Point2D controlPointA = fg.parsePoint(morphCurves.getAttribute("controlPointA"));
                            Point2D anchorPointA = fg.parsePoint(morphCurves.getAttribute("anchorPointA"));
                            Point2D controlPointB = fg.parsePoint(morphCurves.getAttribute("controlPointB"));
                            Point2D anchorPointB = fg.parsePoint(morphCurves.getAttribute("anchorPointB"));
                            boolean isLine = false;
                            if (morphCurves.hasAttribute("isLine")) {
                                isLine = "true".equals(morphCurves.getAttribute("isLine"));
                            }
                            fg.writePoint(controlPointA);
                            fg.writePoint(anchorPointA);
                            fg.writePoint(controlPointB);
                            fg.writePoint(anchorPointB);
                            fg.write(isLine ? 1 : 0);
                            fg.write(0x00, 0x00, 0x00);
                        }
                    }
                

                    /*fg.write(0x04, 0x00, 0x00, 0x66, 0xFF, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0x00, 0x00, 0x66,
                            0x66, 0xFF, 0x00, 0x00, 0xFF, 0xFF, 0x00, 0xFF, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00);*/
                    fg.writeUI16(0);
                    

                    if (fillStyles1.size() == fillStyles2.size()
                            && (strokeStyles1.size() == strokeStyles2.size() || strokeStyles2.isEmpty() || strokeStyles1.isEmpty())) {
                        if (!fillStyles1.isEmpty()) {
                            fg.writeUI16(fills.size());
                            for (Element fill : fills) {
                                writeMorphFillStylePart(fg, fill);
                            }
                        }
                        if (strokeStyles1.isEmpty() && strokeStyles2.isEmpty()) {                            
                            if (flaFormatVersion.ordinal() <= FlaFormatVersion.MX.ordinal()) {
                                fg.writeUI16(2);                            
                                
                                fg.writeUI32(0);
                                fg.writeUI32(0);
                                fg.writeUI16(0);
                                
                                fg.writeUI32(0);
                                fg.writeUI32(0);
                                fg.writeUI16(0);
                            } else {
                                fg.writeUI16(1);
                                fg.writeUI32(0);
                                fg.writeUI32(0);
                                fg.writeUI16(0);
                            }
                        } else {
                            fg.writeUI16(strokes.size());
                            for (Element stroke : strokes) {
                                writeMorphStrokeStylePart(fg, stroke);
                            }
                        }
                    }
                }

                    int shapeTweenBlend = 0;
                    if (frame.hasAttribute("shapeTweenBlend")) {
                        switch (frame.getAttribute("shapeTweenBlend")) {
                            /*case "distributive":
                            shapeTweenBlend = 0;
                            break;*/
                            case "angular":
                                shapeTweenBlend = 1;
                                break;
                        }
                    }
                    fg.write(shapeTweenBlend);

                    boolean useSingleEaseCurve = true;
                    if (frame.hasAttribute("useSingleEaseCurve")) {
                        useSingleEaseCurve = !"false".equals(frame.getAttribute("useSingleEaseCurve"));
                    }

                    int soundEffect = getAttributeAsInt(frame, "soundEffect",
                            Arrays.asList(
                                    "none",
                                    "left channel",
                                    "right channel",
                                    "fade left to right",
                                    "fade right to left",
                                    "fade in",
                                    "fade out",
                                    "custom"
                            ), "none");

                    fg.write(
                            0x00, 0x00, 0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.write(0x01, 0x00, 0x00, 0x00,
                            soundEffect,
                            0x00, 0x00,
                            0x00,
                            anchor ? 1 : 0,
                            0x00, 0x00, 0x00
                    );

                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                        fg.write(useSingleEaseCurve ? 1 : 0, 0x00, 0x00, 0x00);

                        boolean hasCustomEase = false;
                        if (frame.hasAttribute("hasCustomEase")) {
                            hasCustomEase = "true".equals(frame.getAttribute("hasCustomEase"));
                        }

                        fg.writeUI32(hasCustomEase ? 1 : 0);
                        if (hasCustomEase) {
                            Element tweensElement = getSubElementByName(frame, "tweens");
                            List<String> properties = Arrays.asList("position", "rotation", "scale", "color", "filters", "all");

                            if (tweensElement == null) {
                                for (int i = 0; i < properties.size(); i++) {
                                    fg.writeUI32(0);
                                }
                            } else {
                                List<Element> customEaseElements = getAllSubElementsByName(tweensElement, "CustomEase");
                                Map<String, Element> targetToCustomEase = new HashMap<>();
                                for (Element el : customEaseElements) {
                                    if (!el.hasAttribute("target")) {
                                        continue;
                                    }
                                    targetToCustomEase.put(el.getAttribute("target"), el);
                                }

                                for (String property : properties) {
                                    if (!targetToCustomEase.containsKey(property)) {
                                        fg.writeUI32(0);
                                        continue;
                                    }

                                    List<Element> points = getAllSubElementsByName(targetToCustomEase.get(property), "Point");

                                    int numPoints = 2 + (points.size() - 4) / 3;
                                    fg.writeUI32(numPoints);
                                    for (int p = 0; p < points.size(); p++) {
                                        Element point = points.get(p);
                                        double x = 0;
                                        if (point.hasAttribute("x")) {
                                            x = Double.parseDouble(point.getAttribute("x"));
                                        }
                                        double y = 0;
                                        if (point.hasAttribute("y")) {
                                            y = Double.parseDouble(point.getAttribute("y"));
                                        }
                                        if (x < 0) {
                                            x = 0;
                                        }
                                        if (y < 0) {
                                            y = 0;
                                        }

                                        if (p == 0 || p == points.size() - 1) {
                                            if (debugRandom) {
                                                //ignore rounding errors
                                                fg.write('X', 'X', 'X', 'X', 'X', 'X', 'X', 'X');
                                                fg.write('X', 'X', 'X', 'X', 'X', 'X', 'X', 'X');
                                            } else {
                                                fg.writeDouble(x);
                                                fg.writeDouble(y);
                                            }
                                        }
                                        if (debugRandom) {
                                            //ignore rounding errors
                                            fg.write('X', 'X', 'X', 'X', 'X', 'X', 'X', 'X');
                                            fg.write('X', 'X', 'X', 'X', 'X', 'X', 'X', 'X');
                                        } else {
                                            fg.writeDouble(x);
                                            fg.writeDouble(y);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Element motionObjectXML = getSubElementByName(frame, "motionObjectXML");
                    if (motionObjectXML != null) {
                        if (flaFormatVersion == FlaFormatVersion.CS4) {
                            fg.writeBomString(getInnerXml(motionObjectXML));
                            long visibleAnimationKeyframes = 0x1FFFFF;
                            if (frame.hasAttribute("visibleAnimationKeyframes")) {
                                visibleAnimationKeyframes = Long.parseLong(frame.getAttribute("visibleAnimationKeyframes"));
                            }
                            fg.writeUI32(visibleAnimationKeyframes);
                            String tweenInstanceName = "";
                            if (frame.hasAttribute("tweenInstanceName")) {
                                tweenInstanceName = frame.getAttribute("tweenInstanceName");
                            }
                            fg.writeBomString(tweenInstanceName);
                        } else {
                            Logger.getLogger(PageGenerator.class.getName()).warning("Motion objects are not supported in Flash lower than CS4");
                        }
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

                fg.write(0x00, 0x00,
                        0x00, 0x00, 0x00, 0x80,
                        0x00, 0x00, 0x00, 0x80);
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                    fg.write(0x00, 0x00);
                }

                fg.write(flaFormatVersion.getLayerVersionB());

                fg.writeBomString(layerName);
                fg.write(isSelected ? 1 : 0);
                fg.write(hiddenLayer ? 1 : 0);
                fg.write(lockedLayer ? 1 : 0);
                fg.write(0xFF, 0xFF, 0xFF, 0xFF);
                fg.write(color.getRed());
                fg.write(color.getGreen());
                fg.write(color.getBlue());
                fg.write(0xFF);
                fg.write(showOutlines ? 1 : 0);
                fg.write(0x00, 0x00, 0x00, heightMultiplier, 0x00, 0x00, 0x00);
                fg.write(layerType);
            }
        }

    

    private static boolean areElementsEqual(Element elem1, Element elem2) {
        if (elem1 == elem2) {
            return true;
        }
        if (elem1 == null || elem2 == null) {
            return false;
        }
        if (!elem1.getTagName().equals(elem2.getTagName())) {
            return false;
        }

        if (!areAttributesEqual(elem1, elem2)) {
            return false;
        }

        NodeList children1 = elem1.getChildNodes();
        NodeList children2 = elem2.getChildNodes();

        if (children1.getLength() != children2.getLength()) {
            return false;
        }

        for (int i = 0; i < children1.getLength(); i++) {
            Node child1 = children1.item(i);
            Node child2 = children2.item(i);

            if (child1.getNodeType() == Node.ELEMENT_NODE && child2.getNodeType() == Node.ELEMENT_NODE) {
                if (!areElementsEqual((Element) child1, (Element) child2)) {
                    return false;
                }
            } else if (!child1.isEqualNode(child2)) {
                return false;
            }
        }

        return true;
    }

    private static boolean areAttributesEqual(Element elem1, Element elem2) {
        NamedNodeMap attributes1 = elem1.getAttributes();
        NamedNodeMap attributes2 = elem2.getAttributes();

        if (attributes1.getLength() != attributes2.getLength()) {
            return false;
        }

        for (int i = 0; i < attributes1.getLength(); i++) {
            Node attr1 = attributes1.item(i);
            Node attr2 = attributes2.getNamedItem(attr1.getNodeName());

            if (attr2 == null || !attr1.getNodeValue().equals(attr2.getNodeValue())) {
                return false;
            }
        }

        return true;
    }

    private static String getInnerXml(Element element) {
        StringBuilder innerXml = new StringBuilder();
        NodeList childNodes = element.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);

            try {
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();

                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.setOutputProperty(OutputKeys.INDENT, "no");

                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(node), new StreamResult(writer));

                innerXml.append(writer.toString());

            } catch (Exception ex) {
                //ignore
            }
        }

        return innerXml.toString();
    }

    private void writeLayer(
            FlaWriter fg,
            List<Element> layers,
            int layerIndex,
            Set<Integer> writtenLayers,
            Map<String, Integer> definedClasses,
            Reference<Integer> totalObjectCount,
            Reference<Integer> copiedComponentPathRef,
            Reference<Integer> totalFramesCountRef,
            Map<Integer, Integer> layerIndexToNValue
    ) throws IOException {
        if (writtenLayers.contains(layerIndex)) {
            return;
        }
        writtenLayers.add(layerIndex);
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
                case "mask":
                case "guide":
                    canHaveSubLayers = true;
                    break;
            }
        }

        boolean open = true;
        if (layer.hasAttribute("open")) {
            open = !"false".equals(layer.getAttribute("open"));
        }

        int parentLayerIndex = -1;
        if (layer.hasAttribute("parentLayerIndex")) {
            parentLayerIndex = Integer.parseInt(layer.getAttribute("parentLayerIndex"));
        }

        int animationType = 0;
        if (layer.hasAttribute("animationType")) {
            switch (layer.getAttribute("animationType")) {
                case "motion object":
                    animationType = 1;
                    break;
                case "IK pose":
                    //?
                    break;
            }
        }

        int nValue = 1 + definedClasses.size() + totalObjectCount.getVal();
        layerIndexToNValue.put(layerIndex, nValue);

        writeLayerContents(layer, fg, definedClasses, totalObjectCount, copiedComponentPathRef, totalFramesCountRef);
        if (parentLayerIndex > -1 && !writtenLayers.contains(parentLayerIndex)) {
            writeLayer(fg, layers, parentLayerIndex, writtenLayers, definedClasses, totalObjectCount, copiedComponentPathRef, totalFramesCountRef, layerIndexToNValue);
        } else {
            if (parentLayerIndex > -1) {
                fg.writeUI16(layerIndexToNValue.get(parentLayerIndex));
            } else {
                fg.writeUI16(0);
            }
        }

        fg.write(open ? 1 : 0);
        fg.write(autoNamed ? 1 : 0);
        if (flaFormatVersion == FlaFormatVersion.CS4) {
            fg.write(animationType);
        }

        if (!canHaveSubLayers) {
            int pi = parentLayerIndex;
            int li = layerIndex;
            while (pi > -1) {
                if (li == pi + 1) {
                    fg.writeUI16(layerIndexToNValue.get(pi));
                } else {
                    break;
                }
                Element player = layers.get(pi);
                li = pi;
                if (player.hasAttribute("parentLayerIndex")) {
                    pi = Integer.parseInt(player.getAttribute("parentLayerIndex"));
                } else {
                    pi = -1;
                }
            }
        }
    }

    public void generatePageFile(Element domTimeLine, OutputStream os) throws SAXException, IOException, ParserConfigurationException {
        FlaWriter fg = new FlaWriter(os, flaFormatVersion);
        fg.setDebugRandom(debugRandom);
        Map<String, Integer> definedClasses = new HashMap<>();
        Reference<Integer> totalObjectCount = new Reference<>(0);

        fg.write(0x01);
        useClass("CPicPage", fg, definedClasses, totalObjectCount);
        fg.write(flaFormatVersion.getPageVersion());
        fg.write(0x00);

        int nextLayerId = 1;
        int nextFolderId = 1;
        Reference<Integer> copiedComponentPathRef = new Reference<>(0);
        Reference<Integer> totalFramesCountRef = new Reference<>(0);

        Node layersNode = getSubElementByName(domTimeLine, "layers");
        if (layersNode != null) {
            List<Element> layers = getAllSubElementsByName(layersNode, "DOMLayer");

            Map<Integer, Integer> layerIndexToNValue = new HashMap<>();

            Set<Integer> writtenLayers = new HashSet<>();

            for (int layerIndex = layers.size() - 1; layerIndex >= 0; layerIndex--) {
                writeLayer(fg, layers, layerIndex, writtenLayers, definedClasses, totalObjectCount, copiedComponentPathRef, totalFramesCountRef, layerIndexToNValue);
            }
        }
        int currentFrame = 0;
        if (domTimeLine.hasAttribute("currentFrame")) {
            currentFrame = Integer.parseInt(domTimeLine.getAttribute("currentFrame"));
        }

        if (debugRandom) {
            nextLayerId = 'X';
            nextFolderId = 'X';
        }

        fg.write(
                0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00,
                0x80);
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            fg.write(0x00, 0x00, 0x07, nextLayerId, 0x00, nextFolderId, 0x00, currentFrame, 0x00, 0x00, 0x00);
        } else {
            switch (flaFormatVersion) {
                case MX2004:
                    fg.write(0x05);
                    if (debugRandom) {
                        fg.write('U', 'U', 'U', 'U');
                    } else {
                        fg.write(0x02, 0x00, 0x01, 0x00);
                    }
                    break;
                case MX:
                    fg.write(0x03);
                    if (debugRandom) {
                        fg.write('U', 'U');
                    } else {
                        fg.write(0x02, 0x00);
                    }
                    break;
            }

        }
        if (domTimeLine.hasAttribute("guides")) {
            String guidesXml = domTimeLine.getAttribute("guides");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            Document guidesDocument = docBuilder.parse(new ByteArrayInputStream(guidesXml.getBytes("UTF-8")));
            Element guidesRoot = guidesDocument.getDocumentElement();
            List<Element> guidelines = getAllSubElementsByName(guidesRoot, "guideline");
            fg.writeUI32(guidelines.size());
            for (Element guideline : guidelines) {
                int direction = 0;
                if (guideline.hasAttribute("direction") && "v".equals(guideline.getAttribute("direction"))) {
                    direction = 1;
                }
                long value = Long.parseLong(guideline.getTextContent()) * 20;
                fg.writeUI32(direction);
                fg.writeUI32(value);
            }
        } else {
            fg.write(0x00, 0x00, 0x00, 0x00);
        }

    }
}
