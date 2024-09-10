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

import com.jpexs.flash.fla.convertor.streams.DirectoryInputStorage;
import com.jpexs.flash.fla.convertor.streams.DirectoryOutputStorage;
import com.jpexs.flash.fla.extractor.FlaCfbExtractor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.DataProvider;

/**
 *
 * @author JPEXS
 */
public class XlfToCs4ConverterTest {

    private static final String SOURCE_DIR = "testdata/fla/cs5";
    private static final String EXPECTED_DIR = "testdata/fla/cs4";
    private static final String OUTPUT_DIR = "out/tests/fla/cs4";

    private Comparator<File> getFileComparator() {
        return new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };
    }

    @DataProvider(name = "folders")
    public Object[][] provideFolders() {
        File sourceDir = new File(SOURCE_DIR);
        File[] sourceFiles = sourceDir.listFiles();
        Comparator<File> fileNameComparator = getFileComparator();
        List<File> sourceFilesList = Arrays.asList(sourceFiles);
        sourceFilesList.sort(fileNameComparator);

        Object[][] ret = new Object[sourceFilesList.size()][];
        for (int i = 0; i < sourceFilesList.size(); i++) {
            ret[i] = new Object[]{sourceFilesList.get(i).getName()};
        }
        System.err.println("rl " + ret.length);
        return ret;
    }

    @Test(dataProvider = "folders")
    public void testConvert(String folderName) throws Exception {

        File actualDir = new File(OUTPUT_DIR + "/" + folderName);
        deleteDir(actualDir);
        if (!actualDir.exists()) {
            actualDir.mkdirs();
        }
        ContentsGenerator contentsGenerator = new ContentsGenerator();
        contentsGenerator.setDebugRandom(true);
        contentsGenerator.generate(new DirectoryInputStorage(new File(SOURCE_DIR + "/" + folderName)),
                new DirectoryOutputStorage(actualDir)
        );

        File expectedDir = new File(EXPECTED_DIR + "/" + folderName);

        Comparator<File> fileNameComparator = getFileComparator();

        File[] expectedFiles = expectedDir.listFiles();
        File[] actualFiles = actualDir.listFiles();
        assertEquals(actualFiles.length, expectedFiles.length, "Number of files");

        List<File> expectedFilesList = Arrays.asList(expectedFiles);
        expectedFilesList.sort(fileNameComparator);

        List<File> actualFilesList = Arrays.asList(actualFiles);
        actualFilesList.sort(fileNameComparator);

        for (int i = 0; i < actualFilesList.size(); i++) {
            File actualFile = actualFilesList.get(i);
            File expectedFile = expectedFilesList.get(i);

            String actualFileName = actualFile.getName();
            String expectedFileName = expectedFile.getName();
            if (actualFileName.equals(expectedFileName)) { //Like "Contents" file
                continue;
            }
            assertTrue(actualFileName.contains(" "), "Filename does not contain a space");
            String actualType = actualFileName.substring(0, actualFileName.indexOf(" "));
            String expectedType = expectedFileName.substring(0, expectedFileName.indexOf(" "));

            assertEquals(actualType, expectedType, "File type");
        }

        for (int i = 0; i < actualFilesList.size(); i++) {
            File actualFile = actualFilesList.get(i);
            File expectedFile = expectedFilesList.get(i);

            byte[] actualData = readFile(actualFile);
            byte[] expectedData = readFile(expectedFile);

            //assertEquals(actualData.length, expectedData.length, "File data length of file " + actualFile);
            int epos = 0;
            for (int apos = 0; apos < actualData.length; apos++, epos++) {
                if (actualData[apos] == 'X') { //special - all randomness is replaced with 'X' - setDebugRandom(true)
                    continue;
                }
                if (actualData[apos] == 'U') { //unknown data - also when setDebugRandom(true)
                    continue;
                }
                if (apos - 3 > 0 && apos + 6 < actualData.length && actualData[apos] == 3
                        && actualData[apos + 1] == 'Y'
                        && actualData[apos + 2] == 0
                        && actualData[apos + 3] == 'Y'
                        && actualData[apos + 4] == 0
                        && actualData[apos + 5] == 'Y'
                        && actualData[apos + 6] == 0) {
                    if ((actualData[apos - 3] & 0xFF) == 0xFF && (actualData[apos - 2] & 0xFF) == 0xFE && (actualData[apos - 1] & 0xFF) == 0xFF) {
                        apos += 6;
                        int len = expectedData[epos] & 0xFF;
                        if (len == 0xFF) {
                            len = (expectedData[epos + 1] & 0xFF) + ((expectedData[epos + 2] & 0xFF) << 8);
                            if (len == 0xFFFF) {
                                len = (expectedData[epos + 3] & 0xFF)
                                        + ((expectedData[epos + 4] & 0xFF) << 8)
                                        + ((expectedData[epos + 5] & 0xFF) << 16)
                                        + ((expectedData[epos + 6] & 0xFF) << 24);
                                epos = epos + 7 + len * 2 - 1;
                                continue;
                            }
                            epos = epos + 3 + len * 2 - 1;
                            continue;
                        }
                        epos = epos + 1 + len * 2 - 1;
                        continue;
                    }
                }
                assertEquals(actualData[apos] & 0xFF, expectedData[epos] & 0xFF, "Byte in file " + actualFile + " on position apos=" + apos + ", epos=" + epos);
            }
        }
    }

    //@Test
    public void mytest() throws Exception {
        //testConvert("0005_strokes");
    }

    private static void deleteDir(File f) throws IOException {
        if (!f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteDir(c);
            }
        }
        f.delete();
    }

    private byte[] readFile(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            int cnt;
            while ((cnt = fis.read(buf)) > 0) {
                baos.write(buf, 0, cnt);
            }
        }
        return baos.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        File outputDir = new File(EXPECTED_DIR);
        for (File f : outputDir.listFiles()) {
            if (f.isDirectory()) {
                deleteDir(f);
            }
        }
        FlaCfbExtractor.main(new String[0]);
    }
}
