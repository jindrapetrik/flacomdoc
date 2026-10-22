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
package com.jpexs.flash.fla.reader;

/**
 * Immutable interpretation of a native sound-format word. The format describes
 * stored/imported samples, independently of the library item's export settings.
 */
public final class FlaSoundFormat {

    /**
     * Only codec IDs named by the native sound-cache diagnostics are assigned.
     */
    public enum Encoding {
        PCM_LEGACY, ADPCM, MP3, PCM, GSM, NELLYMOSER_8KHZ_MONO, NELLYMOSER, UNKNOWN
    }

    private static final int[] SAMPLE_RATES = {5512, 11025, 22050, 44100};
    private final int flags;

    private FlaSoundFormat(int flags) {
        this.flags = flags;
    }

    /**
     * Interprets flags already normalized by the native loader.
     */
    public static FlaSoundFormat fromFlags(int flags) {
        if (flags < 0) {
            throw new IllegalArgumentException("Negative sound-format flags");
        }
        return new FlaSoundFormat(flags);
    }

    /**
     * Adds the historical rate adjustment only for primary sound revisions
     * 0..4.
     */
    public static FlaSoundFormat fromStoredFlags(int flags, int soundRevision) {
        if (flags < 0 || flags > 0xFFFF || soundRevision < 0 || soundRevision > 255) {
            throw new IllegalArgumentException("Invalid stored sound format or revision");
        }
        return new FlaSoundFormat(flags + (soundRevision < 5 ? 4 : 0));
    }

    public int getFlags() {
        return flags;
    }

    public int getSampleRateCode() {
        return (flags >>> 2) & 3;
    }

    /**
     * Native integer/WAVE rate; code zero is 5512, not a rounded 5513.
     */
    public int getSampleRateHz() {
        return SAMPLE_RATES[getSampleRateCode()];
    }

    /**
     * Exact 44.1 kHz timeline divisor; code zero uses 5512.5.
     */
    public double getTimebaseRateHz() {
        return 44100.0 / (1 << (3 - getSampleRateCode()));
    }

    /**
     * Sample-width flag; compressed codec output can impose additional
     * constraints.
     */
    public int getBitsPerSample() {
        return (flags & 2) != 0 ? 16 : 8;
    }

    /**
     * Channel flag, retained independently of any codec-specific mono
     * constraint.
     */
    public int getChannelCount() {
        return (flags & 1) != 0 ? 2 : 1;
    }

    /**
     * Compression identifier, independent of the low-nibble PCM/rate flags.
     */
    public int getCodecId() {
        return (flags >>> 4) & 15;
    }

    public Encoding getEncoding() {
        int id = getCodecId();
        return id <= 6 ? Encoding.values()[id] : Encoding.UNKNOWN;
    }

    /**
     * Unassigned bits are preserved rather than treated as a codec extension.
     */
    public int getUnknownFlags() {
        return flags & ~0xFF;
    }

    /**
     * Bytes per uncompressed sample frame, not the encoded codec block size.
     */
    public int getPcmFrameByteCount() {
        return getChannelCount() * (getBitsPerSample() / 8);
    }

    /**
     * Nominal duration of per-channel sample frames; codec delays are not
     * removed.
     */
    public double getNominalDurationSeconds(long sampleCount) {
        if (sampleCount < 0 || sampleCount > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Invalid unsigned sample count");
        }
        return sampleCount / getTimebaseRateHz();
    }
}
