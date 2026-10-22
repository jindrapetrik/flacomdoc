package com.jpexs.flash.fla.reader;

import java.io.IOException;
import java.util.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.SemanticTestSupport.*;

public class FlaContextSemanticsTest {

    @Test
    public void xmlFragmentsPreserveRootOrderingAttributesTextAndNamespaces() throws Exception {
        List<FlaXmlElement> xml = FlaXmlElement.parseFragment("<?xml version='1.0'?><state duration='9'/><nodes><node name='a'/></nodes><x:y xmlns:x='urn:test'>a&amp;b</x:y>");
        assertEquals(xml.size(), 3);
        assertEquals(xml.get(0).getNumber("duration"), Double.valueOf(9));
        assertEquals(xml.get(1).getFirstChild("node").getAttribute("name"), "a");
        assertEquals(xml.get(2).getName(), "x:y");
        assertEquals(xml.get(2).getText(), "a&b");
    }

    @DataProvider(name = "badXml")
    public Object[][] badXml() {
        StringBuilder nested = new StringBuilder();
        for (int i = 0; i < 130; i++) {
            nested.append("<a>");
        }
        for (int i = 0; i < 130; i++) {
            nested.append("</a>");
        }
        return new Object[][]{{"<!DOCTYPE a [<!ENTITY e SYSTEM 'file:///should-not-be-read'>]><a>&e;</a>"}, {"<!DOCTYPE a SYSTEM 'https://should-not-be-requested.invalid/'><a/>"}, {"<a>"}, {nested.toString()}};
    }

    @Test(dataProvider = "badXml", expectedExceptions = IOException.class)
    public void xmlErrorsNeverResolveExternalResources(String xml) throws Exception {
        FlaXmlElement.parseFragment(xml);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void xmlAttributeSnapshotsAreImmutable() throws Exception {
        FlaXmlElement.parseFragment("<a x='y'/>").get(0).getAttributes().put("x", "changed");
    }

    @Test
    public void tlfBindsTheActualDirectChildIdentityAndRetainsBothXmlDocuments() throws Exception {
        FlaRecord child = record("CPicShape", "identifier", "child-id");
        FlaRecord text = record("CPicTLFText", "xml1", "<TextFlow><p>hello</p></TextFlow>", "xml2", "<controller/>", "childLinks", Arrays.asList(record("childLink", "child", child, "value", 0xffffffffL)));
        FlaTlfText tlf = text.getTlfText();
        assertSame(tlf.getChildLinks().get(0).getChild(), child);
        assertEquals(tlf.getChildLinks().get(0).getIdentifier(), "child-id");
        assertEquals(tlf.getChildLinks().get(0).getValue(), Long.valueOf(0xffffffffL));
        assertEquals(tlf.parseFirstXml().get(0).getName(), "TextFlow");
        assertEquals(tlf.parseSecondXml().get(0).getName(), "controller");
        assertNull(record("CPicText").getTlfText());
    }

    @Test
    public void sharedCollectionsUseFirstNonemptyNameWithoutDroppingDuplicates() {
        FlaRecord first = record("sharedLibrary", "name", "library", "data", new BinaryData(new byte[]{1, 2}), "names", Arrays.asList("member", "member"));
        FlaRecord duplicate = record("sharedLibrary", "name", "library", "names", Arrays.asList("other"));
        FlaRecord empty = record("sharedLibrary", "name", "", "names", Collections.emptyList());
        FlaRecord second = record("sharedLibrary", "name", "Library", "names", Arrays.asList("member"));
        FlaSharedLibraries libs = FlaSharedLibraries.fromCollection(record("sharedLibraries", "entries", Arrays.asList(first, duplicate, empty, second)));
        assertEquals(libs.getEntries().size(), 4);
        assertEquals(libs.getIgnoredEntries().size(), 2);
        assertSame(libs.find("library").getSourceRecord(), first);
        assertEquals(libs.findMember("member").size(), 2);
        assertEquals(libs.find("library").getMembers().size(), 2);
        assertNull(libs.find("unknown"));
        FlaRecord clip = record("CSwfPage", "exportName", "library", "sharedLibrary", duplicate);
        assertSame(libs.findForClip(clip).getSourceRecord(), first);
        assertSame(FlaSharedLibraries.getEmbeddedLibrary(clip).getSourceRecord(), duplicate);
        first.set("name", "changed");
        assertEquals(libs.find("library").getName(), "library");
    }

    @Test
    public void nativeLibraryKeysStopAtNulWhileRawNamesArePreserved() {
        FlaSharedLibraries libs = FlaSharedLibraries.fromCollection(record("sharedLibraries", "entries", Arrays.asList(
                record("sharedLibrary", "name", "lib\0first", "names", Arrays.asList("member\0suffix")),
                record("sharedLibrary", "name", "lib\0second"), record("sharedLibrary", "name", "\0ignored"))));
        assertEquals(libs.getByName().size(), 1);
        assertEquals(libs.getIgnoredEntries().size(), 2);
        assertEquals(libs.find("lib").getName(), "lib\0first");
        assertEquals(libs.findMember("member").size(), 1);
        assertSame(libs.find("lib\0query"), libs.find("lib"));
        FlaPersistentData data = persistent(value("key\0first", 5, "first"), value("key\0last", 5, "last"));
        assertEquals(data.getString("key"), "last");
        assertEquals(data.getEntries().get(0).getName(), "key\0first");
    }

    @DataProvider(name = "linkage")
    public Object[][] linkage() {
        return new Object[][]{{0, 0, 7, false, false, false}, {1, 3, 7, true, true, true}, {2, 3, 7, false, false, false}, {3, 3, 7, false, false, false}, {1, 2, 0, false, false, true}, {1, 3, 0, false, false, false}, {1, 7, 0x87, true, true, true}};
    }

    @Test(dataProvider = "linkage")
    public void linkageIsAModeSelectorWithIndependentExportFlags(int mode, int revision, int flags, boolean as, boolean runtime, boolean first) {
        FlaLinkage l = record("linkage", "flags", (long) mode, "revision", revision, "exportFlags", flags).getLinkage();
        assertEquals(l.getModeId(), (long) mode);
        assertEquals(l.getMode(), mode == 3 ? FlaLinkage.Mode.UNKNOWN : FlaLinkage.Mode.values()[mode]);
        assertEquals(l.isImportForRuntimeSharing(), mode == 2);
        assertEquals(l.isExportForActionScript(), Boolean.valueOf(as));
        assertEquals(l.isExportForRuntimeSharing(), Boolean.valueOf(runtime));
        assertEquals(l.isExportInFirstFrame(), Boolean.valueOf(first));
        assertEquals(l.getStoredExportFlags(), Integer.valueOf(flags));
    }

    @Test
    public void partialLinkageDoesNotInventAnUnreadOptionByte() {
        FlaLinkage l = record("linkage", "flags", 1L, "revision", 2).getLinkage();
        assertNull(l.getStoredExportFlags());
        assertNull(l.getNativeExportFlags());
        assertNull(l.isExportInFirstFrame());
    }

    @Test
    public void historicalLinkageHasNoStoredByteButNativeFirstFrameFixupIsExplicit() {
        FlaLinkage l = record("linkage", "flags", 1L, "revision", 0).getLinkage();
        assertNull(l.getStoredExportFlags());
        assertEquals(l.getNativeExportFlags(), Integer.valueOf(4));
        assertTrue(l.isExportInFirstFrame());
        assertFalse(l.isExportForActionScript());
    }

    @Test
    public void matrix3DUsesColumnMajorCoefficientsAndTwipTranslations() {
        float[] raw = {0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 2, 0, 25, -40, 5, 1};
        FlaMatrix3D m = FlaMatrix3D.fromValues(raw);
        assertTrue(m.isAffine());
        assertEquals(m.transformAffinePixels(2, 3, 4), new double[]{-1.75, 0, 8.25});
        assertEquals(m.get(0, 1), -1f);
        assertEquals(m.transformHomogeneousTwips(40, 60, 80, 1), new double[]{-35, 0, 165, 1});
        raw[12] = 200;
        assertEquals(m.getTxPixels(), 1.25);
        m.getColumnMajorValues()[12] = 300;
        assertEquals(m.getTxPixels(), 1.25);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void projectiveMatrixNeedsExplicitHomogeneousHandling() {
        FlaMatrix3D.fromValues(1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1).transformAffinePixels(1, 2, 3);
    }

    @Test
    public void native3DValuesMatchXflWithoutFixedPointReinterpretation() throws Exception {
        int found = 0;
        for (FlaRecord r : records(nativeDocument("fla/cs4/0026_3d_rotations-cs4"))) {
            FlaMatrix3D m = r.getMatrix3D();
            if (m == null || m.getTxTwips() < 16000) {
                continue;
            }
            found++;
            assertEquals(m.getTxTwips(), 16296.634765625);
            assertEquals(m.getTxPixels(), 814.83173828125);
            assertEquals(m.get(0, 0), -0.999328553676605f);
            assertEquals(((List<?>) r.get("rotation3D")).get(2), Double.valueOf(62));
        }
        assertEquals(found, 1);
    }

    @DataProvider(name = "commands")
    public Object[][] commands() {
        List<Object[]> rows = new ArrayList<>();
        for (int id = 0; id <= 85; id++) {
            rows.add(new Object[]{id});
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(dataProvider = "commands")
    public void commandKindsRetainTheFactoryGapAndPairedDelimiters(int id) {
        FlaCommandKind k = record("compiledCommand", "type", (long) id).getCommandKind();
        if (id == 35) {
            assertNull(k);
            return;
        }
        assertNotNull(k);
        assertEquals(k.getId(), id);
        if (k.getClosingKind() != null) {
            assertSame(k.getClosingKind().getOpeningKind(), k);
        }
        if (k.getOpeningKind() != null) {
            assertSame(k.getOpeningKind().getClosingKind(), k);
        }
    }

    @Test
    public void doWhileConditionBelongsToItsClosingCommandAndNotTheOpeningMarker() {
        assertEquals(FlaCommandKind.fromId(42).getEditorName(), "DoWhile");
        assertEquals(FlaCommandKind.fromId(42).getClosingKind().getEditorName(), "EndDoWhile");
        assertTrue(FlaCommandKind.fromId(17).isBranchMarker());
        assertTrue(FlaCommandKind.fromId(56).isBranchMarker());
        assertFalse(FlaCommandKind.fromId(22).isBranchMarker());
        assertNull(FlaCommandKind.fromId(0xffffffffL));
        assertNull(record("other", "type", 1L).getCommandKind());
    }
}
