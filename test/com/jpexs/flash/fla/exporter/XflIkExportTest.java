package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.validation.*;
import javax.xml.transform.stream.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;

public class XflIkExportTest {

    static final String NS = "http://ns.adobe.com/xfl/2008/";
    static final String SAMPLE = "0023_inverse_kinematics-cs4-todo";

    @Test
    public void nativeSkinKeepsAllStoredPosesTopologyMatricesAndConnections() throws Exception {
        BinaryFlaDocument source = read();
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertFalse(result.getIssues().stream().anyMatch(i -> Arrays.asList("FRAME_ANIMATION", "LAYER_ANIMATION", "IK_AUTHORING", "SYMBOL_TYPE").contains(i.getCode())), result.getReport());
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_SAMPLE_CACHE")));
        validate(result);
        Element xml = parse(result.getFiles().get("DOMDocument.xml"));
        Element original = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5", SAMPLE, "DOMDocument.xml")));
        Element tree = first(xml, "IKTree"), nativeTree = first(original, "IKTree");
        assertNotNull(tree);
        for (String key : Arrays.asList("name", "treeName", "physicsEnabled")) {
            assertEquals(tree.getAttribute(key), nativeTree.getAttribute(key));
        }
        for (String key : Arrays.asList("connectionIndices", "connectionData")) {
            compareArray(tree.getAttribute(key), nativeTree.getAttribute(key), 0);
        }
        assertEquals(first(xml, "DOMLayer").getAttribute("animationType"), "IK pose");
        assertEquals(first(xml, "DOMLayer").getAttribute("poseLocations"), "0,9,19");
        assertEquals(first(xml, "DOMFrame").getAttribute("tweenType"), "IK pose");
        assertEquals(first(xml, "DOMFrame").getAttribute("isIKPose"), "true");
        NodeList actual = tree.getElementsByTagNameNS(NS, "ChildNode"), expected = nativeTree.getElementsByTagNameNS(NS, "ChildNode");
        assertEquals(actual.getLength(), 7);
        assertEquals(actual.getLength(), expected.getLength());
        for (int i = 0; i < actual.getLength(); i++) {
            Element a = (Element) actual.item(i), e = (Element) expected.item(i);
            assertEquals(a.getAttribute("name"), e.getAttribute("name"));
            assertEquals(a.getAttribute("boneName"), e.getAttribute("boneName"));
            comparePoint(a.getAttribute("location"), e.getAttribute("location"));
            assertFalse(a.hasAttribute("xArray"));
            assertFalse(a.hasAttribute("yArray"));
            assertFalse(a.hasAttribute("angleArray"));
            List<Element> aStates = children(direct(a, "states")), eStates = children(direct(e, "states"));
            assertEquals(aStates.size(), 3);
            for (int j = 0; j < 3; j++) {
                assertEquals(aStates.get(j).getAttribute("duration"), eStates.get(j).getAttribute("duration"));
                for (String key : Arrays.asList("x", "y", "xRel", "yRel", "angle")) {
                    assertEquals(numeric(aStates.get(j), key), numeric(eStates.get(j), key), 0.00002, key);
                }
            }
            Element am = first(direct(a, "worldMatrix"), "Matrix"), em = first(direct(e, "worldMatrix"), "Matrix");
            for (String key : Arrays.asList("a", "b", "c", "d", "tx", "ty")) {
                assertEquals(numeric(am, key), numeric(em, key), 0.000001, key);
            }
        }
        Element contour = first(tree, "IKContour"), nativeContour = first(nativeTree, "IKContour");
        NodeList points = contour.getElementsByTagNameNS(NS, "DataPoint"), nativePoints = nativeContour.getElementsByTagNameNS(NS, "DataPoint");
        assertEquals(points.getLength(), 151);
        assertEquals(points.getLength(), nativePoints.getLength());
        for (int i = 0; i < points.getLength(); i++) {
            comparePoint(((Element) points.item(i)).getAttribute("integerPoint"), ((Element) nativePoints.item(i)).getAttribute("integerPoint"));
        }
        assertEquals(first(contour, "SolidColor").getAttribute("alpha"), "0");
        assertEquals(first(contour, "SolidStroke").getAttribute("weight"), "0.1");
        assertEquals(first(xml, "betweenFrameMatrixList").getElementsByTagNameNS(NS, "Matrix").getLength(), 20);
        boolean library = false;
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().startsWith("LIBRARY/") && file.getKey().endsWith(".xml")) {
                Element item = parse(file.getValue());
                if (item.getAttribute("name").equals("lsd_1")) {
                    assertEquals(item.getAttribute("symbolType"), "ik container");
                    library = true;
                }
            }
        }
        assertTrue(library);
        // Exporting again must leave the source model unchanged and deterministic.
        assertEquals(new BinaryFlaToXflExporter().export(source).getFiles().get("DOMDocument.xml").toByteArray(), result.getFiles().get("DOMDocument.xml").toByteArray());
    }

    @DataProvider(name = "easing")
    public Object[][] easing() {
        return new Object[][]{{0, "none", -37}, {1, "simple quadratic", -37}, {2, "simple cubic", -37}, {3, "simple quartic", -37}, {4, "simple quintic", -37}, {5, "dual quadratic", -37}, {6, "dual cubic", -37}, {7, "dual quartic", -37}, {8, "dual quintic", -37}, {2, "simple cubic", -37.125}};
    }

    @Test(dataProvider = "easing")
    public void storedEasingSelectorsAndSignedStrengthArePreserved(int selector, String expected, double strength) throws Exception {
        BinaryFlaDocument source = read();
        for (FlaRecord owner : Arrays.asList(find(source, "CPicSymbol"), source.getContents())) {
            String key = owner == source.getContents() ? "Armature_6" : "_ikTreeXML";
            replace(owner, key, "easeType=\"0\"", "easeType=\"" + selector + "\"");
            replace(owner, key, "easeValue=\"0.000000\"", "easeValue=\"" + strength + "\"");
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        NodeList states = first(parse(result.getFiles().get("DOMDocument.xml")), "IKTree").getElementsByTagNameNS(NS, "IKState");
        for (int i = 0; i < states.getLength(); i++) {
            assertEquals(((Element) states.item(i)).getAttribute("easeType"), expected);
            assertEquals(Double.parseDouble(((Element) states.item(i)).getAttribute("easeValue")), strength);
        }
    }

    @DataProvider(name = "legacyEase")
    public Object[][] legacyEase() {
        return new Object[][]{{-100, 100}, {25, -30}, {0, 1}};
    }

    @Test(dataProvider = "legacyEase")
    public void legacyEaseWordsHaveNativeXflAttributes(int in, int out) throws Exception {
        BinaryFlaDocument source = read();
        for (FlaRecord owner : Arrays.asList(find(source, "CPicSymbol"), source.getContents())) {
            String key = owner == source.getContents() ? "Armature_6" : "_ikTreeXML";
            replace(owner, key, "easeIn=\"0\"", "easeIn=\"" + in + "\"");
            replace(owner, key, "easeOut=\"0\"", "easeOut=\"" + out + "\"");
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        NodeList states = first(parse(result.getFiles().get("DOMDocument.xml")), "IKTree").getElementsByTagNameNS(NS, "IKState");
        for (int i = 0; i < states.getLength(); i++) {
            Element state = (Element) states.item(i);
            assertEquals(state.getAttribute("easeIn"), in == 0 ? "" : Integer.toString(in));
            assertEquals(state.getAttribute("easeOut"), Integer.toString(out));
        }
    }

    @DataProvider(name = "invalid")
    public Object[][] invalid() {
        return new Object[][]{{"matrix"}, {"names"}, {"topology"}, {"poses"}, {"locked"}, {"easing"}, {"fill"}, {"constraint"}, {"nonfinite"}, {"missingNames"}, {"noncontiguous"}};
    }

    @Test(dataProvider = "invalid")
    public void incompleteSkinIsOmittedAtomicallyWhileBakedFramesSurvive(String kind) throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord placement = find(source, "CPicSymbol"), frame = find(source, "CPicFrame");
        switch (kind) {
            case "matrix":
                frame.set("poseMatrices", Collections.emptyList());
                break;
            case "names":
                change(placement, "lsd_node_names", "ikNode_8");
                break;
            case "topology":
                change(placement, "lsd_tree", Arrays.asList(0.0));
                break;
            case "poses":
                change(placement, "lsd_frame_data", Arrays.asList(0, 10, 19));
                break;
            case "locked":
                replace(placement, "_ikTreeXML", "value=\"false\"", "value=\"invalid\"");
                break;
            case "easing":
                replace(placement, "_ikTreeXML", "easeValue=\"0.000000\"", "easeValue=\"20\"");
                break;
            case "fill":
                replace(placement, "lsd_shape_contour_data", "style=\"solid\"", "style=\"linear\"");
                break;
            case "constraint":
                replace(placement, "lsd_motion_constraints", "speed=1.000000", "speed=NaN");
                break;
            case "nonfinite":
                replace(placement, "lsd_world_matrices", "tx=\"225.000000\"", "tx=\"NaN\"");
                break;
            case "missingNames":
                change(placement, "lsd_node_names", null);
                break;
            case "noncontiguous":
                replace(placement, "_ikTreeXML", "node_6", "node_7000");
                break;
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_SAMPLE_CACHE")));
        Element xml = parse(result.getFiles().get("DOMDocument.xml"));
        assertNull(first(xml, "IKTree"));
        assertNull(first(xml, "betweenFrameMatrixList"));
        assertFalse(first(xml, "DOMFrame").hasAttribute("isIKPose"));
        assertTrue(xml.getElementsByTagNameNS(NS, "DOMSymbolInstance").getLength() > 0);
    }

    @Test
    public void lockedSkinNodesKeepTheirAuthoringLockWithoutDiscardingTheRig() throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord placement = find(source, "CPicSymbol");
        replace(placement, "_ikTreeXML", "value=\"false\"", "value=\"true\"");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        NodeList nodes = first(parse(result.getFiles().get("DOMDocument.xml")), "IKTree").getElementsByTagNameNS(NS, "ChildNode");
        assertEquals(nodes.getLength(), 7);
        for (int i = 0; i < nodes.getLength(); i++) {
            assertEquals(((Element) nodes.item(i)).getAttribute("locked"), "true");
        }
        assertTrue((String.valueOf(entry(placement, "_ikTreeXML").get("value"))).contains("value=\"true\""));
    }

    @DataProvider(name = "strokeFlags")
    public Object[][] strokeFlags() {
        return new Object[][]{{true, false}, {false, true}, {true, true}};
    }

    @Test(dataProvider = "strokeFlags")
    public void skinContourKeepsPixelHintingAndSharpCornerFlags(boolean hinting, boolean sharp) throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord placement = find(source, "CPicSymbol");
        replace(placement, "lsd_shape_contour_data", "strokeHinting=\"false\"", "strokeHinting=\"" + hinting + "\"");
        replace(placement, "lsd_shape_contour_data", "breakAtCorners=\"false\"", "breakAtCorners=\"" + sharp + "\"");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        Element stroke = first(first(parse(result.getFiles().get("DOMDocument.xml")), "IKContour"), "SolidStroke");
        assertEquals(stroke.getAttribute("pixelHinting"), Boolean.toString(hinting));
        assertEquals(stroke.getAttribute("sharpCorners"), Boolean.toString(sharp));
    }

    @Test
    public void constraintsUseNativeNumericNodeSlotsRatherThanBranchTraversalOrder() throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord placement = find(source, "CPicSymbol");
        String first = "motionType=rotation,rotEnabled=false,rotConstrained=true,rotMin=-35.5,rotMax=67.25,motionType=xtrans,xtransEnabled=true,xtransConstrained=true,xtransMin=-2.5,xtransMax=12.75,speed=0.5";
        String second = "motionType=ytrans,ytransEnabled=true,ytransConstrained=true,ytransMin=-4.25,ytransMax=6.5,speed=0.75";
        change(placement, "lsd_motion_constraints", first + "%" + second + "%");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        NodeList nodes = first(parse(result.getFiles().get("DOMDocument.xml")), "IKTree").getElementsByTagNameNS(NS, "ChildNode");
        Element n0 = (Element) nodes.item(0), n1 = (Element) nodes.item(1), n4 = (Element) nodes.item(4);
        assertEquals(n0.getAttribute("rotationEnabled"), "false");
        assertEquals(n0.getAttribute("rotationMin"), "-35.5");
        assertEquals(n0.getAttribute("rotationMax"), "67.25");
        assertEquals(n0.getAttribute("xTranslationMin"), "-2.5");
        assertEquals(n0.getAttribute("speed"), "0.5");
        assertEquals(n1.getAttribute("yTranslationEnabled"), "true");
        assertEquals(n1.getAttribute("yTranslationMin"), "-4.25");
        assertEquals(n1.getAttribute("speed"), "0.75");
        assertFalse(n4.hasAttribute("yTranslationMin"));
        assertEquals(n4.getAttribute("speed"), "1");
        assertEquals(n4.getAttribute("rotationEnabled"), "true");
        assertEquals(entry(placement, "lsd_motion_constraints").get("value"), first + "%" + second + "%");
    }

    @Test
    public void repeatedMotionTypePropertiesRetainOrderAndStationaryDisablesAllMotion() throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord placement = find(source, "CPicSymbol");
        change(placement, "lsd_motion_constraints", "motionType=xtrans,rotEnabled=true,xtransEnabled=true,motionType=rotation,rotEnabled=true,motionType=stationary,ytransEnabled=true%");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        Element node = first(first(parse(result.getFiles().get("DOMDocument.xml")), "IKTree"), "ChildNode");
        for (String key : Arrays.asList("rotationEnabled", "xTranslationEnabled", "yTranslationEnabled")) {
            assertEquals(node.getAttribute(key), "false");
        }
    }

    @DataProvider(name = "contours")
    public Object[][] contours() {
        return new Object[][]{{"siblings"}, {"contained"}, {"containedSiblings"}, {"open"}};
    }

    @Test(dataProvider = "contours")
    public void multipleAndContainedContourChainsKeepAllTheirGeometry(String kind) throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord placement = find(source, "CPicSymbol");
        String original = String.valueOf(entry(placement, "lsd_shape_contour_data").get("value"));
        String copy = original.replace("interior=\"false\"", "interior=\"true\"");
        String data;
        int count;
        if (kind.equals("siblings")) {
            data = original + copy;
            count = 102;
        } else if (kind.equals("contained")) {
            data = original.replace("</Contour>", copy + "</Contour>");
            count = 102;
        } else if (kind.equals("containedSiblings")) {
            data = original.replace("</Contour>", copy + copy + "</Contour>");
            count = 153;
        } else {
            data = original.replace("closed=\"true\"", "closed=\"false\"");
            count = 51;
        }
        assertFalse(data.equals(original));
        change(placement, "lsd_shape_contour_data", data);
        @SuppressWarnings("unchecked")
        List<Integer> old = (List<Integer>) entry(placement, "lsd_connection_indices").get("values");
        List<Integer> indices = new ArrayList<>(old);
        indices.set(0, count);
        change(placement, "lsd_connection_indices", indices);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        validate(result);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_AUTHORING")), result.getReport());
        Element tree = first(parse(result.getFiles().get("DOMDocument.xml")), "IKTree");
        assertEquals(tree.getElementsByTagNameNS(NS, "DataPoint").getLength(), 151 * (count / 51));
        assertEquals(tree.getElementsByTagNameNS(NS, "IKContainedContour").getLength(), kind.startsWith("contained") ? 1 : 0);
        assertEquals(tree.getElementsByTagNameNS(NS, "NextContour").getLength(), kind.equals("siblings") || kind.equals("containedSiblings") ? 1 : 0);
        assertEquals(entry(placement, "lsd_shape_contour_data").get("value"), data);
    }

    static BinaryFlaDocument read() throws Exception {
        Path path = Paths.get("testdata/fla/cs4", SAMPLE);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> fs = Files.walk(path)) {
            for (Path p : (Iterable<Path>) fs.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        return new BinaryFlaReader().readStreams(streams);
    }

    static FlaRecord find(BinaryFlaDocument source, String name) {
        for (BinaryFlaDocument.Page page : source.getPages()) {
            FlaRecord found = find(page.getRoot(), name);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError(name);
    }

    static FlaRecord find(FlaRecord record, String name) {
        if (record.getClassName().equals(name)) {
            return record;
        }
        for (FlaRecord child : record.getChildren()) {
            FlaRecord result = find(child, name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static FlaRecord entry(FlaRecord owner, String name) {
        for (FlaRecord entry : (List<FlaRecord>) ((FlaRecord) owner.get("persistentData")).get("entries")) {
            if (name.equals(entry.get("name"))) {
                return entry;
            }
        }
        throw new AssertionError(name);
    }

    static void change(FlaRecord owner, String name, Object value) {
        FlaRecord entry = entry(owner, name);
        entry.set(value instanceof List ? "values" : "value", value);
    }

    static void replace(FlaRecord owner, String name, String from, String to) {
        FlaRecord entry = entry(owner, name);
        String old = (String) entry.get("value");
        assertTrue(old.contains(from));
        entry.set("value", old.replace(from, to));
    }

    static void validate(XflExportResult result) throws Exception {
        Schema schema = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File("testdata/xfl-schema/xfl.xsd"));
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml") && !file.getKey().equals("META-INF/metadata.xml")) {
                schema.newValidator().validate(new StreamSource(file.getValue().openStream()));
            }
        }
    }

    static Element parse(BinaryData data) throws Exception {
        return parse(data.toByteArray());
    }

    static Element parse(byte[] data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(data)).getDocumentElement();
    }

    static Element first(Element root, String name) {
        NodeList list = root.getElementsByTagNameNS(NS, name);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    static List<Element> children(Element root) {
        List<Element> result = new ArrayList<>();
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                result.add((Element) n);
            }
        }
        return result;
    }

    static Element direct(Element root, String name) {
        for (Element e : children(root)) {
            if (e.getLocalName().equals(name)) {
                return e;
            }
        }
        return null;
    }

    static double numeric(Element element, String key) {
        return element.hasAttribute(key) ? Double.parseDouble(element.getAttribute(key)) : Arrays.asList("a", "d").contains(key) ? 1 : 0;
    }

    static void compareArray(String a, String b, double delta) {
        String[] aa = a.split(","), bb = b.split(",");
        assertEquals(aa.length, bb.length);
        for (int i = 0; i < aa.length; i++) {
            assertEquals(Double.parseDouble(aa[i]), Double.parseDouble(bb[i]), delta);
        }
    }

    static void comparePoint(String a, String b) {
        String[] aa = a.split(","), bb = b.split(",");
        for (int i = 0; i < 2; i++) {
            assertEquals(point(aa[i].trim()), point(bb[i].trim()), 0);
        }
    }

    static double point(String value) {
        if (!value.startsWith("#")) {
            return Double.parseDouble(value);
        }
        String[] pieces = value.substring(1).split("\\.");
        long integer = Long.parseLong(pieces[0], 16);
        if (integer >= 0x800000) {
            integer -= 0x1000000;
        }
        return integer + (pieces.length == 1 ? 0 : Long.parseLong(pieces[1], 16) / Math.pow(16, pieces[1].length()));
    }
}
