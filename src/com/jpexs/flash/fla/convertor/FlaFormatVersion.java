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

/**
 *
 * @author JPEXS
 */
public enum FlaFormatVersion {
    MX(0x29, 0x11, 3, 3, 5, 5, 3, 1, 1, 0x16, 1, 1, 0x0A, 1, 1, 3, 2, 5, 1, 6, 8, 0x0A, 2, 1, 3, 1, 7, 5, 9, 7, 1, 1, 1, 1, 5, 3, 5, 5, 8, 5, 5, 3, 5, 1, 3, 1, 3, 3, 5,1, 9, 494, false),
    MX2004(0x38, 0x16, 5, 5, 5, 6, 3, 2, 2, 0x17, 4, 2, 0x0B, 2, 4, 5, 5, 6, 2, 8, 0xB, 0x0E, 2, 2, 4, 2, 0xA, 6, 0xC, 0xA, 2, 2, 2, 2, 6, 3, 6, 6, 9, 6, 6, 5, 6, 2, 5, 2, 5, 4, 6,2, 9, 494, true),
    F8(0x3F, 0x17, 7, 7, 6, 6, 4, 4, 4, 0x18, 4, 4, 0x0B, 4, 4, 7, 6, 6, 2, 8, 0xB, 0x13, 3, 2, 4, 2, 0xC, 6, 0xD, 0xC, 4, 4, 4, 4, 6, 3, 6, 6, 0xA, 6, 6, 7, 6, 2, 7, 2, 7, 4, 6,2, 9, 494, true),
    CS3(0x43, 0x18, 7, 7, 7, 6, 4, 5, 5, 0x1A, 5, 5, 0x0B, 5, 5, 7, 7, 6, 2, 8, 0xB, 0x13, 6, 2, 4, 2, 0xC, 6, 0xD, 0xC, 5, 5, 5, 5, 6, 3, 6, 6, 0xA, 6, 6, 7, 6, 2, 7, 2, 7, 4, 6,2, 10, 544, true),
    CS4(0x47, 0x19, 7, 7, 7, 6, 4, 5, 5, 0x1D, 5, 5, 0x0D, 5, 5, 7, 7, 6, 2, 8, 0xB, 0x16, 6, 2, 4, 3, 0xF, 6, 0xE, 0xF, 5, 5, 5, 5, 7, 4, 6, 7, 0xA, 6, 7, 7, 6, 2, 7, 2, 7, 4, 6,2, 11, 485, true);

    private final int contentsVersion;
    private final int documentPageVersion;
    private final int documentPageVersionB;
    private final int documentPageVersionC;
    private final int documentPageVersionD;
    private final int documentPageVersionE;
    private final int colorDefVersion;
    private final int pageVersion;
    private final int frameVersion;
    private final int frameVersionB;
    private final int frameVersionC;
    private final int layerVersion;
    private final int layerVersionB;
    private final int spriteVersion;
    private final int spriteVersionB;
    private final int spriteVersionC;
    private final int spriteVersionD;
    private final int spriteVersionE;
    private final int spriteVersionF;
    private final int spriteVersionG;
    private final int buttonVersion;
    private final int symbolType;
    private final int shapeType;
    private final int bitmapType;
    private final int videoType;
    private final int fontVersion;
    private final int fontVersionB;
    private final int fontVersionC;
    private final int textVersion;
    private final int textVersionB;
    private final int textVersionC;
    private final int bitmapVersion;
    private final int videoStreamVersion;
    private final int groupVersion;
    private final int mediaBitsVersion;
    private final int mediaBitsVersionB;
    private final int mediaBitsVersionC;
    private final int mediaSoundVersion;
    private final int mediaSoundVersionB;
    private final int mediaSoundVersionC;
    private final int mediaVideoVersion;
    private final int mediaVideoVersionB;
    private final int mediaVideoVersionC;
    private final int mediaVideoVersionD;
    private final int asLinkageVersion;
    private final int asLinkageVersionB;
    private final int libraryFolderVersion;
    private final int libraryFolderVersionC;
    private final int libraryFolderVersionD;
    //this is actually one version up since we exported the FLA from newer version to older
    private final int generatorVersion;
    private final int generatorBuild;
    private final boolean unicode;
    private final int libraryFolderVersionB;

    FlaFormatVersion(
            int contentsVersion,
            int documentPageVersion,
            int documentPageVersionB,
            int documentPageVersionC,
            int documentPageVersionD,
            int documentPageVersionE,
            int colorDefVersion,
            int pageVersion,
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
            int generatorVersion,
            int generatorBuild,
            boolean unicode
    ) {
        this.contentsVersion = contentsVersion;
        this.documentPageVersion = documentPageVersion;
        this.documentPageVersionB = documentPageVersionB;
        this.documentPageVersionC = documentPageVersionC;
        this.documentPageVersionD = documentPageVersionD;
        this.documentPageVersionE = documentPageVersionE;
        this.colorDefVersion = colorDefVersion;
        this.pageVersion = pageVersion;
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
        this.generatorVersion = generatorVersion;
        this.generatorBuild = generatorBuild;
        this.textVersionC = textVersionC;
        this.unicode = unicode;
    }

    public int getContentsVersion() {
        return contentsVersion;
    }

    public int getDocumentPageVersion() {
        return documentPageVersion;
    }

    public int getDocumentPageVersionB() {
        return documentPageVersionB;
    }

    public int getDocumentPageVersionC() {
        return documentPageVersionC;
    }

    public int getDocumentPageVersionD() {
        return documentPageVersionD;
    }

    public int getDocumentPageVersionE() {
        return documentPageVersionE;
    }

    public int getColorDefVersion() {
        return colorDefVersion;
    }

    public int getPageVersion() {
        return pageVersion;
    }

    public int getFrameVersion() {
        return frameVersion;
    }

    public int getFrameVersionB() {
        return frameVersionB;
    }

    public int getFrameVersionC() {
        return frameVersionC;
    }

    public int getLayerVersion() {
        return layerVersion;
    }

    public int getLayerVersionB() {
        return layerVersionB;
    }

    public int getSpriteVersion() {
        return spriteVersion;
    }

    public int getSpriteVersionB() {
        return spriteVersionB;
    }

    public int getSpriteVersionC() {
        return spriteVersionC;
    }

    public int getSpriteVersionD() {
        return spriteVersionD;
    }

    public int getSpriteVersionE() {
        return spriteVersionE;
    }

    public int getSpriteVersionF() {
        return spriteVersionF;
    }

    public int getSpriteVersionG() {
        return spriteVersionG;
    }

    public int getButtonVersion() {
        return buttonVersion;
    }

    public int getSymbolType() {
        return symbolType;
    }

    public int getShapeType() {
        return shapeType;
    }

    public int getBitmapType() {
        return bitmapType;
    }

    public int getVideoType() {
        return videoType;
    }

    public int getFontVersion() {
        return fontVersion;
    }

    public int getFontVersionB() {
        return fontVersionB;
    }

    public int getFontVersionC() {
        return fontVersionC;
    }

    public int getTextVersion() {
        return textVersion;
    }

    public int getTextVersionB() {
        return textVersionB;
    }

    public int getTextVersionC() {
        return textVersionC;
    }

    public int getBitmapVersion() {
        return bitmapVersion;
    }

    public int getVideoStreamVersion() {
        return videoStreamVersion;
    }

    public int getGroupVersion() {
        return groupVersion;
    }

    public int getMediaBitsVersion() {
        return mediaBitsVersion;
    }

    public int getMediaBitsVersionB() {
        return mediaBitsVersionB;
    }

    public int getMediaBitsVersionC() {
        return mediaBitsVersionC;
    }

    public int getMediaSoundVersion() {
        return mediaSoundVersion;
    }

    public int getMediaSoundVersionB() {
        return mediaSoundVersionB;
    }

    public int getMediaSoundVersionC() {
        return mediaSoundVersionC;
    }

    public int getMediaVideoVersion() {
        return mediaVideoVersion;
    }

    public int getMediaVideoVersionB() {
        return mediaVideoVersionB;
    }

    public int getMediaVideoVersionC() {
        return mediaVideoVersionC;
    }

    public int getMediaVideoVersionD() {
        return mediaVideoVersionD;
    }

    public int getAsLinkageVersion() {
        return asLinkageVersion;
    }

    public int getAsLinkageVersionB() {
        return asLinkageVersionB;
    }

    public int getLibraryFolderVersion() {
        return libraryFolderVersion;
    }

    public int getLibraryFolderVersionB() {
        return libraryFolderVersionB;
    }

    public int getLibraryFolderVersionC() {
        return libraryFolderVersionC;
    }

    public int getLibraryFolderVersionD() {
        return libraryFolderVersionD;
    }        

    public int getGeneratorVersion() {
        return generatorVersion;
    }

    public int getGeneratorBuild() {
        return generatorBuild;
    }

    public boolean isUnicode() {
        return unicode;
    }

}
