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
import java.io.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.*;

/**
 * Cubic authoring nets, including generations without an archived point cache.
 */
public class XflCubicExportTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{FlaFormatVersion.F5}, {FlaFormatVersion.MX}, {FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "versions")
    public void nativeControlNetsAndStoredPointsSurviveExportAndImport(FlaFormatVersion version) throws Exception {
        BinaryFlaDocument source = read(version);
        List<FlaRecord> original = cubics(source);
        assertEquals(original.size(), 5);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        List<String> text = text(result);
        assertEquals(text.size(), 5);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().startsWith("CUBIC")), result.getReport());
        for (int i = 0; i < original.size(); i++) {
            List<FlaPoint2D> p = original.get(i).getCubic().getPoints();
            String header = "!" + p.get(0).getRawX() + " " + p.get(0).getRawY() + "(;";
            for (int j = 1; j < 4; j++) {
                header += (j == 1 ? "" : " ") + p.get(j).getRawX() + "," + p.get(j).getRawY();
            }
            assertTrue(text.get(i).startsWith(header), text.get(i));
            if (version.ordinal() < FlaFormatVersion.CS3.ordinal()) {
                assertEquals(text.get(i), header + ");");
            }
        }
        BinaryFlaDocument restored = roundTrip(result, version);
        List<FlaRecord> actual = cubics(restored);
        assertEquals(actual.size(), original.size());
        for (int i = 0; i < actual.size(); i++) {
            for (String field : Arrays.asList("points", "flags", "boundaryStartPointTwips", "boundaryEndPointTwips")) {
                assertEquals(actual.get(i).get(field), original.get(i).get(field), field);
            }
        }
        for (int i = 0; i < actual.size(); i++) {
            assertPoints(actual.get(i).getCubic(), original.get(i).getCubic());
        }
    }

    @Test(dataProvider = "versions")
    public void oldControlNetWithoutApproximationCanBeStoredInEveryTargetGeneration(FlaFormatVersion target) throws Exception {
        BinaryFlaDocument source = read(FlaFormatVersion.F5);
        List<FlaRecord> original = cubics(source);
        BinaryFlaDocument restored = roundTrip(new BinaryFlaToXflExporter().export(source), target);
        List<FlaRecord> actual = cubics(restored);
        assertEquals(actual.size(), 5);
        for (int i = 0; i < 5; i++) {
            assertEquals(actual.get(i).get("points"), original.get(i).get("points"));
            List<FlaCubic.ApproximationPoint> a = actual.get(i).getCubic().getApproximation();
            assertTrue(a == null || a.isEmpty());
        }
    }

    @Test
    public void bothPointFlagsUseAllFourNativeCommandsAndRemainBoolean() throws Exception {
        BinaryFlaDocument source = read(FlaFormatVersion.CS4);
        FlaRecord first = cubics(source).get(0);
        @SuppressWarnings("unchecked")
        List<FlaRecord> entries = (List<FlaRecord>) first.get("entries");
        assertTrue(entries.size() >= 4);
        for (int i = 0; i < 4; i++) {
            entries.get(i).set("controlFlag", (i & 1) == 0 ? 0 : 7);
            entries.get(i).set("value2", (i & 2) == 0 ? 0 : 9);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        String text = text(result).get(0);
        int previous = 0;
        for (char c : new char[]{'q', 'Q', 'p', 'P'}) {
            int at = text.indexOf(c, previous);
            assertTrue(at >= previous, text);
            previous = at + 1;
        }
        FlaCubic restored = cubics(roundTrip(result, FlaFormatVersion.CS4)).get(0).getCubic();
        for (int i = 0; i < 4; i++) {
            assertEquals(restored.getApproximation().get(i).getControlFlag(), i & 1);
            assertEquals(restored.getApproximation().get(i).getSecondaryFlag(), (i >> 1) & 1);
        }
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().startsWith("CUBIC")), result.getReport());
    }

    @Test
    public void explicitEmptyModernPointListRetainsItsControlNet() throws Exception {
        BinaryFlaDocument source = read(FlaFormatVersion.CS4);
        FlaRecord first = cubics(source).get(0);
        first.set("entries", Collections.emptyList());
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertEquals(text(result).size(), 5);
        FlaRecord restored = cubics(roundTrip(result, FlaFormatVersion.CS4)).get(0);
        assertEquals(restored.get("points"), first.get("points"));
        assertTrue(restored.getCubic().getApproximation().isEmpty());
    }

    @Test
    public void incompleteBoundaryDoesNotLeaveHalfACubicOrDiscardRenderedEdges() throws Exception {
        BinaryFlaDocument source = read(FlaFormatVersion.CS4);
        FlaRecord first = cubics(source).get(0);
        first.set("flags", 3);
        first.set("boundaryStartPointTwips", Arrays.asList(1));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertEquals(text(result).size(), 4);
        Element xml = XflMorphHintExportTest.parse(result);
        assertTrue(xml.getElementsByTagNameNS(XflMorphHintExportTest.NS, "Edge").getLength() > 4);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("CUBIC_DATA")));
    }

    @Test
    public void malformedCubicFailsConversionInsteadOfWritingAMismatchedCount() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(read(FlaFormatVersion.F5));
        XflMorphHintExportTest.Memory input = new XflMorphHintExportTest.Memory(), output = new XflMorphHintExportTest.Memory();
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            input.data.put(f.getKey(), f.getValue().toByteArray());
        }
        String xml = new String(input.data.get("DOMDocument.xml"), "UTF-8");
        xml = xml.replaceFirst("cubics=\"[^\"]*\"", "cubics=\"invalid\"");
        input.data.put("DOMDocument.xml", xml.getBytes("UTF-8"));
        try {
            new LegacyFlaConverter(FlaFormatVersion.CS4, "windows-1250").convert(input, output);
            fail("Malformed cubic accepted");
        } catch (IOException ex) {
            assertTrue(ex.getMessage().contains("Invalid cubic edge"), ex.toString());
        }
    }

    static BinaryFlaDocument read(FlaFormatVersion version) throws Exception {
        return nativeDocument("fla/" + version.name().toLowerCase(Locale.ROOT) + "/0004_cubics");
    }

    static List<FlaRecord> cubics(BinaryFlaDocument doc) {
        List<FlaRecord> result = new ArrayList<>();
        for (FlaRecord r : records(doc)) {
            if (r.getCubic() != null) {
                result.add(r);
            }
        }
        return result;
    }

    static List<String> text(XflExportResult result) throws Exception {
        Element xml = XflMorphHintExportTest.parse(result);
        List<String> list = new ArrayList<>();
        NodeList edges = xml.getElementsByTagNameNS(XflMorphHintExportTest.NS, "Edge");
        for (int i = 0; i < edges.getLength(); i++) {
            Element e = (Element) edges.item(i);
            if (e.hasAttribute("cubics")) {
                list.add(e.getAttribute("cubics"));
            }
        }
        return list;
    }

    static BinaryFlaDocument roundTrip(XflExportResult result, FlaFormatVersion version) throws Exception {
        XflMorphHintExportTest.Memory input = new XflMorphHintExportTest.Memory(), output = new XflMorphHintExportTest.Memory();
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            input.data.put(f.getKey(), f.getValue().toByteArray());
        }
        new LegacyFlaConverter(version, "windows-1250").convert(input, output);
        for (Map.Entry<String, ByteArrayOutputStream> f : output.outputs.entrySet()) {
            output.data.put(f.getKey(), f.getValue().toByteArray());
        }
        BinaryFlaDocument restored = new BinaryFlaReader(java.nio.charset.Charset.forName("windows-1250")).readStreams(output.data);
        assertTrue(restored.getIssues().isEmpty(), restored.getIssues().toString());
        return restored;
    }

    static void assertPoints(FlaCubic a, FlaCubic b) {
        if (b.getApproximation() == null) {
            assertNull(a.getApproximation());
            return;
        }
        assertEquals(a.getApproximation().size(), b.getApproximation().size());
        for (int i = 0; i < a.getApproximation().size(); i++) {
            FlaCubic.ApproximationPoint p = a.getApproximation().get(i), q = b.getApproximation().get(i);
            assertEquals(p.getPoint().getRawX(), q.getPoint().getRawX());
            assertEquals(p.getPoint().getRawY(), q.getPoint().getRawY());
            assertEquals(p.isControl(), q.isControl());
            assertEquals(p.getSecondaryFlag() != 0, q.getSecondaryFlag() != 0);
        }
    }
}
