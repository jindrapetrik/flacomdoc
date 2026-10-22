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
import java.nio.charset.StandardCharsets;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Document editor preferences and archived printer resources.
 */
final class XflDocumentWriter {

    private final BinaryFlaToXflExporter.Session session;

    XflDocumentWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void attributes(Element root, FlaRecord r) {
        color(root, r, "gridColor", "gridColorRgba");
        color(root, r, "guidesColor", "guidesColorRgba");
        if (r.get("nextSceneId") instanceof Number) {
            root.setAttribute("nextSceneIdentifier", Long.toString(n(r, "nextSceneId", 0)));
        }
        if (r.get("gridDisplayMode") instanceof Number) {
            root.setAttribute("gridVisible", Boolean.toString(n(r, "gridDisplayMode", 0) == 3));
        }
        choice(root, r, "rulerUnitType", "rulerUnitType", new String[]{"inches", "decimal inches", "points", "centimeters", "millimeters", "pixels"});
        grid(root, r, "gridSpacingX", "gridSpacingXFixed16Twips");
        grid(root, r, "gridSpacingY", "gridSpacingYFixed16Twips");
        choice(root, r, "gridSnapAccuracy", "gridSnapAccuracy", new String[]{"Must be close", "Normal", "Can be distant", "Always snap"});
        choice(root, r, "guidesSnapAccuracy", "guidesSnapAccuracy", new String[]{"Must be close", "Normal", "Can be distant"});
        String[][] flags = {{"gridSnapTo", "gridSnapTo"}, {"objectsSnapTo", "objectsSnapTo"}, {"guidesLocked", "guidesLocked"},
        {"guidesVisible", "guidesVisible"}, {"guidesSnapTo", "guidesSnapTo"}, {"pixelSnap", "pixelSnap"},
        {"snapAlign", "snapAlign"}, {"snapAlignVerticalCenter", "snapAlignVerticalCenter"}, {"snapAlignHorizontalCenter", "snapAlignHorizontalCenter"}};
        for (String[] f : flags) {
            if (r.get(f[1]) instanceof Number) {
                root.setAttribute(f[0], Boolean.toString(n(r, f[1], 0) != 0));
            }
        }
        String[][] spacing = {{"snapAlignHorizontalSpacing", "snapAlignHorizontalSpacing"}, {"snapAlignVerticalSpacing", "snapAlignVerticalSpacing"}, {"snapAlignBorderSpacing", "snapAlignBorderSpacing"}};
        for (String[] f : spacing) {
            if (r.get(f[1]) instanceof Number) {
                root.setAttribute(f[0], Long.toString(n(r, f[1], 0)));
            }
        }
        byte[] view = bytes(r, "previewAndRulerFlags");
        if (view.length == 3) {
            String[] modes = {"outlines", "fast", "anti alias", "anti alias text", "full"};
            int mode = view[0] & 255;
            if (mode < modes.length) {
                root.setAttribute("previewMode", modes[mode]);
            } else {
                session.issue("DOCUMENT_SETTING", r, "Unassigned preview mode " + mode);
            }
            root.setAttribute("rulerVisible", Boolean.toString(view[1] != 0));
        }
        byte[] options = bytes(r, "viewPlayAndPrinterFlags");
        if (options.length == 2) {
            String[] names = {"viewOptionsAnimationControlVisible", "viewOptionsButtonsActive", "viewOptionsPasteBoardView", "viewOptionsLivePreview",
                "playOptionsPlayLoop", "playOptionsPlayPages", "playOptionsPlayFrameActions", "playOptionsPlaySounds"};
            int bits = options[0] & 255;
            long revision = n(r, "revision", 0);
            if (revision < 0x28) {
                bits |= 8;
            }
            if (revision <= 0x10) {
                bits |= 0x80;
            }
            for (int i = 0; i < names.length; i++) {
                root.setAttribute(names[i], Boolean.toString((bits & (1 << i)) != 0));
            }
        }
        if (r.get("sharedLibraryUrl") instanceof String) {
            session.attribute(root, "sharedLibraryURL", s(r, "sharedLibraryUrl", ""), r);
        }
        if (r.get("viewAngle3D") instanceof Number) {
            double angle = ((Number) r.get("viewAngle3D")).doubleValue();
            if (Double.isFinite(angle)) {
                root.setAttribute("viewAngle3D", number(bounded(r, "viewAngle3D", angle, 1, 179)));
            } else {
                session.issue("DOCUMENT_SETTING", r, "Nonfinite 3D view angle omitted.");
            }
        }
        if (r.get("adjustViewAngle3D") instanceof Number) {
            root.setAttribute("adjustViewAngle3D", Boolean.toString(n(r, "adjustViewAngle3D", 1) != 0));
        }
        Object rawPoint = r.get("vanishingPointTwips");
        if (rawPoint != null) {
            if (rawPoint instanceof java.util.List && ((java.util.List<?>) rawPoint).size() == 2
                    && ((java.util.List<?>) rawPoint).get(0) instanceof Integer && ((java.util.List<?>) rawPoint).get(1) instanceof Integer) {
                java.util.List<?> point = (java.util.List<?>) rawPoint;
                root.setAttribute("vanishingPoint3DX", number(bounded(r, "vanishingPoint3DX", ((Integer) point.get(0)) / 20.0, -115200, 115200)));
                root.setAttribute("vanishingPoint3DY", number(bounded(r, "vanishingPoint3DY", ((Integer) point.get(1)) / 20.0, -115200, 115200)));
            } else {
                session.issue("DOCUMENT_SETTING", r, "Incomplete 3D vanishing point omitted.");
            }
        }
        XflObjectWriter.accessibility(session, root, r);
    }

    void externalXml(FlaRecord r) {
        xmlFile(r, "metadataXml", "META-INF/metadata.xml");
        xmlFile(r, "mobileSettingsXml", "MobileSettings.xml");
    }

    private void xmlFile(FlaRecord r, String field, String path) {
        String xml = s(r, field, "");
        if (!xml.isEmpty()) {
            session.files.put(path, new BinaryData(xml.getBytes(StandardCharsets.UTF_8)));
        }
    }

    void printer(Element root, FlaRecord r) {
        Element printer = child(root, "PrinterSettings");
        for (String side : new String[]{"Left", "Top", "Right", "Bottom"}) {
            String field = "printMargin" + side + "Twips";
            if (r.get(field) instanceof Number) {
                printer.setAttribute("pageMargin" + side, number(bounded(r, "pageMargin" + side,
                        n(r, field, 360), 239, 230400)));
            }
        }
        String[][] flags = {{"printCenterHorizontal", "centerHorizontal"}, {"printCenterVertical", "centerVertical"},
        {"printDisablePostscript", "disablePostscript"}};
        for (String[] flag : flags) {
            if (r.get(flag[0]) instanceof Number) {
                printer.setAttribute(flag[1], Boolean.toString(n(r, flag[0], 0) != 0));
            }
        }
        choice(printer, r, "layout", "printLayout", new String[]{"actual size", "fit on one page",
            "storyboard - boxes", "storyboard - grid", "storyboard - blank"});
        if (r.get("printScaleFixed16") instanceof Number) {
            printer.setAttribute("scale", number(bounded(r, "print scale", n(r, "printScaleFixed16", 65536) * 100.0 / 65536.0, 1, 10000)));
        }
        if (r.get("printAllFrames") instanceof Number) {
            printer.setAttribute("framesToPrint", n(r, "printAllFrames", 0) != 0 ? "all frames" : "first frame only");
        }
        if (r.get("printLabelPages") instanceof Number) {
            printer.setAttribute("labelPages", Boolean.toString(n(r, "printLabelPages", 0) != 0));
        }
        if (r.get("printFrameCountAcrossPage") instanceof Number) {
            printer.setAttribute("frameCountAcrossPage", number(bounded(r, "frameCountAcrossPage",
                    n(r, "printFrameCountAcrossPage", 3), 1, 128)));
        }
        if (r.get("printStoryboardMarginTwips") instanceof Number) {
            // PrinterSettings keeps this distance in whole twips.
            printer.setAttribute("storyBoardMargin", number(bounded(r, "storyBoardMargin",
                    n(r, "printStoryboardMarginTwips", 180), 0, 57600)));
        }
        byte[] options = bytes(r, "viewPlayAndPrinterFlags");
        if (options.length == 2 && options[1] != 0) {
            if (bytes(r, "printerModeData").length != 0 || bytes(r, "printerNamesData").length != 0) {
                session.issue("PRINTER_SETTINGS", r, "Cross-platform printer blobs are discarded by the native compatibility loader.");
            }
            return;
        }
        String[] fields = {"printerModeData", "printerNamesData"}, names = {"mode.prtdata", "names.prtdata"}, attrs = {"modeData", "namesData"};
        for (int i = 0; i < fields.length; i++) {
            Object value = r.get(fields[i]);
            if (value instanceof BinaryData && ((BinaryData) value).size() != 0) {
                session.files.put(names[i], (BinaryData) value);
                printer.setAttribute(attrs[i], names[i]);
            }
        }
    }

    private void grid(Element root, FlaRecord r, String attr, String field) {
        if (!(r.get(field) instanceof Number)) {
            return;
        }
        // Stored 16.16 twips; native XFL authoring rounds to whole twips.
        int fixed = (int) n(r, field, 0);
        int twips = (fixed + 0x8000) >> 16;
        if (twips > 0) {
            root.setAttribute(attr, number(twips / 20.0));
        } else {
            session.issue("DOCUMENT_SETTING", r, "Nonpositive " + attr + " omitted.");
        }
    }

    private double bounded(FlaRecord r, String attribute, double value, double min, double max) {
        double normalized = Math.max(min, Math.min(max, value));
        if (normalized != value) {
            session.issue("DOCUMENT_SETTING", r, attribute + " clamped to the native XFL range.");
        }
        return normalized;
    }

    private void choice(Element root, FlaRecord r, String attr, String field, String[] names) {
        if (!(r.get(field) instanceof Number)) {
            return;
        }
        long index = n(r, field, -1);
        if (index >= 0 && index < names.length) {
            root.setAttribute(attr, names[(int) index]);
        } else {
            session.issue("DOCUMENT_SETTING", r, "Unassigned " + attr + " value " + index);
        }
    }

    private static void color(Element root, FlaRecord r, String attr, String field) {
        if (r.get(field) instanceof Number) {
            root.setAttribute(attr, rgb(n(r, field, 0)));
        }
    }

    private static byte[] bytes(FlaRecord r, String field) {
        Object data = r.get(field);
        return data instanceof BinaryData ? ((BinaryData) data).toByteArray() : new byte[0];
    }
}
