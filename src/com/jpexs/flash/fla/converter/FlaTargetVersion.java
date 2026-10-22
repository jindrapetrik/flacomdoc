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

import com.jpexs.flash.fla.exporter.XflFormatVersion;
import java.util.Locale;

/**
 * User-visible Flash save target: binary through CS4, XFL from CS5.
 */
public enum FlaTargetVersion {
    F1(FlaFormatVersion.F1), F2(FlaFormatVersion.F2), F3(FlaFormatVersion.F3),
    F4(FlaFormatVersion.F4), F5(FlaFormatVersion.F5), MX(FlaFormatVersion.MX),
    MX2004(FlaFormatVersion.MX2004), F8(FlaFormatVersion.F8), CS3(FlaFormatVersion.CS3),
    CS4(FlaFormatVersion.CS4), CS5(XflFormatVersion.CS5),
    CS5_5(XflFormatVersion.CS5_5), CS6(XflFormatVersion.CS6);

    private final FlaFormatVersion binaryVersion;
    private final XflFormatVersion xflVersion;

    FlaTargetVersion(FlaFormatVersion version) {
        binaryVersion = version;
        xflVersion = null;
    }

    FlaTargetVersion(XflFormatVersion version) {
        binaryVersion = null;
        xflVersion = version;
    }

    public boolean isBinary() {
        return binaryVersion != null;
    }

    public FlaFormatVersion getBinaryVersion() {
        if (!isBinary()) {
            throw new IllegalStateException("Target " + this + " uses XFL");
        }
        return binaryVersion;
    }

    public XflFormatVersion getXflVersion() {
        if (isBinary()) {
            throw new IllegalStateException("Target " + this + " uses binary FLA");
        }
        return xflVersion;
    }

    public static FlaTargetVersion parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT).replace('.', '_'));
    }
}
