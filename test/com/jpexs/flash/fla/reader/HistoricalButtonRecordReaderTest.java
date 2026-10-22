package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class HistoricalButtonRecordReaderTest {

    @DataProvider(name = "contexts")
    public Object[][] contexts() {
        List<Object[]> cases = new ArrayList<>();
        for (int button : new int[]{9, 10}) {
            for (int symbol : new int[]{0, 10, 23}) {
                for (int access = 0; access <= 2; access++) {
                    for (int text = 0; text < 10; text++) {
                        cases.add(new Object[]{button, symbol, access, text});
                    }
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "contexts")
    public void readsHistoricalNamesAndAccessibilityWithIndependentParentRevisions(int button, int symbol,
            int access, int text) throws Exception {
        byte[] name = nameBytes(text);
        byte[] bytes = object("CPicButton", button(button, symbol, access, name));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes);
        u16(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), symbol);
        assertEquals(r.get("buttonRevision"), button);
        assertEquals(r.get("trackAsMenu"), 0x80);
        FlaRecord storage = (FlaRecord) r.get("historicalInstanceName");
        assertTrue(storage.isComplete());
        assertEquals(storage.get("byteCount"), (long) name.length);
        assertEquals(((BinaryData) storage.get("data")).toByteArray(), name);
        boolean swapped = button == 9 && name.length != 0 && name[0] == 0;
        assertEquals(storage.get("byteSwapped"), swapped);
        String expected = expectedName(button, text);
        assertEquals(storage.get("value"), expected);
        assertEquals(r.get("instanceName"), expected);
        FlaRecord accessibility = (FlaRecord) r.get("accessibility");
        assertTrue(accessibility.isComplete());
        assertEquals(accessibility.get("revision"), (long) access);
        assertEquals(accessibility.get("unicodeContext"), false);
        assertNull(accessibility.get("tabIndex"));
        assertNull(accessibility.get("reservedString"));
        if (access != 0) {
            assertEquals(accessibility.get("flags"), 0x80000001L);
            assertEquals(accessibility.get("forceSimple"), 0x80000000L);
            assertEquals(accessibility.get("autoLabeling"), 0xFFFFFFFFL);
            if (button == 9) {
                assertEquals(accessibility.get("historicalWideStrings"), true);
                assertEquals(accessibility.get("name"), "access");
                assertEquals(accessibility.get("description"), "\u0100description");
                assertEquals(accessibility.get("shortcut"), "\uD800");
                FlaRecord raw = (FlaRecord) accessibility.get("nameStorage");
                assertTrue(raw.isComplete());
                assertEquals(((BinaryData) raw.get("data")).toByteArray(), wide("access\0suffix", false));
                assertEquals(raw.get("byteSwapped"), false);
            } else {
                assertEquals(accessibility.get("name"), "name");
                assertNull(accessibility.get("historicalWideStrings"));
                assertNull(accessibility.get("nameStorage"));
            }
        }
        assertEquals(r.getConsumedData().toByteArray(), bytes);
        assertSame(a.readObject(), r);
        assertEquals(a.getObjects().size(), 1);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "tails")
    public Object[][] tails() {
        return new Object[][]{{9, 0}, {9, 1}, {9, 2}, {10, 0}, {10, 1}, {10, 2}};
    }

    @Test(dataProvider = "tails")
    public void everyTruncatedHistoricalTailIsMalformedAndKeepsItsPrefix(int button, int access) throws Exception {
        byte[] bytes = object("CPicButton", button(button, 10, access, wide("button\0suffix", button == 9)));
        int start = object("CPicButton", prefix(button, 10).toByteArray()).length;
        for (int length = start; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known historical layout must not be unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
                assertEquals(r.get("buttonRevision"), button);
                if (r.get("instanceName") != null) {
                    assertEquals(r.get("instanceName"), "button");
                    assertTrue(((FlaRecord) r.get("historicalInstanceName")).isComplete());
                }
            }
        }
    }

    @Test
    public void oddNameByteCountsRetainRawBytesAndStopBeforeAccessibility() throws Exception {
        for (int button : new int[]{9, 10}) {
            ByteArrayOutputStream out = prefix(button, 10);
            u32(out, 3);
            out.write(new byte[]{65, 0, 42});
            u32(out, 0);
            u16(out, 0xBEEF);
            MfcArchive a = archive(object("CPicButton", out.toByteArray()));
            try {
                a.readObject();
                fail("Expected an odd UTF-16 byte count");
            } catch (UnsupportedFlaRecordException ex) {
                fail("Invalid UTF-16 size is malformed", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                FlaRecord name = (FlaRecord) r.get("historicalInstanceName");
                assertEquals(name.get("byteCount"), 3L);
                assertEquals(((BinaryData) name.get("data")).toByteArray(), new byte[]{65, 0, 42});
                assertFalse(name.isComplete());
                assertNull(r.get("instanceName"));
                assertNull(r.get("accessibility"));
                assertEquals(a.readUI32(), 0L);
                assertEquals(a.readUI16(), 0xBEEF);
            }
        }
    }

    @Test
    public void oversizedNameLengthsAreBoundedWithoutConsumingFollowingBytes() throws Exception {
        for (int button : new int[]{9, 10}) {
            for (long count : new long[]{0x80000000L, 0xFFFFFFFFL, 100}) {
                ByteArrayOutputStream out = prefix(button, 10);
                u32(out, count);
                u16(out, 0xBEEF);
                MfcArchive a = archive(object("CPicButton", out.toByteArray()));
                try {
                    a.readObject();
                    fail("Expected byte count limit");
                } catch (UnsupportedFlaRecordException ex) {
                    fail("Invalid size is malformed", ex);
                } catch (IOException expected) {
                    FlaRecord name = (FlaRecord) a.getObjects().get(0).get("historicalInstanceName");
                    assertEquals(name.get("byteCount"), count);
                    assertNull(name.get("data"));
                    assertFalse(name.isComplete());
                    assertEquals(a.readUI16(), 0xBEEF);
                }
            }
        }
    }

    @Test
    public void malformedAccessibilityRetainsTheCompletedNameAndPreviousStrings() throws Exception {
        ByteArrayOutputStream out = prefix(9, 10);
        buffer(out, wide("button", false));
        u32(out, 1);
        u32(out, 42);
        buffer(out, wide("access", false));
        u32(out, 3);
        out.write(new byte[]{65, 0, 42});
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicButton", out.toByteArray()));
        try {
            a.readObject();
            fail("Expected odd accessibility string length");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Invalid string size is malformed", ex);
        } catch (IOException expected) {
            FlaRecord r = a.getObjects().get(0);
            assertEquals(r.get("instanceName"), "button");
            FlaRecord access = (FlaRecord) r.get("accessibility");
            assertFalse(access.isComplete());
            assertEquals(access.get("name"), "access");
            assertTrue(((FlaRecord) access.get("nameStorage")).isComplete());
            FlaRecord description = (FlaRecord) access.get("descriptionStorage");
            assertFalse(description.isComplete());
            assertEquals(description.get("byteCount"), 3L);
            assertEquals(((BinaryData) description.get("data")).size(), 3);
            assertNull(access.get("description"));
            assertEquals(a.readUI16(), 0xBEEF);
        }
    }

    @Test
    public void unknownAccessibilityRevisionStopsBeforeItsUnprovenBody() throws Exception {
        for (int button : new int[]{9, 10}) {
            ByteArrayOutputStream out = prefix(button, 10);
            buffer(out, wide("button", false));
            u32(out, 3);
            u16(out, 0xBEEF);
            MfcArchive a = archive(object("CPicButton", out.toByteArray()));
            try {
                a.readObject();
                fail("Expected future accessibility revision");
            } catch (UnsupportedFlaRecordException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertEquals(r.get("instanceName"), "button");
                FlaRecord access = (FlaRecord) r.get("accessibility");
                assertEquals(access.get("revision"), 3L);
                assertFalse(access.isComplete());
                assertEquals(a.readUI16(), 0xBEEF);
            }
        }
    }

    private static ByteArrayOutputStream prefix(int button, int symbol) throws Exception {
        ByteArrayOutputStream out = new InstanceRecordReaderTest().symbol(symbol, 0, null);
        out.write(button);
        u32(out, 0);
        u32(out, 2);
        out.write(0x80);
        return out;
    }

    private static byte[] button(int button, int symbol, int access, byte[] name) throws Exception {
        ByteArrayOutputStream out = prefix(button, symbol);
        buffer(out, name);
        if (button == 9) {
            u32(out, access);
            if (access != 0) {
                u32(out, 0x80000001L);
                buffer(out, wide("access\0suffix", false));
                buffer(out, wide("\u0100description", false));
                buffer(out, wide("\uD800", false));
                u32(out, 0x80000000L);
                u32(out, 0xFFFFFFFFL);
            }
        } else {
            access(out, access, false);
        }
        return out.toByteArray();
    }

    private static byte[] nameBytes(int text) {
        switch (text) {
            case 0:
                return new byte[0];
            case 1:
                return wide("button", false);
            case 2:
                return wide("button", true);
            case 3:
                return wide("ÄąËťlutÄ‚Ëť\uD83D\uDE00", false);
            case 4:
                return wide("\u0100", false);
            case 5:
                return wide("a\0suffix", false);
            case 6:
                return wide("\uD800", false);
            case 7:
                return wide("\0suffix", false);
            case 8:
                return wide("\u1234", true);
            case 9:
                return wide("\uFEFFbutton", false);
            default:
                throw new AssertionError(text);
        }
    }

    private static String expectedName(int button, int text) {
        switch (text) {
            case 0:
            case 7:
                return "";
            case 1:
                return "button";
            case 2:
                return button == 9 ? "button" : "\u6200\u7500\u7400\u7400\u6F00\u6E00";
            case 3:
                return "ÄąËťlutÄ‚Ëť\uD83D\uDE00";
            case 4:
                return button == 9 ? "\u0001" : "\u0100";
            case 5:
                return "a";
            case 6:
                return button == 9 ? "\u00D8" : "\uD800";
            case 8:
                return "\u3412";
            case 9:
                return "\uFEFFbutton";
            default:
                throw new AssertionError(text);
        }
    }

    private static void buffer(ByteArrayOutputStream out, byte[] data) throws IOException {
        u32(out, data.length);
        out.write(data);
    }

    private static byte[] wide(String text, boolean bigEndian) {
        byte[] data = new byte[text.length() * 2];
        for (int i = 0; i < text.length(); i++) {
            char unit = text.charAt(i);
            data[2 * i + (bigEndian ? 1 : 0)] = (byte) unit;
            data[2 * i + (bigEndian ? 0 : 1)] = (byte) (unit >> 8);
        }
        return data;
    }
}
