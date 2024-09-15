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
    F8(0x3F, 0x17, 4, 4, 0x18, 4, 4, 0x0B, 4,4,6, 0x13, 3, 2, 0xD, 0xC, 4,4,4,4,6, 3, 6, 6, 9, 494),
    CS3(0x43, 0x18, 5, 5, 0x1A, 5, 5, 0x0B, 5,5,7, 0x13, 6, 2, 0xD, 0xC, 5,5,5,5,6, 3, 6, 6, 10, 544),
    CS4(0x47, 0x19, 5, 5, 0x1D, 5, 5, 0x0D, 5,5,7, 0x16, 6, 3, 0xE, 0xF, 5,5,5,5,7, 4, 7, 7, 11, 485);

    private final int contentsVersion;
    private final int documentPageVersion;
    private final int pageVersion;
    private final int frameVersion;
    private final int frameVersionB;
    private final int frameVersionC;
    private final int layerVersion;
    private final int layerVersionB;
    private final int spriteVersion;
    private final int spriteVersionB;
    private final int spriteVersionC;
    private final int symbolType;
    private final int shapeType;
    private final int fontVersion;
    private final int textVersion;
    private final int textVersionB;
    private final int textVersionC;
    private final int bitmapVersion;
    private final int videoStreamVersion;
    private final int groupVersion;
    private final int mediaBitsVersion;
    private final int mediaBitsVersionB;
    private final int mediaSoundVersion;
    private final int mediaVideoVersion;
    //this is actually one version up since we exported the FLA from newer version to older
    private final int generatorVersion;
    private final int generatorBuild;
    

    FlaFormatVersion(
            int contentsVersion,
            int documentPageVersion,
            int pageVersion,
            int frameVersion,
            int frameVersionB,
            int frameVersionC,
            int layerVersion,
            int layerVersionB,
            int spriteVersion,   
            int spriteVersionB,
            int spriteVersionC,
            int symbolType,
            int shapeType,
            int fontVersion,
            int textVersion,
            int textVersionB,
            int textVersionC,
            int bitmapVersion,
            int videoStreamVersion,
            int groupVersion,
            int mediaBitsVersion,
            int mediaBitsVersionB,
            int mediaSoundVersion,
            int mediaVideoVersion,
            int generatorVersion,
            int generatorBuild
    ) {
        this.contentsVersion = contentsVersion;
        this.documentPageVersion = documentPageVersion;
        this.pageVersion = pageVersion;
        this.frameVersion = frameVersion;
        this.frameVersionB = frameVersionB;
        this.frameVersionC = frameVersionC;
        this.layerVersion = layerVersion;
        this.layerVersionB = layerVersionB;
        this.spriteVersion = spriteVersion;
        this.spriteVersionB = spriteVersionB;
        this.spriteVersionC = spriteVersionC;
        this.symbolType = symbolType;
        this.shapeType = shapeType;
        this.fontVersion = fontVersion;
        this.textVersion = textVersion;
        this.textVersionB = textVersionB;
        this.bitmapVersion = bitmapVersion;
        this.videoStreamVersion = videoStreamVersion;
        this.groupVersion = groupVersion;
        this.mediaBitsVersion = mediaBitsVersion;
        this.mediaBitsVersionB = mediaBitsVersionB;
        this.mediaSoundVersion = mediaSoundVersion;
        this.mediaVideoVersion = mediaVideoVersion;
        this.generatorVersion = generatorVersion;
        this.generatorBuild = generatorBuild;
        this.textVersionC = textVersionC;
    }

    public int getContentsVersion() {
        return contentsVersion;
    }

    public int getDocumentPageVersion() {
        return documentPageVersion;
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

    public int getSymbolType() {
        return symbolType;
    }

    public int getShapeType() {
        return shapeType;
    }

    public int getFontVersion() {
        return fontVersion;
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

    public int getMediaSoundVersion() {
        return mediaSoundVersion;
    }

    public int getMediaVideoVersion() {
        return mediaVideoVersion;
    }

    public int getGeneratorVersion() {
        return generatorVersion;
    }

    public int getGeneratorBuild() {
        return generatorBuild;
    }

}
