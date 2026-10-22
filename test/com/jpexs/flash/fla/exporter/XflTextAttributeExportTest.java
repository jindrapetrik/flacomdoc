package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.converter.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;
import static com.jpexs.flash.fla.exporter.XflIntegrationExportTest.*;

public class XflTextAttributeExportTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void nativeRunFlagsUseBitmapRenderingAndHistoricalScreenSpacing(String version) throws Exception {
        BinaryFlaDocument source = nativeFile(version, "0012_texts");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        List<Element> attrs = elements(xml(result), "DOMTextAttrs");
        int index = 0;
        for (FlaRecord text : records(source, "CPicText")) {
            for (FlaRecord run : XflXml.records(text, "runs")) {
                FlaRecord style = (FlaRecord) run.get("style");
                Element a = attrs.get(index++);
                assertEquals(a.getAttribute("aliasText"), Boolean.toString(XflXml.n(style, "bitmapRendering", 0) != 0));
                assertEquals(a.getAttribute("useScreenSpacing"), Boolean.toString(XflXml.n(style, "revision", 0) < 10 || XflXml.n(style, "useScreenSpacing", 0) != 0));
            }
        }
        assertTrue(index > 0);
        assertEquals(index, attrs.size());
        if (version.equals("cs4")) {
            List<Element> expected = elements(reference("0012_texts"), "DOMTextAttrs");
            assertEquals(attrs.size(), expected.size());
            for (int i = 0; i < attrs.size(); i++) {
                assertEquals(attrs.get(i).getAttribute("aliasText"), attr(expected.get(i), "aliasText", "true"));
            }
        }
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(file.getValue().toByteArray());
            }
        }
    }

    @DataProvider(name = "textModes")
    public Object[][] textModes() {
        return new Object[][]{{FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "textModes")
    public void independentAliasAndScreenSpacingFlagsSurviveForwardConversion(FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><timelines><DOMTimeline name='Scene 1'><layers><DOMLayer name='Text'><frames><DOMFrame><elements><DOMStaticText fontRenderingMode='standard'><textRuns><DOMTextRun><characters>A</characters><textAttrs><DOMTextAttrs face='_serif' aliasText='true' useScreenSpacing='false'/></textAttrs></DOMTextRun><DOMTextRun><characters>B</characters><textAttrs><DOMTextAttrs face='_serif' aliasText='false' useScreenSpacing='true'/></textAttrs></DOMTextRun></textRuns></DOMStaticText></elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines></DOMDocument>");
        BinaryFlaDocument source = convert(input, version);
        assertTrue(source.getIssues().isEmpty(), source.getIssues().toString());
        FlaRecord text = records(source, "CPicText").get(0);
        List<FlaRecord> runs = XflXml.records(text, "runs");
        assertEquals(runs.size(), 2);
        for (int i = 0; i < 2; i++) {
            FlaRecord style = (FlaRecord) runs.get(i).get("style");
            assertEquals(XflXml.n(style, "bitmapRendering", -1), i == 0 ? 1L : 0L);
            assertEquals(XflXml.n(style, "useScreenSpacing", -1), i == 0 ? 0L : 1L);
        }
        List<Element> attrs = elements(xml(new BinaryFlaToXflExporter().export(source)), "DOMTextAttrs");
        assertEquals(attrs.size(), 2);
        for (int i = 0; i < 2; i++) {
            assertEquals(attrs.get(i).getAttribute("aliasText"), Boolean.toString(i == 0));
            assertEquals(attrs.get(i).getAttribute("useScreenSpacing"), Boolean.toString(XflXml.n((FlaRecord) runs.get(i).get("style"), "revision", 0) < 10 || i == 1));
        }
    }

    @Test
    public void implicitAliasStillUsesParentBitmapRenderingMode() throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><timelines><DOMTimeline name='Scene 1'><layers><DOMLayer name='Text'><frames><DOMFrame><elements><DOMStaticText fontRenderingMode='bitmap'><textRuns><DOMTextRun><characters>A</characters><textAttrs><DOMTextAttrs face='_serif'/></textAttrs></DOMTextRun></textRuns></DOMStaticText></elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines></DOMDocument>");
        BinaryFlaDocument source = convert(input, FlaFormatVersion.CS4);
        FlaRecord style = (FlaRecord) XflXml.records(records(source, "CPicText").get(0), "runs").get(0).get("style");
        assertEquals(XflXml.n(style, "bitmapRendering", -1), 1L);
    }

    @Test
    public void invalidScreenSpacingBooleanStillFailsSchemaValidation() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile("cs4", "0012_texts"));
        String valid = new String(result.getFiles().get("DOMDocument.xml").toByteArray(), StandardCharsets.UTF_8);
        String invalid = valid.replaceFirst("useScreenSpacing=\"(?:true|false)\"", "useScreenSpacing=\"invalid\"");
        assertNotEquals(invalid, valid);
        try {
            XflSchemaTestSupport.validate(invalid.getBytes(StandardCharsets.UTF_8));
            fail("Invalid boolean accepted");
        } catch (org.xml.sax.SAXException expected) {
        }
    }

    @Test
    public void documentAutoLabelingMatchesOriginalAdobeAndSurvivesForwardConversion() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0029_accessibility");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertEquals(xml(result).getAttribute("autoLabeling"), reference("0029_accessibility").getAttribute("autoLabeling"));
        assertEquals(xml(result).getAttribute("autoLabeling"), "false");
        BinaryFlaDocument second = convert(result, FlaFormatVersion.CS4);
        assertEquals(XflXml.n((FlaRecord) second.getContents().get("accessibility"), "autoLabeling", -1), 1L);
    }

    @Test
    public void accessibilityUsesWholeBooleanWordsAndInvertsAutoLabelingWithoutMutatingSource() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0029_accessibility");
        FlaRecord access = (FlaRecord) source.getContents().get("accessibility");
        access.set("flags", 0x80000000L);
        access.set("forceSimple", 0x80000000L);
        access.set("autoLabeling", 0x80000000L);
        Element root = xml(new BinaryFlaToXflExporter().export(source));
        assertEquals(root.getAttribute("silent"), "true");
        assertEquals(root.getAttribute("forceSimple"), "true");
        assertEquals(root.getAttribute("autoLabeling"), "false");
        assertEquals(access.get("autoLabeling"), 0x80000000L);
        access.set("autoLabeling", 0);
        assertEquals(xml(new BinaryFlaToXflExporter().export(source)).getAttribute("autoLabeling"), "true");
    }
}
