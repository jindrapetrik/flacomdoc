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

public class OptionalRecordReaderTest {

    @DataProvider(name = "contexts")
    public Object[][] contexts() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 12; v <= 23; v++) {
            cases.add(new Object[]{"symbol", v});
        }
        for (int v = 6; v <= 7; v++) {
            cases.add(new Object[]{"library", v});
        }
        cases.add(new Object[]{"bitmap", 2});
        return cases.toArray(new Object[0][]);
    }

    @DataProvider(name = "presence")
    public Object[][] presence() {
        List<Object[]> cases = new ArrayList<>();
        for (Object[] context : contexts()) {
            for (int present : new int[]{0, 1, 255}) {
                cases.add(new Object[]{context[0], context[1], present});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "presence")
    public void directPersistentDataKeepsLaterFieldsAndMfcReferencesAligned(String kind, int revision, int present) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(context(kind, revision, present, values()));
        ByteArrayOutputStream child = new ByteArrayOutputStream();
        base(child);
        out.write(object("CPicObj", child.toByteArray()));
        u16(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] holder = {null};
        FlaRecord r = read(a, kind, holder);
        assertTrue(r.isComplete());
        assertEquals(r.get("optionalRecordPresent"), present);
        if (present == 0) {
            assertNull(r.get("persistentData"));
        } else {
            assertValues((FlaRecord) r.get("persistentData"));
        }
        if (kind.equals("symbol") && revision >= 23) {
            assertEquals(r.get("isVisible"), 0xFFFFFFFFL);
        }
        FlaRecord next = a.readObject();
        assertEquals(next.getClassName(), "CPicObj");
        assertTrue(next.isComplete());
        assertEquals(next.getObjectId(), kind.equals("library") ? 2 : 4);
        assertEquals(a.getObjects().size(), kind.equals("library") ? 1 : 2);
        assertSame(a.readObject(), kind.equals("library") ? next : r);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "presence")
    public void everyTruncatedOptionalContextRetainsItsConsumedPrefix(String kind, int revision, int present) throws Exception {
        byte[] bytes = context(kind, revision, present, values());
        int start = kind.equals("library") ? 0 : 6 + className(kind).length();
        for (int length = start; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] root = {null};
            try {
                read(a, kind, root);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known optional context must not become unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(root[0]);
                assertFalse(root[0].isComplete());
                assertEquals(root[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test(dataProvider = "contexts")
    public void emptyAndNegativeContainersDoNotConsumeTheFollowingContextFields(String kind, int revision) throws Exception {
        for (int count : new int[]{0, -1, Integer.MIN_VALUE}) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            u32(data, 0xFFFFFFFFL);
            u32(data, count);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(context(kind, revision, 128, data.toByteArray()));
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = read(a, kind, new FlaRecord[1]);
            FlaRecord persistent = (FlaRecord) r.get("persistentData");
            assertEquals(persistent.get("header"), 0xFFFFFFFFL);
            assertEquals(persistent.get("count"), count);
            assertTrue(records(persistent, "entries").isEmpty());
            assertTrue(r.isComplete());
            assertEquals(a.readUI16(), 0xBEEF);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test(dataProvider = "contexts")
    public void unknownValueTypeKeepsTheEntryAndStopsBeforeItsPayload(String kind, int revision) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        u32(data, 1);
        u32(data, 1);
        data.write(2);
        data.write(42);
        MfcArchive a = archive(context(kind, revision, 1, data.toByteArray()));
        FlaRecord[] root = {null};
        try {
            read(a, kind, root);
            fail("Expected unsupported type");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord persistent = (FlaRecord) root[0].get("persistentData");
            FlaRecord value = records(persistent, "entries").get(0);
            assertFalse(root[0].isComplete());
            assertFalse(persistent.isComplete());
            assertFalse(value.isComplete());
            assertEquals(value.get("type"), 2);
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test(dataProvider = "contexts")
    public void countsByteLengthsAndNestedDepthRemainBoundedInsideOptionalContexts(String kind, int revision) throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            u32(data, 1);
            u32(data, mode == 0 ? 1001 : 1);
            if (mode == 1) {
                data.write(7);
                string(data, "data", true);
                u32(data, 0xFFFFFFFFL);
            } else if (mode == 2) {
                data.write(0);
                string(data, "name", true);
                u32(data, 123);
            }
            byte[] bytes = context(kind, revision, 1, data.toByteArray());
            MfcArchive a = mode == 2
                    ? new MfcArchive(new BinaryData(bytes), "test", new FlaReadOptions(ANSI, 1024 * 1024, 1024 * 1024, 1000, 2), DefaultFlaDecoders.create(), 0) : archive(bytes);
            FlaRecord[] root = {null};
            try {
                read(a, kind, root);
                fail("Expected limit");
            } catch (UnsupportedFlaRecordException ex) {
                fail("Invalid count or depth must be malformed", ex);
            } catch (IOException expected) {
                assertNotNull(root[0]);
                assertFalse(root[0].isComplete());
                assertNotNull(root[0].get("persistentData"));
                if (mode == 2) {
                    assertTrue(expected.getMessage().contains("nesting"));
                }
            }
        }
    }

    @DataProvider(name = "shared")
    public Object[][] shared() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 0; v <= 2; v++) {
            for (long present : new long[]{1, 0x80000000L, 0xFFFFFFFFL}) {
                cases.add(new Object[]{v, present});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "shared")
    public void swfSharedRecordReplacesTheStandaloneExportString(int revision, long present) throws Exception {
        ByteArrayOutputStream out = SwfRecordReaderTest.media(5, 8, present);
        out.write(shared(revision));
        ByteArrayOutputStream next = new ByteArrayOutputStream();
        base(next);
        out.write(object("CPicObj", next.toByteArray()));
        u16(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CSwfPage", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("sharedRecordPresent"), present);
        FlaRecord shared = (FlaRecord) r.get("sharedLibrary");
        assertTrue(shared.isComplete());
        assertEquals(shared.get("revision"), revision);
        assertEquals(r.get("exportName"), "Shared\u03a9");
        assertEquals(((BinaryData) shared.get("data")).toByteArray(), new byte[]{1, 2, 3});
        if (revision >= 1) {
            assertEquals(shared.get("names"), Arrays.asList("duplicate", "duplicate"));
        } else {
            assertNull(shared.get("names"));
        }
        assertEquals(shared.get("legacyNames") != null, revision == 1);
        FlaRecord sibling = a.readObject();
        assertEquals(sibling.getClassName(), "CPicObj");
        assertEquals(sibling.getObjectId(), 4);
        assertSame(a.readObject(), r);
        assertEquals(a.getObjects().size(), 2);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "shared")
    public void everyTruncatedSharedSwfDescriptorRetainsItsNestedRecord(int revision, long present) throws Exception {
        ByteArrayOutputStream prefix = SwfRecordReaderTest.media(5, 8, present);
        int childOffset = 6 + "CSwfPage".length() + prefix.size();
        prefix.write(shared(revision));
        byte[] bytes = object("CSwfPage", prefix.toByteArray());
        for (int length = childOffset; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known shared record must not become unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertNotNull(r.get("sharedLibrary"));
                assertFalse(((FlaRecord) r.get("sharedLibrary")).isComplete());
                assertNull(r.get("exportName"));
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void partialTypedValuesRemainVisibleInThePublicPageModel() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        u32(data, 1);
        u32(data, 1);
        data.write(2);
        data.write(new byte[]{42, 43, 44});
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.write(1);
        page.write(object("CPicPage", new byte[]{1, 0}));
        // CPicPage's child pointer is the symbol; no child terminator is reached on failure.
        page.write(context("symbol", 12, 255, data.toByteArray()));
        byte[] bytes = page.toByteArray();
        java.util.Map<String, byte[]> streams = new java.util.LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Page 1", bytes);
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).readStreams(streams);
        FlaStream stream = doc.getStream("Page 1");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(stream.getIssues().get(0).getKind(), FlaParseIssue.Kind.UNSUPPORTED);
        FlaRecord symbol = stream.getRoot().getChildren().get(0);
        FlaRecord persistent = (FlaRecord) symbol.get("persistentData");
        assertEquals(records(persistent, "entries").get(0).get("type"), 2);
        assertEquals(stream.getData().toByteArray(), bytes);
        assertEquals(bytes[stream.getStopOffset()], 42);
        assertFalse(symbol.isComplete());
        assertFalse(stream.getRoot().isComplete());
    }

    @Test
    public void nativeInverseKinematicsKeepsAllNamedValuesAndFinishesItsPage() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla", "cs4", "0023_inverse_kinematics-cs4-todo.fla");
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).read(path.toFile());
        assertTrue(doc.getIssues().isEmpty(), doc.getIssues().toString());
        int symbols = 0;
        for (FlaStream stream : doc.getStreams().values()) {
            if (stream.getKind() != FlaStream.Kind.PAGE) {
                continue;
            }
            assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE);
            for (FlaRecord r : stream.getObjects()) {
                if (!r.getClassName().equals("CPicSymbol") || r.get("persistentData") == null) {
                    continue;
                }
                symbols++;
                assertEquals(r.get("revision"), 22);
                assertTrue(r.isComplete());
                assertNotNull(r.get("animationFrameSettings"));
                FlaRecord data = (FlaRecord) r.get("persistentData");
                assertTrue(data.isComplete());
                assertEquals(data.get("count"), 15);
                List<FlaRecord> values = records(data, "entries");
                assertEquals(values.get(0).get("name"), "lsd_frame_data");
                assertEquals(values.get(0).get("type"), 1);
                assertEquals(values.get(0).get("values"), Arrays.asList(0, 9, 19));
                boolean xml = false, coordinates = false;
                for (FlaRecord value : values) {
                    assertTrue(value.isComplete());
                    if (value.get("name").equals("_ikTreeXML")) {
                        assertEquals(value.get("type"), 5);
                        assertTrue(((String) value.get("value")).startsWith("<_ikTreeStates>"));
                        xml = true;
                    }
                    if (value.get("name").equals("lsd_xdata0")) {
                        assertEquals(value.get("type"), 4);
                        assertEquals(value.get("count"), 7L);
                        coordinates = true;
                    }
                }
                assertTrue(xml);
                assertTrue(coordinates);
            }
        }
        assertEquals(symbols, 1);
    }

    @Test
    public void inheritedMediaMetadataContinuesAfterItsPersistentData() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(5);
        string(out, "M 1 2", false);
        string(out, "media", true);
        u16(out, 7);
        string(out, "source", true);
        u32(out, 123);
        out.write(context("library", 7, 255, values()));
        u32(out, 0xFEDCBA98L);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CMediaElem", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertValues((FlaRecord) ((FlaRecord) r.get("libraryMetadata")).get("persistentData"));
        assertEquals(r.get("sourcePlatform"), 0xFEDCBA98L);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    private static FlaRecord read(MfcArchive a, String kind, FlaRecord[] root) throws Exception {
        if (kind.equals("library")) {
            return a.readInline("libraryMetadata", r -> root[0] = r, DefaultFlaDecoders::libraryMetadata);
        }
        try {
            return a.readObject();
        } finally {
            if (!a.getObjects().isEmpty()) {
                root[0] = a.getObjects().get(0);
            }
        }
    }

    private static String className(String kind) {
        return kind.equals("symbol") ? "CPicSymbol" : "CPicBitmap";
    }

    private static byte[] context(String kind, int revision, int present, byte[] persistent) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (kind.equals("symbol")) {
            return object("CPicSymbol", new InstanceRecordReaderTest().symbol(revision, present, persistent).toByteArray());
        }
        if (kind.equals("bitmap")) {
            base(out);
            out.write(revision);
            words(out, 6);
            u16(out, 0xFEDC);
        } else {
            u32(out, revision);
            u32(out, 0);
            u32(out, 0);
            u32(out, 0xFFFFFFFFL);
            out.write(0);
            u32(out, 0);
            string(out, "identifier", true);
            string(out, "url", false);
        }
        out.write(present);
        if (present != 0) {
            out.write(persistent);
        }
        return kind.equals("library") ? out.toByteArray() : object("CPicBitmap", out.toByteArray());
    }

    private static byte[] values() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 6);
        for (int type : new int[]{0, 1, 3, 4, 5, 7}) {
            out.write(type);
            string(out, "same\u03a9", true);
            switch (type) {
                case 0:
                    u32(out, Integer.MIN_VALUE);
                    break;
                case 1:
                    u32(out, 2);
                    u32(out, -1);
                    u32(out, 123);
                    break;
                case 3:
                    f64(out, -0.0);
                    break;
                case 4:
                    u32(out, 2);
                    f64(out, Double.NEGATIVE_INFINITY);
                    f64(out, 1.25);
                    break;
                case 5:
                    string(out, "value\u03a9", true);
                    break;
                case 7:
                    u32(out, 3);
                    out.write(new byte[]{0, (byte) 255, 42});
                    break;
                default:
                    throw new AssertionError();
            }
        }
        return out.toByteArray();
    }

    private static void assertValues(FlaRecord data) {
        assertTrue(data.isComplete());
        assertEquals(data.get("count"), 6);
        List<FlaRecord> entries = records(data, "entries");
        assertEquals(entries.size(), 6);
        for (FlaRecord entry : entries) {
            assertTrue(entry.isComplete());
            assertEquals(entry.get("name"), "same\u03a9");
        }
        assertEquals(entries.get(0).get("value"), Integer.MIN_VALUE);
        assertEquals(entries.get(1).get("values"), Arrays.asList(-1, 123));
        assertEquals(entries.get(2).get("value"), -0.0);
        assertEquals(entries.get(3).get("values"), Arrays.asList(Double.NEGATIVE_INFINITY, 1.25));
        assertEquals(entries.get(4).get("value"), "value\u03a9");
        assertEquals(((BinaryData) entries.get(5).get("data")).toByteArray(), new byte[]{0, (byte) 255, 42});
    }

    private static byte[] shared(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        string(out, "Shared\u03a9", true);
        u32(out, 3);
        out.write(new byte[]{1, 2, 3});
        if (revision >= 1) {
            u32(out, 2);
            string(out, "duplicate", false);
            string(out, "duplicate", true);
        }
        if (revision == 1) {
            u32(out, 1);
            string(out, "legacy", true);
        }
        return out.toByteArray();
    }
}
