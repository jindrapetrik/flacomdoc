package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.TimelineConverter;
import java.io.ByteArrayInputStream;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class VideoRecordReaderTest {

    @DataProvider(name = "revisions")
    public Object[][] revisions() {
        List<Object[]> cases = new ArrayList<>();
        for (int base = 0; base <= 6; base++) {
            for (int video = 0; video <= 4; video++) {
                cases.add(new Object[]{base, video});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "revisions")
    public void videoKeepsItsOwnBaseMatrixBoundsAndRevisionSelectedMediaReference(int base, int video) throws Exception {
        ByteArrayOutputStream out = video(base, video, 0x80000001L);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicVideoStream", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("baseRevision"), base);
        assertEquals(r.get("revision"), video);
        assertEquals(r.get("matrixWords"), Arrays.asList(-250, -150, -50, 50, 150, 250));
        assertEquals(r.get("bounds"), Arrays.asList(-200, 800, -400, 1200));
        assertEquals(r.get("reservedByte"), 0xFE);
        assertEquals(r.get("reservedString"), "retained");
        assertEquals(r.get("instanceName"), name(video >= 4));
        assertEquals(r.get("mediaPresent"), video >= 3 ? 0x80000001L : null);
        assertEquals(r.get("mediaId"), 0xFEDC);
        assertNull(r.get("libraryId"));
        assertNull(r.get("colorTransform"));
        assertNull(r.get("shapeData"));
        if (base > 0) {
            assertEquals(r.get("transformationPointX"), -123);
            assertEquals(r.get("transformationPointY"), 456);
        }
        if (base >= 6) {
            assertEquals(r.get("identifier"), "12345678-1234-1234-1234-123456789ABC");
        }
        assertEquals(r.getObjectId(), 2);
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "revisions")
    public void everyTruncatedVideoIsMalformedAndRetainsTheOriginalPrefix(int base, int video) throws Exception {
        byte[] bytes = object("CPicVideoStream", video(base, video, 1).toByteArray());
        int header = 6 + "CPicVideoStream".length();
        for (int length = header; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known video layout must not become unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @DataProvider(name = "presence")
    public Object[][] presence() {
        return new Object[][]{{3, 0L}, {3, 1L}, {3, 0x80000000L}, {3, 0xFFFFFFFFL}, {4, 0L}, {4, 1L}, {4, 0x80000000L}, {4, 0xFFFFFFFFL}};
    }

    @Test(dataProvider = "presence")
    public void aZeroPresenceDwordOmitsTheMediaWordAndOtherFlagsRemainRaw(int revision, long present) throws Exception {
        ByteArrayOutputStream out = video(6, revision, present);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicVideoStream", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertEquals(r.get("mediaPresent"), present);
        assertEquals(r.get("mediaId"), present == 0 ? null : 0xFEDC);
        assertTrue(r.isComplete());
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void futureVideoRevisionStopsAfterItsBaseInsteadOfGuessingThePayload() throws Exception {
        ByteArrayOutputStream out = basePrefix(6);
        out.write(5);
        u32(out, 0x12345678);
        MfcArchive a = archive(object("CPicVideoStream", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected future video revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord r = a.getObjects().get(0);
            assertFalse(r.isComplete());
            assertEquals(r.get("revision"), 5);
            assertEquals(r.get("identifier"), "12345678-1234-1234-1234-123456789ABC");
            assertNull(r.get("matrixWords"));
            assertEquals(a.readUI32(), 0x12345678L);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void inheritedChildrenRetainSharedAndCircularIdentity() throws Exception {
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
        tail(out, 4, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicVideoStream", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertSame(r.getChildren().get(0), r.getChildren().get(1));
        assertSame(r.getChildren().get(0).getChildren().get(0), r);
        assertEquals(a.getObjects().size(), 2);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "targets")
    public Object[][] targets() {
        return new Object[][]{{FlaFormatVersion.MX}, {FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "targets")
    public void converterVideoInstancesKeepTheFollowingFrameAndLayerAligned(FlaFormatVersion version) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String instanceName = name(version != FlaFormatVersion.MX);
        new TimelineConverter(version, "windows-1250", "test").convert(xml("<DOMTimeline><layers><DOMLayer name='after video'><frames><DOMFrame index='0' duration='3'><elements>"
                + "<DOMVideoInstance name='" + instanceName + "' libraryItemName='video' frameLeft='-200' frameRight='800' frameTop='-400' frameBottom='1200'>"
                + "<matrix><Matrix a='1.5' d='0.5' tx='20' ty='-30'/></matrix></DOMVideoInstance></elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline>"),
                xml("<DOMDocument><media><DOMBitmapItem name='before video'/><DOMVideoItem name='video'/></media></DOMDocument>"), out, null);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Timeline", out.toByteArray());
        FlaStream stream = new BinaryFlaReader(ANSI).readStreams(streams).getStream("Timeline");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        FlaRecord r = null;
        for (FlaRecord candidate : stream.getObjects()) {
            if (candidate.getClassName().equals("CPicVideoStream")) {
                r = candidate;
                break;
            }
        }
        assertNotNull(r);
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), version.picVideoStreamRevision);
        assertEquals(r.get("baseRevision"), version.picVideoStreamBaseRevision);
        assertEquals(r.get("mediaId"), 2);
        assertEquals(r.get("instanceName"), instanceName);
        assertEquals(r.get("bounds"), Arrays.asList(-200, 800, -400, 1200));
        assertEquals(r.get("matrixWords"), Arrays.asList(98304, 0, 0, 32768, 400, -600));
        FlaRecord layer = stream.getRoot().getChildren().get(0);
        assertEquals(layer.get("name"), "after video");
        assertEquals(layer.getChildren().get(0).get("duration"), 3);
    }

    @Test(dataProvider = "targets")
    public void referenceVideoIdsResolveToDescriptorsAndOriginalMediaStreams(FlaFormatVersion version) throws Exception {
        Path dir = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla", version.name().toLowerCase(Locale.ROOT), "0015_videos");
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path file : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                streams.put(dir.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).readStreams(streams);
        int videos = 0;
        for (FlaStream page : doc.getStreams().values()) {
            if (page.getKind() != FlaStream.Kind.PAGE) {
                continue;
            }
            assertEquals(page.getParseStatus(), FlaStream.ParseStatus.COMPLETE, page.getIssues().toString());
            for (FlaRecord r : page.getObjects()) {
                if (!r.getClassName().equals("CPicVideoStream")) {
                    continue;
                }
                videos++;
                BinaryFlaDocument.Media match = null;
                for (BinaryFlaDocument.Media media : doc.getMedia()) {
                    if (r.get("mediaId").equals(media.getDescriptor().get("mediaId"))) {
                        match = media;
                        break;
                    }
                }
                assertNotNull(match, "Media ID " + r.get("mediaId"));
                assertEquals(match.getDescriptor().getClassName(), "CMediaVideoStream");
                assertNotNull(match.getStream());
                assertEquals(match.getStream().getKind(), FlaStream.Kind.MEDIA);
                assertEquals(match.getStream().getData().toByteArray(), streams.get(match.getStream().getPath()));
            }
        }
        assertTrue(videos > 0, "Expected video instances in the reference document");
    }

    @Test
    public void unicodeVideoNameSupportsExtendedLengthsIncludingTheReservedWordBoundary() throws Exception {
        for (int length : new int[]{254, 255, 65534, 65535}) {
            char[] chars = new char[length];
            Arrays.fill(chars, '\u03A9');
            String expected = new String(chars);
            ByteArrayOutputStream out = basePrefix(1);
            out.write(4);
            words(out, 6);
            words(out, 4);
            out.write(0);
            string(out, "", true);
            longString(out, expected);
            u32(out, 0);
            u16(out, 0xBEEF);
            MfcArchive a = archive(object("CPicVideoStream", out.toByteArray()));
            FlaRecord r = a.readObject();
            assertEquals(r.get("instanceName"), expected);
            assertNull(r.get("mediaId"));
            assertEquals(a.readUI16(), 0xBEEF);
            assertEquals(a.getRemaining(), 0);
        }
    }

    private static ByteArrayOutputStream video(int base, int revision, long present) throws IOException {
        ByteArrayOutputStream out = basePrefix(base);
        tail(out, revision, present);
        return out;
    }

    private static void tail(ByteArrayOutputStream out, int revision, long present) throws IOException {
        out.write(revision);
        words(out, 6);
        u32(out, -200);
        u32(out, 800);
        u32(out, -400);
        u32(out, 1200);
        out.write(0xFE);
        string(out, "retained", revision >= 4);
        string(out, name(revision >= 4), revision >= 4);
        if (revision >= 3) {
            u32(out, present);
        }
        if (revision < 3 || present != 0) {
            u16(out, 0xFEDC);
        }
    }

    private static ByteArrayOutputStream basePrefix(int revision) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        out.write(0xA5);
        u16(out, 0);
        if (revision > 0) {
            u32(out, -123);
            u32(out, 456);
        }
        if (revision >= 3) {
            out.write(0xFE);
        }
        if (revision >= 4) {
            out.write(0xCD);
        }
        if (revision >= 6) {
            out.write("12345678-1234-1234-1234-123456789ABC".getBytes(StandardCharsets.US_ASCII));
            out.write(0);
        }
        return out;
    }

    private static String name(boolean unicode) {
        return unicode ? "\u010cesk\u00e9 video \u03A9" : "\u010cesk\u00e9 video";
    }

    private static Element xml(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    private static void longString(ByteArrayOutputStream out, String text) throws IOException {
        out.write(255);
        u16(out, 0xFFFE);
        int length = text.length();
        if (length < 255) {
            out.write(length);
        } else {
            out.write(255);
            if (length < 65534) {
                u16(out, length);
            } else {
                u16(out, 65535);
                u32(out, length);
            }
        }
        out.write(text.getBytes(StandardCharsets.UTF_16LE));
    }
}
