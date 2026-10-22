package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class CompatibilityTailReaderTest {

    @DataProvider(name = "native")
    public Object[][] nativeFiles() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.walk(Paths.get(System.getProperty("flacomdoc.testdata", "testdata")))) {
            for (java.util.Iterator<Path> it = files.filter(p -> p.getFileName().toString().equals("Contents")).iterator(); it.hasNext();) {
                Path p = it.next();
                byte[] bytes = Files.readAllBytes(p);
                // Select the known regression fixtures independently of the decoder.
                if (bytes[0] == 0x3F && new String(bytes, StandardCharsets.US_ASCII)
                        .contains("Saved by Adobe Flash Windows 9.0 build 494 timecount = ")) {
                    cases.add(new Object[]{p});
                }
            }
        }
        assertEquals(cases.size(), 26);
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "native")
    public void all26NativeWriterTrailersDecodeWithoutChangingTheStoredVersion(Path file) throws Exception {
        BinaryFlaDocument doc = read(file, Files.readAllBytes(file));
        FlaStream contents = doc.getContentsStream();
        assertEquals(contents.getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
        assertEquals(doc.getContentsRevision(), 0x3F);
        FlaRecord tail = (FlaRecord) doc.getContents().get("compatibilityTail");
        assertTrue(tail.isComplete());
        assertEquals(tail.getConsumedData().size(), 81);
        assertNull(tail.get("variant"));
        FlaRecord libraries = (FlaRecord) tail.get("sharedLibraries");
        assertEquals(libraries.get("revision"), 0);
        assertEquals(libraries.get("count"), 0L);
        FlaRecord identity = (FlaRecord) tail.get("writerIdentity");
        assertEquals(identity.get("words"), Arrays.asList(9, 0, 494, 0));
        assertEquals(identity.get("byteCount"), 66);
        assertTrue(new String(((BinaryData) identity.get("data")).toByteArray(), StandardCharsets.US_ASCII)
                .startsWith("Saved by Adobe Flash Windows 9.0 build 494 timecount = "));
        assertEquals(contents.getUnparsedData().size(), 0);
    }

    @DataProvider(name = "producers")
    public Object[][] producers() {
        List<Object[]> cases = new ArrayList<>();
        for (int major : new int[]{9, 10, 11, 12, 14, 65535}) {
            for (int count : new int[]{0, 1, 3}) {
                cases.add(new Object[]{major, count});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "producers")
    public void producerVersionAndNonemptyLibrariesAreDataRatherThanSignatures(int major, int count) throws Exception {
        Path file = fixture("fla", "f8");
        byte[] base = withoutOldTrailer(file);
        ByteArrayOutputStream suffix = new ByteArrayOutputStream();
        libraries(suffix, count);
        byte[] payload = ("producer " + major + "\0").getBytes(StandardCharsets.US_ASCII);
        identity(suffix, major, 7, 65535, 42, payload);
        BinaryFlaDocument doc = read(file, append(base, suffix.toByteArray()));
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
        FlaRecord tail = (FlaRecord) doc.getContents().get("compatibilityTail");
        FlaRecord collection = (FlaRecord) tail.get("sharedLibraries");
        assertEquals(collection.get("count"), (long) count);
        List<FlaRecord> entries = records(collection, "entries");
        assertEquals(entries.size(), count);
        for (int i = 0; i < count; i++) {
            assertEquals(entries.get(i).get("revision"), i % 3);
            assertEquals(entries.get(i).get("name"), "library \u03A9 " + i);
            assertEquals(((BinaryData) entries.get(i).get("data")).toByteArray(), new byte[]{1, 2, (byte) i});
        }
        FlaRecord writer = (FlaRecord) tail.get("writerIdentity");
        assertEquals(writer.get("words"), Arrays.asList(major, 7, 65535, 42));
        assertEquals(((BinaryData) writer.get("data")).toByteArray(), payload);
        assertNull(tail.get("variant"));
    }

    @DataProvider(name = "olderTargets")
    public Object[][] olderTargets() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}};
    }

    @Test(dataProvider = "olderTargets")
    public void appendedStructuresDoNotRequireOneParticularContentsRevision(String version) throws Exception {
        Path file = fixture("fla", version);
        byte[] base = version.equals("f8") ? withoutOldTrailer(file) : Files.readAllBytes(file);
        assertEquals(read(file, base).getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE);
        ByteArrayOutputStream suffix = new ByteArrayOutputStream();
        libraries(suffix, 1);
        identity(suffix, 14, 0, 110, 0, new byte[]{65, 0});
        BinaryFlaDocument doc = read(file, append(base, suffix.toByteArray()));
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
        assertEquals(doc.getContentsRevision(), base[0] & 255);
        assertNotNull(doc.getContents().get("compatibilityTail"));
    }

    @Test
    public void alreadyDecodedSharedLibrariesAreNotReadAgain() throws Exception {
        Path file = fixture("flang", "cs3");
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(bytes[0], 0x43);
        bytes[0] = 0x42;
        BinaryFlaDocument doc = read(file, bytes);
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
        assertNotNull(doc.getContents().get("sharedLibraries"));
        FlaRecord tail = (FlaRecord) doc.getContents().get("compatibilityTail");
        assertNull(tail.get("sharedLibraries"));
        assertEquals(((FlaRecord) tail.get("writerIdentity")).get("words"), Arrays.asList(9, 0, 494, 0));
    }

    @Test
    public void writerPayloadUsesItsLengthRatherThanAFixedTrailerSize() throws Exception {
        for (int length : new int[]{0, 1, 66, 256, 257}) {
            ByteArrayOutputStream out = prefix();
            u16(out, length);
            if (length <= 256) {
                out.write(new byte[length]);
            }
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = a.readInline("compatibilityTail", v -> {
            }, (in, tail) -> ContentsRecordDecoder.compatibilityTail(in, tail, true));
            assertTrue(r.isComplete());
            FlaRecord writer = (FlaRecord) r.get("writerIdentity");
            assertEquals(writer.get("byteCount"), length);
            if (length <= 256) {
                assertEquals(((BinaryData) writer.get("data")).size(), length);
            } else {
                assertNull(writer.get("data"));
            }
            assertEquals(a.readUI16(), 0xBEEF);
            assertEquals(a.getRemaining(), 0);
            assertTrue(a.getObjects().isEmpty());
        }
    }

    @Test
    public void truncatedStructuresRetainPartialRecordsAndAdditionalBytesRemainUnparsed() throws Exception {
        Path file = fixture("fla", "f8");
        byte[] bytes = Files.readAllBytes(file);
        int start = bytes.length - 81;
        for (int length = 1; length < 81; length++) {
            BinaryFlaDocument doc = read(file, Arrays.copyOf(bytes, start + length));
            assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.MALFORMED, "length=" + length);
            assertFalse(((FlaRecord) doc.getContents().get("compatibilityTail")).isComplete());
        }
        byte[] unknown = bytes.clone();
        unknown[start] = 42; // Unsupported collection revision, not a producer version.
        BinaryFlaDocument doc = read(file, unknown);
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        FlaRecord libraries = (FlaRecord) ((FlaRecord) doc.getContents().get("compatibilityTail")).get("sharedLibraries");
        assertEquals(libraries.get("revision"), 42);
        assertEquals(doc.getContentsStream().getUnparsedData().size(), 80);
        doc = read(file, append(bytes, new byte[]{42}));
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(doc.getContentsStream().getUnparsedData().toByteArray(), new byte[]{42});
    }

    @Test
    public void malformedLibraryCountsAreBounded() throws Exception {
        Path file = fixture("fla", "f8");
        ByteArrayOutputStream suffix = new ByteArrayOutputStream();
        suffix.write(0);
        u32(suffix, 0xFFFFFFFFL);
        BinaryFlaDocument doc = read(file, append(withoutOldTrailer(file), suffix.toByteArray()));
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.MALFORMED);
        assertNotNull(doc.getContents().get("compatibilityTail"));
    }

    @Test
    public void missingSuffixIsValidAndACompleteModernTailIsNotReadTwice() throws Exception {
        Path old = fixture("fla", "f8");
        BinaryFlaDocument doc = read(old, withoutOldTrailer(old));
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE);
        assertNull(doc.getContents().get("compatibilityTail"));
        Path modern = fixture("flang", "cs3");
        doc = read(modern, append(Files.readAllBytes(modern), new byte[]{0, 1}));
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertNull(doc.getContents().get("compatibilityTail"));
        assertEquals(doc.getContentsStream().getUnparsedData().toByteArray(), new byte[]{0, 1});
    }

    private static void libraries(ByteArrayOutputStream out, int count) throws IOException {
        out.write(0);
        u32(out, count);
        for (int i = 0; i < count; i++) {
            int revision = i % 3;
            out.write(revision);
            string(out, "library \u03A9 " + i, true);
            u32(out, 3);
            out.write(new byte[]{1, 2, (byte) i});
            if (revision >= 1) {
                u32(out, 0);
            }
            if (revision == 1) {
                u32(out, 0);
            }
        }
    }

    private static void identity(ByteArrayOutputStream out, int major, int minor, int build, int patch, byte[] data) throws IOException {
        u16(out, major);
        u16(out, minor);
        u16(out, build);
        u16(out, patch);
        u16(out, data.length);
        out.write(data);
    }

    private static ByteArrayOutputStream prefix() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        u32(out, 0);
        u16(out, 9);
        u16(out, 0);
        u16(out, 494);
        u16(out, 0);
        return out;
    }

    private static Path fixture(String family, String version) {
        return Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), family, version, "0001_empty_doc", "Contents");
    }

    private static byte[] withoutOldTrailer(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return Arrays.copyOf(bytes, bytes.length - 81);
    }

    private static byte[] append(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static BinaryFlaDocument read(Path contents, byte[] bytes) throws IOException {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        Path root = contents.getParent();
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            for (java.util.Iterator<Path> it = files.filter(Files::isRegularFile).iterator(); it.hasNext();) {
                Path f = it.next();
                streams.put(root.relativize(f).toString().replace('\\', '/'), f.equals(contents) ? bytes : Files.readAllBytes(f));
            }
        }
        return new BinaryFlaReader(ANSI).readStreams(streams);
    }
}
