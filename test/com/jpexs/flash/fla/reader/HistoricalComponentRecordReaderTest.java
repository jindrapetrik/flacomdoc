package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class HistoricalComponentRecordReaderTest {

    @DataProvider(name = "layouts")
    public Object[][] layouts() {
        List<Object[]> cases = new ArrayList<>();
        for (int revision = 0; revision <= 8; revision++) {
            for (int movie = 0; movie <= 3; movie++) {
                for (int properties = 0; properties <= 2; properties++) {
                    cases.add(new Object[]{revision, movie, properties});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "layouts")
    public void readsIndependentRevisionsAndStopsAtTheFollowingField(int revision, int movie, int properties) throws Exception {
        byte[] payload = component(revision, movie, properties, 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(payload);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("component", value -> {
        }, DefaultFlaDecoders::component);
        assertTrue(r.isComplete());
        assertEquals(r.getConsumedData().toByteArray(), payload);
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("componentEnabled"), 0xFFFFFFFFL);
        assertEquals(r.get("tooltip"), "Příliš žluťoučký");
        assertEquals(r.get("customIconId"), 0x80000000L);
        assertEquals(r.get("parametersAreLocked"), 0xFFFFFFFFL);
        assertEquals(r.get("descriptionType"), 42L);
        assertEquals(r.get("actionScriptClass"), "description");
        List<FlaRecord> movies = records(r, "movies");
        assertEquals(movies.size(), 3);
        for (int i = 0; i < 3; i++) {
            FlaRecord entry = movies.get(i);
            int version = (movie + i) % 4;
            assertTrue(entry.isComplete());
            assertEquals(entry.get("revision"), version);
            if (version >= 1) {
                assertEquals(entry.get("name"), "movie");
                assertEquals(((BinaryData) entry.get("data")).toByteArray(), new byte[]{0, 1, (byte) 255});
            } else {
                assertNull(entry.get("data"));
            }
        }
        if (revision >= 1) {
            assertEquals(r.get("symbolDescription"), "extension");
            FlaRecord list = (FlaRecord) r.get("properties");
            assertTrue(list.isComplete());
            assertEquals(list.get("revision"), properties);
            FlaRecord property = records(list, "entries").get(0);
            assertEquals(property.get("name"), "property");
            if (properties == 0) {
                assertEquals(property.get("value"), "value");
            } else {
                assertEquals(records((FlaRecord) property.get("value"), "entries").get(0).get("value"), "value");
            }
            if (properties >= 2) {
                assertEquals(list.get("parametersAreNew"), 99L);
            }
        } else {
            assertNull(r.get("symbolDescription"));
            assertNull(r.get("properties"));
        }
        if (revision >= 2) {
            assertEquals(r.get("classPropertiesXml"), "<componentPackage></componentPackage>suffix");
        } else {
            assertNull(r.get("classPropertiesXml"));
        }
        if (revision >= 4) {
            assertEquals(r.get("taskCount"), 2);
            assertEquals(records(r, "tasks").size(), 2);
            assertEquals(records(r, "tasks").get(0).get("description"), "key");
            assertEquals(records(r, "tasks").get(1).get("script"), "value1");
        } else {
            assertNull(r.get("taskCount"));
            assertNull(r.get("tasks"));
        }
        if (revision >= 5) {
            assertEquals(r.get("mediaPresent"), 0L);
        } else {
            assertNull(r.get("mediaPresent"));
        }
        if (revision >= 6) {
            assertEquals(r.get("reserved"), 0x80000000L);
        }
        if (revision >= 7) {
            assertEquals(r.get("requiredMinimumPlayerVersionName"), "tail");
            assertEquals(r.get("requiredMinimumASVersion"), 33L);
        }
        if (revision >= 8) {
            assertTrue(((FlaRecord) r.get("livePreview11Movie")).isComplete());
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(a.getObjects().size(), 0);
    }

    @DataProvider(name = "revisions")
    public Object[][] revisions() {
        Object[][] cases = new Object[9][1];
        for (int i = 0; i <= 8; i++) {
            cases[i][0] = i;
        }
        return cases;
    }

    @Test(dataProvider = "revisions")
    public void everyTruncatedPrefixIsMalformedAndRetainsConsumedData(int revision) throws Exception {
        byte[] bytes = component(revision, 3, 2, 2);
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            List<FlaRecord> attached = new ArrayList<>();
            try {
                a.readInline("component", attached::add, DefaultFlaDecoders::component);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known layouts must not be unsupported", ex);
            } catch (IOException expected) {
                assertEquals(attached.size(), 1);
                FlaRecord r = attached.get(0);
                assertFalse(r.isComplete());
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test(dataProvider = "revisions")
    public void readsComponentsWithinBothDescriptorClasses(int revision) throws Exception {
        for (String className : new String[]{"CDocumentPage", "CSwfPage"}) {
            ByteArrayOutputStream out;
            if (className.equals("CDocumentPage")) {
                out = new ComponentReaderTest().descriptorPrefix(18);
            } else {
                out = new ByteArrayOutputStream();
                out.write(object(className, new byte[0]));
                out.write(3);
                string(out, "M 1 2", false);
                string(out, "clip", false);
                u16(out, 7);
                string(out, "source.swf", false);
                u32(out, 123);
            }
            out.write(component(revision, 1, 0, 1));
            if (className.equals("CSwfPage")) {
                out.write(1);
                string(out, "after component", false);
            }
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = a.readObject();
            assertTrue(r.isComplete());
            assertTrue(((FlaRecord) r.get("component")).isComplete());
            if (className.equals("CSwfPage")) {
                assertEquals(r.get("sourceName"), "after component");
            }
            assertEquals(a.getObjects().size(), 1);
            assertEquals(a.readUI16(), 0xBEEF);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void nonpositivePairCountsConsumeNoPairsAndPreserveSignedValue() throws Exception {
        for (int revision = 4; revision <= 8; revision++) {
            for (int count : new int[]{0, -1, Integer.MIN_VALUE}) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(component(revision, 0, 0, count));
                u16(out, 0xBEEF);
                MfcArchive a = archive(out.toByteArray());
                FlaRecord r = a.readInline("component", value -> {
                }, DefaultFlaDecoders::component);
                assertTrue(r.isComplete());
                assertEquals(r.get("taskCount"), count);
                assertEquals(((List<?>) r.get("tasks")).size(), 0);
                assertEquals(a.readUI16(), 0xBEEF);
            }
        }
    }

    @Test
    public void excessivePairCountsAreMalformedBeforeReadingEntries() throws Exception {
        for (int count : new int[]{1001, Integer.MAX_VALUE}) {
            ByteArrayOutputStream out = pairPrefix(4, count);
            for (int i = 0; i < 2200; i++) {
                out.write(0);
            }
            MfcArchive a = archive(out.toByteArray());
            List<FlaRecord> attached = new ArrayList<>();
            try {
                a.readInline("component", attached::add, DefaultFlaDecoders::component);
                fail("Expected count limit");
            } catch (UnsupportedFlaRecordException ex) {
                fail("Invalid counts are malformed", ex);
            } catch (IOException expected) {
                FlaRecord r = attached.get(0);
                assertEquals(r.get("taskCount"), count);
                assertTrue(((List<?>) r.get("tasks")).isEmpty());
                assertEquals(a.getRemaining(), 2200);
                assertFalse(r.isComplete());
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void truncatedTasksRetainTheCompletedTaskAndTheCurrentDescription() throws Exception {
        ByteArrayOutputStream out = pairPrefix(4, 2);
        string(out, "first", true);
        string(out, "value", true);
        string(out, "second", true);
        out.write(5);
        out.write('x');
        MfcArchive a = archive(out.toByteArray());
        List<FlaRecord> attached = new ArrayList<>();
        try {
            a.readInline("component", attached::add, DefaultFlaDecoders::component);
            fail("Expected truncated value");
        } catch (IOException expected) {
            List<FlaRecord> pairs = records(attached.get(0), "tasks");
            assertEquals(pairs.size(), 2);
            assertEquals(pairs.get(0).get("description"), "first");
            assertEquals(pairs.get(0).get("script"), "value");
            assertTrue(pairs.get(0).isComplete());
            assertEquals(pairs.get(1).get("description"), "second");
            assertNull(pairs.get(1).get("script"));
            assertFalse(pairs.get(1).isComplete());
            try {
                pairs.add(null);
                fail("Expected immutable pairs");
            } catch (UnsupportedOperationException immutable) {
            }
        }
    }

    @Test
    public void futureRevisionsStopAtTheirOwnRevisionByte() throws Exception {
        for (int revision : new int[]{9, 255}) {
            MfcArchive a = archive(new byte[]{(byte) revision, 42, 43});
            List<FlaRecord> attached = new ArrayList<>();
            try {
                a.readInline("component", attached::add, DefaultFlaDecoders::component);
                fail("Expected unsupported revision");
            } catch (UnsupportedFlaRecordException expected) {
                assertEquals(attached.get(0).get("revision"), revision);
                assertFalse(attached.get(0).isComplete());
                assertEquals(a.getPosition(), 1);
                assertEquals(a.readUI8(), 42);
            }
        }
    }

    private static byte[] component(int revision, int movie, int properties, int pairCount) throws IOException {
        ByteArrayOutputStream out = prefix(revision, movie, properties);
        if (revision >= 4) {
            u32(out, pairCount);
            for (int i = 0; i < Math.max(0, pairCount); i++) {
                string(out, "key", true);
                string(out, "value" + i, true);
            }
        }
        if (revision >= 5) {
            u32(out, 0);
        }
        if (revision >= 6) {
            u32(out, 0x80000000L);
        }
        if (revision >= 7) {
            u32(out, 31);
            string(out, "tail", true);
            u32(out, 32);
            u32(out, 33);
        }
        if (revision >= 8) {
            movie(out, 3);
        }
        return out.toByteArray();
    }

    private static ByteArrayOutputStream pairPrefix(int revision, int count) throws IOException {
        ByteArrayOutputStream out = prefix(revision, 0, 0);
        u32(out, count);
        return out;
    }

    private static ByteArrayOutputStream prefix(int revision, int movie, int properties) throws IOException {
        boolean unicode = revision >= 3;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        u32(out, 0xFFFFFFFFL);
        string(out, "Příliš žluťoučký", unicode);
        u32(out, 0x80000000L);
        u32(out, 0xFFFFFFFFL);
        u32(out, 42);
        string(out, "description", unicode);
        for (int i = 0; i < 3; i++) {
            movie(out, (movie + i) % 4);
        }
        if (revision >= 1) {
            string(out, "extension", unicode);
            out.write(properties);
            u32(out, 1);
            if (properties == 0) {
                u32(out, 2);
                string(out, "property", false);
                string(out, "value", false);
            } else {
                out.write(0);
                string(out, "property", false);
                out.write(0);
                u16(out, 0);
                u32(out, 1);
                u32(out, 0);
                string(out, "value", false);
            }
            if (properties >= 2) {
                u32(out, 99);
            }
        }
        if (revision >= 2) {
            string(out, "<componentPackage></componentPackage>suffix", unicode);
        }
        return out;
    }

    private static void movie(ByteArrayOutputStream out, int revision) throws IOException {
        out.write(revision);
        if (revision >= 1) {
            string(out, "movie", revision >= 3);
            u32(out, 3);
            out.write(new byte[]{0, 1, (byte) 255});
        }
        if (revision >= 2) {
            u32(out, 123);
        }
    }
}
