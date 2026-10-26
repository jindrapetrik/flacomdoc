package com.jpexs.cfb;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CompoundFileBinaryTest {

    @DataProvider(name = "streamLengths")
    public Object[][] streamLengths() {
        return new Object[][]{
            {4095}, {4096}, {4097},
            {16777215}, {16777216}, {16777217},
            {16777728}, {16777729}
        };
    }

    @Test(dataProvider = "streamLengths")
    public void streamContentsSurviveSectorRounding(int length) throws Exception {
        byte[] expected = new byte[length];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i * 31 + i / 512);
        }
        expected[length - 1] = (byte) 0xa5;
        Path file = Files.createTempFile("cfb-stream-rounding-", ".cfb");
        try {
            try (CompoundFileBinary cfb = new CompoundFileBinary(file.toFile(), true)) {
                cfb.addFile("Contents", expected);
            }
            try (CompoundFileBinary cfb = new CompoundFileBinary(file.toFile())) {
                DirectoryEntry entry = cfb.getEntryByPath("Contents");
                assertNotNull(entry);
                assertEquals(entry.streamSize, (long) length, "Stored stream length");
                try (InputStream in = cfb.getEntryStream(entry)) {
                    ByteArrayOutputStream actual = new ByteArrayOutputStream(length);
                    byte[] buffer = new byte[512];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        actual.write(buffer, 0, count);
                    }
                    assertEquals(actual.size(), length, "Sector chain must contain the entire stream");
                    assertEquals(actual.toByteArray(), expected, "Stream contents");
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
