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
import java.io.IOException;
import java.util.*;

/**
 * Complete version-gated Contents grammar, independent of page archives.
 */
final class BinaryContentsWriter {

    private final BinaryArchiveWriter w;
    private final BinaryLibraryWriter lib;

    BinaryContentsWriter(BinaryArchiveWriter w) {
        this.w = w;
        lib = new BinaryLibraryWriter(w);
    }

    void write(FlaRecord r) throws IOException {
        int v = w.target().contentsRevision;
        w.value(r, "revision");
        w.u8(v);
        if (v >= 0x20) {
            w.u32(w.target().contentsCompatibilityMarker);
            w.value(r, "compatibilityMarker");
        }
        List<FlaRecord> ole = w.records(r, "oleItems");
        w.u32(0);
        if (!ole.isEmpty()) {
            w.issue("OLE_OMITTED", r, "oleItems", "Embedded OLE objects are unavailable in the converted document.");
        }
        if (v >= 0x14) {
            w.u8(r, "reserved1");
        }
        if (v >= 0x15) {
            w.u8(r, "reserved2");
        }
        for (int gate : new int[]{0x21, 0x2B, 0x3B, 0x40, 0x44}) {
            if (v >= gate) {
                w.fixedData(r, "reservedAt" + gate, 4);
            }
        }
        w.objects(r, "scenes");
        w.u16(w.n(r, "nextSceneId", w.session.source.getSceneDescriptors().size() + 1));
        w.u16(w.n(r, "nextItemTag", 1));
        w.object(w.record(r, "selectedScene"));
        if (v >= 0x0E) {
            w.objects(r, "library");
            w.u16(w.n(r, "libraryCounter", 1));
        }
        if (v >= 0x11) {
            w.objects(r, "media");
            w.u16(w.n(r, "mediaCounter", 1));
            w.u16(r, "rulerUnitType");
        }
        w.u8(r, "gridDisplayMode");
        w.words(r, "boundsWords", 4);
        w.u32(w.n(r, "gridSpacingXFixed16Twips", 360L * 65536));
        w.fixedData(r, "previewAndRulerFlags", 3);
        if (v >= 0x0F) {
            w.fixedData(r, "viewPlayAndPrinterFlags", 2);
        }
        w.dwords(r, "printMarginLeftTwips", "printMarginRightTwips", "printMarginTopTwips", "printMarginBottomTwips");
        w.u8(r, "printCenterHorizontal");
        w.u8(r, "printCenterVertical");
        w.u8(r, "printLayout");
        w.u8(r, "printDisablePostscript");
        w.u32(w.n(r, "printScaleFixed16", 65536));
        for (String f : Arrays.asList("printerModeData", "printerNamesData")) {
            BinaryData d = w.data(r, f);
            int n = d == null ? 0 : Math.min(65535, d.size());
            w.u16(n);
            if (d != null) {
                w.raw(d.slice(0, n));
            }
        }
        if (v >= 0x0B) {
            w.u32(w.n(r, "backgroundColorRgba", 0xFFFFFFFFL));
            w.u32(w.n(r, "gridColorRgba", 0xFFCCCCCCL));
        }
        if (v >= 0x0D) {
            w.u32(w.n(r, "frameRateWord", 24L * 65536));
        }
        if (v >= 0x10) {
            w.u8(r, "printAllFrames");
            w.u8(r, "printLabelPages");
            w.u8(w.n(r, "printFrameCountAcrossPage", 3));
            w.u32(w.n(r, "printStoryboardMarginTwips", 180));
        }
        FlaRecord publish = w.record(r, "publishSettings");
        int profiles = 0;
        if (v >= 0x13) {
            profiles = publish(publish, v);
        }
        if (v >= 0x15) {
            lib.strings(w.record(r, "extensionProperties"));
        }
        if (v >= 0x16) {
            lib.colors(w.record(r, "swatches"));
            lib.colors(w.record(r, "extendedSwatches"));
        }
        if (v >= 0x18) {
            FlaRecord view = w.record(r, "libraryView");
            int rv = (int) w.n(view, "revision", 3);
            w.u32(rv);
            w.words(view, "words", 7);
            if (rv >= 2) {
                w.dwords(view, "value1", "value2");
            }
            if (rv >= 3) {
                w.u32(view, "value3");
            }
        }
        if (v >= 0x19) {
            folders(w.record(r, "libraryFolders"));
        }
        if (v >= 0x1A) {
            if (v >= 0x2D) {
                lib.objectMap(w.record(r, "documentObjects"));
            }
            List<FlaRecord> source = w.records(r, "profileObjects");
            for (int i = 0; i < (v < 0x2C ? 1 : profiles); i++) {
                lib.objectMap(i < source.size() ? source.get(i) : null);
            }
        }
        if (v >= 0x1B) {
            w.u16(w.n(r, "nextSymbolIdentifier", 1));
        }
        if (v >= 0x1C) {
            w.u32(w.n(r, "guidesColorRgba", 0xFF00FF00L));
            w.u8(w.n(r, "guidesVisible", 1));
            w.u8(r, "guidesLocked");
            w.u8(r, "guidesSnapTo");
        }
        if (v >= 0x1D) {
            lib.fonts(w.record(r, "fontLibrary"));
        }
        if (v >= 0x1E) {
            w.string(r, "sharedLibraryUrl");
        }
        if (v >= 0x1F) {
            w.u8(r, "gridSnapTo");
            w.u8(r, "objectsSnapTo");
            w.dwords(r, "gridSnapAccuracy", "guidesSnapAccuracy");
            w.u32(w.n(r, "gridSpacingYFixed16Twips", w.n(r, "gridSpacingXFixed16Twips", 360L * 65536)));
        }
        if (v >= 0x22) {
            w.strings(r, "templateDescription", "templateFilename");
        }
        if (v >= 0x23) {
            w.u32(r, "previewFrame");
        }
        if (v >= 0x24) {
            w.dwords(r, "pixelGrid", "pixelSnap");
        }
        if (v >= 0x26) {
            lib.accessibility(w.record(r, "accessibility"), v >= 0x2F);
        }
        if (v >= 0x27) {
            w.u32(w.n(r, "nextMediaIdentifier", 1));
        }
        if (v >= 0x2E) {
            w.u8(r, "snapAlign");
            w.u8(r, "snapAlignVerticalCenter");
            w.u8(r, "snapAlignHorizontalCenter");
            w.u8(r, "snapAlignBorder");
            w.dwords(r, "snapAlignHorizontalSpacing", "snapAlignVerticalSpacing", "snapAlignBorderSpacing");
        }
        if (v >= 0x30) {
            w.u8(r, "screenOutlineFlags");
        }
        if (v >= 0x32) {
            FlaRecord pd = w.record(r, "persistentData");
            w.u8(pd == null ? 0 : 1);
            if (pd != null) {
                lib.persistent(pd);
            }
        }
        if (v >= 0x33) {
            w.u32(r, "tabOrderMode");
        }
        if (v >= 0x34) {
            w.u32(r, "documentType");
        }
        if (v >= 0x35) {
            FlaRecord lang = w.record(r, "languageSettings");
            w.u32(lang == null ? 0 : 1);
            if (lang != null) {
                List<?> values = w.list(lang, "availableLanguages");
                w.u32(values.size());
                w.string(lang, "languageUrl");
                w.u32(lang, "languageAutoDetect");
                w.string(lang, "languageDefault");
                for (Object value : values) {
                    w.string(String.valueOf(value));
                }
            }
        }
        if (v >= 0x36) {
            String lang = w.s(r, "languageForStage");
            w.u32(lang.isEmpty() ? 0 : 1);
            if (!lang.isEmpty()) {
                w.string(lang);
            }
        }
        if (v >= 0x38) {
            w.u32(r, "exportComponentSchemasToSwf");
        }
        if (v >= 0x3A) {
            w.string(r, "metadataXml");
        }
        if (v >= 0x3C) {
            w.string(r, "mobileSettingsXml");
        }
        if (v >= 0x3D) {
            w.u32(r, "languageStringReplaceMethod");
        }
        if (v >= 0x3E) {
            FlaRecord post = w.record(r, "postProcessor");
            w.u32(post == null ? 0 : 1);
            if (post != null) {
                w.dwords(post, "guidData1");
                w.u16(post, "guidData2");
                w.u16(post, "guidData3");
                w.fixedData(post, "guidData4", 8);
                w.string(post, "name");
                BinaryData d = w.data(post, "data");
                w.u32(d == null ? 0 : d.size());
                w.raw(d);
            }
        }
        if (v >= 0x3F) {
            w.u32(w.n(r, "timelineHeight", 300));
            w.u32(w.n(r, "timelineLabelWidth", -1));
        }
        if (v == 0x41) {
            w.u32(r, "discardedLegacyDword");
        }
        if (v >= 0x42) {
            lib.sharedLibraries(w.record(r, "sharedLibraries"));
        }
        if (v >= 0x43) {
            writerIdentity();
            w.value(r, "writerIdentity");
        }
        if (v >= 0x45) {
            w.f64(w.d(r, "viewAngle3D", 55));
        }
        if (v >= 0x46) {
            w.words(r, "vanishingPointTwips", 2);
        }
        if (v >= 0x47) {
            w.u32(w.n(r, "adjustViewAngle3D", 1));
        }
    }

    private int publish(FlaRecord r, int v) {
        int count = 0;
        if (v < 0x2C) {
            lib.strings(w.record(r, "legacyProperties"));
        } else {
            if (v >= 0x2D) {
                lib.strings(w.record(r, "documentProperties"));
            }
            List<FlaRecord> profiles = w.records(r, "profiles");
            count = profiles.size();
            w.u32(count);
            for (FlaRecord p : profiles) {
                lib.strings(p);
            }
        }
        w.u32(r, "trailingValue1");
        w.strings(r, "trailingString1", "trailingString2");
        w.u32(r, "trailingValue2");
        return count;
    }

    private void folders(FlaRecord r) throws IOException {
        w.u32(r, "header");
        List<FlaRecord> entries = w.records(r, "entries");
        w.u32(entries.size());
        for (FlaRecord f : entries) {
            int v = (int) Math.min(w.n(f, "revision", w.target().libraryFolderRevision), w.target().libraryFolderRevision);
            w.u32(v);
            w.string(f, "name");
            if (v == 2) {
                w.u32(f, "legacyItemId");
            }
            lib.metadata(w.record(f, "libraryMetadata"), w.target().folderLibraryItemMetadataRevision);
        }
    }

    private void writerIdentity() {
        // Historical target identity, rather than the identity of the source producer.
        int[] values = {w.target().referenceWriterMajorVersion, 0, w.target().referenceWriterBuild, 0, 0};
        for (int n : values) {
            w.u16(n);
        }
    }
}
