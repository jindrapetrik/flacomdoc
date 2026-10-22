package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.converter.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;
import static com.jpexs.flash.fla.exporter.XflIntegrationExportTest.*;

public class XflSwatchExportTest {

    static final String NS = "http://ns.adobe.com/xfl/2008/";

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void nativeColorSetsKeepOrderAndRgba(String version) throws Exception {
        BinaryFlaDocument source = nativeFile(version, "0020_document_params");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = xml(result);
        for (String field : Arrays.asList("swatches", "extendedSwatches")) {
            FlaRecord list = (FlaRecord) source.getContents().get(field);
            assertNotNull(list);
            Element holder = elements(xml, field.equals("swatches") ? "swatchLists" : "extendedSwatchLists").get(0);
            @SuppressWarnings("unchecked")
            List<FlaRecord> colors = (List<FlaRecord>) list.get("colors");
            Element swatches = elements(holder, "swatches").get(0);
            List<Element> actual = new ArrayList<>();
            for (Node n = swatches.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) {
                    actual.add((Element) n);
                }
            }
            assertEquals(actual.size(), colors.size());
            for (int i = 0; i < colors.size(); i++) {
                FlaRecord c = colors.get(i);
                FlaPaintStyle style = c.getPaintStyle();
                Element a = actual.get(i);
                if (style.getKind() == FlaPaintStyle.Kind.SOLID) {
                    long word = ((Number) c.get("packedValue")).longValue();
                    assertEquals(a.getAttribute("color"), String.format(Locale.ROOT, "#%02X%02X%02X", word & 255, word >>> 8 & 255, word >>> 16 & 255));
                    assertEquals(Double.parseDouble(a.getAttribute("alpha")), (word >>> 24 & 255) / 255.0);
                } else {
                    assertEquals(a.getLocalName(), style.getFlags() == 16 ? "LinearGradientSwatchItem" : "RadialGradientSwatchItem");
                    NodeList stops = a.getElementsByTagNameNS(NS, "GradientEntry");
                    assertEquals(stops.getLength(), style.getStops().size());
                    for (int j = 0; j < stops.getLength(); j++) {
                        assertEquals(Double.parseDouble(((Element) stops.item(j)).getAttribute("ratio")), style.getStops().get(j).getPosition());
                    }
                }
            }
        }
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().startsWith("SWATCH")), result.getReport());
    }

    @Test
    public void cs4SolidSwatchesMatchPairedAdobeXmlIncludingCachedHsb() throws Exception {
        Element actual = xml(new BinaryFlaToXflExporter().export(nativeFile("cs4", "0020_document_params"))), expected = reference("0020_document_params");
        List<Element> a = elements(actual, "SolidSwatchItem"), b = elements(expected, "SolidSwatchItem");
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            for (String key : Arrays.asList("color", "alpha", "hue", "saturation", "brightness")) {
                String fallback = key.equals("color") ? "#000000" : key.equals("alpha") ? "1" : "0";
                if (key.equals("color")) {
                    assertEquals(a.get(i).getAttribute(key), attr(b.get(i), key, fallback));
                } else {
                    assertEquals(Double.parseDouble(a.get(i).getAttribute(key)), Double.parseDouble(attr(b.get(i), key, fallback)), key);
                }
            }
        }
    }

    @DataProvider(name = "hsb")
    public Object[][] hsb() {
        return new Object[][]{{0x000000, new int[]{0, 0, 0}}, {0xffffff, new int[]{0, 0, 240}}, {0x0000ff, new int[]{0, 239, 120}}, {0x00ff00, new int[]{80, 239, 120}}, {0xff0000, new int[]{160, 239, 120}}, {0x808080, new int[]{0, 0, 120}}, {0x00ffff, new int[]{40, 239, 120}}};
    }

    @Test(dataProvider = "hsb")
    public void nativeIntegerHslUpgradeKeepsHistoricalRounding(long rgb, int[] expected) {
        assertEquals(XflSwatchWriter.hsb(rgb), expected);
    }

    @Test
    public void cachedHsbUsesNative255BoundsAndInvalidWordsRecompute() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0001_empty_doc");
        FlaRecord swatch = solid(0xff0000ffL, 256, 255, 241);
        source.getContents().set("swatches", list(swatch));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element s = elements(xml(result), "SolidSwatchItem").get(0);
        assertEquals(s.getAttribute("hue"), "255");
        assertEquals(s.getAttribute("saturation"), "255");
        assertEquals(s.getAttribute("brightness"), "241");
        assertTrue(has(result, "SWATCH_RANGE"));
        swatch.set("hue", 65535);
        s = elements(xml(new BinaryFlaToXflExporter().export(source)), "SolidSwatchItem").get(0);
        assertEquals(s.getAttribute("hue"), "0");
        assertEquals(s.getAttribute("saturation"), "239");
        assertEquals(swatch.get("hue"), 65535);
    }

    @Test
    public void mixedAndEmptyChildSetsRemainSeparateAndUnsupportedColorIsDiagnosed() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0001_empty_doc");
        FlaRecord first = list(solid(0x80abcdefL, 1, 2, 3), gradient(16, 0));
        FlaRecord bad = solid(0, 0, 0, 0);
        bad.set("flags", 0x20);
        first.set("lists", Arrays.asList(list(), list(bad, gradient(18, 1))));
        source.getContents().set("swatches", first);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element holder = elements(xml(result), "swatchLists").get(0);
        assertEquals(elements(holder, "swatchList").size(), 3);
        assertEquals(elements(holder, "SolidSwatchItem").size(), 1);
        assertEquals(elements(holder, "GradientEntry").size(), 1);
        assertTrue(has(result, "SWATCH_COLOR"));
    }

    @Test
    public void gradientMatrixFocalSpreadAndEmptyPaletteSurviveForwardConversion() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0001_empty_doc");
        FlaRecord g = gradient(18, 2);
        source.getContents().set("swatches", list());
        source.getContents().set("extendedSwatches", list(g));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = xml(result);
        Element radial = elements(xml, "RadialGradientSwatchItem").get(0);
        assertEquals(radial.getAttribute("focalPointRatio"), "-0.2");
        assertEquals(radial.getAttribute("spreadMethod"), "reflect");
        assertEquals(radial.getAttribute("interpolationMethod"), "linearRGB");
        BinaryFlaDocument restored = convert(result, FlaFormatVersion.CS4);
        assertTrue(restored.getIssues().isEmpty(), restored.getIssues().toString());
        @SuppressWarnings("unchecked")
        List<FlaRecord> colors = (List<FlaRecord>) ((FlaRecord) restored.getContents().get("extendedSwatches")).get("colors");
        assertEquals(colors.size(), 1);
        for (String key : Arrays.asList("matrixWords", "focalPoint")) {
            assertEquals(colors.get(0).get(key), g.get(key));
        }
        assertEquals(colors.get(0).get("gradientFlags"), 5L);
        assertEquals(g.get("gradientFlags"), 13L);
        source.getContents().set("extendedSwatches", list());
        restored = convert(new BinaryFlaToXflExporter().export(source), FlaFormatVersion.CS4);
        for (String field : Arrays.asList("swatches", "extendedSwatches")) {
            assertTrue(((List<?>) ((FlaRecord) restored.getContents().get(field)).get("colors")).isEmpty());
        }
    }

    @Test
    public void malformedSwatchXmlStillFailsSchema() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile("cs4", "0020_document_params"));
        String xml = new String(result.getFiles().get("DOMDocument.xml").toByteArray(), "UTF-8");
        for (String replacement : Arrays.asList("unknown='1' hue=", "hue='256' removed=")) {
            String invalid = xml.replaceFirst("hue=", replacement);
            try {
                XflSchemaTestSupport.validate(invalid.getBytes("UTF-8"));
                fail("Invalid swatch accepted");
            } catch (org.xml.sax.SAXException expected) {
            }
        }
    }

    static FlaRecord record(String kind, Object... fields) {
        FlaRecord r;
        try {
            java.lang.reflect.Constructor<FlaRecord> c = FlaRecord.class.getDeclaredConstructor(String.class, int.class, int.class, int.class, int.class, BinaryData.class);
            c.setAccessible(true);
            r = c.newInstance(kind, 0, 0, 0, 0, new BinaryData(new byte[0]));
            java.lang.reflect.Method finish = FlaRecord.class.getDeclaredMethod("finish", int.class, boolean.class);
            finish.setAccessible(true);
            finish.invoke(r, 0, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        for (int i = 0; i < fields.length; i += 2) {
            r.set((String) fields[i], fields[i + 1]);
        }
        return r;
    }

    static FlaRecord list(FlaRecord... colors) {
        return record("colorList", "colors", Arrays.asList(colors), "lists", Collections.emptyList(), "revision", 1);
    }

    static FlaRecord solid(long color, int h, int s, int b) {
        return record("CColorDef", "revision", 4, "flags", 0, "secondaryFlags", 0, "packedValue", color, "hue", h, "saturation", s, "brightness", b);
    }

    static FlaRecord gradient(int flags, int count) {
        List<FlaRecord> stops = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            stops.add(record("colorStop", "ratio", i * 255, "colorRgba", i == 0 ? 0x000000ffL : 0x80ff0000L));
        }
        return record("CColorDef", "revision", 4, "flags", flags, "secondaryFlags", 0, "packedValue", 0xff000000L, "matrixWords", Arrays.asList(65536, 32768, -16384, 131072, -31, 72), "focalPoint", -51, "gradientFlags", 13L, "stops", stops, "hue", 0, "saturation", 0, "brightness", 0);
    }
}
