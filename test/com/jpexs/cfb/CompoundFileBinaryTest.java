package com.jpexs.cfb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CompoundFileBinaryTest {

    @Test
    public void creatingOverExistingFileTruncatesEvenIfDeletionIsDenied() throws Exception {
        Path path = Files.createTempFile("cfb-overwrite-", ".cfb");
        try {
            byte[] previous = new byte[16385];
            Arrays.fill(previous, (byte) 0x7f);
            Files.write(path, previous);
            File file = new File(path.toString()) {
                @Override
                public boolean delete() {
                    // A file can allow writes while denying deletion (e.g. an open ZIP).
                    return false;
                }
            };
            try (CompoundFileBinary cfb = new CompoundFileBinary(file, true)) {
                cfb.addFile("Contents", new byte[]{1, 2, 3});
            }
            assertEquals(Files.size(path), 4096L, "Old trailing bytes must be removed");
            try (CompoundFileBinary cfb = new CompoundFileBinary(file);
                    InputStream in = cfb.getEntryStream(cfb.getEntryByPath("Contents"))) {
                assertEquals(in.read(), 1);
                assertEquals(in.read(), 2);
                assertEquals(in.read(), 3);
                assertEquals(in.read(), -1);
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

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
