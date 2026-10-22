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
package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.converter.streams.*;
import com.jpexs.flash.fla.exporter.BinaryFlaToXflExporter;
import com.jpexs.flash.fla.exporter.XflExportResult;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;

/**
 * Semantic XFL import and the single binary-writing pipeline.
 */
public class XflReaderTest {

    @DataProvider(name = "projects")
    public Object[][] projects() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        for (String root : Arrays.asList("testdata/fla/cs5", "testdata/flang/cs5"))try (Stream<Path> paths = Files.list(Paths.get(root))) {
            for (Path p : (Iterable<Path>) paths.filter(Files::isDirectory).sorted()::iterator) {
                if (Files.isRegularFile(p.resolve("DOMDocument.xml"))) {
                    for (FlaFormatVersion v : FlaFormatVersion.values()) {
                        cases.add(new Object[]{p, v});
                    }
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "projects")
    public void corpusUsesSharedModelAndWriter(Path project, FlaFormatVersion target) throws Exception {
        BinaryFlaDocument model = new XflReader().read(project.toFile());
        assertEquals(model.getContentsRevision(), FlaFormatVersion.CS4.contentsRevision);
        assertEquals(model.getContentsStream().getData().size(), 0, "Contents must be a semantic model, not intermediate binary output");
        for (BinaryFlaDocument.Page page : model.getPages()) {
            assertNotNull(page.getStream());
            assertEquals(page.getStream().getData().size(), 0);
        }
        Map<FlaRecord, Map<String, Object>> fields = new IdentityHashMap<>();
        for (FlaRecord r : records(model)) {
            fields.put(r, new LinkedHashMap<>(r.getFields()));
        }
        BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(model));
        assertEquals(output.getContentsRevision(), target.contentsRevision);
        assertEquals(output.getWidthTwips(), model.getWidthTwips());
        assertEquals(output.getHeightTwips(), model.getHeightTwips());
        assertEquals(output.getFrameRate(), model.getFrameRate());
        assertEquals(output.getSceneDescriptors().size(), model.getSceneDescriptors().size());
        assertEquals(output.getLibraryDescriptors().size(), model.getLibraryDescriptors().size());
        assertEquals(geometry(output), geometry(model));
        assertEquals(text(output), text(model));
        for (Map.Entry<FlaRecord, Map<String, Object>> e : fields.entrySet()) {
            assertEquals(e.getKey().getFields(), e.getValue(), "Import model mutated by target writer");
        }
    }

    @Test
    public void modelRetainsTextFlagsSelectionMasksLoopsAndLinkage() throws Exception {
        MemoryInput input = input("<symbols><Include href='symbol.xml'/></symbols><timelines><DOMTimeline name='Scene'><layers>"
                + "<DOMLayer name='Mask' layerType='mask' locked='true'><frames><DOMFrame duration='1'><elements/></DOMFrame></frames></DOMLayer>"
                + "<DOMLayer name='Content' parentLayerIndex='0'><frames><DOMFrame duration='1'><elements>"
                + "<DOMSymbolInstance libraryItemName='Graphic' symbolType='graphic' loop='single frame' firstFrame='2'/>"
                + "<DOMDynamicText width='80' height='20' isSelectable='false' border='true' renderAsHTML='true' lineType='multiline'><textRuns><DOMTextRun><characters>Hello</characters><textAttrs><DOMTextAttrs face='Dialog' size='14' fillColor='#123456'/></textAttrs></DOMTextRun></textRuns></DOMDynamicText>"
                + "</elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines>");
        input.put("LIBRARY/symbol.xml", "<DOMSymbolItem name='Graphic' symbolType='graphic' linkageExportForAS='true' linkageExportForRS='true' linkageExportInFirstFrame='false' linkageIdentifier='Link'><timeline><DOMTimeline><layers><DOMLayer><frames><DOMFrame/></frames></DOMLayer></layers></DOMTimeline></timeline></DOMSymbolItem>");
        BinaryFlaDocument d = new XflReader().read(input);
        List<FlaRecord> layers = d.getPages().stream().filter(p -> p.getRole() == BinaryFlaDocument.Page.Role.SCENE).findFirst().get().getRoot().getChildren();
        assertEquals(layers.get(0).get("name"), "Content");
        assertSame(layers.get(0).get("relatedLayer"), layers.get(1));
        FlaRecord symbol = of(d, "CPicSymbol").get(0);
        assertEquals(symbol.get("loopMode"), 2);
        assertEquals(symbol.get("firstFrame"), 2);
        FlaLinkage linkage = ((FlaRecord) d.getLibraryDescriptors().get(0).get("linkage")).getLinkage();
        assertEquals(linkage.getMode(), FlaLinkage.Mode.EXPORT);
        assertTrue(linkage.isExportForActionScript());
        assertTrue(linkage.isExportForRuntimeSharing());
        assertFalse(linkage.isExportInFirstFrame());
        FlaRecord t = of(d, "CPicText").get(0);
        long flags = ((Number) t.get("textFlags")).longValue();
        assertEquals(flags & 3, 3L);
        assertTrue((flags & 64) != 0);
        assertTrue((flags & 0x8000) != 0);
        assertTrue((flags & 24) == 24);
        assertEquals(t.get("text"), "Hello");
        for (FlaFormatVersion version : FlaFormatVersion.values()) {
            BinaryFlaDocument out = read(new BinaryFlaConverter(version).convert(d));
            assertEquals(of(out, "CPicText").get(0).get("text"), "Hello");
        }
    }

    @Test
    public void pathsPreserveFractionalCoordinatesAndValidateStyleReferences() throws Exception {
        MemoryInput input = input(timeline("<DOMShape><fills><FillStyle index='4'><SolidColor color='#FF0000'/></FillStyle></fills><edges><Edge fillStyle1='4' edges='!#FFFFFF.80 0S2|10 0[12 3 4 5'/></edges></DOMShape>"));
        BinaryFlaDocument d = new XflReader().read(input);
        FlaRecord shape = of(d, "CPicFrame").get(0);
        List<FlaRecord> edges = (List<FlaRecord>) ((FlaRecord) shape.get("shapeData")).get("edges");
        assertEquals(edges.size(), 2);
        assertEquals(edges.get(0).get("startX"), -128);
        assertEquals(edges.get(0).get("fillIndex1"), 0x8001);
        assertEquals(edges.get(1).get("controlX"), 12 * 256);
        read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(d));
        expectFailure(input(timeline("<DOMShape><edges><Edge fillStyle0='3' edges='!0 0|1 1'/></edges></DOMShape>")), "missing style");
    }

    @Test
    public void rejectsExternalXmlAndMissingOrEscapingResources() throws Exception {
        MemoryInput malicious = new MemoryInput();
        malicious.put("DOMDocument.xml", "<!DOCTYPE DOMDocument [<!ENTITY x SYSTEM 'file:///does-not-exist'>]><DOMDocument>&x;</DOMDocument>");
        expectFailure(malicious, "Invalid XFL XML");
        expectFailure(input("<symbols><Include href='../outside.xml'/></symbols>"), "Invalid XFL resource path");
        expectFailure(input("<symbols><Include href='missing.xml'/></symbols>"), "Missing XFL resource");
    }

    @Test
    public void conversionFacadeWorksWithLegacyGeneratorsUnavailable() throws Exception {
        URL location = XflReader.class.getProtectionDomain().getCodeSource().getLocation();
        URL writerLocation = BinaryFlaConverter.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader isolated = new URLClassLoader(new URL[]{location, writerLocation}, null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.endsWith(".TimelineConverter") || name.endsWith(".LegacyFlaConverter") || name.endsWith(".AbstractConverter")) {
                    throw new ClassNotFoundException("Legacy generator forbidden: " + name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> inputType = isolated.loadClass(InputStorageInterface.class.getName()), outputType = isolated.loadClass(OutputStorageInterface.class.getName());
            Object input = isolated.loadClass(DirectoryInputStorage.class.getName()).getConstructor(File.class).newInstance(new File("testdata/fla/cs5/0002_shapes"));
            Map<String, byte[]> streams = new LinkedHashMap<>();
            Object output = java.lang.reflect.Proxy.newProxyInstance(isolated, new Class<?>[]{outputType}, (proxy, method, args) -> {
                if (method.getName().equals("storeData")) {
                    streams.put((String) args[0], (byte[]) args[1]);
                    return null;
                }
                if (method.getName().equals("getOutputStream")) {
                    String name = (String) args[0];
                    return new ByteArrayOutputStream() {
                        @Override
                        public void close() {
                            streams.put(name, toByteArray());
                        }
                    };
                }
                return null;
            });
            Class<?> versionType = isolated.loadClass(FlaFormatVersion.class.getName());
            Object version = versionType.getField("F2").get(null);
            Class<?> converter = isolated.loadClass(FlaConverter.class.getName());
            Object instance = converter.getConstructor(versionType, String.class).newInstance(version, "windows-1252");
            converter.getMethod("convert", inputType, outputType).invoke(instance, input, output);
            assertTrue(streams.containsKey("Contents"));
            BinaryFlaDocument d = new BinaryFlaReader().readStreams(streams);
            assertTrue(d.getIssues().isEmpty(), d.getIssues().toString());
            assertFalse(geometry(d).isEmpty());
        }
    }

    @Test
    public void filtersPersistentDataAndCustomEaseSurviveCs4Writing() throws Exception {
        MemoryInput input = input(timeline("<DOMDynamicText width='80' height='20' hasAccessibleData='true' accName='Label' tabIndex='3'><textRuns><DOMTextRun><characters>Text</characters></DOMTextRun></textRuns><filters><BlurFilter blurX='7' blurY='9' quality='2' isEnabled='false'/><DropShadowFilter color='#123456' alpha='0.5' angle='30' strength='2'/><AdjustColorFilter brightness='12' saturation='-4'/></filters><persistentData><PD n='s' t='s' v='abc'/><PD n='i' t='i' v='-1'/><PD n='d' t='D' v='1.5, -2'/><PD n='b' t='B' v='00FF'/></persistentData></DOMDynamicText>"));
        String xml = new String(input.files.get("DOMDocument.xml"), StandardCharsets.UTF_8).replace("<DOMFrame duration='1'>", "<DOMFrame duration='1' tweenType='motion' motionTweenRotate='none'><tweens><CustomEase target='all'><Point x='0' y='0'/><Point x='0.2' y='0.1'/><Point x='0.8' y='0.9'/><Point x='1' y='1'/></CustomEase></tweens>");
        input.put("DOMDocument.xml", xml.replace("</DOMDocument>", "<persistentData><PD n='s' t='s' v='abc'/><PD n='i' t='i' v='-1'/><PD n='d' t='D' v='1.5, -2'/><PD n='b' t='B' v='00FF'/></persistentData></DOMDocument>"));
        BinaryFlaDocument model = new XflReader().read(input), output = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(model));
        FlaRecord text = of(output, "CPicText").get(0);
        FlaRecord container = (FlaRecord) text.get("filters");
        List<FlaRecord> filters = (List<FlaRecord>) container.get("entries");
        assertEquals(filters.size(), 3);
        FlaRecord blur = (FlaRecord) filters.get(0).get("settings");
        assertEquals(((Number) blur.get("blurX")).doubleValue(), 7.0);
        assertEquals(((Number) blur.get("blurY")).doubleValue(), 9.0);
        assertEquals(((Number) ((FlaRecord) blur.get("state")).get("enabled")).intValue(), 0);
        assertEquals(((Number) filters.get(1).get("strengthPercent")).intValue(), 200);
        FlaRecord access = (FlaRecord) text.get("accessibility");
        assertEquals(access.get("name"), "Label");
        assertEquals(access.get("tabIndex"), "3");
        assertEquals(output.getContents().getPersistentData().getEntries().size(), 4);
        assertEquals(output.getContents().getPersistentData().getEntries().get(1).getValue(), -1);
        FlaRecord frame = of(output, "CPicFrame").get(0);
        assertEquals(((Number) frame.get("keyMode")).intValue() & 0x400, 0);
        assertEquals(((Number) frame.get("rotationMode")).intValue(), 0);
        List<?> curves = (List<?>) frame.get("customEaseCurves");
        assertEquals(((List<?>) curves.get(5)).size(), 2);
    }

    @Test
    public void mediaCachesAndSourceFormatsAreDecodedDirectly() throws Exception {
        MemoryInput input = input("<media><DOMBitmapItem name='Image' href='image.png'/><DOMSoundItem name='Sound' soundDataHRef='sound.dat' format='11kHz 16bit Mono' sampleCount='2'/><DOMSoundItem name='MP3' soundDataHRef='mp3.dat' href='music.mp3' format='44kHz 16bit Stereo' sampleCount='10' dataLength='4' cacheFormat='11kHz 16bit Mono' cachedSampleCount='2'/></media>" + timeline("<DOMBitmapInstance libraryItemName='Image'/>"));
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80804020);
        image.setRGB(1, 0, 0xff010203);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        input.files.put("LIBRARY/image.png", png.toByteArray());
        input.files.put("bin/sound.dat", new byte[]{1, 2, 3, 4});
        input.files.put("bin/mp3.dat", new byte[]{(byte) 255, (byte) 251, 1, 2, 0, 1, 2, 3, 4});
        BinaryFlaDocument model = new XflReader().read(input);
        assertEquals(model.getMedia().size(), 3);
        FlaRecord mp3 = model.getMedia().get(2).getStream().getRoot();
        assertEquals(((BinaryData) ((FlaRecord) mp3.get("primaryAudio")).get("data")).size(), 4);
        assertEquals(((BinaryData) ((FlaRecord) mp3.get("secondaryAudio")).get("data")).size(), 4);
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(model));
        assertEquals(output.getMedia().get(2).getSoundFormat().getCodecId(), 2);
        assertEquals(output.getMedia().get(2).getStream().getData().toByteArray(), model.getMedia().get(2).getStream().getData().toByteArray());
        FlaRecord raster = (FlaRecord) output.getMedia().get(0).getStream().getRoot().get("raster");
        assertEquals(((BinaryData) raster.get("pixelData")).toByteArray(), new byte[]{(byte) 128, 64, 32, 16, (byte) 255, 1, 2, 3});
        BinaryFlaDocument legacy = read(new BinaryFlaConverter(FlaFormatVersion.F2).convert(model));
        assertEquals(legacy.getMedia().get(2).getSoundFormat().getCodecId(), 0);
        assertEquals(legacy.getMedia().get(2).getStream().getData().toByteArray(), new byte[]{1, 2, 3, 4});
    }

    @Test
    public void pcmCachePaddingDoesNotExtendDeclaredSoundDuration() throws Exception {
        for (String format : Arrays.asList("11kHz 16bit Mono", "44kHz 16bit Stereo", "22kHz 8bit Mono")) {
            int frameBytes = FlaSoundFormat.fromFlags(XflMediaReader.soundFormat(format)).getPcmFrameByteCount();
            MemoryInput input = input("<media><DOMSoundItem name='Padded' soundDataHRef='padded.dat' format='"
                    + format + "' sampleCount='2'/></media>" + timeline(""));
            byte[] cache = new byte[4 * frameBytes];
            for (int i = 0; i < cache.length; i++) {
                cache[i] = (byte) (i + 1);
            }
            input.files.put("bin/padded.dat", cache);
            BinaryFlaDocument model = new XflReader().read(input);
            BinaryFlaDocument.Media media = model.getMedia().get(0);
            assertEquals(media.getDescriptor().get("sampleCount"), 2L);
            byte[] expected = Arrays.copyOf(cache, 2 * frameBytes);
            assertEquals(media.getStream().getData().toByteArray(), expected);
            FlaRecord primary = (FlaRecord) media.getStream().getRoot().get("primaryAudio");
            assertEquals(((BinaryData) primary.get("data")).toByteArray(), expected);
            assertEquals(primary.get("shortRead"), false);
            assertEquals(model.getIssues().size(), 1);
            assertTrue(model.getIssues().get(0).toString().contains("padded.dat"));
            for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F4, FlaFormatVersion.CS4)) {
                BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(model));
                assertEquals(output.getMedia().get(0).getDescriptor().get("sampleCount"), 2L);
                assertEquals(output.getMedia().get(0).getStream().getData().toByteArray(), expected);
            }
            assertEquals(input.files.get("bin/padded.dat"), cache);
        }
    }

    @Test
    public void malformedSoundCacheReportsItsItemAndResource() throws Exception {
        for (boolean compressed : new boolean[]{false, true}) {
            MemoryInput input = input("<media><DOMSoundItem name='Broken' soundDataHRef='broken.dat' format='11kHz 16bit Mono' sampleCount='2'"
                    + (compressed ? " href='broken.mp3' dataLength='4' cachedSampleCount='0'" : "")
                    + "/></media>" + timeline(""));
            input.files.put("bin/broken.dat", new byte[]{1, 2, 3, 4, 5, 6, 7});
            try {
                new XflReader().read(input);
                fail("An incomplete PCM frame or excess compressed-cache data must not be silently discarded");
            } catch (IOException ex) {
                assertTrue(ex.getMessage().contains("Broken"), ex.getMessage());
                assertTrue(ex.getMessage().contains("broken.dat"), ex.getMessage());
                assertTrue(ex.getMessage().contains("Trailing bytes"), ex.getMessage());
            }
        }
    }

    @Test
    public void directoryMarkerAndZipUseTheSameImport() throws Exception {
        Path dir = Files.createTempDirectory("flacomdoc-xfl-");
        Path zip = Files.createTempFile("flacomdoc-xfl-", ".fla");
        try {
            byte[] xml = input(timeline("<DOMStaticText><textRuns><DOMTextRun><characters>Archive</characters></DOMTextRun></textRuns></DOMStaticText>")).files.get("DOMDocument.xml");
            Files.write(dir.resolve("DOMDocument.xml"), xml);
            Files.write(dir.resolve("document.xfl"), new byte[0]);
            try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new java.util.zip.ZipEntry("DOMDocument.xml"));
                out.write(xml);
                out.closeEntry();
            }
            XflReader reader = new XflReader();
            Map<String, BinaryData> expected = new BinaryFlaConverter(FlaFormatVersion.CS4).convert(reader.read(dir.toFile())).getStreams();
            for (Path source : Arrays.asList(dir.resolve("document.xfl"), zip)) {
                Map<String, BinaryData> actual = new BinaryFlaConverter(FlaFormatVersion.CS4).convert(reader.read(source.toFile())).getStreams();
                assertEquals(actual.keySet(), expected.keySet());
                for (String path : expected.keySet()) {
                    assertEquals(actual.get(path).toByteArray(), expected.get(path).toByteArray(), path);
                }
            }
        } finally {
            Files.deleteIfExists(zip);
            Files.deleteIfExists(dir.resolve("document.xfl"));
            Files.deleteIfExists(dir.resolve("DOMDocument.xml"));
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void gradientsStrokeDefaultsAndEmptyTextStylesAreKept() throws Exception {
        MemoryInput input = input("<fonts><DOMFontItem name='Font' font='_serif' id='37' embeddedCharacters='abc' embedRanges='1|4|9'/></fonts>" + timeline("<DOMInputText width='20' height='10'><textRuns><DOMTextRun><characters/><textAttrs><DOMTextAttrs face='Font*' size='19' fillColor='#123456'/></textAttrs></DOMTextRun></textRuns></DOMInputText><DOMShape><fills><FillStyle index='9'><LinearGradient spreadMethod='reflect' interpolationMethod='linearRGB'><matrix><Matrix a='0.5' d='0.25' tx='10'/></matrix><GradientEntry ratio='0' color='#FF0000'/><GradientEntry ratio='1' color='#0000FF' alpha='0.5'/></LinearGradient></FillStyle></fills><strokes><StrokeStyle index='5'><RaggedStroke weight='2'><fill><SolidColor color='#123456'/></fill></RaggedStroke></StrokeStyle></strokes><edges><Edge fillStyle1='9' strokeStyle='5' edges='!0 0|10 0'/></edges></DOMShape>"));
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(new XflReader().read(input)));
        FlaRecord text = of(output, "CPicText").get(0), style = (FlaRecord) text.get("defaultStyle");
        assertEquals(((Number) style.get("fontSize")).intValue(), 380);
        assertEquals(style.get("fontFace"), "_serif");
        assertEquals(text.get("embedRanges"), "1|4|9");
        assertEquals(text.get("fontLibraryName"), "abc");
        FlaRecord shape = (FlaRecord) of(output, "CPicFrame").get(0).get("shapeData"), fill = ((List<FlaRecord>) shape.get("fills")).get(0), stroke = ((List<FlaRecord>) shape.get("strokes")).get(0);
        assertEquals(((Number) fill.get("flags")).intValue(), 16);
        assertEquals(((Number) fill.get("gradientFlags")).intValue(), 5);
        assertEquals(((List<?>) fill.get("stops")).size(), 2);
        assertEquals(((Number) stroke.get("styleWord2")).intValue(), 0x14b);
        assertEquals(((Number) stroke.get("width")).intValue(), 40);
        FlaRecord library = (FlaRecord) output.getContents().get("fontLibrary");
        assertEquals(((Number) ((List<FlaRecord>) library.get("entries")).get(0).get("fontId")).intValue(), 37);
    }

    @Test
    public void publishProfilesUseNativeNamesAndSelectors() throws Exception {
        MemoryInput input = input(timeline(""));
        input.put("PublishSettings.xml", "<flash_profiles><flash_profile name='Active' current='true'><PublishFlashProperties><DebuggingPermitted>1</DebuggingPermitted><AS3PackagePaths>src</AS3PackagePaths><RSLPreloaderMethod>event</RSLPreloaderMethod><DefaultLibraryLinkage>merge</DefaultLibraryLinkage></PublishFlashProperties></flash_profile><flash_profile name='Other'/></flash_profiles>");
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(new XflReader().read(input)));
        FlaRecord settings = (FlaRecord) output.getContents().get("publishSettings");
        List<FlaRecord> profiles = (List<FlaRecord>) settings.get("profiles");
        assertEquals(profiles.size(), 2);
        Map<String, Object> values = new HashMap<>();
        for (FlaRecord entry : (List<FlaRecord>) profiles.get(1).get("entries")) {
            values.put((String) entry.get("key"), entry.get("value"));
        }
        assertEquals(values.get("PublishProfileProperties::name"), "Active");
        assertEquals(values.get("Vector::Debugging Permitted"), "1");
        assertEquals(values.get("Vector::AS3 Package Paths"), "src");
        assertEquals(values.get("Vector::RSLPreloaderMethod"), "1");
        assertEquals(values.get("Vector::DefaultLibraryLinkage"), "1");
    }

    @Test
    public void missingLibraryIdsPreserveFoldersWithoutCollidingWithExplicitIds() throws Exception {
        MemoryInput input = input("<folders><DOMFolderItem name='Folder'/></folders><symbols><Include href='symbol.xml'/></symbols>" + timeline("<DOMSymbolInstance libraryItemName='Folder/Symbol'/>"));
        input.put("LIBRARY/symbol.xml", "<DOMSymbolItem name='Folder/Symbol' itemID='ffffffff-00000001'><timeline><DOMTimeline><layers><DOMLayer><frames><DOMFrame/></frames></DOMLayer></layers></DOMTimeline></timeline></DOMSymbolItem>");
        BinaryFlaDocument model = new XflReader().read(input);
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(model));
        FlaRecord folder = ((List<FlaRecord>) ((FlaRecord) output.getContents().get("libraryFolders")).get("entries")).get(0);
        FlaRecord folderItem = (FlaRecord) ((FlaRecord) folder.get("libraryMetadata")).get("item");
        FlaRecord metadata = (FlaRecord) output.getLibraryDescriptors().get(0).get("libraryMetadata");
        FlaRecord parent = (FlaRecord) metadata.get("parentItem"), symbolItem = (FlaRecord) metadata.get("item");
        assertEquals(folderItem.get("firstPart"), parent.get("firstPart"));
        assertEquals(folderItem.get("secondPart"), parent.get("secondPart"));
        assertFalse(folderItem.get("secondPart").equals(symbolItem.get("secondPart")));
        assertEquals(symbolItem.get("secondPart"), 1L);
    }

    @DataProvider(name = "timelineLayout")
    public Object[][] timelineLayout() {
        return new Object[][]{{"", "", -1, 0xffffffffL}, {" timelineLabelWidth='236'", "", 236, 0xffffffffL},
        {" timelineLabelWidth='236'", " heightLiteral='0'", 236, 0xffffffffL},
        {" timelineLabelWidth='274'", " heightLiteral='72' heightMultiplier='2'", 274, 72L}};
    }

    @Test(dataProvider = "timelineLayout")
    public void timelineLayoutUsesNativePixelWidthsAndAutomaticLayerHeight(String documentAttrs, String layerAttrs, int width, long height) throws Exception {
        MemoryInput input = new MemoryInput();
        input.put("DOMDocument.xml", "<DOMDocument" + documentAttrs + "><timelines><DOMTimeline><layers><DOMLayer" + layerAttrs + "><frames><DOMFrame/></frames></DOMLayer></layers></DOMTimeline></timelines></DOMDocument>");
        BinaryFlaDocument model = new XflReader().read(input);
        assertEquals(model.getContents().get("timelineLabelWidth"), width);
        assertEquals(of(model, "CPicLayer").get(0).get("heightLiteral"), height);
        for (FlaFormatVersion target : FlaFormatVersion.values()) {
            BinaryFlaConversionResult result = new BinaryFlaConverter(target).convert(model);
            BinaryFlaDocument output = read(result);
            if (target.picLayerRevision >= 5) {
                assertEquals(of(output, "CPicLayer").get(0).get("heightLiteral"), height);
            }
            if (target.contentsRevision >= 0x3f) {
                assertEquals(output.getContents().get("timelineLabelWidth"), width);
            }
            if (target == FlaFormatVersion.F8) {
                byte[] contents = result.getStreams().get("Contents").toByteArray();
                assertEquals(Arrays.copyOfRange(contents, contents.length - 4, contents.length), new byte[]{(byte) width, (byte) (width >>> 8), (byte) (width >>> 16), (byte) (width >>> 24)}, "F8 stores the integer pixel width, not its floating-point bit pattern");
            }
        }
    }

    @Test
    public void missingModelLayoutFieldsUseNativeAutomaticSentinels() throws Exception {
        BinaryFlaDocument model = new XflReader().read(input(timeline("")));
        model.getContents().set("timelineLabelWidth", null);
        of(model, "CPicLayer").get(0).set("heightLiteral", null);
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.F8).convert(model));
        assertEquals(output.getContents().get("timelineLabelWidth"), -1);
        assertEquals(of(output, "CPicLayer").get(0).get("heightLiteral"), 0xffffffffL);
    }

    @Test
    public void scenesHaveNoLibrarySymbolId() throws Exception {
        String scene = "<DOMTimeline><layers><DOMLayer><frames><DOMFrame/></frames></DOMLayer></layers></DOMTimeline>";
        MemoryInput input = input("<symbols><Include href='symbol.xml'/></symbols><timelines>" + scene + scene + "</timelines>");
        input.put("LIBRARY/symbol.xml", "<DOMSymbolItem name='Graphic' symbolType='graphic'><timeline>" + scene + "</timeline></DOMSymbolItem>");
        BinaryFlaDocument model = new XflReader().read(input);
        for (FlaRecord descriptor : model.getSceneDescriptors()) {
            assertEquals(((Number) descriptor.get("libraryId")).longValue(), 0L);
        }
        assertEquals(((Number) model.getLibraryDescriptors().get(0).get("libraryId")).longValue(), 1L);
        assertNotEquals(model.getSceneDescriptors().get(0).get("streamName"), model.getSceneDescriptors().get(1).get("streamName"));
        for (FlaFormatVersion target : FlaFormatVersion.values()) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(model));
            for (FlaRecord descriptor : output.getSceneDescriptors()) {
                assertEquals(((Number) descriptor.get("libraryId")).longValue(), 0L);
            }
            assertEquals(((Number) output.getLibraryDescriptors().get(0).get("libraryId")).longValue(), 1L);
        }
    }

    @DataProvider(name = "previewModes")
    public Object[][] previewModes() {
        return new Object[][]{{"", 3}, {"outlines", 0}, {"fast", 1}, {"anti alias", 2}, {"anti alias text", 3}, {"full", 4}};
    }

    @Test(dataProvider = "previewModes")
    public void previewDefaultsToAntialiasedTextAndRetainsExplicitModes(String mode, int expected) throws Exception {
        MemoryInput input = new MemoryInput();
        input.put("DOMDocument.xml", "<DOMDocument" + (mode.isEmpty() ? "" : " previewMode='" + mode + "'") + ">" + timeline("") + "</DOMDocument>");
        BinaryFlaDocument model = new XflReader().read(input);
        assertEquals(((BinaryData) model.getContents().get("previewAndRulerFlags")).toByteArray()[0], (byte) expected);
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.F8).convert(model));
        assertEquals(((BinaryData) output.getContents().get("previewAndRulerFlags")).toByteArray()[0], (byte) expected);
    }

    @Test
    public void scalingGridPreservesAsymmetricFractionalBoundsThroughBinaryOutput() throws Exception {
        MemoryInput input = input("<symbols><Include href='grid.xml'/></symbols>" + timeline("<DOMSymbolInstance libraryItemName='Grid'/>"));
        input.put("LIBRARY/grid.xml", "<DOMSymbolItem name='Grid' scaleGridLeft='-5.25' scaleGridRight='125' scaleGridTop='24.85' scaleGridBottom='75.5'><timeline>"
                + timeline("").replace("<timelines>", "").replace("</timelines>", "") + "</timeline></DOMSymbolItem>");
        BinaryFlaDocument model = new XflReader().read(input);
        assertEquals(model.getLibraryDescriptors().get(0).get("scaleGridEnabled"), 1L);
        assertEquals(model.getLibraryDescriptors().get(0).get("scaleGridBounds"), Arrays.asList(2500, -105, 1510, 497));
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F8, FlaFormatVersion.CS3, FlaFormatVersion.CS4)) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(model));
            XflExportResult xfl = new BinaryFlaToXflExporter().export(output);
            Element grid = null;
            for (Map.Entry<String, BinaryData> file : xfl.getFiles().entrySet()) {
                if (file.getKey().startsWith("LIBRARY/") && file.getKey().endsWith(".xml")) {
                    grid = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                            .parse(new ByteArrayInputStream(file.getValue().toByteArray())).getDocumentElement();
                }
            }
            assertNotNull(grid);
            assertEquals(grid.getAttribute("scaleGridLeft"), "-5.25");
            assertEquals(grid.getAttribute("scaleGridRight"), "125");
            assertEquals(grid.getAttribute("scaleGridTop"), "24.85");
            assertEquals(grid.getAttribute("scaleGridBottom"), "75.5");
        }
    }

    static BinaryFlaDocument read(BinaryFlaConversionResult result) throws Exception {
        Map<String, byte[]> streams = new LinkedHashMap<>();
        result.getStreams().forEach((p, b) -> streams.put(p, b.toByteArray()));
        BinaryFlaDocument d = new BinaryFlaReader().readStreams(streams);
        assertTrue(d.getIssues().isEmpty(), d.getIssues().toString());
        return d;
    }

    static List<FlaRecord> records(BinaryFlaDocument d) {
        List<FlaRecord> result = new ArrayList<>();
        Set<FlaRecord> seen = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
        for (FlaStream stream : d.getStreams().values()) {
            collect(stream.getRoot(), seen, result);
        }
        return result;
    }

    static void collect(Object value, Set<FlaRecord> seen, List<FlaRecord> into) {
        if (value instanceof FlaRecord) {
            FlaRecord r = (FlaRecord) value;
            if (!seen.add(r)) {
                return;
            }
            into.add(r);
            for (Object child : r.getFields().values()) {
                collect(child, seen, into);
            }
        } else if (value instanceof List) {
            for (Object child : (List<?>) value) {
                collect(child, seen, into);
            }
        }
    }

    static List<FlaRecord> of(BinaryFlaDocument d, String type) {
        List<FlaRecord> r = records(d);
        r.removeIf(e -> !e.getClassName().equals(type));
        return r;
    }

    static List<String> text(BinaryFlaDocument d) {
        List<String> r = new ArrayList<>();
        for (FlaRecord e : of(d, "CPicText")) {
            r.add((String) e.get("text"));
        }
        Collections.sort(r);
        return r;
    }

    static List<String> geometry(BinaryFlaDocument d) {
        List<String> out = new ArrayList<>();
        for (FlaRecord r : records(d)) {
            if (r.get("startX") != null) {
                StringJoiner line = new StringJoiner(",");
                for (String key : new String[]{"startX", "startY", "controlX", "controlY", "endX", "endY"}) {
                    line.add(String.valueOf(r.get(key)));
                }
                out.add(line.toString());
            }
        }
        Collections.sort(out);
        return out;
    }

    static MemoryInput input(String body) {
        MemoryInput r = new MemoryInput();
        r.put("DOMDocument.xml", "<DOMDocument width='550' height='400'>" + body + "</DOMDocument>");
        return r;
    }

    static String timeline(String elements) {
        return "<timelines><DOMTimeline><layers><DOMLayer name='Layer'><frames><DOMFrame duration='1'><elements>" + elements + "</elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline></timelines>";
    }

    static void expectFailure(MemoryInput input, String part) throws Exception {
        try {
            new XflReader().read(input);
            fail("Expected failure: " + part);
        } catch (IOException ex) {
            assertTrue(ex.getMessage().contains(part), ex.toString());
        }
    }

    static final class MemoryInput implements InputStorageInterface {

        final Map<String, byte[]> files = new HashMap<>();

        void put(String path, String xml) {
            files.put(path, xml.getBytes(StandardCharsets.UTF_8));
        }

        public boolean fileExists(String name) {
            return files.containsKey(name);
        }

        public InputStream readFile(String name) {
            byte[] b = files.get(name);
            return b == null ? null : new ByteArrayInputStream(b);
        }

        public void close() {
        }
    }
}
