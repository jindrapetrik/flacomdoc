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

/**
 * Additional typed authoring-command bodies, with independent revision gates.
 */
final class ExtendedCompiledActionRecordDecoder {

    private ExtendedCompiledActionRecordDecoder() {
    }

    static void read(MfcArchive a, FlaRecord r, long rawType, long listRevision) throws IOException {
        if (rawType > 85 || rawType == 35 || rawType < 0) {
            throw new UnsupportedFlaRecordException("Compiled action command type " + rawType);
        }
        int type = (int) rawType;
        boolean present = listRevision != 0 || (type != 46 && type != 82);
        r.set("bodyPresent", present);
        if (!present) {
            return;
        }
        int maximum = type == 37 ? 4
                : type == 39 || type == 41 || type == 44 || type == 47 || type == 59
                || type == 78 || type == 80 || type == 83 ? 3 : 2;
        long revision = CompiledActionRecordDecoder.revision(a, r, maximum);
        Fields fields = new Fields(a, r, type, revision);
        int metadataRevision = type == 37 ? 4
                : type == 39 || type == 41 || type == 44 || type == 47 || type == 59 || type == 80 ? 3 : 2;
        switch (type) {
            case 6:
            case 49:
                fields.parameter();
                fields.parameter();
                break;
            case 7:
                fields.word();
                fields.parameter();
                fields.word();
                fields.parameter();
                fields.word();
                break;
            case 8:
            case 22:
            case 27:
            case 30:
            case 36:
                fields.parameter();
                break;
            case 10:
                fields.string();
                fields.string();
                fields.word();
                break;
            case 15:
            case 17:
            case 33:
            case 40:
            case 41:
            case 43:
            case 50:
            case 53:
            case 57:
            case 65:
            case 69:
            case 75:
            case 84:
                fields.string();
                break;
            case 20:
                fields.string();
                if (revision >= 2) {
                    r.set("emptyBody", a.readUI8());
                }
                break;
            case 23:
                fields.word();
                fields.parameter();
                fields.parameter();
                break;
            case 24:
                fields.parameter();
                fields.parameter();
                break;
            case 25:
                fields.word();
                fields.parameter();
                fields.parameter();
                fields.string();
                break;
            case 26:
                fields.parameter();
                for (int i = 0; i < 4; i++) {
                    fields.string();
                }
                fields.word();
                fields.word();
                fields.word();
                break;
            case 28:
                fields.string();
                break;
            case 31:
                fields.parameter();
                fields.word();
                break;
            case 32:
                fields.parameter();
                fields.word();
                fields.parameter();
                fields.word();
                break;
            case 34:
                fields.word();
                fields.parameter();
                fields.word();
                fields.word();
                fields.word();
                break;
            case 37:
                fields.string();
                fields.string();
                if (revision >= 1) {
                    fields.word();
                }
                if (revision >= 3) {
                    r.set("declarationOnly", a.readUI8());
                }
                break;
            case 39:
                fields.string();
                if (revision >= 1) {
                    fields.word();
                }
                break;
            case 44:
                fields.string();
                if (revision >= 1) {
                    fields.string();
                    fields.string();
                }
                break;
            case 46:
            case 82:
                if (revision >= 2) {
                    CompiledActionRecordDecoder.metadata(a, r);
                    fields.parameter();
                }
                return;
            case 47:
            case 73:
            case 80:
                fields.string();
                fields.string();
                break;
            case 55:
            case 56:
                fields.string();
                fields.word();
                break;
            case 59:
                fields.string();
                fields.string();
                fields.string();
                if (revision >= 1) {
                    fields.word();
                    fields.word();
                }
                break;
            case 60:
            case 62:
            case 79:
                fields.string();
                fields.string();
                fields.string();
                break;
            case 63:
            case 67:
            case 77:
                break;
            case 71:
                fields.string();
                fields.string();
                fields.string();
                fields.word();
                if (revision >= 1) {
                    fields.word();
                }
                break;
            case 76:
            case 78:
            case 83:
                if (revision >= 2) {
                    fields.string();
                    fields.word();
                    fields.word();
                }
                if (revision >= 3) {
                    fields.string();
                    fields.string();
                }
                return;
            default:
                throw new UnsupportedFlaRecordException("Compiled action command type " + type);
        }
        if (revision >= metadataRevision) {
            if (type == 24) {
                r.set("sourcePrefix", a.readString());
                r.set("sourceText", a.readString());
                r.set("sourceSuffix", a.readString());
                r.set("value40", a.readUI32());
                r.set("valueQuoteCharacter", a.readUI16());
                r.set("suppressBlockDelimiter", a.readUI32());
            } else {
                CompiledActionRecordDecoder.metadata(a, r);
            }
            if (type == 28) {
                fields.word();
            }
        }
    }

    /**
     * Maps known operands to their roles while retaining archive-order keys
     * only for operands whose meaning is still unidentified.
     */
    private static final class Fields {

        private final MfcArchive archive;
        private final FlaRecord record;
        private final int type;
        private final long revision;
        private final String[] stringNames, wordNames;
        private int strings;
        private int words;
        private int parameters;

        Fields(MfcArchive archive, FlaRecord record, int type, long revision) {
            this.archive = archive;
            this.record = record;
            this.type = type;
            this.revision = revision;
            this.stringNames = operandNames(true);
            this.wordNames = operandNames(false);
        }

        void string() throws IOException {
            record.set(operandName(true, ++strings), archive.readString());
        }

        void word() throws IOException {
            record.set(operandName(false, ++words), archive.readUI32());
        }

        private String[] operandNames(boolean string) {
            String[] names = null;
            if (string) {
                switch (type) {
                    case 10:
                        names = new String[]{"scene", "frame"};
                        break;
                    case 15:
                        names = new String[]{"condition"};
                        break;
                    case 17:
                        names = new String[]{"condition"};
                        break;
                    case 20:
                        names = new String[]{"condition"};
                        break;
                    case 25:
                        names = new String[]{"depth"};
                        break;
                    case 26:
                        names = new String[]{"left", "top", "right", "bottom"};
                        break;
                    case 28:
                        names = new String[]{"comment"};
                        break;
                    case 33:
                        names = new String[]{"includePath"};
                        break;
                    case 37:
                        names = new String[]{"functionSignature", "parameters"};
                        break;
                    case 39:
                        names = new String[]{"declaration"};
                        break;
                    case 40:
                        names = new String[]{"returnValue"};
                        break;
                    case 41:
                        names = new String[]{"expression"};
                        break;
                    case 43:
                        names = new String[]{"condition"};
                        break;
                    case 44:
                        names = revision == 0 ? new String[]{"condition"} : new String[]{"initializer", "condition", "increment"};
                        break;
                    case 47:
                        names = new String[]{"iterator", "collection"};
                        break;
                    case 50:
                        names = new String[]{"object"};
                        break;
                    case 53:
                        names = new String[]{"expression"};
                        break;
                    case 55:
                        names = new String[]{"caseExpression"};
                        break;
                    case 56:
                        names = new String[]{"caseExpression"};
                        break;
                    case 57:
                        names = new String[]{"initClipIndex"};
                        break;
                    case 59:
                        names = new String[]{"invocationSignature", "object", "arguments"};
                        break;
                    case 60:
                        names = new String[]{"object", "method", "parameters"};
                        break;
                    case 62:
                        names = new String[]{"object", "method", "arguments"};
                        break;
                    case 65:
                        names = new String[]{"exception"};
                        break;
                    case 69:
                        names = new String[]{"expression"};
                        break;
                    case 71:
                        names = new String[]{"name", "superClass", "interfaces"};
                        break;
                    case 73:
                        names = new String[]{"name", "interfaces"};
                        break;
                    case 75:
                        names = new String[]{"importName"};
                        break;
                    case 76:
                        names = new String[]{"packageName"};
                        break;
                    case 78:
                        names = new String[]{"namespaceName", "namespaceValue", "namespaceQualifier"};
                        break;
                    case 79:
                        names = new String[]{"object", "method", "arguments"};
                        break;
                    case 80:
                        names = new String[]{"iterator", "collection"};
                        break;
                    case 83:
                        names = new String[]{"namespaceName", "namespaceValue", "namespaceQualifier"};
                        break;
                    case 84:
                        names = new String[]{"configuration"};
                        break;
                    default:
                        break;
                }
            } else {
                switch (type) {
                    case 7:
                        names = new String[]{"discardedLegacyDword", "requestMethod", "targetMode"};
                        break;
                    case 10:
                        names = new String[]{"frameMode"};
                        break;
                    case 23:
                        names = new String[]{"propertyIndex"};
                        break;
                    case 25:
                        names = new String[]{"discardedLegacyDword"};
                        break;
                    case 76:
                    case 78:
                    case 83:
                        names = new String[]{"value40", "suppressBlockDelimiter"};
                        break;
                    case 26:
                        names = new String[]{"lockCenter", "constrain"};
                        break;
                    case 28:
                        names = new String[]{"commentStyle"};
                        break;
                    case 31:
                        names = new String[]{"targetMode"};
                        break;
                    case 32:
                        names = new String[]{"requestMethod", "targetMode"};
                        break;
                    case 34:
                        names = new String[]{"discardedLegacyDword", "targetMode", "boundsMode", "printAsBitmap"};
                        break;
                    case 37:
                        names = new String[]{"functionFlags"};
                        break;
                    case 39:
                        names = new String[]{"variableFlags"};
                        break;
                    case 55:
                        names = new String[]{"isDefaultCase"};
                        break;
                    case 56:
                        names = new String[]{"isDefaultCase"};
                        break;
                    case 59:
                        names = new String[]{"objectExpressionFlags"};
                        break;
                    case 71:
                        names = new String[]{"dynamic", "intrinsic"};
                        break;
                    default:
                        break;
                }
            }
            return names;
        }

        private String operandName(boolean string, int index) {
            String[] names = string ? stringNames : wordNames;
            return names != null && index <= names.length && names[index - 1] != null
                    ? names[index - 1] : (string ? "string" : "value") + index;
        }

        void parameter() throws IOException {
            archive.readInline(record, "parameter" + ++parameters, (in, parameter) -> {
                parameter.set("text", in.readString());
                parameter.set("flags", in.readUI32());
            });
        }
    }
}
