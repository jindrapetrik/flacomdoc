package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class PropertyRecordReaderTest {

    private static final byte[] DATA = {0, -1, 3, 0, 9};

    @DataProvider(name = "values")
    public Object[][] values() {
        List<Object[]> cases = new ArrayList<>();
        for (int revision = 0; revision <= 8; revision++) {
            for (int type : new int[]{1, 13, 17, 0xFFFF}) {
                for (boolean named : new boolean[]{false, true}) {
                    cases.add(new Object[]{revision, type, named});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "values")
    public void valueRevisionsPreserveStringEntriesTagsSelectionTablesAndData(int revision, int type, boolean named) throws Exception {
        ByteArrayOutputStream out = value(revision, type, named);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a, PropertyRecordDecoder::value);
        assertTrue(r.isComplete());
        assertEquals(r.get("revision"), revision);
        assertEquals(r.get("type"), type);
        assertEquals(r.get("count"), 2);
        assertEquals(r.get("selectedIndex"), 0xFFFFFFFFL);
        assertEquals(r.get("namedEntries"), revision == 0 ? null : named ? 0x80000001L : 0L);
        assertEquals(r.get("selectionWord"), revision >= 8 && type == 17 ? 0xFFFE : null);
        List<FlaRecord> entries = records(r, "entries");
        assertEquals(entries.size(), 2);
        for (int i = 0; i < 2; i++) {
            assertEquals(entries.get(i).get("name"), named && revision >= 1 ? "name" + i : null);
            assertEquals(entries.get(i).get("value"), text(revision >= 3) + i);
            assertEquals(entries.get(i).get("type"), revision >= 6 ? 0x8000 + i : null);
        }
        if (type == 13 && revision >= 4) {
            checkTable((FlaRecord) r.get("table"), revision);
        } else {
            assertNull(r.get("table"));
        }
        if (revision >= 8) {
            assertEquals(r.get("dataLength"), (long) DATA.length);
            assertEquals(((BinaryData) r.get("data")).toByteArray(), DATA);
        } else {
            assertNull(r.get("data"));
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(a.getObjects().size(), 0);
    }

    @Test(dataProvider = "values")
    public void everyTruncatedValueKeepsTheIncompleteInlineRecord(int revision, int type, boolean named) throws Exception {
        byte[] bytes = value(revision, type, named).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] result = new FlaRecord[1];
            try {
                a.readInline("value", r -> result[0] = r, PropertyRecordDecoder::value);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known value layout must be malformed on truncation", ex);
            } catch (IOException expected) {
                assertNotNull(result[0]);
                assertFalse(result[0].isComplete());
                assertEquals(result[0].getConsumedData().size(), a.getPosition());
            }
        }
    }

    @DataProvider(name = "properties")
    public Object[][] properties() {
        List<Object[]> cases = new ArrayList<>();
        for (int property = 0; property <= 3; property++) {
            for (int value = 0; value <= 8; value++) {
                cases.add(new Object[]{property, value});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "properties")
    public void propertyAndValueVersionsSelectIndependentMetadataAndStringEncodings(int property, int value) throws Exception {
        ByteArrayOutputStream out = property(property, value);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a, PropertyRecordDecoder::property);
        assertEquals(r.get("name"), text(property >= 2));
        assertEquals(r.get("revision"), property);
        FlaRecord nested = (FlaRecord) r.get("value");
        assertEquals(nested.get("revision"), value);
        assertEquals(records(nested, "entries").get(0).get("value"), text(value >= 3) + 0);
        if (property >= 1) {
            assertEquals(r.get("variableName"), text(property >= 2) + " extension");
            FlaRecord id = (FlaRecord) r.get("identifier");
            assertEquals(id.get("revision"), 0xFE);
            assertEquals(((BinaryData) id.get("data")).toByteArray(), identifier());
            assertEquals(id.getConsumedData().size(), 17);
        } else {
            assertNull(r.get("identifier"));
        }
        if (property >= 3) {
            assertEquals(r.get("category"), "metadata");
            assertEquals(r.get("verbosity"), 0x80000001L);
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertTrue(r.isComplete());
    }

    @Test(dataProvider = "properties")
    public void truncatedPropertyMetadataRetainsTheKnownNameAndValue(int property, int value) throws Exception {
        byte[] bytes = property(property, value).toByteArray();
        for (int length = 0; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] result = new FlaRecord[1];
            try {
                a.readInline("property", r -> result[0] = r, PropertyRecordDecoder::property);
                fail("Expected truncated property");
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known property must not be unsupported", ex);
            } catch (IOException expected) {
                assertNotNull(result[0]);
                assertFalse(result[0].isComplete());
            }
        }
    }

    @DataProvider(name = "lists")
    public Object[][] lists() {
        return new Object[][]{{0}, {1}, {2}};
    }

    @Test(dataProvider = "lists")
    public void listKeepsRepeatedNamesOrderAndItsTrailingSetting(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        u32(out, 2);
        if (revision == 0) {
            for (int i = 0; i < 2; i++) {
                u32(out, 0x80000000L + i);
                string(out, "same", true);
                string(out, text(true) + i, true);
            }
        } else {
            out.write(property(3, 8).toByteArray());
            out.write(property(3, 1).toByteArray());
        }
        if (revision >= 2) {
            u32(out, 0xFFFFFFFFL);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a, PropertyRecordDecoder::list);
        List<FlaRecord> entries = records(r, "entries");
        assertEquals(entries.size(), 2);
        assertEquals(entries.get(0).get("name"), entries.get(1).get("name"));
        if (revision == 0) {
            assertEquals(entries.get(0).get("type"), 0x80000000L);
            assertEquals(entries.get(1).get("type"), 0x80000001L);
            assertEquals(entries.get(1).get("value"), text(true) + 1);
            assertNull(entries.get(0).get("revision"));
        }
        assertEquals(r.get("parametersAreNew"), revision >= 2 ? 0xFFFFFFFFL : null);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(a.getObjects().size(), 0);
    }

    @Test
    public void emptyListsAndZeroSizedTablesHaveExactBoundaries() throws Exception {
        for (int revision = 0; revision <= 2; revision++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(revision);
            u32(out, 0);
            if (revision == 2) {
                u32(out, 0);
            }
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = read(a, PropertyRecordDecoder::list);
            assertTrue(records(r, "entries").isEmpty());
            assertEquals(a.readUI16(), 0xBEEF);
        }
        for (int rows : new int[]{0, 2}) {
            ByteArrayOutputStream out = valueHeader(4, 13, 0, false);
            string(out, "", true);
            string(out, "", true);
            u32(out, 0);
            u32(out, rows);
            u16(out, 0xBEEF);
            MfcArchive a = archive(out.toByteArray());
            FlaRecord r = read(a, PropertyRecordDecoder::value);
            FlaRecord table = (FlaRecord) r.get("table");
            assertEquals(records(table, "columns").size(), 0);
            assertEquals(records(table, "rows").size(), rows);
            if (rows > 0) {
                assertEquals(records(table, "rows").get(0).get("cells"), Arrays.asList());
            }
            assertEquals(a.readUI16(), 0xBEEF);
            assertEquals(a.getRemaining(), 0);
        }
    }

    @Test
    public void partialListRetainsEarlierPropertiesAndFutureRevisionWithoutResynchronizing() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(2);
        u32(out, 2);
        out.write(property(0, 0).toByteArray());
        out.write(4);
        u32(out, 0x12345678);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] result = new FlaRecord[1];
        try {
            a.readInline("properties", r -> result[0] = r, PropertyRecordDecoder::list);
            fail("Expected future property");
        } catch (UnsupportedFlaRecordException expected) {
            List<FlaRecord> entries = records(result[0], "entries");
            assertEquals(entries.size(), 2);
            assertTrue(entries.get(0).isComplete());
            assertFalse(entries.get(1).isComplete());
            assertEquals(entries.get(1).get("revision"), 4);
            assertEquals(a.readUI32(), 0x12345678L);
            assertEquals(a.getRemaining(), 0);
        }
        for (int revision : new int[]{3, 9}) {
            // A future list or value revision stops before subsequent fields.
            ByteArrayOutputStream future = new ByteArrayOutputStream();
            future.write(revision);
            u16(future, 0xBEEF);
            MfcArchive b = archive(future.toByteArray());
            try {
                read(b, revision == 3 ? PropertyRecordDecoder::list : PropertyRecordDecoder::value);
                fail("Expected future revision");
            } catch (UnsupportedFlaRecordException expected) {
                assertEquals(b.readUI16(), 0xBEEF);
            }
        }
    }

    @Test
    public void countsCellProductsAndBlobLengthsAreCheckedBeforeAllocation() throws Exception {
        ByteArrayOutputStream list = new ByteArrayOutputStream();
        list.write(2);
        u32(list, 0xFFFFFFFFL);
        malformed(list, PropertyRecordDecoder::list);
        ByteArrayOutputStream entries = valueHeader(1, 1, 1001, false);
        malformed(entries, PropertyRecordDecoder::value);
        ByteArrayOutputStream columns = valueHeader(4, 13, 0, false);
        string(columns, "", true);
        string(columns, "", true);
        u32(columns, 1001);
        malformed(columns, PropertyRecordDecoder::value);
        ByteArrayOutputStream product = valueHeader(4, 13, 0, false);
        string(product, "", true);
        string(product, "", true);
        u32(product, 40);
        for (int i = 0; i < 40; i++) {
            string(product, "", true);
            u16(product, 0);
            string(product, "", true);
            string(product, "", true);
        }
        u32(product, 30);
        product.write(new byte[1200]);
        malformed(product, PropertyRecordDecoder::value);
        ByteArrayOutputStream blob = valueHeader(8, 1, 0, false);
        u32(blob, 0xFFFFFFFFL);
        malformed(blob, PropertyRecordDecoder::value);
    }

    @DataProvider(name = "lengths")
    public Object[][] lengths() {
        return new Object[][]{{254}, {255}, {65534}, {65535}};
    }

    @Test(dataProvider = "lengths")
    public void extendedUnicodeValueLengthsDoNotConsumeTheFollowingFields(int length) throws Exception {
        char[] chars = new char[length];
        Arrays.fill(chars, '\u03A9');
        String expected = new String(chars);
        ByteArrayOutputStream out = valueHeader(8, 1, 1, false);
        longString(out, expected);
        u16(out, 0xFEDC);
        u32(out, 0);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = read(a, PropertyRecordDecoder::value);
        assertEquals(records(r, "entries").get(0).get("value"), expected);
        assertEquals(records(r, "entries").get(0).get("type"), 0xFEDC);
        assertEquals(((BinaryData) r.get("data")).size(), 0);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void spritePropertyListDoesNotConsumeItsAccessibilityOrComponentXml() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(0);
        words(out, 6);
        u16(out, 0);
        u32(out, 42);
        out.write(8);
        u32(out, 0);
        u32(out, 2);
        string(out, "sprite", true);
        out.write(2);
        u32(out, 1);
        out.write(property(3, 8).toByteArray());
        u32(out, 0xFFFFFFFFL);
        access(out, 2, true);
        u32(out, 0x87654321L);
        out.write(1);
        u32(out, 123);
        string(out, "<component/>", true);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicSprite", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(records((FlaRecord) r.get("properties"), "entries").size(), 1);
        assertEquals(((FlaRecord) r.get("accessibility")).get("name"), "name");
        assertEquals(r.get("propertyInspectorState"), 0x87654321L);
        assertEquals(((FlaRecord) r.get("componentXml")).get("xml"), "<component/>");
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void descriptorPropertiesKeepFollowingMetadataAndLinkageAligned() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(12);
        out.write(6);
        out.write("Page 1".getBytes(StandardCharsets.US_ASCII));
        string(out, "Scene", false);
        u32(out, 42);
        out.write(0);
        string(out, "legacy", false);
        u32(out, 0);
        u32(out, 0);
        u32(out, 0);
        u32(out, 456);
        string(out, "Class", false);
        string(out, "source.as", false);
        out.write(1);
        u32(out, 1);
        out.write(property(1, 0).toByteArray());
        u32(out, 0x87654321L);
        out.write(0);
        u32(out, 0xFFFFFFFFL);
        string(out, "linkage", false);
        string(out, "url", false);
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CDocumentPage", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("legacyParametersAreLocked"), 0x87654321L);
        assertEquals(((FlaRecord) r.get("linkage")).get("identifier"), "linkage");
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    private static void checkTable(FlaRecord r, int revision) {
        assertTrue(r.isComplete());
        assertEquals(r.get("collectionClass"), "table1");
        assertEquals(r.get("collectionItemClass"), "table2");
        assertEquals(r.get("firstColumnName"), revision >= 5 ? "table3" : null);
        assertEquals(r.get("columnCount"), 2);
        assertEquals(r.get("rowCount"), 2);
        List<FlaRecord> columns = records(r, "columns");
        assertEquals(columns.size(), 2);
        assertEquals(columns.get(1).get("type"), 0x8001);
        assertEquals(columns.get(1).get("name"), "column1");
        assertEquals(columns.get(1).get("defaultValue"), "second1");
        assertEquals(columns.get(1).get("enumeration"), "third1");
        List<FlaRecord> rows = records(r, "rows");
        assertEquals(rows.get(0).get("cells"), Arrays.asList("00", "01"));
        assertEquals(rows.get(1).get("cells"), Arrays.asList("10", "11"));
    }

    private static ByteArrayOutputStream property(int revision, int valueRevision) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        string(out, text(revision >= 2), revision >= 2);
        out.write(value(valueRevision, 13, true).toByteArray());
        if (revision >= 1) {
            string(out, text(revision >= 2) + " extension", revision >= 2);
            out.write(0xFE);
            out.write(identifier());
        }
        if (revision >= 3) {
            string(out, "metadata", true);
            u32(out, 0x80000001L);
        }
        return out;
    }

    private static ByteArrayOutputStream value(int revision, int type, boolean named) throws IOException {
        ByteArrayOutputStream out = valueHeader(revision, type, 2, named);
        for (int i = 0; i < 2; i++) {
            if (named && revision >= 1) {
                string(out, "name" + i, revision >= 3);
            }
            string(out, text(revision >= 3) + i, revision >= 3);
            if (revision >= 6) {
                u16(out, 0x8000 + i);
            }
        }
        if (type == 13 && revision >= 4) {
            string(out, "table1", true);
            string(out, "table2", true);
            if (revision >= 5) {
                string(out, "table3", true);
            }
            u32(out, 2);
            for (int i = 0; i < 2; i++) {
                string(out, "column" + i, true);
                u16(out, 0x8000 + i);
                string(out, "second" + i, true);
                string(out, "third" + i, true);
            }
            u32(out, 2);
            for (String s : new String[]{"00", "01", "10", "11"}) {
                string(out, s, true);
            }
        }
        if (revision >= 8) {
            u32(out, DATA.length);
            out.write(DATA);
        }
        return out;
    }

    private static ByteArrayOutputStream valueHeader(int revision, int type, int count, boolean named) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(revision);
        u16(out, type);
        if (revision >= 8 && type == 17) {
            u16(out, 0xFFFE);
        }
        u32(out, count);
        u32(out, 0xFFFFFFFFL);
        if (revision >= 1) {
            u32(out, named ? 0x80000001L : 0);
        }
        return out;
    }

    private static String text(boolean unicode) {
        return unicode ? "\u010cesk\u00fd \u03A9" : "\u010cesk\u00fd";
    }

    private static byte[] identifier() {
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = (byte) (255 - i);
        }
        return bytes;
    }

    private static FlaRecord read(MfcArchive a, FlaRecordDecoder decoder) throws IOException {
        return a.readInline("test", r -> {
        }, decoder);
    }

    private static void malformed(ByteArrayOutputStream out, FlaRecordDecoder decoder) throws Exception {
        try {
            read(archive(out.toByteArray()), decoder);
            fail("Expected malformed count or byte length");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Malformed field must not be unsupported", ex);
        } catch (IOException expected) {
        }
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
