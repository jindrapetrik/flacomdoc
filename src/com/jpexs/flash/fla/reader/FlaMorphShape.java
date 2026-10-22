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
 * Correspondence between the two endpoint shapes, without tween easing.
 */
public final class FlaMorphShape {

    public static final class Curve {

        private final FlaPoint2D controlA, anchorA, controlB, anchorB;
        private final Long lineFlags;

        private Curve(FlaRecord r) {
            controlA = FlaPoint2D.read(r, "controlPointA");
            anchorA = FlaPoint2D.read(r, "anchorPointA");
            controlB = FlaPoint2D.read(r, "controlPointB");
            anchorB = FlaPoint2D.read(r, "anchorPointB");
            lineFlags = SemanticFields.unsigned(r, "lineFlags");
        }

        public FlaPoint2D getControlA() {
            return controlA;
        }

        public FlaPoint2D getAnchorA() {
            return anchorA;
        }

        public FlaPoint2D getControlB() {
            return controlB;
        }

        public FlaPoint2D getAnchorB() {
            return anchorB;
        }

        public Long getLineFlags() {
            return lineFlags;
        }

        /**
         * Native XFL export tests the entire dword for nonzero.
         */
        public Boolean isLine() {
            return lineFlags == null ? null : lineFlags != 0;
        }

        public double[] interpolateControlPixels(double fraction) {
            return interpolate(controlA, controlB, fraction);
        }

        public double[] interpolateAnchorPixels(double fraction) {
            return interpolate(anchorA, anchorB, fraction);
        }
    }

    public static final class Segment {

        private final Integer strokeA, strokeB, fillA, fillB;
        private final FlaPoint2D startA, startB;
        private final List<Curve> curves;

        private Segment(FlaRecord r) {
            strokeA = SemanticFields.integer(r, "strokeIndex1");
            strokeB = SemanticFields.integer(r, "strokeIndex2");
            fillA = SemanticFields.integer(r, "fillIndex1");
            fillB = SemanticFields.integer(r, "fillIndex2");
            startA = FlaPoint2D.read(r, "startPointA");
            startB = FlaPoint2D.read(r, "startPointB");
            curves = curveList(r.get("curves"));
        }

        /**
         * Zero-based palette indices; -1 denotes no corresponding style.
         */
        public Integer getStrokeIndexA() {
            return strokeA;
        }

        public Integer getStrokeIndexB() {
            return strokeB;
        }

        public Integer getFillIndexA() {
            return fillA;
        }

        public Integer getFillIndexB() {
            return fillB;
        }

        public FlaPoint2D getStartA() {
            return startA;
        }

        public FlaPoint2D getStartB() {
            return startB;
        }

        public List<Curve> getCurves() {
            return curves;
        }

        public double[] interpolateStartPixels(double fraction) {
            return interpolate(startA, startB, fraction);
        }
    }

    public static final class Hint {

        private final FlaPoint2D a, b;
        private final Long index;

        private Hint(FlaRecord r) {
            a = FlaPoint2D.read(r, "pointA");
            b = FlaPoint2D.read(r, "pointB");
            index = SemanticFields.unsigned(r, "index");
        }

        public FlaPoint2D getPointA() {
            return a;
        }

        public FlaPoint2D getPointB() {
            return b;
        }

        public Long getIndex() {
            return index;
        }
    }
    private final FlaMatrix matrixA, matrixB;
    private final List<Segment> segments;
    private final List<Hint> hints;
    private final List<FlaPaintStyle> fills;
    private final List<FlaStrokeStyle> strokes;
    private final Long extension1, extension2;

    private FlaMorphShape(FlaRecord r) {
        matrixA = r.getMatrix("matrixWordsA");
        matrixB = r.getMatrix("matrixWordsB");
        extension1 = SemanticFields.unsigned(r, "extensionValue1");
        extension2 = SemanticFields.unsigned(r, "acceleration");
        segments = segmentList(r.get("segments"));
        hints = hintList(r.get("hints"));
        List<FlaRecord> fs = SemanticFields.records(r, "fills"), ss = SemanticFields.records(r, "strokes");
        List<FlaPaintStyle> f = new ArrayList<>();
        List<FlaStrokeStyle> s = new ArrayList<>();
        if (fs != null) {
            for (FlaRecord v : fs) {
                f.add(v.getPaintStyle());
            }
        }
        if (ss != null) {
            for (FlaRecord v : ss) {
                s.add(v.getStrokeStyle());
            }
        }
        fills = fs == null ? null : Collections.unmodifiableList(f);
        strokes = ss == null ? null : Collections.unmodifiableList(s);
    }

    public static FlaMorphShape fromRecord(FlaRecord r) {
        return "CPicMorphShape".equals(r.getClassName()) ? new FlaMorphShape(r) : null;
    }

    public FlaMatrix getMatrixA() {
        return matrixA;
    }

    public FlaMatrix getMatrixB() {
        return matrixB;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public List<Hint> getHints() {
        return hints;
    }

    public List<FlaPaintStyle> getFills() {
        return fills;
    }

    public List<FlaStrokeStyle> getStrokes() {
        return strokes;
    }

    public Long getExtensionValue1() {
        return extension1;
    }

    public Integer getAcceleration() {
        return extension2 == null ? null : extension2.intValue();
    }

    /**
     * @deprecated Use getAcceleration() for the signed easing value.
     */
    @Deprecated
    public Long getExtensionValue2() {
        return extension2;
    }

    private static List<Curve> curveList(Object v) {
        if (!(v instanceof List)) {
            return null;
        }
        List<Curve> result = new ArrayList<>();
        for (Object x : (List<?>) v) {
            if (x != null && !(x instanceof FlaRecord)) {
                return null;
            }
            result.add(x == null ? null : new Curve((FlaRecord) x));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Segment> segmentList(Object v) {
        if (!(v instanceof List)) {
            return null;
        }
        List<Segment> result = new ArrayList<>();
        for (Object x : (List<?>) v) {
            if (x != null && !(x instanceof FlaRecord)) {
                return null;
            }
            result.add(x == null ? null : new Segment((FlaRecord) x));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Hint> hintList(Object v) {
        if (!(v instanceof List)) {
            return null;
        }
        List<Hint> result = new ArrayList<>();
        for (Object x : (List<?>) v) {
            if (x != null && !(x instanceof FlaRecord)) {
                return null;
            }
            result.add(x == null ? null : new Hint((FlaRecord) x));
        }
        return Collections.unmodifiableList(result);
    }

    private static double[] interpolate(FlaPoint2D a, FlaPoint2D b, double t) {
        if (!Double.isFinite(t) || t < 0 || t > 1) {
            throw new IllegalArgumentException("Morph fraction must be in [0,1]");
        }
        return a == null || b == null ? null : new double[]{(1 - t) * a.getXPixels() + t * b.getXPixels(), (1 - t) * a.getYPixels() + t * b.getYPixels()};
    }
}
