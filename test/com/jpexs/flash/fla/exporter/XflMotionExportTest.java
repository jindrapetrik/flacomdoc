package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflMotionExportTest {

    @Test
    public void nativeMotionCurvesAndFrameSettingsMatchAdobe() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0022_motiontween-cs4");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result), paired = reference("0022_motiontween-cs4");
        List<Element> actual = elements(root, "DOMFrame"), expected = elements(paired, "DOMFrame");
        assertEquals(actual.size(), expected.size());
        for (int i = 0; i < actual.size(); i++) {
            Element a = actual.get(i), b = expected.get(i);
            for (String attr : Arrays.asList("tweenType", "isMotionObject", "visibleAnimationKeyframes", "tweenInstanceName", "motionTweenRotate", "motionTweenScale")) {
                assertEquals(a.getAttribute(attr), b.getAttribute(attr), attr);
            }
            Element aa = elements(a, "AnimationCore").get(0), bb = elements(b, "AnimationCore").get(0);
            trim(aa);
            trim(bb);
            assertTrue(aa.isEqualNode(bb));
        }
        for (Element layer : elements(root, "DOMLayer")) {
            assertEquals(layer.getAttribute("animationType"), "motion object");
        }
        assertFalse(has(result, "FRAME_ANIMATION"), result.getReport());
        assertFalse(has(result, "LAYER_ANIMATION"), result.getReport());
        assertFalse(has(result, "MOTION_XML"), result.getReport());
        XflSchemaTestSupport.validate(Files.readAllBytes(Paths.get("testdata/fla/cs5/0022_motiontween-cs4/DOMDocument.xml")));
    }

    private static void trim(Node node) {
        for (Node child = node.getFirstChild(); child != null;) {
            Node next = child.getNextSibling();
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else {
                trim(child);
            }
            child = next;
        }
    }

    @Test
    public void malformedMotionXmlIsAtomicAndNeverLoadsExternalEntities() throws Exception {
        for (String xml : Arrays.asList("<AnimationCore>", "<!DOCTYPE AnimationCore [<!ENTITY x SYSTEM 'file:///missing-file'>]><AnimationCore>&x;</AnimationCore>")) {
            BinaryFlaDocument doc = nativeFile("cs4", "0022_motiontween-cs4");
            for (FlaRecord frame : records(doc, "CPicFrame")) {
                frame.set("motionXml", xml);
            }
            XflExportResult result = new BinaryFlaToXflExporter().export(doc);
            assertTrue(has(result, "MOTION_XML"));
            assertTrue(elements(xml(result), "motionObjectXML").isEmpty());
        }
    }
}
