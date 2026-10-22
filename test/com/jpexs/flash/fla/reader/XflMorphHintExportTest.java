/*
 * Copyright (C) 2026 JPEXS.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.exporter.*;
import com.jpexs.flash.fla.converter.*;
import com.jpexs.flash.fla.converter.streams.*;
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

public class XflMorphHintExportTest {

    static final String NS = "http://ns.adobe.com/xfl/2008/";

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{FlaFormatVersion.F5}, {FlaFormatVersion.MX}, {FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "versions")
    public void hintPairsAndSignedKeysRoundTripAcrossAllTargetGenerations(FlaFormatVersion version) throws Exception {
        BinaryFlaDocument source = read(version);
        FlaRecord morph = find(source);
        assertNotNull(morph);
        List<FlaRecord> hints = Arrays.asList(hint(-257, 1, 0, 1024, 0), hint(1025, -2048, 65535, -1, -1));
        morph.set("hints", hints);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = parse(result);
        NodeList actual = xml.getElementsByTagNameNS(NS, "MorphHint");
        assertEquals(actual.getLength(), 2);
        assertEquals(((Element) actual.item(0)).getAttribute("startPoint"), "#FFFFFE.FF, #0.01");
        assertEquals(((Element) actual.item(1)).getAttribute("key"), "-1");
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().startsWith("MORPH_HINT")), result.getReport());
        Memory input = new Memory(), output = new Memory();
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            input.data.put(file.getKey(), file.getValue().toByteArray());
        }
        new LegacyFlaConverter(version, "windows-1250").convert(input, output);
        for (Map.Entry<String, ByteArrayOutputStream> file : output.outputs.entrySet()) {
            output.data.put(file.getKey(), file.getValue().toByteArray());
        }
        BinaryFlaDocument second = new BinaryFlaReader(java.nio.charset.Charset.forName("windows-1250")).readStreams(output.data);
        assertTrue(second.getIssues().isEmpty(), second.getIssues().toString());
        @SuppressWarnings("unchecked")
        List<FlaRecord> restored = (List<FlaRecord>) find(second).get("hints");
        assertEquals(restored.size(), hints.size());
        for (int i = 0; i < hints.size(); i++) {
            for (String key : Arrays.asList("pointA", "pointB", "index")) {
                assertEquals(restored.get(i).get(key), hints.get(i).get(key));
            }
        }
        assertEquals((long) second.getContents().getEndOffset(), second.getStreams().get("Contents").getData().size());
    }

    @Test
    public void unsignedArchiveKeysUseTheNativeSignedXmlSpelling() throws Exception {
        BinaryFlaDocument source = read(FlaFormatVersion.CS4);
        find(source).set("hints", Arrays.asList(hint(0, 0, 0, 0, 0x80000000L), hint(0, 0, 0, 0, 0x7fffffff)));
        NodeList hints = parse(new BinaryFlaToXflExporter().export(source)).getElementsByTagNameNS(NS, "MorphHint");
        assertEquals(((Element) hints.item(0)).getAttribute("key"), "-2147483648");
        assertEquals(((Element) hints.item(1)).getAttribute("key"), "2147483647");
    }

    @Test
    public void nativePointRangeClampingIsExplicitAndDoesNotChangeSource() throws Exception {
        BinaryFlaDocument source = read(FlaFormatVersion.CS4);
        FlaRecord hint = hint(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, 2);
        find(source).set("hints", Arrays.asList(hint));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = (Element) parse(result).getElementsByTagNameNS(NS, "MorphHint").item(0);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("MORPH_HINT_RANGE")));
        assertEquals(xml.getAttribute("startPoint"), "#FE3E00.00, #1C200.00");
        assertEquals(hint.get("pointA"), Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    @Test
    public void incompleteHintDoesNotLeavePartialXmlOrDropCorrespondence() throws Exception {
        BinaryFlaDocument source = read(FlaFormatVersion.CS4);
        FlaRecord hint = hint(0, 0, 0, 0, 0);
        hint.set("pointB", Arrays.asList(1));
        find(source).set("hints", Arrays.asList(hint));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = parse(result);
        assertEquals(xml.getElementsByTagNameNS(NS, "MorphHint").getLength(), 0);
        assertTrue(xml.getElementsByTagNameNS(NS, "MorphSegment").getLength() > 0);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("MORPH_HINTS")));
    }

    static FlaRecord hint(int ax, int ay, int bx, int by, long key) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (long v : new long[]{ax, ay, bx, by, key}) {
            u32(out, v);
        }
        MfcArchive a = archive(out.toByteArray());
        FlaRecord hint = a.readInline("CMorphHintItem", r -> {
        }, MorphRecordDecoder::hint);
        assertEquals(a.getRemaining(), 0);
        return hint;
    }

    static BinaryFlaDocument read(FlaFormatVersion version) throws Exception {
        Path path = Paths.get("testdata/fla", version.name().toLowerCase(Locale.ROOT), "0016_shapetween");
        if (!Files.isDirectory(path)) {
            path = Paths.get(path.toString() + "-todo");
        }
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> fs = Files.walk(path)) {
            for (Path p : (Iterable<Path>) fs.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        return new BinaryFlaReader().readStreams(streams);
    }

    static FlaRecord find(BinaryFlaDocument doc) {
        for (BinaryFlaDocument.Page page : doc.getPages()) {
            FlaRecord value = find(page.getRoot());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static FlaRecord find(FlaRecord r) {
        if (r.get("morphShape") instanceof FlaRecord) {
            return (FlaRecord) r.get("morphShape");
        }
        for (FlaRecord child : r.getChildren()) {
            FlaRecord value = find(child);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static Element parse(XflExportResult result) throws Exception {
        Schema schema = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File("testdata/xfl-schema/xfl.xsd"));
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml") && !file.getKey().equals("META-INF/metadata.xml")) {
                schema.newValidator().validate(new StreamSource(file.getValue().openStream()));
            }
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(result.getFiles().get("DOMDocument.xml").openStream()).getDocumentElement();
    }

    static final class Memory implements InputStorageInterface, OutputStorageInterface {

        final Map<String, byte[]> data = new LinkedHashMap<>();
        final Map<String, ByteArrayOutputStream> outputs = new LinkedHashMap<>();

        public boolean fileExists(String name) {
            return data.containsKey(name);
        }

        public InputStream readFile(String name) {
            return data.containsKey(name) ? new ByteArrayInputStream(data.get(name)) : null;
        }

        public OutputStream getOutputStream(String name) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            outputs.put(name, out);
            return out;
        }

        public void storeData(String name, byte[] bytes) {
            data.put(name, bytes.clone());
        }

        public void storeFile(String name, File file) throws IOException {
            data.put(name, Files.readAllBytes(file.toPath()));
        }

        public void close() {
        }
    }
}
