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
import org.w3c.dom.Element;
import static com.jpexs.flash.fla.exporter.XflXml.*;

/**
 * Persistent values use XFL type codes, independently of binary archive IDs.
 */
final class XflPersistentDataWriter {

    private XflPersistentDataWriter() {
    }

    static void write(BinaryFlaToXflExporter.Session session, Element parent, FlaRecord owner) {
        FlaRecord data = record(owner, "persistentData");
        if (data == null) {
            return;
        }
        if (!data.isComplete()) {
            session.issue("PERSISTENT_DATA", data, "Incomplete persistent data omitted.");
            return;
        }
        FlaPersistentData values = owner.getPersistentData();
        if (values == null || values.getEntries().isEmpty()) {
            return;
        }
        Element container = parent.getOwnerDocument().createElementNS(NS, "persistentData");
        // The native map resolves duplicate keys to their last archived value.
        for (FlaPersistentData.Entry entry : values.getByName().values()) {
            try {
                String type = type(entry.getType()), value = value(entry);
                Element pd = child(container, "PD");
                session.attribute(pd, "n", entry.getName(), owner);
                pd.setAttribute("t", type);
                session.attribute(pd, "v", value, owner);
            } catch (IOException ex) {
                session.issue("PERSISTENT_DATA", owner, ex.getMessage());
            }
        }
        if (container.hasChildNodes()) {
            parent.appendChild(container);
        }
    }

    private static String type(Integer id) throws IOException {
        if (id != null) {
            switch (id) {
                case 0:
                    return "i";
                case 1:
                    return "I";
                case 3:
                    return "d";
                case 4:
                    return "D";
                case 5:
                    return "s";
                case 7:
                    return "B";
            }
        }
        throw new IOException("Unsupported persistent value type " + id);
    }

    private static String value(FlaPersistentData.Entry entry) throws IOException {
        Object value = entry.getValue();
        int type = entry.getType();
        if (type == 5 && value instanceof String) {
            return (String) value;
        }
        if ((type == 0 || type == 3) && value instanceof Number) {
            return numeric((Number) value, type == 0);
        }
        if (type == 7 && value instanceof BinaryData) {
            StringBuilder hex = new StringBuilder();
            char[] digits = "0123456789ABCDEF".toCharArray();
            for (byte b : ((BinaryData) value).toByteArray()) {
                hex.append(digits[(b & 255) >>> 4]).append(digits[b & 15]);
            }
            return hex.toString();
        }
        if ((type == 1 || type == 4) && value instanceof List) {
            StringJoiner text = new StringJoiner(",");
            for (Object item : (List<?>) value) {
                if (!(item instanceof Number)) {
                    throw new IOException("Non-numeric persistent array element.");
                }
                text.add(numeric((Number) item, type == 1));
            }
            return text.toString();
        }
        throw new IOException("Incomplete persistent value " + entry.getName());
    }

    private static String numeric(Number value, boolean integer) throws IOException {
        if (integer) {
            return Integer.toString(value.intValue());
        }
        double d = value.doubleValue();
        if (!Double.isFinite(d)) {
            throw new IOException("Nonfinite persistent number omitted.");
        }
        return d == 0 && Double.doubleToRawLongBits(d) < 0 ? "-0" : number(d);
    }
}
