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
 * Auxiliary cubic control net and its stored quadratic approximation.
 */
public final class FlaCubic {

    public static final class ApproximationPoint {

        private final FlaPoint2D point;
        private final int controlFlag, secondaryFlag;

        private ApproximationPoint(int x, int y, int controlFlag, int secondaryFlag) {
            point = FlaPoint2D.fromTwips(x, y);
            this.controlFlag = controlFlag;
            this.secondaryFlag = secondaryFlag;
        }

        public FlaPoint2D getPoint() {
            return point;
        }

        public int getControlFlag() {
            return controlFlag;
        }

        public boolean isControl() {
            return controlFlag != 0;
        }

        public int getSecondaryFlag() {
            return secondaryFlag;
        }
    }
    private final List<FlaPoint2D> points;
    private final List<ApproximationPoint> approximation;
    private final Integer flags;
    private final FlaPoint2D boundaryStart, boundaryEnd;

    private FlaCubic(FlaRecord r, int[] w) {
        List<FlaPoint2D> p = new ArrayList<>();
        for (int i = 0; i < 8; i += 2) {
            p.add(FlaPoint2D.fromTwips(w[i], w[i + 1]));
        }
        points = Collections.unmodifiableList(p);
        List<FlaRecord> entries = SemanticFields.records(r, "entries");
        List<ApproximationPoint> a = new ArrayList<>();
        boolean valid = entries != null;
        if (valid) {
            for (FlaRecord entry : entries) {
                Integer x = SemanticFields.integer(entry, "x"), y = SemanticFields.integer(entry, "y"), c = SemanticFields.integer(entry, "controlFlag"), s = SemanticFields.integer(entry, "value2");
                if (x == null || y == null || c == null || s == null) {
                    valid = false;
                    break;
                }
                a.add(new ApproximationPoint(x, y, c, s));
            }
        }
        approximation = valid ? Collections.unmodifiableList(a) : null;
        flags = SemanticFields.integer(r, "flags");
        boundaryStart = flags == null ? null : (flags & 1) == 0 ? p.get(0) : FlaPoint2D.read(r, "boundaryStartPointTwips");
        boundaryEnd = flags == null ? null : (flags & 2) == 0 ? p.get(3) : FlaPoint2D.read(r, "boundaryEndPointTwips");
    }

    public static FlaCubic fromRecord(FlaRecord r) {
        int[] w = SemanticFields.words(r, "points", 8);
        return w == null ? null : new FlaCubic(r, w);
    }

    /**
     * Start, first control, second control, end; all four use whole twips.
     */
    public List<FlaPoint2D> getPoints() {
        return points;
    }

    public List<ApproximationPoint> getApproximation() {
        return approximation;
    }

    public Integer getFlags() {
        return flags;
    }

    public FlaPoint2D getBoundaryStart() {
        return boundaryStart;
    }

    public FlaPoint2D getBoundaryEnd() {
        return boundaryEnd;
    }

    /**
     * Mathematical cubic evaluation in pixels; does not use the cached
     * approximation.
     */
    public double[] evaluatePixels(double t) {
        if (!Double.isFinite(t) || t < 0 || t > 1) {
            throw new IllegalArgumentException("Cubic parameter must be in [0,1]");
        }
        double u = 1 - t;
        double[] b = {u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t};
        double x = 0, y = 0;
        for (int i = 0; i < 4; i++) {
            x += b[i] * points.get(i).getXPixels();
            y += b[i] * points.get(i).getYPixels();
        }
        return new double[]{x, y};
    }
}
