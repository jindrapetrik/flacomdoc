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

import com.jpexs.flash.fla.reader.*;
import java.io.IOException;
import java.util.*;

/**
 * Version-selected CObject serializers. Every nested object uses the archive
 * table.
 */
final class BinaryObjectWriter {

    static final Set<String> CLASSES = new HashSet<>(Arrays.asList("CDocumentPage", "CColorDef", "CColorList",
            "CQTAudioSettings", "CPicObj", "CPicPage", "CPicLayer", "CPicFrame", "CPicShape", "CPicRect", "CPicOval",
            "CPicSymbol", "CPicShapeObj", "CPicSprite", "CPicButton", "CPicBitmap", "CPicText", "CPicVideoStream",
            "CMediaElem", "CMediaBits", "CMediaSound", "CMediaVideoStream", "CPicMorphShape", "CMorphSegment",
            "CMorphCurve", "CMorphHintItem", "CPicSwf", "CSwfPage", "CPicScreen", "CPicFont", "CPicSoundCreator", "CPicVideo", "CMediaVideo", "CPicTempClipboardObj"));
    final BinaryArchiveWriter w;
    final FlaFormatVersion t;

    BinaryObjectWriter(BinaryArchiveWriter w) {
        this.w = w;
        t = w.target();
    }

    void write(FlaRecord r, String kind) throws IOException {
        switch (kind) {
            case "CDocumentPage":
                new BinaryLibraryWriter(w).page(r);
                break;
            case "CColorDef":
                new BinaryLibraryWriter(w).color(r);
                break;
            case "CColorList":
                new BinaryLibraryWriter(w).colors(r);
                break;
            case "CQTAudioSettings":
                BinaryData data = w.data(r, "data");
                w.u32(data == null ? 0 : data.size());
                w.raw(data);
                break;
            case "CPicObj":
                base(r, t.picGroupBaseRevision);
                break;
            case "CPicPage":
                page(r);
                break;
            case "CPicLayer":
                layer(r);
                break;
            case "CPicFrame":
                frame(r);
                break;
            case "CPicShape":
                shape(r, t.picGroupBaseRevision);
                break;
            case "CPicRect":
            case "CPicOval":
                shape(r, t.picGroupBaseRevision);
                w.revision(r, "revision", 3);
                w.u8(r, "primitiveFlags");
                if (kind.equals("CPicRect")) {
                    w.words(r, "cornerRadii", 4);
                } else {
                    w.words(r, "bounds", 4);
                    w.f32(w.d(r, "startAngleDegrees", 0));
                    w.f32(w.d(r, "endAngleDegrees", 360));
                    w.f32(w.d(r, "innerRadiusPercent", 0));
                }
                break;
            case "CPicSymbol":
            case "CPicShapeObj":
            case "CPicSprite":
            case "CPicButton":
                symbol(r, kind);
                break;
            case "CPicBitmap":
                bitmap(r);
                break;
            case "CPicText":
                new BinaryTextWriter(w).text(r);
                break;
            case "CPicVideoStream":
                video(r);
                break;
            case "CMediaElem":
            case "CMediaBits":
            case "CMediaSound":
            case "CMediaVideoStream":
                new BinaryLibraryWriter(w).media(r, kind);
                break;
            case "CPicSwf":
            case "CSwfPage":
                new BinaryLibraryWriter(w).swf(r, kind);
                break;
            default:
                if (kind.startsWith("CMorph") || kind.equals("CPicMorphShape")) {
                    new BinaryShapeWriter(w).morph(r, kind);
                } else {
                    new BinaryAuxiliaryWriter(w).write(r, kind);
                }
                break;
        }
    }

    void base(FlaRecord r, int maximum) throws IOException {
        int v = w.revision(r, "baseRevision", maximum);
        w.u8(r, "flags");
        w.objects(r, "children");
        if (v != 0) {
            w.u32(w.n(r, "transformationPointX", Integer.MIN_VALUE));
            w.u32(w.n(r, "transformationPointY", Integer.MIN_VALUE));
        }
        if (v >= 3) {
            w.u8(r, "drawingObjectFlags");
        }
        if (v >= 4) {
            w.u8(r, "cacheAsBitmap");
        }
        if (v >= 6) {
            String id = w.s(r, "identifier");
            w.raw(w.textBytes(id, false));
            w.u8(0);
        }
    }

    void page(FlaRecord r) throws IOException {
        base(r, t.picPageBaseRevision);
        int v = w.revision(r, "revision", t.picPageRevision);
        if (v != 4) {
            w.u16(w.n(r, "nextLayerId", 1));
        }
        if (v >= 5) {
            w.u16(w.n(r, "nextFolderId", 1));
        }
        if (v >= 7) {
            w.u32(r, "currentFrame");
        }
        if (v >= 3) {
            List<?> guides = w.list(r, "guides");
            w.u32(guides.size());
            for (Object p : guides) {
                List<?> xy = (List<?>) p;
                w.u32(((Number) xy.get(0)).longValue());
                w.u32(((Number) xy.get(1)).longValue());
            }
        }
    }

    void layer(FlaRecord r) throws IOException {
        base(r, t.picLayerBaseRevision);
        int v = w.revision(r, "revision", t.picLayerRevision);
        w.string(r, "name");
        if (v <= 3) {
            // Legacy states: hidden=0, locked=1, normal=2, current=3.
            BinaryData state = w.data(r, "stateFlags");
            int legacy = (int) w.n(r, "legacyState", 2);
            if (state != null) {
                byte[] b = state.toByteArray();
                legacy = b.length > 1 && b[1] != 0 ? 0 : b.length > 2 && b[2] != 0 ? 1 : b.length > 0 && b[0] != 0 ? 3 : 2;
            }
            w.u8(legacy);
        } else {
            BinaryData state = w.data(r, "stateFlags");
            if (state != null) {
                w.fixedData(r, "stateFlags", 3);
            } else {
                int legacy = (int) w.n(r, "legacyState", 2);
                w.u8(legacy == 3 ? 1 : 0);
                w.u8(legacy == 0 ? 1 : 0);
                w.u8(legacy == 1 ? 1 : 0);
            }
        }
        if (v >= 5) {
            w.u32(w.n(r, "heightLiteral", 0xffffffffL));
        }
        if (v >= 6) {
            w.u32(w.n(r, "outlineColorRgb", 0x00FF00));
            w.u32(r, "outline");
        }
        if (v >= 8) {
            w.u32(w.n(r, "heightMultiplier", 1));
        }
        int type = (int) w.n(r, "type", 0);
        if (v < 12 && type >= 5) {
            w.issue("ANIMATION_LAYER", r, "type", "Animation layer saved as a normal layer.");
            type = 0;
        }
        boolean convertLegacyLayer = v < 9 && w.n(r, "revision", v) >= 9;
        FlaRecord parent = w.record(r, "relatedLayer");
        if (convertLegacyLayer) {
            if (type == 3) {
                w.issue("LAYER_FOLDER", r, "type", "Layer folder flattened for the legacy target.");
                type = 0;
            }
            Set<FlaRecord> folders = Collections.newSetFromMap(new IdentityHashMap<FlaRecord, Boolean>());
            while (parent != null && parent.getLayerType() == 3 && folders.add(parent)) {
                parent = parent.getLayerParent();
            }
            if (parent != null && parent.getLayerType() == 3) {
                parent = null;
            }
            if (type == 0 && parent != null && parent.getLayerType() == 1) {
                type = 2;
            }
        }
        w.u8(type);
        if (convertLegacyLayer) {
            boolean writeGuideReference = v == 7 || v == 8 ? type == 2 : type != 1 && type != 4;
            w.object(writeGuideReference ? parent : null);
        } else {
            w.object(parent);
        }
        if (v == 7 || v == 8) {
            FlaRecord maskReference = convertLegacyLayer ? null : w.record(r, "legacyRelatedLayer");
            if (convertLegacyLayer && parent != null && parent.getLayerType() == 4) {
                maskReference = parent;
            }
            w.object(maskReference);
        }
        if (v >= 2 && v <= 5) {
            w.u8(r, "legacyFlag");
        }
        if (v >= 3) {
            if (v < 9) {
                w.u8(convertLegacyLayer ? type == 4 ? 1 : 0 : w.n(r, "legacyMask", 0));
            } else {
                w.u8(w.n(r, "open", 1));
            }
        }
        if (v >= 10) {
            w.u8(r, "autoNamed");
        }
        if (v >= 12) {
            int animation = (int) w.n(r, "animationType", type == 6 ? 2 : type == 5 ? 1 : 0);
            if (v >= 13) {
                w.u8(animation);
            }
            if (animation == 2) {
                List<?> values = w.list(r, "animationValues");
                w.u32(values.size());
                for (Object n : values) {
                    w.u32(((Number) n).longValue());
                }
            }
        }
    }

    void shape(FlaRecord r, int base) throws IOException {
        base(r, base);
        int v = w.revision(r, "shapeRevision", t.picShapeRevision);
        w.words(r, "matrixWords", 6, true);
        new BinaryShapeWriter(w).shape(w.record(r, "shapeData"), v >= 3 ? v - 2 : 0);
    }

    void frame(FlaRecord r) throws IOException {
        shape(r, t.picFrameBaseRevision);
        int v = w.revision(r, "revision", t.picFrameRevision);
        w.u16(w.n(r, "duration", 1));
        int key = (int) w.n(r, "keyMode", 9728);
        if (v < 27 && (key & 255) >= 3) {
            w.issue("FRAME_ANIMATION", r, "keyMode", "Advanced animation saved as its stored frame contents.");
            key = (key & 0xFF00);
        }
        if (v <= 2) {
            w.u8(key);
        } else {
            w.u16(key);
        }
        if (v >= 2) {
            w.u16(r, "acceleration");
        }
        if (v >= 5) {
            long sound = w.n(r, "soundMediaId", 0);
            w.u16(w.session.unavailableMedia.contains((int) sound) ? 0 : sound);
        }
        if (v >= 6) {
            List<?> points = w.list(r, "soundEnvelope");
            w.u16(points.size());
            for (Object obj : points) {
                List<?> p = (List<?>) obj;
                w.u32(((Number) p.get(0)).longValue());
                w.u16(((Number) p.get(1)).longValue());
                w.u16(((Number) p.get(2)).longValue());
            }
        }
        if (v >= 7) {
            w.u16(w.n(r, "soundLoopCount", 1));
            w.u8(r, "soundSync");
            w.u32(r, "soundInPoint");
            w.u32(w.n(r, "soundOutPoint", 0x3FFFFFFFL));
        }
        if (v >= 8) {
            w.u16(w.n(r, "soundZoom", 1));
        }
        if (v >= 9) {
            w.string(r, "name");
        }
        FlaRecord actions = w.record(r, "actions");
        BinaryActionWriter aw = new BinaryActionWriter(w);
        if (v >= 4 && v <= 9) {
            aw.historical(actions, true);
        }
        if (v >= 10 && v <= 18) {
            aw.historical(actions, false);
        }
        if (v >= 19) {
            aw.source(actions, t.frameActionScriptRevision);
        }
        if (v >= 11) {
            w.dwords(r, "rotationMode", "rotationCount");
        }
        if (v >= 12) {
            w.u32(r, "labelType");
        }
        if (v >= 13) {
            w.object(w.record(r, "morphShape"));
        }
        if (v >= 14) {
            w.u32(r, "shapeTweenBlend");
        }
        if (v >= 15) {
            w.object(w.record(r, "soundSettings"));
        }
        if (v >= 16) {
            w.string(r, "extensionString");
        }
        if (v == 17) {
            FlaRecord e = w.record(r, "historicalExtension");
            w.u16(e, "flagsWord");
            w.words(e, "matrixWords", 6, true);
            w.u8(e, "beforeColorFlag");
            colorTransform(w.record(e, "colorTransform"), true, true);
            w.u8(e, "afterColorFlag");
        }
        if (v >= 20) {
            w.u32(r, "reserved");
        }
        if (v >= 21) {
            w.u32(r, "soundEffect");
        }
        if (v >= 22) {
            w.u32(r, "bookmark");
        }
        if (v >= 24) {
            w.u32(w.n(r, "useSingleEaseCurve", 1));
            List<?> curves = w.list(r, "customEaseCurves");
            w.value(r, "customEasePresent");
            w.u32(curves.isEmpty() ? 0 : 1);
            if (!curves.isEmpty()) {
                for (int i = 0; i < 6; i++) {
                    List<?> c = i < curves.size() ? (List<?>) curves.get(i) : Collections.emptyList();
                    w.u32(c.size());
                    for (Object seg : c) {
                        for (Object d : (List<?>) seg) {
                            w.f64(((Number) d).doubleValue());
                        }
                    }
                }
            }
        }
        if (v >= 27 && (key & 255) == 3) {
            w.string(r, "motionXml");
            w.u32(r, "visibleAnimationKeyframes");
        }
        if (v >= 28 && (key & 255) == 3) {
            w.string(r, "tweenInstanceName");
        }
        if (v >= 29 && (key & 255) == 4) {
            List<?> matrices = w.list(r, "poseMatrices");
            int duration = (int) w.n(r, "duration", 1);
            w.u32(duration > 0 ? matrices.size() / duration : 0);
            for (Object matrix : matrices) {
                for (Object n : (List<?>) matrix) {
                    w.u32(((Number) n).longValue());
                }
            }
        }
    }

    void colorTransform(FlaRecord r, boolean revision, boolean alpha) {
        if (revision) {
            w.u8(1);
        }
        if (alpha) {
            w.u16(w.n(r, "alphaMultiplier", 256));
            w.u16(r, "alphaOffset");
        }
        for (String channel : Arrays.asList("red", "green", "blue")) {
            w.u16(w.n(r, channel + "Multiplier", 256));
            w.u16(r, channel + "Offset");
        }
        if (revision) {
            w.u16(r, "effectType");
            w.u16(r, "effectPercent");
            w.u32(r, "effectColorRgba");
        }
    }

    void symbol(FlaRecord r, String kind) throws IOException {
        base(r, t.picSymbolBaseRevision);
        int v = w.revision(r, "revision", t.picSymbolRevision);
        w.words(r, "matrixWords", 6, true);
        w.u16(r, "firstFrame");
        if (v >= 2) {
            int loopMode = (int) w.n(r, "loopMode", 0);
            if (r.getClassName().equals("CPicSprite") && kind.equals("CPicSymbol")) {
                loopMode = 0; // Stored graphic loop enum: loop=0, play once=1, single frame=2.
            }
            w.u16(loopMode);
        }
        FlaRecord color = w.record(r, "colorTransform");
        if (v >= 7) {
            colorTransform(color, true, true);
        } else {
            if (v >= 3) {
                colorTransform(color, false, v >= 5);
            }
            if (v >= 4) {
                w.u16(r, "colorEffectType");
                w.u16(r, "colorEffectPercent");
                w.u32(r, "colorEffectRgba");
            }
        }
        if (v >= 6) {
            w.string(r, "legacyName");
        }
        w.u32(r, "libraryId");
        if (v == 8 || v == 9) {
            w.strings(r, "legacyCustomUISourceFilename", "legacySymbolDescription");
            new BinaryLibraryWriter(w).properties(w.record(r, "legacyProperties"));
            w.u32(r, "legacyParametersAreLocked");
            if (v == 9) {
                w.string(r, "legacyComponentName");
            }
        }
        if (v >= 11) {
            FlaRecord effect = w.record(r, "timelineEffect");
            w.value(r, "timelineEffectPresent");
            w.u8(effect == null ? 0 : 1);
            if (effect != null) {
                new BinaryLibraryWriter(w).timelineEffect(effect, v);
            }
        }
        if (v >= 12) {
            FlaRecord pd = w.record(r, "persistentData");
            w.u8(pd == null ? 0 : 1);
            if (pd != null) {
                new BinaryLibraryWriter(w).persistent(pd);
            }
        }
        if (v >= 14) {
            w.u8(r, "isExtensible");
        }
        if (v >= 15) {
            new BinaryLibraryWriter(w).filters(r);
        }
        if (v >= 16) {
            w.u8(w.n(r, "blendMode", 1));
        }
        if (v >= 17) {
            w.u16(w.n(r, "selectedEffectIndex", 65535));
        }
        if (v >= 20) {
            List<?> m = w.list(r, "matrix3D");
            for (int i = 0; i < 16; i++) {
                w.f32(i < m.size() ? ((Number) m.get(i)).doubleValue() : i % 5 == 0 ? 1 : 0);
            }
            for (Object d : three(w.list(r, "rotation3D"))) {
                w.f64(((Number) d).doubleValue());
            }
        }
        if (v >= 21) {
            w.words(r, "centerPoint3D", 3);
        }
        if (v >= 22) {
            List<FlaRecord> settings = w.records(r, "animationFrameSettings");
            w.u16(settings.size());
            for (FlaRecord setting : settings) {
                w.u16(setting, "loopMode");
                w.u16(setting, "firstFrame");
            }
        }
        if (v >= 23) {
            w.u16(r, "bitmapRenderMode");
            w.u32(w.n(r, "bitmapBits32", 1));
            w.u32(r, "bitmapBackgroundColorRgba");
            w.u32(w.n(r, "isVisible", 1));
        }
        if (kind.equals("CPicSprite")) {
            int sv = w.revision(r, "spriteRevision", t.picSpriteRevision);
            if (sv >= 2) {
                new BinaryActionWriter(w).source(w.record(r, "actions"), t.symbolActionScriptRevision);
            }
            w.string(r, "instanceName");
            if (sv >= 3) {
                new BinaryLibraryWriter(w).properties(w.record(r, "properties"));
            }
            if (sv >= 6) {
                new BinaryLibraryWriter(w).accessibility(w.record(r, "accessibility"), sv >= 7);
            }
            if (sv >= 5) {
                w.u32(r, "propertyInspectorState");
            }
            if (sv >= 8) {
                new BinaryLibraryWriter(w).componentXml(w.record(r, "componentXml"));
            }
        } else if (kind.equals("CPicButton")) {
            int bv = w.revision(r, "buttonRevision", (t.ordinal() <= FlaFormatVersion.F2.ordinal() ? 2 : t.ordinal() <= FlaFormatVersion.F4.ordinal() ? 4 : t.picButtonRevision));
            FlaRecord a = w.record(r, "actions");
            BinaryActionWriter aw = new BinaryActionWriter(w);
            if (bv == 2) {
                aw.historical(a, true);
            }
            if (bv == 3 || bv == 4) {
                aw.historical(a, false);
            } else if (bv >= 5) {
                aw.source(a, t.symbolActionScriptRevision);
            }
            if (bv >= 4) {
                w.u8(r, "trackAsMenu");
            }
            if (bv == 9 || bv == 10) {
                byte[] name = w.textBytes(w.s(r, "instanceName"), true);
                w.u32(name.length);
                w.raw(name);
            } else if (bv >= 6) {
                w.string(r, "instanceName");
            }
            if (bv >= 8) {
                new BinaryLibraryWriter(w).accessibility(w.record(r, "accessibility"), bv >= 11);
            }
        }
    }

    private List<?> three(List<?> values) {
        return values.size() == 3 ? values : Arrays.asList(0, 0, 0);
    }

    void bitmap(FlaRecord r) throws IOException {
        base(r, t.picBitmapBaseRevision);
        int v = w.revision(r, "revision", t.picBitmapRevision);
        w.words(r, "matrixWords", 6, true);
        if (v == 0) {
            new BinaryMediaWriter(w.session).inlineBitmap(w, r);
        }
        if (v >= 1) {
            w.u16(r, "mediaId");
        }
        if (v >= 2) {
            FlaRecord pd = w.record(r, "persistentData");
            w.u8(pd == null ? 0 : 1);
            if (pd != null) {
                new BinaryLibraryWriter(w).persistent(pd);
            }
        }
    }

    void video(FlaRecord r) throws IOException {
        base(r, t.picVideoStreamBaseRevision);
        int v = w.revision(r, "revision", t.picVideoStreamRevision);
        w.words(r, "matrixWords", 6, true);
        w.words(r, "bounds", 4);
        w.u8(r, "reservedByte");
        w.strings(r, "reservedString", "instanceName");
        boolean present = w.n(r, "mediaPresent", 1) != 0;
        if (v >= 3) {
            w.u32(present ? 1 : 0);
        }
        if (present) {
            w.u16(r, "mediaId");
        }
    }
}
