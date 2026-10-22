package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
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

public class FlaSoundFormatTest {

    @DataProvider(name = "formats")
    public Object[][] formats() {
        return new Object[][]{
            {0, 5512, 5512.5, 8, 1, 1}, {1, 5512, 5512.5, 8, 2, 2},
            {2, 5512, 5512.5, 16, 1, 2}, {3, 5512, 5512.5, 16, 2, 4},
            {4, 11025, 11025.0, 8, 1, 1}, {5, 11025, 11025.0, 8, 2, 2},
            {6, 11025, 11025.0, 16, 1, 2}, {7, 11025, 11025.0, 16, 2, 4},
            {8, 22050, 22050.0, 8, 1, 1}, {9, 22050, 22050.0, 8, 2, 2},
            {10, 22050, 22050.0, 16, 1, 2}, {11, 22050, 22050.0, 16, 2, 4},
            {12, 44100, 44100.0, 8, 1, 1}, {13, 44100, 44100.0, 8, 2, 2},
            {14, 44100, 44100.0, 16, 1, 2}, {15, 44100, 44100.0, 16, 2, 4}
        };
    }

    @Test(dataProvider = "formats")
    public void pcmFlagsSeparateSampleRateWidthChannelsAndTimelineUnits(int flags, int hz, double timebase, int bits, int channels, int bytes) {
        FlaSoundFormat f = FlaSoundFormat.fromFlags(flags);
        assertEquals(f.getFlags(), flags);
        assertEquals(f.getSampleRateHz(), hz);
        assertEquals(f.getTimebaseRateHz(), timebase);
        assertEquals(f.getBitsPerSample(), bits);
        assertEquals(f.getChannelCount(), channels);
        assertEquals(f.getPcmFrameByteCount(), bytes);
        assertEquals(f.getNominalDurationSeconds(44100), 44100 / timebase);
        assertEquals(f.getEncoding(), FlaSoundFormat.Encoding.PCM_LEGACY);
    }

    @Test
    public void codecNamesDoNotLoseUnknownIdsOrReservedBits() {
        FlaSoundFormat.Encoding[] names = {FlaSoundFormat.Encoding.PCM_LEGACY, FlaSoundFormat.Encoding.ADPCM,
            FlaSoundFormat.Encoding.MP3, FlaSoundFormat.Encoding.PCM, FlaSoundFormat.Encoding.GSM,
            FlaSoundFormat.Encoding.NELLYMOSER_8KHZ_MONO, FlaSoundFormat.Encoding.NELLYMOSER};
        for (int id = 0; id < 16; id++) {
            FlaSoundFormat f = FlaSoundFormat.fromFlags(0x1200 | (id << 4) | 15);
            assertEquals(f.getFlags(), 0x120F | (id << 4));
            assertEquals(f.getCodecId(), id);
            assertEquals(f.getUnknownFlags(), 0x1200);
            assertEquals(f.getSampleRateHz(), 44100);
            assertEquals(f.getEncoding(), id < names.length ? names[id] : FlaSoundFormat.Encoding.UNKNOWN);
        }
        FlaSoundFormat monoCodecWithStereoFlag = FlaSoundFormat.fromFlags(0x5F);
        assertEquals(monoCodecWithStereoFlag.getChannelCount(), 2);
        assertEquals(monoCodecWithStereoFlag.getEncoding(), FlaSoundFormat.Encoding.NELLYMOSER_8KHZ_MONO);
    }

    @Test
    public void historicalAdjustmentIsAdditionAndOnlyAffectsThePrimaryFormat() {
        for (int revision = 0; revision <= 10; revision++) {
            FlaSoundFormat f = FlaSoundFormat.fromStoredFlags(3, revision);
            assertEquals(f.getSampleRateHz(), revision < 5 ? 11025 : 5512);
            assertEquals(f.getFlags(), revision < 5 ? 7 : 3);
        }
        assertEquals(FlaSoundFormat.fromStoredFlags(0xFFFF, 0).getFlags(), 0x10003);
        assertEquals(FlaSoundFormat.fromStoredFlags(0xFFFF, 0).getUnknownFlags(), 0x10000);
        assertEquals(FlaSoundFormat.fromStoredFlags(15, 4).getCodecId(), 1);
        assertEquals(FlaSoundFormat.fromFlags(3).getSampleRateHz(), 5512);
    }

    @Test
    public void unsignedCountsHaveWideNominalDurationsWithoutSignedOverflow() {
        FlaSoundFormat f = FlaSoundFormat.fromFlags(0);
        assertEquals(f.getNominalDurationSeconds(0), 0.0);
        assertEquals(f.getNominalDurationSeconds(0xFFFFFFFFL), 0xFFFFFFFFL / 5512.5);
        assertEquals(f.getNominalDurationSeconds(22050), 4.0);
        for (long n : new long[]{-1, 0x100000000L}) {
            try {
                f.getNominalDurationSeconds(n);
                fail("Expected unsigned count validation");
            } catch (IllegalArgumentException expected) {
            }
        }
        for (int[] pair : new int[][]{{-1, 0}, {65536, 0}, {0, -1}, {0, 256}}) {
            try {
                FlaSoundFormat.fromStoredFlags(pair[0], pair[1]);
                fail("Expected input validation");
            } catch (IllegalArgumentException expected) {
            }
        }
        try {
            FlaSoundFormat.fromFlags(-1);
            fail("Expected normalized format validation");
        } catch (IllegalArgumentException expected) {
        }
    }

    @DataProvider(name = "revisions")
    public Object[][] revisions() {
        Object[][] cases = new Object[11][1];
        for (int i = 0; i < 11; i++) {
            cases[i][0] = i;
        }
        return cases;
    }

    @Test(dataProvider = "revisions")
    public void renamedDescriptorFieldsRetainWidthsGatesAndExactObjectBoundary(int revision) throws Exception {
        ByteArrayOutputStream bytes = descriptor(revision);
        byte[] original = object("CMediaSound", bytes.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(original);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("formatFlags"), 3);
        assertEquals(r.get("sampleCount"), 0xFFFFFFFFL);
        assertEquals(r.get("exportFormat"), revision >= 4 ? -1 : null);
        assertEquals(r.get("exportBits"), revision >= 4 ? 18 : null);
        assertEquals(r.get("encodedByteCount"), revision >= 6 ? 0x80000001L : null);
        assertEquals(r.get("importedMP3QualityFlag"), revision >= 6 ? 255 : null);
        assertEquals(r.get("secondaryFormatFlags"), revision >= 7 ? 15 : null);
        assertEquals(r.get("secondarySampleCount"), revision >= 7 ? 0xFEDCBA98L : null);
        assertEquals(r.get("deviceSoundHRef"), revision >= 9 ? "device.wav" : null);
        assertEquals(r.get("use8kSampleRateFlag"), revision >= 10 ? 0x80000001L : null);
        assertEquals(r.get("legacySourcePath"), revision == 2 ? "legacy.wav" : null);
        for (String field : new String[]{"value1", "value2", "value3", "value4", "value5", "value6", "flag", "sourceName"}) {
            assertNull(r.get(field));
        }
        assertEquals(r.getConsumedData().toByteArray(), original);
        assertEquals(a.readUI16(), 0xBEEF);
        BinaryFlaDocument.Media m = new BinaryFlaDocument.Media(null, r);
        assertEquals(m.getSoundFormat().getFlags(), revision < 5 ? 7 : 3);
        if (revision >= 7) {
            assertEquals(m.getSecondarySoundFormat().getFlags(), 15);
        } else {
            assertNull(m.getSecondarySoundFormat());
        }
        for (int length = 0; length < original.length; length++) {
            MfcArchive truncated = archive(Arrays.copyOf(original, length));
            try {
                truncated.readObject();
                fail("Expected truncation at " + length);
            } catch (java.io.IOException expected) {
            }
        }
    }

    @Test
    public void partialOrOtherMediaDoesNotInventASoundFormat() {
        FlaRecord sound = new FlaRecord("CMediaSound", 1, 0, 0, 0, new BinaryData(new byte[0]));
        BinaryFlaDocument.Media m = new BinaryFlaDocument.Media(null, sound);
        assertNull(m.getSoundFormat());
        assertNull(m.getSecondarySoundFormat());
        sound.set("revision", 4);
        assertNull(m.getSoundFormat());
        sound.set("formatFlags", 0);
        assertEquals(m.getSoundFormat().getSampleRateHz(), 11025);
        FlaRecord video = new FlaRecord("CMediaVideoStream", 1, 0, 0, 0, new BinaryData(new byte[0]));
        video.set("formatFlags", 15);
        video.set("secondaryFormatFlags", 15);
        video.set("revision", 4);
        m = new BinaryFlaDocument.Media(null, video);
        assertNull(m.getSoundFormat());
        assertNull(m.getSecondarySoundFormat());
    }

    @Test
    public void nativeSoundItemsAgreeWithTheirKnownSourceFormatsAndExportSettings() throws Exception {
        Path data = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"));
        int count = 0;
        int[] flags = {7, 11, 14, 15, 13, 3};
        long[] samples = {4870, 9741, 19456, 19456, 19456, 2435};
        for (String target : new String[]{"f5", "mx", "mx2004", "f8", "cs3", "cs4"}) {
            Path dir = data.resolve("fla/" + target + "/0014_sounds");
            Map<String, byte[]> streams = new LinkedHashMap<>();
            try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
                for (java.util.Iterator<Path> it = files.filter(Files::isRegularFile).iterator(); it.hasNext();) {
                    Path f = it.next();
                    streams.put(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
                }
            }
            BinaryFlaDocument doc = new BinaryFlaReader(ANSI).readStreams(streams);
            int index = 0;
            for (BinaryFlaDocument.Media m : doc.getMedia()) {
                FlaRecord r = m.getDescriptor();
                if (!r.getClassName().equals("CMediaSound")) {
                    continue;
                }
                assertEquals(r.get("formatFlags"), flags[index], target + " " + r.get("name"));
                assertEquals(r.get("sampleCount"), samples[index]);
                assertEquals(m.getSoundFormat().getEncoding(), FlaSoundFormat.Encoding.PCM_LEGACY);
                assertEquals(m.getStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE);
                assertEquals(r.get("encodedByteCount"), 0L);
                assertEquals(r.get("importedMP3QualityFlag"), 0);
                assertEquals(r.get("secondaryFormatFlags"), 0);
                assertEquals(r.get("secondarySampleCount"), 0L);
                assertEquals(r.get("exportFormat"), index == 3 ? 11 : index == 4 ? 14 : -1);
                assertEquals(r.get("exportBits"), index == 3 ? 4 : index == 4 ? 5 : -1);
                if (((Number) r.get("revision")).intValue() >= 9) {
                    assertEquals(((String) r.get("deviceSoundHRef")).isEmpty(), index != 5);
                }
                index++;
                count++;
            }
            assertEquals(index, 6, target);
        }
        assertEquals(count, 36);
    }

    private static ByteArrayOutputStream descriptor(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        string(out, "M 1 2", false);
        string(out, "sound", false);
        u16(out, 42);
        out.write(revision);
        if (revision == 2) {
            string(out, "legacy.wav", false);
        }
        u16(out, 3);
        u32(out, 0xFFFFFFFFL);
        if (revision >= 4) {
            u16(out, -1);
            u16(out, 18);
        }
        if (revision >= 6) {
            u32(out, 0x80000001L);
            out.write(255);
        }
        if (revision >= 7) {
            u16(out, 15);
            u32(out, 0xFEDCBA98L);
        }
        if (revision >= 9) {
            string(out, "device.wav", false);
        }
        if (revision >= 10) {
            u32(out, 0x80000001L);
        }
        return out;
    }
}
