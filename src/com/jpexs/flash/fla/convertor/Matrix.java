/*
 *  Copyright (C) 2024 JPEXS, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.flash.fla.convertor;

import java.awt.Point;
import java.awt.geom.Point2D;

/**
 * Matrix class.
 *
 * @author JPEXS
 */
public final class Matrix implements Cloneable {

    public double a = 1;

    public double b;

    public double c;

    public double d = 1;

    public double tx;

    public double ty;
    
    public Matrix() {
        a = 1;
        d = 1;
    }
    
    public Matrix(
            double a,
            double b,
            double c,
            double d,
            double tx,
            double ty
    ) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.tx = tx;
        this.ty = ty;
    }

    public Point2D transform(double x, double y) {
        Point2D result = new Point.Double(
                a * x + c * y + tx,
                b * x + d * y + ty);
        return result;
    }

    public Point2D transform(Point2D point) {
        return transform(point.getX(), point.getY());
    }        
}
