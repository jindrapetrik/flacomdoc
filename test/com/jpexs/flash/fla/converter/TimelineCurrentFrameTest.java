package com.jpexs.flash.fla.converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class TimelineCurrentFrameTest {
    @Test
    public void currentFrameUsesItsOwnDword() throws Exception {
        for (FlaFormatVersion version : new FlaFormatVersion[] {
            FlaFormatVersion.F8, FlaFormatVersion.CS3, FlaFormatVersion.CS4
        }) {
            for (int frame : new int[] {0, 3, 36, 255, 256, 4096}) {
                byte[] bytes = timeline(version, frame);
                ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                int tail = bytes.length - 13;
                assertEquals(data.get(tail) & 255, 7);
                assertEquals(data.getShort(tail + 1), (short) 1, "Layer counter");
                assertEquals(data.getShort(tail + 3), (short) 1, "Folder counter must not contain currentFrame");
                assertEquals(data.getInt(tail + 5), frame, version + ": currentFrame");
                assertEquals(data.getInt(tail + 9), 0, "Guide count");
            }
        }
    }

    @Test
    public void olderPageRevisionsKeepTheirOriginalLayout() throws Exception {
        for (FlaFormatVersion version : new FlaFormatVersion[] {FlaFormatVersion.MX, FlaFormatVersion.MX2004}) {
            byte[] zero = timeline(version, 0);
            byte[] selected = timeline(version, 256);
            // Frame IDs are random, so check the page extension only.
            int length = version == FlaFormatVersion.MX ? 7 : 9;
            assertEquals(java.util.Arrays.copyOfRange(selected, selected.length - length, selected.length),
                    java.util.Arrays.copyOfRange(zero, zero.length - length, zero.length));
            assertEquals(selected[selected.length - length] & 255, version.pageVersionB);
        }
    }

    private byte[] timeline(FlaFormatVersion version, int currentFrame) throws Exception {
        Element timeline = xml("<DOMTimeline name='Scene' currentFrame='" + currentFrame + "'>"
                + "<layers><DOMLayer name='Layer'><frames><DOMFrame index='0' duration='5000' keyMode='9728'>"
                + "<elements/></DOMFrame></frames></DOMLayer></layers></DOMTimeline>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TimelineConverter(version, "windows-1250", "test").convert(timeline, xml("<DOMDocument/>"), out, null);
        return out.toByteArray();
    }

    private Element xml(String value) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(value.getBytes("UTF-8"))).getDocumentElement();
    }
}
