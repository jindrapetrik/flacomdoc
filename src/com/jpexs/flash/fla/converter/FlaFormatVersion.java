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
 * Save-target constants for the binary FLA archive.
 *
 * <p>
 * Each serialized class or nested record has its own revision. Base revisions
 * select the inherited CPicObj/CMediaElem layout; extension revisions select
 * the following derived record. Neither is an XFL symbol type or an MFC class
 * tag. Numeric values and enum ordering also cover legacy targets and are
 * intentional. The detailed semantics of some nested records remain unresolved.
 *
 * @author JPEXS
 */
public enum FlaFormatVersion {

    F1(
            0x10, 0, false, // Contents and archive strings
            3, 0, 0, 0, 0, 0, // Scene and symbol descriptors
            1, 1, 1, 2, 1, 4, 0, // Timeline pages, layers and frames
            1, 1, 3, // Shapes and groups
            1, 2, 0, 0, 0, // Symbols, movie clips and buttons
            1, 0, 0, 0, // Bitmap and video instances
            1, 0, 3, 0, 0, 0, // Text and font library items
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // Media descriptors
            0, 0, // Shared library-item linkage
            0, 0, 0, 0, // Library folders
            0, // Color swatches
            0, // Accessibility
            0, 0 // Reference writer metadata
    ),
    F2(
            0x11, 0, false, // Contents and archive strings
            3, 0, 0, 0, 0, 0, // Scene and symbol descriptors
            1, 1, 1, 2, 1, 8, 0, // Timeline pages, layers and frames
            1, 1, 3, // Shapes and groups
            1, 4, 0, 0, 0, // Symbols, movie clips and buttons
            1, 1, 0, 0, // Bitmap and video instances
            1, 0, 3, 0, 0, 0, // Text and font library items
            3, 0, 2, 3, 5, 0, 0, 0, 0, 0, // Media descriptors
            0, 0, // Shared library-item linkage
            0, 0, 0, 0, // Library folders
            0, // Color swatches
            0, // Accessibility
            0, 0 // Reference writer metadata
    ),
    F3(
            0x14, 0, false, // Contents and archive strings
            4, 0, 0, 0, 0, 0, // Scene and symbol descriptors
            1, 1, 1, 3, 1, 0x10, 0, // Timeline pages, layers and frames
            1, 2, 4, // Shapes and groups
            1, 6, 1, 0, 0, // Symbols, movie clips and buttons
            1, 1, 0, 0, // Bitmap and video instances
            1, 3, 4, 0, 0, 0, // Text and font library items
            3, 0, 2, 3, 5, 0, 0, 0, 0, 0, // Media descriptors
            0, 0, // Shared library-item linkage
            0, 0, 0, 0, // Library folders
            0, // Color swatches
            0, // Accessibility
            0, 0 // Reference writer metadata
    ),
    F4(
            0x1B, 0, false, // Contents and archive strings
            8, 4, 4, 0, 0, 0, // Scene and symbol descriptors
            1, 2, 1, 8, 1, 0x12, 0, // Timeline pages, layers and frames
            1, 2, 4, // Shapes and groups
            1, 7, 1, 0, 0, // Symbols, movie clips and buttons
            1, 1, 0, 0, // Bitmap and video instances
            1, 4, 4, 0, 0, 0, // Text and font library items
            4, 3, 4, 4, 5, 4, 0, 0, 0, 0, // Media descriptors
            0, 0, // Shared library-item linkage
            3, 4, 0, 0, // Library folders
            3, // Color swatches
            0, // Accessibility
            0, 0 // Reference writer metadata
    ),
    F5(
            0x20, 1, false, // Contents and archive strings
            0x0C, 5, 5, 0, 0, 0, // Scene and symbol descriptors
            1, 3, 1, 8, 1, 0x15, 0, // Timeline pages, layers and frames
            1, 2, 5, // Shapes and groups
            1, 8, 3, 5, 0, // Symbols, movie clips and buttons
            1, 1, 1, 0, // Bitmap and video instances
            1, 5, 5, 1, 5, 5, // Text and font library items
            5, 3, 5, 5, 7, 5, 0, 0, 0, 0, // Media descriptors
            0, 0, // Shared library-item linkage
            3, 5, 0, 1, // Library folders
            3, // Color swatches
            0, // Accessibility
            0, 0 // Reference writer metadata
    ),
    MX(
            0x29, 1, false, // Contents and archive strings
            0x11, 5, 5, 3, 1, 2, // Scene and symbol descriptors
            1, 3, 1, 0x0A, 1, 0x16, 1, // Timeline pages, layers and frames
            1, 2, 5, // Shapes and groups
            1, 0x0A, 6, 8, 1, // Symbols, movie clips and buttons
            1, 1, 1, 3, // Bitmap and video instances
            1, 9, 7, 1, 7, 5, // Text and font library items
            5, 3, 5, 5, 8, 5, 5, 5, 3, 1, // Media descriptors
            3, 1, // Shared library-item linkage
            3, 5, 3, 1, // Library folders
            3, // Color swatches
            1, // Accessibility
            0, 0 // Reference writer metadata
    ),
    MX2004(
            0x38, 1, true, // Contents and archive strings
            0x16, 6, 6, 5, 2, 5, // Scene and symbol descriptors
            2, 5, 2, 0x0B, 2, 0x17, 4, // Timeline pages, layers and frames
            2, 2, 5, // Shapes and groups
            2, 0x0E, 8, 0xB, 4, // Symbols, movie clips and buttons
            2, 2, 2, 4, // Bitmap and video instances
            2, 0xC, 0xA, 2, 0xA, 6, // Text and font library items
            6, 3, 6, 6, 9, 6, 6, 6, 5, 2, // Media descriptors
            5, 2, // Shared library-item linkage
            4, 6, 5, 2, // Library folders
            3, // Color swatches
            2, // Accessibility
            0, 0 // Reference writer metadata
    ),
    F8(
            0x3F, 1, true, // Contents and archive strings
            0x17, 6, 6, 7, 2, 6, // Scene and symbol descriptors
            4, 7, 4, 0x0B, 4, 0x18, 4, // Timeline pages, layers and frames
            4, 3, 5, // Shapes and groups
            4, 0x13, 8, 0xB, 4, // Symbols, movie clips and buttons
            4, 2, 4, 4, // Bitmap and video instances
            4, 0xD, 0xC, 2, 0xC, 6, // Text and font library items
            6, 3, 6, 6, 0xA, 6, 6, 6, 7, 2, // Media descriptors
            7, 2, // Shared library-item linkage
            4, 6, 7, 2, // Library folders
            4, // Color swatches
            2, // Accessibility
            9, 494 // Reference writer metadata
    ),
    CS3(
            0x43, 1, true, // Contents and archive strings
            0x18, 6, 6, 7, 2, 7, // Scene and symbol descriptors
            5, 7, 5, 0x0B, 5, 0x1A, 5, // Timeline pages, layers and frames
            5, 6, 5, // Shapes and groups
            5, 0x13, 8, 0xB, 5, // Symbols, movie clips and buttons
            5, 2, 5, 4, // Bitmap and video instances
            5, 0xD, 0xC, 2, 0xC, 6, // Text and font library items
            6, 3, 6, 6, 0xA, 6, 6, 6, 7, 2, // Media descriptors
            7, 2, // Shared library-item linkage
            4, 6, 7, 2, // Library folders
            4, // Color swatches
            2, // Accessibility
            10, 544 // Reference writer metadata
    ),
    CS4(
            0x47, 1, true, // Contents and archive strings
            0x19, 6, 6, 7, 2, 7, // Scene and symbol descriptors
            5, 7, 5, 0x0D, 5, 0x1D, 5, // Timeline pages, layers and frames
            5, 6, 5, // Shapes and groups
            5, 0x16, 8, 0xB, 5, // Symbols, movie clips and buttons
            5, 2, 5, 4, // Bitmap and video instances
            5, 0xE, 0xF, 3, 0xF, 6, // Text and font library items
            7, 4, 6, 7, 0xA, 6, 7, 6, 7, 2, // Media descriptors
            7, 2, // Shared library-item linkage
            4, 6, 7, 2, // Library folders
            4, // Color swatches
            2, // Accessibility
            11, 485 // Reference writer metadata
    );

    // Contents and archive strings
    /**
     * Contents stream format revision (V), independent of class-local
     * revisions.
     */
    public final int contentsRevision;
    /**
     * Contents encoding/compatibility marker; not a record revision.
     */
    public final int contentsCompatibilityMarker;
    /**
     * Whether this target uses Unicode archive strings in the converter.
     */
    public final boolean unicodeStrings;

    // Scene and symbol descriptors
    /**
     * CDocumentPage descriptor revision in Contents, not the page-stream
     * payload.
     */
    public final int documentPageRevision;
    /**
     * Scene descriptor library-item metadata revision.
     */
    public final int sceneLibraryItemMetadataRevision;
    /**
     * Symbol descriptor library-item metadata revision.
     */
    public final int symbolLibraryItemMetadataRevision;
    /**
     * CDocumentPage linkage block revision.
     */
    public final int documentPageLinkageRevision;
    /**
     * Auxiliary record inside document-page linkage; detailed semantics
     * unresolved.
     */
    public final int documentPageLinkageAuxiliaryRevision;
    /**
     * CDocumentPage component metadata revision.
     */
    public final int documentPageComponentMetadataRevision;

    // Timeline pages, layers and frames
    /**
     * CPicObj base revision used by CPicPage.
     */
    public final int picPageBaseRevision;
    /**
     * CPicPage extension revision, after the CPicObj base and its children.
     */
    public final int picPageRevision;
    /**
     * CPicObj base revision used by CPicLayer.
     */
    public final int picLayerBaseRevision;
    /**
     * CPicLayer extension revision.
     */
    public final int picLayerRevision;
    /**
     * CPicObj base revision used by CPicFrame.
     */
    public final int picFrameBaseRevision;
    /**
     * CPicFrame extension revision, after its inherited shape data.
     */
    public final int picFrameRevision;
    /**
     * Frame ActionScript block revision, stored as a dword.
     */
    public final int frameActionScriptRevision;

    // Shapes and groups
    /**
     * CPicObj base revision used by CPicShape groups and floating-shape
     * containers.
     */
    public final int picGroupBaseRevision;
    /**
     * CPicShape wrapper revision; distinct from the inner ShapeData revision.
     */
    public final int picShapeRevision;
    /**
     * Inner ShapeData revision; not the CPicShape wrapper revision.
     */
    public final int shapeDataRevision;

    // Symbols, movie clips and buttons
    /**
     * CPicObj base revision shared by symbol, sprite and button instances.
     */
    public final int picSymbolBaseRevision;
    /**
     * CPicSymbol extension revision; not the movie clip/button/graphic type
     * code.
     */
    public final int picSymbolRevision;
    /**
     * CPicSprite extension revision for movie clips; not the CPicSymbol
     * revision.
     */
    public final int picSpriteRevision;
    /**
     * CPicButton extension revision, after the inherited CPicSymbol fields.
     */
    public final int picButtonRevision;
    /**
     * Sprite/button ActionScript block revision, stored as a dword.
     */
    public final int symbolActionScriptRevision;

    // Bitmap and video instances
    /**
     * CPicObj base revision used by CPicBitmap.
     */
    public final int picBitmapBaseRevision;
    /**
     * CPicBitmap extension revision; not a bitmap type code.
     */
    public final int picBitmapRevision;
    /**
     * CPicObj base revision used by CPicVideoStream instances.
     */
    public final int picVideoStreamBaseRevision;
    /**
     * CPicVideoStream instance revision; not the separate media payload
     * revision.
     */
    public final int picVideoStreamRevision;

    // Text and font library items
    /**
     * Base object revision written before CPicText instance fields.
     */
    public final int picTextBaseRevision;
    /**
     * CPicText extension revision.
     */
    public final int picTextRevision;
    /**
     * Per-text-run style/font-info revision; independent of the CPicText
     * revision.
     */
    public final int textRunStyleRevision;
    /**
     * Font library-item record revision.
     */
    public final int fontItemRevision;
    /**
     * Text-style/font-info record revision embedded in a font library item.
     */
    public final int fontItemTextStyleRevision;
    /**
     * Font library-item metadata revision.
     */
    public final int fontLibraryItemMetadataRevision;

    // Media descriptors
    /**
     * CMediaElem base descriptor revision used by CMediaBits.
     */
    public final int bitmapMediaBaseRevision;
    /**
     * CMediaBits descriptor extension revision; not the bitmap payload
     * revision.
     */
    public final int mediaBitsRevision;
    /**
     * Bitmap library-item metadata revision.
     */
    public final int bitmapLibraryItemMetadataRevision;
    /**
     * CMediaElem base descriptor revision used by CMediaSound.
     */
    public final int soundMediaBaseRevision;
    /**
     * CMediaSound descriptor extension revision.
     */
    public final int mediaSoundRevision;
    /**
     * Sound library-item metadata revision.
     */
    public final int soundLibraryItemMetadataRevision;
    /**
     * CMediaElem descriptor revision used by CMediaVideoStream; not its payload
     * revision.
     */
    public final int videoMediaBaseRevision;
    /**
     * Video library-item metadata revision.
     */
    public final int videoLibraryItemMetadataRevision;
    /**
     * Video library-item linkage revision.
     */
    public final int videoLibraryItemLinkageRevision;
    /**
     * Auxiliary record inside video linkage; detailed semantics unresolved.
     */
    public final int videoLibraryItemLinkageAuxiliaryRevision;

    // Shared library-item linkage
    /**
     * Library-item linkage revision, used by writeAsLinkage.
     */
    public final int libraryItemLinkageRevision;
    /**
     * Auxiliary record inside library-item linkage; detailed semantics
     * unresolved.
     */
    public final int libraryItemLinkageAuxiliaryRevision;

    // Library folders
    /**
     * Library-folder record revision, preceding its name and library-item
     * metadata.
     */
    public final int libraryFolderRevision;
    /**
     * Folder library-item metadata revision.
     */
    public final int folderLibraryItemMetadataRevision;
    /**
     * Folder library-item linkage revision.
     */
    public final int folderLibraryItemLinkageRevision;
    /**
     * Auxiliary record inside folder linkage; detailed semantics unresolved.
     */
    public final int folderLibraryItemLinkageAuxiliaryRevision;

    // Color swatches
    /**
     * CColorDef swatch record revision.
     */
    public final int colorDefRevision;

    // Accessibility
    /**
     * Accessibility metadata revision, written as a word.
     */
    public final int accessibilityRevision;

    // Reference writer metadata
    /**
     * Major version of the application that produced the reference saves, not
     * the target format.
     */
    public final int referenceWriterMajorVersion;
    /**
     * Build number of the application that produced the reference saves.
     */
    public final int referenceWriterBuild;

    FlaFormatVersion(
            // Contents and archive strings
            int contentsRevision,
            int contentsCompatibilityMarker,
            boolean unicodeStrings,
            // Scene and symbol descriptors
            int documentPageRevision,
            int sceneLibraryItemMetadataRevision,
            int symbolLibraryItemMetadataRevision,
            int documentPageLinkageRevision,
            int documentPageLinkageAuxiliaryRevision,
            int documentPageComponentMetadataRevision,
            // Timeline pages, layers and frames
            int picPageBaseRevision,
            int picPageRevision,
            int picLayerBaseRevision,
            int picLayerRevision,
            int picFrameBaseRevision,
            int picFrameRevision,
            int frameActionScriptRevision,
            // Shapes and groups
            int picGroupBaseRevision,
            int picShapeRevision,
            int shapeDataRevision,
            // Symbols, movie clips and buttons
            int picSymbolBaseRevision,
            int picSymbolRevision,
            int picSpriteRevision,
            int picButtonRevision,
            int symbolActionScriptRevision,
            // Bitmap and video instances
            int picBitmapBaseRevision,
            int picBitmapRevision,
            int picVideoStreamBaseRevision,
            int picVideoStreamRevision,
            // Text and font library items
            int picTextBaseRevision,
            int picTextRevision,
            int textRunStyleRevision,
            int fontItemRevision,
            int fontItemTextStyleRevision,
            int fontLibraryItemMetadataRevision,
            // Media descriptors
            int bitmapMediaBaseRevision,
            int mediaBitsRevision,
            int bitmapLibraryItemMetadataRevision,
            int soundMediaBaseRevision,
            int mediaSoundRevision,
            int soundLibraryItemMetadataRevision,
            int videoMediaBaseRevision,
            int videoLibraryItemMetadataRevision,
            int videoLibraryItemLinkageRevision,
            int videoLibraryItemLinkageAuxiliaryRevision,
            // Shared library-item linkage
            int libraryItemLinkageRevision,
            int libraryItemLinkageAuxiliaryRevision,
            // Library folders
            int libraryFolderRevision,
            int folderLibraryItemMetadataRevision,
            int folderLibraryItemLinkageRevision,
            int folderLibraryItemLinkageAuxiliaryRevision,
            // Color swatches
            int colorDefRevision,
            // Accessibility
            int accessibilityRevision,
            // Reference writer metadata
            int referenceWriterMajorVersion,
            int referenceWriterBuild
    ) {
        this.contentsRevision = contentsRevision;
        this.contentsCompatibilityMarker = contentsCompatibilityMarker;
        this.unicodeStrings = unicodeStrings;

        this.documentPageRevision = documentPageRevision;
        this.sceneLibraryItemMetadataRevision = sceneLibraryItemMetadataRevision;
        this.symbolLibraryItemMetadataRevision = symbolLibraryItemMetadataRevision;
        this.documentPageLinkageRevision = documentPageLinkageRevision;
        this.documentPageLinkageAuxiliaryRevision = documentPageLinkageAuxiliaryRevision;
        this.documentPageComponentMetadataRevision = documentPageComponentMetadataRevision;

        this.picPageBaseRevision = picPageBaseRevision;
        this.picPageRevision = picPageRevision;
        this.picLayerBaseRevision = picLayerBaseRevision;
        this.picLayerRevision = picLayerRevision;
        this.picFrameBaseRevision = picFrameBaseRevision;
        this.picFrameRevision = picFrameRevision;
        this.frameActionScriptRevision = frameActionScriptRevision;

        this.picGroupBaseRevision = picGroupBaseRevision;
        this.picShapeRevision = picShapeRevision;
        this.shapeDataRevision = shapeDataRevision;

        this.picSymbolBaseRevision = picSymbolBaseRevision;
        this.picSymbolRevision = picSymbolRevision;
        this.picSpriteRevision = picSpriteRevision;
        this.picButtonRevision = picButtonRevision;
        this.symbolActionScriptRevision = symbolActionScriptRevision;

        this.picBitmapBaseRevision = picBitmapBaseRevision;
        this.picBitmapRevision = picBitmapRevision;
        this.picVideoStreamBaseRevision = picVideoStreamBaseRevision;
        this.picVideoStreamRevision = picVideoStreamRevision;

        this.picTextBaseRevision = picTextBaseRevision;
        this.picTextRevision = picTextRevision;
        this.textRunStyleRevision = textRunStyleRevision;
        this.fontItemRevision = fontItemRevision;
        this.fontItemTextStyleRevision = fontItemTextStyleRevision;
        this.fontLibraryItemMetadataRevision = fontLibraryItemMetadataRevision;

        this.bitmapMediaBaseRevision = bitmapMediaBaseRevision;
        this.mediaBitsRevision = mediaBitsRevision;
        this.bitmapLibraryItemMetadataRevision = bitmapLibraryItemMetadataRevision;
        this.soundMediaBaseRevision = soundMediaBaseRevision;
        this.mediaSoundRevision = mediaSoundRevision;
        this.soundLibraryItemMetadataRevision = soundLibraryItemMetadataRevision;
        this.videoMediaBaseRevision = videoMediaBaseRevision;
        this.videoLibraryItemMetadataRevision = videoLibraryItemMetadataRevision;
        this.videoLibraryItemLinkageRevision = videoLibraryItemLinkageRevision;
        this.videoLibraryItemLinkageAuxiliaryRevision = videoLibraryItemLinkageAuxiliaryRevision;

        this.libraryItemLinkageRevision = libraryItemLinkageRevision;
        this.libraryItemLinkageAuxiliaryRevision = libraryItemLinkageAuxiliaryRevision;

        this.libraryFolderRevision = libraryFolderRevision;
        this.folderLibraryItemMetadataRevision = folderLibraryItemMetadataRevision;
        this.folderLibraryItemLinkageRevision = folderLibraryItemLinkageRevision;
        this.folderLibraryItemLinkageAuxiliaryRevision = folderLibraryItemLinkageAuxiliaryRevision;

        this.colorDefRevision = colorDefRevision;

        this.accessibilityRevision = accessibilityRevision;

        this.referenceWriterMajorVersion = referenceWriterMajorVersion;
        this.referenceWriterBuild = referenceWriterBuild;
    }
}
