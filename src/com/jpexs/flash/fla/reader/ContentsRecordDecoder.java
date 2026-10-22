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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Version-selected document settings after the core Contents prefix.
 */
final class ContentsRecordDecoder {

    private ContentsRecordDecoder() {
    }

    static void extensions(MfcArchive a, FlaRecord r, int version) throws IOException {
        settings(a, r, version);
        tail(a, r, version);
    }

    static void settings(MfcArchive a, FlaRecord r, int version) throws IOException {
        FlaRecord publish = null;
        if (version >= 0x13) {
            publish = a.readInline(r, "publishSettings", (in, settings) -> publish(in, settings, version));
        }
        if (version >= 0x15) {
            a.readInline(r, "extensionProperties", ContentsRecordDecoder::strings);
        }
        if (version >= 0x16) {
            a.readInline(r, "swatches", ColorRecordDecoder::list);
            a.readInline(r, "extendedSwatches", ColorRecordDecoder::list);
        }
        if (version >= 0x18) {
            a.readInline(r, "libraryView", ContentsRecordDecoder::libraryView);
        }
        if (version >= 0x19) {
            a.readInline(r, "libraryFolders", ContentsRecordDecoder::folders);
        }
        if (version >= 0x1A) {
            if (version >= 0x2D) {
                a.readInline(r, "documentObjects", ContentsRecordDecoder::objects);
            }
            int count = version < 0x2C ? 1 : ((Number) publish.get("profileCount")).intValue();
            List<FlaRecord> profiles = new ArrayList<>();
            r.set("profileObjects", Collections.unmodifiableList(profiles));
            for (int i = 0; i < count; i++) {
                a.readInline("profileObjects", profiles::add, ContentsRecordDecoder::objects);
            }
        }
        if (version >= 0x1B) {
            r.set("nextSymbolIdentifier", a.readUI16());
        }
        if (version >= 0x1C) {
            r.set("guidesColorRgba", a.readUI32());
            r.set("guidesVisible", a.readUI8());
            r.set("guidesLocked", a.readUI8());
            r.set("guidesSnapTo", a.readUI8());
        }
    }

    static void tail(MfcArchive a, FlaRecord r, int version) throws IOException {
        if (version >= 0x1D) {
            a.readInline(r, "fontLibrary", DocumentTailRecordDecoder::fonts);
        }
        if (version >= 0x1E) {
            r.set("sharedLibraryUrl", a.readString());
        }
        if (version >= 0x1F) {
            r.set("gridSnapTo", a.readUI8());
            r.set("objectsSnapTo", a.readUI8());
            r.set("gridSnapAccuracy", a.readUI32());
            r.set("guidesSnapAccuracy", a.readUI32());
            r.set("gridSpacingYFixed16Twips", a.readUI32());
        }
        if (version >= 0x22) {
            r.set("templateDescription", a.readString());
            r.set("templateFilename", a.readString());
        }
        if (version >= 0x23) {
            r.set("previewFrame", a.readUI32());
        }
        if (version >= 0x24) {
            r.set("pixelGrid", a.readUI32());
            r.set("pixelSnap", a.readUI32());
        }
        if (version >= 0x26) {
            a.readInline(r, "accessibility", (in, access) -> InstanceRecordDecoder.accessibility(in, access, version >= 0x2F));
        }
        if (version >= 0x27) {
            r.set("nextMediaIdentifier", a.readUI32());
        }
        if (version >= 0x2E) {
            r.set("snapAlign", a.readUI8());
            r.set("snapAlignVerticalCenter", a.readUI8());
            r.set("snapAlignHorizontalCenter", a.readUI8());
            r.set("snapAlignBorder", a.readUI8());
            r.set("snapAlignHorizontalSpacing", a.readUI32());
            r.set("snapAlignVerticalSpacing", a.readUI32());
            r.set("snapAlignBorderSpacing", a.readUI32());
        }
        if (version >= 0x30) {
            r.set("screenOutlineFlags", a.readUI8()); //1 = screenOutline.singleRoot            
        }
        if (version >= 0x32) {
            int present = a.readUI8();
            r.set("persistentDataPresent", present);
            if (present != 0) {
                a.readInline(r, "persistentData", DocumentTailRecordDecoder::persistentData);
            }
        }
        if (version >= 0x33) {
            r.set("tabOrderMode", a.readUI32());
        }
        if (version >= 0x34) {
            r.set("documentType", a.readUI32());
            /*
            0   Regular document with timeline (timeline, original type)
            1   Flash Slide Presentation
            2   Flash Form Application
            3   Flash document for ActionScript 2
            4   Flash document for ActionScript 3
            5   AIR for desktop
            6   Flash Lite
            7   AIR for iOS
            8   AIR for Android
             */
        }
        if (version >= 0x35) {
            long present = a.readUI32();
            r.set("languageSettingsPresent", present);
            if (present != 0) {
                a.readInline(r, "languageSettings", ContentsRecordDecoder::languageSettings);
            }
        }
        if (version >= 0x36) {
            long present = a.readUI32();
            r.set("languageForStagePresent", present);
            if (present == 1) {
                r.set("languageForStage", a.readString());
            }
        }
        if (version >= 0x38) {
            r.set("exportComponentSchemasToSwf", a.readUI32());
        }
        if (version >= 0x3A) {
            r.set("metadataXml", a.readString());
        }
        if (version >= 0x3C) {
            r.set("mobileSettingsXml", a.readString());
        }
        if (version >= 0x3D) {
            r.set("languageStringReplaceMethod", a.readUI32());
        }
        if (version >= 0x3E) {
            long present = a.readUI32();
            r.set("postProcessorPresent", present);
            if (present != 0) {
                a.readInline(r, "postProcessor", ContentsRecordDecoder::postProcessor);
            }
        }
        if (version >= 0x3F) {
            r.set("timelineHeight", a.readUI32());
            // Public binary FLA versions store a signed pixel count, not IEEE-754.
            r.set("timelineLabelWidth", version <= 0x47 ? (Number) a.readI32() : a.readFloat());
        }
        if (version == 0x41) {
            r.set("discardedLegacyDword", a.readUI32());
        }
        if (version >= 0x42) {
            a.readInline(r, "sharedLibraries", DocumentTailRecordDecoder::sharedLibraries);
        }
        if (version >= 0x43) {
            a.readInline(r, "writerIdentity", ContentsRecordDecoder::writerIdentity);
        }
        if (version >= 0x45) {
            r.set("viewAngle3D", a.readDouble());
        }
        if (version >= 0x46) {
            r.set("vanishingPointTwips", DefaultFlaDecoders.integers(a, 2));
        }
        if (version >= 0x47) {
            r.set("adjustViewAngle3D", a.readUI32());
        }
    }

    private static void writerIdentity(MfcArchive a, FlaRecord identity) throws IOException {
        List<Integer> words = new ArrayList<>();
        identity.set("words", Collections.unmodifiableList(words));
        for (int i = 0; i < 4; i++) {
            words.add(a.readUI16());
        }
        int count = a.readUI16();
        identity.set("byteCount", count);
        // Larger counts do not consume a payload in the native loader.
        if (count <= 256) {
            identity.set("data", a.readData(count));
        }
    }

    /**
     * Later writers restore their current save target before emitting these
     * records, even when Contents declares an older format revision.
     */
    static void compatibilityTail(MfcArchive a, FlaRecord r, boolean sharedLibraries) throws IOException {
        if (sharedLibraries) {
            a.readInline(r, "sharedLibraries", DocumentTailRecordDecoder::sharedLibraries);
        }
        a.readInline(r, "writerIdentity", ContentsRecordDecoder::writerIdentity);
    }

    private static void languageSettings(MfcArchive a, FlaRecord r) throws IOException {
        int count = a.readI32();
        r.set("availableLanguageCount", count);
        r.set("languageUrl", a.readString());
        r.set("languageAutoDetect", a.readUI32());
        r.set("languageDefault", a.readString());
        int size = a.checkedCount(Math.max(0, Math.min(count, 101)), 1);
        List<String> values = new ArrayList<>();
        r.set("availableLanguages", Collections.unmodifiableList(values));
        for (int i = 0; i < size; i++) {
            values.add(a.readString());
        }
    }

    private static void postProcessor(MfcArchive a, FlaRecord r) throws IOException {
        r.set("guidData1", a.readUI32());
        r.set("guidData2", a.readUI16());
        r.set("guidData3", a.readUI16());
        r.set("guidData4", a.readData(8));
        r.set("name", a.readString());
        int count = a.readI32();
        r.set("byteCount", count);
        if (count > 0) {
            r.set("data", a.readData(count));
        }
    }

    static void publish(MfcArchive a, FlaRecord r, int version) throws IOException {
        if (version < 0x2C) {
            a.readInline(r, "legacyProperties", ContentsRecordDecoder::strings);
        } else {
            if (version >= 0x2D) {
                a.readInline(r, "documentProperties", ContentsRecordDecoder::strings);
            }
            int count = a.checkedCount(a.readUI32(), 2);
            r.set("profileCount", count);
            List<FlaRecord> profiles = new ArrayList<>();
            r.set("profiles", Collections.unmodifiableList(profiles));
            for (int i = 0; i < count; i++) {
                a.readInline("publishProfile", profiles::add, ContentsRecordDecoder::strings);
            }
        }
        r.set("trailingValue1", a.readUI32());
        r.set("trailingString1", a.readString());
        r.set("trailingString2", a.readString());
        r.set("trailingValue2", a.readUI32());
    }

    static void strings(MfcArchive a, FlaRecord r) throws IOException {
        int count = a.readCount();
        r.set("count", count);
        a.checkedCount(count, 2);
        List<FlaRecord> entries = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(entries));
        for (int i = 0; i < count; i++) {
            a.readInline("stringEntry", entries::add, (in, entry) -> {
                entry.set("key", in.readString());
                entry.set("value", in.readString());
            });
        }
    }

    static void objects(MfcArchive a, FlaRecord r) throws IOException {
        int count = a.readCount();
        r.set("count", count);
        a.checkedCount(count, 3);
        List<FlaRecord> entries = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(entries));
        for (int i = 0; i < count; i++) {
            a.readInline("objectEntry", entries::add, (in, entry) -> {
                entry.set("key", in.readString());
                in.readObjectInto(entry, "value");
            });
        }
    }

    static void folders(MfcArchive a, FlaRecord r) throws IOException {
        r.set("header", a.readUI32());
        long count = a.readUI32();
        r.set("count", count);
        int size = a.checkedCount(count, 13);
        List<FlaRecord> folders = new ArrayList<>();
        r.set("entries", Collections.unmodifiableList(folders));
        for (int i = 0; i < size; i++) {
            a.readInline("libraryFolder", folders::add, (in, folder) -> {
                long revision = in.readUI32();
                folder.set("revision", revision);
                if (revision > 4) {
                    throw new UnsupportedFlaRecordException("Library-folder revision " + revision);
                }
                folder.set("name", in.readString());
                if (revision == 2) {
                    folder.set("legacyItemId", in.readUI32());
                }
                in.readInline(folder, "libraryMetadata", DefaultFlaDecoders::libraryMetadata);
            });
        }
    }

    static void libraryView(MfcArchive a, FlaRecord r) throws IOException {
        long revision = a.readUI32();
        r.set("revision", revision);
        if (revision > 3) {
            throw new UnsupportedFlaRecordException("Library-view revision " + revision);
        }
        r.set("words", DefaultFlaDecoders.integers(a, 7));
        if (revision >= 2) {
            r.set("value1", a.readUI32());
            r.set("value2", a.readUI32());
        }
        if (revision >= 3) {
            r.set("value3", a.readUI32());
        }
    }
}
