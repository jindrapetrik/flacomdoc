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

public class SymbolComponentRecordReaderTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        Object[][] cases = new Object[13][1];
        for (int v = 11; v <= 23; v++) {
            cases[v - 11][0] = v;
        }
        return cases;
    }

    @DataProvider(name = "contexts")
    public Object[][] contexts() {
        List<Object[]> cases = new ArrayList<>();
        for (int v = 11; v <= 23; v++) {
            for (int present : new int[]{1, 255}) {
                for (boolean unicode : new boolean[]{false, true}) {
                    cases.add(new Object[]{v, present, unicode});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "contexts")
    public void parentRevisionSelectsStringsAndAllSevenParameterTypes(int revision, int present, boolean unicode) throws Exception {
        ByteArrayOutputStream component = component(revision, unicode);
        ByteArrayOutputStream out = symbol(revision, present, component.toByteArray());
        ByteArrayOutputStream base = new ByteArrayOutputStream();
        base(base);
        out.write(object("CPicObj", base.toByteArray()));
        u16(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSymbol", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("timelineEffectPresent"), present);
        FlaRecord metadata = (FlaRecord) r.get("timelineEffect");
        assertTrue(metadata.isComplete());
        assertNull(metadata.get("revision"));
        assertEquals(metadata.getObjectId(), 0);
        assertEquals(metadata.get("effectName"), text("s0", unicode));
        assertEquals(metadata.get("groupName"), text("s8", unicode));
        assertEquals(metadata.get("symbolType"), text("s12", unicode));
        assertEquals(metadata.get("originalLayerName"), revision >= 18 ? text("s32", unicode) : null);
        assertEquals(metadata.get("effectId"), revision >= 19 ? text("s4", unicode) : null);
        assertEquals(metadata.get("sourceScriptFilename"), text("s16", unicode));
        List<FlaRecord> parameters = records(metadata, "parameters");
        assertEquals(parameters.size(), 7);
        assertEquals(metadata.get("count"), 7);
        for (int type = 0; type <= 6; type++) {
            FlaRecord p = parameters.get(type);
            assertTrue(p.isComplete());
            assertEquals(p.get("name"), "duplicate");
            assertEquals(p.get("type"), (long) type);
            assertEquals(p.getObjectId(), 0);
            assertEquals(p.get("minimum"), 0xFFFFFFFFL);
            assertEquals(p.get("maximum"), 0x80000001L);
            assertEquals(p.get("maximumLength"), 0xFEDCBA98L);
            assertEquals(p.get("variableName"), text("metadata", unicode));
            assertEquals(p.get("displayOrder"), 0xABCDEF01L);
        }
        assertEquals(parameters.get(0).get("value"), Integer.MIN_VALUE);
        assertEquals(parameters.get(1).get("value"), Double.NEGATIVE_INFINITY);
        assertEquals(parameters.get(2).get("value"), text("value\0", unicode));
        assertEquals(parameters.get(3).get("value"), 0xFFFFFFFFL);
        assertEquals(parameters.get(4).get("value"), Integer.MIN_VALUE + 1);
        assertEquals(parameters.get(5).get("values"), Arrays.asList("same", "same", text("last", unicode)));
        assertEquals(parameters.get(5).get("selectedIndex"), -1);
        assertEquals(Double.doubleToRawLongBits((Double) parameters.get(6).get("value")), Long.MIN_VALUE);
        if (revision >= 12) {
            assertTrue(((FlaRecord) r.get("persistentData")).isComplete());
            assertEquals(((FlaRecord) r.get("persistentData")).get("count"), 0);
        }
        if (revision >= 23) {
            assertEquals(r.get("isVisible"), 0xFFFFFFFFL);
        }
        FlaRecord next = a.readObject();
        assertEquals(next.getClassName(), "CPicObj");
        assertEquals(next.getObjectId(), 4);
        assertTrue(next.isComplete());
        assertSame(a.readObject(), r);
        assertEquals(a.getObjects().size(), 2);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "contexts")
    public void everyTruncatedSymbolAndComponentRetainsItsOriginalPrefix(int revision, int present, boolean unicode) throws Exception {
        byte[] bytes = object("CPicSymbol", symbol(revision, present, component(revision, unicode).toByteArray()).toByteArray());
        for (int length = 6 + "CPicSymbol".length(); length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known component must not become unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test(dataProvider = "versions")
    public void unknownParameterTypesKeepTheirNameAndStopBeforePayload(int revision) throws Exception {
        for (long type : new long[]{7, 0x80000000L, 0xFFFFFFFFL}) {
            ByteArrayOutputStream out = prefix(revision, true);
            u32(out, 2);
            parameter(out, 0, true);
            string(out, "future", true);
            u32(out, type);
            out.write(new byte[]{42, 43});
            MfcArchive a = archive(object("CPicSymbol", symbol(revision, 1, out.toByteArray()).toByteArray()));
            try {
                a.readObject();
                fail("Expected unknown type");
            } catch (UnsupportedFlaRecordException expected) {
                FlaRecord r = a.getObjects().get(0);
                FlaRecord component = (FlaRecord) r.get("timelineEffect");
                List<FlaRecord> params = records(component, "parameters");
                assertEquals(params.size(), 2);
                assertTrue(params.get(0).isComplete());
                assertFalse(params.get(1).isComplete());
                assertFalse(component.isComplete());
                assertFalse(r.isComplete());
                assertEquals(params.get(1).get("name"), "future");
                assertEquals(params.get(1).get("type"), type);
                assertEquals(a.readUI8(), 42);
            }
        }
    }

    @Test(dataProvider = "versions")
    public void nonpositiveParameterCountsConsumeNoEntries(int revision) throws Exception {
        for (int count : new int[]{0, -1, Integer.MIN_VALUE}) {
            ByteArrayOutputStream component = prefix(revision, false);
            u32(component, count);
            ByteArrayOutputStream out = symbol(revision, 128, component.toByteArray());
            u16(out, 0xBEEF);
            MfcArchive a = archive(object("CPicSymbol", out.toByteArray()));
            FlaRecord r = a.readObject();
            assertTrue(r.isComplete());
            FlaRecord metadata = (FlaRecord) r.get("timelineEffect");
            assertEquals(metadata.get("count"), count);
            assertTrue(records(metadata, "parameters").isEmpty());
            assertEquals(a.readUI16(), 0xBEEF);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @DataProvider(name = "lists")
    public Object[][] lists() {
        List<Object[]> cases = new ArrayList<>();
        for (int v : new int[]{11, 18, 19, 23}) {
            for (int count : new int[]{Integer.MIN_VALUE, -1, 0, 1, 3}) {
                cases.add(new Object[]{v, count});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "lists")
    public void stringListsAlwaysReadTheirSignedSelectionAfterTheEffectiveElements(int revision, int count) throws Exception {
        ByteArrayOutputStream component = prefix(revision, true);
        u32(component, 1);
        string(component, "list", true);
        u32(component, 5);
        u32(component, count);
        for (int i = 0; i < Math.max(0, count); i++) {
            string(component, "item", i % 2 == 0);
        }
        u32(component, Integer.MIN_VALUE);
        trailer(component, true);
        ByteArrayOutputStream out = symbol(revision, 255, component.toByteArray());
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSymbol", out.toByteArray()));
        FlaRecord r = a.readObject();
        FlaRecord p = records((FlaRecord) r.get("timelineEffect"), "parameters").get(0);
        assertEquals(p.get("count"), count);
        assertEquals(((List<?>) p.get("values")).size(), Math.max(0, count));
        assertEquals(p.get("selectedIndex"), Integer.MIN_VALUE);
        assertTrue(p.isComplete());
        assertTrue(r.isComplete());
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test(dataProvider = "versions")
    public void countsStringLengthsAndDepthAreBoundedWithinTheComponent(int revision) throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            ByteArrayOutputStream component = prefix(revision, true);
            u32(component, mode == 0 ? 1001 : 1);
            if (mode > 0) {
                string(component, "param", true);
                u32(component, mode == 1 ? 5 : mode == 2 ? 2 : 0);
            }
            if (mode == 1) {
                u32(component, Integer.MAX_VALUE);
            }
            if (mode == 2) {
                component.write(255);
                u16(component, 65535);
                u32(component, 0xFFFFFFFEL);
            }
            if (mode == 3) {
                u32(component, 123);
                trailer(component, true);
            }
            byte[] bytes = object("CPicSymbol", symbol(revision, 1, component.toByteArray()).toByteArray());
            MfcArchive a = mode == 3 ? new MfcArchive(new BinaryData(bytes), "test", new FlaReadOptions(ANSI, 1024 * 1024, 1024 * 1024, 1000, 2), DefaultFlaDecoders.create(), 0) : archive(bytes);
            try {
                a.readObject();
                fail("Expected limit");
            } catch (UnsupportedFlaRecordException ex) {
                fail("Length/depth limits must be malformed", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertNotNull(r.get("timelineEffect"));
                if (mode == 3) {
                    assertTrue(expected.getMessage().contains("nesting"));
                }
            }
        }
    }

    @Test
    public void extendedStringsAndDoubleNanBitsRetainTheirExactEncoding() throws Exception {
        char[] chars = new char[300];
        Arrays.fill(chars, '\u03a9');
        String text = new String(chars);
        ByteArrayOutputStream component = prefix(23, true);
        u32(component, 2);
        string(component, text, true);
        u32(component, 2);
        string(component, text, true);
        trailer(component, true);
        string(component, "number", false);
        u32(component, 6);
        long nan = 0x7FF8000012345678L;
        u32(component, nan);
        u32(component, nan >>> 32);
        trailer(component, false);
        ByteArrayOutputStream out = symbol(23, 1, component.toByteArray());
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSymbol", out.toByteArray()));
        FlaRecord r = a.readObject();
        List<FlaRecord> params = records((FlaRecord) r.get("timelineEffect"), "parameters");
        assertEquals(params.get(0).get("name"), text);
        assertEquals(params.get(0).get("value"), text);
        assertEquals(Double.doubleToRawLongBits((Double) params.get(1).get("value")), nan);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "derived")
    public Object[][] derived() {
        List<Object[]> cases = new ArrayList<>();
        for (int v : new int[]{11, 18, 19, 23}) {
            for (String type : new String[]{"CPicSprite", "CPicButton"}) {
                cases.add(new Object[]{v, type});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "derived")
    public void spriteAndButtonExtensionsFollowTheirInheritedComponent(int revision, String type) throws Exception {
        ByteArrayOutputStream out = symbol(revision, 1, component(revision, true).toByteArray());
        out.write(0);
        if (type.equals("CPicSprite")) {
            string(out, "clip", false);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(object(type, out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertTrue(((FlaRecord) r.get("timelineEffect")).isComplete());
        assertEquals(r.get(type.equals("CPicSprite") ? "spriteRevision" : "buttonRevision"), 0);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void publicPageModelRetainsAnUnknownComponentParameterAndRemainingBytes() throws Exception {
        ByteArrayOutputStream component = prefix(19, true);
        u32(component, 1);
        string(component, "future", true);
        u32(component, 7);
        component.write(new byte[]{42, 43, 44});
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.write(1);
        page.write(object("CPicPage", new byte[]{1, 0}));
        page.write(object("CPicSymbol", symbol(19, 1, component.toByteArray()).toByteArray()));
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Page 1", page.toByteArray());
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).readStreams(streams);
        FlaStream stream = doc.getStream("Page 1");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertEquals(stream.getIssues().get(0).getKind(), FlaParseIssue.Kind.UNSUPPORTED);
        FlaRecord symbol = stream.getRoot().getChildren().get(0);
        FlaRecord metadata = (FlaRecord) symbol.get("timelineEffect");
        assertEquals(records(metadata, "parameters").get(0).get("name"), "future");
        assertFalse(symbol.isComplete());
        assertEquals(stream.getData().toByteArray(), page.toByteArray());
        assertEquals(stream.getUnparsedData().unsignedByte(0), 42);
    }

    private static String text(String value, boolean unicode) {
        return value + (unicode ? " \u03a9" : " \u010d");
    }

    private static ByteArrayOutputStream prefix(int revision, boolean unicode) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String field : new String[]{"s0", "s8", "s12"}) {
            string(out, text(field, unicode), unicode);
        }
        if (revision >= 18) {
            string(out, text("s32", unicode), unicode);
        }
        if (revision >= 19) {
            string(out, text("s4", unicode), unicode);
        }
        string(out, text("s16", unicode), unicode);
        return out;
    }

    private static ByteArrayOutputStream component(int revision, boolean unicode) throws Exception {
        ByteArrayOutputStream out = prefix(revision, unicode);
        u32(out, 7);
        for (int type = 0; type <= 6; type++) {
            parameter(out, type, unicode);
        }
        return out;
    }

    private static void parameter(ByteArrayOutputStream out, int type, boolean unicode) throws Exception {
        string(out, "duplicate", unicode);
        u32(out, type);
        switch (type) {
            case 0:
                u32(out, Integer.MIN_VALUE);
                break;
            case 1:
                f64(out, Double.NEGATIVE_INFINITY);
                break;
            case 2:
                string(out, text("value\0", unicode), unicode);
                break;
            case 3:
                u32(out, 0xFFFFFFFFL);
                break;
            case 4:
                u32(out, Integer.MIN_VALUE + 1);
                break;
            case 5:
                u32(out, 3);
                string(out, "same", false);
                string(out, "same", true);
                string(out, text("last", unicode), unicode);
                u32(out, -1);
                break;
            case 6:
                f64(out, -0.0);
                break;
            default:
                throw new AssertionError();
        }
        trailer(out, unicode);
    }

    private static void trailer(ByteArrayOutputStream out, boolean unicode) throws Exception {
        u32(out, 0xFFFFFFFFL);
        u32(out, 0x80000001L);
        u32(out, 0xFEDCBA98L);
        string(out, text("metadata", unicode), unicode);
        u32(out, 0xABCDEF01L);
    }

    private static ByteArrayOutputStream symbol(int revision, int present, byte[] component) throws Exception {
        ByteArrayOutputStream persistent = new ByteArrayOutputStream();
        u32(persistent, 1);
        u32(persistent, 0);
        return new InstanceRecordReaderTest().symbol(revision, 1, persistent.toByteArray(), present, component);
    }
}
