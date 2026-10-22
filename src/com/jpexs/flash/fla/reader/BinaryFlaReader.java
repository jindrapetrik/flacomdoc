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

import com.jpexs.cfb.CompoundFileBinary;
import com.jpexs.cfb.DirectoryEntry;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loads the complete compound document and incrementally decodes known FLA
 * records.
 */
public final class BinaryFlaReader {

    private static final byte[] CFB_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
        (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private final FlaReadOptions options;
    private final Map<String, FlaRecordDecoder> decoders = DefaultFlaDecoders.create();

    public BinaryFlaReader() {
        this(new FlaReadOptions());
    }

    public BinaryFlaReader(Charset ansiCharset) {
        this(new FlaReadOptions(ansiCharset, 128 * 1024 * 1024, 512L * 1024 * 1024, 1000000, 128));
    }

    public BinaryFlaReader(FlaReadOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Configure decoders before loading; a decoder must consume its exact class
     * payload.
     */
    public BinaryFlaReader registerDecoder(String className, FlaRecordDecoder decoder) {
        decoders.put(Objects.requireNonNull(className, "className"), Objects.requireNonNull(decoder, "decoder"));
        return this;
    }

    public BinaryFlaDocument read(File file) throws IOException {
        byte[] signature = new byte[8];
        try (InputStream in = new FileInputStream(file)) {
            readFully(in, signature);
        }
        if (!Arrays.equals(signature, CFB_SIGNATURE)) {
            throw new IOException("Expected a legacy binary FLA compound document; ZIP/XFL is not supported by this reader");
        }
        Map<String, FlaStream> streams = new LinkedHashMap<>();
        FlaStorage root;
        try (CompoundFileBinary cfb = new CompoundFileBinary(file)) {
            Map<Long, DirectoryEntry> entries = new HashMap<>();
            DirectoryEntry rootEntry = null;
            for (DirectoryEntry entry : cfb.getDirectoryEntries()) {
                if (entry.objectType == CompoundFileBinary.TYPE_UNKNOWN) {
                    continue;
                }
                if (entries.put(entry.streamId, entry) != null) {
                    throw new IOException("Duplicate CFB directory ID");
                }
                if (entry.objectType == CompoundFileBinary.TYPE_ROOT_STORAGE_OBJECT) {
                    if (rootEntry != null) {
                        throw new IOException("Multiple CFB root storages");
                    }
                    rootEntry = entry;
                }
            }
            if (rootEntry == null) {
                throw new IOException("CFB root storage is missing");
            }
            root = storage(rootEntry, "");
            Set<Long> visited = new HashSet<>();
            Set<String> paths = new HashSet<>();
            visited.add(rootEntry.streamId);
            Deque<DirectoryTask> pending = new ArrayDeque<>();
            if (rootEntry.childId != CompoundFileBinary.NOSTREAM) {
                pending.push(new DirectoryTask(rootEntry.childId, root, 1));
            }
            long totalBytes = 0;
            while (!pending.isEmpty()) {
                DirectoryTask task = pending.pop();
                if (task.depth > options.getMaxDepth()) {
                    throw new IOException("CFB storage nesting exceeds read limit");
                }
                DirectoryEntry entry = entries.get(task.id);
                if (entry == null || !visited.add(task.id)) {
                    throw new IOException("Invalid or cyclic CFB directory tree");
                }
                if (entry.objectType != CompoundFileBinary.TYPE_STORAGE_OBJECT && entry.objectType != CompoundFileBinary.TYPE_STREAM_OBJECT) {
                    throw new IOException("Invalid CFB child entry type");
                }
                if (entry.name.isEmpty() || entry.name.indexOf('/') >= 0) {
                    throw new IOException("Invalid CFB entry name");
                }
                String path = task.parent.getPath().isEmpty() ? entry.name : task.parent.getPath() + "/" + entry.name;
                if (!paths.add(path.toUpperCase(Locale.ROOT))) {
                    throw new IOException("Duplicate CFB entry path " + path);
                }
                if (entry.objectType == CompoundFileBinary.TYPE_STORAGE_OBJECT) {
                    FlaStorage child = storage(entry, path);
                    task.parent.add(child);
                    if (entry.childId != CompoundFileBinary.NOSTREAM) {
                        pending.push(new DirectoryTask(entry.childId, child, task.depth + 1));
                    }
                } else {
                    checkSize(entry.streamSize, totalBytes);
                    totalBytes += entry.streamSize;
                    byte[] bytes = new byte[(int) entry.streamSize];
                    try (InputStream in = cfb.getEntryStream(entry)) {
                        readFully(in, bytes);
                    }
                    FlaStream stream = new FlaStream(path, entry.streamId, BinaryData.takeOwnership(bytes));
                    addStream(streams, stream);
                    task.parent.add(stream);
                }
                if (entry.leftSiblingId != CompoundFileBinary.NOSTREAM) {
                    pending.push(new DirectoryTask(entry.leftSiblingId, task.parent, task.depth));
                }
                if (entry.rightSiblingId != CompoundFileBinary.NOSTREAM) {
                    pending.push(new DirectoryTask(entry.rightSiblingId, task.parent, task.depth));
                }
            }
            if (visited.size() != entries.size()) {
                throw new IOException("Unreachable CFB directory entries");
            }
        }
        return decode(new BinaryFlaDocument(root, streams, options.getAnsiCharset()));
    }

    /**
     * Loads already extracted logical streams, copying the supplied arrays.
     */
    public BinaryFlaDocument readStreams(Map<String, byte[]> input) throws IOException {
        FlaStorage root = new FlaStorage("Root Entry", "", 0, new byte[16], 0);
        Map<String, FlaStorage> storages = new LinkedHashMap<>();
        storages.put("", root);
        Map<String, FlaStream> streams = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, byte[]> entry : input.entrySet()) {
            String path = entry.getKey();
            if (path == null || path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
                throw new IOException("Expected a nonempty, storage-relative stream path");
            }
            byte[] bytes = Objects.requireNonNull(entry.getValue(), "stream data");
            checkSize(bytes.length, total);
            total += bytes.length;
            String parent = "";
            String[] parts = path.split("/");
            if (parts.length > options.getMaxDepth()) {
                throw new IOException("Storage nesting exceeds read limit");
            }
            for (int i = 0; i < parts.length - 1; i++) {
                String next = parent.isEmpty() ? parts[i] : parent + "/" + parts[i];
                if (!storages.containsKey(next)) {
                    if (containsPath(storages, next) || containsPath(streams, next)) {
                        throw new IOException("Conflicting storage path " + next);
                    }
                    FlaStorage child = new FlaStorage(parts[i], next, -1, new byte[16], 0);
                    storages.get(parent).add(child);
                    storages.put(next, child);
                }
                parent = next;
            }
            FlaStream stream = new FlaStream(path, -1, new BinaryData(bytes));
            if (containsPath(storages, path)) {
                throw new IOException("Stream conflicts with storage " + path);
            }
            addStream(streams, stream);
            storages.get(parent).add(stream);
        }
        return decode(new BinaryFlaDocument(root, streams, options.getAnsiCharset()));
    }

    private BinaryFlaDocument decode(BinaryFlaDocument document) throws IOException {
        FlaStream contents = document.getContentsStream();
        if (contents == null || contents.getData().size() == 0) {
            throw new IOException("FLA Contents stream is missing or empty");
        }
        decodeContents(contents);
        int version = document.getContentsRevision();
        Set<FlaStream> pages = new HashSet<>();
        List<FlaRecord> descriptors = new ArrayList<>(document.getSceneDescriptors());
        descriptors.addAll(document.getLibraryDescriptors());
        for (int i = 0; i < descriptors.size(); i++) {
            FlaRecord descriptor = descriptors.get(i);
            Object name = descriptor.get("streamName");
            if (!(name instanceof String)) {
                continue;
            }
            FlaStream stream = document.getStream((String) name);
            document.add(new BinaryFlaDocument.Page(stream, descriptor, i < document.getSceneDescriptors().size()
                    ? BinaryFlaDocument.Page.Role.SCENE : BinaryFlaDocument.Page.Role.LIBRARY));
            if (stream == null) {
                missingReference(contents, "page", (String) name);
            } else if (stream == contents) {
                invalidContentsReference(contents);
            } else if (pages.add(stream)) {
                decodePage(stream, version);
            }
        }
        // Discover unlinked page streams by their actual archive header, never by a filename prefix.
        for (FlaStream stream : document.getStreams().values()) {
            if (stream != contents && !pages.contains(stream) && looksLikePage(stream.getData())) {
                decodePage(stream, version);
                pages.add(stream);
                document.add(new BinaryFlaDocument.Page(stream, null, BinaryFlaDocument.Page.Role.UNLINKED));
            }
        }
        for (FlaRecord descriptor : document.getMediaDescriptors()) {
            Object name = descriptor.get("streamName");
            if (!(name instanceof String)) {
                continue;
            }
            FlaStream stream = document.getStream((String) name);
            document.add(new BinaryFlaDocument.Media(stream, descriptor));
            if (stream == null) {
                missingReference(contents, "media", (String) name);
            } else if (stream == contents) {
                invalidContentsReference(contents);
            } else if (!pages.contains(stream)) {
                stream.setKind(FlaStream.Kind.MEDIA);
                if (descriptor.isComplete() && descriptor.getClassName().equals("CMediaBits")
                        && stream.getParseStatus() == FlaStream.ParseStatus.RAW) {
                    decodeBitmap(stream, descriptor, version);
                } else if (descriptor.isComplete() && stream.getParseStatus() == FlaStream.ParseStatus.RAW) {
                    if (descriptor.getClassName().equals("CMediaSound")) {
                        decodeMedia(stream, version, "soundPayload", (a, r) -> MediaPayloadRecordDecoder.sound(a, r, descriptor));
                    } else if (descriptor.getClassName().equals("CMediaVideoStream")) {
                        decodeMedia(stream, version, "videoPayload", MediaPayloadRecordDecoder::video);
                    } else if (descriptor.getClassName().equals("CSwfPage")) {
                        decodeMedia(stream, version, "swfPagePayload", (a, r) -> {
                        });
                    }
                }
            }
        }
        return document;
    }

    private void decodeMedia(FlaStream stream, int contentsRevision, String name, FlaRecordDecoder decoder) {
        MfcArchive a = archive(stream, contentsRevision);
        FlaRecord root = new FlaRecord(name, 0, 0, 0, 0, stream.getData());
        stream.setRoot(root);
        boolean complete = false;
        FlaParseIssue issue = null;
        try {
            decoder.read(a, root);
            if (a.getRemaining() != 0) {
                throw new UnsupportedFlaRecordException(name + " trailing data");
            }
            complete = true;
        } catch (IOException ex) {
            issue = issue(stream, a, ex);
        } finally {
            root.finish(a.getPosition(), complete);
            stream.parsed(a, complete, issue);
        }
    }

    private void decodeBitmap(FlaStream stream, FlaRecord descriptor, int contentsRevision) {
        MfcArchive a = archive(stream, contentsRevision);
        FlaRecord root = new FlaRecord("bitmapPayload", 0, 0, 0, 0, stream.getData());
        stream.setRoot(root);
        boolean complete = false;
        FlaParseIssue issue = null;
        try {
            Object stored = descriptor.get("encodedByteCount");
            int count = stored instanceof Number ? ((Number) stored).intValue() : 0;
            BinaryData data = stream.getData();
            // The historical stream loader recognizes this exact JFIF prefix only.
            if (count == 0 && data.size() >= 4 && data.unsignedByte(0) == 0xFF
                    && data.unsignedByte(1) == 0xD8 && data.unsignedByte(2) == 0xFF
                    && data.unsignedByte(3) == 0xE0) {
                if (data.size() <= 16) {
                    throw a.error("JPEG fallback has no image before its bounds");
                }
                count = data.size() - 16;
                root.set("jpegLengthFallback", true);
            }
            BitmapRecordDecoder.payload(a, root, count);
            if (a.getRemaining() != 0) {
                throw new UnsupportedFlaRecordException("Bitmap payload trailing data");
            }
            complete = true;
        } catch (IOException ex) {
            issue = issue(stream, a, ex);
        } finally {
            root.finish(a.getPosition(), complete);
            stream.parsed(a, complete, issue);
        }
    }

    private void decodeContents(FlaStream stream) {
        stream.setKind(FlaStream.Kind.CONTENTS);
        MfcArchive a = archive(stream, stream.getData().unsignedByte(0));
        FlaRecord root = new FlaRecord("Contents", 0, 0, 0, 0, stream.getData());
        stream.setRoot(root);
        boolean complete = false;
        FlaParseIssue issue = null;
        try {
            int v = a.readUI8();
            root.set("revision", v);
            if (v < 1 || v > 0x4A) {
                throw new UnsupportedFlaRecordException("Contents revision " + v);
            }
            if (v == 1) {
                a.readObjectList(root, "historicalObjects");
            } else {
                if (v >= 0x20) {
                    root.set("compatibilityMarker", a.readUI32());
                }
                long oleHeader = a.readUI32();
                root.set("oleBaseHeader", oleHeader);
                int oleCount = a.checkedCount(oleHeader, 2);
                a.readObjectList(root, "oleItems", oleCount);
                if (v >= 0x14) {
                    root.set("reserved1", a.readUI8());
                }
                if (v >= 0x15) {
                    root.set("reserved2", a.readUI8());
                }
                int[] thresholds = {0x21, 0x2B, 0x3B, 0x40, 0x44};
                for (int threshold : thresholds) {
                    if (v >= threshold) {
                        root.set("reservedAt" + threshold, a.readData(4));
                    }
                }
                a.readObjectList(root, "scenes");
                root.set("nextSceneId", a.readUI16());
                root.set("nextItemTag", a.readUI16());
                a.readObjectInto(root, "selectedScene");
                if (v >= 0x0E) {
                    a.readObjectList(root, "library");
                    root.set("libraryCounter", a.readUI16());
                }
                if (v >= 0x11) {
                    a.readObjectList(root, "media");
                    root.set("mediaCounter", a.readUI16());
                    root.set("rulerUnitType", a.readUI16());
                }
                root.set("gridDisplayMode", a.readUI8());
                root.set("boundsWords", signedWords(a, 4));
                root.set("gridSpacingXFixed16Twips", a.readUI32());
                root.set("previewAndRulerFlags", a.readData(3));
                if (v >= 0x0F) {
                    root.set("viewPlayAndPrinterFlags", a.readData(2));
                }
                root.set("printMarginLeftTwips", a.readI32());
                root.set("printMarginRightTwips", a.readI32());
                root.set("printMarginTopTwips", a.readI32());
                root.set("printMarginBottomTwips", a.readI32());
                root.set("printCenterHorizontal", a.readUI8());
                root.set("printCenterVertical", a.readUI8());
                root.set("printLayout", a.readUI8());
                root.set("printDisablePostscript", a.readUI8());
                root.set("printScaleFixed16", a.readI32());
                root.set("printerModeData", a.readData(a.readUI16()));
                root.set("printerNamesData", a.readData(a.readUI16()));
                if (v >= 0x0B) {
                    root.set("backgroundColorRgba", a.readUI32());
                    root.set("gridColorRgba", a.readUI32());
                }
                if (v >= 0x0D) {
                    root.set("frameRateWord", a.readUI32());
                }
                if (v >= 0x10) {
                    root.set("printAllFrames", a.readUI8());
                    root.set("printLabelPages", a.readUI8());
                    root.set("printFrameCountAcrossPage", a.readUI8());
                    root.set("printStoryboardMarginTwips", a.readI32());
                }
                ContentsRecordDecoder.extensions(a, root, v);
            }
            if (a.getRemaining() != 0 && root.get("writerIdentity") == null) {
                a.readInline(root, "compatibilityTail", (in, tail)
                        -> ContentsRecordDecoder.compatibilityTail(in, tail, root.get("sharedLibraries") == null));
            }
            if (a.getRemaining() != 0) {
                throw new UnsupportedFlaRecordException("Contents trailing data");
            }
            complete = true;
        } catch (IOException ex) {
            issue = issue(stream, a, ex);
        } finally {
            root.finish(a.getPosition(), complete);
            stream.parsed(a, complete, issue);
        }
    }

    private void decodePage(FlaStream stream, int contentsRevision) {
        stream.setKind(FlaStream.Kind.PAGE);
        MfcArchive a = archive(stream, contentsRevision);
        boolean complete = false;
        FlaParseIssue issue = null;
        try {
            int kind = a.readUI8();
            FlaRecord root = a.readObject();
            stream.setRoot(root);
            if (root == null) {
                throw a.error("Missing page root object");
            }
            root.set("pageKind", kind);
            String expected = kind == 0 ? "CPicShape" : "CPicPage";
            if (!root.getClassName().equals(expected)) {
                throw a.error("Expected " + expected + " page root");
            }
            if (a.getRemaining() != 0) {
                throw new UnsupportedFlaRecordException("Page trailing data");
            }
            complete = true;
        } catch (IOException ex) {
            issue = issue(stream, a, ex);
        } finally {
            stream.parsed(a, complete, issue);
        }
    }

    private MfcArchive archive(FlaStream stream, int revision) {
        return new MfcArchive(stream.getData(), stream.getPath(), options, new LinkedHashMap<>(decoders), revision);
    }

    private static FlaParseIssue issue(FlaStream stream, MfcArchive a, IOException ex) {
        return new FlaParseIssue(ex instanceof UnsupportedFlaRecordException ? FlaParseIssue.Kind.UNSUPPORTED
                : FlaParseIssue.Kind.MALFORMED, stream.getPath(), a.getPosition(), ex.getMessage());
    }

    private static void missingReference(FlaStream contents, String kind, String name) {
        contents.addIssue(new FlaParseIssue(FlaParseIssue.Kind.MALFORMED, contents.getPath(), 0,
                "Missing " + kind + " stream referenced by descriptor: " + name));
    }

    private static void invalidContentsReference(FlaStream contents) {
        contents.addIssue(new FlaParseIssue(FlaParseIssue.Kind.MALFORMED, contents.getPath(), 0,
                "Page or media descriptor refers to the Contents stream itself"));
    }

    private static List<Integer> signedWords(MfcArchive a, int count) throws IOException {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(a.readI32());
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean looksLikePage(BinaryData data) {
        if (data.size() < 16 || data.unsignedByte(1) != 255 || data.unsignedByte(2) != 255) {
            return false;
        }
        int length = data.unsignedByte(5) | data.unsignedByte(6) << 8;
        if (length < 1 || length > 63 || length > data.size() - 7) {
            return false;
        }
        String name = new String(data.slice(7, length).toByteArray(), java.nio.charset.StandardCharsets.US_ASCII);
        return data.unsignedByte(0) == 0 ? name.equals("CPicShape") : name.equals("CPicPage");
    }

    private void checkSize(long size, long total) throws IOException {
        if (size < 0 || size > options.getMaxStreamBytes() || size > options.getMaxDocumentBytes() - total) {
            throw new IOException("Compound document stream or total size exceeds read limits");
        }
    }

    private static void addStream(Map<String, FlaStream> streams, FlaStream stream) throws IOException {
        if (containsPath(streams, stream.getPath())) {
            throw new IOException("Duplicate stream path " + stream.getPath());
        }
        streams.put(stream.getPath(), stream);
    }

    private static boolean containsPath(Map<String, ?> entries, String path) {
        for (String entry : entries.keySet()) {
            if (entry.equalsIgnoreCase(path)) {
                return true;
            }
        }
        return false;
    }

    private static FlaStorage storage(DirectoryEntry entry, String path) {
        return new FlaStorage(entry.name, path, entry.streamId, entry.clsId, entry.stateBits);
    }

    private static void readFully(InputStream in, byte[] bytes) throws IOException {
        int position = 0;
        while (position < bytes.length) {
            int count = in.read(bytes, position, Math.min(8192, bytes.length - position));
            if (count <= 0) {
                throw new IOException("Truncated compound document stream");
            }
            position += count;
        }
    }

    private static final class DirectoryTask {

        final long id;
        final FlaStorage parent;
        final int depth;

        DirectoryTask(long id, FlaStorage parent, int depth) {
            this.id = id;
            this.parent = parent;
            this.depth = depth;
        }
    }
}
