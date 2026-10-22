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
package com.jpexs.flash.fla.converter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds SFNT fonts by PostScript name without native APIs or font
 * substitutions.
 */
final class SystemFontFiles {

    private final List<File> directories;
    private Map<String, FontFile> fonts;
    private final Map<String, FontMetadata> metadata = new HashMap<>();

    SystemFontFiles(List<File> directories) {
        this.directories = new ArrayList<>(directories);
    }

    private static final class InstalledFonts {

        static final SystemFontFiles INSTANCE = new SystemFontFiles(getSystemFontDirectories());
    }

    static SystemFontFiles getInstance() {
        return InstalledFonts.INSTANCE;
    }

    synchronized FontMetadata getMetadata(String postScriptName) {
        if (postScriptName == null || postScriptName.isEmpty()) {
            return FontMetadata.UNKNOWN;
        }
        FontMetadata result = metadata.get(postScriptName);
        if (result != null) {
            return result;
        }
        if (fonts == null) {
            fonts = getInstalledFontFiles();
        }
        FontFile font = fonts.get(postScriptName);
        result = FontMetadata.UNKNOWN;
        if (font != null) {
            try {
                result = FontMetadata.read(font.file, font.offset);
            } catch (IOException | SecurityException ex) {
                // An unavailable or damaged font must not prevent conversion.
            }
        }
        metadata.put(postScriptName, result);
        return result;
    }

    private Map<String, FontFile> getInstalledFontFiles() {
        Map<String, FontFile> result = new LinkedHashMap<>();
        for (File file : getSystemFontFiles()) {
            try {
                for (Map.Entry<String, Long> name : FontMetadata.readPostScriptNames(file).entrySet()) {
                    result.putIfAbsent(name.getKey(), new FontFile(file, name.getValue()));
                }
            } catch (IOException | SecurityException ex) {
                // Ignore unreadable or unsupported font files.
            }
        }
        return result;
    }

    List<File> getSystemFontFiles() {
        List<File> result = new ArrayList<>();
        Set<File> visited = new HashSet<>();
        for (File directory : directories) {
            getFontFilesRecursively(directory, result, visited);
        }
        return result;
    }

    private static void getFontFilesRecursively(File directory, List<File> result, Set<File> visited) {
        Deque<File> pending = new ArrayDeque<>();
        pending.push(directory);
        while (!pending.isEmpty()) {
            File file = pending.pop();
            try {
                // Canonical paths deduplicate overlapping roots and break symbolic-link cycles.
                if (!visited.add(file.getCanonicalFile())) {
                    continue;
                }
                if (file.isDirectory()) {
                    File[] children = file.listFiles();
                    if (children != null) {
                        Arrays.sort(children);
                        for (int i = children.length - 1; i >= 0; i--) {
                            pending.push(children[i]);
                        }
                    }
                } else if (file.isFile()) {
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc") || name.endsWith(".otc")) {
                        result.add(file);
                    }
                }
            } catch (IOException | SecurityException ex) {
                // Ignore access errors for this entry and continue with its siblings.
            }
        }
    }

    static List<File> getSystemFontDirectories() {
        return getSystemFontDirectories(System.getProperty("os.name", ""),
                System.getProperty("user.home", ""), System.getenv());
    }

    static List<File> getSystemFontDirectories(String osName, String userHome, Map<String, String> environment) {
        List<File> result = new ArrayList<>();
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            String local = environment.get("LOCALAPPDATA");
            if (local != null && !local.isEmpty()) {
                result.add(new File(local, "Microsoft/Windows/Fonts"));
            } else if (!userHome.isEmpty()) {
                result.add(new File(userHome, "AppData/Local/Microsoft/Windows/Fonts"));
            }
            String windows = environment.get("WINDIR");
            if (windows == null || windows.isEmpty()) {
                windows = environment.get("SystemRoot");
            }
            result.add(new File(windows == null || windows.isEmpty() ? "C:/Windows" : windows, "Fonts"));
        } else if (os.startsWith("mac")) {
            if (!userHome.isEmpty()) {
                result.add(new File(userHome, "Library/Fonts"));
            }
            result.add(new File("/Library/Fonts"));
            result.add(new File("/System/Library/Fonts"));
            result.add(new File("/Network/Library/Fonts"));
        } else {
            String dataHome = environment.get("XDG_DATA_HOME");
            if (dataHome != null && !dataHome.isEmpty()) {
                result.add(new File(dataHome, "fonts"));
            } else if (!userHome.isEmpty()) {
                result.add(new File(userHome, ".local/share/fonts"));
            }
            if (!userHome.isEmpty()) {
                result.add(new File(userHome, ".fonts"));
            }
            String dataDirs = environment.get("XDG_DATA_DIRS");
            if (dataDirs == null || dataDirs.isEmpty()) {
                dataDirs = "/usr/local/share:/usr/share";
            }
            for (String dir : dataDirs.split(":")) {
                if (!dir.isEmpty()) {
                    result.add(new File(dir, "fonts"));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static final class FontFile {

        final File file;
        final long offset;

        FontFile(File file, long offset) {
            this.file = file;
            this.offset = offset;
        }
    }
}
