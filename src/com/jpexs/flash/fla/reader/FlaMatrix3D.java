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
 * Column-major homogeneous transform; coordinates and translation use twips.
 */
public final class FlaMatrix3D {

    private final float[] values;

    private FlaMatrix3D(float[] values) {
        this.values = values.clone();
    }

    public static FlaMatrix3D fromValues(float... values) {
        if (values == null || values.length != 16) {
            throw new IllegalArgumentException("A 3D matrix has 16 values");
        }
        return new FlaMatrix3D(values);
    }

    static FlaMatrix3D fromRecord(FlaRecord r) {
        Object value = r.get("matrix3D");
        if (!(value instanceof List) || ((List<?>) value).size() != 16) {
            return null;
        }
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) {
            Object v = ((List<?>) value).get(i);
            if (!(v instanceof Float)) {
                return null;
            }
            values[i] = (Float) v;
        }
        return new FlaMatrix3D(values);
    }

    public float[] getColumnMajorValues() {
        return values.clone();
    }

    public float get(int row, int column) {
        if (row < 0 || row > 3 || column < 0 || column > 3) {
            throw new IndexOutOfBoundsException();
        }
        return values[column * 4 + row];
    }

    public double getTxTwips() {
        return values[12];
    }

    public double getTyTwips() {
        return values[13];
    }

    public double getTzTwips() {
        return values[14];
    }

    public double getTxPixels() {
        return getTxTwips() / 20;
    }

    public double getTyPixels() {
        return getTyTwips() / 20;
    }

    public double getTzPixels() {
        return getTzTwips() / 20;
    }

    /**
     * Mathematical multiplication; the returned W is not divided out.
     */
    public double[] transformHomogeneousTwips(double x, double y, double z, double w) {
        double[] in = {x, y, z, w}, out = new double[4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                out[row] += values[col * 4 + row] * in[col];
            }
        }
        return out;
    }

    public boolean isAffine() {
        return values[3] == 0 && values[7] == 0 && values[11] == 0 && values[15] == 1;
    }

    public double[] transformAffinePixels(double x, double y, double z) {
        if (!isAffine()) {
            throw new IllegalStateException("Matrix requires homogeneous projection");
        }
        double[] p = transformHomogeneousTwips(x * 20, y * 20, z * 20, 1);
        return new double[]{p[0] / 20, p[1] / 20, p[2] / 20};
    }
}
