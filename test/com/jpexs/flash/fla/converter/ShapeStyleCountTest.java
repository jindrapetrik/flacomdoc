package com.jpexs.flash.fla.converter;

import com.jpexs.helpers.Reference;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ShapeStyleCountTest {
    @Test
    public void fillCountsCrossTheByteBoundary() throws Exception {
        checkShape(255, 0);
        checkShape(256, 0);
    }

    @Test
    public void strokeCountsCrossTheByteBoundary() throws Exception {
        checkShape(0, 255);
        checkShape(0, 256);
        checkShape(256, 256);
    }

    private void checkShape(int fills, int strokes) throws Exception {
        StringBuilder xml = new StringBuilder("<DOMShape><fills>");
        for (int i = 1; i <= fills; i++) {
            xml.append("<FillStyle index='").append(i).append("'><SolidColor color='#123456'/></FillStyle>");
        }
        xml.append("</fills><strokes>");
        for (int i = 1; i <= strokes; i++) {
            xml.append("<StrokeStyle index='").append(i).append("'><SolidStroke weight='1'>")
                    .append("<fill><SolidColor color='#123456'/></fill></SolidStroke></StrokeStyle>");
        }
        xml.append("</strokes><edges/></DOMShape>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TimelineConverter converter = new TimelineConverter(FlaFormatVersion.CS4, "windows-1250", "test");
        Method shape = TimelineConverter.class.getDeclaredMethod("handleShape", Element.class,
                Element.class, FlaWriter.class, boolean.class, Map.class, Reference.class);
        shape.setAccessible(true);
        shape.invoke(converter, element(xml.toString()), element("<DOMDocument/>"),
                new FlaWriter(out, FlaFormatVersion.CS4, "windows-1250"), false,
                new HashMap<String, Integer>(), new Reference<Integer>(0));

        ByteBuffer data = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        // Plain CS4 CPicObj/CPicShape header, including its six-dword matrix.
        data.position(37);
        assertEquals(data.get() & 255, 5, "ShapeData revision");
        assertEquals(data.getInt(), 0);
        assertEquals(data.getShort() & 65535, fills);
        for (int i = 0; i < fills; i++) {
            assertEquals(data.getInt(), 0xff563412);
            assertEquals(data.getShort(), (short) 0);
        }
        assertEquals(data.getShort() & 65535, strokes);
        for (int i = 0; i < strokes; i++) {
            assertEquals(data.getInt(), 0xff563412);
            assertEquals(data.getShort() & 65535, 20);
            data.position(data.position() + 10); // Parameters and extended stroke settings.
            assertEquals(data.getInt(), 0xff563412);
            assertEquals(data.getShort(), (short) 0);
        }
        assertEquals(data.get(), (byte) 0, "Edge terminator");
        assertEquals(data.getInt(), 0, "Auxiliary count");
        assertFalse(data.hasRemaining(), "Every declared style must have been consumed");
    }

    private Element element(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes("UTF-8"))).getDocumentElement();
    }
}
