# JPEXS FLA Convert
![build status](https://github.com/jindrapetrik/jpexs-fla-convert/actions/workflows/main.yml/badge.svg)

## Goals
This project has following goals:
 * Provide tool for extracting Flash binary FLA format CS4 and lower
 * Allow conversion of XFL FLA format CS5+ to binary FLA format CS4 or lower
 * Read legacy binary FLA into an internal document model and progressively export it to XFL
 * Convert directly between binary FLA/SPA versions without an XFL intermediate
 * Use this as a library in [JPEXS Free Flash Decompiler] to export to FLA CS4 or lower

## Requirements
The app requires Java 8 or newer to run.

## Building from source code
Source code is available on GitHub, you can get it via following command

```
git clone https://github.com/jindrapetrik/jpexs-fla-convert.git
```

The source directory contains Netbeans project which you can open and then build here.
Alternatively you can use `ant` command from commandline.

## GUI usage

You can run the app without arguments:
```
java -jar fla_convert.jar
```
And graphics user interface (GUI) will show up where you can do the conversion and extraction.

Click **Convert...**, select the source FLA/SPA/XFL file or XFL project directory,
then choose the destination version from **Files of type** in the save dialog.
CS5–CS6 offer compressed FLA and uncompressed XFL directory types. The correct
`.fla` or `.spa` extension is added automatically.


## Commandline usage

To convert between FLA/XFL: versions:
```
java -jar fla_convert.jar convert [--format <format>] [--charset <charset>] [--container fla|zip|xfl] inputfile.fla/xfl output
```

For `--format` option you can choose: 
`CS6`, `CS5_5`, `CS5`, `CS4`, `CS3`, `F8`, `MX2004`, `MX`, `F5`, `F4`, `F3`, `F2` or `F1`.

`CS5`, `CS5_5` and `CS6` produce compressed ZIP FLA by default; use `--container xfl`
to create a new uncompressed XFL directory. `--container zip` explicitly selects
compressed FLA. Binary targets F1–CS4 use the default `fla` container only.

The charset setting selects the encoding of legacy ANSI strings and defaults to `WINDOWS-1252`.

To extract CS4 and lower FLA (ComDoc format):

```
java -jar fla_convert.jar extract inputfile.fla outputdir
```

Tests validate generated XML directly against an offline working-tree snapshot of
[xfl-schema](https://github.com/jindrapetrik/xfl-schema), including textLayout.xsd.

## Supported formats
For conversion, following target formats are available:
 * CS6
 * CS5.5
 * CS5
 * CS4
 * CS3
 * Flash 8
 * MX 2004
 * MX
 * Flash 5
 * Flash 4
 * Flash 3
 * Flash 2
 * Flash 1

Direct binary conversion supports these ten binary targets. CS5, CS5.5 and CS6
are separate XFL targets with ZIP/directory output; they are not binary profiles.

For ComDoc extraction, binary compound documents are supported (Flash 1 to CS4.

## License
This work is licensed under LGPL v2.1, see [LICENSE.md](LICENSE.md) for details.

## Acknowledges
GUI Application uses FLA icon from [FatCow icons pack].
We also use icons of Macromedia / Adobe icons.

## Author
Jindra Petřík aka JPEXS

[JPEXS Free Flash Decompiler]: https://github.com/jindrapetrik/jpexs-decompiler
[FatCow icons pack]: http://www.fatcow.com/free-icons
