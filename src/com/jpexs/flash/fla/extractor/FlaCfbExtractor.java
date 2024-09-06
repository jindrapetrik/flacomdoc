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
package com.jpexs.flash.fla.extractor;

import com.jpexs.cfb.CompoundFileBinary;
import com.jpexs.cfb.DirectoryEntry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts FLA files in CompoundFileBinary format to directory
 *
 * @author JPEXS
 */
public class FlaCfbExtractor {

    private static File propFile = new File("./config.properties");
    private static Properties props = null;

    public static String getProperty(String propName) {
        if (props == null) {
            if (!propFile.exists()) {
                System.err.println("config.properties does not exist. Copy config.default.properties to config.properties and continue");
                System.exit(1);
            }
            props = new Properties();
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
            } catch (IOException ex) {
                Logger.getLogger(FlaCfbExtractor.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(1);
            }
        }

        return props.getProperty(propName);
    }

    public static void initLog() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT %1$tL] [%4$-7s] %5$s %n");
        setLoggingLevel(Level.FINE);
    }

    public static void setLoggingLevel(Level targetLevel) {
        Logger root = Logger.getLogger("");
        root.setLevel(targetLevel);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(targetLevel);
        }
    }

    public static void main(String[] args) throws Exception {
        //initLog();
        String inputDir = getProperty("extract.source.dir");
        for (File file : new File(inputDir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".fla");
            }

        })) {
            System.out.println("========== Extracting " + file);
            CompoundFileBinary cfb = new CompoundFileBinary(file);
            String outDir = inputDir + file.getName().replace(".fla", "") + "/";
            new File(outDir).mkdir();
            for (DirectoryEntry de : cfb.getDirectoryEntries()) {
                System.out.println("" + de);
                if (de.objectType == CompoundFileBinary.TYPE_STREAM_OBJECT) {
                    InputStream is = cfb.getEntryStream(de);
                    String outFile = outDir + de.getFilename();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte buf[] = new byte[4096];
                    int cnt;
                    while ((cnt = is.read(buf)) > 0) {
                        fos.write(buf, 0, cnt);
                    }
                    fos.close();
                }
            }
            cfb.close();
        }
    }
}
