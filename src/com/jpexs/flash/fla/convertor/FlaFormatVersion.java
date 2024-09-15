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
    MX2004(0x38, 0x16, 5,5,5,3,2, 2, 0x17, 4, 2, 0x0B, 2,4,5,5, 0x0E, 2, 2, 0xA, 0xC, 0xA, 2,2,2,2,6, 3, 6,0x9, 6,5, 5, 5,9, 494),
    F8(0x3F, 0x17, 7,7,6,4,4, 4, 0x18, 4, 4, 0x0B, 4,4,7,6, 0x13, 3, 2, 0xC, 0xD, 0xC, 4,4,4,4,6, 3, 6,0xA, 6,7, 7,7,9, 494),
    CS3(0x43, 0x18, 7,7,7,4,5, 5, 0x1A, 5, 5, 0x0B, 5,5,7,7, 0x13, 6, 2, 0xC, 0xD, 0xC, 5,5,5,5,6, 3, 6,0xA, 6,7, 7,7,10, 544),
    CS4(0x47, 0x19, 7,7,7,4,5, 5, 0x1D, 5, 5, 0x0D, 5,5,7,7, 0x16, 6, 3, 0xF, 0xE, 0xF, 5,5,5,5,7, 4, 7,0xA, 7,7, 7,7,11, 485);

    private final int contentsVersion;
    private final int documentPageVersion;
    private final int documentPageVersionB;
    private final int documentPageVersionC;
    private final int documentPageVersionD;
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
    private final int symbolType;
    private final int shapeType;
    private final int fontVersion;
    private final int fontVersionB;
    private final int textVersion;
    private final int textVersionB;
    private final int textVersionC;
    private final int bitmapVersion;
    private final int videoStreamVersion;
    private final int groupVersion;
    private final int mediaBitsVersion;
    private final int mediaBitsVersionB;
    private final int mediaSoundVersion;
    private final int mediaSoundVersionB;
    private final int mediaVideoVersion;
    private final int mediaVideoVersionB;
    private final int asLinkageVersion;
    private final int libraryFolderVersion;
    //this is actually one version up since we exported the FLA from newer version to older
    private final int generatorVersion;
    private final int generatorBuild;
    

    FlaFormatVersion(
            int contentsVersion,
            int documentPageVersion,
            int documentPageVersionB,
            int documentPageVersionC,
            int documentPageVersionD,
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
            int symbolType,
            int shapeType,
            int fontVersion,
            int fontVersionB,
            int textVersion,
            int textVersionB,
            int textVersionC,
            int bitmapVersion,
            int videoStreamVersion,
            int groupVersion,
            int mediaBitsVersion,
            int mediaBitsVersionB,
            int mediaSoundVersion,
            int mediaSoundVersionB,
            int mediaVideoVersion,
            int mediaVideoVersionB,
            int asLinkageVersion,
            int libraryFolderVersion,
            int generatorVersion,
            int generatorBuild 
    ) {
        this.contentsVersion = contentsVersion;
        this.documentPageVersion = documentPageVersion;
        this.documentPageVersionB = documentPageVersionB;
        this.documentPageVersionC = documentPageVersionC;
        this.documentPageVersionD = documentPageVersionD;
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
        this.symbolType = symbolType;
        this.shapeType = shapeType;
        this.fontVersion = fontVersion;
        this.fontVersionB = fontVersionB;
        this.textVersion = textVersion;
        this.textVersionB = textVersionB;
        this.bitmapVersion = bitmapVersion;
        this.videoStreamVersion = videoStreamVersion;
        this.groupVersion = groupVersion;
        this.mediaBitsVersion = mediaBitsVersion;
        this.mediaBitsVersionB = mediaBitsVersionB;
        this.mediaSoundVersion = mediaSoundVersion;
        this.mediaSoundVersionB = mediaSoundVersionB;
        this.mediaVideoVersion = mediaVideoVersion;
        this.mediaVideoVersionB = mediaVideoVersionB;
        this.asLinkageVersion = asLinkageVersion;
        this.libraryFolderVersion = libraryFolderVersion;
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

    public int getDocumentPageVersionB() {
        return documentPageVersionB;
    }

    public int getDocumentPageVersionC() {
        return documentPageVersionC;
    }           

    public int getDocumentPageVersionD() {
        return documentPageVersionD;
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

    public int getSymbolType() {
        return symbolType;
    }

    public int getShapeType() {
        return shapeType;
    }

    public int getFontVersion() {
        return fontVersion;
    }

    public int getFontVersionB() {
        return fontVersionB;
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

    public int getMediaSoundVersionB() {
        return mediaSoundVersionB;
    }        

    public int getMediaVideoVersion() {
        return mediaVideoVersion;
    }

    public int getMediaVideoVersionB() {
        return mediaVideoVersionB;
    }    
    
    public int getAsLinkageVersion() {
        return asLinkageVersion;
    }        

    public int getLibraryFolderVersion() {
        return libraryFolderVersion;
    }        

    public int getGeneratorVersion() {
        return generatorVersion;
    }

    public int getGeneratorBuild() {
        return generatorBuild;
    }

}
