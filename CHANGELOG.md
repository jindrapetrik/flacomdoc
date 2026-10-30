# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]
### Added
- Read and preserve Flash Generator `CCommandObj` records in binary FLA conversions,  CS5-CS6 XFL export reports unsupported Generator content explicitly.
- XFL imports preserve editable Flash 5 Smart Clips and MX components, including typed instance parameters; MX and later output also retains component previews and built-in icons.
- XFL imports support compiled SWC component definitions and instances in MX 2004 and later binary FLA output, including primary SWF data, custom icons, recursive dependency metadata, bindings and parameters.

### Fixed
- Flash 5 output preserves native typed Smart Clip parameter lists and their legacy instance layout, fixing crashes when reopening CS6 FLA/XFL conversions in Flash 5.
- Smart Clip defaults and edited instance values, arrays and enumeration selections survive Flash 5 to CS6 export and binary format conversions.
- CFB: Prevent stream truncation caused by float rounding
- CFB: Remove stale trailing data when overwriting an existing file, even if file deletion is denied.
- MX component export to CS5-CS6 supplies minimum version attributes, fixing "Could not load scene into memory. Your document may be damaged."
- XFL: Write component parameters, bindings and scripts as CDATA so Adobe editors load them correctly; prevent doubled script line breaks on Windows.
- Publishing: Preserve AS1/AS2 for older documents without a stored ActionScript version instead of inheriting the AS3 default.
- Compiled SWC components from MX 2004 and later retain target versions, minimum version requirements, CDATA class definitions and bindings, and instance IDs, fixing scene loading and invisible components in CS5-CS6 exports.
- AS2 components no longer disappear from the stage when converting CS6 FLA/XFL documents to MX 2004; their placements and editable instance parameters are preserved.
- MX 2004 output uses its supported compiled-clip record revision, fixing "Unexpected file format" when converting newer SWC components.
- XFL transformation points are converted from local to placed coordinates, preventing enlarged component bounds and distorted previews after binary conversion.
- Reading zip files with wrong END header

## [3.0.0] - 2026-10-24
### Added
- Renamed from FlaComDoc to JPEXS FLA Convert
- One Convert button in the GUI, with all supported target versions and containers selectable in the save dialog.
- Conversion to XFL (CS5, CS5.5, CS6)
- Conversion between binary FLA format versions
- Reports

### Fixed
- Flash 5 output uses the original scene/symbol linkage layout, fixing Unexpected file format when converting newer documents.
- F4 get/set property
- F4 compound operators
- F4 texts
- Morphshapes
- Gradient fills

## [2.0.1] - 2026-04-18
### Fixed
- F1-F4 fonts
- F2-F3 images

## [2.0.0] - 2026-04-18
### Added
- Support for conversion to Flash 1, 2, 3 and 4

### Fixed
- Negative numbers in cubics edges

## [1.1.0] - 2025-04-06
### Added
- Charset settings for `MX` and lower

## [1.0.0] - 2024-11-23
- Initial release
