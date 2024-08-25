/*
 * Copyright (C) 2024 JPEXS.
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
package com.jpexs.cfb;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author JPEXS
 */
public class DirectoryEntry {

    public long fileOffset;

    public long streamId;
    public String name;
    public int objectType;
    public int colorFlag;
    public long leftSiblingId;
    public long rightSiblingId;
    public long childId;
    public byte clsId[];
    public long stateBits;
    public Date creationTime;
    public Date modifiedTime;
    public long startingSectorLocation;
    public long streamSize;

    public DirectoryEntry(long fileOffset, long streamId, String name, int objectType, int colorFlag, long leftSiblingId, long rightSiblingId, long childId, byte[] clsId, long stateBits, Date creationTime, Date modifiedTime, long startingSectorLocation, long streamSize) {
        this.fileOffset = fileOffset;
        this.streamId = streamId;
        this.name = name;
        this.objectType = objectType;
        this.colorFlag = colorFlag;
        this.leftSiblingId = leftSiblingId;
        this.rightSiblingId = rightSiblingId;
        this.childId = childId;
        this.clsId = clsId;
        this.stateBits = stateBits;
        this.creationTime = creationTime;
        this.modifiedTime = modifiedTime;
        this.startingSectorLocation = startingSectorLocation;
        this.streamSize = streamSize;
    }

    public String getFilename() {
        String fileName = "";
        for (int n = 0; n < name.length(); n++) {
            char c = name.charAt(n);
            if (c < 0x20) {
                fileName += "[" + (int) c + "]";
            } else {
                fileName += c;
            }
        }
        return fileName;
    }

    @Override
    public String toString() {
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String objectTypeStr = "";
        switch (objectType) {
            case CompoundFileBinary.TYPE_UNKNOWN:
                objectTypeStr = "unknown";
                break;
            case CompoundFileBinary.TYPE_STORAGE_OBJECT:
                objectTypeStr = "storage_object";
                break;
            case CompoundFileBinary.TYPE_STREAM_OBJECT:
                objectTypeStr = "stream_object";
                break;
            case CompoundFileBinary.TYPE_ROOT_STORAGE_OBJECT:
                objectTypeStr = "root_storage_object";
                break;
        }
        String colorStr = colorFlag == CompoundFileBinary.COLOR_BLACK ? "black" : "red";
        return "" + streamId + ": \"" + getFilename() + "\" - "
                + "type " + objectTypeStr + ", "
                + "color " + colorStr + ", "
                + "leftSibling " + streamIdToString(leftSiblingId) + ", "
                + "rightSibling " + streamIdToString(rightSiblingId) + ", "
                + "child " + streamIdToString(childId) + ", "
                + "sector " + startingSectorLocation + ", "
                + "size: " + streamSize + ", "
                + "created " + (creationTime == null ? "-" : sdt.format(creationTime)) + ", "
                + "modified " + (modifiedTime == null ? "-" : sdt.format(modifiedTime));
    }

    private String streamIdToString(long streamId) {
        if (streamId == CompoundFileBinary.NOSTREAM) {
            return "NOSTREAM";
        }
        return "" + streamId;
    }

    public int compareTo(DirectoryEntry entry) {
        int lenDelta = name.length() - entry.name.length();
        if (lenDelta != 0) {
            return lenDelta;
        }
        return name.toUpperCase().compareTo(entry.name.toUpperCase());
    }
}
