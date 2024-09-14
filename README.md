# FLA Compound Document Tools

## Goals
This project has following goals:
 * Provide tool for extracting Flash binary FLA format CS4 and lower
 * Allow conversion of XFL FLA format CS5+ to binary FLA format CS4 or lower
 * Use this as a library in [JPEXS Free Flash Decompiler](https://github.com/jindrapetrik/jpexs-decompiler) to export to FLA CS4 or lower

## How to compile
The directory contains Netbeans project which you can open and then build here.
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
java -jar flacomdoc.jar convert [--format <format>] inputfile.fla/xfl outputfile.fla
```

For `--format` option you can choose `CS4` or `CS3`.


To extract CS4 and lower FLA (ComDoc format):

```
java -jar flacomdoc.jar extract inputfile.fla outputdir
```

## Supported formats
For conversion, target FLA format can be choosen between CS4 and CS3.

For ComDoc extraction, all FLA formats CS4 and lower.


## What is missing
See [TODO.md](TODO.md) for known missing features.

## License
This work is licensed under LGPL v2.1, see [LICENSE.md](LICENSE.md) for details.

## Acknowledges
GUI Application uses FLA icon from [FatCow icons pack].


## Author
Jindra Petřík aka JPEXS


[FatCow icons pack]: http://www.fatcow.com/free-icons