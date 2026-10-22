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

public class AuxiliaryObjectReaderTest {

    @DataProvider(name = "objects")
    public Object[][] objects() {
        List<Object[]> cases = new ArrayList<>();
        for (String type : new String[]{"CPicASObj", "CPicThreeDObject", "CPicTLFText"}) {
            for (int v = 0; v <= 5; v++) {
                for (boolean present : new boolean[]{false, true}) {
                    cases.add(new Object[]{type, v, present});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "objects")
    public void readsVersionedObjectBasesAndTlfLinks(String type, int revision, boolean present) throws Exception {
        byte[] payload = asObject(revision, present);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(payload);
        if (type.equals("CPicTLFText")) {
            out.write(255);
            string(out, "<TextFlow/>", true);
            string(out, "<properties/>", true);
            if (present) {
                u32(out, 0xFFFFFFFFL);
            }
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(object(type, out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("asObjectRevision"), revision);
        assertEquals(r.get("className"), "ClassName");
        if (revision >= 2) {
            assertEquals(((List<?>) r.get("matrix3D")).size(), 16);
            assertEquals(((List<?>) r.get("matrix3D")).get(15), 14.5f);
            assertEquals(r.get("rotation3D"), Arrays.asList(-1.25, 2.5, 3.75));
            assertEquals(r.get("selectedEffectIndex"), 0xFFFF);
            if (present) {
                assertTrue(((FlaRecord) r.get("filters")).isComplete());
            }
        }
        if (revision >= 3) {
            assertEquals(r.get("blendMode"), 255);
        }
        if (revision >= 4) {
            assertTrue(((FlaRecord) r.get("colorTransform")).isComplete());
        }
        if (revision >= 5) {
            FlaRecord relations = (FlaRecord) r.get("instanceRelations");
            assertEquals(relations.get("count"), present ? 2 : -1);
            if (present) {
                assertEquals(records(relations, "entries").get(1).get("identifier"), "id-one");
            }
        }
        if (type.equals("CPicTLFText")) {
            assertEquals(r.get("tlfRevision"), 255);
            assertEquals(r.get("xml1"), "<TextFlow/>");
            List<FlaRecord> links = records(r, "childLinks");
            assertEquals(links.size(), present ? 1 : 0);
            if (present) {
                assertSame(links.get(0).get("child"), r.getChildren().get(0));
                assertEquals(links.get(0).get("markupChildId"), 0xFFFFFFFFL);
                assertEquals(r.getTlfText().getChildLinks().get(0).getMarkupChildId(), Integer.valueOf(-1));
            }
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "objects")
    public void truncationPreservesEveryKnownObjectPrefix(String type, int revision, boolean present) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(asObject(revision, present));
        if (type.equals("CPicTLFText")) {
            out.write(1);
            string(out, "xml", true);
            string(out, "properties", true);
            if (present) {
                u32(out, 42);
            }
        }
        byte[] bytes = object(type, out.toByteArray());
        int first = object(type, new byte[0]).length;
        for (int length = first; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known object layout must not be unsupported", ex);
            } catch (IOException expected) {
                assertFalse(a.getObjects().get(0).isComplete());
                assertEquals(a.getObjects().get(0).getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @DataProvider(name = "screens")
    public Object[][] screens() {
        return new Object[][]{{0}, {1}, {2}, {3}, {4}, {5}};
    }

    @Test(dataProvider = "screens")
    public void screenAddsItsOwnFieldsAfterTheSprite(int revision) throws Exception {
        ByteArrayOutputStream out = new InstanceRecordReaderTest().symbol(10, 0, null);
        out.write(0);
        string(out, "sprite", false);
        out.write(revision);
        u32(out, 0xFFFFFFFFL);
        u32(out, 0x80000000L);
        if (revision >= 3) {
            u32(out, 42);
        }
        string(out, "stored screen name", revision >= 5);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicScreen", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("screenRevision"), revision);
        assertEquals(r.get("instanceName"), "sprite");
        assertEquals(r.get("screenName"), "stored screen name");
        assertEquals(r.get("hidden"), 0xFFFFFFFFL);
        assertEquals(r.get("autoRegUpdate"), revision >= 3 ? 42L : null);
        assertEquals(r.get("reservedScreenValue"), 0x80000000L);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void fontReferencesAndInlineFontRecordsSelectDifferentLayouts() throws Exception {
        for (int mode : new int[]{0, 1, 255}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            base(out);
            out.write(mode);
            if (mode != 0) {
                u16(out, 0xFEED);
            } else {
                out.write(0);
                string(out, "font", false);
                u16(out, 42);
                u32(out, 123);
                out.write(1);
                u32(out, 0);
                u32(out, 0);
            }
            u16(out, 0xBEEF);
            MfcArchive a = archive(object("CPicFont", out.toByteArray()));
            FlaRecord r = a.readObject();
            assertTrue(r.isComplete());
            assertEquals(r.get("fontReferenceMode"), mode);
            if (mode != 0) {
                assertEquals(r.get("fontId"), 0xFEED);
            } else {
                assertEquals(((FlaRecord) r.get("font")).get("fontId"), 42);
            }
            assertEquals(a.readUI16(), 0xBEEF);
        }
    }

    @Test
    public void fixedLegacyObjectsRespectTheirDifferentBaseEnvelopes() throws Exception {
        for (String type : new String[]{"CPicSoundCreator", "CPicTempClipboardObj", "CPicVideo", "CPicOle", "CMediaVideo", "CPicShapeObj"}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (type.equals("CMediaVideo")) {
                out.write(0);
                string(out, "M 1 2", false);
                string(out, "video", false);
                u16(out, 7);
                out.write(255);
            } else if (type.equals("CPicShapeObj")) {
                out = new InstanceRecordReaderTest().symbol(0, 0, null);
            } else {
                if (!type.equals("CPicSoundCreator")) {
                    base(out);
                }
                out.write(255);
                if (!type.equals("CPicSoundCreator")) {
                    words(out, 6);
                }
                if (type.equals("CPicOle")) {
                    words(out, 4);
                    u16(out, 42);
                }
                if (type.equals("CPicSoundCreator") || type.equals("CPicVideo")) {
                    u16(out, 0xFFFF);
                }
            }
            u16(out, 0xBEEF);
            MfcArchive a = archive(object(type, out.toByteArray()));
            FlaRecord r = a.readObject();
            assertTrue(r.isComplete(), type);
            assertEquals(a.readUI16(), 0xBEEF, type);
            assertEquals(a.getRemaining(), 0, type);
            if (type.equals("CPicSoundCreator")) {
                assertNull(r.get("baseRevision"));
            }
        }
    }

    @Test
    public void excessiveRelationCountsAndFutureBaseRevisionsRemainBounded() throws Exception {
        byte[] future = asObject(0, false);
        future[12] = 6;
        MfcArchive a = archive(object("CPicASObj", future));
        try {
            a.readObject();
            fail("Expected future base revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertEquals(a.getObjects().get(0).get("asObjectRevision"), 6);
        }
        byte[] bytes = asObject(5, false);
        int at = bytes.length - 4;
        bytes[at] = (byte) 0xFF;
        bytes[at + 1] = (byte) 0xFF;
        bytes[at + 2] = (byte) 0xFF;
        bytes[at + 3] = 0x7F;
        a = archive(object("CPicASObj", bytes));
        try {
            a.readObject();
            fail("Expected count limit");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Invalid count is malformed", ex);
        } catch (IOException expected) {
            assertFalse(a.getObjects().get(0).isComplete());
        }
    }

    private static byte[] asObject(int revision, boolean present) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1);
        out.write(0);
        if (present) {
            ByteArrayOutputStream child = new ByteArrayOutputStream();
            child.write(6);
            child.write(0);
            u16(child, 0);
            u32(child, 0);
            u32(child, 0);
            child.write(0);
            child.write(0);
            child.write(new byte[]{'i', 'd', '-', 'o', 'n', 'e', 0});
            child.write(0);
            words(child, 6);
            u16(child, 0);
            u32(child, 42);
            out.write(object("CPicShapeObj", child.toByteArray()));
        }
        u16(out, 0);
        u32(out, 0);
        u32(out, 0);
        out.write(revision);
        words(out, 10);
        string(out, "ClassName", true);
        string(out, "source", false);
        if (revision >= 2) {
            out.write(present ? 255 : 0);
            if (present) {
                u32(out, 0);
            }
            u16(out, 0xFFFF);
            for (int i = 0; i < 16; i++) {
                f32(out, i - 0.5f);
            }
            f64(out, -1.25);
            f64(out, 2.5);
            f64(out, 3.75);
            words(out, 3);
        }
        if (revision >= 3) {
            out.write(255);
        }
        if (revision >= 4) {
            out.write(1);
            for (int i = 0; i < 8; i++) {
                u16(out, -i);
            }
            u16(out, 3);
            u16(out, -42);
            u32(out, 0xFFFFFFFFL);
        }
        if (revision >= 5) {
            u32(out, present ? 2 : -1);
            if (present) {
                for (int i = 0; i < 2; i++) {
                    u32(out, 0x80000000L + i);
                    out.write(new byte[]{'i', 'd', '-', 'o', 'n', 'e', 0});
                }
            }
        }
        return out.toByteArray();
    }
}
