package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.FlaWriter;
import com.jpexs.flash.fla.script.ScriptParser;
import com.jpexs.flash.fla.script.commands.AbstractCommand;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class ActionRecordReaderTest {

    @DataProvider(name = "historical")
    public Object[][] historical() {
        Object[][] cases = new Object[11][1];
        for (int i = 0; i < cases.length; i++) {
            cases[i][0] = i;
        }
        return cases;
    }

    @Test(dataProvider = "historical")
    public void historicalRevisionsPreserveWidthsFlagsStringsAndBoundary(int v) throws Exception {
        ByteArrayOutputStream out = historical(v, 253);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = inline(a, ActionRecordDecoder::historical);
        assertTrue(r.isComplete());
        assertEquals(r.get("kind"), 253);
        assertEquals(r.get("url"), v == 10 ? "\u010cesk\u00fd \u03a9" : "\u010cesk\u00fd");
        assertEquals(r.get("frameNumber"), 0xFFFE);
        if (v >= 1) {
            assertEquals(r.get("waitForExtraFrames"), -123);
        }
        if (v >= 2) {
            assertEquals(r.get("label"), "label");
        }
        if (v >= 3) {
            assertEquals(r.get("level"), -2147483647);
            assertEquals(r.get("target"), "target");
        }
        if (v >= 4) {
            assertEquals(r.get("release"), 0x80000000L);
            assertEquals(r.get("dragOut"), 0x80000006L);
        }
        if (v >= 5) {
            assertEquals(r.get("property"), 0xFFFFFFFFL);
            assertEquals(r.get("reservedString2"), "retained2");
        }
        if (v >= 6) {
            assertEquals(r.get("method"), "POST");
            assertEquals(r.get("loadVariables"), 2L);
        }
        if (v >= 7) {
            assertEquals(r.get("arg3"), "arg3");
            assertEquals(r.get("lockMouseToCenter"), 0xF000000BL);
        }
        if (v >= 8) {
            assertEquals(r.get("key"), 0xFFFFFFFFL);
        }
        if (v >= 9) {
            assertEquals(((BinaryData) r.get("extraData")).toByteArray(), new byte[]{0, -1, 3, 0});
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(a.getObjects().size(), 0);
    }

    @Test(dataProvider = "historical")
    public void everyHistoricalTruncationIsMalformedAndRetainsIncompleteRecord(int v) throws Exception {
        byte[] bytes = historical(v, 1).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] result = new FlaRecord[1];
            try {
                a.readInline("action", r -> result[0] = r, ActionRecordDecoder::historical);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known revision must not be unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(result[0]);
                assertFalse(result[0].isComplete());
            }
        }
    }

    @Test
    public void countedListRetainsAllKindsIncludingZeroAndIndependentRevisions() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 3);
        out.write(historical(0, 0).toByteArray());
        out.write(historical(10, 255).toByteArray());
        out.write(historical(4, 1).toByteArray());
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = inline(a, ActionRecordDecoder::legacyList);
        List<FlaRecord> commands = records(r, "commands");
        assertEquals(commands.size(), 3);
        assertEquals(commands.get(0).get("kind"), 0);
        assertEquals(commands.get(1).get("kind"), 255);
        assertEquals(commands.get(2).get("revision"), 4);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void partialListKeepsCompletedCommandsAndTheIncompleteEntry() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 2);
        out.write(historical(0, 1).toByteArray());
        out.write(11);
        out.write(new byte[]{9, 8, 7, 6, 5, 4});
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] result = new FlaRecord[1];
        try {
            a.readInline("actions", r -> result[0] = r, ActionRecordDecoder::legacyList);
            fail("Expected future revision");
        } catch (UnsupportedFlaRecordException expected) {
            List<FlaRecord> commands = records(result[0], "commands");
            assertEquals(commands.size(), 2);
            assertTrue(commands.get(0).isComplete());
            assertFalse(commands.get(1).isComplete());
            assertEquals(commands.get(1).get("revision"), 11);
            assertEquals(a.getRemaining(), 6);
        }
    }

    @DataProvider(name = "sources")
    public Object[][] sources() {
        Object[][] cases = new Object[18][2];
        int i = 0;
        for (int revision = 0; revision <= 5; revision++) {
            for (int mode = 0; mode <= 2; mode++) {
                cases[i++] = new Object[]{revision, mode};
            }
        }
        return cases;
    }

    @Test(dataProvider = "sources")
    public void sourceModesAndMetadataUseTheirOwnRevision(int revision, int mode) throws Exception {
        ByteArrayOutputStream out = sourceHeader(revision, mode);
        if (mode == 0) {
            u32(out, 1);
            u32(out, 0);
        }
        if (mode == 1) {
            if (revision == 3) {
                out.write(6);
                out.write(new byte[]{'a', 0, 0, 0, 'b', 0});
            } else {
                string(out, "play();", revision >= 4);
            }
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = inline(a, ActionRecordDecoder::source);
        assertTrue(r.isComplete());
        assertEquals(r.get("mode"), (long) mode);
        if (revision >= 1) {
            assertEquals(r.get("identifier"), 0xFFFFFFFFL);
            assertEquals(r.get("breakpointIndices"), Arrays.asList(0L, 0x80000000L));
        }
        if (revision >= 5) {
            assertEquals(r.get("codeCollapseRangeWords"), Arrays.asList(-1, 123));
            assertEquals(r.get("codeCollapseWordCount"), 2);
            List<FlaRecord> ranges = records(r, "codeCollapseRanges");
            assertEquals(ranges.size(), 1);
            assertEquals(ranges.get(0).get("offset"), -1);
            assertEquals(ranges.get(0).get("length"), 123);
            assertEquals(ranges.get(0).getConsumedData().size(), 8);
        }
        if (mode == 0) {
            assertTrue(((FlaRecord) r.get("compiledCommands")).isComplete());
        }
        if (mode == 1) {
            assertEquals(r.get("source"), revision == 3 ? "a\0b" : "play();");
        }
        if (mode == 2) {
            assertNull(r.get("source"));
            assertNull(r.get("compiledCommands"));
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "packed")
    public Object[][] packed() {
        return new Object[][]{{false, 0}, {false, 1}, {false, 2}, {false, 3}, {false, 300}, {true, 0}, {true, 1}, {true, 300}};
    }

    @Test(dataProvider = "packed")
    public void revisionThreeSourcePreservesUtf16UnitsAndAnOddTrailingByte(boolean unicode, int length) throws Exception {
        ByteArrayOutputStream out = sourceHeader(3, 1);
        if (unicode) {
            out.write(255);
            u16(out, 0xFFFE);
        }
        if (length < 255) {
            out.write(length);
        } else {
            out.write(255);
            u16(out, length);
        }
        byte[] data = new byte[length * (unicode ? 2 : 1)];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 2 == 0 ? 0 : 0xD8);
        }
        out.write(data);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = inline(a, ActionRecordDecoder::source);
        assertEquals(((BinaryData) r.get("sourceData")).toByteArray(), data);
        String source = (String) r.get("source");
        assertEquals(source.length(), data.length / 2);
        for (int i = 0; i < source.length(); i++) {
            assertEquals(source.charAt(i), '\uD800');
        }
        assertEquals(r.get("sourceUnicodeMarker"), unicode);
        assertEquals(r.get("sourceLength"), (long) length);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void nonemptyCompiledListRetainsTypeAndStopsBeforeItsUnprovenPayload() throws Exception {
        ByteArrayOutputStream out = sourceHeader(1, 0);
        u32(out, 1);
        u32(out, 1);
        u32(out, 86);
        u32(out, 0x12345678);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] result = new FlaRecord[1];
        try {
            a.readInline("actions", r -> result[0] = r, ActionRecordDecoder::source);
            fail("Expected unsupported compiled command");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord list = (FlaRecord) result[0].get("compiledCommands");
            assertEquals(list.get("revision"), 1L);
            assertEquals(list.get("count"), 1);
            FlaRecord cmd = records(list, "commands").get(0);
            assertEquals(cmd.get("type"), 86L);
            assertFalse(cmd.isComplete());
            assertEquals(a.readUI32(), 0x12345678L);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void excessiveCountsAndBlobLengthsAreRejectedBeforeAllocation() throws Exception {
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        u32(legacy, 0xFFFFFFFFL);
        expectMalformed(legacy.toByteArray(), ActionRecordDecoder::legacyList);
        ByteArrayOutputStream source = sourceHeader(5, 0);
        u32(source, 1);
        u32(source, 0x7FFFFFFFL);
        expectMalformed(source.toByteArray(), ActionRecordDecoder::source);
        ByteArrayOutputStream blob = historical(9, 1);
        byte[] bytes = blob.toByteArray();
        Arrays.fill(bytes, bytes.length - 8, bytes.length - 4, (byte) 255);
        expectMalformed(bytes, ActionRecordDecoder::historical);
        ByteArrayOutputStream packed = sourceHeader(3, 1);
        packed.write(255);
        u16(packed, 0xFFFF);
        u32(packed, 0xFFFFFFFE);
        expectMalformed(packed.toByteArray(), ActionRecordDecoder::source);
        ByteArrayOutputStream marker = sourceHeader(3, 1);
        marker.write(new byte[]{-1, -2, -1, -1, -2, -1});
        expectMalformed(marker.toByteArray(), ActionRecordDecoder::source);
    }

    @Test
    public void everySourceTruncationIsMalformed() throws Exception {
        for (int mode = 0; mode <= 2; mode++) {
            ByteArrayOutputStream out = sourceHeader(5, mode);
            if (mode == 0) {
                u32(out, 1);
                u32(out, 0);
            }
            if (mode == 1) {
                string(out, "source", true);
            }
            byte[] bytes = out.toByteArray();
            for (int length = 0; length < bytes.length; length++) {
                expectMalformed(Arrays.copyOf(bytes, length), ActionRecordDecoder::source);
            }
        }
    }

    @Test
    public void revisionNineDescriptorHasFourSeparateCountedLists() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(9);
        out.write(6);
        out.write("Page 1".getBytes("US-ASCII"));
        string(out, "Scene", false);
        u32(out, 42);
        out.write(0);
        string(out, "legacy", false);
        u32(out, 0);
        u32(out, 0);
        u32(out, 0);
        u32(out, 456);
        for (int i = 0; i < 4; i++) {
            u32(out, i == 1 ? 0 : 1);
            if (i != 1) {
                out.write(historical(i, 10 + i).toByteArray());
            }
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CDocumentPage", out.toByteArray()));
        FlaRecord r = a.readObject();
        List<FlaRecord> lists = records(r, "historicalActionLists");
        assertEquals(lists.size(), 4);
        assertEquals(lists.get(1).get("count"), 0L);
        assertEquals(records(lists.get(3), "commands").get(0).get("kind"), 13);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "buttons")
    public Object[][] buttons() {
        return new Object[][]{{2}, {3}, {4}};
    }

    @Test(dataProvider = "buttons")
    public void historicalButtonsConsumeOneCommandOrACountedList(int v) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(0);
        words(out, 6);
        u16(out, 0);
        u32(out, 42);
        out.write(v);
        if (v >= 3) {
            u32(out, 2);
        }
        out.write(historical(1, 2).toByteArray());
        if (v >= 3) {
            out.write(historical(9, 3).toByteArray());
        }
        if (v >= 4) {
            out.write(0x81);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicButton", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        FlaRecord actions = (FlaRecord) r.get("actions");
        if (v == 2) {
            assertEquals(actions.get("kind"), 2);
        } else {
            assertEquals(records(actions, "commands").size(), 2);
        }
        if (v >= 4) {
            assertEquals(r.get("trackAsMenu"), 0x81);
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "writer")
    public Object[][] writer() {
        String[] scripts = {"play();", "stop();", "gotoAndPlay(12);", "getURL(\"https://example.test/\");"};
        Object[][] cases = new Object[16][2];
        int i = 0;
        for (FlaFormatVersion v : new FlaFormatVersion[]{FlaFormatVersion.F1, FlaFormatVersion.F2, FlaFormatVersion.F3, FlaFormatVersion.F4}) {
            for (String script : scripts) {
                cases[i++] = new Object[]{v, script};
            }
        }
        return cases;
    }

    @Test(dataProvider = "writer")
    public void historicalCommandsWrittenByTheConverterRetainTheirParameters(FlaFormatVersion version, String script) throws Exception {
        List<AbstractCommand> expected = new ScriptParser(version).parse(script, false);
        assertFalse(expected.isEmpty());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new FlaWriter(out, version, "windows-1250").writeScript(script);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord record = inline(a, version.ordinal() >= FlaFormatVersion.F3.ordinal() ? ActionRecordDecoder::legacyList : ActionRecordDecoder::historical);
        List<FlaRecord> actual = version.ordinal() >= FlaFormatVersion.F3.ordinal() ? records(record, "commands") : Arrays.asList(record);
        assertEquals(actual.size(), expected.size());
        for (int i = 0; i < actual.size(); i++) {
            AbstractCommand cmd = expected.get(i);
            FlaRecord r = actual.get(i);
            assertEquals(r.get("kind"), cmd.getActionKind());
            assertEquals(r.get("url"), cmd.getUrl().value);
            assertEquals(r.get("window"), cmd.getWindow().value);
            assertEquals(r.get("page"), cmd.getPage());
            assertEquals(r.get("frameNumber"), cmd.getFrameNum());
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    private static FlaRecord inline(MfcArchive a, FlaRecordDecoder decoder) throws IOException {
        return a.readInline("actions", r -> {
        }, decoder);
    }

    private static void expectMalformed(byte[] bytes, FlaRecordDecoder decoder) throws Exception {
        try {
            inline(archive(bytes), decoder);
            fail("Expected malformed data");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Expected malformed data, not unsupported", ex);
        } catch (IOException expected) {
        }
    }

    private static ByteArrayOutputStream sourceHeader(int revision, int mode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, revision);
        u32(out, mode);
        if (revision >= 1) {
            u32(out, 0xFFFFFFFFL);
            u32(out, 2);
            u32(out, 0);
            u32(out, 0x80000000L);
        }
        if (revision >= 5) {
            u32(out, 2);
            u32(out, 0xFFFFFFFFL);
            u32(out, 123);
        }
        return out;
    }

    private static ByteArrayOutputStream historical(int v, int kind) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(v);
        out.write(kind);
        string(out, v == 10 ? "\u010cesk\u00fd \u03a9" : "\u010cesk\u00fd", v >= 10);
        string(out, "window", v >= 10);
        string(out, "page", v >= 10);
        u16(out, 0xFFFE);
        if (v >= 1) {
            u16(out, -123);
        }
        if (v >= 2) {
            string(out, "label", v >= 10);
        }
        if (v >= 3) {
            for (String s : new String[]{"arg0", "arg1", "target"}) {
                string(out, s, v >= 10);
            }
            u32(out, 0x80000001L);
        }
        if (v >= 4) {
            for (int i = 0; i < 7; i++) {
                u32(out, 0x80000000L + i);
            }
        }
        if (v >= 5) {
            u32(out, 0xFFFFFFFFL);
            for (String s : new String[]{"retained1", "retained2", "depth", "variable"}) {
                string(out, s, v >= 10);
            }
        }
        if (v >= 6) {
            string(out, "POST", v >= 10);
            u32(out, 2);
        }
        if (v >= 7) {
            string(out, "arg2", v >= 10);
            string(out, "arg3", v >= 10);
            for (int i = 0; i < 12; i++) {
                u32(out, 0xF0000000L + i);
            }
        }
        if (v >= 8) {
            u32(out, 0x80000000L);
            u32(out, 0xFFFFFFFFL);
        }
        if (v >= 9) {
            u32(out, 4);
            out.write(new byte[]{0, -1, 3, 0});
        }
        return out;
    }
}
