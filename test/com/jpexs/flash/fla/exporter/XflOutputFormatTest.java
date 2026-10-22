package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.converter.*;
import com.jpexs.flash.fla.reader.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;

public class XflOutputFormatTest {

    @DataProvider(name = "xflVersions")
    public Object[][] versions() {
        return new Object[][]{{XflFormatVersion.CS5, "2.0"}, {XflFormatVersion.CS5_5, "2.1"}, {XflFormatVersion.CS6, "2.2"}};
    }

    @Test(dataProvider = "xflVersions")
    public void selectedVersionSetsTheDocumentVersionAndBothContainersMatch(XflFormatVersion version, String expected) throws Exception {
        BinaryFlaDocument source = load("0013_bitmaps");
        XflExportResult result = new BinaryFlaToXflExporter(version).export(source);
        assertEquals(xml(result).getDocumentElement().getAttribute("xflVersion"), expected);
        Path root = Files.createTempDirectory("xfl-output-");
        Path zipPath = root.resolve("converted.fla"), folder = root.resolve("document");
        try {
            result.writeToZip(zipPath.toFile());
            result.writeToDirectory(folder.toFile());
            assertFalse(BinaryFlaConverter.isBinaryFla(zipPath.toFile()));
            try (ZipFile zip = new ZipFile(zipPath.toFile())) {
                ZipEntry first = zip.entries().nextElement();
                assertEquals(first.getName(), "mimetype");
                assertEquals(first.getMethod(), ZipEntry.STORED);
                assertEquals(new String(bytes(zip.getInputStream(first)), StandardCharsets.US_ASCII), "application/vnd.adobe.xfl");
                assertEquals(zip.getEntry("document.xfl").getMethod(), ZipEntry.STORED);
                for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
                    ZipEntry entry = zip.getEntry(file.getKey());
                    assertNotNull(entry, file.getKey());
                    byte[] actual = bytes(zip.getInputStream(entry));
                    assertEquals(actual, file.getValue().toByteArray(), file.getKey());
                    assertEquals(actual, Files.readAllBytes(folder.resolve(file.getKey())), file.getKey());
                }
                assertNotNull(zip.getEntry("conversion-report.txt"));
            }
            result.writeToZip(zipPath.toFile()); // Completed archives can replace existing output.
            try (ZipFile zip = new ZipFile(zipPath.toFile())) {
                assertNotNull(zip.getEntry("DOMDocument.xml"));
            }
            try {
                result.writeToDirectory(folder.toFile());
                fail("Existing directories must be rejected");
            } catch (FileAlreadyExistsException expectedFailure) {
            }
            try (Stream<Path> files = Files.list(root)) {
                assertEquals(files.count(), 2L);
            }
        } finally {
            deleteTestDirectory(root);
        }
    }

    @Test(dataProvider = "xflVersions")
    public void laterSymbolSettingsAreGatedByActualXflSupport(XflFormatVersion version, String expected) throws Exception {
        BinaryFlaDocument source = load("0010_symbol_props");
        FlaRecord symbol = find(source, "CPicSprite");
        assertNotNull(symbol);
        symbol.set("bitmapRenderMode", 2);
        symbol.set("isVisible", 0);
        symbol.set("bitmapBits32", 0);
        XflExportResult result = new BinaryFlaToXflExporter(version).export(source);
        NodeList instances = xml(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "DOMSymbolInstance");
        Element instance = null;
        for (int i = 0; i < instances.getLength(); i++) {
            Element candidate = (Element) instances.item(i);
            if (candidate.getAttribute("symbolType").equals("movie clip")) {
                instance = candidate;
                break;
            }
        }
        assertNotNull(instance);
        assertEquals(instance.hasAttribute("isVisible"), version != XflFormatVersion.CS5);
        assertEquals(instance.hasAttribute("bits32"), version != XflFormatVersion.CS5);
        assertEquals(instance.hasAttribute("exportAsBitmap"), version != XflFormatVersion.CS5);
        assertEquals(result.getIssues().stream().anyMatch(i -> i.getCode().equals("TARGET_VERSION")), version == XflFormatVersion.CS5);
        assertEquals(symbol.get("isVisible"), 0);
    }

    @Test
    public void onlyActualLegacyFormatsAreExposedToTheBinaryWriter() {
        assertEquals(FlaFormatVersion.values().length, 10);
        assertEquals(FlaTargetVersion.parse("CS5.5"), FlaTargetVersion.CS5_5);
        assertFalse(FlaTargetVersion.CS5.isBinary());
        assertFalse(FlaTargetVersion.CS6.isBinary());
        assertEquals(FlaTargetVersion.CS4.getBinaryVersion(), FlaFormatVersion.CS4);
        assertEquals(FlaTargetVersion.CS5.getXflVersion(), XflFormatVersion.CS5);
        try {
            FlaTargetVersion.CS5.getBinaryVersion();
            fail("CS5 is an XFL target");
        } catch (IllegalStateException expected) {
        }
        try {
            FlaFormatVersion.valueOf("CS5");
            fail("There is no binary CS5 FLA target");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static Document xml(XflExportResult result) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(result.getFiles().get("DOMDocument.xml").openStream());
    }

    private static BinaryFlaDocument load(String name) throws Exception {
        Path root = Paths.get("testdata/fla/cs4", name);
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                files.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        return new BinaryFlaReader().readStreams(files);
    }

    private static FlaRecord find(BinaryFlaDocument source, String type) {
        for (FlaStream stream : source.getStreams().values()) {
            for (FlaRecord record : stream.getObjects()) {
                if (record.getClassName().equals(type)) {
                    return record;
                }
            }
        }
        return null;
    }

    private static byte[] bytes(InputStream stream) throws Exception {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) != -1;) {
                out.write(b, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void deleteTestDirectory(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        }
    }
}
