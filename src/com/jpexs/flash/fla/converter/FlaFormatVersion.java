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

/**
 *
 * @author JPEXS
 */
public enum FlaFormatVersion {      
    
    //F1-F4 TODO - stubs - not yet fully working (filled with 0XF1 - 0xFE, 0xE1 - 0xEF, 0xD1 - 0xD5)
    F1    (0x10, 0,    3, 0, 0, 1, 1, 1,    4, 0, 1,    2, 1, 0, 0, 0, 0, 0, 0,   0,    2, 1, 0, 0,0xFC,  0xFD,0xE1,   0,  3, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0xD3,0xD4, 0, 0, 0, 0,0xD5, 3,  0,   0, false),
    F2    (0x11, 0,    3, 0, 0, 1, 1, 1,    8, 0, 1,    2, 1, 0, 0, 0, 0, 0, 0,   0,    4, 1, 1, 0,0xFC,  0xFD,0xE1,   0,  3, 1, 1, 0, 1, 3, 0, 2, 3, 5, 0, 0, 0, 0, 0,0xD3,0xD4, 0, 0, 0, 0,0xD5, 3,  0,   0, false),
    F3    (0x14, 0,    4, 0, 0, 1, 1, 1, 0x10, 0, 1,    3, 1, 0, 0, 0, 0, 0, 1,   0,    6, 2, 1, 0,0xFC,  0xFD,0xE1,   3,  4, 1, 1, 0, 1, 3, 0, 2, 3, 5, 0, 0, 0, 0, 0,0xD3,0xD4, 0, 0, 0, 0,0xD5, 4,  0,   0, false),
    F4    (0x1B, 0,    8, 4, 3, 1, 2, 1, 0x12, 0, 1,    8, 1, 0, 0, 0, 4, 0, 1,   0,    7, 2, 1, 0,0xFC,  0xFD,0xE1,   4,  4, 1, 1, 0, 1, 4, 3, 4, 4, 5, 4, 0, 0, 0, 0,0xD3,0xD4, 0, 3, 4, 0,0xD5, 4,  0,   0, false),
    F5    (0x20, 1, 0x0C, 5, 3, 1, 3, 1, 0x15, 0, 1,    8, 1, 0, 3, 0, 5, 1, 3,   5,    8, 2, 1, 0, 1,   5, 5,   5,   5, 1, 1, 1, 1, 5, 3, 5, 5,   7, 5, 0, 0, 0, 0, 0, 0, 0, 3, 5, 1, 1, 5,  0,   0, false),
    MX    (0x29, 1, 0x11, 5, 3, 1, 3, 1, 0x16, 1, 1, 0x0A, 1, 1, 3, 2, 5, 1, 6,   8, 0x0A, 2, 1, 3, 1,   7, 5,   9,   7, 1, 1, 1, 1, 5, 3, 5, 5,   8, 5, 5, 3, 5, 1, 3, 1, 3, 3, 5, 1, 1, 5,  0,   0, false),
    MX2004(0x38, 1, 0x16, 6, 3, 2, 5, 2, 0x17, 4, 2, 0x0B, 2, 4, 5, 5, 6, 2, 8, 0xB, 0x0E, 2, 2, 4, 2, 0xA, 6, 0xC, 0xA, 2, 2, 2, 2, 6, 3, 6, 6,   9, 6, 6, 5, 6, 2, 5, 2, 5, 4, 6, 2, 2, 5,  0,   0, true),
    F8    (0x3F, 1, 0x17, 6, 4, 4, 7, 4, 0x18, 4, 4, 0x0B, 4, 4, 7, 6, 6, 2, 8, 0xB, 0x13, 3, 2, 4, 2, 0xC, 6, 0xD, 0xC, 4, 4, 4, 4, 6, 3, 6, 6, 0xA, 6, 6, 7, 6, 2, 7, 2, 7, 4, 6, 2, 2, 5,  9, 494, true),
    CS3   (0x43, 1, 0x18, 6, 4, 5, 7, 5, 0x1A, 5, 5, 0x0B, 5, 5, 7, 7, 6, 2, 8, 0xB, 0x13, 6, 2, 4, 2, 0xC, 6, 0xD, 0xC, 5, 5, 5, 5, 6, 3, 6, 6, 0xA, 6, 6, 7, 6, 2, 7, 2, 7, 4, 6, 2, 2, 5, 10, 544, true),
    CS4   (0x47, 1, 0x19, 6, 4, 5, 7, 5, 0x1D, 5, 5, 0x0D, 5, 5, 7, 7, 6, 2, 8, 0xB, 0x16, 6, 2, 4, 3, 0xF, 6, 0xE, 0xF, 5, 5, 5, 5, 7, 4, 6, 7, 0xA, 6, 7, 7, 6, 2, 7, 2, 7, 4, 6, 2, 2, 5, 11, 485, true);

    public final int contentsVersion;
    public final int contentsVersionB;
    public final int documentPageVersion;
    public final int documentPageVersionB;
    public final int colorDefVersion;
    public final int pageVersion;
    public final int pageVersionB;
    public final int frameVersion;
    public final int frameVersionB;
    public final int frameVersionC;
    public final int layerVersion;
    public final int layerVersionB;
    public final int spriteVersion;
    public final int spriteVersionB;
    public final int spriteVersionC;
    public final int spriteVersionD;
    public final int spriteVersionE;
    public final int spriteVersionF;
    public final int spriteVersionG;
    public final int buttonVersion;
    public final int symbolType;
    public final int shapeType;
    public final int bitmapType;
    public final int videoType;
    public final int fontVersion;
    public final int fontVersionB;
    public final int fontVersionC;
    public final int textVersion;
    public final int textVersionB;
    public final int textVersionC;
    public final int bitmapVersion;
    public final int videoStreamVersion;
    public final int groupVersion;
    public final int mediaBitsVersion;
    public final int mediaBitsVersionB;
    public final int mediaBitsVersionC;
    public final int mediaSoundVersion;
    public final int mediaSoundVersionB;
    public final int mediaSoundVersionC;
    public final int mediaVideoVersion;
    public final int mediaVideoVersionB;
    public final int mediaVideoVersionC;
    public final int mediaVideoVersionD;
    public final int asLinkageVersion;
    public final int asLinkageVersionB;
    public final int libraryFolderVersion;    
    public final int libraryFolderVersionB;
    public final int libraryFolderVersionC;
    public final int libraryFolderVersionD;
    public final int accessibilityVersion;
    public final int shapeVersion;
    //this is actually one version up since we exported the FLA from newer version to older
    public final int generatorVersion;
    public final int generatorBuild;
    public final boolean unicode;

    FlaFormatVersion(
            int contentsVersion,
            int contentsVersionB,
            int documentPageVersion,
            int documentPageVersionB,
            int colorDefVersion,
            int pageVersion,
            int pageVersionB,
            int frameVersion,
            int frameVersionB,
            int frameVersionC,
            int layerVersion,
            int layerVersionB,
            int spriteVersion,
            int spriteVersionB,
            int spriteVersionC,
            int spriteVersionD,
            int spriteVersionE,
            int spriteVersionF,
            int spriteVersionG,
            int buttonVersion,
            int symbolType,
            int shapeType,
            int bitmapType,
            int videoType,
            int fontVersion,
            int fontVersionB,
            int fontVersionC,
            int textVersion,
            int textVersionB,
            int textVersionC,
            int bitmapVersion,
            int videoStreamVersion,
            int groupVersion,
            int mediaBitsVersion,
            int mediaBitsVersionB,
            int mediaBitsVersionC,
            int mediaSoundVersion,
            int mediaSoundVersionB,
            int mediaSoundVersionC,
            int mediaVideoVersion,
            int mediaVideoVersionB,
            int mediaVideoVersionC,
            int mediaVideoVersionD,
            int asLinkageVersion,
            int asLinkageVersionB,
            int libraryFolderVersion,
            int libraryFolderVersionB,
            int libraryFolderVersionC,
            int libraryFolderVersionD,
            int accessibilityVersion,
            int shapeVersion,
            int generatorVersion,
            int generatorBuild,            
            boolean unicode
    ) {
        this.contentsVersion = contentsVersion;
        this.contentsVersionB = contentsVersionB;
        this.documentPageVersion = documentPageVersion;
        this.documentPageVersionB = documentPageVersionB;
        this.colorDefVersion = colorDefVersion;
        this.pageVersion = pageVersion;
        this.pageVersionB = pageVersionB;
        this.frameVersion = frameVersion;
        this.frameVersionB = frameVersionB;
        this.frameVersionC = frameVersionC;
        this.layerVersion = layerVersion;
        this.layerVersionB = layerVersionB;
        this.spriteVersion = spriteVersion;
        this.spriteVersionB = spriteVersionB;
        this.spriteVersionC = spriteVersionC;
        this.spriteVersionD = spriteVersionD;
        this.spriteVersionE = spriteVersionE;
        this.spriteVersionF = spriteVersionF;
        this.spriteVersionG = spriteVersionG;
        this.buttonVersion = buttonVersion;
        this.symbolType = symbolType;
        this.shapeType = shapeType;
        this.bitmapType = bitmapType;
        this.videoType = videoType;
        this.fontVersion = fontVersion;
        this.fontVersionB = fontVersionB;
        this.fontVersionC = fontVersionC;
        this.textVersion = textVersion;
        this.textVersionB = textVersionB;
        this.bitmapVersion = bitmapVersion;
        this.videoStreamVersion = videoStreamVersion;
        this.groupVersion = groupVersion;
        this.mediaBitsVersion = mediaBitsVersion;
        this.mediaBitsVersionB = mediaBitsVersionB;
        this.mediaBitsVersionC = mediaBitsVersionC;
        this.mediaSoundVersion = mediaSoundVersion;
        this.mediaSoundVersionB = mediaSoundVersionB;
        this.mediaSoundVersionC = mediaSoundVersionC;
        this.mediaVideoVersion = mediaVideoVersion;
        this.mediaVideoVersionB = mediaVideoVersionB;
        this.mediaVideoVersionC = mediaVideoVersionC;
        this.mediaVideoVersionD = mediaVideoVersionD;
        this.asLinkageVersion = asLinkageVersion;
        this.asLinkageVersionB = asLinkageVersionB;
        this.libraryFolderVersion = libraryFolderVersion;
        this.libraryFolderVersionB = libraryFolderVersionB;
        this.libraryFolderVersionC = libraryFolderVersionC;
        this.libraryFolderVersionD = libraryFolderVersionD;
        this.accessibilityVersion = accessibilityVersion;
        this.shapeVersion = shapeVersion;
        this.generatorVersion = generatorVersion;
        this.generatorBuild = generatorBuild;
        this.textVersionC = textVersionC;
        this.unicode = unicode;
    }      
}
