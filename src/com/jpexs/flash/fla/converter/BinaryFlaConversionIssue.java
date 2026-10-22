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

/**
 * Information omitted or changed when saving a different binary FLA version.
 */
public final class BinaryFlaConversionIssue {

    private final String code, stream, record, field, message;

    BinaryFlaConversionIssue(String code, String stream, String record, String field, String message) {
        this.code = code;
        this.stream = stream;
        this.record = record;
        this.field = field;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getStreamPath() {
        return stream;
    }

    public String getRecordClass() {
        return record;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return code + ": " + stream + " / " + record + (field.isEmpty() ? "" : "." + field) + ": " + message;
    }
}
