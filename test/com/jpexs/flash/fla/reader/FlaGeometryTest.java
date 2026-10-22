package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.FlaWriter;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class FlaGeometryTest {

    @DataProvider(name = "matrices")
    public Object[][] matrices() {
        return new Object[][]{
            {new int[]{65536, 0, 0, 65536, 0, 0}, 2.0, 3.0, 2.0, 3.0},
            {new int[]{65536, 0, 0, 65536, 25, -31}, 2.0, 3.0, 3.25, 1.45},
            {new int[]{0, 65536, -65536, 0, 0, 0}, 2.0, 3.0, -3.0, 2.0},
            {new int[]{131072, -32768, 16384, -65536, 25, -31}, 2.0, 3.0, 6.0, -5.55},
            {new int[]{-65536, 0, 0, 65536, 0, 0}, 2.0, 3.0, -2.0, 3.0},
            {new int[]{0, 0, 0, 0, 5, -5}, 2.0, 3.0, 0.25, -0.25},
            {new int[]{Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0}, 1.0, 1.0, -32768.0, 32767.99998474121}
        };
    }

    @Test(dataProvider = "matrices")
    public void signedAffineWordsMapToTheCorrectAxesAndUnits(int[] words, double x, double y, double expectedX, double expectedY) {
        FlaMatrix m = FlaMatrix.fromWords(words);
        double[] point = m.transformPixels(x, y);
        assertEquals(point[0], expectedX, 1e-12);
        assertEquals(point[1], expectedY, 1e-12);
        assertEquals(m.getWords(), words);
        assertEquals(m.getTxTwips(), words[4]);
        assertEquals(m.getTyTwips(), words[5]);
        assertEquals(m.getTxPixels(), words[4] / 20.0);
        assertEquals(m.getTyPixels(), words[5] / 20.0);
    }

    @Test
    public void boundsAreRangesPerAxisAndPreserveFractionalTwips() {
        FlaBounds b = FlaBounds.fromWords(-21, 100, -50, 51);
        assertTrue(b.isDefined());
        assertEquals(b.getXMinPixels(), Double.valueOf(-1.05));
        assertEquals(b.getXMaxPixels(), Double.valueOf(5));
        assertEquals(b.getYMinPixels(), Double.valueOf(-2.5));
        assertEquals(b.getYMaxPixels(), Double.valueOf(2.55));
        assertEquals(b.getWidthTwips(), Long.valueOf(121));
        assertEquals(b.getHeightTwips(), Long.valueOf(101));
        assertEquals(b.getWidthPixels(), Double.valueOf(6.05));
        assertEquals(b.getHeightPixels(), Double.valueOf(5.05));
        assertEquals(b.getWords(), new int[]{-21, 100, -50, 51});
    }

    @Test
    public void undefinedBoundsHaveNoDerivedSizeAndDoNotRequireFourSentinels() {
        for (int[] words : new int[][]{{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE}, {Integer.MIN_VALUE, 7, -9, 11}}) {
            FlaBounds b = FlaBounds.fromWords(words);
            assertFalse(b.isDefined());
            assertEquals(b.getWords(), words);
            assertEquals(b.getXMinTwips(), Integer.MIN_VALUE);
            assertNull(b.getWidthTwips());
            assertNull(b.getHeightTwips());
            assertNull(b.getWidthPixels());
            assertNull(b.getHeightPixels());
            assertNull(b.getXMinPixels());
            assertNull(b.getXMaxPixels());
            assertNull(b.getYMinPixels());
            assertNull(b.getYMaxPixels());
        }
        FlaBounds zero = FlaBounds.fromWords(0, 0, 0, 0);
        assertTrue(zero.isDefined());
        assertEquals(zero.getWidthTwips(), Long.valueOf(0));
    }

    @Test
    public void wideAndInvertedRangesAreRetainedWithoutSortingOrOverflow() {
        FlaBounds b = FlaBounds.fromWords(Integer.MIN_VALUE + 1, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertTrue(b.isDefined());
        assertEquals(b.getWidthTwips(), Long.valueOf(4294967294L));
        assertEquals(b.getHeightTwips(), Long.valueOf(4294967295L));
        b = FlaBounds.fromWords(100, -100, 300, -300);
        assertTrue(b.isDefined());
        assertEquals(b.getWidthTwips(), Long.valueOf(-200));
        assertEquals(b.getHeightTwips(), Long.valueOf(-600));
    }

    @Test
    public void centerSentinelAppliesToTheWholeTripleAndOtherWordsRemainRaw() {
        FlaCenterPoint3D p = FlaCenterPoint3D.fromWords(21, -41, 1);
        assertTrue(p.isDefined());
        assertEquals(p.getXPixels(), Double.valueOf(1.05));
        assertEquals(p.getYPixels(), Double.valueOf(-2.05));
        assertEquals(p.getZPixels(), Double.valueOf(0.05));
        assertEquals(p.getXTwips(), 21);
        assertEquals(p.getYTwips(), -41);
        assertEquals(p.getZTwips(), 1);
        p = FlaCenterPoint3D.fromWords(Integer.MIN_VALUE, -5, 17);
        assertFalse(p.isDefined());
        assertNull(p.getXPixels());
        assertNull(p.getYPixels());
        assertNull(p.getZPixels());
        assertEquals(p.getZTwips(), 17);
        p = FlaCenterPoint3D.fromWords(0, Integer.MIN_VALUE, 17);
        assertTrue(p.isDefined());
        assertNotNull(p.getYPixels());
    }

    @Test
    public void geometryViewsAreSnapshotsAndAllArraysAreDefensiveCopies() {
        int[] words = {65536, 2, 3, 65536, 5, 6};
        FlaMatrix m = FlaMatrix.fromWords(words);
        words[0] = 0;
        int[] returned = m.getWords();
        returned[0] = 0;
        assertEquals(m.getA(), 1.0);
        int[] rect = {1, 2, 3, 4};
        FlaBounds b = FlaBounds.fromWords(rect);
        rect[0] = Integer.MIN_VALUE;
        b.getWords()[0] = Integer.MIN_VALUE;
        assertTrue(b.isDefined());
        int[] center = {1, 2, 3};
        FlaCenterPoint3D p = FlaCenterPoint3D.fromWords(center);
        center[0] = Integer.MIN_VALUE;
        p.getWords()[0] = Integer.MIN_VALUE;
        assertTrue(p.isDefined());
        FlaRecord r = record();
        List<Integer> values = new ArrayList<>(Arrays.asList(65536, 0, 0, 65536, 20, 40));
        r.set("matrixWords", values);
        m = r.getMatrix();
        values.set(4, 200);
        assertEquals(m.getTxPixels(), 1.0);
        assertEquals(r.getMatrix().getTxPixels(), 10.0);
    }

    @Test
    public void genericAccessorsSelectOnlyCompleteIntegerFieldsAndNamedMorphMatrices() {
        FlaRecord r = record();
        assertNull(r.getMatrix());
        assertNull(r.getBounds());
        assertNull(r.getCenterPoint3D());
        r.set("matrixWords", Arrays.asList(1, 2, 3));
        assertNull(r.getMatrix());
        r.set("matrixWords", Arrays.asList(1, 2, 3, 4, 5, 6L));
        assertNull(r.getMatrix());
        r.set("matrixWords", Arrays.asList(1, 2, 3, 4, null, 6));
        assertNull(r.getMatrix());
        r.set("matrixWordsA", Arrays.asList(65536, 0, 0, 65536, 20, 40));
        assertEquals(r.getMatrix("matrixWordsA").getTyPixels(), 2.0);
        assertNull(r.getMatrix("matrixWordsB"));
        r.set("boundsWords", Arrays.asList(0, 100, 0, 200));
        assertEquals(r.getBounds().getHeightPixels(), Double.valueOf(10));
        r.set("bounds", Arrays.asList(0, 40, 0, 60));
        assertEquals(r.getBounds().getWidthPixels(), Double.valueOf(2));
        r.set("bounds", Collections.singletonList(1));
        assertNull(r.getBounds());
        assertNotNull(r.getBounds("boundsWords"));
        r.set("centerPoint3D", Arrays.asList(0, 1, 2));
        assertTrue(r.getCenterPoint3D().isDefined());
        r.set("centerPoint3D", Arrays.asList(0, 1));
        assertNull(r.getCenterPoint3D());
    }

    @Test
    public void invalidFactoryAritiesDoNotBecomeIdentityMatricesOrZeroCoordinates() {
        for (int[] words : new int[][]{null, new int[0], new int[2], new int[5], new int[7]}) {
            try {
                FlaMatrix.fromWords(words);
                fail("Expected six words");
            } catch (IllegalArgumentException expected) {
            }
        }
        for (int[] words : new int[][]{null, new int[0], new int[3], new int[5]}) {
            try {
                FlaBounds.fromWords(words);
                fail("Expected four words");
            } catch (IllegalArgumentException expected) {
            }
        }
        for (int[] words : new int[][]{null, new int[0], new int[2], new int[4]}) {
            try {
                FlaCenterPoint3D.fromWords(words);
                fail("Expected three words");
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @DataProvider(name = "symbols")
    public Object[][] symbols() {
        Object[][] result = new Object[24][1];
        for (int i = 0; i < 24; i++) {
            result[i][0] = i;
        }
        return result;
    }

    @Test(dataProvider = "symbols")
    public void parsedSymbolsExposeSemanticViewsWithoutChangingArchiveBytesOrReferences(int revision) throws Exception {
        byte[] bytes = object("CPicSymbol", new InstanceRecordReaderTest().symbol(revision, 0, new byte[0]).toByteArray());
        MfcArchive a = archive(bytes);
        FlaRecord r = a.readObject();
        assertEquals(r.getConsumedData().toByteArray(), bytes);
        FlaMatrix m = r.getMatrix();
        assertNotNull(m);
        assertEquals(m.getWords(), new int[]{-250, -150, -50, 50, 150, 250});
        assertEquals(m.getTxPixels(), 7.5);
        assertEquals(m.getTyPixels(), 12.5);
        assertEquals(a.getObjects().size(), 1);
        if (revision >= 21) {
            assertFalse(r.getCenterPoint3D().isDefined());
            assertEquals(r.getCenterPoint3D().getZTwips(), 17);
        } else {
            assertNull(r.getCenterPoint3D());
        }
    }

    @DataProvider(name = "targets")
    public Object[][] targets() {
        List<Object[]> cases = new ArrayList<>();
        for (FlaFormatVersion v : FlaFormatVersion.values()) {
            cases.add(new Object[]{v});
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "targets")
    public void converterMatricesDecodeToTheSameAffineMeaningForEverySaveTarget(FlaFormatVersion target) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FlaWriter writer = new FlaWriter(out, target, "windows-1250");
        writer.writeMatrix(2, -0.5, 0.25, -1, 1.25, -1.55);
        MfcArchive a = archive(out.toByteArray());
        int[] words = new int[6];
        for (int i = 0; i < 6; i++) {
            words[i] = a.readI32();
        }
        assertEquals(words, new int[]{131072, -32768, 16384, -65536, 25, -31});
        assertEquals(FlaMatrix.fromWords(words).transformPixels(2, 3), new double[]{6, -5.55});
    }

    @Test
    public void documentStageDimensionsRespectTheUndefinedBoundsMarker() throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        BinaryFlaDocument d = new BinaryFlaReader(ANSI).readStreams(streams);
        assertNull(d.getStageBounds());
        assertNull(d.getWidthTwips());
        d.getContents().set("boundsWords", Arrays.asList(10, 110, 20, 220));
        assertEquals(d.getWidthTwips(), Long.valueOf(100));
        assertEquals(d.getHeightTwips(), Long.valueOf(200));
        d.getContents().set("boundsWords", Arrays.asList(Integer.MIN_VALUE, 7, 8, 9));
        assertNotNull(d.getStageBounds());
        assertFalse(d.getStageBounds().isDefined());
        assertNull(d.getWidthTwips());
        assertNull(d.getHeightTwips());
    }

    @Test
    public void nativeInstancesHaveNontrivialViewsWithoutChangingStoredFields() throws Exception {
        Path dir = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla/cs4/0002_shapes");
        if (!Files.exists(dir)) {
            throw new AssertionError("Missing native rectangles fixture: " + dir);
        }
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            for (java.util.Iterator<Path> it = files.filter(Files::isRegularFile).iterator(); it.hasNext();) {
                Path f = it.next();
                streams.put(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        }
        BinaryFlaDocument d = new BinaryFlaReader(ANSI).readStreams(streams);
        int matrices = 0;
        for (FlaStream stream : d.getStreams().values()) {
            for (FlaRecord r : stream.getObjects()) {
                if (r.getMatrix() != null) {
                    matrices++;
                    assertEquals(r.getMatrix().getWords(), integers(r, "matrixWords"));
                }
            }
            assertEquals(stream.getData().toByteArray(), streams.get(stream.getPath()));
        }
        assertTrue(matrices > 0);
        assertTrue(d.getStageBounds().isDefined());
        assertEquals(d.getWidthTwips(), d.getStageBounds().getWidthTwips());
    }

    @Test
    public void nativeRasterAndEncodedImageBoundsUseTheSameTwipRanges() throws Exception {
        BinaryFlaDocument d = nativeDocument("0013_bitmaps");
        int images = 0;
        int[][] sizes = {{32, 64}, {220, 209}, {400, 335}};
        for (BinaryFlaDocument.Media media : d.getMedia()) {
            FlaRecord r = media.getStream().getRoot();
            if (r.get("raster") instanceof FlaRecord) {
                r = (FlaRecord) r.get("raster");
            }
            FlaBounds b = r.getBounds();
            assertNotNull(b);
            assertTrue(b.isDefined());
            assertEquals(b.getWidthPixels(), Double.valueOf(sizes[images][0]));
            assertEquals(b.getHeightPixels(), Double.valueOf(sizes[images][1]));
            if (r.get("width") != null) {
                assertEquals(b.getWidthPixels(), Double.valueOf(((Number) r.get("width")).doubleValue()));
            }
            images++;
        }
        assertEquals(images, 3);
    }

    @Test
    public void native3DCentersMatchTheCorrespondingXflPixelCoordinates() throws Exception {
        BinaryFlaDocument d = nativeDocument("0026_3d_rotations-cs4");
        List<List<Double>> centers = new ArrayList<>();
        for (FlaStream stream : d.getStreams().values()) {
            for (FlaRecord r : stream.getObjects()) {
                FlaCenterPoint3D p = r.getCenterPoint3D();
                if (p != null && p.isDefined()) {
                    centers.add(Arrays.asList(p.getXPixels(), p.getYPixels(), p.getZPixels()));
                }
            }
        }
        assertEquals(centers.size(), 2);
        assertTrue(centers.contains(Arrays.asList(203.25, 183.25, 0.0)), centers.toString());
        assertTrue(centers.contains(Arrays.asList(424.9, 191.25, 0.0)), centers.toString());
    }

    private static BinaryFlaDocument nativeDocument(String name) throws Exception {
        Path dir = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla/cs4/" + name);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            for (java.util.Iterator<Path> it = files.filter(Files::isRegularFile).iterator(); it.hasNext();) {
                Path f = it.next();
                streams.put(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        }
        return new BinaryFlaReader(ANSI).readStreams(streams);
    }

    private static int[] integers(FlaRecord r, String key) {
        List<?> words = (List<?>) r.get(key);
        int[] result = new int[words.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = (Integer) words.get(i);
        }
        return result;
    }

    private static FlaRecord record() {
        return new FlaRecord("test", 1, 0, 0, 0, new BinaryData(new byte[0]));
    }
}
