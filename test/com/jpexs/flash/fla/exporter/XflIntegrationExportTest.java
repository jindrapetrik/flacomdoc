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
package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.converter.*;
import com.jpexs.flash.fla.converter.streams.*;
import com.jpexs.flash.fla.reader.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflIntegrationExportTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{FlaFormatVersion.F5}, {FlaFormatVersion.MX}, {FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "versions")
    public void minimalDocumentWritesACompleteContentsTail(FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><timelines><DOMTimeline name='Scene 1'><layers><DOMLayer><frames><DOMFrame index='0'><elements/></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines></DOMDocument>");
        BinaryFlaDocument doc = convert(input, version);
        assertTrue(doc.getIssues().isEmpty(), doc.getIssues().toString());
        assertEquals(doc.getContents().getEndOffset(), doc.getContentsStream().getData().size());
    }

    @Test(dataProvider = "versions")
    public void fractionalAndNegativeGuidesKeepExactTwipsAcrossRoundTrip(FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><timelines><DOMTimeline name='Scene 1' guides='&lt;guidelines&gt;&lt;guideline direction=&quot;h&quot;&gt;-0.05&lt;/guideline&gt;&lt;guideline direction=&quot;v&quot;&gt;60.05&lt;/guideline&gt;&lt;/guidelines&gt;'><layers/></DOMTimeline></timelines></DOMDocument>");
        BinaryFlaDocument first = convert(input, version), second = convert(new BinaryFlaToXflExporter().export(first), version);
        assertTrue(first.getIssues().isEmpty(), first.getIssues().toString());
        assertTrue(second.getIssues().isEmpty(), second.getIssues().toString());
        assertEquals(first.getPages().get(0).getRoot().get("guides"), Arrays.asList(Arrays.asList(0, -1), Arrays.asList(1, 1201)));
        assertEquals(second.getPages().get(0).getRoot().get("guides"), first.getPages().get(0).getRoot().get("guides"));
    }

    @Test(dataProvider = "versions")
    public void safeIncludeFilenamesResolveLogicalNamesAndAllScenes(FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument currentTimeline='2' width='7000.05' height='400.15' frameRate='29.12347412109375'><symbols><Include href='safe-1.xml'/></symbols><timelines>"
                + scene("First", "") + scene("Second", "<DOMSymbolInstance libraryItemName='Folder/Logical name' symbolType='graphic'/>") + "</timelines></DOMDocument>");
        input.put("LIBRARY/safe-1.xml", "<DOMSymbolItem name='Folder/Logical name' symbolType='graphic'><timeline>" + scene("Logical name", "") + "</timeline></DOMSymbolItem>");
        BinaryFlaDocument doc = convert(input, version);
        assertTrue(doc.getIssues().isEmpty(), doc.getIssues().toString());
        assertEquals(doc.getSceneDescriptors().size(), 2);
        assertSame(doc.getContents().get("selectedScene"), doc.getSceneDescriptors().get(1));
        assertEquals(doc.getWidthTwips().intValue(), 140001);
        assertEquals(doc.getHeightTwips().intValue(), 8003);
        assertEquals(doc.getFrameRate().doubleValue(), 29.12347412109375);
        assertEquals(records(doc, "CPicSymbol").size(), 1);
        assertEquals(((Number) records(doc, "CPicSymbol").get(0).get("libraryId")).longValue(), doc.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.LIBRARY).findFirst().get().getLibraryId().longValue());
        BinaryFlaDocument second = convert(new BinaryFlaToXflExporter().export(doc), version);
        assertTrue(second.getIssues().isEmpty(), second.getIssues().toString());
        assertEquals(second.getSceneDescriptors().size(), 2);
        assertEquals(records(second, "CPicSymbol").size(), 1);
        assertEquals(second.getWidthTwips(), doc.getWidthTwips());
    }

    @Test(dataProvider = "versions")
    public void nativeSoundRoundTripKeepsCountsFormatsAndAllPayloadBytes(FlaFormatVersion version) throws Exception {
        BinaryFlaDocument before = nativeFile(version.name().toLowerCase(Locale.ROOT), "0014_sounds");
        BinaryFlaDocument after = convert(new BinaryFlaToXflExporter().export(before), version);
        assertTrue(after.getIssues().isEmpty(), after.getIssues().toString());
        assertEquals(after.getMedia().size(), before.getMedia().size());
        for (int i = 0; i < before.getMedia().size(); i++) {
            assertEquals(after.getMedia().get(i).getSoundFormat().getFlags(), before.getMedia().get(i).getSoundFormat().getFlags());
            assertEquals(after.getMedia().get(i).getDescriptor().get("sampleCount"), before.getMedia().get(i).getDescriptor().get("sampleCount"));
            assertEquals(after.getMedia().get(i).getStream().getData().toByteArray(), before.getMedia().get(i).getStream().getData().toByteArray());
        }
    }

    @Test(dataProvider = "versions")
    public void nativeTextRoundTripKeepsCharactersAndTwipSizes(FlaFormatVersion version) throws Exception {
        BinaryFlaDocument before = nativeFile(version.name().toLowerCase(Locale.ROOT), "0012_texts");
        BinaryFlaDocument after = convert(new BinaryFlaToXflExporter().export(before), version);
        assertTrue(after.getIssues().isEmpty(), after.getIssues().toString());
        List<FlaRecord> a = records(before, "CPicText"), b = records(after, "CPicText");
        assertEquals(b.size(), a.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(b.get(i).get("text"), a.get(i).get("text"));
            assertEquals(b.get(i).get("bounds"), a.get(i).get("bounds"));
            @SuppressWarnings("unchecked")
            List<FlaRecord> ar = (List<FlaRecord>) a.get(i).get("runs"), br = (List<FlaRecord>) b.get(i).get("runs");
            assertEquals(br.size(), ar.size());
            for (int j = 0; j < ar.size(); j++) {
                assertEquals(((FlaRecord) br.get(j).get("style")).get("fontSize"), ((FlaRecord) ar.get(j).get("style")).get("fontSize"));
            }
        }
    }

    @Test(dataProvider = "versions")
    public void nativeMorphRoundTripKeepsStoredCorrespondence(FlaFormatVersion version) throws Exception {
        BinaryFlaDocument before = nativeFile(version.name().toLowerCase(Locale.ROOT), "0016_shapetween");
        BinaryFlaDocument after = convert(new BinaryFlaToXflExporter().export(before), version);
        assertTrue(after.getIssues().isEmpty(), after.getIssues().toString());
        List<FlaRecord> af = records(before, "CPicFrame"), bf = records(after, "CPicFrame");
        assertEquals(bf.size(), af.size());
        for (int i = 0; i < af.size(); i++) {
            FlaRecord a = (FlaRecord) af.get(i).get("morphShape"), b = (FlaRecord) bf.get(i).get("morphShape");
            assertEquals(b == null, a == null);
            if (a == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<FlaRecord> as = (List<FlaRecord>) a.get("segments"), bs = (List<FlaRecord>) b.get("segments");
            assertEquals(bs.size(), as.size());
            for (int j = 0; j < as.size(); j++) {
                for (String field : Arrays.asList("startPointA", "startPointB", "strokeIndex1", "strokeIndex2", "fillIndex1", "fillIndex2")) {
                    assertEquals(bs.get(j).get(field), as.get(j).get(field));
                }
            }
        }
    }

    private static String scene(String name, String elements) {
        return "<DOMTimeline name='" + name + "'><layers><DOMLayer><frames><DOMFrame index='0'><elements>" + elements + "</elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline>";
    }

    static BinaryFlaDocument convert(XflExportResult result, FlaFormatVersion version) throws Exception {
        Memory in = new Memory();
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            in.data.put(f.getKey(), f.getValue().toByteArray());
        }
        return convert(in, version);
    }

    static BinaryFlaDocument convert(Memory input, FlaFormatVersion version) throws Exception {
        Memory out = new Memory();
        new LegacyFlaConverter(version, "windows-1250").convert(input, out);
        for (Map.Entry<String, ByteArrayOutputStream> e : out.outputs.entrySet()) {
            out.data.put(e.getKey(), e.getValue().toByteArray());
        }
        return new BinaryFlaReader(java.nio.charset.Charset.forName("windows-1250")).readStreams(out.data);
    }

    static final class Memory implements InputStorageInterface, OutputStorageInterface {

        final Map<String, byte[]> data = new LinkedHashMap<>();
        final Map<String, ByteArrayOutputStream> outputs = new LinkedHashMap<>();

        void put(String name, String content) {
            data.put(name, content.getBytes(StandardCharsets.UTF_8));
        }

        public boolean fileExists(String file) {
            return data.containsKey(file);
        }

        public InputStream readFile(String file) {
            return data.containsKey(file) ? new ByteArrayInputStream(data.get(file)) : null;
        }

        public OutputStream getOutputStream(String file) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            outputs.put(file, out);
            return out;
        }

        public void storeData(String file, byte[] bytes) {
            data.put(file, bytes.clone());
        }

        public void storeFile(String name, File file) throws IOException {
            data.put(name, Files.readAllBytes(file.toPath()));
        }

        public void close() {
        }
    }
}
