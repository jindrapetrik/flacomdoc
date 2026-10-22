package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.converter.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;
import static com.jpexs.flash.fla.exporter.XflIntegrationExportTest.*;

public class XflDocumentExportTest {

    @Test
    public void documentXmlFilesKeepUnicodeAndXmpPacketInTheExportedFiles() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        String metadata = "<?xpacket begin='\uFEFF' id='W5M0MpCehiHzreSzNTczkc9d'?>\n"
                + "<x:xmpmeta xmlns:x='adobe:ns:meta/'>\n"
                + "  <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n"
                + "    <rdf:Description xmlns:dc='http://purl.org/dc/elements/1.1/'>"
                + "<dc:title><![CDATA[Příliš žluťoučký & \uD83D\uDE00]]></dc:title></rdf:Description>\n"
                + "  </rdf:RDF>\n</x:xmpmeta>\n   \n<?xpacket end='w'?>";
        String mobile = "<?xml version='1.0' encoding='UTF-8'?>\r\n"
                + "<settings profile='Příliš žluťoučký &amp; \uD83D\uDE00'>\r\n"
                + "  <!--keep spacing--> <device><![CDATA[<mobile> & test]]></device>\r\n</settings>\r\n";
        source.getContents().set("mobileSettingsXml", mobile);
        source.getContents().set("metadataXml", metadata);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        byte[] expected = metadata.getBytes(StandardCharsets.UTF_8);
        BinaryData file = result.getFiles().get("META-INF/metadata.xml");
        assertNotNull(file);
        assertEquals(file.toByteArray(), expected);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assertEquals(factory.newDocumentBuilder().parse(new ByteArrayInputStream(file.toByteArray()))
                .getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title").item(0).getTextContent(),
                "Příliš žluťoučký & \uD83D\uDE00");
        assertEquals(source.getContents().get("metadataXml"), metadata);
        assertEquals(result.getFiles().get("MobileSettings.xml").toByteArray(), mobile.getBytes(StandardCharsets.UTF_8));
        assertEquals(factory.newDocumentBuilder().parse(result.getFiles().get("MobileSettings.xml").openStream())
                .getDocumentElement().getAttribute("profile"), "Příliš žluťoučký & \uD83D\uDE00");
        assertEquals(source.getContents().get("mobileSettingsXml"), mobile);

        Path parent = Files.createTempDirectory("xfl-metadata-");
        Path output = parent.resolve("export");
        try {
            result.writeToDirectory(output.toFile());
            assertEquals(Files.readAllBytes(output.resolve("META-INF").resolve("metadata.xml")), expected);
            assertEquals(Files.readAllBytes(output.resolve("MobileSettings.xml")), mobile.getBytes(StandardCharsets.UTF_8));
        } finally {
            try (Stream<Path> paths = Files.walk(parent)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(path);
                }
            }
        }
    }

    @Test
    public void nativeDocumentMetadataIsPreserved() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        String metadata = (String) source.getContents().get("metadataXml");
        assertNotNull(metadata);
        assertFalse(metadata.isEmpty());
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertEquals(new String(result.getFiles().get("META-INF/metadata.xml").toByteArray(), StandardCharsets.UTF_8), metadata);
    }

    @DataProvider(name = "absentXml")
    public Object[][] absentXml() {
        return new Object[][]{{null}, {""}};
    }

    @Test(dataProvider = "absentXml")
    public void emptyOrMissingMetadataDoesNotCreateAFile(String metadata) throws Exception {
        BinaryFlaDocument source = nativeFile("f5", "0020_document_params");
        if (metadata != null) {
            source.getContents().set("metadataXml", metadata);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertFalse(result.getFiles().keySet().stream().anyMatch(path -> path.startsWith("META-INF/")));
    }

    @Test(dataProvider = "absentXml")
    public void emptyOrMissingMobileSettingsDoesNotCreateAFile(String mobile) throws Exception {
        BinaryFlaDocument source = nativeFile("f5", "0020_document_params");
        if (mobile != null) {
            source.getContents().set("mobileSettingsXml", mobile);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertFalse(result.getFiles().containsKey("MobileSettings.xml"));
    }

    @Test
    public void layerOutlineModeMatchesPairedNativeAndSurvivesForwardConversion() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0007_layers");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element actual = xml(result), expected = reference("0007_layers");
        List<Element> a = elements(actual, "DOMLayer"), b = elements(expected, "DOMLayer");
        assertEquals(a.size(), b.size());
        boolean found = false;
        for (int i = 0; i < a.size(); i++) {
            boolean mode = "true".equals(b.get(i).getAttribute("useOutlineView"));
            found |= mode;
            assertEquals(a.get(i).getAttribute("useOutlineView"), Boolean.toString(mode));
            assertEquals(a.get(i).getAttribute("outline"), Boolean.toString(mode));
        }
        assertTrue(found);
        Element second = xml(new BinaryFlaToXflExporter().export(convert(result, FlaFormatVersion.CS4)));
        List<Element> c = elements(second, "DOMLayer");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(c.get(i).getAttribute("useOutlineView"), a.get(i).getAttribute("useOutlineView"));
        }
    }

    @Test
    public void layerOutlineUsesTheWholeStoredBooleanWord() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0007_layers");
        FlaRecord layer = XflIkExportTest.find(source, "CPicLayer");
        layer.set("outline", 0x80000000L);
        Element root = xml(new BinaryFlaToXflExporter().export(source));
        boolean found = false;
        for (Element e : elements(root, "DOMLayer")) {
            if (e.getAttribute("name").equals(layer.get("name"))) {
                assertEquals(e.getAttribute("useOutlineView"), "true");
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void frameAnchorBookmarkMatchesNativeDocument() throws Exception {
        Element actual = xml(new BinaryFlaToXflExporter().export(nativeFile("cs4", "0006_frames"))), expected = reference("0006_frames");
        List<Element> a = elements(actual, "DOMFrame"), b = elements(expected, "DOMFrame");
        assertEquals(a.size(), b.size());
        boolean found = false;
        for (int i = 0; i < a.size(); i++) {
            if ("anchor".equals(b.get(i).getAttribute("labelType"))) {
                assertEquals(a.get(i).getAttribute("labelType"), "anchor");
                assertEquals(a.get(i).getAttribute("bookmark"), b.get(i).getAttribute("bookmark"));
                found = true;
            }
        }
        assertTrue(found);
        XflSchemaTestSupport.validate(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("testdata/fla/cs5/0006_frames/DOMDocument.xml")));
    }

    @Test
    public void bookmarkBooleanTakesPrecedenceOverCommentAndKeepsUnnamedAnchors() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0006_frames");
        FlaRecord frame = XflIkExportTest.find(source, "CPicFrame");
        frame.set("bookmark", 0x80000000L);
        frame.set("labelType", 1);
        frame.set("name", "");
        Element f = elements(xml(new BinaryFlaToXflExporter().export(source)), "DOMFrame").get(0);
        assertEquals(f.getAttribute("labelType"), "anchor");
        assertEquals(f.getAttribute("bookmark"), "true");
        assertTrue(f.hasAttribute("name"));
        assertEquals(f.getAttribute("name"), "");
    }

    @Test
    public void shapesInsideSelectedGroupsRetainSelection() throws Exception {
        Element actual = xml(new BinaryFlaToXflExporter().export(nativeFile("cs4", "0027_groups"))), expected = reference("0027_groups");
        List<Element> a = elements(actual, "DOMShape"), b = elements(expected, "DOMShape");
        assertEquals(a.size(), b.size());
        int selected = 0;
        for (int i = 0; i < a.size(); i++) {
            boolean selection = "true".equals(b.get(i).getAttribute("selected"));
            if (selection) {
                selected++;
            }
            assertEquals(a.get(i).getAttribute("selected"), Boolean.toString(selection));
        }
        assertEquals(selected, 2);
    }

    @DataProvider(name = "vanishingFractions")
    public Object[][] vanishingFractions() {
        return new Object[][]{{0.025, 0}, {0.0449, 0}, {0.0451, 1}};
    }

    @Test(dataProvider = "vanishingFractions")
    public void vanishingPointsFollowNativeXmlImportTruncation(double pixels, int twips) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument vanishingPoint3DX='" + pixels + "' vanishingPoint3DY='" + (-pixels) + "'><timelines><DOMTimeline name='Scene 1'><layers/></DOMTimeline></timelines></DOMDocument>");
        BinaryFlaDocument source = convert(input, FlaFormatVersion.CS4);
        assertTrue(source.getIssues().isEmpty(), source.getIssues().toString());
        assertEquals(source.getContents().get("vanishingPointTwips"), Arrays.asList(twips, -twips));
    }

    @Test
    public void custom3DViewSettingsKeepSignedTwipsAndAdjustmentFlagInBothDirections() throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument viewAngle3D='37.5' vanishingPoint3DX='-0.05' vanishingPoint3DY='617.25' adjustViewAngle3D='false'><timelines><DOMTimeline name='Scene 1'><layers/></DOMTimeline></timelines></DOMDocument>");
        BinaryFlaDocument first = convert(input, FlaFormatVersion.CS4);
        assertTrue(first.getIssues().isEmpty(), first.getIssues().toString());
        assertEquals(first.getContents().get("vanishingPointTwips"), Arrays.asList(-1, 12345));
        assertEquals(((Number) first.getContents().get("adjustViewAngle3D")).intValue(), 0);
        assertEquals(((Number) first.getContents().get("viewAngle3D")).doubleValue(), 37.5);
        XflExportResult result = new BinaryFlaToXflExporter().export(first);
        Element root = xml(result);
        assertEquals(root.getAttribute("vanishingPoint3DX"), "-0.05");
        assertEquals(root.getAttribute("vanishingPoint3DY"), "617.25");
        assertEquals(root.getAttribute("viewAngle3D"), "37.5");
        assertEquals(root.getAttribute("adjustViewAngle3D"), "false");
        BinaryFlaDocument second = convert(result, FlaFormatVersion.CS4);
        assertTrue(second.getIssues().isEmpty(), second.getIssues().toString());
        for (String field : Arrays.asList("vanishingPointTwips", "adjustViewAngle3D", "viewAngle3D")) {
            assertEquals(second.getContents().get(field), first.getContents().get(field));
        }
    }

    @Test
    public void native3DDefaultsAreFixedValuesAndOutOfRangeSourceIsDiagnosed() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        Element initial = xml(new BinaryFlaToXflExporter().export(source));
        assertEquals(initial.getAttribute("vanishingPoint3DX"), "275");
        assertEquals(initial.getAttribute("vanishingPoint3DY"), "200");
        assertEquals(initial.getAttribute("adjustViewAngle3D"), "true");
        source.getContents().set("vanishingPointTwips", Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE));
        source.getContents().set("viewAngle3D", -7.0);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element root = xml(result);
        assertTrue(has(result, "DOCUMENT_SETTING"));
        assertEquals(root.getAttribute("viewAngle3D"), "1");
        assertEquals(root.getAttribute("vanishingPoint3DX"), "-115200");
        assertEquals(root.getAttribute("vanishingPoint3DY"), "115200");
    }

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void nativeEditorPreferencesMatchDocumentReference(String version) throws Exception {
        BinaryFlaDocument d = nativeFile(version, "0020_document_params");
        Element root = xml(new BinaryFlaToXflExporter().export(d)), paired = reference("0020_document_params");
        Map<String, String> defaults = new HashMap<>();
        defaults.put("guidesVisible", "true");
        defaults.put("snapAlign", "true");
        defaults.put("viewOptionsAnimationControlVisible", "true");
        defaults.put("viewOptionsButtonsActive", "false");
        defaults.put("viewOptionsPasteBoardView", "true");
        defaults.put("viewOptionsLivePreview", "true");
        defaults.put("playOptionsPlaySounds", "true");
        defaults.put("previewMode", "anti alias text");
        defaults.put("sharedLibraryURL", "");
        for (String name : Arrays.asList("gridColor", "guidesColor", "gridVisible", "rulerUnitType", "gridSpacingX", "gridSpacingY", "gridSnapAccuracy", "guidesSnapAccuracy", "gridSnapTo", "objectsSnapTo", "guidesLocked", "guidesVisible", "guidesSnapTo", "pixelSnap", "snapAlign", "snapAlignVerticalCenter", "snapAlignHorizontalCenter", "snapAlignHorizontalSpacing", "snapAlignVerticalSpacing", "snapAlignBorderSpacing", "rulerVisible", "previewMode", "viewOptionsAnimationControlVisible", "viewOptionsButtonsActive", "viewOptionsPasteBoardView", "viewOptionsLivePreview", "playOptionsPlayLoop", "playOptionsPlayPages", "playOptionsPlayFrameActions", "playOptionsPlaySounds", "sharedLibraryURL")) {
            if (root.hasAttribute(name)) {
                assertEquals(root.getAttribute(name), attr(paired, name, defaults.getOrDefault(name, "")), version + " " + name);
            }
        }
        assertEquals(Long.parseLong(root.getAttribute("nextSceneIdentifier")), ((Number) d.getContents().get("nextSceneId")).longValue());
        if (root.hasAttribute("viewAngle3D")) {
            assertEquals(Double.parseDouble(root.getAttribute("viewAngle3D")), Double.parseDouble(paired.getAttribute("viewAngle3D")));
        }
    }

    @Test(dataProvider = "versions")
    public void fractionalGridKeepsWholeTwipsThroughForwardAndReverseExport(String version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument gridSpacingX='15.25' gridSpacingY='25.05'><timelines><DOMTimeline name='Scene 1'><layers/></DOMTimeline></timelines></DOMDocument>");
        FlaFormatVersion target = FlaFormatVersion.valueOf(version.toUpperCase(Locale.ROOT));
        BinaryFlaDocument first = convert(input, target);
        XflExportResult exported = new BinaryFlaToXflExporter().export(first);
        Element root = xml(exported);
        assertEquals(root.getAttribute("gridSpacingX"), "15.25");
        assertEquals(root.getAttribute("gridSpacingY"), "25.05");
        BinaryFlaDocument second = convert(exported, target);
        assertTrue(second.getIssues().isEmpty(), second.getIssues().toString());
        assertEquals(second.getContents().get("gridSpacingXFixed16Twips"), first.getContents().get("gridSpacingXFixed16Twips"));
        assertEquals(second.getContents().get("gridSpacingYFixed16Twips"), first.getContents().get("gridSpacingYFixed16Twips"));
    }

    @DataProvider(name = "printerValues")
    public Object[][] printerValues() {
        return new Object[][]{{0, 0, 3, 180, "first frame only", "false", "3", "180"},
        {2, 255, 4, 725, "all frames", "true", "4", "725"},
        {1, 1, 128, 57600, "all frames", "true", "128", "57600"},
        {0, 0, 1, 0, "first frame only", "false", "1", "0"},
        {255, 128, 255, Integer.MAX_VALUE, "all frames", "true", "128", "57600"},
        {0, 0, 0, Integer.MIN_VALUE, "first frame only", "false", "1", "0"}};
    }

    @Test(dataProvider = "printerValues")
    public void printerLayoutExportsNativeEnumsBooleansAndWholeTwips(int all, int labels, int across, int margin,
            String expectedFrames, String expectedLabels, String expectedAcross, String expectedMargin) throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0020_document_params");
        FlaRecord r = doc.getContents();
        r.set("printAllFrames", all);
        r.set("printLabelPages", labels);
        r.set("printFrameCountAcrossPage", across);
        r.set("printStoryboardMarginTwips", margin);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element printer = elements(xml(result), "PrinterSettings").get(0);
        assertEquals(printer.getAttribute("framesToPrint"), expectedFrames);
        assertEquals(printer.getAttribute("labelPages"), expectedLabels);
        assertEquals(printer.getAttribute("frameCountAcrossPage"), expectedAcross);
        assertEquals(printer.getAttribute("storyBoardMargin"), expectedMargin);
        XflSchemaTestSupport.validate(result.getFiles().get("DOMDocument.xml").toByteArray());
        assertEquals(r.get("printFrameCountAcrossPage"), across);
        assertEquals(r.get("printStoryboardMarginTwips"), margin);
        assertEquals(has(result, "DOCUMENT_SETTING"), across < 1 || across > 128 || margin < 0 || margin > 57600);
    }

    @Test(dataProvider = "versions")
    public void nativePrinterFieldsReachXflAcrossTargetVersions(String version) throws Exception {
        BinaryFlaDocument doc = nativeFile(version, "0001_empty_doc");
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
        FlaRecord r = doc.getContents();
        for (String field : new String[]{"printAllFrames", "printLabelPages", "printFrameCountAcrossPage", "printStoryboardMarginTwips"}) {
            assertTrue(r.get(field) instanceof Number, version + ": " + field);
        }
        assertNull(r.get("flags1105"));
        assertNull(r.get("value1108"));
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element printer = elements(xml(result), "PrinterSettings").get(0);
        assertEquals(printer.getAttribute("framesToPrint"), ((Number) r.get("printAllFrames")).intValue() == 0 ? "first frame only" : "all frames");
        assertEquals(printer.getAttribute("labelPages"), Boolean.toString(((Number) r.get("printLabelPages")).intValue() != 0));
        assertEquals(printer.getAttribute("frameCountAcrossPage"), r.get("printFrameCountAcrossPage").toString());
        assertEquals(printer.getAttribute("storyBoardMargin"), r.get("printStoryboardMarginTwips").toString());
        XflSchemaTestSupport.validate(result.getFiles().get("DOMDocument.xml").toByteArray());
    }

    @Test
    public void absentPrinterLayoutFieldsStayAbsentInXml() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0001_empty_doc");
        for (String field : new String[]{"printAllFrames", "printLabelPages", "printFrameCountAcrossPage", "printStoryboardMarginTwips"}) {
            doc.getContents().set(field, null);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element printer = elements(xml(result), "PrinterSettings").get(0);
        for (String attr : new String[]{"framesToPrint", "labelPages", "frameCountAcrossPage", "storyBoardMargin"}) {
            assertFalse(printer.hasAttribute(attr));
        }
        XflSchemaTestSupport.validate(result.getFiles().get("DOMDocument.xml").toByteArray());
    }

    @DataProvider(name = "invalidPrinterAttributes")
    public Object[][] invalidPrinterAttributes() {
        return new Object[][]{{"framesToPrint", "all"}, {"labelPages", "yes"},
        {"frameCountAcrossPage", "0"}, {"frameCountAcrossPage", "129"},
        {"storyBoardMargin", "-1"}, {"storyBoardMargin", "57601"},
        {"storyBoardMargin", "9.5"}, {"inventedPrinterOption", "true"}};
    }

    @Test(dataProvider = "invalidPrinterAttributes")
    public void printerSchemaRejectsWrongEnumsRangesAndUnknownAttributes(String name, String value) throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile("cs4", "0001_empty_doc"));
        Element root = xml(result);
        elements(root, "PrinterSettings").get(0).setAttribute(name, value);
        try {
            XflSchemaTestSupport.validate(XflXml.serialize(root.getOwnerDocument()));
            fail("Schema accepted invalid PrinterSettings " + name + "=" + value);
        } catch (org.xml.sax.SAXException expected) {
        }
    }

    @Test
    public void archivedPrinterBlobsAreExternalFilesWithMatchingReferences() throws Exception {
        BinaryFlaDocument d = nativeFile("cs4", "0020_document_params");
        byte[] mode = {0, 1, (byte) 255, 0}, names = {5, 0, 6, 0};
        d.getContents().set("printerModeData", new BinaryData(mode));
        d.getContents().set("printerNamesData", new BinaryData(names));
        XflExportResult result = new BinaryFlaToXflExporter().export(d);
        Element printer = elements(xml(result), "PrinterSettings").get(0);
        assertEquals(result.getFiles().get(printer.getAttribute("modeData")).toByteArray(), mode);
        assertEquals(result.getFiles().get(printer.getAttribute("namesData")).toByteArray(), names);
    }

    @Test
    public void entireNativeDocumentWithGuidePreferencesValidates() throws Exception {
        XflSchemaTestSupport.validate(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("testdata/fla/cs5/0020_document_params/DOMDocument.xml")));
    }

    @Test
    public void incompatiblePrinterPayloadDoesNotGetAnActiveFileReference() throws Exception {
        BinaryFlaDocument d = nativeFile("cs4", "0020_document_params");
        d.getContents().set("printerModeData", new BinaryData(new byte[]{1, 2}));
        d.getContents().set("viewPlayAndPrinterFlags", new BinaryData(new byte[]{0, 1}));
        XflExportResult result = new BinaryFlaToXflExporter().export(d);
        assertTrue(has(result, "PRINTER_SETTINGS"));
        assertFalse(result.getFiles().containsKey("mode.prtdata"));
        Element printer = elements(xml(result), "PrinterSettings").get(0);
        assertFalse(printer.hasAttribute("modeData"));
        assertEquals(printer.getAttribute("storyBoardMargin"), d.getContents().get("printStoryboardMarginTwips").toString());
        assertTrue(printer.hasAttribute("framesToPrint"));
        assertTrue(printer.hasAttribute("labelPages"));
        assertTrue(printer.hasAttribute("frameCountAcrossPage"));
        XflSchemaTestSupport.validate(result.getFiles().get("DOMDocument.xml").toByteArray());
    }

    @Test
    public void invalidGridDoesNotProduceZeroOrNegativeXmlSpacing() throws Exception {
        BinaryFlaDocument d = nativeFile("cs4", "0020_document_params");
        d.getContents().set("gridSpacingXFixed16Twips", 0);
        d.getContents().set("gridSpacingYFixed16Twips", 0xffff0000L);
        XflExportResult result = new BinaryFlaToXflExporter().export(d);
        Element root = xml(result);
        assertFalse(root.hasAttribute("gridSpacingX"));
        assertFalse(root.hasAttribute("gridSpacingY"));
        assertTrue(has(result, "DOCUMENT_SETTING"));
    }
}
