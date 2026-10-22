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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A serialized record; reference fields retain shared identity and can contain
 * cycles.
 */
public final class FlaRecord {

    private final String className;
    private final int schema;
    private final int objectId;
    private final int offset;
    private final int payloadOffset;
    private final BinaryData source;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private int endOffset;
    private boolean complete;
    private boolean modified;

    FlaRecord(String className, int schema, int objectId, int offset, int payloadOffset, BinaryData source) {
        this.className = className;
        this.schema = schema;
        this.objectId = objectId;
        this.offset = offset;
        this.payloadOffset = payloadOffset;
        this.endOffset = payloadOffset;
        this.source = source;
    }

    /**
     * Creates a detached record for model construction and direct binary
     * writing.
     */
    public static FlaRecord create(String className) {
        int schema = className.equals("CColorDef") || className.equals("CColorList") || className.equals("CSketchCntrItem") ? 0 : 1;
        FlaRecord result = new FlaRecord(className, schema, 0, 0, 0, new BinaryData(new byte[0]));
        result.finish(0, true);
        return result;
    }

    public String getClassName() {
        return className;
    }

    public int getSchema() {
        return schema;
    }

    /**
     * Archive-local MFC object ID; not a cross-stream library identifier.
     */
    public int getObjectId() {
        return objectId;
    }

    public int getOffset() {
        return offset;
    }

    public int getPayloadOffset() {
        return payloadOffset;
    }

    /**
     * End of the consumed prefix, or end of the complete record.
     */
    public int getEndOffset() {
        return endOffset;
    }

    public boolean isComplete() {
        return complete;
    }

    /**
     * True when a field was changed after decoding.
     */
    public boolean isModified() {
        return modified;
    }

    public Map<String, Object> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public Object get(String name) {
        return fields.get(name);
    }

    /**
     * Effective CPicLayer type, including the separate mask flag before R9.
     */
    public int getLayerType() {
        int type = number("type", 0);
        if (number("revision", 13) < 9) {
            if (number("legacyMask", 0) != 0) {
                return 4;
            }
            if (type == 2) {
                return 0;
            }
        }
        return type;
    }

    /**
     * Effective CPicLayer parent after the historical reference fixups.
     */
    public FlaRecord getLayerParent() {
        int revision = number("revision", 13);
        if (revision < 9 && number("legacyMask", 0) != 0) {
            return null;
        }
        Object parent = get("relatedLayer");
        if (revision < 9 && number("type", 0) == 1) {
            parent = null;
        }
        if ((revision == 7 || revision == 8) && get("legacyRelatedLayer") instanceof FlaRecord) {
            parent = get("legacyRelatedLayer");
        }
        return parent instanceof FlaRecord ? (FlaRecord) parent : null;
    }

    private int number(String field, int fallback) {
        Object value = get(field);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    /**
     * Used by decoders; known semantic names or neutral format-specific field
     * names belong here.
     */
    public void set(String name, Object value) {
        fields.put(name, value);
        modified = true;
    }

    @SuppressWarnings("unchecked")
    public List<FlaRecord> getChildren() {
        Object value = fields.get("children");
        return value instanceof List ? Collections.unmodifiableList((List<FlaRecord>) value) : Collections.emptyList();
    }

    /**
     * Default 2D matrix; null when its complete six-word field is unavailable.
     */
    public FlaMatrix getMatrix() {
        return getMatrix("matrixWords");
    }

    /**
     * Named matrix field, for example matrixWordsA/matrixWordsB in morph
     * records.
     */
    public FlaMatrix getMatrix(String field) {
        int[] words = integerWords(field, 6);
        return words == null ? null : FlaMatrix.fromWords(words);
    }

    /**
     * Geometric bounds, with boundsWords used when the bounds field is absent.
     */
    public FlaBounds getBounds() {
        return getBounds(fields.containsKey("bounds") ? "bounds" : "boundsWords");
    }

    /**
     * Interprets a known geometric bounds field; does not assign units to
     * arbitrary lists.
     */
    public FlaBounds getBounds(String field) {
        int[] words = integerWords(field, 4);
        return words == null ? null : FlaBounds.fromWords(words);
    }

    public FlaCenterPoint3D getCenterPoint3D() {
        int[] words = integerWords("centerPoint3D", 3);
        return words == null ? null : FlaCenterPoint3D.fromWords(words);
    }

    private int[] integerWords(String field, int count) {
        Object value = fields.get(field);
        if (!(value instanceof List) || ((List<?>) value).size() != count) {
            return null;
        }
        List<?> list = (List<?>) value;
        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            if (!(list.get(i) instanceof Integer)) {
                return null;
            }
            words[i] = (Integer) list.get(i);
        }
        return words;
    }

    public FlaStrokeStyle getStrokeStyle() {
        return FlaStrokeStyle.fromRecord(this);
    }

    public FlaPaintStyle getPaintStyle() {
        return FlaPaintStyle.fromRecord(this);
    }

    public FlaCubic getCubic() {
        return FlaCubic.fromRecord(this);
    }

    public FlaMorphShape getMorphShape() {
        return FlaMorphShape.fromRecord(this);
    }

    /**
     * Edge coordinates retain eight fractional bits per twip.
     */
    public FlaPoint2D getEdgePoint(String point) {
        if (!"edge".equals(className) || !("start".equals(point) || "control".equals(point) || "end".equals(point))) {
            return null;
        }
        Integer x = SemanticFields.integer(this, point + "X"), y = SemanticFields.integer(this, point + "Y");
        return x == null || y == null ? null : FlaPoint2D.fromEdgeWords(x, y);
    }

    /**
     * Named persistent-data snapshot on this container or its optional child.
     */
    public FlaPersistentData getPersistentData() {
        if ("persistentData".equals(className)) {
            return FlaPersistentData.fromRecord(this);
        }
        Object value = get("persistentData");
        return value instanceof FlaRecord ? FlaPersistentData.fromRecord((FlaRecord) value) : null;
    }

    public FlaMatrix3D getMatrix3D() {
        return FlaMatrix3D.fromRecord(this);
    }

    public FlaTlfText getTlfText() {
        return FlaTlfText.fromRecord(this);
    }

    public FlaLinkage getLinkage() {
        if ("linkage".equals(className)) {
            return FlaLinkage.fromRecord(this);
        }
        Object value = get("linkage");
        return value instanceof FlaRecord ? FlaLinkage.fromRecord((FlaRecord) value) : null;
    }

    public FlaCommandKind getCommandKind() {
        if (!"compiledCommand".equals(className)) {
            return null;
        }
        Long type = SemanticFields.unsigned(this, "type");
        return type == null ? null : FlaCommandKind.fromId(type);
    }

    public FlaComponentParameter getComponentParameter() {
        return FlaComponentParameter.fromRecord(this);
    }

    public BinaryData getConsumedData() {
        return source.slice(offset, endOffset - offset);
    }

    void finish(int end, boolean complete) {
        this.endOffset = end;
        this.complete = complete;
        this.modified = false;
    }
}
