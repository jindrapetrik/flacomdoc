package com.jpexs.flash.fla.converter.streams;

import com.jpexs.flash.fla.converter.XflReader;
import com.jpexs.flash.fla.exporter.XflExportResult;
import com.jpexs.flash.fla.exporter.XflFormatVersion;
import com.jpexs.flash.fla.exporter.XflVersionConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ZippedInputStorageTest {

    @DataProvider(name = "duplicateMetadata")
    public Object[][] duplicateMetadata() {
        return new Object[][]{{false}, {true}};
    }

    @Test(dataProvider = "duplicateMetadata")
    public void invalidCentralDirectoryStillImportsBothConversionModels(boolean duplicateMetadata) throws Exception {
        Path file = Files.createTempFile("broken-central-directory-", ".fla");
        try {
            Files.write(file, archive(duplicateMetadata ? "mimetype" : null));
            try (ZipFile zip = new ZipFile(file.toFile())) {
                fail("The fixture must reproduce ZipFile's central directory failure");
            } catch (ZipException expected) {
            }
            try (ZippedInputStorage input = new ZippedInputStorage(file.toFile())) {
                assertTrue(input.fileExists("DOMDocument.xml"));
                assertFalse(input.fileExists("LIBRARY/"));
                assertNull(input.readFile("missing.xml"));
                assertTrue(input.getFileNames().contains("bin/media.dat"));
            }
            XflExportResult result = new XflVersionConverter(XflFormatVersion.CS6).convert(file.toFile());
            assertEquals(result.getFiles().get("bin/media.dat").toByteArray(), new byte[]{1, 2, 3, 4});
            assertEquals(result.getFiles().get("DOMDocument.xml").toByteArray(), resources().get("DOMDocument.xml"));
            assertEquals(new XflReader().read(file.toFile()).getSceneDescriptors().size(), 1);
        } finally {
            Files.delete(file);
        }
    }

    @Test
    public void recoveryRejectsCorruptedLocalEntryData() throws Exception {
        Path file = Files.createTempFile("broken-local-entry-", ".fla");
        try {
            byte[] bytes = archive(null);
            // The first entry is STORED, so changing its payload must fail CRC validation.
            ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int payload = 30 + Short.toUnsignedInt(header.getShort(26)) + Short.toUnsignedInt(header.getShort(28));
            bytes[payload] ^= 1;
            Files.write(file, bytes);
            try (ZippedInputStorage input = new ZippedInputStorage(file.toFile())) {
                fail("Corrupted entry data must be rejected");
            } catch (ZipException expected) {
                assertTrue(expected.getMessage().contains("CRC"));
                assertEquals(expected.getSuppressed().length, 1);
            }
        } finally {
            Files.delete(file);
        }
    }

    @Test
    public void duplicateProjectResourcesAreStillRejected() throws Exception {
        Path file = Files.createTempFile("duplicate-resource-", ".fla");
        try {
            Files.write(file, archive("DOMDocument.xml"));
            try {
                new XflVersionConverter(XflFormatVersion.CS6).convert(file.toFile());
                fail("Only regenerated metadata may be repeated");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Duplicate XFL resource path"));
            }
        } finally {
            Files.delete(file);
        }
    }

    private static Map<String, byte[]> resources() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("mimetype", "application/vnd.adobe.xfl".getBytes(StandardCharsets.US_ASCII));
        files.put("DOMDocument.xml", ("<DOMDocument xmlns='http://ns.adobe.com/xfl/2008/' xflVersion='2.2'>"
                + "<timelines><DOMTimeline name='Scene 1'><layers><DOMLayer name='Layer 1'><frames>"
                + "<DOMFrame index='0'><elements/></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines>"
                + "</DOMDocument>").getBytes(StandardCharsets.UTF_8));
        files.put("bin/media.dat", new byte[]{1, 2, 3, 4});
        return files;
    }

    private static byte[] archive(String duplicate) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] repeated = new byte[0];
        try (ZipOutputStream zip = new ZipOutputStream(data)) {
            for (Map.Entry<String, byte[]> resource : resources().entrySet()) {
                int start = data.size();
                ZipEntry entry = new ZipEntry(resource.getKey());
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(resource.getValue().length);
                CRC32 crc = new CRC32();
                crc.update(resource.getValue());
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(resource.getValue());
                zip.closeEntry();
                if (resource.getKey().equals(duplicate)) {
                    repeated = Arrays.copyOfRange(data.toByteArray(), start, data.size());
                }
            }
        }
        byte[] original = data.toByteArray();
        int end = original.length - 22;
        ByteBuffer header = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);
        int central = header.getInt(end + 16);
        byte[] broken = new byte[original.length + repeated.length];
        System.arraycopy(original, 0, broken, 0, central);
        System.arraycopy(repeated, 0, broken, central, repeated.length);
        System.arraycopy(original, central, broken, central + repeated.length, original.length - central);
        header = ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN);
        end += repeated.length;
        header.putInt(end + 16, central + repeated.length);
        // Reproduce as3.fla's END record, whose central directory size is 54 bytes too large.
        header.putInt(end + 12, header.getInt(end + 12) + 54);
        return broken;
    }
}
