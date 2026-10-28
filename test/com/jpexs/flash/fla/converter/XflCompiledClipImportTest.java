package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.exporter.*;
import com.jpexs.flash.fla.reader.*;
import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.converter.XflReaderTest.*;

public class XflCompiledClipImportTest {

    private static final byte[] MOVIE = {70, 87, 83, 6, 8, 0, 0, 0};
    private static final byte[] ICON = {1, 2, 3, 4};

    private MemoryInput fixture() {
        String params = "<parametersAsXML><![CDATA[<property id='label'><Inspectable name='Label' variable='label' type='String' defaultValue='Hello'/></property>]]></parametersAsXML>";
        MemoryInput input = input("<media><DOMCompiledClipItem name='Button' actionscriptClass='mx.controls.Button' rootSymbolLinkageID='Button' swfScmHRef='button.swf' playerVersion='6' actionscriptVersion='2' movieRectRight='2000' movieRectBottom='440'>"
                + params + "<classProperties><![CDATA[<component id='Button' class='mx.controls.Button'/>]]></classProperties>"
                + "<customIcon><CustomIcon width='1' height='1' rowByteCount='4' colorDepth='32' isTransparent='true' href='icon.dat'/></customIcon>"
                + "<SwcItem name='Button' implementation='Button.swf' version='4294967295' isTopLevel='true' intrinsicClass='mx.controls.Button' intrinsicScript='intrinsic class mx.controls.Button {}'>"
                + "<assetMap><AssetMapItem key='skin'><SwcItem name='ButtonSkin'/></AssetMapItem></assetMap>"
                + "<includesList><IncludesItem name='BoundingBox'/></includesList><exportAfterList><ExportAfterItem name='__Packages.mx.controls.Button'/></exportAfterList>"
                + "</SwcItem></DOMCompiledClipItem></media>"
                + timeline("<DOMCompiledClipInstance name='sampleButton' libraryItemName='Button' uniqueID='27'>"
                        + "<matrix><Matrix a='1.5' d='0.75' tx='10.25' ty='20.5'/></matrix>"
                        + "<transformationPoint><Point/></transformationPoint>"
                        + "<dataBindingXML><![CDATA[<component metaDataFetched='true'/>]]></dataBindingXML>" + params + "</DOMCompiledClipInstance>"));
        input.files.put("bin/button.swf", MOVIE.clone());
        input.files.put("bin/icon.dat", ICON.clone());
        return input;
    }

    @DataProvider(name = "targets")
    public Object[][] targets() {
        return new Object[][]{{FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "targets")
    public void compiledComponentsKeepMediaMetadataPlacementAndParameters(FlaFormatVersion target) throws Exception {
        MemoryInput input = fixture();
        BinaryFlaDocument model = new XflReader().read(input);
        assertEquals(of(model, "CSwfPage").size(), 1);
        assertEquals(of(model, "CPicSwf").size(), 1);
        for (BinaryFlaDocument source : Arrays.asList(model, read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(model)))) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(source));
            FlaRecord descriptor = of(output, "CSwfPage").get(0), component = record(descriptor, "component");
            assertEquals(descriptor.get("name"), "Button");
            assertEquals(descriptor.get("boundsWords"), Arrays.asList(2000, 0, 440, 0));
            assertEquals(((Number) descriptor.get("revision")).intValue(), target == FlaFormatVersion.MX2004 ? 3 : target == FlaFormatVersion.CS4 ? 5 : 4);
            if (target != FlaFormatVersion.MX2004) {
                assertEquals(descriptor.get("playerVersion"), 6L);
                assertEquals(descriptor.get("actionScriptVersion"), 2L);
            }
            assertEquals(component.get("actionScriptClass"), "mx.controls.Button");
            assertEquals(((BinaryData) entries(component, "movies").get(0).get("data")).toByteArray(), MOVIE);
            FlaRecord metadata = record(descriptor, "metadata");
            assertEquals(metadata.get("version"), 0xffffffffL);
            assertEquals(metadata.get("intrinsicClass"), "mx.controls.Button");
            assertEquals(record(entries(metadata, "assetMap").get(0), "metadata").get("name"), "ButtonSkin");
            assertEquals(record(entries(metadata, "includesList").get(0), "metadata").get("name"), "BoundingBox");
            FlaRecord placement = of(output, "CPicSwf").get(0);
            assertEquals(placement.get("mediaId"), descriptor.get("mediaId"));
            assertEquals(placement.get("instanceName"), "sampleButton");
            assertEquals(placement.getMatrix().getWords(), new int[]{98304, 0, 0, 49152, 205, 410});
            assertEquals(((Number) placement.get("transformationPointX")).intValue(), 205);
            assertEquals(((Number) placement.get("transformationPointY")).intValue(), 410);
            assertEquals(record(placement, "componentXml").get("uniqueId"), 27L);
            assertEquals(record(placement, "componentXml").get("xml"), "<component metaDataFetched='true'/>");
            FlaRecord parameter = entries(record(placement, "properties"), "entries").get(0);
            assertEquals(parameter.get("variableName"), "label");
            assertEquals(entries(record(parameter, "value"), "entries").get(0).get("value"), "Hello");
            XflExportResult exported = new BinaryFlaToXflExporter().export(output);
            assertFalse(exported.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPILED_CLIP_DATA")), exported.getReport());
            assertEquals(exported.getFiles().get("bin/clip-icon-1.dat").toByteArray(), ICON);
        }
        assertEquals(input.files.get("bin/icon.dat"), ICON, "Input resource was mutated");
    }

    @Test
    public void localPivotUsesThePlacementMatrixIncludingShearAndTranslation() throws Exception {
        MemoryInput input = fixture();
        String xml = new String(input.files.get("DOMDocument.xml"), java.nio.charset.StandardCharsets.UTF_8)
                .replace("a='1.5' d='0.75'", "a='1.5' b='0.5' c='-0.25' d='0.75'")
                .replace("<Point/>", "<Point x='12' y='4'/>");
        input.put("DOMDocument.xml", xml);
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.MX2004).convert(new XflReader().read(input)));
        FlaRecord instance = of(output, "CPicSwf").get(0);
        assertEquals(((Number) instance.get("transformationPointX")).intValue(), 545);
        assertEquals(((Number) instance.get("transformationPointY")).intValue(), 590);
    }

    @Test
    public void missingOrInvalidCompiledResourcesFailExplicitly() throws Exception {
        MemoryInput input = fixture();
        input.files.remove("bin/button.swf");
        expectFailure(input, "Missing XFL resource");
        input = fixture();
        input.files.put("bin/button.swf", new byte[]{1, 2, 3});
        expectFailure(input, "valid primary SWF");
        input = fixture();
        input.files.put("bin/icon.dat", new byte[]{1});
        expectFailure(input, "pixel data length");
    }

    private static FlaRecord record(FlaRecord source, String key) {
        return (FlaRecord) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<FlaRecord> entries(FlaRecord source, String key) {
        return (List<FlaRecord>) source.get(key);
    }
}
