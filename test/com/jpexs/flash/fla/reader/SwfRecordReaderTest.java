package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class SwfRecordReaderTest {

    @DataProvider(name = "instances")
    public Object[][] instances() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 0; v <= 7; v++) {
            cases.add(new Object[]{v});
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "instances")
    public void compiledClipUsesItsOwnRevisionAndVersionedTails(int revision) throws Exception {
        ByteArrayOutputStream out = instance(revision, 0x80000001L);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSwf", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("baseRevision"), 1);
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("matrixWords"), Arrays.asList(-250, -150, -50, 50, 150, 250));
        assertEquals(r.get("mediaPresent"), 0x80000001L);
        assertEquals(r.get("mediaId"), 0xFEDC);
        assertNull(r.get("libraryId"));
        assertNull(r.get("colorTransform"));
        assertNull(r.get("shapeData"));
        if (revision >= 2) {
            assertEquals(((FlaRecord) r.get("actions")).get("source"), "trace('clip');");
        } else {
            assertNull(r.get("actions"));
        }
        assertEquals(r.get("instanceName"), revision >= 3 ? "\u010cesk\u00fd clip \u03a9" : null);
        if (revision >= 4) {
            assertEquals(((FlaRecord) r.get("properties")).get("parametersAreNew"), 0x80000000L);
        }
        if (revision >= 5) {
            FlaRecord access = (FlaRecord) r.get("accessibility");
            assertEquals(access.get("tabIndex"), "7");
            assertEquals(access.get("forceSimple"), 0x80000000L);
        }
        if (revision >= 6) {
            FlaRecord xml = (FlaRecord) r.get("componentXml");
            assertEquals(xml.get("revision"), 0xFE);
            assertEquals(xml.get("uniqueId"), 0xFFFFFFFFL);
            assertEquals(xml.get("xml"), "<component name='\u03a9'/>");
        }
        assertEquals(r.get("reservedByte"), revision >= 7 ? 0xA5 : null);
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "instances")
    public void everyTruncatedInstanceIsMalformedAndRetainsItsPrefix(int revision) throws Exception {
        truncated(object("CPicSwf", instance(revision, 1).toByteArray()), "CPicSwf");
    }

    @DataProvider(name = "presence")
    public Object[][] presence() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 0; v <= 7; v++) {
            for (long present : new long[]{0, 1, 0x80000000L, 0xFFFFFFFFL}) {
                cases.add(new Object[]{v, present});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "presence")
    public void mediaIdIsPresentForEveryNonzeroDword(int revision, long present) throws Exception {
        ByteArrayOutputStream out = instance(revision, present);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSwf", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertEquals(r.get("mediaPresent"), present);
        assertEquals(r.get("mediaId"), present == 0 ? null : 0xFEDC);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void baseChildrenStillRetainSharedAndCircularPointers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1);
        out.write(0);
        ByteArrayOutputStream child = new ByteArrayOutputStream();
        child.write(0);
        child.write(0);
        u16(child, 2);
        u16(child, 0);
        out.write(object("CPicObj", child.toByteArray()));
        u16(out, 4);
        u16(out, 0);
        u32(out, -1);
        u32(out, 1);
        byte[] ordinary = instance(0, 0).toByteArray();
        out.write(ordinary, 12, ordinary.length - 12);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSwf", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertSame(r.getChildren().get(0), r.getChildren().get(1));
        assertSame(r.getChildren().get(0).getChildren().get(0), r);
        assertEquals(a.getObjects().size(), 2);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "mediaRevisions")
    public Object[][] mediaRevisions() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 0; v <= 5; v++) {
            for (int component = 5; component <= 8; component++) {
                cases.add(new Object[]{v, component});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "mediaRevisions")
    public void mediaDescriptorIncludesIndependentComponentAndLocalRevisions(int revision, int component) throws Exception {
        ByteArrayOutputStream out = media(revision, component, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CSwfPage", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("mediaBaseRevision"), 3);
        assertEquals(r.get("mediaId"), 0xFEDC);
        assertEquals(r.get("streamName"), "M 1 2");
        assertEquals(r.get("name"), "compiled clip");
        assertEquals(r.get("sourcePath"), "clip.swf");
        FlaRecord block = (FlaRecord) r.get("component");
        assertEquals(block.get("revision"), component);
        assertTrue(block.isComplete());
        assertEquals(records(block, "movies").size(), 3);
        if (component >= 8) {
            FlaRecord movie = (FlaRecord) block.get("livePreview11Movie");
            assertTrue(movie.isComplete());
            assertEquals(movie.get("name"), "fourth \u03a9");
            assertEquals(((BinaryData) movie.get("data")).toByteArray(), new byte[]{'F', 'W', 'S', 7});
        } else {
            assertNull(block.get("livePreview11Movie"));
        }
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("sourceName"), revision >= 1 ? "source \u03a9" : null);
        assertEquals(r.get("boundsWords"), revision >= 2 ? Arrays.asList(-250, -150, -50, 50) : null);
        if (revision >= 3) {
            FlaRecord metadata = (FlaRecord) r.get("metadata");
            assertTrue(metadata.isComplete());
            assertEquals(metadata.get("revision"), 4);
        }
        if (revision >= 4) {
            assertEquals(r.get("legacyValue"), 7L);
            assertEquals(r.get("playerVersion"), 0x80000000L);
            assertEquals(r.get("actionScriptVersion"), 0xFFFFFFFFL);
        }
        if (revision >= 5) {
            assertEquals(r.get("sharedRecordPresent"), 0L);
            assertEquals(r.get("exportName"), "Export\u03a9");
        }
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "mediaRevisions")
    public void everyTruncatedMediaDescriptorRetainsItsInlinePrefix(int revision, int component) throws Exception {
        truncated(object("CSwfPage", media(revision, component, 0).toByteArray()), "CSwfPage");
    }

    @DataProvider(name = "metadataRevisions")
    public Object[][] metadataRevisions() {
        return new Object[][]{{0}, {1}, {2}, {3}, {4}};
    }

    @Test(dataProvider = "metadataRevisions")
    public void recursiveMetadataKeepsAllWireCollectionsAndDuplicateKeys(int revision) throws Exception {
        ByteArrayOutputStream out = metadata(revision, true);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("swfMetadata", value -> {
        }, SwfRecordDecoder::metadata);
        assertTrue(r.isComplete());
        assertEquals(r.get("name"), "name \u03a9");
        assertEquals(r.get("implementation"), "type");
        assertEquals(r.get("version"), 0x80000001L);
        if (revision == 0) {
            assertNull(r.get("isTopLevel"));
        } else if (revision == 1) {
            assertEquals(r.get("isTopLevel"), 0xFE);
        } else {
            assertEquals(r.get("isTopLevel"), 0xFFFFFFFFL);
        }
        if (revision == 2) {
            assertEquals(records(r, "legacyChildren").size(), 1);
        }
        if (revision >= 3) {
            List<FlaRecord> named = records(r, "assetMap");
            assertEquals(named.size(), 2);
            assertEquals(named.get(0).get("key"), "duplicate");
            assertEquals(named.get(1).get("key"), "duplicate");
            assertEquals(((FlaRecord) named.get(0).get("metadata")).get("revision"), 0);
            assertEquals(((FlaRecord) named.get(1).get("metadata")).get("revision"), 1);
            assertEquals(records(r, "includesList").size(), 1);
            assertEquals(records(r, "exportAfterList").size(), 1);
        }
        assertEquals(r.get("intrinsicScript"), revision >= 4 ? "second" : null);
        assertEquals(r.get("intrinsicClass"), revision >= 4 ? "extension \u03a9" : null);
        assertEquals(a.getObjects().size(), 0);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "metadataRevisions")
    public void everyTruncatedRecursiveMetadataRetainsConsumedBytes(int revision) throws Exception {
        byte[] bytes = metadata(revision, true).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] r = {null};
            try {
                a.readInline("swfMetadata", value -> r[0] = value, SwfRecordDecoder::metadata);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known layout must not become unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(r[0]);
                assertFalse(r[0].isComplete());
                assertEquals(r[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void recursiveMetadataStopsAtConfiguredDepthAndRetainsItsParents() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int level = 0; level < 20; level++) {
            out.write(2);
            string(out, "", false);
            string(out, "", false);
            u32(out, 0);
            u32(out, 0);
            u32(out, 1);
        }
        out.write(metadata(0, false).toByteArray());
        MfcArchive a = new MfcArchive(new BinaryData(out.toByteArray()), "test", new FlaReadOptions(ANSI, 1024 * 1024, 1024 * 1024, 1000, 8), DefaultFlaDecoders.create(), 0);
        FlaRecord[] root = {null};
        try {
            a.readInline("swfMetadata", value -> root[0] = value, SwfRecordDecoder::metadata);
            fail("Expected depth limit");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("nesting"));
            assertFalse(root[0].isComplete());
            assertFalse(records(root[0], "legacyChildren").get(0).isComplete());
        }
    }

    @Test
    public void failedInlineReadRestoresTheDepthCounter() throws Exception {
        MfcArchive a = new MfcArchive(new BinaryData(new byte[]{0, 42}), "test", new FlaReadOptions(ANSI, 100, 100, 100, 1), DefaultFlaDecoders.create(), 0);
        try {
            a.readInline("outer", value -> {
            }, (in, r) -> {
                in.readUI8();
                in.readInline(r, "tooDeep", (nested, child) -> {
                });
            });
            fail("Expected depth limit");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("nesting"));
        }
        FlaRecord next = a.readInline("next", value -> {
        }, (in, r) -> r.set("value", in.readUI8()));
        assertEquals(next.get("value"), 42);
        assertTrue(next.isComplete());
    }

    @Test
    public void excessiveMetadataCountIsMalformedWithoutAllocatingEntries() throws Exception {
        ByteArrayOutputStream out = metadataPrefix(3);
        u32(out, 0xFFFFFFFFL);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        try {
            a.readInline("swfMetadata", value -> root[0] = value, SwfRecordDecoder::metadata);
            fail("Expected count limit");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Count limit must be malformed", ex);
        } catch (IOException expected) {
            assertEquals(root[0].get("assetMapCount"), 0xFFFFFFFFL);
            assertNull(root[0].get("assetMap"));
            assertEquals(a.readUI16(), 0xBEEF);
        }
    }

    @Test
    public void unknownNestedMetadataRemainsAttachedBeforeItsUnprovenPayload() throws Exception {
        ByteArrayOutputStream out = metadataPrefix(3);
        u32(out, 1);
        string(out, "entry", false);
        out.write(5);
        out.write(42);
        for (int i = 0; i < 8; i++) {
            out.write(0);
        }
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        try {
            a.readInline("swfMetadata", value -> root[0] = value, SwfRecordDecoder::metadata);
            fail("Expected future metadata revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord entry = records(root[0], "assetMap").get(0);
            FlaRecord child = (FlaRecord) entry.get("metadata");
            assertEquals(child.get("revision"), 5);
            assertFalse(child.isComplete());
            assertFalse(entry.isComplete());
            assertFalse(root[0].isComplete());
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void unknownSharedLibraryRevisionStopsInsideItsAttachedRecord() throws Exception {
        ByteArrayOutputStream out = media(5, 8, 0x80000000L);
        out.write(3);
        out.write(42);
        MfcArchive a = archive(object("CSwfPage", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected shared record stop");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord r = a.getObjects().get(0);
            assertEquals(r.get("sharedRecordPresent"), 0x80000000L);
            assertTrue(((FlaRecord) r.get("metadata")).isComplete());
            assertNull(r.get("exportName"));
            FlaRecord shared = (FlaRecord) r.get("sharedLibrary");
            assertNotNull(shared);
            assertEquals(shared.get("revision"), 3);
            assertFalse(shared.isComplete());
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void unknownInstanceRevisionStopsAfterItsDecodedBase() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(8);
        out.write(42);
        MfcArchive a = archive(object("CPicSwf", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected future instance revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.getObjects().get(0).get("revision"), 8);
            assertNull(a.getObjects().get(0).get("matrixWords"));
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void unknownMediaRevisionKeepsItsCompleteInheritedComponent() throws Exception {
        ByteArrayOutputStream out = media(0, 8, 0);
        byte[] bytes = out.toByteArray();
        bytes[bytes.length - 1] = 6;
        out.reset();
        out.write(bytes);
        out.write(42);
        MfcArchive a = archive(object("CSwfPage", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected future media revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord r = a.getObjects().get(0);
            assertEquals(r.get("revision"), 6);
            assertFalse(r.isComplete());
            assertTrue(((FlaRecord) r.get("component")).isComplete());
            assertNull(r.get("sourceName"));
            assertEquals(a.readUI8(), 42);
        }
    }

    @DataProvider(name = "references")
    public Object[][] references() {
        return new Object[][]{{"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "references")
    public void nativeCompiledClipsResolveToTheirDescriptorsAndKeepPagesAligned(String target) throws Exception {
        BinaryFlaReader reader = new BinaryFlaReader(ANSI);
        BinaryFlaDocument doc = reader.read(Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla", target, "0024_compiled_clips-todo.fla").toFile());
        int clips = 0;
        for (FlaStream page : doc.getStreams().values()) {
            if (page.getKind() != FlaStream.Kind.PAGE) {
                continue;
            }
            assertEquals(page.getParseStatus(), FlaStream.ParseStatus.COMPLETE, page.getPath() + ": " + page.getIssues());
            for (FlaRecord r : page.getObjects()) {
                if (r.getClassName().equals("CPicSwf")) {
                    clips++;
                    assertTrue(r.isComplete());
                    BinaryFlaDocument.Media match = null;
                    for (BinaryFlaDocument.Media media : doc.getMedia()) {
                        if (r.get("mediaId").equals(media.getDescriptor().get("mediaId"))) {
                            match = media;
                            break;
                        }
                    }
                    assertNotNull(match);
                    assertEquals(match.getDescriptor().getClassName(), "CSwfPage");
                    assertNotNull(match.getStream());
                    assertEquals(match.getStream().getKind(), FlaStream.Kind.MEDIA);
                    FlaRecord component = (FlaRecord) match.getDescriptor().get("component");
                    assertNotNull(component);
                    assertTrue(component.isComplete());
                    assertNotEquals(component.get("mediaPresent"), 0L);
                    FlaRecord embedded = (FlaRecord) component.get("embeddedMedia");
                    assertNotNull(embedded);
                    assertTrue(embedded.isComplete());
                    assertTrue(((FlaRecord) embedded.get("payload")).isComplete());
                    assertTrue(match.getDescriptor().isComplete());
                }
            }
        }
        assertTrue(clips > 0);
        for (FlaParseIssue issue : doc.getIssues()) {
            assertNotEquals(issue.getKind(), FlaParseIssue.Kind.MALFORMED);
            assertFalse(issue.getMessage().contains("No payload decoder"));
        }
    }

    private static ByteArrayOutputStream instance(int revision, long present) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(revision);
        words(out, 6);
        u32(out, present);
        if (present != 0) {
            u16(out, 0xFEDC);
        }
        if (revision >= 2) {
            u32(out, 4);
            u32(out, 1);
            u32(out, 0x80000000L);
            u32(out, 1);
            u32(out, 0xFFFFFFFFL);
            string(out, "trace('clip');", true);
        }
        if (revision >= 3) {
            string(out, "\u010cesk\u00fd clip \u03a9", true);
        }
        if (revision >= 4) {
            out.write(2);
            u32(out, 0);
            u32(out, 0x80000000L);
        }
        if (revision >= 5) {
            access(out, 2, true);
        }
        if (revision >= 6) {
            out.write(0xFE);
            u32(out, 0xFFFFFFFFL);
            string(out, "<component name='\u03a9'/>", true);
        }
        if (revision >= 7) {
            out.write(0xA5);
        }
        return out;
    }

    static ByteArrayOutputStream media(int revision, int component, long shared) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(3);
        string(out, "M 1 2", false);
        string(out, "compiled clip", false);
        u16(out, 0xFEDC);
        string(out, "clip.swf", false);
        u32(out, 0xFFFFFFFFL);
        out.write(component);
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
        u32(out, 0);
        if (component >= 6) {
            u32(out, 0x80000001L);
        }
        if (component >= 7) {
            u32(out, 5);
            string(out, "tail", true);
            u32(out, 6);
            u32(out, 7);
        }
        if (component >= 8) {
            out.write(3);
            string(out, "fourth \u03a9", true);
            u32(out, 4);
            out.write(new byte[]{'F', 'W', 'S', 7});
            u32(out, 0xFFFFFFFFL);
        }
        out.write(revision);
        if (revision >= 1) {
            string(out, "source \u03a9", true);
        }
        if (revision >= 2) {
            words(out, 4);
        }
        if (revision >= 3) {
            out.write(metadata(4, true).toByteArray());
        }
        if (revision >= 4) {
            u32(out, 7);
            u32(out, 0x80000000L);
            u32(out, 0xFFFFFFFFL);
        }
        if (revision >= 5) {
            u32(out, shared);
            if (shared == 0) {
                string(out, "Export\u03a9", true);
            }
        }
        return out;
    }

    private static ByteArrayOutputStream metadataPrefix(int revision) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        string(out, "name \u03a9", true);
        string(out, "type", false);
        u32(out, 0x80000001L);
        if (revision == 1) {
            out.write(0xFE);
        } else if (revision >= 2) {
            u32(out, 0xFFFFFFFFL);
        }
        return out;
    }

    private static ByteArrayOutputStream metadata(int revision, boolean nonempty) throws IOException {
        ByteArrayOutputStream out = metadataPrefix(revision);
        if (revision == 2) {
            u32(out, nonempty ? 1 : 0);
            if (nonempty) {
                out.write(metadata(1, false).toByteArray());
            }
        }
        if (revision >= 3) {
            u32(out, nonempty ? 2 : 0);
            if (nonempty) {
                for (int child = 0; child <= 1; child++) {
                    string(out, "duplicate", true);
                    out.write(metadata(child, false).toByteArray());
                }
            }
            u32(out, nonempty ? 1 : 0);
            if (nonempty) {
                out.write(metadata(2, false).toByteArray());
            }
            u32(out, nonempty ? 1 : 0);
            if (nonempty) {
                out.write(metadata(3, false).toByteArray());
            }
        }
        if (revision >= 4) {
            string(out, "extension \u03a9", true);
            string(out, "second", false);
        }
        return out;
    }

    private static void truncated(byte[] bytes, String name) throws Exception {
        for (int length = 6 + name.length(); length < bytes.length; length++) {
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
            }
        }
    }
}
