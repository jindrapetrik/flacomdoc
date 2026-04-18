package com.jpexs.flash.fla.script;

/**
 * ActionScript 1/2 symbol type.
 *
 * @author JPEXS
 */
public enum SymbolType {
    //Keywords
    BREAK,
    CASE,
    CONTINUE,
    DEFAULT,
    DO,
    WHILE,
    ELSE,
    FOR,
    EACH,
    IN(Precedence.PRECEDENCE_RELATIONAL, true),
    IF,
    RETURN,
    SUPER(Precedence.PRECEDENCE_PRIMARY, false),
    SWITCH,
    THROW,
    TRY,
    CATCH,
    FINALLY,
    WITH,
    DYNAMIC,
    PRIVATE,
    PUBLIC,
    STATIC,
    CLASS,
    EXTENDS,
    FUNCTION(Precedence.PRECEDENCE_PRIMARY, false),
    GET,
    IMPLEMENTS,
    INTERFACE,
    SET,
    VAR,
    IMPORT,
    FALSE(Precedence.PRECEDENCE_PRIMARY, false),
    NULL(Precedence.PRECEDENCE_PRIMARY, false),
    THIS(Precedence.PRECEDENCE_PRIMARY, false),
    TRUE(Precedence.PRECEDENCE_PRIMARY, false),
    //Operators
    PARENT_OPEN(Precedence.PRECEDENCE_PRIMARY, false),
    PARENT_CLOSE(Precedence.PRECEDENCE_PRIMARY, false),
    CURLY_OPEN(Precedence.PRECEDENCE_PRIMARY, false),
    CURLY_CLOSE(Precedence.PRECEDENCE_PRIMARY, false),
    BRACKET_OPEN(Precedence.PRECEDENCE_PRIMARY, false),
    BRACKET_CLOSE(Precedence.PRECEDENCE_PRIMARY, false),
    SEMICOLON,
    COMMA(Precedence.PRECEDENCE_COMMA, false),
    REST,
    DOT(Precedence.PRECEDENCE_PRIMARY, false),
    ASSIGN(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    GREATER_THAN(Precedence.PRECEDENCE_RELATIONAL, true),
    LOWER_THAN(Precedence.PRECEDENCE_RELATIONAL, true),
    NOT(Precedence.PRECEDENCE_UNARY, false),
    NEGATE(Precedence.PRECEDENCE_UNARY, false),
    TERNAR(Precedence.PRECEDENCE_CONDITIONAL, true, true), /*!! ternar !!!*/
    COLON(Precedence.PRECEDENCE_CONDITIONAL, false), /*!! ternar !!!*/
    EQUALS(Precedence.PRECEDENCE_EQUALITY, true),
    STRICT_EQUALS(Precedence.PRECEDENCE_EQUALITY, true),
    LOWER_EQUAL(Precedence.PRECEDENCE_RELATIONAL, true),
    GREATER_EQUAL(Precedence.PRECEDENCE_RELATIONAL, true),
    NOT_EQUAL(Precedence.PRECEDENCE_EQUALITY, true),
    STRICT_NOT_EQUAL(Precedence.PRECEDENCE_EQUALITY, true),
    AND(Precedence.PRECEDENCE_LOGICALAND, true),
    OR(Precedence.PRECEDENCE_LOGICALOR, true),
    FULLAND(Precedence.PRECEDENCE_LOGICALAND, true),
    FULLOR(Precedence.PRECEDENCE_LOGICALOR, true),
    INCREMENT(Precedence.PRECEDENCE_POSTFIX, false), //OR Unary
    DECREMENT(Precedence.PRECEDENCE_POSTFIX, false), //OR Unary
    PLUS(Precedence.PRECEDENCE_ADDITIVE, true),
    MINUS(Precedence.PRECEDENCE_ADDITIVE, true), //OR Unary
    MULTIPLY(Precedence.PRECEDENCE_MULTIPLICATIVE, true),
    DIVIDE(Precedence.PRECEDENCE_MULTIPLICATIVE, true),
    BITAND(Precedence.PRECEDENCE_BITWISEAND, true),
    BITOR(Precedence.PRECEDENCE_BITWISEOR, true),
    XOR(Precedence.PRECEDENCE_BITWISEXOR, true),
    MODULO(Precedence.PRECEDENCE_MULTIPLICATIVE, true),
    SHIFT_LEFT(Precedence.PRECEDENCE_BITWISESHIFT, true),
    SHIFT_RIGHT(Precedence.PRECEDENCE_BITWISESHIFT, true),
    USHIFT_RIGHT(Precedence.PRECEDENCE_BITWISESHIFT, true),
    ASSIGN_PLUS(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_MINUS(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_MULTIPLY(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_DIVIDE(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_BITAND(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_BITOR(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_XOR(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_MODULO(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_SHIFT_LEFT(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_SHIFT_RIGHT(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    ASSIGN_USHIFT_RIGHT(Precedence.PRECEDENCE_ASSIGNMENT, true, true),
    DELETE(Precedence.PRECEDENCE_UNARY, false),
    INSTANCEOF(Precedence.PRECEDENCE_RELATIONAL, true),
    NEW(Precedence.PRECEDENCE_PRIMARY, false),
    TYPEOF(Precedence.PRECEDENCE_UNARY, false),
    VOID,
    ATTRIBUTE,
    //Other
    STRING(Precedence.PRECEDENCE_PRIMARY, false),
    COMMENT,
    IDENTIFIER(Precedence.PRECEDENCE_PRIMARY, false),
    INTEGER(Precedence.PRECEDENCE_PRIMARY, false),
    DOUBLE(Precedence.PRECEDENCE_PRIMARY, false),
    TYPENAME(Precedence.PRECEDENCE_PRIMARY, false),
    EOF,
    TRACE(Precedence.PRECEDENCE_PRIMARY, false),
    GETURL(Precedence.PRECEDENCE_PRIMARY, false),
    GOTOANDSTOP(Precedence.PRECEDENCE_PRIMARY, false),
    NEXTFRAME(Precedence.PRECEDENCE_PRIMARY, false),
    PLAY(Precedence.PRECEDENCE_PRIMARY, false),
    PREVFRAME(Precedence.PRECEDENCE_PRIMARY, false),
    TELLTARGET(Precedence.PRECEDENCE_PRIMARY, false),
    STOP(Precedence.PRECEDENCE_PRIMARY, false),
    STOPALLSOUNDS(Precedence.PRECEDENCE_PRIMARY, false),
    TOGGLEHIGHQUALITY(Precedence.PRECEDENCE_PRIMARY, false),
    ORD(Precedence.PRECEDENCE_PRIMARY, false),
    CHR(Precedence.PRECEDENCE_PRIMARY, false),
    DUPLICATEMOVIECLIP(Precedence.PRECEDENCE_PRIMARY, false),
    STOPDRAG(Precedence.PRECEDENCE_PRIMARY, false),
    GETTIMER(Precedence.PRECEDENCE_PRIMARY, false),
    LOADVARIABLES(Precedence.PRECEDENCE_PRIMARY, false),
    LOADMOVIE(Precedence.PRECEDENCE_PRIMARY, false),
    GOTOANDPLAY(Precedence.PRECEDENCE_PRIMARY, false),
    MBORD(Precedence.PRECEDENCE_PRIMARY, false),
    MBCHR(Precedence.PRECEDENCE_PRIMARY, false),
    MBLENGTH(Precedence.PRECEDENCE_PRIMARY, false),
    MBSUBSTRING(Precedence.PRECEDENCE_PRIMARY, false),
    RANDOM(Precedence.PRECEDENCE_PRIMARY, false),
    REMOVEMOVIECLIP(Precedence.PRECEDENCE_PRIMARY, false),
    STARTDRAG(Precedence.PRECEDENCE_PRIMARY, false),
    SUBSTRING(Precedence.PRECEDENCE_PRIMARY, false),
    LENGTH(Precedence.PRECEDENCE_PRIMARY, false), //string.length
    INT(Precedence.PRECEDENCE_PRIMARY, false),
    TARGETPATH(Precedence.PRECEDENCE_PRIMARY, false),
    NUMBER_OP(Precedence.PRECEDENCE_PRIMARY, false),
    STRING_OP(Precedence.PRECEDENCE_PRIMARY, false),
    IFFRAMELOADED,
    EVAL(Precedence.PRECEDENCE_PRIMARY, false),
    UNDEFINED(Precedence.PRECEDENCE_PRIMARY, false),
    NEWLINE(Precedence.PRECEDENCE_PRIMARY, false),
    GETVERSION(Precedence.PRECEDENCE_PRIMARY, false),
    CALL(Precedence.PRECEDENCE_PRIMARY, false),
    LOADMOVIENUM(Precedence.PRECEDENCE_PRIMARY, false),
    LOADVARIABLESNUM(Precedence.PRECEDENCE_PRIMARY, false),
    PRINT(Precedence.PRECEDENCE_PRIMARY, false),
    PRINTNUM(Precedence.PRECEDENCE_PRIMARY, false),
    PRINTASBITMAP(Precedence.PRECEDENCE_PRIMARY, false),
    PRINTASBITMAPNUM(Precedence.PRECEDENCE_PRIMARY, false),
    UNLOADMOVIE(Precedence.PRECEDENCE_PRIMARY, false),
    UNLOADMOVIENUM(Precedence.PRECEDENCE_PRIMARY, false),
    FSCOMMAND(Precedence.PRECEDENCE_PRIMARY, false),
    PREPROCESSOR(Precedence.PRECEDENCE_PRIMARY, false),
    FSCOMMAND2(Precedence.PRECEDENCE_PRIMARY, false),
    DIRECTIVE(Precedence.PRECEDENCE_PRIMARY, false);
    
    private int precedence = Precedence.NOPRECEDENCE;

    private boolean binary = false;

    private boolean rightAssociative = false;

    public boolean isBinary() {
        return binary;
    }

    public boolean isRightAssociative() {
        return rightAssociative;
    }

    public int getPrecedence() {
        return precedence;
    }

    private SymbolType(int precedence, boolean binary) {
        this.precedence = precedence;
        this.binary = binary;
    }

    private SymbolType(int precedence, boolean binary, boolean rightAssociative) {
        this.precedence = precedence;
        this.binary = binary;
        this.rightAssociative = rightAssociative;
    }

    private SymbolType() {

    }
}
