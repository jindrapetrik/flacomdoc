package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.FlaWriter;
import com.jpexs.flash.fla.converter.GradientEntry;
import com.jpexs.flash.fla.converter.TimelineConverter;
import com.jpexs.flash.fla.converter.filters.*;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class FilterRecordReaderTest {

    private static final int[] TYPES = {0, 1, 2, 3, 4, 6, 7};
    private static final String[] NAMES = {"DropShadowFilter", "BlurFilter", "GlowFilter", "BevelFilter", "GradientGlowFilter", "AdjustColorFilter", "GradientBevelFilter"};
    private static final long RGBA = 0xAB785634L;

    @DataProvider(name = "revisions")
    public Object[][] revisions() {
        List<Object[]> cases = new ArrayList<>();
        for (int type : TYPES) {
            int max = type == 0 ? 4 : type <= 3 ? 3 : 1;
            for (int revision = 0; revision <= max; revision++) {
                cases.add(new Object[]{type, revision});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "revisions")
    public void everyFilterRevisionPreservesItsBoundaryAndOriginalFields(int type, int revision) throws Exception {
        ByteArrayOutputStream out = filter(type, revision, 4, 1, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a);
        assertTrue(r.isComplete());
        assertEquals(r.get("type"), type);
        assertEquals(r.get("revision"), revision);
        if (revision > 0) {
            if (type == 0) {
                checkShadow(r, revision, 1);
            }
            if (type == 1 && revision == 1) {
                assertEquals(r.get("legacyColorRgba"), RGBA);
                assertEquals(r.get("blurX"), 17.25f);
                assertEquals(r.get("blurY"), 9.5f);
            }
            if (type == 1 && revision == 2) {
                assertEquals(r.get("blurX"), 17.25f);
                assertEquals(r.get("blurY"), 9.5f);
                checkState((FlaRecord) r.get("state"), 1);
            }
            if (type == 2 && revision <= 2) {
                assertEquals(r.get("colorRgba"), RGBA);
                assertEquals(r.get("blurX"), 17.25f);
                assertEquals(r.get("knockout"), 0xFFFFFFFFL);
            }
            if ((type == 1 || type == 2 || type == 3) && revision == 3) {
                checkShadow((FlaRecord) r.get("settings"), 4, 1);
            }
            if (type == 3 && revision == 2) {
                assertEquals(r.get("distance"), -12.5f);
                assertEquals(r.get("blurX"), 17.25f);
                assertEquals(r.get("legacyByte"), 0xFE);
                assertEquals(r.get("legacyWord"), 0x87654321L);
                assertNull(r.get("strengthPercent"));
            }
            if (type == 3 && revision >= 2) {
                assertEquals(r.get("highlightColorRgba"), 0xCC563412L);
                assertEquals(r.get("full"), 0x80000001L);
            }
            if (type == 4) {
                checkGradient(r, 2);
            }
            if (type == 7) {
                checkGradient((FlaRecord) r.get("gradient"), 2);
            }
            if (type == 6) {
                checkState((FlaRecord) r.get("state"), 1);
                assertEquals(r.get("brightness"), -25.5f);
                assertEquals(r.get("contrast"), 50.25f);
                assertEquals(r.get("saturation"), 75.5f);
                assertEquals(r.get("hue"), -179.25f);
            }
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(a.getObjects().size(), 0);
    }

    @Test(dataProvider = "revisions")
    public void everyTruncatedFilterRetainsItsConsumedPrefix(int type, int revision) throws Exception {
        byte[] bytes = filter(type, revision, 4, 1, 2).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] record = new FlaRecord[1];
            try {
                a.readInline("filter", r -> record[0] = r, FilterRecordDecoder::filter);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known revisions must not be unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(record[0]);
                assertFalse(record[0].isComplete());
                assertEquals(record[0].getConsumedData().size(), a.getPosition());
            }
        }
    }

    @DataProvider(name = "nested")
    public Object[][] nested() {
        List<Object[]> cases = new ArrayList<>();
        for (int type : new int[]{1, 2, 3, 4, 7}) {
            for (int settings = 0; settings <= 4; settings++) {
                for (int state = 0; state <= 1; state++) {
                    cases.add(new Object[]{type, settings, state});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "nested")
    public void inheritedSettingsAndEnabledStateHaveIndependentRevisions(int type, int settings, int state) throws Exception {
        ByteArrayOutputStream out = filter(type, type <= 3 ? 3 : 1, settings, state, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a);
        if (type == 7) {
            r = (FlaRecord) r.get("gradient");
        }
        checkShadow((FlaRecord) r.get("settings"), settings, state);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "gradients")
    public Object[][] gradients() {
        return new Object[][]{{4, 0}, {4, 1}, {4, 15}, {7, 0}, {7, 1}, {7, 15}};
    }

    @Test(dataProvider = "gradients")
    public void gradientStopsRetainDwordRatiosAndRgbaIncludingCapacityBoundary(int type, int count) throws Exception {
        ByteArrayOutputStream out = filter(type, 1, 4, 1, count);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a);
        if (type == 7) {
            r = (FlaRecord) r.get("gradient");
        }
        checkGradient(r, count);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void countedListPreservesOrderRepeatedTypesAndInlineByteRanges() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 4);
        int[] types = {6, 0, 6, 7};
        int[] lengths = new int[4];
        for (int i = 0; i < types.length; i++) {
            byte[] b = filter(types[i], 1, 4, 1, 2).toByteArray();
            lengths[i] = b.length;
            out.write(b);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord list = a.readInline("filters", r -> {
        }, FilterRecordDecoder::list);
        List<FlaRecord> entries = records(list, "entries");
        assertEquals(entries.size(), 4);
        assertEquals(list.get("firstType"), 6);
        int offset = 4;
        for (int i = 0; i < entries.size(); i++) {
            FlaRecord r = entries.get(i);
            assertEquals(r.get("type"), types[i]);
            assertEquals(r.getOffset(), offset);
            assertEquals(r.getConsumedData().size(), lengths[i]);
            offset += lengths[i];
        }
        assertEquals(a.getObjects().size(), 0);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void unknownTypeKeepsEarlierFiltersAndStopsBeforeTheUnknownPayload() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 2);
        out.write(filter(0, 4, 4, 1, 0).toByteArray());
        out.write(5);
        u32(out, 0x12345678);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] result = new FlaRecord[1];
        try {
            a.readInline("filters", r -> result[0] = r, FilterRecordDecoder::list);
            fail("Expected unsupported type");
        } catch (UnsupportedFlaRecordException expected) {
            List<FlaRecord> entries = records(result[0], "entries");
            assertEquals(entries.size(), 2);
            assertTrue(entries.get(0).isComplete());
            assertFalse(entries.get(1).isComplete());
            assertEquals(entries.get(1).get("type"), 5);
            assertEquals(a.readUI32(), 0x12345678L);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void futureRevisionsStopBeforeUnprovenFieldsAtEveryNestingLevel() throws Exception {
        for (int type : TYPES) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(type);
            out.write(type == 0 ? 5 : type <= 3 ? 4 : 2);
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            try {
                read(a);
                fail("Expected future filter revision");
            } catch (UnsupportedFlaRecordException expected) {
                assertEquals(a.readUI16(), 0xBEEF);
            }
        }
        for (byte[] prefix : new byte[][]{{1, 3, 5}, {0, 4, 2}, {7, 1, 2}}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(prefix);
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            try {
                read(a);
                fail("Expected future nested revision");
            } catch (UnsupportedFlaRecordException expected) {
                assertEquals(a.readUI16(), 0xBEEF);
            }
        }
    }

    @Test
    public void listCountsAndGradientCapacityAreValidatedBeforeReadingEntries() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1001);
        out.write(new byte[1001]);
        try {
            archive(out.toByteArray()).readInline("filters", r -> {
            }, FilterRecordDecoder::list);
            fail("Expected count limit");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Expected malformed count", ex);
        } catch (IOException expected) {
        }
        for (long count : new long[]{16, 0xFFFFFFFFL}) {
            ByteArrayOutputStream bad = new ByteArrayOutputStream();
            bad.write(4);
            bad.write(1);
            shadow(bad, 0, 0);
            u32(bad, count);
            u16(bad, 0xBEEF);
            MfcArchive a = archive(bad.toByteArray());
            try {
                read(a);
                fail("Expected excessive gradient count");
            } catch (UnsupportedFlaRecordException ex) {
                fail("Expected malformed count", ex);
            } catch (IOException expected) {
                assertEquals(a.readUI16(), 0xBEEF);
            }
        }
    }

    @Test
    public void rawFloatingPointValuesAreNotClampedOrConvertedToDegrees() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(6);
        out.write(1);
        state(out, 0);
        for (int bits : new int[]{0x80000000, 0x7F800000, 0xFF800000, 0x7FC01234}) {
            u32(out, bits);
        }
        FlaRecord r = read(archive(out.toByteArray()));
        assertEquals(Float.floatToRawIntBits((Float) r.get("brightness")), 0x80000000);
        assertEquals(r.get("contrast"), Float.POSITIVE_INFINITY);
        assertEquals(r.get("saturation"), Float.NEGATIVE_INFINITY);
        assertEquals(Float.floatToRawIntBits((Float) r.get("hue")), 0x7FC01234);
    }

    @DataProvider(name = "writers")
    public Object[][] writers() {
        List<Object[]> cases = new ArrayList<>();
        for (int type : TYPES) {
            for (boolean enabled : new boolean[]{false, true}) {
                cases.add(new Object[]{type, enabled});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "writers")
    public void filtersWrittenByExistingConvertersDecodeTheirNondefaultValues(int type, boolean enabled) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer(type, enabled).write(new FlaWriter(out, FlaFormatVersion.CS4, "windows-1250"));
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a);
        assertEquals(r.get("type"), type);
        FlaRecord settings = type == 7 ? (FlaRecord) ((FlaRecord) r.get("gradient")).get("settings")
                : type > 0 && type != 6 ? (FlaRecord) r.get("settings") : r;
        assertEquals(((FlaRecord) settings.get("state")).get("enabled"), enabled ? 1L : 0L);
        if (type == 6) {
            assertEquals(r.get("brightness"), 12.5f);
            assertEquals(r.get("hue"), -45.5f);
        } else {
            assertEquals(settings.get("blurX"), 11.25f);
            assertEquals(settings.get("blurY"), 7.5f);
        }
        if (type == 0) {
            assertEquals(settings.get("colorRgba"), 0x04030201L);
            assertEquals(settings.get("hideObject"), 1L);
        }
        if (type == 4 || type == 7) {
            FlaRecord gradient = type == 7 ? (FlaRecord) r.get("gradient") : r;
            assertEquals(records(gradient, "stops").size(), 2);
            assertEquals(records(gradient, "stops").get(0).get("ratio"), 64L);
            assertEquals(records(gradient, "stops").get(1).get("colorRgba"), 0x08070605L);
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "timelines")
    public Object[][] timelines() {
        List<Object[]> cases = new ArrayList<>();
        for (FlaFormatVersion version : FlaFormatVersion.values()) {
            if (version.ordinal() < FlaFormatVersion.F8.ordinal()) {
                continue;
            }
            for (boolean text : new boolean[]{false, true}) {
                for (int type : TYPES) {
                    cases.add(new Object[]{version, text, type});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "timelines")
    public void symbolAndTextFiltersKeepTheFollowingTimelineFieldsAligned(FlaFormatVersion version, boolean text, int type) throws Exception {
        int index = 0;
        while (TYPES[index] != type) {
            index++;
        }
        String filter = "<filters><" + NAMES[index] + " blurX='11.25' blurY='7.5' isEnabled='false' strength='1.75' angle='30'>"
                + (type == 4 || type == 7 ? "<GradientEntry color='#123456' ratio='0.25' alpha='0.5'/><GradientEntry color='#ABCDEF' ratio='1'/>" : "")
                + "</" + NAMES[index] + "></filters>";
        String element = text ? "<DOMDynamicText name='textInstance' width='100' height='20'>" + filter
                + "<textRuns><DOMTextRun><characters>Filtered</characters><textAttrs><DOMTextAttrs face='TimesNewRomanPSMT' size='12'/></textAttrs></DOMTextRun></textRuns></DOMDynamicText>"
                : "<DOMSymbolInstance libraryItemName='item' symbolType='movie clip' name='clipInstance'>" + filter + "</DOMSymbolInstance>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TimelineConverter(version, "windows-1250", "test").convert(xml("<DOMTimeline><layers><DOMLayer name='after filters'><frames><DOMFrame index='0' duration='3'><elements>" + element + "</elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline>"),
                xml("<DOMDocument><symbols><Include href='item.xml'/></symbols></DOMDocument>"), out, null);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Timeline", out.toByteArray());
        FlaStream stream = new BinaryFlaReader(ANSI).readStreams(streams).getStream("Timeline");
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        FlaRecord instance = null;
        for (FlaRecord r : stream.getObjects()) {
            if (r.getClassName().equals(text ? "CPicText" : "CPicSprite")) {
                instance = r;
                break;
            }
        }
        assertNotNull(instance);
        assertTrue(instance.isComplete());
        assertEquals(instance.get("instanceName"), text ? "textInstance" : "clipInstance");
        if (text) {
            assertEquals(instance.get("text"), "Filtered");
        }
        FlaRecord list = (FlaRecord) instance.get("filters");
        assertEquals(list.get("count"), 1);
        assertEquals(records(list, "entries").get(0).get("type"), type);
        assertEquals(stream.getRoot().getChildren().get(0).get("name"), "after filters");
    }

    private static FilterInterface writer(int type, boolean enabled) {
        List<GradientEntry> stops = Arrays.asList(new GradientEntry(new Color(1, 2, 3, 4), 0.25f), new GradientEntry(new Color(5, 6, 7, 8), 1f));
        switch (type) {
            case 0:
                return new DropShadowFilter(11.25f, 7.5f, 1.75f, 3, 30, 9, true, true, true, new Color(1, 2, 3, 4), enabled);
            case 1:
                return new BlurFilter(11.25f, 7.5f, 3, enabled);
            case 2:
                return new GlowFilter(11.25f, 7.5f, new Color(1, 2, 3, 4), true, true, 3, 1.75f, enabled);
            case 3:
                return new BevelFilter(11.25f, 7.5f, 1.75f, 3, new Color(1, 2, 3, 4), new Color(5, 6, 7, 8), 30, 9, true, BevelFilter.TYPE_FULL, enabled);
            case 4:
                return new GradientGlowFilter(11.25f, 7.5f, 1.75f, 3, 30, 9, true, GradientGlowFilter.TYPE_FULL, stops, enabled);
            case 6:
                return new AdjustColorFilter(12.5f, 25.5f, 35.5f, -45.5f, enabled);
            case 7:
                return new GradientBevelFilter(11.25f, 7.5f, 1.75f, 3, 30, 9, true, GradientBevelFilter.TYPE_FULL, stops, enabled);
            default:
                throw new AssertionError(type);
        }
    }

    private static void checkState(FlaRecord r, int revision) {
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("enabled"), revision == 0 ? null : 0x80000001L);
    }

    private static void checkShadow(FlaRecord r, int revision, int state) {
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), revision);
        if (revision == 0) {
            assertNull(r.get("state"));
            assertNull(r.get("colorRgba"));
            return;
        }
        assertEquals(r.get("colorRgba"), RGBA);
        assertEquals(r.get("angleRadians"), -0.25f);
        assertEquals(r.get("blurX"), 17.25f);
        assertEquals(r.get("distance"), -12.5f);
        assertEquals(r.get("inner"), 0x80000000L);
        if (revision >= 2) {
            checkState((FlaRecord) r.get("state"), state);
            assertEquals(r.get("quality"), 0xFFFFFFFFL);
        }
        if (revision >= 3) {
            assertEquals(r.get("blurY"), 9.5f);
            assertEquals(r.get("strengthPercent"), 0x80000023L);
        } else {
            assertNull(r.get("blurY"));
            assertNull(r.get("strengthPercent"));
        }
        assertEquals(r.get("hideObject"), revision >= 4 ? 0x80000002L : null);
    }

    private static void checkGradient(FlaRecord r, int count) {
        assertEquals(r.get("stopCount"), (long) count);
        assertEquals(r.get("gradientValue"), 0x87654321L);
        assertEquals(r.get("full"), 0x80000001L);
        List<FlaRecord> stops = records(r, "stops");
        assertEquals(stops.size(), count);
        for (int i = 0; i < count; i++) {
            assertEquals(stops.get(i).get("ratio"), 0x80000000L + i);
            assertEquals(stops.get(i).get("colorRgba"), RGBA + i);
        }
    }

    private static FlaRecord read(MfcArchive a) throws IOException {
        return a.readInline("filter", r -> {
        }, FilterRecordDecoder::filter);
    }

    private static Element xml(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    private static ByteArrayOutputStream filter(int type, int revision, int settings, int state, int count) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(type);
        if (type == 0) {
            shadow(out, revision, state);
            return out;
        }
        out.write(revision);
        if (revision == 0) {
            return out;
        }
        if (type == 1) {
            if (revision == 1) {
                u32(out, RGBA);
                f32(out, 17.25f);
                f32(out, 9.5f);
            }
            if (revision == 2) {
                f32(out, 17.25f);
                f32(out, 9.5f);
                state(out, state);
                u32(out, 0xFFFFFFFFL);
            }
            if (revision == 3) {
                shadow(out, settings, state);
            }
        }
        if (type == 2) {
            if (revision <= 2) {
                u32(out, RGBA);
                f32(out, 17.25f);
                u32(out, 0x80000000L);
                u32(out, 0xFFFFFFFFL);
                if (revision == 2) {
                    state(out, state);
                    u32(out, 0xFFFFFFFFL);
                }
            }
            if (revision == 3) {
                shadow(out, settings, state);
            }
        }
        if (type == 3) {
            if (revision == 2) {
                f32(out, -0.25f);
                u32(out, RGBA);
                f32(out, 17.25f);
                f32(out, -12.5f);
                u32(out, 0x80000000L);
                u32(out, 0xFFFFFFFFL);
                state(out, state);
                u32(out, 0xFFFFFFFFL);
                out.write(0xFE);
                u32(out, 0xCC563412L);
                u32(out, 0x87654321L);
                u32(out, 0x80000001L);
            }
            if (revision == 3) {
                shadow(out, settings, state);
                u32(out, 0xCC563412L);
                u32(out, 0x80000001L);
            }
        }
        if (type == 4) {
            gradient(out, settings, state, count);
        }
        if (type == 6) {
            state(out, state);
            f32(out, -25.5f);
            f32(out, 50.25f);
            f32(out, 75.5f);
            f32(out, -179.25f);
        }
        if (type == 7) {
            out.write(1);
            gradient(out, settings, state, count);
        }
        return out;
    }

    private static void gradient(ByteArrayOutputStream out, int settings, int state, int count) throws IOException {
        shadow(out, settings, state);
        u32(out, count);
        u32(out, 0x87654321L);
        u32(out, 0x80000001L);
        for (int i = 0; i < count; i++) {
            u32(out, 0x80000000L + i);
            u32(out, RGBA + i);
        }
    }

    private static void shadow(ByteArrayOutputStream out, int revision, int state) {
        out.write(revision);
        if (revision == 0) {
            return;
        }
        if (revision <= 2) {
            f32(out, -0.25f);
            u32(out, RGBA);
            f32(out, 17.25f);
            f32(out, -12.5f);
            u32(out, 0x80000000L);
            u32(out, 0xFFFFFFFFL);
            if (revision == 2) {
                state(out, state);
                u32(out, 0xFFFFFFFFL);
            }
        } else {
            state(out, state);
            u32(out, RGBA);
            f32(out, -12.5f);
            f32(out, 17.25f);
            f32(out, 9.5f);
            f32(out, -0.25f);
            u32(out, 0x80000000L);
            u32(out, 0xFFFFFFFFL);
            u32(out, 0xFFFFFFFFL);
            u32(out, 0x80000023L);
            if (revision >= 4) {
                u32(out, 0x80000002L);
            }
        }
    }

    private static void state(ByteArrayOutputStream out, int revision) {
        out.write(revision);
        if (revision != 0) {
            u32(out, 0x80000001L);
        }
    }
}
