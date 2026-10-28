package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.reader.*;
import com.jpexs.flash.fla.exporter.*;
import java.io.File;
import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.converter.XflReaderTest.*;

public class GeneratorCommandConversionTest {

    @DataProvider(name = "nativeTargets")
    public Object[][] nativeTargets() {
        List<Object[]> cases = new ArrayList<>();
        for (String source : Arrays.asList("f4-list", "f5-list")) {
            for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F4, FlaFormatVersion.F5,
                    FlaFormatVersion.MX, FlaFormatVersion.MX2004, FlaFormatVersion.F8,
                    FlaFormatVersion.CS3, FlaFormatVersion.CS4)) {
                cases.add(new Object[]{source, target});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "nativeTargets")
    public void nativeCommandsKeepTheirParametersGeometryAndFollowingTimeline(String fixture, FlaFormatVersion target) throws Exception {
        BinaryFlaDocument original = new BinaryFlaReader().read(new File("testdata/generator/" + fixture + ".fla"));
        assertTrue(original.getIssues().isEmpty(), original.getIssues().toString());
        List<FlaRecord> commands = of(original, "CCommandObj");
        assertFalse(commands.isEmpty());
        BinaryFlaConversionResult converted = new BinaryFlaConverter(target).convert(original);
        BinaryFlaDocument output = read(converted);
        List<FlaRecord> actual = of(output, "CCommandObj");
        assertEquals(actual.size(), commands.size());
        for (int i = 0; i < commands.size(); i++) {
            for (String field : Arrays.asList("revision", "matrixWords", "bounds", "command", "displayLabel")) {
                assertEquals(actual.get(i).get(field), commands.get(i).get(field), field);
            }
            assertEquals(actual.get(i).get("revision"), 1);
            assertTrue(actual.get(i).get("command").toString().startsWith("com.macromedia.generator.commands."));
        }
        assertEquals(of(output, "CPicFrame").size(), of(original, "CPicFrame").size());
        assertEquals(of(output, "CPicLayer").size(), of(original, "CPicLayer").size());
        assertEquals(output.getSceneDescriptors().size(), original.getSceneDescriptors().size());
    }

    @Test
    public void unsupportedEarlyTargetsReportOmissionAndKeepTheTimeline() throws Exception {
        BinaryFlaDocument original = new BinaryFlaReader().read(new File("testdata/generator/f4-list.fla"));
        BinaryFlaConversionResult converted = new BinaryFlaConverter(FlaFormatVersion.F2).convert(original);
        BinaryFlaDocument output = read(converted);
        assertTrue(of(output, "CCommandObj").isEmpty());
        assertFalse(converted.getIssues().isEmpty());
        assertEquals(of(output, "CPicLayer").size(), of(original, "CPicLayer").size());
    }

    @Test
    public void revisionZeroAndEditedCommandsRetainTheirNativeLayout() throws Exception {
        BinaryFlaDocument original = new BinaryFlaReader().read(new File("testdata/generator/f4-list.fla"));
        FlaRecord command = of(original, "CCommandObj").get(0);
        String edited = "com.macromedia.generator.commands.ListCommand instancename=\"edited\" ";
        command.set("command", edited);
        command.set("revision", 0);
        for (FlaFormatVersion target : Arrays.asList(FlaFormatVersion.F4, FlaFormatVersion.F5)) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(original));
            FlaRecord actual = of(output, "CCommandObj").get(0);
            assertEquals(actual.get("revision"), 0);
            assertEquals(actual.get("command"), edited);
            assertNull(actual.get("displayLabel"));
            assertEquals(of(output, "CPicLayer").size(), of(original, "CPicLayer").size());
        }
        assertEquals(command.get("command"), edited);
        assertEquals(command.get("displayLabel"), "List", "Writing an older revision must not mutate source values");
    }

    @Test
    public void xflReportsGeneratorContentAsUnsupportedWithoutBreakingTheScene() throws Exception {
        for (String fixture : Arrays.asList("f4-list", "f5-list")) {
            BinaryFlaDocument original = new BinaryFlaReader().read(new File("testdata/generator/" + fixture + ".fla"));
            for (XflFormatVersion target : XflFormatVersion.values()) {
                XflExportResult result = new BinaryFlaToXflExporter(target).export(original);
                assertNotNull(result.getFiles().get("DOMDocument.xml"));
                assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getCode().equals("GENERATOR_COMMAND")
                        && issue.getMessage().contains("com.macromedia.generator.commands.ListCommand")));
                assertFalse(result.getIssues().stream().anyMatch(issue -> issue.getCode().equals("PARSE_ISSUE")
                        || issue.getCode().equals("DISPLAY_OBJECT")), result.getReport());
                assertFalse(of(original, "CCommandObj").isEmpty(), "XFL export must not modify the input model");
            }
        }
    }
}
