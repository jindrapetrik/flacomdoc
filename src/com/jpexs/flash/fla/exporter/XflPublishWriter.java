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
import java.io.IOException;
import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * The publish profile file has no XFL namespace. Values are never executed.
 */
final class XflPublishWriter {

    private static final String[][] GROUPS = {
        {"PublishFormatProperties", "defaultNames flash projectorWin projectorMac html gif jpeg png qt rnwk flashDefaultName projectorWinDefaultName projectorMacDefaultName htmlDefaultName gifDefaultName jpegDefaultName pngDefaultName qtDefaultName rnwkDefaultName flashFileName projectorWinFileName projectorMacFileName htmlFileName gifFileName jpegFileName pngFileName qtFileName rnwkFileName"},
        {"PublishHtmlProperties", "VersionDetectionIfAvailable VersionInfo UsingDefaultContentFilename UsingDefaultAlternateFilename ContentFilename AlternateFilename UsingOwnAlternateFile OwnAlternateFilename Width Height Align Units Loop StartPaused Scale HorizontalAlignment VerticalAlignment Quality DeblockingFilter WindowMode DisplayMenu DeviceFont TemplateFileName showTagWarnMsg"},
        {"PublishFlashProperties", "TopDown FireFox Report Protect OmitTraceActions Quality DeblockingFilter StreamFormat StreamCompress EventFormat EventCompress OverrideSounds Version ExternalPlayer ActionScriptVersion PackageExportFrame PackagePaths AS3PackagePaths AS3ConfigConst DebuggingPermitted DebuggingPassword CompressMovie InvisibleLayer DeviceSound StreamUse8kSampleRate EventUse8kSampleRate UseNetwork DocumentClass AS3Strict AS3Coach AS3AutoDeclare AS3Dialect AS3ExportFrame AS3Optimize ExportSwc ScriptStuckDelay IncludeXMP HardwareAcceleration AS3Flags DefaultLibraryLinkage RSLPreloaderMethod RSLPreloaderSWF AS3LibraryPaths AS3ExternalLibraryPaths"},
        {"PublishJpegProperties", "Width Height Progressive DPI Size Quality MatchMovieDim"},
        {"PublishRNWKProperties", "exportFlash flashBitRate exportAudio audioFormat singleRateAudio realVideoRate speed28K speed56K speedSingleISDN speedDualISDN speedCorporateLAN speed256K speed384K speed512K exportSMIL"},
        {"PublishGifProperties", "Width Height Animated MatchMovieDim Loop LoopCount OptimizeColors Interlace Smooth DitherSolids RemoveGradients TransparentOption TransparentAlpha DitherOption PaletteOption MaxColors PaletteName"},
        {"PublishPNGProperties", "Width Height OptimizeColors Interlace Transparent Smooth DitherSolids RemoveGradients MatchMovieDim DitherOption FilterOption PaletteOption BitDepth MaxColors PaletteName"},
        {"PublishQTProperties", "Width Height MatchMovieDim UseQTSoundCompression AlphaOption LayerOption QTSndSettings ControllerOption Looping PausedAtStart PlayEveryFrame Flatten"},};
    private final BinaryFlaToXflExporter.Session session;

    XflPublishWriter(BinaryFlaToXflExporter.Session session) {
        this.session = session;
    }

    void write(FlaRecord settings) throws IOException {
        if (settings == null) {
            return;
        }
        if (!settings.isComplete()) {
            session.issue("PUBLISH_SETTINGS", settings, "Incomplete publish profiles omitted.");
            return;
        }
        List<FlaRecord> profiles = records(settings, "profiles");
        FlaRecord legacy = record(settings, "legacyProperties");
        if (legacy != null) {
            profiles = Collections.singletonList(legacy);
        }
        if (profiles.isEmpty()) {
            return;
        }
        Document xml = document("temporary");
        xml.removeChild(xml.getDocumentElement());
        Element root = xml.createElement("flash_profiles");
        xml.appendChild(root);
        Set<String> names = new HashSet<>();
        String activeName = values(profiles.get(profiles.size() - 1)).get("PublishProfileProperties::name");
        Element active = null, last = null;
        for (int i = 0; i < profiles.size(); i++) {
            FlaRecord profile = profiles.get(i);
            if (!profile.isComplete()) {
                session.issue("PUBLISH_SETTINGS", profile, "Incomplete publish profile omitted.");
                continue;
            }
            Map<String, String> values = values(profile);
            // Empty legacy profiles otherwise inherit CS6's AS3 default,
            // disabling the old document's component and instance scripts.
            int revision = session.source.getContentsRevision();
            if (revision <= 0x3F && key(values, "PublishFlashProperties", "ActionScriptVersion") == null) {
                values.put("Vector::ActionScriptVersion", revision <= 0x29 ? "1" : "2");
            }
            String name = values.get("PublishProfileProperties::name");
            if (name == null || name.isEmpty()) {
                name = "Recovered profile " + (i + 1);
                session.issue("PUBLISH_PROFILE_NAME", profile, "Missing profile name replaced with " + name);
            }
            if (!names.add(name)) {
                session.issue("PUBLISH_PROFILE_NAME", profile, "Duplicate publish profile name omitted: " + name);
                continue;
            }
            Element out = append(root, "flash_profile");
            session.attribute(out, "name", name, profile);
            out.setAttribute("version", "1.0");
            last = out;
            if (name.equals(activeName)) {
                active = out;
            }
            objects(values, i);
            String version = values.get("PublishProfileProperties::version");
            if (version != null && !version.equals("1") && !version.equals("1.0")) {
                session.issue("PUBLISH_PROFILE_VERSION", profile, "Unknown stored profile version " + version + "; native XML version 1.0 used.");
            }
            Set<String> consumed = new HashSet<>(Arrays.asList("PublishProfileProperties::name", "PublishProfileProperties::version"));
            for (String[] group : GROUPS) {
                Element element = xml.createElement(group[0]);
                element.setAttribute("enabled", "true");
                for (String property : group[1].split(" ")) {
                    String key = key(values, group[0], property);
                    if (key == null) {
                        continue;
                    }
                    consumed.add(key);
                    String value = values.get(key);
                    if (property.equals("RSLPreloaderMethod") || property.equals("DefaultLibraryLinkage")) {
                        String[] options = property.equals("RSLPreloaderMethod") ? new String[]{"wrap", "event"} : new String[]{"rsl", "merge"};
                        if (value.equals("0") || value.equals("1")) {
                            value = options[Integer.parseInt(value)];
                        } else if (!Arrays.asList(options).contains(value)) {
                            session.issue("PUBLISH_PROPERTY", profile, "Unknown " + property + " selector " + value);
                            continue;
                        }
                    }
                    append(element, property).setTextContent(session.text(value, profile));
                }
                if (element.hasChildNodes()) {
                    out.appendChild(element);
                }
            }
            for (String key : values.keySet()) {
                if (!consumed.contains(key)) {
                    session.issue("PUBLISH_PROPERTY", profile, "Unmapped publish property retained in the source model: " + key);
                }
            }
        }
        // Loading appends profiles and selects each newly appended profile. The
        // active index is transient; no extra archive selection field exists.
        if (active == null) {
            active = last;
            if (profiles.size() > 1 && last != null) {
                session.issue("PUBLISH_PROFILE_SELECTION", settings, "Native last-profile selection could not be resolved; last recoverable profile selected.");
            }
        }
        if (active != null) {
            active.setAttribute("current", "true");
        }
        if (root.hasChildNodes()) {
            session.files.put("PublishSettings.xml", new BinaryData(serialize(xml)));
        }
    }

    private void objects(Map<String, String> values, int index) {
        List<FlaRecord> profiles = records(session.source.getContents(), "profileObjects");
        if (index >= profiles.size()) {
            return;
        }
        for (FlaRecord entry : records(profiles.get(index), "entries")) {
            FlaRecord value = record(entry, "value");
            if (value == null) {
                continue;
            }
            String key = s(entry, "key", "");
            int end = key.indexOf('\0');
            if (end >= 0) {
                key = key.substring(0, end);
            }
            if (key.equals("PublishQTProperties::QTSndSettings") && "CQTAudioSettings".equals(value.getClassName()) && value.isComplete() && value.get("data") instanceof BinaryData) {
                BinaryData data = (BinaryData) value.get("data");
                long size = data.size();
                if (size != n(value, "byteCount", -1) || size > 4092) {
                    session.issue("PUBLISH_OBJECT", entry, "QuickTime audio settings exceed the native XML reader's 4096-byte archive limit or have an inconsistent byte count.");
                    continue;
                }
                // The XML reader decodes two hex digits per archive byte. Preserve
                // that byte stream, including its length prefix; reading words
                // here would swap/group bytes and corrupt nonempty settings.
                StringBuilder hex = new StringBuilder((int) (size + 4) * 2);
                for (int i = 0; i < 4; i++) {
                    hexByte(hex, (int) (size >>> (8 * i)));
                }
                for (byte b : data.toByteArray()) {
                    hexByte(hex, b);
                }
                values.put(key, hex.toString());
            } else {
                session.issue("PUBLISH_OBJECT", entry, "Object-valued publish setting has no verified XML mapping: " + key);
            }
        }
    }

    private static void hexByte(StringBuilder text, int value) {
        String digits = "0123456789abcdef";
        text.append(digits.charAt(value >>> 4 & 15)).append(digits.charAt(value & 15));
    }

    private static String key(Map<String, String> values, String group, String property) {
        String prefix = group.equals("PublishFlashProperties") ? "Vector::" : group + "::";
        // Native XML property labels remove spaces from the binary Vector keys.
        for (String key : values.keySet()) {
            if (key.startsWith(prefix)) {
                String suffix = key.substring(prefix.length()).replace(" ", "");
                if (suffix.equals(property) || property.equals("ExportSwc") && suffix.equals("ExportSWC")) {
                    return key;
                }
            }
        }
        return null;
    }

    private static Map<String, String> values(FlaRecord record) {
        Map<String, String> result = new LinkedHashMap<>();
        for (FlaRecord entry : records(record, "entries")) {
            String key = s(entry, "key", "");
            int end = key.indexOf('\0');
            if (end >= 0) {
                key = key.substring(0, end);
            }
            result.put(key, s(entry, "value", ""));
        }
        return result;
    }

    private static Element append(Element parent, String name) {
        Element result = parent.getOwnerDocument().createElement(name);
        parent.appendChild(result);
        return result;
    }
}
