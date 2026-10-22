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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class ContentsRecordReaderTest {

    @Test
    public void packedPrinterSettingsPreserveIndependentUnsignedBytesAndSignedTwips() throws Exception {
        for (int[] values : new int[][]{{0, 0, 3, 180}, {1, 1, 128, 5760},
        {255, 128, 255, Integer.MIN_VALUE}, {2, 3, 0, Integer.MAX_VALUE}}) {
            BinaryFlaDocument doc = printerDocument(printerContents(0x10, values));
            assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
            FlaRecord r = doc.getContents();
            assertEquals(r.get("printAllFrames"), values[0]);
            assertEquals(r.get("printLabelPages"), values[1]);
            assertEquals(r.get("printFrameCountAcrossPage"), values[2]);
            assertEquals(r.get("printStoryboardMarginTwips"), values[3]);
            assertNull(r.get("flags1105"));
            assertNull(r.get("value1108"));
            assertEquals(r.get("printMarginLeftTwips"), 411);
            assertEquals(r.get("printMarginRightTwips"), 822);
            assertEquals(r.get("printMarginTopTwips"), 1233);
            assertEquals(r.get("printMarginBottomTwips"), -1644);
            assertEquals(r.get("printCenterHorizontal"), 128);
            assertEquals(r.get("printCenterVertical"), 0);
            assertEquals(r.get("printLayout"), 3);
            assertEquals(r.get("printDisablePostscript"), 255);
            assertEquals(r.get("printScaleFixed16"), 81920);
            assertNull(r.get("viewBoundsWords"));
            assertEquals(r.get("frameRateWord"), 0x1E00L);
            assertEquals(r.get("nextSceneId"), 12);
            assertEquals(r.get("nextItemTag"), 0xCAFE);
            assertNull(r.get("sceneCounter"));
            assertNull(r.get("sceneSelectionValue"));
            assertEquals(r.get("libraryCounter"), 34);
            assertEquals(doc.getContentsStream().getUnparsedData().size(), 0);
        }
    }

    @Test
    public void olderContentsDoNotInventPrinterLayoutFields() throws Exception {
        BinaryFlaDocument doc = printerDocument(printerContents(0x0F, new int[]{0, 0, 3, 180}));
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
        for (String field : new String[]{"printAllFrames", "printLabelPages", "printFrameCountAcrossPage", "printStoryboardMarginTwips"}) {
            assertNull(doc.getContents().get(field));
        }
    }

    @Test
    public void truncatedPrinterLayoutIsReportedAsMalformed() throws Exception {
        byte[] data = printerContents(0x10, new int[]{1, 1, 4, 725});
        for (int missing = 1; missing <= 7; missing++) {
            BinaryFlaDocument doc = printerDocument(Arrays.copyOf(data, data.length - missing));
            assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.MALFORMED, "missing=" + missing);
        }
    }

    private static BinaryFlaDocument printerDocument(byte[] contents) throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", contents);
        return new BinaryFlaReader(ANSI).readStreams(streams);
    }

    private static byte[] printerContents(int revision, int[] printer) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        u32(out, 0); // No OLE items.
        u16(out, 0); // No scenes.
        u16(out, 12); // Next scene ID, independent of the scene-list size.
        u16(out, 0xCAFE); // Next item tag, independent of the selected scene.
        u16(out, 0); // Null selected scene.
        u16(out, 0); // No library items.
        u16(out, 34);
        out.write(0); // Grid display mode.
        words(out, 4); // Bounds.
        u32(out, 0x10000);
        out.write(new byte[]{0, 1, 0, 0, 0}); // Preview/ruler and playback/printer options.
        for (int value : new int[]{411, 822, 1233, -1644}) {
            u32(out, value); // Print margins: left, right, top, bottom.
        }
        out.write(new byte[]{(byte) 128, 0, 3, (byte) 255});
        u32(out, 81920); // 125% print scale in 16.16.
        u16(out, 0); // No printer mode blob.
        u16(out, 0); // No printer names blob.
        u32(out, 0xFFFFFFFFL);
        u32(out, 0xFFCCCCCCL);
        u32(out, 0x1E00);
        if (revision >= 0x10) {
            out.write(printer[0]);
            out.write(printer[1]);
            out.write(printer[2]);
            u32(out, printer[3]);
        }
        return out.toByteArray();
    }

    @DataProvider(name = "versions")
    public Object[][] versions() {
        List<Object[]> cases = new ArrayList<>();
        for (int version = 0x12; version <= 0x4A; version++) {
            for (boolean extended : new boolean[]{false, true}) {
                cases.add(new Object[]{version, extended});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "versions")
    public void documentGatesKeepProfilesMapsPalettesFoldersAndScalarFieldsAligned(int version, boolean extended) throws Exception {
        ByteArrayOutputStream out = extensions(version, extended, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        readExtensions(a, root, version);
        FlaRecord r = root[0];
        assertTrue(r.isComplete());
        FlaRecord settings = (FlaRecord) r.get("publishSettings");
        if (version < 0x13) {
            assertNull(settings);
        } else {
            assertTrue(settings.isComplete());
            assertEquals(settings.get("trailingValue1"), 0xFFFFFFFFL);
            assertEquals(settings.get("trailingString1"), "first");
            assertEquals(settings.get("trailingString2"), text(version));
            assertEquals(settings.get("trailingValue2"), 0x80000001L);
            if (version < 0x2C) {
                assertEquals(records((FlaRecord) settings.get("legacyProperties"), "entries").size(), 2);
                assertNull(settings.get("profileCount"));
            } else {
                assertEquals(settings.get("profileCount"), 2);
                assertEquals(records(settings, "profiles").size(), 2);
                assertNotSame(records(settings, "profiles").get(0), records(settings, "profiles").get(1));
            }
            assertEquals(settings.get("documentProperties") != null, version >= 0x2D);
        }
        assertEquals(r.get("extensionProperties") != null, version >= 0x15);
        if (version >= 0x16) {
            assertTrue(((FlaRecord) r.get("swatches")).isComplete());
            assertTrue(((FlaRecord) r.get("extendedSwatches")).isComplete());
            assertEquals(records((FlaRecord) r.get("swatches"), "colors").get(0).getSchema(), 0);
        }
        if (version >= 0x18) {
            FlaRecord view = (FlaRecord) r.get("libraryView");
            assertTrue(view.isComplete());
            assertEquals(view.get("value3"), 0xFEDCBA98L);
        }
        if (version >= 0x19) {
            FlaRecord folders = (FlaRecord) r.get("libraryFolders");
            assertEquals(folders.get("header"), 0x80000000L);
            assertEquals(folders.get("count"), 2L);
            List<FlaRecord> entries = records(folders, "entries");
            assertEquals(entries.get(0).get("name"), "folder \u03a9");
            assertEquals(entries.get(1).get("legacyItemId"), 0xFFFFFFFFL);
            assertTrue(folders.isComplete());
            assertTrue(((FlaRecord) entries.get(1).get("libraryMetadata")).isComplete());
        }
        if (version >= 0x1A) {
            assertEquals(records(r, "profileObjects").size(), version < 0x2C ? 1 : 2);
            FlaRecord entry = records(records(r, "profileObjects").get(0), "entries").get(0);
            FlaRecord audio = (FlaRecord) entry.get("value");
            assertEquals(audio.getClassName(), "CQTAudioSettings");
            assertEquals(((BinaryData) audio.get("data")).toByteArray(), new byte[]{12, 34, 56});
            assertTrue(audio.isComplete());
        }
        assertEquals(r.get("documentObjects") != null, version >= 0x2D);
        assertEquals(r.get("nextSymbolIdentifier"), version >= 0x1B ? 0xFEDC : null);
        if (version >= 0x1C) {
            assertEquals(r.get("guidesColorRgba"), 0xFFFFFFFFL);
            assertEquals(r.get("guidesVisible"), 0xA5);
            assertEquals(r.get("guidesLocked"), 0xFE);
            assertEquals(r.get("guidesSnapTo"), 0xCD);
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "versions")
    public void everyTruncatedKnownExtensionPrefixRetainsItsDecodedFields(int version, boolean extended) throws Exception {
        byte[] bytes = extensions(version, extended, 2).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] root = {null};
            try {
                a.readInline("extensions", value -> root[0] = value, (in, r) -> ContentsRecordDecoder.settings(in, r, version));
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known prefix must not become unsupported at " + length, ex);
            } catch (IOException expected) {
                assertNotNull(root[0]);
                assertFalse(root[0].isComplete());
                assertEquals(root[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void modernZeroProfilesDoNotInventAProfileObjectMap() throws Exception {
        for (int version : new int[]{0x2C, 0x2D, 0x2E, 0x2F, 0x4A}) {
            ByteArrayOutputStream out = extensions(version, false, 0);
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord[] r = {null};
            readExtensions(a, r, version);
            assertEquals(((FlaRecord) r[0].get("publishSettings")).get("profileCount"), 0);
            assertTrue(records(r[0], "profileObjects").isEmpty());
            assertEquals(a.readUI16(), 0xBEEF);
        }
    }

    @Test
    public void stringAndObjectMapsRetainDuplicateKeysNullsAndSharedIdentity() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        count(out, 3, true);
        string(out, "same", true);
        u16(out, 0);
        string(out, "same", true);
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        u32(audio, 0);
        out.write(object("CQTAudioSettings", audio.toByteArray()));
        string(out, "same", false);
        u16(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("objects", value -> {
        }, ContentsRecordDecoder::objects);
        List<FlaRecord> entries = records(r, "entries");
        assertEquals(entries.size(), 3);
        assertNull(entries.get(0).get("value"));
        assertSame(entries.get(1).get("value"), entries.get(2).get("value"));
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
        out = new ByteArrayOutputStream();
        map(out, true, true);
        u16(out, 0xBEEF);
        a = archive(out.toByteArray());
        r = a.readInline("strings", value -> {
        }, ContentsRecordDecoder::strings);
        entries = records(r, "entries");
        assertEquals(entries.get(0).get("key"), entries.get(1).get("key"));
        assertNotEquals(entries.get(0).get("value"), entries.get(1).get("value"));
        assertEquals(a.readUI16(), 0xBEEF);
    }

    @Test
    public void unsupportedObjectRemainsAttachedAtTheExactMapBoundary() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u16(out, 1);
        string(out, "unknown", false);
        out.write(object("FutureSettings", new byte[]{42}));
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        try {
            a.readInline("objects", value -> root[0] = value, ContentsRecordDecoder::objects);
            fail("Expected unsupported object");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord entry = records(root[0], "entries").get(0);
            assertFalse(entry.isComplete());
            assertSame(entry.get("value"), a.getObjects().get(0));
            assertEquals(a.readUI8(), 42);
        }
    }

    @DataProvider(name = "colorCases")
    public Object[][] colorCases() {
        List<Object[]> cases = new ArrayList<>();
        for (int revision = 0; revision <= 4; revision++) {
            for (int flags : new int[]{0, 0x10, 0x20, 0x30, 0x40}) {
                cases.add(new Object[]{revision, flags});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "colorCases")
    public void colorDefinitionsUseSchemaZeroAndTheirOwnStyleBranches(int revision, int flags) throws Exception {
        ByteArrayOutputStream out = color(revision, flags, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(objectZero("CColorDef", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.getSchema(), 0);
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("packedValue"), 0xFEDCBA98L);
        if (revision < 2) {
            assertEquals(((BinaryData) r.get("legacyBytes")).toByteArray(), new byte[]{(byte) 0xA5, (byte) 0xFE});
            assertEquals(r.get("legacyValue"), 0x80000000L);
            assertEquals(r.get("legacyWords"), Arrays.asList(0, 1, 32768, 65535));
        } else {
            assertEquals(r.get("flags"), flags);
            assertEquals(r.get("secondaryFlags"), 0xA5);
            if ((flags & 0x10) != 0) {
                assertEquals(r.get("effectiveStopCount"), 2);
                assertEquals(records(r, "stops").size(), 2);
                assertEquals(records(r, "stops").get(1).get("colorRgba"), 0xFFFFFFFFL);
                assertEquals(r.get("focalPoint"), revision >= 4 ? Integer.MIN_VALUE : null);
            } else if ((flags & 0x20) != 0) {
                assertEquals(r.get("extensionValue"), 0xFFFFFFFFL);
                assertEquals(r.get("extensionWords"), Arrays.asList(0, 1, 32768, 65535));
            } else {
                assertNull(r.get("matrixWords"));
            } // Unlike shape paint, 0x40 adds no media word here.
        }
        if (revision > 0) {
            assertEquals(r.get("hue"), 0xFEDC);
            assertEquals(r.get("saturation"), 0x8000);
            assertEquals(r.get("brightness"), 0xFFFF);
        } else {
            assertNull(r.get("hue"));
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "colorCases")
    public void everyTruncatedColorDefinitionKeepsItsOriginalPrefix(int revision, int flags) throws Exception {
        byte[] bytes = objectZero("CColorDef", color(revision, flags, 2).toByteArray());
        int header = 6 + "CColorDef".length();
        for (int length = header; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known color must not become unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void gradientStopByteIsClampedLikeTheNativeColorDefinitionLoader() throws Exception {
        for (int revision : new int[]{2, 3, 4}) {
            for (int count : new int[]{0, 15, 16, 255}) {
                ByteArrayOutputStream out = color(revision, 0x10, count);
                u16(out, 0xBEEF);
                MfcArchive a = archive(objectZero("CColorDef", out.toByteArray()));
                FlaRecord r = a.readObject();
                assertEquals(r.get("stopCount"), count);
                assertEquals(records(r, "stops").size(), Math.min(15, count));
                assertEquals(a.readUI16(), 0xBEEF);
                assertEquals(a.getRemaining(), 0);
            }
        }
    }

    @Test
    public void colorListsHaveCountedObjectsThenTheirOwnRevisionAndInlineChildren() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u16(out, 2);
        u16(out, 0);
        out.write(objectZero("CColorDef", color(2, 0, 0).toByteArray()));
        out.write(1);
        out.write(1);
        u16(out, 1);
        u16(out, 2);
        out.write(0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("swatches", value -> {
        }, ColorRecordDecoder::list);
        assertNull(records(r, "colors").get(0));
        FlaRecord child = records(r, "lists").get(0);
        assertSame(records(r, "colors").get(1), records(child, "colors").get(0));
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
    }

    @Test
    public void recursiveColorListsRespectTheConfiguredDepthLimit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 12; i++) {
            u16(out, 0);
            out.write(1);
            out.write(1);
        }
        u16(out, 0);
        out.write(0);
        MfcArchive a = new MfcArchive(new BinaryData(out.toByteArray()), "test", new FlaReadOptions(ANSI, 1024, 1024, 1000, 8), DefaultFlaDecoders.create(), 0);
        try {
            a.readInline("swatches", value -> {
            }, ColorRecordDecoder::list);
            fail("Expected depth limit");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("nesting"));
        }
    }

    @Test
    public void unsupportedSchemasAndRevisionsStopBeforeUnprovenFields() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(objectZero("CColorList", new byte[]{0, 0, 0}));
        u16(out, 0xBEEF);
        MfcArchive listArchive = archive(out.toByteArray());
        FlaRecord list = listArchive.readObject();
        assertTrue(list.isComplete());
        assertEquals(list.getSchema(), 0);
        assertEquals(listArchive.readUI16(), 0xBEEF);
        MfcArchive a = archive(object("CColorDef", new byte[]{42}));
        try {
            a.readObject();
            fail("Expected unsupported schema");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.readUI8(), 42);
        }
        a = archive(objectZero("CColorDef", new byte[]{5, 42}));
        try {
            a.readObject();
            fail("Expected unsupported color revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.readUI8(), 42);
        }
        a = archive(new byte[]{0, 0, 2, 42});
        try {
            a.readInline("list", value -> {
            }, ColorRecordDecoder::list);
            fail("Expected unsupported list revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void folderRevisionTwoAndFutureRevisionsKeepTheirOwnBoundaries() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 1);
        u32(out, 5);
        out.write(new byte[13]);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        try {
            a.readInline("folders", value -> root[0] = value, ContentsRecordDecoder::folders);
            fail("Expected future folder revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord folder = records(root[0], "entries").get(0);
            assertEquals(folder.get("revision"), 5L);
            assertFalse(folder.isComplete());
            assertEquals(a.getRemaining(), 13);
        }
    }

    @Test
    public void excessiveMapProfileAndFolderCountsFailBeforeAllocatingEntries() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        count(out, 1001, true);
        MfcArchive a = archive(out.toByteArray());
        try {
            a.readInline("strings", value -> {
            }, ContentsRecordDecoder::strings);
            fail("Expected map limit");
        } catch (IOException expected) {
            assertFalse(expected instanceof UnsupportedFlaRecordException);
        }
        out = new ByteArrayOutputStream();
        u32(out, 1001);
        a = archive(out.toByteArray());
        try {
            a.readInline("profiles", value -> {
            }, (in, r) -> ContentsRecordDecoder.publish(in, r, 0x2C));
            fail("Expected profile limit");
        } catch (IOException expected) {
            assertFalse(expected instanceof UnsupportedFlaRecordException);
        }
        out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 0xFFFFFFFFL);
        a = archive(out.toByteArray());
        try {
            a.readInline("folders", value -> {
            }, ContentsRecordDecoder::folders);
            fail("Expected folder limit");
        } catch (IOException expected) {
            assertFalse(expected instanceof UnsupportedFlaRecordException);
        }
    }

    @DataProvider(name = "viewRevisions")
    public Object[][] viewRevisions() {
        return new Object[][]{{0}, {1}, {2}, {3}};
    }

    @Test(dataProvider = "viewRevisions")
    public void libraryViewHasSevenBaseWordsAndIndependentRevisionSelectedDwords(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, revision);
        words(out, 7);
        if (revision >= 2) {
            u32(out, 0x80000000L);
            u32(out, 0xFFFFFFFFL);
        }
        if (revision >= 3) {
            u32(out, 0xFEDCBA98L);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("libraryView", value -> {
        }, ContentsRecordDecoder::libraryView);
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), (long) revision);
        assertEquals(((List<?>) r.get("words")).size(), 7);
        assertEquals(r.get("value1"), revision >= 2 ? 0x80000000L : null);
        assertEquals(r.get("value3"), revision >= 3 ? 0xFEDCBA98L : null);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void lengthDelimitedQuickTimeSettingsPreserveBytesAndRejectExcessiveLengths() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 0xFFFFFFFFL);
        MfcArchive a = archive(object("CQTAudioSettings", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected invalid byte length");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Invalid length must be malformed", ex);
        } catch (IOException expected) {
            FlaRecord r = a.getObjects().get(0);
            assertFalse(r.isComplete());
            assertEquals(r.get("byteCount"), 0xFFFFFFFFL);
            assertNull(r.get("data"));
        }
    }

    @DataProvider(name = "nativeVersions")
    public Object[][] nativeVersions() {
        return new Object[][]{{"flang", "f3"}, {"flang", "f4"}, {"fla", "f5"}, {"fla", "mx"}, {"fla", "mx2004"}, {"fla", "f8"}, {"fla", "cs3"}, {"fla", "cs4"}};
    }

    @Test(dataProvider = "nativeVersions")
    public void nativeProfilesAndSwatchesFinishBeforeTheNextKnownSection(String family, String target) throws Exception {
        BinaryFlaDocument doc = extracted(Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), family, target, "0001_empty_doc"));
        FlaRecord r = doc.getContents();
        int version = doc.getContentsRevision();
        assertTrue(((FlaRecord) r.get("publishSettings")).isComplete());
        FlaRecord publish = (FlaRecord) r.get("publishSettings");
        FlaRecord profile = version < 0x2C ? (FlaRecord) publish.get("legacyProperties") : records(publish, "profiles").get(0);
        assertTrue(records(profile, "entries").size() > 0 || version == 0x14);
        if (version >= 0x16) {
            assertTrue(((FlaRecord) r.get("swatches")).isComplete());
            assertTrue(((FlaRecord) r.get("extendedSwatches")).isComplete());
        }
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getContentsStream().getIssues().toString());
        for (FlaParseIssue issue : doc.getIssues()) {
            assertNotEquals(issue.getKind(), FlaParseIssue.Kind.MALFORMED);
        }
    }

    @DataProvider(name = "nativeFolders")
    public Object[][] nativeFolders() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "nativeFolders")
    public void nativeFolderPayloadIsConsumedBeforePublishObjectMaps(String target) throws Exception {
        BinaryFlaDocument doc = extracted(Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla", target, "0028_library_folders"));
        FlaRecord folders = (FlaRecord) doc.getContents().get("libraryFolders");
        assertTrue(folders.isComplete());
        assertEquals(folders.get("count"), 3L);
        assertEquals(records(folders, "entries").get(0).get("name"), "MyEmptyFolder");
        FlaRecord fonts = (FlaRecord) doc.getContents().get("fontLibrary");
        assertTrue(fonts.isComplete());
        assertEquals(fonts.get("count"), 1L);
        FlaRecord font = records(fonts, "entries").get(0);
        assertEquals(font.get("name"), "MyFont1");
        assertEquals(font.get("fontId"), 1);
        assertTrue(((FlaRecord) font.get("style")).isComplete());
        assertTrue(((FlaRecord) font.get("libraryMetadata")).isComplete());
        assertTrue(records(doc.getContents(), "profileObjects").get(0).isComplete());
        assertNotNull(doc.getContents().get("nextSymbolIdentifier"));
        for (FlaParseIssue issue : doc.getIssues()) {
            assertNotEquals(issue.getKind(), FlaParseIssue.Kind.MALFORMED);
        }
    }

    private static void readExtensions(MfcArchive a, FlaRecord[] root, int version) throws Exception {
        a.readInline("extensions", value -> root[0] = value, (in, r) -> ContentsRecordDecoder.settings(in, r, version));
    }

    private static ByteArrayOutputStream extensions(int version, boolean extended, int profiles) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean unicode = version >= 0x2F;
        if (version >= 0x13) {
            if (version < 0x2C) {
                map(out, unicode, extended);
            } else {
                if (version >= 0x2D) {
                    map(out, unicode, extended);
                }
                u32(out, profiles);
                for (int i = 0; i < profiles; i++) {
                    map(out, unicode, extended);
                }
            }
            u32(out, 0xFFFFFFFFL);
            string(out, "first", unicode);
            string(out, text(version), unicode);
            u32(out, 0x80000001L);
        }
        if (version >= 0x15) {
            map(out, unicode, extended);
        }
        if (version >= 0x16) {
            for (int i = 0; i < 2; i++) {
                count(out, 1, extended);
                out.write(objectZero("CColorDef", color(4, i == 0 ? 0 : 0x10, 2).toByteArray()));
                out.write(0);
            }
        }
        if (version >= 0x18) {
            u32(out, 3);
            words(out, 7);
            u32(out, 0x80000000L);
            u32(out, 0xFFFFFFFFL);
            u32(out, 0xFEDCBA98L);
        }
        if (version >= 0x19) {
            u32(out, 0x80000000L);
            u32(out, 2);
            for (int v : new int[]{4, 2}) {
                u32(out, v);
                string(out, v == 4 ? "folder \u03a9" : "folder", v == 4);
                if (v == 2) {
                    u32(out, 0xFFFFFFFFL);
                }
                u32(out, 0);
                u32(out, 0);
            }
        }
        if (version >= 0x1A) {
            if (version >= 0x2D) {
                count(out, 0, extended);
            }
            for (int i = 0; i < (version < 0x2C ? 1 : profiles); i++) {
                count(out, 1, extended);
                string(out, "audio", unicode);
                ByteArrayOutputStream audio = new ByteArrayOutputStream();
                u32(audio, 3);
                audio.write(new byte[]{12, 34, 56});
                out.write(object("CQTAudioSettings", audio.toByteArray()));
            }
        }
        if (version >= 0x1B) {
            u16(out, 0xFEDC);
        }
        if (version >= 0x1C) {
            u32(out, 0xFFFFFFFFL);
            out.write(new byte[]{(byte) 0xA5, (byte) 0xFE, (byte) 0xCD});
        }
        return out;
    }

    private static void map(ByteArrayOutputStream out, boolean unicode, boolean extended) throws IOException {
        count(out, 2, extended);
        string(out, "duplicate", unicode);
        string(out, unicode ? "first \u03a9" : "first", unicode);
        string(out, "duplicate", unicode);
        string(out, "second", unicode);
    }

    private static ByteArrayOutputStream color(int revision, int flags, int stops) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        if (revision < 2) {
            out.write(0xA5);
            out.write(0xFE);
            u32(out, 0xFEDCBA98L);
            u32(out, 0x80000000L);
            u16(out, 0xABCD);
            words(out, 6);
            for (int n : new int[]{0, 1, 32768, 65535}) {
                u16(out, n);
            }
        } else {
            u32(out, 0xFEDCBA98L);
            out.write(flags);
            out.write(0xA5);
            if ((flags & 0x10) != 0) {
                words(out, 6);
                out.write(stops);
                if (revision >= 4) {
                    u32(out, Integer.MIN_VALUE);
                    u32(out, 0x80000001L);
                }
                for (int i = 0; i < Math.min(stops, 15); i++) {
                    out.write(i * 100);
                    u32(out, 0xFFFFFFFFL);
                }
            } else if ((flags & 0x20) != 0) {
                words(out, 6);
                u32(out, 0xFFFFFFFFL);
                for (int n : new int[]{0, 1, 32768, 65535}) {
                    u16(out, n);
                }
            }
        }
        if (revision >= 1) {
            u16(out, 0xFEDC);
            u16(out, 0x8000);
            u16(out, 0xFFFF);
        }
        return out;
    }

    private static byte[] objectZero(String name, byte[] payload) throws IOException {
        byte[] bytes = object(name, payload);
        bytes[2] = 0;
        return bytes;
    }

    private static void count(ByteArrayOutputStream out, int count, boolean extended) {
        if (extended) {
            u16(out, 65535);
            u32(out, count);
        } else {
            u16(out, count);
        }
    }

    private static String text(int version) {
        return version >= 0x2F ? "text \u03a9" : "text";
    }

    private static BinaryFlaDocument extracted(Path dir) throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path f : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                streams.put(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        }
        return new BinaryFlaReader(ANSI).readStreams(streams);
    }
}
