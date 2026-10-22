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

public class XflOvalExportTest {

    @Test
    public void storedOvalBoundsAnglesAndClosureSurviveExport() throws Exception {
        XflExportResult result = oval(450.5f);
        int count = 0;
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().endsWith(".xml") && !f.getKey().equals("META-INF/metadata.xml")) {
                byte[] xml = f.getValue().toByteArray();
                SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File(System.getProperty("flacomdoc.xflSchema", "testdata/xfl-schema/xfl.xsd"))).newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                NodeList ovals = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "DOMOvalObject");
                for (int i = 0; i < ovals.getLength(); i++) {
                    Element e = (Element) ovals.item(i);
                    assertEquals(e.getAttribute("x"), "-10");
                    assertEquals(e.getAttribute("y"), "-20");
                    assertEquals(e.getAttribute("objectWidth"), "50");
                    assertEquals(e.getAttribute("objectHeight"), "80");
                    assertEquals(e.getAttribute("startAngle"), "-30.25");
                    assertEquals(e.getAttribute("endAngle"), "450.5");
                    assertEquals(e.getAttribute("innerRadius"), "37.5");
                    assertEquals(e.getAttribute("closePath"), "true");
                    count++;
                }
            }
        }
        assertEquals(count, 2);
    }

    @Test
    public void nonfiniteOvalParameterRetainsRenderedShapeWithoutPartialPrimitive() throws Exception {
        XflExportResult result = oval(Float.NaN);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("PRIMITIVE_GEOMETRY")));
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().endsWith(".xml") && !f.getKey().equals("META-INF/metadata.xml")) {
                assertFalse(new String(f.getValue().toByteArray(), "UTF-8").contains("DOMOvalObject"));
            }
        }
    }

    private static XflExportResult oval(float end) throws Exception {
        Path path = Paths.get("testdata/fla/cs4/0025_components-todo");
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(path)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams);
        for (BinaryFlaDocument.Page p : doc.getPages()) {
            replace(p.getRoot(), end, Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()));
        }
        return new BinaryFlaToXflExporter().export(doc);
    }

    private static void replace(FlaRecord parent, float end, Set<FlaRecord> seen) {
        if (parent == null || !seen.add(parent)) {
            return;
        }
        List<FlaRecord> children = new ArrayList<>();
        for (FlaRecord r : parent.getChildren()) {
            if (r.getClassName().equals("CPicRect")) {
                FlaRecord oval = new FlaRecord("CPicOval", 0, 0, 0, 0, new BinaryData(new byte[0]));
                for (Map.Entry<String, Object> f : r.getFields().entrySet()) {
                    oval.set(f.getKey(), f.getValue());
                }
                oval.set("bounds", Arrays.asList(-200, 800, -400, 1200));
                oval.set("startAngleDegrees", -30.25f);
                oval.set("endAngleDegrees", end);
                oval.set("innerRadiusPercent", 37.5f);
                oval.set("primitiveFlags", 1);
                oval.finish(0, true);
                r = oval;
            }
            children.add(r);
            replace(r, end, seen);
        }
        parent.set("children", children);
    }
}
