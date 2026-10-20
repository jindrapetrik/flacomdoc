package com.jpexs.flash.fla.converter;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class EdgeStyleIndexTest {
    @Test
    public void styleIndicesAndSelectionBitsRoundTrip() throws Exception {
        for (FlaFormatVersion version : new FlaFormatVersion[] {
            FlaFormatVersion.F5, FlaFormatVersion.F8, FlaFormatVersion.CS3, FlaFormatVersion.CS4
        }) {
            for (int selection = 0; selection < 8; selection++) {
                for (int index : new int[] {0, 255, 256, 32767}) {
                    for (int position = 0; position < 3; position++) {
                        int[] styles = {1, 2, 3};
                        styles[position] = index;
                        checkEdge(version, selection, styles);
                    }
                }
            }
        }
    }

    private void checkEdge(FlaFormatVersion version, int selection, int[] styles) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FlaWriter writer = new FlaWriter(out, version, "windows-1250");
        writer.beginShape();
        writer.setStrokeStyle(styles[0]);
        writer.setFillStyle0(styles[1]);
        writer.setFillStyle1(styles[2]);
        writer.writeEdge(selection, null, null, "20", "0", null, null, false);

        ByteBuffer data = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        int control = data.get() & 255;
        assertTrue((control & 0x40) != 0);
        boolean narrow = (control & 0x80) != 0;
        boolean expectedNarrow = selection == 0 && styles[0] < 256 && styles[1] < 256 && styles[2] < 256;
        assertEquals(narrow, expectedNarrow, "Index width");
        int[] selectionBits = {4, 1, 2};
        for (int i = 0; i < styles.length; i++) {
            int value = narrow ? data.get() & 255 : data.getShort() & 65535;
            assertEquals(value & 0x7fff, styles[i], "Style index " + i);
            assertEquals((value & 0x8000) != 0, (selection & selectionBits[i]) != 0, "Selection " + i);
        }
        assertEquals(control & 0x3f, 0x10, "Endpoint uses signed 8.8 coordinates");
        assertEquals(data.getShort(), (short) (20 * 256));
        assertEquals(data.getShort(), (short) 0);
        if (version.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
            assertEquals(data.get(), (byte) 0, "General-line flag");
        }
        assertFalse(data.hasRemaining(), "Style width must not shift coordinates");
    }
}
