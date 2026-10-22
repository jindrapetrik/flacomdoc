package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;
import static com.jpexs.flash.fla.exporter.XflSwatchExportTest.record;

public class XflRecoveredFieldExportTest {

    private static void validate(XflExportResult result) throws Exception {
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(file.getValue().toByteArray());
            }
        }
    }

    @DataProvider(name = "bitmapModes")
    public Object[][] bitmapModes() {
        return new Object[][]{{0, false, false}, {1, true, false}, {2, false, true}};
    }

    @Test(dataProvider = "bitmapModes")
    public void newerBitmapModeOverridesOlderCacheFlagAndPreservesVisibility(int mode, boolean cache, boolean export) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord symbol = records(source, "CPicSprite").get(0);
        symbol.set("cacheAsBitmap", 255);
        symbol.set("bitmapRenderMode", mode);
        symbol.set("bitmapBits32", 0);
        symbol.set("isVisible", 0);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element instance = elements(xml(result), "DOMSymbolInstance").get(0);
        assertEquals(instance.getAttribute("cacheAsBitmap"), Boolean.toString(cache));
        assertEquals(instance.getAttribute("exportAsBitmap"), Boolean.toString(export));
        assertEquals(instance.getAttribute("bits32"), "false");
        assertEquals(instance.getAttribute("isVisible"), "false");
        assertEquals(symbol.get("cacheAsBitmap"), 255);
        validate(result);
    }

    @Test
    public void unassignedBitmapModeReportsTheValueAndRetainsTheBaseCache() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord symbol = records(source, "CPicSprite").get(0);
        symbol.set("cacheAsBitmap", 1);
        symbol.set("bitmapRenderMode", 65535);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertTrue(has(result, "BITMAP_RENDER_MODE"));
        Element instance = elements(xml(result), "DOMSymbolInstance").get(0);
        assertEquals(instance.getAttribute("cacheAsBitmap"), "true");
        assertFalse(instance.hasAttribute("exportAsBitmap"));
    }

    @Test
    public void tlfBlendModeIsIndependentOfTheSelectedEffectIndex() throws Exception {
        FlaRecord text = XflTlfExportTest.text();
        text.set("selectedEffectIndex", 65535);
        text.set("blendMode", 3);
        XflExportResult result = XflTlfExportTest.export(text);
        assertEquals(XflTlfExportTest.tlf(result).getAttribute("blendMode"), "multiply");
        assertFalse(has(result, "BLEND_MODE"));
    }

    @DataProvider(name = "urls")
    public Object[][] urls() {
        return new Object[][]{{null, "old"}, {"new", "new"}, {"", ""}};
    }

    @Test(dataProvider = "urls")
    public void laterTextUrlOverridesOrClearsTheEarlierCopy(String override, String expected) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0012_texts");
        FlaRecord text = records(source, "CPicText").get(0);
        FlaRecord style = XflXml.record(XflXml.records(text, "runs").get(0), "style");
        style.set("url", "old");
        style.set("urlOverride", override);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element attrs = elements(xml(result), "DOMTextAttrs").get(0);
        assertEquals(attrs.getAttribute("url"), expected);
        assertEquals(attrs.hasAttribute("url"), !expected.isEmpty());
        assertEquals(style.get("url"), "old");
        validate(result);
    }

    @DataProvider(name = "morphs")
    public Object[][] morphs() {
        return new Object[][]{{0, -75, "distributive", "-75"}, {1, 25, "angular vertex", "25"},
        {2, 0, "random distribution", "0"}, {3, 100, "linear walk", "100"}, {1, Integer.MIN_VALUE, "angular vertex", "-100"}};
    }

    @Test(dataProvider = "morphs")
    public void morphMappingComesFromParentFrameAndSignedAccelerationFromTheMorph(int mapping, int easing, String expectedMapping, String expectedEasing) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0001_empty_doc");
        FlaRecord frame = records(source, "CPicFrame").get(0);
        FlaRecord morph = record("CPicMorphShape", "acceleration", easing & 0xFFFFFFFFL,
                "segments", Collections.emptyList(), "hints", Collections.emptyList());
        frame.set("shapeTweenBlend", mapping);
        frame.set("morphShape", morph);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element out = elements(xml(result), "MorphShape").get(0);
        assertEquals(out.getAttribute("mappingType"), expectedMapping);
        assertEquals(out.getAttribute("acceleration"), expectedEasing);
        assertEquals(morph.getMorphShape().getAcceleration(), Integer.valueOf(easing));
        assertEquals(has(result, "MORPH_ACCELERATION"), easing < -100);
        validate(result);
    }

    @Test
    public void pagePrinterFieldsUseTwipsAndFixedScaleRatherThanViewportCoordinates() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0001_empty_doc");
        FlaRecord contents = source.getContents();
        contents.set("printMarginLeftTwips", 411);
        contents.set("printMarginRightTwips", 822);
        contents.set("printMarginTopTwips", 1233);
        contents.set("printMarginBottomTwips", 1644);
        contents.set("printCenterHorizontal", 128);
        contents.set("printCenterVertical", 0);
        contents.set("printDisablePostscript", 255);
        contents.set("printLayout", 3);
        contents.set("printScaleFixed16", 81920);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element printer = elements(xml(result), "PrinterSettings").get(0);
        for (String[] expected : new String[][]{{"pageMarginLeft", "411"}, {"pageMarginRight", "822"},
        {"pageMarginTop", "1233"}, {"pageMarginBottom", "1644"}, {"centerHorizontal", "true"},
        {"centerVertical", "false"}, {"disablePostscript", "true"}, {"layout", "storyboard - grid"}, {"scale", "125"}}) {
            assertEquals(printer.getAttribute(expected[0]), expected[1], expected[0]);
        }
        validate(result);
    }

    @Test
    public void explicitLayerHeightAndSymbolCenterMetadataAreExported() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        records(source, "CPicLayer").get(0).set("heightLiteral", 47L);
        FlaRecord descriptor = source.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.LIBRARY).findFirst().get().getDescriptor();
        descriptor.set("lastUniqueIdentifier", 0xFFFFFFFFL);
        descriptor.set("hasValidCenterPoint", 128);
        descriptor.set("isFlexComponent", 1);
        descriptor.set("isSpriteSubclass", 0);
        descriptor.set("transformCenterPointTwips", Arrays.asList(513, -513));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertEquals(elements(xml(result), "DOMLayer").get(0).getAttribute("heightLiteral"), "47");
        Element item = parse(result.getFiles().get("LIBRARY/symbol-1.xml").toByteArray());
        assertEquals(item.getAttribute("lastUniqueIdentifier"), "4294967295");
        assertEquals(item.getAttribute("hasValidCenterPoint"), "true");
        assertEquals(item.getAttribute("isFlexComponent"), "true");
        assertEquals(item.getAttribute("isSpriteSubclass"), "false");
        assertEquals(item.getAttribute("transformCenterPoint"), "#2.01, #FFFFFD.FF");
        validate(result);
    }

    @DataProvider(name = "componentDescriptions")
    public Object[][] componentDescriptions() {
        return new Object[][]{{0, "none"}, {1, "reference"}, {2, "text"}};
    }

    @Test(dataProvider = "componentDescriptions")
    public void componentMetadataIncludesIndependentLockNewFlagsDescriptionsAndTasks(int type, String description) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0025_components-todo");
        FlaRecord descriptor = source.getPages().stream().filter(p -> "Button".equals(p.getDescriptor().get("name"))).findFirst().get().getDescriptor();
        FlaRecord component = XflXml.record(descriptor, "component");
        component.set("parametersAreLocked", 0);
        XflXml.record(component, "properties").set("parametersAreNew", 0);
        component.set("descriptionType", type);
        component.set("symbolDescription", "A&B <description>");
        component.set("requiredMinimumPlayerVersionName", "Flash Player 10");
        component.set("tasks", Arrays.asList(record("componentTask", "description", "First & second", "script", "trace('<x>');"),
                record("componentTask", "description", "First & second", "script", "")));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element item = null;
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().startsWith("LIBRARY/") && file.getKey().endsWith(".xml")) {
                Element candidate = parse(file.getValue().toByteArray());
                if (candidate.getAttribute("name").equals("Button")) {
                    item = candidate;
                }
            }
        }
        assertNotNull(item);
        assertEquals(item.getAttribute("parametersAreLocked"), "false");
        assertEquals(item.getAttribute("parametersAreNew"), "false");
        assertEquals(item.getAttribute("descriptionType"), description);
        assertEquals(item.getAttribute("symbolDescription"), "A&B <description>");
        assertEquals(item.getAttribute("requiredMinimumPlayerVersionName"), "Flash Player 10");
        List<Element> tasks = elements(item, "Task");
        assertEquals(tasks.size(), 2);
        assertEquals(tasks.get(0).getAttribute("description"), "First & second");
        assertEquals(tasks.get(0).getAttribute("script"), "trace('<x>');");
        assertEquals(tasks.get(1).getAttribute("script"), "");
        assertFalse(has(result, "COMPONENT_METADATA"));
        validate(result);
    }

    @Test
    public void symbolAnimationFramesKeepTheirIndependentValuesAndOrder() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord symbol = records(source, "CPicSprite").get(0);
        symbol.set("isExtensible", 255);
        List<FlaRecord> settings = Arrays.asList(record("animationFrameSetting", "loopMode", 0, "firstFrame", 65535),
                record("animationFrameSetting", "loopMode", 2, "firstFrame", 7),
                record("animationFrameSetting", "loopMode", 0, "firstFrame", 65535));
        symbol.set("animationFrameSettings", settings);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element out = elements(xml(result), "DOMSymbolInstance").get(0);
        assertEquals(out.getAttribute("isExtensible"), "true");
        assertEquals(out.getAttribute("animationSymbolMode"), "0,2,0");
        assertEquals(out.getAttribute("animationFrameOffset"), "65535,7,65535");
        assertSame(symbol.get("animationFrameSettings"), settings);
        validate(result);
    }

    @Test
    public void missingAnimationFrameOperandDoesNotWritePartialParallelArrays() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord symbol = records(source, "CPicSprite").get(0);
        symbol.set("isExtensible", 0);
        symbol.set("animationFrameSettings", Arrays.asList(record("animationFrameSetting", "loopMode", 1, "firstFrame", 2),
                record("animationFrameSetting", "loopMode", 2)));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element out = elements(xml(result), "DOMSymbolInstance").get(0);
        assertEquals(out.getAttribute("isExtensible"), "false");
        assertFalse(out.hasAttribute("animationSymbolMode"));
        assertFalse(out.hasAttribute("animationFrameOffset"));
        assertTrue(has(result, "SYMBOL_ANIMATION_SETTINGS"));
        validate(result);
    }

    @DataProvider(name = "actionRanges")
    public Object[][] actionRanges() {
        return new Object[][]{{Collections.emptyList(), "", false}, {Arrays.asList(0, 12, 1, 3), "0,12,1,3", false},
        {Arrays.asList(Integer.MIN_VALUE, -1), "-2147483648,-1", false}, {Arrays.asList(0, 12, 7), "", true}};
    }

    @Test(dataProvider = "actionRanges")
    public void actionEditorBreakpointsAndCollapseOffsetsUseSignedDecimalWords(List<Integer> ranges, String expected, boolean issue) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord frame = records(source, "CPicFrame").get(0);
        String script = "if (x) {\r\n  trace('Ω');\r\n}";
        FlaRecord action = record("actions", "source", script, "mode", 1L,
                "breakpointIndices", Arrays.asList(1L, 0xFFFFFFFFL, 1L), "codeCollapseRangeWords", ranges);
        frame.set("actions", action);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element out = elements(xml(result), "Actionscript").get(0);
        assertEquals(out.getAttribute("breakpoints"), "1,-1,1");
        assertEquals(out.getAttribute("offsets"), expected);
        assertEquals(out.hasAttribute("offsets"), !expected.isEmpty());
        assertEquals(elements(out, "script").get(0).getTextContent(), script);
        assertEquals(action.get("source"), script);
        assertEquals(has(result, "ACTION_EDITOR_METADATA"), issue);
        validate(result);
    }

    @Test
    public void libraryDataBindingsAreTextAndSceneBindingsAreSeparateUtf8Files() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        BinaryFlaDocument.Page scene = source.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.SCENE).findFirst().get();
        FlaRecord descriptor = source.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.LIBRARY).findFirst().get().getDescriptor();
        String binding = "<?xml version=\"1.0\"?><add><binding twoway=\"true\"><source>Ω &amp; x</source><dest>y</dest></binding></add>";
        scene.getDescriptor().set("dataBindingXml", binding);
        descriptor.set("dataBindingXml", binding);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element timeline = elements(xml(result), "DOMTimeline").get(0);
        String href = timeline.getAttribute("hrefDataBindingsFile");
        assertFalse(href.isEmpty());
        assertEquals(result.getFiles().get("bin/" + href).toByteArray(), binding.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Element library = parse(result.getFiles().get("LIBRARY/symbol-1.xml").toByteArray());
        assertEquals(elements(library, "dataBindingXML").get(0).getTextContent(), binding);
        assertEquals(scene.getDescriptor().get("dataBindingXml"), binding);
        assertEquals(descriptor.get("dataBindingXml"), binding);
        XflSchemaTestSupport.validate(result.getFiles().get("DOMDocument.xml").toByteArray());
        XflSchemaTestSupport.validate(result.getFiles().get("LIBRARY/symbol-1.xml").toByteArray());
    }

    @Test
    public void emptyAndMissingBindingsProduceNoReferencesOrFiles() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        for (BinaryFlaDocument.Page page : source.getPages()) {
            page.getDescriptor().set("dataBindingXml", page.getRole() == BinaryFlaDocument.Page.Role.SCENE ? "" : null);
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertFalse(elements(xml(result), "DOMTimeline").get(0).hasAttribute("hrefDataBindingsFile"));
        for (String file : result.getFiles().keySet()) {
            assertFalse(file.startsWith("bin/databindings-"));
        }
        validate(result);
    }

    @DataProvider(name = "drawingFlags")
    public Object[][] drawingFlags() {
        return new Object[][]{{0, 0, true, false}, {1, 0, false, false}, {0, 1, false, true},
        {1, 1, false, true}, {0, 2, true, false}, {0, 4, true, false}};
    }

    @Test(dataProvider = "drawingFlags")
    public void drawingObjectAndFloatingMarkersAreIndependent(int flags, int drawing, boolean group, boolean object) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0002_shapes");
        FlaRecord frame = records(source, "CPicFrame").get(0);
        FlaRecord shape = record("CPicShape", "flags", flags, "drawingObjectFlags", drawing,
                "shapeData", frame.get("shapeData"), "matrixWords", frame.get("matrixWords"));
        for (FlaRecord other : records(source, "CPicFrame")) {
            other.set("shapeData", null);
            other.set("children", Collections.emptyList());
        }
        frame.set("children", Collections.singletonList(shape));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element out = xml(result);
        assertEquals(elements(out, "DOMGroup").size(), group ? 1 : 0);
        Element raw = elements(out, "DOMShape").get(0);
        assertEquals(raw.getAttribute("isDrawingObject"), object ? "true" : "");
        assertEquals(raw.getAttribute("isFloating"), !group && (flags & 1) != 0 ? "true" : "");
        assertEquals(shape.get("drawingObjectFlags"), drawing);
        validate(result);
    }

    @DataProvider(name = "diskObjectFlags")
    public Object[][] diskObjectFlags() {
        Object[][] result = new Object[16][1];
        for (int i = 0; i < result.length; i++) {
            result[i][0] = i;
        }
        return result;
    }

    @Test(dataProvider = "diskObjectFlags")
    public void symbolFlagsAreDecodedFromDiskOrderWithoutMutatingTheRecord(int flags) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord symbol = records(source, "CPicSprite").get(0);
        symbol.set("flags", flags);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element out = elements(xml(result), "DOMSymbolInstance").get(0);
        assertEquals(out.getAttribute("selected"), Boolean.toString((flags & 2) != 0));
        assertEquals(out.getAttribute("locked"), Boolean.toString((flags & 4) != 0));
        assertEquals(symbol.get("flags"), flags);
        validate(result);
    }

    @DataProvider(name = "assignmentQuotes")
    public Object[][] assignmentQuotes() {
        return new Object[][]{{34, 0, 0, "set(\"name\", \"value\");\n"},
        {39, 0, 0, "set(\"name\", 'value');\n"},
        {65535, 0, 0, "set(\"name\", \"value\");\n"},
        {39, 1, 0, "set(name, \"value\");\n"},
        {39, 0, 1, "set(\"name\", value);\n"}};
    }

    @Test(dataProvider = "assignmentQuotes")
    public void assignmentQuoteCharacterAppliesOnlyToLiteralAssignmentValues(int quote, int nameExpression, int valueExpression, String expected) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0010_symbol_props");
        FlaRecord frame = records(source, "CPicFrame").get(0);
        FlaRecord command = record("compiledAction", "type", 24L, "valueQuoteCharacter", quote,
                "parameter1", record("parameter", "text", "name", "flags", nameExpression),
                "parameter2", record("parameter", "text", "value", "flags", valueExpression));
        frame.set("actions", record("actions", "compiledCommands", record("compiledActions", "commands", Collections.singletonList(command))));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        String script = elements(xml(result), "script").get(0).getTextContent();
        assertEquals(script, expected);
        new com.jpexs.flash.fla.script.ScriptParser(com.jpexs.flash.fla.converter.FlaFormatVersion.F4).parse(script, false);
        assertEquals(command.get("valueQuoteCharacter"), quote);
        validate(result);
    }
}
