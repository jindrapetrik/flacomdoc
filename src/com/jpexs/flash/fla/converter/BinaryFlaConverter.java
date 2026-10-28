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
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Direct binary FLA version conversion. No XFL export or XML import is used.
 */
public final class BinaryFlaConverter {

    private final FlaFormatVersion target;
    private final Charset charset;

    public BinaryFlaConverter(FlaFormatVersion target) {
        this(target, Charset.forName("windows-1252"));
    }

    public BinaryFlaConverter(FlaFormatVersion target, String charset) {
        this(target, Charset.forName(charset));
    }

    public BinaryFlaConverter(FlaFormatVersion target, Charset charset) {
        this.target = Objects.requireNonNull(target, "target");
        this.charset = Objects.requireNonNull(charset, "charset");
    }

    public static boolean isBinaryFla(File file) throws IOException {
        byte[] magic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        try (InputStream in = new FileInputStream(file)) {
            for (byte b : magic) {
                if (in.read() != (b & 255)) {
                    return false;
                }
            }
            return true;
        }
    }

    public BinaryFlaConversionResult convert(File input) throws IOException {
        return convert(new BinaryFlaReader(charset).read(input));
    }

    public BinaryFlaConversionResult convert(BinaryFlaDocument source) throws IOException {
        Objects.requireNonNull(source, "source");
        return new Session(source, target, charset).convert();
    }

    static final class Session {

        final BinaryFlaDocument source;
        final FlaFormatVersion target;
        final Charset charset;
        final IdentityHashMap<FlaRecord, Map<String, Object>> overrides = new IdentityHashMap<>();
        final Map<String, BinaryData> replacementMedia = new LinkedHashMap<>();
        final Set<Integer> unavailableMedia = new HashSet<>();
        final Map<String, BinaryData> streams = new LinkedHashMap<>();
        final List<BinaryFlaConversionIssue> issues = new ArrayList<>();
        final IdentityHashMap<FlaRecord, Set<String>> used = new IdentityHashMap<>();
        final IdentityHashMap<FlaRecord, String> locations = new IdentityHashMap<>();
        final Set<String> issueKeys = new LinkedHashSet<>();
        final Map<String, FlaRecord> pageRoots = new LinkedHashMap<>();
        FlaRecord contents;
        final Set<FlaRecord> dropped = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());

        Session(BinaryFlaDocument source, FlaFormatVersion target, Charset charset) {
            this.source = source;
            this.target = target;
            this.charset = charset;
            this.contents = source.getContents();
        }

        void issue(String code, String path, FlaRecord r, String field, String text) {
            String key = code + ":" + path + ":" + (r == null ? "" : r.getClassName() + "@" + r.getOffset()) + ":" + field + ":" + text;
            if (issueKeys.add(key)) {
                issues.add(new BinaryFlaConversionIssue(code, path, r == null ? "" : r.getClassName(), field, text));
            }
        }

        String outputClass(FlaRecord r) {
            if (r.getClassName().equals("CPicSprite") && target.ordinal() <= FlaFormatVersion.F2.ordinal()) {
                issue("MOVIE_CLIP", locations.getOrDefault(r, ""), r, "", "Movie clip saved as a graphic symbol; independent playback, instance name and clip actions are unavailable.");
                return "CPicSymbol";
            }
            if ((r.getClassName().equals("CPicRect") || r.getClassName().equals("CPicOval")) && target.ordinal() < FlaFormatVersion.CS4.ordinal()) {
                issue("PRIMITIVE_GEOMETRY", locations.getOrDefault(r, ""), r, "", "Primitive saved as ordinary shape geometry.");
                return "CPicShape";
            }
            return r.getClassName();
        }

        boolean supports(FlaRecord r) {
            String c = r.getClassName();
            boolean supported = !dropped.contains(r) && BinaryObjectWriter.CLASSES.contains(c)
                    && (!c.equals("CCommandObj") || target.ordinal() >= FlaFormatVersion.F4.ordinal())
                    && (!c.equals("CPicScreen") || target.ordinal() >= FlaFormatVersion.MX2004.ordinal())
                    && (!c.equals("CPicFont") || target.ordinal() >= FlaFormatVersion.F5.ordinal())
                    && (!(c.equals("CPicVideo") || c.equals("CMediaVideo")) || target.ordinal() >= FlaFormatVersion.MX.ordinal())
                    && (!c.equals("CPicTempClipboardObj") || source.getContentsRevision() <= target.contentsRevision)
                    && (!(c.equals("CMediaVideoStream") || c.equals("CPicVideoStream")) || target.ordinal() >= FlaFormatVersion.MX.ordinal())
                    && (!(c.equals("CSwfPage") || c.equals("CPicSwf")) || target.ordinal() >= FlaFormatVersion.MX2004.ordinal());
            if (!supported && dropped.add(r)) {
                issue("OBJECT_OMITTED", locations.getOrDefault(r, ""), r, "", "Object is unavailable in target " + target + ".");
            }
            return supported;
        }

        BinaryFlaConversionResult convert() throws IOException {
            for (FlaParseIssue issue : source.getIssues()) {
                issue("SOURCE_PARSE", issue.getStreamPath(), null, "", issue.toString());
            }
            for (FlaStream stream : source.getStreams().values()) {
                for (FlaRecord record : stream.getObjects()) {
                    locations.putIfAbsent(record, stream.getPath());
                }
            }
            preparePages();
            new BinaryMediaWriter(this).prepare();
            BinaryArchiveWriter contents = new BinaryArchiveWriter(this, "Contents");
            new BinaryContentsWriter(contents).write(this.contents);
            streams.put("Contents", contents.finish());
            for (Map.Entry<String, FlaRecord> page : pageRoots.entrySet()) {
                BinaryArchiveWriter out = new BinaryArchiveWriter(this, page.getKey());
                FlaRecord root = page.getValue();
                out.u8(root.getClassName().equals("CPicShape") ? 0 : 1);
                out.object(root);
                streams.put(out.path, out.finish());
            }
            new BinaryMediaWriter(this).writeStreams();
            for (FlaStream stream : source.getStreams().values()) {
                if (!streams.containsKey(stream.getPath()) && stream.getKind() == FlaStream.Kind.OTHER) {
                    streams.put(stream.getPath(), stream.getData());
                    issue("OPAQUE_STREAM", stream.getPath(), null, "", "Uninterpreted stream retained unchanged.");
                }
            }
            reportUnused();
            Map<String, byte[]> bytes = new LinkedHashMap<>();
            streams.forEach((p, d) -> bytes.put(p, d.toByteArray()));
            BinaryFlaDocument check = new BinaryFlaReader(charset).readStreams(bytes);
            if (!check.getIssues().isEmpty()) {
                throw new IOException("Generated binary FLA failed structural verification: " + check.getIssues().get(0));
            }
            issues.sort(Comparator.comparing(BinaryFlaConversionIssue::getStreamPath)
                    .thenComparing(BinaryFlaConversionIssue::getRecordClass)
                    .thenComparing(BinaryFlaConversionIssue::getField)
                    .thenComparing(BinaryFlaConversionIssue::getCode)
                    .thenComparing(BinaryFlaConversionIssue::getMessage));
            return new BinaryFlaConversionResult(target, streams, issues);
        }

        private void preparePages() throws IOException {
            Set<String> preparedPaths = new HashSet<>();
            for (BinaryFlaDocument.Page page : source.getPages()) {
                FlaStream stream = page.getStream();
                FlaRecord descriptor = page.getDescriptor();
                Object reference = descriptor == null ? null : descriptor.get("streamName");
                String path = stream != null ? stream.getPath() : reference instanceof String ? (String) reference : "";
                if (path.isEmpty()) {
                    throw new IOException("Page has neither a source stream nor a stream name in its descriptor");
                }
                if (!preparedPaths.add(path.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                FlaRecord root = page.getRoot();
                if (root == null || !(root.getClassName().equals("CPicPage") || root.getClassName().equals("CPicShape"))) {
                    root = emptyPage(Collections.emptyList());
                    issue("PAGE_PLACEHOLDER", path, descriptor, "streamName",
                            (stream == null ? "Missing page stream" : "Incomplete page") + " replaced with an empty timeline.");
                }
                pageRoots.put(path, root);
            }
            if (source.getContentsRevision() == 1) {
                Object objects = source.getContents().get("historicalObjects");
                @SuppressWarnings("unchecked")
                List<FlaRecord> children = objects instanceof List ? (List<FlaRecord>) objects : Collections.emptyList();
                FlaRecord page = emptyPage(children), descriptor = FlaRecord.create("CDocumentPage");
                descriptor.set("streamName", "Page 1");
                descriptor.set("name", "Scene 1");
                descriptor.set("libraryId", 1L);
                FlaRecord copy = FlaRecord.create("Contents");
                source.getContents().getFields().forEach(copy::set);
                copy.set("scenes", Collections.singletonList(descriptor));
                copy.set("selectedScene", descriptor);
                copy.set("nextSceneId", 2);
                copy.set("nextItemTag", 1);
                copy.set("boundsWords", Arrays.asList(0, 11000, 0, 8000));
                contents = copy;
                pageRoots.put("Page 1", page);
                issue("HISTORICAL_DOCUMENT", "Contents", source.getContents(), "historicalObjects", "Legacy object list wrapped in a scene with default stage settings.");
            }
        }

        private FlaRecord emptyPage(List<FlaRecord> children) {
            FlaRecord frame = FlaRecord.create("CPicFrame"), layer = FlaRecord.create("CPicLayer"), page = FlaRecord.create("CPicPage");
            frame.set("duration", 1);
            frame.set("keyMode", 9728);
            frame.set("children", children);
            layer.set("name", "Layer 1");
            layer.set("children", Collections.singletonList(frame));
            page.set("children", Collections.singletonList(layer));
            return page;
        }

        private void reportUnused() {
            for (Map.Entry<FlaRecord, Set<String>> entry : used.entrySet()) {
                FlaRecord r = entry.getKey();
                for (Map.Entry<String, Object> field : r.getFields().entrySet()) {
                    String name = field.getKey();
                    Object value = field.getValue();
                    if (!entry.getValue().contains(name) && !name.endsWith("Count") && !name.equals("count") && !name.equals("pageKind") && !name.endsWith("Present") && !name.equals("unicodeContext") && !name.equals("isCompressed")
                            && meaningful(value)) {
                        issue("FIELD_OMITTED", locations.get(r), r, name, "Field is not represented by the selected target record.");
                    }
                }
            }
        }

        private boolean meaningful(Object value) {
            return value != null && !Boolean.FALSE.equals(value) && !(value instanceof String && ((String) value).isEmpty())
                    && !(value instanceof Number && ((Number) value).doubleValue() == 0)
                    && !(value instanceof Collection && ((Collection<?>) value).isEmpty())
                    && !(value instanceof BinaryData && ((BinaryData) value).size() == 0);
        }
    }
}
