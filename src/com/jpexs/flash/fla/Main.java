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
import com.jpexs.flash.fla.convertor.ContentsGenerator;
import com.jpexs.flash.fla.convertor.streams.CfbOutputStorage;
import com.jpexs.flash.fla.convertor.streams.DirectoryInputStorage;
import com.jpexs.flash.fla.convertor.streams.InputStorageInterface;
import com.jpexs.flash.fla.convertor.streams.OutputStorageInterface;
import com.jpexs.flash.fla.convertor.streams.ZippedInputStorage;
import com.jpexs.flash.fla.gui.Gui;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author JPEXS
 */
public class Main {

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
                System.out.println("java -jar flacomdoc.jar convert inputfile.fla/xfl outputfile.fla");
                System.out.println(" OR ");
                System.out.println("java -jar flacomdoc.jar extract inputfile.fla outputdir");               
                break;
            case "convert": {
                if (args.length != 3) {
                    System.err.println("Invalid arguments for convert.");
                    System.err.println("Usage: java -jar flacomdoc.jar convert inputfile.fla/xfl outputfile.fla");
                    System.exit(1);
                }
                File inputFile = new File(args[0]);
                File outputFile = new File(args[1]);

                try {
                    InputStorageInterface inputStorage;
                    if (inputFile.getAbsolutePath().toLowerCase().endsWith(".xfl")) {
                        inputStorage = new DirectoryInputStorage(inputFile.getParentFile());
                    } else {
                        inputStorage = new ZippedInputStorage(inputFile);
                    }
                    OutputStorageInterface outputStorage = new CfbOutputStorage(outputFile);

                    ContentsGenerator contentsGenerator = new ContentsGenerator();
                    contentsGenerator.generate(inputStorage, outputStorage);
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
                File inputFile = new File(args[0]);
                File outputDir = new File(args[1]);
                
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
