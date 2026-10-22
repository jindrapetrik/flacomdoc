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

public class XflPersistentExportTest {

    @Test
    public void allSixTypesKeepNamesValuesEmptyArraysAndLastDuplicate() throws Exception {
        XflExportResult result = export(data(false), "0025_components-todo", true);
        Element root = parse(result.getFiles().get("DOMDocument.xml"));
        Map<String, Element> values = values(root);
        assertEquals(values.size(), 9);
        assertValue(values, "integer", "i", "-2147483648");
        assertValue(values, "integers", "I", "-1,0,2147483647");
        assertValue(values, "double", "d", "0.1234567890123456");
        assertValue(values, "doubles", "D", "-0,1.25,-100");
        assertValue(values, "string", "s", "<&\"'\n\u03a9");
        assertValue(values, "bytes", "B", "00017F80FF");
        assertValue(values, "emptyIntegers", "I", "");
        assertValue(values, "emptyBytes", "B", "");
        assertValue(values, "duplicate", "i", "2");
        NodeList instances = root.getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "DOMComponentInstance");
        assertTrue(instances.getLength() > 0);
        assertValue(values((Element) instances.item(0)), "bytes", "B", "00017F80FF");
    }

    @Test
    public void nonfiniteValueIsDiagnosedWithoutDroppingOtherEntries() throws Exception {
        XflExportResult result = export(data(true), "0025_components-todo", false);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("PERSISTENT_DATA")));
        assertValue(values(parse(result.getFiles().get("DOMDocument.xml"))), "integer", "i", "-2147483648");
        assertFalse(values(parse(result.getFiles().get("DOMDocument.xml"))).containsKey("double"));
    }

    @Test
    public void nativeIkPersistentDataIsRetainedAlongsideAuthoringExport() throws Exception {
        XflExportResult result = export(null, "0023_inverse_kinematics-cs4-todo", false);
        Map<String, Element> v = values(parse(result.getFiles().get("DOMDocument.xml")));
        assertValue(v, "ikTreeCount", "i", "6");
        assertValue(v, "ikNodeCount", "i", "16");
        assertValue(v, "ikBoneCount", "i", "16");
        assertTrue(v.containsKey("Armature_6"));
        assertTrue(v.get("Armature_6").getAttribute("v").contains("BridgeTree"));
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("IK_SAMPLE_CACHE")));
    }

    static FlaRecord data(boolean invalid) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 0);
        u32(out, 10);
        out.write(0);
        string(out, "integer", true);
        u32(out, 0x80000000L);
        out.write(1);
        string(out, "integers", true);
        u32(out, 3);
        u32(out, -1);
        u32(out, 0);
        u32(out, 0x7fffffff);
        out.write(3);
        string(out, "double", true);
        f64(out, invalid ? Double.NaN : 0.1234567890123456);
        out.write(4);
        string(out, "doubles", true);
        u32(out, 3);
        f64(out, -0.0);
        f64(out, 1.25);
        f64(out, -100);
        out.write(5);
        string(out, "string", true);
        string(out, "<&\"'\n\u03a9", true);
        out.write(7);
        string(out, "bytes", true);
        u32(out, 5);
        out.write(new byte[]{0, 1, 127, (byte) 128, (byte) 255});
        out.write(1);
        string(out, "emptyIntegers", true);
        u32(out, 0);
        out.write(7);
        string(out, "emptyBytes", true);
        u32(out, 0);
        for (int n = 1; n <= 2; n++) {
            out.write(0);
            string(out, "duplicate", true);
            u32(out, n);
        }
        MfcArchive archive = archive(out.toByteArray());
        FlaRecord r = archive.readInline("persistentData", v -> {
        }, DocumentTailRecordDecoder::persistentData);
        assertEquals(archive.getRemaining(), 0);
        return r;
    }

    static XflExportResult export(FlaRecord data, String sample, boolean instances) throws Exception {
        Path path = Paths.get("testdata/fla/cs4", sample);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> fs = Files.walk(path)) {
            for (Path p : (Iterable<Path>) fs.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams);
        if (data != null) {
            doc.getContents().set("persistentData", data);
        }
        if (instances) {
            for (BinaryFlaDocument.Page p : doc.getPages()) {
                inject(p.getRoot(), data, Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()));
            }
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Schema schema = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File("testdata/xfl-schema/xfl.xsd"));
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().endsWith(".xml") && !f.getKey().equals("META-INF/metadata.xml")) {
                schema.newValidator().validate(new StreamSource(f.getValue().openStream()));
            }
        }
        return result;
    }

    static void inject(FlaRecord r, FlaRecord data, Set<FlaRecord> seen) {
        if (r == null || !seen.add(r)) {
            return;
        }
        if (Arrays.asList("CPicSprite", "CPicSymbol", "CPicBitmap", "CPicText").contains(r.getClassName())) {
            r.set("persistentData", data);
        }
        for (FlaRecord c : r.getChildren()) {
            inject(c, data, seen);
        }
    }

    static Element parse(BinaryData data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(data.openStream()).getDocumentElement();
    }

    static Map<String, Element> values(Element root) {
        Map<String, Element> ret = new LinkedHashMap<>();
        for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element && "persistentData".equals(c.getLocalName())) {
                for (Node p = c.getFirstChild(); p != null; p = p.getNextSibling()) {
                    if (p instanceof Element) {
                        ret.put(((Element) p).getAttribute("n"), (Element) p);
                    }
                }
            }
        }
        return ret;
    }

    static void assertValue(Map<String, Element> values, String name, String type, String text) {
        assertTrue(values.containsKey(name), name);
        assertEquals(values.get(name).getAttribute("t"), type);
        assertEquals(values.get(name).getAttribute("v"), text);
    }
}
