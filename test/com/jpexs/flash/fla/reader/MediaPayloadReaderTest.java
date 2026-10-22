package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class MediaPayloadReaderTest {

    @DataProvider(name = "sounds")
    public Object[][] sounds() {
        List<Object[]> cases = new ArrayList<>();
        for (int revision = 0; revision <= 10; revision++) {
            for (int flags : new int[]{0, 1, 2, 3, 12, 32, 35}) {
                for (int encoded : new int[]{0, 7, Integer.MIN_VALUE}) {
                    for (boolean secondary : new boolean[]{false, true}) {
                        cases.add(new Object[]{revision, flags, encoded, secondary});
                    }
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "sounds")
    public void audioSizesComeFromTheirDescriptorAndKeepSecondaryMarkers(int revision, int flags, int encoded, boolean secondary) throws Exception {
        FlaRecord descriptor = descriptor(revision, flags, encoded, secondary);
        int format = flags + (revision < 5 ? 4 : 0);
        boolean compressed = (format & 0xF0) != 0;
        int primary = compressed && revision >= 6 && encoded > 0 ? encoded : 3 * ((format & 1) != 0 ? 2 : 1) * ((format & 2) != 0 ? 2 : 1);
        int second = revision >= 7 && secondary ? 8 : 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < primary; i++) {
            out.write(i);
        }
        if (compressed) {
            out.write(255);
            for (int i = 0; i < second; i++) {
                out.write(99 - i);
            }
        }
        byte[] bytes = out.toByteArray();
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("sound", v -> {
        }, (in, root) -> MediaPayloadRecordDecoder.sound(in, root, descriptor));
        assertTrue(r.isComplete());
        assertEquals(r.get("normalizedFormatFlags"), format);
        assertEquals(((FlaRecord) r.get("primaryAudio")).get("byteCount"), (long) primary);
        assertEquals(((BinaryData) ((FlaRecord) r.get("primaryAudio")).get("data")).size(), primary);
        if (compressed) {
            assertEquals(r.get("secondaryMarker"), 255);
            assertEquals(((FlaRecord) r.get("secondaryAudio")).get("byteCount"), (long) second);
        } else {
            assertNull(r.get("secondaryAudio"));
        }
        assertEquals(r.getConsumedData().toByteArray(), bytes);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive truncated = archive(Arrays.copyOf(bytes, length));
            try {
                FlaRecord shortSound = truncated.readInline("sound", v -> {
                }, (in, root) -> MediaPayloadRecordDecoder.sound(in, root, descriptor));
                assertTrue(!compressed || length > primary, "A compressed buffer still needs its following marker");
                FlaRecord shortBuffer = (FlaRecord) shortSound.get(compressed ? "secondaryAudio" : "primaryAudio");
                assertEquals(shortBuffer.get("shortRead"), true);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Audio length failure is malformed", ex);
            } catch (IOException expected) {
                assertTrue(compressed && length <= primary);
            }
        }
    }

    @DataProvider(name = "videos")
    public Object[][] videos() {
        List<Object[]> cases = new ArrayList<>();
        for (int rev = 0; rev <= 3; rev++) {
            for (int marker : new int[]{1, 255}) {
                for (int count : new int[]{0, 2}) {
                    for (boolean data : new boolean[]{false, true}) {
                        cases.add(new Object[]{rev, marker, count, data});
                    }
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "videos")
    public void videoHasTwoTracksWithTypedIndicesAndAnIndependentExtension(int revision, int marker, int count, boolean data) throws Exception {
        ByteArrayOutputStream out = video(revision, marker, count, data);
        byte[] bytes = out.toByteArray();
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline("video", v -> {
        }, MediaPayloadRecordDecoder::video);
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("videoType"), 0x80000001L);
        assertEquals(r.get("frameRate"), -1.25);
        assertEquals(r.get("audioBitsPerSample"), -250);
        assertEquals(r.get("audioChannels"), -150);
        assertEquals(r.get("videoWidth"), -50);
        assertEquals(r.get("videoHeight"), 50);
        assertEquals(r.get("syncVideo"), 255);
        assertEquals(r.get("isSpecial"), 0xFFFFFFFFL);
        assertEquals(r.get("quality"), -42);
        List<FlaRecord> tracks = records(r, "tracks");
        assertEquals(tracks.size(), 2);
        for (int i = 0; i < 2; i++) {
            FlaRecord track = tracks.get(i);
            assertTrue(track.isComplete());
            assertEquals(track.get("kind"), (long) i);
            assertEquals(track.get("terminator"), 0);
            assertEquals(records(track, "indices").size(), count);
            assertEquals(track.get("byteCount"), data ? 3L : 0L);
            for (FlaRecord entry : records(track, "indices")) {
                assertEquals(entry.get("marker"), marker);
                assertEquals(entry.get("offsetBytes"), 0x12345678);
                assertEquals(entry.get("byteCount"), 0x76543210);
                assertEquals(entry.get("frameCount"), Integer.MIN_VALUE);
                assertEquals(entry.get("flags"), -1);
                assertNull(entry.get("time"));
            }
        }
        if (revision == 3) {
            assertEquals(r.get("hasBundledDeviceVideo"), 0xFFFFFFFFL);
            assertEquals(r.get("bundledSourceFilepath"), "extension Ω");
            assertEquals(((FlaRecord) r.get("bundledDeviceVideo")).get("byteCount"), data ? 2L : 0L);
        } else {
            assertNull(r.get("bundledDeviceVideo"));
        }
        assertEquals(r.getConsumedData().toByteArray(), bytes);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertTrue(a.getObjects().isEmpty());
    }

    @Test(dataProvider = "videos")
    public void everyTruncatedVideoPrefixIsMalformedAndKeepsCompletedTracks(int revision, int marker, int count, boolean data) throws Exception {
        byte[] bytes = video(revision, marker, count, data).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] root = {null};
            try {
                a.readInline("video", v -> root[0] = v, MediaPayloadRecordDecoder::video);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known video layout must not be unsupported", ex);
            } catch (IOException expected) {
                assertFalse(root[0].isComplete());
                assertEquals(root[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void oversizedAudioLengthAndFutureVideoRevisionAreBounded() throws Exception {
        FlaRecord descriptor = descriptor(10, 3, 0, false);
        descriptor.set("sampleCount", 0xFFFFFFFFL);
        MfcArchive a = archive(new byte[]{42, 43});
        try {
            a.readInline("sound", v -> {
            }, (in, r) -> MediaPayloadRecordDecoder.sound(in, r, descriptor));
            fail("Expected size limit");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Audio length overflow is malformed", ex);
        } catch (IOException expected) {
            assertEquals(a.getPosition(), 0);
        }
        a = archive(new byte[]{4, 42, 43});
        try {
            a.readInline("video", v -> {
            }, MediaPayloadRecordDecoder::video);
            fail("Expected future video revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.readUI8(), 42);
        }
    }

    @Test
    public void linkedAudioAndVideoStreamsDecodeAndRetainTrailingOrMalformedBytes() throws Exception {
        for (boolean video : new boolean[]{false, true}) {
            byte[] bytes = video ? video(3, 255, 2, true).toByteArray() : new byte[12];
            FlaStream stream = linked(video, bytes).getStream("M 1 2");
            assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
            assertTrue(stream.getRoot().isComplete());
            assertEquals(stream.getData().toByteArray(), bytes);
            assertTrue(stream.getObjects().isEmpty());
            byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
            trailing[bytes.length] = 42;
            stream = linked(video, trailing).getStream("M 1 2");
            assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
            assertEquals(stream.getUnparsedData().toByteArray(), new byte[]{42});
            stream = linked(video, Arrays.copyOf(bytes, bytes.length - 1)).getStream("M 1 2");
            if (video) {
                assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.MALFORMED);
                assertFalse(stream.getRoot().isComplete());
            } else {
                assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE);
                assertEquals(((FlaRecord) stream.getRoot().get("primaryAudio")).get("shortRead"), true);
            }
        }
    }

    @Test
    public void compiledSwfPagePayloadIsEmptyAndUnexpectedBytesRemainPartial() throws Exception {
        byte[] descriptor = SwfRecordReaderTest.media(0, 5, 0).toByteArray();
        FlaStream stream = linked("CSwfPage", descriptor, new byte[0]).getStream("M 1 2");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        assertTrue(stream.getRoot().isComplete());
        assertEquals(stream.getRoot().getClassName(), "swfPagePayload");
        assertTrue(stream.getObjects().isEmpty());
        stream = linked("CSwfPage", descriptor, new byte[]{42, 43}).getStream("M 1 2");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(stream.getUnparsedData().toByteArray(), new byte[]{42, 43});
    }

    private static FlaRecord descriptor(int revision, int flags, int encoded, boolean secondary) {
        FlaRecord r = new FlaRecord("CMediaSound", 1, 0, 0, 0, new BinaryData(new byte[0]));
        r.set("revision", revision);
        r.set("formatFlags", flags);
        r.set("sampleCount", 3L);
        if (revision >= 6) {
            r.set("encodedByteCount", Integer.toUnsignedLong(encoded));
        }
        if (revision >= 7) {
            r.set("secondaryFormatFlags", 3);
            r.set("secondarySampleCount", secondary ? 2L : 0L);
        }
        return r;
    }

    private static ByteArrayOutputStream video(int revision, int marker, int count, boolean data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        u32(out, 0x80000001L);
        f64(out, -1.25);
        f64(out, 10.5);
        words(out, 4);
        f64(out, 2.5);
        u32(out, 0xFFFFFFFFL);
        out.write(128);
        out.write(255);
        u32(out, 27);
        u32(out, -42);
        f64(out, 99.5);
        u32(out, 24);
        for (int i = 0; i < 2; i++) {
            u32(out, i);
            for (int j = 0; j < count; j++) {
                out.write(marker);
                u32(out, 0x12345678);
                u32(out, 0x76543210);
                u32(out, Integer.MIN_VALUE);
                u32(out, -1);
            }
            out.write(0);
            u32(out, data ? 3 : 0);
            if (data) {
                out.write(new byte[]{1, 2, 3});
            }
        }
        if (revision >= 3) {
            u32(out, 0xFFFFFFFFL);
            string(out, "extension Ω", true);
            u32(out, data ? 2 : 0);
            if (data) {
                out.write(new byte[]{4, 5});
            }
        }
        return out;
    }

    private static BinaryFlaDocument linked(boolean video, byte[] payload) throws Exception {
        ByteArrayOutputStream descriptor = new ByteArrayOutputStream();
        descriptor.write(0);
        string(descriptor, "M 1 2", false);
        string(descriptor, "media", false);
        u16(descriptor, 42);
        if (!video) {
            descriptor.write(10);
            u16(descriptor, 3);
            u32(descriptor, 3);
            u16(descriptor, 0);
            u16(descriptor, 0);
            u32(descriptor, 0);
            descriptor.write(0);
            u16(descriptor, 0);
            u32(descriptor, 0);
            string(descriptor, "", false);
            u32(descriptor, 0);
        }
        return linked(video ? "CMediaVideoStream" : "CMediaSound", descriptor.toByteArray(), payload);
    }

    private static BinaryFlaDocument linked(String className, byte[] descriptor, byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x12);
        u32(out, 0);
        for (int i = 0; i < 6; i++) {
            u16(out, 0);
        }
        out.write(object(className, descriptor));
        u16(out, 0);
        u16(out, 42);
        u16(out, 0);
        out.write(0);
        words(out, 4);
        u32(out, 0);
        out.write(new byte[5]);
        words(out, 4);
        out.write(new byte[4]);
        u32(out, 0);
        u16(out, 0);
        u16(out, 0);
        u32(out, 0);
        u32(out, 0);
        u32(out, 24 * 65536);
        out.write(new byte[3]);
        u32(out, 0);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", out.toByteArray());
        streams.put("M 1 2", payload);
        return new BinaryFlaReader(ANSI).readStreams(streams);
    }
}
