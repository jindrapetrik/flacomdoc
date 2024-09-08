# FLA Compound Document Tools

## Goals
This project has following goals:
 * Provide tool for extracting Flash binary FLA format CS4 and lower
 * Allow conversion of XFL FLA format CS5+ to binary FLA format CS4
 * Use this as a library in [JPEXS Free Flash Decompiler](https://github.com/jindrapetrik/jpexs-decompiler) to export to FLA CS4
 * Consider supporting also lower formats than CS4
 
## How to compile
The directory contains Netbeans project which you can open and then build here.
Alternatively you can use `ant` command from commandline.

## How to run

1) You must configure paths in `config.properties` file.
Copy `config.default.properties` to `config.properties` and change paths inside.

2) For extracting FLA CS4 below format, run
```
java -cp flacomdoc.jar com.jpexs.flash.fla.extractor.FlaCfbExtractor
```

3) For converting FLA CS5+ to CS4, run
```
java -cp flacomdoc.jar com.jpexs.flash.fla.convertor.XflToCs4Converter
```

I might add better interface in the future (like commandline arguments instead config file).

## What is missing
See [TODO.md](TODO.md) for known missing features.

## License
This work is licensed under LGPL v2.1, see [LICENSE.md](LICENSE.md) for details.

## Author
Jindra Petřík aka JPEXS