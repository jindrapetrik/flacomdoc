package com.jpexs.flash.fla.script;

/**
 *
 * @author JPEXS
 */
public class Precedence {
    public static final int PRECEDENCE_PRIMARY = 0;

    public static final int PRECEDENCE_POSTFIX = 1;

    public static final int PRECEDENCE_UNARY = 2;

    public static final int PRECEDENCE_MULTIPLICATIVE = 3;

    public static final int PRECEDENCE_ADDITIVE = 4;

    public static final int PRECEDENCE_BITWISESHIFT = 5;

    public static final int PRECEDENCE_RELATIONAL = 6;

    public static final int PRECEDENCE_EQUALITY = 7;

    public static final int PRECEDENCE_BITWISEAND = 8;

    public static final int PRECEDENCE_BITWISEXOR = 9;

    public static final int PRECEDENCE_BITWISEOR = 10;

    public static final int PRECEDENCE_LOGICALAND = 11;

    public static final int PRECEDENCE_LOGICALOR = 12;

    public static final int PRECEDENCE_NULLCOALESCE = 13;

    public static final int PRECEDENCE_CONDITIONAL = 14;

    public static final int PRECEDENCE_ASSIGNMENT = 15;

    public static final int PRECEDENCE_COMMA = 16;

    public static final int NOPRECEDENCE = 17;
}
