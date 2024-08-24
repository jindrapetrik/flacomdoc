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
package com.jpexs.comdoc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author JPEXS
 */
public class ComDoc implements AutoCloseable {

    private static final byte SIGNATURE[] = new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1};

    private static final byte CLSID_NULL[] = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    
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
    
    public ComDoc(File file) throws IOException {
        init(file);
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
        for(int i = 0; i < count; i++) {
            if (readEx() != 0) {
                throw new IOException(errorMsg);
            }
        }
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
    
    private void init(File file) throws IOException {
        raf = new RandomAccessFile(file, "r");
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
        
        while(difatSectorLocation != ENDOFCHAIN) {
            raf.seek((1 + difatSectorLocation) * sectorLength);
            for (int i = 0; i < sectorLength - 4; i += 4) {
                difat.add(readUI32());
            }
            difatSectorLocation = readUI32();
        }
        
        fat = new HashMap<>();
        long fatPos = 0;
        for (long fatSect:difat) {
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
        while(miniFatSectorLocation != ENDOFCHAIN) {
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
                String name = nameLen == 0 ? "" : new String(nameBytes,0,nameLen - 2, "UTF-16LE");
                
                int objectType = readEx();
                if(!Arrays.asList(TYPE_UNKNOWN, TYPE_STORAGE_OBJECT, TYPE_STREAM_OBJECT, TYPE_ROOT_STORAGE_OBJECT).contains(objectType)) {
                    throw new IOException("Invalid object type: "+objectType);
                }
                int colorFlag = readEx();
                if(!Arrays.asList(COLOR_RED, COLOR_BLACK).contains(colorFlag)) {
                    throw new IOException("Invalid color flag: "+colorFlag);
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
                DirectoryEntry dirEntry = new DirectoryEntry(streamId, name, objectType, colorFlag, leftSiblingId, rightSiblingId, childId, dirClsId, stateBits, creationTime, modifiedTime, startingSectorLocation, streamSize);
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
                raf.seek((1 + rsector)*sectorLength + rsectorPos);
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
                raf.seek((1 + rsector)*sectorLength + rsectorPos);
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
    
}
