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

public class CompiledActionRecordReaderTest {

    private static final int[] SIMPLE = {1, 2, 3, 4, 9, 12, 14, 16, 18, 19, 21, 29, 38, 42, 45, 48, 51, 52, 54, 58, 61, 64, 66, 68, 72, 74, 81, 85};
    private static final int[] PAYLOAD = {0, 5, 11, 13};

    @DataProvider(name = "commands")
    public Object[][] commands() {
        List<Object[]> cases = new ArrayList<>();
        for (int type : types()) {
            for (int list : new int[]{0, 1}) {
                if (list == 0 && simple(type)) {
                    cases.add(new Object[]{type, list, -1});
                } else {
                    for (int revision = 0; revision <= 2; revision++) {
                        cases.add(new Object[]{type, list, revision});
                    }
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "commands")
    public void listAndCommandRevisionsIndependentlySelectTheStoredBody(int type, int list, int revision) throws Exception {
        ByteArrayOutputStream out = list(type, list, revision);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("compiledCommands", value -> {
        }, CompiledActionRecordDecoder::list);
        assertTrue(r.isComplete());
        FlaRecord command = records(r, "commands").get(0);
        assertEquals(command.get("type"), (long) type);
        assertTrue(command.isComplete());
        boolean body = !(list == 0 && simple(type));
        assertEquals(command.get("bodyPresent"), body);
        assertEquals(command.get("revision"), body ? (long) revision : null);
        if (body) {
            if (type == 0) {
                assertEquals(command.get("navigationMode"), 0x80000000L);
                assertEquals(command.get("scene"), text(revision, "scene"));
                assertEquals(command.get("frame"), "12");
                assertEquals(command.get("play"), 0xFFFFFFFFL);
            }
            if (type == 5) {
                assertEquals(command.get("url"), text(revision, "url"));
                assertEquals(command.get("window"), "_blank");
                assertEquals(command.get("urlFlags"), 0x80000001L);
                assertEquals(command.get("windowFlags"), 0xFFFFFFFFL);
                assertEquals(command.get("method"), 0xFEDCBA98L);
            }
            if (type == 11 || type == 13) {
                assertEquals(command.get("eventFlags"), 0x80000001L);
            }
            if (type == 11) {
                assertEquals(command.get("keyCode"), 0xFFFFFFFFL);
                assertEquals(command.get("eventString"), revision >= 1 ? text(revision, "event") : null);
            }
            if (revision >= 2) {
                assertEquals(command.get("sourcePrefix"), text(revision, "32"));
                assertEquals(command.get("sourceText"), text(revision, "28"));
                assertEquals(command.get("sourceSuffix"), text(revision, "36"));
                assertEquals(command.get("value40"), 0xFFFFFFFFL);
                assertEquals(command.get("suppressBlockDelimiter"), 0x80000000L);
            } else {
                assertNull(command.get("sourcePrefix"));
            }
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertTrue(a.getObjects().isEmpty());
    }

    @Test(dataProvider = "commands")
    public void everyTruncatedListRetainsCompletedPrefixAndAnAttachedCommand(int type, int list, int revision) throws Exception {
        byte[] bytes = list(type, list, revision).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] root = {null};
            try {
                a.readInline("compiledCommands", r -> root[0] = r, CompiledActionRecordDecoder::list);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known command must not become unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(root[0]);
                assertFalse(root[0].isComplete());
                assertEquals(root[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @DataProvider(name = "types")
    public Object[][] commandTypes() {
        List<Object[]> cases = new ArrayList<>();
        for (int type : types()) {
            cases.add(new Object[]{type});
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "types")
    public void unknownCommandRevisionsStopBeforeTheUnprovenPayload(int type) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 1);
        u32(out, type);
        u32(out, 3);
        out.write(new byte[]{42, 43});
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        try {
            a.readInline("compiledCommands", r -> root[0] = r, CompiledActionRecordDecoder::list);
            fail("Expected future command revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord command = records(root[0], "commands").get(0);
            assertEquals(command.get("revision"), 3L);
            assertFalse(command.isComplete());
            assertFalse(root[0].isComplete());
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void listZeroOmitsSimpleBodiesEvenBetweenNonemptyCommands() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 0);
        u32(out, types().length);
        for (int type : types()) {
            command(out, type, 0, 2);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("compiledCommands", value -> {
        }, CompiledActionRecordDecoder::list);
        assertTrue(r.isComplete());
        assertEquals(records(r, "commands").size(), 32);
        for (FlaRecord cmd : records(r, "commands")) {
            assertEquals(cmd.get("revision") == null, simple(((Number) cmd.get("type")).intValue()));
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void nonzeroListHeadersAndIndependentCommandsPreserveMfcReferenceIds() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream base = new ByteArrayOutputStream();
        base(base);
        out.write(object("CPicObj", base.toByteArray()));
        u32(out, 0xFFFFFFFFL);
        u32(out, types().length);
        for (int type : types()) {
            command(out, type, 0xFFFFFFFFL, type % 3);
        }
        u16(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord object = a.readObject();
        FlaRecord r = a.readInline("compiledCommands", value -> {
        }, CompiledActionRecordDecoder::list);
        assertEquals(r.get("revision"), 0xFFFFFFFFL);
        assertTrue(r.isComplete());
        assertSame(a.readObject(), object);
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void nonpositiveSignedCountsReadNoCommandsAndPositiveCountsRemainBounded() throws Exception {
        for (int count : new int[]{0, -1, Integer.MIN_VALUE}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            u32(out, 99);
            u32(out, count);
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = a.readInline("compiledCommands", value -> {
            }, CompiledActionRecordDecoder::list);
            assertTrue(r.isComplete());
            assertEquals(r.get("count"), count);
            assertTrue(records(r, "commands").isEmpty());
            assertEquals(a.readUI16(), 0xBEEF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 0);
        u32(out, 1001);
        MfcArchive a = archive(out.toByteArray());
        try {
            a.readInline("compiledCommands", value -> {
            }, CompiledActionRecordDecoder::list);
            fail("Expected count limit");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Count limit must be malformed", ex);
        } catch (IOException expected) {
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void unknownTypesKeepEarlierCommandsAndTheExactRawSuffix() throws Exception {
        for (long type : new long[]{35, 86, 0x80000000L, 0xFFFFFFFFL}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            u32(out, 1);
            u32(out, 2);
            command(out, 1, 1, 2);
            u32(out, type);
            out.write(new byte[]{42, 43, 44});
            MfcArchive a = archive(out.toByteArray());
            FlaRecord[] root = {null};
            try {
                a.readInline("compiledCommands", r -> root[0] = r, CompiledActionRecordDecoder::list);
                fail("Expected unimplemented type");
            } catch (UnsupportedFlaRecordException expected) {
                List<FlaRecord> commands = records(root[0], "commands");
                assertEquals(commands.size(), 2);
                assertTrue(commands.get(0).isComplete());
                assertFalse(commands.get(1).isComplete());
                assertEquals(commands.get(1).get("type"), type);
                assertEquals(a.readUI8(), 42);
            }
        }
    }

    @Test
    public void extendedMfcMetadataAndParameterStringsKeepTheirBoundary() throws Exception {
        char[] chars = new char[300];
        Arrays.fill(chars, '\u03a9');
        String value = new String(chars);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 1);
        u32(out, 5);
        u32(out, 2);
        string(out, value, true);
        string(out, "_blank", false);
        words(out, 3);
        string(out, "ANSI metadata", false);
        string(out, value, true);
        string(out, "", true);
        words(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("compiledCommands", v -> {
        }, CompiledActionRecordDecoder::list);
        FlaRecord command = records(r, "commands").get(0);
        assertEquals(command.get("url"), value);
        assertEquals(command.get("sourcePrefix"), "ANSI metadata");
        assertEquals(command.get("sourceText"), value);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "sources")
    public Object[][] sources() {
        return new Object[][]{{0}, {1}, {2}, {3}, {4}, {5}};
    }

    @Test(dataProvider = "sources")
    public void compiledModeRunsInsideEverySupportedSourceContainer(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, revision);
        u32(out, 0);
        if (revision >= 1) {
            u32(out, 42);
            u32(out, 0);
        }
        if (revision >= 5) {
            u32(out, 0);
        }
        u32(out, 0);
        u32(out, 4);
        for (int type : PAYLOAD) {
            command(out, type, 0, type % 3);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("actions", v -> {
        }, ActionRecordDecoder::source);
        assertTrue(r.isComplete());
        FlaRecord list = (FlaRecord) r.get("compiledCommands");
        assertTrue(list.isComplete());
        assertEquals(records(list, "commands").size(), 4);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "native")
    public Object[][] nativeDocuments() {
        return new Object[][]{{"0007_symbols"}, {"0013_frame_action"}, {"0014_button_action"}};
    }

    @Test(dataProvider = "native")
    public void nativeEditorCommandsFinishTheirContainingPages(String name) throws Exception {
        Path dir = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "flang", "mx", name);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                streams.put(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).readStreams(streams);
        assertTrue(doc.getIssues().isEmpty(), doc.getIssues().toString());
        int commands = 0;
        for (FlaStream stream : doc.getStreams().values()) {
            if (stream.getKind() == FlaStream.Kind.PAGE) {
                assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
            }
            for (FlaRecord r : stream.getObjects()) {
                FlaRecord actions = (FlaRecord) r.get("actions");
                if (actions != null && actions.get("compiledCommands") != null) {
                    FlaRecord list = (FlaRecord) actions.get("compiledCommands");
                    assertTrue(list.isComplete());
                    for (FlaRecord command : records(list, "commands")) {
                        assertTrue(command.isComplete());
                        commands++;
                    }
                }
            }
        }
        assertTrue(commands > 0);
    }

    private static boolean simple(int type) {
        for (int candidate : SIMPLE) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
    }

    private static int[] types() {
        int[] types = Arrays.copyOf(SIMPLE, SIMPLE.length + PAYLOAD.length);
        System.arraycopy(PAYLOAD, 0, types, SIMPLE.length, PAYLOAD.length);
        return types;
    }

    private static String text(int revision, String prefix) {
        return revision == 0 ? prefix : prefix + " \u03a9";
    }

    private static ByteArrayOutputStream list(int type, int list, int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, list);
        u32(out, 1);
        command(out, type, list, revision);
        return out;
    }

    private static void command(ByteArrayOutputStream out, int type, long list, int revision) throws Exception {
        u32(out, type);
        if (simple(type) && list == 0) {
            return;
        }
        u32(out, revision);
        boolean unicode = revision >= 1;
        if (type == 0) {
            u32(out, 0x80000000L);
            string(out, text(revision, "scene"), unicode);
            string(out, "12", unicode);
            u32(out, 0xFFFFFFFFL);
        }
        if (type == 5) {
            string(out, text(revision, "url"), unicode);
            string(out, "_blank", unicode);
            u32(out, 0x80000001L);
            u32(out, 0xFFFFFFFFL);
            u32(out, 0xFEDCBA98L);
        }
        if (type == 11 || type == 13) {
            u32(out, 0x80000001L);
            if (type == 11) {
                u32(out, 0xFFFFFFFFL);
                if (revision >= 1) {
                    string(out, text(revision, "event"), unicode);
                }
            }
        }
        if (revision >= 2) {
            string(out, text(revision, "32"), unicode);
            string(out, text(revision, "28"), unicode);
            string(out, text(revision, "36"), unicode);
            u32(out, 0xFFFFFFFFL);
            u32(out, 0x80000000L);
        }
    }
}
