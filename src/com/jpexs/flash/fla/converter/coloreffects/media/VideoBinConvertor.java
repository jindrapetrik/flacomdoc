package com.jpexs.flash.fla.converter.coloreffects.media;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Converts the archive envelope around the two embedded video/audio tracks.
 * Track indices and compressed payloads are copied without re-encoding.
 */
public class VideoBinConvertor {

    private final DataInputStream input;
    private final byte[] buffer = new byte[4096];

    public VideoBinConvertor(InputStream input) {
        this.input = new DataInputStream(input);
    }

    public void convertTo(OutputStream output, int targetRevision) throws IOException {
        if (targetRevision != 2 && targetRevision != 3) {
            throw new IllegalArgumentException("Unsupported video target revision: " + targetRevision);
        }
        // Fixed header through the encoding parameters, before the two tracks.
        byte[] header = new byte[0x47];
        input.readFully(header);
        int sourceRevision = header[0] & 255;
        if (sourceRevision != 2 && sourceRevision != 3) {
            throw new IOException("Unsupported video source revision: " + sourceRevision);
        }
        header[0] = (byte) targetRevision;
        // Preserve the parameter normalization used by the original converter.
        header[0x31] = header[0x32] = header[0x37] = 0;
        output.write(header);

        for (int track = 0; track < 2; track++) {
            copy(output, 4); // Track metadata.
            int marker;
            while ((marker = input.readUnsignedByte()) != 0) {
                output.write(marker);
                copy(output, 16); // Four dwords per index entry.
            }
            output.write(0);
            long length = copyUnsignedInt(output);
            copy(output, length);
        }

        if (sourceRevision == 3) {
            OutputStream tail = targetRevision == 3 ? output : null;
            copy(tail, 4);
            copyString(tail);
            long length = copyUnsignedInt(tail);
            copy(tail, length);
        } else if (targetRevision == 3) {
            // Empty revision-3 extension: dword, Unicode String, byte-count.
            output.write(new byte[]{0, 0, 0, 0, (byte) 255, (byte) 254, (byte) 255, 0, 0, 0, 0, 0});
        }
        if (input.read() != -1) {
            throw new IOException("Unexpected trailing data in video payload");
        }
    }

    private void copy(OutputStream output, long length) throws IOException {
        while (length > 0) {
            int count = (int) Math.min(length, buffer.length);
            input.readFully(buffer, 0, count);
            if (output != null) {
                output.write(buffer, 0, count);
            }
            length -= count;
        }
    }

    private int copyByte(OutputStream output) throws IOException {
        int value = input.readUnsignedByte();
        if (output != null) {
            output.write(value);
        }
        return value;
    }

    private int copyUnsignedShort(OutputStream output) throws IOException {
        int low = copyByte(output);
        return low | (copyByte(output) << 8);
    }

    private long copyUnsignedInt(OutputStream output) throws IOException {
        long low = copyUnsignedShort(output);
        return low | ((long) copyUnsignedShort(output) << 16);
    }

    private void copyString(OutputStream output) throws IOException {
        int width = 1;
        long length = copyByte(output);
        if (length == 0xff) {
            length = copyUnsignedShort(output);
            if (length == 0xfffe) {
                width = 2;
                length = copyByte(output);
                if (length == 0xff) {
                    length = copyUnsignedShort(output);
                }
            }
            if (length == 0xffff) {
                length = copyUnsignedInt(output);
                if (length == 0xffffffffL) {
                    length = 0; // Native archive's null-string length.
                }
            }
        }
        copy(output, length * width);
    }
}
