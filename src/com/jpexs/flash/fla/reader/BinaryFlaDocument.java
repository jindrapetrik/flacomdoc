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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detached in-memory binary FLA document. No source file remains open after
 * loading.
 */
public final class BinaryFlaDocument {

    private final FlaStorage rootStorage;
    private final Map<String, FlaStream> streams;
    private final Charset ansiCharset;
    private final List<Page> pages = new ArrayList<>();
    private final List<Media> media = new ArrayList<>();

    BinaryFlaDocument(FlaStorage rootStorage, Map<String, FlaStream> streams, Charset ansiCharset) {
        this.rootStorage = rootStorage;
        this.streams = Collections.unmodifiableMap(new LinkedHashMap<>(streams));
        this.ansiCharset = ansiCharset;
    }

    public FlaStorage getRootStorage() {
        return rootStorage;
    }

    public Map<String, FlaStream> getStreams() {
        return streams;
    }

    public Charset getAnsiCharset() {
        return ansiCharset;
    }

    public FlaStream getContentsStream() {
        return getStream("Contents");
    }

    /**
     * Shared-library collection snapshot; null when absent or incomplete.
     */
    public FlaSharedLibraries getSharedLibraries() {
        Object value = getContents().get("sharedLibraries");
        return value instanceof FlaRecord ? FlaSharedLibraries.fromCollection((FlaRecord) value) : null;
    }

    public FlaRecord getContents() {
        return getContentsStream().getRoot();
    }

    public int getContentsRevision() {
        return ((Number) getContents().get("revision")).intValue();
    }

    public List<Page> getPages() {
        return Collections.unmodifiableList(pages);
    }

    public List<Media> getMedia() {
        return Collections.unmodifiableList(media);
    }

    /**
     * Width/height in twips, when the core Contents settings have been decoded.
     */
    public Long getWidthTwips() {
        FlaBounds bounds = getStageBounds();
        return bounds == null ? null : bounds.getWidthTwips();
    }

    public Long getHeightTwips() {
        FlaBounds bounds = getStageBounds();
        return bounds == null ? null : bounds.getHeightTwips();
    }

    public Double getFrameRate() {
        Number value = (Number) getContents().get("frameRateWord");
        return value == null ? null : value.longValue() / 65536.0;
    }

    /**
     * Raw stage bounds, or null when their complete field was not reached.
     */
    public FlaBounds getStageBounds() {
        return getContents().getBounds("boundsWords");
    }

    /**
     * Paths are storage-relative; CFB lookup is case-insensitive.
     */
    public FlaStream getStream(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        FlaStream result = streams.get(path);
        if (result == null) {
            for (Map.Entry<String, FlaStream> entry : streams.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(path)) {
                    return entry.getValue();
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<FlaRecord> getSceneDescriptors() {
        return records("scenes");
    }

    public List<FlaRecord> getLibraryDescriptors() {
        return records("library");
    }

    public List<FlaRecord> getMediaDescriptors() {
        return records("media");
    }

    @SuppressWarnings("unchecked")
    private List<FlaRecord> records(String name) {
        Object value = getContents().get(name);
        return value instanceof List ? Collections.unmodifiableList((List<FlaRecord>) value) : Collections.emptyList();
    }

    public List<FlaParseIssue> getIssues() {
        List<FlaParseIssue> result = new ArrayList<>();
        for (FlaStream stream : streams.values()) {
            result.addAll(stream.getIssues());
        }
        return Collections.unmodifiableList(result);
    }

    void add(Page page) {
        pages.add(page);
    }

    void add(Media item) {
        media.add(item);
    }

    public static final class Page {

        public enum Role {
            SCENE, LIBRARY, UNLINKED
        }
        private final FlaStream stream;
        private final FlaRecord descriptor;
        private final Role role;

        Page(FlaStream stream, FlaRecord descriptor, Role role) {
            this.stream = stream;
            this.descriptor = descriptor;
            this.role = role;
        }

        public Role getRole() {
            return role;
        }

        public FlaStream getStream() {
            return stream;
        }

        /**
         * May be null if the descriptor lies in an unsupported Contents suffix.
         */
        public FlaRecord getDescriptor() {
            return descriptor;
        }

        public FlaRecord getRoot() {
            return stream == null ? null : stream.getRoot();
        }

        public String getName() {
            return descriptor == null ? null : (String) descriptor.get("name");
        }

        public Long getLibraryId() {
            return descriptor == null ? null : (Long) descriptor.get("libraryId");
        }
    }

    public static final class Media {

        private final FlaStream stream;
        private final FlaRecord descriptor;

        Media(FlaStream stream, FlaRecord descriptor) {
            this.stream = stream;
            this.descriptor = descriptor;
        }

        /**
         * Null means that the descriptor references a stream missing from this
         * file.
         */
        public FlaStream getStream() {
            return stream;
        }

        public FlaRecord getDescriptor() {
            return descriptor;
        }

        /**
         * Imported sample format, including historical revision normalization;
         * null for other media classes or an incomplete format prefix.
         */
        public FlaSoundFormat getSoundFormat() {
            if (!descriptor.getClassName().equals("CMediaSound")) {
                return null;
            }
            Object flags = descriptor.get("formatFlags");
            Object revision = descriptor.get("revision");
            return flags instanceof Number && revision instanceof Number
                    ? FlaSoundFormat.fromStoredFlags(((Number) flags).intValue(), ((Number) revision).intValue()) : null;
        }

        /**
         * Secondary-buffer format; its flags do not use the primary legacy
         * adjustment.
         */
        public FlaSoundFormat getSecondarySoundFormat() {
            Object flags = descriptor.get("secondaryFormatFlags");
            return descriptor.getClassName().equals("CMediaSound") && flags instanceof Number
                    ? FlaSoundFormat.fromFlags(((Number) flags).intValue()) : null;
        }
    }
}
