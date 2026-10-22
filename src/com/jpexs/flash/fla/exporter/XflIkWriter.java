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
import java.io.IOException;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Converts archived skin authoring data, independently of baked display frames.
 */
final class XflIkWriter {

    private final BinaryFlaToXflExporter.Session session;
    private FlaInverseKinematics ik;
    private FlaRecord placement;
    private List<FlaXmlElement> poses;
    private final Map<Integer, FlaInverseKinematics.Node> nodes = new LinkedHashMap<>();
    private final Map<Integer, Map<String, String>> constraints = new HashMap<>();
    private final Map<Integer, FlaXmlElement> matrices = new HashMap<>();

    private XflIkWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    static boolean write(BinaryFlaToXflExporter.Session session, Element frame, FlaRecord record, long start, long duration) {
        try {
            boolean skin = false;
            for (FlaRecord object : record.getChildren()) {
                FlaPersistentData data = object.getPersistentData();
                skin |= data != null && "lsd".equals(data.getString("lsd"));
            }
            if (!skin) {
                new XflSymbolIkWriter(session).write(frame, record, start, duration);
                return true;
            }
            new XflIkWriter(session).write(frame, record, start, duration);
            session.issue("IK_SAMPLE_CACHE", record, "IK topology, poses and skin bindings exported. Derived node sample arrays are absent from this archive; Flash can rebuild them when saving XFL. Application playback has not been verified.");
            return true;
        } catch (IOException ex) {
            session.issue("IK_AUTHORING", record, "Editable armature omitted; baked symbol frames and persistent data retained: " + ex.getMessage());
            return false;
        }
    }

    private void write(Element frame, FlaRecord record, long start, long duration) throws IOException {
        for (FlaRecord child : record.getChildren()) {
            FlaPersistentData data = child.getPersistentData();
            if (data == null || data.getString("ikTreeName") == null) {
                continue;
            }
            require(placement == null, "Multiple IK placements in one frame are not supported.");
            placement = child;
            ik = data.getInverseKinematics();
        }
        require(ik != null && "lsd".equals(ik.getPersistentData().getString("lsd")), "No archived skin armature found.");
        require(ik.getDiagnostics().isEmpty(), "Malformed IK metadata: " + ik.getDiagnostics());
        require(ik.getRoots().size() == 1, "Skin armature must have one root.");
        require(index(ik.getRoots().get(0)) == 0, "Skin root must use node index zero.");
        collect(ik.getRoots().get(0), 0);
        require(!nodes.isEmpty() && ik.getNodeNames() != null && ik.getBoneNames() != null
                && nodes.size() == ik.getNodeNames().size() && nodes.size() == ik.getBoneNames().size(), "Inconsistent node/name arrays.");
        for (int i = 0; i < nodes.size(); i++) {
            require(nodes.containsKey(i), "Noncontiguous IK node indices.");
        }
        require(new HashSet<>(ik.getNodeNames()).size() == nodes.size() && new HashSet<>(ik.getBoneNames()).size() == nodes.size(), "Duplicate node or bone names.");
        require(!ik.getNodeNames().contains("") && !ik.getBoneNames().contains(""), "Empty node or bone name.");
        require(ik.getTopology() != null && ik.getTopology().size() == nodes.size(), "Missing or inconsistent topology.");
        for (Map.Entry<Integer, FlaInverseKinematics.Node> entry : nodes.entrySet()) {
            List<Integer> children = new ArrayList<>();
            for (FlaInverseKinematics.Node child : entry.getValue().getChildren()) {
                children.add(index(child));
            }
            require(children.equals(ik.getTopology().get(entry.getKey())), "XML and numeric topology disagree.");
        }
        require(ik.getWorldMatrices() != null, "Missing world matrices.");
        for (FlaXmlElement matrix : ik.getWorldMatrices()) {
            int id = integer(matrix, "Name");
            require(nodes.containsKey(id) && matrices.put(id, matrix) == null, "Duplicate or unknown world matrix.");
        }
        require(matrices.size() == nodes.size(), "Incomplete world matrices.");
        require(ik.getSkinBindings() != null, "Missing skin bindings.");
        for (FlaSkinBindings.Point point : ik.getSkinBindings().getPoints()) {
            for (FlaSkinBindings.Connection connection : point.getConnections()) {
                require(nodes.containsKey(connection.getBoneId()), "Skin refers to an unknown bone.");
            }
        }
        poses = ik.getPoseStates();
        require(poses != null && !poses.isEmpty(), "Missing pose states.");
        List<Integer> starts = ik.getPoseStartFrames();
        long position = start;
        require(starts != null && starts.size() == poses.size(), "Missing pose locations.");
        for (int i = 0; i < poses.size(); i++) {
            require(integer(poses.get(i), "index") == i && starts.get(i) == position, "Inconsistent pose index or start frame.");
            int span = integer(poses.get(i), "duration");
            require(span > 0, "Nonpositive pose duration.");
            position += span;
            int easeType = integer(poses.get(i), "easeType");
            double easeValue = value(poses.get(i), "easeValue");
            require(easeType >= 0 && easeType <= 8 && easeValue >= -100 && easeValue <= 100, "Invalid or unsupported IK easing.");
        }
        require(position == start + duration, "Pose durations disagree with frame duration.");
        FlaXmlElement bridge = bridge();
        require(integer(bridge, "startFrame") == start, "Armature start frame disagrees with timeline.");
        readConstraints();

        // Build detached XML first: failures must not leave a partial editable rig.
        Element tree = frame.getOwnerDocument().createElementNS(NS, "IKTree");
        session.attribute(tree, "name", ik.getArmatureName(), placement);
        session.attribute(tree, "treeName", ik.getTreeName(), placement);
        String export = bridge.getAttribute("exportType");
        require("authortime".equals(export) || "runtime".equals(export), "Unknown armature export type.");
        tree.setAttribute("exportType", export);
        int style = integer(bridge, "displayStyle");
        require(style >= 0 && style <= 2, "Unknown armature display style.");
        tree.setAttribute("displayStyle", new String[]{"wire", "solid", "line"}[style]);
        // Springs were not archived in this layout; retain the native CS5 upgrade default.
        tree.setAttribute("physicsEnabled", "true");
        tree.setAttribute("connectionIndices", csv(ik.getPersistentData().get("lsd_connection_indices")));
        tree.setAttribute("connectionData", csv(ik.getPersistentData().get("lsd_connections")));
        FlaInverseKinematics.Node root = ik.getRoots().get(0);
        Element wrapper = child(tree, "IKNode");
        String suffix = ik.getTreeName();
        session.attribute(wrapper, "name", unused("recoveredRoot_" + suffix, ik.getNodeNames()), placement);
        session.attribute(wrapper, "boneName", unused("recoveredBone_" + suffix, ik.getBoneNames()), placement);
        nodeAttributes(wrapper, index(root));
        worldMatrix(wrapper, matrices.get(index(root)));
        node(child(child(wrapper, "childNodes"), "ChildNode"), root);
        Element states = child(tree, "states");
        for (FlaXmlElement pose : poses) {
            Element state = child(states, "IKState");
            state.setAttribute("duration", Integer.toString(integer(pose, "duration")));
            poseEasing(state, integer(pose, "easeIn"), integer(pose, "easeOut"), integer(pose, "easeType"), value(pose, "easeValue"));
        }
        contours(tree);
        Element between = frame.getOwnerDocument().createElementNS(NS, "betweenFrameMatrixList");
        Object raw = record.get("poseMatrices");
        require(raw instanceof List && ((List<?>) raw).size() == duration, "Expected one between-frame matrix per skin frame.");
        for (Object item : (List<?>) raw) {
            require(item instanceof List && ((List<?>) item).size() == 6, "Invalid between-frame matrix.");
            int[] words = new int[6];
            for (int i = 0; i < 6; i++) {
                Object word = ((List<?>) item).get(i);
                require(word instanceof Integer, "Noninteger matrix word.");
                words[i] = (Integer) word;
            }
            Element holder = frame.getOwnerDocument().createElementNS(NS, "holder");
            matrix(holder, FlaMatrix.fromWords(words));
            between.appendChild(holder.getFirstChild().getFirstChild());
        }
        frame.appendChild(between);
        frame.appendChild(tree);
        frame.setAttribute("tweenType", "IK pose");
        frame.setAttribute("isIKPose", "true");
    }

    private FlaXmlElement bridge() throws IOException {
        FlaPersistentData document = session.source.getContents().getPersistentData();
        String xml = document == null ? null : document.getString(ik.getArmatureName());
        require(xml != null, "Missing document armature description.");
        List<FlaXmlElement> roots = FlaXmlElement.parseFragment(xml);
        require(roots.size() == 1 && "BridgeTree".equals(roots.get(0).getName()), "Invalid armature description.");
        FlaXmlElement result = roots.get(0), links = result.getFirstChild("Nodes");
        require(links != null && links.getChildren("BridgeNode").size() == 1, "Multiple armature subtrees are not supported yet.");
        FlaXmlElement link = links.getChildren("BridgeNode").get(0);
        require(ik.getTreeName().equals(link.getAttribute("subTreeName")) && "lsd".equals(link.getAttribute("subTreeType")), "Armature subtree mismatch.");
        FlaXmlElement states = result.getFirstChild("_ikTreeStates");
        require(states != null && states.getChildren("state").size() == poses.size(), "Armature poses disagree with skin poses.");
        for (int i = 0; i < poses.size(); i++) {
            require(states.getChildren("state").get(i).getAttributes().equals(poses.get(i).getAttributes()), "Armature poses disagree with skin poses.");
        }
        return result;
    }

    private void collect(FlaInverseKinematics.Node node, int depth) throws IOException {
        require(depth <= 100, "IK hierarchy exceeds the export limit.");
        require(nodes.put(index(node), node) == null, "Duplicate IK node index.");
        require(node.isLocked() != null, "Missing or invalid IK node lock state.");
        for (FlaInverseKinematics.Node child : node.getChildren()) {
            collect(child, depth + 1);
        }
    }

    private void readConstraints() throws IOException {
        List<List<FlaInverseKinematics.Property>> raw = ik.getMotionConstraints();
        require(raw != null, "Missing motion constraints.");
        require(raw.size() <= nodes.size(), "Motion constraints exceed the node array.");
        // The archive writer traverses branch nodes, but the native restore path
        // indexes this vector by the numeric node ID. Reproduce the loaded data.
        for (int i = 0; i < raw.size(); i++) {
            StringJoiner text = new StringJoiner(",");
            for (FlaInverseKinematics.Property property : raw.get(i)) {
                text.add(property.getName() + "=" + property.getValue());
            }
            Map<String, String> values = constraintValues(text.toString());
            constraints.put(i, values);
        }
    }

    static Map<String, String> constraintValues(String raw) throws IOException {
        if (raw == null || raw.isEmpty()) {
            return Collections.singletonMap("rotationEnabled", "true");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("rotationEnabled", "true");
        String mode = "stationary";
        for (String item : raw.split(",", -1)) {
            int separator = item.indexOf('=');
            require(separator > 0, "Malformed IK constraint.");
            String key = item.substring(0, separator), value = item.substring(separator + 1);
            if ("motionType".equals(key)) {
                require(Arrays.asList("stationary", "rotation", "xtrans", "ytrans").contains(value), "Unknown IK motion type.");
                mode = value;
                if ("stationary".equals(mode)) {
                    values.put("rotationEnabled", "false");
                    values.put("xTranslationEnabled", "false");
                    values.put("yTranslationEnabled", "false");
                }
                continue;
            }
            String prefix = key.startsWith("rot") ? "rot" : key.startsWith("xtrans") ? "xtrans" : key.startsWith("ytrans") ? "ytrans" : null;
            String target;
            if (prefix != null) {
                String suffix = key.substring(prefix.length());
                require(Arrays.asList("Enabled", "Constrained", "Min", "Max").contains(suffix), "Unknown IK constraint " + key);
                target = (prefix.equals("rot") ? "rotation" : prefix.equals("xtrans") ? "xTranslation" : "yTranslation") + suffix;
                if (suffix.equals("Enabled") || suffix.equals("Constrained")) {
                    require("true".equals(value) || "false".equals(value), "Invalid IK constraint boolean.");
                    if (suffix.equals("Enabled")) {
                        value = Boolean.toString("true".equals(value) && mode.equals(prefix.equals("rot") ? "rotation" : prefix));
                    }
                } else {
                    double number = parse(value);
                    // Rotation is already stored as degrees; translation is in pixels.
                    require(!prefix.equals("rot") || number >= -360 && number <= 360, "IK rotation limit exceeds the native XFL range.");
                    value = number(number);
                }
            } else if (key.equals("speed")) {
                double speed = parse(value);
                require(speed >= 0 && speed <= 100, "IK speed is outside the native XFL range.");
                target = "speed";
                value = number(speed);
            } else {
                throw new IOException("Unknown joint constraint " + key);
            }
            values.put(target, value);
        }
        return values;
    }

    private void node(Element output, FlaInverseKinematics.Node input) throws IOException {
        int index = index(input);
        session.attribute(output, "name", ik.getNodeNames().get(index), placement);
        session.attribute(output, "boneName", ik.getBoneNames().get(index), placement);
        nodeAttributes(output, index);
        output.setAttribute("locked", Boolean.toString(input.isLocked()));
        output.setAttribute("rotationEnabled", "true");
        Map<String, String> limits = constraints.get(index);
        if (limits != null) {
            for (Map.Entry<String, String> entry : limits.entrySet()) {
                output.setAttribute(entry.getKey(), entry.getValue());
            }
        }
        List<FlaXmlElement> states = input.getStates();
        require(states != null && states.size() == poses.size(), "Incomplete node poses.");
        Element container = child(output, "states");
        for (int i = 0; i < states.size(); i++) {
            FlaXmlElement state = states.get(i);
            require(integer(state, "index") == i, "Node pose index mismatch.");
            Element e = child(container, "State");
            e.setAttribute("duration", Integer.toString(integer(poses.get(i), "duration")));
            for (String key : Arrays.asList("x", "y", "xRel", "yRel")) {
                e.setAttribute(key, number(value(state, key) * 20));
            }
            e.setAttribute("angle", number(value(state, "angle")));
        }
        worldMatrix(output, matrices.get(index));
        if (!input.getChildren().isEmpty()) {
            Element children = child(output, "childNodes");
            for (FlaInverseKinematics.Node c : input.getChildren()) {
                node(child(children, "ChildNode"), c);
            }
        }
    }

    private void nodeAttributes(Element node, int index) throws IOException {
        FlaXmlElement matrix = matrices.get(index);
        node.setAttribute("location", point(value(matrix, "tx"), value(matrix, "ty")));
        node.setAttribute("nodeType", "lsd");
        node.setAttribute("speed", "1");
    }

    private void worldMatrix(Element parent, FlaXmlElement input) throws IOException {
        Element matrix = child(child(parent, "worldMatrix"), "Matrix");
        for (String key : Arrays.asList("a", "b", "c", "d", "tx", "ty")) {
            double value = value(input, key);
            if (key.length() == 1) {
                value = Math.round(value * 65536) / 65536.0;
            }
            matrix.setAttribute(key, number(value));
        }
    }

    private void contours(Element tree) throws IOException {
        List<FlaXmlElement> contours = ik.getContours();
        require(contours != null && !contours.isEmpty(), "Missing skin contours.");
        int anchors = contourChain(tree, contours, 0, "IKContour", 0);
        require(anchors == ik.getSkinBindings().getDeclaredPointCount(), "Contour and binding point counts disagree.");
    }

    private int contourChain(Element parent, List<FlaXmlElement> list, int index, String tag, int depth) throws IOException {
        require(depth <= 100, "IK contour hierarchy exceeds the export limit.");
        FlaXmlElement input = list.get(index);
        require("Contour".equals(input.getName()), "Unexpected skin contour element.");
        // The native interchange grammar has no separate closed flag; the
        // complete cubic point sequence is retained exactly as its writer does.
        String closed = input.getAttribute("closed");
        require("true".equals(closed) || "false".equals(closed), "Missing or invalid contour closed flag.");
        String data = input.getAttribute("data");
        require(data != null, "Missing contour points.");
        String[] values = data.split(",", -1);
        require(values.length >= 8 && values.length % 6 == 2, "Incomplete cubic contour point sequence.");
        int anchors = (values.length / 2 + 2) / 3;
        Element contour = child(parent, tag);
        for (String key : Arrays.asList("interior", "fillSelected", "strokeHasSelection", "strokeFullySelected")) {
            String flag = input.getAttribute(key);
            require("true".equals(flag) || "false".equals(flag), "Invalid contour selection flag.");
            contour.setAttribute(key, flag);
        }
        for (String key : Arrays.asList("rectLeft", "rectTop", "rectRight", "rectBottom")) {
            contour.setAttribute(key, Integer.toString(Integer.MIN_VALUE));
        }
        Element points = child(contour, "dataPoints");
        for (int i = 0; i < values.length; i += 2) {
            child(points, "DataPoint").setAttribute("integerPoint", point(parse(values[i]), parse(values[i + 1])));
        }
        matrix(contour, placement.getMatrix());
        require(placement.getMatrix() != null, "Missing skin placement matrix.");
        XflIkPaintWriter paints = new XflIkPaintWriter(session, placement);
        paints.stroke(contour, input.getFirstChild("stroke"));
        paints.paint(contour, input.getFirstChild("fill"));
        List<FlaXmlElement> contained = input.getChildren("Contour");
        if (!contained.isEmpty()) {
            anchors += contourChain(contour, contained, 0, "IKContainedContour", depth + 1);
        }
        if (index + 1 < list.size()) {
            anchors += contourChain(contour, list, index + 1, "NextContour", depth + 1);
        }
        return anchors;
    }

    static void poseEasing(Element output, int easeIn, int easeOut, int type, double amount) throws IOException {
        require(easeIn >= -100 && easeIn <= 100 && easeOut >= -100 && easeOut <= 100, "IK legacy easing exceeds the native integer range.");
        String[] kinds = {"none", "simple quadratic", "simple cubic", "simple quartic", "simple quintic", "dual quadratic", "dual cubic", "dual quartic", "dual quintic"};
        require(type >= 0 && type < kinds.length && Double.isFinite(amount) && amount >= -100 && amount <= 100, "Invalid IK pose easing.");
        if (easeIn != 0) {
            output.setAttribute("easeIn", Integer.toString(easeIn));
        }
        if (easeOut != 0) {
            output.setAttribute("easeOut", Integer.toString(easeOut));
        }
        output.setAttribute("easeType", kinds[type]);
        output.setAttribute("easeValue", number(amount));
    }

    static String point(double x, double y) throws IOException {
        // Integer points are serialized through XFL's 24.8 coordinate formatter.
        require(x * 20 >= Integer.MIN_VALUE && x * 20 <= Integer.MAX_VALUE && y * 20 >= Integer.MIN_VALUE && y * 20 <= Integer.MAX_VALUE, "IK point exceeds signed twip range.");
        return number(Math.round(x * 20) / 256.0) + ", " + number(Math.round(y * 20) / 256.0);
    }

    private static int index(FlaInverseKinematics.Node node) throws IOException {
        String name = node.getName();
        require(name != null && name.matches("node_[0-9]+"), "Unknown node index format.");
        try {
            return Integer.parseInt(name.substring(5));
        } catch (NumberFormatException ex) {
            throw new IOException("Node index exceeds integer range.");
        }
    }

    private static String unused(String base, List<String> names) {
        String result = base;
        while (names.contains(result)) {
            result += "_";
        }
        return result;
    }

    static int integer(FlaXmlElement e, String key) throws IOException {
        double n = value(e, key);
        require(n == Math.rint(n) && n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE, "Invalid IK integer " + key);
        return (int) n;
    }

    static double value(FlaXmlElement e, String key) throws IOException {
        require(e != null, "Missing IK element.");
        return parse(e.getAttribute(key));
    }

    static double parse(String text) throws IOException {
        try {
            double value = Double.parseDouble(text);
            require(Double.isFinite(value), "Nonfinite IK number.");
            return value;
        } catch (NullPointerException | NumberFormatException ex) {
            throw new IOException("Missing or invalid IK number.");
        }
    }

    private static String csv(Object value) throws IOException {
        require(value instanceof List && !((List<?>) value).isEmpty(), "Empty IK numeric array.");
        StringJoiner result = new StringJoiner(",");
        for (Object n : (List<?>) value) {
            require(n instanceof Number && Double.isFinite(((Number) n).doubleValue()), "Invalid IK array value.");
            result.add(number(((Number) n).doubleValue()));
        }
        return result.toString();
    }

    static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }
}
