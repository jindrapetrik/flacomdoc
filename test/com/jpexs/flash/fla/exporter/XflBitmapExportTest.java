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

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.converter.*;
import com.jpexs.flash.fla.converter.streams.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;

public class XflBitmapExportTest {

    private static final String NS = "http://ns.adobe.com/xfl/2008/";

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void nativeBitmapItemsInstancesFillsAndFilesMatchReference(String version) throws Exception {
        BinaryFlaDocument source = nativeFile(version);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element root = xml(result);
        NodeList items = root.getElementsByTagNameNS(NS, "DOMBitmapItem");
        assertEquals(items.getLength(), 3);
        assertFalse(has(result, "BITMAP_DATA"), result.getReport());
        assertFalse(has(result, "MEDIA_NOT_EXPORTED"), result.getReport());
        assertFalse(has(result, "MISSING_BITMAP"), result.getReport());
        assertFalse(has(result, "PAINT_STYLE"), result.getReport());
        for (int i = 0; i < 3; i++) {
            Element item = (Element) items.item(i);
            BinaryFlaDocument.Media media = source.getMedia().get(i);
            assertEquals(item.getAttribute("name"), media.getDescriptor().get("name"));
            BinaryData dat = result.getFiles().get("bin/" + item.getAttribute("bitmapDataHRef"));
            assertEquals(dat.toByteArray(), media.getStream().getData().toByteArray());
            BinaryData image = result.getFiles().get("LIBRARY/" + item.getAttribute("href"));
            assertNotNull(image);
            if (item.getAttribute("isJPEG").equals("true")) {
                assertEquals(image.toByteArray(), ((BinaryData) media.getStream().getRoot().get("encodedData")).toByteArray());
                byte[] original = Files.readAllBytes(Paths.get("testdata/fla/cs5/0013_bitmaps/LIBRARY", item.getAttribute("name")));
                assertEquals(image.toByteArray(), original);
            }
        }
        Element paired = parse(Files.readAllBytes(Paths.get("testdata/fla/cs5/0013_bitmaps/DOMDocument.xml")));
        NodeList a = root.getElementsByTagNameNS(NS, "DOMBitmapInstance"), b = paired.getElementsByTagNameNS(NS, "DOMBitmapInstance");
        assertEquals(a.getLength(), b.getLength());
        for (int i = 0; i < a.getLength(); i++) {
            assertEquals(((Element) a.item(i)).getAttribute("libraryItemName"), ((Element) b.item(i)).getAttribute("libraryItemName"));
        }
        a = root.getElementsByTagNameNS(NS, "BitmapFill");
        b = paired.getElementsByTagNameNS(NS, "BitmapFill");
        // Older reference documents contain four fills; later Adobe saves added
        // a fifth. Verify their native count rather than inventing that fill.
        assertEquals(a.getLength(), version.equals("f5") || version.equals("mx") || version.equals("mx2004") ? 4 : b.getLength());
        for (int i = 0; i < a.getLength(); i++) {
            Element x = (Element) a.item(i), y = (Element) b.item(i);
            assertEquals(x.getAttribute("bitmapPath"), y.getAttribute("bitmapPath"));
            assertEquals(x.getAttribute("bitmapIsClipped"), y.hasAttribute("bitmapIsClipped") ? y.getAttribute("bitmapIsClipped") : "true");
            Element xm = (Element) x.getElementsByTagNameNS(NS, "Matrix").item(0), ym = (Element) y.getElementsByTagNameNS(NS, "Matrix").item(0);
            for (String field : Arrays.asList("a", "d", "tx", "ty")) {
                assertEquals(Double.parseDouble(xm.getAttribute(field)), ym.hasAttribute(field) ? Double.parseDouble(ym.getAttribute(field)) : field.equals("a") || field.equals("d") ? 1.0 : 0.0, 0.00002);
            }
        }
        if (version.equals("cs4")) {
            assertEquals(((Element) items.item(1)).getAttribute("useDeblocking"), "true");
        }
    }

    @Test
    public void nativePngRetainsStoredPremultipliedPixelsAndReferenceOpaqueColors() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4");
        FlaRecord raster = (FlaRecord) source.getMedia().get(0).getStream().getRoot().get("raster");
        byte[] stored = inflated(raster);
        BufferedImage original = ImageIO.read(Paths.get("testdata/fla/cs5/0013_bitmaps/LIBRARY/bitmapfill.png").toFile());
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        BufferedImage exported = ImageIO.read(result.getFiles().get("LIBRARY/bitmap-1.png").openStream());
        int stride = ((Number) raster.get("rowStride")).intValue();
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int color = original.getRGB(x, y), alpha = color >>> 24;
                int p = y * stride + x * 4;
                // Adobe's imported raster is already quantized. Its external
                // source PNG is evidence for channels/alpha, not original bytes
                // that can be recovered exactly after premultiplication.
                assertTrue(Math.abs((stored[p] & 255) - alpha) <= 1);
                int reconstructed = exported.getRGB(x, y);
                assertEquals(reconstructed >>> 24, stored[p] & 255);
                for (int c = 0; c < 3; c++) {
                    int channel = color >>> (16 - c * 8) & 255;
                    int expected = stored[p + 1 + c] & 255;
                    if (alpha == 255) {
                        assertTrue(Math.abs(expected - channel) <= 1);
                    }
                    int preview = reconstructed >>> (16 - c * 8) & 255;
                    assertTrue(Math.abs((preview * (reconstructed >>> 24) + 127) / 255 - expected) <= 1);
                }
            }
        }
    }

    @DataProvider(name = "rasters")
    public Object[][] rasters() {
        List<Object[]> cases = new ArrayList<>();
        for (int revision = 0; revision < 4; revision++) {
            for (int format : new int[]{0, 1, 2, 3, 4, 5}) {
                cases.add(new Object[]{revision, format, revision == 1 || revision == 3});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "rasters")
    public void historicalAndCurrentRasterVariantsRespectPaddingPaletteAndAlpha(int revision, int format, boolean compressed) throws Exception {
        BinaryFlaDocument source = replaceRaster(raster(revision, format, compressed, false, 3, 2));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertFalse(has(result, "BITMAP_DATA"), result.getReport());
        xml(result);
        BufferedImage png = ImageIO.read(result.getFiles().get("LIBRARY/bitmap-1.png").openStream());
        assertEquals(png.getWidth(), 3);
        assertEquals(png.getHeight(), 2);
        assertEquals(png.getRGB(0, 0), 0xFFFF0000);
        assertEquals(png.getRGB(1, 0), 0xFF0000FF);
        assertEquals(png.getRGB(2, 1), 0xFF0000FF);
        byte[] dat = result.getFiles().get("bin/bitmap1.dat").toByteArray();
        assertEquals(dat[0], (byte) 3);
        if (revision == 3) {
            assertEquals(dat, source.getMedia().get(0).getStream().getData().toByteArray());
        } else {
            BinaryFlaDocument normalized = replaceRaster(dat);
            FlaRecord r = (FlaRecord) normalized.getMedia().get(0).getStream().getRoot().get("raster");
            assertEquals(r.get("revision"), 3);
            assertEquals(r.get("hasAlpha"), 0);
            XflExportResult again = new BinaryFlaToXflExporter().export(normalized);
            BufferedImage image = ImageIO.read(again.getFiles().get("LIBRARY/bitmap-1.png").openStream());
            assertEquals(image.getRGB(0, 0), png.getRGB(0, 0));
            assertEquals(image.getRGB(2, 1), png.getRGB(2, 1));
        }
    }

    @Test
    public void transparencyIsUnpremultipliedOnlyForTheExternalPng() throws Exception {
        byte[] raw = raster(3, 5, false, true, 3, 2);
        XflExportResult result = new BinaryFlaToXflExporter().export(replaceRaster(raw));
        assertFalse(has(result, "BITMAP_DATA"), result.getReport());
        BufferedImage png = ImageIO.read(result.getFiles().get("LIBRARY/bitmap-1.png").openStream());
        assertEquals(png.getRGB(0, 0) >>> 24, 128);
        assertEquals(png.getRGB(0, 0) >>> 16 & 255, 128, 1);
        assertEquals(png.getRGB(1, 0), 0);
        assertEquals(result.getFiles().get("bin/bitmap1.dat").toByteArray(), raw);
    }

    @Test
    public void paletteAlphaUsesTheSameChannelOrderAsDirectPixels() throws Exception {
        byte[] raw = raster(3, 3, false, true, 3, 2);
        raw[27] = (byte) 128;
        raw[28] = 64;
        raw[29] = 0;
        raw[30] = 32;
        Arrays.fill(raw, 31, 35, (byte) 0);
        XflExportResult result = new BinaryFlaToXflExporter().export(replaceRaster(raw));
        assertFalse(has(result, "BITMAP_DATA"), result.getReport());
        BufferedImage png = ImageIO.read(result.getFiles().get("LIBRARY/bitmap-1.png").openStream());
        int color = png.getRGB(0, 0);
        assertEquals(color >>> 24, 128);
        assertEquals(color >>> 16 & 255, 128, 1);
        assertEquals(color & 255, 64, 1);
        assertEquals(png.getRGB(1, 0), 0);
        assertEquals(result.getFiles().get("bin/bitmap1.dat").toByteArray(), raw);
    }

    @Test
    public void opaqueRasterFlagIgnoresStoredAlphaWithoutChangingTheNativeBin() throws Exception {
        byte[] raw = raster(3, 5, false, false, 3, 2);
        raw[26] = 0;
        XflExportResult result = new BinaryFlaToXflExporter().export(replaceRaster(raw));
        assertFalse(has(result, "BITMAP_DATA"), result.getReport());
        BufferedImage png = ImageIO.read(result.getFiles().get("LIBRARY/bitmap-1.png").openStream());
        assertEquals(png.getRGB(0, 0), 0xFFFF0000);
        assertEquals(result.getFiles().get("bin/bitmap1.dat").toByteArray(), raw);
    }

    @Test
    public void invalidJpegIsOmittedWithoutAffectingOtherMedia() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4");
        Map<String, byte[]> streams = streams(source);
        String jpegPath = source.getMedia().get(1).getStream().getPath();
        streams.get(jpegPath)[0] = 0;
        XflExportResult result = new BinaryFlaToXflExporter().export(new BinaryFlaReader().readStreams(streams));
        assertTrue(has(result, "BITMAP_DATA"));
        assertNull(result.getFiles().get("bin/bitmap2.dat"));
        assertNotNull(result.getFiles().get("bin/bitmap1.dat"));
        assertNotNull(result.getFiles().get("bin/bitmap3.dat"));
        assertEquals(xml(result).getElementsByTagNameNS(NS, "DOMBitmapItem").getLength(), 2);
    }

    @Test
    public void jpegDimensionsAreLimitedBeforeDecodingPixels() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4");
        Map<String, byte[]> streams = streams(source);
        byte[] jpeg = streams.get(source.getMedia().get(1).getStream().getPath());
        boolean changed = false;
        for (int p = 2; p + 9 < jpeg.length; p++) {
            if ((jpeg[p] & 255) == 255 && (jpeg[p + 1] & 255) == 192) {
                Arrays.fill(jpeg, p + 5, p + 9, (byte) 255);
                changed = true;
                break;
            }
        }
        assertTrue(changed, "Reference JPEG must contain baseline SOF dimensions");
        XflExportResult result = new BinaryFlaToXflExporter().export(new BinaryFlaReader().readStreams(streams));
        assertTrue(has(result, "BITMAP_DATA"));
        assertNull(result.getFiles().get("bin/bitmap2.dat"));
        xml(result);
    }

    @DataProvider(name = "badRasters")
    public Object[][] badRasters() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        byte[] good = raster(3, 5, true, false, 3, 2);
        byte[] corrupt = good.clone();
        corrupt[28] ^= 0x55;
        cases.add(new Object[]{corrupt});
        byte[] tooSmall = raster(3, 5, false, false, 3, 2);
        tooSmall[4] = 5; // Width exceeds the stride's visible pixel capacity.
        cases.add(new Object[]{tooSmall});
        byte[] zero = raster(3, 5, false, false, 3, 2);
        zero[4] = 0;
        cases.add(new Object[]{zero});
        byte[] palette = raster(3, 3, false, false, 3, 2);
        palette[palette.length - 8] = 7;
        cases.add(new Object[]{palette});
        byte[] matte = raster(3, 5, false, true, 3, 2);
        matte[27] = (byte) 255;
        cases.add(new Object[]{matte});
        byte[] sixteenBit = raster(3, 5, false, false, 3, 2);
        sixteenBit[1] = 4;
        sixteenBit[4] = 20; // RGB555 row exceeds the stride.
        cases.add(new Object[]{sixteenBit});
        return cases.toArray(new Object[0][]);
    }

    @DataProvider(name = "zlibBoundaries")
    public Object[][] zlibBoundaries() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        for (int length : new int[]{31, 33}) {
            cases.add(new Object[]{compressedRaster(deflate(new byte[length], false))});
        }
        byte[] correct = deflate(new byte[32], false);
        cases.add(new Object[]{compressedRaster(Arrays.copyOf(correct, correct.length - 2))});
        byte[] crc = correct.clone();
        crc[crc.length - 1] ^= 1;
        cases.add(new Object[]{compressedRaster(crc)});
        cases.add(new Object[]{compressedRaster(Arrays.copyOf(correct, correct.length + 1))});
        cases.add(new Object[]{compressedRaster(deflate(new byte[32], true))});
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "zlibBoundaries")
    public void zlibRequiresExactOutputChecksumTerminationAndNoDictionary(byte[] raw) throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(replaceRaster(raw));
        assertTrue(has(result, "BITMAP_DATA"), result.getReport());
        assertNull(result.getFiles().get("bin/bitmap1.dat"));
        assertNull(result.getFiles().get("LIBRARY/bitmap-1.png"));
        xml(result);
    }

    @Test
    public void perFillSmoothingConflictsAreReportedWithoutInventedAttributes() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4");
        source.getMedia().get(0).getDescriptor().set("allowSmoothing", 0);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertFalse(has(result, "BITMAP_SMOOTHING"));
        assertTrue(has(result, "BITMAP_VARIANT"));
        Element root = xml(result);
        NodeList fills = root.getElementsByTagNameNS(NS, "BitmapFill");
        assertTrue(fills.getLength() > 0);
        for (int i = 0; i < fills.getLength(); i++) {
            assertFalse(((Element) fills.item(i)).hasAttribute("bitmapIsSmoothed"));
        }
        assertEquals(((Element) root.getElementsByTagNameNS(NS, "DOMBitmapItem").item(0)).getAttribute("allowSmoothing"), "false");
    }

    @Test(dataProvider = "badRasters")
    public void malformedCodecDataDoesNotLeaveFilesOrUnresolvedReferences(byte[] raw) throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(replaceRaster(raw));
        assertTrue(has(result, "BITMAP_DATA"), result.getReport());
        assertTrue(has(result, "MISSING_BITMAP"), result.getReport());
        assertNull(result.getFiles().get("LIBRARY/bitmap-1.png"));
        assertNull(result.getFiles().get("bin/bitmap1.dat"));
        assertEquals(xml(result).getElementsByTagNameNS(NS, "DOMBitmapItem").getLength(), 2);
    }

    @Test
    public void excessiveInflatedSizeIsRejectedBeforeAllocation() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4");
        FlaRecord raster = (FlaRecord) source.getMedia().get(0).getStream().getRoot().get("raster");
        raster.set("width", 65535);
        raster.set("height", 65535);
        raster.set("rowStride", 65535);
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertTrue(has(result, "BITMAP_DATA"));
        assertNull(result.getFiles().get("LIBRARY/bitmap-1.png"));
    }

    @Test
    public void missingStreamDuplicateIdAndUnsafeNamesAreHandledExplicitly() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4");
        source.getMedia().get(0).getDescriptor().set("name", "../../outside & \"image\"");
        source.getMedia().get(1).getDescriptor().set("mediaId", source.getMedia().get(0).getDescriptor().get("mediaId"));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertTrue(has(result, "MEDIA_ID"));
        assertEquals(((Element) xml(result).getElementsByTagNameNS(NS, "DOMBitmapItem").item(0)).getAttribute("name"), "../../outside & \"image\"");
        assertNotNull(result.getFiles().get("LIBRARY/bitmap-1.png"));
        for (String path : result.getFiles().keySet()) {
            assertFalse(path.contains(".."));
        }
        Map<String, byte[]> streams = streams(nativeFile("cs4"));
        streams.remove((String) source.getMedia().get(0).getDescriptor().get("streamName"));
        result = new BinaryFlaToXflExporter().export(new BinaryFlaReader().readStreams(streams));
        assertTrue(has(result, "BITMAP_DATA"));
        assertEquals(xml(result).getElementsByTagNameNS(NS, "DOMBitmapItem").getLength(), 2);
    }

    @Test
    public void exportedNativeBinFilesSurviveTheExistingBinaryWriter() throws Exception {
        BinaryFlaDocument original = nativeFile("cs4");
        XflExportResult result = new BinaryFlaToXflExporter().export(original);
        Memory io = new Memory();
        for (Map.Entry<String, BinaryData> e : result.getFiles().entrySet()) {
            io.bytes.put(e.getKey(), e.getValue().toByteArray());
        }
        Memory out = new Memory();
        new LegacyFlaConverter(FlaFormatVersion.CS4, "windows-1250").convert(io, out);
        for (Map.Entry<String, ByteArrayOutputStream> e : out.output.entrySet()) {
            out.bytes.put(e.getKey(), e.getValue().toByteArray());
        }
        BinaryFlaDocument roundTrip = new BinaryFlaReader().readStreams(out.bytes);
        assertEquals(roundTrip.getMedia().size(), original.getMedia().size());
        for (int i = 0; i < original.getMedia().size(); i++) {
            BinaryFlaDocument.Media a = original.getMedia().get(i), b = roundTrip.getMedia().get(i);
            assertEquals(b.getStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE);
            assertEquals(b.getStream().getData().toByteArray(), a.getStream().getData().toByteArray());
            for (String field : Arrays.asList("compressionMode", "jpegQuality", "allowSmoothing", "useDeblocking", "encodedByteCount")) {
                assertEquals(b.getDescriptor().get(field), a.getDescriptor().get(field), field);
            }
        }
    }

    @Test
    public void legacyInlineBitmapBecomesALibraryItem() throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        Path base = Paths.get("testdata/flang/f1/0010_bitmaps");
        try (java.util.stream.Stream<Path> files = Files.walk(base)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                streams.put(base.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams);
        assertTrue(doc.getIssues().isEmpty(), doc.getIssues().toString());
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertFalse(has(result, "BITMAP_DATA"), result.getReport());
        assertTrue(xml(result).getElementsByTagNameNS(NS, "DOMBitmapItem").getLength() > 0);
        assertTrue(xml(result).getElementsByTagNameNS(NS, "DOMBitmapInstance").getLength() > 0);
    }

    @Test
    public void schemaAcceptsAdobeBitmapMetadataAndKeepsItsBooleanTypes() throws Exception {
        byte[] paired = Files.readAllBytes(Paths.get("testdata/fla/cs5/0013_bitmaps/DOMDocument.xml"));
        XflSchemaTestSupport.validate(paired);
        String bad = new String(paired, java.nio.charset.StandardCharsets.UTF_8).replace("useDeblocking=\"true\"", "useDeblocking=\"maybe\"");
        try {
            XflSchemaTestSupport.validate(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fail("Invalid boolean accepted");
        } catch (org.xml.sax.SAXException expected) {
        }
    }

    @DataProvider(name = "adobeSchemas")
    public Object[][] adobeSchemas() {
        return new Object[][]{{"0009_symbols"}, {"0017_classictween"}, {"0027_groups"}};
    }

    @Test(dataProvider = "adobeSchemas")
    public void schemaFixesValidateEntireAdobeReferenceDocuments(String sample) throws Exception {
        XflSchemaTestSupport.validate(Files.readAllBytes(Paths.get("testdata/fla/cs5", sample, "DOMDocument.xml")));
    }

    private static BinaryFlaDocument nativeFile(String version) throws Exception {
        BinaryFlaDocument d = new BinaryFlaReader().read(Paths.get("testdata/fla", version, "0013_bitmaps.fla").toFile());
        assertTrue(d.getIssues().isEmpty(), d.getIssues().toString());
        return d;
    }

    private static Map<String, byte[]> streams(BinaryFlaDocument doc) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (FlaStream stream : doc.getStreams().values()) {
            result.put(stream.getPath(), stream.getData().toByteArray());
        }
        return result;
    }

    private static BinaryFlaDocument replaceRaster(byte[] raw) throws Exception {
        BinaryFlaDocument original = nativeFile("cs4");
        Map<String, byte[]> streams = streams(original);
        streams.put(original.getMedia().get(0).getStream().getPath(), raw);
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams);
        assertTrue(doc.getIssues().isEmpty(), doc.getIssues().toString());
        return doc;
    }

    private static byte[] raster(int revision, int format, boolean compressed, boolean alpha, int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        out.write(format);
        int depth = format == 5 ? 32 : 1 << format, stride = ((width * depth + 7) / 8 + 3) & ~3;
        if (format == 5) {
            stride += 4;
        }
        u16(out, stride);
        u16(out, width);
        u16(out, height);
        for (int word : new int[]{0, width * 20, 0, height * 20}) {
            for (int i = 0; i < 4; i++) {
                out.write(word >>> (i * 8) & 255);
            }
        }
        if (revision == 3) {
            out.write(alpha ? 1 : 0);
        }
        if (format < 4) {
            u16(out, 2);
            for (int color : new int[]{0xFFFF0000, 0xFF0000FF}) {
                if (revision == 3) {
                    for (int shift = 24; shift >= 0; shift -= 8) {
                        out.write(color >>> shift & 255);
                    }
                } else {
                    for (int shift = 16; shift >= 0; shift -= 8) {
                        u16(out, ((color >>> shift & 255) << 8) | 0x12);
                    }
                }
            }
        }
        byte[] pixels = new byte[stride * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (x + y) % 2;
                if (format == 5) {
                    int p = y * stride + x * 4;
                    pixels[p] = (byte) (alpha ? index == 0 ? 128 : 0 : revision == 3 ? 255 : 0);
                    pixels[p + 1] = (byte) (index == 0 ? alpha ? 64 : 255 : 0);
                    pixels[p + 3] = (byte) (index == 1 && !alpha ? 255 : 0);
                } else if (format == 4) {
                    int p = y * stride + x * 2, word = index == 0 ? 0x7C00 : 0x001F;
                    pixels[p] = (byte) (word >>> 8);
                    pixels[p + 1] = (byte) word;
                } else {
                    int bit = x * depth;
                    pixels[y * stride + bit / 8] |= index << (8 - depth - bit % 8);
                }
            }
        }
        if (revision >= 2) {
            out.write(compressed ? 1 : 0);
        }
        if (revision == 1 || compressed) {
            ByteArrayOutputStream zlib = new ByteArrayOutputStream();
            try (DeflaterOutputStream def = new DeflaterOutputStream(zlib)) {
                def.write(pixels);
            }
            byte[] data = zlib.toByteArray();
            for (int p = 0; p < data.length;) {
                int count = Math.min(3, data.length - p);
                u16(out, count);
                out.write(data, p, count);
                p += count;
            }
            u16(out, 0);
        } else {
            out.write(pixels);
        }
        return out.toByteArray();
    }

    @Test
    public void sixteenBitWordsUseBigEndianRgb555AndNativeChannelQuantization() throws Exception {
        byte[] raw = raster(3, 4, false, true, 32, 1);
        int[] levels = {0, 15, 23, 31, 39, 47, 55, 63, 71, 79, 87, 95, 95, 111, 119, 127, 135, 143, 151, 159, 167, 175, 183, 191, 199, 207, 215, 223, 223, 239, 247, 255};
        for (int x = 0; x < 32; x++) {
            int word = 0x8000 | x << 10 | (31 - x) << 5 | (x * 3 & 31);
            raw[26 + x * 2] = (byte) (word >>> 8);
            raw[27 + x * 2] = (byte) word;
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(replaceRaster(raw));
        assertFalse(has(result, "BITMAP_DATA"), result.getReport());
        BufferedImage image = ImageIO.read(result.getFiles().get("LIBRARY/bitmap-1.png").openStream());
        for (int x = 0; x < 32; x++) {
            assertEquals(image.getRGB(x, 0), 0xFF000000 | levels[x] << 16 | levels[31 - x] << 8 | levels[x * 3 & 31]);
        }
        assertEquals(result.getFiles().get("bin/bitmap1.dat").toByteArray(), raw);
        xml(result);
    }

    private static byte[] deflate(byte[] bytes, boolean dictionary) throws Exception {
        Deflater deflater = new Deflater();
        if (dictionary) {
            deflater.setDictionary(new byte[]{1, 2, 3, 4});
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(result, deflater)) {
            out.write(bytes);
        } finally {
            deflater.end();
        }
        return result.toByteArray();
    }

    private static byte[] compressedRaster(byte[] body) throws Exception {
        byte[] header = Arrays.copyOf(raster(3, 5, false, false, 3, 2), 26);
        header[25] = 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        // Split the zlib stream at arbitrary boundaries, including its trailer.
        for (int p = 0; p < body.length;) {
            int count = Math.min(3, body.length - p);
            u16(out, count);
            out.write(body, p, count);
            p += count;
        }
        u16(out, 0);
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static byte[] inflated(FlaRecord raster) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        for (FlaRecord chunk : (List<FlaRecord>) raster.get("chunks")) {
            compressed.write(((BinaryData) chunk.get("data")).toByteArray());
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            byte[] bytes = new byte[4096];
            int n;
            while ((n = in.read(bytes)) != -1) {
                result.write(bytes, 0, n);
            }
        }
        return result.toByteArray();
    }

    private static void u16(ByteArrayOutputStream out, int n) {
        out.write(n & 255);
        out.write(n >>> 8 & 255);
    }

    private static boolean has(XflExportResult r, String code) {
        return r.getIssues().stream().anyMatch(i -> i.getCode().equals(code));
    }

    private static Element xml(XflExportResult r) throws Exception {
        byte[] bytes = r.getFiles().get("DOMDocument.xml").toByteArray();
        XflSchemaTestSupport.validate(bytes);
        return parse(bytes);
    }

    private static Element parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
    }

    private static final class Memory implements InputStorageInterface, OutputStorageInterface {

        final Map<String, byte[]> bytes = new LinkedHashMap<>();
        final Map<String, ByteArrayOutputStream> output = new LinkedHashMap<>();

        public boolean fileExists(String path) {
            return bytes.containsKey(path);
        }

        public InputStream readFile(String path) {
            return bytes.containsKey(path) ? new ByteArrayInputStream(bytes.get(path)) : null;
        }

        public OutputStream getOutputStream(String path) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            output.put(path, out);
            return out;
        }

        public void storeData(String path, byte[] value) {
            bytes.put(path, value.clone());
        }

        public void storeFile(String path, File file) throws IOException {
            bytes.put(path, Files.readAllBytes(file.toPath()));
        }

        public void close() {
        }
    }
}
