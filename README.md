# FLA Compound Document Tools
![build status](https://github.com/jindrapetrik/flacomdoc/actions/workflows/main.yml/badge.svg)

## Goals
This project has following goals:
 * Provide tool for extracting Flash binary FLA format CS4 and lower
 * Allow conversion of XFL FLA format CS5+ to binary FLA format CS4 or lower
 * Use this as a library in [JPEXS Free Flash Decompiler] to export to FLA CS4 or lower

## Requirements
The app requires Java 8 or newer to run.

## Building from source code
Source code is available on GitHub, you can get it via following command

```
git clone https://github.com/jindrapetrik/flacomdoc.git
```

The source directory contains Netbeans project which you can open and then build here.
Alternatively you can use `ant` command from commandline.

## GUI usage

You can run the app without arguments:
```
java -jar flacomdoc.jar
```
And graphics user interface (GUI) will show up where you can do the conversion and extraction.

## Commandline usage

To convert CS5+ FLA/XFL to lower:
```
java -jar flacomdoc.jar convert [--format <format>] [--charset <charset>] inputfile.fla/xfl outputfile.fla
```

For `--format` option you can choose: `CS4`, `CS3`, `F8`, `MX2004`, `MX` or `F5`.

Charset setting is applicable for `MX` and lower formats and defaults to `WINDOWS-1252`.

To extract CS4 and lower FLA (ComDoc format):

```
java -jar flacomdoc.jar extract inputfile.fla outputdir
```

## Supported formats
For conversion, following target formats are available:
 * CS4
 * CS3
 * Flash 8
 * MX 2004
 * MX
 * Flash 5

For ComDoc extraction, all FLA formats CS4 and lower.


## What is known to be missing
 * Compiled clips
 * Components
 * Inverse Kinematics
 * Support for FLA below Flash 5

## License
This work is licensed under LGPL v2.1, see [LICENSE.md](LICENSE.md) for details.

## Acknowledges
GUI Application uses FLA icon from [FatCow icons pack].


## Author
Jindra Petřík aka JPEXS

[JPEXS Free Flash Decompiler]: https://github.com/jindrapetrik/jpexs-decompiler
[FatCow icons pack]: http://www.fatcow.com/free-icons