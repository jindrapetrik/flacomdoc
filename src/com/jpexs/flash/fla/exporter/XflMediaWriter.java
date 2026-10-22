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
package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Sound buffers and native video envelopes, without codec re-encoding.
 */
final class XflMediaWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflMediaWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    String sound(Element parent, BinaryFlaDocument.Media media, int ordinal) throws IOException {
        FlaRecord d = media.getDescriptor(), payload = payload(media);
        FlaSoundFormat format = media.getSoundFormat();
        if (format == null || format.getUnknownFlags() != 0) {
            throw new IOException("Unavailable or unassigned sound format.");
        }
        FlaRecord primary = record(payload, "primaryAudio");
        BinaryData audio = data(primary);
        String name = session.unique(session.fullName(d), d);
        Element item = parent.getOwnerDocument().createElementNS(NS, "DOMSoundItem");
        session.attribute(item, "name", name, d);
        // Match the restricted cached-media stem used by the native loader.
        String dat = "sound" + ordinal + ".dat";
        item.setAttribute("soundDataHRef", dat);
        item.setAttribute("format", format(format));
        item.setAttribute("sampleCount", Long.toString(n(d, "sampleCount", 0)));
        for (String field : new String[]{"exportFormat", "exportBits"}) {
            if (n(d, field, -1) >= 0) {
                item.setAttribute(field, Long.toString(n(d, field, -1)));
            }
        }
        item.setAttribute("exportNative", Boolean.toString(n(d, "importedMP3QualityFlag", 0) != 0));
        item.setAttribute("use8kSampleRate", Boolean.toString(n(d, "use8kSampleRateFlag", 0) != 0));
        if (!s(d, "deviceSoundHRef", "").isEmpty()) {
            session.attribute(item, "deviceSoundHRef", s(d, "deviceSoundHRef", ""), d);
        }
        if (format.getCodecId() != 0) {
            item.setAttribute("dataLength", Long.toString(n(d, "encodedByteCount", 0)));
            FlaSoundFormat secondary = media.getSecondarySoundFormat();
            if (secondary != null) {
                item.setAttribute("cacheFormat", format(secondary));
                item.setAttribute("cachedSampleCount", Long.toString(n(d, "secondarySampleCount", 0)));
            }
        }
        String href = "sound-" + ordinal + ".wav";
        BinaryData external = null;
        if (format.getCodecId() == 0 || format.getCodecId() == 3) {
            external = wave(audio, format);
        } else if (format.getCodecId() == 2) {
            href = "sound-" + ordinal + ".mp3";
            external = audio;
        } else {
            href = "sound-" + ordinal + ".audio";
            external = audio;
            session.issue("SOUND_CODEC", d, "Native audio and format are retained in bin; no standalone source decoder for codec " + format.getCodecId());
        }
        item.setAttribute("href", href);
        if (external != null) {
            session.files.put("LIBRARY/" + href, external);
            item.setAttribute("externalFileSize", Integer.toString(external.size()));
        }
        imported(item, d);
        session.linkage(item, d);
        session.files.put("bin/" + dat, media.getStream().getData());
        if (Boolean.TRUE.equals(primary.get("shortRead"))) {
            session.issue("SOUND_SHORT_READ", primary, "Native short audio buffer retained exactly; WAV contains the available complete sample frames.");
        }
        parent.appendChild(item);
        return name;
    }

    String video(Element parent, BinaryFlaDocument.Media media, int ordinal) throws IOException {
        FlaRecord d = media.getDescriptor(), p = payload(media);
        String[] sourceFields = {"audioBitsPerSample", "audioChannels", "videoWidth", "videoHeight"};
        for (String field : sourceFields) {
            if (!(p.get(field) instanceof Number)) {
                throw new IOException("Missing video " + field);
            }
        }
        String[] types = {"no media", "jpeg media", "h263 media", "screen share media", "vp6 media", "vp6 alpha media", "screen v2 video packet", "avc video packet", "count media type"};
        long kind = n(p, "videoType", 255);
        String fps = finite(p, "frameRate"), rate = finite(p, "audioSampleRate"), length = finite(p, "durationSeconds");
        String name = session.unique(session.fullName(d), d);
        Element item = child(parent, "DOMVideoItem");
        session.attribute(item, "name", name, d);
        item.setAttribute("videoType", kind >= 0 && kind < types.length ? types[(int) kind] : "unknown");
        item.setAttribute("fps", fps);
        item.setAttribute("rate", rate);
        item.setAttribute("length", length);
        String[] fields = {"bits", "channels", "width", "height"};
        for (int i = 0; i < fields.length; i++) {
            item.setAttribute(fields[i], p.get(sourceFields[i]).toString());
        }
        for (String field : new String[]{"isSpecial", "hasBundledDeviceVideo"}) {
            if (p.get(field) instanceof Number) {
                item.setAttribute(field, Boolean.toString(n(p, field, 0) != 0));
            }
        }
        if (p.get("bundledSourceFilepath") instanceof String) {
            session.attribute(item, "bundledSourceFilepath", s(p, "bundledSourceFilepath", ""), p);
        }
        String dat = "video" + ordinal + ".dat";
        item.setAttribute("videoDataHRef", dat);
        session.itemId(item, d);
        imported(item, d);
        session.files.put("bin/" + dat, media.getStream().getData());
        return name;
    }

    private static String finite(FlaRecord r, String field) throws IOException {
        Object value = r.get(field);
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
            throw new IOException("Invalid video " + field);
        }
        return number(((Number) value).doubleValue());
    }

    private static FlaRecord payload(BinaryFlaDocument.Media media) throws IOException {
        if (media.getStream() == null || media.getStream().getRoot() == null || media.getStream().getParseStatus() != FlaStream.ParseStatus.COMPLETE) {
            throw new IOException("Missing or incomplete media payload.");
        }
        return media.getStream().getRoot();
    }

    private static BinaryData data(FlaRecord r) throws IOException {
        if (r == null || !(r.get("data") instanceof BinaryData)) {
            throw new IOException("Missing audio buffer.");
        }
        return (BinaryData) r.get("data");
    }

    private void imported(Element item, FlaRecord d) {
        session.mediaSourcePlatform(item, d);
        item.setAttribute("sourceLastImported", Long.toString(n(d, "sourceTime", 0)));
        // Retain provenance without interpreting it as a destination path.
        if (!s(d, "sourcePath", "").isEmpty()) {
            session.attribute(item, "sourceExternalFilepath", s(d, "sourcePath", ""), d);
        }
    }

    private static String format(FlaSoundFormat f) {
        return new String[]{"5kHz", "11kHz", "22kHz", "44kHz"}[f.getSampleRateCode()] + " " + f.getBitsPerSample() + "bit " + (f.getChannelCount() == 2 ? "Stereo" : "Mono");
    }

    private static BinaryData wave(BinaryData data, FlaSoundFormat format) throws IOException {
        int block = format.getPcmFrameByteCount(), count = data.size() / block * block;
        ByteArrayOutputStream out = new ByteArrayOutputStream(count + 45);
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        le(out, 36L + count + (count & 1), 4);
        out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        le(out, 16, 4);
        le(out, 1, 2);
        le(out, format.getChannelCount(), 2);
        le(out, format.getSampleRateHz(), 4);
        le(out, (long) format.getSampleRateHz() * block, 4);
        le(out, block, 2);
        le(out, format.getBitsPerSample(), 2);
        out.write("data".getBytes(StandardCharsets.US_ASCII));
        le(out, count, 4);
        byte[] bytes = data.toByteArray();
        out.write(bytes, 0, count);
        if ((count & 1) != 0) {
            out.write(0);
        }
        return new BinaryData(out.toByteArray());
    }

    private static void le(OutputStream out, long value, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            out.write((int) (value >>> (i * 8)) & 255);
        }
    }
}
