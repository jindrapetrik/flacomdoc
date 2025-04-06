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
package com.jpexs.flash.fla;

import com.jpexs.cfb.CompoundFileBinary;
import com.jpexs.flash.fla.converter.FlaConverter;
import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.streams.CfbOutputStorage;
import com.jpexs.flash.fla.converter.streams.DirectoryInputStorage;
import com.jpexs.flash.fla.converter.streams.InputStorageInterface;
import com.jpexs.flash.fla.converter.streams.OutputStorageInterface;
import com.jpexs.flash.fla.converter.streams.ZippedInputStorage;
import com.jpexs.flash.fla.gui.Gui;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author JPEXS
 */
public class Main {

    private static int parseOptions(
            String args[],
            String definedShortOptions,
            List<String> definedLongOptions,
            Map<String, String> options
    ) {
        int i = 1; //first is command
        for (; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--")) {
                return i + 1;
            }
            if (arg.startsWith("--")) {
                arg = arg.substring(2);
                if (!arg.matches("^[a-z_A-Z0-9]+(=.*)?$")) {
                    throw new IllegalArgumentException("Invalid option: --" + arg);
                }
                if (definedLongOptions.contains(arg)) {
                    options.put(arg, "");
                    continue;
                }
                if (definedLongOptions.contains(arg + ":")) {
                    if (arg.contains("=")) {
                        String key = arg.substring(0, arg.indexOf("="));
                        String value = arg.substring(arg.indexOf("=") + 1);
                        if (value.isEmpty()) {
                            throw new IllegalArgumentException("Option --" + arg + " requires value");
                        }
                        options.put(key, value);
                        continue;
                    }
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Option --" + arg + " requires value");
                    }
                    options.put(arg, args[i + 1]);
                    i++;
                    continue;
                }
                throw new IllegalArgumentException("Unknown option: --" + arg);
            }
            if (arg.startsWith("-")) {
                arg = arg.substring(1);
                if (!arg.matches("^[a-zA-Z0-9]+$")) {
                    throw new IllegalArgumentException("Invalid options: -" + arg);
                }
                for (int j = 0; j < arg.length(); j++) {
                    char opt = arg.charAt(j);
                    if (!definedShortOptions.contains("" + opt)) {
                        throw new IllegalArgumentException("Unknown option: -" + opt);
                    }
                    if (definedShortOptions.contains("" + opt + ":")) {
                        if (j < arg.length() - 1) {
                            throw new IllegalArgumentException("Option -" + opt + " requires value, but it is not last in the combined options");
                        }
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Option -" + arg + " requires value");
                        }
                        options.put("" + opt, args[i + 1]);
                        i++;
                        continue;
                    }
                    options.put("" + opt, "");
                }
                continue;
            }
            return i;
        }
        return i;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            Gui.start();
            return;
        }
        switch (args[0]) {
            case "/?":
            case "--help":
            case "help":
                System.out.println("Usage:");
                System.out.println("java -jar flacomdoc.jar convert [--format <format>] [--charset <charset>] inputfile.fla/xfl outputfile.fla");
                System.out.println(" OR ");
                System.out.println("java -jar flacomdoc.jar extract inputfile.fla outputdir");
                System.out.println();
                System.out.print("Available formats for --format: ");
                boolean first = true;
                for (FlaFormatVersion v : FlaFormatVersion.values()) {
                    if (v.ordinal() >= FlaFormatVersion.F5.ordinal()) {
                        if (!first) {
                            System.out.print(", ");
                        }
                        first = false;
                        System.out.print(v.toString());
                    }
                }
                System.out.println();
                break;
            case "convert": {

                int pos = 1;
                Map<String, String> options = new HashMap<>();
                try {
                    pos = parseOptions(args, "f:c:", Arrays.asList("format:", "charset:"), options);
                } catch (IllegalArgumentException iex) {
                    System.err.println(iex.getMessage());
                    System.exit(1);
                }
                if (options.containsKey("format") && options.containsKey("f")) {
                    System.err.println("Cannot combine --format and -f options");
                    System.exit(1);
                }
                if (options.containsKey("charset") && options.containsKey("c")) {
                    System.err.println("Cannot combine --charset and -c options");
                    System.exit(1);
                }
                if (options.containsKey("f")) {
                    options.put("format", options.get("f"));
                }
                if (options.containsKey("c")) {
                    options.put("charset", options.get("c"));
                }

                String charset = "WINDOWS-1252";
                if (options.containsKey("charset")) {
                    charset = options.get("charset");
                    if (!Charset.isSupported(charset)) {
                        System.err.println("The charset " + charset +" is NOT supported by Java");
                        System.exit(1);
                    }
                }

                FlaFormatVersion flaFormatVersion = FlaFormatVersion.CS4;
                if (options.containsKey("format")) {
                    try {
                        flaFormatVersion = FlaFormatVersion.valueOf(options.get("format"));
                    } catch (IllegalArgumentException iex) {
                        System.err.println("Invalid --format value");
                        System.exit(1);
                    }
                }

                if (pos + 1 >= args.length) {
                    System.err.println("Invalid arguments for convert.");
                    System.err.println("Usage: java -jar flacomdoc.jar convert [--format <format>] inputfile.fla/xfl outputfile.fla");
                    System.exit(1);
                }
                File inputFile = new File(args[pos]);
                File outputFile = new File(args[pos + 1]);

                if (!inputFile.exists()) {
                    System.err.println("Input file does not exists");
                    System.exit(1);
                }
                if (inputFile.isDirectory()) {
                    System.err.println("Input must be a regular file - it is a directory");
                    System.exit(1);
                }
                
                try {
                    InputStorageInterface inputStorage;
                    if (inputFile.getAbsolutePath().toLowerCase().endsWith(".xfl")) {
                        inputStorage = new DirectoryInputStorage(inputFile.getParentFile());
                    } else {
                        inputStorage = new ZippedInputStorage(inputFile);
                    }
                    OutputStorageInterface outputStorage = new CfbOutputStorage(outputFile);

                    FlaConverter contentsGenerator = new FlaConverter(flaFormatVersion, charset);
                    contentsGenerator.convert(inputStorage, outputStorage);
                    inputStorage.close();
                    outputStorage.close();
                    System.out.println("OK");
                } catch (Exception ex) {
                    System.err.println("Error: " + ex.getLocalizedMessage());
                    System.exit(1);
                }
            }
            break;
            case "extract": {
                if (args.length != 3) {
                    System.err.println("Invalid arguments for extract.");
                    System.err.println("Usage: java -jar flacomdoc.jar extract inputfile.fla outputdir");
                    System.exit(1);
                }
                File inputFile = new File(args[1]);
                File outputDir = new File(args[2]);

                if (!inputFile.exists()) {
                    System.err.println("Input file does not exists");
                    System.exit(1);
                }
                if (inputFile.isDirectory()) {
                    System.err.println("Input must be a regular file - it is a directory");
                    System.exit(1);
                }
                
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                if (!outputDir.isDirectory()) {
                    System.err.println("Target is not a directory");
                    System.exit(1);
                }

                try {
                    CompoundFileBinary cfb = new CompoundFileBinary(inputFile);
                    cfb.extractTo("", outputDir);
                    cfb.close();
                } catch (IOException ex) {
                    System.err.println("Error: " + ex.getLocalizedMessage());
                    System.exit(1);
                }
            }
            break;
            default:
                System.err.println("Invalid command");
                System.exit(1);
        }
    }
}
