package com.jpexs.flash.fla.gui;

import com.jpexs.flash.fla.converter.FlaTargetVersion;
import com.jpexs.flash.fla.exporter.XflExportResult;
import com.jpexs.flash.fla.exporter.XflFormatVersion;
import com.jpexs.flash.fla.exporter.XflVersionConverter;
import com.jpexs.flash.fla.reader.BinaryData;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class MainMenuFrameTest {

    @Test
    public void xflSelectionUsesAFileAndExportsToItsBasename() {
        for (FlaTargetVersion version : FlaTargetVersion.values()) {
            if (version.isBinary()) {
                continue;
            }
            MainMenuFrame.ConversionFileFilter filter = new MainMenuFrame.ConversionFileFilter(version, true);
            for (String name : new String[]{"animation", "animation.fla", "animation.spa", "animation.xfl", "animation.XFL"}) {
                File selected = filter.destination(new File("output", name));
                assertTrue(selected.getName().toLowerCase().endsWith(".xfl"));
                assertEquals(filter.output(selected), new File("output", "animation"));
            }
            assertEquals(filter.output(filter.destination(new File("output", "animation.v2.xfl"))),
                    new File("output", "animation.v2"));
            assertTrue(filter.accept(new File("animation.XFL")));
            assertFalse(filter.accept(new File("animation.fla")));
            assertTrue(filter.getDescription().contains("*.xfl"));
        }
    }

    @Test
    public void existingProjectIsOnlyReplacedWhenOverwriteWasApproved() throws Exception {
        XflExportResult result = new XflVersionConverter(XflFormatVersion.CS6).convert(Collections.singletonMap(
                "DOMDocument.xml", new BinaryData(("<DOMDocument xmlns='http://ns.adobe.com/xfl/2008/' "
                        + "xflVersion='2.2'/>").getBytes(StandardCharsets.UTF_8))));
        Path root = Files.createTempDirectory("gui-xfl-output-");
        Path directory = root.resolve("animation");
        try {
            MainMenuFrame.writeXflDirectory(result, directory.toFile(), false);
            Path stale = directory.resolve("LIBRARY/old.xml");
            Files.createDirectories(stale.getParent());
            Files.write(stale, new byte[]{1, 2, 3});
            try {
                MainMenuFrame.writeXflDirectory(result, directory.toFile(), false);
                fail("An existing project needs overwrite approval");
            } catch (IOException expected) {
                assertEquals(Files.readAllBytes(stale), new byte[]{1, 2, 3});
            }
            MainMenuFrame.writeXflDirectory(result, directory.toFile(), true);
            assertFalse(Files.exists(stale));
            for (String name : result.getFiles().keySet()) {
                assertEquals(Files.readAllBytes(directory.resolve(name)), result.getFiles().get(name).toByteArray());
            }
            assertTrue(Files.isRegularFile(directory.resolve("conversion-report.txt")));
            try (Stream<Path> files = Files.list(root)) {
                assertEquals(files.count(), 1L, "Temporary export directories must be removed");
            }
        } finally {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(file);
                }
            }
        }
    }
}
