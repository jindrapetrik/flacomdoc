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

public class PrimitiveRecordReaderTest {

    @DataProvider(name = "layouts")
    public Object[][] layouts() {
        List<Object[]> result = new ArrayList<>();
        for (String type : new String[]{"CPicRect", "CPicOval"}) {
            for (int primitive = 0; primitive <= 3; primitive++) {
                for (int shape = 0; shape <= 6; shape++) {
                    result.add(new Object[]{type, primitive, shape});
                }
            }
        }
        return result.toArray(new Object[0][]);
    }

    @Test(dataProvider = "layouts")
    public void primitiveAndShapeRevisionsAreIndependentAndPreserveTheExactBoundary(String type, int primitive, int shape) throws Exception {
        ByteArrayOutputStream payload = primitive(type, primitive, shape);
        u16(payload, 0xBEEF);
        MfcArchive a = archive(object(type, payload.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.get("baseRevision"), 1);
        assertEquals(r.get("shapeRevision"), shape);
        assertEquals(r.get("revision"), primitive);
        assertEquals(r.get("primitiveFlags"), 0xFE);
        assertEquals(r.get("matrixWords"), Arrays.asList(-250, -150, -50, 50, 150, 250));
        FlaRecord data = (FlaRecord) r.get("shapeData");
        assertTrue(data.isComplete());
        assertEquals(data.get("revision"), 5);
        assertEquals(data.get("extensionMode"), shape >= 3 ? shape - 2 : 0);
        assertEquals(records(data, "fills").get(0).get("packedValue"), 0xAB785634L);
        if (type.equals("CPicRect")) {
            assertEquals(r.get("cornerRadii"), Arrays.asList(0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE));
            assertNull(r.get("bounds"));
            assertNull(r.get("startAngleDegrees"));
        } else {
            assertEquals(r.get("bounds"), Arrays.asList(-200, 800, -400, 1200));
            assertEquals(r.get("startAngleDegrees"), -30.25f);
            assertEquals(r.get("endAngleDegrees"), 450.5f);
            assertEquals(r.get("innerRadiusPercent"), 37.5f);
            assertNull(r.get("cornerRadii"));
        }
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertEquals(a.getObjects().size(), 1);
        assertEquals(r.getObjectId(), 2);
        assertEquals(data.getObjectId(), 0);
        assertEquals(data.getEndOffset(), r.getEndOffset() - (type.equals("CPicRect") ? 18 : 30));
    }

    @Test(dataProvider = "layouts")
    public void everyTruncatedPrimitivePrefixIsMalformed(String type, int primitive, int shape) throws Exception {
        byte[] bytes = object(type, primitive(type, primitive, shape).toByteArray());
        int headerLength = 6 + type.length();
        for (int length = headerLength; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known layout must not be unsupported", ex);
            } catch (IOException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertEquals(r.getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void primitiveFlagsArePreservedWithoutBooleanNormalization() throws Exception {
        for (String type : new String[]{"CPicRect", "CPicOval"}) {
            for (int flags : new int[]{0, 1, 2, 0x80, 0xFF}) {
                byte[] bytes = primitive(type, 3, 6).toByteArray();
                int tail = type.equals("CPicRect") ? 18 : 30;
                bytes[bytes.length - tail + 1] = (byte) flags;
                FlaRecord r = archive(object(type, bytes)).readObject();
                assertEquals(r.get("primitiveFlags"), flags);
                assertTrue(r.isComplete());
            }
        }
    }

    @Test
    public void ovalFloatsPreserveSignedZeroInfinitiesAndNanPayloads() throws Exception {
        for (int[] bits : new int[][]{{0x80000000, 0x7F800000, 0xFF800000}, {0x7FC01234, 0xFFC05678, 0x00000001}}) {
            ByteArrayOutputStream payload = shapePrefix(6);
            payload.write(3);
            payload.write(0x81);
            words(payload, 4);
            for (int value : bits) {
                u32(payload, value);
            }
            FlaRecord r = archive(object("CPicOval", payload.toByteArray())).readObject();
            assertEquals(Float.floatToRawIntBits((Float) r.get("startAngleDegrees")), bits[0]);
            assertEquals(Float.floatToRawIntBits((Float) r.get("endAngleDegrees")), bits[1]);
            assertEquals(Float.floatToRawIntBits((Float) r.get("innerRadiusPercent")), bits[2]);
        }
    }

    @Test
    public void primitivesKeepSharedAndCircularChildrenInTheInheritedArchiveTable() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(1);
        payload.write(0xA5);
        ByteArrayOutputStream child = new ByteArrayOutputStream();
        child.write(0);
        child.write(0);
        u16(child, 2);
        u16(child, 0);
        payload.write(object("CPicObj", child.toByteArray()));
        u16(payload, 4);
        u16(payload, 0);
        u32(payload, -123);
        u32(payload, 456);
        shape(payload, 6);
        payload.write(3);
        payload.write(1);
        words(payload, 4);
        u16(payload, 0xBEEF);
        MfcArchive a = archive(object("CPicRect", payload.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertEquals(r.getChildren().size(), 2);
        assertSame(r.getChildren().get(0), r.getChildren().get(1));
        assertSame(r.getChildren().get(0).getChildren().get(0), r);
        assertEquals(a.getObjects().size(), 2);
        assertEquals(r.get("transformationPointX"), -123);
        assertEquals(r.get("transformationPointY"), 456);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void pageKeepsPrimitiveSiblingsAndParentFieldsAfterBothTails() throws Exception {
        FlaStream stream = page(primitive("CPicRect", 2, 5).toByteArray(), primitive("CPicOval", 3, 6).toByteArray());
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        FlaRecord root = stream.getRoot();
        assertTrue(root.isComplete());
        assertEquals(root.getChildren().size(), 2);
        FlaRecord rect = root.getChildren().get(0), oval = root.getChildren().get(1);
        assertEquals(rect.getClassName(), "CPicRect");
        assertEquals(oval.getClassName(), "CPicOval");
        assertTrue(rect.isComplete());
        assertTrue(oval.isComplete());
        assertEquals(root.get("transformationPointX"), -42);
        assertEquals(root.get("transformationPointY"), 1234);
        assertEquals(root.get("shapeRevision"), 6);
        assertTrue(((FlaRecord) root.get("shapeData")).isComplete());
        assertEquals(stream.getUnparsedData().size(), 0);
        assertEquals(stream.getObjects().size(), 3);
    }

    @Test
    public void futurePrimitiveRevisionsKeepTheInheritedShapeAndUnparsedTail() throws Exception {
        for (String type : new String[]{"CPicRect", "CPicOval"}) {
            ByteArrayOutputStream payload = shapePrefix(6);
            payload.write(4);
            u32(payload, 0x12345678);
            MfcArchive a = archive(object(type, payload.toByteArray()));
            try {
                a.readObject();
                fail("Expected unsupported primitive revision");
            } catch (UnsupportedFlaRecordException expected) {
                FlaRecord r = a.getObjects().get(0);
                assertFalse(r.isComplete());
                assertEquals(r.get("revision"), 4);
                assertTrue(((FlaRecord) r.get("shapeData")).isComplete());
                assertEquals(a.readUI32(), 0x12345678L);
                assertEquals(a.getRemaining(), 0);
            }
        }
        ByteArrayOutputStream future = shapePrefix(6);
        future.write(4);
        future.write(new byte[]{9, 8, 7, 6});
        FlaStream partial = page(future.toByteArray(), primitive("CPicOval", 3, 6).toByteArray());
        assertEquals(partial.getParseStatus(), FlaStream.ParseStatus.PARTIAL);
        assertFalse(partial.getRoot().isComplete());
        assertFalse(partial.getRoot().getChildren().get(0).isComplete());
        assertEquals(partial.getUnparsedData().slice(0, 4).toByteArray(), new byte[]{9, 8, 7, 6});
        assertEquals(partial.getRoot().getChildren().size(), 1);
    }

    private static FlaStream page(byte[] rect, byte[] oval) throws Exception {
        ByteArrayOutputStream root = new ByteArrayOutputStream();
        root.write(1);
        root.write(0);
        root.write(object("CPicRect", rect));
        root.write(object("CPicOval", oval));
        u16(root, 0);
        u32(root, -42);
        u32(root, 1234);
        shape(root, 6);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(0);
        stream.write(object("CPicShape", root.toByteArray()));
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Primitives", stream.toByteArray());
        return new BinaryFlaReader(ANSI).readStreams(streams).getStream("Primitives");
    }

    private static ByteArrayOutputStream primitive(String type, int revision, int shape) throws IOException {
        ByteArrayOutputStream out = shapePrefix(shape);
        out.write(revision);
        out.write(0xFE);
        if (type.equals("CPicRect")) {
            u32(out, 0);
            u32(out, -1);
            u32(out, Integer.MIN_VALUE);
            u32(out, Integer.MAX_VALUE);
        } else {
            u32(out, -200);
            u32(out, 800);
            u32(out, -400);
            u32(out, 1200);
            f32(out, -30.25f);
            f32(out, 450.5f);
            f32(out, 37.5f);
        }
        return out;
    }

    private static ByteArrayOutputStream shapePrefix(int revision) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        shape(out, revision);
        return out;
    }

    private static void shape(ByteArrayOutputStream out, int revision) throws IOException {
        out.write(revision);
        words(out, 6);
        out.write(5);
        u32(out, 0x87654321L);
        u16(out, 1);
        u32(out, 0xAB785634L);
        out.write(0);
        out.write(0xA5);
        u16(out, 0);
        out.write(0);
        u32(out, 0);
    }
}
