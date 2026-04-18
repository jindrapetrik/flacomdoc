package com.jpexs.flash.fla.converter.coloreffects.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;

/**
 *
 * @author JPEXS
 */
public class JpegImageBinToFlash23Convertor {

    private final InputStream is;

    public JpegImageBinToFlash23Convertor(InputStream is) {
        this.is = is;
    }

    public void convertTo(OutputStream os, int version) throws IOException {
        if (version != 2 && version != 3) {
            throw new IllegalArgumentException("Version " + version + " is not supported");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int cnt;
        while ((cnt = is.read(buf)) > 0) {
            baos.write(buf, 0, cnt);
        }
        byte[] data = baos.toByteArray();

        byte[] jpegData = Arrays.copyOf(data, data.length - 4 * 4);
        byte[] rectArr = Arrays.copyOfRange(data, data.length - 4 * 4, data.length);

        ByteArrayInputStream bais = new ByteArrayInputStream(rectArr);
        int left = bais.read() + (bais.read() << 8) + (bais.read() << 16) + (bais.read() << 24);
        int right = bais.read() + (bais.read() << 8) + (bais.read() << 16) + (bais.read() << 24);
        int top = bais.read() + (bais.read() << 8) + (bais.read() << 16) + (bais.read() << 24);
        int bottom = bais.read() + (bais.read() << 8) + (bais.read() << 16) + (bais.read() << 24);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegData));

        int width = img.getWidth();
        int height = img.getHeight();

        os.write(version);
        os.write(0x05);

        int decRowLen = 4 * img.getWidth();
        writeUI16(os, decRowLen);

        writeUI16(os, img.getWidth());
        writeUI16(os, img.getHeight());

        writeUI32(os, 0);
        writeUI32(os, 20 * img.getWidth());
        writeUI32(os, 0);
        writeUI32(os, 20 * img.getHeight());

        if (version == 3) {
            os.write(0x00); //has transparency
        }
        os.write(0x01); //compressed variant

        baos = new ByteArrayOutputStream();
        DeflaterOutputStream def = new DeflaterOutputStream(baos, new Deflater(1));

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgba = img.getRGB(x, y);
                int a = (rgba >> 24) & 0xFF;
                int b = (rgba >> 16) & 0xFF;
                int g = (rgba >> 8) & 0xFF;
                int r = rgba & 0xFF;

                //some weird premultiplication
                if (a != 255) {
                    r = (int) Math.floor(r * a / 256f);
                    g = (int) Math.floor(g * a / 256f);
                    b = (int) Math.floor(b * a / 256f);
                }

                //also weird, but this way it works...
                if (a != 0 && a != 255) {
                    a = a + 1;
                }

                def.write(0);
                def.write(b);
                def.write(g);
                def.write(r);
            }
        }
        def.flush();
        def.finish();
        data = baos.toByteArray();
        int pos = 0;
        while (pos < data.length) {
            cnt = 2048; //it seems that using large chunk sizes like 0xFFFF crashes flash. 2024 is used in CS5.
            if (pos + cnt > data.length) {
                cnt = data.length - pos;
            }
            writeUI16(os, cnt);
            os.write(data, pos, cnt);
            pos += cnt;
        }
        writeUI16(os, 0);
    }

    private void writeUI16(OutputStream os, int val) throws IOException {
        os.write(val & 0xFF);
        os.write((val >> 8) & 0xFF);
    }

    private void writeUI32(OutputStream os, long val) throws IOException {
        os.write((int) (val & 0xFF));
        os.write((int) ((val >> 8) & 0xFF));
        os.write((int) ((val >> 16) & 0xFF));
        os.write((int) ((val >> 24) & 0xFF));
    }
}
