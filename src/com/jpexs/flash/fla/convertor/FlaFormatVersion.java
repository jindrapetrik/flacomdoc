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
    CS3(0x43, 0x18, 0x1A, 0x0B, 0x13, 2, 0xD, 0xC, 6, 3, 6, 6, 10, 544), 
    CS4(0x47, 0x19, 0x1D, 0x0D, 0x16, 3, 0xE, 0xF, 7, 4, 7, 7, 11, 485);
    
    private final int contentsVersion;
    private final int pageVersion;
    private final int frameVersion;
    private final int layerVersion;    
    private final int symbolType;
    private final int fontVersion;
    private final int textVersion;
    private final int textVersionB;
    private final int mediaBitsVersion;
    private final int mediaBitsVersionB;    
    private final int mediaSoundVersion;
    private final int mediaVideoVersion;
    //this is actually one version up since we exported the FLA from newer version to older
    private final int generatorVersion;
    private final int generatorBuild;
    
    FlaFormatVersion(
            int contentsVersion,
            int pageVersion,
            int frameVersion,
            int layerVersion,
            int symbolType,
            int fontVersion,
            int textVersion,
            int textVersionB,
            int mediaBitsVersion,
            int mediaBitsVersionB,
            int mediaSoundVersion,
            int mediaVideoVersion,
            int generatorVersion,
            int generatorBuild            
            ) {
        this.contentsVersion = contentsVersion;
        this.pageVersion = pageVersion;    
        this.frameVersion = frameVersion;
        this.layerVersion = layerVersion;
        this.symbolType = symbolType;
        this.fontVersion = fontVersion;
        this.textVersion = textVersion;
        this.textVersionB = textVersionB;
        this.mediaBitsVersion = mediaBitsVersion;
        this.mediaBitsVersionB = mediaBitsVersionB;
        this.mediaSoundVersion = mediaSoundVersion;
        this.mediaVideoVersion = mediaVideoVersion;
        this.generatorVersion = generatorVersion;
        this.generatorBuild = generatorBuild;
    }

    public int getContentsVersion() {
        return contentsVersion;
    }

    public int getPageVersion() {
        return pageVersion;
    }

    public int getFrameVersion() {
        return frameVersion;
    }  

    public int getLayerVersion() {
        return layerVersion;
    }      

    public int getSymbolType() {
        return symbolType;
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
