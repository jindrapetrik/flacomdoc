package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.converter.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;
import static com.jpexs.flash.fla.exporter.XflIntegrationExportTest.*;

public class XflColorEffectExportTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void nativeEditorModesMatchAdobeAttributesAndRemainTheSameOnRoundTrip(String version) throws Exception {
        BinaryFlaDocument source = nativeFile(version, "0010_symbol_props");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element actual = xml(result), expected = reference("0010_symbol_props");
        List<Element> a = elements(actual, "Color"), b = elements(expected, "Color");
        assertEquals(a.size(), 4);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            for (String key : Arrays.asList("brightness", "tintColor", "tintMultiplier", "alphaMultiplier", "redMultiplier", "greenMultiplier", "blueMultiplier", "redOffset", "greenOffset", "blueOffset", "alphaOffset")) {
                assertEquals(a.get(i).hasAttribute(key), b.get(i).hasAttribute(key), key + " on effect " + i);
                if (a.get(i).hasAttribute(key)) {
                    if (key.equals("tintColor")) {
                        assertEquals(a.get(i).getAttribute(key), b.get(i).getAttribute(key));
                    } else {
                        assertEquals(Double.parseDouble(a.get(i).getAttribute(key)), Double.parseDouble(b.get(i).getAttribute(key)), key);
                    }
                }
            }
        }
        FlaFormatVersion target = FlaFormatVersion.valueOf(version.toUpperCase(Locale.ROOT));
        BinaryFlaDocument restored = convert(result, target);
        assertTrue(restored.getIssues().isEmpty(), restored.getIssues().toString());
        List<FlaRecord> old = effects(source), next = effects(restored);
        assertEquals(old.size(), 7);
        assertEquals(old.size(), next.size());
        for (int i = 0; i < old.size(); i++) {
            if (((Number) old.get(i).get("effectType")).intValue() != 0) {
                for (String key : Arrays.asList("effectType", "effectPercent", "effectColorRgba", "alphaMultiplier", "redMultiplier", "greenMultiplier", "blueMultiplier", "alphaOffset", "redOffset", "greenOffset", "blueOffset")) {
                    assertEquals(next.get(i).get(key), old.get(i).get(key), key);
                }
            }
        }
    }

    @DataProvider(name = "legacyModes")
    public Object[][] legacyModes() {
        return new Object[][]{{1, "brightness", "-0.25"}, {2, "tintMultiplier", "0.25"}, {4, "alphaMultiplier", "0.25"}};
    }

    @Test(dataProvider = "legacyModes")
    public void historicalInstanceLevelMetadataSelectsItsOriginalColorMode(int mode, String attribute, String expected) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord instance = records(source, "CPicSprite").get(0), transform = (FlaRecord) instance.get("colorTransform");
        transform.set("effectType", null);
        instance.set("colorEffectType", mode);
        instance.set("colorEffectPercent", mode == 1 ? -25 : 25);
        instance.set("colorEffectRgba", 0x80112233L);
        transform.set("alphaMultiplier", 64);
        Element color = elements(xml(new BinaryFlaToXflExporter().export(source)), "Color").get(0);
        assertEquals(color.getAttribute(attribute), expected);
        if (mode == 2) {
            assertEquals(color.getAttribute("tintColor"), "#332211");
        }
        assertFalse(color.hasAttribute("redOffset"));
    }

    @Test
    public void alphaUsesExactStoredMultiplierRatherThanRoundedPercentage() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord alpha = effects(source).get(3);
        alpha.set("alphaMultiplier", 1);
        alpha.set("effectPercent", 100);
        Element xml = xml(new BinaryFlaToXflExporter().export(source));
        List<Element> colors = elements(xml, "Color");
        assertEquals(colors.get(3).getAttribute("alphaMultiplier"), "0.00390625");
        assertEquals(colors.get(3).getAttributes().getLength(), 1);
    }

    @Test
    public void percentagesAreBoundedWithoutChangingTheStoredData() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        List<FlaRecord> values = effects(source);
        values.get(0).set("effectPercent", -101);
        values.get(1).set("effectPercent", 32767);
        values.get(3).set("alphaMultiplier", -1);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        List<Element> colors = elements(xml(result), "Color");
        assertEquals(colors.get(0).getAttribute("brightness"), "-1");
        assertEquals(colors.get(1).getAttribute("tintMultiplier"), "1");
        assertEquals(colors.get(3).getAttribute("alphaMultiplier"), "0");
        assertEquals(result.getIssues().stream().filter(i -> i.getCode().equals("COLOR_EFFECT_RANGE")).count(), 3L);
        assertEquals(values.get(0).get("effectPercent"), -101);
    }

    @Test
    public void tintAndBrightnessUseTheirEditorParametersInsteadOfStaleChannelCaches() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        List<FlaRecord> values = effects(source);
        for (int i : new int[]{0, 1}) {
            values.get(i).set("redMultiplier", 0);
            values.get(i).set("redOffset", 255);
        }
        List<Element> colors = elements(xml(new BinaryFlaToXflExporter().export(source)), "Color");
        assertEquals(colors.get(0).getAttribute("brightness"), "-0.38");
        assertEquals(colors.get(1).getAttribute("tintColor"), "#8A4984");
        assertFalse(colors.get(1).hasAttribute("redOffset"));
    }

    static List<FlaRecord> effects(BinaryFlaDocument doc) {
        List<FlaRecord> out = new ArrayList<>();
        for (FlaRecord r : records(doc, "CPicSprite")) {
            FlaRecord c = (FlaRecord) r.get("colorTransform");
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }
}
