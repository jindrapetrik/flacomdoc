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

import com.jpexs.flash.fla.reader.BinaryData;
import com.jpexs.flash.fla.converter.streams.ZippedInputStorage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.w3c.dom.*;

/**
 * Converts XFL projects without reducing them to the legacy binary model.
 * Unchanged XML and all media/cache resources retain their original bytes.
 */
public final class XflVersionConverter {

    private final XflFormatVersion target;

    public XflVersionConverter(XflFormatVersion target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    /**
     * Accepts an XFL directory, its .xfl marker, or a compressed FLA.
     */
    public XflExportResult convert(File input) throws IOException {
        Map<String, BinaryData> files = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        if (input.isDirectory() || input.getName().toLowerCase(Locale.ROOT).endsWith(".xfl")) {
            if (!input.exists()) {
                throw new FileNotFoundException(input.toString());
            }
            Path root = (input.isDirectory() ? input.toPath() : input.toPath().toAbsolutePath().getParent()).toRealPath();
            if (!Files.isRegularFile(root.resolve("DOMDocument.xml"))) {
                throw new IOException("Missing XFL resource: DOMDocument.xml");
            }
            try (Stream<Path> paths = Files.walk(root)) {
                Iterator<Path> iterator = paths.sorted().iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Symbolic links are not XFL project resources: " + path);
                    }
                    if (Files.isRegularFile(path)) {
                        add(files, names, root.relativize(path).toString(), new BinaryData(Files.readAllBytes(path)));
                    }
                }
            }
        } else {
            try (ZippedInputStorage zip = new ZippedInputStorage(input)) {
                for (String name : zip.getFileNames()) {
                    try (InputStream stream = zip.readFile(name)) {
                        ByteArrayOutputStream data = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = stream.read(buffer)) != -1) {
                            data.write(buffer, 0, length);
                        }
                        add(files, names, name, new BinaryData(data.toByteArray()));
                    }
                }
            }
        }
        return convert(files);
    }

    /**
     * Detached project resources. Neither the map nor its values are modified.
     */
    public XflExportResult convert(Map<String, BinaryData> input) throws IOException {
        Map<String, BinaryData> files = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, BinaryData> entry : input.entrySet()) {
            add(files, names, entry.getKey(), Objects.requireNonNull(entry.getValue(), entry.getKey()));
        }
        BinaryData main = files.get("DOMDocument.xml");
        if (main == null) {
            throw new IOException("Missing XFL resource: DOMDocument.xml");
        }
        Document doc = XflVersionRules.parse(main, "DOMDocument.xml");
        Element root = doc.getDocumentElement();
        if (!XflVersionRules.xfl(root) || !XflVersionRules.name(root).equals("DOMDocument")) {
            throw new IOException("DOMDocument.xml does not contain an XFL document");
        }
        String source = root.getAttribute("xflVersion");
        if (!Arrays.asList("2.0", "2.1", "2.2").contains(source)) {
            throw new IOException("Unsupported source XFL version: " + source + "; expected CS5, CS5.5 or CS6");
        }
        List<XflExportIssue> issues = new ArrayList<>();
        files = XflVersionRules.convert(files, target, issues);
        files.put("document.xfl", new BinaryData("PROXY-CS5".getBytes(StandardCharsets.US_ASCII)));
        return new XflExportResult(files, issues, "XFL " + source + " to XFL " + target.getXflVersion()
                + " (" + target.name().replace('_', '.') + ")\n"
                + "Media resources and supported XML content are retained. Listed changes may lose information.\n\n");
    }

    private static void add(Map<String, BinaryData> files, Set<String> names, String path, BinaryData data) throws IOException {
        path = checkedPath(path);
        if (path.equals("mimetype") || path.equals("conversion-report.txt")
                || !path.contains("/") && path.toLowerCase(Locale.ROOT).endsWith(".xfl")) {
            return;
        }
        if (!names.add(path.toLowerCase(Locale.ROOT))) {
            throw new IOException("Duplicate XFL resource path: " + path);
        }
        files.put(path, data);
    }

    static String checkedPath(String path) throws IOException {
        path = path.replace('\\', '/');
        if (path.startsWith("/") || path.contains(":")) {
            throw new IOException("XFL resource must be project-relative: " + path);
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("Invalid XFL resource path: " + path);
            }
        }
        return path;
    }
}
