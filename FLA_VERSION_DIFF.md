# FLA / XFL version differences

History of changes to editable documents, from FutureSplash Animator to Adobe Animate. Research checked on **2026-10-30**.

## Scope and evidence

- This document covers saved authoring data: document structure, library items, layers, frames, editable geometry, text, scripts, animation parameters, media references and publish settings. A feature is relevant even when publishing it produces no new SWF tag.
- Player-only APIs, SWF instruction/tag inventories, interface redesigns and performance improvements are excluded unless they change saved content or document compatibility. See [SWF_VERSION_DIFF.md](SWF_VERSION_DIFF.md) for the separate SWF history.
- **Format evidence** means a field, revision or representation visible in this repository's implementation or native fixtures. **Authoring change** means a feature documented by the editor vendor; its impact on the document model is described, but an exact binary layout or XML spelling is not implied. Where the storage consequence is inferred, it is identified explicitly.
- Numeric binary revisions below are the repository's save-target profiles, corroborated by the extracted `Contents` streams in [testdata/flang](testdata/flang). They are useful compatibility reference points, not a claim that every patch, platform and historical producer writes identical records.
- An editor release number, a binary `Contents` revision, an individual class revision, an XFL `xflVersion`, and a selected Flash Player/SWF target are different values. Changing one version number alone does not convert a document.

## Container and version reference

- **FutureSplash / Flash 1 through Flash CS4:** the Windows fixtures use CFB/OLE compound documents, identified by `D0 CF 11 E0 A1 B1 1A E1`. They contain a binary `Contents` stream and separate page/symbol/media streams. Page descriptors, page payloads and media payloads have distinct layouts.
- **Flash CS4:** XFL appears as an interchange/import format while normal `.fla` saving remains binary. This must be distinguished from CS5's native XFL-based document format. Adobe's [After Effects CS6 reference](https://helpx.adobe.com/pdf/cs6/after_effects_reference.pdf) describes XFL composition interchange with Flash CS4 and later.
- **Flash CS5 onward:** a normal `.fla` is a ZIP package containing XFL XML and supporting resources. An uncompressed XFL project contains the corresponding directory tree and a small `.xfl` entry file; the entry file alone is not the document. Typical resources include `DOMDocument.xml`, `LIBRARY/*.xml`, `bin/*` and `PublishSettings.xml`. Media and auxiliary data can remain binary. See the project's [XFL container implementation](src/com/jpexs/flash/fla/exporter/XflVersionConverter.java).
- **Animate:** retains the ZIP/directory XFL family. The Flash-to-Animate rename does not introduce another container format. Platform/document type, feature data and producer metadata still matter within that family.

### Binary save-target values

The following values come from [FlaFormatVersion.java](src/com/jpexs/flash/fla/converter/FlaFormatVersion.java). Class columns show their **local extension revisions**, separately from inherited base revisions. All revision values in this table are hexadecimal.

| Editor / target | `Contents` | Compatibility marker | Archive strings used by the target | `CDocumentPage` | `CPicLayer` | `CPicFrame` | `CPicSymbol` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FutureSplash / Flash 1 (`F1`) | `0x10` | `0` | ANSI | `0x03` | `0x02` | `0x04` | `0x02` |
| Flash 2 | `0x11` | `0` | ANSI | `0x03` | `0x02` | `0x08` | `0x04` |
| Flash 3 | `0x14` | `0` | ANSI | `0x04` | `0x03` | `0x10` | `0x06` |
| Flash 4 | `0x1B` | `0` | ANSI | `0x08` | `0x08` | `0x12` | `0x07` |
| Flash 5 | `0x20` | `1` | ANSI | `0x0C` | `0x08` | `0x15` | `0x08` |
| Flash MX (6) | `0x29` | `1` | ANSI | `0x11` | `0x0A` | `0x16` | `0x0A` |
| Flash MX 2004 (7) | `0x38` | `1` | Unicode | `0x16` | `0x0B` | `0x17` | `0x0E` |
| Flash 8 | `0x3F` | `1` | Unicode | `0x17` | `0x0B` | `0x18` | `0x13` |
| Flash CS3 (9) | `0x43` | `1` | Unicode | `0x18` | `0x0B` | `0x1A` | `0x13` |
| Flash CS4 (10) | `0x47` | `1` | Unicode | `0x19` | `0x0D` | `0x1D` | `0x16` |

- The `Contents` number is the first byte of the **extracted stream**, not the first byte of the outer CFB file. `F1` does not mean `Contents = 1`.
- The string column describes general archive strings in these target profiles. Earlier files can contain separately encoded text payloads; Unicode authoring support in Flash MX does not mean every archive string switched to UTF-16 in MX. Unicode archive strings use the archive's Unicode marker and UTF-16LE data. See [FlaWriter.java](src/com/jpexs/flash/fla/converter/FlaWriter.java).
- Each inherited serializer can have its own revision. For example, CS3 and CS4 share some base revisions but have different layer/frame/symbol extension revisions. MFC schema values are another independent mechanism.
- Some later applications retain legacy binary-reader/writer tables with additional compatibility selectors. Those internal entries do not establish that CS5 or CS6 normally saved binary FLA; their native documents belong to the XFL family.

### XFL save-target values

| Editor | Native document family | `DOMDocument/@xflVersion` verified in this project |
| --- | --- | --- |
| Flash CS5 (11.0) | ZIP FLA / uncompressed XFL | `2.0` |
| Flash CS5.5 (11.5) | ZIP FLA / uncompressed XFL | `2.1` |
| Flash CS6 (12.0) | ZIP FLA / uncompressed XFL | `2.2` |
| Flash Professional CC and Animate | ZIP FLA / uncompressed XFL | Per-release native values are not established by this project's CS5–CS6 profiles |

- CS5–CS6 values are defined in [XflFormatVersion.java](src/com/jpexs/flash/fla/exporter/XflFormatVersion.java); the [native CS5 empty document](testdata/fla/cs5/0001_empty_doc/DOMDocument.xml) also records `creatorInfo`, `versionInfo`, `majorVersion` and `buildNumber`.
- Producer stamps describe the saving application/build. They are not interchangeable with the document feature version or publish target. Do not extrapolate later `xflVersion` strings from Animate's marketing year.
- The namespace `http://ns.adobe.com/xfl/2008/` is an XML namespace, not an assertion that a document was created in 2008 or is restricted to CS4 features.

## FutureSplash Animator 1.0 (1996)

- **Format evidence:** editable source documents use `.spa`; the local `F1` reference files have binary `Contents = 0x10`. This is source-document storage, distinct from the published `.spl` movie.
- Establishes the baseline authoring model: scenes/pages, layers, frame spans and keyframes, grouped vector artwork, fills and strokes, imported bitmaps, text, reusable symbols and button states. These structures are present in the [F1 fixture set](testdata/flang/f1).
- Animation stores editable interpolation settings and symbol-instance transforms. The source retains authoring organization that cannot be reconstructed merely from a sequence of rendered frames.
- Frame and button behavior is represented by the early command model, with a small command vocabulary. It should not be treated as a modern free-form ActionScript source block.
- The fixture family has no Flash 2 sound descriptors or Flash 3 independent movie-clip records. Those additions need explicit handling when converting back to this target.

## Macromedia Flash 1.0 (1996)

- Continues the FutureSplash document lineage under the Flash product name and `.fla` source extension.
- This project uses one `F1` compatibility profile for the earliest generation. A separate, fully verified FutureSplash-to-Macromedia-Flash-1 binary delta is **not established here**; the rename or extension change alone is not evidence of a new record layout.
- For this boundary, preserve the actual container, `Contents` value and producer-specific data instead of inferring a format revision from the filename.

## Macromedia Flash 2 (1997)

- **Format evidence:** `Contents = 0x11`; frame extension revision changes from `0x04` to `0x08`, and symbol extension revision from `0x02` to `0x04`.
- Adds sound authoring data: sound library entries, stored media, and frame/button sound assignments with playback settings. The [Flash 2 sound fixture](testdata/flang/f2/0018_sound-f2.fla) demonstrates the new document content.
- Bitmap instances and imported-media descriptors gain dedicated versioned records in the target profile. F1's inline/legacy bitmap representation cannot be replaced with the later media layout without conversion.
- The document remains a binary compound file with ANSI archive strings; neither the new sound data nor the new editor number implies XFL.

## Macromedia Flash 3 (1998)

- **Format evidence:** `Contents = 0x14`; scene/symbol descriptors move to `CDocumentPage` revision `0x04`; layers, frames, symbols and inner shape data also change revisions.
- Adds independent movie-clip symbols, represented by `CPicSprite` in the binary model. Their nested timelines must remain distinct from graphic symbols whose playback follows the parent timeline. See the [Flash 3 movie-clip fixture](testdata/flang/f3/0019_movieclip-f3.fla).
- The generation adds richer animation content including shape morphing, mask relationships and alpha/color effects. The FLA consequences are editable shape correspondence/tween data, relationships between layers, and instance color-transform values rather than only flattened display frames.
- Frame labels and expanded timeline/targeting commands become relevant stored authoring values. Legacy command records still require their own decoder; a later ActionScript text representation is not their original on-disk form.
- Generator-era material also occurs in this period. The repository has a Flash 3 Generator fixture, but does not establish a complete native command grammar for all pre-Flash-4 variants.

## Macromedia Flash 4 (1999)

- **Format evidence:** `Contents = 0x1B`; `CDocumentPage = 0x08`, `CPicLayer = 0x08`, `CPicFrame = 0x12`, and `CPicSymbol = 0x07`.
- Expands editable scripting with variables, expressions, conditionals and loops. Frame/button action records must preserve their command parameters and expression structure, not just a display label for an action.
- Editable text fields introduce document values for variable binding and field behavior, including input/dynamic text settings. These are additional source-text properties beyond static styled text.
- Sound publishing gains MP3-related choices; the FLA-relevant difference is stored sound compression/export configuration and source media, separately from the published audio encoding.
- Library organization and color swatches have explicit records in this target profile. These editor-side structures are useful examples of changes that need not map to a new SWF construct.
- **Format evidence:** native Generator objects store `CCommandObj` data: transform, bounds, command text and, for local revision 1, a display label. The Flash 4 and Flash 5 fixtures both use this layout. Their common object base still changes with the target.

## Macromedia Flash 5 (2000)

- **Format evidence:** `Contents = 0x20`; the compatibility marker becomes `1`. The frame revision becomes `0x15`, and inner shape data becomes revision `5` instead of `4`.
- ActionScript 1 authoring substantially expands saved script/command content. Movie-clip instance handlers and frame scripts are distinct attachment sites; converting only timeline actions loses instance behavior.
- Introduces Smart Clips: reusable movie clips with editable, per-instance parameters. The FLA stores parameter definitions and selected values in addition to the clip's graphics and scripts. Native examples are in [testdata/components](testdata/components); Adobe also identifies Smart Clips as a Flash 5 addition in its [archived troubleshooting reference](https://helpx.adobe.com/archive/flash/flash-professional-cs3-troubleshooting.pdf).
- Shared-library linkage introduces import/export identifiers and external library references as document data. These relationships must survive separately from local library item names.
- Pen-tool editing introduces cubic Bezier authoring information that can coexist with the quadratic geometry used for publishing. Keeping only published edges loses editable handles; Adobe explains this distinction in its [archived drawing discussion](https://helpx.adobe.com/archive/flash/flash-professional-cs5-5-troubleshooting.pdf).
- The target profile includes font library items and extended instance/text metadata. General archive strings remain ANSI; do not infer a document-wide UTF-16 switch from later script or font capabilities.

## Macromedia Flash MX / Flash 6 (2002)

- **Format evidence:** `Contents = 0x29`; `CPicLayer = 0x0A`, `CPicFrame = 0x16`, and `CPicSymbol = 0x0A`. The profile still uses ANSI for general archive strings.
- Adds timeline layer folders. Folder membership, hierarchy and editor expansion/visibility state belong to the source document, independently of the ordering of rendered objects.
- Introduces the first MX UI component generation, extending the Smart Clip model with component definitions, editable parameter values, and library linkage. Preserve the component's authoring metadata as well as any embedded compiled content.
- Video authoring adds video library/media descriptors, video-instance records and linked or embedded video data. A reference video and a stream intended for publishing are different document roles.
- Accessibility authoring introduces named accessibility metadata on supported document objects. The target table includes its own accessibility revision.
- Unicode text authoring changes what text content can be represented, but does not make every binary FLA string Unicode. Text/run payload encoding and archive-name/string encoding must be considered separately; the repository's MX versus MX 2004 profiles demonstrate that distinction.

## Macromedia Flash MX 2004 / Flash 7 (2003; 7.2 update in 2004)

- **Format evidence:** `Contents = 0x38`; the target switches general archive strings to Unicode. Common object base revisions also change, so updating text encoding alone does not produce an MX 2004 file.
- Adds ActionScript 2 authoring settings: the selected language version, class/linkage information and external source/class paths are document or publish-profile data. External `.as` files remain dependencies rather than automatically becoming embedded FLA text.
- Adds named publish profiles. A document can retain multiple sets of export/publish values and an active profile; a converter must not collapse these into one unnamed settings map. Adobe dates the corresponding authoring API to MX 2004 in [Extending Flash CS4](https://help.adobe.com/archive/en_US/flash/cs4/flash_cs4_extending.pdf).
- Flash Professional adds screen-based slide/form documents. The source stores screen hierarchy, screen-specific properties and behavior/component data, not just ordinary scenes. The binary reader recognizes `CPicScreen`.
- Timeline Effects and Behaviors generate animation, symbols and script content inside the document. Their presence can change saved authoring structure even when the result could be published by an older player.
- Components move to the AS2-era component model, with richer parameter/property data and compiled-clip resources. These must not be conflated with Flash 5 Smart Clips or MX components.
- The 7.2 update includes fixes and documentation updates; a separate top-level format revision for every 7.x patch is not established here. See the vendor's [documentation set](https://www.adobe.com/support/documentation/en/flash/documentation-mx.html) and [7.2 release notes](https://www.adobe.com/support/documentation/en/flash/mx2004/releasenotes.html).

## Macromedia Flash 8 (2005)

- **Format evidence:** `Contents = 0x3F`; `CPicFrame = 0x18`, `CPicSymbol = 0x13`, and the common object base used by many record types advances to revision `4`.
- Adds editable filters and blend modes on supported instances/text: blur, drop shadow, glow, bevel, gradient effects and color adjustment. The source stores filter lists, enabled states and parameters, not only their rendered appearance.
- Adds bitmap caching and 9-slice scaling metadata. Cache selection belongs to an instance; the scaling grid belongs to the symbol definition and must not be replaced by an ordinary bounding box.
- Extends strokes with cap, join, miter, scale and hinting choices; gradients gain focal, spread and interpolation choices and support up to 15 stops. Later XFL exposes these concepts through values such as `focalPointRatio`, `spreadMethod` and `interpolationMethod`.
- Adds Object Drawing, which retains independently editable drawing objects instead of merging all overlapping artwork. The distinction affects source grouping/shape structure even when the published picture is identical.
- Adds custom easing curves and improved text anti-aliasing controls, with saved curve data and text rendering parameters. Video import settings expand for newer video/alpha workflows.
- The [vendor's Flash 8 feature list](https://opensource.adobe.com/Spry/demos/products_pe/index.html) documents these authoring additions; exact field spellings for their XFL equivalents can be checked against the project's [XFL schema snapshot](testdata/xfl-schema/xfl.xsd).

## Adobe Flash CS3 Professional / Flash 9 (2007)

- **Format evidence:** `Contents = 0x43`; `CDocumentPage = 0x18`, `CPicFrame = 0x1A`, and the common object base used by many classes becomes revision `5`. `CPicSymbol` itself remains `0x13`, illustrating why one class revision cannot identify the whole file.
- Adds ActionScript 3 document authoring: a document class, AS3 linkage class/base-class values, source paths and AS3 publish settings. AS1/AS2 and AS3 authoring modes have different allowed script attachment sites.
- Introduces editable rectangle and oval primitives. Corner radii, oval start/end angles, inner radius and closure are authoring parameters, distinct from the expanded vector outline. Later XFL represents these as `DOMRectangleObject` and `DOMOvalObject`.
- Copy/paste motion as ActionScript 3 introduces a motion description that can be reused outside the original timeline. This is separate from CS4's later object-based motion-tween model.
- Illustrator and Photoshop integration changes the imported source structure: layer/group organization, editable versus rasterized text, and symbol/bitmap choices. Importing artwork can therefore change the library and hierarchy without changing the outer FLA container.
- See Adobe's [Flash CS3 user guide](https://www.adobe.com/support/documentation/archived_content/en/flash/cs3/flash_cs3_help.pdf) for the new authoring features. The converter's current fallback for primitives is not evidence that the editor first introduced them in CS4.

## Adobe Flash CS4 Professional / Flash 10 (2008)

- **Format evidence:** `Contents = 0x47`; `CPicLayer = 0x0D`, `CPicFrame = 0x1D`, and `CPicSymbol = 0x16`. Native saving remains binary FLA.
- Adds object-based motion tweens alongside the previous tween model, subsequently called **classic tweens**. Motion spans retain property-specific keyframes, paths and easing curves; they must not be reduced to one classic-tween flag.
- Adds 3D authoring transforms and projection/center parameters for supported instances. Later XFL has a `matrix3D` representation; flattening to a six-value 2D affine matrix discards editable 3D information. See the [native 3D fixture](testdata/fla/cs4/0026_3d_rotations-cs4.fla).
- Adds Bone-tool inverse kinematics: armature hierarchy, bone constraints, pose spans, and shape binding/skin data. These are saved rig data in addition to the visible object transforms.
- XFL interchange is introduced, but importing an XFL interchange document is different from making ZIP/XFL the native `.fla` save representation. That native switch follows in CS5.
- Motion presets and procedural drawing workflows can generate motion/geometry content. Preserve the resulting editable data; do not assume every tool or preset requires a separate new top-level document type. Adobe distinguishes the older and newer tween models in [Motion tweens and classic tweens](https://helpx.adobe.com/si/animate/desktop/animation/differences-between-motion-and-classic-tweens.html).

## Adobe Flash Professional CS5 / Flash 11.0 (2010)

- **Format evidence:** the major container transition occurs here: native `.fla` becomes ZIP/XFL and uncompressed XFL becomes a native save choice. `DOMDocument/@xflVersion = "2.0"` in the [native fixtures](testdata/fla/cs5).
- Scene timelines, layers, frames, symbol instances and library definitions become XML resources and references. Symbol definitions live in separate library XML files; preserving the project requires those resources and media, not just `DOMDocument.xml`.
- Adds Text Layout Framework (TLF) authoring alongside classic text. TLF text retains structured flow/paragraph/span markup, layout and linking information. In XFL, `DOMTLFText` and the `http://ns.adobe.com/textLayout/2008` namespace must remain distinct from classic text runs. See Adobe's [TLF documentation](https://helpx.adobe.com/animate/desktop/using/text-layout-framework-tlf-text.html) and the [local TLF schema](testdata/xfl-schema/textLayout.xsd).
- Extends IK with spring-related properties. Rigs and pose animation remain editable authoring data; merely preserving the resulting sampled positions does not preserve the rig.
- Existing CS4-era motion objects, primitives and 3D data now have XFL representations. Their appearance in XML is a storage transition, not their first introduction as editor features.
- Saving a CS4-compatible copy requires actual conversion back to the binary model and may lose newer features. Renaming a ZIP FLA or changing `xflVersion` cannot perform this conversion. See the project's [binary converter](src/com/jpexs/flash/fla/converter/BinaryFlaConverter.java).

## Adobe Flash Professional CS5.5 / Flash 11.5 (2011)

- **Format evidence:** `xflVersion = "2.1"`.
- Adds symbol display/export settings represented by `isVisible`, `exportAsBitmap`, `bits32` and `MatteColor` in the project's [verified conversion rules](src/com/jpexs/flash/fla/exporter/XflVersionRules.java). These are different from an alpha-zero color effect or ordinary runtime `cacheAsBitmap`.
- Adds Bone-tool pinning; XFL rig data can contain `pins` under `IKNode` or `ChildNode`. Removing pins retains some poses but changes editing constraints.
- Adds document autosave settings represented by `autoSaveEnabled` and `autoSaveHasPrompted` in the verified rules.
- Extends TLF authoring with tab-ruler/tab-stop editing and adds project-based sharing of assets between FLA documents during authoring. Tab stops are text-layout values; shared asset relationships are different from merely copying an independent local symbol.
- Stage resizing with content scaling changes saved geometry, transforms and related animation values. Converting selected artwork to a bitmap also changes the library/media and element types; it is different from preserving vector artwork with an export-as-bitmap option.
- Compatibility with CS5 requires feature-aware handling: an invisible instance can become visible, matte settings can disappear, and replacing export-time rasterization with runtime caching changes behavior. These are the explicit limitations tested by [XflVersionConverterTest.java](test/com/jpexs/flash/fla/exporter/XflVersionConverterTest.java). Adobe's [CS6 reference](https://helpx.adobe.com/pdf/cs6/flash_reference.pdf) includes the CS5.5 feature summary.

## Adobe Flash Professional CS6 / Flash 12 (2012)

- **Format evidence:** `xflVersion = "2.2"`; native documents remain ZIP FLA or uncompressed XFL.
- Adds a saved choice of SWF compression algorithm. The source-document consequence is `PublishFlashProperties/CompressionType` in `PublishSettings.xml`, in addition to the existing compression toggle. This chooses published SWF compression; it is not the ZIP compression of the FLA package.
- Adds Direct-mode publishing settings; the verified rules recognize the HTML `WindowMode` value `direct`. AIR publishing also gains deployment/runtime/native-extension choices whose settings and external dependencies matter to the source project.
- Toolkit for CreateJS enables HTML5-oriented exporting from existing Flash documents. It should not be confused with the native HTML5 Canvas document type introduced in the CC generation.
- Sprite-sheet/PNG export is a new output workflow, not by itself proof of a new stage element or FLA container. Include export settings only where they are actually persisted.
- See Adobe's [CS6 feature summary in the official reference](https://helpx.adobe.com/pdf/cs6/flash_reference.pdf). Legacy Generator content is an important compatibility exception: CS6 rejects the repository's native Generator fixture.

## Adobe Flash Professional CC / Flash 13 (2013)

- Retains native XFL-based FLA, but changes the supported authoring model. Opening an old project can convert or remove content before the next save; a successful load does not imply an editable round trip.
- Removes AS1/AS2 authoring support. Old projects are switched to AS3 publishing; scripts attached to instances are removed, and old AS2 components cannot be treated as usable AS3 components.
- Removes TLF authoring: old TLF text is converted to classic static text, losing TLF-specific flow and editing behavior.
- The initial CC release drops the Bone-tool IK workflow and converts old IK animation into frame-by-frame content. This is a historical CC conversion rule, not a statement that later Animate never supports bones; the tool returns in 2015.
- Adds filters, blend modes, color transforms and opaque-background bitmap caching to compiled clip components such as FLVPlayback. This is a distinct compatibility boundary from ordinary symbol-instance display properties in CS5.5: Adobe's Flash team explains that CS5.5/CS6 skip this newer compiled-clip data in its [2013 compatibility answer](https://community.adobe.com/questions-540/can-i-save-flash-pro-cc-down-to-flash-5-5-93247).
- These conversion rules are explicitly described by Adobe in [Opening Flash CS6 files](https://helpx.adobe.com/animate/desktop/multimedia-and-video/opening-cs6-files-cc.html). They affect source-document values, not just the runtime output.
- The 13.1 update adds **native HTML5 Canvas document authoring**. Document/platform identity, JavaScript timeline code and Canvas publish settings become relevant project data. This is broader than CS6's export extension. See Adobe's [2013 update announcement](https://blog.adobe.com/jp/publish/2013/12/16/creative-cloud-update-2013-december).

## Adobe Flash Professional CC 2014 / Flash 14 (2014)

- Adds variable-width strokes and saved width profiles, including shape tweening of such strokes. The source must retain the width description/profile association; a uniform stroke width is insufficient.
- Restores a Motion Editor for property curves. An editor tool returning is not automatically a new tween container, but edits made with it change the saved motion curves and easing values.
- Adds WebGL publishing/document workflows and, in the 2014 update cycle, custom-platform extensibility. Platform identity and platform-specific publish/script data must be preserved even when the shared vector/timeline model is unchanged.
- SVG export alone does not make FLA an SVG document. Imported or edited artwork remains represented through the document's authoring objects.
- Sources: the [2014 release announcement in Adobe's forum](https://community.adobe.com/questions-540/ot-flash-professional-cc-is-now-live-93154), Adobe's [Motion Editor history](https://blog.adobe.com/en/publish/2016/05/25/easing-and-the-motion-editor), [width-profile documentation](https://helpx.adobe.com/animate/desktop/multimedia-and-video/draw-simple-lines-shapes.html) and [custom-platform documentation](https://helpx.adobe.com/animate/desktop/platforms/custom-platform-support.html).

## Adobe Flash Professional CC 2015 / Flash 15.0 (2015)

- Restores the Bone tool and editable IK workflows. Preserve armatures, constraints, poses and binding data rather than applying the original CC fallback unconditionally.
- Adds H.264 video with audio as an authoring/reference workflow. A converter must distinguish reference footage from an embedded video stream intended for publication; the source's media role and reference paths matter.
- Adds a universal document-type converter. Converting between AS3, HTML5 Canvas and other document types can replace unsupported features and rewrite platform-specific document settings; it is not just changing an output filename.
- Improved audio editing, including sound splitting workflows, changes frame assignments and sound ranges. The resulting timeline/sound data matters even without a new audio codec.
- Faster FLA saving and recovery changes do not, on their own, establish a new public container or schema revision. Source: Adobe's [CC 2015 feature summary](https://helpx.adobe.com/pdf/flash_reference.pdf).

## Adobe Animate CC 2015.1 / 2015.2 (2016)

- Renames Flash Professional to **Animate** while retaining XFL-based FLA. The product name and producer metadata change; no new outer document container follows from the rename.
- Adds vector Paint Brush artwork and art/pattern brush workflows. **Inferred document-model consequence:** preserving editability requires brush/stroke information in addition to any expanded outline used for rendering.
- Adds **tagged swatches**, allowing artwork to refer to a reusable document color definition. Replacing each reference with a literal RGB value can preserve one image while losing coordinated color editing. Adobe describes the relationships in [Color Panel](https://helpx.adobe.com/animate/desktop/multimedia-and-video/color.html).
- Adds SVG import and richer library/asset integration. The FLA-relevant result is imported editable artwork, symbols, media and any saved references; the external Creative Cloud library itself is not automatically part of the FLA package.
- Content scaling updates stage/content values and animation geometry. Temporary stage-view rotation is an editor viewing operation and must not be mistaken for rotating saved artwork.
- Source: Adobe's [Animate CC 2015.1 reference](https://helpx.adobe.com/archive/en/animate/cc/2015/animate_reference.pdf), including the February 2016 feature summary. This naming is intentional: the first Animate release shipped in 2016 but carried the 2015.1 label.

## Adobe Animate CC 2017 / 16.x (late 2016–2017)

- Adds a native virtual camera, with a dedicated camera layer and animatable camera values such as position, zoom and rotation. This is authoring data, not merely a viewport zoom preference. See the Adobe-authored [2017 Classroom in a Book sample](https://ptgmedia.pearsoncmg.com/images/9780134665238/samplepages/9780134665238.pdf).
- Adds HTML5 Canvas component authoring. Component identity, properties, instance names and JavaScript dependencies need preservation alongside ordinary symbols; these are not the old AS2 component records.
- Adds further brush capabilities. Preserve editable brush/shape information rather than assuming all new artwork is interchangeable with a classic uniform stroke.
- The June 2017 update adds easing presets for classic/shape tweens and custom easing for shape tweens. Saved easing curves/settings affect interpolation even where the keyframe artwork is unchanged. Adobe documents these in the [2017.5 announcement](https://community.adobe.com/questions-540/adobe-animate-cc-2017-5-is-now-live-99553).
- Component packaging and texture-atlas export are separate distribution/output mechanisms. They do not, by themselves, establish a new FLA element type or container. The [Animate reference](https://helpx.adobe.com/pdf/animate_reference.pdf) covers this release family.

## Adobe Animate CC 2018 / 18.x (2017–2018)

- Adds the advanced-layer/layer-depth workflow and improved camera integration. Depth and camera relationships affect the evaluated positions of artwork; they must be preserved independently of ordinary layer stacking order.
- Advanced-layer use changes how layer animation is represented/evaluated and supports the later layer-effect/parenting workflows. Do not equate an advanced layer with a legacy normal layer merely because both contain familiar frame elements.
- Expands easing presets and time-based timeline editing. Stretching/compressing animation changes frame positions, durations and associated tween data, rather than introducing a separate movie file format.
- The HTML5 Actions Code Wizard generates script/event content attached to the project. The generated actions remain part of the authoring document even if the wizard itself is only a UI feature.
- Source: Adobe's [Animate CC 2018 announcement](https://blog.adobe.com/en/publish/2017/10/18/announcing-adobe-animate-cc-2018) and [timeline layer documentation](https://helpx.adobe.com/animate/desktop/workspace-and-workflow/timeline-layers.html).

## Adobe Animate CC 2019 / 19.x (2018–2019)

- Adds **Asset Warp** for vector and bitmap content. **Inferred document-model consequence:** source artwork, a deformable mesh, handles/pins and animated poses must survive as editable data; replacing them with rendered bitmaps loses the deformation model.
- Adds **layer parenting**: relationships between parent and child layers affect motion. These are different from layer folders and mask-parent relationships.
- Adds **layer effects**, including frame-level filters and tint/color effects. Instance-only filter support is insufficient to preserve such a document.
- Adds automatic lip sync. It places graphic-symbol mouth poses on keyframes using analyzed sound; preserve generated keyframes and symbol frame selections. A separate new universal lip-sync element should not be assumed without native evidence.
- Adds VR document/publishing workflows in beta and texture publishing. Their platform/publish data belongs with the project, while the generated glTF/texture output is a separate artifact.
- Source: Adobe's [Animate CC 2019 announcement](https://blog.adobe.com/en/publish/2018/10/15/announcing-adobe-animate-cc-2019) and [layer-parenting explanation](https://blog.adobe.com/en/publish/2019/06/24/rigging-characters-using-layer-parenting).

## Adobe Animate 2020 / 20.x (2019–2020)

- Adds **Fluid Brush**, a vector drawing tool. The saved result is editable vector artwork; GPU acceleration of drawing does not make it a new video or SWF object. See Adobe's [20.0 announcement](https://blog.adobe.com/en/publish/2019/11/04/adobe-animate-20-0-is-live-now) and [Fluid Brush documentation](https://helpx.adobe.com/animate/desktop/using/fluid-brushes.html).
- Version 20.5 adds automatic keyframe/blank-keyframe insertion. This changes the generated timeline contents and spans; it does not imply a new kind of keyframe. Transforms, filters and color edits can now create keys automatically.
- Adds reusable assets and selective texture-publishing workflows. Source symbols/artwork and publishing rasterization choices remain distinct; preserving generated textures alone does not preserve the editable composition.
- Source: Adobe's [June 2020 announcement](https://blog.adobe.com/en/publish/2020/06/16/adobe-animate-june-2020-available-now). Exact XML names for new settings are not established by this project's CS5–CS6 schema snapshot.

## Adobe Animate 2021 / 21.x (2020–2021)

- Expands reusable rigs and rigged-animation assets, initially in beta. Named rig nodes can be matched automatically or mapped manually to another character; the resulting rig/animation relationships matter to source editing.
- Symbols can be shared through `.ana` asset packages. An ANA package and the user's Assets panel are separate from the current FLA; placing the asset into a composition adds its content to the document. Source: Adobe's [Animate 2021 announcement](https://blog.adobe.com/en/publish/2020/10/20/adobe-animate-2021-available-now).
- Expands graphic-symbol playback, including reverse playback and a selectable last frame for a looping subset in the 21.x release cycle. A converter must retain first/last-frame limits and direction/mode, not assume every graphic loops from its first frame to the end of the symbol timeline. Adobe describes the resulting playback model in [Elements](https://helpx.adobe.com/animate/desktop/using/elements.html).
- Fixes include preservation of variable-width profiles when reopening files and disappearing bitmap-warp objects in classic tweens. These are serialization/round-trip behavior changes, not evidence of a new outer file format. See Adobe's [fixed issues](https://helpx.adobe.com/animate/desktop/troubleshooting/fixed-issues.html).

## Adobe Animate 2022 / 22.x (2021–2022)

- Expands parenting to propagate **scale, skew and flip**, in addition to position and rotation. The propagation choice changes the interpretation of transforms and must be retained. Adobe explicitly dates this change to Animate 2022 in [timeline layers](https://helpx.adobe.com/animate/desktop/workspace-and-workflow/timeline-layers.html).
- Version 22.0.2 adds **modern rigging** to Asset Warp: hard/soft bones, joint controls, frozen joints and configurable mesh density. This is a mesh-deformation workflow, distinct from the older CS4 Bone-tool armature model.
- **Inferred document-model consequence:** preserve bone types, joints, constraints, mesh data and poses across keyframes. Saving only the visible vector outline or bitmap cannot preserve the rig's editing behavior. Adobe describes the features in its [modern-rigging documentation](https://helpx.adobe.com/mx/animate/desktop/animation/character-rigging-in-animate.html); its [release history](https://helpx.adobe.com/uk/animate/using/whats-new.html) identifies the December 2021 update as 22.0.2.
- Later 22.x fixes address parented-object rotation after reopening and parenting structure during keyframe copying. Such fixes can change round-trip correctness while the package family stays the same. See [fixed issues](https://helpx.adobe.com/animate/desktop/troubleshooting/fixed-issues.html).

## Adobe Animate 2023 / 23.x (2022–2023)

- Adds **Flexi Bones**, using Bezier controls for curved bones. **Inferred storage consequence:** a bone needs curve/handle data, not just two joint positions and a hard/soft classification.
- Adds **Envelope Deformer**, with editable outer deformation controls for warped vector or bitmap content. Preserve the deformation controls in addition to source artwork and evaluated poses.
- Adds **Rig Edit Mode**, allowing changes to the base rig to be mapped across existing poses. Rest/base structure and per-pose deformation data must remain distinguishable.
- Makes warped objects manageable through the library, including naming and organization. They must be preserved as referenced authoring assets rather than treated solely as unrelated shapes on each frame.
- These features are described in Adobe's [2023 Classroom in a Book introduction](https://api.pageplace.de/preview/DT0400.9780137982325_A46475299/preview-9780137982325_A46475299.pdf) and the vendor's [October 2022 feature announcement](https://community.adobe.com/animate%E3%83%95%E3%82%A9%E3%83%BC%E3%83%A9%E3%83%A0-136?content_type=productUpdate&lang=ja). Exact XML names and binary-resource layouts are not established by this repository's legacy profiles.
- Version 23.0.2 fixes warped assets appearing blank after saving/reopening when nested in a symbol. This is a source-document persistence fix; see [fixed issues](https://helpx.adobe.com/animate/desktop/troubleshooting/fixed-issues.html).

## Adobe Animate 2024 / 24.x (2023–2026; latest verified public family)

- Version 24.0 adds **Reset Warped Asset**, resetting warp pins to help create new poses. Its source-document effect is a change to existing warp/pose values; Adobe does not describe it as a new outer FLA container.
- Apple Silicon support and the new interface do not, by themselves, establish a new document schema or a different macOS-only FLA package. See Adobe's [October 2023 feature summary](https://helpx.adobe.com/animate/desktop/using/whats-new/2024.html).
- Version 24.0 fixes opening older binary FLAs, and 24.0.1 fixes an opening problem involving bitmap-warped documents. These are relevant compatibility changes even though they do not add a new authoring element.
- As of the research date, Adobe's [release notes](https://helpx.adobe.com/animate/desktop/release-notes.html) still identify the October 2023 / 24.0 generation as the latest feature release; its [fixed-issues page](https://helpx.adobe.com/animate/desktop/troubleshooting/fixed-issues.html) lists maintenance updates through **24.0.15**. No separate public Animate 2025/2026 feature-format generation is established by those sources.
- Adobe states that Animate is in maintenance mode, with security and bug fixes continuing and no new features planned. This describes the current release policy; it does not prove that every future patch will write byte-identical FLA/XFL resources. See the [current vendor statement](https://helpx.adobe.com/animate/desktop/introduction-to-animate/whats-new.html).

## Compatibility implications

- **Preserve authoring data beyond rendered output:** library/folder identity, layer relationships, cubic handles, primitive parameters, easing curves, rigs, text flow and script attachment sites can all be lost while a single published frame still looks correct.
- **Opening is not lossless conversion:** CC's removal of TLF, AS1/AS2 and initial IK support is a concrete example. Later reintroduction of a tool does not reconstruct data already flattened or removed by an earlier save.
- **Newer XFL accepted by an older editor is not full feature support:** Adobe documents forward opening from CS5.5 onward, but an older application can ignore, replace or default unsupported feature/publish data. See [file compatibility](https://helpx.adobe.com/animate/desktop/multimedia-and-video/opening-cs6-files-cc.html) and [publish settings](https://helpx.adobe.com/animate/desktop/exporting-and-publishing/publish-settings.html).
- **Distinguish observed format values from inferred storage requirements:** exact CS5–CS6 feature rules are in [XflVersionRules.java](src/com/jpexs/flash/fla/exporter/XflVersionRules.java). The bundled schema and those profiles do not constitute an exhaustive specification for every later Animate feature.
- **Do not promote implementation limitations into product history:** a missing converter mapping, an unsupported local revision, or a fallback to shapes is evidence about this implementation's coverage, not proof that a historical editor lacked the feature.
