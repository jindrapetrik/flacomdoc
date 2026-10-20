package com.jpexs.flash.fla.converter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class FocalGradientTest {
    @Test
    public void signedFocalPointInExtendedGradients() throws Exception {
        for (FlaFormatVersion version : new FlaFormatVersion[] {
            FlaFormatVersion.F8, FlaFormatVersion.CS3, FlaFormatVersion.CS4
        }) {
            for (double ratio : new double[] {-1, -0.5, 0, 0.5, 1}) {
                byte[] bytes = gradient(version, ratio);
                ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                assertEquals(data.getInt(31), (int) Math.round(ratio * 255), version + ": " + ratio);
                assertEquals(data.getInt(35), 5, "Spread and interpolation parameters");
                assertEquals(bytes.length, 49);
                assertEquals(bytes[39] & 255, 0, "First stop ratio");
                assertEquals(bytes[44] & 255, 255, "Last stop ratio");
            }
        }
    }

    @Test
    public void olderGradientsHaveNoExtension() throws Exception {
        byte[] bytes = gradient(FlaFormatVersion.MX2004, -0.5);
        assertEquals(bytes.length, 41);
        assertEquals(bytes[31] & 255, 0);
        assertEquals(bytes[36] & 255, 255);
    }

    private byte[] gradient(FlaFormatVersion version, double ratio) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new FlaWriter(out, version, "windows-1250").writeGradientFill(
                new Color[] {Color.BLACK, Color.WHITE}, new double[] {0, 1},
                FlaWriter.FILLTYPE_RADIAL_GRADIENT, true, FlaWriter.FLOW_REFLECT,
                new Matrix(), ratio);
        return out.toByteArray();
    }
}
