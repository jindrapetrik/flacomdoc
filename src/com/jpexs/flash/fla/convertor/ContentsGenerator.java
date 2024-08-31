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
package com.jpexs.flash.fla.convertor;

import com.jpexs.flash.fla.convertor.swatches.ExtendedSwatchItem;
import com.jpexs.flash.fla.convertor.swatches.LinearGradientSwatchItem;
import com.jpexs.flash.fla.convertor.swatches.RadialGradientSwatchItem;
import com.jpexs.flash.fla.convertor.swatches.SolidSwatchItem;
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 *
 * @author JPEXS
 */
public class ContentsGenerator extends AbstractGenerator {

    public long timeCreatedMs = Calendar.getInstance().getTimeInMillis();
    public long timeCreated = timeCreatedMs / 1000;

    public static final int RULERUNITS_INCHES = 0;
    public static final int RULERUNITS_INCHES_DECIMAL = 1;
    public static final int RULERUNITS_POINTS = 2;
    public static final int RULERUNITS_CM = 3;
    public static final int RULERUNITS_MM = 4;
    public static final int RULERUNITS_PX = 5;

    private static final SecureRandom random = new SecureRandom();

    private String generateGUID() {
        final String HEX_CHARS = "ABCDEF0123456789";
        int length = 32;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(HEX_CHARS.length());
            sb.append(HEX_CHARS.charAt(index));
        }
        return sb.toString();
    }

    private String generateXmppId() {
        int length = 24;

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

    //protected int contentsSymbolCnt = 0;
    //protected List<Media> media = new ArrayList<>();
    private Map<String, String> vectorsMap = new LinkedHashMap<>();

    {

        vectorsMap.put("Vector::Package Paths", "$(LocalData)/Classes;.");
        vectorsMap.put("Vector::AS3 Package Paths", "$(AppConfig)/ActionScript 3.0/Classes;$(AppConfig)/Component Source/ActionScript 3.0/User Interface;$(AppConfig)/Component Source/ActionScript 3.0/FLVPlayback;$(AppConfig)/Component Source/ActionScript 3.0/FLVPlaybackCaptioning;.");
        vectorsMap.put("Vector::ActionScriptVersion", "1");
        vectorsMap.put("Vector::Version", "" + getFlashVersion());
        vectorsMap.put("Vector::External Player", "Flash Player " + getFlashVersion());
    }

    protected List<SolidSwatchItem> solidSwatches = Arrays.asList(
            /* 0 */new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 1 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 1 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 2 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 3 */ new SolidSwatchItem(0x00, 0x33, 0x00, 0x50, 0xEF, 0x18),
            /* 4 */ new SolidSwatchItem(0x00, 0x66, 0x00, 0x50, 0xEF, 0x30),
            /* 5 */ new SolidSwatchItem(0x00, 0x99, 0x00, 0x50, 0xEF, 0x48),
            /* 6 */ new SolidSwatchItem(0x00, 0xCC, 0x00, 0x50, 0xEF, 0x60),
            /* 7 */ new SolidSwatchItem(0x00, 0xFF, 0x00, 0x50, 0xEF, 0x78),
            /* 8 */ new SolidSwatchItem(0x33, 0x00, 0x00, 0x00, 0xEF, 0x18),
            /* 9 */ new SolidSwatchItem(0x33, 0x33, 0x00, 0x28, 0xEF, 0x18),
            /* 10 */ new SolidSwatchItem(0x33, 0x66, 0x00, 0x3C, 0xEF, 0x30),
            /* 11 */ new SolidSwatchItem(0x33, 0x99, 0x00, 0x43, 0xEF, 0x48),
            /* 12 */ new SolidSwatchItem(0x33, 0xCC, 0x00, 0x46, 0xEF, 0x60),
            /* 13 */ new SolidSwatchItem(0x33, 0xFF, 0x00, 0x48, 0xEF, 0x78),
            /* 14 */ new SolidSwatchItem(0x66, 0x00, 0x00, 0x00, 0xEF, 0x30),
            /* 15 */ new SolidSwatchItem(0x66, 0x33, 0x00, 0x14, 0xEF, 0x30),
            /* 16 */ new SolidSwatchItem(0x66, 0x66, 0x00, 0x28, 0xEF, 0x30),
            /* 17 */ new SolidSwatchItem(0x66, 0x99, 0x00, 0x35, 0xEF, 0x48),
            /* 18 */ new SolidSwatchItem(0x66, 0xCC, 0x00, 0x3C, 0xEF, 0x60),
            /* 19 */ new SolidSwatchItem(0x66, 0xFF, 0x00, 0x40, 0xEF, 0x78),
            /* 20 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 21 */ new SolidSwatchItem(0x33, 0x33, 0x33, 0x00, 0x00, 0x30),
            /* 22 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 23 */ new SolidSwatchItem(0x00, 0x00, 0x33, 0xA0, 0xEF, 0x18),
            /* 24 */ new SolidSwatchItem(0x00, 0x33, 0x33, 0x78, 0xEF, 0x18),
            /* 25 */ new SolidSwatchItem(0x00, 0x66, 0x33, 0x64, 0xEF, 0x30),
            /* 26 */ new SolidSwatchItem(0x00, 0x99, 0x33, 0x5D, 0xEF, 0x48),
            /* 27 */ new SolidSwatchItem(0x00, 0xCC, 0x33, 0x5A, 0xEF, 0x60),
            /* 28 */ new SolidSwatchItem(0x00, 0xFF, 0x33, 0x58, 0xEF, 0x78),
            /* 29 */ new SolidSwatchItem(0x33, 0x00, 0x33, 0xC8, 0xEF, 0x18),
            /* 30 */ new SolidSwatchItem(0x33, 0x33, 0x33, 0x00, 0x00, 0x30),
            /* 31 */ new SolidSwatchItem(0x33, 0x66, 0x33, 0x50, 0x50, 0x48),
            /* 32 */ new SolidSwatchItem(0x33, 0x99, 0x33, 0x50, 0x78, 0x60),
            /* 33 */ new SolidSwatchItem(0x33, 0xCC, 0x33, 0x50, 0x90, 0x78),
            /* 34 */ new SolidSwatchItem(0x33, 0xFF, 0x33, 0x50, 0xEF, 0x90),
            /* 35 */ new SolidSwatchItem(0x66, 0x00, 0x33, 0xDC, 0xEF, 0x30),
            /* 36 */ new SolidSwatchItem(0x66, 0x33, 0x33, 0x00, 0x50, 0x48),
            /* 37 */ new SolidSwatchItem(0x66, 0x66, 0x33, 0x28, 0x50, 0x48),
            /* 38 */ new SolidSwatchItem(0x66, 0x99, 0x33, 0x3C, 0x78, 0x60),
            /* 39 */ new SolidSwatchItem(0x66, 0xCC, 0x33, 0x43, 0x90, 0x78),
            /* 40 */ new SolidSwatchItem(0x66, 0xFF, 0x33, 0x46, 0xEF, 0x90),
            /* 41 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 42 */ new SolidSwatchItem(0x66, 0x66, 0x66, 0x00, 0x00, 0x60),
            /* 43 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 44 */ new SolidSwatchItem(0x00, 0x00, 0x66, 0xA0, 0xEF, 0x30),
            /* 45 */ new SolidSwatchItem(0x00, 0x33, 0x66, 0x8C, 0xEF, 0x30),
            /* 46 */ new SolidSwatchItem(0x00, 0x66, 0x66, 0x78, 0xEF, 0x30),
            /* 47 */ new SolidSwatchItem(0x00, 0x99, 0x66, 0x6B, 0xEF, 0x48),
            /* 48 */ new SolidSwatchItem(0x00, 0xCC, 0x66, 0x64, 0xEF, 0x60),
            /* 49 */ new SolidSwatchItem(0x00, 0xFF, 0x66, 0x60, 0xEF, 0x78),
            /* 50 */ new SolidSwatchItem(0x33, 0x00, 0x66, 0xB4, 0xEF, 0x30),
            /* 51 */ new SolidSwatchItem(0x33, 0x33, 0x66, 0xA0, 0x50, 0x48),
            /* 52 */ new SolidSwatchItem(0x33, 0x66, 0x66, 0x78, 0x50, 0x48),
            /* 53 */ new SolidSwatchItem(0x33, 0x99, 0x66, 0x64, 0x78, 0x60),
            /* 54 */ new SolidSwatchItem(0x33, 0xCC, 0x66, 0x5D, 0x90, 0x78),
            /* 55 */ new SolidSwatchItem(0x33, 0xFF, 0x66, 0x5A, 0xEF, 0x90),
            /* 56 */ new SolidSwatchItem(0x66, 0x00, 0x66, 0xC8, 0xEF, 0x30),
            /* 57 */ new SolidSwatchItem(0x66, 0x33, 0x66, 0xC8, 0x50, 0x48),
            /* 58 */ new SolidSwatchItem(0x66, 0x66, 0x66, 0x00, 0x00, 0x60),
            /* 59 */ new SolidSwatchItem(0x66, 0x99, 0x66, 0x50, 0x30, 0x78),
            /* 60 */ new SolidSwatchItem(0x66, 0xCC, 0x66, 0x50, 0x78, 0x90),
            /* 61 */ new SolidSwatchItem(0x66, 0xFF, 0x66, 0x50, 0xEF, 0xA8),
            /* 62 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 63 */ new SolidSwatchItem(0x99, 0x99, 0x99, 0x00, 0x00, 0x90),
            /* 64 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 65 */ new SolidSwatchItem(0x00, 0x00, 0x99, 0xA0, 0xEF, 0x48),
            /* 66 */ new SolidSwatchItem(0x00, 0x33, 0x99, 0x93, 0xEF, 0x48),
            /* 67 */ new SolidSwatchItem(0x00, 0x66, 0x99, 0x85, 0xEF, 0x48),
            /* 68 */ new SolidSwatchItem(0x00, 0x99, 0x99, 0x78, 0xEF, 0x48),
            /* 69 */ new SolidSwatchItem(0x00, 0xCC, 0x99, 0x6E, 0xEF, 0x60),
            /* 70 */ new SolidSwatchItem(0x00, 0xFF, 0x99, 0x68, 0xEF, 0x78),
            /* 71 */ new SolidSwatchItem(0x33, 0x00, 0x99, 0xAD, 0xEF, 0x48),
            /* 72 */ new SolidSwatchItem(0x33, 0x33, 0x99, 0xA0, 0x78, 0x60),
            /* 73 */ new SolidSwatchItem(0x33, 0x66, 0x99, 0x8C, 0x78, 0x60),
            /* 74 */ new SolidSwatchItem(0x33, 0x99, 0x99, 0x78, 0x78, 0x60),
            /* 75 */ new SolidSwatchItem(0x33, 0xCC, 0x99, 0x6B, 0x90, 0x78),
            /* 76 */ new SolidSwatchItem(0x33, 0xFF, 0x99, 0x64, 0xEF, 0x90),
            /* 77 */ new SolidSwatchItem(0x66, 0x00, 0x99, 0xBB, 0xEF, 0x48),
            /* 78 */ new SolidSwatchItem(0x66, 0x33, 0x99, 0xB4, 0x78, 0x60),
            /* 79 */ new SolidSwatchItem(0x66, 0x66, 0x99, 0xA0, 0x30, 0x78),
            /* 80 */ new SolidSwatchItem(0x66, 0x99, 0x99, 0x78, 0x30, 0x78),
            /* 81 */ new SolidSwatchItem(0x66, 0xCC, 0x99, 0x64, 0x78, 0x90),
            /* 82 */ new SolidSwatchItem(0x66, 0xFF, 0x99, 0x5D, 0xEF, 0xA8),
            /* 83 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 84 */ new SolidSwatchItem(0xCC, 0xCC, 0xCC, 0x00, 0x00, 0xC0),
            /* 85 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 86 */ new SolidSwatchItem(0x00, 0x00, 0xCC, 0xA0, 0xEF, 0x60),
            /* 87 */ new SolidSwatchItem(0x00, 0x33, 0xCC, 0x96, 0xEF, 0x60),
            /* 88 */ new SolidSwatchItem(0x00, 0x66, 0xCC, 0x8C, 0xEF, 0x60),
            /* 89 */ new SolidSwatchItem(0x00, 0x99, 0xCC, 0x82, 0xEF, 0x60),
            /* 90 */ new SolidSwatchItem(0x00, 0xCC, 0xCC, 0x78, 0xEF, 0x60),
            /* 91 */ new SolidSwatchItem(0x00, 0xFF, 0xCC, 0x70, 0xEF, 0x78),
            /* 92 */ new SolidSwatchItem(0x33, 0x00, 0xCC, 0xAA, 0xEF, 0x60),
            /* 93 */ new SolidSwatchItem(0x33, 0x33, 0xCC, 0xA0, 0x90, 0x78),
            /* 94 */ new SolidSwatchItem(0x33, 0x66, 0xCC, 0x93, 0x90, 0x78),
            /* 95 */ new SolidSwatchItem(0x33, 0x99, 0xCC, 0x85, 0x90, 0x78),
            /* 96 */ new SolidSwatchItem(0x33, 0xCC, 0xCC, 0x78, 0x90, 0x78),
            /* 97 */ new SolidSwatchItem(0x33, 0xFF, 0xCC, 0x6E, 0xEF, 0x90),
            /* 98 */ new SolidSwatchItem(0x66, 0x00, 0xCC, 0xB4, 0xEF, 0x60),
            /* 99 */ new SolidSwatchItem(0x66, 0x33, 0xCC, 0xAD, 0x90, 0x78),
            /* 100 */ new SolidSwatchItem(0x66, 0x66, 0xCC, 0xA0, 0x78, 0x90),
            /* 101 */ new SolidSwatchItem(0x66, 0x99, 0xCC, 0x8C, 0x78, 0x90),
            /* 102 */ new SolidSwatchItem(0x66, 0xCC, 0xCC, 0x78, 0x78, 0x90),
            /* 103 */ new SolidSwatchItem(0x66, 0xFF, 0xCC, 0x6B, 0xEF, 0xA8),
            /* 104 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 105 */ new SolidSwatchItem(0xFF, 0xFF, 0xFF, 0x00, 0x00, 0xF0),
            /* 106 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 107 */ new SolidSwatchItem(0x00, 0x00, 0xFF, 0xA0, 0xEF, 0x78),
            /* 108 */ new SolidSwatchItem(0x00, 0x33, 0xFF, 0x98, 0xEF, 0x78),
            /* 109 */ new SolidSwatchItem(0x00, 0x66, 0xFF, 0x90, 0xEF, 0x78),
            /* 110 */ new SolidSwatchItem(0x00, 0x99, 0xFF, 0x88, 0xEF, 0x78),
            /* 111 */ new SolidSwatchItem(0x00, 0xCC, 0xFF, 0x80, 0xEF, 0x78),
            /* 112 */ new SolidSwatchItem(0x00, 0xFF, 0xFF, 0x78, 0xEF, 0x78),
            /* 113 */ new SolidSwatchItem(0x33, 0x00, 0xFF, 0xA8, 0xEF, 0x78),
            /* 114 */ new SolidSwatchItem(0x33, 0x33, 0xFF, 0xA0, 0xEF, 0x90),
            /* 115 */ new SolidSwatchItem(0x33, 0x66, 0xFF, 0x96, 0xEF, 0x90),
            /* 116 */ new SolidSwatchItem(0x33, 0x99, 0xFF, 0x8C, 0xEF, 0x90),
            /* 117 */ new SolidSwatchItem(0x33, 0xCC, 0xFF, 0x82, 0xEF, 0x90),
            /* 118 */ new SolidSwatchItem(0x33, 0xFF, 0xFF, 0x78, 0xEF, 0x90),
            /* 119 */ new SolidSwatchItem(0x66, 0x00, 0xFF, 0xB0, 0xEF, 0x78),
            /* 120 */ new SolidSwatchItem(0x66, 0x33, 0xFF, 0xAA, 0xEF, 0x90),
            /* 121 */ new SolidSwatchItem(0x66, 0x66, 0xFF, 0xA0, 0xEF, 0xA8),
            /* 122 */ new SolidSwatchItem(0x66, 0x99, 0xFF, 0x93, 0xEF, 0xA8),
            /* 123 */ new SolidSwatchItem(0x66, 0xCC, 0xFF, 0x85, 0xEF, 0xA8),
            /* 124 */ new SolidSwatchItem(0x66, 0xFF, 0xFF, 0x78, 0xEF, 0xA8),
            /* 125 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 126 */ new SolidSwatchItem(0xFF, 0x00, 0x00, 0x00, 0xEF, 0x78),
            /* 127 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 128 */ new SolidSwatchItem(0x99, 0x00, 0x00, 0x00, 0xEF, 0x48),
            /* 129 */ new SolidSwatchItem(0x99, 0x33, 0x00, 0x0D, 0xEF, 0x48),
            /* 130 */ new SolidSwatchItem(0x99, 0x66, 0x00, 0x1B, 0xEF, 0x48),
            /* 131 */ new SolidSwatchItem(0x99, 0x99, 0x00, 0x28, 0xEF, 0x48),
            /* 132 */ new SolidSwatchItem(0x99, 0xCC, 0x00, 0x32, 0xEF, 0x60),
            /* 133 */ new SolidSwatchItem(0x99, 0xFF, 0x00, 0x38, 0xEF, 0x78),
            /* 134 */ new SolidSwatchItem(0xCC, 0x00, 0x00, 0x00, 0xEF, 0x60),
            /* 135 */ new SolidSwatchItem(0xCC, 0x33, 0x00, 0x0A, 0xEF, 0x60),
            /* 136 */ new SolidSwatchItem(0xCC, 0x66, 0x00, 0x14, 0xEF, 0x60),
            /* 137 */ new SolidSwatchItem(0xCC, 0x99, 0x00, 0x1E, 0xEF, 0x60),
            /* 138 */ new SolidSwatchItem(0xCC, 0xCC, 0x00, 0x28, 0xEF, 0x60),
            /* 139 */ new SolidSwatchItem(0xCC, 0xFF, 0x00, 0x30, 0xEF, 0x78),
            /* 140 */ new SolidSwatchItem(0xFF, 0x00, 0x00, 0x00, 0xEF, 0x78),
            /* 141 */ new SolidSwatchItem(0xFF, 0x33, 0x00, 0x08, 0xEF, 0x78),
            /* 142 */ new SolidSwatchItem(0xFF, 0x66, 0x00, 0x10, 0xEF, 0x78),
            /* 143 */ new SolidSwatchItem(0xFF, 0x99, 0x00, 0x18, 0xEF, 0x78),
            /* 144 */ new SolidSwatchItem(0xFF, 0xCC, 0x00, 0x20, 0xEF, 0x78),
            /* 145 */ new SolidSwatchItem(0xFF, 0xFF, 0x00, 0x28, 0xEF, 0x78),
            /* 146 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 147 */ new SolidSwatchItem(0x00, 0xFF, 0x00, 0x50, 0xEF, 0x78),
            /* 148 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 149 */ new SolidSwatchItem(0x99, 0x00, 0x33, 0xE3, 0xEF, 0x48),
            /* 150 */ new SolidSwatchItem(0x99, 0x33, 0x33, 0x00, 0x78, 0x60),
            /* 151 */ new SolidSwatchItem(0x99, 0x66, 0x33, 0x14, 0x78, 0x60),
            /* 152 */ new SolidSwatchItem(0x99, 0x99, 0x33, 0x28, 0x78, 0x60),
            /* 153 */ new SolidSwatchItem(0x99, 0xCC, 0x33, 0x35, 0x90, 0x78),
            /* 154 */ new SolidSwatchItem(0x99, 0xFF, 0x33, 0x3C, 0xEF, 0x90),
            /* 155 */ new SolidSwatchItem(0xCC, 0x00, 0x33, 0xE6, 0xEF, 0x60),
            /* 156 */ new SolidSwatchItem(0xCC, 0x33, 0x33, 0x00, 0x90, 0x78),
            /* 157 */ new SolidSwatchItem(0xCC, 0x66, 0x33, 0x0D, 0x90, 0x78),
            /* 158 */ new SolidSwatchItem(0xCC, 0x99, 0x33, 0x1B, 0x90, 0x78),
            /* 159 */ new SolidSwatchItem(0xCC, 0xCC, 0x33, 0x28, 0x90, 0x78),
            /* 160 */ new SolidSwatchItem(0xCC, 0xFF, 0x33, 0x32, 0xEF, 0x90),
            /* 161 */ new SolidSwatchItem(0xFF, 0x00, 0x33, 0xE8, 0xEF, 0x78),
            /* 162 */ new SolidSwatchItem(0xFF, 0x33, 0x33, 0x00, 0xEF, 0x90),
            /* 163 */ new SolidSwatchItem(0xFF, 0x66, 0x33, 0x0A, 0xEF, 0x90),
            /* 164 */ new SolidSwatchItem(0xFF, 0x99, 0x33, 0x14, 0xEF, 0x90),
            /* 165 */ new SolidSwatchItem(0xFF, 0xCC, 0x33, 0x1E, 0xEF, 0x90),
            /* 166 */ new SolidSwatchItem(0xFF, 0xFF, 0x33, 0x28, 0xEF, 0x90),
            /* 167 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 168 */ new SolidSwatchItem(0x00, 0x00, 0xFF, 0xA0, 0xEF, 0x78),
            /* 169 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 170 */ new SolidSwatchItem(0x99, 0x00, 0x66, 0xD5, 0xEF, 0x48),
            /* 171 */ new SolidSwatchItem(0x99, 0x33, 0x66, 0xDC, 0x78, 0x60),
            /* 172 */ new SolidSwatchItem(0x99, 0x66, 0x66, 0x00, 0x30, 0x78),
            /* 173 */ new SolidSwatchItem(0x99, 0x99, 0x66, 0x28, 0x30, 0x78),
            /* 174 */ new SolidSwatchItem(0x99, 0xCC, 0x66, 0x3C, 0x78, 0x90),
            /* 175 */ new SolidSwatchItem(0x99, 0xFF, 0x66, 0x43, 0xEF, 0xA8),
            /* 176 */ new SolidSwatchItem(0xCC, 0x00, 0x66, 0xDC, 0xEF, 0x60),
            /* 177 */ new SolidSwatchItem(0xCC, 0x33, 0x66, 0xE3, 0x90, 0x78),
            /* 178 */ new SolidSwatchItem(0xCC, 0x66, 0x66, 0x00, 0x78, 0x90),
            /* 179 */ new SolidSwatchItem(0xCC, 0x99, 0x66, 0x14, 0x78, 0x90),
            /* 180 */ new SolidSwatchItem(0xCC, 0xCC, 0x66, 0x28, 0x78, 0x90),
            /* 181 */ new SolidSwatchItem(0xCC, 0xFF, 0x66, 0x35, 0xEF, 0xA8),
            /* 182 */ new SolidSwatchItem(0xFF, 0x00, 0x66, 0xE0, 0xEF, 0x78),
            /* 183 */ new SolidSwatchItem(0xFF, 0x33, 0x66, 0xE6, 0xEF, 0x90),
            /* 184 */ new SolidSwatchItem(0xFF, 0x66, 0x66, 0x00, 0xEF, 0xA8),
            /* 185 */ new SolidSwatchItem(0xFF, 0x99, 0x66, 0x0D, 0xEF, 0xA8),
            /* 186 */ new SolidSwatchItem(0xFF, 0xCC, 0x66, 0x1B, 0xEF, 0xA8),
            /* 187 */ new SolidSwatchItem(0xFF, 0xFF, 0x66, 0x28, 0xEF, 0xA8),
            /* 188 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 189 */ new SolidSwatchItem(0xFF, 0xFF, 0x00, 0x28, 0xEF, 0x78),
            /* 190 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 191 */ new SolidSwatchItem(0x99, 0x00, 0x99, 0xC8, 0xEF, 0x48),
            /* 192 */ new SolidSwatchItem(0x99, 0x33, 0x99, 0xC8, 0x78, 0x60),
            /* 193 */ new SolidSwatchItem(0x99, 0x66, 0x99, 0xC8, 0x30, 0x78),
            /* 194 */ new SolidSwatchItem(0x99, 0x99, 0x99, 0x00, 0x00, 0x90),
            /* 195 */ new SolidSwatchItem(0x99, 0xCC, 0x99, 0x50, 0x50, 0xA8),
            /* 196 */ new SolidSwatchItem(0x99, 0xFF, 0x99, 0x50, 0xEF, 0xC0),
            /* 197 */ new SolidSwatchItem(0xCC, 0x00, 0x99, 0xD2, 0xEF, 0x60),
            /* 198 */ new SolidSwatchItem(0xCC, 0x33, 0x99, 0xD5, 0x90, 0x78),
            /* 199 */ new SolidSwatchItem(0xCC, 0x66, 0x99, 0xDC, 0x78, 0x90),
            /* 200 */ new SolidSwatchItem(0xCC, 0x99, 0x99, 0x00, 0x50, 0xA8),
            /* 201 */ new SolidSwatchItem(0xCC, 0xCC, 0x99, 0x28, 0x50, 0xA8),
            /* 202 */ new SolidSwatchItem(0xCC, 0xFF, 0x99, 0x3C, 0xEF, 0xC0),
            /* 203 */ new SolidSwatchItem(0xFF, 0x00, 0x99, 0xD8, 0xEF, 0x78),
            /* 204 */ new SolidSwatchItem(0xFF, 0x33, 0x99, 0xDC, 0xEF, 0x90),
            /* 205 */ new SolidSwatchItem(0xFF, 0x66, 0x99, 0xE3, 0xEF, 0xA8),
            /* 206 */ new SolidSwatchItem(0xFF, 0x99, 0x99, 0x00, 0xEF, 0xC0),
            /* 207 */ new SolidSwatchItem(0xFF, 0xCC, 0x99, 0x14, 0xEF, 0xC0),
            /* 208 */ new SolidSwatchItem(0xFF, 0xFF, 0x99, 0x28, 0xEF, 0xC0),
            /* 209 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 210 */ new SolidSwatchItem(0x00, 0xFF, 0xFF, 0x78, 0xEF, 0x78),
            /* 211 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 212 */ new SolidSwatchItem(0x99, 0x00, 0xCC, 0xBE, 0xEF, 0x60),
            /* 213 */ new SolidSwatchItem(0x99, 0x33, 0xCC, 0xBB, 0x90, 0x78),
            /* 214 */ new SolidSwatchItem(0x99, 0x66, 0xCC, 0xB4, 0x78, 0x90),
            /* 215 */ new SolidSwatchItem(0x99, 0x99, 0xCC, 0xA0, 0x50, 0xA8),
            /* 216 */ new SolidSwatchItem(0x99, 0xCC, 0xCC, 0x78, 0x50, 0xA8),
            /* 217 */ new SolidSwatchItem(0x99, 0xFF, 0xCC, 0x64, 0xEF, 0xC0),
            /* 218 */ new SolidSwatchItem(0xCC, 0x00, 0xCC, 0xC8, 0xEF, 0x60),
            /* 219 */ new SolidSwatchItem(0xCC, 0x33, 0xCC, 0xC8, 0x90, 0x78),
            /* 220 */ new SolidSwatchItem(0xCC, 0x66, 0xCC, 0xC8, 0x78, 0x90),
            /* 221 */ new SolidSwatchItem(0xCC, 0x99, 0xCC, 0xC8, 0x50, 0xA8),
            /* 222 */ new SolidSwatchItem(0xCC, 0xCC, 0xCC, 0x00, 0x00, 0xC0),
            /* 223 */ new SolidSwatchItem(0xCC, 0xFF, 0xCC, 0x50, 0xEF, 0xD8),
            /* 224 */ new SolidSwatchItem(0xFF, 0x00, 0xCC, 0xD0, 0xEF, 0x78),
            /* 225 */ new SolidSwatchItem(0xFF, 0x33, 0xCC, 0xD2, 0xEF, 0x90),
            /* 226 */ new SolidSwatchItem(0xFF, 0x66, 0xCC, 0xD5, 0xEF, 0xA8),
            /* 227 */ new SolidSwatchItem(0xFF, 0x99, 0xCC, 0xDC, 0xEF, 0xC0),
            /* 228 */ new SolidSwatchItem(0xFF, 0xCC, 0xCC, 0x00, 0xEF, 0xD8),
            /* 229 */ new SolidSwatchItem(0xFF, 0xFF, 0xCC, 0x28, 0xEF, 0xD8),
            /* 230 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 231 */ new SolidSwatchItem(0xFF, 0x00, 0xFF, 0xC8, 0xEF, 0x78),
            /* 232 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            /* 233 */ new SolidSwatchItem(0x99, 0x00, 0xFF, 0xB8, 0xEF, 0x78),
            /* 234 */ new SolidSwatchItem(0x99, 0x33, 0xFF, 0xB4, 0xEF, 0x90),
            /* 235 */ new SolidSwatchItem(0x99, 0x66, 0xFF, 0xAD, 0xEF, 0xA8),
            /* 236 */ new SolidSwatchItem(0x99, 0x99, 0xFF, 0xA0, 0xEF, 0xC0),
            /* 237 */ new SolidSwatchItem(0x99, 0xCC, 0xFF, 0x8C, 0xEF, 0xC0),
            /* 238 */ new SolidSwatchItem(0x99, 0xFF, 0xFF, 0x78, 0xEF, 0xC0),
            /* 239 */ new SolidSwatchItem(0xCC, 0x00, 0xFF, 0xC0, 0xEF, 0x78),
            /* 240 */ new SolidSwatchItem(0xCC, 0x33, 0xFF, 0xBE, 0xEF, 0x90),
            /* 241 */ new SolidSwatchItem(0xCC, 0x66, 0xFF, 0xBB, 0xEF, 0xA8),
            /* 242 */ new SolidSwatchItem(0xCC, 0x99, 0xFF, 0xB4, 0xEF, 0xC0),
            /* 243 */ new SolidSwatchItem(0xCC, 0xCC, 0xFF, 0xA0, 0xEF, 0xD8),
            /* 244 */ new SolidSwatchItem(0xCC, 0xFF, 0xFF, 0x78, 0xEF, 0xD8),
            /* 245 */ new SolidSwatchItem(0xFF, 0x00, 0xFF, 0xC8, 0xEF, 0x78),
            /* 246 */ new SolidSwatchItem(0xFF, 0x33, 0xFF, 0xC8, 0xEF, 0x90),
            /* 247 */ new SolidSwatchItem(0xFF, 0x66, 0xFF, 0xC8, 0xEF, 0xA8),
            /* 248 */ new SolidSwatchItem(0xFF, 0x99, 0xFF, 0xC8, 0xEF, 0xC0),
            /* 249 */ new SolidSwatchItem(0xFF, 0xCC, 0xFF, 0xC8, 0xEF, 0xD8),
            /* 250 */ new SolidSwatchItem(0xFF, 0xFF, 0xFF, 0x00, 0x00, 0xF0)
    ///* 251 */ new SolidSwatchItem(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    );

    protected List<ExtendedSwatchItem> extendedSwatches = Arrays.asList(
            /* 0 */new LinearGradientSwatchItem(new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0), new GradientEntry(1)),
            /* 1 */ new RadialGradientSwatchItem(new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0), new GradientEntry(1)),
            /* 2 */ new RadialGradientSwatchItem(new GradientEntry(new Color(0xFF, 0x00, 0x00), 0), new GradientEntry(1)),
            /* 3 */ new RadialGradientSwatchItem(new GradientEntry(new Color(0x00, 0xFF, 0x00), 0), new GradientEntry(1)),
            /* 4 */ new RadialGradientSwatchItem(new GradientEntry(new Color(0x00, 0x00, 0xFF), 0), new GradientEntry(1)),
            /* 5 */ new LinearGradientSwatchItem(
                    new GradientEntry(new Color(0x00, 0x66, 0xFD), 0),
                    new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0.376470588235294f),
                    new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 0.47843137254902f),
                    new GradientEntry(new Color(0x99, 0x66, 0x00), 0.501960784313725f),
                    new GradientEntry(new Color(0xFF, 0xcc, 0x00), 0.666666666666667f),
                    new GradientEntry(new Color(0xFF, 0xFF, 0xFF), 1)
            ),
            /* 6 */ new LinearGradientSwatchItem(
                    new GradientEntry(new Color(0xFF, 0x00, 0x00), 0),
                    new GradientEntry(new Color(0xFF, 0xFF, 0x00), 0.164705882352941f),
                    new GradientEntry(new Color(0x00, 0xFF, 0x00), 0.364705882352941f),
                    new GradientEntry(new Color(0x00, 0xFF, 0xFF), 0.498039215686275f),
                    new GradientEntry(new Color(0x00, 0x00, 0xFF), 0.666666666666667f),
                    new GradientEntry(new Color(0xFF, 0x00, 0xFF), 0.831372549019608f),
                    new GradientEntry(new Color(0xFF, 0x00, 0x00), 1)
            ));

    public int getFlashVersion() {
        return 10;
    }

    public void generate(
            InputStream domDocumentIs,
            InputStream publishSettingsIs,
            InputStream metadataIs,
            File outputDir
    ) throws SAXException, IOException, ParserConfigurationException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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

        int rulerUnitType = getAttributeAsInt(document, "rulerUnitType", Arrays.asList(
                "inches",
                "decimal inches",
                "points",
                "centimeters",
                "millimeters",
                "pixels"
        ), "pixels");

        float frameRate = 24;
        if (document.hasAttribute("frameRate")) {
            frameRate = Float.parseFloat(document.getAttribute("frameRate"));
        }

        List<Element> timelinesElements = getAllSubElementsByName(document, "timelines");

        List<String> definedClasses = new ArrayList<>();

        File contentsFile = outputDir.toPath().resolve("Contents").toFile();
        try (FileOutputStream os = new FileOutputStream(contentsFile)) {
            String strCDocumentPage = "CDocumentPage";
            FlaCs4Writer fg = new FlaCs4Writer(os);
            fg.write(0x47, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);

            int pageNum = 1;
            for (Element timelinesElement : timelinesElements) {
                Element domTimeline = getSubElementByName(timelinesElement, "DOMTimeline");
                if (domTimeline == null) {
                    continue;
                }
                if (!domTimeline.hasAttribute("name")) {
                    continue;
                }
                String name = domTimeline.getAttribute("name");

                useClass("CDocumentPage", 0x19, fg, definedClasses);

                String pageName = "P " + pageNum + " " + timeCreated;
                pageNum++;
                fg.writeLenUnicodeString(pageName);
                fg.write(0xFF, 0xFE, 0xFF);
                fg.writeLenUnicodeString(name);
                fg.write(
                        0x00, 0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x01, 0x00, 0x00, 0x00, 0x06,
                        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
                        0x00, 0x00, 0x00);
                fg.writeUI32(timeCreated);
                fg.write(
                        0x56, 0x02, //??? 598 dec
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07,
                        0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x02, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00);
                fg.writeUI32(timeCreated);
                fg.write(
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x02, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
                        0x07, 0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x02, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF,
                        0xFF, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x07,
                        0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x03,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x03,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x03,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x02, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00,
                        0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                        0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00,
                        0xFF, 0xFE, 0xFF, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x80, 0x00, 0x00, 0x00);
                /*0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x02, 0x00, 0x01, 0x00, 0x02, 0x00);*/
                //writeContentsSymbol(dw, "Symbol 1", "shape 1", false);
                //writeContentsSymbol(dw, "Symbol 2", "shape 2", false);
                //writeContentsSymbol(dw, "Symbol 3", "sprite 3", true);

                /*fg.write(0x00, 0x00, contentsSymbolCnt + 1, 0x00, 0x00, 0x00, contentsSymbolCnt + 1, 0x00,
                0x05, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00);*/
                fg.write(
                        0x80, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00,
                        0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00);

                PageGenerator pageGenerator = new PageGenerator();
                pageGenerator.generatePageFile(domTimeline, outputDir.toPath().resolve(pageName).toFile());
            }

            int currentTimeline = 1;

            if (document.hasAttribute("currentTimeline")) {
                currentTimeline = Integer.parseInt(document.getAttribute("currentTimeline"));
            }

            int nextSceneIdentifier = timelinesElements.size() + 1;
            if (document.hasAttribute("nextSceneIdentifier")) {
                nextSceneIdentifier = Integer.parseInt(document.getAttribute("nextSceneIdentifier"));
            }

            fg.write(0x00, 0x00,
                    nextSceneIdentifier,
                    0x00,
                    0x01, 0x00,
                    1 + currentTimeline,
                    0x00, 0x00, 0x00, 0x01, 0x00,
                    0x00, 0x00, 0x01, 0x00,
                    rulerUnitType,
                    0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00);

            fg.writeUI16(width * 20);
            fg.write(0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00);
            fg.writeUI16(height * 20);
            fg.write(0x00, 0x00, 0x00, 0x00,
                    0xC8, 0x00, //?, was 68 01
                    0x03, 0x00,
                    timelinesElements.size() > 1 ? 1 : 0, //?? something like "Did you ever used multiple scenes"
                    0x8D, 0x00, 0x68,
                    0x01, 0x00, 0x00, 0x68, 0x01, 0x00, 0x00, 0x68,
                    0x01, 0x00, 0x00, 0x68, 0x01, 0x00, 0x00, 0x01,
                    0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                    0x00, 0x00, 0x00, backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), 0xFF,
                    0x94, 0x94, 0x94, //? some color
                    0xFF, 0x00, (int) Math.round((frameRate - Math.floor(frameRate)) * 256), (int) Math.floor(frameRate), 0x00, 0x00,
                    0x00, 0x03, 0xb4, 0x00, 0x00, 0x00);
            writeMap(fg, getLegacyProperties(), true);

            if (publishSettings == null) {
                fg.writeUI32(1);
                writeMap(fg, getProperties("Untitled-1", width, height), true);
            } else {
                List<Element> flashProfiles = getAllSubElementsByName(publishSettings.getDocumentElement(), "flash_profile");
                fg.writeUI32(flashProfiles.size()); //?
                for (Element flashProfile : flashProfiles) {
                    Map<String, String> properties = getProperties("Untitled-1", width, height);
                    for (Element propertiesSet : getAllSubElements(flashProfile)) {
                        String namespace = propertiesSet.getTagName();
                        for (Element property : getAllSubElements(propertiesSet)) {
                            String key = property.getTagName();
                            String value = property.getTextContent();
                            if ("PublishFlashProperties".equals(namespace)
                                    && ("LibraryPath".equals(key)
                                    || "LibraryVersions".equals(key))) {
                                continue;
                            }
                            String nsKey = namespace + "::" + key;
                            if (!properties.containsKey(nsKey)) {
                                continue;
                            }
                            properties.put(nsKey, value);
                        }
                    }
                    writeMap(fg, properties, true);
                }
            }
            fg.write(0xFF, 0xFF, 0xFF, 0xFF);
            fg.write(0xFF, 0xFE, 0xFF, 0x00);
            fg.write(0xFF, 0xFE, 0xFF, 0x00);
            fg.write(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0x00, 0xFF, 0xFF, 0x00, 0x00);
            writeColorDef(fg, timelinesElements.size(), 0x04, true);
            fg.write(0x00,
                    0xFF, 0xFE, 0xFF);
            fg.writeLenUnicodeString("PublishQTProperties::QTSndSettings");
            fg.write(0xFF, 0xFF, 0x01, 0x00);
            writeQTAudioSettings(fg, true);
            fg.write(0xFF, 0xFE, 0xFF);
            fg.write(0xFF);
            String creatorInfo = "";
            if (document.hasAttribute("creatorInfo")) {
                creatorInfo = document.getAttribute("creatorInfo");
            }
            fg.writeDoubleLenUnicodeString(getXmpp(creatorInfo));
            /*
            public int majorVersion = 11;  // 11 = CS5, 10 = CS4
    public int buildNumber = 485;
    public String platform = "Windows";

    public String versionInfo = "Saved by Adobe Flash " + platform + " " + majorVersion + ".0 build " + buildNumber;
    public String creatorInfo = "Adobe Flash Professional CS5";
             */
            fg.write(0xFF, 0xFE, 0xFF, 00);
            fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            fg.write(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
            fg.write(0x00, 0x00, 0x00, 0x00, 0x00);
            int majorVersion = 0;
            if (document.hasAttribute("majorVersion")) {
                majorVersion = Integer.parseInt(document.getAttribute("majorVersion"));
            }
            fg.write(majorVersion);
            fg.write(0x00, 0x00, 0x00);
            int buildNumber = 0;
            if (document.hasAttribute("buildNumber")) {
                buildNumber = Integer.parseInt(document.getAttribute("buildNumber"));
            }

            fg.writeUI16(buildNumber);
            fg.write(0x00, 0x00, 0x43, 0x00);

            String versionInfo = "";
            if (document.hasAttribute("versionInfo")) {
                versionInfo = document.getAttribute("versionInfo");
            }

            fg.write(versionInfo.getBytes());
            String timecount = " timecount = " + timeCreated;
            fg.write(timecount.getBytes());
            fg.write(0x00);
            fg.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x4B, 0x40, 0x7C, 0x15, 0x00, 0x00, 0xA0, 0x0F, 0x00, 0x00,
                    0x01, 0x00, 0x00, 0x00);
        }
    }

    protected void writeQTAudioSettings(FlaCs4Writer dw, boolean cs4) throws IOException {
        String CQTAudioSettings = "CQTAudioSettings";
        dw.write(CQTAudioSettings.length(),
                0x00);
        dw.write(CQTAudioSettings.getBytes());
        dw.write(
                0x00, 0x00, 0x00, 0x00, 0x01, 0x00, cs4 ? 0x58 : 0x00, 0xFF,
                cs4 ? 0xFF : 0x00, 0xFF, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0xFE, 0xFF,
                0x00, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x00, 0x00, 0xC8, 0x00,//0x68, 0x01,
                0xFF, 0xFE, 0xFF,
                0x00,
                0xFF, 0xFE, 0xFF,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF,
                0xFF, 0xFE, 0xFF,
                0x00, 0x01, 0x00, 0x00, 0x00,
                0xFF, 0xFE, 0xFF,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);

    }

    protected void writeColorDef(FlaCs4Writer dw, int sceneCount, int b, boolean cs4) throws IOException {

        String CColorDef = "CColorDef";
        int sep = 0x02 + sceneCount;//contentsSymbolCnt + (media.isEmpty() ? 0 : media.size() + 1);
        dw.write(CColorDef.length(), 0x00);
        dw.write(CColorDef.getBytes());
        dw.write(b);
        //254 swatches        
        for (int s = 0; s < solidSwatches.size(); s++) {
            if (s > 0) {
                dw.write(0x00, sep, 0x80, b);
            }
            SolidSwatchItem sw = solidSwatches.get(s);
            dw.write(sw.red, sw.green, sw.blue, 0xFF, 0x00, 0x00, sw.hue, 0x00, sw.saturation, 0x00, sw.brightness);
        }

        dw.write(0x00);
        if (cs4) {
            dw.write(0x01);
        }

        dw.write(0x00, extendedSwatches.size());

        for (int x = 0; x < extendedSwatches.size(); x++) {
            ExtendedSwatchItem ex = extendedSwatches.get(x);
            dw.write(0x00, sep, 0x80, b);
            if (x == 5) { //WTF?
                dw.write(0xFF, 0xFF, 0xFF);
            } else {
                dw.write(0x00, 0x00, 0x00);
            }
            dw.write(0xFF, ex.getType(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, ex.entries.size());
            if (b == 0x04) {
                dw.write(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            }
            for (GradientEntry en : ex.entries) {
                int r = (int) Math.round(en.ratio * 255);
                dw.write(r, en.color.getRed(), en.color.getGreen(), en.color.getBlue(), 0xFF);
            }
            dw.write(0x00, 0x00, 0x00, 0x00);
            if (x == 5) {
                dw.write(0xF0);
            } else {
                dw.write(0x00);
            }
        }

        dw.write(0x00, 0x01, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01);

    }

    protected void writeMap(FlaCs4Writer dw, Map<String, String> map, boolean isUni) throws IOException {
        dw.writeUI16(map.size());
        for (String key : map.keySet()) {
            String val = map.get(key);
            if (isUni) {
                dw.write(0xFF, 0xFE, 0xFF);
                dw.writeLenUnicodeString(key);
                dw.write(0xFF, 0xFE, 0xFF);
                dw.writeLenUnicodeString(val);
            } else {
                dw.writeLenAsciiString(key);
                dw.writeLenAsciiString(val);
            }
        }
    }

    protected void writeVectors(FlaCs4Writer dw, boolean isUni) throws IOException {
        writeMap(dw, vectorsMap, isUni);
    }

    public void generate(
            File domDocumentFile,
            File publishSettingsFile,
            File metadataFile,
            File outputDir) throws IOException, SAXException, ParserConfigurationException {
        try (FileInputStream domDocumentIs = new FileInputStream(domDocumentFile); FileInputStream publishSettingsIs = publishSettingsFile == null ? null : new FileInputStream(publishSettingsFile); FileInputStream metadataIs = metadataFile == null ? null : new FileInputStream(metadataFile)) {
            generate(domDocumentIs, publishSettingsIs, metadataIs, outputDir);
        }
    }

    private Map<String, String> getLegacyProperties() {
        Map<String, String> propertiesMap = new HashMap<>();
        propertiesMap.put("legacyLineSpacing", "0");
        return propertiesMap;
    }

    private Map<String, String> getProperties(String basePublishName, int width, int height) {
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

        String xmppDocumentId = generateGUID();
        String xmppOriginalDocumentId = generateGUID();

        return "<?xpacket begin=\"" + (char) 0xFEFF + "\" id=\"" + generateXmppId() + "\"?>\n"
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
                + "            xmlns:stEvt=\"http://ns.adobe.com/xap/1.0/sType/ResourceEvent#\"\n"
                + "            xmlns:stRef=\"http://ns.adobe.com/xap/1.0/sType/ResourceRef#\">\n"
                + "         <xmpMM:InstanceID>xmp.iid:" + xmppDocumentId + "</xmpMM:InstanceID>\n"
                + "         <xmpMM:DocumentID>xmp.did:" + xmppDocumentId + "</xmpMM:DocumentID>\n"
                + "         <xmpMM:OriginalDocumentID>xmp.did:" + xmppOriginalDocumentId + "</xmpMM:OriginalDocumentID>\n"
                + "         <xmpMM:History>\n"
                + "            <rdf:Seq>\n"
                + "               <rdf:li rdf:parseType=\"Resource\">\n"
                + "                  <stEvt:action>created</stEvt:action>\n"
                + "                  <stEvt:instanceID>xmp.iid:" + xmppOriginalDocumentId + "</stEvt:instanceID>\n"
                + "                  <stEvt:when>" + timeCreatedXmpp + "</stEvt:when>\n"
                + "                  <stEvt:softwareAgent>Adobe Flash Professional CS5</stEvt:softwareAgent>\n"
                + "               </rdf:li>\n"
                + "               <rdf:li rdf:parseType=\"Resource\">\n"
                + "                  <stEvt:action>saved</stEvt:action>\n"
                + "                  <stEvt:instanceID>xmp.iid:" + xmppDocumentId + "</stEvt:instanceID>\n"
                + "                  <stEvt:when>" + timeCreatedXmpp + "</stEvt:when>\n"
                + "                  <stEvt:softwareAgent>Adobe Flash Professional CS5</stEvt:softwareAgent>\n"
                + "                  <stEvt:changed>/</stEvt:changed>\n"
                + "               </rdf:li>\n"
                + "            </rdf:Seq>\n"
                + "         </xmpMM:History>\n"
                + "         <xmpMM:DerivedFrom rdf:parseType=\"Resource\">\n"
                + "            <stRef:instanceID>xmp.iid:" + xmppOriginalDocumentId + "</stRef:instanceID>\n"
                + "            <stRef:documentID>xmp.did:" + xmppOriginalDocumentId + "</stRef:documentID>\n"
                + "            <stRef:originalDocumentID>xmp.did:" + xmppOriginalDocumentId + "</stRef:originalDocumentID>\n"
                + "         </xmpMM:DerivedFrom>\n"
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
    }
}
