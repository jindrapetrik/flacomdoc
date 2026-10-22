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
import com.jpexs.flash.fla.converter.streams.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import org.w3c.dom.*;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Reads XFL directly into the shared reader records using CS4-compatible
 * revisions.
 */
public final class XflReader {

    private final Charset charset;

    public XflReader() {
        this(Charset.forName("windows-1252"));
    }

    public XflReader(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
    }

    public BinaryFlaDocument read(File file) throws IOException {
        InputStorageInterface input = file.isDirectory() ? new DirectoryInputStorage(file) : file.getName().toLowerCase(Locale.ROOT).endsWith(".xfl") ? new DirectoryInputStorage(file.getAbsoluteFile().getParentFile()) : new ZippedInputStorage(file);
        try {
            return read(input);
        } finally {
            try {
                input.close();
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
    }

    /**
     * The caller owns the supplied storage. No binary converter is invoked.
     */
    public BinaryFlaDocument read(InputStorageInterface input) throws IOException {
        try {
            return new Session(input, charset).read();
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid XFL: " + ex.getMessage(), ex);
        }
    }

    static final class Session {

        static final FlaFormatVersion V = FlaFormatVersion.CS4;
        final InputStorageInterface input;
        final FlaDocumentBuilder builder;
        final Map<String, FlaRecord> symbols = new LinkedHashMap<>(), media = new LinkedHashMap<>();
        final Map<Element, String> locations = new IdentityHashMap<>();
        final Map<String, Element> symbolXml = new LinkedHashMap<>();
        final Map<Element, FlaRecord> generatedItems = new IdentityHashMap<>();
        final Set<String> itemIds = new HashSet<>();
        long nextGeneratedItemId = 1;
        final List<FlaRecord> library = new ArrayList<>(), mediaList = new ArrayList<>(), scenes = new ArrayList<>();
        FlaRecord contents;
        Element document;

        Session(InputStorageInterface input, Charset charset) {
            this.input = input;
            builder = new FlaDocumentBuilder(charset);
        }

        byte[] resource(String path, boolean required) throws IOException {
            path = path.replace('\\', '/');
            if (path.startsWith("/") || path.contains(":")) {
                throw new IOException("XFL resource must be project-relative: " + path);
            }
            for (String part : path.split("/", -1)) {
                if (part.equals("..") || part.equals(".") || part.isEmpty()) {
                    throw new IOException("Invalid XFL resource path: " + path);
                }
            }
            byte[] data = bytes(input.readFile(path));
            if (data == null && required) {
                throw new IOException("Missing XFL resource: " + path);
            }
            return data;
        }

        Element load(String path, boolean required) throws IOException {
            byte[] data = resource(path, required);
            if (data == null) {
                return null;
            }
            Element e = parse(data, path).getDocumentElement();
            locate(e, path);
            return e;
        }

        void locate(Element e, String path) {
            locations.put(e, path);
            String itemId = s(e, "itemID", "");
            if (!itemId.isEmpty()) {
                itemIds.add(itemKey(itemId));
            }
            for (Element c : children(e)) {
                locate(c, path);
            }
        }

        void issue(Element e, String text) {
            builder.unsupported(locations.getOrDefault(e, "DOMDocument.xml"), tag(e) + ": " + text);
        }

        BinaryFlaDocument read() throws IOException {
            document = load("DOMDocument.xml", true);
            if (!tag(document).equals("DOMDocument")) {
                throw new IOException("Expected DOMDocument root");
            }
            contents = r("Contents", "revision", V.contentsRevision, "boundsWords", Arrays.asList(0, twips(d(document, "width", 550)), 0, twips(d(document, "height", 400))), "frameRateWord", Math.round(d(document, "frameRate", 24) * 65536), "backgroundColorRgba", rgba(document, "backgroundColor", "alpha", 0xffffffffL));
            for (Element include : children(child(document, "symbols"), "Include")) {
                String href = s(include, "href", "");
                if (href.isEmpty()) {
                    throw new IOException("Symbol Include has no href");
                }
                Element root = load("LIBRARY/" + href, true);
                String name = s(root, "name", href);
                if (symbolXml.containsKey(name)) {
                    throw new IOException("Duplicate library symbol " + name);
                }
                symbolXml.put(name, root);
            }
            // Reserve explicit IDs from every library XML before generating missing IDs.
            new XflDocumentReader(this).settings();
            int id = 0;
            for (Map.Entry<String, Element> entry : symbolXml.entrySet()) {
                FlaRecord descriptor = descriptor(entry.getValue(), "S " + (++id), id);
                symbols.put(entry.getKey(), descriptor);
                library.add(descriptor);
            }
            new XflMediaReader(this).read();
            for (Map.Entry<String, Element> entry : symbolXml.entrySet()) {
                Element root = entry.getValue();
                Element timeline = nested(root, "timeline", "DOMTimeline");
                if (timeline == null) {
                    throw new IOException("Missing timeline for symbol " + entry.getKey());
                }
                FlaRecord page = new XflTimelineReader(this).timeline(timeline);
                builder.stream((String) symbols.get(entry.getKey()).get("streamName"), FlaStream.Kind.PAGE, page);
            }
            int scene = 0;
            for (Element timeline : children(child(document, "timelines"), "DOMTimeline")) {
                // Scenes have no library symbol ID. A nonzero ID makes the
                // editor treat their workspace as symbol editing (no stage).
                FlaRecord descriptor = descriptor(timeline, "P " + (++scene), 0);
                descriptor.set("symbolType", 0);
                scenes.add(descriptor);
                builder.stream((String) descriptor.get("streamName"), FlaStream.Kind.PAGE, new XflTimelineReader(this).timeline(timeline));
            }
            if (scenes.isEmpty()) {
                throw new IOException("XFL document contains no timeline");
            }
            contents.set("scenes", scenes);
            contents.set("library", library);
            contents.set("media", mediaList);
            contents.set("selectedScene", scenes.get(Math.min(scenes.size() - 1, Math.max(0, i(document, "currentTimeline", 1) - 1))));
            contents.set("nextSceneId", Math.max(scene + 1, i(document, "nextSceneIdentifier", scene + 1)));
            contents.set("nextItemTag", library.size() + 1);
            contents.set("libraryCounter", library.size() + 1);
            contents.set("mediaCounter", mediaList.size() + 1);
            contents.set("nextMediaIdentifier", (long) mediaList.size() + 1);
            for (Element e : children(document)) {
                if (!Arrays.asList("symbols", "timelines", "media", "fonts", "folders", "swatchLists", "extendedSwatchLists", "PrinterSettings", "persistentData", "swcCache", "publishHistory").contains(tag(e))) {
                    issue(e, "Document extension is not represented by the binary model yet");
                }
            }
            builder.stream("Contents", FlaStream.Kind.CONTENTS, contents);
            return builder.build();
        }

        FlaRecord descriptor(Element e, String stream, long id) throws IOException {
            FlaRecord r = r("CDocumentPage", "revision", V.documentPageRevision, "streamName", stream, "name", baseName(s(e, "name", "Scene 1")), "libraryId", id, "symbolType", choice(e, "symbolType", tag(e).equals("DOMTimeline") ? 0 : 2, "graphic", "button", "movie clip", "video", "font", "ik container"), "sourceTime", timestamp(e, "lastModified"), "placeholder", 1L);
            if (tag(e).equals("DOMTimeline")) {
                r.set("name", s(e, "name", "Scene 1"));
            }
            if (child(e, "parametersAsXML") != null) {
                issue(e, "Component definition parameters are not imported yet");
            }
            r.set("libraryMetadata", metadata(e));
            r.set("linkage", linkage(e));
            r.set("lastUniqueIdentifier", (long) i(e, "lastUniqueIdentifier", 0));
            boolean grid = e.hasAttribute("scaleGridLeft");
            r.set("scaleGridEnabled", grid ? 1L : 0L);
            // Scaling grids store right/left/bottom/top, unlike ordinary bounds.
            r.set("scaleGridBounds", grid ? Arrays.asList(twips(d(e, "scaleGridRight", 0)), twips(d(e, "scaleGridLeft", 0)), twips(d(e, "scaleGridBottom", 0)), twips(d(e, "scaleGridTop", 0))) : Arrays.asList(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
            return r;
        }

        FlaRecord metadata(Element e) throws IOException {
            String full = s(e, "name", "");
            String parent = full.contains("/") ? full.substring(0, full.lastIndexOf('/')) : "";
            FlaRecord parentItem = itemId("");
            for (Element folder : children(child(document, "folders"), "DOMFolderItem")) {
                if (s(folder, "name", "").equals(parent)) {
                    parentItem = item(folder);
                }
            }
            return r("libraryMetadata", "revision", 6, "item", item(e), "parentItem", parentItem, "linkage", linkage(e), "attributes", 0L, "persistentData", XflObjectReader.persistent(e));
        }

        FlaRecord item(Element e) {
            String explicit = s(e, "itemID", "");
            if (!explicit.isEmpty()) {
                return itemId(explicit);
            }
            FlaRecord existing = generatedItems.get(e);
            if (existing != null) {
                return existing;
            }
            String generated;
            do {
                generated = String.format(Locale.ROOT, "ffffffff-%08x", nextGeneratedItemId++);
            } while (!itemIds.add(generated));
            FlaRecord result = itemId(generated);
            generatedItems.put(e, result);
            return result;
        }

        static String itemKey(String value) {
            if (!value.matches("[0-9a-fA-F]{1,8}-[0-9a-fA-F]{1,8}")) {
                throw new IllegalArgumentException("Invalid itemID " + value);
            }
            String[] parts = value.split("-");
            return String.format(Locale.ROOT, "%08x-%08x", Long.parseLong(parts[0], 16), Long.parseLong(parts[1], 16));
        }

        static String baseName(String name) {
            return name.substring(name.lastIndexOf('/') + 1);
        }

        FlaRecord itemId(String value) {
            if (value.isEmpty()) {
                return r("item", "marker", 0L);
            }
            String[] parts = itemKey(value).split("-");
            return r("item", "marker", 1L, "firstPart", Long.parseLong(parts[0], 16), "secondPart", Long.parseLong(parts[1], 16));
        }

        FlaRecord linkage(Element e) {
            boolean rs = b(e, "linkageImportForRS", false), as = b(e, "linkageExportForAS", false), exp = b(e, "linkageExportForRS", false);
            int flags = rs ? 2 : (as || exp) ? 1 : 0;
            int exportFlags = (as ? 1 : 0) | (exp ? 2 : 0) | (b(e, "linkageExportInFirstFrame", true) ? 4 : 0);
            return r("linkage", "revision", V.documentPageLinkageRevision, "flags", (long) flags, "identifier", s(e, "linkageIdentifier", ""), "className", s(e, "linkageClassName", ""), "baseClass", s(e, "linkageBaseClass", ""), "url", s(e, "linkageURL", ""), "exportFlags", exportFlags, "auxiliary", r("auxiliary", "revision", 1, "sourcePath", s(e, "sourceLibraryItemHRef", ""), "sourceItemName", s(e, "name", ""), "sourceLastModified", timestamp(e, "sourceLastModified")));
        }
    }
}
