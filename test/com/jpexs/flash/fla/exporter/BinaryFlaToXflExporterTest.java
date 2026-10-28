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

import com.jpexs.flash.fla.converter.LegacyFlaConverter;
import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.streams.InputStorageInterface;
import com.jpexs.flash.fla.converter.streams.OutputStorageInterface;
import com.jpexs.flash.fla.reader.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.*;
import static org.testng.Assert.*;

public class BinaryFlaToXflExporterTest {

    private static final String NS = "http://ns.adobe.com/xfl/2008/";
    private static final String SHAPE = "<DOMShape><fills><FillStyle index='1'><SolidColor color='#123456' alpha='0.5'/></FillStyle></fills>"
            + "<strokes><StrokeStyle index='1'><SolidStroke weight='2.5' caps='square' joints='miter' miterLimit='4' scaleMode='vertical'>"
            + "<fill><SolidColor color='#ABCDEF'/></fill></SolidStroke></StrokeStyle></strokes>"
            + "<edges><Edge strokeStyle='1' fillStyle1='1' edges='!#FFFFFF.80 #0.01|#2.40 #FFFFFF.FF[#3.12 4 5 6'/></edges></DOMShape>";

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{FlaFormatVersion.F5}, {FlaFormatVersion.MX}, {FlaFormatVersion.MX2004},
        {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "versions")
    public void exportsStageLayersFramePositionsAndSourceScript(FlaFormatVersion version) throws Exception {
        String xml = "<DOMDocument width='640' height='360' frameRate='29.5' backgroundColor='#123456'><timelines>"
                + "<DOMTimeline name='\u017Dlu\u0165ou\u010Dk\u00FD &amp; &quot;scene&quot;' currentFrame='258'><layers>"
                + "<DOMLayer name='Top' visible='false' locked='true' color='#123456'><frames>"
                + "<DOMFrame index='0' duration='10' name='Label' labelType='comment'><Actionscript><script>trace(&quot;&lt;&amp;&quot;);</script></Actionscript><elements/></DOMFrame>"
                + "<DOMFrame index='10' duration='4' name='Next'><elements/></DOMFrame></frames></DOMLayer>"
                + "<DOMLayer name='Bottom'><frames><DOMFrame index='0'><elements/></DOMFrame></frames></DOMLayer>"
                + "</layers></DOMTimeline></timelines></DOMDocument>";
        BinaryFlaDocument model = convert(xml, version);
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        Element d = xml(result, "DOMDocument.xml");
        assertEquals(d.getNamespaceURI(), NS);
        assertEquals(d.getAttribute("width"), "640");
        assertEquals(d.getAttribute("height"), "360");
        assertEquals(d.getAttribute("frameRate"), "29.5");
        assertEquals(d.getAttribute("backgroundColor"), "#123456");
        Element timeline = first(d, "DOMTimeline");
        assertEquals(timeline.getAttribute("name"), "\u017Dlu\u0165ou\u010Dk\u00FD & \"scene\"");
        assertEquals(timeline.getAttribute("currentFrame"), version.ordinal() >= FlaFormatVersion.F8.ordinal() ? "258" : "0");
        NodeList layers = d.getElementsByTagNameNS(NS, "DOMLayer");
        Element top = (Element) layers.item(0);
        assertEquals(top.getAttribute("name"), "Top");
        assertEquals(top.getAttribute("visible"), "false");
        assertEquals(top.getAttribute("locked"), "true");
        assertEquals(top.getAttribute("color"), "#123456");
        NodeList frames = top.getElementsByTagNameNS(NS, "DOMFrame");
        assertEquals(((Element) frames.item(1)).getAttribute("index"), "10");
        assertEquals(((Element) frames.item(0)).getAttribute("labelType"), "comment");
        assertEquals(first(top, "script").getTextContent().trim(), "trace(\"<&\");");
        assertEquals(((Element) layers.item(1)).getAttribute("name"), "Bottom");
        assertTrue(has(result, "EXPORT_SCOPE"));
    }

    @Test(dataProvider = "versions")
    public void contoursPreserveSignedFractionalCoordinatesAndStyleAssignments(FlaFormatVersion version) throws Exception {
        BinaryFlaDocument original = convert(stage(SHAPE), version);
        XflExportResult result = new BinaryFlaToXflExporter().export(original);
        Element d = xml(result, "DOMDocument.xml");
        assertEquals(d.getElementsByTagNameNS(NS, "DOMShape").getLength(), 1);
        assertEquals(first(d, "SolidColor").getAttribute("color"), "#123456");
        assertTrue(first(d, "Edge").getAttribute("edges").startsWith("!#FFFFFF.80 #0.01"));
        BinaryFlaDocument roundTrip = convert(result, version);
        FlaRecord before = shapeData(original), after = shapeData(roundTrip);
        List<FlaRecord> a = records(before, "edges"), b = records(after, "edges");
        assertEquals(b.size(), a.size());
        for (int i = 0; i < a.size(); i++) {
            for (String field : Arrays.asList("startX", "startY", "controlX", "controlY", "endX", "endY", "strokeIndex", "fillIndex0", "fillIndex1", "curve")) {
                assertEquals(b.get(i).get(field), a.get(i).get(field), field);
            }
        }
        assertEquals(records(after, "fills").get(0).get("packedValue"), records(before, "fills").get(0).get("packedValue"));
        assertEquals(records(after, "strokes").get(0).get("width"), 50);
        if (version.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            assertEquals(records(after, "strokes").get(0).get("capStyle"), 2);
        }
    }

    @Test
    public void gradientsPreserveNegativeFocalPointStopsAndMatrices() throws Exception {
        String shape = "<DOMShape><fills><FillStyle index='1'><RadialGradient focalPointRatio='-0.5' spreadMethod='reflect' interpolationMethod='linearRGB'>"
                + "<matrix><Matrix a='0.5' d='0.25' tx='12.5' ty='-3'/></matrix>"
                + "<GradientEntry ratio='0.25' color='#FF0000' alpha='0.25'/><GradientEntry ratio='0.75' color='#00FF00'/>"
                + "</RadialGradient></FillStyle></fills><edges><Edge fillStyle1='1' edges='!0 0|1 1'/></edges></DOMShape>";
        BinaryFlaDocument model = convert(stage(shape), FlaFormatVersion.CS4);
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        Element g = first(xml(result, "DOMDocument.xml"), "RadialGradient");
        assertTrue(Double.parseDouble(g.getAttribute("focalPointRatio")) < 0);
        assertEquals(g.getAttribute("spreadMethod"), "reflect");
        assertEquals(g.getAttribute("interpolationMethod"), "linearRGB");
        assertEquals(first(g, "Matrix").getAttribute("tx"), "12.5");
        FlaRecord before = records(shapeData(model), "fills").get(0);
        FlaRecord after = records(shapeData(convert(result, FlaFormatVersion.CS4)), "fills").get(0);
        for (String field : Arrays.asList("focalPoint", "gradientFlags", "matrixWords")) {
            assertEquals(after.get(field), before.get(field));
        }
        assertEquals(after.getPaintStyle().getStops().get(0).getRgbaWord(), before.getPaintStyle().getStops().get(0).getRgbaWord());
    }

    @Test(dataProvider = "versions")
    public void selectionBitsDoNotBecomeStyleIndexes(FlaFormatVersion version) throws Exception {
        BinaryFlaDocument model = convert(stage(SHAPE.replace("#0.01|", "#0.01S6|")), version);
        FlaRecord edge = records(shapeData(model), "edges").get(0);
        assertEquals(edge.get("strokeIndex"), 32769);
        assertEquals(edge.get("fillIndex1"), 32769);
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        assertFalse(has(result, "STYLE_INDEX"));
        Element exported = first(xml(result, "DOMDocument.xml"), "Edge");
        assertEquals(exported.getAttribute("strokeStyle"), "1");
        assertEquals(exported.getAttribute("fillStyle1"), "1");
        assertTrue(exported.getAttribute("edges").contains("S6"));
        FlaRecord roundTrip = records(shapeData(convert(result, version)), "edges").get(0);
        assertEquals(roundTrip.get("strokeIndex"), 32769);
        assertEquals(roundTrip.get("fillIndex1"), 32769);
    }

    @Test
    public void symbolReferencesUseIdsAndSafeGeneratedFileNames() throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><symbols><Include href='One.xml'/><Include href='Two.xml'/></symbols><timelines>"
                + "<DOMTimeline name='Scene'><layers><DOMLayer name='L'><frames><DOMFrame index='0'><elements>"
                + "<DOMSymbolInstance libraryItemName='One' symbolType='graphic' loop='single frame' firstFrame='8'>"
                + "<matrix><Matrix a='2' d='0.5' tx='12.5' ty='-3'/></matrix></DOMSymbolInstance>"
                + "<DOMSymbolInstance libraryItemName='Two' name='clip'/>"
                + "</elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines></DOMDocument>");
        input.put("LIBRARY/One.xml", "<DOMSymbolItem name='One' symbolType='graphic' linkageExportForRS='true' linkageIdentifier='id' linkageURL='lib.swf'><timeline><DOMTimeline name='One'/></timeline></DOMSymbolItem>");
        input.put("LIBRARY/Two.xml", "<DOMSymbolItem name='Two'><timeline><DOMTimeline name='Two'/></timeline></DOMSymbolItem>");
        BinaryFlaDocument model = convert(input, FlaFormatVersion.CS4);
        model.getLibraryDescriptors().get(0).set("name", "../../escape & \"\u6F22\"");
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        assertNotNull(result.getFiles().get("LIBRARY/symbol-1.xml"));
        assertEquals(result.getFiles().keySet(), new HashSet<>(Arrays.asList(
                "LIBRARY/symbol-1.xml", "LIBRARY/symbol-2.xml", "PublishSettings.xml",
                "META-INF/metadata.xml", "DOMDocument.xml", "document.xfl")));
        assertNotNull(result.getFiles().get("PublishSettings.xml"));
        Element instance = first(xml(result, "DOMDocument.xml"), "DOMSymbolInstance");
        assertNotNull(instance, result.getReport());
        assertEquals(instance.getAttribute("libraryItemName"), "../../escape & \"\u6F22\"");
        assertEquals(instance.getAttribute("firstFrame"), "8");
        assertEquals(instance.getAttribute("loop"), "single frame");
        assertEquals(first(instance, "Matrix").getAttribute("tx"), "12.5");
        Element symbol = xml(result, "LIBRARY/symbol-1.xml");
        assertEquals(symbol.getAttribute("name"), instance.getAttribute("libraryItemName"));
        assertEquals(symbol.getAttribute("linkageExportForRS"), "true");
        assertEquals(symbol.getAttribute("linkageIdentifier"), "id");
        assertEquals(symbol.getAttribute("symbolType"), "graphic");
    }

    @Test
    public void nativeNestedLayersMatchPairedXflOrderingStateAndParents() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeDocument("cs4", "0008_nested_layers"));
        Element actual = xml(result, "DOMDocument.xml");
        Element expected = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0008_nested_layers/DOMDocument.xml")));
        NodeList a = actual.getElementsByTagNameNS(NS, "DOMLayer"), b = expected.getElementsByTagNameNS(NS, "DOMLayer");
        assertEquals(a.getLength(), b.getLength());
        for (int i = 0; i < a.getLength(); i++) {
            Element x = (Element) a.item(i), y = (Element) b.item(i);
            for (String key : Arrays.asList("name", "color", "parentLayerIndex", "layerType")) {
                assertEquals(x.getAttribute(key), y.getAttribute(key), "layer " + i + " " + key);
            }
            for (String key : Arrays.asList("open", "autoNamed")) {
                assertEquals(x.getAttribute(key), y.hasAttribute(key) ? y.getAttribute(key) : "true", "layer " + i + " " + key);
            }
            assertEquals(x.getAttribute("locked"), y.hasAttribute("locked") ? y.getAttribute("locked") : "false");
        }
        assertFalse(has(result, "LAYER_PARENT"));
    }

    @Test
    public void nativeFoldersAndSymbolsMatchPairedXflLogicalNames() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeDocument("cs4", "0028_library_folders"));
        Element actual = xml(result, "DOMDocument.xml");
        Element expected = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0028_library_folders/DOMDocument.xml")));
        assertEquals(names(actual, "DOMFolderItem"), names(expected, "DOMFolderItem"));
        List<String> exported = new ArrayList<>(), originals = new ArrayList<>();
        for (String path : result.getFiles().keySet()) {
            if (path.startsWith("LIBRARY/") && path.endsWith(".xml")) {
                exported.add(xml(result, path).getAttribute("name"));
            }
        }
        NodeList includes = expected.getElementsByTagNameNS(NS, "Include");
        for (int i = 0; i < includes.getLength(); i++) {
            String path = ((Element) includes.item(i)).getAttribute("href");
            originals.add(parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0028_library_folders/LIBRARY").resolve(path))).getAttribute("name"));
        }
        Collections.sort(exported);
        Collections.sort(originals);
        assertEquals(exported, originals);
        assertFalse(has(result, "MISSING_FOLDER"));
    }

    @Test
    public void nativeSymbolDefinitionsAndPlacementTypesMatchPairedXflIndependently() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeDocument("cs4", "0009_symbols"));
        Element expected = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0009_symbols/DOMDocument.xml")));
        NodeList a = xml(result, "DOMDocument.xml").getElementsByTagNameNS(NS, "DOMSymbolInstance");
        NodeList b = expected.getElementsByTagNameNS(NS, "DOMSymbolInstance");
        assertEquals(a.getLength(), b.getLength());
        for (int i = 0; i < a.getLength(); i++) {
            Element x = (Element) a.item(i), y = (Element) b.item(i);
            assertEquals(x.getAttribute("libraryItemName"), y.getAttribute("libraryItemName"));
            assertEquals(x.getAttribute("symbolType"), y.hasAttribute("symbolType") ? y.getAttribute("symbolType") : "movie clip");
            assertEquals(x.getAttribute("locked"), y.hasAttribute("locked") ? y.getAttribute("locked") : "false");
            if (x.getAttribute("symbolType").equals("graphic")) {
                assertEquals(x.getAttribute("loop"), y.hasAttribute("loop") ? y.getAttribute("loop") : "loop");
                assertEquals(x.getAttribute("firstFrame"), y.hasAttribute("firstFrame") ? y.getAttribute("firstFrame") : "0");
            }
        }
        Map<String, String> types = new HashMap<>();
        for (String path : result.getFiles().keySet()) {
            if (path.startsWith("LIBRARY/") && path.endsWith(".xml")) {
                Element s = xml(result, path);
                types.put(s.getAttribute("name"), s.getAttribute("symbolType"));
            }
        }
        assertEquals(types.get("MyButton 1"), "button");
        assertEquals(types.get("MyGraphic 1"), "graphic");
        assertEquals(types.get("MySymbol 1"), "movie clip");
        assertFalse(has(result, "MISSING_SYMBOL"));
        assertFalse(has(result, "INSTANCE_EFFECTS"));
    }

    @Test
    public void nativeScaleGridUsesRightLeftBottomTopWireOrder() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeDocument("cs4", "0009_symbols"));
        Element grid = null;
        for (String path : result.getFiles().keySet()) {
            if (path.startsWith("LIBRARY/") && path.endsWith(".xml") && xml(result, path).getAttribute("name").equals("MySymbol 2")) {
                grid = xml(result, path);
            }
        }
        assertNotNull(grid);
        assertEquals(grid.getAttribute("scaleGridLeft"), "10");
        assertEquals(grid.getAttribute("scaleGridRight"), "70");
        assertEquals(grid.getAttribute("scaleGridTop"), "10");
        assertEquals(grid.getAttribute("scaleGridBottom"), "70");
        assertFalse(has(result, "SCALE_GRID"));
    }

    @Test
    public void nonIdentity3DTransformsAreExported() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeDocument("cs4", "0026_3d_rotations-cs4"));
        assertFalse(has(result, "INSTANCE_EFFECTS"));
        NodeList instances = xml(result, "DOMDocument.xml").getElementsByTagNameNS(NS, "DOMSymbolInstance");
        assertTrue(instances.getLength() > 0);
        for (int i = 0; i < instances.getLength(); i++) {
            assertEquals(((Element) instances.item(i)).getAttribute("matrix3D").split(" ").length, 16);
        }
    }

    @Test
    public void schemaRejectsUnknownAttributesAndWrongChildOrder() throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(convert(stage(SHAPE), FlaFormatVersion.CS4));
        String valid = new String(result.getFiles().get("DOMDocument.xml").toByteArray(), StandardCharsets.UTF_8);
        for (String invalid : Arrays.asList(valid.replaceFirst("<DOMShape(?=[ >])", "<DOMShape inventedAttribute='true'"),
                valid.replaceFirst("<PrinterSettings\\b[^>]*/>", "<unknownElement/>"))) {
            assertNotEquals(invalid, valid, "Negative schema fixture must change the generated XML");
            try {
                XflSchemaTestSupport.validate(invalid.getBytes(StandardCharsets.UTF_8));
                fail("Schema accepted invalid XML");
            } catch (org.xml.sax.SAXException expected) {
            }
        }
        String wrongOrder = valid.replace("<publishHistory/>", "").replaceFirst("<PrinterSettings\\b[^>]*/>", "<publishHistory/>$0");
        assertNotEquals(wrongOrder, valid, "Negative child-order fixture must change the generated XML");
        try {
            XflSchemaTestSupport.validate(wrongOrder.getBytes(StandardCharsets.UTF_8));
            fail("Schema accepted wrong child order");
        } catch (org.xml.sax.SAXException expected) {
        }
    }

    @Test
    public void schemaAttributesAreDemonstratedByUnmodifiedAdobeFixtures() throws Exception {
        // Keep actual Adobe reference files as evidence for these declarations.
        Element groups = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0027_groups/DOMDocument.xml")));
        boolean selected = false;
        NodeList nodes = groups.getElementsByTagNameNS(NS, "DOMGroup");
        for (int i = 0; i < nodes.getLength(); i++) {
            selected |= ((Element) nodes.item(i)).getAttribute("selected").equals("true");
        }
        assertTrue(selected);
        Element symbols = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0009_symbols/DOMDocument.xml")));
        boolean menu = false;
        nodes = symbols.getElementsByTagNameNS(NS, "DOMSymbolInstance");
        for (int i = 0; i < nodes.getLength(); i++) {
            menu |= ((Element) nodes.item(i)).getAttribute("trackAsMenu").equals("true");
        }
        assertTrue(menu);
        Element tweens = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0017_classictween/DOMDocument.xml")));
        boolean scaling = false;
        nodes = tweens.getElementsByTagNameNS(NS, "DOMFrame");
        for (int i = 0; i < nodes.getLength(); i++) {
            scaling |= ((Element) nodes.item(i)).getAttribute("motionTweenScale").equals("false");
        }
        assertTrue(scaling);
    }

    @Test
    public void groupsContainingOnlyMergedGeometryRemainGroups() throws Exception {
        BinaryFlaDocument model = convert(stage("<DOMGroup><members>" + SHAPE + "</members></DOMGroup>"), FlaFormatVersion.CS4);
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        Element root = xml(result, "DOMDocument.xml");
        assertEquals(root.getElementsByTagNameNS(NS, "DOMGroup").getLength(), 1);
        assertEquals(first(root, "members").getElementsByTagNameNS(NS, "DOMShape").getLength(), 1);
        assertEquals(root.getElementsByTagNameNS(NS, "DOMShape").getLength(), 1);
        BinaryFlaDocument roundTrip = convert(result, FlaFormatVersion.CS4);
        FlaRecord frame = roundTrip.getPages().get(0).getRoot().getChildren().get(0).getChildren().get(0);
        assertEquals(frame.getChildren().size(), 1);
        assertEquals(records((FlaRecord) frame.getChildren().get(0).get("shapeData"), "edges").size(), 2);
    }

    @Test
    public void drawingObjectsKeepTheirFloatingFlag() throws Exception {
        BinaryFlaDocument model = convert(stage(SHAPE.replace("<DOMShape>", "<DOMShape isFloating='true'>")), FlaFormatVersion.CS4);
        Element root = xml(new BinaryFlaToXflExporter().export(model), "DOMDocument.xml");
        assertEquals(first(root, "DOMShape").getAttribute("isFloating"), "true");
        assertEquals(root.getElementsByTagNameNS(NS, "DOMGroup").getLength(), 0);
    }

    @Test
    public void nativeMediaTextAndProceduralStrokesAreExported() throws Exception {
        BinaryFlaToXflExporter exporter = new BinaryFlaToXflExporter();
        XflExportResult media = exporter.export(nativeDocument("cs4", "0014_sounds"));
        assertFalse(has(media, "MEDIA_NOT_EXPORTED"));
        assertFalse(has(exporter.export(nativeDocument("cs4", "0012_texts")), "DISPLAY_OBJECT"));
        assertFalse(has(exporter.export(nativeDocument("cs4", "0005_strokes")), "STROKE_STYLE"));
    }

    @Test
    public void invalidLayerParentsDoNotProduceSelfReferences() throws Exception {
        BinaryFlaDocument model = convert(stage(""), FlaFormatVersion.CS4);
        FlaRecord layer = model.getPages().get(0).getRoot().getChildren().get(0);
        layer.set("relatedLayer", layer);
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        assertTrue(has(result, "LAYER_PARENT"));
        assertFalse(first(xml(result, "DOMDocument.xml"), "DOMLayer").hasAttribute("parentLayerIndex"));
    }

    @DataProvider(name = "nativeFiles")
    public Object[][] nativeFiles() {
        List<Object[]> result = new ArrayList<>();
        for (String version : Arrays.asList("f5", "mx", "mx2004", "f8", "cs3", "cs4")) {
            for (String name : Arrays.asList("0001_empty_doc", "0002_shapes", "0003_fills", "0004_cubics", "0006_frames", "0009_symbols", "0021_scenes", "0027_groups")) {
                if (Files.isRegularFile(Paths.get("testdata/fla", version, name + ".fla"))) {
                    result.add(new Object[]{version, name});
                }
            }
        }
        return result.toArray(new Object[0][]);
    }

    @Test(dataProvider = "nativeFiles")
    public void nativeDocumentsEmitValidXmlAndDoNotChangeSourceBytesOrRecords(String version, String name) throws Exception {
        BinaryFlaDocument source = nativeDocument(version, name);
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        Map<FlaRecord, Map<String, Object>> fields = new IdentityHashMap<>();
        for (FlaStream stream : source.getStreams().values()) {
            bytes.put(stream.getPath(), stream.getData().toByteArray());
            for (FlaRecord r : stream.getObjects()) {
                fields.put(r, new LinkedHashMap<>(r.getFields()));
            }
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        for (String path : result.getFiles().keySet()) {
            if (path.endsWith(".xml")) {
                Element output = xml(result, path);
                assertEquals(output.getNamespaceURI(), path.equals("PublishSettings.xml") ? null
                        : path.equals("META-INF/metadata.xml") ? "adobe:ns:meta/" : NS);
                if (path.equals("PublishSettings.xml")) {
                    assertEquals(output.getTagName(), "flash_profiles");
                }
            }
        }
        assertEquals(first(xml(result, "DOMDocument.xml"), "timelines").getElementsByTagNameNS(NS, "DOMTimeline").getLength(), source.getSceneDescriptors().size());
        for (FlaStream stream : source.getStreams().values()) {
            assertEquals(stream.getData().toByteArray(), bytes.get(stream.getPath()));
        }
        for (Map.Entry<FlaRecord, Map<String, Object>> entry : fields.entrySet()) {
            assertEquals(entry.getKey().getFields(), entry.getValue());
        }
    }

    @Test
    public void unsupportedStylesOmitWholeShapeAndReportTheOriginalLocation() throws Exception {
        BinaryFlaDocument model = convert(stage(SHAPE), FlaFormatVersion.CS4);
        FlaRecord paint = records(shapeData(model), "fills").get(0);
        paint.set("flags", 0x20);
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        assertEquals(xml(result, "DOMDocument.xml").getElementsByTagNameNS(NS, "DOMShape").getLength(), 0);
        XflExportIssue issue = result.getIssues().stream().filter(i -> i.getCode().equals("PAINT_STYLE")).findFirst().get();
        assertEquals(issue.getOffset(), paint.getOffset());
        assertEquals(issue.getStreamPath(), model.getPages().get(0).getStream().getPath());
    }

    @Test
    public void historicalPaintsAreNotEmittedWithUnnormalizedAlpha() throws Exception {
        BinaryFlaDocument model = convert(stage(SHAPE), FlaFormatVersion.CS4);
        shapeData(model).set("revision", 3);
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        assertFalse(has(result, "HISTORICAL_SHAPE"));
        Element root = xml(result, "DOMDocument.xml");
        assertEquals(root.getElementsByTagNameNS(NS, "DOMShape").getLength(), 1);
        assertEquals(first(root, "SolidColor").getAttribute("alpha"), "1");
    }

    @Test
    public void invalidStyleReferencesAndCyclicDisplayTreesProduceDiagnostics() throws Exception {
        BinaryFlaDocument model = convert(stage("<DOMGroup><members>" + SHAPE + "</members></DOMGroup>"), FlaFormatVersion.CS4);
        FlaRecord frame = model.getPages().get(0).getRoot().getChildren().get(0).getChildren().get(0);
        FlaRecord group = frame.getChildren().get(0);
        group.set("children", Collections.singletonList(group));
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        assertTrue(has(result, "OBJECT_CYCLE"));
        model = convert(stage(SHAPE), FlaFormatVersion.CS4);
        records(shapeData(model), "edges").get(0).set("fillIndex1", 500);
        result = new BinaryFlaToXflExporter().export(model);
        assertTrue(has(result, "STYLE_INDEX"));
        assertEquals(xml(result, "DOMDocument.xml").getElementsByTagNameNS(NS, "DOMShape").getLength(), 0);
    }

    @Test
    public void invalidXmlCharactersAreReportedAndReplacedWhileScriptsWithCdataTerminatorsSurvive() throws Exception {
        BinaryFlaDocument model = convert(stage(""), FlaFormatVersion.CS4);
        model.getSceneDescriptors().get(0).set("name", "bad\u0000\uD800name");
        FlaRecord frame = model.getPages().get(0).getRoot().getChildren().get(0).getChildren().get(0);
        ((FlaRecord) frame.get("actions")).set("source", "trace(\"]]> & <\");\r\n");
        XflExportResult result = new BinaryFlaToXflExporter().export(model);
        Element d = xml(result, "DOMDocument.xml");
        assertTrue(has(result, "XML_CHARACTER"));
        assertEquals(first(d, "DOMTimeline").getAttribute("name"), "bad\uFFFD\uFFFDname");
        // Native CDATA payloads use XML's normalized line endings.
        assertEquals(first(d, "script").getTextContent(), "trace(\"]]> & <\");\n");
    }

    @Test
    public void missingPageAndMissingSymbolDoNotFabricateDisplayContent() throws Exception {
        BinaryFlaDocument model = convert(stage(""), FlaFormatVersion.CS4);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", model.getContentsStream().getData().toByteArray());
        XflExportResult result = new BinaryFlaToXflExporter().export(new BinaryFlaReader().readStreams(streams));
        assertTrue(has(result, "MISSING_PAGE"));
        assertNotNull(first(xml(result, "DOMDocument.xml"), "DOMTimeline"));
    }

    @Test
    public void outputIsDetachedDeterministicAndRefusesExistingTargets() throws Exception {
        BinaryFlaDocument model = convert(stage(SHAPE), FlaFormatVersion.CS4);
        BinaryFlaToXflExporter exporter = new BinaryFlaToXflExporter();
        XflExportResult first = exporter.export(model), second = exporter.export(model);
        assertEquals(first.getFiles().keySet(), second.getFiles().keySet());
        for (String key : first.getFiles().keySet()) {
            assertEquals(first.getFiles().get(key).toByteArray(), second.getFiles().get(key).toByteArray());
        }
        try {
            first.getFiles().clear();
            fail("Mutable output");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            first.getIssues().clear();
            fail("Mutable diagnostics");
        } catch (UnsupportedOperationException expected) {
        }
        Path parent = Files.createTempDirectory("xfl-export-");
        Path out = parent.resolve("export");
        try {
            first.writeToDirectory(out.toFile());
            assertEquals(new String(Files.readAllBytes(out.resolve("document.xfl")), StandardCharsets.US_ASCII), "PROXY-CS5");
            byte[] before = Files.readAllBytes(out.resolve("DOMDocument.xml"));
            assertTrue(new String(Files.readAllBytes(out.resolve("conversion-report.txt")), StandardCharsets.UTF_8).contains("EXPORT_SCOPE"));
            try {
                first.writeToDirectory(out.toFile());
                fail("Overwrote existing output");
            } catch (FileAlreadyExistsException expected) {
            }
            assertEquals(Files.readAllBytes(out.resolve("DOMDocument.xml")), before);
        } finally {
            try (Stream<Path> paths = Files.walk(parent)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(path);
                }
            }
        }
    }

    private static String stage(String elements) {
        return "<DOMDocument><timelines><DOMTimeline name='Scene'><layers><DOMLayer name='Layer'><frames>"
                + "<DOMFrame index='0'><elements>" + elements + "</elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines></DOMDocument>";
    }

    private static BinaryFlaDocument nativeDocument(String version, String name) throws Exception {
        BinaryFlaDocument model = new BinaryFlaReader().read(Paths.get("testdata/fla", version, name + ".fla").toFile());
        assertTrue(model.getIssues().isEmpty(), model.getIssues().toString());
        return model;
    }

    private static BinaryFlaDocument convert(String xml, FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", xml);
        return convert(input, version);
    }

    private static BinaryFlaDocument convert(XflExportResult result, FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        for (Map.Entry<String, BinaryData> entry : result.getFiles().entrySet()) {
            input.bytes.put(entry.getKey(), entry.getValue().toByteArray());
        }
        return convert(input, version);
    }

    private static BinaryFlaDocument convert(Memory input, FlaFormatVersion version) throws Exception {
        Memory output = new Memory();
        new LegacyFlaConverter(version, "windows-1250").convert(input, output);
        for (Map.Entry<String, ByteArrayOutputStream> e : output.outputs.entrySet()) {
            output.bytes.put(e.getKey(), e.getValue().toByteArray());
        }
        BinaryFlaDocument model = new BinaryFlaReader(java.nio.charset.Charset.forName("windows-1250")).readStreams(output.bytes);
        assertTrue(model.getIssues().isEmpty(), model.getIssues().toString());
        assertEquals(model.getContents().getEndOffset(), model.getContentsStream().getData().size());
        for (BinaryFlaDocument.Page page : model.getPages()) {
            assertEquals(page.getStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE);
        }
        return model;
    }

    private static Element xml(XflExportResult result, String path) throws Exception {
        byte[] bytes = result.getFiles().get(path).toByteArray();
        XflSchemaTestSupport.validate(bytes);
        return parse(bytes);
    }

    private static Element parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
    }

    private static Element first(Element root, String name) {
        return (Element) root.getElementsByTagNameNS(NS, name).item(0);
    }

    private static boolean has(XflExportResult result, String code) {
        return result.getIssues().stream().anyMatch(i -> i.getCode().equals(code));
    }

    @SuppressWarnings("unchecked")
    private static List<FlaRecord> records(FlaRecord r, String key) {
        return (List<FlaRecord>) r.get(key);
    }

    private static FlaRecord shapeData(BinaryFlaDocument model) {
        return (FlaRecord) model.getPages().get(0).getRoot().getChildren().get(0).getChildren().get(0).get("shapeData");
    }

    private static List<String> names(Element root, String tag) {
        List<String> result = new ArrayList<>();
        NodeList list = root.getElementsByTagNameNS(NS, tag);
        for (int i = 0; i < list.getLength(); i++) {
            result.add(((Element) list.item(i)).getAttribute("name"));
        }
        Collections.sort(result);
        return result;
    }

    @Test(dataProvider = "versions")
    public void objectSelectionAndLockAreWrittenInNativeDiskOrder(FlaFormatVersion version) throws Exception {
        for (String attribute : new String[]{"selected", "locked"}) {
            int expected = attribute.equals("selected") ? 2 : 4;
            BinaryFlaDocument model = convert(stage("<DOMGroup " + attribute + "='true'><members>" + SHAPE + "</members></DOMGroup>"), version);
            FlaRecord group = model.getPages().get(0).getRoot().getChildren().get(0).getChildren().get(0).getChildren().get(0);
            assertEquals(((Number) group.get("flags")).intValue(), expected);
            Element out = xml(new BinaryFlaToXflExporter().export(model), "DOMDocument.xml");
            assertEquals(first(out, "DOMGroup").getAttribute("selected"), Boolean.toString(expected == 2));
            if (expected == 4) {
                assertTrue(has(new BinaryFlaToXflExporter().export(model), "OBJECT_LOCK"));
            }
        }
    }

    private static final class Memory implements InputStorageInterface, OutputStorageInterface {

        final Map<String, byte[]> bytes = new LinkedHashMap<>();
        final Map<String, ByteArrayOutputStream> outputs = new LinkedHashMap<>();

        void put(String path, String value) {
            bytes.put(path, value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean fileExists(String file) {
            return bytes.containsKey(file);
        }

        @Override
        public InputStream readFile(String file) throws IOException {
            if (!bytes.containsKey(file)) {
                return null;
            }
            return new ByteArrayInputStream(bytes.get(file));
        }

        @Override
        public OutputStream getOutputStream(String file) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            outputs.put(file, out);
            return out;
        }

        @Override
        public void storeData(String file, byte[] data) {
            bytes.put(file, data.clone());
        }

        @Override
        public void storeFile(String name, File file) throws IOException {
            bytes.put(name, Files.readAllBytes(file.toPath()));
        }

        @Override
        public void close() {
        }
    }
}
