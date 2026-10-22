package com.jpexs.flash.fla.converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import static org.testng.Assert.*;

public class FontMetadataTest {

    @DataProvider(name = "classifications")
    public Object[][] classifications() {
        return new Object[][]{
            // PANOSE family, serif style and proportion; post.isFixedPitch; expected LOGFONT byte.
            {2, 2, 3, 0, 0x12}, // Times New Roman
            {2, 11, 4, 0, 0x22}, // Arial
            {2, 15, 2, 0, 0x22}, // Calibri
            {3, 15, 2, 0, 0x42}, // Comic Sans MS
            {2, 7, 9, 65537, 0x31}, // Courier New: any nonzero post value means fixed pitch.
            {2, 11, 9, 1, 0x31}, // Lucida Console
            {2, 11, 4, 0, 0x22}, // Tahoma
            {2, 4, 2, 0, 0x12}, // Georgia
            {5, 3, 2, 0, 0x12}, // Webdings
            {5, 0, 0, 0, 0x02}, // Wingdings
            {4, 0, 0, 0, 0x52}, // Decorative
            {2, 11, 9, 0, 0x31}, // PANOSE alone can identify monospace.
            {2, 2, 3, 1, 0x31} // post.isFixedPitch takes precedence over proportional PANOSE.
        };
    }

    @Test(dataProvider = "classifications")
    public void reconstructsPitchAndFamily(int family, int serif, int proportion, int fixed, int expected) throws Exception {
        Map<String, byte[]> tables = tables("FixturePS", family, serif, proportion, fixed, 1, false);
        File file = temporaryFont(sfnt(tables, false, 0));
        try {
            FontMetadata result = FontMetadata.read(file, 0);
            assertEquals(result.getPitchAndFamily(), expected);
            assertEquals(result.getCharSet(), 0);
        } finally {
            Files.delete(file.toPath());
        }
    }

    @DataProvider(name = "charsets")
    public Object[][] charsets() {
        return new Object[][]{
            {1L, false, 0}, {2L, false, 238}, {4L, false, 204}, {8L, false, 161},
            {16L, false, 162}, {32L, false, 177}, {64L, false, 178}, {128L, false, 186},
            {256L, false, 163}, {1L << 16, false, 222}, {1L << 17, false, 128},
            {1L << 18, false, 134}, {1L << 19, false, 129}, {1L << 20, false, 136},
            {1L << 21, false, 130}, {1L << 22, false, 0},
            {1L << 29, false, 77}, {1L << 30, false, 255},
            {0x80000001L, false, 2}, {1L, true, 2}, {0L, false, 0},
            {0x00030003L, false, 0} // ANSI wins over other supported code pages.
        };
    }

    @Test(dataProvider = "charsets")
    public void selectsDeterministicWindowsCharset(long codePages, boolean symbol, int expected) throws Exception {
        File file = temporaryFont(sfnt(tables("FixturePS", 2, 2, 3, 0, codePages, symbol), true, 0));
        try {
            assertEquals(FontMetadata.read(file, 0).getCharSet(), expected);
        } finally {
            Files.delete(file.toPath());
        }
    }

    @Test
    public void readsLegacyOs2AndSymbolCmapWithoutCodePageFlags() throws Exception {
        Map<String, byte[]> tables = tables("LegacyPS", 5, 0, 0, 0, 0, true);
        byte[] os2 = Arrays.copyOf(tables.get("OS/2"), 78);
        os2[1] = 0; // OS/2 version 0 has no code-page fields.
        tables.put("OS/2", os2);
        File file = temporaryFont(sfnt(tables, false, 0));
        try {
            assertEquals(FontMetadata.read(file, 0).getCharSet(), 2);
        } finally {
            Files.delete(file.toPath());
        }
    }

    @Test
    public void missingClassificationTablesReturnNeutralValues() throws Exception {
        File file = temporaryFont(sfnt(Collections.singletonMap("name", nameTable("UnknownPS")), false, 0));
        try {
            FontMetadata result = FontMetadata.read(file, 0);
            assertEquals(result.getPitchAndFamily(), 0);
            assertEquals(result.getCharSet(), 0);
        } finally {
            Files.delete(file.toPath());
        }
    }

    @Test
    public void readsUnicodeAndMacintoshPostScriptNames() throws Exception {
        for (int platform : new int[]{0, 1}) {
            byte[] text = "PortablePS".getBytes(platform == 0 ? StandardCharsets.UTF_16BE : StandardCharsets.US_ASCII);
            ByteBuffer name = ByteBuffer.allocate(18 + text.length);
            name.putShort((short) 0).putShort((short) 1).putShort((short) 18)
                    .putShort((short) platform).putShort((short) 0).putShort((short) 0)
                    .putShort((short) 6).putShort((short) text.length).putShort((short) 0).put(text);
            File file = temporaryFont(sfnt(Collections.singletonMap("name", name.array()), true, 0));
            try {
                assertEquals(FontMetadata.readPostScriptNames(file).get("PortablePS"), Long.valueOf(0));
            } finally {
                Files.delete(file.toPath());
            }
        }
    }

    @Test
    public void collectionIndexPreservesTheSelectedFaceAndAbsoluteTableOffsets() throws Exception {
        Map<String, byte[]> first = tables("FirstPS", 2, 2, 3, 0, 1, false);
        Map<String, byte[]> second = tables("SecondPS", 5, 0, 0, 0, 0x80000000L, true);
        byte[] a = sfnt(first, false, 20);
        byte[] b = sfnt(second, true, 20 + a.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(0x74746366);
        data.writeInt(0x00010000);
        data.writeInt(2);
        data.writeInt(20);
        data.writeInt(20 + a.length);
        data.write(a);
        data.write(b);
        File file = temporaryFont(out.toByteArray());
        try {
            Map<String, Long> names = FontMetadata.readPostScriptNames(file);
            assertEquals(names.size(), 2);
            assertEquals(names.get("SecondPS").longValue(), 20 + a.length);
            FontMetadata result = FontMetadata.read(file, names.get("SecondPS"));
            assertEquals(result.getPitchAndFamily(), 0x02);
            assertEquals(result.getCharSet(), 2);
        } finally {
            Files.delete(file.toPath());
        }
    }

    @Test
    public void indexDoesNotReadUnusedMetadataAndCachesUsedFonts() throws Exception {
        Path dir = Files.createTempDirectory("font-index-");
        Path used = dir.resolve("used.OtF");
        Path unused = dir.resolve("unused.ttf");
        try {
            Files.write(used, sfnt(tables("UsedPS", 2, 11, 4, 0, 1, false), true, 0));
            Map<String, byte[]> damaged = tables("UnusedPS", 2, 2, 3, 0, 1, false);
            damaged.put("OS/2", new byte[1]);
            Files.write(unused, sfnt(damaged, false, 0));
            SystemFontFiles fonts = new SystemFontFiles(Collections.singletonList(dir.toFile()));
            FontMetadata result = fonts.getMetadata("UsedPS");
            assertEquals(result.getPitchAndFamily(), 0x22);
            Files.delete(used);
            assertSame(fonts.getMetadata("UsedPS"), result);
            assertSame(fonts.getMetadata("UnusedPS"), FontMetadata.UNKNOWN);
            assertSame(fonts.getMetadata("NotInstalledPS"), FontMetadata.UNKNOWN);
        } finally {
            Files.deleteIfExists(used);
            Files.deleteIfExists(unused);
            Files.delete(dir);
        }
    }

    @Test
    public void discoveryHandlesMissingDirectoriesOverlappingRootsAndMixedCaseExtensions() throws Exception {
        Path dir = Files.createTempDirectory("font-discovery-");
        Path nested = Files.createDirectory(dir.resolve("nested"));
        String[] names = {"a.TtF", "b.OTF", "c.ttc", "d.OtC", "ignored.txt"};
        try {
            for (String name : names) {
                Files.write(nested.resolve(name), new byte[0]);
            }
            SystemFontFiles fonts = new SystemFontFiles(Arrays.asList(dir.resolve("missing").toFile(), dir.toFile(), nested.toFile()));
            List<File> files = fonts.getSystemFontFiles();
            assertEquals(files.size(), 4);
            assertEquals(files.get(0).getName(), "a.TtF");
        } finally {
            for (String name : names) {
                Files.delete(nested.resolve(name));
            }
            Files.delete(nested);
            Files.delete(dir);
        }
    }

    @Test
    public void includesModernUserAndSystemDirectoriesOnEachPlatform() {
        Map<String, String> env = new HashMap<>();
        env.put("XDG_DATA_HOME", "/data/user");
        env.put("XDG_DATA_DIRS", "/data/shared:/other");
        assertEquals(SystemFontFiles.getSystemFontDirectories("LINUX", "/home/test", env), Arrays.asList(
                new File("/data/user/fonts"), new File("/home/test/.fonts"),
                new File("/data/shared/fonts"), new File("/other/fonts")));
        assertTrue(SystemFontFiles.getSystemFontDirectories("Linux", "/home/test", Collections.emptyMap())
                .contains(new File("/home/test/.local/share/fonts")));
        env.put("WINDIR", "C:/Windows");
        env.put("LOCALAPPDATA", "C:/Users/test/AppData/Local");
        List<File> windows = SystemFontFiles.getSystemFontDirectories("Windows 10", "C:/Users/test", env);
        assertEquals(windows.get(0), new File("C:/Users/test/AppData/Local/Microsoft/Windows/Fonts"));
        assertEquals(windows.get(1), new File("C:/Windows/Fonts"));
        assertTrue(SystemFontFiles.getSystemFontDirectories("Mac OS X", "/Users/test", Collections.emptyMap())
                .contains(new File("/Users/test/Library/Fonts")));
    }

    @Test
    public void rejectsTruncatedOrOutOfRangeFontStructures() throws Exception {
        byte[] valid = sfnt(tables("FixturePS", 2, 2, 3, 0, 1, false), false, 0);
        byte[] badDirectory = valid.clone();
        ByteBuffer.wrap(badDirectory).putInt(20, -1); // Unsigned table offset 0xFFFFFFFF.
        assertInvalid(badDirectory, false);
        assertInvalid(Arrays.copyOf(valid, 16), true);
        Map<String, byte[]> bad = tables("FixturePS", 2, 2, 3, 0, 1, false);
        bad.put("post", new byte[15]);
        assertInvalid(sfnt(bad, true, 0), false);
        bad = tables("FixturePS", 2, 2, 3, 0, 1, false);
        bad.put("OS/2", new byte[10]);
        assertInvalid(sfnt(bad, false, 0), false);
        bad = tables("FixturePS", 2, 2, 3, 0, 1, false);
        ByteBuffer.wrap(bad.get("name")).putShort(4, (short) 65535);
        assertInvalid(sfnt(bad, false, 0), true);
        bad = tables("FixturePS", 2, 2, 3, 0, 1, true);
        ByteBuffer.wrap(bad.get("cmap")).putInt(8, -1);
        assertInvalid(sfnt(bad, false, 0), false);
    }

    @Test
    public void timelineWritesMetadataFromTheSelectedPostScriptFontInAllVersions() throws Exception {
        Path dir = Files.createTempDirectory("font-timeline-");
        Path font = dir.resolve("symbol.otf");
        try {
            Files.write(font, sfnt(tables("SyntheticSymbolPS", 5, 0, 0, 0, 0x80000000L, true), true, 0));
            SystemFontFiles fonts = new SystemFontFiles(Collections.singletonList(dir.toFile()));
            for (FlaFormatVersion version : FlaFormatVersion.values()) {
                for (String face : new String[]{"SyntheticSymbolPS", "Imported*", "MissingPS"}) {
                    if (face.equals("Imported*") && version.ordinal() < FlaFormatVersion.F5.ordinal()) {
                        continue; // These target versions do not support library font aliases.
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    new TimelineConverter(version, "windows-1250", "test", fonts).convert(xml(
                            "<DOMTimeline name='Scene'><layers><DOMLayer name='Layer'><frames>"
                            + "<DOMFrame index='0' keyMode='9728'><elements><DOMStaticText>"
                            + "<textRuns><DOMTextRun><characters>A</characters><textAttrs>"
                            + "<DOMTextAttrs face='" + face + "' fillColor='#123456'/>"
                            + "</textAttrs></DOMTextRun></textRuns></DOMStaticText></elements>"
                            + "</DOMFrame></frames></DOMLayer></layers></DOMTimeline>"), xml("<DOMDocument><fonts>"
                                    + "<DOMFontItem name='Imported' font='SyntheticSymbolPS'/></fonts></DOMDocument>"), out, null);
                    byte[] bytes = out.toByteArray();
                    int color = findColor(bytes);
                    assertTrue(color >= 0, version + ": text-run color");
                    int expected = face.equals("MissingPS") ? 0 : 2;
                    assertEquals(bytes[color + 4] & 255, expected, version + ": " + face + " pitch/family");
                    assertEquals(bytes[color + 5] & 255, expected, version + ": " + face + " charset");
                }
            }
        } finally {
            Files.delete(font);
            Files.delete(dir);
        }
    }

    private int findColor(byte[] bytes) {
        for (int i = 0; i + 5 < bytes.length; i++) {
            if (bytes[i] == 0x12 && bytes[i + 1] == 0x34 && bytes[i + 2] == 0x56 && bytes[i + 3] == (byte) 255) {
                return i;
            }
        }
        return -1;
    }

    private Element xml(String value) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    private void assertInvalid(byte[] bytes, boolean names) throws Exception {
        File file = temporaryFont(bytes);
        try {
            try {
                if (names) {
                    FontMetadata.readPostScriptNames(file);
                } else {
                    FontMetadata.read(file, 0);
                }
                fail("Malformed font accepted");
            } catch (IOException expected) {
                // Expected parse failure, rather than an unchecked buffer or allocation error.
            }
        } finally {
            Files.delete(file.toPath());
        }
    }

    private File temporaryFont(byte[] bytes) throws IOException {
        Path path = Files.createTempFile("font-metadata-", ".ttf");
        Files.write(path, bytes);
        return path.toFile();
    }

    private Map<String, byte[]> tables(String psName, int family, int serif, int proportion,
            int fixed, long codePages, boolean symbol) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("name", nameTable(psName));
        byte[] os2 = new byte[96];
        ByteBuffer data = ByteBuffer.wrap(os2).order(ByteOrder.BIG_ENDIAN);
        data.putShort(0, (short) 4);
        os2[32] = (byte) family;
        os2[33] = (byte) serif;
        os2[35] = (byte) proportion;
        data.putInt(78, (int) codePages);
        result.put("OS/2", os2);
        byte[] post = new byte[32];
        ByteBuffer.wrap(post).putInt(0, 0x00030000).putInt(12, fixed);
        result.put("post", post);
        byte[] cmap = new byte[24];
        ByteBuffer.wrap(cmap).putShort(2, (short) 1).putShort(4, (short) 3)
                .putShort(6, (short) (symbol ? 0 : 1)).putInt(8, 12).putShort(12, (short) 6).putShort(14, (short) 12);
        result.put("cmap", cmap);
        return result;
    }

    private byte[] nameTable(String psName) {
        byte[] text = psName.getBytes(StandardCharsets.UTF_16BE);
        ByteBuffer data = ByteBuffer.allocate(18 + text.length).order(ByteOrder.BIG_ENDIAN);
        data.putShort((short) 0).putShort((short) 1).putShort((short) 18);
        data.putShort((short) 3).putShort((short) 1).putShort((short) 0x0409).putShort((short) 6)
                .putShort((short) text.length).putShort((short) 0).put(text);
        return data.array();
    }

    private byte[] sfnt(Map<String, byte[]> tables, boolean otf, int baseOffset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(otf ? 0x4F54544F : 0x00010000);
        data.writeShort(tables.size());
        data.write(new byte[6]);
        int offset = 12 + 16 * tables.size();
        for (Map.Entry<String, byte[]> table : tables.entrySet()) {
            data.write(table.getKey().getBytes(StandardCharsets.US_ASCII));
            data.writeInt(0);
            data.writeInt(baseOffset + offset);
            data.writeInt(table.getValue().length);
            offset += (table.getValue().length + 3) & ~3;
        }
        for (byte[] table : tables.values()) {
            data.write(table);
            for (int i = table.length; (i & 3) != 0; i++) {
                data.writeByte(0);
            }
        }
        return out.toByteArray();
    }
}
