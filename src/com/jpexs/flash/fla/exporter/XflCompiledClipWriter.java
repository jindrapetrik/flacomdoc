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
import java.io.IOException;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Compiled library clips retain their SWF, class XML, icon and available SWC.
 */
final class XflCompiledClipWriter {

    private final BinaryFlaToXflExporter.Session session;
    private final Map<String, FlaSharedLibraries.Entry> libraries = new LinkedHashMap<>();

    XflCompiledClipWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    String item(Element parent, FlaRecord r, int ordinal) throws IOException {
        FlaRecord component = record(r, "component");
        List<FlaRecord> movies = records(component, "movies");
        if (movies.isEmpty() || !(movies.get(0).get("data") instanceof BinaryData)) {
            throw new IOException("Compiled clip has no primary SWF.");
        }
        BinaryData movie = (BinaryData) movies.get(0).get("data");
        byte[] signature = movie.slice(0, Math.min(8, movie.size())).toByteArray();
        if (signature.length < 8 || (signature[0] != 'F' && signature[0] != 'C' && signature[0] != 'Z') || signature[1] != 'W' || signature[2] != 'S') {
            throw new IOException("Compiled clip primary data is not an SWF stream.");
        }
        if (!r.isComplete() || component == null || !component.isComplete() || !movies.get(0).isComplete()) {
            throw new IOException("Incomplete compiled clip.");
        }
        FlaRecord raster = record(record(record(component, "embeddedMedia"), "payload"), "raster");
        byte[] icon = icon(raster);
        FlaBounds iconBounds = raster.getBounds();
        String name = session.unique(session.fullName(r), r);
        Element item = child(parent, "DOMCompiledClipItem");
        session.attribute(item, "name", name, r);
        session.itemId(item, r);
        session.mediaSourcePlatform(item, r);
        item.setAttribute("sourceLastImported", Long.toString(n(r, "sourceTime", 0)));
        String swf = "clip-" + ordinal + ".swf", image = "clip-icon-" + ordinal + ".dat";
        item.setAttribute("swfScmHRef", swf);
        session.attribute(item, "actionscriptClass", s(component, "actionScriptClass", s(r, "sourceName", "")), component);
        session.attribute(item, "rootSymbolLinkageID", s(r, "sourceName", ""), r);
        if (r.get("playerVersion") instanceof Number) {
            item.setAttribute("playerVersion", Long.toString(n(r, "playerVersion", 9)));
        }
        if (r.get("actionScriptVersion") instanceof Number) {
            item.setAttribute("actionscriptVersion", Long.toString(n(r, "actionScriptVersion", 3)));
        }
        FlaRecord libraryMetadata = record(r, "libraryMetadata");
        FlaLinkage linkage = libraryMetadata == null ? null : libraryMetadata.getLinkage();
        if (linkage != null) {
            if (linkage.isExportForActionScript() != null) {
                item.setAttribute("linkageExportForAS", linkage.isExportForActionScript().toString());
            }
            if (linkage.getClassName() != null) {
                session.attribute(item, "linkageClassName", linkage.getClassName(), r);
            }
            if (linkage.isImportForRuntimeSharing() || Boolean.TRUE.equals(linkage.isExportForRuntimeSharing())) {
                session.issue("COMPILED_CLIP_LINKAGE", r, "Compiled clip runtime-sharing extras need an application-level check.");
            }
        }
        Object bounds = r.get("boundsWords");
        if (bounds instanceof List && ((List<?>) bounds).size() == 4) {
            String[] attrs = {"movieRectRight", "movieRectLeft", "movieRectBottom", "movieRectTop"};
            for (int i = 0; i < 4; i++) {
                item.setAttribute(attrs[i], ((List<?>) bounds).get(i).toString());
            }
        }
        child(item, "classProperties").setTextContent(session.text(s(component, "classPropertiesXml", ""), component));
        writeIcon(item, raster, image);
        FlaRecord metadata = record(r, "metadata");
        swcItem(item, "SwcItem", metadata, s(r, "sourceName", ""),
                Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>()));
        FlaSharedLibraries.Entry shared = FlaSharedLibraries.getEmbeddedLibrary(r);
        FlaSharedLibraries collection = session.source.getSharedLibraries();
        if (collection == null) {
            collection = FlaSharedLibraries.fromCollection(record(record(session.source.getContents(), "compatibilityTail"), "sharedLibraries"));
        }
        if (shared == null && collection != null) {
            shared = collection.findForClip(r);
        }
        if (shared != null && shared.getData() != null && shared.getData().size() != 0) {
            String hash = key(shared.getName());
            if (!hash.isEmpty()) {
                libraries.putIfAbsent(hash, shared);
                session.attribute(item, "hashKey", hash, r);
            }
        } else {
            session.issue("COMPILED_CLIP_CACHE", r, "Primary SWF is retained; no bound SWC cache is available in this historical descriptor.");
        }
        session.files.put("bin/" + swf, movie);
        session.files.put("bin/" + image, new BinaryData(icon));
        // The native authoring application can build an 11-style preview from
        // the primary movie. An unarchived generated preview is not fabricated.
        session.issue("COMPILED_CLIP_PREVIEW", component, "Primary compiled movie and icon exported; generated live-preview movies are not reconstructed.");
        return name;
    }

    private boolean swcItem(Element parent, String element, FlaRecord metadata, String fallbackName, Set<FlaRecord> active) {
        if (metadata != null && (!metadata.isComplete() || !active.add(metadata))) {
            session.issue("COMPILED_CLIP_METADATA", metadata, "Incomplete or cyclic SWC dependency metadata omitted.");
            return false;
        }
        try {
            Element item = child(parent, element);
            session.attribute(item, "name", s(metadata, "name", fallbackName), metadata);
            item.setAttribute("version", Long.toString(n(metadata, "version", 0)));
            item.setAttribute("isTopLevel", Boolean.toString(n(metadata, "isTopLevel", 0) != 0));
            for (String attribute : new String[]{"implementation", "intrinsicClass", "intrinsicScript"}) {
                if (metadata != null && metadata.get(attribute) instanceof String) {
                    session.attribute(item, attribute, s(metadata, attribute, ""), metadata);
                }
            }
            for (String list : new String[]{"assetMap", "includesList", "exportAfterList"}) {
                List<FlaRecord> entries = records(metadata, list);
                if (entries.isEmpty()) {
                    continue;
                }
                Element container = child(item, list);
                for (FlaRecord entry : entries) {
                    FlaRecord dependency = record(entry, "metadata");
                    if (dependency == null || !entry.isComplete()) {
                        session.issue("COMPILED_CLIP_METADATA", entry, "Incomplete SWC dependency entry omitted.");
                        continue;
                    }
                    if ("assetMap".equals(list)) {
                        Element asset = child(container, "AssetMapItem");
                        session.attribute(asset, "key", s(entry, "key", ""), entry);
                        if (!swcItem(asset, "SwcItem", dependency, "", active)) {
                            container.removeChild(asset);
                        }
                    } else {
                        swcItem(container, "includesList".equals(list) ? "IncludesItem" : "ExportAfterItem", dependency, "", active);
                    }
                }
                if (!container.hasChildNodes()) {
                    item.removeChild(container);
                }
            }
            if (!records(metadata, "legacyChildren").isEmpty()) {
                session.issue("COMPILED_CLIP_METADATA", metadata, "Historical revision-2 SWC children have no confirmed XFL collection mapping.");
            }
            return true;
        } finally {
            active.remove(metadata);
        }
    }

    private static byte[] icon(FlaRecord raster) throws IOException {
        if (raster == null || !raster.isComplete()) {
            throw new IOException("Incomplete custom icon.");
        }
        int format = (int) n(raster, "formatCode", -1), revision = (int) n(raster, "revision", -1);
        if (format < 0 || format > 5 || revision < 0 || revision > 3) {
            throw new IOException("Unsupported custom icon raster.");
        }
        long width = n(raster, "width", 0), height = n(raster, "height", 0), stride = n(raster, "rowStride", 0), length = stride * height;
        int depth = format == 5 ? 32 : 1 << format;
        if (width <= 0 || height <= 0 || stride < (width * depth + 7) / 8 || width * height > XflBitmapWriter.MAX_PIXELS || length > XflBitmapWriter.MAX_RASTER_BYTES) {
            throw new IOException("Invalid compiled clip icon dimensions.");
        }
        byte[] icon = XflBitmapWriter.pixels(raster, (int) length);
        if (format == 5) {
            if ((stride & 3) != 0) {
                throw new IOException("Unaligned 32-bit icon rows.");
            }
            for (int i = 0; i < icon.length; i += 4) {
                byte a = icon[i], r = icon[i + 1];
                icon[i] = icon[i + 3];
                icon[i + 1] = icon[i + 2];
                icon[i + 2] = r;
                icon[i + 3] = revision < 3 ? (byte) 255 : a;
            }
        } else if (format == 4) {
            if ((stride & 1) != 0) {
                throw new IOException("Unaligned 16-bit icon rows.");
            }
            for (int i = 0; i < icon.length; i += 2) {
                byte first = icon[i];
                icon[i] = icon[i + 1];
                icon[i + 1] = first;
            }
        } else {
            int[] palette = XflBitmapWriter.palette(raster, revision, revision >= 3);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int bit = x * depth, index = (icon[(int) (y * stride) + bit / 8] >>> (8 - depth - bit % 8)) & ((1 << depth) - 1);
                    if (index >= palette.length) {
                        throw new IOException("Custom icon refers to a missing palette entry.");
                    }
                }
            }
        }
        FlaBounds bounds = raster.getBounds();
        if (bounds == null || !bounds.isDefined()) {
            throw new IOException("Missing compiled clip icon bounds.");
        }
        return icon;
    }

    void customIcon(Element item, FlaRecord raster, String image) throws IOException {
        byte[] bytes = icon(raster);
        writeIcon(item, raster, image);
        session.files.put("bin/" + image, new BinaryData(bytes));
    }

    private void writeIcon(Element item, FlaRecord raster, String image) throws IOException {
        long width = n(raster, "width", 0), height = n(raster, "height", 0), stride = n(raster, "rowStride", 0);
        FlaBounds iconBounds = raster.getBounds();
        Element custom = child(child(item, "customIcon"), "CustomIcon");
        custom.setAttribute("rowByteCount", Long.toString(stride));
        int format = (int) n(raster, "formatCode", -1), revision = (int) n(raster, "revision", -1);
        custom.setAttribute("colorDepth", Integer.toString(format == 5 ? 32 : 1 << format));
        custom.setAttribute("width", Long.toString(width));
        custom.setAttribute("height", Long.toString(height));
        custom.setAttribute("isTransparent", Boolean.toString(n(raster, "hasAlpha", 0) != 0));
        custom.setAttribute("href", image);
        String[] frameAttrs = {"frameLeft", "frameRight", "frameTop", "frameBottom"};
        for (int i = 0; i < 4; i++) {
            custom.setAttribute(frameAttrs[i], Integer.toString(iconBounds.getWords()[i]));
        }
        if (format < 4) {
            Element table = child(custom, "colorTable");
            for (int color : XflBitmapWriter.palette(raster, revision, revision >= 3)) {
                Element entry = child(table, "ColorItem");
                entry.setAttribute("color", String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF));
                entry.setAttribute("alpha", number((color >>> 24) / 255.0));
            }
        }
    }

    void instance(Element parent, FlaRecord r) {
        String name = session.compiledClips.get(n(r, "mediaId", -1));
        if (name == null) {
            session.issue("MISSING_COMPILED_CLIP", r, "Instance refers to unavailable compiled-clip media " + r.get("mediaId"));
            return;
        }
        Element e = child(parent, "DOMCompiledClipInstance");
        session.attribute(e, "libraryItemName", name, r);
        session.attribute(e, "name", s(r, "instanceName", ""), r);
        e.setAttribute("selected", Boolean.toString((n(r, "flags", 0) & 2) != 0));
        e.setAttribute("locked", Boolean.toString((n(r, "flags", 0) & 4) != 0));
        FlaRecord binding = record(r, "componentXml");
        if (binding != null) {
            e.setAttribute("uniqueID", Long.toString(n(binding, "value", 0)));
        }
        matrix(e, r.getMatrix());
        XflObjectWriter.transformPoint(session, e, r);
        XflObjectWriter.accessibility(session, e, r);
        new XflActionWriter(session).actions(e, record(r, "actions"));
        XflPersistentDataWriter.write(session, e, r);
        if (!s(binding, "xml", "").isEmpty()) {
            child(e, "dataBindingXML").setTextContent(session.text(s(binding, "xml", ""), binding));
        }
        new XflComponentWriter(session).parameters(e, record(r, "properties"));
    }

    void cache(Element root) {
        if (libraries.isEmpty()) {
            return;
        }
        Element cache = child(root, "swcCache");
        int ordinal = 0;
        for (Map.Entry<String, FlaSharedLibraries.Entry> item : libraries.entrySet()) {
            String path = "compiled-library-" + (++ordinal) + ".swc";
            Element swc = child(cache, "Swc");
            session.attribute(swc, "hashKey", item.getKey(), item.getValue().getSourceRecord());
            swc.setAttribute("href", path);
            Element definitions = child(swc, "classDefinitions");
            List<String> members = item.getValue().getMembers();
            if (members != null) {
                for (String member : members) {
                    session.attribute(child(definitions, "ClassDefinition"), "value", member, item.getValue().getSourceRecord());
                }
            }
            session.files.put("bin/" + path, item.getValue().getData());
        }
    }

    private static String key(String name) {
        if (name == null) {
            return "";
        }
        int zero = name.indexOf('\0');
        return zero < 0 ? name : name.substring(0, zero);
    }
}
