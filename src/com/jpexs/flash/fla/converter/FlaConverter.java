/*
 * Copyright (C) 2024 JPEXS.
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

import com.jpexs.flash.fla.converter.streams.InputStorageInterface;
import com.jpexs.flash.fla.converter.streams.OutputStorageInterface;
import com.jpexs.flash.fla.converter.swatches.ExtendedSwatchItem;
import com.jpexs.flash.fla.converter.swatches.LinearGradientSwatchItem;
import com.jpexs.flash.fla.converter.swatches.RadialGradientSwatchItem;
import com.jpexs.flash.fla.converter.swatches.SolidSwatchItem;
import com.jpexs.helpers.Reference;
import java.awt.Color;
import java.awt.Font;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import java.util.logging.Logger;

/**
 *
 * @author JPEXS
 */
public class FlaConverter extends AbstractConverter {

    public long timeCreatedMs = Calendar.getInstance().getTimeInMillis();
    public long timeCreated = timeCreatedMs / 1000;

    public static final int RULERUNITS_INCHES = 0;
    public static final int RULERUNITS_INCHES_DECIMAL = 1;
    public static final int RULERUNITS_POINTS = 2;
    public static final int RULERUNITS_CM = 3;
    public static final int RULERUNITS_MM = 4;
    public static final int RULERUNITS_PX = 5;

    public static final int SYMBOLTYPE_GRAPHIC = 0;
    public static final int SYMBOLTYPE_BUTTON = 1;
    public static final int SYMBOLTYPE_MOVIECLIP = 2;

    private static final SecureRandom random = new SecureRandom();

    public FlaConverter(FlaFormatVersion flaFormatVersion, String charset) {
        super(flaFormatVersion, charset);
        if (flaFormatVersion.ordinal() < FlaFormatVersion.F5.ordinal()) {
            throw new UnsupportedOperationException("Version " + flaFormatVersion + " is not supported yet");
        }
    }

    private void writeTime(FlaWriter fg, long time) throws IOException {
        if (debugRandom) {
            fg.write('X', 'X', 'X', 'X');
        } else {
            fg.writeUI32(time);
        }
    }

    private void writeTimeCreated(FlaWriter fg) throws IOException {
        if (debugRandom) {
            fg.write('X', 'X', 'X', 'X');
        } else {
            fg.writeUI32(timeCreated);
        }
    }

    private String getTimeCreatedAsString() {
        if (debugRandom) {
            return "XXXXXXXXXX";
        }
        return "" + timeCreated;
    }

    private String getTimeAsString(long time) {
        if (debugRandom) {
            return "XXXXXXXXXX";
        }
        return "" + time;
    }

    private String generateGUID() {
        int length = 32;

        if (debugRandom) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append('X');
            }
            return sb.toString();
        }

        final String HEX_CHARS = "ABCDEF0123456789";

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(HEX_CHARS.length());
            sb.append(HEX_CHARS.charAt(index));
        }
        return sb.toString();
    }

    private String generateXmppId() {
        int length = 24;

        if (debugRandom) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append('X');
            }
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder(length);

        final String ALPHA_NUMERIC_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final String ALPHA_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        int firstCharIndex = random.nextInt(ALPHA_CHARS.length());
        sb.append(ALPHA_CHARS.charAt(firstCharIndex));

        for (int i = 1; i < length; i++) {
            int index = random.nextInt(ALPHA_NUMERIC_CHARS.length());
            sb.append(ALPHA_NUMERIC_CHARS.charAt(index));
        }

        return sb.toString();
    }

    protected List<SolidSwatchItem> defaultSolidSwatches = Arrays.asList(
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x33, 0x00, 0x50, 0xEF, 0x18),
            new SolidSwatchItem(0x00, 0x66, 0x00, 0x50, 0xEF, 0x30),
            new SolidSwatchItem(0x00, 0x99, 0x00, 0x50, 0xEF, 0x48),
            new SolidSwatchItem(0x00, 0xCC, 0x00, 0x50, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0xFF, 0x00, 0x50, 0xEF, 0x78),
            new SolidSwatchItem(0x33, 0x00, 0x00, 0x00, 0xEF, 0x18),
            new SolidSwatchItem(0x33, 0x33, 0x00, 0x28, 0xEF, 0x18),
            new SolidSwatchItem(0x33, 0x66, 0x00, 0x3C, 0xEF, 0x30),
            new SolidSwatchItem(0x33, 0x99, 0x00, 0x43, 0xEF, 0x48),
            new SolidSwatchItem(0x33, 0xCC, 0x00, 0x46, 0xEF, 0x60),
            new SolidSwatchItem(0x33, 0xFF, 0x00, 0x48, 0xEF, 0x78),
            new SolidSwatchItem(0x66, 0x00, 0x00, 0x00, 0xEF, 0x30),
            new SolidSwatchItem(0x66, 0x33, 0x00, 0x14, 0xEF, 0x30),
            new SolidSwatchItem(0x66, 0x66, 0x00, 0x28, 0xEF, 0x30),
            new SolidSwatchItem(0x66, 0x99, 0x00, 0x35, 0xEF, 0x48),
            new SolidSwatchItem(0x66, 0xCC, 0x00, 0x3C, 0xEF, 0x60),
            new SolidSwatchItem(0x66, 0xFF, 0x00, 0x40, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x33, 0x33, 0x33, 0x00, 0x00, 0x30),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0x33, 0xA0, 0xEF, 0x18),
            new SolidSwatchItem(0x00, 0x33, 0x33, 0x78, 0xEF, 0x18),
            new SolidSwatchItem(0x00, 0x66, 0x33, 0x64, 0xEF, 0x30),
            new SolidSwatchItem(0x00, 0x99, 0x33, 0x5D, 0xEF, 0x48),
            new SolidSwatchItem(0x00, 0xCC, 0x33, 0x5A, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0xFF, 0x33, 0x58, 0xEF, 0x78),
            new SolidSwatchItem(0x33, 0x00, 0x33, 0xC8, 0xEF, 0x18),
            new SolidSwatchItem(0x33, 0x33, 0x33, 0x00, 0x00, 0x30),
            new SolidSwatchItem(0x33, 0x66, 0x33, 0x50, 0x50, 0x48),
            new SolidSwatchItem(0x33, 0x99, 0x33, 0x50, 0x78, 0x60),
            new SolidSwatchItem(0x33, 0xCC, 0x33, 0x50, 0x90, 0x78),
            new SolidSwatchItem(0x33, 0xFF, 0x33, 0x50, 0xEF, 0x90),
            new SolidSwatchItem(0x66, 0x00, 0x33, 0xDC, 0xEF, 0x30),
            new SolidSwatchItem(0x66, 0x33, 0x33, 0x00, 0x50, 0x48),
            new SolidSwatchItem(0x66, 0x66, 0x33, 0x28, 0x50, 0x48),
            new SolidSwatchItem(0x66, 0x99, 0x33, 0x3C, 0x78, 0x60),
            new SolidSwatchItem(0x66, 0xCC, 0x33, 0x43, 0x90, 0x78),
            new SolidSwatchItem(0x66, 0xFF, 0x33, 0x46, 0xEF, 0x90),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x66, 0x66, 0x66, 0x00, 0x00, 0x60),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0x66, 0xA0, 0xEF, 0x30),
            new SolidSwatchItem(0x00, 0x33, 0x66, 0x8C, 0xEF, 0x30),
            new SolidSwatchItem(0x00, 0x66, 0x66, 0x78, 0xEF, 0x30),
            new SolidSwatchItem(0x00, 0x99, 0x66, 0x6B, 0xEF, 0x48),
            new SolidSwatchItem(0x00, 0xCC, 0x66, 0x64, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0xFF, 0x66, 0x60, 0xEF, 0x78),
            new SolidSwatchItem(0x33, 0x00, 0x66, 0xB4, 0xEF, 0x30),
            new SolidSwatchItem(0x33, 0x33, 0x66, 0xA0, 0x50, 0x48),
            new SolidSwatchItem(0x33, 0x66, 0x66, 0x78, 0x50, 0x48),
            new SolidSwatchItem(0x33, 0x99, 0x66, 0x64, 0x78, 0x60),
            new SolidSwatchItem(0x33, 0xCC, 0x66, 0x5D, 0x90, 0x78),
            new SolidSwatchItem(0x33, 0xFF, 0x66, 0x5A, 0xEF, 0x90),
            new SolidSwatchItem(0x66, 0x00, 0x66, 0xC8, 0xEF, 0x30),
            new SolidSwatchItem(0x66, 0x33, 0x66, 0xC8, 0x50, 0x48),
            new SolidSwatchItem(0x66, 0x66, 0x66, 0x00, 0x00, 0x60),
            new SolidSwatchItem(0x66, 0x99, 0x66, 0x50, 0x30, 0x78),
            new SolidSwatchItem(0x66, 0xCC, 0x66, 0x50, 0x78, 0x90),
            new SolidSwatchItem(0x66, 0xFF, 0x66, 0x50, 0xEF, 0xA8),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x99, 0x99, 0x99, 0x00, 0x00, 0x90),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0x99, 0xA0, 0xEF, 0x48),
            new SolidSwatchItem(0x00, 0x33, 0x99, 0x93, 0xEF, 0x48),
            new SolidSwatchItem(0x00, 0x66, 0x99, 0x85, 0xEF, 0x48),
            new SolidSwatchItem(0x00, 0x99, 0x99, 0x78, 0xEF, 0x48),
            new SolidSwatchItem(0x00, 0xCC, 0x99, 0x6E, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0xFF, 0x99, 0x68, 0xEF, 0x78),
            new SolidSwatchItem(0x33, 0x00, 0x99, 0xAD, 0xEF, 0x48),
            new SolidSwatchItem(0x33, 0x33, 0x99, 0xA0, 0x78, 0x60),
            new SolidSwatchItem(0x33, 0x66, 0x99, 0x8C, 0x78, 0x60),
            new SolidSwatchItem(0x33, 0x99, 0x99, 0x78, 0x78, 0x60),
            new SolidSwatchItem(0x33, 0xCC, 0x99, 0x6B, 0x90, 0x78),
            new SolidSwatchItem(0x33, 0xFF, 0x99, 0x64, 0xEF, 0x90),
            new SolidSwatchItem(0x66, 0x00, 0x99, 0xBB, 0xEF, 0x48),
            new SolidSwatchItem(0x66, 0x33, 0x99, 0xB4, 0x78, 0x60),
            new SolidSwatchItem(0x66, 0x66, 0x99, 0xA0, 0x30, 0x78),
            new SolidSwatchItem(0x66, 0x99, 0x99, 0x78, 0x30, 0x78),
            new SolidSwatchItem(0x66, 0xCC, 0x99, 0x64, 0x78, 0x90),
            new SolidSwatchItem(0x66, 0xFF, 0x99, 0x5D, 0xEF, 0xA8),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0xCC, 0xCC, 0xCC, 0x00, 0x00, 0xC0),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0xCC, 0xA0, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0x33, 0xCC, 0x96, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0x66, 0xCC, 0x8C, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0x99, 0xCC, 0x82, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0xCC, 0xCC, 0x78, 0xEF, 0x60),
            new SolidSwatchItem(0x00, 0xFF, 0xCC, 0x70, 0xEF, 0x78),
            new SolidSwatchItem(0x33, 0x00, 0xCC, 0xAA, 0xEF, 0x60),
            new SolidSwatchItem(0x33, 0x33, 0xCC, 0xA0, 0x90, 0x78),
            new SolidSwatchItem(0x33, 0x66, 0xCC, 0x93, 0x90, 0x78),
            new SolidSwatchItem(0x33, 0x99, 0xCC, 0x85, 0x90, 0x78),
            new SolidSwatchItem(0x33, 0xCC, 0xCC, 0x78, 0x90, 0x78),
            new SolidSwatchItem(0x33, 0xFF, 0xCC, 0x6E, 0xEF, 0x90),
            new SolidSwatchItem(0x66, 0x00, 0xCC, 0xB4, 0xEF, 0x60),
            new SolidSwatchItem(0x66, 0x33, 0xCC, 0xAD, 0x90, 0x78),
            new SolidSwatchItem(0x66, 0x66, 0xCC, 0xA0, 0x78, 0x90),
            new SolidSwatchItem(0x66, 0x99, 0xCC, 0x8C, 0x78, 0x90),
            new SolidSwatchItem(0x66, 0xCC, 0xCC, 0x78, 0x78, 0x90),
            new SolidSwatchItem(0x66, 0xFF, 0xCC, 0x6B, 0xEF, 0xA8),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0xFF, 0xFF, 0xFF, 0x00, 0x00, 0xF0),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0xFF, 0xA0, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x33, 0xFF, 0x98, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x66, 0xFF, 0x90, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x99, 0xFF, 0x88, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0xCC, 0xFF, 0x80, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0xFF, 0xFF, 0x78, 0xEF, 0x78),
            new SolidSwatchItem(0x33, 0x00, 0xFF, 0xA8, 0xEF, 0x78),
            new SolidSwatchItem(0x33, 0x33, 0xFF, 0xA0, 0xEF, 0x90),
            new SolidSwatchItem(0x33, 0x66, 0xFF, 0x96, 0xEF, 0x90),
            new SolidSwatchItem(0x33, 0x99, 0xFF, 0x8C, 0xEF, 0x90),
            new SolidSwatchItem(0x33, 0xCC, 0xFF, 0x82, 0xEF, 0x90),
            new SolidSwatchItem(0x33, 0xFF, 0xFF, 0x78, 0xEF, 0x90),
            new SolidSwatchItem(0x66, 0x00, 0xFF, 0xB0, 0xEF, 0x78),
            new SolidSwatchItem(0x66, 0x33, 0xFF, 0xAA, 0xEF, 0x90),
            new SolidSwatchItem(0x66, 0x66, 0xFF, 0xA0, 0xEF, 0xA8),
            new SolidSwatchItem(0x66, 0x99, 0xFF, 0x93, 0xEF, 0xA8),
            new SolidSwatchItem(0x66, 0xCC, 0xFF, 0x85, 0xEF, 0xA8),
            new SolidSwatchItem(0x66, 0xFF, 0xFF, 0x78, 0xEF, 0xA8),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0xFF, 0x00, 0x00, 0x00, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x99, 0x00, 0x00, 0x00, 0xEF, 0x48),
            new SolidSwatchItem(0x99, 0x33, 0x00, 0x0D, 0xEF, 0x48),
            new SolidSwatchItem(0x99, 0x66, 0x00, 0x1B, 0xEF, 0x48),
            new SolidSwatchItem(0x99, 0x99, 0x00, 0x28, 0xEF, 0x48),
            new SolidSwatchItem(0x99, 0xCC, 0x00, 0x32, 0xEF, 0x60),
            new SolidSwatchItem(0x99, 0xFF, 0x00, 0x38, 0xEF, 0x78),
            new SolidSwatchItem(0xCC, 0x00, 0x00, 0x00, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0x33, 0x00, 0x0A, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0x66, 0x00, 0x14, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0x99, 0x00, 0x1E, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0xCC, 0x00, 0x28, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0xFF, 0x00, 0x30, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x00, 0x00, 0x00, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x33, 0x00, 0x08, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x66, 0x00, 0x10, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x99, 0x00, 0x18, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0xCC, 0x00, 0x20, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0xFF, 0x00, 0x28, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0xFF, 0x00, 0x50, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x99, 0x00, 0x33, 0xE3, 0xEF, 0x48),
            new SolidSwatchItem(0x99, 0x33, 0x33, 0x00, 0x78, 0x60),
            new SolidSwatchItem(0x99, 0x66, 0x33, 0x14, 0x78, 0x60),
            new SolidSwatchItem(0x99, 0x99, 0x33, 0x28, 0x78, 0x60),
            new SolidSwatchItem(0x99, 0xCC, 0x33, 0x35, 0x90, 0x78),
            new SolidSwatchItem(0x99, 0xFF, 0x33, 0x3C, 0xEF, 0x90),
            new SolidSwatchItem(0xCC, 0x00, 0x33, 0xE6, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0x33, 0x33, 0x00, 0x90, 0x78),
            new SolidSwatchItem(0xCC, 0x66, 0x33, 0x0D, 0x90, 0x78),
            new SolidSwatchItem(0xCC, 0x99, 0x33, 0x1B, 0x90, 0x78),
            new SolidSwatchItem(0xCC, 0xCC, 0x33, 0x28, 0x90, 0x78),
            new SolidSwatchItem(0xCC, 0xFF, 0x33, 0x32, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0x00, 0x33, 0xE8, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x33, 0x33, 0x00, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0x66, 0x33, 0x0A, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0x99, 0x33, 0x14, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0xCC, 0x33, 0x1E, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0xFF, 0x33, 0x28, 0xEF, 0x90),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0x00, 0xFF, 0xA0, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x99, 0x00, 0x66, 0xD5, 0xEF, 0x48),
            new SolidSwatchItem(0x99, 0x33, 0x66, 0xDC, 0x78, 0x60),
            new SolidSwatchItem(0x99, 0x66, 0x66, 0x00, 0x30, 0x78),
            new SolidSwatchItem(0x99, 0x99, 0x66, 0x28, 0x30, 0x78),
            new SolidSwatchItem(0x99, 0xCC, 0x66, 0x3C, 0x78, 0x90),
            new SolidSwatchItem(0x99, 0xFF, 0x66, 0x43, 0xEF, 0xA8),
            new SolidSwatchItem(0xCC, 0x00, 0x66, 0xDC, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0x33, 0x66, 0xE3, 0x90, 0x78),
            new SolidSwatchItem(0xCC, 0x66, 0x66, 0x00, 0x78, 0x90),
            new SolidSwatchItem(0xCC, 0x99, 0x66, 0x14, 0x78, 0x90),
            new SolidSwatchItem(0xCC, 0xCC, 0x66, 0x28, 0x78, 0x90),
            new SolidSwatchItem(0xCC, 0xFF, 0x66, 0x35, 0xEF, 0xA8),
            new SolidSwatchItem(0xFF, 0x00, 0x66, 0xE0, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x33, 0x66, 0xE6, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0x66, 0x66, 0x00, 0xEF, 0xA8),
            new SolidSwatchItem(0xFF, 0x99, 0x66, 0x0D, 0xEF, 0xA8),
            new SolidSwatchItem(0xFF, 0xCC, 0x66, 0x1B, 0xEF, 0xA8),
            new SolidSwatchItem(0xFF, 0xFF, 0x66, 0x28, 0xEF, 0xA8),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0xFF, 0xFF, 0x00, 0x28, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x99, 0x00, 0x99, 0xC8, 0xEF, 0x48),
            new SolidSwatchItem(0x99, 0x33, 0x99, 0xC8, 0x78, 0x60),
            new SolidSwatchItem(0x99, 0x66, 0x99, 0xC8, 0x30, 0x78),
            new SolidSwatchItem(0x99, 0x99, 0x99, 0x00, 0x00, 0x90),
            new SolidSwatchItem(0x99, 0xCC, 0x99, 0x50, 0x50, 0xA8),
            new SolidSwatchItem(0x99, 0xFF, 0x99, 0x50, 0xEF, 0xC0),
            new SolidSwatchItem(0xCC, 0x00, 0x99, 0xD2, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0x33, 0x99, 0xD5, 0x90, 0x78),
            new SolidSwatchItem(0xCC, 0x66, 0x99, 0xDC, 0x78, 0x90),
            new SolidSwatchItem(0xCC, 0x99, 0x99, 0x00, 0x50, 0xA8),
            new SolidSwatchItem(0xCC, 0xCC, 0x99, 0x28, 0x50, 0xA8),
            new SolidSwatchItem(0xCC, 0xFF, 0x99, 0x3C, 0xEF, 0xC0),
            new SolidSwatchItem(0xFF, 0x00, 0x99, 0xD8, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x33, 0x99, 0xDC, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0x66, 0x99, 0xE3, 0xEF, 0xA8),
            new SolidSwatchItem(0xFF, 0x99, 0x99, 0x00, 0xEF, 0xC0),
            new SolidSwatchItem(0xFF, 0xCC, 0x99, 0x14, 0xEF, 0xC0),
            new SolidSwatchItem(0xFF, 0xFF, 0x99, 0x28, 0xEF, 0xC0),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x00, 0xFF, 0xFF, 0x78, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x99, 0x00, 0xCC, 0xBE, 0xEF, 0x60),
            new SolidSwatchItem(0x99, 0x33, 0xCC, 0xBB, 0x90, 0x78),
            new SolidSwatchItem(0x99, 0x66, 0xCC, 0xB4, 0x78, 0x90),
            new SolidSwatchItem(0x99, 0x99, 0xCC, 0xA0, 0x50, 0xA8),
            new SolidSwatchItem(0x99, 0xCC, 0xCC, 0x78, 0x50, 0xA8),
            new SolidSwatchItem(0x99, 0xFF, 0xCC, 0x64, 0xEF, 0xC0),
            new SolidSwatchItem(0xCC, 0x00, 0xCC, 0xC8, 0xEF, 0x60),
            new SolidSwatchItem(0xCC, 0x33, 0xCC, 0xC8, 0x90, 0x78),
            new SolidSwatchItem(0xCC, 0x66, 0xCC, 0xC8, 0x78, 0x90),
            new SolidSwatchItem(0xCC, 0x99, 0xCC, 0xC8, 0x50, 0xA8),
            new SolidSwatchItem(0xCC, 0xCC, 0xCC, 0x00, 0x00, 0xC0),
            new SolidSwatchItem(0xCC, 0xFF, 0xCC, 0x50, 0xEF, 0xD8),
            new SolidSwatchItem(0xFF, 0x00, 0xCC, 0xD0, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x33, 0xCC, 0xD2, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0x66, 0xCC, 0xD5, 0xEF, 0xA8),
            new SolidSwatchItem(0xFF, 0x99, 0xCC, 0xDC, 0xEF, 0xC0),
            new SolidSwatchItem(0xFF, 0xCC, 0xCC, 0x00, 0xEF, 0xD8),
            new SolidSwatchItem(0xFF, 0xFF, 0xCC, 0x28, 0xEF, 0xD8),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0xFF, 0x00, 0xFF, 0xC8, 0xEF, 0x78),
            new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            new SolidSwatchItem(0x99, 0x00, 0xFF, 0xB8, 0xEF, 0x78),
            new SolidSwatchItem(0x99, 0x33, 0xFF, 0xB4, 0xEF, 0x90),
            new SolidSwatchItem(0x99, 0x66, 0xFF, 0xAD, 0xEF, 0xA8),
            new SolidSwatchItem(0x99, 0x99, 0xFF, 0xA0, 0xEF, 0xC0),
            new SolidSwatchItem(0x99, 0xCC, 0xFF, 0x8C, 0xEF, 0xC0),
            new SolidSwatchItem(0x99, 0xFF, 0xFF, 0x78, 0xEF, 0xC0),
            new SolidSwatchItem(0xCC, 0x00, 0xFF, 0xC0, 0xEF, 0x78),
            new SolidSwatchItem(0xCC, 0x33, 0xFF, 0xBE, 0xEF, 0x90),
            new SolidSwatchItem(0xCC, 0x66, 0xFF, 0xBB, 0xEF, 0xA8),
            new SolidSwatchItem(0xCC, 0x99, 0xFF, 0xB4, 0xEF, 0xC0),
            new SolidSwatchItem(0xCC, 0xCC, 0xFF, 0xA0, 0xEF, 0xD8),
            new SolidSwatchItem(0xCC, 0xFF, 0xFF, 0x78, 0xEF, 0xD8),
            new SolidSwatchItem(0xFF, 0x00, 0xFF, 0xC8, 0xEF, 0x78),
            new SolidSwatchItem(0xFF, 0x33, 0xFF, 0xC8, 0xEF, 0x90),
            new SolidSwatchItem(0xFF, 0x66, 0xFF, 0xC8, 0xEF, 0xA8),
            new SolidSwatchItem(0xFF, 0x99, 0xFF, 0xC8, 0xEF, 0xC0),
            new SolidSwatchItem(0xFF, 0xCC, 0xFF, 0xC8, 0xEF, 0xD8),
            new SolidSwatchItem(0xFF, 0xFF, 0xFF, 0x00, 0x00, 0xF0)
    );

    protected List<ExtendedSwatchItem> defaultExtendedSwatches = Arrays.asList(
            new LinearGradientSwatchItem(new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0), new GradientEntry(1)),
            new RadialGradientSwatchItem(new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0), new GradientEntry(1)),
            new RadialGradientSwatchItem(new GradientEntry(new Color(0xFF, 0x00, 0x00), 0), new GradientEntry(1)),
            new RadialGradientSwatchItem(new GradientEntry(new Color(0x00, 0xFF, 0x00), 0), new GradientEntry(1)),
            new RadialGradientSwatchItem(new GradientEntry(new Color(0x00, 0x00, 0xFF), 0), new GradientEntry(1)),
            new LinearGradientSwatchItem(
                    new GradientEntry(new Color(0x00, 0x66, 0xFD), 0),
                    new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0.376470588235294f),
                    new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0.47843137254902f),
                    new GradientEntry(new Color(0x99, 0x66, 0x00), 0.501960784313725f),
                    new GradientEntry(new Color(0xFF, 0xcc, 0x00), 0.666666666666667f),
                    new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 1)
            ),
            new LinearGradientSwatchItem(
                    new GradientEntry(new Color(0xFF, 0x00, 0x00), 0),
                    new GradientEntry(new Color(0xFF, 0xFF, 0x00), 0.164705882352941f),
                    new GradientEntry(new Color(0x00, 0xFF, 0x00), 0.364705882352941f),
                    new GradientEntry(new Color(0x00, 0xFF, 0xFF), 0.498039215686275f),
                    new GradientEntry(new Color(0x00, 0x00, 0xFF), 0.666666666666667f),
                    new GradientEntry(new Color(0xFF, 0x00, 0xFF), 0.831372549019608f),
                    new GradientEntry(new Color(0xFF, 0x00, 0x00), 1)
            ));

    private void writeAsLinkage(FlaWriter dw, Element element) throws IOException {
        boolean linkageExportForAS = false;
        if (element.hasAttribute("linkageExportForAS")) {
            linkageExportForAS = "true".equals(element.getAttribute("linkageExportForAS"));
        }

        String linkageClassName = "";
        if (element.hasAttribute("linkageClassName")) {
            linkageClassName = element.getAttribute("linkageClassName");
        }

        boolean linkageExportInFirstFrame = true;
        if (element.hasAttribute("linkageExportInFirstFrame")) {
            linkageExportInFirstFrame = !"false".equals(element.getAttribute("linkageExportInFirstFrame"));
        }

        String linkageBaseClass = "";
        if (element.hasAttribute("linkageBaseClass")) {
            linkageBaseClass = element.getAttribute("linkageBaseClass");
        }

        boolean linkageExportForRS = false;
        if (element.hasAttribute("linkageExportForRS")) {
            linkageExportForRS = "true".equals(element.getAttribute("linkageExportForRS"));
        }

        String linkageURL = "";
        if (element.hasAttribute("linkageURL")) {
            linkageURL = element.getAttribute("linkageURL");
        }

        boolean linkageImportForRS = false;
        if (element.hasAttribute("linkageImportForRS")) {
            linkageImportForRS = "true".equals(element.getAttribute("linkageImportForRS"));
        }

        String sourceLibraryItemHRef = "";
        if (element.hasAttribute("sourceLibraryItemHRef")) {
            sourceLibraryItemHRef = element.getAttribute("sourceLibraryItemHRef");
        }
        String linkageIdentifier = "";
        if (element.hasAttribute("linkageIdentifier")) {
            linkageIdentifier = element.getAttribute("linkageIdentifier");
        }

        dw.write(0x00, 0x00,
                0x00, 0x00);
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
            dw.write(flaFormatVersion.getAsLinkageVersion());

            dw.write((linkageExportForAS ? 1 : 0) + (linkageImportForRS ? 2 : 0));
            dw.write(0x00, 0x00, 0x00
            );
            dw.writeBomString(linkageIdentifier);
            dw.writeBomString(linkageURL);
        }

        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            dw.writeBomString(linkageClassName);
        }
        int linkageFlags = 0;
        if (linkageExportInFirstFrame) {
            linkageFlags |= 4;
        }
        if (linkageExportForAS) {
            linkageFlags |= 1;
        }
        if (linkageExportForRS) {
            linkageFlags |= 2;
        }
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX.ordinal()) {
            dw.write(debugRandom ? 'X' : linkageFlags, //:-( sometimes, there's just no 4 flag, randomly
                    flaFormatVersion.getAsLinkageVersionB(), 0x00, 0x00, 0x00);
            dw.writeBomString("");
            dw.writeBomString(sourceLibraryItemHRef);
            dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0xFF, 0xFF, 0xFF, 0xFF);
        }
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            dw.write(0x00);
            dw.writeBomString(linkageBaseClass);
        }
    }

    private String getParentFolderItemID(Element document, String itemName) {
        if (!itemName.contains("/")) {
            return null;
        }
        String folderName = itemName.substring(0, itemName.lastIndexOf("/"));
        Element foldersElement = getSubElementByName(document, "folders");
        if (foldersElement == null) {
            return null;
        }
        List<Element> domFolderItems = getAllSubElementsByName(foldersElement, "DOMFolderItem");
        for (Element domFolderItem : domFolderItems) {
            if (domFolderItem.getAttribute("name").equals(folderName)) {
                if (domFolderItem.hasAttribute("itemID")) {
                    return domFolderItem.getAttribute("itemID");
                }
                return null;
            }
        }
        return null;
    }

    private int writeSymbols(FlaWriter fg, Element document, DocumentBuilder docBuilder, InputStorageInterface sourceDir, OutputStorageInterface outputDir, Reference<Long> generatedItemIdOrder, Map<String, Integer> definedClasses, Reference<Integer> objectsCount) throws SAXException, IOException, FileNotFoundException, ParserConfigurationException {
        int symbolCount = 0;
        List<Element> includes = getSymbols(document);
        List<Element> sorted = new ArrayList<>(includes);
        sorted.sort(new Comparator<Element>() {
            @Override
            public int compare(Element o1, Element o2) {
                int itemIcon1 = -1;
                int itemIcon2 = -1;
                if (o1.hasAttribute("itemIcon")) {
                    itemIcon1 = Integer.parseInt(o1.getAttribute("itemIcon"));
                }
                if (o2.hasAttribute("itemIcon")) {
                    itemIcon2 = Integer.parseInt(o2.getAttribute("itemIcon"));
                }
                return itemIcon1 - itemIcon2;
            }
        });

        for (int i = 0; i < includes.size(); i++) {
            Element include = includes.get(i);
            if (!include.hasAttribute("href")) {
                continue;
            }
            String href = include.getAttribute("href");
            Document symbolDocument = docBuilder.parse(sourceDir.readFile("LIBRARY/" + href));
            Element symbolElement = symbolDocument.getDocumentElement();
            Element timelineElement = getSubElementByName(symbolElement, "timeline");
            if (timelineElement == null) {
                continue;
            }
            Element domTimelineElement = getSubElementByName(timelineElement, "DOMTimeline");
            if (domTimelineElement == null) {
                continue;
            }
            symbolCount++;
            int symbolId = i + 1;
            long symbolTime = timeCreated;
            String symbolFile = "S " + symbolId + " " + getTimeAsString(symbolTime);
            if (debugRandom) {
                int symbolIdOrdered = sorted.indexOf(includes.get(i)) + 1;
                symbolFile = "S " + symbolIdOrdered + " " + getTimeAsString(symbolTime);
            }
            String symbolName = "Symbol " + symbolId;
            if (domTimelineElement.hasAttribute("name")) {
                symbolName = domTimelineElement.getAttribute("name");
            }

            String symbolFullName = symbolElement.getAttribute("name");
            String parentFolderItemId = getParentFolderItemID(document, symbolFullName);

            //scaleGridLeft="22.75" scaleGridRight="68.25" scaleGridTop="22.75" scaleGridBottom="68.25" 
            float scaleGridLeft = 0f;
            if (symbolElement.hasAttribute("scaleGridLeft")) {
                scaleGridLeft = Float.parseFloat(symbolElement.getAttribute("scaleGridLeft"));
            }

            float scaleGridRight = 0f;
            if (symbolElement.hasAttribute("scaleGridRight")) {
                scaleGridRight = Float.parseFloat(symbolElement.getAttribute("scaleGridRight"));
            }

            float scaleGridTop = 0f;
            if (symbolElement.hasAttribute("scaleGridTop")) {
                scaleGridTop = Float.parseFloat(symbolElement.getAttribute("scaleGridTop"));
            }

            float scaleGridBottom = 0f;
            if (symbolElement.hasAttribute("scaleGridBottom")) {
                scaleGridBottom = Float.parseFloat(symbolElement.getAttribute("scaleGridBottom"));
            }

            int symbolType = getAttributeAsInt(symbolElement, "symbolType", Arrays.asList("graphic", "button", "movie clip"), "movie clip");

            String itemID = generateItemID(generatedItemIdOrder);

            if (symbolElement.hasAttribute("itemID")) {
                itemID = symbolElement.getAttribute("itemID");
            }

            useClass("CDocumentPage", 0x01, fg, definedClasses, objectsCount);
            fg.write(flaFormatVersion.getDocumentPageVersion());
            fg.writeString(debugRandom ? "YYY" : symbolFile);
            fg.writeBomString(symbolName);
            if (debugRandom) {
                fg.write('X', 'X');
            } else {
                fg.writeUI16(symbolId);
            }
            fg.write(0x00, 0x00, symbolType);
            fg.writeBomString("");

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F4.ordinal()) {
                fg.write(0x01, 0x00, 0x00, 0x00, flaFormatVersion.getSpriteVersionE());
                fg.write(0x00);
                fg.write(0x00, 0x00,
                        0x01, 0x00, 0x00, 0x00);
                if (parentFolderItemId == null) {
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                } else {
                    fg.writeItemID(parentFolderItemId);
                }
                fg.write(0x01, 0x00, 0x00, 0x00);

                fg.writeItemID(itemID);

                writeAsLinkage(fg, symbolElement);
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                fg.write(0x00);
            }

            writeTime(fg, symbolTime);
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                fg.writeBomString("");
                fg.writeBomString("");
            }

            if (flaFormatVersion.ordinal() == FlaFormatVersion.F5.ordinal()) {
                fg.write(0x01, 0x00, 0x00, 0x00, 0x00, 0x01);
            } else {
                fg.write(0x02, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x00, 0x00, 0x00,
                        0x01, 0x00, 0x00, 0x00,
                        flaFormatVersion.getSpriteVersionC()); //getDocumentPageVersionC
                fg.write(0x00, 0x00, 0x00);
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                    fg.write(0x00);
                }
                fg.writeBomString("");
                fg.writeBomString("");
                fg.writeBomString("");

                fg.write(0x00,
                        flaFormatVersion.getSpriteVersionF(),
                        0x00, 0x00, 0x00);
                fg.writeBomString("");
                fg.writeBomString("");
                fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFF, 0xFF, 0xFF);

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {

                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                        fg.write(0x00);
                        fg.writeBomString("");
                    }
                    fg.write(
                            flaFormatVersion.getSpriteVersionD(), //getDocumentPageVersionD
                            0x00,
                            0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.write(0x03);
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x03);
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x03);
                    fg.writeBomString("");
                } else {
                    fg.write(0x00, flaFormatVersion.getSpriteVersionD());
                }
                fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                fg.writeBomString("");
                fg.write(0x02, 0x00, 0x00, 0x00, 0x00);
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                    fg.write(0x01, 0x00);
                }
                fg.write(0x00, 0x00);
                fg.writeBomString("");
                fg.write(0x00, 0x00, 0x00, 0x00, 0x00);
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                fg.write(0x00, 0x00, 0x00);
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                fg.write(0x00, 0x00, 0x00, 0x00);
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                fg.write(0x01, 0x00, 0x00, 0x00);
                fg.writeBomString("");
                fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                fg.writeBomString("");
                fg.writeBomString("");
                fg.write(0x00, 0x00, 0x00, 0x00);
                fg.writeBomString("");
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                if (scaleGridLeft != 0f || scaleGridRight != 0f || scaleGridTop != 0f || scaleGridBottom != 0f) {
                    fg.write(0x01, 0x00, 0x00, 0x00);
                    fg.writeUI32(Math.round(scaleGridRight * 20));
                    fg.writeUI32(Math.round(scaleGridLeft * 20));
                    fg.writeUI32(Math.round(scaleGridBottom * 20));
                    fg.writeUI32(Math.round(scaleGridTop * 20));
                } else {
                    fg.write(0x00, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x80,
                            0x00, 0x00, 0x00, 0x80,
                            0x00, 0x00, 0x00, 0x80,
                            0x00, 0x00, 0x00, 0x80);
                }
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                fg.write(0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            }

            if (flaFormatVersion == FlaFormatVersion.CS4) {
                fg.write(0x00, 0x00);
            }
            TimelineConverter symbolPageGenerator = new TimelineConverter(flaFormatVersion, charset, symbolName);
            symbolPageGenerator.setDebugRandom(debugRandom);
            try (OutputStream sos = outputDir.getOutputStream(symbolFile)) {
                symbolPageGenerator.convert(domTimelineElement, document, sos);
            }
        }

        return symbolCount;
    }

    public void convert(
            InputStorageInterface sourceDir,
            OutputStorageInterface outputDir
    ) throws SAXException, IOException, ParserConfigurationException {

        InputStream domDocumentIs = sourceDir.readFile("DOMDocument.xml");
        InputStream publishSettingsIs = sourceDir.readFile("PublishSettings.xml");
        InputStream metadataIs = sourceDir.readFile("META-INF/metadata.xml");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(false);
        DocumentBuilder docBuilder = factory.newDocumentBuilder();

        Document domDocument = docBuilder.parse(domDocumentIs);
        Document publishSettings = publishSettingsIs == null ? null : docBuilder.parse(publishSettingsIs);
        Document metadata = metadataIs == null ? null : docBuilder.parse(metadataIs);

        Color backgroundColor = Color.white;
        Element document = domDocument.getDocumentElement();
        if (document.hasAttribute("backgroundColor")) {
            backgroundColor = parseColor(document.getAttribute("backgroundColor"));
        }

        int width = 550;
        int height = 400;

        if (document.hasAttribute("width")) {
            width = Integer.parseInt(document.getAttribute("width"));
        }
        if (document.hasAttribute("height")) {
            height = Integer.parseInt(document.getAttribute("height"));
        }

        float frameRate = 24;
        if (document.hasAttribute("frameRate")) {
            frameRate = Float.parseFloat(document.getAttribute("frameRate"));
        }

        List<Element> timelinesElements = getAllSubElementsByName(document, "timelines");

        Map<String, Integer> definedClasses = new HashMap<>();
        Reference<Integer> objectsCount = new Reference<>(0);

        try (OutputStream os = outputDir.getOutputStream("Contents")) {
            FlaWriter fg = new FlaWriter(os, flaFormatVersion, charset);
            fg.setTitle("Contents");
            if (debugRandom) {
                fg.setDebugRandom(true);
            }
            fg.write(flaFormatVersion.getContentsVersion());
            fg.write(flaFormatVersion.getContentsVersionB());

            fg.write(0x00, 0x00, 0x00);

            if (flaFormatVersion.ordinal() >= flaFormatVersion.F3.ordinal()) {
                fg.write(0x00);
            }
            if (flaFormatVersion.ordinal() >= flaFormatVersion.F4.ordinal()) {
                fg.write(0x00);
            }
            if (flaFormatVersion.ordinal() >= flaFormatVersion.F5.ordinal()) {
                fg.write(0x00, 0x00, 0x00, 0x00);
            }

            if (flaFormatVersion.ordinal() >= flaFormatVersion.MX.ordinal()) {
                fg.write(0x00, 0x00, 0x00, 0x00);
            }

            if (flaFormatVersion.ordinal() >= flaFormatVersion.MX2004.ordinal()) {
                fg.write(0x00, 0x00, 0x00, 0x00);
            }
            if (flaFormatVersion.ordinal() >= flaFormatVersion.F8.ordinal()) {
                fg.write(0x00, 0x00, 0x00, 0x00);
            }

            if (flaFormatVersion.ordinal() >= flaFormatVersion.CS3.ordinal()) {
                fg.write(0x00, 0x00, 0x00, 0x00);
            }

            if (flaFormatVersion == FlaFormatVersion.CS4) {
                fg.write(0x00, 0x00, 0x00, 0x00);
            }

            int pageCount = 0;
            Reference<Long> generatedItemIdOrder = new Reference<>(0L);

            for (Element timelinesElement : timelinesElements) {
                Element domTimeline = getSubElementByName(timelinesElement, "DOMTimeline");
                if (domTimeline == null) {
                    continue;
                }
                if (!domTimeline.hasAttribute("name")) {
                    continue;
                }
                String sceneName = domTimeline.getAttribute("name");

                useClass("CDocumentPage", 1, fg, definedClasses, objectsCount);
                fg.write(flaFormatVersion.getDocumentPageVersion());

                pageCount++;

                String pageName = "P " + pageCount + " " + getTimeCreatedAsString();

                String debugPageName = "YYY"; //"P X " + getTimeCreatedAsString();

                fg.writeString(debugRandom ? debugPageName : pageName);
                fg.writeBomString(sceneName);
                fg.write(0x00, 0x00); //symbolId:UI16. FIXME? Is it 32 bit?
                fg.write(0x00, 0x00);
                fg.write(0x00); //symbolType

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F4.ordinal()) {
                    fg.writeBomString("");
                    fg.write(0x01, 0x00, 0x00, 0x00, flaFormatVersion.getDocumentPageVersionE(),
                            0x00,
                            0x00, 0x00,
                            0x01, 0x00, 0x00, 0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //parent folder id
                            0x01, 0x00, 0x00, 0x00);

                    String pageItemID = generateItemID(generatedItemIdOrder);

                    fg.writeItemID(pageItemID);
                    //begin AsLinkage
                    fg.write(0x00, 0x00, 0x00, 0x00);
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                    fg.write(flaFormatVersion.getAsLinkageVersion(), //getAsLinkageVersion
                            0x00, //linkageExportForAS | linkageImportForRS
                            0x00, 0x00, 0x00);
                    fg.writeBomString(""); //linkageIdentifier
                    fg.writeBomString(""); //linkageURL
                }
                if (flaFormatVersion.ordinal() >= flaFormatVersion.MX.ordinal()) {
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                        fg.writeBomString(""); //linkageClassName
                    }

                    fg.write(debugRandom ? 'X' : 0); //linkageFlags

                    fg.write(flaFormatVersion.getAsLinkageVersionB());

                    fg.write(0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.writeBomString(""); //sourceLibraryItemHRef

                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0xFF, 0xFF, 0xFF, 0xFF);
                }
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                    fg.write(0x00);
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                    fg.writeBomString(""); //linkageBaseClass
                    fg.write(0x00);
                }
                //end of AsLinkage

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F4.ordinal()) {
                    writeTimeCreated(fg);
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                    fg.writeBomString("");
                    fg.writeBomString("");
                }
                if (flaFormatVersion.ordinal() == FlaFormatVersion.F5.ordinal()) {
                    fg.write(0x01, 0x00, 0x00, 0x00, 0x00, 0x01);
                } else {
                    fg.write(0x02, 0x00, 0x00, 0x00, 0x00,
                            debugRandom ? 'U' : 0x01, //NEW - different from sprite
                            0x00, 0x00, 0x00,
                            0x01, 0x00, 0x00, 0x00,
                            flaFormatVersion.getSpriteVersionC()); //getDocumentPageVersionC
                    fg.write(0x00, 0x00, 0x00);
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                        fg.write(0x00);
                    }
                    fg.writeBomString("");
                    fg.writeBomString("");
                    fg.writeBomString("");

                    fg.write(0x00,
                            flaFormatVersion.getSpriteVersionF(),
                            0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0xFF, 0xFF, 0xFF, 0xFF);

                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {

                        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                            fg.write(0x00);
                            fg.writeBomString("");
                        }
                        fg.write(
                                flaFormatVersion.getSpriteVersionD(), //getDocumentPageVersionD
                                0x00,
                                0x00, 0x00, 0x00);
                        fg.writeBomString("");
                        fg.write(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00);
                        fg.writeBomString("");
                        fg.write(0x03);
                        fg.writeBomString("");
                        fg.write(0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x03);
                        fg.writeBomString("");
                        fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x03);
                        fg.writeBomString("");
                    } else {
                        fg.write(0x00, flaFormatVersion.getSpriteVersionD());
                    }
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.write(0x02, 0x00, 0x00, 0x00, 0x00);
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                        fg.write(debugRandom ? 'U' : 0x01, //NEW - different from sprite
                                0x00);
                    }
                    fg.write(0x00, 0x00);
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00);
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                    fg.write(0x00, 0x00, 0x00);
                }
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                    fg.write(0x00, 0x00, 0x00, 0x00);
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                    fg.write(0x01, 0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                    fg.writeBomString("");
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00, 0x00);
                    fg.writeBomString("");
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                    fg.write(0x00, 0x00, 0x00, 0x00, //scaleGrid toggle
                            0x00, 0x00, 0x00, 0x80, //scaleGridRight
                            0x00, 0x00, 0x00, 0x80, //scaleGridLeft
                            0x00, 0x00, 0x00, 0x80, //scaleGridBottom
                            0x00, 0x00, 0x00, 0x80); //scaleGridTop
                }
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                    fg.write(0x00,
                            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                }

                if (flaFormatVersion == FlaFormatVersion.CS4) {
                    fg.write(0x00, 0x00);
                }

                TimelineConverter pageGenerator = new TimelineConverter(flaFormatVersion, charset, "Page " + pageCount);
                pageGenerator.setDebugRandom(debugRandom);
                try (OutputStream pos = outputDir.getOutputStream(pageName)) {
                    pageGenerator.convert(domTimeline, document, pos);
                }
            }

            int currentTimeline = 1;

            if (document.hasAttribute("currentTimeline")) {
                currentTimeline = Integer.parseInt(document.getAttribute("currentTimeline"));
            }

            int nextSceneIdentifier = timelinesElements.size() + 1;
            if (document.hasAttribute("nextSceneIdentifier")) {
                nextSceneIdentifier = Integer.parseInt(document.getAttribute("nextSceneIdentifier"));
            }

            fg.write(0x00, 0x00);
            if (debugRandom) {
                fg.write('U');
            } else if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                fg.write(0x02);
            } else {
                fg.write(0x03);
            }

            fg.write(0x00);
            fg.write(0x01, 0x00,
                    1 + currentTimeline,
                    0x00);

            int symbolCount = writeSymbols(fg, document, docBuilder, sourceDir, outputDir, generatedItemIdOrder, definedClasses, objectsCount);

            fg.write(0x00, 0x00);
            fg.write(debugRandom ? 'U' : 0x01); //??

            fg.write(0x00);
            int mediaCount = writeMedia(fg, document, generatedItemIdOrder, definedClasses, objectsCount, outputDir, sourceDir);

            fg.write(0x00, 0x00);

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F2.ordinal()) {
                if (debugRandom) {
                    fg.write('X', 'X');
                } else {
                    fg.writeUI16(1 + mediaCount); //?
                }

                int rulerUnitType = getAttributeAsInt(document, "rulerUnitType", Arrays.asList(
                        "inches",
                        "decimal inches",
                        "points",
                        "centimeters",
                        "millimeters",
                        "pixels"
                ), "pixels");

                boolean gridVisible = false;
                if (document.hasAttribute("gridVisible")) {
                    gridVisible = !"false".equals(document.getAttribute("gridVisible"));
                }

                fg.write(
                        rulerUnitType,
                        0x00,
                        gridVisible ? 3 : 0,
                        0x00);
            }
            fg.write(0x00, 0x00, 0x00);

            fg.writeUI16(width * 20);
            fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            fg.writeUI16(height * 20);
            fg.write(0x00, 0x00, 0x00, 0x00);

            boolean gridSnapTo = false;
            if (document.hasAttribute("gridSnapTo")) {
                gridSnapTo = !"false".equals(document.getAttribute("gridSnapTo"));
            }

            boolean snapAlign = true;
            if (document.hasAttribute("snapAlign")) {
                snapAlign = !"false".equals(document.getAttribute("snapAlign"));
            }

            int gridSpacingX = 10;
            if (document.hasAttribute("gridSpacingX")) {
                gridSpacingX = Integer.parseInt(document.getAttribute("gridSpacingX"));
            }

            int gridSpacingY = 10;
            if (document.hasAttribute("gridSpacingY")) {
                gridSpacingY = Integer.parseInt(document.getAttribute("gridSpacingY"));
            }

            int gridSnapAccuracy = 1;
            if (document.hasAttribute("gridSnapAccuracy")) {
                switch (document.getAttribute("gridSnapAccuracy")) {
                    case "Must be close":
                        gridSnapAccuracy = 0;
                        break;
                    case "Can be distant":
                        gridSnapAccuracy = 2;
                        break;
                    case "Always snap":
                        gridSnapAccuracy = 3;
                        break;
                }
            }

            boolean pixelSnap = false;
            if (document.hasAttribute("pixelSnap")) {
                pixelSnap = "true".equals(document.getAttribute("pixelSnap"));
            }

            boolean objectsSnapTo = true;
            if (document.hasAttribute("objectsSnapTo")) {
                objectsSnapTo = !"false".equals(document.getAttribute("objectsSnapTo"));
            }

            long snapAlignBorderSpacing = 0;
            if (document.hasAttribute("snapAlignBorderSpacing")) {
                snapAlignBorderSpacing = Long.parseLong(document.getAttribute("snapAlignBorderSpacing"));
            }
            long snapAlignHorizontalSpacing = 0;
            if (document.hasAttribute("snapAlignHorizontalSpacing")) {
                snapAlignHorizontalSpacing = Long.parseLong(document.getAttribute("snapAlignHorizontalSpacing"));
            }
            long snapAlignVerticalSpacing = 0;
            if (document.hasAttribute("snapAlignVerticalSpacing")) {
                snapAlignVerticalSpacing = Long.parseLong(document.getAttribute("snapAlignVerticalSpacing"));
            }

            boolean snapAlignHorizontalCenter = false;
            if (document.hasAttribute("snapAlignHorizontalCenter")) {
                snapAlignHorizontalCenter = "true".equals(document.getAttribute("snapAlignHorizontalCenter"));
            }

            boolean snapAlignVerticalCenter = false;
            if (document.hasAttribute("snapAlignVerticalCenter")) {
                snapAlignVerticalCenter = "true".equals(document.getAttribute("snapAlignVerticalCenter"));
            }

            boolean rulerVisible = false;
            if (document.hasAttribute("rulerVisible")) {
                rulerVisible = "true".equals(document.getAttribute("rulerVisible"));
            }

            Color gridColor = new Color(0x94, 0x94, 0x94);
            if (document.hasAttribute("gridColor")) {
                gridColor = parseColor(document.getAttribute("gridColor"));
            }

            fg.writeUI16(gridSpacingX * 20);

            boolean pageTabsVisible = false; //"View/Page tabs"

            int previewMode = getAttributeAsInt(document, "previewMode", Arrays.asList(
                    "outlines",
                    "fast",
                    "anti alias",
                    "anti alias text",
                    "full"
            ), "anti alias text");

            fg.write(
                    previewMode,
                    rulerVisible ? 1 : 0,
                    pageTabsVisible ? 1 : 0
            );

            //NO EFFECT:
            //"View/Toolbars..."
            //"View/Symbol palette"
            //"View/100%, Show Page, Show All", zooming in general
            //"Tools/Snap"
            //"Tools/Assistant"
            //"Tools/Options"
            //"Frame view" dropdown menu
            //"Anchor onion marks" button
            //"Onion skin" button
            //"View/Timeline and Layers" = +1       viewOptionsAnimationControlVisible
            //"Play/Buttons active" = +2            viewOptionsButtonsActive
            //"View/Show work area" = +4            viewOptionsPasteBoardView
            //"Control/Enable live preview" = +8    viewOptionsLivePreview
            //"Play/Loop" = +0x10                   playOptionsPlayLoop
            //"Play/Play all pages" = +0x20         playOptionsPlayPages
            //"Play/Do frame actions" = +0x40       playOptionsPlayFrameActions
            //"Play/Play sounds" = +0x80            playOptionsPlaySounds
            int viewOptions = 0;

            if (!document.getAttribute("viewOptionsAnimationControlVisible").equals("false")) {
                viewOptions += 1;
            }
            if (document.getAttribute("viewOptionsButtonsActive").equals("true")) {
                viewOptions += 2;
            }
            if (!document.getAttribute("viewOptionsPasteBoardView").equals("false")) {
                viewOptions += 4;
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX.ordinal()
                    && !document.getAttribute("viewOptionsLivePreview").equals("false")) {
                viewOptions += 8;
            }

            int playOptions = 0;
            if (!document.getAttribute("playOptionsPlayLoop").equals("false")) {
                playOptions += 1;
            }
            if (!document.getAttribute("playOptionsPlayPages").equals("false")) {
                playOptions += 2;
            }
            if (!document.getAttribute("playOptionsPlayFrameActions").equals("false")) {
                playOptions += 4;
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F2.ordinal() && !document.getAttribute("playOptionsPlaySounds").equals("false")) {
                playOptions += 8;
            }

            fg.write((playOptions << 4) + viewOptions);

            fg.write(0x00, 0x68,
                    0x01, 0x00, 0x00, 0x68, 0x01, 0x00, 0x00, 0x68,
                    0x01, 0x00, 0x00, 0x68, 0x01, 0x00, 0x00, 0x01,
                    0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                    0x00, 0x00, 0x00, backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), 0xFF,
                    gridColor.getRed(), gridColor.getGreen(), gridColor.getBlue(),
                    0xFF, 0x00, (int) Math.round((frameRate - Math.floor(frameRate)) * 256), (int) Math.floor(frameRate), 0x00, 0x00,
                    0x00, 0x03, 0xb4, 0x00, 0x00, 0x00);

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F3.ordinal()) {
                if (flaFormatVersion == FlaFormatVersion.F3) {
                    writeMap(fg, new HashMap<>()); // 0x00, 0x00
                } else {
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                        writeMap(fg, getLegacyProperties());
                    }

                    if (publishSettings == null) {
                        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                            fg.writeUI32(1);
                        }
                        writeMap(fg, getProperties("Untitled-1", width, height, flaFormatVersion));
                    } else {
                        List<Element> flashProfiles = getAllSubElementsByName(publishSettings.getDocumentElement(), "flash_profile");

                        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                            fg.writeUI32(flashProfiles.size()); //?
                        }
                        for (Element flashProfile : flashProfiles) {
                            Map<String, String> properties = getProperties("Untitled-1", width, height, flaFormatVersion);
                            for (Element propertiesSet : getAllSubElements(flashProfile)) {
                                String namespace = propertiesSet.getTagName();
                                if ("PublishFlashProperties".equals(namespace)) {
                                    namespace = "Vector";
                                }
                                for (Element property : getAllSubElements(propertiesSet)) {
                                    String key = property.getTagName();
                                    String value = property.getTextContent();
                                    if ("Vector".equals(namespace)
                                            && ("LibraryPath".equals(key)
                                            || "LibraryVersions".equals(key))) {
                                        continue;
                                    }
                                    String nsKey = namespace + "::" + key;
                                    if (!properties.containsKey(nsKey)) {
                                        continue;
                                    }
                                    if (nsKey.equals("Vector::RSLPreloaderMethod")) {
                                        switch (value) {
                                            case "wrap":
                                                value = "0";
                                                break;
                                            case "event":
                                                value = "1";
                                                break;
                                        }
                                    }
                                    if (nsKey.equals("Vector::DefaultLibraryLinkage")) {
                                        switch (value) {
                                            case "rsl":
                                                value = "0";
                                                break;
                                            case "merge":
                                                value = "1";
                                                break;
                                        }
                                    }
                                    if (nsKey.equals("PublishRNWKProperties::flashBitRate")) {
                                        value = "1200";
                                    }
                                    properties.put(nsKey, value);
                                }
                            }

                            if (debugRandom) {
                                for (String key : properties.keySet()) {
                                    properties.put(key, "YYY");
                                }
                            }

                            writeMap(fg, properties);

                            if (flaFormatVersion.ordinal() <= FlaFormatVersion.MX.ordinal()) {
                                break; //only single properties
                            }
                        }
                    }
                }
                fg.write(0xFF, 0xFF, 0xFF, 0xFF);
                fg.writeBomString("");
                fg.writeBomString("");
                fg.write(0x01, 0x00, 0x00, 0x00);
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F4.ordinal()) {
                fg.write(0x00, 0x00);
                writeColorDef(document, fg, flaFormatVersion, definedClasses, objectsCount);

                Element foldersElement = getSubElementByName(document, "folders");
                List<Element> domFolderItems = new ArrayList<>();
                if (foldersElement != null) {
                    domFolderItems = getAllSubElementsByName(foldersElement, "DOMFolderItem");
                }

                fg.writeUI32(domFolderItems.size());

                for (Element domFolderItem : domFolderItems) {
                    fg.write(flaFormatVersion.getLibraryFolderVersionB(), 0x00, 0x00, 0x00);
                    String folderFullName = domFolderItem.getAttribute("name");
                    String folderName = folderFullName;
                    String parentFolder = "";
                    if (folderFullName.contains("/")) {
                        folderName = folderFullName.substring(folderFullName.lastIndexOf("/") + 1);
                        parentFolder = folderFullName.substring(0, folderFullName.lastIndexOf("/"));
                    }
                    fg.writeBomString(folderName);
                    fg.write(flaFormatVersion.getLibraryFolderVersionC(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00);

                    if (parentFolder.isEmpty()) {
                        fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                    } else {
                        boolean parentFound = false;
                        for (Element domFolderItem2 : domFolderItems) {
                            if (domFolderItem2.getAttribute("name").equals(parentFolder)) {
                                String parentItemID = domFolderItem2.getAttribute("itemID");
                                fg.writeItemID(parentItemID);
                                parentFound = true;
                                break;
                            }
                        }
                        if (!parentFound) {
                            fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                        }
                    }

                    fg.write(0x01, 0x00, 0x00, 0x00);
                    String itemID = generateItemID(generatedItemIdOrder);
                    if (domFolderItem.hasAttribute("itemID")) {
                        itemID = domFolderItem.getAttribute("itemID");
                    }
                    fg.writeItemID(itemID);

                    boolean isExpanded = false;
                    if (domFolderItem.hasAttribute("isExpanded")) {
                        isExpanded = "true".equals(domFolderItem.getAttribute("isExpanded"));
                    }

                    fg.write(isExpanded ? 1 : 0, 0x00,
                            0x00, 0x00);

                    fg.write(flaFormatVersion.getLibraryFolderVersion(), 0x00, 0x00, 0x00, 0x00);
                    fg.writeBomString("");
                    fg.writeBomString("");
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX.ordinal()) {
                        fg.writeBomString("");
                    }

                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                        fg.write(0x00);
                    }
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX.ordinal()) {
                        fg.write(flaFormatVersion.getLibraryFolderVersionD(), 0x00, 0x00, 0x00);
                        fg.writeBomString("");
                        fg.writeBomString("");
                        fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0xFF, 0xFF, 0xFF, 0xFF
                        );
                    }
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                        fg.write(0x00);
                    }
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                        fg.writeBomString("");
                        fg.write(0x00);
                    }
                }

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                    fg.write(0x00, 0x00);
                }
                fg.write(0x01);
                fg.write(0x00);
                fg.writeBomString("PublishQTProperties::QTSndSettings");
                writeQTAudioSettings(fg);
                fg.write(0x01, 0x00);
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F5.ordinal()) {

                boolean guidesLocked = false;
                if (document.hasAttribute("guidesLocked")) {
                    guidesLocked = "true".equals(document.getAttribute("guidesLocked"));
                }

                boolean guidesVisible = true;
                if (document.hasAttribute("guidesVisible")) {
                    guidesVisible = !"false".equals(document.getAttribute("guidesVisible"));
                }

                boolean guidesSnapTo = true;
                if (document.hasAttribute("guidesSnapTo")) {
                    guidesSnapTo = !"false".equals(document.getAttribute("guidesSnapTo"));
                }

                Color guidesColor = new Color(0x58, 0xFF, 0xFF);
                if (document.hasAttribute("guidesColor")) {
                    guidesColor = parseColor(document.getAttribute("guidesColor"));
                }

                fg.write(guidesColor.getRed(), guidesColor.getGreen(), guidesColor.getBlue());
                fg.write(0xFF);
                fg.write(
                        guidesVisible ? 1 : 0,
                        guidesLocked ? 1 : 0,
                        guidesSnapTo ? 1 : 0,
                        0x00, 0x00, 0x00, 0x00);

                int fontCount = writeFonts(fg, document, generatedItemIdOrder);

                String sharedLibraryURL = "";
                if (document.hasAttribute("sharedLibraryURL")) {
                    sharedLibraryURL = document.getAttribute("sharedLibraryURL");
                }

                fg.writeBomString(sharedLibraryURL);
                fg.write(gridSnapTo ? 1 : 0, objectsSnapTo ? 1 : 0, gridSnapAccuracy, 0x00, 0x00, 0x00);

                int guidesSnapAccuracy = 1;
                if (document.hasAttribute("guidesSnapAccuracy")) {
                    switch (document.getAttribute("guidesSnapAccuracy")) {
                        case "Must be close":
                            guidesSnapAccuracy = 0;
                            break;
                        case "Can be distant":
                            guidesSnapAccuracy = 2;
                            break;
                    }
                }

                fg.write(guidesSnapAccuracy,
                        0x00, 0x00, 0x00, 0x00, 0x00);
                fg.writeUI16(gridSpacingY * 20);
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX.ordinal()) {
                fg.writeBomString("");
                fg.writeBomString("");
                fg.write(0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00,
                        pixelSnap ? 1 : 0);
                fg.write(0x00, 0x00, 0x00);
                writeAccessibleData(fg, document, true);

                fg.write(0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00);
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                fg.write(snapAlign ? 1 : 0,
                        snapAlignVerticalCenter ? 1 : 0,
                        snapAlignHorizontalCenter ? 1 : 0,
                        0x00);
                fg.writeUI32(snapAlignHorizontalSpacing);
                fg.writeUI32(snapAlignVerticalSpacing);
                fg.writeUI32(snapAlignBorderSpacing);
                fg.write(0x01, 0x00, 0x01,
                        0x00, 0x00, 0x00,
                        debugRandom ? 'U' : 0, //??
                        0x00, 0x00, 0x00, 0x01,
                        0x00, 0x00, 0x00,
                        0xFF, 0xFF, 0xFF, 0xFF);
                fg.writeBomString("");
                fg.write(0x01, 0x00, 0x00, 0x00);
                fg.writeBomString("");
                fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);

                if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                    if (flaFormatVersion == FlaFormatVersion.CS4) {
                        String creatorInfo = "";
                        if (document.hasAttribute("creatorInfo")) {
                            creatorInfo = document.getAttribute("creatorInfo");
                        }
                        if (debugRandom) {
                            fg.writeBomString("YYY");
                        } else {
                            fg.writeBomString(getXmpp(creatorInfo));
                        }
                    } else {
                        fg.writeBomString("");
                    }
                    fg.writeBomString("");
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
                    fg.write(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
                    fg.write(0x00, 0x00, 0x00, 0x00, 0x00);
                    int majorVersion = 0;
                    if (document.hasAttribute("majorVersion")) {
                        String majorStr = document.getAttribute("majorVersion");
                        if (majorStr.matches("^[0-9]+$")) {
                            majorVersion = Integer.parseInt(majorStr);
                        }
                    }
                    fg.write(debugRandom ? 'X' : majorVersion);
                    fg.write(0x00, 0x00, 0x00);
                    int buildNumber = 0;
                    if (document.hasAttribute("buildNumber")) {
                        String buildStr = document.getAttribute("buildNumber");
                        if (buildStr.matches("^[0-9]+$")) {
                            buildNumber = Integer.parseInt(buildStr);
                        }
                    }

                    if (debugRandom) {
                        fg.write('X', 'X');
                    } else {
                        fg.writeUI16(buildNumber);
                    }
                    fg.write(0x00, 0x00);
                    if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {
                        fg.write('C');
                    } else {
                        fg.write('B');
                    }
                    fg.write(0x00);

                    String versionInfo = "";
                    if (document.hasAttribute("versionInfo")) {
                        versionInfo = document.getAttribute("versionInfo");
                    }

                    if (debugRandom) {
                        fg.write('N', 'N', 'N');
                    } else {
                        fg.write(versionInfo.getBytes());
                        String timecount = " timecount = " + getTimeCreatedAsString();
                        fg.write(timecount.getBytes());
                    }
                    fg.write(0x00);
                    if (flaFormatVersion == FlaFormatVersion.CS4) {
                        if (debugRandom) {
                            fg.write('U', 'U', 'U', 'U', 'U', 'U', 'U', 'U');
                        } else {
                            fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x4B, 0x40);
                        }
                        fg.write(0x7C, 0x15, 0x00, 0x00, 0xA0, 0x0F, 0x00, 0x00,
                                0x01);

                        fg.write(0x00, 0x00, 0x00);
                    }
                }
            }
        }
    }

    private String generateItemID(Reference<Long> generatedItemIdOrder) {
        if (debugRandom) {
            return "XXXXXXXX-XXXXXXXX";
        }
        String itemID = String.format("%1$08x-%2$08x", timeCreated, generatedItemIdOrder.getVal());
        generatedItemIdOrder.setVal(generatedItemIdOrder.getVal() + 1);
        return itemID;
    }

    protected void writeDomSoundItem(FlaWriter dw, Element domSoundItem, Map<String, Integer> definedClasses, Reference<Integer> objectsCount, int mediaCount, Reference<Long> generatedItemIdOrder, OutputStorageInterface outputDir, InputStorageInterface sourceDir) throws IOException {
        useClass("CMediaSound", 1, dw, definedClasses, objectsCount);
        dw.write(flaFormatVersion.getMediaSoundVersion());
        String mediaFile = "M " + mediaCount + " " + getTimeCreatedAsString();
        dw.writeString(debugRandom ? "YYY" : mediaFile);
        String fullName = "";
        if (domSoundItem.hasAttribute("name")) {
            fullName = domSoundItem.getAttribute("name");
        }
        String name = fullName;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        String parentFolderItemID = getParentFolderItemID(domSoundItem.getOwnerDocument().getDocumentElement(), fullName);

        dw.writeBomString(name);
        String importFilePath = "";
        if (debugRandom) {
            dw.write('X', 'X');
        } else {
            dw.writeUI16(mediaCount);
        }
        dw.writeBomString(debugRandom ? "YYY" : importFilePath);
        writeTimeCreated(dw);
        dw.write(flaFormatVersion.getMediaSoundVersionC(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00);
        if (parentFolderItemID == null) {
            dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        } else {
            dw.writeItemID(parentFolderItemID);
        }
        dw.write(0x01, 0x00, 0x00, 0x00);
        String itemID = generateItemID(generatedItemIdOrder);
        if (domSoundItem.hasAttribute("itemID")) {
            itemID = domSoundItem.getAttribute("itemID");
        }
        dw.writeItemID(itemID);

        String format = "";
        if (domSoundItem.hasAttribute("format")) {
            format = domSoundItem.getAttribute("format");
        }
        String[] fparts = format.split(" ", -1);
        boolean stereo = true;
        boolean is16bit = true;
        int samplingRate = 3;
        if (fparts.length == 3) {
            if ("Mono".equals(fparts[2])) {
                stereo = false;
            }
            if ("8bit".equals(fparts[1])) {
                is16bit = false;
            }
            switch (fparts[0]) {
                case "44kHz":
                    samplingRate = 3;
                    break;
                case "22kHz":
                    samplingRate = 2;
                    break;
                case "11kHz":
                    samplingRate = 1;
                    break;
                case "5kHz":
                    samplingRate = 0;
                    break;
            }
        }
        int formatAsNum = (samplingRate << 2) + (is16bit ? 2 : 0) + (stereo ? 1 : 0);

        long sampleCount = 0;
        if (domSoundItem.hasAttribute("sampleCount")) {
            sampleCount = Long.parseLong(domSoundItem.getAttribute("sampleCount"));
        }

        int exportFormat = -1;
        if (domSoundItem.hasAttribute("exportFormat")) {
            exportFormat = Integer.parseInt(domSoundItem.getAttribute("exportFormat"));
        }
        int exportBits = -1;
        if (domSoundItem.hasAttribute("exportBits")) {
            exportBits = Integer.parseInt(domSoundItem.getAttribute("exportBits"));
        }

        writeAsLinkage(dw, domSoundItem);
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            dw.write(0x00);
        }
        dw.write(0x01, 0x00, 0x00, 0x00, flaFormatVersion.getMediaSoundVersionB(),
                formatAsNum, 0x00);
        dw.writeUI32(sampleCount);

        dw.writeUI16(exportFormat);
        dw.writeUI16(exportBits);

        String deviceSoundHRef = "";
        if (domSoundItem.hasAttribute("deviceSoundHRef")) {
            deviceSoundHRef = domSoundItem.getAttribute("deviceSoundHRef");
        }

        dw.write(0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00);

        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            dw.writeBomString(deviceSoundHRef);
        }
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            dw.write(0x00, 0x00, 0x00, 0x00);
        }

        boolean hasBinData = false;
        if (domSoundItem.hasAttribute("soundDataHRef")) {
            String videoDataHRef = domSoundItem.getAttribute("soundDataHRef");
            //File videDataFile = sourceDir.toPath().resolve("bin").resolve(videoDataHRef).toFile();
            if (sourceDir.fileExists("bin/" + videoDataHRef)) {
                //copy the data file
                try (OutputStream fos = outputDir.getOutputStream(mediaFile); InputStream fis = sourceDir.readFile("bin/" + videoDataHRef)) {
                    byte[] buf = new byte[4096];
                    int cnt;
                    while ((cnt = fis.read(buf)) > 0) {
                        fos.write(buf, 0, cnt);
                    }
                }
                hasBinData = true;
            }
        }

        if (!hasBinData) {
            Logger.getLogger(FlaConverter.class.getName()).log(Level.WARNING, "Missing bin/*.dat file for {0}", name);
        }
    }

    protected void writeDomVideoItem(FlaWriter dw, Element domVideoItem, Map<String, Integer> definedClasses, Reference<Integer> objectsCount, int mediaCount, Reference<Long> generatedItemIdOrder, OutputStorageInterface outputDir, InputStorageInterface sourceDir) throws IOException {
        useClass("CMediaVideoStream", 1, dw, definedClasses, objectsCount);
        dw.write(flaFormatVersion.getMediaVideoVersion());
        String mediaFile = "M " + mediaCount + " " + getTimeCreatedAsString();
        dw.writeString(debugRandom ? "YYY" : mediaFile);
        String fullName = "";
        if (domVideoItem.hasAttribute("name")) {
            fullName = domVideoItem.getAttribute("name");
        }
        String name = fullName;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        String parentFolderItemID = getParentFolderItemID(domVideoItem.getOwnerDocument().getDocumentElement(), fullName);

        dw.writeBomString(name);

        String importFilePath = "";
        if (debugRandom) {
            dw.write('X', 'X');
        } else {
            dw.writeUI16(mediaCount);
        }
        dw.writeBomString(debugRandom ? "YYY" : importFilePath);
        writeTimeCreated(dw);
        dw.write(flaFormatVersion.getMediaVideoVersionC(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00);
        if (parentFolderItemID == null) {
            dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        } else {
            dw.writeItemID(parentFolderItemID);
        }
        dw.write(0x01, 0x00, 0x00, 0x00);
        String itemID = generateItemID(generatedItemIdOrder);
        if (domVideoItem.hasAttribute("itemID")) {
            itemID = domVideoItem.getAttribute("itemID");
        }
        dw.writeItemID(itemID);

        dw.write(0x00, 0x00, 0x00, 0x00, flaFormatVersion.getMediaVideoVersionB(), 0x00, 0x00, 0x00, 0x00);
        dw.writeBomString("");
        dw.writeBomString("");
        dw.writeBomString("");
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            dw.write(0x00);
        }
        dw.write(flaFormatVersion.getMediaVideoVersionD(), 0x00, 0x00, 0x00);
        dw.writeBomString("");
        dw.writeBomString("");
        dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        dw.write(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        dw.write(0xFF, 0xFF, 0xFF, 0xFF);
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            dw.write(0x00);
        }
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
            dw.writeBomString("");
            dw.write(0x00);
        }
        dw.write(0x01, 0x00, 0x00, 0x00);

        boolean hasBinData = false;
        if (domVideoItem.hasAttribute("videoDataHRef")) {
            String videoDataHRef = domVideoItem.getAttribute("videoDataHRef");
            //File videDataFile = sourceDir.toPath().resolve("bin").resolve(videoDataHRef).toFile();
            if (sourceDir.fileExists("bin/" + videoDataHRef)) {
                //copy the data file
                try (OutputStream fos = outputDir.getOutputStream(mediaFile); InputStream fis = sourceDir.readFile("bin/" + videoDataHRef)) {
                    DataInputStream dais = new DataInputStream(fis);
                    byte[] buf = new byte[0x31];
                    dais.readFully(buf);
                    fos.write(buf);
                    fis.read();
                    fos.write(0); //change 1 to 0
                    fis.read();
                    fos.write(0); //change 1 to 0

                    buf = new byte[4];
                    dais.readFully(buf);
                    fos.write(buf);
                    fis.read();
                    fos.write(0); //change 1 to 0

                    //copy rest of the file
                    buf = new byte[4096];
                    int cnt;
                    while ((cnt = fis.read(buf)) > 0) {
                        fos.write(buf, 0, cnt);
                    }
                }
                hasBinData = true;
            }
        }

        if (!hasBinData) {
            Logger.getLogger(FlaConverter.class.getName()).log(Level.WARNING, "Missing bin/*.dat file for {0}", name);
        }
    }

    protected void writeDomBitmapItem(FlaWriter dw, Element domBitmapItem, Map<String, Integer> definedClasses, Reference<Integer> objectsCount, int mediaCount, Reference<Long> generatedItemIdOrder, OutputStorageInterface outputDir, InputStorageInterface sourceDir) throws IOException {

        /*
        <media>
          <DOMBitmapItem name="bitmapfill.jpg" itemID="66d4468f-000004f3" sourceExternalFilepath=".\LIBRARY\bitmapfill.jpg" sourceLastImported="1667390241" externalFileSize="12213" quality="50" href="bitmapfill.jpg" bitmapDataHRef="M 2 1725187727.dat" frameRight="640" frameBottom="1280" isJPEG="true"/>
     </media>
         */
        useClass("CMediaBits", 1, dw, definedClasses, objectsCount);
        dw.write(flaFormatVersion.getMediaBitsVersion());
        String mediaFile = "M " + mediaCount + " " + getTimeCreatedAsString();
        dw.writeString(debugRandom ? "YYY" : mediaFile);

        String fullName = "";
        if (domBitmapItem.hasAttribute("name")) {
            fullName = domBitmapItem.getAttribute("name");
        }
        String name = fullName;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        String parentFolderItemID = getParentFolderItemID(domBitmapItem.getOwnerDocument().getDocumentElement(), fullName);

        String sourceExternalFilepath = domBitmapItem.getAttribute("sourceExternalFilepath");
        dw.writeBomString(name);
        String importFilePath = "";
        if (debugRandom) {
            dw.write('X', 'X');
        } else {
            dw.writeUI16(mediaCount);
        }
        dw.writeBomString(debugRandom ? "YYY" : importFilePath);
        writeTimeCreated(dw);
        dw.write(flaFormatVersion.getMediaBitsVersionC(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00);
        if (parentFolderItemID == null) {
            dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        } else {
            dw.writeItemID(parentFolderItemID);
        }
        dw.write(0x01, 0x00, 0x00, 0x00);

        String itemID = generateItemID(generatedItemIdOrder);
        if (domBitmapItem.hasAttribute("itemID")) {
            itemID = domBitmapItem.getAttribute("itemID");
        }
        dw.writeItemID(itemID);

        boolean allowSmoothing = false;

        if (domBitmapItem.hasAttribute("allowSmoothing")) {
            allowSmoothing = "true".equals(domBitmapItem.getAttribute("allowSmoothing"));
        }

        int quality = 80;
        if (domBitmapItem.hasAttribute("quality")) {
            quality = Integer.parseInt(domBitmapItem.getAttribute("quality"));
        }

        boolean useImportedJPEGData = true;

        if (domBitmapItem.hasAttribute("useImportedJPEGData")) {
            useImportedJPEGData = "true".equals(domBitmapItem.getAttribute("useImportedJPEGData"));
        }

        boolean useDeblocking = false;
        if (domBitmapItem.hasAttribute("useDeblocking")) {
            useDeblocking = "true".equals(domBitmapItem.getAttribute("useDeblocking"));
        }

        long externalFileSize = 0;
        if (domBitmapItem.hasAttribute("externalFileSize")) {
            externalFileSize = Long.parseLong(domBitmapItem.getAttribute("externalFileSize"));
        }

        boolean isJPEG = false;
        if (domBitmapItem.hasAttribute("isJPEG")) {
            isJPEG = "true".equals(domBitmapItem.getAttribute("isJPEG"));
        }

        if (sourceExternalFilepath != null && sourceExternalFilepath.toLowerCase().endsWith(".jpg")) {
            isJPEG = true;
        }

        boolean compressionTypeLossless = false;
        if (domBitmapItem.hasAttribute("compressionType")) { //"photo" (default) or "lossless"
            compressionTypeLossless = "lossless".equals(domBitmapItem.getAttribute("compressionType"));
        }

        boolean hasBinData = false;
        if (domBitmapItem.hasAttribute("bitmapDataHRef")) {
            String bitmapDataHRef = domBitmapItem.getAttribute("bitmapDataHRef");
            if (sourceDir.fileExists("bin/" + bitmapDataHRef)) {
                //copy the data file
                try (OutputStream fos = outputDir.getOutputStream(mediaFile); InputStream fis = sourceDir.readFile("bin/" + bitmapDataHRef);) {
                    byte[] buf = new byte[4096];
                    int cnt;
                    while ((cnt = fis.read(buf)) > 0) {
                        fos.write(buf, 0, cnt);
                    }
                }
                hasBinData = true;
            }
        }

        if (!hasBinData) {
            Logger.getLogger(FlaConverter.class.getName()).log(Level.WARNING, "Missing bin/*.dat file for {0}", name);
        }

        writeAsLinkage(dw, domBitmapItem);
        if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
            dw.write(0x00);
        }
        dw.write(0x01, 0x00, 0x00, 0x00);
        dw.write(flaFormatVersion.getMediaBitsVersionB());
        if (compressionTypeLossless) {
            dw.write(0x01);
        } else {
            if (useImportedJPEGData) {
                dw.write(0x00);
            } else {
                dw.write(0x02);
            }
        }
        dw.write(quality, allowSmoothing ? 1 : 0);
        if (isJPEG) {
            dw.writeUI32(externalFileSize);
        } else {
            dw.writeUI32(0);
        }
        if (flaFormatVersion == FlaFormatVersion.CS4) {
            dw.write(useDeblocking ? 1 : 0);
        }
    }

    protected int writeMedia(FlaWriter dw, Element document, Reference<Long> generatedItemIdOrder, Map<String, Integer> definedClasses, Reference<Integer> objectsCount, OutputStorageInterface outputDir, InputStorageInterface sourceDir) throws IOException {
        List<Element> media = getMedia(document);

        /*int imageCount = 0;
        for (Element mediaItem : media) {
            if ("DOMBitmapItem".equals(mediaItem.getTagName())) {
                imageCount++;
            }
        }*/
        int mediaCount = 0;

        for (Element mediaItem : media) {
            mediaCount++;
            switch (mediaItem.getTagName()) {
                case "DOMBitmapItem":
                    writeDomBitmapItem(dw, mediaItem, definedClasses, objectsCount, mediaCount, generatedItemIdOrder, outputDir, sourceDir);
                    break;
                case "DOMSoundItem":
                    writeDomSoundItem(dw, mediaItem, definedClasses, objectsCount, mediaCount, generatedItemIdOrder, outputDir, sourceDir);
                    break;
                case "DOMVideoItem":
                    writeDomVideoItem(dw, mediaItem, definedClasses, objectsCount, mediaCount, generatedItemIdOrder, outputDir, sourceDir);
                    break;
            }

        }

        return mediaCount;
    }

    protected int getNextId(Element document) {
        Element fontsElement = getSubElementByName(document, "fonts");
        if (fontsElement == null) {
            return 1;
        }

        List<Element> domFontItems = getAllSubElementsByName(fontsElement, "DOMFontItem");
        int id = 0;
        for (Element domFontItem : domFontItems) {
            if (domFontItem.hasAttribute("id")) {
                int nid = Integer.parseInt(domFontItem.getAttribute("id"));
                if (nid > id) {
                    id = nid;
                }
            }
        }
        return id + 1;
    }

    protected int writeFonts(FlaWriter dw, Element document, Reference<Long> generatedItemIdOrder) throws IOException {
        Element fontsElement = getSubElementByName(document, "fonts");
        List<Element> domFontItems = new ArrayList<>();
        if (fontsElement != null) {
            domFontItems = getAllSubElementsByName(fontsElement, "DOMFontItem");
        }
        dw.writeUI32(domFontItems.size());

        if (flaFormatVersion.ordinal() <= FlaFormatVersion.CS3.ordinal()) {
            Collections.reverse(domFontItems);
        }

        int fontCount = 0;
        for (Element domFontItem : domFontItems) {
            fontCount++;
            String fullName = "";
            if (domFontItem.hasAttribute("name")) {
                fullName = domFontItem.getAttribute("name");
            }
            String name = fullName;
            if (name.contains("/")) {
                name = name.substring(name.lastIndexOf("/") + 1);
            }
            String parentFolderItemID = getParentFolderItemID(domFontItem.getOwnerDocument().getDocumentElement(), fullName);

            String fontPsName = domFontItem.getAttribute("font"); //assuming has font
            int id = Integer.parseInt(domFontItem.getAttribute("id"));

            String itemID = generateItemID(generatedItemIdOrder);
            if (domFontItem.hasAttribute("itemID")) {
                itemID = domFontItem.getAttribute("itemID");
            }
            String fontFamily = fontPsName;
            boolean bold = false;
            boolean italic = false;
            if (psNameToFontName.containsKey(fontPsName)) {
                Font font = psNameToFontName.get(fontPsName);
                fontFamily = font.getFamily(Locale.US);

                String fontNameLowercase = font.getFontName(Locale.US).toLowerCase();
                bold = fontNameLowercase.contains("bold");
                italic = fontNameLowercase.contains("italic") || fontNameLowercase.contains("oblique");
            } else {
                if (debugRandom) {
                    fontFamily = "YYY";
                }
            }

            dw.write(flaFormatVersion.getFontVersion());
            dw.writeBomString(name);
            dw.writeUI16(id);
            writeTimeCreated(dw);
            dw.write(flaFormatVersion.getFontVersionB());
            dw.write(0x00, 0x00);
            if (flaFormatVersion == FlaFormatVersion.CS4) {
                dw.writeBomString(fontFamily);
            } else {
                dw.writeString(fontFamily);
            }

            if (flaFormatVersion == FlaFormatVersion.CS4) {
                dw.writeBomString(fontPsName);
                if (debugRandom) {
                    dw.write('U', 'U', 'U', 'U');
                } else {
                    dw.write(0x00, 0x00, 0x00, 0x40);
                }
            }
            //following part might be copied from textfield
            if (debugRandom) {
                dw.write('U', 'U', 'U', 'U');
            } else {
                dw.write(0x00, 0x00, 0x00, 0x00);
            }
            dw.write(debugRandom ? 'U' : 0x12); //something magic, see TimelineConverter for details
            dw.write(0x00);
            dw.write(debugRandom ? 'X' : (bold ? 1 : 0));
            dw.write(debugRandom ? 'X' : (italic ? 1 : 0));
            dw.write(0x00,
                    debugRandom ? 'U' : 0x00, //maybe unused = not used in any text???
                    0x00,
                    debugRandom ? 'U' : 0x00,
                    debugRandom ? 'U' : 0x00,
                    debugRandom ? 'U' : 0x00,
                    debugRandom ? 'U' : 0x00,
                    0x00,
                    debugRandom ? 'U' : 0x00,
                    debugRandom ? 'U' : 0x00,
                    debugRandom ? 'U' : 0x00,
                    debugRandom ? 'U' : 0x00,
                    debugRandom ? 'U' : 0x00,
                    0x00);
            //end of copied part           

            if (flaFormatVersion == FlaFormatVersion.CS4) {
                dw.writeBomString("");
            } else {
                dw.writeString("");
            }
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX.ordinal()) {
                dw.write(0x00);
                dw.write(0x00);

                dw.write(debugRandom ? 'U' : 0x00);
                if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                    dw.write(0x00);
                }
                if (flaFormatVersion == FlaFormatVersion.CS4) {
                    dw.writeBomString("");
                } else {
                    dw.writeString("");
                }
            }

            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                dw.write(0x02,
                        debugRandom ? 'U' : 0x01,
                        0x00, 0x00,
                        debugRandom ? 'U' : 0x00,
                        debugRandom ? 'U' : 0x00,
                        0x00, 0x00,
                        debugRandom ? 'U' : 0x00,
                        debugRandom ? 'U' : 0x00);
                if (flaFormatVersion == FlaFormatVersion.CS4) {
                    dw.writeBomString("");
                } else {
                    dw.writeString("");
                }
            }

            dw.write(flaFormatVersion.getFontVersionC(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00);
            if (parentFolderItemID == null) {
                dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            } else {
                dw.writeItemID(parentFolderItemID);
            }

            dw.write(0x01, 0x00, 0x00, 0x00);

            dw.writeItemID(itemID);
            writeAsLinkage(dw, domFontItem);
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.MX2004.ordinal()) {
                dw.write(0x00);
            }

            if (flaFormatVersion == FlaFormatVersion.CS4) {
                dw.write(0x00, 0x00, 0x00, 0x00);
            }
        }
        return fontCount;
    }

    protected void writeQTAudioSettings(FlaWriter dw) throws IOException {
        dw.write(0xFF, 0xFF, 0x01, 0x00);
        String CQTAudioSettings = "CQTAudioSettings";
        dw.write(CQTAudioSettings.length(),
                0x00);
        dw.write(CQTAudioSettings.getBytes());
        dw.write(0x00, 0x00, 0x00, 0x00);
    }

    protected void writeColorDef(Element document, FlaWriter dw, FlaFormatVersion flaFormatVersion, Map<String, Integer> definedClasses, Reference<Integer> objectsCount) throws IOException {

        List<SolidSwatchItem> solidSwatches = new ArrayList<>();
        List<ExtendedSwatchItem> extendedSwatches = new ArrayList<>();

        Element swatchListsElement = getSubElementByName(document, "swatchLists");
        if (swatchListsElement != null) {
            List<Element> swatchLists = getAllSubElementsByName(swatchListsElement, "swatchList");
            for (Element swatchListElement : swatchLists) {
                Element swatchesElement = getSubElementByName(swatchListElement, "swatches");
                if (swatchesElement != null) {
                    List<Element> solidSwatchItems = getAllSubElementsByName(swatchesElement, "SolidSwatchItem");
                    for (Element solidSwatchItem : solidSwatchItems) {
                        Color color = parseColorWithAlpha(solidSwatchItem);
                        String hueStr = solidSwatchItem.getAttribute("hue");
                        if (hueStr.equals("")) {
                            hueStr = "0";
                        }
                        String saturationStr = solidSwatchItem.getAttribute("saturation");
                        if (saturationStr.equals("")) {
                            saturationStr = "0";
                        }
                        String brightnessStr = solidSwatchItem.getAttribute("brightness");
                        if (brightnessStr.equals("")) {
                            brightnessStr = "0";
                        }
                        int hue = Integer.parseInt(hueStr);
                        int saturation = Integer.parseInt(saturationStr);
                        int brightness = Integer.parseInt(brightnessStr);

                        solidSwatches.add(new SolidSwatchItem(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), hue, saturation, brightness));
                    }
                }
            }
        }

        Element extendedSwatchListsElement = getSubElementByName(document, "extendedSwatchLists");
        if (extendedSwatchListsElement != null) {
            List<Element> swatchLists = getAllSubElementsByName(extendedSwatchListsElement, "swatchList");
            for (Element swatchListElement : swatchLists) {
                Element swatchesElement = getSubElementByName(swatchListElement, "swatches");
                if (swatchesElement != null) {
                    List<Element> swatches = getAllSubElements(swatchesElement);
                    for (Element swatch : swatches) {
                        List<GradientEntry> gradientEntries = new ArrayList<>();

                        switch (swatch.getNodeName()) {
                            case "LinearGradientSwatchItem":
                            case "RadialGradientSwatchItem":
                                List<Element> gradientEntryElements = getAllSubElementsByName(swatch, "GradientEntry");
                                for (Element gradientEntry : gradientEntryElements) {
                                    Color gradColor = parseColorWithAlpha(gradientEntry);
                                    float ratio = 0;
                                    if (gradientEntry.hasAttribute("ratio")) {
                                        ratio = Float.parseFloat(gradientEntry.getAttribute("ratio"));
                                    }
                                    gradientEntries.add(new GradientEntry(gradColor, ratio));
                                }
                                break;
                        }

                        boolean linearRGB = swatch.getAttribute("interpolationMethod").equals("linearRGB");

                        int spreadMethod = FlaWriter.FLOW_EXTEND;
                        switch (swatch.getAttribute("spreadMethod")) {
                            case "reflect":
                                spreadMethod = FlaWriter.FLOW_REFLECT;
                                break;
                            case "repeat":
                                spreadMethod = FlaWriter.FLOW_REPEAT;
                                break;
                        }

                        switch (swatch.getNodeName()) {
                            case "LinearGradientSwatchItem":
                                extendedSwatches.add(new LinearGradientSwatchItem(gradientEntries, spreadMethod, linearRGB));
                                break;
                            case "RadialGradientSwatchItem":
                                extendedSwatches.add(new RadialGradientSwatchItem(gradientEntries, spreadMethod, linearRGB));
                                break;
                        }
                    }
                }
            }
        }

        if (solidSwatches.isEmpty() && extendedSwatches.isEmpty()) {
            solidSwatches.addAll(defaultSolidSwatches);
            extendedSwatches.addAll(defaultExtendedSwatches);
        }

        dw.writeUI16(solidSwatches.size());
        for (int s = 0; s < solidSwatches.size(); s++) {
            useClass("CColorDef", 0x00, dw, definedClasses, objectsCount);
            dw.write(flaFormatVersion.getColorDefVersion());
            SolidSwatchItem sw = solidSwatches.get(s);
            dw.write(sw.red, sw.green, sw.blue, sw.alpha, 0x00, 0x00, sw.hue, 0x00, sw.saturation, 0x00, sw.brightness, 0x00);
        }

        if (flaFormatVersion == FlaFormatVersion.CS4) {
            dw.write(0x01);
        }
        dw.write(0x00);
        dw.writeUI16(extendedSwatches.size());

        for (int x = 0; x < extendedSwatches.size(); x++) {
            ExtendedSwatchItem ex = extendedSwatches.get(x);
            useClass("CColorDef", 0x00, dw, definedClasses, objectsCount);
            dw.write(flaFormatVersion.getColorDefVersion());
            /*if (x == 5) {
                dw.write(0xFF, 0xFF, 0xFF);
            } else if (x == 6) {
                dw.write(0xFF, 0x00, 0x00);
            } else {*/

            if (debugRandom) {
                dw.write('U', 'U', 'U');
            } else {
                dw.write(0x00, 0x00, 0x00);
            }
            //}
            dw.write(0xFF, ex.getType(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    ex.entries.size());
            if (flaFormatVersion.ordinal() >= FlaFormatVersion.F8.ordinal()) {
                dw.write(0x00,//focalRatio
                        0x00, 0x00, 0x00,
                        ex.spreadMethod + (ex.interpolationMethodLinearRGB ? 1 : 0), 0x00, 0x00, 0x00);
            }
            for (GradientEntry en : ex.entries) {
                int r = (int) Math.round(en.ratio * 255);
                dw.write(r, en.color.getRed(), en.color.getGreen(), en.color.getBlue(), en.color.getAlpha());
            }
            dw.write(0x00, 0x00);
            /*if (x == 5) { //WTF are these?
                dw.write(0x00, 0x00, 0xF0, 0x00);
            } else if (x == 6) {
                dw.write(0xEF, 0x00, 0x78, 0x00);
            } else {*/
            if (debugRandom) {
                dw.write('U', 'U', 'U', 'U');
            } else {
                dw.write(0x00, 0x00, 0x00, 0x00);
            }
            //}
        }

        if (flaFormatVersion == FlaFormatVersion.CS4) {
            dw.write(0x01);
        }

        dw.write(0x00, 0x03, 0x00, 0x00, 0x00);
        //if (flaFormatVersion.ordinal() >= FlaFormatVersion.CS3.ordinal()) {

        if (debugRandom) {
            dw.write('U', 0x00, 0x00, 0x00,
                    'U', 'U', 0x00, 0x00,
                    'U', 'U', 0x00, 0x00,
                    'U', 'U', 0x00, 0x00,
                    'U', 'U', 0x00, 0x00,
                    0x01,
                    0x00, 0x00, 0x00, 'U');
        } else {
            dw.write(0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x01,
                    0x00, 0x00, 0x00, 0x01);
        }
        //In F8:
        /*
            dw.write(0x01, 0x00, 0x00, 0x00,
                    0x97, 0x01, 0x00, 0x00, //wtf are these?
                    0x11, 0x03, 0x00, 0x00,
                    0xA6, 0x02, 0x00, 0x00,
                    0xE1, 0x03, 0x00, 0x00,
                    0x01,
                    0x00, 0x00, 0x00, 0x00);
        }*/
        dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00);
    }

    protected void writeMap(FlaWriter dw, Map<String, String> map) throws IOException {
        if (debugRandom) {
            //dw.write('M', 'M', 'M');
            //return;
        }
        dw.writeUI16(map.size());
        for (String key : map.keySet()) {
            String val = map.get(key);
            dw.writeBomString(key);
            dw.writeBomString(val);
        }
    }

    private Map<String, String> getLegacyProperties() {
        Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put("legacyLineSpacing", "0");
        return propertiesMap;
    }

    private Map<String, String> getProperties(String basePublishName, int width, int height, FlaFormatVersion flaFormatVersion) {
        switch (flaFormatVersion) {
            case CS4:
                return getPropertiesCs4(basePublishName, width, height);
            case CS3:
                return getPropertiesCs3(basePublishName, width, height);
            case F8:
                return getPropertiesF8(basePublishName, width, height);
            case MX2004:
                return getPropertiesMx2004(basePublishName, width, height);
            case MX:
                return getPropertiesMx(basePublishName, width, height);
            case F5:
                return getPropertiesF5(basePublishName, width, height);
        }
        return null;
    }

    private Map<String, String> getPropertiesF5(String basePublishName, int width, int height) {
        Map<String, String> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("PublishGifProperties::PaletteName", "");
        propertiesMap.put("PublishRNWKProperties::speed256K", "0");
        propertiesMap.put("Vector::AS3 Package Paths", ".");
        propertiesMap.put("PublishHtmlProperties::StartPaused", "0");
        propertiesMap.put("PublishFormatProperties::htmlFileName", basePublishName + ".html");
        propertiesMap.put("PublishQTProperties::LayerOption", "");
        propertiesMap.put("PublishQTProperties::AlphaOption", "");
        propertiesMap.put("PublishQTProperties::MatchMovieDim", "1");
        propertiesMap.put("Vector::RSLPreloaderMethod", "0");
        propertiesMap.put("Vector::UseNetwork", "0");
        propertiesMap.put("Vector::Debugging Permitted", "0");
        propertiesMap.put("PublishProfileProperties::name", "Default");
        propertiesMap.put("PublishHtmlProperties::Loop", "1");
        propertiesMap.put("PublishFormatProperties::jpeg", "0");
        propertiesMap.put("PublishQTProperties::Width", "" + width);
        propertiesMap.put("PublishPNGProperties::OptimizeColors", "1");
        propertiesMap.put("PublishRNWKProperties::speedSingleISDN", "0");
        propertiesMap.put("PublishRNWKProperties::singleRateAudio", "0");
        propertiesMap.put("Vector::DocumentClass", "");
        propertiesMap.put("Vector::External Player", "");
        propertiesMap.put("PublishHtmlProperties::showTagWarnMsg", "1");
        propertiesMap.put("PublishHtmlProperties::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::Units", "0");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultAlternateFilename", "1");
        propertiesMap.put("PublishGifProperties::Smooth", "1");
        propertiesMap.put("PublishRNWKProperties::mediaCopyright", "(c) 2000");
        propertiesMap.put("PublishRNWKProperties::flashBitRate", "1200");
        propertiesMap.put("Vector::ScriptStuckDelay", "15");
        propertiesMap.put("Vector::Compress Movie", "0");
        propertiesMap.put("Vector::Package Paths", "");
        propertiesMap.put("PublishFormatProperties::flashFileName", basePublishName + ".swf");
        propertiesMap.put("PublishFormatProperties::gifDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMac", "0");
        propertiesMap.put("PublishGifProperties::DitherOption", "");
        propertiesMap.put("PublishRNWKProperties::exportSMIL", "1");
        propertiesMap.put("PublishRNWKProperties::speed384K", "0");
        propertiesMap.put("PublishRNWKProperties::exportAudio", "1");
        propertiesMap.put("Vector::AS3ExportFrame", "1");
        propertiesMap.put("Vector::Invisible Layer", "1");
        propertiesMap.put("PublishHtmlProperties::Quality", "4");
        propertiesMap.put("PublishHtmlProperties::VerticalAlignment", "1");
        propertiesMap.put("PublishFormatProperties::pngFileName", basePublishName + ".png");
        propertiesMap.put("PublishFormatProperties::html", "1");
        propertiesMap.put("PublishPNGProperties::FilterOption", "");
        propertiesMap.put("PublishRNWKProperties::mediaDescription", "");
        propertiesMap.put("Vector::Override Sounds", "0");
        propertiesMap.put("PublishHtmlProperties::DeviceFont", "0");
        propertiesMap.put("PublishQTProperties::Flatten", "1");
        propertiesMap.put("PublishPNGProperties::BitDepth", "24-bit with Alpha");
        propertiesMap.put("PublishPNGProperties::Smooth", "1");
        propertiesMap.put("PublishGifProperties::DitherSolids", "0");
        propertiesMap.put("PublishGifProperties::Interlace", "0");
        propertiesMap.put("PublishJpegProperties::DPI", "4718592");
        propertiesMap.put("Vector::Quality", "80");
        propertiesMap.put("Vector::Protect", "0");
        propertiesMap.put("PublishHtmlProperties::DisplayMenu", "1");
        propertiesMap.put("PublishHtmlProperties::HorizontalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::VersionDetectionIfAvailable", "0");
        propertiesMap.put("PublishFormatProperties::rnwkDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::jpegDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::gif", "0");
        propertiesMap.put("PublishGifProperties::Loop", "1");
        propertiesMap.put("PublishGifProperties::Width", "" + width);
        propertiesMap.put("PublishRNWKProperties::mediaKeywords", "");
        propertiesMap.put("PublishRNWKProperties::mediaTitle", "");
        propertiesMap.put("PublishRNWKProperties::speed28K", "1");
        propertiesMap.put("Vector::AS3Flags", "4102");
        propertiesMap.put("PublishFormatProperties::qtFileName", basePublishName + ".mov");
        propertiesMap.put("PublishPNGProperties::DitherOption", "");
        propertiesMap.put("PublishGifProperties::PaletteOption", "");
        propertiesMap.put("PublishGifProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishRNWKProperties::speedDualISDN", "0");
        propertiesMap.put("PublishRNWKProperties::realVideoRate", "100000");
        propertiesMap.put("PublishJpegProperties::Quality", "80");
        propertiesMap.put("Vector::IncludeXMP", "1");
        propertiesMap.put("PublishFormatProperties::flash", "1");
        propertiesMap.put("PublishPNGProperties::PaletteOption", "");
        propertiesMap.put("PublishPNGProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishJpegProperties::MatchMovieDim", "1");
        propertiesMap.put("Vector::Package Export Frame", "1");
        propertiesMap.put("PublishProfileProperties::version", "1");
        propertiesMap.put("PublishHtmlProperties::Align", "0");
        propertiesMap.put("PublishFormatProperties::projectorWinFileName", basePublishName + ".exe");
        propertiesMap.put("PublishFormatProperties::pngDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacDefaultName", "1");
        propertiesMap.put("PublishQTProperties::PlayEveryFrame", "0");
        propertiesMap.put("PublishPNGProperties::DitherSolids", "0");
        propertiesMap.put("PublishJpegProperties::Progressive", "0");
        propertiesMap.put("Vector::Export Swc", "0");
        propertiesMap.put("Vector::Debugging Password", "");
        propertiesMap.put("Vector::Omit Trace Actions", "0");
        propertiesMap.put("PublishHtmlProperties::Height", "" + height);
        propertiesMap.put("PublishHtmlProperties::Width", "" + width);
        propertiesMap.put("PublishFormatProperties::jpegFileName", basePublishName + ".jpg");
        propertiesMap.put("PublishFormatProperties::flashDefaultName", "1");
        propertiesMap.put("PublishPNGProperties::Interlace", "0");
        propertiesMap.put("PublishGifProperties::Height", "" + height);
        propertiesMap.put("PublishJpegProperties::Size", "0");
        propertiesMap.put("Vector::DefaultLibraryLinkage", "0");
        propertiesMap.put("Vector::UseAS3Namespace", "1");
        propertiesMap.put("Vector::AS3AutoDeclare", "4096");
        propertiesMap.put("Vector::AS3Coach", "4");
        propertiesMap.put("Vector::DeviceSound", "0");
        propertiesMap.put("PublishHtmlProperties::TemplateFileName", "");
        propertiesMap.put("PublishHtmlProperties::WindowMode", "0");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultContentFilename", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacFileName", basePublishName + ".app");
        propertiesMap.put("PublishFormatProperties::htmlDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::rnwk", "0");
        propertiesMap.put("PublishFormatProperties::png", "0");
        propertiesMap.put("PublishQTProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::RemoveGradients", "0");
        propertiesMap.put("PublishGifProperties::MaxColors", "255");
        propertiesMap.put("PublishGifProperties::TransparentOption", "");
        propertiesMap.put("PublishGifProperties::LoopCount", "");
        propertiesMap.put("PublishRNWKProperties::speed56K", "1");
        propertiesMap.put("Vector::Report", "0");
        propertiesMap.put("PublishHtmlProperties::OwnAlternateFilename", "");
        propertiesMap.put("PublishHtmlProperties::AlternateFilename", basePublishName + "_alternate.html");
        propertiesMap.put("PublishHtmlProperties::ContentFilename", basePublishName + "_content.html");
        propertiesMap.put("legacyLineSpacing", "0");
        propertiesMap.put("PublishGifProperties::OptimizeColors", "1");
        propertiesMap.put("PublishRNWKProperties::audioFormat", "0");
        propertiesMap.put("Vector::RSLPreloaderSWF", "$(AppConfig)/ActionScript 3.0/rsls/loader_animation.swf");
        propertiesMap.put("Vector::HardwareAcceleration", "0");
        propertiesMap.put("Vector::AS3Strict", "2");
        propertiesMap.put("Vector::Version", "5");
        propertiesMap.put("Vector::Event Format", "0");
        propertiesMap.put("Vector::Stream Compress", "7");
        propertiesMap.put("PublishFormatProperties::qt", "0");
        propertiesMap.put("PublishPNGProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::Width", "" + width);
        propertiesMap.put("PublishGifProperties::RemoveGradients", "0");
        propertiesMap.put("PublishRNWKProperties::speed512K", "0");
        propertiesMap.put("PublishJpegProperties::Height", "" + height);
        propertiesMap.put("Vector::EventUse8kSampleRate", "0");
        propertiesMap.put("Vector::StreamUse8kSampleRate", "0");
        propertiesMap.put("Vector::ActionScriptVersion", "2");
        propertiesMap.put("Vector::Event Compress", "7");
        propertiesMap.put("PublishHtmlProperties::Scale", "0");
        propertiesMap.put("PublishFormatProperties::projectorWinDefaultName", "1");
        propertiesMap.put("PublishQTProperties::Looping", "0");
        propertiesMap.put("PublishQTProperties::UseQTSoundCompression", "0");
        propertiesMap.put("PublishPNGProperties::PaletteName", "");
        propertiesMap.put("PublishPNGProperties::Transparent", "0");
        propertiesMap.put("PublishGifProperties::TransparentAlpha", "128");
        propertiesMap.put("PublishGifProperties::Animated", "0");
        propertiesMap.put("PublishRNWKProperties::mediaAuthor", "");
        propertiesMap.put("PublishRNWKProperties::speedCorporateLAN", "0");
        propertiesMap.put("PublishRNWKProperties::showBitrateDlog", "1");
        propertiesMap.put("PublishRNWKProperties::exportFlash", "1");
        propertiesMap.put("PublishJpegProperties::Width", "" + width);
        propertiesMap.put("Vector::Stream Format", "0");
        propertiesMap.put("Vector::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::VersionInfo", "10,1,52,0;9,0,124,0;8,0,24,0;7,0,14,0;6,0,79,0;5,0,58,0;4,0,32,0;3,0,8,0;2,0,1,12;1,0,0,1;");
        propertiesMap.put("PublishFormatProperties::gifFileName", basePublishName + ".gif");
        propertiesMap.put("PublishFormatProperties::qtDefaultName", "1");
        propertiesMap.put("PublishQTProperties::PausedAtStart", "0");
        propertiesMap.put("PublishQTProperties::ControllerOption", "0");
        propertiesMap.put("PublishPNGProperties::MaxColors", "255");
        propertiesMap.put("PublishHtmlProperties::UsingOwnAlternateFile", "0");
        propertiesMap.put("PublishFormatProperties::rnwkFileName", basePublishName + ".smil");
        propertiesMap.put("PublishFormatProperties::projectorWin", "0");
        propertiesMap.put("PublishFormatProperties::defaultNames", "1");

        return propertiesMap;
    }

    private Map<String, String> getPropertiesMx(String basePublishName, int width, int height) {
        Map<String, String> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("PublishFormatProperties::htmlFileName", basePublishName + ".html");
        propertiesMap.put("PublishHtmlProperties::StartPaused", "0");
        propertiesMap.put("Vector::AS3 Package Paths", ".");
        propertiesMap.put("PublishRNWKProperties::speed256K", "0");
        propertiesMap.put("PublishGifProperties::PaletteName", "");
        propertiesMap.put("PublishFormatProperties::jpeg", "0");
        propertiesMap.put("PublishHtmlProperties::Loop", "1");
        propertiesMap.put("PublishProfileProperties::name", "Default");
        propertiesMap.put("Vector::Debugging Permitted", "0");
        propertiesMap.put("Vector::UseNetwork", "0");
        propertiesMap.put("Vector::RSLPreloaderMethod", "0");
        propertiesMap.put("PublishQTProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishQTProperties::AlphaOption", "");
        propertiesMap.put("PublishQTProperties::LayerOption", "");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultAlternateFilename", "1");
        propertiesMap.put("PublishHtmlProperties::Units", "0");
        propertiesMap.put("PublishHtmlProperties::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::showTagWarnMsg", "1");
        propertiesMap.put("Vector::External Player", "");
        propertiesMap.put("Vector::DocumentClass", "");
        propertiesMap.put("PublishRNWKProperties::singleRateAudio", "0");
        propertiesMap.put("PublishRNWKProperties::speedSingleISDN", "0");
        propertiesMap.put("PublishPNGProperties::OptimizeColors", "1");
        propertiesMap.put("PublishQTProperties::Width", "" + width);
        propertiesMap.put("PublishFormatProperties::projectorMac", "0");
        propertiesMap.put("PublishFormatProperties::gifDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::flashFileName", basePublishName + ".swf");
        propertiesMap.put("Vector::Package Paths", "");
        propertiesMap.put("Vector::Compress Movie", "1");
        propertiesMap.put("Vector::ScriptStuckDelay", "15");
        propertiesMap.put("PublishRNWKProperties::flashBitRate", "1200");
        propertiesMap.put("PublishRNWKProperties::mediaCopyright", "(c) 2000");
        propertiesMap.put("PublishGifProperties::Smooth", "1");
        propertiesMap.put("PublishFormatProperties::html", "1");
        propertiesMap.put("PublishFormatProperties::pngFileName", basePublishName + ".png");
        propertiesMap.put("PublishHtmlProperties::VerticalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::Quality", "4");
        propertiesMap.put("Vector::Invisible Layer", "1");
        propertiesMap.put("Vector::AS3ExportFrame", "1");
        propertiesMap.put("PublishRNWKProperties::exportAudio", "1");
        propertiesMap.put("PublishRNWKProperties::speed384K", "0");
        propertiesMap.put("PublishRNWKProperties::exportSMIL", "1");
        propertiesMap.put("PublishGifProperties::DitherOption", "");
        propertiesMap.put("PublishHtmlProperties::DeviceFont", "0");
        propertiesMap.put("Vector::Override Sounds", "0");
        propertiesMap.put("PublishRNWKProperties::mediaDescription", "");
        propertiesMap.put("PublishPNGProperties::FilterOption", "");
        propertiesMap.put("PublishFormatProperties::gif", "0");
        propertiesMap.put("PublishFormatProperties::jpegDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::rnwkDefaultName", "1");
        propertiesMap.put("PublishHtmlProperties::VersionDetectionIfAvailable", "0");
        propertiesMap.put("PublishHtmlProperties::HorizontalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::DisplayMenu", "1");
        propertiesMap.put("Vector::Protect", "0");
        propertiesMap.put("Vector::Quality", "80");
        propertiesMap.put("PublishJpegProperties::DPI", "4718592");
        propertiesMap.put("PublishGifProperties::Interlace", "0");
        propertiesMap.put("PublishGifProperties::DitherSolids", "0");
        propertiesMap.put("PublishPNGProperties::Smooth", "1");
        propertiesMap.put("PublishPNGProperties::BitDepth", "24-bit with Alpha");
        propertiesMap.put("PublishQTProperties::Flatten", "1");
        propertiesMap.put("PublishFormatProperties::qtFileName", basePublishName + ".mov");
        propertiesMap.put("Vector::AS3Flags", "4102");
        propertiesMap.put("PublishRNWKProperties::speed28K", "1");
        propertiesMap.put("PublishRNWKProperties::mediaTitle", "");
        propertiesMap.put("PublishRNWKProperties::mediaKeywords", "");
        propertiesMap.put("PublishGifProperties::Width", "" + width);
        propertiesMap.put("PublishGifProperties::Loop", "1");
        propertiesMap.put("PublishFormatProperties::flash", "1");
        propertiesMap.put("Vector::IncludeXMP", "1");
        propertiesMap.put("PublishJpegProperties::Quality", "80");
        propertiesMap.put("PublishRNWKProperties::realVideoRate", "100000");
        propertiesMap.put("PublishRNWKProperties::speedDualISDN", "0");
        propertiesMap.put("PublishGifProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishGifProperties::PaletteOption", "");
        propertiesMap.put("PublishPNGProperties::DitherOption", "");
        propertiesMap.put("PublishFormatProperties::projectorMacDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::pngDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorWinFileName", basePublishName + ".exe");
        propertiesMap.put("PublishHtmlProperties::Align", "0");
        propertiesMap.put("PublishProfileProperties::version", "1");
        propertiesMap.put("Vector::Package Export Frame", "1");
        propertiesMap.put("PublishJpegProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishPNGProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishPNGProperties::PaletteOption", "");
        propertiesMap.put("PublishFormatProperties::flashDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::jpegFileName", basePublishName + ".jpg");
        propertiesMap.put("PublishHtmlProperties::Width", "" + width);
        propertiesMap.put("PublishHtmlProperties::Height", "" + height);
        propertiesMap.put("Vector::Omit Trace Actions", "0");
        propertiesMap.put("Vector::Debugging Password", "");
        propertiesMap.put("Vector::Export Swc", "0");
        propertiesMap.put("PublishJpegProperties::Progressive", "0");
        propertiesMap.put("PublishPNGProperties::DitherSolids", "0");
        propertiesMap.put("PublishQTProperties::PlayEveryFrame", "0");
        propertiesMap.put("PublishFormatProperties::png", "0");
        propertiesMap.put("PublishFormatProperties::rnwk", "0");
        propertiesMap.put("PublishFormatProperties::htmlDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacFileName", basePublishName + ".app");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultContentFilename", "1");
        propertiesMap.put("PublishHtmlProperties::WindowMode", "0");
        propertiesMap.put("PublishHtmlProperties::TemplateFileName", "");
        propertiesMap.put("Vector::DeviceSound", "0");
        propertiesMap.put("Vector::AS3Coach", "4");
        propertiesMap.put("Vector::AS3AutoDeclare", "4096");
        propertiesMap.put("Vector::UseAS3Namespace", "1");
        propertiesMap.put("Vector::DefaultLibraryLinkage", "0");
        propertiesMap.put("PublishJpegProperties::Size", "0");
        propertiesMap.put("PublishGifProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::Interlace", "0");
        propertiesMap.put("PublishHtmlProperties::ContentFilename", basePublishName + "_content.html");
        propertiesMap.put("PublishHtmlProperties::AlternateFilename", basePublishName + "_alternate.html");
        propertiesMap.put("PublishHtmlProperties::OwnAlternateFilename", "");
        propertiesMap.put("Vector::Report", "0");
        propertiesMap.put("PublishRNWKProperties::speed56K", "1");
        propertiesMap.put("PublishGifProperties::LoopCount", "");
        propertiesMap.put("PublishGifProperties::TransparentOption", "");
        propertiesMap.put("PublishGifProperties::MaxColors", "255");
        propertiesMap.put("PublishPNGProperties::RemoveGradients", "0");
        propertiesMap.put("PublishQTProperties::Height", "" + height);
        propertiesMap.put("PublishFormatProperties::qt", "0");
        propertiesMap.put("Vector::Stream Compress", "7");
        propertiesMap.put("Vector::Event Format", "0");
        propertiesMap.put("Vector::Version", "6");
        propertiesMap.put("Vector::AS3Strict", "2");
        propertiesMap.put("Vector::HardwareAcceleration", "0");
        propertiesMap.put("Vector::RSLPreloaderSWF", "$(AppConfig)/ActionScript 3.0/rsls/loader_animation.swf");
        propertiesMap.put("PublishRNWKProperties::audioFormat", "0");
        propertiesMap.put("PublishGifProperties::OptimizeColors", "1");
        propertiesMap.put("legacyLineSpacing", "0");
        propertiesMap.put("PublishFormatProperties::projectorWinDefaultName", "1");
        propertiesMap.put("PublishHtmlProperties::Scale", "0");
        propertiesMap.put("Vector::Event Compress", "7");
        propertiesMap.put("Vector::ActionScriptVersion", "2");
        propertiesMap.put("Vector::StreamUse8kSampleRate", "0");
        propertiesMap.put("Vector::EventUse8kSampleRate", "0");
        propertiesMap.put("PublishJpegProperties::Height", "" + height);
        propertiesMap.put("PublishRNWKProperties::speed512K", "0");
        propertiesMap.put("PublishGifProperties::RemoveGradients", "0");
        propertiesMap.put("PublishPNGProperties::Width", "" + width);
        propertiesMap.put("PublishPNGProperties::Height", "" + height);
        propertiesMap.put("PublishFormatProperties::qtDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::gifFileName", basePublishName + ".gif");
        propertiesMap.put("PublishHtmlProperties::VersionInfo", "10,1,52,0;9,0,124,0;8,0,24,0;7,0,14,0;6,0,79,0;5,0,58,0;4,0,32,0;3,0,8,0;2,0,1,12;1,0,0,1;");
        propertiesMap.put("Vector::DeblockingFilter", "0");
        propertiesMap.put("Vector::Stream Format", "0");
        propertiesMap.put("PublishJpegProperties::Width", "" + width);
        propertiesMap.put("PublishRNWKProperties::exportFlash", "1");
        propertiesMap.put("PublishRNWKProperties::showBitrateDlog", "1");
        propertiesMap.put("PublishRNWKProperties::speedCorporateLAN", "0");
        propertiesMap.put("PublishRNWKProperties::mediaAuthor", "");
        propertiesMap.put("PublishGifProperties::Animated", "0");
        propertiesMap.put("PublishGifProperties::TransparentAlpha", "128");
        propertiesMap.put("PublishPNGProperties::Transparent", "0");
        propertiesMap.put("PublishPNGProperties::PaletteName", "");
        propertiesMap.put("PublishQTProperties::UseQTSoundCompression", "0");
        propertiesMap.put("PublishQTProperties::Looping", "0");
        propertiesMap.put("PublishFormatProperties::defaultNames", "1");
        propertiesMap.put("PublishFormatProperties::projectorWin", "0");
        propertiesMap.put("PublishFormatProperties::rnwkFileName", basePublishName + ".smil");
        propertiesMap.put("PublishHtmlProperties::UsingOwnAlternateFile", "0");
        propertiesMap.put("PublishPNGProperties::MaxColors", "255");
        propertiesMap.put("PublishQTProperties::ControllerOption", "0");
        propertiesMap.put("PublishQTProperties::PausedAtStart", "0");

        return propertiesMap;
    }

    private Map<String, String> getPropertiesMx2004(String basePublishName, int width, int height) {
        Map<String, String> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("PublishFormatProperties::htmlFileName", basePublishName + ".html");
        propertiesMap.put("PublishHtmlProperties::StartPaused", "0");
        propertiesMap.put("Vector::AS3 Package Paths", ".");
        propertiesMap.put("PublishRNWKProperties::speed256K", "0");
        propertiesMap.put("PublishGifProperties::PaletteName", "");
        propertiesMap.put("PublishFormatProperties::jpeg", "0");
        propertiesMap.put("PublishHtmlProperties::Loop", "1");
        propertiesMap.put("PublishProfileProperties::name", "Default");
        propertiesMap.put("Vector::Debugging Permitted", "0");
        propertiesMap.put("Vector::UseNetwork", "0");
        propertiesMap.put("Vector::RSLPreloaderMethod", "0");
        propertiesMap.put("PublishQTProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishQTProperties::AlphaOption", "");
        propertiesMap.put("PublishQTProperties::LayerOption", "");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultAlternateFilename", "1");
        propertiesMap.put("PublishHtmlProperties::Units", "0");
        propertiesMap.put("PublishHtmlProperties::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::showTagWarnMsg", "1");
        propertiesMap.put("Vector::External Player", "");
        propertiesMap.put("Vector::DocumentClass", "");
        propertiesMap.put("PublishRNWKProperties::singleRateAudio", "0");
        propertiesMap.put("PublishRNWKProperties::speedSingleISDN", "0");
        propertiesMap.put("PublishPNGProperties::OptimizeColors", "1");
        propertiesMap.put("PublishQTProperties::Width", "" + width);
        propertiesMap.put("PublishFormatProperties::projectorMac", "0");
        propertiesMap.put("PublishFormatProperties::gifDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::flashFileName", basePublishName + ".swf");
        propertiesMap.put("Vector::Package Paths", "");
        propertiesMap.put("Vector::Compress Movie", "1");
        propertiesMap.put("Vector::ScriptStuckDelay", "15");
        propertiesMap.put("PublishRNWKProperties::flashBitRate", "1200");
        propertiesMap.put("PublishRNWKProperties::mediaCopyright", "(c) 2000");
        propertiesMap.put("PublishGifProperties::Smooth", "1");
        propertiesMap.put("PublishFormatProperties::html", "1");
        propertiesMap.put("PublishFormatProperties::pngFileName", basePublishName + ".png");
        propertiesMap.put("PublishHtmlProperties::VerticalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::Quality", "4");
        propertiesMap.put("Vector::Invisible Layer", "1");
        propertiesMap.put("Vector::AS3ExportFrame", "1");
        propertiesMap.put("PublishRNWKProperties::exportAudio", "1");
        propertiesMap.put("PublishRNWKProperties::speed384K", "0");
        propertiesMap.put("PublishRNWKProperties::exportSMIL", "1");
        propertiesMap.put("PublishGifProperties::DitherOption", "");
        propertiesMap.put("PublishHtmlProperties::DeviceFont", "0");
        propertiesMap.put("Vector::Override Sounds", "0");
        propertiesMap.put("PublishRNWKProperties::mediaDescription", "");
        propertiesMap.put("PublishPNGProperties::FilterOption", "");
        propertiesMap.put("PublishFormatProperties::gif", "0");
        propertiesMap.put("PublishFormatProperties::jpegDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::rnwkDefaultName", "1");
        propertiesMap.put("PublishHtmlProperties::VersionDetectionIfAvailable", "0");
        propertiesMap.put("PublishHtmlProperties::HorizontalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::DisplayMenu", "1");
        propertiesMap.put("Vector::Protect", "0");
        propertiesMap.put("Vector::Quality", "80");
        propertiesMap.put("PublishJpegProperties::DPI", "4718592");
        propertiesMap.put("PublishGifProperties::Interlace", "0");
        propertiesMap.put("PublishGifProperties::DitherSolids", "0");
        propertiesMap.put("PublishPNGProperties::Smooth", "1");
        propertiesMap.put("PublishPNGProperties::BitDepth", "24-bit with Alpha");
        propertiesMap.put("PublishQTProperties::Flatten", "1");
        propertiesMap.put("PublishFormatProperties::qtFileName", basePublishName + ".mov");
        propertiesMap.put("Vector::AS3Flags", "4102");
        propertiesMap.put("PublishRNWKProperties::speed28K", "1");
        propertiesMap.put("PublishRNWKProperties::mediaTitle", "");
        propertiesMap.put("PublishRNWKProperties::mediaKeywords", "");
        propertiesMap.put("PublishGifProperties::Width", "" + width);
        propertiesMap.put("PublishGifProperties::Loop", "1");
        propertiesMap.put("PublishFormatProperties::flash", "1");
        propertiesMap.put("Vector::IncludeXMP", "1");
        propertiesMap.put("PublishJpegProperties::Quality", "80");
        propertiesMap.put("PublishRNWKProperties::realVideoRate", "100000");
        propertiesMap.put("PublishRNWKProperties::speedDualISDN", "0");
        propertiesMap.put("PublishGifProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishGifProperties::PaletteOption", "");
        propertiesMap.put("PublishPNGProperties::DitherOption", "");
        propertiesMap.put("PublishFormatProperties::projectorMacDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::pngDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorWinFileName", basePublishName + ".exe");
        propertiesMap.put("PublishHtmlProperties::Align", "0");
        propertiesMap.put("PublishProfileProperties::version", "1");
        propertiesMap.put("Vector::Package Export Frame", "1");
        propertiesMap.put("PublishJpegProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishPNGProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishPNGProperties::PaletteOption", "");
        propertiesMap.put("PublishFormatProperties::flashDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::jpegFileName", basePublishName + ".jpg");
        propertiesMap.put("PublishHtmlProperties::Width", "" + width);
        propertiesMap.put("PublishHtmlProperties::Height", "" + height);
        propertiesMap.put("Vector::Omit Trace Actions", "0");
        propertiesMap.put("Vector::Debugging Password", "");
        propertiesMap.put("Vector::Export Swc", "0");
        propertiesMap.put("PublishJpegProperties::Progressive", "0");
        propertiesMap.put("PublishPNGProperties::DitherSolids", "0");
        propertiesMap.put("PublishQTProperties::PlayEveryFrame", "0");
        propertiesMap.put("PublishFormatProperties::png", "0");
        propertiesMap.put("PublishFormatProperties::rnwk", "0");
        propertiesMap.put("PublishFormatProperties::htmlDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacFileName", basePublishName + ".app");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultContentFilename", "1");
        propertiesMap.put("PublishHtmlProperties::WindowMode", "0");
        propertiesMap.put("PublishHtmlProperties::TemplateFileName", "");
        propertiesMap.put("Vector::DeviceSound", "0");
        propertiesMap.put("Vector::AS3Coach", "4");
        propertiesMap.put("Vector::AS3AutoDeclare", "4096");
        propertiesMap.put("Vector::UseAS3Namespace", "1");
        propertiesMap.put("Vector::DefaultLibraryLinkage", "0");
        propertiesMap.put("PublishJpegProperties::Size", "0");
        propertiesMap.put("PublishGifProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::Interlace", "0");
        propertiesMap.put("PublishHtmlProperties::ContentFilename", basePublishName + "_content.html");
        propertiesMap.put("PublishHtmlProperties::AlternateFilename", basePublishName + "_alternate.html");
        propertiesMap.put("PublishHtmlProperties::OwnAlternateFilename", "");
        propertiesMap.put("Vector::Report", "0");
        propertiesMap.put("PublishRNWKProperties::speed56K", "1");
        propertiesMap.put("PublishGifProperties::LoopCount", "");
        propertiesMap.put("PublishGifProperties::TransparentOption", "");
        propertiesMap.put("PublishGifProperties::MaxColors", "255");
        propertiesMap.put("PublishPNGProperties::RemoveGradients", "0");
        propertiesMap.put("PublishQTProperties::Height", "" + height);
        propertiesMap.put("PublishFormatProperties::qt", "0");
        propertiesMap.put("Vector::Stream Compress", "7");
        propertiesMap.put("Vector::Event Format", "0");
        propertiesMap.put("Vector::Version", "7");
        propertiesMap.put("Vector::AS3Strict", "2");
        propertiesMap.put("Vector::HardwareAcceleration", "0");
        propertiesMap.put("Vector::RSLPreloaderSWF", "$(AppConfig)/ActionScript 3.0/rsls/loader_animation.swf");
        propertiesMap.put("PublishRNWKProperties::audioFormat", "0");
        propertiesMap.put("PublishGifProperties::OptimizeColors", "1");
        propertiesMap.put("PublishFormatProperties::projectorWinDefaultName", "1");
        propertiesMap.put("PublishHtmlProperties::Scale", "0");
        propertiesMap.put("Vector::Event Compress", "7");
        propertiesMap.put("Vector::ActionScriptVersion", "2");
        propertiesMap.put("Vector::StreamUse8kSampleRate", "0");
        propertiesMap.put("Vector::EventUse8kSampleRate", "0");
        propertiesMap.put("PublishJpegProperties::Height", "" + height);
        propertiesMap.put("PublishRNWKProperties::speed512K", "0");
        propertiesMap.put("PublishGifProperties::RemoveGradients", "0");
        propertiesMap.put("PublishPNGProperties::Width", "" + width);
        propertiesMap.put("PublishPNGProperties::Height", "" + height);
        propertiesMap.put("PublishFormatProperties::qtDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::gifFileName", basePublishName + ".gif");
        propertiesMap.put("PublishHtmlProperties::VersionInfo", "10,1,52,0;9,0,124,0;8,0,24,0;7,0,14,0;6,0,79,0;5,0,58,0;4,0,32,0;3,0,8,0;2,0,1,12;1,0,0,1;");
        propertiesMap.put("Vector::DeblockingFilter", "0");
        propertiesMap.put("Vector::Stream Format", "0");
        propertiesMap.put("PublishJpegProperties::Width", "" + width);
        propertiesMap.put("PublishRNWKProperties::exportFlash", "1");
        propertiesMap.put("PublishRNWKProperties::showBitrateDlog", "1");
        propertiesMap.put("PublishRNWKProperties::speedCorporateLAN", "0");
        propertiesMap.put("PublishRNWKProperties::mediaAuthor", "");
        propertiesMap.put("PublishGifProperties::Animated", "0");
        propertiesMap.put("PublishGifProperties::TransparentAlpha", "128");
        propertiesMap.put("PublishPNGProperties::Transparent", "0");
        propertiesMap.put("PublishPNGProperties::PaletteName", "");
        propertiesMap.put("PublishQTProperties::UseQTSoundCompression", "0");
        propertiesMap.put("PublishQTProperties::Looping", "0");
        propertiesMap.put("PublishFormatProperties::defaultNames", "1");
        propertiesMap.put("PublishFormatProperties::projectorWin", "0");
        propertiesMap.put("PublishFormatProperties::rnwkFileName", basePublishName + ".smil");
        propertiesMap.put("PublishHtmlProperties::UsingOwnAlternateFile", "0");
        propertiesMap.put("PublishPNGProperties::MaxColors", "255");
        propertiesMap.put("PublishQTProperties::ControllerOption", "0");
        propertiesMap.put("PublishQTProperties::PausedAtStart", "0");
        return propertiesMap;
    }

    private Map<String, String> getPropertiesF8(String basePublishName, int width, int height) {
        Map<String, String> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("PublishGifProperties::PaletteName", "");
        propertiesMap.put("PublishRNWKProperties::speed256K", "0");
        propertiesMap.put("Vector::AS3 Package Paths", ".");
        propertiesMap.put("PublishHtmlProperties::StartPaused", "0");
        propertiesMap.put("PublishFormatProperties::htmlFileName", basePublishName + ".html");
        propertiesMap.put("PublishQTProperties::LayerOption", "");
        propertiesMap.put("PublishQTProperties::AlphaOption", "");
        propertiesMap.put("PublishQTProperties::MatchMovieDim", "1");
        propertiesMap.put("Vector::RSLPreloaderMethod", "0");
        propertiesMap.put("Vector::UseNetwork", "0");
        propertiesMap.put("Vector::Debugging Permitted", "0");
        propertiesMap.put("PublishProfileProperties::name", "Default");
        propertiesMap.put("PublishHtmlProperties::Loop", "1");
        propertiesMap.put("PublishFormatProperties::jpeg", "0");
        propertiesMap.put("PublishQTProperties::Width", "" + width);
        propertiesMap.put("PublishPNGProperties::OptimizeColors", "1");
        propertiesMap.put("PublishRNWKProperties::speedSingleISDN", "0");
        propertiesMap.put("PublishRNWKProperties::singleRateAudio", "0");
        propertiesMap.put("Vector::DocumentClass", "");
        propertiesMap.put("Vector::External Player", "");
        propertiesMap.put("PublishHtmlProperties::showTagWarnMsg", "1");
        propertiesMap.put("PublishHtmlProperties::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::Units", "0");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultAlternateFilename", "1");
        propertiesMap.put("PublishGifProperties::Smooth", "1");
        propertiesMap.put("PublishRNWKProperties::mediaCopyright", "(c) 2000");
        propertiesMap.put("PublishRNWKProperties::flashBitRate", "1200");
        propertiesMap.put("Vector::ScriptStuckDelay", "15");
        propertiesMap.put("Vector::Compress Movie", "1");
        propertiesMap.put("Vector::Package Paths", "");
        propertiesMap.put("PublishFormatProperties::flashFileName", basePublishName + ".swf");
        propertiesMap.put("PublishFormatProperties::gifDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMac", "0");
        propertiesMap.put("PublishGifProperties::DitherOption", "");
        propertiesMap.put("PublishRNWKProperties::exportSMIL", "1");
        propertiesMap.put("PublishRNWKProperties::speed384K", "0");
        propertiesMap.put("PublishRNWKProperties::exportAudio", "1");
        propertiesMap.put("Vector::AS3ExportFrame", "1");
        propertiesMap.put("Vector::Invisible Layer", "1");
        propertiesMap.put("PublishHtmlProperties::Quality", "4");
        propertiesMap.put("PublishHtmlProperties::VerticalAlignment", "1");
        propertiesMap.put("PublishFormatProperties::pngFileName", basePublishName + ".png");
        propertiesMap.put("PublishFormatProperties::html", "1");
        propertiesMap.put("PublishPNGProperties::FilterOption", "");
        propertiesMap.put("PublishRNWKProperties::mediaDescription", "");
        propertiesMap.put("Vector::Override Sounds", "0");
        propertiesMap.put("PublishHtmlProperties::DeviceFont", "0");
        propertiesMap.put("PublishQTProperties::Flatten", "1");
        propertiesMap.put("PublishPNGProperties::BitDepth", "24-bit with Alpha");
        propertiesMap.put("PublishPNGProperties::Smooth", "1");
        propertiesMap.put("PublishGifProperties::DitherSolids", "0");
        propertiesMap.put("PublishGifProperties::Interlace", "0");
        propertiesMap.put("PublishJpegProperties::DPI", "4718592");
        propertiesMap.put("Vector::Quality", "80");
        propertiesMap.put("Vector::Protect", "0");
        propertiesMap.put("PublishHtmlProperties::DisplayMenu", "1");
        propertiesMap.put("PublishHtmlProperties::HorizontalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::VersionDetectionIfAvailable", "0");
        propertiesMap.put("PublishFormatProperties::rnwkDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::jpegDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::gif", "0");
        propertiesMap.put("PublishGifProperties::Loop", "1");
        propertiesMap.put("PublishGifProperties::Width", "" + width);
        propertiesMap.put("PublishRNWKProperties::mediaKeywords", "");
        propertiesMap.put("PublishRNWKProperties::mediaTitle", "");
        propertiesMap.put("PublishRNWKProperties::speed28K", "1");
        propertiesMap.put("Vector::AS3Flags", "4102");
        propertiesMap.put("PublishFormatProperties::qtFileName", basePublishName + ".mov");
        propertiesMap.put("PublishPNGProperties::DitherOption", "");
        propertiesMap.put("PublishGifProperties::PaletteOption", "");
        propertiesMap.put("PublishGifProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishRNWKProperties::speedDualISDN", "0");
        propertiesMap.put("PublishRNWKProperties::realVideoRate", "100000");
        propertiesMap.put("PublishJpegProperties::Quality", "80");
        propertiesMap.put("Vector::IncludeXMP", "1");
        propertiesMap.put("PublishFormatProperties::flash", "1");
        propertiesMap.put("PublishPNGProperties::PaletteOption", "");
        propertiesMap.put("PublishPNGProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishJpegProperties::MatchMovieDim", "1");
        propertiesMap.put("Vector::Package Export Frame", "1");
        propertiesMap.put("PublishProfileProperties::version", "1");
        propertiesMap.put("PublishHtmlProperties::Align", "0");
        propertiesMap.put("PublishFormatProperties::projectorWinFileName", basePublishName + ".exe");
        propertiesMap.put("PublishFormatProperties::pngDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacDefaultName", "1");
        propertiesMap.put("PublishQTProperties::PlayEveryFrame", "0");
        propertiesMap.put("PublishPNGProperties::DitherSolids", "0");
        propertiesMap.put("PublishJpegProperties::Progressive", "0");
        propertiesMap.put("Vector::Export Swc", "0");
        propertiesMap.put("Vector::Debugging Password", "");
        propertiesMap.put("Vector::Omit Trace Actions", "0");
        propertiesMap.put("PublishHtmlProperties::Height", "" + height);
        propertiesMap.put("PublishHtmlProperties::Width", "" + width);
        propertiesMap.put("PublishFormatProperties::jpegFileName", basePublishName + ".jpg");
        propertiesMap.put("PublishFormatProperties::flashDefaultName", "1");
        propertiesMap.put("PublishPNGProperties::Interlace", "0");
        propertiesMap.put("PublishGifProperties::Height", "" + height);
        propertiesMap.put("PublishJpegProperties::Size", "0");
        propertiesMap.put("Vector::DefaultLibraryLinkage", "0");
        propertiesMap.put("Vector::UseAS3Namespace", "1");
        propertiesMap.put("Vector::AS3AutoDeclare", "4096");
        propertiesMap.put("Vector::AS3Coach", "4");
        propertiesMap.put("Vector::DeviceSound", "0");
        propertiesMap.put("PublishHtmlProperties::TemplateFileName", "");
        propertiesMap.put("PublishHtmlProperties::WindowMode", "0");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultContentFilename", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacFileName", basePublishName + ".app");
        propertiesMap.put("PublishFormatProperties::htmlDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::rnwk", "0");
        propertiesMap.put("PublishFormatProperties::png", "0");
        propertiesMap.put("PublishQTProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::RemoveGradients", "0");
        propertiesMap.put("PublishGifProperties::MaxColors", "255");
        propertiesMap.put("PublishGifProperties::TransparentOption", "");
        propertiesMap.put("PublishGifProperties::LoopCount", "");
        propertiesMap.put("PublishRNWKProperties::speed56K", "1");
        propertiesMap.put("Vector::Report", "0");
        propertiesMap.put("PublishHtmlProperties::OwnAlternateFilename", "");
        propertiesMap.put("PublishHtmlProperties::AlternateFilename", basePublishName + "_alternate.html");
        propertiesMap.put("PublishHtmlProperties::ContentFilename", basePublishName + "_content.html");
        propertiesMap.put("PublishGifProperties::OptimizeColors", "1");
        propertiesMap.put("PublishRNWKProperties::audioFormat", "0");
        propertiesMap.put("Vector::RSLPreloaderSWF", "$(AppConfig)/ActionScript 3.0/rsls/loader_animation.swf");
        propertiesMap.put("Vector::HardwareAcceleration", "0");
        propertiesMap.put("Vector::AS3Strict", "2");
        propertiesMap.put("Vector::Version", "8");
        propertiesMap.put("Vector::Event Format", "0");
        propertiesMap.put("Vector::Stream Compress", "7");
        propertiesMap.put("PublishFormatProperties::qt", "0");
        propertiesMap.put("PublishPNGProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::Width", "" + width);
        propertiesMap.put("PublishGifProperties::RemoveGradients", "0");
        propertiesMap.put("PublishRNWKProperties::speed512K", "0");
        propertiesMap.put("PublishJpegProperties::Height", "" + height);
        propertiesMap.put("Vector::EventUse8kSampleRate", "0");
        propertiesMap.put("Vector::StreamUse8kSampleRate", "0");
        propertiesMap.put("Vector::ActionScriptVersion", "2");
        propertiesMap.put("Vector::Event Compress", "7");
        propertiesMap.put("PublishHtmlProperties::Scale", "0");
        propertiesMap.put("PublishFormatProperties::projectorWinDefaultName", "1");
        propertiesMap.put("PublishQTProperties::Looping", "0");
        propertiesMap.put("PublishQTProperties::UseQTSoundCompression", "0");
        propertiesMap.put("PublishPNGProperties::PaletteName", "");
        propertiesMap.put("PublishPNGProperties::Transparent", "0");
        propertiesMap.put("PublishGifProperties::TransparentAlpha", "128");
        propertiesMap.put("PublishGifProperties::Animated", "0");
        propertiesMap.put("PublishRNWKProperties::mediaAuthor", "");
        propertiesMap.put("PublishRNWKProperties::speedCorporateLAN", "0");
        propertiesMap.put("PublishRNWKProperties::showBitrateDlog", "1");
        propertiesMap.put("PublishRNWKProperties::exportFlash", "1");
        propertiesMap.put("PublishJpegProperties::Width", "" + width);
        propertiesMap.put("Vector::Stream Format", "0");
        propertiesMap.put("Vector::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::VersionInfo", "10,1,52,0;9,0,124,0;8,0,24,0;7,0,14,0;6,0,79,0;5,0,58,0;4,0,32,0;3,0,8,0;2,0,1,12;1,0,0,1;");
        propertiesMap.put("PublishFormatProperties::gifFileName", basePublishName + ".gif");
        propertiesMap.put("PublishFormatProperties::qtDefaultName", "1");
        propertiesMap.put("PublishQTProperties::PausedAtStart", "0");
        propertiesMap.put("PublishQTProperties::ControllerOption", "0");
        propertiesMap.put("PublishPNGProperties::MaxColors", "255");
        propertiesMap.put("PublishHtmlProperties::UsingOwnAlternateFile", "0");
        propertiesMap.put("PublishFormatProperties::rnwkFileName", basePublishName + ".smil");
        propertiesMap.put("PublishFormatProperties::projectorWin", "0");
        propertiesMap.put("PublishFormatProperties::defaultNames", "1");
        return propertiesMap;
    }

    private Map<String, String> getPropertiesCs3(String basePublishName, int width, int height) {
        Map<String, String> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("PublishFormatProperties::htmlFileName", basePublishName + ".html");
        propertiesMap.put("PublishHtmlProperties::StartPaused", "0");
        propertiesMap.put("Vector::AS3 Package Paths", ".");
        propertiesMap.put("PublishRNWKProperties::speed256K", "0");
        propertiesMap.put("PublishGifProperties::PaletteName", "");
        propertiesMap.put("PublishFormatProperties::jpeg", "0");
        propertiesMap.put("PublishHtmlProperties::Loop", "1");
        propertiesMap.put("PublishProfileProperties::name", "Default");
        propertiesMap.put("Vector::Debugging Permitted", "0");
        propertiesMap.put("Vector::UseNetwork", "0");
        propertiesMap.put("Vector::RSLPreloaderMethod", "0");
        propertiesMap.put("PublishQTProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishQTProperties::AlphaOption", "");
        propertiesMap.put("PublishQTProperties::LayerOption", "");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultAlternateFilename", "1");
        propertiesMap.put("PublishHtmlProperties::Units", "0");
        propertiesMap.put("PublishHtmlProperties::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::showTagWarnMsg", "1");
        propertiesMap.put("Vector::External Player", "");
        propertiesMap.put("Vector::DocumentClass", "");
        propertiesMap.put("PublishRNWKProperties::singleRateAudio", "0");
        propertiesMap.put("PublishRNWKProperties::speedSingleISDN", "0");
        propertiesMap.put("PublishPNGProperties::OptimizeColors", "1");
        propertiesMap.put("PublishQTProperties::Width", "" + width);
        propertiesMap.put("PublishFormatProperties::projectorMac", "0");
        propertiesMap.put("PublishFormatProperties::gifDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::flashFileName", basePublishName + ".swf");
        propertiesMap.put("Vector::Package Paths", "");
        propertiesMap.put("Vector::Compress Movie", "1");
        propertiesMap.put("Vector::ScriptStuckDelay", "15");
        propertiesMap.put("PublishRNWKProperties::flashBitRate", "1200");
        propertiesMap.put("PublishRNWKProperties::mediaCopyright", "(c) 2000");
        propertiesMap.put("PublishGifProperties::Smooth", "1");
        propertiesMap.put("PublishFormatProperties::html", "1");
        propertiesMap.put("PublishFormatProperties::pngFileName", basePublishName + ".png");
        propertiesMap.put("PublishHtmlProperties::VerticalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::Quality", "4");
        propertiesMap.put("Vector::Invisible Layer", "1");
        propertiesMap.put("Vector::AS3ExportFrame", "1");
        propertiesMap.put("PublishRNWKProperties::exportAudio", "1");
        propertiesMap.put("PublishRNWKProperties::speed384K", "0");
        propertiesMap.put("PublishRNWKProperties::exportSMIL", "1");
        propertiesMap.put("PublishGifProperties::DitherOption", "");
        propertiesMap.put("PublishHtmlProperties::DeviceFont", "0");
        propertiesMap.put("Vector::Override Sounds", "0");
        propertiesMap.put("PublishRNWKProperties::mediaDescription", "");
        propertiesMap.put("PublishPNGProperties::FilterOption", "");
        propertiesMap.put("PublishFormatProperties::gif", "0");
        propertiesMap.put("PublishFormatProperties::jpegDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::rnwkDefaultName", "1");
        propertiesMap.put("PublishHtmlProperties::VersionDetectionIfAvailable", "0");
        propertiesMap.put("PublishHtmlProperties::HorizontalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::DisplayMenu", "1");
        propertiesMap.put("Vector::Protect", "0");
        propertiesMap.put("Vector::Quality", "80");
        propertiesMap.put("PublishJpegProperties::DPI", "4718592");
        propertiesMap.put("PublishGifProperties::Interlace", "0");
        propertiesMap.put("PublishGifProperties::DitherSolids", "0");
        propertiesMap.put("PublishPNGProperties::Smooth", "1");
        propertiesMap.put("PublishPNGProperties::BitDepth", "24-bit with Alpha");
        propertiesMap.put("PublishQTProperties::Flatten", "1");
        propertiesMap.put("PublishFormatProperties::qtFileName", basePublishName + ".mov");
        propertiesMap.put("Vector::AS3Flags", "4102");
        propertiesMap.put("PublishRNWKProperties::speed28K", "1");
        propertiesMap.put("PublishRNWKProperties::mediaTitle", "");
        propertiesMap.put("PublishRNWKProperties::mediaKeywords", "");
        propertiesMap.put("PublishGifProperties::Width", "" + width);
        propertiesMap.put("PublishGifProperties::Loop", "1");
        propertiesMap.put("PublishFormatProperties::flash", "1");
        propertiesMap.put("Vector::IncludeXMP", "1");
        propertiesMap.put("PublishJpegProperties::Quality", "80");
        propertiesMap.put("PublishRNWKProperties::realVideoRate", "100000");
        propertiesMap.put("PublishRNWKProperties::speedDualISDN", "0");
        propertiesMap.put("PublishGifProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishGifProperties::PaletteOption", "");
        propertiesMap.put("PublishPNGProperties::DitherOption", "");
        propertiesMap.put("PublishFormatProperties::projectorMacDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::pngDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorWinFileName", basePublishName + ".exe");
        propertiesMap.put("PublishHtmlProperties::Align", "0");
        propertiesMap.put("PublishProfileProperties::version", "1");
        propertiesMap.put("Vector::Package Export Frame", "1");
        propertiesMap.put("PublishJpegProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishPNGProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishPNGProperties::PaletteOption", "");
        propertiesMap.put("PublishFormatProperties::flashDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::jpegFileName", basePublishName + ".jpg");
        propertiesMap.put("PublishHtmlProperties::Width", "" + width);
        propertiesMap.put("PublishHtmlProperties::Height", "" + height);
        propertiesMap.put("Vector::Omit Trace Actions", "0");
        propertiesMap.put("Vector::Debugging Password", "");
        propertiesMap.put("Vector::Export Swc", "0");
        propertiesMap.put("PublishJpegProperties::Progressive", "0");
        propertiesMap.put("PublishPNGProperties::DitherSolids", "0");
        propertiesMap.put("PublishQTProperties::PlayEveryFrame", "0");
        propertiesMap.put("PublishFormatProperties::png", "0");
        propertiesMap.put("PublishFormatProperties::rnwk", "0");
        propertiesMap.put("PublishFormatProperties::htmlDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacFileName", basePublishName + ".app");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultContentFilename", "1");
        propertiesMap.put("PublishHtmlProperties::WindowMode", "0");
        propertiesMap.put("PublishHtmlProperties::TemplateFileName", "");
        propertiesMap.put("Vector::DeviceSound", "0");
        propertiesMap.put("Vector::AS3Coach", "4");
        propertiesMap.put("Vector::AS3AutoDeclare", "4096");
        propertiesMap.put("Vector::UseAS3Namespace", "1");
        propertiesMap.put("Vector::DefaultLibraryLinkage", "0");
        propertiesMap.put("PublishJpegProperties::Size", "0");
        propertiesMap.put("PublishGifProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::Interlace", "0");
        propertiesMap.put("PublishHtmlProperties::ContentFilename", basePublishName + "_content.html");
        propertiesMap.put("PublishHtmlProperties::AlternateFilename", basePublishName + "_alternate.html");
        propertiesMap.put("PublishHtmlProperties::OwnAlternateFilename", "");
        propertiesMap.put("Vector::Report", "0");
        propertiesMap.put("PublishRNWKProperties::speed56K", "1");
        propertiesMap.put("PublishGifProperties::LoopCount", "");
        propertiesMap.put("PublishGifProperties::TransparentOption", "");
        propertiesMap.put("PublishGifProperties::MaxColors", "255");
        propertiesMap.put("PublishPNGProperties::RemoveGradients", "0");
        propertiesMap.put("PublishQTProperties::Height", "" + height);
        propertiesMap.put("PublishFormatProperties::qt", "0");
        propertiesMap.put("Vector::Stream Compress", "7");
        propertiesMap.put("Vector::Event Format", "0");
        propertiesMap.put("Vector::Version", "9");
        propertiesMap.put("Vector::AS3Strict", "2");
        propertiesMap.put("Vector::HardwareAcceleration", "0");
        propertiesMap.put("Vector::RSLPreloaderSWF", "$(AppConfig)/ActionScript 3.0/rsls/loader_animation.swf");
        propertiesMap.put("PublishRNWKProperties::audioFormat", "0");
        propertiesMap.put("PublishGifProperties::OptimizeColors", "1");
        propertiesMap.put("PublishFormatProperties::projectorWinDefaultName", "1");
        propertiesMap.put("PublishHtmlProperties::Scale", "0");
        propertiesMap.put("Vector::Event Compress", "7");
        propertiesMap.put("Vector::ActionScriptVersion", "3");
        propertiesMap.put("Vector::StreamUse8kSampleRate", "0");
        propertiesMap.put("Vector::EventUse8kSampleRate", "0");
        propertiesMap.put("PublishJpegProperties::Height", "400");
        propertiesMap.put("PublishRNWKProperties::speed512K", "0");
        propertiesMap.put("PublishGifProperties::RemoveGradients", "0");
        propertiesMap.put("PublishPNGProperties::Width", "" + width);
        propertiesMap.put("PublishPNGProperties::Height", "" + height);
        propertiesMap.put("PublishFormatProperties::qtDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::gifFileName", basePublishName + ".gif");
        propertiesMap.put("PublishHtmlProperties::VersionInfo", "10,1,52,0;9,0,124,0;8,0,24,0;7,0,14,0;6,0,79,0;5,0,58,0;4,0,32,0;3,0,8,0;2,0,1,12;1,0,0,1;");
        propertiesMap.put("Vector::DeblockingFilter", "0");
        propertiesMap.put("Vector::Stream Format", "0");
        propertiesMap.put("PublishJpegProperties::Width", "" + width);
        propertiesMap.put("PublishRNWKProperties::exportFlash", "1");
        propertiesMap.put("PublishRNWKProperties::showBitrateDlog", "1");
        propertiesMap.put("PublishRNWKProperties::speedCorporateLAN", "0");
        propertiesMap.put("PublishRNWKProperties::mediaAuthor", "");
        propertiesMap.put("PublishGifProperties::Animated", "0");
        propertiesMap.put("PublishGifProperties::TransparentAlpha", "128");
        propertiesMap.put("PublishPNGProperties::Transparent", "0");
        propertiesMap.put("PublishPNGProperties::PaletteName", "");
        propertiesMap.put("PublishQTProperties::UseQTSoundCompression", "0");
        propertiesMap.put("PublishQTProperties::Looping", "0");
        propertiesMap.put("PublishFormatProperties::defaultNames", "1");
        propertiesMap.put("PublishFormatProperties::projectorWin", "0");
        propertiesMap.put("PublishFormatProperties::rnwkFileName", basePublishName + ".smil");
        propertiesMap.put("PublishHtmlProperties::UsingOwnAlternateFile", "0");
        propertiesMap.put("PublishPNGProperties::MaxColors", "255");
        propertiesMap.put("PublishQTProperties::ControllerOption", "0");
        propertiesMap.put("PublishQTProperties::PausedAtStart", "0");
        return propertiesMap;
    }

    private Map<String, String> getPropertiesCs4(String basePublishName, int width, int height) {
        Map<String, String> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("PublishGifProperties::PaletteName", "");
        propertiesMap.put("PublishRNWKProperties::speed256K", "0");
        propertiesMap.put("Vector::AS3 Package Paths", ".");
        propertiesMap.put("PublishHtmlProperties::StartPaused", "0");
        propertiesMap.put("PublishFormatProperties::htmlFileName", basePublishName + ".html");
        propertiesMap.put("Vector::AS3 Library Paths", "$(AppConfig)/ActionScript 3.0/libs");
        propertiesMap.put("PublishQTProperties::LayerOption", "");
        propertiesMap.put("PublishQTProperties::AlphaOption", "");
        propertiesMap.put("PublishQTProperties::MatchMovieDim", "1");
        propertiesMap.put("Vector::RSLPreloaderMethod", "0");
        propertiesMap.put("Vector::UseNetwork", "0");
        propertiesMap.put("Vector::Debugging Permitted", "0");
        propertiesMap.put("PublishProfileProperties::name", "Default");
        propertiesMap.put("PublishHtmlProperties::Loop", "1");
        propertiesMap.put("PublishFormatProperties::jpeg", "0");
        propertiesMap.put("PublishQTProperties::Width", "" + width);
        propertiesMap.put("PublishPNGProperties::OptimizeColors", "1");
        propertiesMap.put("PublishRNWKProperties::speedSingleISDN", "0");
        propertiesMap.put("PublishRNWKProperties::singleRateAudio", "0");
        propertiesMap.put("Vector::DocumentClass", "");
        propertiesMap.put("Vector::External Player", "FlashPlayer10");
        propertiesMap.put("PublishHtmlProperties::showTagWarnMsg", "1");
        propertiesMap.put("PublishHtmlProperties::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::Units", "0");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultAlternateFilename", "1");
        propertiesMap.put("PublishGifProperties::Smooth", "1");
        propertiesMap.put("PublishRNWKProperties::mediaCopyright", "(c) 2000");
        propertiesMap.put("PublishRNWKProperties::flashBitRate", "1200");
        propertiesMap.put("Vector::ScriptStuckDelay", "15");
        propertiesMap.put("Vector::Compress Movie", "1");
        propertiesMap.put("Vector::Package Paths", "");
        propertiesMap.put("PublishFormatProperties::flashFileName", basePublishName + ".swf");
        propertiesMap.put("PublishFormatProperties::gifDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMac", "0");
        propertiesMap.put("PublishGifProperties::DitherOption", "");
        propertiesMap.put("PublishRNWKProperties::exportSMIL", "1");
        propertiesMap.put("PublishRNWKProperties::speed384K", "0");
        propertiesMap.put("PublishRNWKProperties::exportAudio", "1");
        propertiesMap.put("Vector::AS3ExportFrame", "1");
        propertiesMap.put("Vector::Invisible Layer", "1");
        propertiesMap.put("PublishHtmlProperties::Quality", "4");
        propertiesMap.put("PublishHtmlProperties::VerticalAlignment", "1");
        propertiesMap.put("PublishFormatProperties::pngFileName", basePublishName + ".png");
        propertiesMap.put("PublishFormatProperties::html", "1");
        propertiesMap.put("PublishPNGProperties::FilterOption", "");
        propertiesMap.put("PublishRNWKProperties::mediaDescription", "");
        propertiesMap.put("Vector::Override Sounds", "0");
        propertiesMap.put("PublishHtmlProperties::DeviceFont", "0");
        propertiesMap.put("PublishQTProperties::Flatten", "1");
        propertiesMap.put("PublishPNGProperties::BitDepth", "24-bit with Alpha");
        propertiesMap.put("PublishPNGProperties::Smooth", "1");
        propertiesMap.put("PublishGifProperties::DitherSolids", "0");
        propertiesMap.put("PublishGifProperties::Interlace", "0");
        propertiesMap.put("PublishJpegProperties::DPI", "4718592");
        propertiesMap.put("Vector::Quality", "80");
        propertiesMap.put("Vector::Protect", "0");
        propertiesMap.put("PublishHtmlProperties::DisplayMenu", "1");
        propertiesMap.put("PublishHtmlProperties::HorizontalAlignment", "1");
        propertiesMap.put("PublishHtmlProperties::VersionDetectionIfAvailable", "0");
        propertiesMap.put("PublishFormatProperties::rnwkDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::jpegDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::gif", "0");
        propertiesMap.put("PublishGifProperties::Loop", "1");
        propertiesMap.put("PublishGifProperties::Width", "" + width);
        propertiesMap.put("PublishRNWKProperties::mediaKeywords", "");
        propertiesMap.put("PublishRNWKProperties::mediaTitle", "");
        propertiesMap.put("PublishRNWKProperties::speed28K", "1");
        propertiesMap.put("Vector::AS3Flags", "4102");
        propertiesMap.put("PublishFormatProperties::qtFileName", basePublishName + ".mov");
        propertiesMap.put("PublishPNGProperties::DitherOption", "");
        propertiesMap.put("PublishGifProperties::PaletteOption", "");
        propertiesMap.put("PublishGifProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishRNWKProperties::speedDualISDN", "0");
        propertiesMap.put("PublishRNWKProperties::realVideoRate", "100000");
        propertiesMap.put("PublishJpegProperties::Quality", "80");
        propertiesMap.put("Vector::IncludeXMP", "1");
        propertiesMap.put("PublishFormatProperties::flash", "1");
        propertiesMap.put("PublishPNGProperties::PaletteOption", "");
        propertiesMap.put("PublishPNGProperties::MatchMovieDim", "1");
        propertiesMap.put("PublishJpegProperties::MatchMovieDim", "1");
        propertiesMap.put("Vector::Package Export Frame", "1");
        propertiesMap.put("PublishProfileProperties::version", "1");
        propertiesMap.put("PublishHtmlProperties::Align", "0");
        propertiesMap.put("PublishFormatProperties::projectorWinFileName", basePublishName + ".exe");
        propertiesMap.put("PublishFormatProperties::pngDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacDefaultName", "1");
        propertiesMap.put("PublishQTProperties::PlayEveryFrame", "0");
        propertiesMap.put("PublishPNGProperties::DitherSolids", "0");
        propertiesMap.put("PublishJpegProperties::Progressive", "0");
        propertiesMap.put("Vector::Export Swc", "0");
        propertiesMap.put("Vector::Debugging Password", "");
        propertiesMap.put("Vector::Omit Trace Actions", "0");
        propertiesMap.put("PublishHtmlProperties::Height", "" + height);
        propertiesMap.put("PublishHtmlProperties::Width", "" + width);
        propertiesMap.put("PublishFormatProperties::jpegFileName", basePublishName + ".jpg");
        propertiesMap.put("PublishFormatProperties::flashDefaultName", "1");
        propertiesMap.put("PublishPNGProperties::Interlace", "0");
        propertiesMap.put("PublishGifProperties::Height", "" + height);
        propertiesMap.put("PublishJpegProperties::Size", "0");
        propertiesMap.put("Vector::DefaultLibraryLinkage", "0");
        propertiesMap.put("Vector::UseAS3Namespace", "1");
        propertiesMap.put("Vector::AS3AutoDeclare", "4096");
        propertiesMap.put("Vector::AS3Coach", "4");
        propertiesMap.put("Vector::DeviceSound", "0");
        propertiesMap.put("PublishHtmlProperties::TemplateFileName", "Default.html");
        propertiesMap.put("PublishHtmlProperties::WindowMode", "0");
        propertiesMap.put("PublishHtmlProperties::UsingDefaultContentFilename", "1");
        propertiesMap.put("PublishFormatProperties::projectorMacFileName", basePublishName + ".app");
        propertiesMap.put("PublishFormatProperties::htmlDefaultName", "1");
        propertiesMap.put("PublishFormatProperties::rnwk", "0");
        propertiesMap.put("PublishFormatProperties::png", "0");
        propertiesMap.put("PublishQTProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::RemoveGradients", "0");
        propertiesMap.put("PublishGifProperties::MaxColors", "255");
        propertiesMap.put("PublishGifProperties::TransparentOption", "");
        propertiesMap.put("PublishGifProperties::LoopCount", "");
        propertiesMap.put("PublishRNWKProperties::speed56K", "1");
        propertiesMap.put("Vector::Report", "0");
        propertiesMap.put("PublishHtmlProperties::OwnAlternateFilename", "");
        propertiesMap.put("PublishHtmlProperties::AlternateFilename", "");
        propertiesMap.put("PublishHtmlProperties::ContentFilename", "");
        propertiesMap.put("Vector::AS3 External Library Paths", "$(AppConfig)/ActionScript 3.0/libs/11.0/textLayout.swc");
        propertiesMap.put("PublishGifProperties::OptimizeColors", "1");
        propertiesMap.put("PublishRNWKProperties::audioFormat", "0");
        propertiesMap.put("Vector::RSLPreloaderSWF", "$(AppConfig)/ActionScript 3.0/rsls/loader_animation.swf");
        propertiesMap.put("Vector::HardwareAcceleration", "0");
        propertiesMap.put("Vector::AS3Strict", "2");
        propertiesMap.put("Vector::Version", "10");
        propertiesMap.put("Vector::Event Format", "0");
        propertiesMap.put("Vector::Stream Compress", "7");
        propertiesMap.put("PublishFormatProperties::qt", "0");
        propertiesMap.put("PublishPNGProperties::Height", "" + height);
        propertiesMap.put("PublishPNGProperties::Width", "" + width);
        propertiesMap.put("PublishGifProperties::RemoveGradients", "0");
        propertiesMap.put("PublishRNWKProperties::speed512K", "0");
        propertiesMap.put("PublishJpegProperties::Height", "" + height);
        propertiesMap.put("Vector::EventUse8kSampleRate", "0");
        propertiesMap.put("Vector::StreamUse8kSampleRate", "0");
        propertiesMap.put("Vector::ActionScriptVersion", "3");
        propertiesMap.put("Vector::Event Compress", "7");
        propertiesMap.put("PublishHtmlProperties::Scale", "0");
        propertiesMap.put("PublishFormatProperties::projectorWinDefaultName", "1");
        propertiesMap.put("PublishQTProperties::Looping", "0");
        propertiesMap.put("PublishQTProperties::UseQTSoundCompression", "0");
        propertiesMap.put("PublishPNGProperties::PaletteName", "");
        propertiesMap.put("PublishPNGProperties::Transparent", "0");
        propertiesMap.put("PublishGifProperties::TransparentAlpha", "128");
        propertiesMap.put("PublishGifProperties::Animated", "0");
        propertiesMap.put("PublishRNWKProperties::mediaAuthor", "");
        propertiesMap.put("PublishRNWKProperties::speedCorporateLAN", "0");
        propertiesMap.put("PublishRNWKProperties::showBitrateDlog", "1");
        propertiesMap.put("PublishRNWKProperties::exportFlash", "1");
        propertiesMap.put("PublishJpegProperties::Width", "" + width);
        propertiesMap.put("Vector::Stream Format", "0");
        propertiesMap.put("Vector::DeblockingFilter", "0");
        propertiesMap.put("PublishHtmlProperties::VersionInfo", "10,1,52,0;9,0,124,0;8,0,24,0;7,0,14,0;6,0,79,0;5,0,58,0;4,0,32,0;3,0,8,0;2,0,1,12;1,0,0,1;");
        propertiesMap.put("PublishFormatProperties::gifFileName", basePublishName + ".gif");
        propertiesMap.put("PublishFormatProperties::qtDefaultName", "1");
        propertiesMap.put("PublishQTProperties::PausedAtStart", "0");
        propertiesMap.put("PublishQTProperties::ControllerOption", "0");
        propertiesMap.put("PublishPNGProperties::MaxColors", "255");
        propertiesMap.put("Vector::AS3 Config Const", "CONFIG::FLASH_AUTHORING=\"true\";");
        propertiesMap.put("PublishHtmlProperties::UsingOwnAlternateFile", "0");
        propertiesMap.put("PublishFormatProperties::rnwkFileName", basePublishName + ".smil");
        propertiesMap.put("PublishFormatProperties::projectorWin", "0");
        propertiesMap.put("PublishFormatProperties::defaultNames", "1");

        return propertiesMap;
    }

    private String getXmpp(String creatorInfo) {
        SimpleDateFormat XMPP_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        String timeCreatedXmpp = XMPP_DATE_FORMAT.format(new Date(timeCreatedMs));

        if (debugRandom) {
            String timNotRandom = "";
            for (int i = 0; i < timeCreatedXmpp.length(); i++) {
                timNotRandom += 'X';
            }
            timeCreatedXmpp = timNotRandom;
        }
        String xmppDocumentId = generateGUID();
        String xmppOriginalDocumentId = generateGUID();
        String xmppId = generateXmppId();
        String xmppInstanceId = generateGUID();

        return "<?xpacket begin=\"" + (char) 0xFEFF + "\" id=\"" + xmppId + "\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.0-c060 61.134777, 2010/02/12-17:32:00        \">\n"
                + "   <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "      <rdf:Description rdf:about=\"\"\n"
                + "            xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n"
                + "         <xmp:CreatorTool>" + creatorInfo + "</xmp:CreatorTool>\n"
                + "         <xmp:CreateDate>" + timeCreatedXmpp + "</xmp:CreateDate>\n"
                + "         <xmp:MetadataDate>" + timeCreatedXmpp + "</xmp:MetadataDate>\n"
                + "         <xmp:ModifyDate>" + timeCreatedXmpp + "</xmp:ModifyDate>\n"
                + "      </rdf:Description>\n"
                + "      <rdf:Description rdf:about=\"\"\n"
                + "            xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                + "         <dc:format>application/vnd.adobe.fla</dc:format>\n"
                + "      </rdf:Description>\n"
                + "      <rdf:Description rdf:about=\"\"\n"
                + "            xmlns:xmpMM=\"http://ns.adobe.com/xap/1.0/mm/\"\n"
                + "            xmlns:stRef=\"http://ns.adobe.com/xap/1.0/sType/ResourceRef#\"\n"
                + "            xmlns:stEvt=\"http://ns.adobe.com/xap/1.0/sType/ResourceEvent#\">\n"
                + "         <xmpMM:DerivedFrom rdf:parseType=\"Resource\">\n"
                + "            <stRef:instanceID>xmp.iid:" + xmppInstanceId + "</stRef:instanceID>\n"
                + "            <stRef:documentID>xmp.did:" + xmppOriginalDocumentId + "</stRef:documentID>\n"
                + "            <stRef:originalDocumentID>xmp.did:" + xmppOriginalDocumentId + "</stRef:originalDocumentID>\n"
                + "         </xmpMM:DerivedFrom>\n"
                + "         <xmpMM:DocumentID>xmp.did:" + xmppDocumentId + "</xmpMM:DocumentID>\n"
                + "         <xmpMM:InstanceID>xmp.iid:" + xmppDocumentId + "</xmpMM:InstanceID>\n"
                + "         <xmpMM:OriginalDocumentID>xmp.did:" + xmppOriginalDocumentId + "</xmpMM:OriginalDocumentID>\n"
                + "         <xmpMM:History>\n"
                + "            <rdf:Seq>\n"
                + "               <rdf:li rdf:parseType=\"Resource\">\n"
                + "                  <stEvt:action>created</stEvt:action>\n"
                + "                  <stEvt:instanceID>xmp.iid:" + xmppOriginalDocumentId + "</stEvt:instanceID>\n"
                + "                  <stEvt:when>" + timeCreatedXmpp + "</stEvt:when>\n"
                + "                  <stEvt:softwareAgent>" + creatorInfo + "</stEvt:softwareAgent>\n"
                + "               </rdf:li>\n"
                + "               <rdf:li rdf:parseType=\"Resource\">\n"
                + "                  <stEvt:action>created</stEvt:action>\n"
                + "                  <stEvt:instanceID>xmp.iid:" + xmppInstanceId + "</stEvt:instanceID>\n"
                + "                  <stEvt:when>" + timeCreatedXmpp + "</stEvt:when>\n"
                + "                  <stEvt:softwareAgent>" + creatorInfo + "</stEvt:softwareAgent>\n"
                + "               </rdf:li>\n"
                + "               <rdf:li rdf:parseType=\"Resource\">\n"
                + "                  <stEvt:action>created</stEvt:action>\n"
                + "                  <stEvt:instanceID>xmp.iid:" + xmppDocumentId + "</stEvt:instanceID>\n"
                + "                  <stEvt:when>" + timeCreatedXmpp + "</stEvt:when>\n"
                + "                  <stEvt:softwareAgent>" + creatorInfo + "</stEvt:softwareAgent>\n"
                + "               </rdf:li>\n"
                + "            </rdf:Seq>\n"
                + "         </xmpMM:History>\n"
                + "      </rdf:Description>\n"
                + "   </rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                           \n"
                + "<?xpacket end=\"w\"?>";

        /*
        return "<?xpacket begin=\"" + (char) 0xFEFF + "\" id=\"" + xmppId + "\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.0-c060 61.134777, 2010/02/12-17:32:00        \">\n"
                + "   <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "      <rdf:Description rdf:about=\"\"\n"
                + "            xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n"
                + "         <xmp:CreatorTool>" + creatorInfo + "</xmp:CreatorTool>\n"
                + "         <xmp:CreateDate>" + timeCreatedXmpp + "</xmp:CreateDate>\n"
                + "         <xmp:MetadataDate>" + timeCreatedXmpp + "</xmp:MetadataDate>\n"
                + "         <xmp:ModifyDate>" + timeCreatedXmpp + "</xmp:ModifyDate>\n"
                + "      </rdf:Description>\n"
                + "      <rdf:Description rdf:about=\"\"\n"
                + "            xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                + "         <dc:format>application/vnd.adobe.fla</dc:format>\n"
                + "      </rdf:Description>\n"
                + "      <rdf:Description rdf:about=\"\"\n"
                + "            xmlns:xmpMM=\"http://ns.adobe.com/xap/1.0/mm/\"\n"
                + "            xmlns:stRef=\"http://ns.adobe.com/xap/1.0/sType/ResourceRef#\"\n"
                + "            xmlns:stEvt=\"http://ns.adobe.com/xap/1.0/sType/ResourceEvent#\">\n"
                + "         <xmpMM:DerivedFrom rdf:parseType=\"Resource\">\n"
                + "            <stRef:instanceID>xmp.iid:" + xmppOriginalDocumentId + "</stRef:instanceID>\n"
                + "            <stRef:documentID>xmp.did:" + xmppOriginalDocumentId + "</stRef:documentID>\n"
                + "            <stRef:originalDocumentID>xmp.did:" + xmppOriginalDocumentId + "</stRef:originalDocumentID>\n"
                + "         </xmpMM:DerivedFrom>\n"
                + "         <xmpMM:DocumentID>xmp.did:" + xmppDocumentId + "</xmpMM:DocumentID>\n"
                + "         <xmpMM:InstanceID>xmp.iid:" + xmppDocumentId + "</xmpMM:InstanceID>\n"
                + "         <xmpMM:OriginalDocumentID>xmp.did:" + xmppOriginalDocumentId + "</xmpMM:OriginalDocumentID>\n"
                + "         <xmpMM:History>\n"
                + "            <rdf:Seq>\n"
                + "               <rdf:li rdf:parseType=\"Resource\">\n"
                + "                  <stEvt:action>created</stEvt:action>\n"
                + "                  <stEvt:instanceID>xmp.iid:" + xmppOriginalDocumentId + "</stEvt:instanceID>\n"
                + "                  <stEvt:when>" + timeCreatedXmpp + "</stEvt:when>\n"
                + "                  <stEvt:softwareAgent>" + creatorInfo + "</stEvt:softwareAgent>\n"
                + "               </rdf:li>\n"
                + "               <rdf:li rdf:parseType=\"Resource\">\n"
                + "                  <stEvt:action>created</stEvt:action>\n"
                + "                  <stEvt:instanceID>xmp.iid:" + xmppDocumentId + "</stEvt:instanceID>\n"
                + "                  <stEvt:when>" + timeCreatedXmpp + "</stEvt:when>\n"
                + "                  <stEvt:softwareAgent>" + creatorInfo + "</stEvt:softwareAgent>\n"
                + "               </rdf:li>\n"
                + "            </rdf:Seq>\n"
                + "         </xmpMM:History>\n"
                + "      </rdf:Description>\n"
                + "   </rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                                                                                                    \n"
                + "                           \n"
                + "<?xpacket end=\"w\"?>";*/
    }
}
