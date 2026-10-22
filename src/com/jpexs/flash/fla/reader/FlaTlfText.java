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
package com.jpexs.flash.fla.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TLF markup, localization/variable metadata and direct child-object bindings.
 */
public final class FlaTlfText {

    public static final class ChildLink {

        private final FlaRecord child;
        private final Long value;
        private final String identifier;

        private ChildLink(FlaRecord r) {
            child = r.get("child") instanceof FlaRecord ? (FlaRecord) r.get("child") : null;
            identifier = child != null && child.get("identifier") instanceof String ? (String) child.get("identifier") : null;
            value = SemanticFields.unsigned(r, r.get("markupChildId") instanceof Number ? "markupChildId" : "value");
        }

        /**
         * Actual archive identity, not a new object or an archive ID from
         * value.
         */
        public FlaRecord getChild() {
            return child;
        }

        public String getIdentifier() {
            return identifier;
        }

        /**
         * Signed identifier used by the TLF markup, independent of MFC IDs.
         */
        public Integer getMarkupChildId() {
            return value == null ? null : value.intValue();
        }

        /**
         * @deprecated Use getMarkupChildId(); this accessor retains unsigned
         * bits.
         */
        @Deprecated
        public Long getValue() {
            return value;
        }
    }
    private final String xml1, xml2, markup;
    private final List<ChildLink> links;

    private FlaTlfText(FlaRecord r) {
        xml1 = string(r, "localizationStringId", "xml1");
        xml2 = string(r, "variableName", "xml2");
        markup = string(r, "source", "source");
        List<FlaRecord> records = SemanticFields.records(r, "childLinks");
        List<ChildLink> l = new ArrayList<>();
        if (records != null) {
            for (FlaRecord record : records) {
                l.add(new ChildLink(record));
            }
        }
        links = records == null ? null : Collections.unmodifiableList(l);
    }

    public static FlaTlfText fromRecord(FlaRecord r) {
        return "CPicTLFText".equals(r.getClassName()) ? new FlaTlfText(r) : null;
    }

    private static String string(FlaRecord r, String name, String legacy) {
        Object value = r.get(name) instanceof String ? r.get(name) : r.get(legacy);
        return value instanceof String ? (String) value : null;
    }

    public String getMarkup() {
        return markup;
    }

    public List<FlaXmlElement> parseMarkup() throws IOException {
        return markup == null ? null : FlaXmlElement.parseFragment(markup);
    }

    public String getLocalizationStringId() {
        return xml1;
    }

    public String getVariableName() {
        return xml2;
    }

    /**
     * @deprecated The first auxiliary string is a localization ID, not XML.
     */
    @Deprecated
    public String getFirstXml() {
        return xml1;
    }

    /**
     * @deprecated The second auxiliary string is a variable name, not XML.
     */
    @Deprecated
    public String getSecondXml() {
        return xml2;
    }

    /**
     * @deprecated A localization ID is not generally parseable as XML.
     */
    @Deprecated
    public List<FlaXmlElement> parseFirstXml() throws IOException {
        return xml1 == null ? null : FlaXmlElement.parseFragment(xml1);
    }

    /**
     * @deprecated A variable name is not generally parseable as XML.
     */
    @Deprecated
    public List<FlaXmlElement> parseSecondXml() throws IOException {
        return xml2 == null ? null : FlaXmlElement.parseFragment(xml2);
    }

    public List<ChildLink> getChildLinks() {
        return links;
    }
}
