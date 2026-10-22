/*
 * Copyright (C) 2026 JPEXS.
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

import com.jpexs.flash.fla.converter.streams.InputStorageInterface;
import com.jpexs.flash.fla.converter.streams.OutputStorageInterface;
import com.jpexs.helpers.Reference;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class VideoPayloadVersionTest {

    @Test
    public void existingVideoStreamsMatchAllTargetReferences() throws Exception {
        Path root = Paths.get(System.getProperty("flacomdoc.testdata", "testdata"), "fla");
        Path source = root.resolve("cs5/0015_videos");
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(source.resolve("DOMDocument.xml").toFile());
        NodeList items = document.getElementsByTagName("DOMVideoItem");
        assertEquals(items.getLength(), 3);
        for (int i = 0; i < items.getLength(); i++) {
            String file = ((Element) items.item(i)).getAttribute("videoDataHRef");
            byte[] input = Files.readAllBytes(source.resolve("bin").resolve(file));
            for (FlaFormatVersion version : new FlaFormatVersion[]{
                FlaFormatVersion.MX, FlaFormatVersion.MX2004, FlaFormatVersion.F8,
                FlaFormatVersion.CS3, FlaFormatVersion.CS4
            }) {
                byte[] expected = Files.readAllBytes(root.resolve(version.name().toLowerCase(Locale.ROOT))
                        .resolve("0015_videos").resolve(file.substring(0, file.length() - 4)));
                assertEquals(convert(version, input), expected, version + ": " + file);
            }
        }
    }

    @Test
    public void variableRevisionThreeTailIsRemovedAtTheTrackBoundary() throws Exception {
        byte[] core = core(3);
        for (boolean unicode : new boolean[]{false, true}) {
            for (int length : new int[]{0, 255, 65534}) {
                ByteArrayOutputStream source = new ByteArrayOutputStream();
                source.write(core);
                FlaWriter tail = new FlaWriter(source, unicode ? FlaFormatVersion.CS4 : FlaFormatVersion.MX, "UTF-8");
                tail.writeUI32(99);
                char[] text = new char[length];
                Arrays.fill(text, 'x');
                tail.writeBomString(new String(text));
                tail.writeUI32(17);
                source.write(new byte[17]);
                byte[] expected = normalized(core, 2);
                assertEquals(convert(FlaFormatVersion.MX, source.toByteArray()), expected);
                assertEquals(convert(FlaFormatVersion.MX2004, source.toByteArray()), expected);
                assertEquals(convert(FlaFormatVersion.F8, source.toByteArray()), normalized(source.toByteArray(), 3));
            }
        }
    }

    @Test
    public void revisionTwoCanBePreservedOrUpgraded() throws Exception {
        byte[] core = core(2);
        assertEquals(convert(FlaFormatVersion.MX, core), normalized(core, 2));
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(normalized(core, 3));
        expected.write(new byte[]{0, 0, 0, 0, (byte) 255, (byte) 254, (byte) 255, 0, 0, 0, 0, 0});
        assertEquals(convert(FlaFormatVersion.CS4, core), expected.toByteArray());
    }

    @Test(expectedExceptions = IOException.class)
    public void truncatedTailIsRejected() throws Exception {
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        source.write(core(3));
        source.write(new byte[]{0, 0, 0, 0, (byte) 255, (byte) 254});
        convert(FlaFormatVersion.MX, source.toByteArray());
    }

    private byte[] core(int revision) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = new byte[0x47];
        header[0] = (byte) revision;
        header[0x31] = header[0x32] = header[0x37] = 1;
        out.write(header);
        FlaWriter writer = new FlaWriter(out, FlaFormatVersion.CS4, "UTF-8");
        for (int track = 0; track < 2; track++) {
            writer.writeUI32(0x1234 + track);
            for (int entry = 0; entry <= track; entry++) {
                writer.write(1);
                writer.writeUI32(entry);
                writer.writeUI32(track);
                writer.writeUI32(0);
                writer.writeUI32(0x00fffeff);
            }
            writer.write(0);
            byte[] payload = {(byte) 255, (byte) 254, (byte) 255, 0, 1};
            writer.writeUI32(payload.length);
            out.write(payload);
        }
        return out.toByteArray();
    }

    private byte[] normalized(byte[] input, int revision) {
        byte[] result = input.clone();
        result[0] = (byte) revision;
        result[0x31] = result[0x32] = result[0x37] = 0;
        return result;
    }

    private byte[] convert(FlaFormatVersion version, byte[] input) throws Exception {
        MemoryStorage storage = new MemoryStorage(input);
        Element item = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream("<DOMVideoItem name='video' videoDataHRef='video.dat'/>".getBytes("UTF-8")))
                .getDocumentElement();
        LegacyFlaConverter converter = new LegacyFlaConverter(version, "windows-1250");
        converter.writeDomVideoItem(new FlaWriter(new ByteArrayOutputStream(), version, "windows-1250"),
                item, new HashMap<String, Integer>(), new Reference<Integer>(0), 1,
                new Reference<Long>(0L), storage, storage);
        assertEquals(storage.output.size(), 1);
        return storage.output.values().iterator().next().toByteArray();
    }

    private static class MemoryStorage implements InputStorageInterface, OutputStorageInterface {

        private final byte[] input;
        private final Map<String, ByteArrayOutputStream> output = new HashMap<>();

        MemoryStorage(byte[] input) {
            this.input = input;
        }

        public boolean fileExists(String name) {
            return "bin/video.dat".equals(name);
        }

        public InputStream readFile(String name) {
            // Exercise short reads as well as the usual file stream.
            return new ByteArrayInputStream(input) {
                @Override
                public synchronized int read(byte[] b, int off, int len) {
                    return super.read(b, off, Math.min(len, 3));
                }
            };
        }

        public OutputStream getOutputStream(String name) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            output.put(name, result);
            return result;
        }

        public void storeData(String name, byte[] bytes) throws IOException {
            getOutputStream(name).write(bytes);
        }

        public void storeFile(String name, File file) {
            throw new UnsupportedOperationException();
        }

        public void close() {
        }
    }
}
