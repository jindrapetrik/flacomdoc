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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A logical CFB stream and its optional decoded archive.
 */
public final class FlaStream {

    public enum Kind {
        CONTENTS, PAGE, MEDIA, OTHER
    }

    public enum ParseStatus {
        RAW, COMPLETE, PARTIAL, MALFORMED
    }

    private final String path;
    private final long directoryId;
    private final BinaryData data;
    private Kind kind = Kind.OTHER;
    private ParseStatus status = ParseStatus.RAW;
    private FlaRecord root;
    private List<FlaRecord> objects = Collections.emptyList();
    private final List<FlaParseIssue> issues = new ArrayList<>();
    private int stopOffset;

    FlaStream(String path, long directoryId, BinaryData data) {
        this.path = path;
        this.directoryId = directoryId;
        this.data = data;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public long getDirectoryId() {
        return directoryId;
    }

    public BinaryData getData() {
        return data;
    }

    public Kind getKind() {
        return kind;
    }

    public ParseStatus getParseStatus() {
        return status;
    }

    public FlaRecord getRoot() {
        return root;
    }

    public List<FlaRecord> getObjects() {
        return objects;
    }

    public List<FlaParseIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public int getStopOffset() {
        return stopOffset;
    }

    public BinaryData getUnparsedData() {
        return data.slice(stopOffset, data.size() - stopOffset);
    }

    /**
     * Builds a stream from records without serializing or decoding an archive.
     */
    static FlaStream fromModel(String path, long id, Kind kind, FlaRecord root, BinaryData data) {
        FlaStream stream = new FlaStream(path, id, data);
        stream.kind = kind;
        stream.root = root;
        if (root != null) {
            List<FlaRecord> records = new ArrayList<>();
            collectModel(root, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<FlaRecord, Boolean>()), records);
            stream.objects = Collections.unmodifiableList(records);
            stream.status = ParseStatus.COMPLETE;
            stream.stopOffset = data.size();
        }
        return stream;
    }

    private static void collectModel(Object value, java.util.Set<FlaRecord> seen, List<FlaRecord> records) {
        if (value instanceof FlaRecord) {
            FlaRecord record = (FlaRecord) value;
            if (!seen.add(record)) {
                return;
            }
            records.add(record);
            for (Object child : record.getFields().values()) {
                collectModel(child, seen, records);
            }
        } else if (value instanceof List) {
            for (Object child : (List<?>) value) {
                collectModel(child, seen, records);
            }
        }
    }

    void setKind(Kind kind) {
        this.kind = kind;
    }

    void setRoot(FlaRecord root) {
        this.root = root;
    }

    void addIssue(FlaParseIssue issue) {
        issues.add(issue);
    }

    void parsed(MfcArchive archive, boolean complete, FlaParseIssue issue) {
        stopOffset = archive.getPosition();
        objects = Collections.unmodifiableList(new ArrayList<>(archive.getObjects()));
        if (root == null && !objects.isEmpty()) {
            root = objects.get(0);
        }
        status = complete ? ParseStatus.COMPLETE : ParseStatus.PARTIAL;
        if (issue != null) {
            issues.add(issue);
            if (issue.getKind() == FlaParseIssue.Kind.MALFORMED) {
                status = ParseStatus.MALFORMED;
            }
        }
    }
}
