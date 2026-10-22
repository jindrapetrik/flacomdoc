package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.exporter.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.validation.*;
import javax.xml.transform.stream.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

/**
 * Golden source forms follow native formatter templates, using serialized
 * commands.
 */
public class XflCompiledControlExportTest {

    static final class Command {

        final int type, revision;
        final Object[] operands;
        int delimiterState;
        long commentStyle;
        String[] text = {"", "", ""};

        Command(int type, int revision, Object... operands) {
            this.type = type;
            this.revision = revision;
            this.operands = operands;
        }

        Command text(String prefix, String body, String suffix) {
            text = new String[]{prefix, body, suffix};
            return this;
        }

        Command suppressed() {
            delimiterState = 1;
            return this;
        }

        Command commentStyle(long value) {
            commentStyle = value;
            return this;
        }
    }

    static final class Parameter {

        final String text;
        final int flags;

        Parameter(String text, int flags) {
            this.text = text;
            this.flags = flags;
        }
    }

    static Command c(int type, Object... operands) {
        return new Command(type, 1, operands);
    }

    static Parameter expression(String text) {
        return new Parameter(text, 1);
    }

    @DataProvider(name = "control")
    public Object[][] control() {
        return new Object[][]{
            {new Command[]{new Command(28, 2, " A\r\nB\nC ").commentStyle(1), c(1)}, "/* A\r\nB\nC */\nplay();\n"},
            {new Command[]{new Command(28, 2, "A\r\nB").commentStyle(0)}, "// A\n// B\n"},
            {new Command[]{new Command(28, 2, "preserved").commentStyle(0x80000000L)}, "/*preserved*/\n"},
            {new Command[]{new Command(28, 1, "legacy")}, "// legacy\n"},
            {new Command[]{new Command(20, 2, "ready", (byte) 1), new Command(21, 2).suppressed(), c(1)}, "while (ready);\nplay();\n"},
            {new Command[]{c(20, "x < 3"), c(1), c(21)}, "while (x < 3) {\nplay();\n}\n"},
            {new Command[]{c(44, "var i = 0", "i < 3", "i++"), c(27, expression("i")), c(45)}, "for (var i = 0; i < 3; i++) {\ntrace(i);\n}\n"},
            {new Command[]{c(44, "", "", ""), c(46), c(45)}, "for (; ; ) {\nbreak;\n}\n"},
            {new Command[]{new Command(44, 0, "i < 3"), c(45)}, "for (; i < 3; ) {\n}\n"},
            {new Command[]{c(47, "var key", "object"), c(27, expression("key")), c(48)}, "for (var key in object) {\ntrace(key);\n}\n"},
            {new Command[]{c(80, "var value", "object"), c(27, expression("value")), c(81)}, "for each (var value in object) {\ntrace(value);\n}\n"},
            {new Command[]{c(53, "state"), c(55, "\"a\"", 0), c(2), c(46), c(56, "", 1), c(1), c(54)}, "switch (state) {\ncase \"a\":\nstop();\nbreak;\ndefault:\nplay();\n}\n"},
            {new Command[]{c(49, expression("clip"), new Parameter("other clip", 0))}, "swapDepths(clip, \"other clip\");\n"},
            {new Command[]{c(57, "2"), c(1), c(58)}, "#initclip 2\nplay();\n#endinitclip\n"},
            {new Command[]{c(70)}, "#strict\n"},
            {new Command[]{new Command(76, 2, "example.packageName", 0, 0), c(77)}, "package example.packageName {\n}\n"},
            {new Command[]{c(84, "CONFIG::debug"), c(27, new Parameter("debug", 0)), c(85)}, "CONFIG::debug {\ntrace(\"debug\");\n}\n"},
            {new Command[]{c(53, "state"), c(55, "1", 0), c(20, "ready"), c(46), c(21), c(54)}, "switch (state) {\ncase 1:\nwhile (ready) {\nbreak;\n}\n}\n"},
            {new Command[]{c(10, "", "5", 0), c(1), c(19)}, "ifFrameLoaded (5) {\nplay();\n}\n"},
            {new Command[]{c(10, "Scene 2", "loaded", 1), c(1), c(19)}, "ifFrameLoaded (\"Scene 2\", \"loaded\") {\nplay();\n}\n"},
            {new Command[]{c(10, "", "frameExpression", 2), c(1), c(19)}, "ifFrameLoaded (frameExpression) {\nplay();\n}\n"},
            {new Command[]{c(13, 259), c(1), c(14)}, "onClipEvent (load, enterFrame, data) {\nplay();\n}\n"},
            {new Command[]{c(23, 10, new Parameter("clip", 0), expression("angle"))}, "setProperty(\"clip\", _rotation, angle);\n"},
            {new Command[]{c(25, 123, expression("clip"), new Parameter("copy", 0), "baseDepth + 1")}, "duplicateMovieClip(clip, \"copy\", baseDepth + 1);\n"},
            {new Command[]{c(26, expression("clip"), "", "", "", "", 0, 0, 99)}, "startDrag(clip);\n"},
            {new Command[]{c(26, expression("clip"), "", "", "", "", 1, 0, 99)}, "startDrag(clip, true);\n"},
            {new Command[]{c(26, expression("clip"), "-10", "top", "200", "bottom", 0, 1, 99)}, "startDrag(clip, false, -10, top, 200, bottom);\n"},
            {new Command[]{c(7, 99, new Parameter("movie.swf", 0), 2, new Parameter("2", 0), 0)}, "loadMovieNum(\"movie.swf\", 2, \"POST\");\n"},
            {new Command[]{c(7, 99, expression("url"), 0, new Parameter("clip", 0), 1)}, "loadMovie(url, \"clip\");\n"},
            {new Command[]{c(32, new Parameter("data.txt", 0), 1, new Parameter("3", 0), 0)}, "loadVariablesNum(\"data.txt\", 3, \"GET\");\n"},
            {new Command[]{c(32, expression("url"), 0, expression("clip"), 1)}, "loadVariables(url, clip);\n"},
            {new Command[]{c(31, new Parameter("2", 0), 0)}, "unloadMovieNum(2);\n"},
            {new Command[]{c(31, expression("clip"), 1)}, "unloadMovie(clip);\n"},
            {new Command[]{c(34, 99, new Parameter("3", 0), 0, 0, 0)}, "printNum(3, \"bmovie\");\n"},
            {new Command[]{c(34, 99, expression("clip"), 1, 1, 1)}, "printAsBitmap(clip, \"bframe\");\n"},
            {new Command[]{c(34, 99, new Parameter("3", 0), 0, 2, 1)}, "printAsBitmapNum(3, \"bmax\");\n"},
            {new Command[]{c(37, "sum:Number", "a:Number, b:Number", 0), c(40, "a+b"), c(38)}, "function sum(a:Number, b:Number):Number {\nreturn a+b;\n}\n"},
            {new Command[]{c(37, "value:String", "", 13), c(40, "\"ok\""), c(38)}, "public static function get value():String {\nreturn \"ok\";\n}\n"},
            {new Command[]{c(37, "value:void", "x:String", 18), c(38)}, "private function set value(x:String):void {\n}\n"},
            {new Command[]{c(73, "I", ""), new Command(37, 3, "call:void", "x:Number", 0, (byte) 1), c(74)}, "interface I {\nfunction call(x:Number):void;\n}\n"},
            {new Command[]{c(39, "x:Number = 5", 132)}, "static protected var x:Number = 5;\n"},
            {new Command[]{c(39, "x:Number", 1)}, "public var x:Number;\n"},
            {new Command[]{c(71, "Example", "Base", "I1, I2", 1, 0), c(72)}, "dynamic class Example extends Base implements I1, I2 {\n}\n"},
            {new Command[]{c(71, "NativeType", "", "", 0, 1), c(72)}, "intrinsic class NativeType {\n}\n"},
            {new Command[]{c(73, "IChild", "IBase"), c(74)}, "interface IChild extends IBase {\n}\n"},
            {new Command[]{new Command(78, 2, "ns", 31, 42)}, "namespace ns;\n"},
            {new Command[]{new Command(78, 3, "ns", 31, 42, "\"urn:test\"", "public")}, "public namespace ns = \"urn:test\";\n"},
            {new Command[]{new Command(83, 3, "CONFIG", 31, 42, "", "")}, "config namespace CONFIG;\n"},
            {new Command[]{new Command(83, 3, "CONFIG", 31, 42, "otherNamespace", "public")}, "public config namespace CONFIG = otherNamespace;\n"},
            {new Command[]{c(59, "trace(message)", "ignored", "x, \"text\"", 0, 0)}, "trace(x, \"text\");\n"},
            {new Command[]{c(59, ".call(arg)", "clip", "x", 1, 0)}, "clip.call(x);\n"},
            {new Command[]{c(59, "@Class.call()", "clip", "ignored", 1, 1)}, "clip.call();\n"},
            {new Command[]{c(60, "clip", "handler", "event"), c(61)}, "clip.handler = function(event) {\n}\n"},
            {new Command[]{c(79, "clip", "call", "a, b")}, "clip.call(a, b);\n"},
            {new Command[]{c(62, "xml", ".child", "\"name\"")}, "xml.child(\"name\");\n"},
            {new Command[]{c(62, "xml", "..descendant", "")}, "xml..descendant();\n"},
            {new Command[]{new Command(37, 4, "test:void", "", 0, (byte) 0), c(38)}, "function test():void {\n}\n"},
            {new Command[]{new Command(39, 3, "x:Number", 0)}, "var x:Number;\n"},
            {new Command[]{new Command(71, 2, "Example", "", "", 0, 0), c(72)}, "class Example {\n}\n"}
        };
    }

    @Test(dataProvider = "control")
    public void reconstructsNativeControlTemplatesFromParsedRecords(Command[] commands, String expected) throws Exception {
        XflExportResult result = export(commands);
        Element frame = frame(result);
        NodeList scripts = frame.getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "script");
        assertEquals(scripts.getLength(), 1, result.getReport());
        assertEquals(scripts.item(0).getTextContent(), expected);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPILED_SCRIPT")), result.getReport());
    }

    @DataProvider(name = "invalid")
    public Object[][] invalid() {
        return new Object[][]{
            {new Command[]{c(55, "1", 0)}}, {new Command[]{c(58)}}, {new Command[]{c(53, "state"), c(21)}},
            {new Command[]{new Command(20, 2, "ready", (byte) 1), c(21)}},
            {new Command[]{c(7, 0, new Parameter("url", 0), 3, new Parameter("1", 0), 0)}},
            {new Command[]{c(23, 0xFFFFFFFFL, expression("clip"), expression("value"))}},
            {new Command[]{c(13, 0), c(14)}}, {new Command[]{c(13, 512), c(14)}},
            {new Command[]{c(34, 0, expression("clip"), 1, 3, 0)}},
            {new Command[]{c(10, "", "frame", 3), c(19)}},
            {new Command[]{new Command(78, 0)}}, {new Command[]{c(59, "@invalid", "clip", "", 1, 1)}},
            {new Command[]{c(60, "", "handler", ""), c(61)}}, {new Command[]{c(62, "", ".child", "")}},
            {new Command[]{new Command(37, 3, "prototype:void", "", 0, (byte) 1), c(38)}},
            {new Command[]{c(20, "ready"), new Command(21, 2).suppressed()}}
        };
    }

    @Test(dataProvider = "invalid")
    public void unsupportedNormalizationOrBrokenDelimitersOmitTheWholeScript(Command[] commands) throws Exception {
        XflExportResult result = export(commands);
        assertTrue(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPILED_SCRIPT")));
        assertEquals(frame(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "Actionscript").getLength(), 0);
    }

    @DataProvider(name = "archivedBlocks")
    public Object[][] archivedBlocks() {
        return new Object[][]{
            {new Command(8, 2, expression("clip")), 9, "tellTarget (clip)"},
            {new Command(10, 2, "", "5", 0), 19, "ifFrameLoaded (5)"},
            {new Command(20, 2, "ready", (byte) 0), 21, "while (ready)"},
            {new Command(44, 3, "var i=0", "i<3", "i++"), 45, "for (var i=0;i<3;i++)"},
            {new Command(47, 3, "var key", "object"), 48, "for (var key in object)"},
            {new Command(50, 2, "object"), 51, "with (object)"},
            {new Command(80, 3, "var value", "object"), 81, "for each (var value in object)"}
        };
    }

    @Test(dataProvider = "archivedBlocks")
    public void completeArchivedTextPreservesUnbracedContexts(Command open, int end, String header) throws Exception {
        String prefix = "/*Žluťoučký*/\r\n", suffix = " // trailing\r\n";
        Command body = new Command(2, 2).text("\t", "stop();", suffix);
        Command close = new Command(end, 2).suppressed();
        open.text(prefix, header, " ");
        XflExportResult result = export(new Command[]{open, body, close});
        assertEquals(frame(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "script").item(0).getTextContent(), prefix + header + " \tstop();" + suffix);
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPILED_SCRIPT")), result.getReport());
    }

    @Test
    public void archivedElseChainAndWhitespaceAreNotReformatted() throws Exception {
        XflExportResult result = export(new Command[]{
            new Command(15, 2, "ready").text("", "if (ready)", " "),
            new Command(2, 2).text("", "stop();", " "),
            new Command(16, 2).text("", "else", " "),
            new Command(1, 2).text("", "play();", "\n"), new Command(18, 2).suppressed()});
        assertEquals(frame(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "script").item(0).getTextContent(), "if (ready) stop(); else play();\n");
    }

    @Test
    public void missingCachedThrowTextMustUseItsOperandInsteadOfDroppingTheStatement() throws Exception {
        XflExportResult result = export(new Command[]{new Command(1, 2).text("", "play();", "\n"), new Command(69, 2, "problem")});
        assertEquals(frame(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "script").item(0).getTextContent(), "play();\nthrow problem;\n");
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPILED_SCRIPT")), result.getReport());
    }

    @DataProvider(name = "uncachedEnds")
    public Object[][] uncachedEnds() {
        return new Object[][]{
            {new Command(8, 2, expression("clip")), 9, "tellTarget (clip) {"},
            {new Command(15, 2, "ready"), 18, "if (ready) {"},
            {new Command(20, 2, "ready", (byte) 0), 21, "while (ready) {"},
            {new Command(57, 2, "2"), 58, "#initclip 2"}
        };
    }

    @Test(dataProvider = "uncachedEnds")
    public void missingNonsuppressedEndTextMustNotProduceAnUnclosedScript(Command open, int end, String expected) throws Exception {
        open.text("", "stale partial header", "\n");
        XflExportResult result = export(new Command[]{open, new Command(2, 2).text("", "stop();", "\n"), new Command(end, 2)});
        assertEquals(frame(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "script").item(0).getTextContent(), expected + "\nstop();\n" + (end == 58 ? "#endinitclip" : "}") + "\n");
        assertFalse(result.getIssues().stream().anyMatch(i -> i.getCode().equals("COMPILED_SCRIPT")), result.getReport());
    }

    private static Element frame(XflExportResult result) throws Exception {
        byte[] data = result.getFiles().get("DOMDocument.xml").toByteArray();
        SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema").newSchema(new File(System.getProperty("flacomdoc.xflSchema", "testdata/xfl-schema/xfl.xsd"))).newValidator().validate(new StreamSource(new ByteArrayInputStream(data)));
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        NodeList frames = f.newDocumentBuilder().parse(new ByteArrayInputStream(data)).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", "DOMFrame");
        for (int i = 0; i < frames.getLength(); i++) {
            if (((Element) frames.item(i)).getAttribute("name").equals("Control export target")) {
                return (Element) frames.item(i);
            }
        }
        throw new AssertionError("Missing target frame");
    }

    private static XflExportResult export(Command[] commands) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, commands.length);
        for (Command c : commands) {
            u32(out, c.type);
            u32(out, c.revision);
            for (Object operand : c.operands) {
                if (operand instanceof Parameter) {
                    Parameter p = (Parameter) operand;
                    string(out, p.text, true);
                    u32(out, p.flags);
                } else if (operand instanceof String) {
                    string(out, (String) operand, true);
                } else if (operand instanceof Byte) {
                    out.write((Byte) operand);
                } else {
                    u32(out, ((Number) operand).longValue());
                }
            }
            int metadata = c.type == 37 ? 4 : c.type == 39 || c.type == 41 || c.type == 44 || c.type == 47 || c.type == 59 || c.type == 80 ? 3 : 2;
            if (c.revision >= metadata && c.type != 76 && c.type != 78 && c.type != 83) {
                for (String part : c.text) {
                    string(out, part, true);
                }
                u32(out, 0);
                u32(out, c.delimiterState);
                if (c.type == 28) {
                    u32(out, c.commentStyle);
                }
            }
        }
        MfcArchive a = archive(out.toByteArray());
        FlaRecord list = a.readInline("compiledCommands", r -> {
        }, CompiledActionRecordDecoder::list);
        assertEquals(a.getRemaining(), 0);
        assertTrue(list.isComplete());
        Path path = Paths.get("testdata/flang/mx/0013_frame_action");
        Map<String, byte[]> streams = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(path)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
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
        frame.set("name", "Control export target");
        FlaRecord actions = (FlaRecord) frame.get("actions");
        actions.set("source", "");
        actions.set("compiledCommands", list);
        return new BinaryFlaToXflExporter().export(doc);
    }

    private static FlaRecord find(FlaRecord r, Set<FlaRecord> seen) {
        if (r == null || !seen.add(r)) {
            return null;
        }
        if (r.getClassName().equals("CPicFrame") && r.get("actions") instanceof FlaRecord) {
            return r;
        }
        for (FlaRecord child : r.getChildren()) {
            FlaRecord value = find(child, seen);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
