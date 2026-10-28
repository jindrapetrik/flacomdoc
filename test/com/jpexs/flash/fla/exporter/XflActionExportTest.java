package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.script.ScriptParser;
import java.nio.file.*;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

public class XflActionExportTest {

    @DataProvider(name = "historical")
    public Object[][] historical() {
        return new Object[][]{
            {"play();"}, {"stop();"}, {"nextFrame();"}, {"prevFrame();"}, {"nextScene();"}, {"prevScene();"},
            {"toggleHighQuality();"}, {"stopAllSounds();"}, {"gotoAndStop(5);"}, {"gotoAndPlay(10);"},
            {"gotoAndStop(\"Scene 2\", 5);"}, {"gotoAndPlay(\"a label\");"},
            {"getURL(\"https://example.test/?a=1&b=2\", \"_blank\");"},
            {"getURL(urlExpression, windowExpression, \"POST\");"},
            {"fscommand(\"quit\", \"\");"}, {"tellTarget(\"clip\") {play();}"},
            {"ifFrameLoaded(5) {play();}"}, {"if (x > 1) {play();} else {stop();}"},
            {"if (x > 2) {play();} else if (x == 2) {stop();} else {stopAllSounds();}"},
            {"while (x > 1) {x = x - 1;}"}, {"call(\"aLabel\");"}, {"set(\"variable\", value);"},
            {"setProperty(\"clip\", _x, 25);"}, {"trace(\"escaped\\nline\");"},
            {"duplicateMovieClip(\"clip\", \"copy\", 10);"}, {"removeMovieClip(\"clip\");"},
            {"startDrag(\"clip\", true); stopDrag();"}, {"// comment\nplay();"}
        };
    }

    @Test(dataProvider = "historical")
    public void historicalSourceCanBeParsedAgain(String source) throws Exception {
        BinaryFlaDocument doc = historical(source);
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertFalse(has(result, "HISTORICAL_SCRIPT"), result.getReport());
        List<Element> scripts = elements(xml(result), "script");
        assertFalse(scripts.isEmpty(), source);
        String recovered = scripts.get(0).getTextContent();
        assertFalse(new ScriptParser(FlaFormatVersion.F4).parse(recovered, false).isEmpty(), recovered);
    }

    @DataProvider(name = "nativeCompiled")
    public Object[][] nativeCompiled() {
        return new Object[][]{{"0013_frame_action"}, {"0014_button_action"}, {"0007_symbols"}};
    }

    @Test(dataProvider = "nativeCompiled")
    public void nativeCompiledCommandsHaveBalancedReconstructedSource(String sample) throws Exception {
        BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams(Paths.get("testdata/flang/mx", sample)));
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertFalse(has(result, "COMPILED_SCRIPT"), result.getReport());
        List<Element> scripts = new ArrayList<>();
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (!file.getKey().endsWith(".xml")) {
                continue;
            }
            XflSchemaTestSupport.validate(file.getValue().toByteArray());
            scripts.addAll(elements(parse(file.getValue().toByteArray()), "script"));
        }
        assertFalse(scripts.isEmpty());
        for (Element script : scripts) {
            assertEquals(script.getFirstChild().getNodeType(), Node.CDATA_SECTION_NODE);
            new ScriptParser(FlaFormatVersion.F4).parse(script.getTextContent(), false);
        }
    }

    @Test
    public void adobeTextPayloadsUseCdataAndPreserveSectionTerminators() throws Exception {
        Document source = XflXml.document("DOMComponentItem");
        String text = "a < b && c > d;\r\ntrace(\"]]>\");\nnext();\rlast();";
        for (String tag : Arrays.asList("script", "parametersAsXML", "classProperties", "dataBindingXML")) {
            XflXml.cdata(source.getDocumentElement(), tag, text);
        }
        Element result = parse(XflXml.serialize(source));
        for (String tag : Arrays.asList("script", "parametersAsXML", "classProperties", "dataBindingXML")) {
            Element payload = elements(result, tag).get(0);
            assertEquals(payload.getTextContent(), text.replace("\r\n", "\n").replace('\r', '\n'));
            assertEquals(payload.getFirstChild().getNodeType(), Node.CDATA_SECTION_NODE);
        }
    }

    @Test
    public void unsupportedOrMismatchedCommandsOmitTheWholeScript() throws Exception {
        for (int type : new int[]{85, 12}) {
            BinaryFlaDocument doc = new BinaryFlaReader().readStreams(streams(Paths.get("testdata/flang/mx/0013_frame_action")));
            List<FlaRecord> frames = records(doc, "CPicFrame");
            FlaRecord frame = frames.stream().filter(f -> {
                FlaRecord a = XflXml.record(f, "actions");
                return !XflXml.records(XflXml.record(a, "compiledCommands"), "commands").isEmpty();
            }).findFirst().get();
            FlaRecord actions = (FlaRecord) frame.get("actions"), list = (FlaRecord) actions.get("compiledCommands");
            @SuppressWarnings("unchecked")
            List<FlaRecord> commands = (List<FlaRecord>) list.get("commands");
            assertFalse(commands.isEmpty());
            commands.get(0).set("type", (long) type);
            frame.set("name", "Atomic script target");
            XflExportResult result = new BinaryFlaToXflExporter().export(doc);
            assertTrue(has(result, "COMPILED_SCRIPT"), result.getReport());
            Element output = elements(xml(result), "DOMFrame").stream().filter(f -> f.getAttribute("name").equals("Atomic script target")).findFirst().get();
            for (Node n = output.getFirstChild(); n != null; n = n.getNextSibling()) {
                assertFalse(n instanceof Element && "Actionscript".equals(n.getLocalName()));
            }
        }
    }

    @Test
    public void emptyHistoricalActionDoesNotClaimAConversionLoss() throws Exception {
        BinaryFlaDocument doc = historical("");
        XflExportResult result = new BinaryFlaToXflExporter().export(doc);
        assertFalse(has(result, "HISTORICAL_SCRIPT"), result.getReport());
    }

    private static BinaryFlaDocument historical(String script) throws Exception {
        Document d = XflXml.document("DOMDocument");
        Element root = d.getDocumentElement();
        Element timeline = XflXml.child(XflXml.child(root, "timelines"), "DOMTimeline");
        timeline.setAttribute("name", "Scene 1");
        Element layer = XflXml.child(XflXml.child(timeline, "layers"), "DOMLayer");
        layer.setAttribute("name", "Layer 1");
        Element frame = XflXml.child(XflXml.child(layer, "frames"), "DOMFrame");
        frame.setAttribute("index", "0");
        XflXml.child(XflXml.child(frame, "Actionscript"), "script").setTextContent(script);
        XflXml.child(frame, "elements");
        XflIntegrationExportTest.Memory input = new XflIntegrationExportTest.Memory();
        input.data.put("DOMDocument.xml", XflXml.serialize(d));
        BinaryFlaDocument ret = XflIntegrationExportTest.convert(input, FlaFormatVersion.F4);
        FlaRecord actions = (FlaRecord) records(ret, "CPicFrame").get(0).get("actions");
        assertNotNull(actions);
        assertTrue(actions.get("commands") instanceof List);
        if (!script.isEmpty()) {
            assertFalse(((List<?>) actions.get("commands")).isEmpty(), script);
        }
        return ret;
    }
}
