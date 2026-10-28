package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class GeneratorCommandReaderTest {

    @DataProvider(name = "revisions")
    public Object[][] revisions() {
        return new Object[][]{{0, false}, {1, false}, {0, true}, {1, true}};
    }

    private byte[] payload(int revision, boolean unicode) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base(out);
        out.write(revision);
        for (int value : new int[]{65536, -32768, 16384, 131072, 5027, -3967,
            -1024, 2048, -512, 4096}) {
            u32(out, value);
        }
        string(out, "com.macromedia.generator.commands.ListCommand instancename=\"sample\" ", unicode);
        if (revision >= 1) {
            string(out, unicode ? "List \u03a9" : "List", unicode);
        }
        return out.toByteArray();
    }

    @Test(dataProvider = "revisions")
    public void readsNativeCommandFieldsAndStopsAtTheNextRecord(int revision, boolean unicode) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(payload(revision, unicode));
        u16(bytes, 0xBEEF);
        MfcArchive archive = archive(object("CCommandObj", bytes.toByteArray()));
        FlaRecord command = archive.readObject();
        assertTrue(command.isComplete());
        assertEquals(command.get("revision"), revision);
        assertEquals(command.get("matrixWords"), Arrays.asList(65536, -32768, 16384, 131072, 5027, -3967));
        assertEquals(command.get("bounds"), Arrays.asList(-1024, 2048, -512, 4096));
        assertEquals(command.getMatrix().getTxPixels(), 251.35);
        assertEquals(command.getBounds().getYMinTwips(), -512);
        assertEquals(command.get("command"), "com.macromedia.generator.commands.ListCommand instancename=\"sample\" ");
        assertEquals(command.get("displayLabel"), revision == 0 ? null : unicode ? "List \u03a9" : "List");
        assertEquals(archive.readUI16(), 0xBEEF);
        assertEquals(archive.getRemaining(), 0);
    }

    @Test(dataProvider = "revisions")
    public void truncatedCommandsFailWithoutGuessingTheirEnd(int revision, boolean unicode) throws Exception {
        byte[] data = object("CCommandObj", payload(revision, unicode));
        int header = object("CCommandObj", new byte[0]).length;
        for (int length = header; length < data.length; length++) {
            try {
                archive(Arrays.copyOf(data, length)).readObject();
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("A supported command prefix must not be treated as an unknown class", ex);
            } catch (IOException expected) {
                // An incomplete variable-length string must never consume another record.
            }
        }
    }

    @Test
    public void rejectsUnknownRevisionBeforeGuessingFutureFields() throws Exception {
        try {
            archive(object("CCommandObj", payload(2, false))).readObject();
            fail("Expected unsupported revision");
        } catch (UnsupportedFlaRecordException expected) {
            assertTrue(expected.getMessage().contains("revision"));
        }
    }
}
