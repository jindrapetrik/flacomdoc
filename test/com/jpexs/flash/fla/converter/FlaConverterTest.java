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
package com.jpexs.flash.fla.converter;

import com.jpexs.flash.fla.converter.debug.EdgeReader;
import com.jpexs.flash.fla.converter.streams.DirectoryInputStorage;
import com.jpexs.flash.fla.converter.streams.DirectoryOutputStorage;
import com.jpexs.flash.fla.extractor.FlaCfbExtractor;
import com.jpexs.helpers.Reference;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.DataProvider;
import org.xml.sax.SAXException;

/**
 *
 * @author JPEXS
 */
public class FlaConverterTest {

    private static final String ADD = "";
    
    private static final String SOURCE_DIR = "testdata/fla" + ADD + "/cs5";

    private static final String EXPECTED_BASE_DIR = "testdata/fla" + ADD;

    private static final String OUTPUT_BASE_DIR = "out/tests/fla" + ADD;

    private Comparator<File> getFileComparator() {
        return new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };
    }

    @DataProvider(name = "folders-cs4")
    public Object[][] provideFoldersCs4() {
        return provideFolders(FlaFormatVersion.CS4);
    }

    @DataProvider(name = "folders-cs3")
    public Object[][] provideFoldersCs3() {
        return provideFolders(FlaFormatVersion.CS3);
    }

    @DataProvider(name = "folders-f8")
    public Object[][] provideFoldersF8() {
        return provideFolders(FlaFormatVersion.F8);
    }

    @DataProvider(name = "folders-mx2004")
    public Object[][] provideFoldersMx2004() {
        return provideFolders(FlaFormatVersion.MX2004);
    }

    @DataProvider(name = "folders-mx")
    public Object[][] provideFoldersMx() {
        return provideFolders(FlaFormatVersion.MX);
    }

    @DataProvider(name = "folders-f5")
    public Object[][] provideFoldersF5() {
        return provideFolders(FlaFormatVersion.F5);
    }
    
    @DataProvider(name = "folders-f4")
    public Object[][] provideFoldersF4() {
        return provideFolders(FlaFormatVersion.F4);
    }

    @DataProvider(name = "folders-f3")
    public Object[][] provideFoldersF3() {
        return provideFolders(FlaFormatVersion.F3);
    }
    
    @DataProvider(name = "folders-f2")
    public Object[][] provideFoldersF2() {
        return provideFolders(FlaFormatVersion.F2);
    }
    
    @DataProvider(name = "folders-f1")
    public Object[][] provideFoldersF1() {
        return provideFolders(FlaFormatVersion.F1);
    }



    private Object[][] provideFolders(FlaFormatVersion flaFormatVersion) {
        File sourceDir = new File(EXPECTED_BASE_DIR + "/" + flaFormatVersion.name().toLowerCase());
        File[] sourceFiles = sourceDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });
        Comparator<File> fileNameComparator = getFileComparator();
        List<File> sourceFilesList = new ArrayList<>(Arrays.asList(sourceFiles));
        sourceFilesList.sort(fileNameComparator);

        for (int i = sourceFilesList.size() - 1; i >= 0; i--) {
            String name = sourceFilesList.get(i).getName();
            if (name.endsWith("-todo")) {
                sourceFilesList.remove(i);
                continue;
            }
            if (name.contains("-")) {
                String suffix = name.substring(name.indexOf("-") + 1);
                FlaFormatVersion lowestFlaVersion = FlaFormatVersion.valueOf(suffix.toUpperCase());
                if (flaFormatVersion.ordinal() < lowestFlaVersion.ordinal()) {
                    sourceFilesList.remove(i);
                }
            }
        }

        Object[][] ret = new Object[sourceFilesList.size()][];
        for (int i = 0; i < sourceFilesList.size(); i++) {
            ret[i] = new Object[]{sourceFilesList.get(i).getName()};
        }
        return ret;
    }

    private void convert(String folderName, FlaFormatVersion flaFormatVersion) throws Exception {

        String outputDirParent = OUTPUT_BASE_DIR + "/" + flaFormatVersion.name().toLowerCase();
        String expectedDirParent = EXPECTED_BASE_DIR + "/" + flaFormatVersion.name().toLowerCase();

        File actualDir = new File(outputDirParent + "/" + folderName);
        deleteDir(actualDir);
        if (!actualDir.exists()) {
            actualDir.mkdirs();
        }
        FlaConverter contentsGenerator = new FlaConverter(flaFormatVersion, "WINDOWS-1250");
        contentsGenerator.setDebugRandom(true);

        contentsGenerator.convert(new DirectoryInputStorage(new File(SOURCE_DIR + "/" + folderName)),
                new DirectoryOutputStorage(actualDir)
        );

        /*contentsGenerator.setDebugRandom(false);        
        try(CfbOutputStorage cfb = new CfbOutputStorage(new File(outputDirParent + "/" + folderName + ".fla"))) {
            contentsGenerator.convert(new DirectoryInputStorage(new File(SOURCE_DIR + "/" + folderName)),
                    cfb
            );
        }*/
        File expectedDir = new File(expectedDirParent + "/" + folderName);

        Comparator<File> fileNameComparator = getFileComparator();

        File[] expectedFiles = expectedDir.listFiles();
        File[] actualFiles = actualDir.listFiles();

        List<File> expectedFilesList = new ArrayList<>(Arrays.asList(expectedFiles));
        expectedFilesList.sort(fileNameComparator);

        List<File> actualFilesList = new ArrayList<>(Arrays.asList(actualFiles));
        actualFilesList.sort(fileNameComparator);

        //Ignore media and symbol files
        for (int i = actualFilesList.size() - 1; i >= 0; i--) {
            File actualFile = actualFilesList.get(i);
            if (actualFile.getName().startsWith("M ")
                    || actualFile.getName().startsWith("S ")) {
                actualFilesList.remove(i);
            }
        }

        for (int i = expectedFilesList.size() - 1; i >= 0; i--) {
            File expectedFile = expectedFilesList.get(i);
            if (expectedFile.getName().startsWith("M ")
                    || expectedFile.getName().startsWith("S ")) {
                expectedFilesList.remove(i);
            }
        }

        assertEquals(actualFilesList.size(), expectedFilesList.size(), "Number of files");

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

            if (expectedType.equals("Page")) {
                expectedType = "P";
            }

            assertEquals(actualType, expectedType, "File type");
        }

        for (int i = 0; i < actualFilesList.size(); i++) {
            File actualFile = actualFilesList.get(i);
            File expectedFile = expectedFilesList.get(i);

            if (expectedFile.getName().startsWith("M ")) {
                //do not compare media files
                continue;
            }
            compareFiles(actualFile, expectedFile, flaFormatVersion);
        }
    }

    private void compareFiles(File actualFile, File expectedFile, FlaFormatVersion flaFormatVersion) throws IOException {
        byte[] actualData = readFile(actualFile);
        byte[] expectedData = readFile(expectedFile);

        //assertEquals(actualData.length, expectedData.length, "File data length of file " + actualFile);
        int epos = 0;
        int apos = 0;
        long lastEdgeCount = -1;
        for (; apos < actualData.length && epos < expectedData.length; apos++, epos++) {
            if (actualData[apos] == 'X') { //special - all randomness is replaced with 'X' - setDebugRandom(true)
                continue;
            }
            if (actualData[apos] == 'U') { //unknown data - also when setDebugRandom(true)
                continue;
            }

            if (apos + 6 < actualData.length
                    && actualData[apos] == '!'
                    && actualData[apos + 1] == 'E'
                    && actualData[apos + 2] == 'C'
                    && actualData[apos + 3] == 'O'
                    && actualData[apos + 4] == 'U'
                    && actualData[apos + 5] == 'N'
                    && actualData[apos + 6] == 'T') {
                lastEdgeCount = (expectedData[epos] & 0xFF)
                        + ((expectedData[epos + 1] & 0xFF) << 8)
                        + ((expectedData[epos + 2] & 0xFF) << 16)
                        + ((expectedData[epos + 3] & 0xFF) << 24);
                epos += 3;
                apos += 6;
                continue;
            }

            if (apos + 5 < actualData.length
                    && actualData[apos] == '!'
                    && actualData[apos + 1] == 'E'
                    && actualData[apos + 2] == 'D'
                    && actualData[apos + 3] == 'G'
                    && actualData[apos + 4] == 'E'
                    && actualData[apos + 5] == 'S') {
                Reference<Integer> eposRef = new Reference<Integer>(epos);
                for (long e = 0; e < lastEdgeCount; e++) {
                    EdgeReader.readEdge(new InputStream() {
                        @Override
                        public int read() throws IOException {
                            int epos = eposRef.getVal();
                            eposRef.setVal(epos + 1);
                            return expectedData[epos] & 0xFF;
                        }
                    }, flaFormatVersion);
                }
                if (lastEdgeCount > 0) {
                    epos = eposRef.getVal() - 1;
                    apos += 5;
                    lastEdgeCount = -1;
                    continue;
                }
            }

            if (apos + 2 < actualData.length
                    && actualData[apos] == 'N'
                    && actualData[apos + 1] == 'N'
                    && actualData[apos + 2] == 'N') {
                while (expectedData[epos] != 0) {
                    epos++;
                }
                apos += 3;
            }

            if (apos + 2 < actualData.length
                    && actualData[apos] == 'V'
                    && actualData[apos + 1] == 'V'
                    && actualData[apos + 2] == 'V') {
                int len = expectedData[epos];
                epos += len;
                apos += 3 - 1;
                continue;
            }
            
            if (apos + 2 < actualData.length
                    && actualData[apos] == 'M'
                    && actualData[apos + 1] == 'M'
                    && actualData[apos + 2] == 'M') {
                int numItems = (expectedData[epos] & 0xFF) + ((expectedData[epos+1] & 0xFF) << 8);
                epos += 2;
                for (int i = 0; i < numItems; i++) {
                    epos = readString(expectedData, epos, flaFormatVersion);
                    epos = readString(expectedData, epos, flaFormatVersion);
                }
                epos -= 1;
                apos += 3 - 1;
                continue;
            }

            if (apos + 3 < actualData.length && actualData[apos] == 3
                    && actualData[apos + 1] == 'Y'
                    && actualData[apos + 2] == 'Y'
                    && actualData[apos + 3] == 'Y') {
                apos += 3;
                int len = expectedData[epos] & 0xFF;
                if (len == 0xFF) {
                    len = (expectedData[epos + 1] & 0xFF) + ((expectedData[epos + 2] & 0xFF) << 8);
                    if (len == 0xFFFF) {
                        len = (expectedData[epos + 3] & 0xFF)
                                + ((expectedData[epos + 4] & 0xFF) << 8)
                                + ((expectedData[epos + 5] & 0xFF) << 16)
                                + ((expectedData[epos + 6] & 0xFF) << 24);
                        epos = epos + 7 + len - 1;
                        continue;
                    }
                    epos = epos + 3 + len - 1;
                    continue;
                }
                epos = epos + 1 + len - 1;
                continue;
            }
            if (apos + 6 < actualData.length && actualData[apos] == 3
                    && actualData[apos + 1] == 'Y'
                    && actualData[apos + 2] == 0
                    && actualData[apos + 3] == 'Y'
                    && actualData[apos + 4] == 0
                    && actualData[apos + 5] == 'Y'
                    && actualData[apos + 6] == 0) {
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
            assertEquals(actualData[apos] & 0xFF, expectedData[epos] & 0xFF, "Byte in file " + actualFile + " on position apos=" + apos + ", epos=" + epos);
        }
        assertEquals(apos, actualData.length, "The file " + actualFile + " is longer than expected file");
        assertEquals(epos, expectedData.length, "The file " + actualFile + " is shorter than expected file");

    }
    
    private int readString(byte[] data, int pos, FlaFormatVersion flaFormatVersion) {        
        if (flaFormatVersion.isUnicode()) {
            pos += 3;
        }
        int len = data[pos] & 0xFF;
        pos++;
        if (len == 0xFF) {
            len = (data[pos] & 0xFF) + ((data[pos + 1] & 0xFF) << 8);
            pos += 2;
        }
        if (len == 0xFFFF) {
            len = (data[pos] & 0xFF) + ((data[pos + 1] & 0xFF) << 8)+ ((data[pos + 2] & 0xFF) << 16) + ((data[pos + 3] & 0xFF) << 24);
            pos += 4;
        }      
        
        if (flaFormatVersion.isUnicode()) {
            len *= 2;
        }
        /*byte[] subdata = Arrays.copyOfRange(data, pos, pos + len);
        if (flaFormatVersion.isUnicode()) {
            try {
                System.out.println("STR: \"" + new String(subdata, "UTF-16LE") +"\"");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(FlaConverterTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            System.out.println("STR: \"" + new String(subdata) +"\"");
        }*/
        
        pos += len;
        return pos;
    }

    @Test(dataProvider = "folders-cs4")
    public void testConvertCs4(String folder) throws Exception {
        convert(folder, FlaFormatVersion.CS4);
    }

    @Test(dataProvider = "folders-cs3")
    public void testConvertCs3(String folder) throws Exception {
        convert(folder, FlaFormatVersion.CS3);
    }

    @Test(dataProvider = "folders-f8")
    public void testConvertF8(String folder) throws Exception {
        convert(folder, FlaFormatVersion.F8);
    }

    @Test(dataProvider = "folders-mx2004")
    public void testConvertMx2004(String folder) throws Exception {
        convert(folder, FlaFormatVersion.MX2004);
    }

    @Test(dataProvider = "folders-mx")
    public void testConvertMx(String folder) throws Exception {
        convert(folder, FlaFormatVersion.MX);
    }

    @Test(dataProvider = "folders-f5")
    public void testConvertF5(String folder) throws Exception {
        convert(folder, FlaFormatVersion.F5);
    }
    
    /*@Test(dataProvider = "folders-f4")
    public void testConvertF4(String folder) throws Exception {
        convert(folder, FlaFormatVersion.F4);
    }

    @Test(dataProvider = "folders-f3")
    public void testConvertF3(String folder) throws Exception {
        convert(folder, FlaFormatVersion.F3);
    }
    
    @Test(dataProvider = "folders-f2")
    public void testConvertF2(String folder) throws Exception {
        convert(folder, FlaFormatVersion.F2);
    }
    
    @Test(dataProvider = "folders-f1")
    public void testConvertF1(String folder) throws Exception {
        convert(folder, FlaFormatVersion.F1);
    }
*/


    //@Test
    public void singleTest() throws Exception {
        //testConvertF8("0002_shapes");
    }

    private void compareSpecific(FlaFormatVersion flaFormatVersion, String folderName, String actualFilename, String expectedFileName) throws IOException, SAXException, ParserConfigurationException {
        FlaConverter contentsGenerator = new FlaConverter(flaFormatVersion, "WINDOWS-1250");
        contentsGenerator.setDebugRandom(true);

        String outputDirParent = OUTPUT_BASE_DIR + "/" + flaFormatVersion.name().toLowerCase();
        String expectedDirParent = EXPECTED_BASE_DIR + "/" + flaFormatVersion.name().toLowerCase();

        File actualDir = new File(outputDirParent + "/" + folderName);
        deleteDir(actualDir);
        if (!actualDir.exists()) {
            actualDir.mkdirs();
        }

        contentsGenerator.convert(new DirectoryInputStorage(new File(SOURCE_DIR + "/" + folderName)),
                new DirectoryOutputStorage(actualDir)
        );
        compareFiles(actualDir.toPath().resolve(actualFilename).toFile(),
                new File(expectedDirParent).toPath().resolve(folderName + "/" + expectedFileName).toFile(),
                flaFormatVersion);
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
        for (FlaFormatVersion flaFormatVersion : FlaFormatVersion.values()) {
            String expectedDirParent = EXPECTED_BASE_DIR + "/" + flaFormatVersion.name().toLowerCase();
            File expectedDir = new File(expectedDirParent);
            for (File f : expectedDir.listFiles()) {
                if (f.isDirectory()) {
                    deleteDir(f);
                }
            }
            FlaCfbExtractor.main(new String[]{expectedDirParent});
        }
    }
}
