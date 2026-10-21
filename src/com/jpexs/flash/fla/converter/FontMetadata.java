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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the font classification needed by legacy FLA text runs from SFNT files. */
final class FontMetadata {

    static final FontMetadata UNKNOWN = new FontMetadata(0, 0);

    private static final int TRUETYPE = 0x00010000;
    private static final int TRUE = 0x74727565;
    private static final int OTTO = 0x4F54544F;
    private static final int TTCF = 0x74746366;
    private static final int NAME = 0x6E616D65;
    private static final int OS_2 = 0x4F532F32;
    private static final int POST = 0x706F7374;
    private static final int CMAP = 0x636D6170;

    // OS/2 code-page bits and their Windows LOGFONT charset values.
    private static final int[][] CODE_PAGE_CHARSETS = {
        {0, 0}, {1, 238}, {2, 204}, {3, 161}, {4, 162}, {5, 177}, {6, 178}, {7, 186}, {8, 163},
        {16, 222}, // Thai (874)
        {17, 128}, // Shift-JIS (932)
        {18, 134}, // GB2312 (936)
        {19, 129}, // Hangul (949)
        {20, 136}, // Big5 (950)
        {21, 130}, // Johab (1361)
        {29, 77}, {30, 255}
    };

    private final int pitchAndFamily;
    private final int charSet;

    private FontMetadata(int pitchAndFamily, int charSet) {
        this.pitchAndFamily = pitchAndFamily;
        this.charSet = charSet;
    }

    int getPitchAndFamily() {
        return pitchAndFamily;
    }

    int getCharSet() {
        return charSet;
    }

    /** Reads only name-table data; classification tables are read on demand. */
    static Map<String, Long> readPostScriptNames(File file) throws IOException {
        Map<String, Long> names = new LinkedHashMap<>();
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            checkRange(in, 0, 12);
            if (in.readInt() == TTCF) {
                int version = in.readInt();
                if (version != 0x00010000 && version != 0x00020000) {
                    throw new IOException("Unsupported font collection version");
                }
                long count = readUnsignedInt(in);
                checkRange(in, 12, count * 4);
                for (long i = 0; i < count; i++) {
                    in.seek(12 + i * 4);
                    long offset = readUnsignedInt(in);
                    // A damaged face must not hide the other faces in a collection.
                    try {
                        addPostScriptName(in, offset, names);
                    } catch (IOException ex) {
                        // Ignore an unreadable face.
                    }
                }
            } else {
                addPostScriptName(in, 0, names);
            }
        }
        return names;
    }

    private static void addPostScriptName(RandomAccessFile in, long offset,
            Map<String, Long> names) throws IOException {
        Table name = readDirectory(in, offset).get(NAME);
        if (name == null) {
            return;
        }
        name.require(6);
        in.seek(name.offset);
        int format = in.readUnsignedShort();
        if (format > 1) {
            throw new IOException("Unsupported name table format");
        }
        int count = in.readUnsignedShort();
        int strings = in.readUnsignedShort();
        name.require(6L + 12L * count);
        if (strings < 6L + 12L * count || strings > name.length) {
            throw new IOException("Invalid name string storage");
        }
        String bestName = null;
        int bestScore = -1;
        for (int i = 0; i < count; i++) {
            in.seek(name.offset + 6 + 12L * i);
            int platform = in.readUnsignedShort();
            int encoding = in.readUnsignedShort();
            int language = in.readUnsignedShort();
            int nameId = in.readUnsignedShort();
            int length = in.readUnsignedShort();
            int stringOffset = in.readUnsignedShort();
            if (nameId != 6) {
                continue;
            }
            boolean unicode = platform == 0 || (platform == 3
                    && (encoding == 0 || encoding == 1 || encoding == 10));
            if (!unicode && !(platform == 1 && encoding == 0)) {
                continue;
            }
            if (unicode && (length & 1) != 0) {
                continue;
            }
            name.require((long) strings + stringOffset + length);
            byte[] bytes = new byte[length];
            in.seek(name.offset + strings + stringOffset);
            in.readFully(bytes);
            String value = new String(bytes, unicode ? StandardCharsets.UTF_16BE : StandardCharsets.ISO_8859_1);
            int score = platform == 3 ? (language == 0x0409 ? 3 : 2) : platform == 0 ? 1 : 0;
            if (score > bestScore && isPostScriptName(value)) {
                bestName = value;
                bestScore = score;
            }
        }
        if (bestName != null) {
            names.putIfAbsent(bestName, offset);
        }
    }

    private static boolean isPostScriptName(String value) {
        if (value.isEmpty() || value.length() > 63) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 33 || c > 126 || "[](){}<>/%".indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    static FontMetadata read(File file, long fontOffset) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            Map<Integer, Table> tables = readDirectory(in, fontOffset);
            Table os2 = tables.get(OS_2);
            byte[] panose = null;
            long codePages = 0;
            if (os2 != null) {
                os2.require(78);
                in.seek(os2.offset);
                int version = in.readUnsignedShort();
                panose = new byte[10];
                in.seek(os2.offset + 32);
                in.readFully(panose);
                if (version >= 1) {
                    os2.require(86);
                    in.seek(os2.offset + 78);
                    codePages = readUnsignedInt(in);
                }
            }
            Table post = tables.get(POST);
            boolean fixedPitch = false;
            if (post != null) {
                post.require(16);
                in.seek(post.offset + 12);
                fixedPitch = readUnsignedInt(in) != 0;
            }
            int pitch = panose == null && post == null ? 0 : pitchAndFamily(panose, fixedPitch);
            int charset = charset(codePages, hasSymbolCmap(in, tables.get(CMAP)));
            return new FontMetadata(pitch, charset);
        }
    }

    private static int pitchAndFamily(byte[] panose, boolean fixedPitch) {
        int familyType = panose == null ? 0 : panose[0] & 0xFF;
        int serifStyle = panose == null ? 0 : panose[1] & 0xFF;
        fixedPitch |= panose != null && (panose[3] & 0xFF) == 9;
        int family;
        if (familyType == 3) {
            family = 0x40; // FF_SCRIPT
        } else if (familyType == 4) {
            family = 0x50; // FF_DECORATIVE
        } else if (fixedPitch) {
            family = 0x30; // FF_MODERN
        } else if (serifStyle >= 2 && serifStyle <= 10) {
            // Pictorial fonts also use this rule, matching Windows font enumeration.
            family = 0x10; // FF_ROMAN
        } else if (serifStyle >= 11 && serifStyle <= 15) {
            family = 0x20; // FF_SWISS
        } else {
            family = 0; // FF_DONTCARE
        }
        return family | (fixedPitch ? 1 : 2);
    }

    private static int charset(long codePages, boolean symbolCmap) {
        if (symbolCmap || (codePages & 0x80000000L) != 0) {
            return 2; // SYMBOL_CHARSET
        }
        // Prefer ANSI, then the first supported Windows code page, independently of the host locale.
        for (int[] mapping : CODE_PAGE_CHARSETS) {
            if ((codePages & (1L << mapping[0])) != 0) {
                return mapping[1];
            }
        }
        return 0; // Unresolved charset, also used when no font metadata is available.
    }

    private static boolean hasSymbolCmap(RandomAccessFile in, Table cmap) throws IOException {
        if (cmap == null) {
            return false;
        }
        cmap.require(4);
        in.seek(cmap.offset);
        if (in.readUnsignedShort() != 0) {
            throw new IOException("Unsupported cmap version");
        }
        int count = in.readUnsignedShort();
        cmap.require(4L + 8L * count);
        boolean symbol = false;
        for (int i = 0; i < count; i++) {
            int platform = in.readUnsignedShort();
            int encoding = in.readUnsignedShort();
            long offset = readUnsignedInt(in);
            if (offset < 4L + 8L * count || offset > cmap.length - 2) {
                throw new IOException("Invalid cmap subtable offset");
            }
            symbol |= platform == 3 && encoding == 0;
        }
        return symbol;
    }

    private static Map<Integer, Table> readDirectory(RandomAccessFile in, long offset) throws IOException {
        checkRange(in, offset, 12);
        in.seek(offset);
        int signature = in.readInt();
        if (signature != TRUETYPE && signature != TRUE && signature != OTTO) {
            throw new IOException("Not a supported SFNT font");
        }
        int count = in.readUnsignedShort();
        checkRange(in, offset + 12, 16L * count);
        in.skipBytes(6);
        Map<Integer, Table> tables = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int tag = in.readInt();
            in.readInt(); // Checksum; glyph data is not needed for this metadata reader.
            long tableOffset = readUnsignedInt(in);
            long length = readUnsignedInt(in);
            checkRange(in, tableOffset, length);
            if (tables.put(tag, new Table(tableOffset, length)) != null) {
                throw new IOException("Duplicate SFNT table");
            }
        }
        return tables;
    }

    private static long readUnsignedInt(RandomAccessFile in) throws IOException {
        return in.readInt() & 0xFFFFFFFFL;
    }

    private static void checkRange(RandomAccessFile in, long offset, long length) throws IOException {
        long fileLength = in.length();
        if (offset < 0 || length < 0 || offset > fileLength || length > fileLength - offset) {
            throw new IOException("Font data extends beyond the file");
        }
    }

    private static final class Table {
        final long offset;
        final long length;

        Table(long offset, long length) {
            this.offset = offset;
            this.length = length;
        }

        void require(long size) throws IOException {
            if (size > length) {
                throw new IOException("Truncated font table");
            }
        }
    }
}
