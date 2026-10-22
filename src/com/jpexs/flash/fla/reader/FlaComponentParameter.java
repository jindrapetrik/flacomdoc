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

import java.util.List;

/**
 * Inspectable symbol-component parameter, independent of other property type
 * tables.
 */
public final class FlaComponentParameter {

    public enum Type {
        NUMBER, DOUBLE, STRING, COLOR, BOOLEAN, STRINGS, PERCENT, UNKNOWN
    }
    private final long typeId;
    private final Object value;
    private final List<String> choices;
    private final Integer selectedIndex, minimum, maximum, maximumLength;
    private final Long order;
    private final String name, variable;

    private FlaComponentParameter(FlaRecord r, long type) {
        typeId = type;
        value = r.get("value");
        choices = SemanticFields.strings(r, "values");
        selectedIndex = SemanticFields.integer(r, "selectedIndex");
        minimum = signed(r, "minimum");
        maximum = signed(r, "maximum");
        maximumLength = signed(r, "maximumLength");
        order = SemanticFields.unsigned(r, "displayOrder");
        name = r.get("name") instanceof String ? (String) r.get("name") : null;
        variable = r.get("variableName") instanceof String ? (String) r.get("variableName") : null;
    }

    private static Integer signed(FlaRecord r, String key) {
        Long v = SemanticFields.unsigned(r, key);
        return v == null ? null : v.intValue();
    }

    public static FlaComponentParameter fromRecord(FlaRecord r) {
        if (!"componentParameter".equals(r.getClassName())) {
            return null;
        }
        Long type = SemanticFields.unsigned(r, "type");
        return type == null ? null : new FlaComponentParameter(r, type);
    }

    public long getTypeId() {
        return typeId;
    }

    public Type getType() {
        return typeId <= 6 ? Type.values()[(int) typeId] : Type.UNKNOWN;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public List<String> getChoices() {
        return choices;
    }

    public Integer getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Null for an absent or out-of-range selection; the raw index is never
     * clamped.
     */
    public String getSelectedChoice() {
        return typeId != 5 || choices == null || selectedIndex == null || selectedIndex < 0 || selectedIndex >= choices.size() ? null : choices.get(selectedIndex);
    }

    /**
     * The Percent payload is a fraction; its editor display multiplies by 100.
     */
    public Double getDisplayPercent() {
        return typeId == 6 && value instanceof Double ? (Double) value * 100 : null;
    }

    /**
     * Bounds retain signed -1, the native unspecified marker.
     */
    public Integer getMinimum() {
        return minimum;
    }

    public Integer getMaximum() {
        return maximum;
    }

    public Integer getMaximumLength() {
        return maximumLength;
    }

    public String getVariable() {
        return variable;
    }

    public Long getDisplayOrder() {
        return order;
    }
}
