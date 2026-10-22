package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflGuidesExportTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void documentGuidesMatchNativePositionsAndDirections(String version) throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile(version, "0020_document_params"));
        Element timeline = elements(xml(result), "DOMTimeline").get(0), paired = elements(reference("0020_document_params"), "DOMTimeline").get(0);
        assertEquals(timeline.getAttribute("guides"), paired.getAttribute("guides"));
        assertFalse(has(result, "GUIDES"), result.getReport());
    }

    @Test
    public void unknownGuideDirectionDoesNotBecomeAnInventedOrientation() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0020_document_params");
        doc.getPages().get(0).getRoot().set("guides", Arrays.asList(Arrays.asList(7, 1200)));
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertTrue(has(result, "GUIDES"));
        assertFalse(elements(xml(result), "DOMTimeline").get(0).hasAttribute("guides"));
    }
}
