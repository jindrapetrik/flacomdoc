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
import java.util.Objects;

/**
 * Limits apply before allocations and recursive archive/container traversal.
 */
public final class FlaReadOptions {

    private final Charset ansiCharset;
    private final int maxStreamBytes;
    private final long maxDocumentBytes;
    private final int maxObjects;
    private final int maxDepth;

    public FlaReadOptions() {
        this(Charset.forName("windows-1252"), 128 * 1024 * 1024, 512L * 1024 * 1024, 1000000, 128);
    }

    public FlaReadOptions(Charset ansiCharset, int maxStreamBytes, long maxDocumentBytes, int maxObjects, int maxDepth) {
        this.ansiCharset = Objects.requireNonNull(ansiCharset, "ansiCharset");
        if (maxStreamBytes <= 0 || maxDocumentBytes <= 0 || maxObjects <= 0 || maxDepth <= 0) {
            throw new IllegalArgumentException("Read limits must be positive");
        }
        this.maxStreamBytes = maxStreamBytes;
        this.maxDocumentBytes = maxDocumentBytes;
        this.maxObjects = maxObjects;
        this.maxDepth = maxDepth;
    }

    public Charset getAnsiCharset() {
        return ansiCharset;
    }

    public int getMaxStreamBytes() {
        return maxStreamBytes;
    }

    public long getMaxDocumentBytes() {
        return maxDocumentBytes;
    }

    public int getMaxObjects() {
        return maxObjects;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
}
