package com.jpexs.flash.fla.converter.coloreffects.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 *
 * @author JPEXS
 */
public class JpegImageBinToFlash1Convertor {
    private final InputStream is;

    public JpegImageBinToFlash1Convertor(InputStream is) {
        this.is = is;
    }
    
    public void convertTo(OutputStream os) throws IOException {
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
        
        os.write(0x00);
        os.write(0x05);
        int decRowLen = 4 * width;
        writeUI16(os, decRowLen);

        writeUI16(os, width);
        writeUI16(os, height);

        writeUI32(os, left);
        writeUI32(os, right);
        writeUI32(os, top);
        writeUI32(os, bottom);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
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
                
                os.write(0);
                os.write(b);
                os.write(g);
                os.write(r);
            }
        }
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
