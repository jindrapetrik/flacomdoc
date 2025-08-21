# SWF Version difference

## SWF 1
- Future Splash Animator 1.0 and Macromedia Flash 1.0

### Tags
- PlaceObject (4)
- RemoveObject (5)
- ShowFrame (1)
- SetBackgroundColor (9)
- End (0)
- DefineShape (2)
- DefineBits (6)
- JPEGTables (8)
- DefineFont (10)
- DefineFontInfo (13)
- DefineText (11)
- DefineButton (7)
- DoAction (12)

### Actions
- ActionPlay (0x06)
- ActionStop (0x07)
- ActionNextFrame (0x04)
- ActionPrevFrame (0x05)
- ActionGotoFrame (0x81)
- ActionToggleQuality (0x08)
- ActionGetURL (0x83)

## SWF 2
- Macromedia Flash 2

### Tags
- Protect (24)
- DefineShape2 (22)
    - More styles in stylelists than 255 (up to 65535)    
    - StateNewStyles flag in StyleChangeRecord - allows multiple lists of styles
- DefineBitsJPEG2 (21)
    - Contains both JPEG encoding table and JPEG image data
- DefineSound (14)
- StartSound (15)
- SoundStreamHead (18)
- SoundStreamBlock (19)
- CXFORM field in PlaceObject
- DefineButtonSound (17)
- DefineBitsLossLess (20)
- DefineButtonCxform (23)

### Actions
- ActionStopSounds (0x09)

## SWF 3
- Macromedia Flash 3

### Tags
- PlaceObject2 (26)
    - can both add and modify character
    - color transform with alpha
- RemoveObject2 (28)
    - does not need character id to remove, just depth
- FrameLabel (43)
- DefineShape3 (32)
    - alpha data
- DefineBitsJPEG3 (35)
    - alpha data
- DefineBitsLossless2 (36)
    - alpha data
- DefineMorphShape (46)
- DefineFont2 (48)
    - 32-bit entries in offset table
    - mapping to device fonts
    - font metrics
- DefineText2 (33)
    - alpha channel
- SoundStreamHead2 (45)
    - allows different values for StreamSoundCompression and StreamSoundSize
- DefineButton2 (34)
    - any state transition can trigger action
    - color transform
- DefineSprite (39)

### Actions
- ActionWaitForFrame (0x8A)
- ActionSetTarget (0x8B)
- ActionGoToLabel (0x8C)

## SWF 4
- Macromedia Flash 4

### Tags
- DefineEditText (37)
- key press code in BUTTONCONDACTION in DefineButton2

### Actions
- ActionPush (0x96)
- ActionPop (0x17)
- ActionAdd (0x0A)
- ActionSubtract (0x0B)
- ActionMultiply (0x0C)
- ActionDivide (0x0D)
- ActionEquals (0x0E)
- ActionLess (0x0F)
- ActionAnd (0x10)
- ActionOr (0x11)
- ActionNot (0x12)
- ActionStringEquals (0x13)
- ActionStringLength (0x14)
- ActionStringAdd (0x21)
- ActionStringExtract (0x15)
- ActionStringLess (0x29)
- ActionMBStringLength (0x31)
- ActionMBStringExtract (0x35)
- ActionToInteger (0x18)
- ActionCharToAscii (0x32)
- ActionAsciiToChar (0x33)
- ActionMBCharToAscii (0x36)
- ActionMBAsciiToChar (0x37)
- ActionJump (0x99)
- ActionIf (0x9D)
- ActionCall (0x9E)
- ActionGetVariable (0x1C)
- ActionSetVariable (0x1D)
- ActionGetURL2 (0x9A)
- ActionGotoFrame2 (0x9F)
- ActionSetTarget2 (0x20)
- ActionGetProperty (0x22)
- ActionSetProperty (0x23)
- ActionCloneSprite (0x24)
- ActionRemoveSprite (0x25)
- ActionStartDrag (0x27)
- ActionEndDrag (0x28)
- ActionWaitForFrame2 (0x8D)
- ActionTrace (0x26)
- ActionGetTime (0x34)
- ActionRandomNumber (0x30)

## SWF 5
- Macromedia Flash 5

### Tags
- clip actions to PlaceObject2
- ExportAssets (56)
- ImportAssets (57)
- EnableDebugger (58)

### Actions
- ActionCallFunction (0x3D)
- ActionCallMethod (0x52)
- ActionConstantPool (0x88)
- ActionDefineFunction (0x9B)
- ActionDefineLocal (0x3C)
- ActionDefineLocal2 (0x41)
- ActionDelete (0x3A)
- ActionDelete2 (0x3B)
- ActionEnumerate (0x46)
- ActionEquals2 (0x49)
- ActionGetMember (0x4E)
- ActionInitArray (0x42)
- ActionInitObject (0x43)
- ActionNewMethod (0x53)
- ActionNewObject (0x40)
- ActionSetMember (0x4F)
- ActionTargetPath (0x45)
- ActionWith (0x94)
- ActionToNumber (0x4A)
- ActionToString (0x4B)
- ActionTypeOf (0x44)
- ActionAdd2 (0x47)
    - addition according to datatype
- ActionLess2 (0x48)
    - comparison according to datatype
- ActionModulo (0x3F)
- ActionBitAnd (0x60)
- ActionBitLShift (0x63)
- ActionBitOr (0x61)
- ActionBitRShift (0x64)
- ActionBitURShift (0x65)
- ActionBitXor (0x62)
- ActionDecrement (0x51)
- ActionIncrement (0x50)
- ActionPushDuplicate (0x4C)
- ActionReturn (0x3E)
- ActionStackSwap (0x4D)
- ActionStoreRegister (0x87)
- null, undefined, register, Boolean types,
  double, integer, constant8, constant16 added to ActionPush
- if A is zero, the result NaN, Infinity, or -Infinity is pushed to the stack
  in ActionDivide
- true/false result of ActionEquals, ActionLess, ActionAnd, ActionOr, ActionNot,
  ActionStringEquals, ActionStringLess
- condition is converted to Boolean and tested to true in ActionIf
- _quality, _xmouse and _ymouse properties in ActionGetProperty
  and ActionSetProperty

## SWF 6
- Macromedia Flash MX (6)

### Tags
- ClipEventFlags structure modified
    - added mouseDrag, mouseRollOut, mouseRollOver, mouseReleaseOutside,
      mouseReleaseInside, mousePress, initialize
- EnableDebugger2 (64)
- DefineFontInfo2 (62)
    - langcode
- langcode in DefineFont2
- DefineVideoStream (60)
- VideoFrame (61)
- DoInitAction (59)
- maximum depth for ActionWith is 16

### Actions
- ActionInstanceOf (0x54)
- ActionEnumerate2 (0x55)
    - uses stack argument of object type
- ActionStrictEquals (0x66)
- ActionGreater (0x67)
- ActionStringGreater (0x68)

### Other
- All text requires Unicode encoding

## SWF 7
- Macromedia Flash MX 2004 (7)

### Tags
- ClipEventFlags structure modified
    - added construct, keyPress, mouseDragOut event
- ScriptLimits (65)
- SetTabIndex (66)
- fontFlagsSmallText flag in DefineFontInfo, DefineFontInfo2 and DefineFont2
- screen video in DefineVideoStream

### Actions
- ActionDefineFunction2 (0x8E)
    - 256 registers
    - preloaded variables
- ActionExtends (0x69)
- ActionCastOp (0x2B)
- ActionImplementsOp (0x2C)
- ActionTry (0x8F)
- ActionThrow (0x2A)

## SWF 8
- Macromedia Flash 8

### Tags
- PlaceObject3 (70)
    - class name
    - has image flag
    - cache as bitmap
    - blend modes
    - bitmap filters
- FileAttributes (69)
- Metadata (77)
- ImportAssets2 (71)
- DefineShape4 (83), DefineMorphShape2 (84)
    - (MORPH)LINESTYLE2 (joins, caps, scaling, stroke fills)
    - edge bounds
    - focal gradient
    - spread mode
    - interpolation mode
    - number of gradients > 8 (up to 15)
- DefineFont3 (75)
    - shape coordinates multiplied by 20
- DefineFontAlignZones (73)
- CSMTextSettings (74)
- DefineScalingGrid (78)
- PNG or GIF in DefineBitsJPEG2, DefineBitsJPEG3
- blend mode in ButtonRecord in DefineButton2
- filters in ButtonRecord in DefineButton2
- VP6 and VP6 with alpha in DefineVideoStream

## SWF 9
- Adobe Flash Professional CS3 (9)

### Tags
- SymbolClass (76)
- DoABC (72)
- DoABC2 (82)
    - flags
    - name    
- DefineFontName (88)
- StartSound2 (89)
    - play sound by classname
- EnableTelemetry (93)
- DefineBinaryData (87)
- DefineSceneAndFrameLabelData (86)

## SWF 10
- Adobe Flash Professional CS4 (10)
- Adobe Flash Professional CS5 (11)

### Tags
- DefineBitsJPEG4 (90)
    - deblocking parameter
- DefineFont4 (91)
    - fonts in CFF format

## SWF 11
- Adobe Flash Professional CS5.5 (11.5)

### Tags
- opaque background to PlaceObject3
- visible flag to PlaceObject3

## SWF 11 - FP 11.6
- Adobe Flash Professional CS6 (12)

### Tags
- PlaceObject4 (94)
    - AMF metadata
