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

import com.jpexs.flash.fla.extractor.FlaCfbExtractor;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parses CS5 XFL file and produces CS4 page file.
 *
 * @author JPEXS
 */
public class XflToCs4Converter {

    private static Element getFirstSubElement(Node n) {
        NodeList list = n.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if (sn instanceof Element) {
                return (Element) sn;
            }
        }
        return null;
    }

    private static Node getSubNodeByName(Node n, String name) {
        NodeList list = n.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if (name.equals(sn.getNodeName())) {
                return sn;
            }
        }
        return null;
    }

    private static List<Element> getAllSubElements(Node n) {
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

    private static List<Node> getAllSubNodesByName(Node n, String name) {
        NodeList list = n.getChildNodes();
        List<Node> ret = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            Node sn = list.item(i);
            if (name.equals(sn.getNodeName())) {
                ret.add(sn);
            }
        }
        return ret;
    }

    private static Color parseColorWithAlpha(Node node) {
        Color color = Color.BLACK;
        Node colorAttr = node.getAttributes().getNamedItem("color");
        if (colorAttr != null) {
            color = parseColor(colorAttr.getTextContent());
        }
        Node alphaAttr = node.getAttributes().getNamedItem("alpha");
        if (alphaAttr != null) {
            double alphaD = Double.parseDouble(alphaAttr.getTextContent());
            int alpha255 = (int) Math.round(alphaD * 255);
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha255);
        }

        return color;
    }

    private static Color parseColor(String value) {
        Pattern pat = Pattern.compile("^#([a-fA-F0-9]{6})$");
        Matcher m = pat.matcher(value);
        if (m.matches()) {
            int rgb = Integer.parseInt(m.group(1), 16);
            return new Color(rgb);
        }
        throw new IllegalArgumentException("Invalid color");
    }

    private static void handleFill(Node fillStyleVal, FlaCs4Writer fg) throws IOException {
        switch (fillStyleVal.getNodeName()) {
            case "SolidColor":
                Color color = parseColorWithAlpha(fillStyleVal);
                fg.writeSolidFill(color);
                break;
            case "LinearGradient":
            case "RadialGradient": {
                double a = 1;
                double b = 0;
                double c = 0;
                double d = 1;
                double tx = 0;
                double ty = 0;
                double focalPointRatio = 0;
                Node matrix = getSubNodeByName(fillStyleVal, "matrix");
                if (matrix != null) {
                    matrix = getSubNodeByName(matrix, "Matrix");
                }
                Node aAttr = matrix.getAttributes().getNamedItem("a");
                if (aAttr != null) {
                    a = Double.parseDouble(aAttr.getTextContent());
                }
                Node bAttr = matrix.getAttributes().getNamedItem("b");
                if (bAttr != null) {
                    b = Double.parseDouble(bAttr.getTextContent());
                }
                Node cAttr = matrix.getAttributes().getNamedItem("c");
                if (cAttr != null) {
                    c = Double.parseDouble(cAttr.getTextContent());
                }
                Node dAttr = matrix.getAttributes().getNamedItem("d");
                if (dAttr != null) {
                    d = Double.parseDouble(dAttr.getTextContent());
                }

                Node txAttr = matrix.getAttributes().getNamedItem("tx");
                if (txAttr != null) {
                    tx = Double.parseDouble(txAttr.getTextContent());
                }

                Node tyAttr = matrix.getAttributes().getNamedItem("ty");
                if (tyAttr != null) {
                    ty = Double.parseDouble(tyAttr.getTextContent());
                }

                if ("RadialGradient".equals(fillStyleVal.getNodeName())) {
                    Node focalPointRatioAttr = fillStyleVal.getAttributes().getNamedItem("focalPointRatio");
                    if (focalPointRatioAttr != null) {
                        focalPointRatio = Double.parseDouble(focalPointRatioAttr.getTextContent());
                    }
                }

                List<Node> gradientEntries = getAllSubNodesByName(fillStyleVal, "GradientEntry");
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
                fg.writeGradientFill(colors, ratios, type, linearRGB, spreadMethod, a, b, c, d, tx, ty, focalPointRatio);
            }
            break;
            case "BitmapFill": {
                Node bitmapPathAttr = fillStyleVal.getAttributes().getNamedItem("bitmapPath");
                if (bitmapPathAttr != null) {
                    String bitmapPath = bitmapPathAttr.getTextContent(); //assuming attribute set
                    Node mediaNode = getSubNodeByName(fillStyleVal.getOwnerDocument().getDocumentElement(), "media");
                    if (mediaNode != null) {
                        List<Element> mediaElements = getAllSubElements(mediaNode);
                        int mediaId = 0;
                        for (Element e : mediaElements) {
                            mediaId++;
                            if ("DOMBitmapItem".equals(e.getNodeName())) {
                                String name = e.getAttribute("name");
                                if (name != null) {
                                    if (bitmapPath.equals(name)) {

                                        double a = 1;
                                        double b = 0;
                                        double c = 0;
                                        double d = 1;
                                        double tx = 0;
                                        double ty = 0;
                                        
                                        Node matrix = getSubNodeByName(fillStyleVal, "matrix");
                                        if (matrix != null) {
                                            matrix = getSubNodeByName(matrix, "Matrix");
                                        }
                                        Node aAttr = matrix.getAttributes().getNamedItem("a");
                                        if (aAttr != null) {
                                            a = Double.parseDouble(aAttr.getTextContent());
                                        }
                                        Node bAttr = matrix.getAttributes().getNamedItem("b");
                                        if (bAttr != null) {
                                            b = Double.parseDouble(bAttr.getTextContent());
                                        }
                                        Node cAttr = matrix.getAttributes().getNamedItem("c");
                                        if (cAttr != null) {
                                            c = Double.parseDouble(cAttr.getTextContent());
                                        }
                                        Node dAttr = matrix.getAttributes().getNamedItem("d");
                                        if (dAttr != null) {
                                            d = Double.parseDouble(dAttr.getTextContent());
                                        }

                                        Node txAttr = matrix.getAttributes().getNamedItem("tx");
                                        if (txAttr != null) {
                                            tx = Double.parseDouble(txAttr.getTextContent());
                                        }

                                        Node tyAttr = matrix.getAttributes().getNamedItem("ty");
                                        if (tyAttr != null) {
                                            ty = Double.parseDouble(tyAttr.getTextContent());
                                        }
                                        
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
                                        
                                        fg.writeBitmapFill(type, a, b, c, d, tx, ty, mediaId);
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

    private static int getStrokeTypeParameter(Node node, String attributeName, List<String> allowedValues, String defaultValue) {
        Node attr = node.getAttributes().getNamedItem(attributeName);
        if (attr != null) {
            int index = allowedValues.indexOf(attr.getTextContent());
            if (index > -1) {
                return index;
            }
        }
        return allowedValues.indexOf(defaultValue);
    }

    private static void handleFolder(Node folder, FlaCs4Writer fg, Map<Integer, Integer> layerIndexToRevLayerIndex, boolean isEmpty) throws IOException {
        String folderName = "Folder";
        Node layerNameAttr = folder.getAttributes().getNamedItem("name");
        if (layerNameAttr != null) {
            folderName = layerNameAttr.getTextContent();
        }

        Node outlineAttr = folder.getAttributes().getNamedItem("outline");
        Node useOutlineViewAttr = folder.getAttributes().getNamedItem("useOutlineView");

        boolean showOutlines = false;
        if (outlineAttr != null && useOutlineViewAttr != null) {
            showOutlines = "true".equals(outlineAttr.getTextContent())
                    && "true".equals(useOutlineViewAttr.getTextContent());
        }

        Color color = Color.BLACK;
        Node colorAttr = folder.getAttributes().getNamedItem("color");
        if (colorAttr != null) {
            color = parseColor(colorAttr.getTextContent());
        }

        boolean hiddenLayer = false;
        Node visibleAttr = folder.getAttributes().getNamedItem("visible");
        if (visibleAttr != null) {
            hiddenLayer = "false".equals(visibleAttr.getTextContent());
        }

        boolean lockedLayer = false;
        Node lockedAttr = folder.getAttributes().getNamedItem("locked");
        if (lockedAttr != null) {
            lockedLayer = "true".equals(lockedAttr.getTextContent());
        }

        boolean isSelected = false; //Note: how is this different from "current" attribute
        Node isSelectedAttr = folder.getAttributes().getNamedItem("isSelected");
        if (isSelectedAttr != null) {
            isSelected = "true".equals(isSelectedAttr.getTextContent());
        }

        boolean open = true;
        Node openAttr = folder.getAttributes().getNamedItem("open");
        if (openAttr != null) {
            open = !"false".equals(openAttr.getTextContent());
        }

        int folderParentLayerIndex = -1;
        Node folderParentLayerIndexAttr = folder.getAttributes().getNamedItem("parentLayerIndex");
        if (folderParentLayerIndexAttr != null) {
            folderParentLayerIndex = Integer.parseInt(folderParentLayerIndexAttr.getTextContent());
        }
        //fg.writeLayerSeparator();
        fg.write(new byte[]{0x03, (byte) 0x80, 0x05, 0x00});

        Node folderFramesNode = getSubNodeByName(folder, "frames");
        if (folderFramesNode != null) {
            NodeList frames = folderFramesNode.getChildNodes();
            for (int f = 0; f < frames.getLength(); f++) {
                Node frame = frames.item(f);
                if ("DOMFrame".equals(frame.getNodeName())) {
                    fg.writeKeyFrameBegin();
                    fg.write(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
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

                    fg.writeKeyFrameEnd(duration, keyMode);
                }
            }
        }
        fg.createFolder(folderName, isSelected, hiddenLayer, lockedLayer, color, showOutlines, open, isEmpty, folderParentLayerIndex == -1 ? -1 : layerIndexToRevLayerIndex.get(folderParentLayerIndex));
    }

    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
        FlaCfbExtractor.initLog();
        File dir = new File(FlaCfbExtractor.getProperty("convert.xfl.dir"));
        File domDocumentFile = dir.toPath().resolve("DOMDocument.xml").toFile();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = factory.newDocumentBuilder();

        Document doc = docBuilder.parse(domDocumentFile);

        String file = FlaCfbExtractor.getProperty("convert.xfl.output.file");
        FileOutputStream fos = new FileOutputStream(file);
        FlaCs4Writer fg = new FlaCs4Writer(fos);
        fg.writePageHeader();

        int nextLayerId = 1;
        int nextFolderId = 1;

        NodeList timeLines = doc.getElementsByTagName("DOMTimeline");
        for (int i = 0; i < timeLines.getLength(); i++) {
            Node n = timeLines.item(i);
            Node layersNode = getSubNodeByName(n, "layers");
            if (layersNode != null) {
                List<Node> layers = getAllSubNodesByName(layersNode, "DOMLayer");

                Map<Integer, Integer> layerIndexToRevLayerIndex = new HashMap<>();
                Stack<Integer> openedLayers = new Stack<>();
                Map<Integer, Integer> layerToFolderEndCount = new HashMap<>();
                Map<Integer, Integer> folderToEndLayer = new HashMap<>();

                for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
                    Node layer = layers.get(layerIndex);
                    boolean isFolder = false;
                    Node layerTypeAttr = layer.getAttributes().getNamedItem("layerType");
                    if (layerTypeAttr != null) {
                        isFolder = "folder".equals(layerTypeAttr.getTextContent());
                    }

                    int parentLayerIndex = -1;
                    Node parentLayerIndexAttr = layer.getAttributes().getNamedItem("parentLayerIndex");
                    if (parentLayerIndexAttr != null) {
                        parentLayerIndex = Integer.parseInt(parentLayerIndexAttr.getTextContent());
                    }

                    if (isFolder) {
                        Stack<Integer> subs = new Stack<>();
                        subs.push(layerIndex);
                        int endLayer = layers.size() - 1;
                        for (int layerIndex2 = layerIndex + 1; layerIndex2 < layers.size(); layerIndex2++) {
                            Node layer2 = layers.get(layerIndex2);
                            boolean isFolder2 = false;
                            Node layerTypeAttr2 = layer2.getAttributes().getNamedItem("layerType");
                            if (layerTypeAttr2 != null) {
                                isFolder2 = "folder".equals(layerTypeAttr2.getTextContent());
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
                            if (isFolder2) {
                                subs.push(layerIndex2);
                            }
                        }

                        if (!layerToFolderEndCount.containsKey(endLayer)) {
                            layerToFolderEndCount.put(endLayer, 0);
                        }
                        layerToFolderEndCount.put(endLayer, layerToFolderEndCount.get(endLayer) + 1);

                        folderToEndLayer.put(layerIndex, endLayer);
                    }
                }

                for (int folderLayerIndex : folderToEndLayer.keySet()) {
                    int endLayer = folderToEndLayer.get(folderLayerIndex);
                    int reverseParent = 0;
                    for (int layerIndex3 = endLayer; layerIndex3 < layers.size(); layerIndex3++) {
                        Node layer3 = layers.get(layerIndex3);
                        boolean isFolder3 = false;
                        Node layerTypeAttr3 = layer3.getAttributes().getNamedItem("layerType");
                        if (layerTypeAttr3 != null) {
                            isFolder3 = "folder".equals(layerTypeAttr3.getTextContent());
                        }
                        if (isFolder3) {
                            reverseParent++;
                        } else {
                            reverseParent += 2;
                        }
                    }

                    Node layer3 = layers.get(folderLayerIndex);
                    Node parentLayerIndexAttr = layer3.getAttributes().getNamedItem("parentLayerIndex");
                    while (parentLayerIndexAttr != null) {
                        reverseParent++;
                        int parentLayerIndex = Integer.parseInt(parentLayerIndexAttr.getTextContent());
                        parentLayerIndexAttr = null;
                        if (parentLayerIndex >= 0 && parentLayerIndex < layers.size()) {
                            layer3 = layers.get(parentLayerIndex);
                            parentLayerIndexAttr = layer3.getAttributes().getNamedItem("parentLayerIndex");
                        }
                    }

                    String folderName = layers.get(folderLayerIndex).getAttributes().getNamedItem("name").getTextContent();
                    layerIndexToRevLayerIndex.put(folderLayerIndex, reverseParent);
                }

                Stack<Integer> openedParentLayers = new Stack<>();

                for (int layerIndex = layers.size() - 1; layerIndex >= 0; layerIndex--) {
                    Node layer = layers.get(layerIndex);

                    boolean isFolder = false;
                    Node layerTypeAttr = layer.getAttributes().getNamedItem("layerType");
                    if (layerTypeAttr != null) {
                        isFolder = "folder".equals(layerTypeAttr.getTextContent());
                    }

                    int parentLayerIndex = -1;
                    Node parentLayerIndexAttr = layer.getAttributes().getNamedItem("parentLayerIndex");
                    if (parentLayerIndexAttr != null) {
                        parentLayerIndex = Integer.parseInt(parentLayerIndexAttr.getTextContent());
                    }

                    if (isFolder) {
                        nextFolderId++;

                        boolean folderEmpty = true;
                        for (int layerIndex2 = layerIndex + 1; layerIndex2 < layers.size(); layerIndex2++) {
                            Node layer2 = layers.get(layerIndex2);
                            int parentLayerIndex2 = -1;
                            Node parentLayerIndexAttr2 = layer2.getAttributes().getNamedItem("parentLayerIndex");
                            if (parentLayerIndexAttr2 != null) {
                                parentLayerIndex2 = Integer.parseInt(parentLayerIndexAttr2.getTextContent());
                            }
                            if (parentLayerIndex2 == layerIndex) {
                                folderEmpty = false;
                                break;
                            }
                        }
                        if (folderEmpty) {
                            handleFolder(layer, fg, layerIndexToRevLayerIndex, true);
                        }

                        continue;
                    }

                    if (layerIndex < layers.size() - 1) {
                        fg.writeLayerSeparator();
                    }

                    nextLayerId++;

                    Node framesNode = getSubNodeByName(layer, "frames");
                    if (framesNode != null) {
                        NodeList frames = framesNode.getChildNodes();
                        for (int f = 0; f < frames.getLength(); f++) {
                            Node frame = frames.item(f);
                            if ("DOMFrame".equals(frame.getNodeName())) {
                                fg.writeKeyFrameBegin();
                                Node elementsNode = getSubNodeByName(frame, "elements");
                                if (elementsNode != null) {
                                    NodeList elements = elementsNode.getChildNodes();
                                    boolean emptyFrame = true;
                                    for (int e = 0; e < elements.getLength(); e++) {
                                        Node element = elements.item(e);
                                        if ("DOMShape".equals(element.getNodeName())) {
                                            emptyFrame = false;
                                            Node fillsNode = getSubNodeByName(element, "fills");
                                            List<Node> fillStyles = new ArrayList<>();
                                            if (fillsNode != null) {
                                                fillStyles = getAllSubNodesByName(fillsNode, "FillStyle");
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
                                            Node strokesNode = getSubNodeByName(element, "strokes");
                                            List<Node> strokeStyles = new ArrayList<>();
                                            if (strokesNode != null) {
                                                strokeStyles = getAllSubNodesByName(strokesNode, "StrokeStyle");
                                            }
                                            strokeStyles.sort(indexComparator);

                                            Node edgesNode = getSubNodeByName(element, "edges");
                                            List<Node> edges = new ArrayList<>();
                                            if (edgesNode != null) {
                                                edges = getAllSubNodesByName(edgesNode, "Edge");
                                            }

                                            int totalEdgeCount = 0;

                                            for (Node edge : edges) {
                                                Node edgesAttrNode = edge.getAttributes().getNamedItem("edges");
                                                if (edgesAttrNode != null) {
                                                    String edgesAttr = edgesAttrNode.getTextContent();
                                                    totalEdgeCount += FlaCs4Writer.getEdgesCount(edgesAttr);
                                                }
                                            }

                                            //fg.writeKeyFrame(1, KEYMODE_STANDARD);
                                            fg.write(new byte[]{
                                                (byte) totalEdgeCount, 0x00, 0x00, 0x00
                                            });
                                            fg.write(new byte[]{(byte) fillStyles.size(), 0x00});
                                            for (Node fillStyle : fillStyles) {
                                                Node fillStyleVal = getFirstSubElement(fillStyle);
                                                handleFill(fillStyleVal, fg);
                                            }
                                            fg.write(new byte[]{(byte) strokeStyles.size(), 0x00});
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
                                                        int pattern = getStrokeTypeParameter(strokeStyleVal, "pattern", Arrays.asList("solid", "simple", "random", "dotted", "random dotted", "triple dotted", "random tripple dotted"), "simple");
                                                        int waveHeight = getStrokeTypeParameter(strokeStyleVal, "waveHeight", Arrays.asList("flat", "wavy", "very wavy", "wild"), "wavy");
                                                        int waveLength = getStrokeTypeParameter(strokeStyleVal, "waveLength", Arrays.asList("very short", "short", "medium", "long"), "short");
                                                        styleParam2 = 0x08 * pattern + 0x40 * waveHeight + 0x100 * waveLength + 0x03;
                                                        break;
                                                    case "StippleStroke":
                                                        int dotSize = getStrokeTypeParameter(strokeStyleVal, "dotSize", Arrays.asList("tiny", "small", "medium", "large"), "small");
                                                        int variation = getStrokeTypeParameter(strokeStyleVal, "variation", Arrays.asList("one size", "small variation", "varied sizes", "random sizes"), "varied sizes");
                                                        int density = getStrokeTypeParameter(strokeStyleVal, "density", Arrays.asList("very dense", "dense", "sparse", "very sparse"), "sparse");

                                                        styleParam2 = 0x08 * dotSize + 0x20 * variation + 0x80 * density + 0x04;
                                                        break;
                                                    case "HatchedStroke":
                                                        int hatchThickness = getStrokeTypeParameter(strokeStyleVal, "hatchThickness", Arrays.asList("hairline", "thin", "medium", "thick"), "hairline");
                                                        int space = getStrokeTypeParameter(strokeStyleVal, "space", Arrays.asList("very close", "close", "distant", "very distant"), "distant");
                                                        int jiggle = getStrokeTypeParameter(strokeStyleVal, "jiggle", Arrays.asList("none", "bounce", "loose", "wild"), "none");
                                                        int rotate = getStrokeTypeParameter(strokeStyleVal, "rotate", Arrays.asList("none", "slight", "medium", "free"), "none");
                                                        int curve = getStrokeTypeParameter(strokeStyleVal, "curve", Arrays.asList("straight", "slight curve", "medium curve", "very curved"), "straight");
                                                        int length = getStrokeTypeParameter(strokeStyleVal, "length", Arrays.asList("equal", "slight variation", "medium variation", "random"), "equal");

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

                                                Node fill = getSubNodeByName(strokeStyleVal, "fill");
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
                                        fg.write(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
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

                                    fg.writeKeyFrameEnd(duration, keyMode);
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

                        fg.writeLayerEnd(layerName,
                                isSelected,
                                hiddenLayer,
                                lockedLayer,
                                color,
                                showOutlines,
                                true);
                    }
                    if (parentLayerIndex > -1) {
                        int reverseParentLayerIndex = layerIndexToRevLayerIndex.get(parentLayerIndex);
                        if (openedParentLayers.contains(parentLayerIndex)) {
                            fg.writeLayerEnd2(reverseParentLayerIndex);
                            if (parentLayerIndex == layerIndex - 1) {
                                fg.writeEndParentLayer(reverseParentLayerIndex);
                                openedParentLayers.pop();
                            }
                        } else {
                            Node folder = layers.get(parentLayerIndex);
                            handleFolder(folder, fg, layerIndexToRevLayerIndex, false);
                            if (parentLayerIndex == layerIndex - 1) {
                                fg.write(new byte[]{(byte) (7 + reverseParentLayerIndex), 0x00});
                            } else {
                                openedParentLayers.push(parentLayerIndex);
                            }
                        }
                    } else {
                        fg.writeLayerEnd2(-1);
                    }
                }
            }
        }
        fg.writePageFooter(nextLayerId, nextFolderId, 0);
        fos.close();
    }
}
