/*
 * Copyright (C) 2026 JPEXS.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.jpexs.flash.fla.reader;

import com.jpexs.cfb.CompoundFileBinary;
import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.LegacyFlaConverter;
import com.jpexs.flash.fla.converter.FlaWriter;
import com.jpexs.flash.fla.converter.TimelineConverter;
import com.jpexs.flash.fla.converter.streams.CfbOutputStorage;
import com.jpexs.flash.fla.converter.streams.DirectoryInputStorage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class BinaryFlaReaderTest {

    private static final byte[] EMPTY_CONTENTS = {1, 0, 0};

    @Test
    public void archivePreservesSharedAndCircularObjectIdentity() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        newClass(bytes, "Node"); // Class ID 1, object ID 2.
        bytes.write(10);
        ui16(bytes, 0x8001); // New object ID 3, previously declared class.
        bytes.write(20);
        ui16(bytes, 2); // Back reference to object ID 2.
        ui16(bytes, 3); // A second root reference to object ID 3.
        Map<String, FlaRecordDecoder> decoders = new LinkedHashMap<>();
        decoders.put("Node", (a, r) -> {
            r.set("value", a.readUI8());
            a.readObjectInto(r, "next");
        });
        MfcArchive archive = archive(bytes.toByteArray(), decoders);
        FlaRecord first = archive.readObject();
        FlaRecord second = (FlaRecord) first.get("next");
        assertSame(second.get("next"), first);
        assertSame(archive.readObject(), second);
        assertEquals(first.getObjectId(), 2);
        assertEquals(second.getObjectId(), 3);
        assertEquals(archive.getObjects().size(), 2);
        assertEquals(archive.getRemaining(), 0);
    }

    @Test
    public void archiveSupportsExtendedClassAndObjectTags() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        newClass(bytes, "Scalar");
        bytes.write(1);
        ui16(bytes, 0x7FFF);
        ui32(bytes, 0x80000001L);
        bytes.write(2);
        ui16(bytes, 0x7FFF);
        ui32(bytes, 2);
        MfcArchive archive = archive(bytes.toByteArray(), Collections.singletonMap("Scalar", (a, r) -> r.set("n", a.readUI8())));
        FlaRecord first = archive.readObject();
        FlaRecord second = archive.readObject();
        assertNotSame(first, second);
        assertEquals(second.get("n"), 2);
        assertSame(archive.readObject(), first);
    }

    @Test
    public void archiveReadsAnsiUnicodeAndExtendedStringLengths() throws Exception {
        for (FlaFormatVersion version : new FlaFormatVersion[]{FlaFormatVersion.F5, FlaFormatVersion.CS4}) {
            for (int length : new int[]{0, 254, 255, 65533, 65534, 65535}) {
                char[] chars = new char[length];
                Arrays.fill(chars, '\u010D');
                String value = new String(chars);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                new FlaWriter(bytes, version, "windows-1250").writeBomString(value);
                MfcArchive archive = new MfcArchive(new BinaryData(bytes.toByteArray()), "strings",
                        new FlaReadOptions(Charset.forName("windows-1250"), 1024 * 1024, 1024 * 1024, 100, 20), Collections.emptyMap(), 0);
                assertEquals(archive.readString(), value, version + ": " + length);
                assertEquals(archive.getRemaining(), 0);
            }
        }
        assertEquals(archive(new byte[]{(byte) 255, (byte) 255, (byte) 255, -1, -1, -1, -1}, Collections.emptyMap()).readString(), "");
    }

    @Test
    public void archiveRejectsInvalidReferencesAndExcessiveLengths() throws Exception {
        expectIo(() -> archive(new byte[]{1, 0}, Collections.emptyMap()).readObject());
        expectIo(() -> archive(new byte[]{(byte) 255, (byte) 255, (byte) 255, 0, 0, 0, 127}, Collections.emptyMap()).readString());
        expectIo(() -> archive(new byte[]{(byte) 255, (byte) 255, -1, -1, -1, 127}, Collections.emptyMap()).readCount());
        expectIo(() -> archive(new byte[]{1, '!'}, Collections.emptyMap()).readStreamName(false, false));
    }

    @Test
    public void unknownNestedClassStopsWithoutGuessingItsLengthAndRetainsTheTree() throws Exception {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.write(1);
        newClass(page, "CPicPage");
        page.write(1);
        page.write(0);
        newClass(page, "UnknownObject");
        int stop = page.size();
        byte[] opaque = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        page.write(opaque);
        Map<String, byte[]> streams = streams();
        streams.put("arbitrary name", page.toByteArray());
        BinaryFlaDocument document = new BinaryFlaReader().readStreams(streams);
        FlaStream stream = document.getStream("arbitrary name");
        assertEquals(stream.getKind(), FlaStream.Kind.PAGE);
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(stream.getStopOffset(), stop);
        assertEquals(stream.getUnparsedData().toByteArray(), opaque);
        assertEquals(stream.getRoot().getChildren().get(0).getClassName(), "UnknownObject");
        assertFalse(stream.getRoot().isComplete());
        assertFalse(stream.getRoot().getChildren().get(0).isComplete());
        assertEquals(stream.getObjects().size(), 2);
        assertEquals(stream.getIssues().get(0).getKind(), FlaParseIssue.Kind.UNSUPPORTED);
    }

    @Test
    public void classDecodersCanBeExtendedWithoutChangingContainerLoading() throws Exception {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.write(1);
        newClass(page, "CPicPage");
        page.write(1);
        page.write(0);
        newClass(page, "Extension");
        page.write(123);
        ui16(page, 0);
        ui32(page, 0x80000000L);
        ui32(page, 0x80000000L);
        page.write(1);
        ui16(page, 1);
        Map<String, byte[]> streams = streams();
        streams.put("Custom page", page.toByteArray());
        BinaryFlaDocument document = new BinaryFlaReader().registerDecoder("Extension", (a, r) -> r.set("value", a.readUI8())).readStreams(streams);
        FlaStream stream = document.getStream("Custom page");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE);
        assertEquals(stream.getRoot().getChildren().get(0).get("value"), 123);
    }

    @Test
    public void malformedArchivesAreDistinguishedFromUnsupportedRecords() throws Exception {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.write(1);
        newClass(page, "CPicPage");
        page.write(1);
        page.write(0);
        ui16(page, 77); // Invalid child reference.
        Map<String, byte[]> streams = streams();
        streams.put("Broken page", page.toByteArray());
        BinaryFlaDocument document = new BinaryFlaReader().readStreams(streams);
        FlaStream stream = document.getStream("Broken page");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.MALFORMED);
        assertEquals(stream.getIssues().get(0).getKind(), FlaParseIssue.Kind.MALFORMED);
        assertEquals(stream.getData().toByteArray(), page.toByteArray());
    }

    @DataProvider(name = "targets")
    public Object[][] targets() {
        Object[][] values = new Object[FlaFormatVersion.values().length][1];
        for (int i = 0; i < values.length; i++) {
            values[i][0] = FlaFormatVersion.values()[i];
        }
        return values;
    }

    @Test(dataProvider = "targets")
    public void emptyTimelineUsesClassLocalVersionsAndASeparateArchive(FlaFormatVersion version) throws Exception {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        new TimelineConverter(version, "windows-1252", "test").convert(xml("<DOMTimeline currentFrame='258'/>"), xml("<DOMDocument/>"), page, null);
        Map<String, byte[]> streams = streams();
        streams.put("Unrelated name", page.toByteArray());
        streams.put("Another name", page.toByteArray());
        BinaryFlaDocument document = new BinaryFlaReader().readStreams(streams);
        FlaStream first = document.getStream("Unrelated name");
        assertEquals(first.getParseStatus(), FlaStream.ParseStatus.COMPLETE);
        assertEquals(first.getRoot().get("revision"), version.picPageRevision);
        assertEquals(first.getRoot().get("baseRevision"), version.picPageBaseRevision);
        assertEquals(first.getRoot().getObjectId(), 2);
        assertNotSame(first.getRoot(), document.getStream("Another name").getRoot());
        if (version.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            assertEquals(first.getRoot().get("currentFrame"), 258L);
        }
    }

    @Test(dataProvider = "targets")
    public void emptyFramesAndLayerNamesAreStructuredWhereTheirGrammarIsSupported(FlaFormatVersion version) throws Exception {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        new TimelineConverter(version, "windows-1252", "test").convert(xml("<DOMTimeline><layers>"
                + "<DOMLayer name='A named layer'><frames><DOMFrame index='0' duration='3' keyMode='9728'>"
                + "<elements/></DOMFrame></frames></DOMLayer></layers></DOMTimeline>"), xml("<DOMDocument/>"), page, null);
        Map<String, byte[]> streams = streams();
        streams.put("Timeline", page.toByteArray());
        FlaStream stream = new BinaryFlaReader().readStreams(streams).getStream("Timeline");
        FlaRecord layer = stream.getRoot().getChildren().get(0);
        FlaRecord frame = layer.getChildren().get(0);
        assertEquals(frame.get("duration"), 3);
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        assertEquals(layer.get("name"), "A named layer");
        assertTrue(frame.isComplete());
    }

    @Test(dataProvider = "targets")
    public void generatedFlaResolvesSceneStreamsAndCoreSettings(FlaFormatVersion version) throws Exception {
        Path dir = Files.createTempDirectory("binary-fla-scenes-");
        Path xml = dir.resolve("DOMDocument.xml");
        Path file = dir.resolve("document.fla");
        try {
            Files.write(xml, ("<DOMDocument width='640' height='480' frameRate='29.5'><timelines>"
                    + "<DOMTimeline name='Scena one' currentFrame='258'/></timelines><timelines><DOMTimeline name='Scena two'/>"
                    + "</timelines></DOMDocument>").getBytes(StandardCharsets.UTF_8));
            try (CfbOutputStorage output = new CfbOutputStorage(file.toFile()); DirectoryInputStorage input = new DirectoryInputStorage(dir.toFile())) {
                new LegacyFlaConverter(version, "windows-1250").convert(input, output);
            }
            BinaryFlaDocument document = new BinaryFlaReader(Charset.forName("windows-1250")).read(file.toFile());
            assertEquals(document.getContentsRevision(), version.contentsRevision);
            assertEquals(document.getSceneDescriptors().get(0).get("name"), "Scena one");
            assertEquals(document.getPages().get(0).getRole(), BinaryFlaDocument.Page.Role.SCENE);
            assertNotNull(document.getPages().get(0).getStream());
            for (BinaryFlaDocument.Page page : document.getPages()) {
                assertEquals(page.getStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE);
            }
            assertEquals(document.getSceneDescriptors().size(), 2);
            assertEquals(document.getSceneDescriptors().get(1).get("name"), "Scena two");
            assertSame(document.getContents().get("selectedScene"), document.getSceneDescriptors().get(0));
            assertEquals(document.getWidthTwips(), Long.valueOf(12800));
            assertEquals(document.getHeightTwips(), Long.valueOf(9600));
            assertEquals(document.getFrameRate(), Double.valueOf(29.5));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(xml);
            Files.delete(dir);
        }
    }

    @Test
    public void bytesAreDetachedAndNestedStoragesAreRetained() throws Exception {
        Map<String, byte[]> streams = streams();
        byte[] bytes = {5, 6, 7};
        streams.put("extra/data", bytes);
        BinaryFlaDocument document = new BinaryFlaReader().readStreams(streams);
        bytes[0] = 99;
        byte[] copy = document.getStream("extra/data").getData().toByteArray();
        copy[1] = 99;
        assertEquals(document.getStream("extra/data").getData().toByteArray(), new byte[]{5, 6, 7});
        assertEquals(document.getRootStorage().getStorages().get(0).getName(), "extra");
        assertSame(document.getStream("/EXTRA/DATA"), document.getStream("extra/data"));
        assertEquals(document.getStream("extra/data").getParseStatus(), FlaStream.ParseStatus.RAW);
        assertTrue(document.getStream("extra/data").getData().asReadOnlyBuffer().isReadOnly());
    }

    @Test
    public void readsMiniFatAndFatStreamsAndClosesTheSourceFile() throws Exception {
        Path file = Files.createTempFile("binary-fla-", ".fla");
        byte[] small = new byte[211];
        byte[] large = new byte[10001];
        for (int i = 0; i < small.length; i++) {
            small[i] = (byte) (i * 7);
        }
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i * 13);
        }
        try {
            try (CompoundFileBinary cfb = new CompoundFileBinary(file.toFile(), true)) {
                cfb.addFile("Contents", EMPTY_CONTENTS);
                cfb.addDirectory("extra");
                cfb.addFile("extra/small", small);
                cfb.addFile("extra/large", large);
                cfb.addFile("empty", new byte[0]);
            }
            BinaryFlaDocument document = new BinaryFlaReader().read(file.toFile());
            Files.delete(file); // On Windows this also verifies that the file handle was closed.
            assertEquals(document.getStreams().size(), 4);
            assertEquals(document.getStream("extra/small").getData().toByteArray(), small);
            assertEquals(document.getStream("extra/large").getData().toByteArray(), large);
            assertEquals(document.getStream("empty").getData().size(), 0);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test(timeOut = 5000)
    public void cyclicDirectorySectorChainFailsAndClosesItsFile() throws Exception {
        Path file = Files.createTempFile("binary-fla-cycle-", ".fla");
        try {
            try (CompoundFileBinary cfb = new CompoundFileBinary(file.toFile(), true)) {
                cfb.addFile("Contents", EMPTY_CONTENTS);
            }
            try (RandomAccessFile data = new RandomAccessFile(file.toFile(), "rw")) {
                data.seek(0x30);
                long directory = Integer.toUnsignedLong(Integer.reverseBytes(data.readInt()));
                data.seek(0x4C);
                long fat = Integer.toUnsignedLong(Integer.reverseBytes(data.readInt()));
                data.seek((fat + 1) * 512 + directory * 4);
                data.writeInt(Integer.reverseBytes((int) directory));
            }
            expectIo(() -> new BinaryFlaReader().read(file.toFile()));
            Files.delete(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsNonFlaContainersMissingContentsAndExcessiveSizes() throws Exception {
        expectIo(() -> new BinaryFlaReader().readStreams(Collections.emptyMap()));
        BinaryFlaReader limited = new BinaryFlaReader(new FlaReadOptions(StandardCharsets.UTF_8, 8, 8, 10, 10));
        Map<String, byte[]> streams = streams();
        streams.put("large", new byte[9]);
        expectIo(() -> limited.readStreams(streams));
        Map<String, byte[]> collision = streams();
        collision.put("folder", new byte[0]);
        collision.put("folder/data", new byte[0]);
        expectIo(() -> new BinaryFlaReader().readStreams(collision));
        Path zip = Files.createTempFile("not-binary-fla-", ".fla");
        try {
            Files.write(zip, new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});
            expectIo(() -> new BinaryFlaReader().read(zip.toFile()));
        } finally {
            Files.delete(zip);
        }
    }

    @DataProvider(name = "extracted")
    public Object[][] extracted() throws IOException {
        Path root = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"));
        List<Path> paths;
        try (Stream<Path> files = Files.walk(root)) {
            paths = files.filter(p -> p.getFileName().toString().equals("Contents")).sorted().collect(Collectors.toList());
        }
        assertFalse(paths.isEmpty(), "Binary FLA reference streams must be present");
        return paths.stream().map(p -> new Object[]{p.toFile()}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "extracted")
    public void loadsExistingBinaryReferenceStreamsWithoutLosingBytes(File contents) throws Exception {
        Path base = contents.toPath().getParent();
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                streams.put(base.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        BinaryFlaDocument document = new BinaryFlaReader(Charset.forName("windows-1250")).readStreams(streams);
        assertEquals(document.getStreams().size(), streams.size());
        assertTrue(document.getContentsRevision() > 0);
        for (Map.Entry<String, byte[]> entry : streams.entrySet()) {
            FlaStream stream = document.getStream(entry.getKey());
            assertEquals(stream.getData().toByteArray(), entry.getValue(), entry.getKey());
            assertTrue(stream.getStopOffset() <= stream.getData().size());
        }
        for (FlaParseIssue issue : document.getIssues()) {
            assertNotEquals(issue.getKind(), FlaParseIssue.Kind.MALFORMED, contents + ": " + issue);
        }
        assertFalse(document.getSceneDescriptors().isEmpty(), contents.toString());
        assertFalse(document.getPages().isEmpty(), contents.toString());
    }

    private MfcArchive archive(byte[] bytes, Map<String, FlaRecordDecoder> decoders) {
        return new MfcArchive(new BinaryData(bytes), "test", new FlaReadOptions(), decoders, 0);
    }

    private Map<String, byte[]> streams() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("Contents", EMPTY_CONTENTS);
        return result;
    }

    private org.w3c.dom.Element xml(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    private void newClass(ByteArrayOutputStream out, String name) throws IOException {
        ui16(out, 0xFFFF);
        ui16(out, 1);
        ui16(out, name.length());
        out.write(name.getBytes(StandardCharsets.US_ASCII));
    }

    private void ui16(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >>> 8);
    }

    private void ui32(ByteArrayOutputStream out, long value) {
        ui16(out, (int) value);
        ui16(out, (int) (value >>> 16));
    }

    private void expectIo(IoCall call) throws Exception {
        try {
            call.run();
            fail("Expected IOException");
        } catch (IOException expected) {
        }
    }

    @FunctionalInterface
    private interface IoCall {

        void run() throws Exception;
    }
}
