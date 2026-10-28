package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.exporter.*;
import com.jpexs.flash.fla.reader.*;
import java.io.File;
import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.converter.XflReaderTest.*;

/** Native CheckBox, Menu and RadioButton Smart Clips created in Flash 5. */
public class F5SmartClipConversionTest {

    @DataProvider(name = "bridges")
    public Object[][] bridges() {
        List<Object[]> inputs = new ArrayList<>();
        for (String fixture : Arrays.asList("f5-smartclips", "f5-smartclips-edited")) {
            for (FlaFormatVersion bridge : Arrays.asList(null, FlaFormatVersion.MX, FlaFormatVersion.MX2004, FlaFormatVersion.CS4)) {
                inputs.add(new Object[]{fixture, bridge});
            }
        }
        return inputs.toArray(new Object[0][]);
    }

    @Test(dataProvider = "bridges")
    public void nativeSmartClipsKeepDefinitionsAndInstanceValuesThroughCs6(String fixture, FlaFormatVersion bridge) throws Exception {
        BinaryFlaDocument original = new BinaryFlaReader().read(new File("testdata/components/" + fixture + ".fla"));
        Map<String, List<Object>> definitions = definitions(original), instances = instances(original);
        assertEquals(definitions.size(), 3);
        assertEquals(instances.size(), 3);
        List<?> checked = (List<?>) instances.get("CheckBox").get(1);
        assertEquals(checked.get(0), "checked");
        assertEquals(((Number) checked.get(2)).intValue(), fixture.endsWith("edited") ? 0 : 1);
        BinaryFlaDocument source = bridge == null ? original : read(new BinaryFlaConverter(bridge).convert(original));
        XflExportResult xfl = new BinaryFlaToXflExporter(XflFormatVersion.CS6).export(source);
        MemoryInput input = new MemoryInput();
        xfl.getFiles().forEach((path, data) -> input.files.put(path, data.toByteArray()));
        BinaryFlaDocument imported = new XflReader().read(input);
        BinaryFlaConversionResult converted = new BinaryFlaConverter(FlaFormatVersion.F5).convert(imported);
        BinaryFlaDocument output = read(converted);
        assertEquals(definitions(output), definitions);
        assertEquals(instances(output), instances);
        assertEquals(definitions(original), definitions, "Source definitions mutated");
        assertEquals(instances(original), instances, "Source instances mutated");
        for (FlaRecord instance : of(output, "CPicSprite")) {
            if (!entries(record(instance, "properties")).isEmpty()) {
                nativeLayout(record(instance, "properties"));
            }
            if (!entries(record(instance, "legacyProperties")).isEmpty()) {
                nativeLayout(record(instance, "legacyProperties"));
                assertEquals(instance.get("legacyParametersAreLocked"), 1L);
            }
        }
        for (FlaRecord descriptor : output.getLibraryDescriptors()) {
            if (!entries(record(descriptor, "properties")).isEmpty()) {
                nativeLayout(record(descriptor, "properties"));
            }
        }
    }

    private static void nativeLayout(FlaRecord properties) {
        assertEquals(((Number) properties.get("revision")).intValue(), 1);
        for (FlaRecord parameter : entries(properties)) {
            assertEquals(((Number) parameter.get("revision")).intValue(), 0);
            assertEquals(((Number) record(parameter, "value").get("revision")).intValue(), 1);
        }
    }

    private static Map<String, List<Object>> definitions(BinaryFlaDocument document) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (FlaRecord descriptor : document.getLibraryDescriptors()) {
            FlaRecord properties = record(descriptor, "properties");
            if (!entries(properties).isEmpty()) {
                result.put((String) descriptor.get("name"), signature(properties));
            }
        }
        return result;
    }

    private static Map<String, List<Object>> instances(BinaryFlaDocument document) {
        Map<Long, String> names = new HashMap<>();
        for (FlaRecord descriptor : document.getLibraryDescriptors()) {
            names.put(((Number) descriptor.get("libraryId")).longValue(), (String) descriptor.get("name"));
        }
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (FlaRecord instance : of(document, "CPicSprite")) {
            FlaRecord properties = record(instance, "properties");
            if (entries(properties).isEmpty()) {
                properties = record(instance, "legacyProperties");
            }
            if (!entries(properties).isEmpty()) {
                String name = names.get(((Number) instance.get("libraryId")).longValue());
                assertNull(result.put(name, signature(properties)), "Duplicate Smart Clip instance");
            }
        }
        return result;
    }

    private static List<Object> signature(FlaRecord properties) {
        List<Object> result = new ArrayList<>();
        for (FlaRecord parameter : entries(properties)) {
            FlaRecord value = record(parameter, "value");
            List<Object> values = new ArrayList<>();
            for (FlaRecord entry : entries(value)) {
                values.add(Arrays.asList(entry.get("name"), entry.get("value")));
            }
            result.add(Arrays.asList(parameter.get("name"), value.get("type"), value.get("selectedIndex"), values));
        }
        return result;
    }

    private static FlaRecord record(FlaRecord record, String field) {
        return record == null ? null : (FlaRecord) record.get(field);
    }

    @SuppressWarnings("unchecked")
    private static List<FlaRecord> entries(FlaRecord record) {
        return record == null ? Collections.emptyList() : (List<FlaRecord>) record.get("entries");
    }
}
