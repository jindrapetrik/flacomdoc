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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Indexed linear skin deformation data. Offsets address doubles, not bytes or
 * poses.
 */
public final class FlaSkinBindings {

    public static final class Parameters {

        private final int offset;
        private final double longitudinal, perpendicular, distance, weight;

        private Parameters(int offset, List<?> data) throws IOException {
            this.offset = offset;
            if (offset < 0 || offset > data.size() - 4) {
                throw new IOException("Skin parameter offset outside the stored array");
            }
            double[] v = new double[4];
            for (int i = 0; i < 4; i++) {
                Object n = data.get(offset + i);
                if (!(n instanceof Double)) {
                    throw new IOException("Skin parameters must be doubles");
                }
                v[i] = (Double) n;
            }
            longitudinal = v[0];
            perpendicular = v[1];
            distance = v[2];
            weight = v[3];
        }

        public int getOffset() {
            return offset;
        }

        public double getLongitudinalFraction() {
            return longitudinal;
        }

        public double getSignedPerpendicularDistance() {
            return perpendicular;
        }

        public double getDistanceToSegment() {
            return distance;
        }

        public double getWeight() {
            return weight;
        }
    }

    public static final class Connection {

        private final int boneId, cubicFlag;
        private final Parameters anchor, control1, control2;

        private Connection(int boneId, int cubicFlag, Parameters anchor, Parameters control1, Parameters control2) {
            this.boneId = boneId;
            this.cubicFlag = cubicFlag;
            this.anchor = anchor;
            this.control1 = control1;
            this.control2 = control2;
        }

        public int getBoneId() {
            return boneId;
        }

        public int getCubicFlag() {
            return cubicFlag;
        }

        public boolean hasControlPoints() {
            return cubicFlag != 0;
        }

        public Parameters getAnchor() {
            return anchor;
        }

        public Parameters getControl1() {
            return control1;
        }

        public Parameters getControl2() {
            return control2;
        }
    }

    public static final class Point {

        private final int index, terminator;
        private final List<Connection> connections;

        private Point(int index, int terminator, List<Connection> connections) {
            this.index = index;
            this.terminator = terminator;
            this.connections = Collections.unmodifiableList(new ArrayList<>(connections));
        }

        public int getIndex() {
            return index;
        }

        public int getTerminator() {
            return terminator;
        }

        public List<Connection> getConnections() {
            return connections;
        }
    }
    private final int pointCount;
    private final List<Point> points;

    private FlaSkinBindings(int count, List<Point> points) {
        pointCount = count;
        this.points = Collections.unmodifiableList(points);
    }

    /**
     * Validate the native index grammar and every referenced four-double tuple.
     */
    public static FlaSkinBindings fromArrays(List<?> indices, List<?> data) throws IOException {
        Cursor c = new Cursor(indices);
        int count = c.next();
        if (count < 0) {
            throw new IOException("Negative skin point count");
        }
        List<Point> points = new ArrayList<>();
        while (c.hasNext()) {
            int point = c.next();
            if (point < 0 || point >= count) {
                throw new IOException("Skin point index outside declared count");
            }
            List<Connection> links = new ArrayList<>();
            int bone;
            while ((bone = c.next()) >= 0) {
                int offset = c.next(), flag = c.next();
                Parameters a = new Parameters(offset, data), p = null, q = null;
                if (flag != 0) {
                    int one = c.next(), two = c.next();
                    if (one >= 0) {
                        p = new Parameters(one, data);
                    }
                    if (two >= 0) {
                        q = new Parameters(two, data);
                    }
                }
                links.add(new Connection(bone, flag, a, p, q));
            }
            points.add(new Point(point, bone, links));
        }
        return new FlaSkinBindings(count, points);
    }

    public int getDeclaredPointCount() {
        return pointCount;
    }

    public List<Point> getPoints() {
        return points;
    }

    private static final class Cursor {

        private final List<?> values;
        private int index;

        Cursor(List<?> values) {
            if (values == null) {
                throw new NullPointerException("indices");
            }
            this.values = values;
        }

        boolean hasNext() {
            return index < values.size();
        }

        int next() throws IOException {
            if (!hasNext()) {
                throw new IOException("Truncated skin connection indices");
            }
            Object v = values.get(index++);
            if (!(v instanceof Integer)) {
                throw new IOException("Skin indices must be integers");
            }
            return (Integer) v;
        }
    }
}
