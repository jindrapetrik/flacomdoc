package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.exporter.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class XflComplexParameterExportTest {

    @DataProvider(name = "values")
    public Object[][] values() {
        return new Object[][]{
            {2, new String[]{"", "a,b", "<&\""}, new String[]{"", "", ""}, 0L, ",a&#2Cb,<&\"", ""},
            {3, new String[]{"1", "two,three"}, new String[]{"x", "a,b"}, 0L, "x:1,a&#2Cb:two&#2Cthree", ""},
            {4, new String[]{"", "a,b", "c"}, new String[]{"", "", ""}, 1L, "a,b", ",a&#2Cb,c"},
            {4, new String[]{"a", "b"}, new String[]{"", ""}, 0xffffffffL, "", ""},
            {16, new String[]{"v,1", "v2"}, new String[]{"n1", "n2"}, 0L, "n1,v&#2C1,n2,v2", ""},
            {11, new String[]{"operation"}, new String[]{""}, 0L, "operation", ""},
            {12, new String[]{"item"}, new String[]{""}, 0L, "item", ""},
            {14, new String[]{"category"}, new String[]{""}, 0L, "category", ""},
            {19, new String[]{"preview"}, new String[]{""}, 0L, "preview", ""},
            {2, new String[]{}, new String[]{}, 0L, "", ""}
        };
    }

    @Test(dataProvider = "values")
    public void nativeListObjectAndCuePointTemplates(int type, String[] values, String[] names, long selected, String expected, String enumeration) throws Exception {
        XflExportResult result = export(property(type, values, names, selected, false));
        Element inspect = inspect(result);
        assertEquals(inspect.getAttribute("defaultValue"), expected);
        assertEquals(inspect.getAttribute("enumeration"), enumeration);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPONENT_PARAMETERS")), result.getReport());
    }

    @Test
    public void collectionRetainsColumnsRowsAndEscapedCells() throws Exception {
        XflExportResult result = export(property(13, new String[0], new String[0], 0, true));
        assertEquals(inspect(result).getAttribute("defaultValue"), "Item, Class, label, 2, label, 5, String, a|b, enabled, 7, Boolean, , 2, one&#2Ctwo, true, <&\"', false");
    }

    @Test
    public void collectionHeadersAndColumnDefaultsHaveSemanticNames() throws Exception {
        FlaRecord properties = property(13, new String[0], new String[0], 0, true);
        FlaRecord parameter = records(properties, "entries").get(0);
        FlaRecord table = (FlaRecord) ((FlaRecord) parameter.get("value")).get("table");
        assertEquals(table.get("collectionClass"), "Item");
        assertEquals(table.get("collectionItemClass"), "Class");
        List<FlaRecord> columns = records(table, "columns");
        assertEquals(columns.get(0).get("name"), "label");
        assertEquals(columns.get(0).get("defaultValue"), "String");
        assertEquals(columns.get(0).get("enumeration"), "a,b");
        // A default value is data; the numeric type is stored separately.
        columns.get(0).set("defaultValue", "Initial & value");
        assertTrue(inspect(export(properties)).getAttribute("defaultValue").contains("label, 5, Initial & value, a|b"));
    }

    @Test
    public void reservedTokenOmitsTheEntireListWithoutCorruptingValues() throws Exception {
        XflExportResult result = export(property(2, new String[]{"literal&#2C"}, new String[]{""}, 0, false));
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPONENT_PARAMETERS")));
        assertEquals(component(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "parametersAsXML").getLength(), 0);
    }

    @DataProvider(name = "legacyScalar")
    public Object[][] legacyScalar() {
        return new Object[][]{
            {5, "Žluťoučký <&\"", "Žluťoučký <&\""}, {1, "default text", "default text"},
            {6, " -12,5 ", " -12,5 "}, {6, "+", "+"}, {6, "NaN", "0"}, {6, "1e2", "0"}, {6, "", "0"},
            {7, "true", "true"}, {7, "TRUE", "false"}, {7, "false", "false"},
            {9, "aBcD12extra", "#aBcD12"}, {9, "#xyz123", "#000000"}, {9, "", "#000000"},
            {8, "Times New Roman", "Times New Roman"}, {15, "Example.Type", "Example.Type"},
            {17, "skin.swf", "skin.swf"}, {18, "video.flv", "video.flv"}, {19, "preview", "preview"},
            {2, "one,two", ""}, {3, "name:value", ""}, {4, "selected", ""},
            {13, "legacy collection", ", , , 0, 0"}, {16, "name,value", ""},
            {2, "", ""}, {3, "", ""}, {4, "", ""}, {13, "", ", , , 0, 0"}, {16, "", ""}
        };
    }

    @Test(dataProvider = "legacyScalar")
    public void legacyScalarPropertyListsFollowNativeTextNormalization(int type, String raw, String expected) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0);
        u32(bytes, 1);
        u32(bytes, type);
        string(bytes, "Legacy parameter", true);
        string(bytes, raw, true);
        MfcArchive archive = archive(bytes.toByteArray());
        FlaRecord properties = archive.readInline("properties", r -> {
        }, PropertyRecordDecoder::list);
        assertEquals(archive.getRemaining(), 0);
        XflExportResult result = export(properties);
        Element inspect = inspect(result);
        assertEquals(inspect.getAttribute("defaultValue"), expected);
        assertEquals(inspect.getAttribute("variable"), "Legacy parameter");
        boolean reset = Arrays.asList(2, 3, 4, 13, 16).contains(type) && !raw.isEmpty();
        assertEquals(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPONENT_PARAMETER_UPGRADE")), reset);
        if (type == 4) {
            assertEquals(inspect.getAttribute("enumeration"), "");
        }
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPONENT_PARAMETERS")), result.getReport());
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml") && !file.getKey().equals("META-INF/metadata.xml")) {
                javax.xml.validation.SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File("testdata/xfl-schema/xfl.xsd")).newValidator().validate(new javax.xml.transform.stream.StreamSource(file.getValue().openStream()));
            }
        }
        @SuppressWarnings("unchecked")
        List<FlaRecord> records = (List<FlaRecord>) properties.get("entries");
        assertEquals(records.get(0).get("value"), raw);
    }

    static FlaRecord property(int type, String[] values, String[] names, long selected, boolean table) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(2);
        u32(out, 1);
        out.write(3);
        string(out, "Complex parameter", true);
        out.write(8);
        u16(out, type);
        u32(out, values.length);
        u32(out, selected);
        u32(out, 1);
        for (int i = 0; i < values.length; i++) {
            string(out, names[i], true);
            string(out, values[i], true);
            u16(out, 5);
        }
        if (table) {
            string(out, "Item", true);
            string(out, "Class", true);
            string(out, "label", true);
            u32(out, 2);
            for (String[] c : new String[][]{{"label", "5", "String", "a,b"}, {"enabled", "7", "Boolean", ""}}) {
                string(out, c[0], true);
                u16(out, Integer.parseInt(c[1]));
                string(out, c[2], true);
                string(out, c[3], true);
            }
            u32(out, 2);
            for (String cell : new String[]{"one,two", "true", "<&\"'", "false"}) {
                string(out, cell, true);
            }
        }
        u32(out, 0);
        string(out, "complex", true);
        out.write(0);
        out.write(new byte[16]);
        string(out, "Test", true);
        u32(out, 1);
        u32(out, 0);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord p = a.readInline("properties", r -> {
        }, PropertyRecordDecoder::list);
        assertEquals(a.getRemaining(), 0);
        assertTrue(p.isComplete());
        return p;
    }

    static XflExportResult export(FlaRecord properties) throws Exception {
        Path path = Paths.get("testdata/fla/cs4/0025_components-todo");
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> fs = Files.walk(path)) {
            for (Path p : (Iterable<Path>) fs.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams);
        boolean found = false;
        for (BinaryFlaDocument.Page page : doc.getPages()) {
            if (page.getDescriptor() != null && "Button".equals(page.getDescriptor().get("name"))) {
                ((FlaRecord) page.getDescriptor().get("component")).set("properties", properties);
                found = true;
            }
        }
        assertTrue(found);
        return new BinaryFlaToXflExporter().export(doc);
    }

    static Element component(XflExportResult result) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        for (Map.Entry<String, BinaryData> entry : result.getFiles().entrySet()) {
            if (entry.getKey().startsWith("LIBRARY/") && entry.getKey().endsWith(".xml")) {
                Element e = f.newDocumentBuilder().parse(entry.getValue().openStream()).getDocumentElement();
                if (e.getLocalName().equals("DOMComponentItem")) {
                    return e;
                }
            }
        }
        throw new AssertionError("Component not exported");
    }

    static Element inspect(XflExportResult result) throws Exception {
        NodeList params = component(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "parametersAsXML");
        assertEquals(params.getLength(), 1, result.getReport());
        String fragment = params.item(0).getTextContent();
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        return (Element) f.newDocumentBuilder().parse(new ByteArrayInputStream(("<root>" + fragment + "</root>").getBytes("UTF-8"))).getElementsByTagName("Inspectable").item(0);
    }
}
