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
package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.converter.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.*;
import org.w3c.dom.Element;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.reader.RecordTestSupport.*;

public class FlaLinkageWriterTest {

    @DataProvider(name = "linkageRevisions")
    public Object[][] linkageRevisions() {
        List<Object[]> cases = new ArrayList<>();
        for (int revision = 0; revision <= 7; revision++) {
            for (int auxiliary = 0; auxiliary <= (revision < 2 ? 0 : 3); auxiliary++) {
                cases.add(new Object[]{revision, auxiliary});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "linkageRevisions")
    public void importTypeAndAutoUpdateRetainTheirWidthsAndRevisionGates(int revision, int auxiliary) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 5); // Library metadata revision, two absent item IDs and attributes.
        u32(out, 0);
        u32(out, 0);
        u32(out, 0);
        out.write(revision);
        u32(out, 1);
        string(out, "id", false);
        string(out, "library.swf", false);
        if (revision >= 5) {
            string(out, "Class", true);
        }
        if (revision >= 1) {
            out.write(7);
        }
        if (revision >= 2) {
            u32(out, auxiliary);
            string(out, "source.fla", true);
            string(out, "Source item", false);
            u32(out, 0xFFFFFFFFL);
            if (auxiliary >= 1) {
                u32(out, 0x80000000L);
            }
            u32(out, 0); // Absent item identity.
        }
        if (revision >= 6) {
            out.write(255);
        }
        if (revision >= 7) {
            string(out, "Base", false);
        }
        u16(out, 0xBEEF);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord metadata = a.readInline("libraryMetadata", r -> {
        }, DefaultFlaDecoders::libraryMetadata);
        FlaRecord raw = (FlaRecord) metadata.get("linkage");
        FlaLinkage interpreted = metadata.getLinkage();
        assertEquals(raw.get("importType"), revision >= 6 ? 255 : null);
        assertEquals(interpreted.getImportType(), revision >= 6 ? Integer.valueOf(255) : null);
        assertNull(raw.get("extensionFlags"));
        if (revision >= 2) {
            FlaRecord aux = (FlaRecord) raw.get("auxiliary");
            assertEquals(aux.get("sourceAutoUpdate"), auxiliary >= 1 ? 0x80000000L : null);
            assertNull(aux.get("value2"));
            assertEquals(aux.get("sourceLastModified"), 0xFFFFFFFFL);
        }
        assertTrue(metadata.isComplete());
        assertEquals(a.readUI16(), 0xBEEF);
        assertEquals(a.getRemaining(), 0);
    }

    @DataProvider(name = "linkageModes")
    public Object[][] linkageModes() {
        List<Object[]> cases = new ArrayList<>();
        for (FlaFormatVersion v : new FlaFormatVersion[]{FlaFormatVersion.MX, FlaFormatVersion.MX2004, FlaFormatVersion.F8, FlaFormatVersion.CS3, FlaFormatVersion.CS4}) {
            cases.add(new Object[]{v, false, false, false, 0});
            cases.add(new Object[]{v, true, false, false, 1});
            cases.add(new Object[]{v, false, true, false, 1});
            cases.add(new Object[]{v, true, true, false, 1});
            cases.add(new Object[]{v, false, false, true, 2});
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "linkageModes")
    public void writerProducesAnExclusiveModeAndIndependentExportBits(FlaFormatVersion version, boolean as, boolean runtime, boolean imported, int mode) throws Exception {
        Element e = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("DOMSymbolItem");
        e.setAttribute("linkageExportForAS", Boolean.toString(as));
        e.setAttribute("linkageExportForRS", Boolean.toString(runtime));
        e.setAttribute("linkageImportForRS", Boolean.toString(imported));
        e.setAttribute("linkageIdentifier", "symbol");
        e.setAttribute("linkageURL", "library.swf");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, 5);
        u32(out, 0);
        u32(out, 0);
        Method m = LegacyFlaConverter.class.getDeclaredMethod("writeAsLinkage", FlaWriter.class, Element.class);
        m.setAccessible(true);
        m.invoke(new LegacyFlaConverter(version, "windows-1250"), new FlaWriter(out, version, "windows-1250"), e);
        MfcArchive a = archive(out.toByteArray());
        FlaRecord r = a.readInline(SemanticTestSupport.record("owner"), "libraryMetadata", DefaultFlaDecoders::libraryMetadata);
        assertEquals(a.getPosition(), out.size());
        FlaLinkage l = r.getLinkage();
        assertNotNull(l);
        assertEquals(l.getModeId(), (long) mode);
        assertEquals(l.isImportForRuntimeSharing(), imported);
        assertEquals(l.isExportForRuntimeSharing(), Boolean.valueOf(runtime));
        assertEquals(l.isExportForActionScript(), Boolean.valueOf(as));
        assertEquals(l.getIdentifier(), "symbol");
        assertEquals(l.getUrl(), "library.swf");
    }
}
