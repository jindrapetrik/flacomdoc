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

import com.jpexs.flash.fla.reader.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.testng.annotations.*;
import org.w3c.dom.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;
import static com.jpexs.flash.fla.exporter.XflSwatchExportTest.record;

public class XflTlfExportTest {

    static final String TEXT_NS = "http://ns.adobe.com/textLayout/2008";
    static final String ID = "12345678-1234-1234-1234-123456789abc";
    static final String MARKUP = "<tlfTextObject type='Paragraph' editPolicy='readWrite' columnGap='2.5' paddingLeft='1.25' borderWidth='0.5' maxChars='123' displayAsPassword='true' text='secret'><TextFlow xmlns='" + TEXT_NS + "'><p><span fontFamily='Arial'> A&amp;B </span><span>  </span><span>C</span></p></TextFlow></tlfTextObject>";

    static FlaRecord text() {
        return record("CPicTLFText", "flags", 10, "source", MARKUP, "xml1", "localization-key", "xml2", "model.value", "className", "fl.text.TLFTextField", "identifier", ID, "matrixWords", Arrays.asList(65536, 0, 0, 65536, 31, -42), "bounds", Arrays.asList(-20, 1000, -40, 500), "children", Collections.emptyList(), "childLinks", Collections.emptyList());
    }

    static XflExportResult export(FlaRecord text) throws Exception {
        BinaryFlaDocument source = nativeFile("cs4", "0002_shapes");
        FlaRecord frame = XflIkExportTest.find(source, "CPicFrame");
        frame.set("children", Collections.singletonList(text));
        return new BinaryFlaToXflExporter().export(source);
    }

    static Element tlf(XflExportResult result) throws Exception {
        assertFalse(has(result, "TLF_DATA"), result.getReport());
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(f.getValue().toByteArray());
            }
        }
        List<Element> found = elements(xml(result), "DOMTLFText");
        assertEquals(found.size(), 1);
        return found.get(0);
    }

    @Test
    public void markupIsTheInheritedSourceAndAuxiliaryStringsArePlainMetadata() throws Exception {
        FlaRecord text = text();
        FlaTlfText semantic = text.getTlfText();
        assertEquals(semantic.getMarkup(), MARKUP);
        assertEquals(semantic.getLocalizationStringId(), "localization-key");
        assertEquals(semantic.getVariableName(), "model.value");
        assertEquals(semantic.parseMarkup().get(0).getName(), "tlfTextObject");
        Element e = tlf(export(text));
        assertEquals(e.getAttribute("localizationStringID"), "localization-key");
        assertEquals(e.getAttribute("variableName"), "model.value");
        assertEquals(e.getAttribute("referenceID"), ID);
        assertEquals(e.getAttribute("left"), "-20");
        assertEquals(e.getAttribute("right"), "1000");
        assertEquals(e.getAttribute("top"), "-40");
        assertEquals(e.getAttribute("bottom"), "500");
        assertEquals(e.getAttribute("selected"), "true");
        assertEquals(e.getAttribute("locked"), "false");
        assertEquals(e.getAttribute("isInBackground"), "true");
        assertEquals(text.get("source"), MARKUP);
    }

    @Test
    public void mixedInlineWhitespaceSurvivesFinalDocumentSerialization() throws Exception {
        Element e = tlf(export(text()));
        NodeList flows = e.getElementsByTagNameNS(TEXT_NS, "TextFlow");
        assertEquals(flows.getLength(), 1);
        assertEquals(flows.item(0).getTextContent(), " A&B   C");
        Node p = flows.item(0).getFirstChild();
        assertEquals(p.getChildNodes().getLength(), 3);
        assertEquals(p.getFirstChild().getTextContent(), " A&B ");
        assertEquals(p.getFirstChild().getNextSibling().getTextContent(), "  ");
    }

    @Test
    public void correctSemanticFieldNamesTakePrecedenceOverLegacyAliases() throws Exception {
        FlaRecord text = text();
        text.set("localizationStringId", "new localization");
        text.set("variableName", "newVariable");
        Element e = tlf(export(text));
        assertEquals(e.getAttribute("localizationStringID"), "new localization");
        assertEquals(e.getAttribute("variableName"), "newVariable");
    }

    @Test
    public void inlineObjectsUseActualChildIdentityAndSignedMarkupAssociation() throws Exception {
        FlaRecord text = text();
        FlaRecord shape = inlineShape();
        shape.set("identifier", ID);
        text.set("children", Collections.singletonList(shape));
        text.set("childLinks", Collections.singletonList(record("childLink", "child", shape, "value", 0xffffffffL)));
        Element e = tlf(export(text));
        Element ref = elements(e, "MarkupElement").get(0);
        assertEquals(ref.getAttribute("referenceID"), ID);
        assertEquals(ref.getAttribute("markupChildID"), "-1");
        assertEquals(ref.getAttribute("referenceType"), "inline");
        Element member = (Element) elements(e, "members").get(0).getFirstChild();
        assertEquals(member.getAttribute("referenceID"), ID);
        assertEquals(shape.get("identifier"), ID);
    }

    @Test
    public void previousAndNextFlowConnectionsKeepNativeReferenceKinds() throws Exception {
        FlaRecord text = text();
        text.set("instanceRelations", record("instanceRelations", "entries", Arrays.asList(record("relation", "identifier", ID, "value", 0), record("relation", "identifier", ID, "value", 1))));
        Element e = tlf(export(text));
        List<Element> refs = elements(e, "MarkupElement");
        assertEquals(refs.size(), 2);
        assertEquals(refs.get(0).getAttribute("referenceType"), "previous");
        assertEquals(refs.get(1).getAttribute("referenceType"), "next");
    }

    @DataProvider(name = "bad")
    public Object[][] bad() {
        return new Object[][]{{"wrongRoot"}, {"wrongNamespace"}, {"entity"}, {"badXml"}, {"missingAssociation"}, {"duplicateAssociation"}};
    }

    @Test(dataProvider = "bad")
    public void malformedMarkupAndAssociationsDoNotLeavePartialTlfObjects(String kind) throws Exception {
        FlaRecord text = text();
        switch (kind) {
            case "wrongRoot":
                text.set("source", "<TextFlow xmlns='" + TEXT_NS + "'/>");
                break;
            case "wrongNamespace":
                text.set("source", "<tlfTextObject><TextFlow/></tlfTextObject>");
                break;
            case "entity":
                text.set("source", "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///missing-tlf-entity'>]><tlfTextObject>&e;</tlfTextObject>");
                break;
            case "badXml":
                text.set("source", "<tlfTextObject>");
                break;
            default:
                FlaRecord child = inlineShape();
                child.set("identifier", ID);
                text.set("children", Collections.singletonList(child));
                if (kind.equals("duplicateAssociation")) {
                    FlaRecord link = record("childLink", "child", child, "value", 1);
                    text.set("childLinks", Arrays.asList(link, link));
                }
                break;
        }
        XflExportResult result = export(text);
        assertTrue(has(result, "TLF_DATA"), result.getReport());
        assertTrue(elements(xml(result), "DOMTLFText").isEmpty());
        if (kind.equals("duplicateAssociation")) {
            assertTrue(result.getReport().contains("duplicate direct-child association"), result.getReport());
        }
        for (Map.Entry<String, BinaryData> f : result.getFiles().entrySet()) {
            if (f.getKey().endsWith(".xml")) {
                XflSchemaTestSupport.validate(f.getValue().toByteArray());
            }
        }
    }

    @Test
    public void bomAndXmlDeclarationAreAcceptedWithoutChangingText() throws Exception {
        FlaRecord text = text();
        text.set("source", "\ufeff<?xml version='1.0'?>" + MARKUP);
        assertEquals(tlf(export(text)).getElementsByTagNameNS(TEXT_NS, "TextFlow").item(0).getTextContent(), " A&B   C");
    }

    static FlaRecord inlineShape() throws Exception {
        FlaRecord frame = XflIkExportTest.find(nativeFile("cs4", "0002_shapes"), "CPicFrame");
        return record("CPicShape", "flags", 1, "matrixWords", frame.get("matrixWords"), "shapeData", frame.get("shapeData"));
    }

    @DataProvider(name = "flags")
    public Object[][] flags() {
        Object[][] out = new Object[8][];
        for (int i = 0; i < 8; i++) {
            out[i] = new Object[]{i};
        }
        return out;
    }

    @Test(dataProvider = "flags")
    public void nativeFlagBitsRemainIndependent(int flags) throws Exception {
        FlaRecord record = text();
        record.set("flags", flags);
        Element e = tlf(export(record));
        assertEquals(e.getAttribute("selected"), Boolean.toString((flags & 2) != 0));
        assertEquals(e.getAttribute("locked"), Boolean.toString((flags & 4) != 0));
        assertEquals(e.getAttribute("isInBackground"), Boolean.toString((flags & 8) != 0));
    }

    @Test
    public void prefixedContainerAndFlowNamespacesAreAccepted() throws Exception {
        FlaRecord record = text();
        record.set("source", "<x:tlfTextObject xmlns:x='http://ns.adobe.com/xfl/2008/' xmlns:t='" + TEXT_NS + "'><t:TextFlow><t:p><t:span> prefix </t:span></t:p></t:TextFlow></x:tlfTextObject>");
        assertEquals(tlf(export(record)).getElementsByTagNameNS(TEXT_NS, "span").item(0).getTextContent(), " prefix ");
    }

    @Test
    public void richFlowKeepsListsLinksInlineGraphicsAndFormatDictionaries() throws Exception {
        String source = "<tlfTextObject firstBaselineOffset='12.5'><TextFlow xmlns='" + TEXT_NS + "' version='2.0.0' blockProgression='rl' whiteSpaceCollapse='preserve' columnCount='inherit' paddingLeft='auto' lineBreak='toFit'>"
                + "<div><list listStyleType='decimal'><li><p paragraphStartIndent='1.5' textIndent='-2.25' direction='rtl' tabStops='s10 d20'>"
                + "<linkNormalFormat><TextLayoutFormat fontWeight='bold'/></linkNormalFormat>"
                + "<a href='https://example.invalid/' target='_blank'><span fontSize='12.5' lineHeight='125.5%' trackingLeft='-2.5%' baselineShift='superscript'>A<br/>B<tab/>C</span></a>"
                + "<g><tcy><span fontStyle='inherit'>12</span></tcy></g><img source='embedded' width='12.5' height='50%' float='left' customSource='-1'/>"
                + "</p><p/></li></list></div></TextFlow></tlfTextObject>";
        FlaRecord record = text();
        record.set("source", source);
        Element e = tlf(export(record));
        NodeList flow = e.getElementsByTagNameNS(TEXT_NS, "TextFlow");
        assertEquals(flow.getLength(), 1);
        Element image = (Element) e.getElementsByTagNameNS(TEXT_NS, "img").item(0);
        assertEquals(image.getAttribute("customSource"), "-1");
        assertEquals(image.getAttribute("width"), "12.5");
        assertEquals(e.getElementsByTagNameNS(TEXT_NS, "p").getLength(), 2);
        assertEquals(record.get("source"), source);
    }

    @Test
    public void linkedEmptyContainerHasAValidEmptyFlow() throws Exception {
        FlaRecord record = text();
        record.set("source", "<tlfTextObject><TextFlow xmlns='" + TEXT_NS + "'/></tlfTextObject>");
        assertEquals(tlf(export(record)).getElementsByTagNameNS(TEXT_NS, "TextFlow").getLength(), 1);
    }

    @DataProvider(name = "invalidSchema")
    public Object[][] invalidSchema() {
        return new Object[][]{{"<p><span fontSize='0'>x</span></p>"}, {"<p><span fontWeight='heavy'>x</span></p>"}, {"<list><span>x</span></list>"}, {"<p><unknown/></p>"}, {"<p><span textAlpha='2'>x</span></p>"}};
    }

    @Test(dataProvider = "invalidSchema")
    public void richSchemaStillRejectsInvalidKnownPropertiesAndHierarchy(String content) throws Exception {
        FlaRecord record = text();
        record.set("source", "<tlfTextObject><TextFlow xmlns='" + TEXT_NS + "'>" + content + "</TextFlow></tlfTextObject>");
        XflExportResult result = export(record);
        boolean rejected = false;
        for (Map.Entry<String, BinaryData> file : result.getFiles().entrySet()) {
            if (file.getKey().endsWith(".xml"))try {
                XflSchemaTestSupport.validate(file.getValue().toByteArray());
            } catch (org.xml.sax.SAXException ex) {
                rejected = true;
            }
        }
        assertTrue(rejected, "Known TLF property or hierarchy must remain validated: " + content);
    }
}
