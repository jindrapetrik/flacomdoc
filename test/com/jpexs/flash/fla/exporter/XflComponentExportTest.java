package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflComponentExportTest {

    @Test
    public void nativeComponentRetainsClassPreviewIconAndInspectableValues() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0025_components-todo");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element paired = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0025_components-todo/LIBRARY/Button.xml"))), component = null;
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().startsWith("LIBRARY/") && f.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(f.getValue().toByteArray());
                Element root = parse(f.getValue().toByteArray());
                if (root.getLocalName().equals("DOMComponentItem")) {
                    component = root;
                }
            }
        }
        assertNotNull(component, result.getReport());
        for (String attr : Arrays.asList("name", "tooltip", "actionscriptClass", "customIconID", "editFrameIndex", "requiredMinimumPlayerVersion", "requiredMinimumASVersion", "parametersAreLocked")) {
            assertEquals(component.getAttribute(attr), paired.getAttribute(attr), attr);
        }
        assertEquals(elements(component, "classProperties").get(0).getTextContent(), elements(paired, "classProperties").get(0).getTextContent());
        Element parameters = parse(("<root>" + elements(component, "parametersAsXML").get(0).getTextContent() + "</root>").getBytes(StandardCharsets.UTF_8));
        Element expected = parse(("<root>" + elements(paired, "parametersAsXML").get(0).getTextContent() + "</root>").getBytes(StandardCharsets.UTF_8));
        org.w3c.dom.NodeList a = parameters.getElementsByTagName("Inspectable"), b = expected.getElementsByTagName("Inspectable");
        assertEquals(a.getLength(), 7);
        assertEquals(a.getLength(), b.getLength());
        for (int i = 0; i < a.getLength(); i++) {
            for (String attr : Arrays.asList("name", "variable", "category", "verbose", "defaultValue", "type", "enumeration")) {
                assertEquals(((Element) a.item(i)).getAttribute(attr), ((Element) b.item(i)).getAttribute(attr), attr);
            }
        }
        FlaRecord c = (FlaRecord) doc.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.LIBRARY && "Button".equals(p.getDescriptor().get("name"))).findFirst().get().getDescriptor().get("component");
        assertEquals(result.getFiles().get("bin/" + component.getAttribute("livePreviewScmHRef")).toByteArray(), ((BinaryData) XflXml.records(c, "movies").get(1).get("data")).toByteArray());
        Element icon = elements(component, "CustomIcon").get(0);
        assertEquals(result.getFiles().get("bin/" + icon.getAttribute("href")).toByteArray(), Files.readAllBytes(Paths.get("testdata/fla/cs5/0025_components-todo/bin/hadr6x0lui.dat")));
        assertFalse(has(result, "COMPONENT_PARAMETERS"), result.getReport());
        Element placement = elements(xml(result), "DOMComponentInstance").get(0), expectedPlacement = elements(reference("0025_components-todo"), "DOMComponentInstance").get(0);
        for (String attr : Arrays.asList("libraryItemName", "uniqueID", "name", "centerPoint3DX", "centerPoint3DY")) {
            assertEquals(placement.getAttribute(attr), expectedPlacement.getAttribute(attr), attr);
        }
        assertEquals(elements(placement, "dataBindingXML").get(0).getTextContent(), elements(expectedPlacement, "dataBindingXML").get(0).getTextContent());
        assertEquals(elements(placement, "parametersAsXML").size(), 1);
        assertEquals(elements(placement, "parametersAsXML").get(0).getFirstChild().getNodeType(), org.w3c.dom.Node.CDATA_SECTION_NODE);
        assertEquals(elements(component, "parametersAsXML").get(0).getFirstChild().getNodeType(), org.w3c.dom.Node.CDATA_SECTION_NODE);
        XflSchemaTestSupport.validate(Files.readAllBytes(Paths.get("testdata/fla/cs5/0025_components-todo/DOMDocument.xml")));
    }

    @DataProvider(name = "detection")
    public Object[][] detection() {
        return new Object[][]{{"parameters", true}, {"previewName", true}, {"definitionName", true}, {"preview11Name", true}, {"primaryOnly", false}, {"empty", false}};
    }

    @Test(dataProvider = "detection")
    public void historicalComponentRecognitionUsesParameterAndPreviewPresence(String kind, boolean expected) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0025_components-todo");
        FlaRecord descriptor = source.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.LIBRARY && "Button".equals(p.getDescriptor().get("name"))).findFirst().get().getDescriptor();
        FlaRecord component = (FlaRecord) descriptor.get("component");
        FlaRecord parameters = (FlaRecord) component.get("properties");
        component.set("componentEnabled", 0);
        component.set("classPropertiesXml", "");
        component.set("properties", kind.equals("parameters") ? parameters : XflSwatchExportTest.record("properties", "entries", Collections.emptyList(), "count", 0));
        List<FlaRecord> movies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            movies.add(XflSwatchExportTest.record("smartClipMovie", "name", "", "byteCount", 0, "data", new BinaryData(new byte[0])));
        }
        component.set("movies", movies);
        component.set("livePreview11Movie", null);
        int index = kind.equals("primaryOnly") ? 0 : kind.equals("previewName") ? 1 : kind.equals("definitionName") ? 2 : -1;
        String path = "unavailable/authoring-preview.swf";
        if (index >= 0) {
            movies.get(index).set("name", path);
        }
        if (kind.equals("preview11Name")) {
            component.set("livePreview11Movie", XflSwatchExportTest.record("smartClipMovie", "name", path, "byteCount", 0, "data", new BinaryData(new byte[0])));
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element item = null;
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(file.getValue().toByteArray());
                if (file.getKey().startsWith("LIBRARY/")) {
                    Element root = parse(file.getValue().toByteArray());
                    if (root.getAttribute("name").equals("Button")) {
                        item = root;
                    }
                }
            }
        }
        assertNotNull(item);
        assertEquals(item.getLocalName(), expected ? "DOMComponentItem" : "DOMSymbolItem");
        String attr = kind.equals("previewName") ? "livePreviewScm" : kind.equals("definitionName") ? "customUIScm" : kind.equals("preview11Name") ? "livePreview11Scm" : null;
        if (attr != null) {
            assertEquals(item.getAttribute(attr + "SourceFilename"), path);
            assertFalse(item.hasAttribute(attr + "HRef"));
        }
        if (kind.equals("parameters")) {
            assertEquals(elements(item, "parametersAsXML").size(), 1);
        }
        assertEquals(component.get("componentEnabled"), 0);
    }

    @DataProvider(name = "legacyDescriptor")
    public Object[][] legacyDescriptor() {
        return new Object[][]{{10}, {14}, {15}, {16}, {17}};
    }

    @Test(dataProvider = "legacyDescriptor")
    public void oldDescriptorMovieOrderAndTooltipIconUpgradeMatchNativeMembers(int revision) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0025_components-todo");
        FlaRecord descriptor = source.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.LIBRARY && "Button".equals(p.getDescriptor().get("name"))).findFirst().get().getDescriptor();
        FlaRecord modern = (FlaRecord) descriptor.get("component");
        @SuppressWarnings("unchecked")
        List<FlaRecord> modernMovies = (List<FlaRecord>) modern.get("movies");
        FlaRecord preview = modernMovies.get(1);
        FlaRecord definitionMovie = XflSwatchExportTest.record("smartClipMovie", "name", "definition.swf", "byteCount", preview.get("byteCount"), "data", preview.get("data"));
        FlaRecord liveMovie = XflSwatchExportTest.record("smartClipMovie", "name", "preview.swf", "byteCount", preview.get("byteCount"), "data", preview.get("data"));
        descriptor.set("component", null);
        descriptor.set("revision", revision);
        descriptor.set("properties", XflSwatchExportTest.record("properties", "entries", Collections.emptyList(), "count", 0));
        descriptor.set("legacyComponentName", "discarded old name");
        descriptor.set("legacyCustomUISourceFilename", "definition-only.swf");
        descriptor.set("legacySymbolDescription", "Legacy component description");
        descriptor.set("legacyMovies", revision >= 15 ? Arrays.asList(definitionMovie, liveMovie) : Collections.emptyList());
        if (revision >= 16) {
            descriptor.set("legacyDescriptionType", 2);
        }
        if (revision == 17) {
            descriptor.set("legacyComponentEnabled", 0x80000000L);
            descriptor.set("legacyComponentTooltip", "Legacy tooltip");
            descriptor.set("legacyComponentIconId", 25);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element item = null;
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(f.getValue().toByteArray());
                if (f.getKey().startsWith("LIBRARY/")) {
                    Element e = parse(f.getValue().toByteArray());
                    if (e.getAttribute("name").equals("Button")) {
                        item = e;
                    }
                }
            }
        }
        assertNotNull(item);
        assertEquals(item.getLocalName(), "DOMComponentItem");
        assertEquals(item.getAttribute("requiredMinimumPlayerVersion"), "0");
        assertEquals(item.getAttribute("requiredMinimumASVersion"), "0");
        assertEquals(item.getAttribute("customIconID"), "0");
        assertEquals(item.getAttribute("symbolDescription"), "Legacy component description");
        assertEquals(item.getAttribute("descriptionType"), "text");
        assertEquals(item.getAttribute("tooltip"), revision == 17 ? "Legacy tooltip" : "");
        if (revision >= 15) {
            for (String attr : Arrays.asList("customUIScm", "livePreviewScm")) {
                assertEquals(result.getFiles().get("bin/" + item.getAttribute(attr + "HRef")).toByteArray(), ((BinaryData) preview.get("data")).toByteArray());
            }
            assertEquals(item.getAttribute("customUIScmSourceFilename"), "definition.swf");
            assertEquals(item.getAttribute("livePreviewScmSourceFilename"), "preview.swf");
        } else {
            assertEquals(item.getAttribute("customUIScmSourceFilename"), "definition-only.swf");
            assertFalse(item.hasAttribute("customUIScmHRef"));
        }
        assertNull(descriptor.get("component"));
        assertEquals(descriptor.get("legacyComponentName"), "discarded old name");
    }

    @Test
    public void nativeMxComponentsSupplyVersionRequirementsForCs6Loading() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile("mx", "0025_components-todo"));
        int components = 0;
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (!file.getKey().startsWith("LIBRARY/") || !file.getKey().endsWith(".xml")) {
                continue;
            }
            Element item = parse(file.getValue().toByteArray());
            if (!item.getLocalName().equals("DOMComponentItem")) {
                continue;
            }
            components++;
            // XSD validation alone accepts omissions; the CS6 loader does not.
            assertEquals(item.getAttribute("requiredMinimumPlayerVersion"), "0", item.getAttribute("name"));
            assertEquals(item.getAttribute("requiredMinimumASVersion"), "0", item.getAttribute("name"));
            XflSchemaTestSupport.validate(file.getValue().toByteArray());
        }
        assertTrue(components > 0, result.getReport());
        assertFalse(elements(xml(result), "DOMComponentInstance").isEmpty());
    }

    @Test
    public void discardedHistoricalComponentNameAloneDoesNotCreateAComponent() throws Exception {
        FlaRecord descriptor = XflSwatchExportTest.record("CDocumentPage", "symbolType", 2, "revision", 14, "legacyComponentName", "ignored");
        assertFalse(XflComponentWriter.isComponent(descriptor));
    }
}
