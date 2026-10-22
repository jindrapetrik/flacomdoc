package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.nio.file.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflSwatchExportTest.record;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflLibraryMetadataExportTest {

    @DataProvider(name = "samples")
    public Object[][] samples() {
        return new Object[][]{{"0009_symbols"}, {"0012_texts"}, {"0013_bitmaps"}, {"0014_sounds"}, {"0015_videos"}, {"0024_compiled_clips-todo"}, {"0025_components-todo"}, {"0028_library_folders"}};
    }

    @Test(dataProvider = "samples")
    public void idsTimesAndSourceNamesMatchIndependentAdobeDocuments(String sample) throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile("cs4", sample));
        Map<String, Element> a = items(result), b = new TreeMap<>();
        try (java.util.stream.Stream<Path> fs = Files.walk(Paths.get("testdata/fla/cs5", sample))) {
            for (Path p : (Iterable<Path>) fs.filter(Files::isRegularFile)::iterator) {
                if (p.toString().endsWith(".xml") && Files.size(p) > 0 && !p.getFileName().toString().equals("PublishSettings.xml")) {
                    collect(parse(Files.readAllBytes(p)), b);
                }
            }
        }
        int ids = 0;
        for (Map.Entry<String, Element> item : b.entrySet()) {
            Element expected = item.getValue(), actual = a.get(item.getKey());
            assertNotNull(actual, item.getKey());
            for (String attr : Arrays.asList("itemID", "lastModified", "sourceLastModified", "sourceLibraryItemHRef", "isExpanded")) {
                if (expected.hasAttribute(attr)) {
                    assertEquals(actual.getAttribute(attr), expected.getAttribute(attr), item.getKey() + "@" + attr);
                    if (attr.equals("itemID")) {
                        ids++;
                    }
                }
            }
        }
        assertTrue(ids > 0);
        assertFalse(has(result, "ITEM_ID"), result.getReport());
    }

    @Test
    public void originalSourcePathsRemainArchiveTextRatherThanLaterAdobeRelocation() throws Exception {
        for (String sample : Arrays.asList("0013_bitmaps", "0014_sounds", "0028_library_folders")) {
            BinaryFlaDocument source = nativeFile("cs4", sample);
            Map<String, Element> actual = items(new BinaryFlaToXflExporter().export(source));
            int checked = 0;
            for (BinaryFlaDocument.Media media : source.getMedia()) {
                FlaRecord d = media.getDescriptor();
                String path = (String) d.get("sourcePath");
                if (path == null || path.isEmpty()) {
                    continue;
                }
                for (Element e : actual.values()) {
                    if (e.getAttribute("name").endsWith((String) d.get("name"))) {
                        assertEquals(e.getAttribute("sourceExternalFilepath"), path);
                        checked++;
                        break;
                    }
                }
            }
            assertTrue(checked > 0, sample);
        }
    }

    @DataProvider(name = "sourceAutoUpdate")
    public Object[][] sourceAutoUpdate() {
        return new Object[][]{{"C:/source & Ω.fla", 0L}, {"C:/source & Ω.fla", 1L},
        {"relative/source.fla", 0x80000000L}, {"source.fla", null}, {"", 1L}, {null, 1L}};
    }

    @Test(dataProvider = "sourceAutoUpdate")
    public void sourceLinkProvenanceIsIndependentOfRuntimeSharingMode(String path, Long flag) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0009_symbols");
        FlaRecord descriptor = source.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.LIBRARY).findFirst().get().getDescriptor();
        FlaRecord aux = record("auxiliary", "sourcePath", path, "sourceItemName", "Source Ω", "sourceLastModified", 0xFFFFFFFFL, "sourceAutoUpdate", flag);
        FlaRecord link = record("linkage", "revision", 7, "flags", 0L, "exportFlags", 0, "auxiliary", aux);
        ((FlaRecord) descriptor.get("libraryMetadata")).set("linkage", link);
        descriptor.set("linkage", record("linkage", "flags", 0L, "auxiliary", record("auxiliary", "sourcePath", "wrong.fla", "sourceAutoUpdate", 0L)));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element item = items(result).values().stream().filter(e -> e.getAttribute("name").endsWith((String) descriptor.get("name"))).findFirst().get();
        boolean hasPath = path != null && !path.isEmpty();
        assertEquals(item.getAttribute("sourceFlashFilepath"), hasPath ? path : "");
        assertEquals(item.hasAttribute("sourceAutoUpdate"), hasPath && flag != null);
        if (hasPath && flag != null) {
            assertEquals(item.getAttribute("sourceAutoUpdate"), Boolean.toString(flag != 0));
        }
        assertEquals(item.getAttribute("sourceLibraryItemHRef"), "Source Ω");
        assertEquals(aux.get("sourcePath"), path);
        assertEquals(aux.get("sourceAutoUpdate"), flag);
    }

    @DataProvider(name = "mediaPlatforms")
    public Object[][] mediaPlatforms() {
        List<Object[]> cases = new ArrayList<>();
        for (String sample : Arrays.asList("0013_bitmaps", "0014_sounds", "0015_videos", "0024_compiled_clips-todo")) {
            for (Long value : Arrays.asList(0L, 1L, 0xFFFFFFFFL, null)) {
                cases.add(new Object[]{sample, value});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "mediaPlatforms")
    public void sourcePlatformUsesTheStoredFlagInsteadOfTheHostOperatingSystem(String sample, Long value) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", sample);
        FlaRecord descriptor = source.getMedia().get(0).getDescriptor();
        descriptor.set("sourcePlatform", value);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element item = items(result).values().stream().filter(e -> e.getAttribute("name").endsWith((String) descriptor.get("name"))).findFirst().get();
        boolean known = value != null && (value == 0 || value == 1);
        assertEquals(item.hasAttribute("sourcePlatform"), known);
        if (known) {
            assertEquals(item.getAttribute("sourcePlatform"), value == 1 ? "windows" : "macintosh");
        }
        assertEquals(has(result, "MEDIA_PLATFORM"), value != null && !known);
        assertEquals(descriptor.get("sourcePlatform"), value);
    }

    @Test
    public void timelineEffectMetadataIsReportedUnderItsRecoveredMeaning() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0009_symbols");
        FlaRecord symbol = records(source, "CPicSprite").get(0);
        FlaRecord effect = record("timelineEffect", "effectName", "Blur & trace", "effectId", "myEffect", "sourceScriptFilename", "effect.jsfl");
        symbol.set("timelineEffectPresent", 255);
        symbol.set("timelineEffect", effect);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        items(result);
        assertTrue(has(result, "TIMELINE_EFFECT"));
        assertFalse(has(result, "INSTANCE_COMPONENT"));
        assertEquals(effect.get("sourceScriptFilename"), "effect.jsfl");
    }

    @Test
    public void duplicateItemIdentityDoesNotPropagateToTwoLibraryItems() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0009_symbols");
        List<BinaryFlaDocument.Page> pages = new ArrayList<>();
        for (BinaryFlaDocument.Page page : source.getPages()) {
            if (page.getRole() == BinaryFlaDocument.Page.Role.LIBRARY) {
                pages.add(page);
            }
        }
        FlaRecord a = (FlaRecord) ((FlaRecord) pages.get(0).getDescriptor().get("libraryMetadata")).get("item"), b = (FlaRecord) ((FlaRecord) pages.get(1).getDescriptor().get("libraryMetadata")).get("item");
        b.set("firstPart", a.get("firstPart"));
        b.set("secondPart", a.get("secondPart"));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertTrue(has(result, "ITEM_ID"));
        Set<String> ids = new HashSet<>();
        for (Element item : items(result).values()) {
            if (item.hasAttribute("itemID")) {
                assertTrue(ids.add(item.getAttribute("itemID")));
            }
        }
        assertEquals(((FlaRecord) ((FlaRecord) pages.get(1).getDescriptor().get("libraryMetadata")).get("item")).get("firstPart"), a.get("firstPart"));
    }

    @Test
    public void bitmapSmoothingVariantDoesNotCopyTheOriginalIdentity() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0013_bitmaps");
        source.getMedia().get(0).getDescriptor().set("allowSmoothing", 0);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertTrue(has(result, "BITMAP_VARIANT"));
        Set<String> ids = new HashSet<>();
        boolean variant = false;
        for (Element e : items(result).values()) {
            if (e.getLocalName().equals("DOMBitmapItem")) {
                if (e.hasAttribute("itemID")) {
                    assertTrue(ids.add(e.getAttribute("itemID")));
                }
                if (e.getAttribute("name").contains("(smoothed)")) {
                    assertFalse(e.hasAttribute("itemID"));
                    variant = true;
                }
            }
        }
        assertTrue(variant);
    }

    @Test
    public void fullFolderBooleanWordControlsExpansion() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0028_library_folders");
        FlaRecord folders = (FlaRecord) source.getContents().get("libraryFolders");
        @SuppressWarnings("unchecked")
        List<FlaRecord> entries = (List<FlaRecord>) folders.get("entries");
        ((FlaRecord) entries.get(0).get("libraryMetadata")).set("attributes", 0x80000000L);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        String suffix = "@" + entries.get(0).get("name");
        boolean found = false;
        for (Map.Entry<String, Element> e : items(result).entrySet()) {
            if (e.getKey().equals("DOMFolderItem" + suffix)) {
                assertEquals(e.getValue().getAttribute("isExpanded"), "true");
                found = true;
            }
        }
        assertTrue(found);
    }

    static Map<String, Element> items(XflExportResult result) throws Exception {
        Map<String, Element> all = new TreeMap<>();
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(file.getValue().toByteArray());
                collect(parse(file.getValue().toByteArray()), all);
            }
        }
        return all;
    }

    static void collect(Element root, Map<String, Element> map) {
        if ("http://ns.adobe.com/xfl/2008/".equals(root.getNamespaceURI()) && root.getLocalName().endsWith("Item") && root.hasAttribute("name")) {
            map.put(root.getLocalName() + "@" + root.getAttribute("name"), root);
        }
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                collect((Element) n, map);
            }
        }
    }
}
