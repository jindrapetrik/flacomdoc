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
 * Library descriptors, shared inline metadata and typed component values.
 */
final class BinaryLibraryWriter {

    final BinaryArchiveWriter w;
    final FlaFormatVersion t;

    BinaryLibraryWriter(BinaryArchiveWriter w) {
        this.w = w;
        t = w.target();
    }

    void page(FlaRecord r) throws IOException {
        int v = w.revision(r, "revision", t.documentPageRevision);
        w.streamName(w.s(r, "streamName"), v >= 21);
        w.string(r, "name");
        if (v >= 2) {
            w.u32(r, "libraryId");
        }
        if (v >= 3) {
            long type = w.n(r, "symbolType", 0);
            if (type == 2 && t.ordinal() <= FlaFormatVersion.F2.ordinal()) {
                w.session.issue("MOVIE_CLIP", w.path, r, "symbolType", "Movie clip library item saved as a graphic symbol.");
                type = 0;
            }
            w.u8(type);
        }
        if (v >= 6) {
            w.string(r, "legacyName");
        }
        if (v >= 7) {
            w.u32(r, "placeholder");
        }
        if (v >= 5) {
            metadata(w.record(r, "libraryMetadata"), t.symbolLibraryItemMetadataRevision);
        }
        if (v >= 8) {
            w.u32(r, "sourceTime");
        }
        if (v == 9) {
            List<FlaRecord> lists = w.records(r, "historicalActionLists");
            for (int i = 0; i < 4; i++) {
                new BinaryActionWriter(w).historical(i < lists.size() ? lists.get(i) : null, false);
            }
        }
        if (v >= 10) {
            w.strings(r, "legacyCustomUISourceFilename", "legacySymbolDescription");
            properties(w.record(r, "properties"));
        }
        if (v >= 11) {
            w.u32(r, "legacyParametersAreLocked");
        }
        if (v >= 12) {
            linkage(w.record(r, "linkage"), t.documentPageLinkageRevision);
        }
        if (v >= 14 && v <= 17) {
            w.string(r, "legacyComponentName");
        }
        if (v >= 15 && v <= 17) {
            List<FlaRecord> movies = w.records(r, "legacyMovies");
            for (int i = 0; i < 2; i++) {
                movie(i < movies.size() ? movies.get(i) : null);
            }
        }
        if (v >= 16 && v <= 17) {
            w.u32(r, "legacyDescriptionType");
        }
        if (v == 17) {
            w.u32(r, "legacyComponentEnabled");
            w.string(r, "legacyComponentTooltip");
            w.u32(r, "legacyComponentIconId");
        }
        if (v >= 18) {
            component(w.record(r, "component"));
        }
        if (v >= 20) {
            w.strings(r, "templateName", "templateVersion");
        }
        if (v >= 22) {
            w.u32(r, "lastUniqueIdentifier");
            w.string(r, "dataBindingXml");
        }
        if (v >= 23) {
            w.u32(r, "scaleGridEnabled");
            w.words(r, "scaleGridBounds", 4);
        }
        if (v >= 24) {
            w.u8(r, "hasValidCenterPoint");
            w.words(r, "transformCenterPointTwips", 2);
        }
        if (v >= 25) {
            w.u8(r, "isFlexComponent");
            w.u8(r, "isSpriteSubclass");
        }
    }

    void item(FlaRecord r) {
        long marker = w.n(r, "marker", 0);
        w.u32(marker);
        if (marker > 0) {
            w.dwords(r, "firstPart", "secondPart");
        }
    }

    void metadata(FlaRecord r, int maximum) throws IOException {
        int v = (int) Math.min(w.n(r, "revision", maximum), maximum);
        w.u32(v);
        if (v <= 3) {
            w.u32(r, "legacyParentId");
        } else {
            item(w.record(r, "parentItem"));
        }
        if (v > 1) {
            if (v <= 3) {
                w.u32(r, "legacyItemId");
            } else {
                item(w.record(r, "item"));
            }
        }
        if (v > 2) {
            w.u32(r, "attributes");
        }
        if (v >= 5) {
            linkage(w.record(r, "linkage"), t.libraryItemLinkageRevision);
        }
        if (v >= 6) {
            FlaRecord pd = w.record(r, "persistentData");
            w.u8(pd == null ? 0 : 1);
            if (pd != null) {
                persistent(pd);
            }
        }
    }

    void linkage(FlaRecord r, int maximum) {
        int v = w.revision(r, "revision", maximum);
        w.u32(r, "flags");
        w.strings(r, "identifier", "url");
        if (v >= 5) {
            w.string(r, "className");
        }
        if (v >= 1) {
            w.u8(r, "exportFlags");
        }
        if (v >= 2) {
            FlaRecord aux = w.record(r, "auxiliary");
            int av = (int) Math.min(w.n(aux, "revision", t.libraryItemLinkageAuxiliaryRevision), t.libraryItemLinkageAuxiliaryRevision);
            w.u32(av);
            w.strings(aux, "sourcePath", "sourceItemName");
            w.u32(aux, "sourceLastModified");
            if (av >= 1) {
                w.u32(aux, "sourceAutoUpdate");
            }
            item(w.record(aux, "item"));
        }
        if (v >= 6) {
            w.u8(r, "importType");
        }
        if (v >= 7) {
            w.string(r, "baseClass");
        }
    }

    void media(FlaRecord r, String kind) throws IOException {
        int baseMax = kind.equals("CMediaBits") ? t.bitmapMediaBaseRevision : kind.equals("CMediaSound") ? t.soundMediaBaseRevision : t.videoMediaBaseRevision;
        int v = w.revision(r, "mediaBaseRevision", baseMax);
        w.streamName(w.s(r, "streamName"), v >= 6);
        w.string(r, "name");
        w.u16(r, "mediaId");
        if (v >= 2) {
            w.string(r, "sourcePath");
        }
        if (v >= 3) {
            w.u32(r, "sourceTime");
        }
        if (v >= 4) {
            metadata(w.record(r, "libraryMetadata"), kind.equals("CMediaBits") ? t.bitmapLibraryItemMetadataRevision : kind.equals("CMediaSound") ? t.soundLibraryItemMetadataRevision : t.videoLibraryItemMetadataRevision);
        }
        if (v >= 5) {
            w.u32(r, "sourcePlatform");
        }
        if (kind.equals("CMediaBits")) {
            int rv = w.revision(r, "revision", t.mediaBitsRevision);
            if (rv < 2) {
                w.string(r, "legacySourcePath");
            }
            if (rv >= 1) {
                w.u8(r, "compressionMode");
                w.u8(w.n(r, "jpegQuality", 50));
            }
            if (rv >= 2) {
                w.u8(r, "allowSmoothing");
            }
            if (rv >= 3) {
                w.u32(r, "encodedByteCount");
            }
            if (rv >= 4) {
                w.u8(r, "useDeblocking");
            }
        } else if (kind.equals("CMediaSound")) {
            int rv = w.revision(r, "revision", t.mediaSoundRevision);
            if (rv == 2) {
                w.string(r, "legacySourcePath");
            }
            int format = (int) w.n(r, "formatFlags", 0);
            int original = (int) w.n(r, "revision", rv);
            if (original < 5) {
                format += 4;
            }
            if (rv < 5) {
                format -= 4;
            }
            w.u16(format);
            w.u32(r, "sampleCount");
            if (rv >= 4) {
                w.u16(r, "exportFormat");
                w.u16(r, "exportBits");
            }
            if (rv >= 6) {
                w.u32(r, "encodedByteCount");
                w.u8(r, "importedMP3QualityFlag");
            }
            if (rv >= 7) {
                w.u16(r, "secondaryFormatFlags");
                w.u32(r, "secondarySampleCount");
            }
            if (rv >= 9) {
                w.string(r, "deviceSoundHRef");
            }
            if (rv >= 10) {
                w.u32(r, "use8kSampleRateFlag");
            }
        }
    }

    void component(FlaRecord r) throws IOException {
        int v = w.revision(r, "revision", t.documentPageComponentMetadataRevision);
        w.u32(r, "componentEnabled");
        w.string(r, "tooltip");
        w.dwords(r, "customIconId", "parametersAreLocked", "descriptionType");
        w.string(r, "actionScriptClass");
        List<FlaRecord> movies = w.records(r, "movies");
        for (int i = 0; i < 3; i++) {
            movie(i < movies.size() ? movies.get(i) : null);
        }
        if (v >= 1) {
            w.string(r, "symbolDescription");
            properties(w.record(r, "properties"));
        }
        if (v >= 2) {
            w.string(r, "classPropertiesXml");
        }
        if (v >= 4) {
            List<FlaRecord> tasks = w.records(r, "tasks");
            w.u32(tasks.size());
            for (FlaRecord task : tasks) {
                w.strings(task, "description", "script");
            }
        }
        if (v >= 5) {
            FlaRecord media = w.record(r, "embeddedMedia");
            w.u32(media == null ? 0 : 1);
            if (media != null) {
                media(media, "CMediaBits");
                new BinaryMediaWriter(w.session).bitmapPayload(w, w.record(media, "payload"), media);
            }
        }
        if (v >= 6) {
            w.u32(r, "reserved");
        }
        if (v >= 7) {
            w.u32(r, "editFrameIndex");
            w.string(r, "requiredMinimumPlayerVersionName");
            w.dwords(r, "requiredMinimumPlayerVersion", "requiredMinimumASVersion");
        }
        if (v >= 8) {
            movie(w.record(r, "livePreview11Movie"));
        }
    }

    void movie(FlaRecord r) {
        int v = w.revision(r, "revision", 3);
        if (v >= 1) {
            w.string(r, "name");
            BinaryData d = w.data(r, "data");
            w.u32(d == null ? 0 : d.size());
            w.raw(d);
        }
        if (v >= 2) {
            w.u32(r, "reserved");
        }
    }

    void componentXml(FlaRecord r) {
        w.u8(w.n(r, "revision", 0));
        w.u32(r, "uniqueId");
        w.string(r, "xml");
    }

    void accessibility(FlaRecord r, boolean unicode) {
        int v = (int) Math.min(w.n(r, "revision", 0), t.accessibilityRevision);
        w.u32(v);
        if (v != 0) {
            w.u32(r, "flags");
            w.strings(r, "name", "description", "shortcut");
            if (unicode && v >= 2) {
                w.strings(r, "tabIndex", "reservedString");
            }
            w.dwords(r, "forceSimple", "autoLabeling");
        }
    }

    void persistent(FlaRecord r) {
        w.u32(r, "header");
        List<FlaRecord> entries = w.records(r, "entries");
        w.u32(entries.size());
        for (FlaRecord e : entries) {
            int type = (int) w.n(e, "type", 0);
            w.u8(type);
            w.string(e, "name");
            switch (type) {
                case 0:
                    w.u32(e, "value");
                    break;
                case 3:
                    w.f64(w.d(e, "value", 0));
                    break;
                case 5:
                    w.string(e, "value");
                    break;
                case 7:
                    BinaryData data = w.data(e, "data");
                    w.u32(data == null ? 0 : data.size());
                    w.raw(data);
                    break;
                default:
                    List<?> values = w.list(e, "values");
                    w.u32(values.size());
                    for (Object n : values) {
                        if (type == 1) {
                            w.u32(((Number) n).longValue());
                        } else {
                            w.f64(((Number) n).doubleValue());
                        }
                    }
            }
        }
    }

    void strings(FlaRecord r) {
        List<FlaRecord> entries = w.records(r, "entries");
        w.count(entries.size());
        for (FlaRecord e : entries) {
            w.strings(e, "key", "value");
        }
    }

    void objectMap(FlaRecord r) throws IOException {
        List<FlaRecord> entries = w.records(r, "entries");
        w.count(entries.size());
        for (FlaRecord e : entries) {
            w.string(e, "key");
            w.object(w.record(e, "value"));
        }
    }

    void colors(FlaRecord r) throws IOException {
        int maximum = t.ordinal() >= FlaFormatVersion.CS4.ordinal() ? 1 : 0;
        List<FlaRecord> colors = new ArrayList<>(w.supported(w.records(r, "colors")));
        if (maximum == 0) {
            flattenColors(r, colors);
        }
        w.count(colors.size());
        for (FlaRecord c : colors) {
            w.object(c);
        }
        int v = w.revision(r, "revision", maximum);
        if (v >= 1) {
            List<FlaRecord> lists = w.records(r, "lists");
            w.u8(lists.size());
            for (FlaRecord list : lists) {
                colors(list);
            }
        }
    }

    private void flattenColors(FlaRecord r, List<FlaRecord> colors) {
        List<FlaRecord> lists = w.records(r, "lists");
        if (!lists.isEmpty()) {
            w.issue("SWATCH_GROUPS", r, "lists", "Nested swatch groups flattened into the target palette.");
        }
        for (FlaRecord child : lists) {
            colors.addAll(w.supported(w.records(child, "colors")));
            flattenColors(child, colors);
        }
    }

    void color(FlaRecord r) {
        int v = w.revision(r, "revision", t.colorDefRevision);
        if (v < 2) {
            w.fixedData(r, "legacyBytes", 2);
            w.u32(r, "packedValue");
            w.u32(r, "legacyValue");
            w.u16(r, "legacyWord");
            w.words(r, "legacyMatrixWords", 6);
            List<?> values = w.list(r, "legacyWords");
            for (int i = 0; i < 4; i++) {
                w.u16(i < values.size() ? ((Number) values.get(i)).longValue() : 0);
            }
        } else {
            new BinaryShapeWriter(w).fill(r, v >= 4 ? 1 : 0, v);
        }
        if (v >= 1) {
            w.u16(w.n(r, "hue", 65535));
            w.u16(w.n(r, "saturation", 65535));
            w.u16(w.n(r, "brightness", 65535));
        }
    }

    void fonts(FlaRecord r) throws IOException {
        w.u32(r, "header");
        List<FlaRecord> entries = w.records(r, "entries");
        w.u32(entries.size());
        for (FlaRecord e : entries) {
            font(e);
        }
    }

    void font(FlaRecord e) throws IOException {
        int v = w.revision(e, "revision", t.fontItemRevision);
        w.string(e, "name");
        w.u16(e, "fontId");
        w.u32(e, "sourceTime");
        new BinaryTextWriter(w).style(w.record(e, "style"), t.fontItemTextStyleRevision);
        metadata(w.record(e, "libraryMetadata"), t.fontLibraryItemMetadataRevision);
        if (v >= 3) {
            w.u32(e, "fontFlags");
        }
    }

    void sharedLibraries(FlaRecord r) {
        w.u8(0);
        List<FlaRecord> entries = w.records(r, "entries");
        w.u32(entries.size());
        for (FlaRecord e : entries) {
            sharedLibrary(e);
        }
    }

    void sharedLibrary(FlaRecord r) {
        int v = w.revision(r, "revision", 2);
        w.string(r, "name");
        BinaryData d = w.data(r, "data");
        w.u32(d == null ? 0 : d.size());
        w.raw(d);
        if (v >= 1) {
            stringList(r, "names");
        }
        if (v == 1) {
            stringList(r, "legacyNames");
        }
    }

    void stringList(FlaRecord r, String field) {
        List<?> names = w.list(r, field);
        w.u32(names.size());
        for (Object name : names) {
            w.string(String.valueOf(name));
        }
    }

    void properties(FlaRecord r) {
        boolean upgrade = w.session.source.getContentsRevision() <= w.target().contentsRevision;
        int v = w.revision(r, "revision", upgrade ? 2 : 0);
        List<FlaRecord> entries = w.records(r, "entries");
        w.u32(entries.size());
        for (FlaRecord e : entries) {
            if (v == 0) {
                FlaRecord value = w.record(e, "value");
                w.u32(value == null ? w.n(e, "type", 0) : w.n(value, "type", 0));
                w.string(e, "name");
                if (value == null) {
                    w.string(e, "value");
                } else {
                    List<FlaRecord> values = w.records(value, "entries");
                    w.string(values.isEmpty() ? "" : w.s(values.get(0), "value"));
                    w.issue("COMPONENT_PROPERTY", e, "value", "Typed component value reduced to its first scalar value.");
                }
            } else {
                property(e);
            }
        }
        if (v >= 2) {
            w.u32(r, "parametersAreNew");
        }
    }

    private void property(FlaRecord r) {
        int v = w.revision(r, "revision", 3);
        w.string(r, "name");
        propertyValue(w.record(r, "value"));
        if (v >= 1) {
            w.string(r, "variableName");
            FlaRecord id = w.record(r, "identifier");
            w.u8(id, "revision");
            w.fixedData(id, "data", 16);
        }
        if (v >= 3) {
            w.string(r, "category");
            w.u32(r, "verbosity");
        }
    }

    private void propertyValue(FlaRecord r) {
        int v = w.revision(r, "revision", 8), type = (int) w.n(r, "type", 0);
        w.u16(type);
        if (v >= 8 && type == 17) {
            w.u16(r, "selectionWord");
        }
        List<FlaRecord> entries = w.records(r, "entries");
        w.u32(entries.size());
        w.u32(r, "selectedIndex");
        long named = w.n(r, "namedEntries", 0);
        if (v >= 1) {
            w.u32(named);
        }
        for (FlaRecord e : entries) {
            if (v >= 1 && named != 0) {
                w.string(e, "name");
            }
            w.string(e, "value");
            if (v >= 6) {
                w.u16(e, "type");
            }
        }
        if (type == 13 && v >= 4) {
            FlaRecord table = w.record(r, "table");
            w.strings(table, "collectionClass", "collectionItemClass");
            if (v >= 5) {
                w.string(table, "firstColumnName");
            }
            List<FlaRecord> columns = w.records(table, "columns"), rows = w.records(table, "rows");
            w.u32(columns.size());
            for (FlaRecord c : columns) {
                w.string(c, "name");
                w.u16(c, "type");
                w.strings(c, "defaultValue", "enumeration");
            }
            w.u32(rows.size());
            for (FlaRecord row : rows) {
                List<?> cells = w.list(row, "cells");
                for (int i = 0; i < columns.size(); i++) {
                    w.string(i < cells.size() ? String.valueOf(cells.get(i)) : "");
                }
            }
        }
        if (v >= 8) {
            BinaryData data = w.data(r, "data");
            w.u32(data == null ? 0 : data.size());
            w.raw(data);
        }
    }

    void timelineEffect(FlaRecord r, int symbolRevision) {
        w.strings(r, "effectName", "groupName", "symbolType");
        if (symbolRevision >= 18) {
            w.string(r, "originalLayerName");
        }
        if (symbolRevision >= 19) {
            w.string(r, "effectId");
        }
        w.string(r, "sourceScriptFilename");
        List<FlaRecord> parameters = w.records(r, "parameters");
        w.u32(parameters.size());
        for (FlaRecord p : parameters) {
            w.string(p, "name");
            int type = (int) w.n(p, "type", 0);
            w.u32(type);
            switch (type) {
                case 0:
                case 3:
                case 4:
                    w.u32(p, "value");
                    break;
                case 1:
                case 6:
                    w.f64(w.d(p, "value", 0));
                    break;
                case 2:
                    w.string(p, "value");
                    break;
                case 5:
                    List<?> values = w.list(p, "values");
                    w.u32(values.size());
                    for (Object value : values) {
                        w.string(String.valueOf(value));
                    }
                    w.u32(p, "selectedIndex");
                    break;
                default:
                    throw new IllegalArgumentException("Invalid effect parameter type: " + type);
            }
            w.dwords(p, "minimum", "maximum", "maximumLength");
            w.string(p, "variableName");
            w.u32(p, "displayOrder");
        }
    }

    void filters(FlaRecord owner) {
        FlaRecord filters = w.record(owner, "filters");
        List<FlaRecord> entries = w.records(filters, "entries");
        w.value(owner, "filtersPresent");
        w.u8(entries.isEmpty() ? 0 : 1);
        if (!entries.isEmpty()) {
            w.u32(entries.size());
            for (FlaRecord filter : entries) {
                new BinaryFilterWriter(w).filter(filter);
            }
        }
    }

    void swf(FlaRecord r, String kind) throws IOException {
        if (kind.equals("CPicSwf")) {
            new BinaryObjectWriter(w).base(r, t.picSymbolBaseRevision);
            int v = w.revision(r, "revision", t.ordinal() >= FlaFormatVersion.CS4.ordinal() ? 7 : 6);
            w.words(r, "matrixWords", 6, true);
            boolean present = w.n(r, "mediaPresent", 0) != 0;
            w.u32(present ? 1 : 0);
            if (present) {
                w.u16(r, "mediaId");
            }
            if (v >= 2) {
                new BinaryActionWriter(w).source(w.record(r, "actions"), t.symbolActionScriptRevision);
            }
            if (v >= 3) {
                w.string(r, "instanceName");
            }
            if (v >= 4) {
                properties(w.record(r, "properties"));
            }
            if (v >= 5) {
                accessibility(w.record(r, "accessibility"), true);
            }
            if (v >= 6) {
                componentXml(w.record(r, "componentXml"));
            }
            if (v >= 7) {
                w.u8(r, "reservedByte");
            }
        } else {
            media(r, "CMediaVideoStream");
            component(w.record(r, "component"));
            int v = w.revision(r, "revision", t.ordinal() >= FlaFormatVersion.CS4.ordinal() ? 5 : 4);
            if (v >= 1) {
                w.string(r, "sourceName");
            }
            if (v >= 2) {
                w.words(r, "boundsWords", 4);
            }
            if (v >= 3) {
                swfMetadata(w.record(r, "metadata"));
            }
            if (v >= 4) {
                w.dwords(r, "legacyValue", "playerVersion", "actionScriptVersion");
            }
            if (v >= 5) {
                FlaRecord lib = w.record(r, "sharedLibrary");
                w.u32(lib == null ? 0 : 1);
                if (lib == null) {
                    w.string(r, "exportName");
                } else {
                    sharedLibrary(lib);
                }
            }
        }
    }

    private void swfMetadata(FlaRecord r) {
        int v = w.revision(r, "revision", 4);
        w.strings(r, "name", "implementation");
        w.u32(r, "version");
        if (v == 1) {
            w.u8(r, "isTopLevel");
        } else if (v >= 2) {
            w.u32(r, "isTopLevel");
        }
        if (v == 2) {
            swfEntries(r, "legacyChildren", false);
        } else if (v >= 3) {
            swfEntries(r, "assetMap", true);
            swfEntries(r, "includesList", false);
            swfEntries(r, "exportAfterList", false);
        }
        if (v >= 4) {
            w.strings(r, "intrinsicClass", "intrinsicScript");
        }
    }

    private void swfEntries(FlaRecord r, String field, boolean named) {
        List<FlaRecord> entries = w.records(r, field);
        w.u32(entries.size());
        for (FlaRecord e : entries) {
            if (named) {
                w.string(e, "key");
            }
            swfMetadata(w.record(e, "metadata"));
        }
    }
}
