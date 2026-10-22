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

/**
 * Library linkage mode and export options, retaining stored and effective
 * values separately.
 */
public final class FlaLinkage {

    public enum Mode {
        NONE, EXPORT, IMPORT, UNKNOWN
    }
    private final long modeId;
    private final Integer storedExports, nativeExports, importType;
    private final String identifier, url, className, baseClass;

    private FlaLinkage(FlaRecord r, long modeId) {
        this.modeId = modeId;
        importType = SemanticFields.integer(r, "importType");
        storedExports = SemanticFields.integer(r, "exportFlags");
        Integer revision = SemanticFields.integer(r, "revision");
        nativeExports = revision == null || revision >= 1 && storedExports == null ? null : (storedExports == null ? 0 : storedExports) | (revision < 3 && modeId == 1 ? 4 : 0);
        identifier = string(r, "identifier");
        url = string(r, "url");
        className = string(r, "className");
        baseClass = string(r, "baseClass");
    }

    public static FlaLinkage fromRecord(FlaRecord r) {
        if (!"linkage".equals(r.getClassName())) {
            return null;
        }
        Long mode = SemanticFields.unsigned(r, "flags");
        return mode == null ? null : new FlaLinkage(r, mode);
    }

    private static String string(FlaRecord r, String key) {
        return r.get(key) instanceof String ? (String) r.get(key) : null;
    }

    /**
     * The stored dword is a selector, despite its historical raw field name
     * flags.
     */
    public long getModeId() {
        return modeId;
    }

    public Mode getMode() {
        return modeId <= 2 ? Mode.values()[(int) modeId] : Mode.UNKNOWN;
    }

    /**
     * Archived linkageImportType selector, or null before linkage revision 6.
     */
    public Integer getImportType() {
        return importType;
    }

    public Integer getStoredExportFlags() {
        return storedExports;
    }

    /**
     * Constructor defaults and the R0..2 compatibility first-frame bit; raw
     * fields are unchanged.
     */
    public Integer getNativeExportFlags() {
        return nativeExports;
    }

    public boolean isImportForRuntimeSharing() {
        return modeId == 2;
    }

    public Boolean isExportForActionScript() {
        return nativeExports == null ? null : modeId == 1 && (nativeExports & 1) != 0;
    }

    public Boolean isExportForRuntimeSharing() {
        return nativeExports == null ? null : modeId == 1 && (nativeExports & 2) != 0;
    }

    public Boolean isExportInFirstFrame() {
        return nativeExports == null ? null : modeId == 1 && (nativeExports & 4) != 0;
    }

    public Integer getUnknownExportFlags() {
        return storedExports == null ? null : storedExports & ~7;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getUrl() {
        return url;
    }

    public String getClassName() {
        return className;
    }

    public String getBaseClass() {
        return baseClass;
    }
}
