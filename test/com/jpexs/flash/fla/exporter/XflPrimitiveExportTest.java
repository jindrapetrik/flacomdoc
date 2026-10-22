package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.nio.file.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflPrimitiveExportTest {

    @Test
    public void componentRectangleGeometryAndPaintsMatchAdobe() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0025_components-todo");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        int count = 0;
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (!file.getKey().startsWith("LIBRARY/") || !file.getKey().endsWith(".xml")) {
                continue;
            }
            XflSchemaTestSupport.validate(file.getValue().toByteArray());
            Element root = parse(file.getValue().toByteArray());
            List<Element> rectangles = elements(root, "DOMRectangleObject");
            if (rectangles.isEmpty()) {
                continue;
            }
            Element paired = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0025_components-todo/LIBRARY", root.getAttribute("name") + ".xml")));
            List<Element> expected = elements(paired, "DOMRectangleObject");
            assertEquals(rectangles.size(), expected.size());
            for (int i = 0; i < rectangles.size(); i++) {
                Element a = rectangles.get(i), b = expected.get(i);
                for (String attr : Arrays.asList("objectWidth", "objectHeight", "x", "y", "topLeftRadius", "topRightRadius", "bottomLeftRadius", "bottomRightRadius")) {
                    assertEquals(Double.parseDouble(a.getAttribute(attr)), Double.parseDouble(b.getAttribute(attr)), 0.000001, attr);
                }
                assertEquals(a.getAttribute("lockFlag"), attr(b, "lockFlag", "false"));
                assertEquals(elements(a, "SolidColor").get(0).getAttribute("color"), elements(b, "SolidColor").get(0).getAttribute("color"));
                count++;
            }
        }
        assertEquals(count, 2);
        assertFalse(has(result, "DISPLAY_OBJECT"), result.getReport());
        assertFalse(has(result, "PRIMITIVE_GEOMETRY"), result.getReport());
    }

    @Test
    public void originalAdobeComponentLibraryWithPrimitivesValidates() throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get("testdata/fla/cs5/0025_components-todo/LIBRARY"))) {
            for (Path p : (Iterable<Path>) paths.filter(f -> f.toString().endsWith(".xml"))::iterator) {
                XflSchemaTestSupport.validate(Files.readAllBytes(p));
            }
        }
    }

    @Test
    public void independentSignedCornerRadiiPreserveArchiveOrder() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0025_components-todo");
        for (FlaRecord r : records(doc, "CPicRect")) {
            r.set("cornerRadii", Arrays.asList(20, -40, 60, -80));
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(f.getValue().toByteArray());
                for (Element e : elements(parse(f.getValue().toByteArray()), "DOMRectangleObject")) {
                    assertEquals(e.getAttribute("topLeftRadius"), "1");
                    assertEquals(e.getAttribute("topRightRadius"), "-2");
                    assertEquals(e.getAttribute("bottomLeftRadius"), "3");
                    assertEquals(e.getAttribute("bottomRightRadius"), "-4");
                }
            }
        }
    }
}
