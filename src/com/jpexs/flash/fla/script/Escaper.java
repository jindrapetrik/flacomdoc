package com.jpexs.flash.fla.script;

/**
 *
 * @author JPEXS
 */
public class Escaper {
    public static String escapeActionScriptString(String s) {
        StringBuilder ret = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                ret.append("\\n");
            } else if (c == '\r') {
                ret.append("\\r");
            } else if (c == '\t') {
                ret.append("\\t");
            } else if (c == '\b') {
                ret.append("\\b");
            } else if (c == '\f') {
                ret.append("\\f");
            } else if (c == '\\') {
                ret.append("\\\\");
            } else if (c == '"') {
                ret.append("\\\"");
            } else if (c == '\'') {
                ret.append("\\'");
            } else if (c < 32) {
                ret.append("\\x").append(String.format("%02x", c));
            } else {
                ret.append(c);
            }
        }

        return ret.toString();
    }
}
