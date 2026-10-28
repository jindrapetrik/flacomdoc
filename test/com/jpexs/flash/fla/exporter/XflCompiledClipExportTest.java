package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.nio.file.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflSwatchExportTest.record;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflCompiledClipExportTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void compiledClipRetainsPrimaryMovieIconMetadataAndBinding(String version) throws Exception {
        BinaryFlaDocument doc = nativeFile(version, "0024_compiled_clips-todo");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result), item = elements(root, "DOMCompiledClipItem").get(0);
        assertEquals(elements(root, "DOMCompiledClipItem").size(), 1);
        assertFalse(has(result, "COMPILED_CLIP_DATA"), result.getReport());
        assertFalse(has(result, "MISSING_COMPILED_CLIP"), result.getReport());
        FlaRecord descriptor = doc.getMedia().get(0).getDescriptor(), component = XflXml.record(descriptor, "component");
        assertEquals(result.getFiles().get("bin/" + item.getAttribute("swfScmHRef")).toByteArray(), ((BinaryData) XflXml.records(component, "movies").get(0).get("data")).toByteArray());
        assertEquals(elements(item, "classProperties").get(0).getTextContent(), normalized((String) component.get("classPropertiesXml")));
        assertEquals(elements(item, "classProperties").get(0).getFirstChild().getNodeType(), org.w3c.dom.Node.CDATA_SECTION_NODE);
        for (String attr : Arrays.asList("requiredMinimumPlayerVersion", "requiredMinimumASVersion")) {
            assertEquals(item.getAttribute(attr), Long.toString(XflXml.n(component, attr, 0)));
        }
        BinaryData movie = (BinaryData) XflXml.records(component, "movies").get(0).get("data");
        assertEquals(item.getAttribute("playerVersion"), Long.toString(XflXml.n(descriptor, "playerVersion", movie.toByteArray()[3] & 255)));
        assertEquals(item.getAttribute("actionscriptVersion"), Long.toString(XflXml.n(descriptor, "actionScriptVersion", 2)));
        Element instance = elements(root, "DOMCompiledClipInstance").get(0);
        assertEquals(instance.getAttribute("libraryItemName"), item.getAttribute("name"));
        FlaRecord original = records(doc, "CPicSwf").get(0);
        FlaRecord binding = XflXml.record(original, "componentXml");
        assertEquals(instance.getAttribute("uniqueID"), Long.toString(XflXml.n(binding, "uniqueId", 0)));
        assertEquals(elements(instance, "dataBindingXML").get(0).getTextContent(), normalized((String) binding.get("xml")));
        assertEquals(elements(instance, "dataBindingXML").get(0).getFirstChild().getNodeType(), org.w3c.dom.Node.CDATA_SECTION_NODE);
        if (version.equals("cs4")) {
            Element paired = reference("0024_compiled_clips-todo"), pairedItem = elements(paired, "DOMCompiledClipItem").get(0);
            for (String attr : Arrays.asList("name", "actionscriptClass", "playerVersion", "actionscriptVersion", "rootSymbolLinkageID", "movieRectRight", "movieRectBottom")) {
                assertEquals(item.getAttribute(attr), pairedItem.getAttribute(attr));
            }
            Element icon = elements(item, "CustomIcon").get(0);
            byte[] expected = Files.readAllBytes(Paths.get("testdata/fla/cs5/0024_compiled_clips-todo/bin/4m4mnjuda0.dat"));
            assertEquals(result.getFiles().get("bin/" + icon.getAttribute("href")).toByteArray(), expected);
            Element point = elements(instance, "Point").get(0);
            assertEquals(point.getAttribute("x"), "42.5");
            assertEquals(point.getAttribute("y"), "32.5");
        }
    }

    private static String normalized(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test(dataProvider = "versions")
    public void swcCacheUsesExactArchivedBytesWhenAvailable(String version) throws Exception {
        BinaryFlaDocument doc = nativeFile(version, "0024_compiled_clips-todo");
        FlaRecord descriptor = doc.getMedia().get(0).getDescriptor();
        FlaSharedLibraries collection = doc.getSharedLibraries();
        if (collection == null) {
            collection = FlaSharedLibraries.fromCollection(XflXml.record(XflXml.record(doc.getContents(), "compatibilityTail"), "sharedLibraries"));
        }
        FlaSharedLibraries.Entry entry = FlaSharedLibraries.getEmbeddedLibrary(descriptor);
        if (entry == null && collection != null) {
            entry = collection.findForClip(descriptor);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result);
        if (entry == null) {
            assertTrue(has(result, "COMPILED_CLIP_CACHE"));
            return;
        }
        Element swc = elements(root, "Swc").get(0);
        assertEquals(swc.getAttribute("hashKey"), entry.getName());
        assertEquals(result.getFiles().get("bin/" + swc.getAttribute("href")).toByteArray(), entry.getData().toByteArray());
        assertEquals(elements(swc, "ClassDefinition").size(), entry.getMembers() == null ? 0 : entry.getMembers().size());
    }

    @Test
    public void recursiveSwcDependenciesRetainAllAttributesOrderAndDuplicateKeys() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0024_compiled_clips-todo");
        FlaRecord root = swc("root & Ω");
        root.set("version", 0xFFFFFFFFL);
        root.set("isTopLevel", 255);
        root.set("implementation", "impl<&>");
        root.set("intrinsicClass", "pkg.Widget");
        root.set("intrinsicScript", "class Widget { /* <&> */ }");
        FlaRecord asset = swc("asset"), include = swc("included"), after = swc("after");
        asset.set("includesList", Arrays.asList(entry(include)));
        include.set("exportAfterList", Arrays.asList(entry(after)));
        after.set("assetMap", Arrays.asList(asset("nested", swc("leaf"))));
        root.set("assetMap", Arrays.asList(asset("same & key", asset), asset("same & key", swc("second"))));
        root.set("includesList", Arrays.asList(entry(include)));
        root.set("exportAfterList", Arrays.asList(entry(after)));
        doc.getMedia().get(0).getDescriptor().set("metadata", root);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element item = elements(xml(result), "SwcItem").get(0);
        assertEquals(item.getAttribute("name"), "root & Ω");
        assertEquals(item.getAttribute("version"), "4294967295");
        assertEquals(item.getAttribute("isTopLevel"), "true");
        assertEquals(item.getAttribute("implementation"), "impl<&>");
        assertEquals(item.getAttribute("intrinsicClass"), "pkg.Widget");
        assertEquals(item.getAttribute("intrinsicScript"), root.get("intrinsicScript"));
        List<String> containers = new ArrayList<>();
        for (org.w3c.dom.Node n = item.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                containers.add(n.getLocalName());
            }
        }
        assertEquals(containers, Arrays.asList("assetMap", "includesList", "exportAfterList"));
        Element map = elements(item, "assetMap").get(0);
        List<Element> assets = elements(map, "AssetMapItem");
        assertEquals(assets.get(0).getAttribute("key"), "same & key");
        assertEquals(assets.get(assets.size() - 1).getAttribute("key"), "same & key");
        Element included = elements(item, "IncludesItem").get(0);
        assertEquals(included.getAttribute("name"), "included");
        assertEquals(elements(included, "ExportAfterItem").get(0).getAttribute("name"), "after");
        assertEquals(elements(included, "AssetMapItem").get(0).getAttribute("key"), "nested");
        assertFalse(has(result, "COMPILED_CLIP_METADATA"), result.getReport());
    }

    @Test
    public void cyclicSwcDependencyIsReportedWithoutDiscardingItsSiblings() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0024_compiled_clips-todo");
        FlaRecord root = swc("root");
        root.set("assetMap", Arrays.asList(asset("cycle", root), asset("valid", swc("valid"))));
        doc.getMedia().get(0).getDescriptor().set("metadata", root);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element item = elements(xml(result), "SwcItem").get(0);
        assertTrue(has(result, "COMPILED_CLIP_METADATA"));
        assertEquals(elements(item, "AssetMapItem").size(), 1);
        assertEquals(elements(item, "AssetMapItem").get(0).getAttribute("key"), "valid");
    }

    @Test
    public void revisionTwoChildrenAreReportedRatherThanAssignedToAModernList() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0024_compiled_clips-todo");
        FlaRecord root = swc("old");
        root.set("revision", 2);
        root.set("legacyChildren", Arrays.asList(entry(swc("unknown role"))));
        doc.getMedia().get(0).getDescriptor().set("metadata", root);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element item = elements(xml(result), "SwcItem").get(0);
        assertTrue(has(result, "COMPILED_CLIP_METADATA"));
        assertTrue(elements(item, "assetMap").isEmpty());
        assertTrue(elements(item, "includesList").isEmpty());
        assertTrue(elements(item, "exportAfterList").isEmpty());
    }

    @Test
    public void malformedSwcEntryIsReportedAndDoesNotLeaveAnEmptyMapContainer() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0024_compiled_clips-todo");
        FlaRecord root = swc("root");
        root.set("assetMap", Arrays.asList(record("swfMetadataEntry", "key", "missing child")));
        doc.getMedia().get(0).getDescriptor().set("metadata", root);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertTrue(has(result, "COMPILED_CLIP_METADATA"));
        assertTrue(elements(elements(xml(result), "SwcItem").get(0), "assetMap").isEmpty());
    }

    @Test
    public void swcSchemaRejectsMisspelledCollectionsAndMissingAssetKeys() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0024_compiled_clips-todo");
        FlaRecord root = swc("root");
        root.set("assetMap", Arrays.asList(asset("key", swc("asset"))));
        doc.getMedia().get(0).getDescriptor().set("metadata", root);
        byte[] bytes = new BinaryFlaToXflExporter().export(doc).getFiles().get("DOMDocument.xml").toByteArray();
        XflSchemaTestSupport.validate(bytes);
        String original = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        for (String broken : Arrays.asList(original.replace("AssetMapItem", "Asset"),
                original.replace(" key=\"key\"", ""), original.replace("assetMap", "namedChildren"))) {
            assertNotEquals(broken, original);
            try {
                XflSchemaTestSupport.validate(broken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fail("Invalid SWC structure accepted");
            } catch (org.xml.sax.SAXException expected) {
                // The native element names and the map key are structural.
            }
        }
    }

    private static FlaRecord swc(String name) {
        return record("swfMetadata", "revision", 4, "name", name, "implementation", "", "version", 1L,
                "isTopLevel", 0L, "intrinsicClass", "", "intrinsicScript", "");
    }

    private static FlaRecord entry(FlaRecord metadata) {
        return record("swfMetadataEntry", "metadata", metadata);
    }

    private static FlaRecord asset(String key, FlaRecord metadata) {
        FlaRecord r = entry(metadata);
        r.set("key", key);
        return r;
    }

    @Test
    public void malformedCompiledIconLeavesNoPartialItemOrReferences() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0024_compiled_clips-todo");
        FlaRecord raster = XflXml.record(XflXml.record(XflXml.record(XflXml.record(doc.getMedia().get(0).getDescriptor(), "component"), "embeddedMedia"), "payload"), "raster");
        raster.set("width", 65535);
        raster.set("height", 65535);
        raster.set("rowStride", 65535);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result);
        assertTrue(has(result, "COMPILED_CLIP_DATA"));
        assertTrue(elements(root, "DOMCompiledClipItem").isEmpty());
        assertTrue(elements(root, "DOMCompiledClipInstance").isEmpty());
        assertFalse(result.getFiles().keySet().stream().anyMatch(p -> p.startsWith("bin/clip-")));
    }

    @Test
    public void schemaValidatesOriginalAdobeCompiledClipDocument() throws Exception {
        XflSchemaTestSupport.validate(Files.readAllBytes(Paths.get("testdata/fla/cs5/0024_compiled_clips-todo/DOMDocument.xml")));
    }
}
