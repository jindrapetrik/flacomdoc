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
import java.awt.geom.AffineTransform;
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

    public static Matrix getScaleInstance(double scale) {
        Matrix mat = new Matrix();
        mat.scale(scale);
        return mat;
    }

    public static Matrix getScaleInstance(double scaleX, double scaleY) {
        Matrix mat = new Matrix();
        mat.scale(scaleX, scaleY);
        return mat;
    }

    public static Matrix getRotateInstance(double rotateAngle) {
        return getRotateInstance(rotateAngle, 0, 0);
    }

    public static Matrix getRotateInstance(double rotateAngle, double tx, double ty) {
        double angleRad = -rotateAngle * Math.PI / 180;
        Matrix mat = new Matrix();
        mat.b = -Math.sin(angleRad);
        mat.c = Math.sin(angleRad);
        mat.a = Math.cos(angleRad);
        mat.d = Math.cos(angleRad);
        mat = mat.preConcatenate(getTranslateInstance(tx, ty))
                .concatenate(getTranslateInstance(-tx, -ty));
        return mat;
    }

    public static Matrix getSkewXInstance(double skewAngle) {
        double angleRad = skewAngle * Math.PI / 180;
        Matrix mat = new Matrix();
        mat.c = Math.tan(angleRad);
        return mat;
    }

    public static Matrix getSkewYInstance(double skewAngle) {
        double angleRad = skewAngle * Math.PI / 180;
        Matrix mat = new Matrix();
        mat.b = Math.tan(angleRad);
        return mat;
    }

    public static Matrix getTranslateInstance(double x, double y) {
        Matrix mat = new Matrix();
        mat.translate(x, y);
        return mat;
    }

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

    public Matrix(AffineTransform transform) {
        this();
        if (transform != null) {
            a = transform.getScaleX();
            c = transform.getShearX();
            tx = transform.getTranslateX();
            b = transform.getShearY();
            d = transform.getScaleY();
            ty = transform.getTranslateY();
        }
    }

    @Override
    public Matrix clone() {
        try {
            Matrix mat = (Matrix) super.clone();
            return mat;
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException();
        }
    }

    public Point2D deltaTransform(double x, double y) {
        Point2D result = new Point2D.Double(
                a * x + c * y,
                b * x + d * y);
        return result;
    }

    public Point2D deltaTransform(Point2D point) {
        return deltaTransform(point.getX(), point.getY());
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

    public void translate(double x, double y) {
        tx = a * x + c * y + tx;
        ty = b * x + d * y + ty;
    }

    public void scale(double factor) {
        a *= factor;
        d *= factor;
        b *= factor;
        c *= factor;
    }

    public void scale(double factorX, double factorY) {
        a *= factorX;
        d *= factorY;
        b *= factorX;
        c *= factorY;
    }

    public Matrix concatenate(Matrix m) {
        Matrix result = new Matrix();
        result.a = a * m.a + c * m.b;
        result.b = b * m.a + d * m.b;
        result.c = a * m.c + c * m.d;
        result.d = b * m.c + d * m.d;
        result.tx = a * m.tx + c * m.ty + tx;
        result.ty = b * m.tx + d * m.ty + ty;
        return result;
    }

    public Matrix preConcatenate(Matrix m) {
        Matrix result = new Matrix();
        result.a = m.a * a + m.c * b;
        result.b = m.b * a + m.d * b;
        result.c = m.a * c + m.c * d;
        result.d = m.b * c + m.d * d;
        result.tx = m.a * tx + m.c * ty + m.tx;
        result.ty = m.b * tx + m.d * ty + m.ty;
        return result;
    }

    public AffineTransform toTransform() {
        AffineTransform transform = new AffineTransform(a, b,
                c, d,
                tx, ty);
        return transform;
    }

    @Override
    public String toString() {
        return "[Matrix scale:" + a + "," + d + ", rotate:" + b + "," + c + ", translate:" + tx + "," + ty + "]";
    }

    public Matrix inverse() {
        double det = a * d - c * b;

        double a2 = d / det;
        double b2 = -c / det;
        double tx2 = (c * ty - tx * d) / det;
        double c2 = -b / det;
        double d2 = a / det;
        double ty2 = (tx * b - a * ty) / det;

        Matrix ret = new Matrix();
        ret.a = a2;
        ret.b = c2;
        ret.c = b2;
        ret.d = d2;
        ret.tx = tx2;
        ret.ty = ty2;
        return ret;
    }
}
