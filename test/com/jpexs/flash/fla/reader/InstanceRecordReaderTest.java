package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class InstanceRecordReaderTest {

    @DataProvider(name = "symbols")
    public Object[][] symbols() {
        Object[][] result = new Object[24][1];
        for (int i = 0; i < 24; i++) {
            result[i][0] = i;
        }
        return result;
    }

    @Test(dataProvider = "symbols")
    public void symbolRevisionsPreserveTheirExactBoundaryAndNativeWidths(int revision) throws Exception {
        ByteArrayOutputStream out = symbol(revision);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSymbol", out.toByteArray()));
        FlaRecord record = a.readObject();
        assertTrue(record.isComplete());
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(record.get("firstFrame"), 0xFFEE);
        assertEquals(record.get("libraryId"), 0xFEEDBEADL);
        if (revision >= 2) {
            assertEquals(record.get("loopMode"), 0xABCD);
        }
        if (revision >= 3) {
            FlaRecord color = (FlaRecord) record.get("colorTransform");
            assertEquals(color.get("redMultiplier"), -2);
            assertEquals(color.get("blueOffset"), -7);
            if (revision >= 5) {
                assertEquals(color.get("alphaOffset"), -1);
            } else {
                assertNull(color.get("alphaOffset"));
            }
            if (revision >= 7) {
                assertEquals(color.getConsumedData().size(), 25);
                assertEquals(color.get("effectPercent"), -42);
                assertEquals(color.get("effectColorRgba"), 0x87654321L);
            }
        }
        if (revision >= 20) {
            List<?> matrix = (List<?>) record.get("matrix3D");
            assertEquals(matrix.size(), 16);
            assertEquals(matrix.get(15), 14.75f);
            assertEquals(record.get("rotation3D"), Arrays.asList(-1.1, 2.2, 3.3));
        }
        if (revision >= 21) {
            assertEquals(record.get("centerPoint3D"), Arrays.asList(Integer.MIN_VALUE, -5, 17));
        }
        if (revision >= 22) {
            List<FlaRecord> settings = records(record, "animationFrameSettings");
            assertEquals(settings.size(), 2);
            assertEquals(settings.get(0).get("loopMode"), 65535);
            assertEquals(settings.get(0).get("firstFrame"), 32768);
            assertEquals(settings.get(1).get("loopMode"), 7);
            assertEquals(settings.get(1).get("firstFrame"), 8);
            assertEquals(settings.get(0).getConsumedData().size(), 4);
        }
        if (revision >= 17) {
            assertEquals(record.get("selectedEffectIndex"), 0xFF80);
        }
        if (revision >= 23) {
            assertEquals(record.get("bitmapRenderMode"), 0xFABC);
            assertEquals(record.get("bitmapBits32"), 0x80000000L);
            assertEquals(record.get("bitmapBackgroundColorRgba"), 0x87654321L);
            assertEquals(record.get("isVisible"), 0xFFFFFFFFL);
        }
    }

    @DataProvider(name = "sprites")
    public Object[][] sprites() {
        return new Object[][]{{0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}, {8}};
    }

    @Test(dataProvider = "sprites")
    public void spriteConsumesItsOwnRevisionActionsPropertiesAndXml(int v) throws Exception {
        ByteArrayOutputStream out = symbol(10);
        out.write(v);
        if (v >= 2) {
            u32(out, 0);
            u32(out, 2);
        }
        string(out, "clip name", v >= 7);
        if (v >= 3) {
            out.write(2);
            u32(out, 0);
            u32(out, 0x80000001L);
        }
        if (v >= 6) {
            access(out, 2, v >= 7);
        }
        if (v >= 5) {
            u32(out, 0xFFFFFFFFL);
        }
        if (v >= 8) {
            out.write(1);
            u32(out, 123);
            string(out, "<component/>", true);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSprite", out.toByteArray()));
        FlaRecord record = a.readObject();
        assertTrue(record.isComplete());
        assertEquals(record.get("spriteRevision"), v);
        assertEquals(record.get("revision"), 10);
        assertEquals(record.get("instanceName"), "clip name");
        if (v >= 2) {
            assertEquals(((FlaRecord) record.get("actions")).get("mode"), 2L);
        }
        if (v >= 3) {
            assertEquals(((FlaRecord) record.get("properties")).get("parametersAreNew"), 0x80000001L);
        }
        if (v >= 6) {
            FlaRecord access = (FlaRecord) record.get("accessibility");
            assertEquals(access.get("name"), "name");
            assertEquals(access.get("autoLabeling"), 0xFFFFFFFFL);
            assertEquals(access.get("tabIndex"), v >= 7 ? "7" : null);
        }
        if (v >= 8) {
            assertEquals(((FlaRecord) record.get("componentXml")).get("xml"), "<component/>");
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "buttons")
    public Object[][] buttons() {
        return new Object[][]{{0}, {1}, {3}, {4}, {5}, {6}, {7}, {8}, {11}};
    }

    @Test(dataProvider = "buttons")
    public void buttonDoesNotUseTheMovieClipExtensionLayout(int v) throws Exception {
        ByteArrayOutputStream out = symbol(10);
        out.write(v);
        if (v == 3 || v == 4) {
            u32(out, 0);
        }
        if (v >= 5) {
            u32(out, 0);
            u32(out, 2);
        }
        if (v >= 4) {
            out.write(0x80);
        }
        if (v >= 6) {
            string(out, "button name", v >= 11);
        }
        if (v >= 8) {
            access(out, 2, v >= 11);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicButton", out.toByteArray()));
        FlaRecord record = a.readObject();
        assertTrue(record.isComplete());
        assertEquals(record.get("buttonRevision"), v);
        assertNull(record.get("spriteRevision"));
        if (v >= 4) {
            assertEquals(record.get("trackAsMenu"), 0x80);
        }
        if (v >= 6) {
            assertEquals(record.get("instanceName"), "button name");
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void truncatedComponentRetainsItsNameAndParentPrefix() throws Exception {
        ByteArrayOutputStream component = new ByteArrayOutputStream();
        string(component, "name", true);
        ByteArrayOutputStream payload = symbol(11, 0, null, 255, component.toByteArray());
        MfcArchive a = archive(object("CPicSymbol", payload.toByteArray()));
        try {
            a.readObject();
            fail("Expected truncated component");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Known strings must not be unsupported", ex);
        } catch (IOException expected) {
            FlaRecord record = a.getObjects().get(0);
            assertEquals(record.get("libraryId"), 0xFEEDBEADL);
            assertEquals(record.get("timelineEffectPresent"), 255);
            FlaRecord metadata = (FlaRecord) record.get("timelineEffect");
            assertNotNull(metadata);
            assertEquals(metadata.get("effectName"), "name");
            assertFalse(metadata.isComplete());
            assertFalse(record.isComplete());
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void everyTruncatedSymbolPrefixIsRejected() throws Exception {
        byte[] bytes = object("CPicSymbol", symbol(23).toByteArray());
        for (int length = 17; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Truncation must be malformed", ex);
            } catch (IOException expected) {
                assertFalse(a.getObjects().get(0).isComplete());
            }
        }
    }

    @Test
    public void futureClassAndColorRevisionsStayUnsupported() throws Exception {
        ByteArrayOutputStream future = new ByteArrayOutputStream();
        base(future);
        future.write(24);
        future.write(99);
        MfcArchive a = archive(object("CPicSymbol", future.toByteArray()));
        try {
            a.readObject();
            fail("Expected unsupported symbol revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.getRemaining(), 1);
        }
        ByteArrayOutputStream bad = new ByteArrayOutputStream();
        base(bad);
        bad.write(7);
        words(bad, 6);
        u16(bad, 0);
        u16(bad, 0);
        bad.write(2);
        bad.write(99);
        MfcArchive b = archive(object("CPicSymbol", bad.toByteArray()));
        try {
            b.readObject();
            fail("Expected unsupported color revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(b.getRemaining(), 1);
        }
    }

    @Test
    public void emptyFilterListsHaveAKnownBoundaryAndNonemptyListsRetainTheirType() throws Exception {
        byte[] prefix = symbol(15).toByteArray();
        prefix[prefix.length - 1] = 1;
        ByteArrayOutputStream empty = new ByteArrayOutputStream();
        empty.write(prefix);
        u32(empty, 0);
        u16(empty, 0xBEEF);
        MfcArchive a = archive(object("CPicSymbol", empty.toByteArray()));
        FlaRecord record = a.readObject();
        assertEquals(((FlaRecord) record.get("filters")).get("count"), 0);
        assertEquals(a.readUI16(), 0xBEEF);
        ByteArrayOutputStream nonempty = new ByteArrayOutputStream();
        nonempty.write(prefix);
        u32(nonempty, 1);
        nonempty.write(5);
        nonempty.write(99);
        MfcArchive b = archive(object("CPicSymbol", nonempty.toByteArray()));
        try {
            b.readObject();
            fail("Expected unsupported filter");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord filters = (FlaRecord) b.getObjects().get(0).get("filters");
            assertEquals(filters.get("count"), 1);
            assertEquals(filters.get("firstType"), 5);
            assertFalse(filters.isComplete());
            assertEquals(b.getRemaining(), 1);
        }
    }

    private ByteArrayOutputStream symbol(int v) throws Exception {
        return symbol(v, 0, null);
    }

    ByteArrayOutputStream symbol(int v, int present, byte[] persistent) throws Exception {
        return symbol(v, present, persistent, 0, null);
    }

    ByteArrayOutputStream symbol(int v, int present, byte[] persistent, int componentPresent, byte[] component) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(v);
        words(out, 6);
        u16(out, 0xFFEE);
        if (v >= 2) {
            u16(out, 0xABCD);
        }
        if (v >= 7) {
            out.write(1);
        }
        if (v >= 3) {
            if (v >= 5) {
                u16(out, 256);
                u16(out, -1);
            }
            for (int i = 2; i <= 7; i++) {
                u16(out, -i);
            }
        }
        if (v >= 4) {
            u16(out, 0x8000);
            u16(out, -42);
            u32(out, 0x87654321L);
        }
        if (v >= 6) {
            string(out, "legacy", false);
        }
        u32(out, 0xFEEDBEADL);
        if (v == 8 || v == 9) {
            string(out, "class", false);
            string(out, "source.as", false);
            out.write(1);
            u32(out, 0);
            u32(out, 123);
            if (v == 9) {
                string(out, "component", false);
            }
        }
        if (v >= 11) {
            out.write(componentPresent);
            if (componentPresent != 0) {
                out.write(component);
            }
        }
        if (v >= 12) {
            out.write(present);
            if (present != 0) {
                out.write(persistent);
            }
        }
        if (v >= 14) {
            out.write(3);
        }
        if (v >= 15) {
            out.write(0);
        }
        if (v >= 16) {
            out.write(0xFE);
        }
        if (v >= 17) {
            u16(out, 0xFF80);
        }
        if (v >= 20) {
            for (int i = 0; i < 16; i++) {
                f32(out, i - 0.25f);
            }
            f64(out, -1.1);
            f64(out, 2.2);
            f64(out, 3.3);
        }
        if (v >= 21) {
            u32(out, Integer.MIN_VALUE);
            u32(out, -5);
            u32(out, 17);
        }
        if (v >= 22) {
            u16(out, 2);
            u16(out, 65535);
            u16(out, 32768);
            u16(out, 7);
            u16(out, 8);
        }
        if (v >= 23) {
            u16(out, 0xFABC);
            u32(out, 0x80000000L);
            u32(out, 0x87654321L);
            u32(out, 0xFFFFFFFFL);
        }
        return out;
    }
}
