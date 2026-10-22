package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.exporter.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class XflLegacyWaitExportTest {

    @DataProvider(name = "waits")
    public Object[][] waits() {
        return new Object[][]{
            {8, 2, false, "ifFrameLoaded (7) {\ngotoAndStop(5);\n}\n"},
            {10, -2, false, "ifFrameLoaded (3) {\ngotoAndPlay(5);\n}\n"},
            {11, 2, false, "ifFrameLoaded (7) {\ngetURL(\"movie.html\", \"_blank\");\ngotoAndStop(5);\n}\n"},
            {12, 2, false, "ifFrameLoaded (7) {\ngetURL(\"movie.html\", \"_blank\");\ngotoAndPlay(5);\n}\n"},
            {8, -16000, false, "gotoAndStop(5);\n"},
            {1, 2, false, "play();\n"},
            {8, 2, true, "gotoAndStop(5);\n"}
        };
    }

    @Test(dataProvider = "waits")
    public void oldSingleActionWaitBecomesALoadedFrameGuard(int kind, int extra, boolean list, String expected) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1);
        out.write(kind);
        string(out, "movie.html", false);
        string(out, "_blank", false);
        string(out, "", false);
        u16(out, 5);
        u16(out, extra);
        MfcArchive archive = archive(out.toByteArray());
        FlaRecord command = archive.readInline("historicalAction", r -> {
        }, ActionRecordDecoder::historical);
        assertEquals(archive.getRemaining(), 0);
        FlaRecord actions = command;
        if (list) {
            actions = new FlaRecord("legacyActions", 0, 0, 0, 0, new BinaryData(new byte[0]));
            actions.set("commands", Arrays.asList(command));
            actions.finish(0, true);
        }
        Path path = Paths.get("testdata/flang/mx/0013_frame_action");
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> fs = Files.walk(path)) {
            for (Path p : (Iterable<Path>) fs.filter(Files::isRegularFile)::iterator) {
                streams.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams);
        FlaRecord frame = null;
        for (BinaryFlaDocument.Page p : doc.getPages()) {
            frame = find(p.getRoot(), Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()));
            if (frame != null) {
                break;
            }
        }
        assertNotNull(frame);
        frame.set("actions", actions);
        frame.set("name", "Legacy wait target");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("HISTORICAL_SCRIPT")), result.getReport());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document xml = f.newDocumentBuilder().parse(result.getFiles().get("DOMDocument.xml").openStream());
        NodeList frames = xml.getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "DOMFrame");
        boolean found = false;
        for (int i = 0; i < frames.getLength(); i++) {
            if (((Element) frames.item(i)).getAttribute("name").equals("Legacy wait target")) {
                found = true;
                assertEquals(((Element) frames.item(i)).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "script").item(0).getTextContent(), expected);
            }
        }
        assertTrue(found);
    }

    static FlaRecord find(FlaRecord r, Set<FlaRecord> seen) {
        if (r == null || !seen.add(r)) {
            return null;
        }
        if (r.getClassName().equals("CPicFrame")) {
            return r;
        }
        for (FlaRecord child : r.getChildren()) {
            FlaRecord found = find(child, seen);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
