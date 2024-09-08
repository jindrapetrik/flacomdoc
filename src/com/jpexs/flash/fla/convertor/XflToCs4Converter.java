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
package com.jpexs.flash.fla.convertor;

import com.jpexs.cfb.CompoundFileBinary;
import com.jpexs.flash.fla.extractor.FlaCfbExtractor;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * Parses CS5 XFL file and produces CS4 page file. WIP
 *
 * @author JPEXS
 */
public class XflToCs4Converter {

    /**
     * If true, all random ids are generated as 'X' characters
     */
    private static final boolean DEBUG_RANDOM = true;

    private static void deleteDir(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteDir(c);
            }
        }
        f.delete();
    }

    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
        FlaCfbExtractor.initLog();
        File sourceDir = new File(FlaCfbExtractor.getProperty("convert.xfl.dir"));

        File outputDir = new File(FlaCfbExtractor.getProperty("convert.xfl.output.dir"));
        File domDocumentFile = sourceDir.toPath().resolve("DOMDocument.xml").toFile();
        File publishSettingsFile = sourceDir.toPath().resolve("PublishSettings.xml").toFile();
        File metadataFile = sourceDir.toPath().resolve("META-INF/metadata.xml").toFile();

        if (!publishSettingsFile.exists()) {
            publishSettingsFile = null;
        }
        if (!metadataFile.exists()) {
            metadataFile = null;
        }

        File outputFlaFile = new File(FlaCfbExtractor.getProperty("convert.xfl.output.fla"));

        deleteDir(outputDir);
        outputDir.mkdirs();

        ContentsGenerator contentsGenerator = new ContentsGenerator();
        contentsGenerator.setDebugRandom(DEBUG_RANDOM);
        contentsGenerator.generate(domDocumentFile, publishSettingsFile, metadataFile, sourceDir, outputDir);
        try (CompoundFileBinary cfb = new CompoundFileBinary(outputFlaFile, true)) {
            cfb.addDirectoryContents("", outputDir);
        }
    }
}
