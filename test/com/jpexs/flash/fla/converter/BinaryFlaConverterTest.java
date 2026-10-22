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
package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.script.FlaActionSource;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.testng.annotations.*;
import static org.testng.Assert.*;

/**
 * Tests direct COMDOC conversion independently of the XML converter/exporter.
 */
public class BinaryFlaConverterTest {

    private static final Charset ANSI = Charset.forName("windows-1252");

    @Test
    public void flash5DescriptorsUseOriginalLinkageLayout() throws Exception {
        BinaryFlaDocument source = load(Paths.get("testdata/fla/cs4/0009_symbols"));
        List<FlaRecord> descriptors = records(source, "CDocumentPage");
        assertTrue(descriptors.size() > 1, "Exercise both scene and symbol descriptors");
        for (FlaRecord descriptor : descriptors) {
            FlaRecord linkage = (FlaRecord) descriptor.get("linkage");
            assertNotNull(linkage);
            linkage.set("revision", 7);
            linkage.set("flags", 1L);
            linkage.set("identifier", "ExportedSymbol");
            linkage.set("url", "shared.swf");
            linkage.set("exportFlags", 1);
        }
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.F5).convert(source));
        List<FlaRecord> actual = records(output, "CDocumentPage");
        assertEquals(actual.size(), descriptors.size());
        for (FlaRecord descriptor : actual) {
            FlaRecord linkage = (FlaRecord) descriptor.get("linkage");
            assertEquals(linkage.get("revision"), 0);
            assertEquals(linkage.get("flags"), 1L);
            assertEquals(linkage.get("identifier"), "ExportedSymbol");
            assertEquals(linkage.get("url"), "shared.swf");
            assertNull(linkage.get("exportFlags"));
            assertNull(linkage.get("auxiliary"));
            // Flash 5 consumes only a revision byte, flags and two ANSI strings.
            assertEquals(linkage.getEndOffset() - linkage.getOffset(),
                    1 + 4 + 1 + "ExportedSymbol".length() + 1 + "shared.swf".length());
        }
        assertEquals(texts(output), texts(source));
        assertEquals(edges(output), edges(source));
        for (FlaRecord descriptor : descriptors) {
            assertEquals(((FlaRecord) descriptor.get("linkage")).get("revision"), 7);
        }
    }

    @Test
    public void legacyLayerStatesPreserveVisibleTextAndLayerSelection() throws Exception {
        BinaryFlaDocument source = load(Paths.get("testdata/fla/cs4/0012_texts"));
        FlaRecord original = records(source, "CPicLayer").get(0);
        original.set("stateFlags", new BinaryData(new byte[]{0, 0, 0}));
        List<FlaRecord> layers = new ArrayList<>();
        byte[][] states = {{0, 1, 0}, {0, 0, 1}, {0, 0, 0}, {1, 0, 0}};
        for (int i = 0; i < 4; i++) {
            FlaRecord layer = testLayer("State " + i, 0, 1, null);
            layer.set("stateFlags", new BinaryData(states[i]));
            layers.add(layer);
        }
        layers.add(original);
        source.getPages().get(0).getRoot().set("children", layers);
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F1, FlaFormatVersion.F2, FlaFormatVersion.F3, FlaFormatVersion.F4, FlaFormatVersion.CS4)) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(source));
            List<FlaRecord> actual = output.getPages().get(0).getRoot().getChildren();
            for (int i = 0; i < 4; i++) {
                if (target.ordinal() <= FlaFormatVersion.F3.ordinal()) {
                    assertEquals(actual.get(i).get("legacyState"), i);
                } else {
                    assertEquals(((BinaryData) actual.get(i).get("stateFlags")).toByteArray(), states[i]);
                }
                assertEquals(((BinaryData) layers.get(i).get("stateFlags")).toByteArray(), states[i]);
            }
            assertEquals(texts(output), texts(source));
            if (target.ordinal() <= FlaFormatVersion.F3.ordinal()) {
                assertEquals(actual.get(4).get("legacyState"), 2, "The unselected text layer must remain visible");
            }
        }
    }

    @Test
    public void legacyOnlyLayerStatesExpandToModernFlags() throws Exception {
        BinaryFlaDocument source = empty();
        List<FlaRecord> layers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            FlaRecord layer = testLayer("Legacy " + i, 0, 1, null);
            layer.set("legacyState", i);
            layers.add(layer);
        }
        source.getPages().get(0).getRoot().set("children", layers);
        byte[][] expected = {{0, 1, 0}, {0, 0, 1}, {0, 0, 0}, {1, 0, 0}};
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(source));
        for (int i = 0; i < 4; i++) {
            assertEquals(((BinaryData) output.getPages().get(0).getRoot().getChildren().get(i).get("stateFlags")).toByteArray(), expected[i]);
            assertEquals(layers.get(i).get("legacyState"), i);
            assertNull(layers.get(i).get("stateFlags"));
        }
    }

    @Test
    public void futureSplashBitmapFillsBecomeSolidWithoutChangingFlash2Fills() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord shape = (FlaRecord) records(source, "CPicFrame").get(0).get("shapeData");
        List<FlaRecord> fills = new ArrayList<>();
        for (int flags : new int[]{64, 65, 66, 67}) {
            FlaRecord fill = FlaRecord.create("fills");
            fill.set("packedValue", 0x00030201L);
            fill.set("flags", flags);
            fill.set("secondaryFlags", 0);
            fill.set("bitmapMediaId", 1);
            fill.set("matrixWords", Arrays.asList(65536, 0, 0, 65536, 200, 400));
            fills.add(fill);
        }
        fills.add(testGradient(16));
        shape.set("fills", fills);
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F1, FlaFormatVersion.F2, FlaFormatVersion.CS4)) {
            BinaryFlaConversionResult converted = new BinaryFlaConverter(target).convert(source);
            BinaryFlaDocument output = read(converted);
            List<FlaRecord> actual = (List<FlaRecord>) ((FlaRecord) records(output, "CPicFrame").get(0).get("shapeData")).get("fills");
            assertEquals(actual.size(), fills.size());
            for (int i = 0; i < 4; i++) {
                assertEquals(actual.get(i).get("flags"), target == FlaFormatVersion.F1 ? 0 : 64 + i);
                assertEquals(actual.get(i).get("packedValue"), target == FlaFormatVersion.F1 ? 0xFF030201L : 0x00030201L);
                if (target == FlaFormatVersion.F1) {
                    assertNull(actual.get(i).get("bitmapMediaId"));
                    assertNull(actual.get(i).get("matrixWords"));
                } else {
                    assertEquals(actual.get(i).get("bitmapMediaId"), 1);
                    assertEquals(actual.get(i).get("matrixWords"), fills.get(i).get("matrixWords"));
                }
                assertEquals(fills.get(i).get("flags"), 64 + i);
                assertEquals(fills.get(i).get("packedValue"), 0x00030201L);
            }
            assertEquals(actual.get(4).get("flags"), 16);
            assertEquals(actual.get(4).get("matrixWords"), fills.get(4).get("matrixWords"));
            assertEquals(((List<?>) actual.get(4).get("stops")).size(), 3);
            assertEquals(has(converted, "BITMAP_FILL"), target == FlaFormatVersion.F1);
        }
    }

    @Test
    public void futureSplashBitmapsUseRawRevisionZeroAndPreservePixelData() throws Exception {
        for (int revision : new int[]{0, 1, 2, 3}) {
            BinaryFlaDocument source = empty();
            FlaRecord raster = FlaRecord.create("raster");
            raster.set("revision", revision);
            raster.set("formatCode", 5);
            raster.set("rowStride", 12);
            raster.set("width", 2);
            raster.set("height", 2);
            raster.set("boundsWords", Arrays.asList(0, 40, 0, 40));
            raster.set("hasAlpha", 0);
            byte[] pixels = new byte[]{-1, 10, 20, 30, -1, 40, 50, 60, 0, 0, 0, 0,
                -1, 70, 80, 90, -1, 100, 110, 120, 0, 0, 0, 0};
            if (revision > 0) {
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                try (OutputStream out = new java.util.zip.DeflaterOutputStream(compressed)) {
                    out.write(pixels);
                }
                FlaRecord chunk = FlaRecord.create("chunks");
                chunk.set("data", new BinaryData(compressed.toByteArray()));
                raster.set("chunks", Collections.singletonList(chunk));
            } else {
                raster.set("pixelData", new BinaryData(pixels));
            }
            FlaRecord bitmap = FlaRecord.create("CPicBitmap");
            bitmap.set("raster", raster);
            records(source, "CPicFrame").get(0).set("children", Collections.singletonList(bitmap));
            BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.F1).convert(source));
            FlaRecord actual = (FlaRecord) records(output, "CPicBitmap").get(0).get("raster");
            assertEquals(actual.get("revision"), 0);
            assertEquals(actual.get("formatCode"), 5);
            assertEquals(actual.get("rowStride"), 12);
            assertEquals(actual.get("width"), 2);
            assertEquals(actual.get("height"), 2);
            assertEquals(actual.get("boundsWords"), raster.get("boundsWords"));
            assertEquals(((BinaryData) actual.get("pixelData")).toByteArray(), pixels);
            assertEquals(actual.get("isCompressed"), false);
            assertNull(actual.get("chunks"));
            assertEquals(raster.get("revision"), revision);
            assertEquals(rasterPixels(raster), pixels);
        }
        BinaryFlaDocument source = load(Paths.get("testdata/fla/cs4/0013_bitmaps"));
        BinaryFlaConversionResult converted = new BinaryFlaConverter(FlaFormatVersion.F1).convert(source);
        BinaryFlaDocument output = read(converted);
        assertEquals(records(output, "CPicBitmap").size(), records(source, "CPicBitmap").size());
        for (FlaRecord raster : records(output, "raster")) {
            assertEquals(raster.get("revision"), 0);
            assertEquals(((BinaryData) raster.get("pixelData")).size(),
                    ((Number) raster.get("rowStride")).intValue() * ((Number) raster.get("height")).intValue());
        }
        assertTrue(has(converted, "BITMAP_ALPHA"));
    }

    @Test
    public void flash1And2MovieClipsBecomeGraphicSymbolsWithMatchingLibraryItems() throws Exception {
        BinaryFlaDocument source = load(Paths.get("testdata/fla/cs4/0009_symbols"));
        List<FlaRecord> sprites = records(source, "CPicSprite");
        assertFalse(sprites.isEmpty());
        sprites.get(0).set("instanceName", "clipName");
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F1, FlaFormatVersion.F2, FlaFormatVersion.F3, FlaFormatVersion.CS4)) {
            BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(source);
            BinaryFlaDocument output = read(result);
            boolean legacy = target.ordinal() <= FlaFormatVersion.F2.ordinal();
            assertEquals(records(output, "CPicSprite").size(), legacy ? 0 : sprites.size());
            assertEquals(records(output, "CPicSymbol").size(), records(source, "CPicSymbol").size() + (legacy ? sprites.size() : 0));
            assertEquals(records(output, "CPicButton").size(), records(source, "CPicButton").size());
            assertEquals(has(result, "MOVIE_CLIP"), legacy);
            for (int i = 0; i < source.getPages().size(); i++) {
                FlaRecord before = source.getPages().get(i).getDescriptor(), after = output.getPages().get(i).getDescriptor();
                int type = ((Number) before.get("symbolType")).intValue();
                assertEquals(((Number) after.get("symbolType")).intValue(), legacy && type == 2 ? 0 : type);
                assertEquals(after.get("libraryId"), before.get("libraryId"));
                assertEquals(after.get("streamName"), before.get("streamName"));
                List<FlaRecord> objects = output.getPages().get(i).getStream().getObjects();
                for (FlaRecord sprite : source.getPages().get(i).getStream().getObjects()) {
                    if (!sprite.getClassName().equals("CPicSprite")) {
                        continue;
                    }
                    FlaRecord actual = objects.stream().filter(r -> r.getClassName().equals(legacy ? "CPicSymbol" : "CPicSprite")
                            && Objects.equals(r.get("libraryId"), sprite.get("libraryId"))
                            && Objects.equals(r.get("matrixWords"), sprite.get("matrixWords"))).findFirst().get();
                    assertEquals(actual.get("firstFrame"), sprite.get("firstFrame"));
                    assertEquals(actual.get("loopMode"), legacy ? 0 : sprite.get("loopMode"));
                    if (legacy) {
                        assertNull(actual.get("spriteRevision"));
                        assertNull(actual.get("instanceName"));
                    } else {
                        assertEquals(actual.get("instanceName"), sprite.get("instanceName"));
                    }
                }
            }
        }
        assertEquals(sprites.get(0).get("instanceName"), "clipName");
        assertEquals(sprites.get(0).getClassName(), "CPicSprite");
    }

    @Test
    public void unsupportedStrokePaintBecomesVisibleSolidWithoutChangingOrdinaryAlpha() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord shape = (FlaRecord) records(source, "CPicFrame").get(0).get("shapeData");
        FlaRecord linear = testPaintedStroke(0L, testGradient(16));
        FlaRecord radial = testPaintedStroke(0L, testGradient(18));
        FlaRecord bitmapPaint = FlaRecord.create("paint");
        bitmapPaint.set("flags", 64);
        FlaRecord bitmap = testPaintedStroke(0x00030201L, bitmapPaint);
        FlaRecord transparent = testPaintedStroke(0L, null);
        FlaRecord translucent = testPaintedStroke(0x7F0000FFL, null);
        shape.set("strokes", Arrays.asList(linear, radial, bitmap, transparent, translucent));
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F3, FlaFormatVersion.F4, FlaFormatVersion.MX2004, FlaFormatVersion.F8, FlaFormatVersion.CS4)) {
            BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(source);
            BinaryFlaDocument output = read(result);
            FlaRecord actualShape = (FlaRecord) records(output, "CPicFrame").get(0).get("shapeData");
            List<FlaRecord> strokes = (List<FlaRecord>) actualShape.get("strokes");
            boolean supported = target.ordinal() >= FlaFormatVersion.F8.ordinal();
            for (int i = 0; i < strokes.size(); i++) {
                assertEquals(strokes.get(i).get("width"), 400);
                assertEquals(strokes.get(i).get("styleWord1"), 0);
                assertEquals(strokes.get(i).get("styleWord2"), 0);
            }
            assertEquals(strokes.get(0).get("colorRgba"), supported ? 0L : 0xFF000000L);
            assertEquals(strokes.get(1).get("colorRgba"), supported ? 0L : 0xFF000000L);
            assertEquals(strokes.get(2).get("colorRgba"), supported ? 0x00030201L : 0xFF030201L);
            assertEquals(strokes.get(3).get("colorRgba"), 0L);
            assertEquals(strokes.get(4).get("colorRgba"), 0x7F0000FFL);
            assertEquals(has(result, "STROKE_PAINT"), !supported);
            if (supported) {
                assertEquals(((FlaRecord) strokes.get(0).get("paint")).get("flags"), 16);
            } else {
                assertNull(strokes.get(0).get("paint"));
            }
        }
        assertEquals(linear.get("colorRgba"), 0L);
        assertEquals(radial.get("colorRgba"), 0L);
        assertEquals(bitmap.get("colorRgba"), 0x00030201L);
    }

    private static FlaRecord testPaintedStroke(long color, FlaRecord paint) {
        FlaRecord r = FlaRecord.create("strokes");
        r.set("colorRgba", color);
        r.set("width", 400);
        r.set("styleWord1", 0);
        r.set("styleWord2", 0);
        r.set("paint", paint);
        return r;
    }

    @Test
    public void flash4GradientHeadersAreOpaqueWithoutChangingStopTransparency() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord frame = records(source, "CPicFrame").get(0);
        FlaRecord shape = (FlaRecord) frame.get("shapeData");
        FlaRecord linear = testGradient(16), radial = testGradient(18);
        FlaRecord solid = FlaRecord.create("fills");
        solid.set("packedValue", 0x000000FFL);
        solid.set("flags", 0);
        solid.set("secondaryFlags", 0);
        shape.set("fills", Arrays.asList(linear, radial, solid));
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F4, FlaFormatVersion.F5, FlaFormatVersion.CS4)) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(source));
            FlaRecord actualShape = (FlaRecord) records(output, "CPicFrame").get(0).get("shapeData");
            List<FlaRecord> fills = (List<FlaRecord>) actualShape.get("fills");
            for (int i = 0; i < 2; i++) {
                FlaRecord actual = fills.get(i), expected = i == 0 ? linear : radial;
                assertEquals(actual.get("packedValue"), target == FlaFormatVersion.F4 ? 0xFF000000L : 0L);
                assertEquals(actual.get("flags"), expected.get("flags"));
                assertEquals(actual.get("matrixWords"), expected.get("matrixWords"));
                List<FlaRecord> a = (List<FlaRecord>) actual.get("stops"), b = (List<FlaRecord>) expected.get("stops");
                assertEquals(a.size(), b.size());
                for (int j = 0; j < a.size(); j++) {
                    assertEquals(a.get(j).get("ratio"), b.get(j).get("ratio"));
                    assertEquals(a.get(j).get("colorRgba"), b.get(j).get("colorRgba"));
                }
            }
            assertEquals(fills.get(2).get("packedValue"), 0x000000FFL);
        }
        assertEquals(linear.get("packedValue"), 0L);
        assertEquals(radial.get("packedValue"), 0L);
    }

    private static FlaRecord testGradient(int flags) {
        FlaRecord r = FlaRecord.create("fills");
        r.set("packedValue", 0L);
        r.set("flags", flags);
        r.set("secondaryFlags", 0);
        r.set("matrixWords", Arrays.asList(8000, 0, 0, 4000, 3000, 2000));
        List<FlaRecord> stops = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            FlaRecord stop = FlaRecord.create("stops");
            stop.set("ratio", i == 2 ? 255 : i * 127);
            stop.set("colorRgba", i == 0 ? 0x000000FFL : i == 1 ? 0x7F00FFFFL : 0xFF000000L);
            stops.add(stop);
        }
        r.set("stops", stops);
        return r;
    }

    @Test
    public void legacyMasksAreIndependentOfOpenStateAndKeepTheirMaskedLayers() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord mask = testLayer("Mask", 4, 0, null);
        FlaRecord masked = testLayer("Masked", 0, 1, mask);
        FlaRecord normal = testLayer("Normal", 0, 1, null);
        FlaRecord guide = testLayer("Guide", 1, 1, null);
        FlaRecord guided = testLayer("Guided", 0, 1, guide);
        FlaRecord folder = testLayer("Folder", 3, 1, mask);
        FlaRecord nested = testLayer("Nested", 0, 1, folder);
        source.getPages().get(0).getRoot().set("children", Arrays.asList(nested, folder, masked, mask, guided, guide, normal));
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F4, FlaFormatVersion.F5, FlaFormatVersion.MX, FlaFormatVersion.CS4)) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(source));
            Map<String, FlaRecord> layers = new HashMap<>();
            for (FlaRecord r : output.getPages().get(0).getRoot().getChildren()) {
                layers.put((String) r.get("name"), r);
            }
            boolean legacy = target.ordinal() <= FlaFormatVersion.F5.ordinal();
            assertEquals(layers.get("Mask").getLayerType(), 4);
            assertNull(layers.get("Mask").getLayerParent());
            assertEquals(layers.get("Normal").getLayerType(), 0);
            assertEquals(layers.get("Masked").getLayerType(), 0);
            assertSame(layers.get("Masked").getLayerParent(), layers.get("Mask"));
            assertSame(layers.get("Guided").getLayerParent(), layers.get("Guide"));
            assertEquals(layers.get("Guided").getLayerType(), 0);
            assertSame(layers.get("Nested").getLayerParent(), layers.get(legacy ? "Mask" : "Folder"));
            if (legacy) {
                assertEquals(((Number) layers.get("Mask").get("legacyMask")).intValue(), 1);
                assertEquals(((Number) layers.get("Normal").get("legacyMask")).intValue(), 0);
                assertEquals(((Number) layers.get("Masked").get("legacyMask")).intValue(), 0);
                assertNull(layers.get("Masked").get("relatedLayer"));
                assertSame(layers.get("Masked").get("legacyRelatedLayer"), layers.get("Mask"));
                assertEquals(((Number) layers.get("Guided").get("type")).intValue(), 2);
                assertSame(layers.get("Guided").get("relatedLayer"), layers.get("Guide"));
                assertNull(layers.get("Normal").get("open"));
                BinaryFlaDocument upgraded = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(output));
                FlaRecord upgradedMasked = upgraded.getPages().get(0).getRoot().getChildren().stream()
                        .filter(r -> "Masked".equals(r.get("name"))).findFirst().get();
                assertEquals(upgradedMasked.getLayerParent().getLayerType(), 4);
            } else {
                assertEquals(((Number) layers.get("Normal").get("open")).intValue(), 1);
                assertEquals(((Number) layers.get("Mask").get("open")).intValue(), 0);
            }
        }
        assertEquals(mask.get("open"), 0);
        assertSame(masked.get("relatedLayer"), mask);
        assertSame(nested.get("relatedLayer"), folder);
    }

    private static FlaRecord testLayer(String name, int type, int open, FlaRecord parent) {
        FlaRecord r = FlaRecord.create("CPicLayer");
        r.set("revision", 13);
        r.set("name", name);
        r.set("type", type);
        r.set("open", open);
        r.set("relatedLayer", parent);
        return r;
    }

    @Test
    public void nativeLegacyMaskReferencesAreUsedByXflExport() throws Exception {
        BinaryFlaDocument source = load(Paths.get("testdata/fla/f5/0008_nested_layers"));
        FlaRecord masked = records(source, "CPicLayer").stream()
                .filter(r -> "Layer 18".equals(r.get("name"))).findFirst().get();
        assertNull(masked.get("relatedLayer"));
        assertSame(masked.getLayerParent(), masked.get("legacyRelatedLayer"));
        assertEquals(masked.getLayerParent().getLayerType(), 4);
        assertNull(masked.getLayerParent().get("open"));
        byte[] xml = new com.jpexs.flash.fla.exporter.BinaryFlaToXflExporter().export(source)
                .getFiles().get("DOMDocument.xml").toByteArray();
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        org.w3c.dom.NodeList layers = document.getElementsByTagName("DOMLayer");
        org.w3c.dom.Element child = null;
        for (int i = 0; i < layers.getLength(); i++) {
            org.w3c.dom.Element layer = (org.w3c.dom.Element) layers.item(i);
            if ("Layer 18".equals(layer.getAttribute("name"))) {
                child = layer;
                break;
            }
        }
        assertNotNull(child);
        org.w3c.dom.Element parent = (org.w3c.dom.Element) layers.item(Integer.parseInt(child.getAttribute("parentLayerIndex")));
        assertEquals(parent.getAttribute("name"), "Layer 29");
        assertEquals(parent.getAttribute("layerType"), "mask");
        assertFalse(parent.hasAttribute("open"));
    }

    @Test
    public void preCs4PalettesUseFlatWireLayoutAndKeepNestedColors() throws Exception {
        BinaryFlaDocument source = load(Paths.get("testdata/fla/cs4/0001_empty_doc"));
        FlaRecord palette = (FlaRecord) source.getContents().get("swatches");
        List<FlaRecord> original = new ArrayList<>((List<FlaRecord>) palette.get("colors"));
        FlaRecord child = FlaRecord.create("CColorList");
        child.set("revision", 1);
        child.set("colors", Collections.singletonList(original.get(1)));
        palette.set("colors", Collections.singletonList(original.get(0)));
        palette.set("lists", Collections.singletonList(child));
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F8, FlaFormatVersion.CS3, FlaFormatVersion.CS4)) {
            BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(source);
            BinaryFlaDocument output = read(result);
            FlaRecord actual = (FlaRecord) output.getContents().get("swatches");
            assertEquals(((Number) actual.get("revision")).intValue(), target == FlaFormatVersion.CS4 ? 1 : 0);
            List<FlaRecord> colors = (List<FlaRecord>) actual.get("colors");
            assertEquals(colors.size(), target == FlaFormatVersion.CS4 ? 1 : 2);
            assertEquals(colors.get(0).get("packedValue"), original.get(0).get("packedValue"));
            if (target == FlaFormatVersion.CS4) {
                assertEquals(((List<?>) actual.get("lists")).size(), 1);
                assertFalse(has(result, "SWATCH_GROUPS"));
            } else {
                assertNull(actual.get("lists"));
                assertEquals(colors.get(1).get("packedValue"), original.get(1).get("packedValue"));
                assertTrue(has(result, "SWATCH_GROUPS"));
                assertNotNull(output.getContents().get("languageSettings"));
                assertEquals(output.getContents().get("timelineHeight"), source.getContents().get("timelineHeight"));
            }
        }
        assertEquals(((Number) palette.get("revision")).intValue(), 1);
        assertEquals(((List<?>) palette.get("colors")).size(), 1);
        assertEquals(((List<?>) palette.get("lists")).size(), 1);
    }

    @DataProvider(name = "nativeConversions")
    public Object[][] nativeConversions() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        for (FlaFormatVersion version : FlaFormatVersion.values()) {
            Path directory = Paths.get("testdata", version.ordinal() < 4 ? "flang" : "fla", version.name().toLowerCase(Locale.ROOT));
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : (Iterable<Path>) paths.filter(Files::isDirectory).sorted()::iterator) {
                    if (Files.isRegularFile(path.resolve("Contents"))) {
                        for (FlaFormatVersion target : FlaFormatVersion.values()) {
                            cases.add(new Object[]{path, target});
                        }
                    }
                }
            }
        }
        return cases.toArray(new Object[cases.size()][]);
    }

    @Test(dataProvider = "nativeConversions")
    public void nativeDocumentsConvertAcrossAllBinaryProfiles(Path path, FlaFormatVersion target) throws Exception {
        BinaryFlaDocument source = load(path);
        IdentityHashMap<FlaRecord, Map<String, Object>> fields = snapshot(source);
        BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(source);
        BinaryFlaDocument output = read(result);
        assertEquals(output.getContentsRevision(), target.contentsRevision);
        assertTrue(output.getIssues().isEmpty(), output.getIssues().toString());
        assertEquals(output.getWidthTwips(), source.getWidthTwips());
        assertEquals(output.getHeightTwips(), source.getHeightTwips());
        assertEquals(output.getSceneDescriptors().size(), source.getSceneDescriptors().size());
        assertEquals(output.getLibraryDescriptors().size(), source.getLibraryDescriptors().size());
        assertEquals(records(output, "CPicFrame").size(), records(source, "CPicFrame").size());
        assertEquals(output.getFrameRate(), source.getFrameRate());
        for (Map.Entry<FlaRecord, Map<String, Object>> entry : fields.entrySet()) {
            assertEquals(entry.getKey().getFields(), entry.getValue(), "Source model modified");
        }
        if (target.contentsRevision == source.getContentsRevision()) {
            assertEquals(texts(output), texts(source));
            assertEquals(edges(output), edges(source));
            Set<FlaRecord> compared = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
            for (FlaStream stream : source.getStreams().values()) {
                FlaStream other = output.getStream(stream.getPath());
                if (other != null) {
                    assertCommonValues(stream.getRoot(), other.getRoot(), stream.getPath(), compared);
                }
            }
        }
        assertFalse(result.getStreams().containsKey("DOMDocument.xml"));
    }

    @DataProvider(name = "missingPageRoles")
    public Object[][] missingPageRoles() {
        return new Object[][]{{BinaryFlaDocument.Page.Role.SCENE}, {BinaryFlaDocument.Page.Role.LIBRARY}};
    }

    @Test(dataProvider = "missingPageRoles")
    public void missingPageStreamsBecomeLocatedEmptyTimelines(BinaryFlaDocument.Page.Role role) throws Exception {
        BinaryFlaDocument nativeDocument = load(Paths.get("testdata/fla/cs4/0009_symbols"));
        BinaryFlaDocument.Page removed = nativeDocument.getPages().stream().filter(p -> p.getRole() == role).findFirst().get();
        String missingPath = removed.getStream().getPath();
        Map<String, byte[]> streams = new LinkedHashMap<>();
        nativeDocument.getStreams().forEach((path, stream) -> streams.put(path, stream.getData().toByteArray()));
        streams.remove(missingPath);
        BinaryFlaDocument source = new BinaryFlaReader().readStreams(streams);
        BinaryFlaDocument.Page missing = source.getPages().stream()
                .filter(p -> p.getDescriptor() != null && missingPath.equals(p.getDescriptor().get("streamName"))).findFirst().get();
        assertNull(missing.getStream());
        assertFalse(source.getIssues().isEmpty());
        for (FlaFormatVersion target : FlaFormatVersion.values()) {
            BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(source);
            BinaryFlaDocument output = read(result);
            BinaryFlaDocument.Page recovered = output.getPages().stream()
                    .filter(p -> p.getDescriptor() != null && missingPath.equals(p.getDescriptor().get("streamName"))).findFirst().get();
            assertNotNull(recovered.getStream());
            assertEquals(recovered.getRole(), role);
            assertEquals(recovered.getName(), missing.getName());
            assertEquals(recovered.getLibraryId(), missing.getLibraryId());
            FlaRecord root = recovered.getRoot();
            assertEquals(root.getClassName(), "CPicPage");
            assertEquals(root.getChildren().size(), 1);
            FlaRecord frame = root.getChildren().get(0).getChildren().get(0);
            assertEquals(frame.getClassName(), "CPicFrame");
            assertTrue(frame.getChildren().isEmpty());
            assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("PAGE_PLACEHOLDER")
                    && i.getStreamPath().equals(missingPath)), result.getReport());
            assertTrue(has(result, "SOURCE_PARSE"));
        }
        assertNull(missing.getStream());
        assertFalse(source.getStreams().containsKey(missingPath));
    }

    @Test
    public void sharedObjectsAndCircularReferencesUseNewArchiveIds() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord group = FlaRecord.create("CPicObj");
        FlaRecord frame = records(source, "CPicFrame").get(0);
        group.set("children", Collections.singletonList(group));
        frame.set("children", Arrays.asList(group, group));
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(source));
        List<FlaRecord> children = records(output, "CPicFrame").get(0).getChildren();
        assertEquals(children.size(), 2);
        assertSame(children.get(0), children.get(1));
        assertSame(children.get(0).getChildren().get(0), children.get(0));
    }

    @Test
    public void extendedMfcClassAndObjectReferencesDoNotWrap() throws Exception {
        BinaryFlaConverter.Session session = session();
        BinaryArchiveWriter writer = new BinaryArchiveWriter(session, "references");
        FlaRecord last = null;
        for (int i = 0; i < 32770; i++) {
            last = FlaRecord.create("CPicObj");
            writer.object(last);
        }
        writer.object(last);
        MfcArchive archive = new MfcArchive(writer.finish(), "references", new FlaReadOptions(),
                Collections.singletonMap("CPicObj", (a, r) -> {
                    assertEquals(a.readUI8(), 5);
                    a.readUI8();
                    assertNull(a.readObject());
                    a.readI32();
                    a.readI32();
                    a.readUI8();
                    a.readUI8();
                }), 0x47);
        FlaRecord decoded = null;
        for (int i = 0; i < 32770; i++) {
            decoded = archive.readObject();
        }
        assertSame(archive.readObject(), decoded);
        assertEquals(archive.getRemaining(), 0);
    }

    @Test
    public void cstringLengthBoundariesRoundTripUnicodeAndLegacyEncoding() throws Exception {
        for (boolean unicode : new boolean[]{false, true}) {
            for (int length : new int[]{0, 254, 255, 65533, 65534, 65535}) {
                char[] units = new char[length];
                Arrays.fill(units, unicode ? '\u010d' : '\u00e9');
                String value = new String(units);
                BinaryArchiveWriter writer = new BinaryArchiveWriter(session(), "strings");
                writer.string(value, unicode);
                MfcArchive archive = new MfcArchive(writer.finish(), "strings", new FlaReadOptions(), Collections.emptyMap(), 0x47);
                assertEquals(archive.readString(), value);
                assertEquals(archive.getRemaining(), 0);
            }
        }
    }

    @Test
    public void fileOutputIsARealCompoundDocumentAndAllowsInPlaceConversion() throws Exception {
        Path directory = Files.createTempDirectory("binary-fla-conversion-");
        Path file = directory.resolve("document.bin");
        try {
            new BinaryFlaConverter(FlaFormatVersion.CS4).convert(load(Paths.get("testdata/fla/cs4/0013_bitmaps"))).writeToFile(file.toFile());
            assertTrue(BinaryFlaConverter.isBinaryFla(file.toFile()));
            new BinaryFlaConverter(FlaFormatVersion.F1).convert(file.toFile()).writeToFile(file.toFile());
            BinaryFlaDocument output = new BinaryFlaReader().read(file.toFile());
            assertEquals(output.getContentsRevision(), FlaFormatVersion.F1.contentsRevision);
            assertTrue(output.getIssues().isEmpty(), output.getIssues().toString());
            assertFalse(records(output, "CPicBitmap").isEmpty());
            for (FlaRecord bitmap : records(output, "CPicBitmap")) {
                assertNotNull(bitmap.get("raster"));
            }
            try (Stream<Path> files = Files.list(directory)) {
                assertEquals(files.count(), 1L);
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void lossReportIncludesUnsupportedObjectFieldsAndCharsetReplacement() throws Exception {
        BinaryFlaDocument source = empty();
        source.getSceneDescriptors().get(0).set("name", "\u4e2d\u6587");
        FlaRecord unknown = FlaRecord.create("UnknownVendorObject");
        records(source, "CPicFrame").get(0).set("children", Collections.singletonList(unknown));
        source.getContents().set("metadataXml", "<metadata/>");
        BinaryFlaConversionResult result = new BinaryFlaConverter(FlaFormatVersion.F1).convert(source);
        assertTrue(has(result, "OBJECT_OMITTED"), result.getReport());
        assertTrue(has(result, "CHARSET"), result.getReport());
        assertTrue(has(result, "FIELD_OMITTED"), result.getReport());
        assertTrue(records(read(result), "CPicFrame").get(0).getChildren().isEmpty());
    }

    @Test
    public void historicalRevisionOneCanBeWrappedIntoEveryProfile() throws Exception {
        BinaryFlaDocument source = new BinaryFlaReader().readStreams(Collections.singletonMap("Contents", new byte[]{1, 0, 0}));
        for (FlaFormatVersion target : FlaFormatVersion.values()) {
            BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(source);
            BinaryFlaDocument output = read(result);
            assertEquals(output.getSceneDescriptors().size(), 1);
            assertEquals(output.getWidthTwips().longValue(), 11000L);
            assertEquals(records(output, "CPicFrame").size(), 1);
            assertTrue(has(result, "HISTORICAL_DOCUMENT"));
        }
    }

    @Test
    public void unsupportedInternalObjectsAreReportedForEveryBinaryTarget() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord tlf = FlaRecord.create("CPicTLFText");
        tlf.set("source", "<TextFlow/>");
        records(source, "CPicFrame").get(0).set("children", Collections.singletonList(tlf));
        for (FlaFormatVersion target : FlaFormatVersion.values()) {
            BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(source);
            assertTrue(records(read(result), "CPicTLFText").isEmpty());
            assertTrue(has(result, "OBJECT_OMITTED"), result.getReport());
        }
    }

    @Test
    public void longUnicodeTextIsSplitWithoutLosingSurrogatePairsOrRuns() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord text = FlaRecord.create("CPicText"), run = FlaRecord.create("textRun");
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < 1026; i++) {
            chars.append('a');
        }
        chars.append("\ud83d\ude00");
        run.set("text", chars.toString());
        text.set("runs", Collections.singletonList(run));
        records(source, "CPicFrame").get(0).set("children", Collections.singletonList(text));
        assertTrue(texts(read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(source))).values().stream().anyMatch(v -> v.equals(Collections.singletonList(chars.toString()))));
    }

    @Test
    public void scriptsCrossTheHistoricalBoundaryAndUnsupportedScriptsHaveDiagnostics() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord frame = records(source, "CPicFrame").get(0);
        FlaRecord actions = FlaRecord.create("actions");
        actions.set("source", "stop();");
        frame.set("actions", actions);
        BinaryFlaDocument old = read(new BinaryFlaConverter(FlaFormatVersion.F4).convert(source));
        FlaRecord legacy = (FlaRecord) records(old, "CPicFrame").get(0).get("actions");
        assertEquals(FlaActionSource.reconstruct(legacy).trim(), "stop();");
        actions.set("source", "stop(); this.unknownModernCall();");
        assertTrue(has(new BinaryFlaConverter(FlaFormatVersion.F3).convert(source), "SCRIPT_OMITTED"));
        FlaRecord single = FlaRecord.create("historicalAction");
        single.set("kind", 8);
        single.set("frameNumber", 5);
        single.set("waitForExtraFrames", 2);
        assertEquals(FlaActionSource.reconstruct(single), "ifFrameLoaded (7) {\ngotoAndStop(5);\n}\n");
    }

    @Test
    public void defaultTextStyleIsNotEmittedBeforeItsFlagsExist() throws Exception {
        BinaryFlaDocument source = empty();
        FlaRecord text = FlaRecord.create("CPicText"), run = FlaRecord.create("textRun");
        text.set("textFlags", 0x4000L);
        text.set("defaultStyle", FlaRecord.create("textStyle"));
        run.set("text", "Default style");
        text.set("runs", Collections.singletonList(run));
        records(source, "CPicFrame").get(0).set("children", Collections.singletonList(text));
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F1, FlaFormatVersion.F2, FlaFormatVersion.F3)) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(source));
            assertTrue(texts(output).values().stream().anyMatch(v -> v.equals(Collections.singletonList("Default style"))));
            assertNull(records(output, "CPicText").get(0).get("defaultStyle"));
        }
    }

    @Test
    public void historicalButtonActionsSurviveSameVersionConversion() throws Exception {
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F1, FlaFormatVersion.F2, FlaFormatVersion.F3, FlaFormatVersion.F4)) {
            BinaryFlaDocument source = load(Paths.get("testdata/flang", target.name().toLowerCase(Locale.ROOT), "0014_button_action"));
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(source));
            List<FlaRecord> before = records(source, "CPicButton"), after = records(output, "CPicButton");
            assertEquals(after.size(), before.size());
            for (int i = 0; i < before.size(); i++) {
                assertEquals(after.get(i).get("buttonRevision"), before.get(i).get("buttonRevision"));
                assertEquals(FlaActionSource.reconstruct((FlaRecord) after.get(i).get("actions")), FlaActionSource.reconstruct((FlaRecord) before.get(i).get("actions")));
            }
        }
    }

    @Test
    public void editedCompiledCommandsAreNotReplacedByTheirOriginalBytes() throws Exception {
        BinaryFlaDocument source = empty();
        BinaryArchiveWriter bytes = new BinaryArchiveWriter(session(), "actions");
        // Source revision 5, compiled mode, one breakpoint and one collapse pair.
        for (int word : new int[]{5, 0, 42, 1, 7, 2, 2, 3, 0, 1, 1}) {
            bytes.u32(word);
        }
        MfcArchive archive = new MfcArchive(bytes.finish(), "actions", new FlaReadOptions(), Collections.emptyMap(), 0x47);
        FlaRecord actions = archive.readInline("actions", r -> {
        }, (a, r) -> {
            r.set("revision", a.readUI32());
            r.set("mode", a.readUI32());
            r.set("identifier", a.readUI32());
            a.readUI32();
            r.set("breakpointIndices", Collections.singletonList(a.readUI32()));
            a.readUI32();
            r.set("codeCollapseRangeWords", Arrays.asList(a.readI32(), a.readI32()));
            a.readInline(r, "compiledCommands", (in, list) -> {
                list.set("revision", in.readUI32());
                list.set("count", in.readI32());
                List<FlaRecord> commands = new ArrayList<>();
                in.readInline("compiledCommand", commands::add, (commandIn, c) -> c.set("type", commandIn.readUI32()));
                list.set("commands", Collections.unmodifiableList(commands));
            });
        });
        assertFalse(actions.isModified());
        records(source, "CPicFrame").get(0).set("actions", actions);
        FlaRecord retained = (FlaRecord) records(read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(source)), "CPicFrame").get(0).get("actions");
        assertEquals(retained.get("mode"), 0L);
        assertNotNull(retained.get("compiledCommands"));
        FlaRecord command = (FlaRecord) ((List<?>) ((FlaRecord) actions.get("compiledCommands")).get("commands")).get(0);
        command.set("type", 2L);
        BinaryFlaConversionResult changed = new BinaryFlaConverter(FlaFormatVersion.CS4).convert(source);
        FlaRecord rewritten = (FlaRecord) records(read(changed), "CPicFrame").get(0).get("actions");
        assertEquals(rewritten.get("mode"), 1L);
        assertEquals(((String) rewritten.get("source")).trim(), "stop();");
        assertTrue(((List<?>) rewritten.get("breakpointIndices")).isEmpty());
        assertTrue(((List<?>) rewritten.get("codeCollapseRangeWords")).isEmpty());
        assertTrue(has(changed, "SCRIPT_EDITOR_METADATA"));
    }

    private static final Set<String> REENCODED = new HashSet<>(Arrays.asList("revision", "control", "startDelta", "controlDelta", "endDelta", "firstEdgeControl", "writerIdentity", "unicodeContext", "pixelData", "chunks", "compressed", "isCompressed", "compressedByteCount", "chunkTerminator", "encodedByteCount", "sourceLength", "sourceUnicodeMarker", "charactersData", "codeCollapseRanges"));

    private static void assertCommonValues(Object source, Object output, String path, Set<FlaRecord> seen) {
        if (source instanceof FlaRecord && output instanceof FlaRecord) {
            FlaRecord a = (FlaRecord) source, b = (FlaRecord) output;
            if (!seen.add(a)) {
                return;
            }
            if (a.getClassName().equals("raster")) {
                assertEquals(rasterPixels(b), rasterPixels(a), path + " raster pixels");
            }
            for (String key : a.getFields().keySet()) {
                if (!REENCODED.contains(key) && !key.endsWith("Count") && b.getFields().containsKey(key)) {
                    if (a.get(key) instanceof BinaryData) {
                        if (key.equals("data") || key.equals("encodedData")) {
                            assertEquals(((BinaryData) b.get(key)).toByteArray(), ((BinaryData) a.get(key)).toByteArray(), path + "/" + key);
                        }
                    } else {
                        assertCommonValues(a.get(key), b.get(key), path + "/" + a.getClassName() + "." + key, seen);
                    }
                }
            }
        } else if (source instanceof List && output instanceof List) {
            List<?> a = (List<?>) source, b = (List<?>) output;
            assertEquals(b.size(), a.size(), path);
            for (int i = 0; i < a.size(); i++) {
                assertCommonValues(a.get(i), b.get(i), path + "[" + i + "]", seen);
            }
        } else if (source instanceof Number && output instanceof Number) {
            assertEquals(Double.compare(((Number) output).doubleValue(), ((Number) source).doubleValue()), 0, path);
        } else {
            assertEquals(output, source, path);
        }
    }

    private static byte[] rasterPixels(FlaRecord raster) {
        if (raster.get("pixelData") instanceof BinaryData) {
            return ((BinaryData) raster.get("pixelData")).toByteArray();
        }
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            for (Object entry : (List<?>) raster.get("chunks")) {
                compressed.write(((BinaryData) ((FlaRecord) entry).get("data")).toByteArray());
            }
            ByteArrayOutputStream pixels = new ByteArrayOutputStream();
            try (InputStream in = new java.util.zip.InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
                byte[] buffer = new byte[8192];
                for (int count; (count = in.read(buffer)) != -1;) {
                    pixels.write(buffer, 0, count);
                }
            }
            return pixels.toByteArray();
        } catch (IOException ex) {
            throw new AssertionError("Invalid raster compression", ex);
        }
    }

    private static BinaryFlaConverter.Session session() throws Exception {
        return new BinaryFlaConverter.Session(empty(), FlaFormatVersion.CS4, ANSI);
    }

    private static boolean has(BinaryFlaConversionResult result, String code) {
        return result.getIssues().stream().anyMatch(i -> i.getCode().equals(code));
    }

    private static BinaryFlaDocument empty() throws Exception {
        return load(Paths.get("testdata/fla/cs4/0001_empty_doc"));
    }

    private static BinaryFlaDocument load(Path path) throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return new BinaryFlaReader().readStreams(streams);
    }

    private static BinaryFlaDocument read(BinaryFlaConversionResult result) throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        result.getStreams().forEach((p, d) -> streams.put(p, d.toByteArray()));
        BinaryFlaDocument document = new BinaryFlaReader().readStreams(streams);
        assertTrue(document.getIssues().isEmpty(), document.getIssues().toString());
        return document;
    }

    private static IdentityHashMap<FlaRecord, Map<String, Object>> snapshot(BinaryFlaDocument source) {
        IdentityHashMap<FlaRecord, Map<String, Object>> result = new IdentityHashMap<>();
        for (FlaStream stream : source.getStreams().values()) {
            for (FlaRecord r : stream.getObjects()) {
                result.put(r, new LinkedHashMap<>(r.getFields()));
            }
        }
        return result;
    }

    private static List<FlaRecord> records(BinaryFlaDocument document, String type) {
        List<FlaRecord> result = new ArrayList<>();
        Set<FlaRecord> seen = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
        for (FlaStream stream : document.getStreams().values()) {
            collect(stream.getRoot(), type, seen, result);
        }
        return result;
    }

    private static void collect(Object value, String type, Set<FlaRecord> seen, List<FlaRecord> result) {
        if (value instanceof FlaRecord) {
            FlaRecord r = (FlaRecord) value;
            if (!seen.add(r)) {
                return;
            }
            if (r.getClassName().equals(type)) {
                result.add(r);
            }
            for (Object child : r.getFields().values()) {
                collect(child, type, seen, result);
            }
        } else if (value instanceof List) {
            for (Object child : (List<?>) value) {
                collect(child, type, seen, result);
            }
        }
    }

    private static Map<String, List<String>> texts(BinaryFlaDocument doc) {
        Map<String, List<String>> result = new TreeMap<>();
        for (FlaStream stream : doc.getStreams().values()) {
            List<FlaRecord> texts = new ArrayList<>();
            collect(stream.getRoot(), "CPicText", Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()), texts);
            if (texts.isEmpty()) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (FlaRecord r : texts) {
                StringBuilder text = new StringBuilder();
                for (Object run : (List<?>) r.get("runs")) {
                    text.append(((FlaRecord) run).get("text"));
                }
                values.add(text.toString());
            }
            result.put(stream.getPath(), values);
        }
        return result;
    }

    private static Map<String, List<List<Object>>> edges(BinaryFlaDocument doc) {
        Map<String, List<List<Object>>> result = new TreeMap<>();
        for (FlaStream stream : doc.getStreams().values()) {
            List<FlaRecord> edges = new ArrayList<>();
            collect(stream.getRoot(), "edge", Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()), edges);
            if (edges.isEmpty()) {
                continue;
            }
            List<List<Object>> values = new ArrayList<>();
            for (FlaRecord edge : edges) {
                List<Object> words = new ArrayList<>();
                for (String key : Arrays.asList("startX", "startY", "controlX", "controlY", "endX", "endY", "curve", "strokeIndex", "fillIndex0", "fillIndex1")) {
                    words.add(edge.get(key));
                }
                values.add(words);
            }
            result.put(stream.getPath(), values);
        }
        return result;
    }
}
