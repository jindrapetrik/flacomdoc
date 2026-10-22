package com.jpexs.flash.fla.reader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class RecordTestSupport {

    static final Charset ANSI = Charset.forName("windows-1250");

    private RecordTestSupport() {
    }

    static void u16(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
    }

    static void u32(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 4; i++) {
            out.write((int) (value >> (8 * i)));
        }
    }

    static void f32(ByteArrayOutputStream out, float value) {
        u32(out, Float.floatToRawIntBits(value));
    }

    static void f64(ByteArrayOutputStream out, double value) {
        long bits = Double.doubleToRawLongBits(value);
        u32(out, bits);
        u32(out, bits >>> 32);
    }

    static void string(ByteArrayOutputStream out, String value, boolean unicode) throws IOException {
        if (unicode) {
            out.write(255);
            u16(out, 0xFFFE);
        }
        int length = value.length();
        if (length < 255) {
            out.write(length);
        } else {
            out.write(255);
            u16(out, length);
        }
        out.write(value.getBytes(unicode ? StandardCharsets.UTF_16LE : ANSI));
    }

    static void base(ByteArrayOutputStream out) {
        out.write(1);
        out.write(0);
        u16(out, 0);
        u32(out, 0);
        u32(out, 0);
    }

    static void words(ByteArrayOutputStream out, int count) {
        for (int i = 0; i < count; i++) {
            u32(out, i * 100 - 250);
        }
    }

    static byte[] object(String name, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u16(out, 0xFFFF);
        u16(out, 1);
        u16(out, name.length());
        out.write(name.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        return out.toByteArray();
    }

    static MfcArchive archive(byte[] bytes) {
        return new MfcArchive(new BinaryData(bytes), "test", new FlaReadOptions(ANSI, 1024 * 1024, 1024 * 1024, 1000, 128), DefaultFlaDecoders.create(), 0);
    }

    @SuppressWarnings("unchecked")
    static List<FlaRecord> records(FlaRecord record, String field) {
        return (List<FlaRecord>) record.get(field);
    }

    static void access(ByteArrayOutputStream out, int revision, boolean unicodeContext) throws IOException {
        u32(out, revision);
        if (revision == 0) {
            return;
        }
        u32(out, 0x80000001L);
        string(out, "name", unicodeContext);
        string(out, "description", unicodeContext);
        string(out, "shortcut", unicodeContext);
        if (revision >= 2 && unicodeContext) {
            string(out, "7", true);
            string(out, "reserved", true);
        }
        u32(out, 0x80000000L);
        u32(out, 0xFFFFFFFFL);
    }
}
