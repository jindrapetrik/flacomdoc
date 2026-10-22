package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.TimelineConverter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class MorphRecordReaderTest {

    @DataProvider(name = "revisions")
    public Object[][] revisions() {
        return new Object[][]{{0}, {1}, {2}};
    }

    @Test(dataProvider = "revisions")
    public void morphHasItsOwnMatricesCountedObjectsAndModeZeroStyles(int revision) throws Exception {
        ByteArrayOutputStream out = shape(revision);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicMorphShape", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), revision);
        assertNull(r.get("baseRevision"));
        assertTrue(r.getChildren().isEmpty());
        assertEquals(r.get("matrixWordsA"), Arrays.asList(-250, -150, -50, 50, 150, 250));
        assertEquals(r.get("matrixWordsB"), Arrays.asList(Integer.MIN_VALUE, -1, 0, 1, 256, Integer.MAX_VALUE));
        assertEquals(r.get("extensionValue1"), revision == 2 ? 0x80000001L : null);
        assertEquals(r.get("acceleration"), revision == 2 ? 0xFFFFFFFFL : null);
        FlaRecord segment = records(r, "segments").get(0);
        assertTrue(segment.isComplete());
        assertEquals(segment.get("strokeIndex1"), -1);
        assertEquals(segment.get("strokeIndex2"), Integer.MIN_VALUE);
        assertEquals(segment.get("fillIndex1"), 5);
        assertEquals(segment.get("fillIndex2"), -2);
        assertEquals(segment.get("startPointA"), Arrays.asList(-250, -150));
        assertEquals(segment.get("startPointB"), Arrays.asList(0, 1));
        FlaRecord curve = records(segment, "curves").get(0);
        assertTrue(curve.isComplete());
        assertEquals(curve.get("controlPointA"), Arrays.asList(-250, -150));
        assertEquals(curve.get("anchorPointA"), Arrays.asList(-50, 50));
        assertEquals(curve.get("controlPointB"), Arrays.asList(150, 250));
        assertEquals(curve.get("anchorPointB"), Arrays.asList(350, 450));
        assertEquals(curve.get("lineFlags"), 0x80000001L);
        assertNull(curve.get("revision"));
        FlaRecord hint = records(r, "hints").get(0);
        assertTrue(hint.isComplete());
        assertEquals(hint.get("pointA"), Arrays.asList(-250, -150));
        assertEquals(hint.get("pointB"), Arrays.asList(-50, 50));
        assertEquals(hint.get("index"), 0xFFFFFFFFL);
        assertNull(hint.get("revision"));
        List<FlaRecord> fills = records(r, "fills");
        assertEquals(fills.size(), 4);
        assertEquals(fills.get(0).get("packedValue"), 0xFEDCBA98L);
        assertEquals(fills.get(1).get("stopCount"), 2);
        assertNull(fills.get(1).get("focalPoint"));
        assertEquals(records(fills.get(1), "stops").get(1).get("colorRgba"), 0x80000001L);
        assertEquals(fills.get(2).get("bitmapMediaId"), 0xFEDC);
        assertEquals(fills.get(3).get("extensionValue"), 0x80000000L);
        assertEquals(fills.get(3).get("extensionWords"), Arrays.asList(0, 1, 32768, 65535));
        FlaRecord stroke = records(r, "strokes").get(0);
        assertEquals(stroke.get("width"), 65535);
        assertEquals(stroke.get("styleWord1"), 0xFEDC);
        assertEquals(stroke.get("styleWord2"), 0x8007);
        assertNull(stroke.get("paint"));
        assertEquals(a.getObjects().size(), 4);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "revisions")
    public void everyTruncatedMorphKeepsItsPartiallyDecodedObjects(int revision) throws Exception {
        byte[] bytes = object("CPicMorphShape", shape(revision).toByteArray());
        int header = 6 + "CPicMorphShape".length();
        for (int length = header; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known layout must not become unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
                if (a.getObjects().size() > 1) {
                    assertSame(records(r, "segments").get(0), a.getObjects().get(1));
                }
                if (a.getObjects().size() > 2) {
                    assertSame(records(a.getObjects().get(1), "curves").get(0), a.getObjects().get(2));
                }
            }
        }
    }

    @DataProvider(name = "countHeaders")
    public Object[][] countHeaders() {
        return new Object[][]{{false}, {true}};
    }

    @Test(dataProvider = "countHeaders")
    public void countedListsPreserveNullSharedClassAndCircularPointers(boolean extended) throws Exception {
        ByteArrayOutputStream out = prefix(2);
        count(out, 5, extended);
        u16(out, 0); // Null is an element, not the end of this list.
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        words(segment, 8);
        count(segment, 1, extended);
        u16(segment, 2);
        out.write(object("CMorphSegment", segment.toByteArray())); // class 3, object 4
        u16(out, 4);
        u16(out, 0x8003);
        words(out, 8);
        u16(out, 0); // new object 5 of the same class
        u16(out, 2); // root object shared by this list and the nested curves list
        count(out, 0, extended);
        u16(out, 0);
        u16(out, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicMorphShape", out.toByteArray()));
        FlaRecord r = a.readObject();
        List<FlaRecord> segments = records(r, "segments");
        assertEquals(segments.size(), 5);
        assertNull(segments.get(0));
        assertSame(segments.get(1), segments.get(2));
        assertNotSame(segments.get(1), segments.get(3));
        assertSame(segments.get(4), r);
        assertSame(records(segments.get(1), "curves").get(0), r);
        assertEquals(a.getObjects().size(), 3);
        assertTrue(records(r, "hints").isEmpty());
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void unsupportedNestedObjectRemainsAttachedToTheCountedList() throws Exception {
        ByteArrayOutputStream out = prefix(2);
        u16(out, 1);
        out.write(object("FutureMorph", new byte[]{42}));
        MfcArchive a = archive(object("CPicMorphShape", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected unsupported class");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord r = a.getObjects().get(0);
            FlaRecord child = records(r, "segments").get(0);
            assertSame(child, a.getObjects().get(1));
            assertFalse(r.isComplete());
            assertFalse(child.isComplete());
            assertEquals(a.readUI8(), 42);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void futureMorphRevisionDoesNotConsumeAnAssumedMatrix() throws Exception {
        MfcArchive a = archive(object("CPicMorphShape", new byte[]{3, 42}));
        try {
            a.readObject();
            fail("Expected unsupported revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.getObjects().get(0).get("revision"), 3);
            assertNull(a.getObjects().get(0).get("matrixWordsA"));
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void countedListRejectsOversizedCountBeforeReadingElements() throws Exception {
        ByteArrayOutputStream out = prefix(2);
        u16(out, 65535);
        u32(out, 1001);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicMorphShape", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected count limit");
        } catch (IOException expected) {
            assertFalse(a.getObjects().get(0).isComplete());
            assertEquals(a.readUI16(), 0xBEEF);
        }
    }

    @DataProvider(name = "targets")
    public Object[][] targets() {
        List<Object[]> cases = new ArrayList<>();
        for (FlaFormatVersion v : FlaFormatVersion.values()) {
            if (v.ordinal() >= FlaFormatVersion.F3.ordinal()) {
                cases.add(new Object[]{v});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "targets")
    public void converterMorphKeepsFollowingFrameAndLayerFieldsAligned(FlaFormatVersion version) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TimelineConverter(version, "windows-1250", "test").convert(xml("<DOMTimeline><layers><DOMLayer name='after morph'><frames><DOMFrame index='0' duration='3' tweenType='shape' shapeTweenBlend='angular'><elements/>"
                + "<MorphShape><morphSegments><MorphSegment startPointA='-1.5, 2.25' startPointB='3, -4' strokeIndex1='-1' fillIndex1='2'>"
                + "<MorphCurves controlPointA='1, 2' anchorPointA='3, 4' controlPointB='5, 6' anchorPointB='7, 8' isLine='true'/>"
                + "</MorphSegment></morphSegments></MorphShape></DOMFrame></frames></DOMLayer></layers></DOMTimeline>"), xml("<DOMDocument/>"), out, null);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Timeline", out.toByteArray());
        FlaStream stream = new BinaryFlaReader(ANSI).readStreams(streams).getStream("Timeline");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        FlaRecord morph = null;
        for (FlaRecord candidate : stream.getObjects()) {
            if (candidate.getClassName().equals("CPicMorphShape")) {
                morph = candidate;
                break;
            }
        }
        assertNotNull(morph);
        assertTrue(morph.isComplete());
        assertEquals(morph.get("revision"), 2);
        FlaRecord segment = records(morph, "segments").get(0);
        assertEquals(segment.get("startPointA"), Arrays.asList(-384, 576));
        assertEquals(segment.get("startPointB"), Arrays.asList(768, -1024));
        assertEquals(segment.get("fillIndex1"), 2);
        FlaRecord curve = records(segment, "curves").get(0);
        assertEquals(curve.get("anchorPointB"), Arrays.asList(1792, 2048));
        assertEquals(curve.get("lineFlags"), 1L);
        FlaRecord layer = stream.getRoot().getChildren().get(0);
        assertEquals(layer.get("name"), "after morph");
        assertEquals(layer.getChildren().get(0).get("duration"), 3);
    }

    @DataProvider(name = "referenceMorphs")
    public Object[][] referenceMorphs() {
        return new Object[][]{{"f5", "0016_shapetween-todo"}, {"mx", "0016_shapetween-todo"}, {"mx2004", "0016_shapetween"}, {"f8", "0016_shapetween"}, {"cs3", "0016_shapetween"}, {"cs4", "0016_shapetween"}};
    }

    @Test(dataProvider = "referenceMorphs")
    public void nativeReferenceMorphPagesDecodeToTheirExactEnd(String target, String name) throws Exception {
        BinaryFlaDocument document = new BinaryFlaReader(ANSI).read(Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla", target, name + ".fla").toFile());
        int morphs = 0;
        for (FlaStream page : document.getStreams().values()) {
            if (page.getKind() != FlaStream.Kind.PAGE) {
                continue;
            }
            assertEquals(page.getParseStatus(), FlaStream.ParseStatus.COMPLETE, page.getPath() + ": " + page.getIssues());
            for (FlaRecord r : page.getObjects()) {
                if (r.getClassName().equals("CPicMorphShape")) {
                    morphs++;
                    assertTrue(r.isComplete());
                    assertFalse(records(r, "segments").isEmpty());
                    for (FlaRecord segment : records(r, "segments")) {
                        assertTrue(segment.isComplete());
                        for (FlaRecord curve : records(segment, "curves")) {
                            assertTrue(curve.isComplete());
                        }
                    }
                }
            }
        }
        assertTrue(morphs > 0, "Expected native morph records");
    }

    private static ByteArrayOutputStream prefix(int revision) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        words(out, 6);
        for (int n : new int[]{Integer.MIN_VALUE, -1, 0, 1, 256, Integer.MAX_VALUE}) {
            u32(out, n);
        }
        if (revision >= 2) {
            u32(out, 0x80000001L);
            u32(out, 0xFFFFFFFFL);
        }
        return out;
    }

    private static ByteArrayOutputStream shape(int revision) throws IOException {
        ByteArrayOutputStream out = prefix(revision);
        u16(out, 1);
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        u32(segment, -1);
        u32(segment, Integer.MIN_VALUE);
        u32(segment, 5);
        u32(segment, -2);
        words(segment, 2);
        u32(segment, 0);
        u32(segment, 1);
        u16(segment, 1);
        ByteArrayOutputStream curve = new ByteArrayOutputStream();
        words(curve, 8);
        u32(curve, 0x80000001L);
        segment.write(object("CMorphCurve", curve.toByteArray()));
        out.write(object("CMorphSegment", segment.toByteArray()));
        u16(out, 1);
        ByteArrayOutputStream hint = new ByteArrayOutputStream();
        words(hint, 4);
        u32(hint, 0xFFFFFFFFL);
        out.write(object("CMorphHintItem", hint.toByteArray()));
        u16(out, 4);
        paint(out, 0);
        paint(out, 0x70);
        words(out, 6);
        out.write(2);
        out.write(0);
        u32(out, 0xFEDCBA98L);
        out.write(255);
        u32(out, 0x80000001L);
        paint(out, 0x40);
        words(out, 6);
        u16(out, 0xFEDC);
        paint(out, 0x60);
        words(out, 6);
        u32(out, 0x80000000L);
        for (int n : new int[]{0, 1, 32768, 65535}) {
            u16(out, n);
        }
        u16(out, 1);
        u32(out, 0xFEDCBA98L);
        u16(out, 65535);
        u16(out, 0xFEDC);
        u16(out, 0x8007);
        return out;
    }

    private static void paint(ByteArrayOutputStream out, int flags) {
        u32(out, 0xFEDCBA98L);
        out.write(flags);
        out.write(0xA5);
    }

    private static void count(ByteArrayOutputStream out, int count, boolean extended) {
        if (extended) {
            u16(out, 65535);
            u32(out, count);
        } else {
            u16(out, count);
        }
    }

    private static Element xml(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }
}
