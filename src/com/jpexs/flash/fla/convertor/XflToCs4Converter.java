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

    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
        FlaCfbExtractor.initLog();
        File dir = new File(FlaCfbExtractor.getProperty("convert.xfl.dir"));

        File outputFile = new File(FlaCfbExtractor.getProperty("convert.xfl.output.file"));
        File domDocumentFile = dir.toPath().resolve("DOMDocument.xml").toFile();

        File outputContentsFile = new File(FlaCfbExtractor.getProperty("convert.xfl.output.contents.file"));

        PageGenerator pageGenerator = new PageGenerator();
        pageGenerator.generatePageFile(domDocumentFile, outputFile);

        ContentsGenerator contentsGenerator = new ContentsGenerator();
        contentsGenerator.generateContentsFile(domDocumentFile, outputContentsFile);
    }
}
