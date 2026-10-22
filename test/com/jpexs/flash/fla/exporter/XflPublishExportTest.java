package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.converter.*;
import java.nio.file.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;
import static com.jpexs.flash.fla.exporter.XflIntegrationExportTest.*;

public class XflPublishExportTest {

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{"f5"}, {"mx"}, {"mx2004"}, {"f8"}, {"cs3"}, {"cs4"}};
    }

    @Test(dataProvider = "versions")
    public void nativeProfileMapsKeepStoredScalarValuesAndAcceptedLegacyPaths(String version) throws Exception {
        BinaryFlaDocument source = nativeFile(version, "0020_document_params");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertFalse(has(result, "PUBLISH_SETTINGS"), result.getReport());
        assertTrue(result.getFiles().containsKey("PublishSettings.xml"));
        Element xml = publish(result);
        assertNull(xml.getNamespaceURI());
        Element profile = (Element) xml.getElementsByTagName("flash_profile").item(0);
        assertEquals(profile.getAttribute("name"), "Default");
        assertEquals(profile.getAttribute("version"), "1.0");
        assertEquals(profile.getAttribute("current"), "true");
        FlaRecord properties = profile(source);
        Map<String, String> values = map(properties);
        String[][] fields = {{"CompressMovie", "Vector::Compress Movie"}, {"DebuggingPermitted", "Vector::Debugging Permitted"}, {"StreamCompress", "Vector::Stream Compress"}, {"EventFormat", "Vector::Event Format"}, {"AS3PackagePaths", "Vector::AS3 Package Paths"}, {"flashFileName", "PublishFormatProperties::flashFileName"}, {"Loop", "PublishHtmlProperties::Loop"}};
        for (String[] field : fields) {
            if (values.containsKey(field[1])) {
                assertEquals(xml.getElementsByTagName(field[0]).item(0).getTextContent(), values.get(field[1]), field[0]);
            }
        }
        if (values.containsKey("Vector::AS3 Library Paths")) {
            assertEquals(xml.getElementsByTagName("AS3LibraryPaths").item(0).getTextContent(), values.get("Vector::AS3 Library Paths"));
        }
        assertEquals(xml.getElementsByTagName("RSLPreloaderMethod").item(0).getTextContent(), "0".equals(values.get("Vector::RSLPreloaderMethod")) ? "wrap" : "event");
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(file.getValue().toByteArray());
            }
        }
    }

    @Test
    public void everyOriginalAdobePublishDocumentValidatesIncludingLibraryMetadata() throws Exception {
        int count = 0;
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get("testdata/fla/cs5"))) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.getFileName().toString().equals("PublishSettings.xml"))::iterator) {
                XflSchemaTestSupport.validate(Files.readAllBytes(path));
                count++;
            }
        }
        assertTrue(count >= 29, "Expected at least the original 29 Adobe publish documents, got " + count);
    }

    @Test
    public void swcPublishPropertiesValidateIncludingNewerCompressionType() throws Exception {
        byte[] nativeXml = Files.readAllBytes(Paths.get("testdata/fla/cs5/graphics/PublishSettings.xml"));
        XflSchemaTestSupport.validate(nativeXml);
        for (String fields : Arrays.asList("", "<swc>1</swc><swcDefaultName>0</swcDefaultName><swcFileName>library.swc</swcFileName>",
                "<swc/><swcDefaultName/><swcFileName/>")) {
            String xml = "<flash_profiles><flash_profile name='SWC' version='1.0'><PublishFormatProperties>"
                    + fields + "</PublishFormatProperties><PublishFlashProperties><CompressionType>1</CompressionType>"
                    + "</PublishFlashProperties></flash_profile></flash_profiles>";
            XflSchemaTestSupport.validate(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    public void profileNameAndLiteralXmlCharactersSurviveBothDirections() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        FlaRecord profile = profile(source);
        entry(profile, "PublishProfileProperties::name").set("value", "Custom <&\"' \u03a9");
        entry(profile, "Vector::DocumentClass").set("value", "example.Main<&");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = publish(result);
        assertEquals(((Element) xml.getElementsByTagName("flash_profile").item(0)).getAttribute("name"), "Custom <&\"' \u03a9");
        BinaryFlaDocument second = convert(result, FlaFormatVersion.CS4);
        assertTrue(second.getIssues().isEmpty(), second.getIssues().toString());
        assertEquals(map(profile(second)).get("PublishProfileProperties::name"), "Custom <&\"' \u03a9");
        assertEquals(map(profile(second)).get("Vector::DocumentClass"), "example.Main<&");
    }

    @DataProvider(name = "targets")
    public Object[][] targets() {
        return new Object[][]{{FlaFormatVersion.F5}, {FlaFormatVersion.MX}, {FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "targets")
    public void forwardWriterMapsXmlLabelsToRealArchiveKeysAndSelectsOneLegacyProfile(FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><timelines><DOMTimeline name='Scene 1'><layers/></DOMTimeline></timelines></DOMDocument>");
        input.put("PublishSettings.xml", "<flash_profiles><flash_profile name='First' version='1.0'><PublishFlashProperties><CompressMovie>1</CompressMovie></PublishFlashProperties></flash_profile><flash_profile name='Chosen' current='true' version='1.0'><PublishFlashProperties><CompressMovie>1</CompressMovie><DebuggingPermitted>1</DebuggingPermitted><EventCompress>9</EventCompress><AS3PackagePaths>my/code</AS3PackagePaths></PublishFlashProperties></flash_profile></flash_profiles>");
        BinaryFlaDocument source = convert(input, version);
        assertTrue(source.getIssues().isEmpty(), source.getIssues().toString());
        assertTrue(source.getContents().isComplete());
        assertEquals((long) source.getContents().getEndOffset(), source.getStreams().get("Contents").getData().size());
        FlaRecord settings = (FlaRecord) source.getContents().get("publishSettings");
        @SuppressWarnings("unchecked")
        List<FlaRecord> profiles = (List<FlaRecord>) settings.get("profiles");
        FlaRecord chosen = version.ordinal() < FlaFormatVersion.MX2004.ordinal() ? (FlaRecord) settings.get("legacyProperties") : profiles.get(1);
        Map<String, String> properties = map(chosen);
        assertEquals(properties.get("PublishProfileProperties::name"), "Chosen");
        assertEquals(properties.get("Vector::Compress Movie"), "1");
        assertEquals(properties.get("Vector::Debugging Permitted"), "1");
        assertEquals(properties.get("Vector::Event Compress"), "9");
        assertEquals(properties.get("Vector::AS3 Package Paths"), "my/code");
        if (profiles != null) {
            assertEquals(profiles.size(), 2);
        }
    }

    @DataProvider(name = "countedTargets")
    public Object[][] countedTargets() {
        return targets();
    }

    @Test(dataProvider = "countedTargets")
    public void zeroProfilesDoNotLeaveAnOrphanObjectMap(FlaFormatVersion version) throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><timelines><DOMTimeline name='Scene 1'><layers/></DOMTimeline></timelines></DOMDocument>");
        input.put("PublishSettings.xml", "<flash_profiles/>");
        BinaryFlaDocument source = convert(input, version);
        assertTrue(source.getIssues().isEmpty(), source.getIssues().toString());
        FlaRecord settings = (FlaRecord) source.getContents().get("publishSettings");
        if (version.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            assertEquals(((Number) settings.get("profileCount")).intValue(), 0);
        } else {
            assertNotNull(settings.get("legacyProperties"));
        }
        assertTrue(source.getContents().isComplete());
        assertEquals((long) source.getContents().getEndOffset(), source.getStreams().get("Contents").getData().size());
    }

    @Test
    public void unknownPropertiesAreDiagnosedAndCannotCreateArbitraryXml() throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        entry(profile(source), "Vector::UseAS3Namespace").set("key", "Unknown::unsafe tag");
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        assertTrue(has(result, "PUBLISH_PROPERTY"));
        Element xml = publish(result);
        assertEquals(xml.getElementsByTagName("unsafe").getLength(), 0);
    }

    @Test
    public void unknownElementsAndProfileAttributesStillFailSchemaValidation() throws Exception {
        for (String xml : Arrays.asList("<flash_profiles bad='1'/>", "<flash_profiles><flash_profile name='X' version='1.0'><Arbitrary/></flash_profile></flash_profiles>",
                "<flash_profiles><flash_profile name='X' version='1.0'><PublishFormatProperties><swcUnknown>1</swcUnknown></PublishFormatProperties></flash_profile></flash_profiles>",
                "<flash_profiles><flash_profile name='X' version='1.0'><PublishFormatProperties><swc>0</swc><swc>1</swc></PublishFormatProperties></flash_profile></flash_profiles>",
                "<flash_profiles><flash_profile name='X' version='1.0'><PublishFlashProperties><swcFileName>library.swc</swcFileName></PublishFlashProperties></flash_profile></flash_profiles>")) {
            try {
                XflSchemaTestSupport.validate(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fail("Unknown publish declaration accepted");
            } catch (org.xml.sax.SAXException expected) {
            }
        }
    }

    @Test
    public void countedProfileLoadingSelectsLastProfileAndKeepsObjectMapAssociation() throws Exception {
        Memory input = new Memory();
        input.put("DOMDocument.xml", "<DOMDocument><timelines><DOMTimeline name='Scene 1'><layers/></DOMTimeline></timelines></DOMDocument>");
        input.put("PublishSettings.xml", "<flash_profiles><flash_profile name='First' current='true' version='1.0'/><flash_profile name='Last' version='1.0'/></flash_profiles>");
        BinaryFlaDocument source = convert(input, FlaFormatVersion.CS4);
        @SuppressWarnings("unchecked")
        List<FlaRecord> profiles = (List<FlaRecord>) source.getContents().get("profileObjects");
        for (int i = 0; i < profiles.size(); i++) {
            FlaRecord audio = audio(profiles.get(i));
            audio.set("byteCount", 2);
            audio.set("data", new BinaryData(new byte[]{(byte) (0x80 + i), (byte) 0xff}));
        }
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = publish(result);
        NodeList list = xml.getElementsByTagName("flash_profile");
        assertEquals(list.getLength(), 2);
        assertEquals(((Element) list.item(0)).getAttribute("current"), "");
        assertEquals(((Element) list.item(1)).getAttribute("current"), "true");
        for (int i = 0; i < 2; i++) {
            assertEquals(((Element) list.item(i)).getElementsByTagName("QTSndSettings").item(0).getTextContent(), i == 0 ? "0200000080ff" : "0200000081ff");
        }
        assertFalse(has(result, "PUBLISH_OBJECT"), result.getReport());
        assertFalse(has(result, "PUBLISH_PROFILE_SELECTION"), result.getReport());
    }

    @DataProvider(name = "audio")
    public Object[][] audio() {
        return new Object[][]{{new byte[0], "00000000"}, {new byte[]{0, 1, (byte) 0x80, (byte) 0xff}, "04000000000180ff"}};
    }

    @Test(dataProvider = "audio")
    public void quickTimeSoundSettingsUseHexOfTheWholeInlineArchive(byte[] bytes, String expected) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        FlaRecord audio = audioOf(source);
        audio.set("byteCount", bytes.length);
        audio.set("data", new BinaryData(bytes));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        Element xml = publish(result);
        assertEquals(xml.getElementsByTagName("QTSndSettings").item(0).getTextContent(), expected);
        assertFalse(has(result, "PUBLISH_OBJECT"), result.getReport());
        assertEquals(((BinaryData) audio.get("data")).toByteArray(), bytes);
    }

    @DataProvider(name = "invalidAudio")
    public Object[][] invalidAudio() {
        return new Object[][]{{4093, 4093}, {2, 3}};
    }

    @Test(dataProvider = "invalidAudio")
    public void oversizedOrInconsistentQuickTimeAudioCannotProduceTruncatedXml(int length, int count) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        FlaRecord audio = audioOf(source);
        audio.set("byteCount", count);
        audio.set("data", new BinaryData(new byte[length]));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        publish(result);
        assertTrue(has(result, "PUBLISH_OBJECT"), result.getReport());
    }

    @DataProvider(name = "audioLengths")
    public Object[][] audioLengths() {
        return new Object[][]{{0}, {1}, {2}, {3}, {252}, {253}, {255}, {256}, {4091}, {4092}};
    }

    @Test(dataProvider = "audioLengths")
    public void nativeXmlByteDecoderRecoversLengthAndEveryPayloadByte(int length) throws Exception {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i * 37 + 129);
        }
        BinaryFlaDocument source = nativeFile("cs4", "0020_document_params");
        FlaRecord audio = audioOf(source);
        audio.set("byteCount", length);
        audio.set("data", new BinaryData(bytes));
        XflExportResult result = new BinaryFlaToXflExporter().export(source);
        String hex = publish(result).getElementsByTagName("QTSndSettings").item(0).getTextContent();
        assertFalse(has(result, "PUBLISH_OBJECT"), result.getReport());
        assertEquals(hex.length(), 2 * (length + 4));
        assertTrue(hex.length() / 2 <= 4096);
        // Match the independent importer: each successive pair is one byte.
        byte[] archive = new byte[hex.length() / 2];
        for (int i = 0; i < archive.length; i++) {
            archive[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        java.nio.ByteBuffer input = java.nio.ByteBuffer.wrap(archive).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(input.getInt(), length);
        byte[] restored = new byte[input.remaining()];
        input.get(restored);
        assertEquals(restored, bytes);
        assertEquals(((BinaryData) audio.get("data")).toByteArray(), bytes);
    }

    @SuppressWarnings("unchecked")
    static FlaRecord audio(FlaRecord map) {
        for (FlaRecord entry : (List<FlaRecord>) map.get("entries")) {
            if ("PublishQTProperties::QTSndSettings".equals(entry.get("key"))) {
                return (FlaRecord) entry.get("value");
            }
        }
        throw new AssertionError("No QuickTime sound settings");
    }

    @SuppressWarnings("unchecked")
    static FlaRecord audioOf(BinaryFlaDocument source) {
        return audio(((List<FlaRecord>) source.getContents().get("profileObjects")).get(0));
    }

    static Element publish(XflExportResult result) throws Exception {
        byte[] xml = result.getFiles().get("PublishSettings.xml").toByteArray();
        XflSchemaTestSupport.validate(xml);
        return parse(xml);
    }

    @SuppressWarnings("unchecked")
    static FlaRecord profile(BinaryFlaDocument source) {
        FlaRecord settings = (FlaRecord) source.getContents().get("publishSettings");
        FlaRecord legacy = (FlaRecord) settings.get("legacyProperties");
        return legacy != null ? legacy : ((List<FlaRecord>) settings.get("profiles")).get(0);
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> map(FlaRecord profile) {
        Map<String, String> result = new LinkedHashMap<>();
        for (FlaRecord entry : (List<FlaRecord>) profile.get("entries")) {
            result.put((String) entry.get("key"), (String) entry.get("value"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static FlaRecord entry(FlaRecord profile, String key) {
        for (FlaRecord entry : (List<FlaRecord>) profile.get("entries")) {
            if (key.equals(entry.get("key"))) {
                return entry;
            }
        }
        throw new AssertionError(key);
    }
}
