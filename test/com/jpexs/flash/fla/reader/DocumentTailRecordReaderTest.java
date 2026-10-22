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

public class DocumentTailRecordReaderTest {

    @DataProvider(name = "tails")
    public Object[][] tails() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 0x1C; v <= 0x4A; v++) {
            for (int branch = 0; branch < 3; branch++) {
                cases.add(new Object[]{v, branch});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "tails")
    public void allDocumentGatesKeepFollowingFieldsAligned(int version, int branch) throws Exception {
        ByteArrayOutputStream out = tail(version, branch);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("tail", value -> {
        }, (in, record) -> ContentsRecordDecoder.tail(in, record, version));
        assertTrue(r.isComplete());
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(r.get("fontLibrary") != null, version >= 0x1D);
        assertEquals(r.get("sharedLibraryUrl") != null, version >= 0x1E);
        assertEquals(r.get("gridSpacingYFixed16Twips"), version >= 0x1F ? 0x80000001L : null);
        assertEquals(r.get("templateDescription"), version >= 0x22 ? "a" : null);
        assertEquals(r.get("templateFilename"), version >= 0x22 ? "b" : null);
        assertEquals(r.get("previewFrame"), version >= 0x23 ? 0xFFFFFFFFL : null);
        assertEquals(r.get("nextMediaIdentifier"), version >= 0x27 ? 0xFFFFFFFFL : null);
        assertEquals(r.get("snapAlignBorder"), version >= 0x2E ? 4 : null);
        assertEquals(r.get("tabOrderMode"), version >= 0x33 ? 2L : null);
        assertEquals(r.get("exportComponentSchemasToSwf"), version >= 0x38 ? 0xFFFFFFFFL : null);
        assertEquals(r.get("mobileSettingsXml"), version >= 0x3C ? "x" : null);
        assertEquals(r.get("languageStringReplaceMethod"), version >= 0x3D ? 0xFFFFFFFFL : null);
        assertEquals(r.get("timelineHeight"), version >= 0x3F ? 0xFFFFFFFFL : null);
        Number labelWidth = null;
        if (version >= 0x48) {
            labelWidth = -1.5f;
        } else if (version >= 0x3F) {
            labelWidth = -1;
        }
        assertEquals(r.get("timelineLabelWidth"), labelWidth);
        assertEquals(r.get("accessibility") != null, version >= 0x26);
        assertEquals(r.get("persistentDataPresent") != null, version >= 0x32);
        if (version >= 0x32) {
            assertEquals(r.get("persistentData") != null, branch != 0);
        }
        if (version >= 0x35 && branch != 0) {
            FlaRecord table = (FlaRecord) r.get("languageSettings");
            assertEquals(table.get("availableLanguageCount"), branch == 1 ? 102 : -1);
            assertEquals(table.get("languageUrl"), "s1");
            assertEquals(table.get("languageAutoDetect"), 0xFFFFFFFFL);
            assertEquals(table.get("languageDefault"), "s2");
            assertEquals(((List<?>) table.get("availableLanguages")).size(), branch == 1 ? 101 : 0);
        }
        if (version >= 0x36) {
            assertEquals(r.get("languageForStage") != null, branch == 1);
        }
        if (version >= 0x3E && branch != 0) {
            FlaRecord binary = (FlaRecord) r.get("postProcessor");
            assertEquals(binary.get("guidData1"), 0xFFFFFFFFL);
            assertEquals(binary.get("guidData2"), 65535);
            assertEquals(binary.get("guidData3"), 32768);
            assertEquals(((BinaryData) binary.get("guidData4")).toByteArray(), new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
            assertEquals(binary.get("name"), "binary");
            assertEquals(binary.get("byteCount"), branch == 1 ? 3 : -1);
            assertEquals(binary.get("data") != null, branch == 1);
            assertEquals(((BinaryData) binary.get("guidData4")).size(), 8);
        }
        assertEquals(r.get("discardedLegacyDword") != null, version == 0x41);
        assertEquals(r.get("sharedLibraries") != null, version >= 0x42);
        if (version >= 0x43) {
            FlaRecord identity = (FlaRecord) r.get("writerIdentity");
            assertEquals(identity.get("byteCount"), branch == 0 ? 0 : branch == 1 ? 256 : 257);
            assertEquals(identity.get("data") != null, branch != 2);
        }
        assertEquals(r.get("viewAngle3D"), version >= 0x45 ? -123.25 : null);
        if (version >= 0x46) {
            assertEquals(r.get("vanishingPointTwips"), Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE));
        }
        assertEquals(r.get("adjustViewAngle3D"), version >= 0x47 ? 0xFFFFFFFFL : null);
    }

    @Test(dataProvider = "tails")
    public void everyTruncatedDocumentTailRetainsItsDecodedPrefix(int version, int branch) throws Exception {
        byte[] bytes = tail(version, branch).toByteArray();
        truncated(bytes, (in, r) -> ContentsRecordDecoder.tail(in, r, version));
    }

    @DataProvider(name = "fonts")
    public Object[][] fonts() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 0; v <= 3; v++) {
            for (int style = 2; style <= 15; style++) {
                cases.add(new Object[]{v, style});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "fonts")
    public void fontRecordsHaveIndependentStyleRevisionsAndNoMfcEnvelope(int revision, int style) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 0xFFFFFFFFL);
        u32(out, 2);
        for (int i = 0; i < 2; i++) {
            font(out, revision, style, i + 1);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("fontLibrary", value -> {
        }, DocumentTailRecordDecoder::fonts);
        assertTrue(r.isComplete());
        List<FlaRecord> fonts = records(r, "entries");
        assertEquals(fonts.size(), 2);
        assertTrue(a.getObjects().isEmpty());
        for (int i = 0; i < 2; i++) {
            FlaRecord f = fonts.get(i);
            assertEquals(f.get("revision"), revision);
            assertEquals(f.get("name"), revision >= 2 ? "Font \u03a9" : "Font");
            assertEquals(f.get("fontId"), i + 1);
            assertEquals(f.get("sourceTime"), 0xFEDCBA98L);
            assertEquals(f.get("fontFlags"), revision >= 3 ? 0x80000001L : null);
            FlaRecord st = (FlaRecord) f.get("style");
            assertEquals(st.get("revision"), style);
            assertEquals(st.get("pitchAndFamily"), 0x31);
            assertEquals(st.get("charSet"), 0xCC);
            assertTrue(((FlaRecord) f.get("libraryMetadata")).isComplete());
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "fonts")
    public void everyTruncatedFontRecordRemainsAttached(int revision, int style) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        font(out, revision, style, 65535);
        truncated(out.toByteArray(), DocumentTailRecordDecoder::font);
    }

    @DataProvider(name = "shared")
    public Object[][] shared() {
        return new Object[][]{{0}, {1}, {2}};
    }

    @Test(dataProvider = "shared")
    public void sharedLibraryRevisionOneAloneHasTheSecondStringList(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        shared(out, revision);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("shared", value -> {
        }, DocumentTailRecordDecoder::sharedLibrary);
        assertTrue(r.isComplete());
        assertEquals(r.get("name"), "library \u03a9");
        assertEquals(((BinaryData) r.get("data")).toByteArray(), new byte[]{1, 2, 3});
        if (revision >= 1) {
            assertEquals(r.get("names"), Arrays.asList("same", "same"));
        } else {
            assertNull(r.get("names"));
        }
        assertEquals(r.get("legacyNames") != null, revision == 1);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "shared")
    public void everyTruncatedSharedLibraryRecordRetainsItsPrefix(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        shared(out, revision);
        truncated(out.toByteArray(), DocumentTailRecordDecoder::sharedLibrary);
    }

    @Test
    public void persistentValuesRetainTypesDuplicatesAndSignedNumbers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        persistent(out);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("persistent", value -> {
        }, DocumentTailRecordDecoder::persistentData);
        List<FlaRecord> e = records(r, "entries");
        assertTrue(r.isComplete());
        assertEquals(e.size(), 6);
        for (FlaRecord entry : e) {
            assertEquals(entry.get("name"), "same \u03a9");
        }
        assertEquals(e.get(0).get("value"), Integer.MIN_VALUE);
        assertEquals(e.get(1).get("values"), Arrays.asList(-1, 123));
        assertEquals(e.get(2).get("value"), -1.25);
        assertEquals(e.get(3).get("values"), Arrays.asList(-0.0, Double.POSITIVE_INFINITY));
        assertEquals(e.get(4).get("value"), "text \u03a9");
        assertEquals(((BinaryData) e.get(5).get("data")).toByteArray(), new byte[]{0, (byte) 255});
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertTrue(a.getObjects().isEmpty());
        truncated(persistentBytes(), DocumentTailRecordDecoder::persistentData);
    }

    @Test
    public void emptyArraysAndNonpositiveContainerCountsConsumeNoInventedPayload() throws Exception {
        for (int count : new int[]{0, -1, Integer.MIN_VALUE}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            u32(out, 1);
            u32(out, count);
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = a.readInline("persistent", value -> {
            }, DocumentTailRecordDecoder::persistentData);
            assertEquals(r.get("count"), count);
            assertTrue(records(r, "entries").isEmpty());
            assertEquals(a.readUI16(), 0xBEEF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 3);
        for (int type : new int[]{1, 4, 7}) {
            out.write(type);
            string(out, "", false);
            u32(out, 0);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("persistent", value -> {
        }, DocumentTailRecordDecoder::persistentData);
        assertTrue(r.isComplete());
        assertEquals(a.readUI16(), 0xBEEF);
    }

    @Test
    public void futureRevisionsAndTypesStopAtTheFirstUnprovenByte() throws Exception {
        for (FlaRecordDecoder decoder : new FlaRecordDecoder[]{DocumentTailRecordDecoder::font, DocumentTailRecordDecoder::sharedLibrary, DocumentTailRecordDecoder::sharedLibraries}) {
            MfcArchive a = archive(new byte[]{99, 42});
            FlaRecord[] r = {null};
            try {
                a.readInline("future", v -> r[0] = v, decoder);
                fail("Expected future revision");
            } catch (UnsupportedFlaRecordException expected) {
                assertEquals(r[0].get("revision"), 99);
                assertFalse(r[0].isComplete());
                assertEquals(a.readUI8(), 42);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 1);
        out.write(2);
        out.write(42);
        out.write(43);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] r = {null};
        try {
            a.readInline("persistent", v -> r[0] = v, DocumentTailRecordDecoder::persistentData);
            fail("Expected unknown type");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(records(r[0], "entries").get(0).get("type"), 2);
            assertFalse(r[0].isComplete());
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void futureDirectCollectionEntriesRemainAttachedEvenWithoutAnyPayload() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FlaRecordDecoder decoder;
            if (kind == 0) {
                u32(out, 0);
                u32(out, 1);
                out.write(99);
                decoder = DocumentTailRecordDecoder::fonts;
            } else if (kind == 1) {
                out.write(0);
                u32(out, 1);
                out.write(99);
                decoder = DocumentTailRecordDecoder::sharedLibraries;
            } else {
                u32(out, 1);
                u32(out, 1);
                out.write(2);
                decoder = DocumentTailRecordDecoder::persistentData;
            }
            MfcArchive a = archive(out.toByteArray());
            FlaRecord[] root = {null};
            try {
                a.readInline("collection", v -> root[0] = v, decoder);
                fail("Expected unknown entry");
            } catch (UnsupportedFlaRecordException expected) {
                FlaRecord entry = records(root[0], "entries").get(0);
                assertFalse(entry.isComplete());
                assertEquals(entry.get(kind == 2 ? "type" : "revision"), kind == 2 ? 2 : 99);
                assertEquals(a.getRemaining(), 0);
            }
        }
    }

    @Test
    public void excessiveCountsAndByteLengthsFailWithoutAllocatingPayloads() throws Exception {
        for (int kind = 0; kind < 5; kind++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FlaRecordDecoder decoder;
            if (kind == 0) {
                u32(out, 0);
                u32(out, 1001);
                decoder = DocumentTailRecordDecoder::fonts;
            } else if (kind == 1) {
                out.write(0);
                u32(out, 1001);
                decoder = DocumentTailRecordDecoder::sharedLibraries;
            } else if (kind == 2) {
                out.write(0);
                string(out, "", true);
                u32(out, 0xFFFFFFFFL);
                decoder = DocumentTailRecordDecoder::sharedLibrary;
            } else {
                u32(out, 1);
                u32(out, kind == 3 ? 1001 : 1);
                if (kind == 4) {
                    out.write(4);
                    string(out, "", true);
                    u32(out, 0xFFFFFFFFL);
                }
                decoder = DocumentTailRecordDecoder::persistentData;
            }
            MfcArchive a = archive(out.toByteArray());
            try {
                a.readInline("limit", v -> {
                }, decoder);
                fail("Expected read limit");
            } catch (UnsupportedFlaRecordException ex) {
                fail("Invalid length must be malformed", ex);
            } catch (IOException expected) {
                assertEquals(a.getRemaining(), 0);
            }
        }
    }

    private static void truncated(byte[] bytes, FlaRecordDecoder decoder) throws Exception {
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] r = {null};
            try {
                a.readInline("record", value -> r[0] = value, decoder);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known prefix must not become unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(r[0]);
                assertFalse(r[0].isComplete());
                assertEquals(r[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    private static void font(ByteArrayOutputStream out, int revision, int style, int id) throws Exception {
        out.write(revision);
        string(out, revision >= 2 ? "Font \u03a9" : "Font", revision >= 2);
        u16(out, id);
        u32(out, 0xFEDCBA98L);
        new TextRecordReaderTest().style(out, style, "Test", 2);
        u32(out, 0);
        u32(out, 0);
        if (revision >= 3) {
            u32(out, 0x80000001L);
        }
    }

    private static void shared(ByteArrayOutputStream out, int revision) throws Exception {
        out.write(revision);
        string(out, "library \u03a9", true);
        u32(out, 3);
        out.write(new byte[]{1, 2, 3});
        if (revision >= 1) {
            u32(out, 2);
            string(out, "same", false);
            string(out, "same", true);
        }
        if (revision == 1) {
            u32(out, 1);
            string(out, "legacy", false);
        }
    }

    private static byte[] persistentBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        persistent(out);
        return out.toByteArray();
    }

    private static void persistent(ByteArrayOutputStream out) throws Exception {
        u32(out, 1);
        u32(out, 6);
        for (int type : new int[]{0, 1, 3, 4, 5, 7}) {
            out.write(type);
            string(out, "same \u03a9", true);
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
                    f64(out, -1.25);
                    break;
                case 4:
                    u32(out, 2);
                    f64(out, -0.0);
                    f64(out, Double.POSITIVE_INFINITY);
                    break;
                case 5:
                    string(out, "text \u03a9", true);
                    break;
                case 7:
                    u32(out, 2);
                    out.write(0);
                    out.write(255);
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    private static ByteArrayOutputStream tail(int v, int branch) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean unicode = v >= 0x2F;
        if (v >= 0x1D) {
            u32(out, 0);
            u32(out, branch == 0 ? 0 : 1);
            if (branch != 0) {
                font(out, 3, 15, 65535);
            }
        }
        if (v >= 0x1E) {
            string(out, "name", unicode);
        }
        if (v >= 0x1F) {
            out.write(0xFE);
            out.write(0x80);
            u32(out, -1);
            u32(out, 0);
            u32(out, 0x80000001L);
        }
        if (v >= 0x22) {
            string(out, "a", unicode);
            string(out, "b", unicode);
        }
        if (v >= 0x23) {
            u32(out, -1);
        }
        if (v >= 0x24) {
            u32(out, -1);
            u32(out, 7);
        }
        if (v >= 0x26) {
            access(out, 2, unicode);
        }
        if (v >= 0x27) {
            u32(out, -1);
        }
        if (v >= 0x2E) {
            out.write(new byte[]{1, 2, 3, 4});
            words(out, 3);
        }
        if (v >= 0x30) {
            out.write(255);
        }
        if (v >= 0x32) {
            out.write(branch == 0 ? 0 : 255);
            if (branch != 0) {
                persistent(out);
            }
        }
        if (v >= 0x33) {
            u32(out, 2);
        }
        if (v >= 0x34) {
            u32(out, -1);
        }
        if (v >= 0x35) {
            u32(out, branch);
            if (branch != 0) {
                u32(out, branch == 1 ? 102 : -1);
                string(out, "s1", true);
                u32(out, -1);
                string(out, "s2", true);
                if (branch == 1) {
                    for (int i = 0; i < 101; i++) {
                        string(out, "s" + i, true);
                    }
                }
            }
        }
        if (v >= 0x36) {
            u32(out, branch);
            if (branch == 1) {
                string(out, "present", true);
            }
        }
        if (v >= 0x38) {
            u32(out, -1);
        }
        if (v >= 0x3A) {
            string(out, "extension", unicode);
        }
        if (v >= 0x3C) {
            string(out, "x", unicode);
        }
        if (v >= 0x3D) {
            u32(out, -1);
        }
        if (v >= 0x3E) {
            u32(out, branch);
            if (branch != 0) {
                u32(out, -1);
                u16(out, 65535);
                u16(out, 32768);
                out.write(new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
                string(out, "binary", unicode);
                u32(out, branch == 1 ? 3 : -1);
                if (branch == 1) {
                    out.write(new byte[]{7, 8, 9});
                }
            }
        }
        if (v >= 0x3F) {
            u32(out, -1);
            if (v <= 0x47) {
                u32(out, -1);
            } else {
                f32(out, -1.5f);
            }
        }
        if (v == 0x41) {
            u32(out, -1);
        }
        if (v >= 0x42) {
            out.write(0);
            u32(out, branch == 0 ? 0 : 3);
            if (branch != 0) {
                for (int rev = 0; rev <= 2; rev++) {
                    shared(out, rev);
                }
            }
        }
        if (v >= 0x43) {
            for (int i = 0; i < 4; i++) {
                u16(out, i * 10000);
            }
            int n = branch == 0 ? 0 : branch == 1 ? 256 : 257;
            u16(out, n);
            if (n <= 256) {
                out.write(new byte[n]);
            }
        }
        if (v >= 0x45) {
            f64(out, -123.25);
        }
        if (v >= 0x46) {
            u32(out, Integer.MIN_VALUE);
            u32(out, Integer.MAX_VALUE);
        }
        if (v >= 0x47) {
            u32(out, -1);
        }
        return out;
    }
}
