# Binary FLA target constants

`FlaFormatVersion` describes the binary layout selected for a save target. Its
fields now identify serialized records rather than using ambiguous `Type` names
or `VersionB`/`VersionC` suffixes. The ten target enum entries, their order, and
all numeric/boolean values are unchanged.

## Reading the names

- `contentsRevision` is the revision of the `Contents` stream.
- `pic...BaseRevision` selects the inherited `CPicObj` layout. The corresponding
  `pic...Revision` selects the following derived record.
- `bitmapMediaBaseRevision`, `soundMediaBaseRevision` and `videoMediaBaseRevision`
  select the `CMediaElem` descriptor layout, separately from media extensions.
- `picShapeRevision` is the `CPicShape` wrapper revision; `shapeDataRevision` is
  the revision inside the shape data. Neither is a fill or shape type code.
- Library-item metadata, linkage, and auxiliary linkage records have separate
  fields. Javadoc notes where the record's detailed semantics remain unresolved. An `Auxiliary` name does not claim a decoded
  property schema.
- `picVideoStreamRevision` describes an on-stage instance. The separate embedded
  video payload revision is selected by the video converter, not by this field.
- `referenceWriterMajorVersion` and `referenceWriterBuild` describe the application
  that created the reference saves. They are not binary-format revisions.
- `unicodeStrings` describes archive-string encoding in this converter.

Legacy targets can select different base revisions or contain unused constants;
these values have deliberately not been merged or inferred from other fields.

## Field migration

This is a source API rename. Update callers of the public fields and recompile;
the old field aliases are not retained. XFL attribute names such as `symbolType`
and actual movie clip/button/graphic type constants have not changed.

| Previous field | Current field |
|---|---|
| `contentsVersion` | `contentsRevision` |
| `contentsVersionB` | `contentsCompatibilityMarker` |
| `documentPageVersion` | `documentPageRevision` |
| `documentPageVersionB` | `sceneLibraryItemMetadataRevision` |
| `colorDefVersion` | `colorDefRevision` |
| `pageVersion` | `picPageBaseRevision` |
| `pageVersionB` | `picPageRevision` |
| `frameVersion` | `picFrameBaseRevision` |
| `frameVersionB` | `picFrameRevision` |
| `frameVersionC` | `frameActionScriptRevision` |
| `layerVersion` | `picLayerBaseRevision` |
| `layerVersionB` | `picLayerRevision` |
| `spriteVersion` | `picSymbolBaseRevision` |
| `spriteVersionB` | `symbolActionScriptRevision` |
| `spriteVersionC` | `documentPageLinkageRevision` |
| `spriteVersionD` | `documentPageComponentMetadataRevision` |
| `spriteVersionE` | `symbolLibraryItemMetadataRevision` |
| `spriteVersionF` | `documentPageLinkageAuxiliaryRevision` |
| `spriteVersionG` | `picSpriteRevision` |
| `buttonVersion` | `picButtonRevision` |
| `symbolType` | `picSymbolRevision` |
| `shapeType` | `picShapeRevision` |
| `bitmapType` | `picBitmapRevision` |
| `videoType` | `picVideoStreamRevision` |
| `fontVersion` | `fontItemRevision` |
| `fontVersionB` | `fontItemTextStyleRevision` |
| `fontVersionC` | `fontLibraryItemMetadataRevision` |
| `textVersion` | `picTextRevision` |
| `textVersionB` | `textRunStyleRevision` |
| `textVersionC` | `picTextBaseRevision` |
| `bitmapVersion` | `picBitmapBaseRevision` |
| `videoStreamVersion` | `picVideoStreamBaseRevision` |
| `groupVersion` | `picGroupBaseRevision` |
| `mediaBitsVersion` | `bitmapMediaBaseRevision` |
| `mediaBitsVersionB` | `mediaBitsRevision` |
| `mediaBitsVersionC` | `bitmapLibraryItemMetadataRevision` |
| `mediaSoundVersion` | `soundMediaBaseRevision` |
| `mediaSoundVersionB` | `mediaSoundRevision` |
| `mediaSoundVersionC` | `soundLibraryItemMetadataRevision` |
| `mediaVideoVersion` | `videoMediaBaseRevision` |
| `mediaVideoVersionB` | `videoLibraryItemLinkageRevision` |
| `mediaVideoVersionC` | `videoLibraryItemMetadataRevision` |
| `mediaVideoVersionD` | `videoLibraryItemLinkageAuxiliaryRevision` |
| `asLinkageVersion` | `libraryItemLinkageRevision` |
| `asLinkageVersionB` | `libraryItemLinkageAuxiliaryRevision` |
| `libraryFolderVersion` | `folderLibraryItemLinkageRevision` |
| `libraryFolderVersionB` | `libraryFolderRevision` |
| `libraryFolderVersionC` | `folderLibraryItemMetadataRevision` |
| `libraryFolderVersionD` | `folderLibraryItemLinkageAuxiliaryRevision` |
| `accessibilityVersion` | `accessibilityRevision` |
| `shapeVersion` | `shapeDataRevision` |
| `generatorVersion` | `referenceWriterMajorVersion` |
| `generatorBuild` | `referenceWriterBuild` |
| `unicode` | `unicodeStrings` |

## Verification of the rename

The existing and regression suites both before and after the refactor completed
with 229 tests, zero failures and zero skips. All 759 generated stream files
matched byte for byte. The five affected production classes also had identical
bytecode instructions after normalizing renamed field references. No new binary
layout or target-version behavior is introduced by this change.
