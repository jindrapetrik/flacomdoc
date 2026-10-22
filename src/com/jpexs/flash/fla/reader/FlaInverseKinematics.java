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
package com.jpexs.flash.fla.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoring IK topology, poses, contour connections and named XML metadata.
 */
public final class FlaInverseKinematics {

    public static final class Node {

        private final FlaXmlElement xml;
        private final List<Node> children;

        private Node(FlaXmlElement xml) {
            this.xml = xml;
            List<Node> c = new ArrayList<>();
            for (FlaXmlElement e : xml.getChildren("node")) {
                c.add(new Node(e));
            }
            children = Collections.unmodifiableList(c);
        }

        public String getName() {
            return xml.getAttribute("name");
        }

        public FlaXmlElement getXml() {
            return xml;
        }

        public List<Node> getChildren() {
            return children;
        }

        public List<FlaXmlElement> getStates() {
            FlaXmlElement states = xml.getFirstChild("states");
            return states == null ? null : states.getChildren("state");
        }

        public Boolean isLocked() {
            FlaXmlElement lock = xml.getFirstChild("lock");
            String v = lock == null ? null : lock.getAttribute("value");
            return "true".equals(v) ? Boolean.TRUE : "false".equals(v) ? Boolean.FALSE : null;
        }
    }

    public static final class Property {

        private final String name, value;

        private Property(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
    private final FlaPersistentData data;
    private final Map<String, List<FlaXmlElement>> xml;
    private final List<String> diagnostics;
    private final List<Node> roots;
    private final FlaSkinBindings skin;
    private final List<List<Integer>> topology;

    private FlaInverseKinematics(FlaPersistentData data) {
        this.data = data;
        List<String> issues = new ArrayList<>();
        Map<String, List<FlaXmlElement>> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, FlaPersistentData.Entry> entry : data.getByName().entrySet()) {
            String name = entry.getKey();
            Object v = entry.getValue().getValue();
            if (v instanceof String && ((String) v).trim().startsWith("<")
                    && (name.startsWith("_ik") || name.startsWith("lsd_") || name.startsWith("Armature_"))) {
                try {
                    parsed.put(name, FlaXmlElement.parseFragment((String) v));
                } catch (IOException ex) {
                    issues.add(name + ": " + ex.getMessage());
                }
            }
        }
        xml = Collections.unmodifiableMap(parsed);
        List<Node> nodes = new ArrayList<>();
        List<FlaXmlElement> tree = xml.get("_ikTreeXML");
        if (tree != null) {
            for (FlaXmlElement e : tree) {
                if ("nodes".equals(e.getName())) {
                    for (FlaXmlElement n : e.getChildren("node")) {
                        nodes.add(new Node(n));
                    }
                }
            }
        }
        roots = Collections.unmodifiableList(nodes);
        Object indices = data.get("lsd_connection_indices"), parameters = data.get("lsd_connections");
        FlaSkinBindings bindings = null;
        if (indices != null || parameters != null) {
            if (!(indices instanceof List) || !(parameters instanceof List)) {
                issues.add("Skin binding arrays are absent or have the wrong type");
            } else try {
                bindings = FlaSkinBindings.fromArrays((List<?>) indices, (List<?>) parameters);
            } catch (IOException ex) {
                issues.add(ex.getMessage());
            }
        }
        skin = bindings;
        List<List<Integer>> adjacency = null;
        Object rawTree = data.get("lsd_tree");
        if (rawTree != null) {
            try {
                adjacency = readTopology(rawTree);
            } catch (IOException ex) {
                issues.add(ex.getMessage());
            }
        }
        topology = adjacency;
        diagnostics = Collections.unmodifiableList(issues);
    }

    static FlaInverseKinematics fromPersistentData(FlaPersistentData data) {
        for (String name : data.getByName().keySet()) {
            if (name.startsWith("_ik") || name.startsWith("lsd_") || name.startsWith("ikTree") || name.startsWith("Armature_")) {
                return new FlaInverseKinematics(data);
            }
        }
        return null;
    }

    public FlaPersistentData getPersistentData() {
        return data;
    }

    public String getTreeName() {
        return data.getString("ikTreeName");
    }

    public String getArmatureName() {
        return data.getString("Armature_");
    }

    /**
     * XML fragments retain their original root ordering and unknown attributes.
     */
    public Map<String, List<FlaXmlElement>> getXmlFragments() {
        return xml;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    public List<Node> getRoots() {
        return roots;
    }

    public FlaSkinBindings getSkinBindings() {
        return skin;
    }

    /**
     * Child indices for each node, in node-array order.
     */
    public List<List<Integer>> getTopology() {
        return topology;
    }

    public List<String> getNodeNames() {
        return names("lsd_node_names");
    }

    public List<String> getBoneNames() {
        return names("lsd_bone_names");
    }

    public List<Integer> getPoseStartFrames() {
        Object v = data.get("lsd_frame_data");
        if (!(v instanceof List)) {
            return null;
        }
        List<Integer> frames = new ArrayList<>();
        for (Object x : (List<?>) v) {
            if (!(x instanceof Integer)) {
                return null;
            }
            frames.add((Integer) x);
        }
        return Collections.unmodifiableList(frames);
    }

    public List<FlaXmlElement> getPoseStates() {
        List<FlaXmlElement> tree = xml.get("_ikTreeXML");
        if (tree != null) {
            for (FlaXmlElement e : tree) {
                if ("_ikTreeStates".equals(e.getName())) {
                    return e.getChildren("state");
                }
            }
        }
        return null;
    }

    public List<FlaXmlElement> getWorldMatrices() {
        return xml.get("lsd_world_matrices");
    }

    public List<FlaXmlElement> getContours() {
        return xml.get("lsd_shape_contour_data");
    }

    /**
     * Percent-separated records; repeated motionType properties remain ordered.
     */
    public List<List<Property>> getMotionConstraints() {
        String value = data.getString("lsd_motion_constraints");
        if (value == null) {
            return null;
        }
        List<List<Property>> records = new ArrayList<>();
        for (String record : value.split("%", -1)) {
            if (record.isEmpty()) {
                continue;
            }
            List<Property> props = new ArrayList<>();
            for (String item : record.split(",", -1)) {
                int equal = item.indexOf('=');
                props.add(new Property(equal < 0 ? item : item.substring(0, equal), equal < 0 ? null : item.substring(equal + 1)));
            }
            records.add(Collections.unmodifiableList(props));
        }
        return Collections.unmodifiableList(records);
    }

    private List<String> names(String key) {
        String names = data.getString(key);
        if (names == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        if (!names.isEmpty()) {
            Collections.addAll(result, names.split(",", -1));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<List<Integer>> readTopology(Object raw) throws IOException {
        if (!(raw instanceof List)) {
            throw new IOException("IK topology must be a double array");
        }
        List<?> values = (List<?>) raw;
        List<List<Integer>> nodes = new ArrayList<>();
        int offset = 0;
        while (offset < values.size()) {
            int count = topologyInteger(values.get(offset++));
            if (count < 0 || count > values.size() - offset) {
                throw new IOException("Invalid IK child count");
            }
            List<Integer> children = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                children.add(topologyInteger(values.get(offset++)));
            }
            nodes.add(Collections.unmodifiableList(children));
        }
        for (List<Integer> children : nodes) {
            for (int child : children) {
                if (child < 0 || child >= nodes.size()) {
                    throw new IOException("IK child index outside node array");
                }
            }
        }
        return Collections.unmodifiableList(nodes);
    }

    private static int topologyInteger(Object v) throws IOException {
        if (!(v instanceof Double)) {
            throw new IOException("IK topology must contain doubles");
        }
        double n = (Double) v;
        if (!Double.isFinite(n) || n != Math.rint(n) || n < 0 || n > Integer.MAX_VALUE) {
            throw new IOException("Invalid IK topology integer");
        }
        return (int) n;
    }
}
