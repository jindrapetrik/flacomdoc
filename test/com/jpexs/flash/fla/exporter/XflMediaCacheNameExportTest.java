package com.jpexs.flash.fla.exporter;

import com.jpexs.flash.fla.reader.BinaryFlaDocument;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.testng.Assert.*;
import static com.jpexs.flash.fla.exporter.XflAuthoringExportTest.*;

/**
 * Cached media filenames must survive the native loader's restricted stem.
 */
public class XflMediaCacheNameExportTest {

    @DataProvider(name = "media")
    public Object[][] media() {
        java.util.List<Object[]> cases = new java.util.ArrayList<>();
        for (String version : new String[]{"f5", "mx", "mx2004", "f8", "cs3", "cs4"}) {
            cases.add(new Object[]{version, "0013_bitmaps", "DOMBitmapItem", "bitmapDataHRef", 3});
            cases.add(new Object[]{version, "0014_sounds", "DOMSoundItem", "soundDataHRef", 6});
            if (!version.equals("f5")) {
                cases.add(new Object[]{version, "0015_videos", "DOMVideoItem", "videoDataHRef", 3});
            }
        }
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "media")
    public void everyCacheResolvesUnderTheNativeLoaderStem(String version, String fixture, String tag, String attribute, int count) throws Exception {
        BinaryFlaDocument input = nativeFile(version, fixture);
        XflExportResult result = new BinaryFlaToXflExporter().export(input);
        NodeList items = xml(result).getElementsByTagNameNS("http://ns.adobe.com/xfl/2008/", tag);
        assertEquals(items.getLength(), count, result.getReport());
        for (int i = 0; i < items.getLength(); i++) {
            String reference = ((Element) items.item(i)).getAttribute(attribute);
            String nativePath = "bin/" + nativeCacheName(reference);
            assertTrue(result.getFiles().containsKey(nativePath), "Native cache lookup cannot resolve " + reference + " as " + nativePath);
            assertEquals(result.getFiles().get(nativePath).toByteArray(), input.getMedia().get(i).getStream().getData().toByteArray(), "Only the name may change; cached media bytes remain exact");
        }
    }

    // The native media loader normalizes only the stem, before keeping .dat.
    private static String nativeCacheName(String reference) {
        int dot = reference.lastIndexOf('.');
        assertTrue(dot > 0);
        StringBuilder stem = new StringBuilder();
        for (int i = 0; i < dot; i++) {
            char c = reference.charAt(i);
            stem.append(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ' ' ? c : '0');
        }
        return stem + reference.substring(dot);
    }
}
