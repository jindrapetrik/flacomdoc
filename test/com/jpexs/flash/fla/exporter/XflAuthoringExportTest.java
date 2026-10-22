package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.sound.sampled.*;
import javax.xml.parsers.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;

/**
 * Native paired-file checks for media, text, effects and editable easing.
 */
public class XflAuthoringExportTest {

    private static final String NS = "http://ns.adobe.com/xfl/2008/";

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void soundsPreserveNativeBytesAndFrameEnvelope(String version) throws Exception {
        BinaryFlaDocument doc = nativeFile(version, "0014_sounds");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result), reference = reference("0014_sounds");
        List<Element> actual = elements(root, "DOMSoundItem"), expected = elements(reference, "DOMSoundItem");
        assertEquals(actual.size(), 6);
        for (int i = 0; i < actual.size(); i++) {
            Element a = actual.get(i), b = expected.get(i);
            for (String field : Arrays.asList("name", "format", "sampleCount")) {
                assertEquals(a.getAttribute(field), b.getAttribute(field));
            }
            assertEquals(result.getFiles().get("bin/" + a.getAttribute("soundDataHRef")).toByteArray(), doc.getMedia().get(i).getStream().getData().toByteArray());
            BinaryData wav = result.getFiles().get("LIBRARY/" + a.getAttribute("href"));
            assertNotNull(wav);
            try (AudioInputStream sound = AudioSystem.getAudioInputStream(wav.openStream())) {
                FlaSoundFormat f = doc.getMedia().get(i).getSoundFormat();
                assertEquals(sound.getFormat().getChannels(), f.getChannelCount());
                assertEquals(sound.getFormat().getSampleSizeInBits(), f.getBitsPerSample());
                assertEquals(sound.getFormat().getSampleRate(), (float) f.getSampleRateHz());
                BinaryData primary = (BinaryData) ((FlaRecord) doc.getMedia().get(i).getStream().getRoot().get("primaryAudio")).get("data");
                assertEquals(sound.getFrameLength(), (long) primary.size() / f.getPcmFrameByteCount());
            }
        }
        actual = elements(root, "DOMFrame");
        expected = elements(reference, "DOMFrame");
        for (int i = 0; i < actual.size(); i++) {
            Element a = actual.get(i), b = expected.get(i);
            assertEquals(a.getAttribute("soundName"), b.getAttribute("soundName"));
            assertEquals(a.getAttribute("soundSync"), attr(b, "soundSync", "event"));
            assertEquals(a.getAttribute("soundLoop"), attr(b, "soundLoop", "1"));
            assertEquals(a.getAttribute("soundEffect"), attr(b, "soundEffect", "none"));
            List<Element> ap = elements(a, "SoundEnvelopePoint"), bp = elements(b, "SoundEnvelopePoint");
            assertEquals(ap.size(), bp.size());
            for (int j = 0; j < ap.size(); j++) {
                for (String f : Arrays.asList("mark44", "level0", "level1")) {
                    assertEquals(ap.get(j).getAttribute(f), attr(bp.get(j), f, "0"));
                }
            }
        }
        assertFalse(has(result, "MEDIA_DATA"), result.getReport());
        assertFalse(has(result, "FRAME_SOUND"), result.getReport());
    }

    @Test(dataProvider = "versions")
    public void nativeVideoIndicesUseOffsetsLengthsFrameCountsAndFlags(String version) throws Exception {
        if (version.equals("f5")) {
            return;
        }
        BinaryFlaDocument doc = nativeFile(version, "0015_videos");
        int packets = 0;
        for (BinaryFlaDocument.Media media : doc.getMedia()) {
            FlaRecord payload = media.getStream().getRoot();
            if (!"videoPayload".equals(payload.getClassName())) {
                continue;
            }
            assertTrue(((Number) payload.get("videoWidth")).intValue() > 0);
            assertTrue(((Number) payload.get("videoHeight")).intValue() > 0);
            for (FlaRecord track : XflXml.records(payload, "tracks")) {
                int end = 0;
                for (FlaRecord packet : XflXml.records(track, "indices")) {
                    int offset = ((Number) packet.get("offsetBytes")).intValue();
                    int length = ((Number) packet.get("byteCount")).intValue();
                    assertEquals(offset, end);
                    assertTrue(length >= 0);
                    assertTrue(((Number) packet.get("frameCount")).intValue() >= 1);
                    assertNotNull(packet.get("flags"));
                    assertNull(packet.get("time"));
                    end += length;
                    packets++;
                }
                assertEquals((long) end, ((Number) track.get("byteCount")).longValue());
            }
        }
        assertTrue(packets > 0);
    }

    @DataProvider(name = "videoHeaderFields")
    public Object[][] videoHeaderFields() {
        return new Object[][]{{"audioBitsPerSample"}, {"audioChannels"}, {"videoWidth"}, {"videoHeight"}};
    }

    @Test(dataProvider = "videoHeaderFields")
    public void missingSemanticVideoHeaderDoesNotLeaveAPartialItem(String field) throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0015_videos");
        doc.getMedia().get(0).getStream().getRoot().set(field, null);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertTrue(has(result, "MEDIA_DATA"));
        assertEquals(elements(xml(result), "DOMVideoItem").size(), 2);
    }

    @Test
    public void bundledVideoMetadataIsExportedAndEmbeddedFileBytesStayIntact() throws Exception {
        BinaryFlaDocument doc = nativeFile("cs4", "0015_videos");
        BinaryFlaDocument.Media media = doc.getMedia().get(0);
        FlaRecord payload = media.getStream().getRoot();
        payload.set("isSpecial", 0x80000000L);
        payload.set("hasBundledDeviceVideo", 255L);
        payload.set("bundledSourceFilepath", "C:/video & clips/Ω.mov");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element item = elements(xml(result), "DOMVideoItem").get(0);
        assertEquals(item.getAttribute("isSpecial"), "true");
        assertEquals(item.getAttribute("hasBundledDeviceVideo"), "true");
        assertEquals(item.getAttribute("bundledSourceFilepath"), "C:/video & clips/Ω.mov");
        assertEquals(result.getFiles().get("bin/" + item.getAttribute("videoDataHRef")).toByteArray(), media.getStream().getData().toByteArray());
        assertFalse(item.hasAttribute("bundledVideoData"));
    }

    @Test(dataProvider = "versions")
    public void videoDimensionsAudioAndInstancesMatchNative(String version) throws Exception {
        if (version.equals("f5")) {
            return; // Video embedding was introduced in MX.
        }
        BinaryFlaDocument doc = nativeFile(version, "0015_videos");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result), reference = reference("0015_videos");
        List<Element> actual = elements(root, "DOMVideoItem"), expected = elements(reference, "DOMVideoItem");
        assertEquals(actual.size(), 3);
        for (int i = 0; i < actual.size(); i++) {
            Element a = actual.get(i), b = expected.get(i);
            for (String f : Arrays.asList("name", "videoType", "width", "height", "length", "fps")) {
                assertEquals(a.getAttribute(f), b.getAttribute(f));
            }
            assertEquals(a.getAttribute("channels"), attr(b, "channels", "1"));
            assertEquals(result.getFiles().get("bin/" + a.getAttribute("videoDataHRef")).toByteArray(), doc.getMedia().get(i).getStream().getData().toByteArray());
        }
        actual = elements(root, "DOMVideoInstance");
        expected = elements(reference, "DOMVideoInstance");
        assertEquals(actual.size(), expected.size());
        for (int i = 0; i < actual.size(); i++) {
            for (String f : Arrays.asList("libraryItemName", "frameLeft", "frameRight", "frameTop", "frameBottom")) {
                assertEquals(actual.get(i).getAttribute(f), attr(expected.get(i), f, "0"));
            }
        }
        assertFalse(has(result, "MEDIA_DATA"), result.getReport());
        assertFalse(has(result, "MISSING_VIDEO"), result.getReport());
    }

    @Test(dataProvider = "versions")
    public void textRunsPreserveCharactersTwipMetricsAndColors(String version) throws Exception {
        BinaryFlaDocument doc = nativeFile(version, "0012_texts");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result);
        List<FlaRecord> texts = records(doc, "CPicText");
        List<Element> exported = elements(root, "textRuns");
        assertEquals(exported.size(), texts.size());
        for (int i = 0; i < texts.size(); i++) {
            FlaRecord source = texts.get(i);
            List<Element> characters = elements(exported.get(i), "characters");
            StringBuilder text = new StringBuilder();
            for (Element c : characters) {
                text.append(c.getTextContent());
            }
            assertEquals(text.toString(), source.get("text"));
            @SuppressWarnings("unchecked")
            List<FlaRecord> runs = (List<FlaRecord>) source.get("runs");
            List<Element> attrs = elements(exported.get(i), "DOMTextAttrs");
            for (int j = 0; j < runs.size(); j++) {
                FlaRecord style = (FlaRecord) runs.get(j).get("style");
                assertEquals(Long.parseLong(attrs.get(j).getAttribute("bitmapSize")), ((Number) style.get("fontSize")).longValue());
                assertEquals(Double.parseDouble(attrs.get(j).getAttribute("letterSpacing")), ((Number) style.get("letterSpacing")).doubleValue() / 20);
                if (version.equals("cs4")) {
                    assertEquals(attrs.get(j).getAttribute("face"), style.get("fontFace"));
                }
            }
        }
        if (version.equals("cs4")) {
            List<Element> a = elements(root, "DOMTextAttrs"), b = elements(reference("0012_texts"), "DOMTextAttrs");
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                for (String f : Arrays.asList("face", "fillColor", "letterSpacing", "lineSpacing", "indent", "leftMargin", "rightMargin")) {
                    String fallback = f.equals("lineSpacing") ? "2" : "0";
                    if (f.equals("face") || f.equals("fillColor")) {
                        assertEquals(a.get(i).getAttribute(f), b.get(i).getAttribute(f));
                    } else {
                        assertEquals(Double.parseDouble(a.get(i).getAttribute(f)), Double.parseDouble(attr(b.get(i), f, fallback)));
                    }
                }
            }
            Element font = elements(root, "DOMFontItem").get(0);
            assertEquals(font.getAttribute("embeddedCharacters"), ".-*");
            assertEquals(font.getAttribute("embedRanges"), "1|2|17");
        }
    }

    @Test(dataProvider = "versions")
    public void proceduralStrokesRetainAllSixKinds(String version) throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile(version, "0005_strokes"));
        Element root = xml(result);
        for (String kind : Arrays.asList("Solid", "Dashed", "Dotted", "Ragged", "Stipple", "Hatched")) {
            assertFalse(elements(root, kind + "Stroke").isEmpty(), kind);
        }
        assertFalse(has(result, "STROKE_STYLE"), result.getReport());
    }

    @DataProvider(name = "modern")
    public Object[][] modern() {
        return new Object[][]{{"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "modern")
    public void filtersHaveValidValuesAndNativeOrder(String version) throws Exception {
        XflExportResult result = new BinaryFlaToXflExporter().export(nativeFile(version, "0011_filters"));
        Element root = xml(result);
        for (String kind : Arrays.asList("DropShadow", "Blur", "Glow", "Bevel", "GradientGlow", "GradientBevel", "AdjustColor")) {
            assertFalse(elements(root, kind + "Filter").isEmpty(), kind);
        }
        assertFalse(has(result, "FILTER_VALUE"), result.getReport());
        assertFalse(has(result, "INSTANCE_EFFECTS"), result.getReport());
    }

    @Test(dataProvider = "modern")
    public void customEasingRetainsNativeControlPoints(String version) throws Exception {
        BinaryFlaDocument doc = nativeFile(version, "0017_classictween");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        Element root = xml(result);
        assertFalse(elements(root, "CustomEase").isEmpty());
        for (Element ease : elements(root, "CustomEase")) {
            assertEquals((elements(ease, "Point").size() - 1) % 3, 0);
        }
        assertFalse(has(result, "CUSTOM_EASE"), result.getReport());
    }

    @Test
    public void missingSoundAndVideoStreamsDoNotLeaveDanglingItems() throws Exception {
        for (String sample : Arrays.asList("0014_sounds", "0015_videos")) {
            Map<String, byte[]> streams = streams(Paths.get("testdata/fla/cs4", sample));
            streams.keySet().removeIf(p -> p.startsWith("M "));
            XflExportResult result = new BinaryFlaToXflExporter().export(new BinaryFlaReader().readStreams(streams));
            Element root = xml(result);
            assertTrue(elements(root, "DOMSoundItem").isEmpty());
            assertTrue(elements(root, "DOMVideoItem").isEmpty());
            assertTrue(has(result, "MEDIA_DATA"));
        }
    }

    @Test
    public void originalAdobeSoundVideoAndTextDocumentsValidate() throws Exception {
        for (String sample : Arrays.asList("0012_texts", "0014_sounds", "0015_videos")) {
            XflSchemaTestSupport.validate(Files.readAllBytes(Paths.get("testdata/fla/cs5", sample, "DOMDocument.xml")));
        }
    }

    static BinaryFlaDocument nativeFile(String version, String sample) throws Exception {
        Path path = Paths.get("testdata/fla", version, sample);
        if (!Files.isDirectory(path)) {
            path = Paths.get("testdata/fla", version, sample + "-todo");
        }
        return new BinaryFlaReader().readStreams(streams(path));
    }

    static Map<String, byte[]> streams(Path dir) throws Exception {
        Map<String, byte[]> ret = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                ret.put(dir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        return ret;
    }

    static Element xml(XflExportResult result) throws Exception {
        byte[] data = result.getFiles().get("DOMDocument.xml").toByteArray();
        XflSchemaTestSupport.validate(data);
        return parse(data);
    }

    static Element reference(String name) throws Exception {
        return parse(Files.readAllBytes(Paths.get("testdata/fla/cs5", name, "DOMDocument.xml")));
    }

    static Element parse(byte[] data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(data)).getDocumentElement();
    }

    static List<Element> elements(Element root, String name) {
        List<Element> result = new ArrayList<>();
        NodeList list = root.getElementsByTagNameNS(NS, name);
        for (int i = 0; i < list.getLength(); i++) {
            result.add((Element) list.item(i));
        }
        return result;
    }

    static String attr(Element e, String name, String fallback) {
        return e.hasAttribute(name) ? e.getAttribute(name) : fallback;
    }

    static boolean has(XflExportResult result, String code) {
        return result.getIssues().stream().anyMatch(i -> i.getCode().equals(code));
    }

    static List<FlaRecord> records(BinaryFlaDocument d, String type) {
        List<FlaRecord> result = new ArrayList<>();
        Set<FlaRecord> seen = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
        for (BinaryFlaDocument.Page p : d.getPages()) {
            visit(p.getRoot(), type, seen, result);
        }
        return result;
    }

    static void visit(FlaRecord r, String type, Set<FlaRecord> seen, List<FlaRecord> out) {
        if (r == null || !seen.add(r)) {
            return;
        }
        if (type.equals(r.getClassName())) {
            out.add(r);
        }
        for (FlaRecord child : r.getChildren()) {
            visit(child, type, seen, out);
        }
    }
}
