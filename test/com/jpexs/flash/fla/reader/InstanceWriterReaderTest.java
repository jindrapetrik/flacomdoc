package com.jpexs.flash.fla.reader;

import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.TimelineConverter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import static org.testng.Assert.*;

public class InstanceWriterReaderTest {

    @DataProvider(name = "targets")
    public Object[][] targets() {
        Object[][] result = new Object[FlaFormatVersion.values().length * 4][2];
        int i = 0;
        for (FlaFormatVersion target : FlaFormatVersion.values()) {
            for (String kind : new String[]{"graphic", "movie clip", "button", "text"}) {
                result[i++] = new Object[]{target, kind};
            }
        }
        return result;
    }

    @Test(dataProvider = "targets")
    public void readsTextAndInstancesProducedByEveryExistingSaveTarget(FlaFormatVersion version, String kind) throws Exception {
        String element = kind.equals("text")
                ? "<DOMStaticText width='100' height='20'><textRuns><DOMTextRun><characters>Hello</characters>"
                + "<textAttrs><DOMTextAttrs face='TimesNewRomanPSMT' size='12' fillColor='#123456'/></textAttrs></DOMTextRun></textRuns></DOMStaticText>"
                : "<DOMSymbolInstance libraryItemName='item' symbolType='" + kind + "' name='instance'/>";
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        new TimelineConverter(version, "windows-1250", "test").convert(xml("<DOMTimeline><layers><DOMLayer><frames>"
                + "<DOMFrame index='0' duration='1'><elements>" + element + "</elements></DOMFrame></frames></DOMLayer></layers></DOMTimeline>"),
                xml("<DOMDocument><symbols><Include href='item.xml'/></symbols></DOMDocument>"), page, null);
        Map<String, byte[]> streams = new LinkedHashMap<>();
        streams.put("Contents", new byte[]{1, 0, 0});
        streams.put("Timeline", page.toByteArray());
        FlaStream stream = new BinaryFlaReader(RecordTestSupport.ANSI).readStreams(streams).getStream("Timeline");
        String expected = kind.equals("text") ? "CPicText" : kind.equals("graphic") ? "CPicSymbol" : kind.equals("button") ? "CPicButton" : "CPicSprite";
        FlaRecord instance = null;
        for (FlaRecord record : stream.getObjects()) {
            if (record.getClassName().equals(expected)) {
                instance = record;
                break;
            }
        }
        assertNotNull(instance, expected);
        assertTrue(instance.isComplete(), stream.getIssues().toString());
        if (kind.equals("text")) {
            assertEquals(instance.get("text"), "Hello");
        } else {
            assertEquals(instance.get("libraryId"), 1L);
        }
        assertEquals(stream.getParseStatus(), FlaStream.ParseStatus.COMPLETE, stream.getIssues().toString());
        for (FlaParseIssue issue : stream.getIssues()) {
            assertNotEquals(issue.getKind(), FlaParseIssue.Kind.MALFORMED, issue.toString());
        }
    }

    private Element xml(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }
}
