package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.exporter.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.validation.*;
import javax.xml.transform.stream.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.*;

public class XflIkPaintExportTest {

    static final String NS = "http://ns.adobe.com/xfl/2008/", MATRIX = "<Matrix a='1.1' b='0' c='0' d='0.5' tx='3.15' ty='-2.5'/>";

    @DataProvider(name = "gradients")
    public Object[][] gradients() {
        return new Object[][]{{"linearGradient", "extend", false, 0}, {"linearGradient", "repeat", true, 0}, {"radialGradient", "reflect", true, 127.9}, {"radialGradient", "extend", false, -300}};
    }

    @Test(dataProvider = "gradients")
    public void archivedGradientStopsAndAuthoringMatrixAreRestored(String style, String spread, boolean linear, double focal) throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord instance = skin(source);
        paint(instance, "<fill style='" + style + "' posArray='0,127.8,255' colorArray='#11223380,#445566,#77889900' overflow='" + spread + "' linearRGB='" + linear + "' focalPoint='" + focal + "'>" + MATRIX + "</fill>");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element contour = contour(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        Element gradient = (Element) contour.getElementsByTagNameNS(NS, style.equals("linearGradient") ? "LinearGradient" : "RadialGradient").item(0);
        assertNotNull(gradient);
        assertEquals(gradient.getAttribute("spreadMethod"), spread.equals("extend") ? "pad" : spread);
        assertEquals(gradient.getAttribute("interpolationMethod"), linear ? "linearRGB" : "rgb");
        NodeList stops = gradient.getElementsByTagNameNS(NS, "GradientEntry");
        assertEquals(stops.getLength(), 3);
        assertEquals(Double.parseDouble(((Element) stops.item(1)).getAttribute("ratio")), 127 / 255.0);
        assertEquals(((Element) stops.item(0)).getAttribute("color"), "#112233");
        assertEquals(Double.parseDouble(((Element) stops.item(0)).getAttribute("alpha")), 128 / 255.0);
        Element matrix = (Element) gradient.getElementsByTagNameNS(NS, "Matrix").item(0);
        assertEquals(Double.parseDouble(matrix.getAttribute("a")), Math.round(1.1 * 65536) / 65536.0);
        assertEquals(matrix.getAttribute("tx"), "3.15");
        if (style.equals("radialGradient")) {
            assertEquals(Double.parseDouble(gradient.getAttribute("focalPointRatio")), (int) Math.max(-255, Math.min(255, focal)) / 255.0);
        }
    }

    @DataProvider(name = "strokes")
    public Object[][] strokes() {
        return new Object[][]{
            {"dashed", "DashedStroke", "dash1='3.7' dash2='2.9'", "dash1", "3"},
            {"dotted", "DottedStroke", "dotSpace='5.5'", "dotSpace", "5"},
            {"ragged", "RaggedStroke", "pattern='triple dotted' waveHeight='wild' waveLength='short'", "pattern", "triple dotted"},
            {"stipple", "StippleStroke", "dotSize='large' variation='random sizes' density='sparse'", "variation", "random sizes"},
            {"hatched", "HatchedStroke", "hatchThickness='thin' space='distant' rotate='free' jiggle='bounce' curve='medium curve' length='equal'", "rotate", "free"}
        };
    }

    @Test(dataProvider = "strokes")
    public void allProceduralContourStrokeKindsRetainTheirParameters(String style, String tag, String attributes, String key, String expected) throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord instance = skin(source);
        String xml = raw(instance);
        int begin = xml.indexOf("<stroke "), end = xml.indexOf("/>", begin) + 2;
        String stroke = xml.substring(begin, end).replace("style=\"solid\"", "style=\"" + style + "\" " + attributes);
        set(instance, xml.substring(0, begin) + stroke + xml.substring(end));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element contour = contour(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        Element output = (Element) contour.getElementsByTagNameNS(NS, tag).item(0);
        assertNotNull(output);
        assertEquals(output.getAttribute(key), expected);
        assertEquals(output.getAttribute("weight"), "0.1");
        assertEquals(output.getElementsByTagNameNS(NS, "SolidColor").getLength(), 1);
    }

    @Test
    public void absentFillAndStrokeNeedNoFabricatedTransparentStyle() throws Exception {
        BinaryFlaDocument source = read();
        FlaRecord instance = skin(source);
        paint(instance, "<fill style='noFill'/>");
        set(instance, raw(instance).replaceFirst("<stroke [^>]*/>", "<stroke style='noStroke'/>"));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element contour = contour(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        assertEquals(contour.getElementsByTagNameNS(NS, "SolidColor").getLength(), 0);
        assertEquals(contour.getElementsByTagNameNS(NS, "SolidStroke").getLength(), 0);
    }

    @Test
    public void bitmapContourFillResolvesTheActualExportedMedia() throws Exception {
        BinaryFlaDocument source = read(), mediaSource = nativeDocument("fla/cs4/0013_bitmaps");
        BinaryFlaDocument.Media bitmap = null;
        for (BinaryFlaDocument.Media candidate : mediaSource.getMedia()) {
            if (candidate.getDescriptor().getClassName().equals("CMediaBits")) {
                bitmap = candidate;
                break;
            }
        }
        assertNotNull(bitmap);
        source.add(bitmap);
        String name = (String) bitmap.getDescriptor().get("name");
        FlaRecord instance = skin(source);
        paint(instance, "<fill style='bitmap' bitmapPath='" + name + "' bitmapIsClipped='true'>" + MATRIX + "</fill>");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element contour = contour(result);
        assertFalse(has(result, "IK_AUTHORING"), result.getReport());
        Element fill = (Element) contour.getElementsByTagNameNS(NS, "BitmapFill").item(0);
        assertNotNull(fill);
        assertEquals(fill.getAttribute("bitmapIsClipped"), "true");
        assertFalse(fill.getAttribute("bitmapPath").isEmpty());
    }

    @DataProvider(name = "badPaint")
    public Object[][] badPaint() {
        return new Object[][]{{"<fill style='bitmap' bitmapPath='missing' bitmapIsClipped='true'>" + MATRIX + "</fill>"}, {"<fill style='linearGradient' posArray='0,255' colorArray='#ff0000' overflow='extend' linearRGB='false'>" + MATRIX + "</fill>"}, {"<fill style='radialGradient' posArray='0' colorArray='#ff0000' overflow='invalid' linearRGB='true' focalPoint='0'>" + MATRIX + "</fill>"}};
    }

    @Test(dataProvider = "badPaint")
    public void malformedPaintCannotLeavePartialEditableArmature(String fill) throws Exception {
        BinaryFlaDocument source = read();
        paint(skin(source), fill);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element doc = validate(result);
        assertTrue(has(result, "IK_AUTHORING"), result.getReport());
        assertEquals(doc.getElementsByTagNameNS(NS, "IKTree").getLength(), 0);
    }

    static BinaryFlaDocument read() throws Exception {
        return nativeDocument("fla/cs4/0023_inverse_kinematics-cs4-todo");
    }

    static FlaRecord skin(BinaryFlaDocument source) {
        for (FlaRecord r : records(source)) {
            if (r.getPersistentData() != null && "lsd".equals(r.getPersistentData().getString("lsd"))) {
                return r;
            }
        }
        throw new AssertionError("No skin instance");
    }

    @SuppressWarnings("unchecked")
    static FlaRecord entry(FlaRecord owner) {
        for (FlaRecord r : (List<FlaRecord>) ((FlaRecord) owner.get("persistentData")).get("entries")) {
            if ("lsd_shape_contour_data".equals(r.get("name"))) {
                return r;
            }
        }
        throw new AssertionError();
    }

    static String raw(FlaRecord owner) {
        return (String) entry(owner).get("value");
    }

    static void set(FlaRecord owner, String xml) {
        entry(owner).set("value", xml);
    }

    static void paint(FlaRecord owner, String fill) {
        set(owner, raw(owner).replaceFirst("<fill [^>]*>.*?</fill>", java.util.regex.Matcher.quoteReplacement(fill)));
    }

    static boolean has(XflExportResult result, String code) {
        return result.getIssues().stream().anyMatch(i -> i.getCode().equals(code));
    }

    static Element contour(XflExportResult result) throws Exception {
        return (Element) validate(result).getElementsByTagNameNS(NS, "IKContour").item(0);
    }

    static Element validate(XflExportResult result) throws Exception {
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
}
