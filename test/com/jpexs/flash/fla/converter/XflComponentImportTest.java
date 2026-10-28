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
import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.converter.XflReaderTest.*;

/**
 * Regression coverage for editable MX components passing through XFL.
 */
public class XflComponentImportTest {

    private String parameters(String label) {
        return "<parametersAsXML><![CDATA["
                + "<property id='label'><Inspectable name='Label' variable='label' type='Default' defaultValue='" + label + "'/></property>"
                + "<property id='labels'><Inspectable name='Labels' variable='labels' type='Array' defaultValue='First&amp;#2CItem,Second'/></property>"
                + "<property id='selected'><Inspectable name='Selected' variable='selected' type='Boolean' defaultValue='true'/></property>"
                + "<property id='mode'><Inspectable name='Mode' variable='mode' type='List' defaultValue='On' enumeration='Off,On'/></property>"
                + "]]></parametersAsXML>";
    }

    private MemoryInput fixture() {
        MemoryInput input = input("<symbols><Include href='button.xml'/></symbols>"
                + timeline("<DOMComponentInstance name='button' libraryItemName='PushButton'>" + parameters("Hello") + "</DOMComponentInstance>"));
        input.put("LIBRARY/button.xml", "<DOMComponentItem name='PushButton' customIconID='1' tooltip='Push Button' parametersAreLocked='true' descriptionType='reference' symbolDescription='UI Components/FPushButton' livePreviewScmHRef='preview.swf' livePreviewScmSourceFilename='button_live.swf' customUIScmHRef='custom.swf' customUIScmSourceFilename='custom.swf'>"
                + "<timeline><DOMTimeline><layers/></DOMTimeline></timeline>" + parameters("Push Button") + "</DOMComponentItem>");
        input.files.put("bin/preview.swf", new byte[]{70, 87, 83, 6});
        input.files.put("bin/custom.swf", new byte[]{67, 87, 83, 6});
        return input;
    }

    private FlaRecord record(FlaRecord r, String name) {
        return (FlaRecord) r.get(name);
    }

    @SuppressWarnings("unchecked")
    private List<FlaRecord> entries(FlaRecord r) {
        return (List<FlaRecord>) r.get("entries");
    }

    private void values(FlaRecord p, String label) {
        assertEquals(((Number) p.get("revision")).intValue(), 2);
        List<FlaRecord> entries = entries(p);
        assertEquals(entries.size(), 4);
        assertEquals(entries.get(0).get("name"), "Label");
        assertEquals(entries.get(0).get("variableName"), "label");
        assertEquals(entries(record(entries.get(0), "value")).get(0).get("value"), label);
        FlaRecord array = record(entries.get(1), "value");
        assertEquals(((Number) array.get("type")).intValue(), 2);
        assertEquals(entries(array).get(0).get("value"), "First,Item");
        assertEquals(entries(array).get(1).get("value"), "Second");
        assertEquals(entries(record(entries.get(2), "value")).get(0).get("value"), "true");
        FlaRecord list = record(entries.get(3), "value");
        assertEquals(((Number) list.get("selectedIndex")).intValue(), 1);
        assertEquals(entries(list).get(0).get("value"), "Off");
        assertEquals(entries(list).get(1).get("value"), "On");
    }

    @DataProvider(name = "targets")
    public Object[][] targets() {
        return new Object[][]{{FlaFormatVersion.MX}, {FlaFormatVersion.MX2004}, {FlaFormatVersion.F8}, {FlaFormatVersion.CS3}, {FlaFormatVersion.CS4}};
    }

    @Test(dataProvider = "targets")
    public void typedDefinitionsInstancesAndPreviewsSurviveConversion(FlaFormatVersion target) throws Exception {
        BinaryFlaDocument model = new XflReader().read(fixture());
        Map<FlaRecord, Map<String, Object>> snapshot = new IdentityHashMap<>();
        for (FlaRecord r : records(model)) {
            snapshot.put(r, new LinkedHashMap<>(r.getFields()));
        }
        // Exercise both XFL -> target and CS4 binary -> target.
        for (BinaryFlaDocument source : Arrays.asList(model, read(new BinaryFlaConverter(FlaFormatVersion.CS4).convert(model)))) {
            BinaryFlaDocument output = read(new BinaryFlaConverter(target).convert(source));
            FlaRecord descriptor = output.getLibraryDescriptors().get(0);
            FlaRecord component = record(descriptor, "component");
            FlaRecord definition = target == FlaFormatVersion.MX ? record(descriptor, "properties") : record(component, "properties");
            values(definition, "Push Button");
            values(record(of(output, "CPicSprite").get(0), "properties"), "Hello");
            if (target == FlaFormatVersion.MX) {
                assertEquals(descriptor.get("legacyComponentEnabled"), 1L);
                assertEquals(descriptor.get("legacyComponentIconId"), 1L);
                assertEquals(descriptor.get("legacyComponentTooltip"), "Push Button");
                assertEquals(descriptor.get("legacyParametersAreLocked"), 1L);
                assertEquals(descriptor.get("legacyDescriptionType"), 1L);
                assertEquals(descriptor.get("legacySymbolDescription"), "UI Components/FPushButton");
                @SuppressWarnings("unchecked")
                List<FlaRecord> movies = (List<FlaRecord>) descriptor.get("legacyMovies");
                assertEquals(movies.get(0).get("name"), "custom.swf");
                assertEquals(((BinaryData) movies.get(0).get("data")).toByteArray(), new byte[]{67, 87, 83, 6});
                assertEquals(movies.get(1).get("name"), "button_live.swf");
                assertEquals(((BinaryData) movies.get(1).get("data")).toByteArray(), new byte[]{70, 87, 83, 6});
                assertEquals(((Number) entries(definition).get(0).get("revision")).intValue(), 1);
                assertEquals(((Number) record(entries(definition).get(0), "value").get("revision")).intValue(), 1);
            } else {
                assertEquals(component.get("componentEnabled"), 1L);
            }
        }
        snapshot.forEach((r, fields) -> assertEquals(r.getFields(), fields, "Source model mutated"));
    }

    @Test
    public void invalidInspectableAndMissingPreviewFailExplicitly() throws Exception {
        MemoryInput input = fixture();
        input.files.remove("bin/preview.swf");
        expectFailure(input, "Missing XFL resource");
        input = fixture();
        input.put("LIBRARY/button.xml", "<DOMComponentItem name='PushButton'><timeline><DOMTimeline><layers/></DOMTimeline></timeline><parametersAsXML><![CDATA[<property id='x'><Inspectable type='Unknown'/></property>]]></parametersAsXML></DOMComponentItem>");
        expectFailure(input, "Unsupported Inspectable");
    }

    @DataProvider(name = "f5InstanceParameters")
    public Object[][] f5InstanceParameters() {
        return new Object[][]{{true}, {false}};
    }

    @Test(dataProvider = "f5InstanceParameters")
    public void flash5UsesNativeTypedListsAndSeparatesDefaultsFromInstanceValues(boolean explicit) throws Exception {
        MemoryInput input = fixture();
        if (!explicit) {
            input.put("DOMDocument.xml", new String(input.files.get("DOMDocument.xml"), java.nio.charset.StandardCharsets.UTF_8)
                    .replace(parameters("Hello"), ""));
        }
        BinaryFlaDocument source = new XflReader().read(input);
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.F5).convert(source));
        FlaRecord descriptor = output.getLibraryDescriptors().get(0), instance = of(output, "CPicSprite").get(0);
        for (FlaRecord properties : Arrays.asList(record(descriptor, "properties"), record(instance, "legacyProperties"))) {
            assertEquals(((Number) properties.get("revision")).intValue(), 1);
            List<FlaRecord> parameters = entries(properties);
            assertEquals(parameters.size(), 4);
            for (FlaRecord parameter : parameters) {
                assertEquals(((Number) parameter.get("revision")).intValue(), 0);
                assertNull(parameter.get("variableName"));
                assertEquals(((Number) record(parameter, "value").get("revision")).intValue(), 1);
            }
            assertEquals(parameters.get(0).get("name"), "label", "F5 uses the variable name as its parameter name");
            assertEquals(entries(record(parameters.get(0), "value")).get(0).get("value"), "Push Button");
            assertEquals(entries(record(parameters.get(1), "value")).get(0).get("value"), "First,Item");
            assertEquals(entries(record(parameters.get(1), "value")).get(1).get("value"), "Second");
            assertEquals(entries(record(parameters.get(2), "value")).get(0).get("value"), "true");
            assertEquals(((Number) record(parameters.get(3), "value").get("selectedIndex")).intValue(), 1);
        }
        List<FlaRecord> overrides = entries(record(instance, "properties"));
        assertEquals(overrides.size(), explicit ? 4 : 0);
        if (explicit) {
            assertEquals(overrides.get(0).get("name"), "label");
            assertEquals(entries(record(overrides.get(0), "value")).get(0).get("value"), "Hello");
            assertEquals(((Number) overrides.get(0).get("revision")).intValue(), 0);
        }
        assertEquals(instance.get("legacyParametersAreLocked"), 1L);
        assertEquals(instance.get("legacyCustomUISourceFilename"), "custom.swf");
        assertEquals(instance.get("legacySymbolDescription"), "UI Components/FPushButton");
    }

    @Test
    public void nativeMxPrivateAnonymousParameterSurvives() throws Exception {
        MemoryInput input = input(timeline("<DOMSymbolInstance libraryItemName='Private'><parametersAsXML><![CDATA[<property id=''><Inspectable name='' variable='' type='Default' defaultValue=''/></property>]]></parametersAsXML></DOMSymbolInstance>")
                + "<symbols><Include href='private.xml'/></symbols>");
        input.put("LIBRARY/private.xml", "<DOMSymbolItem name='Private'><timeline><DOMTimeline><layers/></DOMTimeline></timeline></DOMSymbolItem>");
        BinaryFlaDocument output = read(new BinaryFlaConverter(FlaFormatVersion.MX).convert(new XflReader().read(input)));
        FlaRecord properties = record(of(output, "CPicSprite").get(0), "properties");
        assertEquals(entries(properties).size(), 1);
        assertEquals(entries(properties).get(0).get("variableName"), "");
        assertEquals(entries(record(entries(properties).get(0), "value")).get(0).get("value"), "");
    }
}
