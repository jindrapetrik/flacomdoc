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
 * Restores symbol armatures from their per-instance persistent properties.
 */
final class XflSymbolIkWriter {

    private final BinaryFlaToXflExporter.Session session;
    private final Map<String, FlaRecord> nodes = new LinkedHashMap<>();
    private final Set<String> visited = new HashSet<>(), bones = new HashSet<>();
    private List<FlaXmlElement> poses;
    private String treeName, armature;

    XflSymbolIkWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void write(Element frame, FlaRecord record, long start, long duration) throws IOException {
        for (FlaRecord instance : record.getChildren()) {
            FlaPersistentData data = instance.getPersistentData();
            if (data == null || data.getString("ikTreeName") == null) {
                continue;
            }
            String tree = data.getString("ikTreeName"), name = data.getString("Armature_");
            require(treeName == null || treeName.equals(tree), "Symbol frame contains different IK subtrees.");
            require(name != null && (armature == null || armature.equals(name)), "Inconsistent symbol armature names.");
            treeName = tree;
            armature = name;
            String id = data.getString("ikNode_");
            require(id != null && !id.isEmpty() && nodes.put(id, instance) == null, "Missing or duplicate symbol IK node identity.");
        }
        require(!nodes.isEmpty(), "Missing symbol armature nodes.");
        FlaPersistentData document = session.source.getContents().getPersistentData();
        require(document != null, "Missing document armature metadata.");
        String bridgeText = document.getString(armature);
        require(bridgeText != null, "Missing document armature description.");
        List<FlaXmlElement> fragment = FlaXmlElement.parseFragment(bridgeText);
        require(fragment.size() == 1 && fragment.get(0).getName().equals("BridgeTree"), "Invalid symbol armature description.");
        FlaXmlElement bridge = fragment.get(0), links = bridge.getFirstChild("Nodes"), states = bridge.getFirstChild("_ikTreeStates");
        require(links != null && links.getChildren("BridgeNode").size() == 1, "Multiple symbol armature subtrees require their bridge topology.");
        FlaXmlElement link = links.getChildren("BridgeNode").get(0);
        require(treeName.equals(link.getAttribute("subTreeName")) && "ik".equals(link.getAttribute("subTreeType")), "Symbol subtree type/name mismatch.");
        require(integer(bridge, "startFrame") == start && states != null, "Missing or inconsistent symbol pose start.");
        poses = states.getChildren("state");
        require(!poses.isEmpty(), "Missing symbol armature poses.");
        long total = 0;
        for (int i = 0; i < poses.size(); i++) {
            FlaXmlElement pose = poses.get(i);
            int span = integer(pose, "duration");
            require(integer(pose, "index") == i && span > 0, "Invalid symbol pose order/duration.");
            total += span;
        }
        require(total == duration, "Symbol pose span disagrees with frame duration.");
        String root = document.getString(treeName);
        require(root != null && nodes.containsKey(root), "Missing symbol root-node reference.");
        Element tree = frame.getOwnerDocument().createElementNS(NS, "IKTree");
        session.attribute(tree, "name", armature, record);
        session.attribute(tree, "treeName", treeName, record);
        String export = bridge.getAttribute("exportType");
        require(Arrays.asList("authortime", "runtime").contains(export), "Unknown symbol IK export type.");
        tree.setAttribute("exportType", export);
        int style = integer(bridge, "displayStyle");
        require(style >= 0 && style <= 2, "Invalid symbol IK display style.");
        tree.setAttribute("displayStyle", new String[]{"wire", "solid", "line"}[style]);
        tree.setAttribute("physicsEnabled", "true");
        node(child(tree, "IKNode"), root, null, 0);
        require(visited.size() == nodes.size(), "Disconnected symbol IK nodes.");
        Element outputStates = child(tree, "states");
        String[] easing = {"none", "simple quadratic", "simple cubic", "simple quartic", "simple quintic", "dual quadratic", "dual cubic", "dual quartic", "dual quintic"};
        for (FlaXmlElement pose : poses) {
            int type = integer(pose, "easeType");
            double amount = value(pose, "easeValue");
            require(type >= 0 && type < easing.length && amount >= -100 && amount <= 100, "Invalid symbol IK easing.");
            Element e = child(outputStates, "IKState");
            e.setAttribute("duration", Integer.toString(integer(pose, "duration")));
            poseEasing(e, integer(pose, "easeIn"), integer(pose, "easeOut"), type, amount);
        }
        // Resolve every reference before attaching the editable armature.
        Map<Element, String> references = new LinkedHashMap<>();
        Element elements = first(frame, "elements");
        require(elements != null, "Missing symbol display list.");
        for (Map.Entry<String, FlaRecord> entry : nodes.entrySet()) {
            String id = s(entry.getValue(), "identifier", "");
            require(id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"), "Symbol IK instance has no persistent reference identifier.");
            Element found = null;
            for (org.w3c.dom.Node n = elements.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) {
                    Element e = (Element) n;
                    if ((e.getLocalName().equals("DOMSymbolInstance") || e.getLocalName().equals("DOMComponentInstance")) && e.getAttribute("referenceID").equals(id)) {
                        require(found == null, "Duplicate symbol reference identity.");
                        found = e;
                    }
                }
            }
            require(found != null && !references.containsKey(found), "Symbol IK display reference is missing or ambiguous.");
            references.put(found, id);
        }
        frame.appendChild(tree);
        frame.setAttribute("tweenType", "IK pose");
        frame.setAttribute("isIKPose", "true");
        session.issue("IK_SAMPLE_CACHE", record, "Symbol topology, stored poses, pivots and constraints exported; runtime-derived sample arrays are not archived and application playback is unverified.");
    }

    private void node(Element output, String id, String parent, int depth) throws IOException {
        require(depth <= 100 && visited.add(id), "Cyclic, shared or excessively deep symbol IK topology.");
        FlaRecord instance = nodes.get(id);
        require(instance != null, "Symbol IK child reference does not resolve.");
        FlaPersistentData data = instance.getPersistentData();
        require(parent == null || parent.equals(data.getString("ikNode_parent")), "Symbol IK parent/child references disagree.");
        String bone = data.getString("ikBoneName");
        require(bone != null && !bone.isEmpty() && bones.add(bone), "Missing or duplicate symbol bone name.");
        session.attribute(output, "name", id, instance);
        session.attribute(output, "boneName", bone, instance);
        String reference = s(instance, "identifier", "");
        session.attribute(output, "referenceID", reference, instance);
        output.setAttribute("nodeType", "ik");
        output.setAttribute("speed", "1");
        String pivot = data.getString("ik_tp");
        require(pivot != null, "Missing symbol IK local pivot.");
        String[] xy = pivot.split(",", -1);
        require(xy.length == 2, "Invalid symbol IK local pivot.");
        FlaMatrix matrix = instance.getMatrix();
        require(matrix != null, "Missing symbol IK placement matrix.");
        double[] location = matrix.transformPixels(parse(xy[0]), parse(xy[1]));
        output.setAttribute("location", point(location[0], location[1]));
        for (Map.Entry<String, String> constraint : constraintValues(data.getString("ikNode_motionConstraints")).entrySet()) {
            output.setAttribute(constraint.getKey(), constraint.getValue());
        }
        String raw = data.getString(id + "_ikNodeStates");
        require(raw != null, "Missing symbol IK pose data.");
        List<Map<String, Double>> states = states(raw);
        require(states.size() == poses.size(), "Symbol IK pose count mismatch.");
        Element holder = child(output, "states");
        for (int i = 0; i < states.size(); i++) {
            Map<String, Double> state = states.get(i);
            Element e = child(holder, "State");
            e.setAttribute("duration", Integer.toString(integer(poses.get(i), "duration")));
            for (String key : Arrays.asList("x", "y", "xRel", "yRel", "angle")) {
                require(state.containsKey(key), "Missing symbol IK pose coordinate.");
                e.setAttribute(key, number(state.get(key) * (key.equals("angle") ? 1 : 20)));
            }
            require(state.keySet().equals(new HashSet<>(Arrays.asList("duration", "angle", "x", "y", "xRel", "yRel", "easeIn", "easeOut", "easeType", "easeValue"))), "Unexpected symbol IK pose properties.");
            require(state.get("duration") != null && state.get("duration") == value(poses.get(i), "duration"), "Symbol and document pose durations disagree.");
            poseEasing(e, stateInteger(state, "easeIn"), stateInteger(state, "easeOut"), stateInteger(state, "easeType"), state.get("easeValue"));
        }
        String pins = data.getString("ikNode_pins");
        if (pins != null && !pins.isEmpty()) {
            String[] coordinates = pins.split(",", -1);
            require(coordinates.length % 2 == 0, "Invalid symbol IK pin point list.");
            Element points = child(output, "pins");
            for (int i = 0; i < coordinates.length; i += 2) {
                child(points, "Pin").setAttribute("integerPoint", point(parse(coordinates[i]), parse(coordinates[i + 1])));
            }
        }
        String children = data.getString("ikNode_children");
        if (children != null && !children.isEmpty()) {
            Element descendants = child(output, "childNodes");
            for (String child : children.split(",", -1)) {
                node(child(descendants, "ChildNode"), child, id, depth + 1);
            }
        }
    }

    private static int stateInteger(Map<String, Double> state, String key) throws IOException {
        Double v = state.get(key);
        require(v != null && v == Math.rint(v) && v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE, "Invalid symbol pose integer.");
        return v.intValue();
    }

    private static List<Map<String, Double>> states(String text) throws IOException {
        List<Map<String, Double>> result = new ArrayList<>();
        for (String state : text.split(";", -1)) {
            if (state.isEmpty()) {
                continue;
            }
            Map<String, Double> values = new LinkedHashMap<>();
            for (String field : state.split(",", -1)) {
                int equal = field.indexOf('=');
                require(equal > 0, "Invalid symbol IK pose field.");
                require(values.put(field.substring(0, equal), parse(field.substring(equal + 1))) == null, "Duplicate symbol IK pose field.");
            }
            result.add(values);
        }
        return result;
    }

    private static Element first(Element parent, String name) {
        for (org.w3c.dom.Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && ((Element) node).getLocalName().equals(name)) {
                return (Element) node;
            }
        }
        return null;
    }
}
