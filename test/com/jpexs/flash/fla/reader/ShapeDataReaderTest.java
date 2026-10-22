package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ShapeDataReaderTest {

    @DataProvider(name = "modes")
    public Object[][] modes() {
        return new Object[][]{{0}, {1}, {2}, {3}, {4}};
    }

    @Test(dataProvider = "modes")
    public void readsSolidGradientBitmapAndFlag20Fills(int mode) throws Exception {
        ByteArrayOutputStream data = header(4);
        fill(data, 0x04030201L, 0);
        gradient(data, mode); // 0x30 tests gradient precedence over the 0x20 branch.
        fill(data, 0x44332211L, 0x20);
        matrix(data);
        u32(data, 0xFEDCBA98L);
        for (int i = 0; i < 4; i++) {
            u16(data, 0x8000 + i);
        }
        fill(data, 0xFF0000FFL, 0x40);
        matrix(data);
        u16(data, 0xABCD);
        u16(data, 0);
        data.write(0);
        u32(data, 0);
        FlaRecord shape = complete(data, mode);
        List<FlaRecord> fills = records(shape, "fills");
        assertEquals(fills.size(), 4);
        assertEquals(fills.get(0).get("packedValue"), 0x04030201L); // Do not normalize alpha.
        FlaRecord gradient = fills.get(1);
        assertEquals(gradient.get("matrixWords"), Arrays.asList(1, -2, 3, -4, 5, -6));
        assertEquals(records(gradient, "stops").get(1).get("ratio"), 255);
        assertEquals(records(gradient, "stops").get(1).get("colorRgba"), 0x88776655L);
        if (mode >= 1) {
            assertEquals(gradient.get("focalPoint"), -123);
            assertEquals(gradient.get("gradientFlags"), 0x80000003L);
        } else {
            assertNull(gradient.get("focalPoint"));
        }
        assertEquals(fills.get(2).get("extensionWords"), Arrays.asList(32768, 32769, 32770, 32771));
        assertEquals(fills.get(3).get("bitmapMediaId"), 0xABCD);
        assertTrue(fills.get(3).isComplete());
        assertEquals(fills.get(0).getConsumedData().size(), 6);
    }

    @Test
    public void legacyFillLayoutUsesAWordRatherThanExtendedFlags() throws Exception {
        for (int revision = 0; revision < 3; revision++) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            data.write(revision);
            u32(data, 77);
            u16(data, 1);
            u32(data, 0x04030201);
            u16(data, 0xFFFF);
            u16(data, 0);
            if (revision >= 2) {
                data.write(0);
            }
            FlaRecord shape = complete(data, 0);
            FlaRecord fill = records(shape, "fills").get(0);
            assertEquals(fill.get("legacyValue"), 0xFFFF);
            assertNull(fill.get("flags"));
            assertEquals(fill.getConsumedData().size(), 6);
        }
    }

    @Test(dataProvider = "modes")
    public void readsPackedStrokeWordsAndVersionedPaint(int mode) throws Exception {
        ByteArrayOutputStream data = header(0);
        u16(data, 1);
        u32(data, 0xAABBCCDDL);
        u16(data, 65535);
        u16(data, 42);
        u16(data, 0x80FE);
        if (mode >= 1) {
            data.write(new byte[]{1, 3, 2, 1});
            u16(data, 0x1234);
            gradient(data, mode);
        }
        data.write(0);
        u32(data, 0);
        FlaRecord stroke = records(complete(data, mode), "strokes").get(0);
        assertEquals(stroke.get("colorRgba"), 0xAABBCCDDL);
        assertEquals(stroke.get("width"), 65535);
        assertEquals(stroke.get("styleWord1"), 42);
        assertEquals(stroke.get("styleWord2"), 0x80FE);
        assertEquals(stroke.get("styleType"), 1);
        assertEquals(stroke.get("sharpCorners"), true);
        if (mode >= 1) {
            assertEquals(stroke.get("scaleMode"), 3);
            assertEquals(stroke.get("miterLimitWord"), 0x1234);
            assertEquals(records((FlaRecord) stroke.get("paint"), "stops").size(), 2);
        } else {
            assertNull(stroke.get("paint"));
            assertEquals(stroke.getConsumedData().size(), 10);
        }
    }

    @DataProvider(name = "vectors")
    public Object[][] vectors() {
        Object[][] cases = new Object[128][4];
        int index = 0;
        for (int mode : new int[]{0, 2}) {
            for (int start = 0; start < 4; start++) {
                for (int control = 0; control < 4; control++) {
                    for (int end = 0; end < 4; end++) {
                        cases[index++] = new Object[]{mode, start, control, end};
                    }
                }
            }
        }
        return cases;
    }

    @Test(dataProvider = "vectors")
    public void reconstructsAllVectorModesAndReusesFullWidthStyleIndices(int mode, int start, int control, int end) throws Exception {
        ByteArrayOutputStream data = header(0);
        u16(data, 0);
        data.write(0x40 | start | control << 2 | end << 4);
        u16(data, 0x8001);
        u16(data, 0x81FF);
        u16(data, 255);
        vector(data, start);
        vector(data, control);
        vector(data, end);
        if (control == 0 && mode >= 2) {
            data.write(0x83);
        }
        data.write(0x10);
        vector(data, 1); // Reuse style IDs and the previous endpoint.
        if (mode >= 2) {
            data.write(0x42);
        }
        data.write(0);
        u32(data, 0);
        List<FlaRecord> edges = records(complete(data, mode), "edges");
        assertEquals(edges.size(), 2);
        FlaRecord first = edges.get(0), second = edges.get(1);
        assertEquals(first.get("startX"), x(start));
        assertEquals(first.get("startY"), y(start));
        assertEquals(first.get("endX"), x(start) + x(end));
        assertEquals(first.get("endY"), y(start) + y(end));
        assertEquals(first.get("curve"), control != 0);
        if (control != 0) {
            assertEquals(first.get("controlX"), x(start) + x(control));
            assertEquals(first.get("controlY"), y(start) + y(control));
            assertNull(first.get("lineFlags"));
        } else if (mode >= 2) {
            assertEquals(first.get("lineFlags"), 0x83);
        }
        assertEquals(second.get("strokeIndex"), 0x8001);
        assertEquals(second.get("fillIndex0"), 0x81FF);
        assertEquals(second.get("fillIndex1"), 255);
        assertEquals(second.get("startX"), first.get("endX"));
        assertEquals(second.get("endX"), x(start) + x(end) + x(1));
    }

    @Test
    public void byteStyleIdsAndInitialZeroIdsAreIndependentOfCoordinates() throws Exception {
        ByteArrayOutputStream data = header(0);
        u16(data, 0);
        data.write(0x10);
        vector(data, 1);
        data.write(0xC0);
        data.write(new byte[]{(byte) 255, (byte) 128, 1}); // No coordinate payload.
        data.write(0);
        u32(data, 0);
        List<FlaRecord> edges = records(complete(data, 0), "edges");
        assertEquals(edges.get(0).get("strokeIndex"), 0);
        assertEquals(edges.get(1).get("strokeIndex"), 255);
        assertEquals(edges.get(1).get("fillIndex0"), 128);
        assertEquals(edges.get(1).get("startX"), x(1));
        assertEquals(edges.get(1).get("endX"), x(1));
    }

    @Test(dataProvider = "modes")
    public void readsAuxiliaryGeometryAtEachExtensionGate(int mode) throws Exception {
        ByteArrayOutputStream data = header(0);
        u16(data, 0);
        data.write(0);
        u32(data, 1);
        for (int i = 0; i < 8; i++) {
            u32(data, -i);
        }
        if (mode >= 3) {
            data.write(2);
            u32(data, -123);
            u32(data, 456);
            data.write(255);
            data.write(128);
            u32(data, 789);
            u32(data, -456);
            data.write(7);
            data.write(9);
        }
        if (mode >= 4) {
            data.write(3);
            u32(data, -1);
            u32(data, -2);
            u32(data, 3);
            u32(data, 4);
        }
        FlaRecord auxiliary = records(complete(data, mode), "auxiliary").get(0);
        assertEquals(auxiliary.get("points"), Arrays.asList(0, -1, -2, -3, -4, -5, -6, -7));
        if (mode >= 3) {
            FlaRecord entry = records(auxiliary, "entries").get(0);
            assertEquals(entry.get("x"), -123);
            assertEquals(entry.get("controlFlag"), 255);
            assertEquals(entry.getConsumedData().size(), 10);
        }
        if (mode >= 4) {
            assertEquals(auxiliary.get("boundaryStartPointTwips"), Arrays.asList(-1, -2));
            assertEquals(auxiliary.get("boundaryEndPointTwips"), Arrays.asList(3, 4));
        }
    }

    @Test
    public void truncatedEntriesRemainAttachedAndDoNotLoseSourceBytes() throws Exception {
        ByteArrayOutputStream data = header(1);
        gradient(data, 2);
        u16(data, 0);
        data.write(0);
        u32(data, 0);
        byte[] page = page(data.toByteArray(), 2);
        for (int length = 20; length < page.length; length++) {
            byte[] truncated = Arrays.copyOf(page, length);
            FlaStream stream = parse(truncated, new BinaryFlaReader());
            assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.MALFORMED, "cut at " + length);
            assertEquals(stream.getData().toByteArray(), truncated);
            assertFalse(stream.getRoot().isComplete());
        }
        FlaStream partial = parse(Arrays.copyOf(page, page.length - 15), new BinaryFlaReader());
        FlaRecord shape = (FlaRecord) partial.getRoot().get("shapeData");
        assertFalse(records(shape, "fills").get(0).isComplete());
    }

    @Test
    public void rejectsExcessiveCountsAndKeepsUnknownRevisionsUnsupported() throws Exception {
        ByteArrayOutputStream data = header(1);
        fill(data, 0, 0x10);
        matrix(data);
        data.write(16);
        FlaStream badGradient = parse(page(data.toByteArray(), 0), new BinaryFlaReader());
        assertEquals(badGradient.getParseStatus(), FlaStream.ParseStatus.MALFORMED);
        ByteArrayOutputStream edges = header(0);
        u16(edges, 0);
        for (int i = 0; i < 3; i++) {
            edges.write(0x80);
        }
        edges.write(0);
        u32(edges, 0);
        BinaryFlaReader limited = new BinaryFlaReader(new FlaReadOptions(StandardCharsets.UTF_8, 4096, 8192, 2, 20));
        assertEquals(parse(page(edges.toByteArray(), 0), limited).getParseStatus(), FlaStream.ParseStatus.MALFORMED);
        byte[] unknown = {6};
        assertEquals(parse(page(unknown, 0), new BinaryFlaReader()).getParseStatus(), FlaStream.ParseStatus.PARTIAL);
    }

    private FlaRecord complete(ByteArrayOutputStream data, int mode) throws Exception {
        FlaStream stream = parse(page(data.toByteArray(), mode), new BinaryFlaReader());
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        FlaRecord shape = (FlaRecord) stream.getRoot().get("shapeData");
        assertTrue(shape.isComplete());
        return shape;
    }

    private FlaStream parse(byte[] page, BinaryFlaReader reader) throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Shape", page);
        return reader.readStreams(streams).getStream("Shape");
    }

    @SuppressWarnings("unchecked")
    private List<FlaRecord> records(FlaRecord record, String field) {
        return (List<FlaRecord>) record.get(field);
    }

    private ByteArrayOutputStream header(int fills) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(5);
        u32(data, 1234);
        u16(data, fills);
        return data;
    }

    private byte[] page(byte[] data, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        u16(out, 0xFFFF);
        u16(out, 1);
        u16(out, 9);
        out.write("CPicShape".getBytes(StandardCharsets.US_ASCII));
        out.write(1);
        out.write(0);
        u16(out, 0);
        u32(out, 0);
        u32(out, 0);
        out.write(mode + 2);
        matrix(out);
        out.write(data);
        return out.toByteArray();
    }

    private void gradient(ByteArrayOutputStream out, int mode) throws IOException {
        fill(out, 0xFF000000L, 0x30);
        matrix(out);
        out.write(2);
        if (mode >= 1) {
            u32(out, -123);
            u32(out, 0x80000003L);
        }
        out.write(0);
        u32(out, 0x44332211L);
        out.write(255);
        u32(out, 0x88776655L);
    }

    private void fill(ByteArrayOutputStream out, long color, int flags) throws IOException {
        u32(out, color);
        out.write(flags);
        out.write(0xA5);
    }

    private void matrix(ByteArrayOutputStream out) {
        for (int i = 1; i <= 6; i++) {
            u32(out, i % 2 == 0 ? -i : i);
        }
    }

    private void vector(ByteArrayOutputStream out, int mode) {
        if (mode == 2) {
            u32(out, x(mode));
            u32(out, y(mode));
        } else if (mode != 0) {
            u16(out, mode == 3 ? -255 : -3);
            u16(out, mode == 3 ? 256 : 5);
        }
    }

    private int x(int mode) {
        return mode == 0 ? 0 : mode == 1 ? -3 : mode == 2 ? -0x1234567 : -255 << 7;
    }

    private int y(int mode) {
        return mode == 0 ? 0 : mode == 1 ? 5 : mode == 2 ? 0x2345678 : 256 << 7;
    }

    private void u16(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
    }

    private void u32(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 4; i++) {
            out.write((int) (value >> (8 * i)));
        }
    }
}
