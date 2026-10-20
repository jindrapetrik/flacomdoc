package com.jpexs.flash.fla.converter;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ArchiveStringLengthTest {
    @Test
    public void reservedUnicodeMarkerIsNeverUsedAsALength() throws Exception {
        for (FlaFormatVersion version : new FlaFormatVersion[] {
            FlaFormatVersion.F1, FlaFormatVersion.F5, FlaFormatVersion.MX,
            FlaFormatVersion.MX2004, FlaFormatVersion.CS4
        }) {
            for (int length : new int[] {254, 255, 65533, 65534, 65535}) {
                char[] chars = new char[length];
                Arrays.fill(chars, 'A');
                checkString(version, "windows-1250", new String(chars));
            }
        }
    }

    @Test
    public void ansiLengthCountsEncodedBytes() throws Exception {
        char[] chars = new char[32767];
        Arrays.fill(chars, '\u00e9'); // Two UTF-8 bytes: exactly 65534 bytes overall.
        checkString(FlaFormatVersion.MX, "UTF-8", new String(chars));
    }

    private void checkString(FlaFormatVersion version, String charset, String value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FlaWriter writer = new FlaWriter(out, version, charset);
        writer.writeBomString(value);
        writer.writeUI32(0x12345678);
        ByteBuffer data = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        int width = 1;
        int length = data.get() & 255;
        if (length == 255) {
            length = data.getShort() & 65535;
            if (length == 0xfffe) {
                width = 2;
                length = data.get() & 255;
                if (length == 255) {
                    length = data.getShort() & 65535;
                }
            }
            if (length == 0xffff) {
                length = data.getInt();
            }
        }
        assertEquals(width, version.unicode ? 2 : 1, "Archive character width");
        byte[] expected = value.getBytes(version.unicode ? "UTF-16LE" : charset);
        assertEquals(length * width, expected.length, "Payload length");
        byte[] payload = new byte[length * width];
        data.get(payload);
        assertEquals(payload, expected);
        assertEquals(data.getInt(), 0x12345678, "Next field must remain aligned");
        assertFalse(data.hasRemaining());
        int prefix = version.unicode ? 3 : 0;
        if (expected.length / width >= 0xfffe) {
            assertEquals(out.toByteArray()[prefix] & 255, 255);
            assertEquals(ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
                    .getShort(prefix + 1) & 65535, 0xffff, "Canonical extended length");
        }
    }
}
