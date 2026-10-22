/*
 * Copyright (C) 2026 JPEXS.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.BinaryData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;

public class XflVersionConverterTest {

    private static final String NS = "http://ns.adobe.com/xfl/2008/";

    @DataProvider(name = "versions")
    public Object[][] versions() {
        return new Object[][]{{XflFormatVersion.CS5}, {XflFormatVersion.CS5_5}, {XflFormatVersion.CS6}};
    }

    @Test(dataProvider = "versions")
    public void nestedFeaturesAreDowngradedAndSupportedResourcesRemain(XflFormatVersion target) throws Exception {
        Map<String, BinaryData> source = project();
        byte[] original = source.get("LIBRARY/symbol.xml").toByteArray();
        XflExportResult result = new XflVersionConverter(target).convert(source);
        Element root = xml(result, "DOMDocument.xml");
        assertEquals(root.getAttribute("xflVersion"), target.getXflVersion());
        assertEquals(root.hasAttribute("autoSaveEnabled"), target != XflFormatVersion.CS5);
        Element symbol = xml(result, "LIBRARY/symbol.xml");
        Element instance = (Element) symbol.getElementsByTagNameNS(NS, "DOMSymbolInstance").item(0);
        assertEquals(instance.hasAttribute("isVisible"), target != XflFormatVersion.CS5);
        assertEquals(instance.hasAttribute("exportAsBitmap"), target != XflFormatVersion.CS5);
        assertEquals(instance.hasAttribute("bits32"), target != XflFormatVersion.CS5);
        assertEquals(symbol.getElementsByTagNameNS(NS, "pins").getLength(), target == XflFormatVersion.CS5 ? 0 : 2);
        assertEquals(symbol.getElementsByTagNameNS(NS, "MatteColor").getLength(), target == XflFormatVersion.CS5 ? 0 : 1);
        if (target == XflFormatVersion.CS5) {
            assertEquals(instance.getAttribute("cacheAsBitmap"), "true");
            Element color = (Element) instance.getElementsByTagNameNS(NS, "Color").item(0);
            assertFalse(color.hasAttribute("alphaMultiplier"));
            assertEquals(color.getAttribute("alphaOffset"), "10");
            assertEquals(color.getAttribute("redMultiplier"), "0.5");
        } else {
            assertEquals(result.getFiles().get("LIBRARY/symbol.xml").toByteArray(), original);
        }
        assertEquals(symbol.getElementsByTagNameNS(NS, "IKNode").getLength(), 1);
        assertEquals(symbol.getElementsByTagNameNS(NS, "DOMTLFText").getLength(), 1);
        assertEquals(symbol.getElementsByTagNameNS("http://ns.adobe.com/textLayout/2008", "TextFlow").item(0).getTextContent(), " A B ");
        assertEquals(symbol.getElementsByTagNameNS(NS, "script").item(0).getTextContent(), "trace('preserved');");
        assertEquals(symbol.getAttribute("scaleGridLeft"), "25");
        for (String path : Arrays.asList("bin/bitmap.dat", "bin/clip.swf", "LIBRARY/audio.mp3", "META-INF/metadata.xml", "MobileSettings.xml", "custom.dat")) {
            assertEquals(result.getFiles().get(path).toByteArray(), source.get(path).toByteArray(), path);
        }
        assertEquals(source.get("LIBRARY/symbol.xml").toByteArray(), original);
        assertTrue(result.getIssues().stream().allMatch(i -> !i.getStreamPath().isEmpty()));
    }

    @Test(dataProvider = "versions")
    public void allPublishProfilesRespectTargetCompressionAndPlayer(XflFormatVersion target) throws Exception {
        XflExportResult result = new XflVersionConverter(target).convert(project());
        Element publish = xml(result, "PublishSettings.xml");
        NodeList profiles = publish.getElementsByTagName("PublishFlashProperties");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element p = (Element) profiles.item(i);
            assertEquals(p.getElementsByTagName("CompressionType").getLength(), target == XflFormatVersion.CS6 ? 1 : 0);
            assertEquals(p.getElementsByTagName("CompressMovie").item(0).getTextContent(), "1");
            assertEquals(p.getElementsByTagName("Version").item(0).getTextContent(), target == XflFormatVersion.CS6 ? "15" : target == XflFormatVersion.CS5 ? "10" : "11");
            assertEquals(p.getElementsByTagName("ExternalPlayer").item(0).getTextContent(), target == XflFormatVersion.CS6 ? "FlashPlayer11.2" : target == XflFormatVersion.CS5 ? "FlashPlayer10" : "FlashPlayer10.2");
        }
        assertEquals(result.getIssues().isEmpty(), target == XflFormatVersion.CS6);
        assertTrue(result.getReport().startsWith("XFL 2.2 to XFL " + target.getXflVersion()));
        assertFalse(result.getReport().contains("binary FLA backup"));
    }

    @Test
    public void directoryMarkerZipAndInPlaceZipUseTheSameResources() throws Exception {
        Path temp = Files.createTempDirectory("xfl-versions-");
        try {
            Path input = temp.resolve("input");
            new XflVersionConverter(XflFormatVersion.CS6).convert(project()).writeToDirectory(input.toFile());
            XflVersionConverter converter = new XflVersionConverter(XflFormatVersion.CS5);
            XflExportResult folder = converter.convert(input.toFile());
            XflExportResult marker = converter.convert(input.resolve("document.xfl").toFile());
            assertFiles(folder, marker);
            Path zip = temp.resolve("input.fla");
            new XflVersionConverter(XflFormatVersion.CS6).convert(project()).writeToZip(zip.toFile());
            XflExportResult compressed = converter.convert(zip.toFile());
            assertFiles(folder, compressed);
            compressed.writeToZip(zip.toFile());
            assertEquals(xml(converter.convert(zip.toFile()), "DOMDocument.xml").getAttribute("xflVersion"), "2.0");
            try (ZipFile check = new ZipFile(zip.toFile())) {
                assertEquals(check.entries().nextElement().getName(), "mimetype");
                assertEquals(check.getEntry("mimetype").getMethod(), ZipEntry.STORED);
                assertNotNull(check.getEntry("document.xfl"));
                assertNotNull(check.getEntry("conversion-report.txt"));
            }
        } finally {
            remove(temp);
        }
    }

    @Test
    public void oldPlayerTargetsAndUnchangedXmlStayUnchanged() throws Exception {
        Map<String, BinaryData> files = project();
        put(files, "PublishSettings.xml", "<flash_profiles><flash_profile><PublishFlashProperties><Version>9</Version><ExternalPlayer>FlashPlayer9</ExternalPlayer></PublishFlashProperties></flash_profile></flash_profiles>");
        XflExportResult result = new XflVersionConverter(XflFormatVersion.CS5).convert(files);
        assertEquals(result.getFiles().get("PublishSettings.xml").toByteArray(), files.get("PublishSettings.xml").toByteArray());
    }

    @DataProvider(name = "hiddenColors")
    public Object[][] hiddenColors() {
        return new Object[][]{{""}, {"brightness='0.25'"}, {"tintColor='#FF0000' tintMultiplier='0.5'"}, {"alphaMultiplier='0.5' alphaOffset='10'"}};
    }

    @Test(dataProvider = "hiddenColors")
    public void invisibleInstancesBecomeVisibleWithoutChangingColorOrAlpha(String attributes) throws Exception {
        Map<String, BinaryData> files = new LinkedHashMap<>();
        put(files, "DOMDocument.xml", "<DOMDocument xmlns='" + NS + "' xflVersion='2.2'><timelines><DOMTimeline name='Scene 1'><layers><DOMLayer name='Layer 1' color='#000000'><frames><DOMFrame index='0' keyMode='9728'><elements><DOMSymbolInstance libraryItemName='test' isVisible='false'>"
                + (attributes.isEmpty() ? "" : "<color><Color " + attributes + "/></color>")
                + "<persistentData/></DOMSymbolInstance></elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines><PrinterSettings/><publishHistory/></DOMDocument>");
        Element before = XflVersionRules.parse(files.get("DOMDocument.xml"), "DOMDocument.xml").getDocumentElement();
        XflExportResult result = new XflVersionConverter(XflFormatVersion.CS5).convert(files);
        XflSchemaTestSupport.validate(result.getFiles().get("DOMDocument.xml").toByteArray());
        Element after = xml(result, "DOMDocument.xml");
        Element instance = (Element) after.getElementsByTagNameNS(NS, "DOMSymbolInstance").item(0);
        assertFalse(instance.hasAttribute("isVisible"));
        Node originalColor = before.getElementsByTagNameNS(NS, "Color").item(0);
        Node convertedColor = after.getElementsByTagNameNS(NS, "Color").item(0);
        if (originalColor == null) {
            assertNull(convertedColor);
        } else {
            assertTrue(originalColor.isEqualNode(convertedColor));
        }
        assertTrue(result.getReport().contains("becomes visible"));
    }

    @Test
    public void rejectsUnsafePathsExternalEntitiesMissingSymbolsAndFutureVersions() throws Exception {
        for (String path : Arrays.asList("../escape.dat", "/escape.dat", "C:/escape.dat", "bin/../escape.dat", "bin//bad")) {
            Map<String, BinaryData> files = project();
            files.put(path, new BinaryData(new byte[0]));
            reject(files);
        }
        Map<String, BinaryData> files = project();
        files.put("domdocument.xml", files.get("DOMDocument.xml"));
        reject(files);
        files = project();
        files.remove("LIBRARY/symbol.xml");
        reject(files);
        files = project();
        put(files, "DOMDocument.xml", "<!DOCTYPE DOMDocument [<!ENTITY x SYSTEM 'file:///missing'>]><DOMDocument xflVersion='2.2'>&x;</DOMDocument>");
        reject(files);
        files = project();
        put(files, "DOMDocument.xml", "<DOMDocument xflVersion='2.4'/>");
        reject(files);
    }

    @Test(dataProvider = "versions")
    public void nativeProjectsRetainMediaAndScalingGrid(XflFormatVersion target) throws Exception {
        for (String fixture : Arrays.asList("0009_symbols", "0013_bitmaps", "0025_components-todo")) {
            Path project = Paths.get("testdata/fla/cs5", fixture);
            XflExportResult result = new XflVersionConverter(target).convert(project.toFile());
            if (fixture.equals("0009_symbols")) {
                Element grid = xml(result, "LIBRARY/MySymbol 2.xml");
                assertEquals(grid.getAttribute("scaleGridLeft"), "10");
                assertEquals(grid.getAttribute("scaleGridTop"), "10");
            }
            for (Map.Entry<String, BinaryData> resource : result.getFiles().entrySet()) {
                String path = resource.getKey();
                if (path.startsWith("bin/") || path.startsWith("LIBRARY/")) {
                    assertEquals(resource.getValue().toByteArray(), Files.readAllBytes(project.resolve(path)), path);
                }
            }
        }
    }

    private static Map<String, BinaryData> project() {
        Map<String, BinaryData> files = new LinkedHashMap<>();
        put(files, "DOMDocument.xml", "<DOMDocument xmlns='" + NS + "' xflVersion='2.2' creatorInfo='Adobe Flash Professional CS6' majorVersion='12' autoSaveEnabled='true' autoSaveHasPrompted='true'><symbols><Include href='symbol.xml'/></symbols></DOMDocument>");
        put(files, "LIBRARY/symbol.xml", "<DOMSymbolItem xmlns='" + NS + "' name='Symbol' scaleGridLeft='25'><timeline><DOMTimeline><layers><DOMLayer><frames><DOMFrame><elements><DOMGroup><members>"
                + "<DOMSymbolInstance libraryItemName='Symbol' isVisible='false' exportAsBitmap='true' bits32='false'><color><Color redMultiplier='0.5' alphaOffset='10'/></color><MatteColor color='#123456'/></DOMSymbolInstance>"
                + "<DOMTLFText><markup><t:TextFlow xmlns:t='http://ns.adobe.com/textLayout/2008'><t:p><t:span> A</t:span><t:span> B </t:span></t:p></t:TextFlow></markup></DOMTLFText></members></DOMGroup></elements>"
                + "<Actionscript><script><![CDATA[trace('preserved');]]></script></Actionscript><IKTree><IKNode><pins><Pin integerPoint='1,2'/></pins><childNodes><ChildNode><pins><Pin integerPoint='3,4'/></pins></ChildNode></childNodes></IKNode></IKTree>"
                + "</DOMFrame></frames></DOMLayer></layers></DOMTimeline></timeline></DOMSymbolItem>");
        String profile = "<flash_profile><PublishFlashProperties><Version>15</Version><ExternalPlayer>FlashPlayer11.2</ExternalPlayer><CompressMovie>1</CompressMovie><CompressionType>1</CompressionType></PublishFlashProperties></flash_profile>";
        put(files, "PublishSettings.xml", "<flash_profiles>" + profile + profile + "</flash_profiles>");
        for (String path : Arrays.asList("bin/bitmap.dat", "bin/clip.swf", "LIBRARY/audio.mp3", "META-INF/metadata.xml", "MobileSettings.xml", "custom.dat")) {
            put(files, path, "unchanged bytes " + path);
        }
        return files;
    }

    private static void put(Map<String, BinaryData> files, String path, String data) {
        files.put(path, new BinaryData(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static Element xml(XflExportResult result, String path) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(result.getFiles().get(path).toByteArray())).getDocumentElement();
    }

    private static void assertFiles(XflExportResult a, XflExportResult b) {
        assertEquals(a.getFiles().keySet(), b.getFiles().keySet());
        for (String path : a.getFiles().keySet()) {
            assertEquals(a.getFiles().get(path).toByteArray(), b.getFiles().get(path).toByteArray(), path);
        }
    }

    private static void reject(Map<String, BinaryData> files) throws Exception {
        try {
            new XflVersionConverter(XflFormatVersion.CS5).convert(files);
            fail("Expected invalid input rejection");
        } catch (IOException expected) {
        }
    }

    private static void remove(Path path) throws IOException {
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(file);
            }
        }
    }
}
