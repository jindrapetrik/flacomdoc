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
package com.jpexs.cfb;

import com.jpexs.flash.fla.extractor.FlaCfbExtractor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compound File Binary file format reading class.
 *
 * @author JPEXS
 */
public class CompoundFileBinary implements AutoCloseable {

    private static final byte SIGNATURE[] = new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1};

    private static final byte CLSID_NULL[] = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    private RandomAccessFile raf;

    /**
     * Maximum regular sector number.
     */
    private static final long MAXREGSECT = 0xFFFFFFFAl;
    /**
     * Reserved for future use.
     */
    private static final long NOT_APPLICABLE = 0xFFFFFFFBl;
    /**
     * Specifies a DIFAT sector in the FAT.
     */
    private static final long DIFSECT = 0xFFFFFFFCl;
    /**
     * Specifies a FAT sector in the FAT.
     */
    private static final long FATSECT = 0xFFFFFFFDl;
    /**
     * End of a linked chain of sectors.
     */
    private static final long ENDOFCHAIN = 0xFFFFFFFEl;
    /**
     * Specifies an unallocated sector in the FAT, Mini FAT, or DIFAT.
     */
    private static final long FREESECT = 0xFFFFFFFFl;

    /**
     * Maximum regular stream ID.
     */
    public static final long MAXREGSID = 0xFFFFFFFAl;
    /**
     * Terminator or empty pointer.
     */
    public static final long NOSTREAM = 0xFFFFFFFFl;

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_STORAGE_OBJECT = 1;
    public static final int TYPE_STREAM_OBJECT = 2;
    public static final int TYPE_ROOT_STORAGE_OBJECT = 5;

    public static final int COLOR_RED = 0;
    public static final int COLOR_BLACK = 1;

    private Map<Long, Long> fat;
    private Map<Long, Long> minifat;
    private List<DirectoryEntry> directoryEntries;
    private long miniStreamStartingSector;

    private long miniStreamCutoffSize;
    private long miniStreamSize;
    private int sectorLength;

    public CompoundFileBinary(File file) throws IOException {
        this(file, false);
    }

    public CompoundFileBinary(File file, boolean createNew) throws IOException {
        if (createNew) {
            initNew(file);
        } else {
            initExisting(file);
        }
    }

    private static byte[] fromStringToByteArray(String clsid) {
        Pattern pattern = Pattern.compile("^([0-9A-Fa-f]{8})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{12})$");
        Matcher matcher = pattern.matcher(clsid);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid CLSID format");
        }

        String part1 = matcher.group(1);
        String part2 = matcher.group(2);
        String part3 = matcher.group(3);
        String part4 = matcher.group(4);
        String part5 = matcher.group(5);

        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putInt(Integer.reverseBytes((int) Long.parseLong(part1, 16))); // Little-endian
        buffer.putShort(Short.reverseBytes((short) Integer.parseInt(part2, 16))); // Little-endian
        buffer.putShort(Short.reverseBytes((short) Integer.parseInt(part3, 16))); // Little-endian
        buffer.put(hexStringToByteArray(part4));
        buffer.put(hexStringToByteArray(part5));

        return buffer.array();
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public void setRootClsId(String value) throws IOException {
        DirectoryEntry root = getRootDirEntry();
        root.clsId = fromStringToByteArray(value);
        raf.seek(root.fileOffset + 0x50);
        raf.write(root.clsId);
    }

    private byte[] readBytes(int count) throws IOException {
        byte[] ret = new byte[count];
        raf.readFully(ret);
        return ret;
    }

    private Date readDate() throws IOException {
        //long date = (new Date().getTime() + 11644473600000L) * 10000L;
        long filetime = readUI64();
        if (filetime == 0) {
            return null;
        }
        return new Date((filetime / 10000L) - 11644473600000L);
    }

    private int readEx() throws IOException {
        int ret = raf.read();
        if (ret == -1) {
            throw new IOException("Premature end of the file reached");
        }
        return ret;
    }

    private void skipZeroBytes(int count, String errorMsg) throws IOException {
        for (int i = 0; i < count; i++) {
            if (readEx() != 0) {
                throw new IOException(errorMsg);
            }
        }
    }

    private void writeZeroBytes(int num) throws IOException {
        raf.write(new byte[num]);
    }

    private void write(int value) throws IOException {
        raf.write(value);
    }

    private void writeUI16(int value) throws IOException {
        write(value & 0xFF);
        write((value >> 8) & 0xFF);
    }

    private void writeUI32(long value) throws IOException {
        write((int) (value & 0xFF));
        write((int) ((value >> 8) & 0xFF));
        write((int) ((value >> 16) & 0xFF));
        write((int) ((value >> 24) & 0xFF));
    }

    private void writeUI64(long value) throws IOException {
        write((int) (value & 0xFF));
        write((int) ((value >> 8) & 0xFF));
        write((int) ((value >> 16) & 0xFF));
        write((int) ((value >> 24) & 0xFF));
        write((int) ((value >> 32) & 0xFF));
        write((int) ((value >> 40) & 0xFF));
        write((int) ((value >> 48) & 0xFF));
        write((int) ((value >> 56) & 0xFF));
    }

    private void writeDate(Date value) throws IOException {
        if (value == null) {
            writeUI64(0);
            return;
        }
        long date = (value.getTime() + 11644473600000L) * 10000L;
        writeUI64(date);
    }

    private int readUI16() throws IOException {
        return readEx() + (readEx() << 8);
    }

    private long readUI32() throws IOException {
        return (readEx() + (readEx() << 8) + (readEx() << 16) + (readEx() << 24)) & 0xffffffffL;
    }

    //This is actually not UI64, it is SI664
    private long readUI64() throws IOException {
        return readUI32() + (readUI32() << 32);
    }

    private void initNew(File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }
        raf = new RandomAccessFile(file, "rw");
        raf.write(SIGNATURE);       //0x00
        raf.write(CLSID_NULL);      //0x08
        writeUI16(0x003E); //minorVersion,  0x18
        writeUI16(0x0003); //majorVersion   0x1A
        writeUI16(0xFFFE); //byteOrder      0x1C
        writeUI16(0x0009); //sectorShift    0x1E
        writeUI16(0x0006); //miniSectorShift 0x20
        writeZeroBytes(6);   //0x22
        writeUI32(0); //numDirectorySectors, must be zero for majorVersion 3. 0x28

        sectorLength = 512; //major version 3

        long numFatSectors = 1;
        long firstDirectorySectorLocation = 1;
        long transactionSignatureNumber = 0;
        miniStreamCutoffSize = 4096;
        long firstMiniFatSectorLocation = 2; //ENDOFCHAIN;
        long numMiniFatSectors = 1; //
        long firstDifatSectorLocation = ENDOFCHAIN;
        long numDifatSectors = 0;
        writeUI32(numFatSectors);   //0x2C
        writeUI32(firstDirectorySectorLocation); //0x30
        writeUI32(transactionSignatureNumber); //0x34
        writeUI32(miniStreamCutoffSize); //0x38
        writeUI32(firstMiniFatSectorLocation); //0x3C
        writeUI32(numMiniFatSectors); //0x40
        writeUI32(firstDifatSectorLocation); //0x44
        writeUI32(numDifatSectors); //0x48

        writeUI32(0); //difat[0] - sector #0 for FAT . 0x4C
        for (int i = 0; i < 108; i++) {
            writeUI32(FREESECT);
        }

        //Sector 0: FAT
        writeUI32(FATSECT);
        writeUI32(ENDOFCHAIN); //end of directory chain
        writeUI32(ENDOFCHAIN); //end of miniFAT chain        
        writeUI32(ENDOFCHAIN); //mini stream
        for (int i = 4; i <= 127; i++) {
            writeUI32(FREESECT); //empty unallocated free sectors
        }

        //Sector 1: Directory sector
        Random rnd = new Random();
        byte[] dirClsId = new byte[16];
        rnd.nextBytes(dirClsId);

        Date d = new Date();
        writeDirectoryEntry(
                "Root Entry",
                TYPE_ROOT_STORAGE_OBJECT,
                COLOR_BLACK,
                NOSTREAM,
                NOSTREAM,
                NOSTREAM,
                dirClsId,
                0,
                d,
                d,
                3, //miniStreamStartingSector
                64 //miniStreamSize xx576
        );

        for (int i = 128; i < sectorLength; i += 128) {
            writeEmptyDirectoryEntry();
        }

        //Sector 2: MiniFAT sector
        writeUI32(ENDOFCHAIN);
        for (int i = 4; i < sectorLength; i += 4) {
            writeUI32(FREESECT);
        }

        //Sector 3: Mini stream sector
        for (int i = 0; i < sectorLength; i++) {
            writeUI32(0);
        }
        readFile();
    }

    private void seekToMiniStream(long miniSectorId) throws IOException {
        long miniStreamSector = miniStreamStartingSector;
        long sId = 0;
        while (miniStreamSector != ENDOFCHAIN) {
            for (int i = 0; i < sectorLength; i += 64) {
                if (sId == miniSectorId) {
                    raf.seek((1 + miniStreamSector) * sectorLength + i);
                    return;
                }
                sId++;
            }
            miniStreamSector = fat.get(miniStreamSector);
        }
        throw new IOException("No such miniSectorId exists");
    }

    public DirectoryEntry addFile(String path, File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int cnt;
        try (FileInputStream fis = new FileInputStream(file)) {
            while ((cnt = fis.read(buf)) > 0) {
                baos.write(buf, 0, cnt);
            }
        }
        return addFile(path, baos.toByteArray());
    }

    public DirectoryEntry getRootDirEntry() {
        for (DirectoryEntry entry : directoryEntries) {
            if (entry.objectType == TYPE_ROOT_STORAGE_OBJECT) {
                return entry;
            }
        }
        return null;
    }

    public List<DirectoryEntry> getEntriesInRootDir() {
        return getEntriesInDir(getRootDirEntry());
    }

    public DirectoryEntry getEntryByPath(String path) {
        DirectoryEntry dir = getRootDirEntry();

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        boolean trailingSlash = path.endsWith("/");

        if (trailingSlash) {
            path = path.substring(0, path.length() - 1);
        }
        String parts[] = path.split("/", -1);

        loopp:
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            List<DirectoryEntry> entries = getEntriesInDir(dir);
            for (DirectoryEntry entry : entries) {
                if (entry.name.equals(part)) {
                    if (entry.objectType == TYPE_STORAGE_OBJECT) {
                        dir = entry;
                        continue loopp;
                    }
                    if (entry.objectType == TYPE_STREAM_OBJECT && i == parts.length - 1) {
                        if (trailingSlash) { //must be dir
                            return null;
                        }
                        return entry;
                    }
                    return null;
                }
            }
            return null;
        }
        return dir;
    }

    public List<DirectoryEntry> getEntriesInDir(DirectoryEntry dir) {
        List<DirectoryEntry> ret = new ArrayList<>();
        if (dir.childId != NOSTREAM) {
            DirectoryEntry child = getDirEntryById(dir.childId);
            ret.add(child);
            walkEntry(child, ret);
        }
        return ret;
    }

    public List<DirectoryEntry> getEntriesInDir(int streamId) {
        DirectoryEntry dir = getDirEntryById(streamId);
        return getEntriesInDir(dir);
    }

    private void walkEntry(DirectoryEntry entry, List<DirectoryEntry> result) {
        if (entry.leftSiblingId != NOSTREAM) {
            DirectoryEntry left = getDirEntryById(entry.leftSiblingId);
            result.add(left);
            walkEntry(left, result);
        }
        if (entry.rightSiblingId != NOSTREAM) {
            DirectoryEntry right = getDirEntryById(entry.rightSiblingId);
            result.add(right);
            walkEntry(right, result);
        }
    }

    public DirectoryEntry getDirEntryById(long streamId) {
        for (DirectoryEntry de : directoryEntries) {
            if (de.streamId == streamId) {
                return de;
            }
        }
        return null;
    }

    public DirectoryEntry addDirectory(String path) throws IOException {
        DirectoryEntry existing = getEntryByPath(path);
        if (existing != null) {
            return existing;
        }
        Logger.getLogger(CompoundFileBinary.class.getName()).log(Level.FINE, "adding directory {0}", path);
        DirectoryEntry parent = getRootDirEntry();
        if (path.contains("/")) {
            String parentPath = path.substring(0, path.lastIndexOf("/"));
            path = path.substring(path.lastIndexOf("/") + 1);
            parent = getEntryByPath(parentPath);
            if (parent == null) {
                parent = addDirectory(parentPath);
            }
        }
        Date d = new Date();
        DirectoryEntry entry = new DirectoryEntry(-1, -1, path, TYPE_STORAGE_OBJECT, COLOR_BLACK, NOSTREAM, NOSTREAM, NOSTREAM, CLSID_NULL, 0, d, d, 0, 0);
        addDirectoryEntry(parent, entry);
        return entry;
    }

    private void markTreeBlack(DirectoryEntry entry) throws IOException {
        if (entry == null) {
            return;
        }
        entry.colorFlag = COLOR_BLACK;
        raf.seek(entry.fileOffset + 0x43);
        write(entry.colorFlag);

        DirectoryEntry left = entry.leftSiblingId == NOSTREAM ? null : getDirEntryById(entry.leftSiblingId);
        markTreeBlack(left);
        DirectoryEntry right = entry.rightSiblingId == NOSTREAM ? null : getDirEntryById(entry.rightSiblingId);
        markTreeBlack(right);

    }

    private void addDirectoryEntry(DirectoryEntry parent, DirectoryEntry newEntry) throws IOException {
        boolean asChild = false;
        Long targetId = null;
        if (parent.childId == NOSTREAM) {
            asChild = true;
        } else {
            DirectoryEntry child = getDirEntryById(parent.childId);

            DirectoryEntry e = child;
            while (true) {
                DirectoryEntry left = e.leftSiblingId == NOSTREAM ? null : getDirEntryById(e.leftSiblingId);
                DirectoryEntry right = e.rightSiblingId == NOSTREAM ? null : getDirEntryById(e.rightSiblingId);

                if (left != null) {
                    //The left sibling MUST always be less than the right sibling
                    if (newEntry.compareTo(left) > 0) {
                        e = left;
                        continue;
                    }
                }
                if (left != null && right != null) {
                    e = right;
                    continue;
                }
                if (left == null) {
                    break;
                }
                break;
            }

            /*while (child.leftSiblingId != NOSTREAM && child.rightSiblingId != NOSTREAM) {
                child = getDirEntryById(child.leftSiblingId);
            }*/
            targetId = e.streamId;
        }

        raf.seek(0x30);
        long firstDirectorySectorLocation = readUI32();

        long directorySector = firstDirectorySectorLocation;
        long sectorBefore = directorySector;
        int newStreamId = 0;
        DirectoryEntry entry = null;
        loopDir:
        while (directorySector != ENDOFCHAIN) {
            raf.seek((1 + directorySector) * sectorLength);
            for (int i = 0; i < sectorLength; i += 128) {
                byte[] nameBytes = readBytes(64);
                int nameLen = readUI16();   //64

                int objectType = readEx(); //66
                int colorFlag = readEx(); //67
                long leftSiblingId = readUI32(); //68
                long rightSiblingId = readUI32(); //72
                long childId = readUI32(); //76
                byte dirClsId[] = readBytes(16); // 80
                long stateBits = readUI32(); //96
                Date creationTime = readDate(); //100
                Date modifiedTime = readDate(); //108
                long startingSectorLocation = readUI32(); //if rootdir, then miniStreamStartingSector , 116
                long streamSize = readUI64(); //if rootdir, then miniStreamSize, 120
                //if (majorVersion == 3) {
                streamSize = streamSize & 0xFFFFFFFFl;
                //}
                boolean nameEmpty = true;
                for (int j = 0; j < 64; j++) {
                    if (nameBytes[j] != 0) {
                        nameEmpty = false;
                        break;
                    }
                }

                if (nameEmpty
                        && nameLen == 0
                        && objectType == TYPE_UNKNOWN
                        && colorFlag == COLOR_RED
                        && leftSiblingId == NOSTREAM
                        && rightSiblingId == NOSTREAM
                        && childId == NOSTREAM
                        && Arrays.equals(dirClsId, CLSID_NULL)
                        && stateBits == 0
                        && creationTime == null
                        && modifiedTime == null
                        && startingSectorLocation == 0
                        && streamSize == 0) {
                    raf.seek((1 + directorySector) * sectorLength + i);
                    Date d = new Date();
                    entry = newEntry;
                    entry.fileOffset = (1 + directorySector) * sectorLength + i;
                    entry.streamId = newStreamId;
                    writeDirectoryEntry(entry);
                    break loopDir;
                }
                newStreamId++;
            }
            sectorBefore = directorySector;
            directorySector = fat.get(directorySector);
        }

        if (directorySector == ENDOFCHAIN) {
            directorySector = allocateNewSector(sectorBefore);
            raf.seek((1 + directorySector) * sectorLength);
            entry = newEntry;
            entry.fileOffset = (1 + directorySector) * sectorLength;
            entry.streamId = newStreamId;
            writeDirectoryEntry(entry);
            for (int i = 128; i < sectorLength; i += 128) {
                writeEmptyDirectoryEntry();
            }
        }
        directoryEntries.add(entry);

        directorySector = firstDirectorySectorLocation;
        int streamId = 0;
        loopDir:
        while (directorySector != ENDOFCHAIN) {
            for (int i = 0; i < sectorLength; i += 128) {
                raf.seek((1 + directorySector) * sectorLength + i);
                readBytes(64);
                readUI16();   //64

                int objectType = readEx(); //66
                readEx(); //67
                long leftSiblingId = readUI32(); //68
                long rightSiblingId = readUI32(); //72
                long childId = readUI32(); //76

                if (asChild && streamId == parent.streamId) {
                    raf.seek((1 + directorySector) * sectorLength + i + 76);
                    writeUI32(newStreamId);
                    parent.childId = newStreamId;
                }
                if (targetId != null && streamId == targetId) {
                    if (rightSiblingId == NOSTREAM) {
                        raf.seek((1 + directorySector) * sectorLength + i + 72);
                        writeUI32(newStreamId);
                        getDirEntryById(streamId).rightSiblingId = newStreamId;
                    } else if (leftSiblingId == NOSTREAM) {
                        raf.seek((1 + directorySector) * sectorLength + i + 68);
                        writeUI32(newStreamId);
                        getDirEntryById(streamId).leftSiblingId = newStreamId;
                    }
                }
                streamId++;
            }
            directorySector = fat.get(directorySector);
        }
        markTreeBlack(getDirEntryById(parent.childId));
    }

    private void walkFiles(String localPath, String absPath, List<DirectoryEntry> ret) throws IOException {

        File root = new File(absPath);
        File[] list = root.listFiles();

        if (list == null) {
            return;
        }

        for (File f : list) {
            String name = f.getName();
            String localSubPath = localPath.isEmpty() ? name : localPath + "/" + name;
            if (f.isDirectory()) {
                walkFiles(localSubPath, f.getAbsolutePath(), ret);
                ret.add(addDirectory(localSubPath));
            } else {
                ret.add(addFile(localSubPath, f));
            }
        }
    }

    public List<DirectoryEntry> addDirectoryContents(String path, File dir) throws IOException {
        List<DirectoryEntry> ret = new ArrayList<>();
        walkFiles(path, dir.getAbsolutePath(), ret);
        return ret;
    }

    public DirectoryEntry addFile(String path, byte[] data) throws IOException {

        DirectoryEntry existing = getEntryByPath(path);
        if (existing != null) {
            throw new IOException("File with path " + path + " already exists");
        }

        Logger.getLogger(CompoundFileBinary.class.getName()).log(Level.FINE, "adding file {0}", path);

        DirectoryEntry parent = getRootDirEntry();
        if (path.contains("/")) {
            String parentPath = path.substring(0, path.lastIndexOf("/"));
            path = path.substring(path.lastIndexOf("/") + 1);
            parent = getEntryByPath(parentPath);
            if (parent == null) {
                parent = addDirectory(parentPath);
            }
        }

        Long sectorId = null;
        Long firstSectorId = null;
        int pos = 0;
        while (pos < data.length) {
            int sectorSize;
            if (data.length < miniStreamCutoffSize) {
                sectorId = allocateNewMiniSector(sectorId);
                seekToMiniStream(sectorId);
                sectorSize = 64;
            } else {
                sectorId = allocateNewSector(sectorId);
                raf.seek((1 + sectorId) * sectorLength);
                sectorSize = sectorLength;
            }
            if (firstSectorId == null) {
                firstSectorId = sectorId;
            }
            int len = sectorSize;
            if (pos + len > data.length) {
                len = data.length - pos;
            }
            raf.write(data, pos, len);
            pos += len;
        }

        if (data.length == 0) {
            firstSectorId = allocateNewMiniSector(null);
        }

        if (!USE_DIRENTRY) {
            return null;
        }
        Date d = new Date();
        DirectoryEntry entry = new DirectoryEntry(-1, -1, path, TYPE_STREAM_OBJECT, COLOR_BLACK, NOSTREAM, NOSTREAM, NOSTREAM, CLSID_NULL, 0, d, d, firstSectorId, data.length);
        addDirectoryEntry(parent, entry);
        return entry;
    }

    public boolean USE_DIRENTRY = true;

    private long allocateNewMiniSector(Long prevSector) throws IOException {
        raf.seek(0x30);

        long firstDirectorySectorLocation = readUI32();

        long miniStreamSizeFileOffset = 0;
        long directorySector = firstDirectorySectorLocation;
        miniStreamStartingSector = ENDOFCHAIN;
        miniStreamSize = 0L;
        loopDir:
        while (directorySector != ENDOFCHAIN) {
            for (int i = 0; i < sectorLength; i += 128) {
                raf.seek((1 + directorySector) * sectorLength + i * 128 + 0x42);
                int objectType = readEx();
                if (objectType == TYPE_ROOT_STORAGE_OBJECT) {
                    raf.seek((1 + directorySector) * sectorLength + i * 128 + 0x74);
                    miniStreamStartingSector = readUI32();
                    miniStreamSizeFileOffset = raf.getFilePointer();
                    miniStreamSize = readUI32();
                    if (miniStreamStartingSector == ENDOFCHAIN) {
                        miniStreamStartingSector = allocateNewSector(null);
                        raf.seek((1 + directorySector) * sectorLength + i * 128 + 0x74);
                        writeUI32(miniStreamStartingSector);
                        raf.seek((1 + miniStreamStartingSector) * sectorLength);
                        for (int j = 0; j < sectorLength; j++) {
                            write(0);
                        }
                    }
                    break loopDir;
                }
            }
            directorySector = fat.get(directorySector);
        }

        raf.seek(0x3C);
        long firstMiniFatSectorLocation = readUI32();
        if (firstMiniFatSectorLocation == ENDOFCHAIN) {
            firstMiniFatSectorLocation = allocateNewSector(null);
            raf.seek((1 + firstMiniFatSectorLocation) * sectorLength);
            for (int i = 0; i < sectorLength; i += 4) {
                writeUI32(FREESECT);
            }
        }

        long minifatSector = firstMiniFatSectorLocation;

        long sectorBefore = minifatSector;
        Long foundMiniSectorId = 0L;
        long sectorId = 0;
        loopMiniFat:
        while (minifatSector != ENDOFCHAIN) {
            raf.seek((1 + minifatSector) * sectorLength);
            for (int i = 0; i < sectorLength; i += 4) {
                long val = readUI32();
                if (val == FREESECT) {
                    raf.seek((1 + minifatSector) * sectorLength + i);
                    writeUI32(ENDOFCHAIN);
                    foundMiniSectorId = sectorId;
                    minifat.put(sectorId, ENDOFCHAIN);
                    break loopMiniFat;
                }
                sectorId++;
            }
            sectorBefore = minifatSector;
            minifatSector = fat.get(minifatSector);
        }

        if (minifatSector == ENDOFCHAIN) {
            minifatSector = allocateNewSector(sectorBefore);
            raf.seek((1 + minifatSector) * sectorLength);
            writeUI32(ENDOFCHAIN);
            for (int i = 4; i < sectorLength; i += 4) {
                writeUI32(FREESECT);
            }
            foundMiniSectorId = sectorId;

            raf.seek(0x40);
            long numMiniFatSectors = readUI32();
            numMiniFatSectors++;
            raf.seek(0x40);
            writeUI32(numMiniFatSectors);
        }

        long miniStreamSector = miniStreamStartingSector;
        long miniSectorAddr = 64 * foundMiniSectorId;
        long addr = 0;
        sectorBefore = miniStreamSector;
        long offset = 0;
        while (miniStreamSector != ENDOFCHAIN) {
            if (miniSectorAddr < addr + sectorLength) {
                offset = miniSectorAddr - addr;
                raf.seek((1 + miniStreamSector) * sectorLength + offset);
                for (int i = 0; i < 64; i++) {
                    write(0);
                }
                miniStreamSize += 64;
                raf.seek(miniStreamSizeFileOffset);
                writeUI32(miniStreamSize);

                break;
            }
            addr += sectorLength;
            sectorBefore = miniStreamSector;
            miniStreamSector = fat.get(miniStreamSector);
        }

        if (miniStreamSector == ENDOFCHAIN) {
            miniStreamSector = allocateNewSector(sectorBefore);
            miniStreamSize += 64;
            raf.seek(miniStreamSizeFileOffset);
            writeUI32(miniStreamSize);
        }
        if (prevSector != null) {
            minifatSector = firstMiniFatSectorLocation;
            sectorId = 0;
            loopMiniFat2:
            while (minifatSector != ENDOFCHAIN) {
                raf.seek((1 + minifatSector) * sectorLength);
                for (int i = 0; i < sectorLength; i += 4) {
                    if (sectorId == prevSector) {
                        writeUI32(foundMiniSectorId);
                        break loopMiniFat2;
                    }
                    readUI32();
                    sectorId++;
                }
                minifatSector = fat.get(minifatSector);
            }
        }

        Logger.getLogger(CompoundFileBinary.class.getName()).log(Level.FINE, "allocated new mini sector {0}({1}) after sector {2}", new Object[]{foundMiniSectorId, String.format("%1$04X", (1 + miniStreamSector) * sectorLength + offset), prevSector});

        return foundMiniSectorId;
    }

    private long allocateNewSector(Long prevSector) throws IOException {
        raf.seek(0x4C);
        List<Long> difat = new ArrayList<>();
        for (int i = 0; i < 109; i++) {
            difat.add(readUI32());
        }

        raf.seek(0x44);
        long firstDifatSectorLocation = readUI32();

        long difatSectorLocation = firstDifatSectorLocation;

        while (difatSectorLocation != ENDOFCHAIN) {
            raf.seek((1 + difatSectorLocation) * sectorLength);
            for (int i = 0; i < sectorLength - 4; i += 4) {
                difat.add(readUI32());
            }
            difatSectorLocation = readUI32();
        }

        Long newSectorId = null;
        long sectorId = 0;
        Long newSectorFatSector = null;
        for (long fatSect : difat) {
            if (fatSect == FREESECT) {
                continue;
            }
            raf.seek((1 + fatSect) * sectorLength);
            for (int i = 0; i < sectorLength; i += 4) {
                long sect = readUI32();
                if (sect == FREESECT) {
                    newSectorId = sectorId;
                    newSectorFatSector = fatSect;
                    raf.seek((1 + fatSect) * sectorLength + i);
                    writeUI32(ENDOFCHAIN);
                    fat.put(sectorId, ENDOFCHAIN);
                    break;
                }
                sectorId++;
            }
        }
        if (newSectorId == null) {
            int numNewSectors = 2;
            Long newFatSectorId = sectorId;
            //we need to enlarge difat
            raf.seek(0x4C);
            boolean inMainDiFat = false;
            for (int i = 0; i < 109; i++) {
                long difatval = readUI32();
                if (difatval == FREESECT) {
                    raf.seek(0x4C + i * 4);
                    writeUI32(sectorId);
                    inMainDiFat = true;
                    break;
                }
            }
            if (!inMainDiFat) {
                numNewSectors++;
                long newDiFatSectorId = sectorId;
                newFatSectorId = newDiFatSectorId++;
                if (firstDifatSectorLocation == ENDOFCHAIN) {
                    raf.seek(0x44);
                    writeUI32(newDiFatSectorId);
                } else {
                    difatSectorLocation = firstDifatSectorLocation;
                    while (difatSectorLocation != ENDOFCHAIN) {
                        raf.seek((1 + difatSectorLocation) * sectorLength + sectorLength - 4);
                        long newDifatSectorLocation = readUI32();
                        if (newDifatSectorLocation == ENDOFCHAIN) {
                            raf.seek((1 + difatSectorLocation) * sectorLength + sectorLength - 4);
                            writeUI32(newDiFatSectorId);
                        }
                        difatSectorLocation = newDifatSectorLocation;
                    }
                }
                raf.seek((1 + newDiFatSectorId) * sectorLength);
                writeUI32(newFatSectorId);
                for (int i = 4; i < sectorLength - 4; i += 4) {
                    writeUI32(FREESECT);
                }
                writeUI32(ENDOFCHAIN);
                fat.put(newDiFatSectorId, ENDOFCHAIN);
            }
            fat.put(newFatSectorId, ENDOFCHAIN);
            newSectorId = newFatSectorId + 1;
            raf.seek((1 + newFatSectorId) * sectorLength);
            for (int i = 0; i < numNewSectors; i++) {
                writeUI32(ENDOFCHAIN);
            }
            for (int i = numNewSectors * 4; i < sectorLength; i += 4) {
                writeUI32(FREESECT);
            }
            raf.seek(0x2C);
            long numFatSectors = readUI32();
            numFatSectors++;
            raf.seek(0x2C);
            writeUI32(numFatSectors);
        }
        raf.seek((1 + newSectorId) * sectorLength);
        for (int i = 0; i < sectorLength; i++) {
            write(0);
        }

        if (prevSector != null) {
            sectorId = 0;
            loopF:
            for (long fatSect : difat) {
                raf.seek((1 + fatSect) * sectorLength);
                for (int i = 0; i < sectorLength; i += 4) {
                    if (sectorId == prevSector) {
                        writeUI32(newSectorId);
                        break loopF;
                    }
                    readUI32();
                    sectorId++;
                }
            }
            fat.put(prevSector, newSectorId);
        }
        Logger.getLogger(CompoundFileBinary.class.getName()).log(Level.FINE, "allocated new long sector {0}({1}) after sector {2}", new Object[]{newSectorId, String.format("%1$04X", (1 + newSectorId) * sectorLength), prevSector});
        return newSectorId;
    }

    private void writeEmptyDirectoryEntry() throws IOException {
        writeDirectoryEntry(null,
                TYPE_UNKNOWN,
                COLOR_RED,
                NOSTREAM,
                NOSTREAM,
                NOSTREAM,
                CLSID_NULL,
                0,
                null,
                null,
                0,
                0);
    }

    private void writeDirectoryEntry(DirectoryEntry de) throws IOException {
        writeDirectoryEntry(de.name, de.objectType, de.colorFlag, de.leftSiblingId, de.rightSiblingId, de.childId, de.clsId, de.stateBits, de.creationTime, de.modifiedTime, de.startingSectorLocation, de.streamSize);
    }

    private void writeDirectoryEntry(
            String name,
            int objectType,
            int colorFlag,
            long leftSiblingId,
            long rightSiblingId,
            long childId,
            byte[] clsId,
            long stateBits,
            Date creationTime,
            Date modifiedTime,
            long startingSectorLocation,
            long streamSize
    ) throws UnsupportedEncodingException, IOException {

        if (name == null) {
            writeZeroBytes(64);
            writeUI16(0);
        } else {
            byte[] dirNameBytes = name.getBytes("UTF-16LE");
            if (dirNameBytes.length - 2 > 62) {
                throw new IllegalArgumentException("Name \"" + name + "\" exceeds limit of 32 chars");
            }
            raf.write(dirNameBytes);
            int restBytes = 64 - dirNameBytes.length;
            if (restBytes > 0) {
                writeZeroBytes(restBytes);
            }
            writeUI16(dirNameBytes.length + 2); // 0x40
        }
        write(objectType); //0x42
        write(colorFlag); //0x43
        writeUI32(leftSiblingId); //0x44
        writeUI32(rightSiblingId); //0x48
        writeUI32(childId); //0x4C
        raf.write(clsId);   //0x50
        writeUI32(stateBits);        //0x60
        writeDate(creationTime);     //0x64
        writeDate(modifiedTime);        //0x6C
        writeUI32(startingSectorLocation); //0x74
        writeUI64(streamSize); //0x78
    }

    private void initExisting(File file) throws IOException {
        raf = new RandomAccessFile(file, "r");
        readFile();
    }

    private void readFile() throws IOException {
        raf.seek(0);
        byte signature[] = readBytes(SIGNATURE.length);
        if (!Arrays.equals(signature, SIGNATURE)) {
            throw new IOException("Not a CFB file");
        }
        byte clsid[] = readBytes(16);
        if (!Arrays.equals(clsid, CLSID_NULL)) {
            throw new IOException("Invalid clsid - MUST be CLSID_NULL");
        }
        int minorVersion = readUI16(); //should be 0x003E
        int majorVersion = readUI16();

        if (majorVersion != 3 && majorVersion != 4) {
            throw new IOException("Unknown version of the file " + majorVersion);
        }
        int byteOrder = readUI16();
        if (byteOrder != 0xFFFE) {
            throw new IOException("Invalid byte order");
        }
        int sectorShift = readUI16();
        if (majorVersion == 3 && sectorShift != 0x0009) {
            throw new IOException("Sector shift must be 0x0009 for majorVersion 3");
        }
        if (majorVersion == 4 && sectorShift != 0x000C) {
            throw new IOException("Sector shift must be 0x000C for majorVersion 4");
        }
        int miniSectorShift = readUI16();
        if (miniSectorShift != 0x0006) {
            throw new IOException("Mini sector shift must be 0x0006");
        }
        skipZeroBytes(6, "Reserved bytes must be zero");
        long numDirectorySectors = readUI32();
        if (majorVersion == 3 && numDirectorySectors != 0) {
            throw new IOException("Number of directory sectors must be zero for majorVersion 3");
        }

        long numFatSectors = readUI32();
        long firstDirectorySectorLocation = readUI32();
        long transactionSignatureNumber = readUI32();
        miniStreamCutoffSize = readUI32();
        long firstMiniFatSectorLocation = readUI32();
        long numMiniFatSectors = readUI32();
        long firstDifatSectorLocation = readUI32();
        long numDifatSectors = readUI32();

        List<Long> difat = new ArrayList<>();
        for (int i = 0; i < 109; i++) {
            difat.add(readUI32());
        }
        if (majorVersion == 4) {
            skipZeroBytes(3584, "Rest of header sector should be zero");
        }
        sectorLength = 0;
        if (majorVersion == 3) {
            sectorLength = 512;
        }
        if (majorVersion == 4) {
            sectorLength = 4096;
        }

        long difatSectorLocation = firstDifatSectorLocation;

        while (difatSectorLocation != ENDOFCHAIN) {
            raf.seek((1 + difatSectorLocation) * sectorLength);
            for (int i = 0; i < sectorLength - 4; i += 4) {
                difat.add(readUI32());
            }
            difatSectorLocation = readUI32();
        }

        fat = new HashMap<>();
        long fatPos = 0;
        for (long fatSect : difat) {
            if (fatSect <= MAXREGSECT) {
                raf.seek((1 + fatSect) * sectorLength);
                for (int i = 0; i < sectorLength; i += 4) {
                    fat.put(fatPos, readUI32());
                    fatPos++;
                }
            }
        }

        minifat = new HashMap<>();
        long miniFatSectorLocation = firstMiniFatSectorLocation;
        long miniFatPos = 0;
        while (miniFatSectorLocation != ENDOFCHAIN) {
            raf.seek((1 + miniFatSectorLocation) * sectorLength);
            for (int i = 0; i < sectorLength; i += 4) {
                minifat.put(miniFatPos, readUI32());
                miniFatPos++;
            }
            miniFatSectorLocation = fat.get(miniFatSectorLocation);
        }

        long directorySector = firstDirectorySectorLocation;

        directoryEntries = new ArrayList<>();
        long streamId = 0;
        while (directorySector != ENDOFCHAIN) {
            raf.seek((1 + directorySector) * sectorLength);

            for (int i = 0; i < sectorLength; i += 128) {
                byte[] nameBytes = readBytes(64);
                int nameLen = readUI16();
                String name = nameLen == 0 ? "" : new String(nameBytes, 0, nameLen - 2, "UTF-16LE");

                int objectType = readEx();
                if (!Arrays.asList(TYPE_UNKNOWN, TYPE_STORAGE_OBJECT, TYPE_STREAM_OBJECT, TYPE_ROOT_STORAGE_OBJECT).contains(objectType)) {
                    throw new IOException("Invalid object type: " + objectType);
                }
                int colorFlag = readEx();
                if (!Arrays.asList(COLOR_RED, COLOR_BLACK).contains(colorFlag)) {
                    throw new IOException("Invalid color flag: " + colorFlag);
                }

                long leftSiblingId = readUI32();
                long rightSiblingId = readUI32();
                long childId = readUI32();
                byte dirClsId[] = readBytes(16);
                long stateBits = readUI32();
                Date creationTime = readDate();
                Date modifiedTime = readDate();
                long startingSectorLocation = readUI32(); //if rootdir, then miniStreamStartingSector
                long streamSize = readUI64(); //if rootdir, then miniStreamSize
                if (majorVersion == 3) {
                    streamSize = streamSize & 0xFFFFFFFFl;
                }
                DirectoryEntry dirEntry = new DirectoryEntry((1 + directorySector) * sectorLength + i, streamId, name, objectType, colorFlag, leftSiblingId, rightSiblingId, childId, dirClsId, stateBits, creationTime, modifiedTime, startingSectorLocation, streamSize);
                if (objectType == TYPE_ROOT_STORAGE_OBJECT) {
                    miniStreamStartingSector = startingSectorLocation;
                    miniStreamSize = streamSize;
                }
                directoryEntries.add(dirEntry);
                streamId++;
            }
            directorySector = fat.get(directorySector);
        }
    }

    private InputStream getMiniStream(long sector, long totalSize) {
        int miniSectorLength = 64;
        return new InputStream() {
            int rsectorPos = 0;
            long rsector = sector;
            long readPos = 0L;

            @Override
            public int read() throws IOException {
                if (readPos >= totalSize) {
                    return -1;
                }
                if (rsector == ENDOFCHAIN) {
                    return -1;
                }
                if (rsectorPos == miniSectorLength) {
                    rsector = minifat.get(rsector);
                    rsectorPos = 0;
                }
                if (rsector == ENDOFCHAIN) {
                    return -1;
                }
                InputStream miniIs = getLargeStream(miniStreamStartingSector, miniStreamSize);
                miniIs.skip(rsector * miniSectorLength + rsectorPos);
                rsectorPos++;
                readPos++;
                return miniIs.read();
            }
        };
    }

    private InputStream getLargeStream(long sector, long totalSize) {
        return new InputStream() {
            int rsectorPos = 0;
            long rsector = sector;
            long readPos = 0L;

            @Override
            public long skip(long n) throws IOException {
                for (long i = 0; i < n; i++) {
                    if (readPos >= totalSize) {
                        return i;
                    }
                    if (rsector == ENDOFCHAIN) {
                        return i;
                    }
                    if (rsectorPos == sectorLength) {
                        rsector = fat.get(rsector);
                        rsectorPos = 0;
                    }
                    if (rsector == ENDOFCHAIN) {
                        return i;
                    }
                    rsectorPos++;
                }
                return n;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (readPos >= totalSize) {
                    return -1;
                }
                if (rsector == ENDOFCHAIN) {
                    return -1;
                }
                if (rsectorPos == sectorLength) {
                    rsector = fat.get(rsector);
                    rsectorPos = 0;
                }
                if (rsector == ENDOFCHAIN) {
                    return -1;
                }
                int realReadLen = sectorLength - rsectorPos;
                if (realReadLen > len) {
                    realReadLen = len;
                }
                boolean readAll = false;
                if (readPos + realReadLen > totalSize) {
                    realReadLen = (int) (totalSize - readPos);
                    readAll = true;
                }
                raf.seek((1 + rsector) * sectorLength + rsectorPos);
                raf.readFully(b, off, realReadLen);
                rsectorPos += realReadLen;
                readPos += realReadLen;
                int ret = realReadLen;
                if (!readAll && realReadLen < len) {
                    ret += read(b, off + realReadLen, len - realReadLen);
                }
                return ret;
            }

            @Override
            public int read() throws IOException {
                if (readPos >= totalSize) {
                    return -1;
                }
                if (rsector == ENDOFCHAIN) {
                    return -1;
                }
                if (rsectorPos == sectorLength) {
                    rsector = fat.get(rsector);
                    rsectorPos = 0;
                }
                if (rsector == ENDOFCHAIN) {
                    return -1;
                }
                raf.seek((1 + rsector) * sectorLength + rsectorPos);
                rsectorPos++;
                readPos++;
                return raf.read();
            }
        };
    }

    public InputStream getEntryStream(DirectoryEntry entry) {
        if (entry.streamSize < miniStreamCutoffSize) {
            return getMiniStream(entry.startingSectorLocation, entry.streamSize);
        }
        return getLargeStream(entry.startingSectorLocation, entry.streamSize);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    public List<DirectoryEntry> getDirectoryEntries() {
        return directoryEntries;
    }

    public static void main(String[] args) throws IOException {
        new File("out").mkdir();
        FlaCfbExtractor.initLog();
        File outFile = new File("out/mycbf.fla");
        CompoundFileBinary cd = new CompoundFileBinary(outFile, true);
        cd.setRootClsId("08fcfece-b230-461b-9f84-d72f31db07ae"); //FLA cls id
        cd.addDirectoryContents("", new File("testdata/cbf"));
    }
}
