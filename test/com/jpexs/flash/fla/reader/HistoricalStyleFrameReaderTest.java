package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class HistoricalStyleFrameReaderTest {

    @DataProvider(name = "styles")
    public Object[][] styles() {
        return new Object[][]{{0, 0}, {0, 4}, {1, 0}, {1, 4}};
    }

    @Test(dataProvider = "styles")
    public void revisionOnlyStylesDoNotConsumeCharacterBytesOrFollowingRuns(int style, int text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(text);
        words(out, 10);
        out.write(0);
        if (text == 4) {
            out.write(0);
            u16(out, 0x4000);
            u16(out, 0);
            string(out, "", false);
            out.write(style);
        }
        u16(out, 3);
        out.write(style);
        out.write(new byte[]{(byte) 0x9E, 0, 'A'});
        u16(out, 1);
        out.write(1 - style);
        out.write('B');
        u16(out, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicText", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("text"), "ž\0AB");
        for (FlaRecord run : records(r, "runs")) {
            FlaRecord s = (FlaRecord) run.get("style");
            assertTrue(s.isComplete());
            assertEquals(s.getConsumedData().size(), 1);
            assertEquals(s.getFields().size(), 1);
            assertFalse((Boolean) run.get("unicode"));
        }
        if (text == 4) {
            assertEquals(((FlaRecord) r.get("defaultStyle")).getFields().size(), 1);
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void directStylesAreRevisionOnlyAndAnAbsentRevisionIsMalformed() throws Exception {
        for (int revision = 0; revision < 2; revision++) {
            MfcArchive a = archive(new byte[]{(byte) revision, 42});
            FlaRecord s = a.readInline("style", v -> {
            }, TextRecordDecoder::style);
            assertTrue(s.isComplete());
            assertEquals(s.get("revision"), revision);
            assertEquals(a.readUI8(), 42);
        }
        try {
            archive(new byte[0]).readInline("style", v -> {
            }, TextRecordDecoder::style);
            fail("Expected truncation");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Missing revision is malformed", ex);
        } catch (IOException expected) {
        }
    }

    @DataProvider(name = "frames")
    public Object[][] frames() {
        return new Object[][]{{16}, {17}, {18}};
    }

    @Test(dataProvider = "frames")
    public void frame17AloneHasTheHistoricalTransformBlock(int revision) throws Exception {
        byte[] bytes = frame(revision, 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        if (revision == 17) {
            FlaRecord ext = (FlaRecord) r.get("historicalExtension");
            assertTrue(ext.isComplete());
            assertEquals(ext.getConsumedData().size(), 53);
            assertEquals(ext.get("flagsWord"), 0xFFFF);
            assertEquals(ext.get("matrixWords"), Arrays.asList(-250, -150, -50, 50, 150, 250));
            assertEquals(ext.get("beforeColorFlag"), 0x80);
            assertEquals(ext.get("afterColorFlag"), 255);
            FlaRecord color = (FlaRecord) ext.get("colorTransform");
            assertTrue(color.isComplete());
            assertEquals(color.get("effectPercent"), -7);
            assertEquals(color.get("effectColorRgba"), 0xFFFFFFFFL);
        } else {
            assertNull(r.get("historicalExtension"));
        }
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void everyTruncatedExtensionPrefixKeepsItsParentAndInlineRecord() throws Exception {
        byte[] bytes = frame(17, 1);
        for (int length = bytes.length - 53; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known extension must not be unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertNotNull(r.get("historicalExtension"));
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void unknownColorRevisionKeepsTheExtensionPrefixAndStopsBeforeColorWords() throws Exception {
        MfcArchive a = archive(frame(17, 2));
        try {
            a.readObject();
            fail("Expected unsupported transform revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord r = a.getObjects().get(0);
            FlaRecord ext = (FlaRecord) r.get("historicalExtension");
            assertEquals(ext.get("beforeColorFlag"), 0x80);
            assertFalse(ext.isComplete());
            assertEquals(((FlaRecord) ext.get("colorTransform")).get("revision"), 2);
            assertEquals(a.getRemaining(), 25);
        }
    }

    private static byte[] frame(int revision, int colorRevision) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(0);
        words(out, 6);
        out.write(0);
        u32(out, 0);
        u16(out, 0);
        u16(out, 0); // Empty shape R0.
        out.write(revision);
        u16(out, 1);
        u16(out, 0);
        u16(out, 0);
        u16(out, 0);
        u16(out, 0);
        u16(out, 0);
        out.write(0);
        u32(out, 0);
        u32(out, 0);
        u16(out, 0);
        string(out, "frame", false);
        u32(out, 0);
        u32(out, 0);
        u32(out, 0);
        u32(out, 0);
        u16(out, 0);
        u32(out, 0);
        u16(out, 0);
        string(out, "extension", false);
        if (revision == 17) {
            u16(out, 0xFFFF);
            words(out, 6);
            out.write(0x80);
            out.write(colorRevision);
            for (int i = 0; i < 8; i++) {
                u16(out, -i);
            }
            u16(out, 42);
            u16(out, -7);
            u32(out, 0xFFFFFFFFL);
            out.write(255);
        }
        return object("CPicFrame", out.toByteArray());
    }
}
