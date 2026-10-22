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
import java.io.*;
import java.util.*;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.converter.XflModelXml.*;

/**
 * Timelines, layer references, keyframes, actions, sound and morph
 * correspondence.
 */
final class XflTimelineReader {

    final XflReader.Session s;

    XflTimelineReader(XflReader.Session s) {
        this.s = s;
    }

    FlaRecord timeline(Element e) throws IOException {
        List<Element> elements = children(child(e, "layers"), "DOMLayer");
        List<FlaRecord> layers = new ArrayList<>();
        for (Element layer : elements) {
            FlaRecord r = r("CPicLayer", "revision", 13, "name", s(layer, "name", "Layer " + (layers.size() + 1)), "stateFlags", new BinaryData(new byte[]{(byte) (b(layer, "current", false) || b(layer, "isSelected", false) ? 1 : 0), (byte) (b(layer, "visible", true) ? 0 : 1), (byte) (b(layer, "locked", false) ? 1 : 0)}), "type", choice(layer, "layerType", 0, "normal", "guide", "guided", "folder", "mask"), "open", b(layer, "open", true) ? 1 : 0, "autoNamed", b(layer, "autoNamed", false) ? 1 : 0, "outlineColorRgb", rgb(layer), "outline", b(layer, "outline", b(layer, "useOutlineView", false)) ? 1L : 0L, "heightMultiplier", (long) i(layer, "heightMultiplier", 1));
            int height = i(layer, "heightLiteral", -1);
            r.set("heightLiteral", height > 0 ? (long) height : 0xffffffffL);
            List<FlaRecord> frames = new ArrayList<>();
            int position = 0;
            for (Element f : children(child(layer, "frames"), "DOMFrame")) {
                int index = i(f, "index", position);
                if (index < position) {
                    throw new IOException("Overlapping/out-of-order XFL keyframes");
                }
                if (index > position) {
                    frames.add(blank(index - position));
                }
                FlaRecord frame = frame(f);
                frames.add(frame);
                position = index + ((Number) frame.get("duration")).intValue();
            }
            if (frames.isEmpty()) {
                frames.add(blank(1));
            }
            bindMorphStyles(frames);
            r.set("children", frames);
            layers.add(r);
        }
        for (int i = 0; i < elements.size(); i++) {
            Element layer = elements.get(i);
            if (layer.hasAttribute("parentLayerIndex")) {
                int parent = i(layer, "parentLayerIndex", -1);
                if (parent < 0 || parent >= i) {
                    throw new IOException("Layer parent must precede child in XFL order");
                }
                layers.get(i).set("relatedLayer", layers.get(parent));
            }
            if (!s(layer, "animationType", "").isEmpty()) {
                s.issue(layer, "Advanced layer animation metadata is not imported yet");
            }
        }
        Collections.reverse(layers);
        FlaRecord page = r("CPicPage", "revision", XflReader.Session.V.picPageRevision, "baseRevision", XflReader.Session.V.picPageBaseRevision, "children", layers, "currentFrame", (long) i(e, "currentFrame", 0), "nextLayerId", layers.size() + 1);
        List<List<Integer>> guides = new ArrayList<>();
        String g = s(e, "guides", "");
        if (!g.isEmpty()) {
            Element root = parse(g.getBytes(java.nio.charset.StandardCharsets.UTF_8), "timeline guides").getDocumentElement();
            for (Element line : children(root, "guideline")) {
                guides.add(Arrays.asList(s(line, "direction", "h").equals("h") ? 0 : 1, twips(Double.parseDouble(line.getTextContent()))));
            }
        }
        page.set("guides", guides);
        return page;
    }

    static long rgb(Element e) {
        long rgba = rgba(e, "color", "alpha", 0xff00ff00L);
        return ((rgba & 255) << 16) | (rgba & 0xff00) | ((rgba >>> 16) & 255);
    }

    static FlaRecord blank(int duration) {
        if (duration < 1 || duration > 65535) {
            throw new IllegalArgumentException("Frame duration outside 1..65535");
        }
        return r("CPicFrame", "revision", XflReader.Session.V.picFrameRevision, "shapeRevision", XflReader.Session.V.picShapeRevision, "baseRevision", XflReader.Session.V.picFrameBaseRevision, "duration", duration, "keyMode", 9728, "shapeData", XflShapeReader.empty(), "children", new ArrayList<FlaRecord>());
    }

    @SuppressWarnings("unchecked")
    FlaRecord frame(Element e) throws IOException {
        FlaRecord frame = blank(i(e, "duration", 1));
        String tween = s(e, "tweenType", "");
        int key = i(e, "keyMode", tween.equals("motion") ? 0x5601 : tween.equals("shape") ? 0x5602 : 9728);
        if (tween.equals("motion")) {
            key = (key & 0xff00) | 1;
        } else if (tween.equals("shape")) {
            key = (key & 0xff00) | 2;
        }
        if (b(e, "isMotionObject", false)) {
            key = (key & 0xff00) | 3;
        }
        if (!tween.isEmpty()) {
            for (String attr : new String[]{"motionTweenOrientToPath", "motionTweenScale", "motionTweenSync", "motionTweenSnap"}) {
                int mask = attr.equals("motionTweenOrientToPath") ? 0x100 : attr.equals("motionTweenScale") ? 0x200 : attr.equals("motionTweenSync") ? 0x800 : 0x1000;
                boolean fallback = attr.equals("motionTweenScale");
                key = b(e, attr, fallback) ? key | mask : key & ~mask;
            }
        }
        if (tween.equals("motion")) {
            key = s(e, "motionTweenRotate", "auto").equals("none") ? key & ~0x400 : key | 0x400;
        }
        frame.set("keyMode", key);
        frame.set("name", s(e, "name", ""));
        frame.set("labelType", s(e, "labelType", "").equals("comment") ? 1L : 0L);
        frame.set("bookmark", b(e, "bookmark", s(e, "labelType", "").equals("anchor")) ? 1L : 0L);
        frame.set("acceleration", i(e, "acceleration", 0));
        frame.set("rotationMode", (long) choice(e, "motionTweenRotate", 0, "auto", "clockwise", "counter-clockwise", "none") % 3);
        frame.set("rotationCount", (long) i(e, "motionTweenRotateTimes", 0));
        frame.set("shapeTweenBlend", (long) choice(e, "shapeTweenBlend", 0, "distributive", "angular"));
        frame.set("actions", XflObjectReader.actions(e));
        List<FlaRecord> objects = (List<FlaRecord>) frame.get("children");
        for (Element element : children(child(e, "elements"))) {
            FlaRecord object = new XflObjectReader(s).object(element);
            if (object == null) {
                continue;
            }
            if (tag(element).equals("DOMShape") && !b(element, "isFloating", false) && !b(element, "isDrawingObject", false) && object.get("matrixWords").equals(Arrays.asList(65536, 0, 0, 65536, 0, 0))) {
                XflShapeReader.merge((FlaRecord) frame.get("shapeData"), object);
            } else {
                objects.add(object);
            }
        }
        Element morph = child(e, "MorphShape");
        if (morph != null) {
            if (morph.hasAttribute("mappingType")) {
                frame.set("shapeTweenBlend", (long) choice(morph, "mappingType", 0, "distributive", "angular vertex", "random distribution", "linear walk"));
            }
            FlaRecord model = morph(morph);
            FlaRecord shape = (FlaRecord) frame.get("shapeData");
            model.set("fills", shape.get("fills"));
            model.set("strokes", shape.get("strokes"));
            frame.set("morphShape", model);
        }
        String sound = s(e, "soundName", "");
        if (!sound.isEmpty()) {
            FlaRecord item = s.media.get(sound);
            if (item == null) {
                throw new IOException("Unknown frame sound " + sound);
            }
            frame.set("soundMediaId", ((Number) item.get("mediaId")).intValue());
            frame.set("soundLoopCount", s(e, "soundLoopMode", "").equals("loop") ? 32767 : i(e, "soundLoop", 1));
            frame.set("soundSync", choice(e, "soundSync", 0, "event", "start", "stop", "stream"));
            frame.set("soundEffect", (long) choice(e, "soundEffect", 0, "none", "left channel", "right channel", "fade left to right", "fade right to left", "fade in", "fade out", "custom"));
            frame.set("soundInPoint", (long) i(e, "inPoint44", 0));
            frame.set("soundOutPoint", (long) i(e, "outPoint44", 0x3fffffff));
            frame.set("soundZoom", i(e, "soundZoomLevel", 1));
            List<List<Long>> envelope = new ArrayList<>();
            for (Element point : children(child(e, "SoundEnvelope"), "SoundEnvelopePoint")) {
                envelope.add(Arrays.asList((long) i(point, "mark44", 0), (long) i(point, "level0", 32768), (long) i(point, "level1", 32768)));
            }
            frame.set("soundEnvelope", envelope);
        }
        Element motion = child(e, "motionObjectXML");
        if (motion != null) {
            frame.set("motionXml", inner(motion));
            frame.set("visibleAnimationKeyframes", (long) i(e, "visibleAnimationKeyframes", 0x1fffff));
            frame.set("tweenInstanceName", s(e, "tweenInstanceName", ""));
        }
        ease(e, frame);
        if (b(e, "isIKPose", false)) {
            s.issue(e, "IK pose animation is not imported yet; stored frame contents retained");
        }
        return frame;
    }

    void ease(Element e, FlaRecord frame) throws IOException {
        frame.set("useSingleEaseCurve", b(e, "useSingleEaseCurve", true) ? 1L : 0L);
        List<List<List<Double>>> curves = new ArrayList<>();
        String[] targets = {"position", "rotation", "scale", "color", "filters", "all"};
        for (String target : targets) {
            List<List<Double>> curve = new ArrayList<>();
            for (Element ease : children(child(e, "tweens"), "CustomEase")) {
                if (s(ease, "target", "").equals(target)) {
                    List<Element> points = children(ease, "Point");
                    if (points.size() < 4 || (points.size() - 1) % 3 != 0) {
                        throw new IOException("Custom ease requires 4+3n points");
                    }
                    for (int n = 0; n < points.size(); n += 3) {
                        List<Double> values = new ArrayList<>();
                        for (int j : new int[]{Math.max(0, n - 1), n, Math.min(points.size() - 1, n + 1)}) {
                            values.add(d(points.get(j), "x", 0));
                            values.add(d(points.get(j), "y", 0));
                        }
                        curve.add(values);
                    }
                }
            }
            curves.add(curve);
        }
        boolean present = false;
        for (List<?> curve : curves) {
            present |= !curve.isEmpty();
        }
        if (present) {
            frame.set("customEaseCurves", curves);
        }
    }
    
    @SuppressWarnings("unchecked")
    static void bindMorphStyles(List<FlaRecord> frames) {
        for (int n = 0; n < frames.size(); n++) {
            FlaRecord morph = (FlaRecord) frames.get(n).get("morphShape");
            if (morph == null) {
                continue;
            }
            FlaRecord a = (FlaRecord) frames.get(n).get("shapeData"), b = (FlaRecord) frames.get(Math.min(frames.size() - 1, n + 1)).get("shapeData");
            for (String key : new String[]{"fills", "strokes"}) {
                List<FlaRecord> first = (List<FlaRecord>) a.get(key), second = (List<FlaRecord>) b.get(key), combined = new ArrayList<>();
                for (int i = 0; i < Math.max(first.size(), second.size()); i++) {
                    FlaRecord x = i < first.size() ? first.get(i) : null, y = i < second.size() ? second.get(i) : null;
                    combined.add(x);
                    if (!same(x, y)) {
                        combined.add(y);
                    }
                }
                morph.set(key, combined);
            }
        }
    }

    static boolean same(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a instanceof FlaRecord && b instanceof FlaRecord) {
            return same(((FlaRecord) a).getFields(), ((FlaRecord) b).getFields());
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> x = (Map<?, ?>) a, y = (Map<?, ?>) b;
            if (!x.keySet().equals(y.keySet())) {
                return false;
            }
            for (Object key : x.keySet()) {
                if (!same(x.get(key), y.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List && b instanceof List) {
            List<?> x = (List<?>) a, y = (List<?>) b;
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!same(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    FlaRecord morph(Element e) throws IOException {
        List<FlaRecord> segments = new ArrayList<>();
        for (Element segment : children(child(e, "morphSegments"), "MorphSegment")) {
            FlaRecord r = r("CMorphSegment", "startPointA", point(segment, "startPointA"), "startPointB", point(segment, "startPointB"));
            for (String key : new String[]{"strokeIndex1", "strokeIndex2", "fillIndex1", "fillIndex2"}) {
                r.set(key, (long) i(segment, key, -1));
            }
            List<FlaRecord> curves = new ArrayList<>();
            for (Element c : children(segment, "MorphCurves")) {
                FlaRecord curve = r("CMorphCurve", "lineFlags", b(c, "isLine", false) ? 1L : 0L);
                for (String key : new String[]{"controlPointA", "anchorPointA", "controlPointB", "anchorPointB"}) {
                    curve.set(key, point(c, key));
                }
                curves.add(curve);
            }
            r.set("curves", curves);
            segments.add(r);
        }
        List<FlaRecord> hints = new ArrayList<>();
        for (Element hint : children(child(e, "morphHintsList"), "MorphHint")) {
            hints.add(r("CMorphHintItem", "pointA", point(hint, "startPoint"), "pointB", point(hint, "endPoint"), "index", (long) i(hint, "key", 0)));
        }
        return r("CPicMorphShape", "revision", 2, "segments", segments, "hints", hints, "fills", Collections.emptyList(), "strokes", Collections.emptyList(), "acceleration", (long) i(e, "acceleration", 0));
    }

    static List<Integer> point(Element e, String name) throws IOException {
        String[] xy = s(e, name, "0, 0").split(",");
        if (xy.length != 2) {
            throw new IOException("Invalid morph point " + name);
        }
        return Arrays.asList(XflShapeReader.coord(xy[0].trim()), XflShapeReader.coord(xy[1].trim()));
    }
}
