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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Binary FLA to version-selected XFL exporter. Each invocation has independent
 * state and leaves the source model untouched. Unsupported features produce
 * located diagnostics; this is not a full authoring round trip.
 */
public final class BinaryFlaToXflExporter {

    private final XflFormatVersion version;

    public BinaryFlaToXflExporter() {
        this(XflFormatVersion.CS6);
    }

    public BinaryFlaToXflExporter(XflFormatVersion version) {
        this.version = java.util.Objects.requireNonNull(version, "version");
    }

    public XflExportResult export(BinaryFlaDocument document) throws IOException {
        if (document == null) {
            throw new NullPointerException("document");
        }
        return new Session(document, version).export();
    }

    static final class Session {

        final BinaryFlaDocument source;
        final XflFormatVersion version;
        final List<XflExportIssue> issues = new ArrayList<>();
        final Map<String, BinaryData> files = new LinkedHashMap<>();
        final Map<Long, BinaryFlaDocument.Page> symbols = new HashMap<>();
        final Map<BinaryFlaDocument.Page, String> symbolNames = new IdentityHashMap<>();
        final Map<Long, String> compiledClips = new HashMap<>();
        final XflCompiledClipWriter clipWriter = new XflCompiledClipWriter(this);
        final Map<Long, String> sounds = new HashMap<>();
        final Map<Long, String> videos = new HashMap<>();
        final XflTextWriter textWriter = new XflTextWriter(this);
        final Map<Long, XflBitmapWriter.Bitmap> bitmaps = new HashMap<>();
        final Map<XflBitmapWriter.Bitmap, XflBitmapWriter.Bitmap> smoothingVariants = new IdentityHashMap<>();
        final Map<FlaRecord, XflBitmapWriter.Bitmap> inlineBitmaps = new IdentityHashMap<>();
        final Map<String, FlaRecord> folderIds = new HashMap<>();
        final Map<FlaRecord, String> folderNames = new IdentityHashMap<>();
        final Set<String> names = new HashSet<>();
        final Set<String> itemIds = new HashSet<>();
        final Set<FlaRecord> active = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
        String stream = "Contents";
        Element mediaElement;
        int bitmapOrdinal;

        Session(BinaryFlaDocument source) {
            this(source, XflFormatVersion.CS6);
        }

        Session(BinaryFlaDocument source, XflFormatVersion version) {
            this.source = source;
            this.version = version;
        }

        XflExportResult export() throws IOException {
            for (FlaStream s : source.getStreams().values()) {
                for (FlaParseIssue i : s.getIssues()) {
                    issues.add(new XflExportIssue("PARSE_ISSUE", s.getPath(), i.getOffset(), i.toString()));
                }
            }
            Document doc = document("DOMDocument");
            Element root = doc.getDocumentElement();
            root.setAttribute("xflVersion", version.getXflVersion());
            root.setAttribute("creatorInfo", "FlaComDoc");
            if (source.getWidthTwips() != null && source.getHeightTwips() != null) {
                root.setAttribute("width", number(source.getWidthTwips() / 20.0));
                root.setAttribute("height", number(source.getHeightTwips() / 20.0));
            } else {
                issue("STAGE_SIZE", source.getContents(), "Stage dimensions are unavailable; XFL defaults apply.");
            }
            Double rate = source.getFrameRate();
            if (rate != null && rate > 0) {
                root.setAttribute("frameRate", number(rate));
            } else {
                issue("FRAME_RATE", source.getContents(), "Frame rate is unavailable; XFL defaults apply.");
            }
            if (source.getContents().get("backgroundColorRgba") instanceof Number) {
                root.setAttribute("backgroundColor", rgb(n(source.getContents(), "backgroundColorRgba", 0)));
            }
            XflDocumentWriter documentWriter = new XflDocumentWriter(this);
            documentWriter.attributes(root, source.getContents());
            folders(root);
            textWriter.fonts(root);
            mediaElement = child(root, "media");
            media();
            Element includes = child(root, "symbols");
            int ordinal = 0;
            for (BinaryFlaDocument.Page page : source.getPages()) {
                if (page.getRole() != BinaryFlaDocument.Page.Role.LIBRARY) {
                    continue;
                }
                FlaRecord descriptor = page.getDescriptor();
                symbolNames.put(page, unique(fullName(descriptor), descriptor));
                Long id = page.getLibraryId();
                if (id == null || symbols.containsKey(id)) {
                    issue("LIBRARY_ID", descriptor, "Missing or duplicate library ID; instances use the first matching item.");
                } else {
                    symbols.put(id, page);
                }
                child(includes, "Include").setAttribute("href", "symbol-" + (++ordinal) + ".xml");
            }
            ordinal = 0;
            for (BinaryFlaDocument.Page page : source.getPages()) {
                if (page.getRole() == BinaryFlaDocument.Page.Role.LIBRARY) {
                    stream = "Contents";
                    boolean component = XflComponentWriter.isComponent(page.getDescriptor());
                    XflComponentWriter componentWriter = new XflComponentWriter(this);
                    Document symbol = document(component ? "DOMComponentItem" : "DOMSymbolItem");
                    Element item = symbol.getDocumentElement();
                    attribute(item, "name", symbolNames.get(page), page.getDescriptor());
                    if (!component) {
                        item.setAttribute("symbolType", symbolType(page.getDescriptor()));
                    } else {
                        componentWriter.itemAttributes(item, page.getDescriptor(), ordinal + 1);
                    }
                    FlaRecord descriptor = page.getDescriptor();
                    if (descriptor.get("lastUniqueIdentifier") instanceof Number) {
                        item.setAttribute("lastUniqueIdentifier", Long.toString(n(descriptor, "lastUniqueIdentifier", 0)));
                    }
                    for (String flag : new String[]{"hasValidCenterPoint", "isFlexComponent", "isSpriteSubclass"}) {
                        if (descriptor.get(flag) instanceof Number) {
                            item.setAttribute(flag, Boolean.toString(n(descriptor, flag, 0) != 0));
                        }
                    }
                    Object center = descriptor.get("transformCenterPointTwips");
                    if (center instanceof List && ((List<?>) center).size() == 2
                            && ((List<?>) center).get(0) instanceof Number && ((List<?>) center).get(1) instanceof Number) {
                        int x = ((Number) ((List<?>) center).get(0)).intValue(), y = ((Number) ((List<?>) center).get(1)).intValue();
                        int cx = Math.max(-0x1C20000, Math.min(0x1C20000, x)), cy = Math.max(-0x1C20000, Math.min(0x1C20000, y));
                        if (cx != x || cy != y) {
                            issue("SYMBOL_CENTER_POINT", descriptor, "Symbol center clamped to the native XFL point range.");
                        }
                        item.setAttribute("transformCenterPoint", integerPointCoordinate(cx) + ", " + integerPointCoordinate(cy));
                    }
                    linkage(item, page.getDescriptor());
                    if (n(page.getDescriptor(), "scaleGridEnabled", 0) != 0) {
                        scaleGrid(item, page.getDescriptor());
                    }
                    timeline(child(item, "timeline"), page);
                    String bindings = s(descriptor, "dataBindingXml", "");
                    if (!bindings.isEmpty()) {
                        cdata(item, "dataBindingXML", text(bindings, descriptor));
                    }
                    if (component) {
                        componentWriter.children(item, page.getDescriptor(), ordinal + 1);
                    }
                    files.put("LIBRARY/symbol-" + (++ordinal) + ".xml", new BinaryData(serialize(symbol)));
                }
            }
            Element timelines = child(root, "timelines");
            int sceneIndex = 0;
            for (BinaryFlaDocument.Page page : source.getPages()) {
                if (page.getRole() == BinaryFlaDocument.Page.Role.SCENE) {
                    timeline(timelines, page);
                    if (page.getDescriptor() == source.getContents().get("selectedScene")) {
                        root.setAttribute("currentTimeline", Integer.toString(sceneIndex + 1));
                    }
                    sceneIndex++;
                } else if (page.getRole() == BinaryFlaDocument.Page.Role.UNLINKED) {
                    stream = page.getStream().getPath();
                    issue("UNLINKED_PAGE", page.getRoot(), "Page has no scene/library descriptor and is not emitted.");
                }
            }
            stream = "Contents";
            if (sceneIndex == 0) {
                issue("NO_SCENES", source.getContents(), "No linked scenes are available; an empty recovery timeline was generated.");
                Element recovery = child(timelines, "DOMTimeline");
                recovery.setAttribute("name", "Recovered Scene");
                child(recovery, "layers");
            }
            new XflPublishWriter(this).write(record(source.getContents(), "publishSettings"));
            issue("EXPORT_SCOPE", source.getContents(), "Experimental authoring export; source recovery and remaining feature limitations are listed in this report and the XFL export documentation.");
            new XflSwatchWriter(this).write(root, source.getContents());
            XflPersistentDataWriter.write(this, root, source.getContents());
            documentWriter.externalXml(source.getContents());
            documentWriter.printer(root, source.getContents());
            child(root, "publishHistory");
            clipWriter.cache(root);
            files.put("DOMDocument.xml", new BinaryData(serialize(doc)));
            files.put("document.xfl", new BinaryData("PROXY-CS5".getBytes(StandardCharsets.US_ASCII)));
            return new XflExportResult(XflVersionRules.convert(files, version, issues), issues);
        }

        void issue(String code, FlaRecord r, String message) {
            issues.add(new XflExportIssue(code, stream, r == null ? 0 : r.getOffset(), message));
        }

        String text(String value, FlaRecord r) {
            String valid = validXml(value);
            if (!valid.equals(value)) {
                issue("XML_CHARACTER", r, "Characters forbidden by XML 1.0 were replaced with U+FFFD.");
            }
            return valid;
        }

        void attribute(Element e, String name, String value, FlaRecord r) {
            e.setAttribute(name, text(value, r));
        }

        private void media() {
            Set<Long> ids = new HashSet<>();
            for (BinaryFlaDocument.Media media : source.getMedia()) {
                FlaRecord descriptor = media.getDescriptor();
                long id = n(descriptor, "mediaId", -1);
                if (id < 0 || !ids.add(id)) {
                    issue("MEDIA_ID", descriptor, "Missing or duplicate media ID; only the first item may resolve instances.");
                    continue;
                }
                if (descriptor.getClassName().equals("CSwfPage")) {
                    try {
                        compiledClips.put(id, clipWriter.item(mediaElement, descriptor, (int) id));
                    } catch (IOException ex) {
                        issue("COMPILED_CLIP_DATA", descriptor, ex.getMessage());
                    }
                    continue;
                }
                if (descriptor.getClassName().equals("CMediaSound") || descriptor.getClassName().equals("CMediaVideoStream")) {
                    String saved = stream;
                    stream = media.getStream() == null ? "Contents" : media.getStream().getPath();
                    try {
                        XflMediaWriter writer = new XflMediaWriter(this);
                        if (descriptor.getClassName().equals("CMediaSound")) {
                            sounds.put(id, writer.sound(mediaElement, media, (int) id));
                        } else {
                            videos.put(id, writer.video(mediaElement, media, (int) id));
                        }
                    } catch (IOException ex) {
                        issue("MEDIA_DATA", descriptor, ex.getMessage());
                    }
                    stream = saved;
                    continue;
                }
                if (!descriptor.getClassName().equals("CMediaBits")) {
                    issue("MEDIA_NOT_EXPORTED", descriptor, "Media item is not exported yet: " + s(descriptor, "name", ""));
                    continue;
                }
                FlaRecord payload = media.getStream() == null ? null : media.getStream().getRoot();
                try {
                    XflBitmapWriter.Bitmap bitmap = new XflBitmapWriter(this).write(mediaElement, descriptor,
                            payload, record(payload, "raster"), fullName(descriptor), ++bitmapOrdinal);
                    bitmaps.put(id, bitmap);
                } catch (IOException ex) {
                    String saved = stream;
                    stream = media.getStream() == null ? "Contents" : media.getStream().getPath();
                    issue("BITMAP_DATA", payload == null ? descriptor : payload, ex.getMessage());
                    stream = saved;
                }
            }
        }

        private void folders(Element root) {
            FlaRecord collection = record(source.getContents(), "libraryFolders");
            List<FlaRecord> folders = records(collection, "entries");
            for (FlaRecord folder : folders) {
                String id = itemKey(record(folder, "libraryMetadata"), false);
                if (id != null && folderIds.putIfAbsent(id, folder) != null) {
                    issue("FOLDER_ID", folder, "Duplicate folder ID; the first folder is used for parent lookup.");
                }
            }
            Element container = child(root, "folders");
            for (FlaRecord folder : folders) {
                String name = folderName(folder, new HashSet<FlaRecord>());
                if (!names.add(name)) {
                    issue("LIBRARY_NAME", folder, "Duplicate folder name: " + name);
                    continue;
                }
                Element item = child(container, "DOMFolderItem");
                attribute(item, "name", name, folder);
                itemId(item, folder);
                FlaRecord metadata = record(folder, "libraryMetadata");
                if (metadata != null && metadata.get("attributes") instanceof Number) {
                    item.setAttribute("isExpanded", Boolean.toString(n(metadata, "attributes", 0) != 0));
                }
            }
        }

        private String itemKey(FlaRecord metadata, boolean parent) {
            String old = parent ? "legacyParentId" : "legacyItemId";
            if (metadata != null && metadata.get(old) instanceof Number) {
                long id = n(metadata, old, 0);
                return id == 0 ? null : "legacy:" + id;
            }
            FlaRecord id = record(metadata, parent ? "parentItem" : "item");
            long a = n(id, "firstPart", 0), b = n(id, "secondPart", 0);
            return a == 0 && b == 0 ? null : a + ":" + b;
        }

        private String folderName(FlaRecord folder, Set<FlaRecord> visiting) {
            String cached = folderNames.get(folder);
            if (cached != null) {
                return cached;
            }
            if (!visiting.add(folder) || visiting.size() > 100) {
                issue("FOLDER_CYCLE", folder, "Cyclic or excessively deep folder ancestry; parent link omitted.");
                return s(folder, "name", "Folder");
            }
            String name = withParent(folder, s(folder, "name", "Folder"), visiting);
            visiting.remove(folder);
            folderNames.put(folder, name);
            return name;
        }

        private String withParent(FlaRecord descriptor, String name, Set<FlaRecord> visiting) {
            String key = itemKey(record(descriptor, "libraryMetadata"), true);
            FlaRecord parent = folderIds.get(key);
            if (parent != null) {
                return folderName(parent, visiting) + "/" + name;
            }
            if (key != null) {
                issue("MISSING_FOLDER", descriptor, "Parent folder not found; item is placed in the library root.");
            }
            return name;
        }

        String fullName(FlaRecord descriptor) {
            return withParent(descriptor, s(descriptor, "name", "Symbol"), new HashSet<FlaRecord>());
        }

        String unique(String name, FlaRecord r) {
            name = text(name, r);
            String candidate = name.isEmpty() ? "Symbol" : name;
            int i = 1;
            while (!names.add(candidate)) {
                candidate = name + " (" + (++i) + ")";
            }
            if (!name.equals(candidate)) {
                issue("LIBRARY_NAME", r, "Empty or duplicate item name renamed to " + candidate);
            }
            return candidate;
        }

        String symbolType(FlaRecord descriptor) {
            // Descriptor selector order differs from the instance class order.
            long type = n(descriptor, "symbolType", 2);
            if (type == 5) {
                return "ik container";
            }
            if (type < 0 || type > 2) {
                issue("SYMBOL_TYPE", descriptor, "Unknown symbol type " + type + "; movie clip used.");
            }
            return type == 1 ? "button" : type == 0 ? "graphic" : "movie clip";
        }

        void itemId(Element element, FlaRecord descriptor) {
            FlaRecord id = record(record(descriptor, "libraryMetadata"), "item");
            if (n(id, "marker", 0) == 0 || !(id.get("firstPart") instanceof Number) || !(id.get("secondPart") instanceof Number)) {
                return;
            }
            long first = n(id, "firstPart", 0) & 0xFFFFFFFFL, second = n(id, "secondPart", 0) & 0xFFFFFFFFL;
            if (first == 0 && second == 0) {
                return;
            }
            String value = String.format(java.util.Locale.ROOT, "%08x-%08x", first, second);
            if (itemIds.add(value)) {
                element.setAttribute("itemID", value);
            } else {
                issue("ITEM_ID", descriptor, "Duplicate library item identity omitted: " + value);
            }
        }

        void mediaSourcePlatform(Element element, FlaRecord descriptor) {
            if (!(descriptor.get("sourcePlatform") instanceof Number)) {
                return;
            }
            long value = n(descriptor, "sourcePlatform", -1);
            if (value == 0 || value == 1) {
                element.setAttribute("sourcePlatform", value == 1 ? "windows" : "macintosh");
            } else {
                issue("MEDIA_PLATFORM", descriptor, "Unknown stored media source platform " + value + "; attribute omitted.");
            }
        }

        void linkage(Element element, FlaRecord descriptor) {
            itemId(element, descriptor);
            // Library metadata takes precedence over the older descriptor copy.
            FlaRecord metadata = record(descriptor, "libraryMetadata");
            FlaRecord rawLink = record(metadata, "linkage");
            if (rawLink == null) {
                rawLink = record(descriptor, "linkage");
            }
            FlaRecord auxiliary = record(rawLink, "auxiliary");
            if (!s(auxiliary, "sourceItemName", "").isEmpty()) {
                attribute(element, "sourceLibraryItemHRef", s(auxiliary, "sourceItemName", ""), auxiliary);
            }
            if ("CDocumentPage".equals(descriptor.getClassName())) {
                if (!s(auxiliary, "sourcePath", "").isEmpty()) {
                    attribute(element, "sourceFlashFilepath", s(auxiliary, "sourcePath", ""), auxiliary);
                    if (auxiliary.get("sourceAutoUpdate") instanceof Number) {
                        element.setAttribute("sourceAutoUpdate", Boolean.toString(n(auxiliary, "sourceAutoUpdate", 0) != 0));
                    }
                }
                if (descriptor.get("sourceTime") instanceof Number) {
                    element.setAttribute("lastModified", Long.toString(n(descriptor, "sourceTime", 0)));
                }
                if (n(auxiliary, "sourceLastModified", 0) != 0) {
                    element.setAttribute("sourceLastModified", Long.toString(n(auxiliary, "sourceLastModified", 0)));
                }
            }
            FlaLinkage link = metadata == null ? null : metadata.getLinkage();
            if (link == null) {
                link = descriptor.getLinkage();
            }
            if (link == null || link.getMode() == FlaLinkage.Mode.NONE) {
                return;
            }
            if (link.getMode() == FlaLinkage.Mode.UNKNOWN) {
                issue("LINKAGE_MODE", descriptor, "Unknown linkage mode " + link.getModeId());
                return;
            }
            element.setAttribute("linkageImportForRS", Boolean.toString(link.isImportForRuntimeSharing()));
            if (link.isExportForActionScript() != null) {
                element.setAttribute("linkageExportForAS", link.isExportForActionScript().toString());
                element.setAttribute("linkageExportForRS", link.isExportForRuntimeSharing().toString());
                element.setAttribute("linkageExportInFirstFrame", link.isExportInFirstFrame().toString());
            }
            if (link.getIdentifier() != null) {
                attribute(element, "linkageIdentifier", link.getIdentifier(), descriptor);
            }
            if (link.getUrl() != null) {
                attribute(element, "linkageURL", link.getUrl(), descriptor);
            }
            if (link.getClassName() != null) {
                attribute(element, "linkageClassName", link.getClassName(), descriptor);
            }
            if (link.getBaseClass() != null) {
                attribute(element, "linkageBaseClass", link.getBaseClass(), descriptor);
            }
        }

        private void scaleGrid(Element item, FlaRecord descriptor) {
            Object value = descriptor.get("scaleGridBounds");
            if (!(value instanceof List) || ((List<?>) value).size() != 4) {
                issue("SCALE_GRID", descriptor, "Enabled scale grid has incomplete bounds.");
                return;
            }
            List<?> words = (List<?>) value;
            for (Object word : words) {
                if (!(word instanceof Integer) || (Integer) word == Integer.MIN_VALUE) {
                    issue("SCALE_GRID", descriptor, "Enabled scale grid has undefined bounds.");
                    return;
                }
            }
            // This field has right/left/bottom/top order, unlike generic bounds.
            String[] attributes = {"scaleGridRight", "scaleGridLeft", "scaleGridBottom", "scaleGridTop"};
            for (int i = 0; i < attributes.length; i++) {
                item.setAttribute(attributes[i], number(((Integer) words.get(i)) / 20.0));
            }
        }

        private void timeline(Element parent, BinaryFlaDocument.Page page) {
            stream = page.getStream() == null ? "Contents" : page.getStream().getPath();
            Element timeline = child(parent, "DOMTimeline");
            attribute(timeline, "name", page.getName() == null ? "Scene" : page.getName(), page.getDescriptor());
            if (page.getRole() == BinaryFlaDocument.Page.Role.SCENE) {
                String bindings = s(page.getDescriptor(), "dataBindingXml", "");
                if (!bindings.isEmpty()) {
                    String filename = "databindings-scene-" + (source.getPages().indexOf(page) + 1) + ".xml";
                    timeline.setAttribute("hrefDataBindingsFile", filename);
                    files.put("bin/" + filename, new BinaryData(text(bindings, page.getDescriptor()).getBytes(StandardCharsets.UTF_8)));
                }
            }
            FlaRecord root = page.getRoot();
            if (root == null) {
                issue("MISSING_PAGE", page.getDescriptor(), "Referenced page stream is missing.");
                child(timeline, "layers");
                return;
            }
            timeline.setAttribute("currentFrame", Long.toString(n(root, "currentFrame", 0)));
            Object guides = root.get("guides");
            if (guides instanceof List && !((List<?>) guides).isEmpty()) {
                StringBuilder value = new StringBuilder("<guidelines>");
                boolean valid = true;
                for (Object item : (List<?>) guides) {
                    if (!(item instanceof List) || ((List<?>) item).size() != 2) {
                        valid = false;
                        break;
                    }
                    List<?> pair = (List<?>) item;
                    if (!(pair.get(0) instanceof Number) || !(pair.get(1) instanceof Number)) {
                        valid = false;
                        break;
                    }
                    int direction = ((Number) pair.get(0)).intValue();
                    if (direction != 0 && direction != 1) {
                        valid = false;
                        break;
                    }
                    value.append("<guideline direction=\"").append(direction == 0 ? "h" : "v").append("\">");
                    value.append(number(((Number) pair.get(1)).doubleValue() / 20)).append("</guideline>");
                }
                if (valid) {
                    timeline.setAttribute("guides", value.append("</guidelines>").toString());
                } else {
                    issue("GUIDES", root, "Invalid guide list omitted as a whole.");
                }
            }
            List<FlaRecord> layers = new ArrayList<>();
            Set<FlaRecord> seen = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
            for (FlaRecord layer : root.getChildren()) {
                if ("CPicLayer".equals(layer.getClassName()) && seen.add(layer)) {
                    layers.add(layer);
                } else if (!"CPicLayer".equals(layer.getClassName())) {
                    issue("TIMELINE_CHILD", layer, "Unsupported page child " + layer.getClassName());
                }
            }
            Collections.reverse(layers);
            Element container = child(timeline, "layers");
            for (int i = 0; i < layers.size(); i++) {
                layer(container, layers.get(i), layers, i);
            }
        }

        private void layer(Element container, FlaRecord r, List<FlaRecord> layers, int index) {
            Element e = child(container, "DOMLayer");
            attribute(e, "name", s(r, "name", "Layer"), r);
            long type = r.getLayerType();
            if (type == 1 || type == 3 || type == 4) {
                e.setAttribute("layerType", type == 1 ? "guide" : type == 3 ? "folder" : "mask");
            } else if (type != 0 && type != 2 && type != 5 && type != 6) {
                issue("LAYER_TYPE", r, "Unknown layer type " + type);
            }
            FlaRecord related = r.getLayerParent();
            if (related != null) {
                int parent = layers.indexOf(related);
                if (parent >= 0 && parent < index) {
                    e.setAttribute("parentLayerIndex", Integer.toString(parent));
                } else {
                    issue("LAYER_PARENT", r, "Missing, cyclic or out-of-order layer parent; link omitted.");
                }
            }
            Object color = r.get("stateFlags");
            if (color instanceof BinaryData && ((BinaryData) color).size() == 3) {
                byte[] state = ((BinaryData) color).toByteArray();
                e.setAttribute("current", Boolean.toString(state[0] != 0));
                e.setAttribute("isSelected", Boolean.toString(state[0] != 0));
                e.setAttribute("visible", Boolean.toString(state[1] == 0));
                e.setAttribute("locked", Boolean.toString(state[2] != 0));
            } else if (r.get("legacyState") instanceof Number) {
                long state = n(r, "legacyState", 2);
                e.setAttribute("current", Boolean.toString(state == 3));
                e.setAttribute("isSelected", Boolean.toString(state == 3));
                e.setAttribute("visible", Boolean.toString(state != 0));
                e.setAttribute("locked", Boolean.toString(state == 1));
            }
            if (r.get("outlineColorRgb") != null) {
                e.setAttribute("color", rgb(n(r, "outlineColorRgb", 0)));
            } else {
                e.setAttribute("color", "#000000");
                issue("LAYER_COLOR", r, "Outline color is unavailable; black was used.");
            }
            if (r.get("outline") != null) {
                boolean outline = n(r, "outline", 0) != 0;
                e.setAttribute("outline", Boolean.toString(outline));
                e.setAttribute("useOutlineView", Boolean.toString(outline));
            }
            if (r.get("heightLiteral") instanceof Number) {
                long height = (int) n(r, "heightLiteral", 0);
                if (height >= 1) {
                    e.setAttribute("heightLiteral", Long.toString(height));
                }
            }
            if (r.get("heightMultiplier") != null) {
                long height = n(r, "heightMultiplier", 1);
                if (height > 0) {
                    e.setAttribute("heightMultiplier", Long.toString(height));
                } else {
                    issue("LAYER_HEIGHT", r, "Nonpositive layer height omitted; the default height applies.");
                }
            }
            if (r.get("open") != null) {
                e.setAttribute("open", Boolean.toString(n(r, "open", 1) != 0));
            }
            if (r.get("autoNamed") != null) {
                e.setAttribute("autoNamed", Boolean.toString(n(r, "autoNamed", 1) != 0));
            }
            if (n(r, "animationType", 0) == 1) {
                e.setAttribute("animationType", "motion object");
            }
            Element frames = child(e, "frames");
            long start = 0;
            for (FlaRecord frame : r.getChildren()) {
                if (!"CPicFrame".equals(frame.getClassName())) {
                    issue("LAYER_CHILD", frame, "Unsupported layer child " + frame.getClassName());
                    continue;
                }
                long duration = n(frame, "duration", 1);
                if (duration <= 0) {
                    issue("FRAME_DURATION", frame, "Zero duration replaced with one frame.");
                    duration = 1;
                }
                frame(frames, frame, start, duration);
                start += duration;
            }
            if (n(r, "animationType", 0) > 1 || type == 6) {
                boolean supported = n(r, "animationType", 0) == 2;
                org.w3c.dom.NodeList frameElements = frames.getElementsByTagNameNS(NS, "DOMFrame");
                int poseFrames = 0;
                for (int i = 0; i < frameElements.getLength(); i++) {
                    Element f = (Element) frameElements.item(i);
                    if ((Long.parseLong(f.getAttribute("keyMode")) & 255) == 4) {
                        poseFrames++;
                        supported &= "true".equals(f.getAttribute("isIKPose"));
                    }
                }
                Object locations = r.get("animationValues");
                supported &= poseFrames > 0 && locations instanceof List;
                if (supported) {
                    java.util.StringJoiner positions = new java.util.StringJoiner(",");
                    for (Object value : (List<?>) locations) {
                        if (!(value instanceof Number)) {
                            supported = false;
                            break;
                        }
                        positions.add(value.toString());
                    }
                    if (supported) {
                        e.setAttribute("animationType", "IK pose");
                        e.setAttribute("poseLocations", positions.toString());
                    }
                }
                if (!supported) {
                    issue("LAYER_ANIMATION", r, "Unsupported or incomplete IK layer authoring data.");
                }
            }
        }

        private void frame(Element frames, FlaRecord r, long start, long duration) {
            Element e = child(frames, "DOMFrame");
            e.setAttribute("index", Long.toString(start));
            e.setAttribute("duration", Long.toString(duration));
            long mode = n(r, "keyMode", 0);
            e.setAttribute("keyMode", Long.toString(mode));
            long kind = mode & 255;
            if (kind == 1 || kind == 2) {
                e.setAttribute("tweenType", kind == 1 ? "motion" : "shape");
            }
            if (kind > 4) {
                issue("FRAME_ANIMATION", r, "Unknown frame animation kind " + kind);
            }
            e.setAttribute("acceleration", Long.toString((short) n(r, "acceleration", 0)));
            String label = s(r, "name", "");
            boolean bookmark = n(r, "bookmark", 0) != 0;
            if (!label.isEmpty() || bookmark) {
                attribute(e, "name", label, r);
                long type = n(r, "labelType", 0);
                e.setAttribute("labelType", bookmark ? "anchor" : type == 1 ? "comment" : "name");
                if (bookmark) {
                    e.setAttribute("bookmark", "true");
                }
                if (type > 1) {
                    issue("FRAME_LABEL", r, "Unknown frame-label type " + type);
                }
            }
            long rotation = n(r, "rotationMode", 0);
            e.setAttribute("motionTweenRotate", rotation == 1 ? "clockwise" : rotation == 2 ? "counter-clockwise" : "auto");
            e.setAttribute("motionTweenRotateTimes", Long.toString(n(r, "rotationCount", 0)));
            if (kind == 1) {
                e.setAttribute("motionTweenOrientToPath", Boolean.toString((mode & 0x100) != 0));
                e.setAttribute("motionTweenScale", Boolean.toString((mode & 0x200) != 0));
                e.setAttribute("motionTweenSync", Boolean.toString((mode & 0x800) != 0));
                e.setAttribute("motionTweenSnap", Boolean.toString((mode & 0x1000) != 0));
                if (rotation == 0 && (mode & 0x400) == 0) {
                    e.setAttribute("motionTweenRotate", "none");
                }
            }
            if (kind == 2) {
                e.setAttribute("shapeTweenBlend", n(r, "shapeTweenBlend", 0) == 1 ? "angular" : "distributive");
            }
            actions(e, record(r, "actions"));
            new XflMorphWriter(this).morph(e, record(r, "morphShape"), r);
            frameSound(e, r);
            customEase(e, r);
            XflMotionWriter.motion(this, e, r);
            Element elements = child(e, "elements");
            for (FlaRecord c : r.getChildren()) {
                element(elements, c, 0);
            }
            new XflShapeWriter(this).shape(elements, r, false);
            if (kind == 4 && !XflIkWriter.write(this, e, r, start, duration)) {
                issue("FRAME_ANIMATION", r, "Incomplete IK authoring data; baked symbol frames retained.");
            }
        }

        private void actions(Element parent, FlaRecord actions) {
            new XflActionWriter(this).actions(parent, actions);
        }

        void element(Element parent, FlaRecord r, int depth) {
            if (depth > 100 || !active.add(r)) {
                issue("OBJECT_CYCLE", r, "Cyclic or excessively deep display object tree; repeated branch omitted.");
                return;
            }
            try {
                switch (r.getClassName()) {
                    case "CPicShape":
                        if (((n(r, "flags", 0) & 1) == 0 && (n(r, "drawingObjectFlags", 0) & 1) == 0) || !r.getChildren().isEmpty()) {
                            Element group = child(parent, "DOMGroup");
                            group.setAttribute("selected", Boolean.toString((n(r, "flags", 0) & 2) != 0));
                            if ((n(r, "flags", 0) & 4) != 0) {
                                issue("OBJECT_LOCK", r, "Group lock flag is not exported yet.");
                            }
                            if ((n(r, "drawingObjectFlags", 0) & 1) != 0) {
                                issue("DRAWING_OBJECT_GROUP", r, "Drawing object has child geometry; separate members retained without native geometry merging.");
                            }
                            Element members = child(group, "members");
                            for (FlaRecord c : r.getChildren()) {
                                element(members, c, depth + 1);
                            }
                            new XflShapeWriter(this).shape(members, r, false);
                        } else {
                            new XflShapeWriter(this).shape(parent, r, (n(r, "flags", 0) & 1) != 0);
                        }
                        break;
                    case "CPicRect":
                    case "CPicOval":
                        new XflPrimitiveWriter(this).primitive(parent, r);
                        break;
                    case "CPicSymbol":
                    case "CPicShapeObj":
                    case "CPicSprite":
                    case "CPicButton":
                        symbolInstance(parent, r);
                        break;
                    case "CPicSwf":
                        clipWriter.instance(parent, r);
                        break;
                    case "CCommandObj":
                        issue("GENERATOR_COMMAND", r, "Flash Generator commands have no supported CS5-CS6 XFL representation; command omitted: "
                                + s(r, "command", ""));
                        for (FlaRecord c : r.getChildren()) {
                            element(parent, c, depth + 1);
                        }
                        break;
                    case "CPicText":
                        textWriter.text(parent, r);
                        break;
                    case "CPicTLFText":
                        new XflTlfWriter(this).text(parent, r, depth);
                        break;
                    case "CPicVideo":
                    case "CPicVideoStream":
                        videoInstance(parent, r);
                        break;
                    case "CPicBitmap":
                        bitmapInstance(parent, r);
                        break;
                    default:
                        issue("DISPLAY_OBJECT", r, "Display object is not exported yet: " + r.getClassName());
                }
            } finally {
                active.remove(r);
            }
        }

        private void symbolInstance(Element parent, FlaRecord r) {
            BinaryFlaDocument.Page page = symbols.get(n(r, "libraryId", -1));
            if (page == null) {
                issue("MISSING_SYMBOL", r, "Instance refers to an unknown library ID " + r.get("libraryId"));
                return;
            }
            boolean component = "CPicSprite".equals(r.getClassName()) && XflComponentWriter.isComponent(page.getDescriptor());
            Element e = child(parent, component ? "DOMComponentInstance" : "DOMSymbolInstance");
            attribute(e, "libraryItemName", symbolNames.get(page), r);
            // A clip can be placed as a graphic or button independently of its
            // library definition. The serialized instance class selects this.
            String type = r.getClassName().equals("CPicSprite") ? "movie clip"
                    : r.getClassName().equals("CPicButton") ? "button" : "graphic";
            if (!component) {
                e.setAttribute("symbolType", type);
            }
            e.setAttribute("selected", Boolean.toString((n(r, "flags", 0) & 2) != 0));
            e.setAttribute("locked", Boolean.toString((n(r, "flags", 0) & 4) != 0));
            attribute(e, "name", s(r, "instanceName", ""), r);
            String reference = s(r, "identifier", "");
            if (reference.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                attribute(e, "referenceID", reference, r);
            }
            new XflObjectWriter(this).placement(e, r);
            matrix(e, r.getMatrix());
            XflObjectWriter.transformPoint(this, e, r);
            if (type.equals("graphic")) {
                e.setAttribute("firstFrame", Long.toString(n(r, "firstFrame", 0)));
                long loop = n(r, "loopMode", 0);
                e.setAttribute("loop", loop == 1 ? "play once" : loop == 2 ? "single frame" : "loop");
            }
            symbolAnimationSettings(e, r);
            if (n(r, "trackAsMenu", 0) != 0) {
                e.setAttribute("trackAsMenu", "true");
            }
            actions(e, record(r, "actions"));
            new XflObjectWriter(this).filters(e, r);
            new XflObjectWriter(this).colorEffect(e, r);
            XflPersistentDataWriter.write(this, e, r);
            if (component) {
                FlaRecord binding = record(r, "componentXml");
                if (binding != null) {
                    e.setAttribute("uniqueID", Long.toString(n(binding, "uniqueId", 0)));
                }
                if (!s(binding, "xml", "").isEmpty()) {
                    cdata(e, "dataBindingXML", text(s(binding, "xml", ""), binding));
                }
                FlaRecord parameters = record(r, "properties");
                if (records(parameters, "entries").isEmpty()) {
                    parameters = record(r, "legacyProperties");
                }
                new XflComponentWriter(this).parameters(e, parameters);
            }
            if (n(r, "timelineEffectPresent", 0) != 0) {
                issue("TIMELINE_EFFECT", r, "Legacy timeline-effect authoring metadata has no confirmed native XFL representation; source fields are retained in the reader.");
            }
        }

        private void symbolAnimationSettings(Element out, FlaRecord r) {
            if (r.get("isExtensible") instanceof Number) {
                out.setAttribute("isExtensible", Boolean.toString(n(r, "isExtensible", 0) != 0));
            }
            List<FlaRecord> settings = records(r, "animationFrameSettings");
            if (settings.isEmpty()) {
                return;
            }
            StringJoiner modes = new StringJoiner(","), frames = new StringJoiner(",");
            for (FlaRecord setting : settings) {
                if (setting == null || !(setting.get("loopMode") instanceof Number)
                        || !(setting.get("firstFrame") instanceof Number)) {
                    issue("SYMBOL_ANIMATION_SETTINGS", r, "Incomplete animation frame settings omitted as a whole.");
                    return;
                }
                modes.add(Integer.toString(((Number) setting.get("loopMode")).intValue()));
                frames.add(Integer.toString(((Number) setting.get("firstFrame")).intValue()));
            }
            out.setAttribute("animationSymbolMode", modes.toString());
            out.setAttribute("animationFrameOffset", frames.toString());
        }

        @SuppressWarnings("unchecked")
        private void customEase(Element e, FlaRecord r) {
            if (r.get("useSingleEaseCurve") != null) {
                e.setAttribute("useSingleEaseCurve", Boolean.toString(n(r, "useSingleEaseCurve", 1) != 0));
            }
            if (n(r, "customEasePresent", 0) == 0) {
                return;
            }
            e.setAttribute("hasCustomEase", "true");
            Object raw = r.get("customEaseCurves");
            if (!(raw instanceof List)) {
                issue("CUSTOM_EASE", r, "Missing custom ease curves.");
                return;
            }
            Element tweens = child(e, "tweens");
            String[] targets = {"position", "rotation", "scale", "color", "filters", "all"};
            List<List<List<Double>>> curves = (List<List<List<Double>>>) raw;
            for (int i = 0; i < Math.min(6, curves.size()); i++) {
                List<List<Double>> curve = curves.get(i);
                if (curve.isEmpty()) {
                    continue;
                }
                Element ease = e.getOwnerDocument().createElementNS(NS, "CustomEase");
                ease.setAttribute("target", targets[i]);
                boolean valid = curve.size() >= 2;
                for (int j = 0; j < curve.size(); j++) {
                    List<Double> values = curve.get(j);
                    for (int k = 0; k < 3; k++) {
                        if (j == 0 && k == 0 || j == curve.size() - 1 && k == 2) {
                            continue;
                        }
                        double x = values.get(k * 2), y = values.get(k * 2 + 1);
                        if (!Double.isFinite(x) || !Double.isFinite(y)) {
                            valid = false;
                            continue;
                        }
                        Element p = child(ease, "Point");
                        p.setAttribute("x", number(x));
                        p.setAttribute("y", number(y));
                    }
                }
                if (valid) {
                    tweens.appendChild(ease);
                } else {
                    issue("CUSTOM_EASE", r, "Invalid cubic ease curve omitted for " + targets[i]);
                }
            }
        }

        private void frameSound(Element e, FlaRecord r) {
            long id = n(r, "soundMediaId", 0);
            if (id == 0) {
                return;
            }
            String name = sounds.get(id);
            if (name == null) {
                issue("MISSING_SOUND", r, "Frame refers to unavailable sound ID " + id);
                return;
            }
            attribute(e, "soundName", name, r);
            String[] sync = {"event", "start", "stop", "stream"};
            long mode = n(r, "soundSync", 0);
            if (mode >= 0 && mode < sync.length) {
                e.setAttribute("soundSync", sync[(int) mode]);
            } else {
                issue("SOUND_SYNC", r, "Unassigned sound sync " + mode);
            }
            long loop = n(r, "soundLoopCount", 1);
            e.setAttribute("soundLoop", Long.toString(loop));
            e.setAttribute("soundLoopMode", loop == 32767 ? "loop" : "repeat");
            e.setAttribute("inPoint44", Long.toString(n(r, "soundInPoint", 0)));
            e.setAttribute("outPoint44", Long.toString(n(r, "soundOutPoint", 0x3FFFFFFF)));
            e.setAttribute("soundZoomLevel", Long.toString((short) n(r, "soundZoom", -1)));
            String[] effects = {"none", "left channel", "right channel", "fade left to right", "fade right to left", "fade in", "fade out", "custom"};
            long effect = n(r, "soundEffect", 0);
            if (effect >= 0 && effect < effects.length) {
                e.setAttribute("soundEffect", effects[(int) effect]);
            } else {
                issue("SOUND_EFFECT", r, "Unassigned sound effect " + effect);
            }
            Object envelope = r.get("soundEnvelope");
            if (envelope instanceof List && !((List<?>) envelope).isEmpty()) {
                Element out = child(e, "SoundEnvelope");
                for (Object point : (List<?>) envelope) {
                    List<?> values = (List<?>) point;
                    Element p = child(out, "SoundEnvelopePoint");
                    String[] attrs = {"mark44", "level0", "level1"};
                    for (int i = 0; i < 3; i++) {
                        p.setAttribute(attrs[i], values.get(i).toString());
                    }
                }
            }
        }

        private void videoInstance(Element parent, FlaRecord r) {
            String name = videos.get(n(r, "mediaId", -1));
            if (name == null) {
                issue("MISSING_VIDEO", r, "Video refers to unavailable media ID " + r.get("mediaId"));
                return;
            }
            Element e = child(parent, "DOMVideoInstance");
            attribute(e, "libraryItemName", name, r);
            attribute(e, "name", s(r, "instanceName", ""), r);
            e.setAttribute("selected", Boolean.toString((n(r, "flags", 0) & 2) != 0));
            FlaBounds b = r.getBounds();
            if (b != null && b.isDefined()) {
                int[] values = b.getWords();
                String[] attrs = {"frameLeft", "frameRight", "frameTop", "frameBottom"};
                for (int i = 0; i < 4; i++) {
                    e.setAttribute(attrs[i], Integer.toString(values[i]));
                }
            }
            matrix(e, r.getMatrix());
            XflObjectWriter.transformPoint(this, e, r);
        }

        XflBitmapWriter.Bitmap bitmapSmoothing(XflBitmapWriter.Bitmap original, boolean smoothing, FlaRecord location) {
            if (original.smoothed == smoothing) {
                return original;
            }
            XflBitmapWriter.Bitmap cached = smoothingVariants.get(original);
            if (cached != null) {
                return cached;
            }
            org.w3c.dom.NodeList children = mediaElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (!(children.item(i) instanceof Element)) {
                    continue;
                }
                Element item = (Element) children.item(i);
                if (!item.getTagName().equals("DOMBitmapItem") || !item.getAttribute("name").equals(original.name)) {
                    continue;
                }
                Element variant = (Element) item.cloneNode(true);
                String name = unique(original.name + (smoothing ? " (smoothed)" : " (unsmoothed)"), location);
                variant.setAttribute("name", name);
                variant.removeAttribute("itemID");
                variant.setAttribute("allowSmoothing", Boolean.toString(smoothing));
                for (String attr : new String[]{"linkageExportForAS", "linkageExportForRS", "linkageImportForRS", "linkageExportInFirstFrame", "linkageClassName", "linkageBaseClass", "linkageIdentifier", "linkageURL"}) {
                    variant.removeAttribute(attr);
                }
                mediaElement.appendChild(variant);
                XflBitmapWriter.Bitmap bitmap = new XflBitmapWriter.Bitmap(name, smoothing);
                smoothingVariants.put(original, bitmap);
                issue("BITMAP_VARIANT", location, "A library variant shares the original image/bin data to preserve this fill's smoothing setting: " + name);
                return bitmap;
            }
            throw new IllegalStateException("Exported bitmap has no library item: " + original.name);
        }

        private void bitmapInstance(Element parent, FlaRecord r) {
            FlaRecord raster = record(r, "raster");
            XflBitmapWriter.Bitmap bitmap;
            if (raster != null) {
                bitmap = inlineBitmaps.get(raster);
                if (bitmap == null) {
                    try {
                        int ordinal = ++bitmapOrdinal;
                        bitmap = new XflBitmapWriter(this).write(mediaElement, null, raster, raster,
                                "Recovered bitmap " + ordinal, ordinal);
                        inlineBitmaps.put(raster, bitmap);
                    } catch (IOException ex) {
                        issue("BITMAP_DATA", raster, ex.getMessage());
                        return;
                    }
                }
            } else {
                bitmap = bitmaps.get(n(r, "mediaId", -1));
            }
            if (bitmap == null) {
                issue("MISSING_BITMAP", r, "Instance refers to an unavailable bitmap ID " + r.get("mediaId"));
                return;
            }
            Element e = child(parent, "DOMBitmapInstance");
            attribute(e, "libraryItemName", bitmap.name, r);
            e.setAttribute("selected", Boolean.toString((n(r, "flags", 0) & 2) != 0));
            matrix(e, r.getMatrix());
            XflObjectWriter.transformPoint(this, e, r);
            XflPersistentDataWriter.write(this, e, r);
            if ((n(r, "flags", 0) & 4) != 0) {
                issue("OBJECT_LOCK", r, "Bitmap instance lock flag is not exported yet.");
            }
        }

    }
}
