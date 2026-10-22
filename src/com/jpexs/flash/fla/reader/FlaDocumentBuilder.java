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
import java.util.*;

/**
 * Constructs the reader model from semantic records, without a binary
 * intermediate.
 */
public final class FlaDocumentBuilder {

    private final Charset charset;
    private final Map<String, FlaStream> streams = new LinkedHashMap<>();
    private final List<FlaParseIssue> issues = new ArrayList<>();

    public FlaDocumentBuilder(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
    }

    public FlaDocumentBuilder stream(String path, FlaStream.Kind kind, FlaRecord root, BinaryData data) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(data);
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("\\")) {
            throw new IllegalArgumentException("Invalid model stream path: " + path);
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid model stream path: " + path);
            }
        }
        for (String existing : streams.keySet()) {
            if (existing.equalsIgnoreCase(path)) {
                throw new IllegalArgumentException("Duplicate model stream: " + path);
            }
        }
        streams.put(path, FlaStream.fromModel(path, streams.size() + 1L, kind, root, data));
        return this;
    }

    public FlaDocumentBuilder stream(String path, FlaStream.Kind kind, FlaRecord root) {
        return stream(path, kind, root, new BinaryData(new byte[0]));
    }

    public void unsupported(String path, String message) {
        issues.add(new FlaParseIssue(FlaParseIssue.Kind.UNSUPPORTED, path, 0, message));
    }

    public BinaryFlaDocument build() {
        FlaStream contents = streams.get("Contents");
        if (contents == null || contents.getRoot() == null || !contents.getRoot().getClassName().equals("Contents")) {
            throw new IllegalStateException("Contents model is required");
        }
        FlaStorage root = new FlaStorage("Root Entry", "", 0, new byte[16], 0);
        Map<String, FlaStorage> folders = new LinkedHashMap<>();
        folders.put("", root);
        for (FlaStream stream : streams.values()) {
            String path = stream.getPath(), parent = "";
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                String next = parent.isEmpty() ? parts[i] : parent + "/" + parts[i];
                if (!folders.containsKey(next)) {
                    FlaStorage folder = new FlaStorage(parts[i], next, streams.size() + folders.size(), new byte[16], 0);
                    folders.get(parent).add(folder);
                    folders.put(next, folder);
                }
                parent = next;
            }
            folders.get(parent).add(stream);
        }
        for (FlaParseIssue issue : issues) {
            contents.addIssue(issue);
        }
        BinaryFlaDocument document = new BinaryFlaDocument(root, streams, charset);
        Set<FlaStream> linked = Collections.newSetFromMap(new IdentityHashMap<FlaStream, Boolean>());
        for (FlaRecord descriptor : document.getSceneDescriptors()) {
            link(document, descriptor, BinaryFlaDocument.Page.Role.SCENE, linked);
        }
        for (FlaRecord descriptor : document.getLibraryDescriptors()) {
            link(document, descriptor, BinaryFlaDocument.Page.Role.LIBRARY, linked);
        }
        for (FlaStream stream : streams.values()) {
            if (stream.getKind() == FlaStream.Kind.PAGE && linked.add(stream)) {
                document.add(new BinaryFlaDocument.Page(stream, null, BinaryFlaDocument.Page.Role.UNLINKED));
            }
        }
        for (FlaRecord descriptor : document.getMediaDescriptors()) {
            document.add(new BinaryFlaDocument.Media(document.getStream((String) descriptor.get("streamName")), descriptor));
        }
        return document;
    }

    private void link(BinaryFlaDocument document, FlaRecord descriptor, BinaryFlaDocument.Page.Role role, Set<FlaStream> linked) {
        FlaStream stream = document.getStream((String) descriptor.get("streamName"));
        document.add(new BinaryFlaDocument.Page(stream, descriptor, role));
        if (stream != null) {
            linked.add(stream);
        }
    }
}
