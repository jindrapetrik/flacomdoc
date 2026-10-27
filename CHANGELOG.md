# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]
### Fixed
- CFB: Prevent stream truncation caused by float rounding
- CFB: Remove stale trailing data when overwriting an existing file, even if file deletion is denied.

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
