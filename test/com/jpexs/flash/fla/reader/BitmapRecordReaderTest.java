package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class BitmapRecordReaderTest {

    private static final byte[] PIXELS = {0, 1, 2, (byte) 255, 4, 5, 6, (byte) 128};

    @DataProvider(name = "rasters")
    public Object[][] rasters() {
        List<Object[]> cases = new ArrayList<>();
        for (int revision = 0; revision <= 3; revision++) {
            for (int format = 0; format <= 5; format++) {
                for (int flag : revision < 2 ? new int[]{revision} : new int[]{0, 1, 0xFE}) {
                    cases.add(new Object[]{revision, format, flag});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "rasters")
    public void independentRasterRevisionsRetainPaletteWidthsAndStoredPixels(int revision, int format, int flag) throws Exception {
        ByteArrayOutputStream out = raster(revision, format, flag);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("raster", value -> {
        }, BitmapRecordDecoder::raster);
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("formatCode"), format);
        assertEquals(r.get("rowStride"), 4);
        assertEquals(r.get("width"), 2);
        assertEquals(r.get("height"), 2);
        assertEquals(r.get("boundsWords"), Arrays.asList(-250, -150, -50, 50));
        assertEquals(r.get("hasAlpha"), revision >= 3 ? 0xFE : null);
        assertEquals(r.get("expectedByteCount"), 8L); // Stride * height, independent of width * height * 4.
        if (format < 4) {
            assertEquals(r.get("paletteCount"), 2);
            List<FlaRecord> palette = records(r, "palette");
            assertEquals(palette.size(), 2);
            assertEquals(palette.get(0).get("components"), revision >= 3 ? Arrays.asList(0, 1, 128, 255) : Arrays.asList(0x1234, 0xFEDC, 0x8001));
        } else {
            assertNull(r.get("palette"));
        }
        boolean compressed = revision == 1 || (revision >= 2 && flag != 0);
        assertEquals(r.get("isCompressed"), compressed);
        assertEquals(r.get("compressed"), revision >= 2 ? flag : null);
        if (compressed) {
            assertNull(r.get("pixelData"));
            assertEquals(records(r, "chunks").size(), 2);
            assertEquals(r.get("chunkTerminator"), 0);
            assertEquals(inflate(r), PIXELS);
        } else {
            assertEquals(((BinaryData) r.get("pixelData")).toByteArray(), PIXELS);
            assertNull(r.get("chunks"));
        }
        assertEquals(a.getObjects().size(), 0);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "rasters")
    public void everyTruncatedRasterRetainsItsOriginalPrefix(int revision, int format, int flag) throws Exception {
        byte[] bytes = raster(revision, format, flag).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] root = {null};
            try {
                a.readInline("raster", value -> root[0] = value, BitmapRecordDecoder::raster);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known raster must not become unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(root[0]);
                assertFalse(root[0].isComplete());
                assertEquals(root[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void compressedFramingDrainsThroughZeroIncludingChunksAfterZlibEnd() throws Exception {
        ByteArrayOutputStream out = raster(3, 5, 1);
        byte[] bytes = out.toByteArray();
        out.reset();
        out.write(bytes, 0, bytes.length - 2);
        u16(out, 3);
        out.write(new byte[]{12, 34, 56});
        u16(out, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("raster", value -> {
        }, BitmapRecordDecoder::raster);
        assertEquals(records(r, "chunks").size(), 3);
        assertEquals(((BinaryData) records(r, "chunks").get(2).get("data")).toByteArray(), new byte[]{12, 34, 56});
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void zeroSizedAndLargeCompressedRasterDoNotAllocateDecodedPixels() throws Exception {
        for (int size : new int[]{0, 65535}) {
            ByteArrayOutputStream out = rasterHeader(3, 5, size, size, size);
            out.write(1);
            u16(out, 0);
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = a.readInline("raster", value -> {
            }, BitmapRecordDecoder::raster);
            assertEquals(r.get("expectedByteCount"), (long) size * size);
            assertTrue(records(r, "chunks").isEmpty());
            assertNull(r.get("pixelData"));
            assertEquals(a.readUI16(), 0xBEEF);
        }
    }

    @Test
    public void oversizedRawRasterFailsBeforeAllocationAndKeepsTheUnsignedSize() throws Exception {
        ByteArrayOutputStream out = rasterHeader(0, 5, 65535, 65535, 65535);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        try {
            a.readInline("raster", value -> root[0] = value, BitmapRecordDecoder::raster);
            fail("Expected missing raw pixels");
        } catch (IOException expected) {
            assertEquals(root[0].get("expectedByteCount"), 4294836225L);
            assertNull(root[0].get("pixelData"));
            assertFalse(root[0].isComplete());
        }
    }

    @Test
    public void paletteCapacityAndConfiguredCollectionLimitsAreApplied() throws Exception {
        ByteArrayOutputStream out = rasterHeader(3, 3, 0, 0, 0);
        u16(out, 257);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        try {
            a.readInline("raster", value -> {
            }, BitmapRecordDecoder::raster);
            fail("Expected native palette capacity");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("palette"));
            assertEquals(a.readUI16(), 0xBEEF);
        }
        out = rasterHeader(3, 5, 4, 2, 2);
        out.write(1);
        for (int i = 0; i < 3; i++) {
            u16(out, 1);
            out.write(i);
        }
        u16(out, 0);
        MfcArchive limited = new MfcArchive(new BinaryData(out.toByteArray()), "test", new FlaReadOptions(ANSI, 1024, 1024, 2, 128), DefaultFlaDecoders.create(), 0);
        FlaRecord[] root = {null};
        try {
            limited.readInline("raster", value -> root[0] = value, BitmapRecordDecoder::raster);
            fail("Expected chunk count limit");
        } catch (IOException expected) {
            assertEquals(records(root[0], "chunks").size(), 2);
            assertFalse(root[0].isComplete());
        }
    }

    @Test
    public void fullPaletteAndWordSizedChunkLengthsAreNotByteSized() throws Exception {
        ByteArrayOutputStream out = rasterHeader(3, 3, 0, 0, 0);
        u16(out, 256);
        for (int i = 0; i < 256; i++) {
            u32(out, 0x80000000L + i);
        }
        out.write(1);
        byte[] chunk = new byte[65535];
        Arrays.fill(chunk, (byte) 0xA5);
        u16(out, chunk.length);
        out.write(chunk);
        u16(out, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("raster", value -> {
        }, BitmapRecordDecoder::raster);
        assertEquals(records(r, "palette").size(), 256);
        assertEquals(records(r, "chunks").get(0).get("byteCount"), 65535);
        assertEquals(r.get("compressedByteCount"), 65535L);
        assertEquals(((BinaryData) records(r, "chunks").get(0).get("data")).toByteArray(), chunk);
        assertEquals(a.readUI16(), 0xBEEF);
    }

    @Test
    public void futureRasterRevisionStopsBeforeItsUnprovenHeader() throws Exception {
        MfcArchive a = archive(new byte[]{4, 42});
        FlaRecord[] root = {null};
        try {
            a.readInline("raster", value -> root[0] = value, BitmapRecordDecoder::raster);
            fail("Expected future raster revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(root[0].get("revision"), 4);
            assertNull(root[0].get("formatCode"));
            assertEquals(a.readUI8(), 42);
        }
    }

    @DataProvider(name = "encodedCounts")
    public Object[][] encodedCounts() {
        return new Object[][]{{Integer.MIN_VALUE}, {-1}, {0}, {8}};
    }

    @Test(dataProvider = "encodedCounts")
    public void payloadSelectsEncodedImageOnlyForPositiveSignedCount(int count) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (count > 0) {
            out.write(PIXELS);
            words(out, 4);
        } else {
            out.write(raster(3, 3, 1).toByteArray());
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("payload", value -> {
        }, (in, payload) -> BitmapRecordDecoder.payload(in, payload, count));
        assertEquals(r.get("encodedByteCount"), count);
        if (count > 0) {
            assertEquals(((BinaryData) r.get("encodedData")).toByteArray(), PIXELS);
            assertEquals(r.get("boundsWords"), Arrays.asList(-250, -150, -50, 50));
            assertNull(r.get("raster"));
        } else {
            assertTrue(((FlaRecord) r.get("raster")).isComplete());
            assertNull(r.get("encodedData"));
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "components")
    public Object[][] components() {
        List<Object[]> cases = new ArrayList<>();
        for (int component = 5; component <= 8; component++) {
            for (int bits = 0; bits <= 4; bits++) {
                cases.add(new Object[]{component, bits});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "components")
    public void inlineDescriptorAndPayloadKeepTheComponentTailAligned(int revision, int bits) throws Exception {
        ByteArrayOutputStream out = component(revision, bits);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("component", value -> {
        }, DefaultFlaDecoders::component);
        assertTrue(r.isComplete());
        assertEquals(r.get("mediaPresent"), 0x80000000L);
        FlaRecord media = (FlaRecord) r.get("embeddedMedia");
        assertTrue(media.isComplete());
        assertEquals(media.get("revision"), bits);
        assertEquals(media.get("mediaId"), 42);
        FlaRecord payload = (FlaRecord) media.get("payload");
        assertTrue(payload.isComplete());
        if (bits >= 3) {
            assertEquals(((BinaryData) payload.get("encodedData")).toByteArray(), PIXELS);
        } else {
            assertEquals(inflate((FlaRecord) payload.get("raster")), PIXELS);
        }
        if (revision >= 6) {
            assertEquals(r.get("reserved"), 0x80000001L);
        }
        if (revision >= 7) {
            assertEquals(r.get("requiredMinimumASVersion"), 0xFFFFFFFFL);
        }
        if (revision >= 8) {
            assertTrue(((FlaRecord) r.get("livePreview11Movie")).isComplete());
        }
        assertEquals(a.getObjects().size(), 0);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "components")
    public void everyTruncatedEmbeddedMediaKeepsItsParentAndPartialRecords(int revision, int bits) throws Exception {
        byte[] bytes = component(revision, bits).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] root = {null};
            try {
                a.readInline("component", value -> root[0] = value, DefaultFlaDecoders::component);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known component must not become unsupported", ex);
            } catch (IOException expected) {
                assertFalse(root[0].isComplete());
                assertEquals(root[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @DataProvider(name = "historical")
    public Object[][] historical() {
        return new Object[][]{{0}, {1}, {2}, {3}};
    }

    @Test(dataProvider = "historical")
    public void historicalBitmapHasAnInlineRasterInsteadOfAMediaWord(int rasterRevision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(0);
        words(out, 6);
        out.write(raster(rasterRevision, 3, rasterRevision == 0 ? 0 : 1).toByteArray());
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicBitmap", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertNull(r.get("mediaId"));
        FlaRecord raster = (FlaRecord) r.get("raster");
        assertTrue(raster.isComplete());
        assertEquals(raster.get("revision"), rasterRevision);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void linkedPayloadStreamsDecodeAndKeepTheOriginalBytes() throws Exception {
        byte[] raster = raster(3, 3, 1).toByteArray();
        BinaryFlaDocument doc = linked(0, raster);
        FlaStream stream = doc.getStream("M 1 2");
        assertEquals(stream.getKind(), FlaStream.Kind.MEDIA);
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        assertEquals(stream.getData().toByteArray(), raster);
        assertEquals(stream.getUnparsedData().size(), 0);
        assertTrue(stream.getObjects().isEmpty());
        assertEquals(inflate((FlaRecord) stream.getRoot().get("raster")), PIXELS);
        assertSame(doc.getMedia().get(0).getStream(), stream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PIXELS);
        words(out, 4);
        doc = linked(8, out.toByteArray());
        stream = doc.getStream("M 1 2");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE);
        assertEquals(((BinaryData) stream.getRoot().get("encodedData")).toByteArray(), PIXELS);
    }

    @Test
    public void jpegFallbackAppliesOnlyToTheExactStandaloneStreamPrefixAndZeroCount() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] jpeg = {(byte) 255, (byte) 216, (byte) 255, (byte) 224, 12, 34, 56};
        out.write(jpeg);
        words(out, 4);
        BinaryFlaDocument doc = linked(0, out.toByteArray());
        FlaStream stream = doc.getStream("M 1 2");
        FlaRecord root = stream.getRoot();
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE);
        assertEquals(root.get("jpegLengthFallback"), true);
        assertEquals(root.get("encodedByteCount"), jpeg.length);
        assertEquals(doc.getMediaDescriptors().get(0).get("encodedByteCount"), 0);
        assertEquals(((BinaryData) root.get("encodedData")).toByteArray(), jpeg);
        doc = linked(jpeg.length, out.toByteArray());
        assertNull(doc.getStream("M 1 2").getRoot().get("jpegLengthFallback"));
        byte[] exif = out.toByteArray();
        exif[3] = (byte) 225;
        assertEquals(linked(0, exif).getStream("M 1 2").getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(linked(0, new byte[]{(byte) 255, (byte) 216, (byte) 255, (byte) 224}).getStream("M 1 2").getParseStatus(), FlaStream.ParseStatus.MALFORMED);
        MfcArchive a = archive(out.toByteArray());
        try {
            a.readInline("payload", value -> {
            }, (in, payload) -> BitmapRecordDecoder.payload(in, payload, 0));
            fail("Inline payload must not use stream-length fallback");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.getPosition(), 1);
        }
    }

    @Test
    public void malformedAndFuturePayloadsRetainDiagnosticOffsetsAndTrailingData() throws Exception {
        FlaStream stream = linked(8, new byte[]{1, 2}).getStream("M 1 2");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.MALFORMED);
        assertEquals(stream.getStopOffset(), 0);
        assertFalse(stream.getRoot().isComplete());
        byte[] bytes = raster(0, 5, 0).toByteArray();
        byte[] extra = Arrays.copyOf(bytes, bytes.length + 1);
        extra[bytes.length] = 42;
        stream = linked(0, extra).getStream("M 1 2");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(stream.getStopOffset(), bytes.length);
        assertEquals(stream.getUnparsedData().toByteArray(), new byte[]{42});
        assertTrue(((FlaRecord) stream.getRoot().get("raster")).isComplete());
        stream = linked(0, new byte[]{4, 42}).getStream("M 1 2");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(stream.getStopOffset(), 1);
        assertFalse(((FlaRecord) stream.getRoot().get("raster")).isComplete());
    }

    @DataProvider(name = "nativeComponents")
    public Object[][] nativeComponents() {
        return new Object[][]{{"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "nativeComponents")
    public void nativeComponentsContinuePastEmbeddedRastersIntoLaterDescriptors(String target) throws Exception {
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).read(Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla", target, "0025_components-todo.fla").toFile());
        int embedded = 0, swf = 0;
        for (FlaRecord r : doc.getContentsStream().getObjects()) {
            FlaRecord component = (FlaRecord) r.get("component");
            if (component != null && component.get("embeddedMedia") != null) {
                FlaRecord media = (FlaRecord) component.get("embeddedMedia");
                assertTrue(media.isComplete());
                FlaRecord payload = (FlaRecord) media.get("payload");
                assertTrue(payload.isComplete());
                FlaRecord raster = (FlaRecord) payload.get("raster");
                assertEquals((long) inflate(raster).length, raster.get("expectedByteCount"));
                embedded++;
            }
            if (r.getClassName().equals("CSwfPage")) {
                swf++;
                assertTrue(r.isComplete());
                assertTrue(((FlaRecord) r.get("metadata")).isComplete());
            }
        }
        assertTrue(embedded >= 2);
        assertTrue(swf > 0);
        assertTrue(doc.getIssues().isEmpty(), doc.getIssues().toString());
    }

    @Test
    public void nativeFlashOneBitmapsKeepBothInlineRastersAndTheirFollowingRecords() throws Exception {
        Path dir = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "flang", "f1", "0010_bitmaps");
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path f : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                streams.put(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).readStreams(streams);
        int bitmaps = 0;
        for (FlaStream page : doc.getStreams().values()) {
            if (page.getKind() != FlaStream.Kind.PAGE) {
                continue;
            }
            assertEquals(page.getParseStatus(), FlaStream.ParseStatus.COMPLETE, page.getIssues().toString());
            for (FlaRecord r : page.getObjects()) {
                if (r.getClassName().equals("CPicBitmap")) {
                    bitmaps++;
                    assertEquals(r.get("revision"), 0);
                    assertTrue(((FlaRecord) r.get("raster")).isComplete());
                    assertNull(r.get("mediaId"));
                }
            }
        }
        assertEquals(bitmaps, 2);
    }

    @DataProvider(name = "nativeBitmaps")
    public Object[][] nativeBitmaps() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "nativeBitmaps")
    public void nativeBitmapStreamsHaveExactPayloadBoundariesAndInflatableStoredChunks(String target) throws Exception {
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).read(Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla", target, "0013_bitmaps.fla").toFile());
        int bitmaps = 0;
        for (BinaryFlaDocument.Media media : doc.getMedia()) {
            if (!media.getDescriptor().getClassName().equals("CMediaBits")) {
                continue;
            }
            bitmaps++;
            FlaStream stream = media.getStream();
            assertNotNull(stream);
            assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
            assertEquals(stream.getRoot().getConsumedData().toByteArray(), stream.getData().toByteArray());
            assertEquals(stream.getUnparsedData().size(), 0);
            assertTrue(stream.getObjects().isEmpty());
            FlaRecord raster = (FlaRecord) stream.getRoot().get("raster");
            if (raster != null) {
                assertTrue(raster.isComplete());
                assertEquals((long) inflate(raster).length, raster.get("expectedByteCount"));
            } else {
                assertTrue(((BinaryData) stream.getRoot().get("encodedData")).size() > 0);
                assertNotNull(stream.getRoot().get("boundsWords"));
            }
        }
        assertTrue(bitmaps > 0);
    }

    private static ByteArrayOutputStream rasterHeader(int revision, int format, int stride, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        out.write(format);
        u16(out, stride);
        u16(out, width);
        u16(out, height);
        words(out, 4);
        if (revision >= 3) {
            out.write(0xFE);
        }
        return out;
    }

    private static ByteArrayOutputStream raster(int revision, int format, int flag) throws Exception {
        ByteArrayOutputStream out = rasterHeader(revision, format, 4, 2, 2);
        if (format < 4) {
            u16(out, 2);
            for (int entry = 0; entry < 2; entry++) {
                if (revision >= 3) {
                    out.write(new byte[]{0, 1, (byte) 128, (byte) 255});
                } else {
                    u16(out, 0x1234);
                    u16(out, 0xFEDC);
                    u16(out, 0x8001);
                }
            }
        }
        if (revision >= 2) {
            out.write(flag);
        }
        if (revision == 1 || (revision >= 2 && flag != 0)) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream zip = new DeflaterOutputStream(compressed)) {
                zip.write(PIXELS);
            }
            byte[] bytes = compressed.toByteArray();
            u16(out, 1);
            out.write(bytes, 0, 1);
            u16(out, bytes.length - 1);
            out.write(bytes, 1, bytes.length - 1);
            u16(out, 0);
        } else {
            out.write(PIXELS);
        }
        return out;
    }

    private static byte[] inflate(FlaRecord raster) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        for (FlaRecord chunk : records(raster, "chunks")) {
            compressed.write(((BinaryData) chunk.get("data")).toByteArray());
        }
        Inflater inflater = new Inflater();
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            inflater.setInput(compressed.toByteArray());
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                assertTrue(n > 0 || inflater.finished(), "Incomplete zlib data");
                pixels.write(buffer, 0, n);
            }
        } finally {
            inflater.end();
        }
        return pixels.toByteArray();
    }

    private static ByteArrayOutputStream bits(int revision, int count) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        string(out, "M 1 2", false);
        string(out, "bitmap", false);
        u16(out, 42);
        out.write(revision);
        if (revision < 2) {
            string(out, "legacy.png", false);
        }
        if (revision >= 1) {
            out.write(0xFE);
            out.write(0xA5);
        }
        if (revision >= 2) {
            out.write(0xCD);
        }
        if (revision >= 3) {
            u32(out, count);
        }
        if (revision >= 4) {
            out.write(0xAB);
        }
        return out;
    }

    private static ByteArrayOutputStream component(int revision, int bitmap) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        u32(out, 1);
        string(out, "component", true);
        u32(out, 2);
        u32(out, 3);
        u32(out, 4);
        string(out, "description", true);
        out.write(0);
        out.write(0);
        out.write(0);
        string(out, "extension", true);
        out.write(2);
        u32(out, 0);
        u32(out, 99);
        string(out, "description", true);
        u32(out, 0);
        u32(out, 0x80000000L);
        out.write(bits(bitmap, PIXELS.length).toByteArray());
        if (bitmap >= 3) {
            out.write(PIXELS);
            words(out, 4);
        } else {
            out.write(raster(3, 3, 1).toByteArray());
        }
        if (revision >= 6) {
            u32(out, 0x80000001L);
        }
        if (revision >= 7) {
            u32(out, 5);
            string(out, "tail", true);
            u32(out, 6);
            u32(out, 0xFFFFFFFFL);
        }
        if (revision >= 8) {
            out.write(0);
        }
        return out;
    }

    private static BinaryFlaDocument linked(int count, byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x12);
        u32(out, 0);
        for (int i = 0; i < 4; i++) {
            u16(out, 0);
        }
        u16(out, 0);
        u16(out, 0);
        out.write(object("CMediaBits", bits(3, count).toByteArray()));
        u16(out, 0);
        u16(out, 42);
        u16(out, 0);
        out.write(0);
        words(out, 4);
        u32(out, 0);
        out.write(new byte[5]);
        words(out, 4);
        out.write(new byte[4]);
        u32(out, 0);
        u16(out, 0);
        u16(out, 0);
        u32(out, 0);
        u32(out, 0);
        u32(out, 24 * 65536);
        out.write(new byte[3]);
        u32(out, 0);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", out.toByteArray());
        streams.put("M 1 2", payload);
        return new BinaryFlaReader(ANSI).readStreams(streams);
    }
}
