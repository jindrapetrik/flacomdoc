package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class OleRecordReaderTest {

    @Test
    public void nonemptyOleBaseUsesACountedListAndSharesPageReferencesWithScenes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(2);
        u32(out, 2);
        out.write(item(true));
        u16(out, 2); // Repeat the same OLE object; no terminator.
        u16(out, 4);
        u16(out, 0);
        u16(out, 1);
        u16(out, 0);
        u16(out, 4); // Scene list and selected scene.
        out.write(0);
        words(out, 4);
        u32(out, 0);
        out.write(new byte[3]);
        words(out, 4);
        out.write(new byte[4]);
        u32(out, 0);
        u16(out, 0);
        u16(out, 0);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", out.toByteArray());
        streams.put("Embedding 1/data", new byte[]{1, 2, 3});
        BinaryFlaDocument doc = new BinaryFlaReader(ANSI).readStreams(streams);
        assertEquals(doc.getContentsStream().getParseStatus(), FlaStream.ParseStatus.COMPLETE, doc.getIssues().toString());
        FlaRecord root = doc.getContents();
        assertEquals(root.get("oleBaseHeader"), 2L);
        FlaRecord ole = records(root, "oleItems").get(0);
        assertSame(records(root, "oleItems").get(1), ole);
        FlaRecord page = (FlaRecord) ole.get("documentPage");
        assertSame(records(root, "scenes").get(0), page);
        assertSame(root.get("selectedScene"), page);
        assertEquals(ole.getSchema(), 0);
        assertEquals(ole.get("signature"), 0x100L);
        assertEquals(ole.get("itemNumber"), 1L);
        assertEquals(ole.get("monikerPresent"), 0xFFFF);
        assertEquals(ole.get("oleItemId"), 42);
        assertTrue(ole.isComplete());
        assertEquals(doc.getStream("Embedding 1/data").getData().toByteArray(), new byte[]{1, 2, 3});
    }

    @Test
    public void clientItemEndsAtItsPageReferenceWithoutAnInventedInlineStorageBlob() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(item(false));
        u16(out, 2);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        assertSame(a.readObject(), r);
        assertEquals(r.get("value1"), 0x80000000L);
        assertEquals(r.get("drawAspect"), 0xFFFFFFFFL);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @Test
    public void embeddedOlePictureReadsTheSameMfcItemWhenTheNumericIdIsZero() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(255);
        words(out, 10);
        u16(out, 0);
        out.write(item(false));
        u16(out, 0xBEEF);
        MfcArchive a = archive(object("CPicOle", out.toByteArray()));
        FlaRecord r = a.readObject();
        assertTrue(r.isComplete());
        FlaRecord ole = (FlaRecord) r.get("oleItem");
        assertTrue(ole.isComplete());
        assertEquals(ole.get("oleItemId"), 42);
        assertEquals(a.readUI16(), 0xBEEF);
    }

    @Test
    public void everyTruncatedItemPrefixIsMalformedAndRetainsOriginalData() throws Exception {
        byte[] bytes = item(true);
        int start = object("CSketchCntrItem", new byte[0]).length;
        for (int length = start; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            try {
                a.readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known OLE layout must not be unsupported", ex);
            } catch (IOException expected) {
                assertFalse(a.getObjects().get(0).isComplete());
                assertEquals(a.getObjects().get(0).getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
            }
        }
    }

    @Test
    public void invalidSignatureAndExcessiveItemCountsAreMalformed() throws Exception {
        byte[] bytes = item(false);
        bytes[object("CSketchCntrItem", new byte[0]).length] = 2;
        MfcArchive a = archive(bytes);
        try {
            a.readObject();
            fail("Expected invalid signature");
        } catch (UnsupportedFlaRecordException ex) {
            fail("Invalid signature is malformed", ex);
        } catch (IOException expected) {
            assertEquals(a.getObjects().get(0).get("signature"), 0x102L);
            assertFalse(a.getObjects().get(0).isComplete());
        }
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{2, -1, -1, -1, -1, 42, 43});
        FlaStream contents = new BinaryFlaReader().readStreams(streams).getContentsStream();
        assertEquals(contents.getParseStatus(), FlaStream.ParseStatus.MALFORMED);
        assertEquals(contents.getStopOffset(), 5);
        assertEquals(contents.getUnparsedData().toByteArray(), new byte[]{42, 43});
    }

    private static byte[] item(boolean page) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 0x100);
        u32(out, 1);
        u32(out, 0x80000000L);
        u16(out, 0xFFFF);
        u32(out, 0xFFFFFFFFL);
        out.write(255);
        u16(out, 42);
        if (page) {
            ByteArrayOutputStream descriptor = new ByteArrayOutputStream();
            descriptor.write(0);
            string(descriptor, "Page 1", false);
            string(descriptor, "Scene", false);
            out.write(object("CDocumentPage", descriptor.toByteArray()));
        } else {
            u16(out, 0);
        }
        byte[] bytes = object("CSketchCntrItem", out.toByteArray());
        bytes[2] = 0;
        return bytes;
    }
}
