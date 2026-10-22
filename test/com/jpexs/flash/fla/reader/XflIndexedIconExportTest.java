package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.exporter.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.validation.*;
import javax.xml.transform.stream.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class XflIndexedIconExportTest {

    @DataProvider(name = "depths")
    public Object[][] depths() {
        return new Object[][]{{0}, {1}, {2}, {3}};
    }

    @Test(dataProvider = "depths")
    public void indexedIconRetainsPackedIndexesPaddingPaletteAndAlpha(int format) throws Exception {
        int depth = 1 << format;
        byte[] pixels = new byte[4];
        pixels[depth / 8] = (byte) (1 << (8 - depth - depth % 8));
        pixels[3] = 23;
        XflExportResult result = export(format, pixels);
        Element icon = icon(result);
        assertEquals(icon.getAttribute("colorDepth"), Integer.toString(depth));
        assertEquals(icon.getAttribute("rowByteCount"), "4");
        assertEquals(result.getFiles().get("bin/" + icon.getAttribute("href")).toByteArray(), pixels);
        NodeList colors = icon.getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "ColorItem");
        assertEquals(colors.getLength(), 2);
        Element first = (Element) colors.item(0), second = (Element) colors.item(1);
        assertEquals(first.getAttribute("color"), "#102030");
        assertEquals(Double.parseDouble(first.getAttribute("alpha")), 128 / 255.0);
        assertEquals(second.getAttribute("color"), "#608090");
        assertEquals(second.getAttribute("alpha"), "1");
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPONENT_ICON")), result.getReport());
    }

    @Test
    public void missingPaletteEntryOmitsIconAndItsFileAtomically() throws Exception {
        XflExportResult result = export(3, new byte[]{0, 2, 0, 0});
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPONENT_ICON")));
        assertFalse(result.getFiles().keySet().stream().anyMatch(p -> p.startsWith("bin/component-icon-")));
    }

    @DataProvider(name = "unaligned")
    public Object[][] unaligned() {
        return new Object[][]{{4, 3, 2}, {5, 5, 4}};
    }

    @Test(dataProvider = "unaligned")
    public void alignmentIsCheckedPerRowEvenWhenTotalLengthIsAligned(int format, int stride, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(3);
        out.write(format);
        u16(out, stride);
        u16(out, 1);
        u16(out, height);
        for (int v : new int[]{0, 20, 0, 20 * height}) {
            u32(out, v);
        }
        out.write(0);
        out.write(0);
        out.write(new byte[stride * height]);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord raster = a.readInline("raster", r -> {
        }, BitmapRecordDecoder::raster);
        assertEquals(a.getRemaining(), 0);
        XflExportResult result = export(raster);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPONENT_ICON")), result.getReport());
        assertFalse(result.getFiles().keySet().stream().anyMatch(p -> p.startsWith("bin/component-icon-")));
    }

    static XflExportResult export(int format, byte[] pixels) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(3);
        out.write(format);
        u16(out, 4);
        u16(out, 3);
        u16(out, 1);
        for (int v : new int[]{0, 60, 0, 20}) {
            u32(out, v);
        }
        out.write(1);
        u16(out, 2);
        for (int color : new int[]{0x80102030, 0xff608090}) {
            for (int shift = 24; shift >= 0; shift -= 8) {
                out.write(color >>> shift & 255);
            }
        }
        out.write(0);
        out.write(pixels);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord raster = a.readInline("raster", r -> {
        }, BitmapRecordDecoder::raster);
        assertEquals(a.getRemaining(), 0);
        assertTrue(raster.isComplete());
        return export(raster);
    }

    static XflExportResult export(FlaRecord raster) throws Exception {
        Path path = Paths.get("testdata/fla/cs4/0025_components-todo");
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> fs = Files.walk(path)) {
            for (Path p : (Iterable<Path>) fs.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams);
        boolean found = false;
        for (BinaryFlaDocument.Page p : doc.getPages()) {
            if (p.getDescriptor() != null && "Button".equals(p.getDescriptor().get("name"))) {
                FlaRecord c = (FlaRecord) p.getDescriptor().get("component"), embedded = (FlaRecord) c.get("embeddedMedia"), payload = (FlaRecord) embedded.get("payload");
                payload.set("raster", raster);
                found = true;
            }
        }
        assertTrue(found);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Schema schema = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File("testdata/xfl-schema/xfl.xsd"));
        for (Map.Entry<String, BinaryData> e : result.getFiles().entrySet()) {
            if (e.getKey().endsWith(".xml") && !e.getKey().equals("META-INF/metadata.xml")) {
                schema.newValidator().validate(new StreamSource(e.getValue().openStream()));
            }
        }
        return result;
    }

    static Element icon(XflExportResult result) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        for (Map.Entry<String, BinaryData> e : result.getFiles().entrySet()) {
            if (e.getKey().startsWith("LIBRARY/") && e.getKey().endsWith(".xml")) {
                Element root = f.newDocumentBuilder().parse(e.getValue().openStream()).getDocumentElement();
                NodeList icons = root.getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "CustomIcon");
                if (icons.getLength() > 0) {
                    return (Element) icons.item(0);
                }
            }
        }
        throw new AssertionError(result.getReport());
    }
}
