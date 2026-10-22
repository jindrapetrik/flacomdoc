package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.exporter.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.validation.*;
import javax.xml.transform.stream.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.nativeDocument;

public class XflSymbolIkExportTest {

    static final String NS = "http://ns.adobe.com/xfl/2008/", ROOT = "10000000-0000-0000-0000-000000000001", CHILD = "10000000-0000-0000-0000-000000000002";
    static final String ARM = "Armature_6", TREE = "ikTreeName5";
    static final int[] SPANS = {9, 10, 1};

    static final class Fixture {

        BinaryFlaDocument document;
        FlaRecord frame, root, child;
    }

    @Test
    public void symbolArmatureRestoresTopologyPivotsPosesReferencesAndConstraints() throws Exception {
        Fixture f = fixture();
        byte[] before = ((FlaRecord) f.root.get("persistentData")).getConsumedData().toByteArray();
        XflExportResult result = new BinaryFlaToXflExporter().export(f.document);
        Element xml = validate(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        Element tree = (Element) xml.getElementsByTagNameNS(NS, "IKTree").item(0);
        assertNotNull(tree);
        Element root = (Element) tree.getElementsByTagNameNS(NS, "IKNode").item(0), child = (Element) tree.getElementsByTagNameNS(NS, "ChildNode").item(0);
        assertEquals(root.getAttribute("name"), ROOT);
        assertEquals(root.getAttribute("referenceID"), ROOT);
        assertEquals(child.getAttribute("referenceID"), CHILD);
        assertEquals(root.getAttribute("boneName"), "rootBone");
        assertEquals(child.getAttribute("boneName"), "childBone");
        assertEquals(root.getAttribute("nodeType"), "ik");
        assertEquals(root.getAttribute("location"), "8.203125, 15.3125");
        assertEquals(child.getAttribute("rotationMin"), "-30");
        assertEquals(child.getAttribute("rotationMax"), "45");
        assertEquals(child.getAttribute("rotationConstrained"), "true");
        NodeList states = root.getElementsByTagNameNS(NS, "State");
        assertEquals(states.getLength(), 6);
        Element first = (Element) states.item(0);
        assertEquals(first.getAttribute("x"), "200");
        assertEquals(first.getAttribute("yRel"), "-40");
        assertEquals(first.getAttribute("angle"), "0.25");
        assertEquals(tree.getElementsByTagNameNS(NS, "IKState").getLength(), 3);
        NodeList refs = xml.getElementsByTagNameNS(NS, "DOMSymbolInstance");
        int linked = 0;
        for (int i = 0; i < refs.getLength(); i++) {
            if (((Element) refs.item(i)).hasAttribute("referenceID")) {
                linked++;
            }
        }
        assertEquals(linked, 2);
        assertEquals(((FlaRecord) f.root.get("persistentData")).getConsumedData().toByteArray(), before);
        assertEquals(new BinaryFlaToXflExporter().export(f.document).getFiles().get("DOMDocument.xml").toByteArray(), result.getFiles().get("DOMDocument.xml").toByteArray());
    }

    @Test
    public void anonymousAndDuplicateInstanceNamesUseIdentityReferences() throws Exception {
        for (String name : Arrays.asList("", "same")) {
            Fixture f = fixture();
            f.root.set("instanceName", name);
            f.child.set("instanceName", name);
            XflExportResult result = new BinaryFlaToXflExporter().export(f.document);
            Element xml = validate(result);
            assertFalse(has(result, "IK_AUTHORING"), result.getReport());
            assertEquals(xml.getElementsByTagNameNS(NS, "ChildNode").getLength(), 1);
        }
    }

    @Test
    public void nodePoseEasingIsIndependentOfTheArmaturePoseEasing() throws Exception {
        Fixture f = fixture();
        set(f.child, CHILD + "_ikNodeStates", states(3).replace("easeIn=0", "easeIn=17").replace("easeOut=0", "easeOut=-23").replace("easeType=0", "easeType=4").replace("easeValue=0", "easeValue=12.5"));
        XflExportResult result = new BinaryFlaToXflExporter().export(f.document);
        Element xml = validate(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        Element child = (Element) xml.getElementsByTagNameNS(NS, "ChildNode").item(0);
        Element state = (Element) child.getElementsByTagNameNS(NS, "State").item(0);
        assertEquals(state.getAttribute("easeIn"), "17");
        assertEquals(state.getAttribute("easeOut"), "-23");
        assertEquals(state.getAttribute("easeType"), "simple quintic");
        assertEquals(state.getAttribute("easeValue"), "12.5");
        Element global = (Element) xml.getElementsByTagNameNS(NS, "IKState").item(0);
        assertEquals(global.getAttribute("easeIn"), "");
        assertEquals(global.getAttribute("easeValue"), "0");
    }

    @DataProvider(name = "speed")
    public Object[][] speed() {
        return new Object[][]{{0.0}, {2.5}, {100.0}};
    }

    @Test(dataProvider = "speed")
    public void speedUsesTheNativeXmlRange(double speed) throws Exception {
        Fixture f = fixture();
        set(f.root, "ikNode_motionConstraints", "speed=" + speed);
        XflExportResult result = new BinaryFlaToXflExporter().export(f.document);
        Element xml = validate(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        assertEquals(Double.parseDouble(((Element) xml.getElementsByTagNameNS(NS, "IKNode").item(0)).getAttribute("speed")), speed);
    }

    @Test
    public void pinPointsUseTheirOwnNativeContainer() throws Exception {
        Fixture f = fixture();
        set(f.root, "ikNode_pins", "1.5,-2.5,3,4");
        XflExportResult result = new BinaryFlaToXflExporter().export(f.document);
        Element xml = validate(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        NodeList pins = xml.getElementsByTagNameNS(NS, "Pin");
        assertEquals(pins.getLength(), 2);
        assertEquals(((Element) pins.item(0)).getAttribute("integerPoint"), "0.1171875, -0.1953125");
    }

    @DataProvider(name = "broken")
    public Object[][] broken() {
        return new Object[][]{{"cycle"}, {"parent"}, {"root"}, {"missingChild"}, {"poses"}, {"nonfinite"}, {"identity"}, {"bone"}, {"pivot"}, {"pins"}, {"constraint"}, {"duplicateReference"}};
    }

    @Test(dataProvider = "broken")
    public void malformedSymbolArmaturesLeaveBakedFramesAndNoPartialRig(String fault) throws Exception {
        Fixture f = fixture();
        switch (fault) {
            case "cycle":
                set(f.child, "ikNode_children", ROOT);
                break;
            case "parent":
                set(f.child, "ikNode_parent", "unknown");
                break;
            case "root":
                set(f.document.getContents(), TREE, "missing");
                break;
            case "missingChild":
                set(f.root, "ikNode_children", "missing");
                break;
            case "poses":
                set(f.child, CHILD + "_ikNodeStates", states(1));
                break;
            case "nonfinite":
                set(f.child, CHILD + "_ikNodeStates", states(3).replace("angle=0.25", "angle=NaN"));
                break;
            case "identity":
                f.child.set("identifier", "");
                break;
            case "bone":
                set(f.child, "ikBoneName", "rootBone");
                break;
            case "pivot":
                set(f.root, "ik_tp", "one,two");
                break;
            case "pins":
                set(f.root, "ikNode_pins", "1,2,3");
                break;
            case "constraint":
                set(f.root, "ikNode_motionConstraints", "unknown=1");
                break;
            case "duplicateReference":
                f.child.set("identifier", ROOT);
                break;
            default:
                throw new AssertionError(fault);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(f.document);
        Element xml = validate(result);
        assertTrue(has(result, "IK_AUTHORING"), result.getReport());
        assertEquals(xml.getElementsByTagNameNS(NS, "IKTree").getLength(), 0);
        NodeList objects = xml.getElementsByTagNameNS(NS, "DOMSymbolInstance");
        assertEquals(objects.getLength(), 2);
    }

    static Fixture fixture() throws Exception {
        Fixture f = new Fixture();
        f.document = nativeDocument("fla/cs4/0023_inverse_kinematics-cs4-todo");
        for (FlaRecord r : SemanticTestSupport.records(f.document)) {
            if (r.getClassName().equals("CPicFrame") && (((Number) r.get("keyMode")).longValue() & 255) == 4) {
                f.frame = r;
                break;
            }
        }
        assertNotNull(f.frame);
        f.root = f.frame.getChildren().get(0);
        f.child = SemanticTestSupport.record("CPicSymbol");
        for (Map.Entry<String, Object> field : f.root.getFields().entrySet()) {
            f.child.set(field.getKey(), field.getValue());
        }
        f.child.finish(0, true);
        f.root.set("identifier", ROOT);
        f.root.set("instanceName", "rootInstance");
        f.root.set("matrixWords", Arrays.asList(65536, 0, 0, 65536, 2000, 4000));
        f.child.set("identifier", CHILD);
        f.child.set("instanceName", "childInstance");
        f.child.set("matrixWords", Arrays.asList(65536, 0, 0, 65536, 2200, 4200));
        f.root.set("persistentData", data(ROOT, "rootBone", null, CHILD));
        f.child.set("persistentData", data(CHILD, "childBone", ROOT, null));
        f.frame.set("children", Arrays.asList(f.root, f.child));
        List<FlaRecord> entries = new ArrayList<>();
        FlaRecord old = (FlaRecord) f.document.getContents().get("persistentData");
        for (FlaRecord entry : records(old, "entries")) {
            if (!ARM.equals(entry.get("name")) && !TREE.equals(entry.get("name"))) {
                entries.add(entry);
            }
        }
        StringBuilder poses = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            poses.append("<state duration='").append(SPANS[i]).append("' easeIn='0' easeOut='0' easeType='0' easeValue='0' index='").append(i).append("'/>");
        }
        FlaRecord doc = dataMap(new String[][]{{ARM, "<BridgeTree name='" + ARM + "' displayStyle='2' exportType='authortime' startFrame='0'><_ikTreeStates>" + poses + "</_ikTreeStates><Nodes><BridgeNode subTreeName='" + TREE + "' subTreeType='ik'/></Nodes></BridgeTree>"}, {TREE, ROOT}});
        entries.addAll(records(doc, "entries"));
        doc.set("entries", entries);
        f.document.getContents().set("persistentData", doc);
        return f;
    }

    static FlaRecord data(String id, String bone, String parent, String children) throws Exception {
        return dataMap(new String[][]{{"ikNode_", id}, {"ikBoneName", bone}, {"ikNode_parent", parent == null ? "" : parent}, {"ikNode_children", children == null ? "" : children}, {"ikTreeName", TREE}, {"Armature_", ARM}, {"ik_tp", "5,-4"}, {id + "_ikNodeStates", states(3)}, {"ikNode_motionConstraints", "motionType=rotation,rotEnabled=true,rotConstrained=true,rotMin=-30,rotMax=45,speed=0.75"}, {"ikNode_pins", ""}});
    }

    static String states(int count) {
        StringJoiner s = new StringJoiner(";");
        for (int i = 0; i < count; i++) {
            s.add("duration=" + SPANS[i] + ",angle=0.25,x=10,y=20,xRel=1,yRel=-2,easeIn=0,easeOut=0,easeType=0,easeValue=0");
        }
        return s.toString();
    }

    static FlaRecord dataMap(String[][] fields) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 0);
        u32(out, fields.length);
        for (String[] field : fields) {
            out.write(5);
            string(out, field[0], true);
            string(out, field[1], true);
        }
        MfcArchive a = archive(out.toByteArray());
        FlaRecord result = a.readInline("persistentData", r -> {
        }, DocumentTailRecordDecoder::persistentData);
        assertEquals(a.getRemaining(), 0);
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<FlaRecord> records(FlaRecord r, String field) {
        return (List<FlaRecord>) r.get(field);
    }

    static void set(FlaRecord owner, String name, String value) {
        for (FlaRecord entry : records((FlaRecord) owner.get("persistentData"), "entries")) {
            if (name.equals(entry.get("name"))) {
                entry.set("value", value);
                return;
            }
        }
        throw new AssertionError(name);
    }

    static boolean has(XflExportResult result, String code) {
        return result.getIssues().stream().anyMatch(i -> i.getCode().equals(code));
    }

    static Element validate(XflExportResult result) throws Exception {
        Schema schema = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File("testdata/xfl-schema/xfl.xsd"));
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml") && !file.getKey().equals("META-INF/metadata.xml")) {
                schema.newValidator().validate(new StreamSource(file.getValue().openStream()));
            }
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(result.getFiles().get("DOMDocument.xml").openStream()).getDocumentElement();
    }
}
