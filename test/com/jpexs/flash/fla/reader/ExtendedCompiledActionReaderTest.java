package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class ExtendedCompiledActionReaderTest {

    // Independently transcribed native wire layouts: S=String, U=u32, B=u8,
    // P=String/u32 parameter, M=common metadata, X=SetVariable metadata with its word.
    private static final Object[][] LAYOUTS = {
        {6, new String[]{"PP", "PP", "PPM"}},
        {7, new String[]{"UPUPU", "UPUPU", "UPUPUM"}},
        {8, new String[]{"P", "P", "PM"}},
        {10, new String[]{"SSU", "SSU", "SSUM"}},
        {15, new String[]{"S", "S", "SM"}},
        {17, new String[]{"S", "S", "SM"}},
        {20, new String[]{"S", "S", "SBM"}},
        {22, new String[]{"P", "P", "PM"}},
        {23, new String[]{"UPP", "UPP", "UPPM"}},
        {24, new String[]{"PP", "PP", "PPX"}},
        {25, new String[]{"UPPS", "UPPS", "UPPSM"}},
        {26, new String[]{"PSSSSUUU", "PSSSSUUU", "PSSSSUUUM"}},
        {27, new String[]{"P", "P", "PM"}},
        {28, new String[]{"S", "S", "SMU"}},
        {30, new String[]{"P", "P", "PM"}},
        {31, new String[]{"PU", "PU", "PUM"}},
        {32, new String[]{"PUPU", "PUPU", "PUPUM"}},
        {33, new String[]{"S", "S", "SM"}},
        {34, new String[]{"UPUUU", "UPUUU", "UPUUUM"}},
        {36, new String[]{"P", "P", "PM"}},
        {37, new String[]{"SS", "SSU", "SSU", "SSUB", "SSUBM"}},
        {39, new String[]{"S", "SU", "SU", "SUM"}},
        {40, new String[]{"S", "S", "SM"}},
        {41, new String[]{"S", "S", "S", "SM"}},
        {43, new String[]{"S", "S", "SM"}},
        {44, new String[]{"S", "SSS", "SSS", "SSSM"}},
        {46, new String[]{"", "", "MP"}},
        {47, new String[]{"SS", "SS", "SS", "SSM"}},
        {49, new String[]{"PP", "PP", "PPM"}},
        {50, new String[]{"S", "S", "SM"}},
        {53, new String[]{"S", "S", "SM"}},
        {55, new String[]{"SU", "SU", "SUM"}},
        {56, new String[]{"SU", "SU", "SUM"}},
        {57, new String[]{"S", "S", "SM"}},
        {59, new String[]{"SSS", "SSSUU", "SSSUU", "SSSUUM"}},
        {60, new String[]{"SSS", "SSS", "SSSM"}},
        {62, new String[]{"SSS", "SSS", "SSSM"}},
        {63, new String[]{"", "", "M"}},
        {65, new String[]{"S", "S", "SM"}},
        {67, new String[]{"", "", "M"}},
        {69, new String[]{"S", "S", "SM"}},
        {70, new String[]{"", "", "M"}},
        {71, new String[]{"SSSU", "SSSUU", "SSSUUM"}},
        {73, new String[]{"SS", "SS", "SSM"}},
        {75, new String[]{"S", "S", "SM"}},
        {76, new String[]{"", "", "SUU"}},
        {77, new String[]{"", "", "M"}},
        {78, new String[]{"", "", "SUU", "SUUSS"}},
        {79, new String[]{"SSS", "SSS", "SSSM"}},
        {80, new String[]{"SS", "SS", "SS", "SSM"}},
        {82, new String[]{"", "", "MP"}},
        {83, new String[]{"", "", "SUU", "SUUSS"}},
        {84, new String[]{"S", "S", "SM"}},};

    @DataProvider(name = "contexts")
    public Object[][] contexts() {
        List<Object[]> cases = new ArrayList<>();
        for (Object[] layout : LAYOUTS) {
            int type = (Integer) layout[0];
            String[] versions = (String[]) layout[1];
            for (int revision = 0; revision < versions.length; revision++) {
                for (long list : new long[]{0, 1, 0xFFFFFFFFL}) {
                    for (boolean unicode : new boolean[]{false, true}) {
                        cases.add(new Object[]{type, revision, list, unicode, versions[revision]});
                    }
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "contexts")
    public void readsAllFactoryBodiesAndTheirIndependentGates(int type, int revision, long list, boolean unicode, String layout) throws Exception {
        Map<String, Object> expected = new LinkedHashMap<>();
        byte[] bytes = fixture(type, revision, list, unicode, layout, expected);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord root = a.readInline("compiledCommands", v -> {
        }, CompiledActionRecordDecoder::list);
        assertTrue(root.isComplete());
        FlaRecord command = records(root, "commands").get(0);
        assertTrue(command.isComplete());
        Map<String, Object> actual = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : command.getFields().entrySet()) {
            actual.put(entry.getKey(), entry.getValue() instanceof FlaRecord ? ((FlaRecord) entry.getValue()).getFields() : entry.getValue());
        }
        assertEquals(actual, expected);
        assertEquals(root.getConsumedData().toByteArray(), bytes);
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
        assertTrue(a.getObjects().isEmpty());
    }

    @DataProvider(name = "types")
    public Object[][] types() {
        return LAYOUTS;
    }

    @Test(dataProvider = "types")
    public void everyTruncatedBodyRetainsTheAttachedPartialCommand(int type, String[] versions) throws Exception {
        byte[] bytes = fixture(type, versions.length - 1, 1, true, versions[versions.length - 1], new LinkedHashMap<>());
        for (int length = 8; length < bytes.length; length++) {
            MfcArchive a = archive(Arrays.copyOf(bytes, length));
            FlaRecord[] root = {null};
            try {
                a.readInline("compiledCommands", v -> root[0] = v, CompiledActionRecordDecoder::list);
                fail("Expected truncation at " + length);
            } catch (UnsupportedFlaRecordException ex) {
                fail("Known body must not be unsupported", ex);
            } catch (IOException expected) {
                assertFalse(root[0].isComplete());
                assertEquals(root[0].getConsumedData().toByteArray(), Arrays.copyOf(bytes, a.getPosition()));
                if (length >= 12) {
                    assertFalse(records(root[0], "commands").get(0).isComplete());
                }
            }
        }
    }

    @Test(dataProvider = "types")
    public void futureBodyRevisionsStopBeforePayload(int type, String[] versions) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, 1);
        u32(out, type);
        u32(out, versions.length);
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord[] root = {null};
        try {
            a.readInline("compiledCommands", v -> root[0] = v, CompiledActionRecordDecoder::list);
            fail("Expected unsupported revision");
        } catch (UnsupportedFlaRecordException expected) {
            FlaRecord command = records(root[0], "commands").get(0);
            assertFalse(command.isComplete());
            assertEquals(command.get("revision"), (long) versions.length);
            assertEquals(a.readUI16(), 0xBEEF);
        }
    }

    @Test
    public void allNewTypesCanShareOneListWithoutInventedReferenceIds() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 1);
        u32(out, LAYOUTS.length);
        for (Object[] row : LAYOUTS) {
            String[] layouts = (String[]) row[1];
            byte[] bytes = fixture((Integer) row[0], layouts.length - 1, 1, true, layouts[layouts.length - 1], new LinkedHashMap<>());
            out.write(bytes, 8, bytes.length - 8);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord root = a.readInline("commands", v -> {
        }, CompiledActionRecordDecoder::list);
        assertEquals(records(root, "commands").size(), 53);
        assertTrue(root.isComplete());
        assertEquals(a.readUI16(), 0xBEEF);
        assertTrue(a.getObjects().isEmpty());
    }

    private static String operandKey(int type, int revision, boolean string, int index) {
        switch (type) {
            case 7:
                if (!string && index == 2) {
                    return "requestMethod";
                }
                if (!string && index == 3) {
                    return "targetMode";
                }
                break;
            case 10:
                if (string && index == 1) {
                    return "scene";
                }
                if (string && index == 2) {
                    return "frame";
                }
                if (!string && index == 1) {
                    return "frameMode";
                }
                break;
            case 15:
                if (string && index == 1) {
                    return "condition";
                }
                break;
            case 17:
                if (string && index == 1) {
                    return "condition";
                }
                break;
            case 20:
                if (string && index == 1) {
                    return "condition";
                }
                break;
            case 23:
                if (!string && index == 1) {
                    return "propertyIndex";
                }
                break;
            case 25:
                if (string && index == 1) {
                    return "depth";
                }
                break;
            case 26:
                if (string && index == 1) {
                    return "left";
                }
                if (string && index == 2) {
                    return "top";
                }
                if (string && index == 3) {
                    return "right";
                }
                if (string && index == 4) {
                    return "bottom";
                }
                if (!string && index == 1) {
                    return "lockCenter";
                }
                if (!string && index == 2) {
                    return "constrain";
                }
                break;
            case 28:
                if (string && index == 1) {
                    return "comment";
                }
                if (!string && index == 1) {
                    return "commentStyle";
                }
                break;
            case 31:
                if (!string && index == 1) {
                    return "targetMode";
                }
                break;
            case 32:
                if (!string && index == 1) {
                    return "requestMethod";
                }
                if (!string && index == 2) {
                    return "targetMode";
                }
                break;
            case 33:
                if (string && index == 1) {
                    return "includePath";
                }
                break;
            case 34:
                if (!string && index == 2) {
                    return "targetMode";
                }
                if (!string && index == 3) {
                    return "boundsMode";
                }
                if (!string && index == 4) {
                    return "printAsBitmap";
                }
                break;
            case 37:
                if (string && index == 1) {
                    return "functionSignature";
                }
                if (string && index == 2) {
                    return "parameters";
                }
                if (!string && index == 1) {
                    return "functionFlags";
                }
                break;
            case 39:
                if (string && index == 1) {
                    return "declaration";
                }
                if (!string && index == 1) {
                    return "variableFlags";
                }
                break;
            case 40:
                if (string && index == 1) {
                    return "returnValue";
                }
                break;
            case 41:
                if (string && index == 1) {
                    return "expression";
                }
                break;
            case 43:
                if (string && index == 1) {
                    return "condition";
                }
                break;
            case 44:
                if (string && index == 1) {
                    return revision == 0 ? "condition" : "initializer";
                }
                if (string && index == 2) {
                    return "condition";
                }
                if (string && index == 3) {
                    return "increment";
                }
                break;
            case 47:
                if (string && index == 1) {
                    return "iterator";
                }
                if (string && index == 2) {
                    return "collection";
                }
                break;
            case 50:
                if (string && index == 1) {
                    return "object";
                }
                break;
            case 53:
                if (string && index == 1) {
                    return "expression";
                }
                break;
            case 55:
                if (string && index == 1) {
                    return "caseExpression";
                }
                if (!string && index == 1) {
                    return "isDefaultCase";
                }
                break;
            case 56:
                if (string && index == 1) {
                    return "caseExpression";
                }
                if (!string && index == 1) {
                    return "isDefaultCase";
                }
                break;
            case 57:
                if (string && index == 1) {
                    return "initClipIndex";
                }
                break;
            case 59:
                if (string && index == 1) {
                    return "invocationSignature";
                }
                if (string && index == 2) {
                    return "object";
                }
                if (string && index == 3) {
                    return "arguments";
                }
                if (!string && index == 1) {
                    return "objectExpressionFlags";
                }
                break;
            case 60:
                if (string && index == 1) {
                    return "object";
                }
                if (string && index == 2) {
                    return "method";
                }
                if (string && index == 3) {
                    return "parameters";
                }
                break;
            case 62:
                if (string && index == 1) {
                    return "object";
                }
                if (string && index == 2) {
                    return "method";
                }
                if (string && index == 3) {
                    return "arguments";
                }
                break;
            case 65:
                if (string && index == 1) {
                    return "exception";
                }
                break;
            case 69:
                if (string && index == 1) {
                    return "expression";
                }
                break;
            case 71:
                if (string && index == 1) {
                    return "name";
                }
                if (string && index == 2) {
                    return "superClass";
                }
                if (string && index == 3) {
                    return "interfaces";
                }
                if (!string && index == 1) {
                    return "dynamic";
                }
                if (!string && index == 2) {
                    return "intrinsic";
                }
                break;
            case 73:
                if (string && index == 1) {
                    return "name";
                }
                if (string && index == 2) {
                    return "interfaces";
                }
                break;
            case 75:
                if (string && index == 1) {
                    return "importName";
                }
                break;
            case 76:
                if (string && index == 1) {
                    return "packageName";
                }
                break;
            case 78:
                if (string && index == 1) {
                    return "namespaceName";
                }
                if (string && index == 2) {
                    return "namespaceValue";
                }
                if (string && index == 3) {
                    return "namespaceQualifier";
                }
                break;
            case 79:
                if (string && index == 1) {
                    return "object";
                }
                if (string && index == 2) {
                    return "method";
                }
                if (string && index == 3) {
                    return "arguments";
                }
                break;
            case 80:
                if (string && index == 1) {
                    return "iterator";
                }
                if (string && index == 2) {
                    return "collection";
                }
                break;
            case 83:
                if (string && index == 1) {
                    return "namespaceName";
                }
                if (string && index == 2) {
                    return "namespaceValue";
                }
                if (string && index == 3) {
                    return "namespaceQualifier";
                }
                break;
            case 84:
                if (string && index == 1) {
                    return "configuration";
                }
                break;
            default:
                break;
        }
        if (!string && index == 1 && (type == 7 || type == 25 || type == 34)) {
            return "discardedLegacyDword";
        }
        if (!string && (type == 76 || type == 78 || type == 83)) {
            return index == 1 ? "value40" : "suppressBlockDelimiter";
        }
        return (string ? "string" : "value") + index;
    }

    private static byte[] fixture(int type, int revision, long list, boolean unicode, String layout, Map<String, Object> expected) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, list);
        u32(out, 1);
        u32(out, type);
        expected.put("type", (long) type);
        boolean present = list != 0 || type != 46 && type != 70 && type != 82;
        expected.put("bodyPresent", present);
        if (!present) {
            return out.toByteArray();
        }
        u32(out, revision);
        expected.put("revision", (long) revision);
        int strings = 0, words = 0, parameters = 0, serial = 0;
        for (char token : layout.toCharArray()) {
            serial++;
            switch (token) {
                case 'S': {
                    String value = "text" + serial + (unicode ? " Ω" : "");
                    string(out, value, unicode);
                    expected.put(operandKey(type, revision, true, ++strings), value);
                    break;
                }
                case 'U': {
                    long value = 0x80000000L + serial;
                    u32(out, value);
                    expected.put(operandKey(type, revision, false, ++words), value);
                    break;
                }
                case 'B': {
                    out.write(0x80);
                    expected.put(type == 20 ? "emptyBody" : "declarationOnly", 128);
                    break;
                }
                case 'P': {
                    Map<String, Object> parameter = new LinkedHashMap<>();
                    String value = "parameter" + serial;
                    long flags = 0x80000000L + serial;
                    string(out, value, unicode);
                    u32(out, flags);
                    parameter.put("text", value);
                    parameter.put("flags", flags);
                    expected.put("parameter" + (++parameters), parameter);
                    break;
                }
                case 'M':
                case 'X': {
                    for (String name : new String[]{"sourcePrefix", "sourceText", "sourceSuffix"}) {
                        string(out, name, unicode);
                        expected.put(name, name);
                    }
                    u32(out, 0x80000001L);
                    expected.put("value40", 0x80000001L);
                    if (token == 'X') {
                        u16(out, 0xFEDC);
                        expected.put("valueQuoteCharacter", 0xFEDC);
                    }
                    u32(out, 0xFFFFFFFFL);
                    expected.put("suppressBlockDelimiter", 0xFFFFFFFFL);
                    break;
                }
                default:
                    throw new AssertionError(token);
            }
        }
        return out.toByteArray();
    }
}
