package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class TextRecordReaderTest {

    @DataProvider(name = "revisions")
    public Object[][] revisions() {
        Object[][] result = new Object[98][2];
        int i = 0;
        for (int text : new int[]{0, 3, 4, 9, 10, 13, 14}) {
            for (int style = 2; style <= 15; style++) {
                result[i++] = new Object[]{text, style};
            }
        }
        return result;
    }

    @Test(dataProvider = "revisions")
    public void textAndStyleRevisionsSelectTheirOwnLayouts(int revision, int styleRevision) throws Exception {
        ByteArrayOutputStream out = prefix(revision, 0x2000);
        String value = styleRevision >= 10 ? "\u017Elu\u0165\uD83D\uDE00\0A" : "\u017Elu\u0165\0A";
        run(out, styleRevision, value, "\u010Cesk\u00FD font", 2);
        tail(out, revision);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicText", out.toByteArray()));
        FlaRecord record = a.readObject();
        assertTrue(record.isComplete());
        assertEquals(record.get("text"), value);
        assertEquals(record.get("reservedTextByte"), revision > 2 ? 0 : null);
        assertEquals(record.get("embedVariantGlyphs"), revision >= 14 ? 7 : null);
        assertEquals(record.get("localizationStringId"), revision >= 11 ? "extension" : null);
        assertEquals(record.get("selectedEffectIndex"), revision >= 13 ? 0xAABB : null);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        if (revision < 4) {
            assertNull(record.get("textFlags"));
            assertNull(record.get("maxCharacters"));
        } else {
            assertEquals(record.get("fontLibraryName"), "library font");
        }
        List<FlaRecord> runs = records(record, "runs");
        assertEquals(runs.size(), 1);
        FlaRecord run = runs.get(0), style = (FlaRecord) run.get("style");
        assertEquals(run.get("characterCount"), value.length());
        assertEquals(run.get("unicode"), styleRevision >= 10);
        assertEquals(((BinaryData) run.get("charactersData")).size(), value.length() * (styleRevision >= 10 ? 2 : 1));
        assertEquals(run.getConsumedData().size(), 2 + style.getConsumedData().size() + ((BinaryData) run.get("charactersData")).size());
        assertEquals(style.get("fontFamily"), "\u010Cesk\u00FD font");
        assertEquals(style.get("colorRgba"), 0x04030201L);
        assertEquals(style.get("pitchAndFamily"), 0x31);
        assertEquals(style.get("charSet"), 0xCC);
        assertEquals(style.get("useScreenSpacing"), styleRevision == 2 ? 1 : 0);
        assertEquals(style.get("lineSpacing"), -20);
        assertEquals(style.get("letterSpacing"), -5);
        assertEquals(style.get("alignment"), 3);
        if (styleRevision >= 14) {
            assertEquals(style.get("fontFace"), "FacePSMT");
            assertEquals(style.get("fontFlags"), 0x40000000L);
        }
        if (styleRevision == 2) {
            assertEquals(style.get("legacyFlags"), 0x1F);
            assertEquals(style.get("autoKern"), 0);
        }
        if (styleRevision >= 11) {
            assertEquals(((FlaRecord) style.get("antiAlias")).get("sharpness"), -1.5f);
        }
        if (styleRevision == 13) {
            assertEquals(style.get("legacyFontValue"), 0xDEADBEEFL);
        }
        if (revision >= 9) {
            assertEquals(((FlaRecord) record.get("accessibility")).get("name"), "name");
        }
    }

    @Test
    public void mixedRunEncodingsDoNotInheritThePreviousOrDefaultStyleEncoding() throws Exception {
        ByteArrayOutputStream out = prefix(14, 0x4000);
        style(out, 15, "default", 2);
        run(out, 5, "\u010D", "old", 2);
        run(out, 10, "\u6F22\uD83D\uDE00", "modern", 2);
        run(out, 5, "A", "old", 2);
        tail(out, 14);
        FlaRecord record = archive(object("CPicText", out.toByteArray())).readObject();
        assertEquals(record.get("text"), "\u010D\u6F22\uD83D\uDE00A");
        assertEquals(records(record, "runs").size(), 3);
        assertEquals(((FlaRecord) record.get("defaultStyle")).get("revision"), 15);
    }

    @Test
    public void compact255ByteFontNamesAreNotExtendedCStringLengths() throws Exception {
        for (int revision : new int[]{5, 10, 14, 15}) {
            char[] chars = new char[revision == 15 ? 300 : 255];
            Arrays.fill(chars, 'x');
            String name = new String(chars);
            ByteArrayOutputStream out = prefix(14, 0);
            run(out, revision, "A", name, 2);
            tail(out, 14);
            FlaRecord record = archive(object("CPicText", out.toByteArray())).readObject();
            assertEquals(((FlaRecord) records(record, "runs").get(0).get("style")).get("fontFamily"), name);
        }
    }

    @Test
    public void readsAnEmptyObjectWithADefaultStyleAndHistoricalAntiAliasWords() throws Exception {
        ByteArrayOutputStream out = prefix(14, 0x4000);
        style(out, 12, "default", 1);
        tail(out, 14);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicText", out.toByteArray()));
        FlaRecord record = a.readObject();
        assertEquals(record.get("text"), "");
        assertTrue(records(record, "runs").isEmpty());
        FlaRecord anti = (FlaRecord) ((FlaRecord) record.get("defaultStyle")).get("antiAlias");
        assertEquals(anti.get("legacyWords"), Arrays.asList(-250, -150, -50));
        assertEquals(anti.get("thickness"), 0.25f);
        assertEquals(a.readUI16(), 0xBEEF);
    }

    @Test
    public void everyTruncatedTextPrefixFailsAndPreservesIncompleteRecords() throws Exception {
        ByteArrayOutputStream out = prefix(14, 0);
        run(out, 15, "hello", "font", 2);
        tail(out, 14);
        byte[] bytes = object("CPicText", out.toByteArray());
        for (int length = 15; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncated text at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Truncation must not become unsupported", ex);
            } catch (IOException expected) {
                assertFalse(a.getObjects().get(0).isComplete());
            }
        }
    }

    @Test
    public void enforcesNativeRunLengthAndStopsOnFutureStyles() throws Exception {
        ByteArrayOutputStream out = prefix(14, 0);
        u16(out, 515);
        MfcArchive bad = archive(object("CPicText", out.toByteArray()));
        try {
            bad.readObject();
            fail("Expected invalid run length");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Invalid count is malformed", ex);
        } catch (IOException expected) {
            assertEquals(records(bad.getObjects().get(0), "runs").get(0).get("characterCount"), 515);
        }
        ByteArrayOutputStream future = prefix(14, 0);
        u16(future, 1);
        future.write(16);
        future.write(new byte[]{1, 2, 3, 4});
        MfcArchive unknown = archive(object("CPicText", future.toByteArray()));
        try {
            unknown.readObject();
            fail("Expected unsupported style");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord run = records(unknown.getObjects().get(0), "runs").get(0);
            assertFalse(run.isComplete());
            assertEquals(unknown.getRemaining(), 4);
            assertEquals(((FlaRecord) run.get("style")).get("revision"), 16);
        }
    }

    @Test
    public void preservesUtf16CodeUnitsIncludingUnpairedSurrogates() throws Exception {
        ByteArrayOutputStream out = prefix(14, 0);
        u16(out, 3);
        style(out, 10, "font", 2);
        u16(out, 0xD800);
        u16(out, 0);
        u16(out, 0xDC00);
        tail(out, 14);
        FlaRecord record = archive(object("CPicText", out.toByteArray())).readObject();
        assertEquals(record.get("text"), new String(new char[]{'\uD800', '\0', '\uDC00'}));
        assertEquals(((BinaryData) records(record, "runs").get(0).get("charactersData")).toByteArray(),
                new byte[]{0, (byte) 0xD8, 0, 0, 0, (byte) 0xDC});
    }

    @Test
    public void acceptsTheMaximumNativeRunLength() throws Exception {
        char[] chars = new char[514];
        Arrays.fill(chars, 'x');
        String value = new String(chars);
        ByteArrayOutputStream out = prefix(14, 0);
        run(out, 5, value, "font", 2);
        tail(out, 14);
        assertEquals(archive(object("CPicText", out.toByteArray())).readObject().get("text"), value);
    }

    private ByteArrayOutputStream prefix(int v, long flags) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(v);
        words(out, 10);
        out.write(0);
        if (v > 2) {
            out.write(0);
        }
        if (v >= 4) {
            if (v == 4) {
                u16(out, (int) flags);
            } else {
                u32(out, flags);
            }
            u16(out, 99);
            string(out, "variable", v >= 10);
            if ((flags & 0x2000) != 0) {
                string(out, "library font", v >= 10);
            }
        }
        if (v >= 14) {
            out.write(7);
        }
        return out;
    }

    private void tail(ByteArrayOutputStream out, int v) throws Exception {
        u16(out, 0);
        if (v >= 6) {
            string(out, "instance", v >= 10);
        }
        if (v >= 9) {
            access(out, 2, v >= 10);
        }
        if (v >= 8) {
            u32(out, 0xFFFFFFFFL);
        }
        if (v >= 11) {
            string(out, "extension", true);
        }
        if (v >= 12) {
            string(out, "1|2", true);
        }
        if (v >= 13) {
            out.write(0);
            u16(out, 0xAABB);
        }
    }

    private void run(ByteArrayOutputStream out, int v, String text, String font, int aa) throws Exception {
        u16(out, text.length());
        style(out, v, font, aa);
        out.write(text.getBytes(v >= 10 ? StandardCharsets.UTF_16LE : ANSI));
    }

    void style(ByteArrayOutputStream out, int v, String font, int aa) throws Exception {
        out.write(v);
        u16(out, 0x8FFF);
        styleString(out, v, font);
        if (v >= 14) {
            styleString(out, v, "FacePSMT");
            u32(out, 0x40000000L);
        }
        u32(out, 0x04030201);
        out.write(0x31);
        out.write(0xCC);
        out.write(0x80);
        if (v == 2) {
            out.write(0x1F);
        } else {
            out.write(new byte[]{1, 0, 1, 2});
        }
        out.write(3);
        u16(out, -20);
        u16(out, -1);
        u16(out, -2);
        u16(out, 3);
        u16(out, -5);
        if (v >= 5) {
            styleString(out, v, "http://example");
        }
        if (v >= 6) {
            out.write(new byte[]{1, 0, 1});
        }
        if (v >= 8) {
            out.write(1);
        }
        if (v >= 7) {
            styleString(out, v, "_blank");
        }
        if (v >= 11) {
            out.write(aa);
            out.write(2);
            if (aa == 1) {
                words(out, 3);
            }
            f32(out, 0.25f);
            f32(out, -1.5f);
        }
        if (v >= 12) {
            styleString(out, v, "extension URL");
        }
        if (v == 13) {
            styleString(out, v, "unused font");
            u32(out, 0xDEADBEEFL);
        }
    }

    private void styleString(ByteArrayOutputStream out, int v, String value) throws Exception {
        if (v >= 15) {
            string(out, value, true);
        } else {
            out.write(value.length());
            out.write(value.getBytes(v >= 10 ? StandardCharsets.UTF_16LE : ANSI));
        }
    }
}
