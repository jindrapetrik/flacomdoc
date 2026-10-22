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

import com.jpexs.flash.fla.converter.streams.*;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * XFL conversion facade: XML -> shared reader model -> common binary writer.
 */
public final class FlaConverter {

    public static final int RULERUNITS_INCHES = 0, RULERUNITS_INCHES_DECIMAL = 1, RULERUNITS_POINTS = 2, RULERUNITS_CM = 3, RULERUNITS_MM = 4, RULERUNITS_PX = 5;
    public static final int SYMBOLTYPE_GRAPHIC = 0, SYMBOLTYPE_BUTTON = 1, SYMBOLTYPE_MOVIECLIP = 2;
    private final FlaFormatVersion target;
    private final Charset charset;

    public FlaConverter(FlaFormatVersion target, String charset) {
        this.target = java.util.Objects.requireNonNull(target);
        this.charset = Charset.forName(charset);
    }

    /**
     * Model imports use deterministic IDs; retained for source compatibility.
     */
    @Deprecated
    public void setDebugRandom(boolean enabled) {
    }

    public void convert(InputStorageInterface input, OutputStorageInterface output) throws IOException, javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        convertWithResult(input, output);
    }

    /**
     * The caller owns both storages. Returns import and downgrade diagnostics.
     */
    public BinaryFlaConversionResult convertWithResult(InputStorageInterface input, OutputStorageInterface output) throws IOException {
        BinaryFlaConversionResult result = new BinaryFlaConverter(target, charset).convert(new XflReader(charset).read(input));
        result.writeTo(output);
        return result;
    }
}
