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
package com.jpexs.flash.fla.exporter;

/**
 * XFL document versions, independent of their ZIP/directory container.
 */
public enum XflFormatVersion {
    CS5("2.0"), CS5_5("2.1"), CS6("2.2");

    private final String xflVersion;

    XflFormatVersion(String xflVersion) {
        this.xflVersion = xflVersion;
    }

    public String getXflVersion() {
        return xflVersion;
    }

    public boolean supportsSymbolDisplaySettings() {
        return ordinal() >= CS5_5.ordinal();
    }
}
