package com.jpexs.flash.fla.script;

import com.jpexs.flash.fla.script.commands.AbstractCommand;
import com.jpexs.flash.fla.script.commands.BeginTellTargetCommand;
import com.jpexs.flash.fla.script.commands.CallCommand;
import com.jpexs.flash.fla.script.commands.DuplicateMovieClipCommand;
import com.jpexs.flash.fla.script.commands.ElseCommand;
import com.jpexs.flash.fla.script.commands.EmptyCommand;
import com.jpexs.flash.fla.script.commands.EndFrameLoadedCommand;
import com.jpexs.flash.fla.script.commands.EndIfCommand;
import com.jpexs.flash.fla.script.commands.EndLoopCommand;
import com.jpexs.flash.fla.script.commands.EndOnCommand;
import com.jpexs.flash.fla.script.commands.EndTellTargetCommand;
import com.jpexs.flash.fla.script.commands.FsCommandCommand;
import com.jpexs.flash.fla.script.commands.GetUrlCommand;
import com.jpexs.flash.fla.script.commands.GetUrlGotoAndPlay;
import com.jpexs.flash.fla.script.commands.GetUrlGotoAndStop;
import com.jpexs.flash.fla.script.commands.GotoAndPlayCommand;
import com.jpexs.flash.fla.script.commands.GotoAndStopCommand;
import com.jpexs.flash.fla.script.commands.IfCommand;
import com.jpexs.flash.fla.script.commands.IfFrameIsLoadedCommand;
import com.jpexs.flash.fla.script.commands.LoadMovieCommand;
import com.jpexs.flash.fla.script.commands.LoadVariablesCommand;
import com.jpexs.flash.fla.script.commands.LoopWhileCommand;
import com.jpexs.flash.fla.script.commands.NextFrameCommand;
import com.jpexs.flash.fla.script.commands.NextSceneCommand;
import com.jpexs.flash.fla.script.commands.OnCommand;
import com.jpexs.flash.fla.script.commands.PlayCommand;
import com.jpexs.flash.fla.script.commands.PrevFrameCommand;
import com.jpexs.flash.fla.script.commands.PrevSceneCommand;
import com.jpexs.flash.fla.script.commands.RemoveMovieClipCommand;
import com.jpexs.flash.fla.script.commands.SetVariableCommand;
import com.jpexs.flash.fla.script.commands.StartDragCommand;
import com.jpexs.flash.fla.script.commands.StopAllSoundsCommand;
import com.jpexs.flash.fla.script.commands.StopCommand;
import com.jpexs.flash.fla.script.commands.StopDragCommand;
import com.jpexs.flash.fla.script.commands.ToggleHighQualityCommand;
import com.jpexs.flash.fla.script.commands.TraceCommand;
import com.jpexs.flash.fla.script.commands.UnloadMovieCommand;
import com.jpexs.flash.fla.converter.FlaFormatVersion;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ActionScript 1 parser.
 *
 * @author JPEXS
 */
public class ScriptParser {

    private final FlaFormatVersion flaVersion;    
    
    private ScriptLexer lexer;
   
    
    /**
     * Constructor
     *
     * @param flaVersion Target version of the Fla format
     */
    public ScriptParser(FlaFormatVersion flaVersion) {
        this.flaVersion = flaVersion;
    }

    private final boolean debugMode = false;  
    
    
    private void expected(ParsedSymbol symb, int line, Object... expected) throws IOException, ScriptParseException {
        boolean found = false;
        for (Object t : expected) {
            if (symb.type == t) {
                found = true;
            }
            if (symb.group == t) {
                found = true;
            }
        }
        if (!found) {
            String expStr = "";
            boolean first = true;
            for (Object e : expected) {
                if (!first) {
                    expStr += " or ";
                }
                expStr += e;
                first = false;
            }
            throw new ScriptParseException("" + expStr + " expected but " + symb.type + " found", line);
        }
    }
    
    
    private ParsedSymbol expectedType(Object... type) throws IOException, ScriptParseException, InterruptedException {
        ParsedSymbol symb = lex();
        expected(symb, lexer.yyline(), type);
        return symb;
    }
    
    
     private ParsedSymbol lex() throws IOException, ScriptParseException, InterruptedException {
        ParsedSymbol ret = lexer.lex();
        if (debugMode) {
            System.out.println(ret);
        }
        return ret;
    }
    
    
    public List<AbstractCommand> parse(String script, boolean debugRandom) throws IOException, ScriptParseException, InterruptedException {
        lexer = new ScriptLexer(new StringReader(script));
        List<AbstractCommand> ret = commands();
        
        
        AbstractCommand singleCommand = null;
        
        if (flaVersion.ordinal() <= FlaFormatVersion.F2.ordinal()) {
            if (ret.size() > 2) {
                if (ret.get(0) instanceof IfFrameIsLoadedCommand && ret.get(ret.size() - 1) instanceof EndFrameLoadedCommand) {
                    IfFrameIsLoadedCommand ifl = (IfFrameIsLoadedCommand) ret.get(0);
                    if (!ifl.getArg0().isEmpty()) {
                        int waitFrames = ifl.getFrameNum();
                        int pos = 1;
                        String url = "";
                        String window = "";
                        boolean hasGetUrl = false;
                        if (ret.get(pos) instanceof GetUrlCommand) {
                            GetUrlCommand gu = (GetUrlCommand) ret.get(pos);
                            if (gu.getMinFlaVersion() == FlaFormatVersion.F1) {
                                url = gu.getUrl().value;
                                window = gu.getWindow().value;
                                hasGetUrl = true;
                                pos++;
                            }                            
                        } 
                        if (ret.size() == pos + 1) {
                            if (ret.get(pos) instanceof GotoAndStopCommand) {
                                GotoAndStopCommand cmd = (GotoAndStopCommand) ret.get(pos);
                                if (cmd.getMinFlaVersion() == FlaFormatVersion.F1) {
                                    int frameNum = cmd.getFrameNum();
                                    String page = cmd.getPage();
                                    if (hasGetUrl) {
                                        singleCommand = new GetUrlGotoAndStop(url, window, page, frameNum, waitFrames);
                                    } else {
                                        singleCommand = new GotoAndStopCommand(page, frameNum, waitFrames);
                                    }
                                }
                            } else if (ret.get(pos) instanceof GotoAndPlayCommand) {
                                GotoAndPlayCommand cmd = (GotoAndPlayCommand) ret.get(pos);
                                if (cmd.getMinFlaVersion() == FlaFormatVersion.F1) {
                                    int frameNum = cmd.getFrameNum();
                                    String page = cmd.getPage();
                                    if (hasGetUrl) {
                                        singleCommand = new GetUrlGotoAndPlay(url, window, page, frameNum, waitFrames);
                                    } else {
                                        singleCommand = new GotoAndPlayCommand(page, frameNum, waitFrames);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (singleCommand == null) {
                if (ret.size() == 2) {
                    String url = "";
                    String window = "";
                    if (ret.get(0) instanceof GetUrlCommand) {
                        GetUrlCommand gu = (GetUrlCommand) ret.get(0);
                        if (gu.getMinFlaVersion() == FlaFormatVersion.F1) {
                            url = gu.getUrl().value;
                            window = gu.getWindow().value;
                            
                            if (ret.get(1) instanceof GotoAndStopCommand) {
                                GotoAndStopCommand cmd = (GotoAndStopCommand) ret.get(1);
                                if (cmd.getMinFlaVersion() == FlaFormatVersion.F1) {
                                    int frameNum = cmd.getFrameNum();
                                    String page = cmd.getPage();
                                    singleCommand = new GetUrlGotoAndStop(url, window, page, frameNum);
                                }
                            } else if (ret.get(1) instanceof GotoAndPlayCommand) {
                                GotoAndPlayCommand cmd = (GotoAndPlayCommand) ret.get(1);
                                if (cmd.getMinFlaVersion() == FlaFormatVersion.F1) {
                                    int frameNum = cmd.getFrameNum();
                                    String page = cmd.getPage();
                                    singleCommand = new GetUrlGotoAndPlay(url, window, page, frameNum);
                                }
                            }
                        }                            
                    }                                                                 
                }                
            }
        }        
        
        if (singleCommand != null) {
            ret.clear();
            ret.add(singleCommand);
        }
        
        if (flaVersion.ordinal() <= FlaFormatVersion.F2.ordinal() && ret.isEmpty()) {
            ret.add(new EmptyCommand());
        }
        
        if (flaVersion.ordinal() <= FlaFormatVersion.F2.ordinal() && ret.size() > 1) {
            throw new ScriptParseException("Only single action expected for FLA format " + flaVersion, -1);
        }
        
        FlaFormatVersion minVersion = FlaFormatVersion.F1;                        
        for (AbstractCommand cmd : ret) {
            if (cmd.getMinFlaVersion().ordinal() > minVersion.ordinal()) {
                minVersion = cmd.getMinFlaVersion();
            }
        }        
        
        if (minVersion.ordinal() > flaVersion.ordinal()) {
            throw new ScriptParseException("The code cannot be represented in FLA format " + flaVersion +". Minimum is " + minVersion + ".", -1);
        }
        
        if (debugRandom) {
            for (AbstractCommand cmd : ret) {
                cmd.defaultPage = "YYY";
                cmd.defaultUrl = new Expression("YYY", true);
                cmd.defaultWindow = new Expression("YYY", true);
                cmd.defaultFrameNum = ('X' << 8) + 'X';
                cmd.defaultLevel = ('X' << 8) + 'X';
                cmd.defaultProperty = ('X' << 24) + ('X' << 16) + ('X' << 8) + 'X';
                cmd.defaultKey = ('X' << 24) + ('X' << 16) + ('X' << 8) + 'X';
            }
        }
        
        return ret;
    }
    
    private void expectedIdentifier(ParsedSymbol s, int line, Object... exceptions) throws IOException, ScriptParseException {
        for (Object ex : exceptions) {
            if (s.isType(ex)) {
                return;
            }
        }
        if (!isIdentifier(s)) {
            throw new ScriptParseException(SymbolType.IDENTIFIER + " expected but " + s.type + " found", line);
        }
    }
    
    private boolean isIdentifier(ParsedSymbol s, Object... exceptions) {
        for (Object ex : exceptions) {
            if (s.isType(ex)) {
                return true;
            }
        }
        return s.isType(SymbolType.IDENTIFIER,
                SymbolType.TRUE, SymbolType.FALSE, SymbolGroup.GLOBALCONST,
                SymbolType.GET, SymbolType.SET,
                SymbolType.EACH, SymbolGroup.GLOBALFUNC,
                SymbolType.NUMBER_OP, SymbolType.STRING_OP);
    }

    
   private List<AbstractCommand> commands() throws IOException, ScriptParseException, InterruptedException {
        List<AbstractCommand> ret = new ArrayList<>();
        if (debugMode) {
            System.out.println("commands:");
        }
        List<AbstractCommand> cmd;
        while (!(cmd = command(true)).isEmpty()) {
            ret.addAll(cmd);
        }
        if (debugMode) {
            System.out.println("/commands");
        }
        return ret;
    }
    
    private List<AbstractCommand> command(boolean mustBeCommand) throws IOException, ScriptParseException, InterruptedException {
        LexBufferer buf = new LexBufferer();
        lexer.addListener(buf);
        List<AbstractCommand> ret = new ArrayList<>();
        if (debugMode) {
            System.out.println("command:");
        }
        ParsedSymbol s = lex();
        if (s.type == SymbolType.EOF) {
            return new ArrayList<>();
        }        

        switch (s.type) {
            case DUPLICATEMOVIECLIP:
                expectedType(SymbolType.PARENT_OPEN);
                Expression src3 = expression();
                expectedType(SymbolType.COMMA);
                Expression tar3 = expression();
                expectedType(SymbolType.COMMA);
                Expression dep3 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                //ret = new CloneSpriteActionItem(null, null, src3, tar3, dep3);
                ret.add(new DuplicateMovieClipCommand(src3, tar3, dep3));
                break;
            case FSCOMMAND:
                expectedType(SymbolType.PARENT_OPEN);
                Expression command = expression();
                s = lex();
                Expression parameter = new Expression();
                if (s.isType(SymbolType.COMMA)) {
                    parameter = expression();
                } else {
                    lexer.pushback(s);
                }
                ret.add(new FsCommandCommand(command, parameter));
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case SET:
                expectedType(SymbolType.PARENT_OPEN);
                Expression name1 = expression();
                expectedType(SymbolType.COMMA);
                Expression value1 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new SetVariableCommand(name1, value1));
                break;
            case TRACE:
                expectedType(SymbolType.PARENT_OPEN);
                ret.add(new TraceCommand(expression()));
                expectedType(SymbolType.PARENT_CLOSE);
                break;

            case GETURL:
                expectedType(SymbolType.PARENT_OPEN);
                s = lex();
                if (s.type == SymbolType.STRING) {
                    ParsedSymbol urlSymb = s;
                    s = lex();
                    if (s.type == SymbolType.COMMA) {
                        ParsedSymbol targetSymb = lex();
                        if (targetSymb.type == SymbolType.STRING) {
                            ParsedSymbol s2 = lex();
                            if (s2.type == SymbolType.PARENT_CLOSE) {
                                ret.add(new GetUrlCommand(new Expression(urlSymb.value.toString(), true), new Expression(targetSymb.value.toString(), true)));
                                break;
                            }
                            lexer.pushback(s2);
                        }
                        lexer.pushback(targetSymb);
                    } else if (s.type == SymbolType.PARENT_CLOSE) {
                        ret.add(new GetUrlCommand(new Expression(urlSymb.value.toString(), true), new Expression()));
                        break;
                    }
                    lexer.pushback(s);
                    lexer.pushback(urlSymb);
                } else {
                    lexer.pushback(s);
                }
                Expression url = expression();
                s = lex();
                expected(s, lexer.yyline(), SymbolType.PARENT_CLOSE, SymbolType.COMMA);
                String sendVarsMethod = "";
                Expression target = new Expression();
                if (s.type == SymbolType.COMMA) {
                    target = expression();
                    s = lex();
                    if (s.type == SymbolType.COMMA) {
                        s = lex();
                        expected(s, lexer.yyline(), SymbolType.STRING);
                        if (s.value.equals("GET")) {
                            sendVarsMethod = "GET";
                        } else if (s.value.equals("POST")) {
                            sendVarsMethod = "POST";
                        } else {
                            throw new ScriptParseException("Invalid method, \"GET\" or \"POST\" expected.", lexer.yyline());
                        }
                    } else {
                        lexer.pushback(s);
                    }
                } else {
                    lexer.pushback(s);                    
                }
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new GetUrlCommand(url, target, sendVarsMethod));
                break;
            case GOTOANDSTOP:
            case GOTOANDPLAY:
                SymbolType gtKind = s.type;
                expectedType(SymbolType.PARENT_OPEN);
                Expression gtsFrame = expression();
                int gtsSceneBias = -1;
                s = lex();
                String page = "";
                if (s.type == SymbolType.COMMA) {
                    if (!gtsFrame.isRaw) {
                        throw new ScriptParseException("Scene must be string", lexer.yyline());
                    }
                    page = gtsFrame.value;
                    gtsFrame = expression();
                } else {
                    lexer.pushback(s);
                }
                int frameNum = -1;
                if (gtsFrame.isNumeric) {
                    frameNum = (int)(double)Double.parseDouble(gtsFrame.value);
                }
                if (gtKind == SymbolType.GOTOANDPLAY) {
                    if (frameNum > -1) {
                        ret.add(new GotoAndPlayCommand(page, frameNum));
                    } else {
                        ret.add(new GotoAndPlayCommand(page, gtsFrame));
                    }
                } else {
                    if (frameNum > -1) {
                        ret.add(new GotoAndStopCommand(page, frameNum));
                    } else {
                        ret.add(new GotoAndStopCommand(page, gtsFrame));
                    }
                }
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case NEXTFRAME:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new NextFrameCommand());
                break;
            case PLAY:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new PlayCommand());
                break;
            case PREVFRAME:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new PrevFrameCommand());
                break;
            case STOP:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new StopCommand());
                break;
            case STOPALLSOUNDS:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new StopAllSoundsCommand());
                break;
            case TOGGLEHIGHQUALITY:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new ToggleHighQualityCommand());
                break;

            case STOPDRAG:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret.add(new StopDragCommand());
                break;

            case UNLOADMOVIE:
            case UNLOADMOVIENUM:
                SymbolType unloadType = s.type;
                expectedType(SymbolType.PARENT_OPEN);
                Expression unTargetOrNum = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                
                int level = -1;
                if (unloadType == SymbolType.UNLOADMOVIENUM && unTargetOrNum.isNumeric) {
                    level = (int)(double)Double.parseDouble(unTargetOrNum.value);
                }
                if (level > -1) {
                    ret.add(new UnloadMovieCommand(level));
                } else {
                    ret.add(new UnloadMovieCommand(unTargetOrNum, unloadType == SymbolType.UNLOADMOVIENUM));
                }
                                
                break;
            
            case LOADVARIABLES:
            case LOADMOVIE:
            case LOADVARIABLESNUM:
            case LOADMOVIENUM:
                SymbolType loadType = s.type;
                expectedType(SymbolType.PARENT_OPEN);
                Expression url2 = expression();
                expectedType(SymbolType.COMMA);
                Expression targetOrNum = expression();

                s = lex();
                expected(s, lexer.yyline(), SymbolType.PARENT_CLOSE, SymbolType.COMMA);
                String lvmethod = "";
                if (s.type == SymbolType.COMMA) {
                    s = lex();
                    expected(s, lexer.yyline(), SymbolType.STRING);
                    if (s.value.equals("POST")) {
                        lvmethod = "POST";
                    } else if (s.value.equals("GET")) {
                        lvmethod = "GET";
                    } else {
                        throw new ScriptParseException("Invalid method, \"GET\" or \"POST\" expected.", lexer.yyline());
                    }
                } else {
                    lexer.pushback(s);
                }
                
                int num = -1;
                if (targetOrNum.isNumeric) {
                    num = (int)(double)Double.parseDouble(targetOrNum.value);
                }
                
                expectedType(SymbolType.PARENT_CLOSE);
                switch (loadType) {
                    case LOADVARIABLES:
                        ret.add(new LoadVariablesCommand(url2, targetOrNum, true, lvmethod));
                        break;
                    case LOADMOVIE:
                        ret.add(new LoadMovieCommand(url2, targetOrNum, true, lvmethod));                        
                        break;
                    case LOADVARIABLESNUM:
                        if (num > -1) {
                            ret.add(new LoadVariablesCommand(url2, num, lvmethod));
                        } else {
                            ret.add(new LoadVariablesCommand(url2, targetOrNum, false, lvmethod));
                        }
                        break;
                    case LOADMOVIENUM:
                        if (num > -1) {
                            ret.add(new LoadMovieCommand(url2, num, lvmethod));
                        } else {
                            ret.add(new LoadMovieCommand(url2, targetOrNum, false, lvmethod));
                        }
                        break;
                }
                break;
            case REMOVEMOVIECLIP:
                expectedType(SymbolType.PARENT_OPEN);
                ret.add(new RemoveMovieClipCommand(expression()));
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case STARTDRAG:
                expectedType(SymbolType.PARENT_OPEN);
                Expression dragTarget = expression();
                boolean lockCenter = false;
                boolean constrain = false;
                Expression x1 = new Expression();
                Expression y1 = new Expression();
                Expression x2 = new Expression();
                Expression y2 = new Expression();
                s = lex();
                if (s.type == SymbolType.COMMA) {
                    Expression lockCenterExpr = expression();
                    if (!lockCenterExpr.isRaw && lockCenterExpr.value.equals("true")) {
                        lockCenter = true;
                    }
                    s = lex();
                    if (s.type == SymbolType.COMMA) {
                        constrain = true;
                        x1 = expression();
                        s = lex();
                        if (s.type == SymbolType.COMMA) {
                            y1 = expression();
                            s = lex();
                            if (s.type == SymbolType.COMMA) {
                                x2 = expression();
                                s = lex();
                                if (s.type == SymbolType.COMMA) {
                                    y2 = expression();
                                } else {
                                    lexer.pushback(s);
                                    y2 = new Expression("0", true, true);
                                }
                            } else {
                                lexer.pushback(s);
                                x2 = new Expression("0", true, true);
                                y2 = new Expression("0", true, true);
                            }
                        } else {
                            lexer.pushback(s);
                            x2 = new Expression("0", true, true);
                            y2 = new Expression("0", true, true);
                            y1 = new Expression("0", true, true);

                        }
                    } else {
                        lexer.pushback(s);                        
                    }
                } else {    
                    lexer.pushback(s);
                }
                expectedType(SymbolType.PARENT_CLOSE);
                if (constrain) {
                    ret.add(new StartDragCommand(dragTarget, x1, y1, x2, y2, lockCenter));
                } else {
                    ret.add(new StartDragCommand(dragTarget, lockCenter));
                }
                break;
            case CALL:
                expectedType(SymbolType.PARENT_OPEN);
                ret.add(new CallCommand(expression()));
                expectedType(SymbolType.PARENT_CLOSE);
                break;            
            case TELLTARGET:
                int tellTargetLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                Expression tellTarget = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                expectedType(SymbolType.CURLY_OPEN);
                ret.add(new BeginTellTargetCommand(tellTarget));
                ret.addAll(commands());
                ret.add(new EndTellTargetCommand());
                expectedType(SymbolType.CURLY_CLOSE);                
                
                break;

            case IFFRAMELOADED:
                int ifFrameLoadedLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                Expression iflExpr = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                expectedType(SymbolType.CURLY_OPEN);
                int iflFrameNum = -1;
                if (iflExpr.isNumeric) {
                    iflFrameNum = (int)(double)Double.parseDouble(iflExpr.value);
                }
                if (iflFrameNum > -1) {
                    ret.add(new IfFrameIsLoadedCommand(iflFrameNum));
                } else {
                    ret.add(new IfFrameIsLoadedCommand(iflExpr));
                }
                ret.addAll(commands());
                ret.add(new EndFrameLoadedCommand());
                expectedType(SymbolType.CURLY_CLOSE);
                break;            
            case VAR:
                s = lex();
                expectedIdentifier(s, lexer.yyline());
                String varIdentifier = s.value.toString();
                s = lex();
                if (s.type == SymbolType.ASSIGN) {
                    Expression varval = expression();
                    ret.add(new SetVariableCommand(new Expression(varIdentifier, true), varval));
                } else {
                    lexer.pushback(s);
                }
                break;
            case CURLY_OPEN:
                ret.addAll(commands());
                expectedType(SymbolType.CURLY_CLOSE);
                break;            
            case IF:
                int ifLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                Expression ifExpr = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                
                ret.add(new IfCommand(ifExpr));
                ret.addAll(command(true));
                s = lex();
                if (s.type == SymbolType.ELSE) {
                    ret.add(new ElseCommand());
                    ret.addAll(command(true));
                } else {
                    lexer.pushback(s);
                }
                ret.add(new EndIfCommand());                
                break;
            case WHILE:
                int whileLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                ret.add(new LoopWhileCommand(expression()));
                expectedType(SymbolType.PARENT_CLOSE);
                ret.addAll(command(true));
                ret.add(new EndLoopCommand());                
                break;
            case SEMICOLON: //empty command
                if (debugMode) {
                    System.out.println("/command");
                }
                return ret;            
            case IDENTIFIER:            
                String lowercaseIdent = s.value.toString().toLowerCase();
                if ("on".equals(lowercaseIdent)) {
                    expectedType(SymbolType.PARENT_OPEN);
                    ParsedSymbol symb = lex();
                    boolean condEmpty = true;
                    boolean onPress = false;
                    boolean onRelease = false;
                    boolean onReleaseOutside = false;
                    boolean onRollOver = false;
                    boolean onRollOut = false;
                    boolean onDragOver = false;
                    boolean onDragOut = false;
                    boolean onKeyPress = false;
                    int key = AbstractCommand.NO_KEY;
                    while (symb.type == SymbolType.IDENTIFIER) {
                        condEmpty = false;
                        switch ((String) symb.value) {
                            case "press":
                                onPress = true;
                                break;
                            case "release":
                                onRelease = true;
                                break;
                            case "releaseOutside":
                                onReleaseOutside = true;
                                break;
                            case "rollOver":
                                onRollOver = true;
                                break;
                            case "rollOut":
                                onRollOut = true;
                                break;
                            case "dragOver":
                                onDragOver = true;
                                break;
                            case "dragOut":
                                onDragOut = true;
                                break;
                            case "keyPress":
                                symb = lex();
                                expected(symb, lexer.yyline(), SymbolType.STRING);
                                Integer keyObj = OnCommand.stringToKey((String) symb.value);
                                if (keyObj == null) {
                                    throw new ScriptParseException("Invalid key", lexer.yyline());
                                }
                                key = keyObj;
                                onKeyPress = true;
                                break;
                            default:
                                throw new ScriptParseException("Unrecognized event type", lexer.yyline());
                        }
                        symb = lex();
                        if (symb.type == SymbolType.PARENT_CLOSE) {
                            break;
                        }
                        expected(symb, lexer.yyline(), SymbolType.COMMA);
                        symb = lex();
                    }
                    expected(symb, lexer.yyline(), SymbolType.PARENT_CLOSE);
                    if (condEmpty) {
                        throw new ScriptParseException("condition must be non empty", lexer.yyline());
                    }
                    expectedType(SymbolType.CURLY_OPEN);
                    
                    if (flaVersion.ordinal() <= FlaFormatVersion.F2.ordinal() && (onPress || onReleaseOutside || onRollOver || onRollOut || onDragOver || onDragOut)) {
                        throw new ScriptParseException("Only release handler is available in " + flaVersion + " FLA", lexer.yyline());
                    }
                    
                    List<AbstractCommand> commands = commands();
                    
                    if (!commands.isEmpty()) {
                        boolean hasOnClause = onPress || onReleaseOutside || onRollOver || onRollOut || onDragOver || onDragOut;
                        if (hasOnClause) {
                            if (onKeyPress) {
                                ret.add(new OnCommand(onPress, onRelease, onReleaseOutside, onRollOver, onRollOut, onDragOver, onDragOut, key));
                            } else {
                                ret.add(new OnCommand(onPress, onRelease, onReleaseOutside, onRollOver, onRollOut, onDragOver, onDragOut));
                            }
                        }
                        ret.addAll(commands);
                        if (hasOnClause) {
                            ret.add(new EndOnCommand());
                        }
                    }
                    expectedType(SymbolType.CURLY_CLOSE);
                } else if ("nextscene".equals(lowercaseIdent)) {
                    expectedType(SymbolType.PARENT_OPEN);
                    expectedType(SymbolType.PARENT_CLOSE);                    
                    ret.add(new NextSceneCommand());
                } else if ("prevscene".equals(lowercaseIdent)) {
                    expectedType(SymbolType.PARENT_OPEN);
                    expectedType(SymbolType.PARENT_CLOSE);
                    ret.add(new PrevSceneCommand());
                } else { //general identifier
                    Expression varName = new Expression(s.value.toString(), true);
                    ParsedSymbol symb = lex();
                    if (symb.isType(SymbolType.ASSIGN)) {
                        Expression expr = expression();
                        ret.add(new SetVariableCommand(varName, expr));
                    } else {
                        throw new ScriptParseException("Unknown identifier", lexer.yyline());
                    }
                }
                break;
            default:
                lexer.pushback(s);
        }
        if (debugMode) {
            System.out.println("/command");
        }
        lexer.removeListener(buf);
        if (ret == null) {  //can be popped expression
            buf.pushAllBack(lexer);
        }
        s = lex();
        if ((s != null) && (s.type != SymbolType.SEMICOLON)) {
            lexer.pushback(s);
        }

        return ret;

    }            

    
    private Expression expression() throws IOException, ScriptParseException, InterruptedException {
        if (debugMode) {
            System.out.println("expression:");
        }
        Expression prim = expressionPrimary(false, true);
        if (prim == null) {
            return new Expression();
        }
        Expression expr = expression1(prim, Precedence.NOPRECEDENCE);
            
        if (debugMode) {
            System.out.println("/expression");
        }
        return expr;
    }
    
    private Expression handleVariable(ParsedSymbol s) throws IOException, ScriptParseException, InterruptedException {
        Expression ret;
        if (s.value.equals("not")) {
            ret = new Expression("not " + expressionPrimary(false, true));
        } else {
            String varName = s.value.toString();           

            ret = new Expression(varName);
        }
        return ret;
    }
    
    private Expression expressionPrimary(boolean allowEmpty, boolean allowCall) throws IOException, ScriptParseException, InterruptedException {
        if (debugMode) {
            System.out.println("primary:");
        }
        Expression ret = null;
        ParsedSymbol s = lex();

        switch (s.type) {
            case PREPROCESSOR:
                throw new ScriptParseException("Unknown preprocessor instruction: §§" + s.value, lexer.yyline());                
            case MINUS:
                s = lex();
                if (s.isType(SymbolType.DOUBLE)) {
                    ret = new Expression("" + (-(double) (Double) s.value), true, true);

                } else if (s.isType(SymbolType.INTEGER)) {
                    ret = new Expression("" + (-(long) (Long) s.value), true, true);
                } else {
                    lexer.pushback(s);
                    Expression num = expressionPrimary(false, true);
                    ret = new Expression("- (" + num + ")");
                }
                break;
            case TRUE:
                ret = new Expression("True");
                break;
            case FALSE:
                ret = new Expression("False");
                break;            
            case STRING:
                ret = new Expression((String) s.value, true);
                break;
            case NEWLINE:
                ret = new Expression("Newline");
                break;
            case INTEGER:
            case DOUBLE:
                ret = new Expression("" + s.value, true, true);
                break;                        
            case NOT:
                ret = new Expression("not " + expressionPrimary(false, true));
                break;
            case PARENT_OPEN:
                Expression pexpr = expression();
                if (pexpr == null) {
                    throw new ScriptParseException("Expression expected", lexer.yyline());
                }                
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new Expression("(" + pexpr + ")");
                break;            
            case EVAL:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("Eval(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);                
                break;
            case MBORD:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("MBOrd(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case MBCHR:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("MBChr(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case MBLENGTH:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("MBLength(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case MBSUBSTRING:
                expectedType(SymbolType.PARENT_OPEN);
                Expression val1 = expression();
                expectedType(SymbolType.COMMA);
                Expression index1 = expression();
                expectedType(SymbolType.COMMA);
                Expression len1 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new Expression("MBSubstring(" + val1 + "," + index1 +"," + len1 + ")");
                break;
            case SUBSTRING:
                expectedType(SymbolType.PARENT_OPEN);
                Expression val2 = expression();
                expectedType(SymbolType.COMMA);
                Expression index2 = expression();
                expectedType(SymbolType.COMMA);
                Expression len2 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new Expression("Substring(" + val2 + "," + index2 +"," + len2 + ")");
                break;
            case LENGTH:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("Length(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case RANDOM:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("Random(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case INT:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("Int(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;                       
            case ORD:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("Ord(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case CHR:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new Expression("Chr(" + expression() + ")");
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case GETTIMER:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new Expression("GetTimer()");
                break;
            case IDENTIFIER:                
                if (s.value.toString().toLowerCase().equals("getpropety")) {
                    expectedType(SymbolType.PARENT_OPEN);
                    Expression gpTarget = expression();
                    expectedType(SymbolType.COMMA);
                    ParsedSymbol sprop = lex();
                    expectedIdentifier(sprop, lexer.yyline());
                    ret = new Expression("GetProperty(" + gpTarget+ "," + sprop.value.toString() + ")");
                    expectedType(SymbolType.PARENT_CLOSE);
                } else {                
                    ret = handleVariable(s);
                }
                break;
            default:

                boolean isGlobalFuncVar = false;
                if (s.group == SymbolGroup.GLOBALFUNC) {
                    ParsedSymbol s2 = peekLex();
                    if (s2.type != SymbolType.PARENT_OPEN) {
                        ret = handleVariable(s);
                        isGlobalFuncVar = true;
                    }
                }

                if (!isGlobalFuncVar) {
                    lexer.pushback(s);
                }
        }      
        if (debugMode) {
            System.out.println("/primary");
        }
        return ret;
    }

    private ParsedSymbol peekLex() throws IOException, ScriptParseException, InterruptedException {
        ParsedSymbol lookahead = lex();
        lexer.pushback(lookahead);
        return lookahead;
    }

    private static final String[] operatorIdentifiers = new String[]{"add", "eq", "ne", "lt", "ge", "gt", "le"};

    private boolean isBinaryOperator(ParsedSymbol s) {
        if (s.type == SymbolType.IDENTIFIER && Arrays.asList(operatorIdentifiers).contains(s.value.toString())) {
            return true;
        }
        return s.type.isBinary();
    }

    private int getSymbPrecedence(ParsedSymbol s) {
        if (s.type == SymbolType.IDENTIFIER && Arrays.asList(operatorIdentifiers).contains(s.value.toString())) {
            switch (s.value.toString()) {
                case "add":
                    return Precedence.PRECEDENCE_ADDITIVE;
                case "eq":
                case "ne":
                    return Precedence.PRECEDENCE_EQUALITY;
                case "lt":
                case "ge":
                case "gt":
                case "le":
                    return Precedence.PRECEDENCE_RELATIONAL;
            }
        }
        return s.type.getPrecedence();
    }

    private Expression expression1(Expression lhs, int min_precedence) throws IOException, ScriptParseException, InterruptedException {
        ParsedSymbol op;
        Expression rhs;
        ParsedSymbol lookahead = peekLex();
        if (debugMode) {
            System.out.println("expression1:");
        }
        //Note: algorithm from http://en.wikipedia.org/wiki/Operator-precedence_parser
        //with relation operators reversed as we have precedence in reverse order
        while (isBinaryOperator(lookahead) && getSymbPrecedence(lookahead) <= min_precedence) {
            op = lookahead;
            lex();           

            rhs = expressionPrimary(true, true);
            if (rhs == null) {
                throw new ScriptParseException("Missing operand", lexer.yyline());
                //lexer.pushback(op);
                //break;
            }

            lookahead = peekLex();
            while ((isBinaryOperator(lookahead) && getSymbPrecedence(lookahead) < getSymbPrecedence(op))
                    || (lookahead.type.isRightAssociative() && getSymbPrecedence(lookahead) == getSymbPrecedence(op))) {
                rhs = expression1(rhs, getSymbPrecedence(lookahead));
                lookahead = peekLex();
            }

            switch (op.type) {
                case DIVIDE:
                    lhs = new Expression("(" + lhs + " / " + rhs + ")");
                    break;
                case EQUALS:
                    lhs = new Expression("(" + lhs + " = " + rhs + ")");
                    break;
                case NOT_EQUAL:
                    lhs = new Expression("(" + lhs + " <> " + rhs + ")");
                    break;
                case LOWER_THAN:
                    lhs = new Expression("(" + lhs + " < " + rhs + ")");
                    break;
                case LOWER_EQUAL:
                    lhs = new Expression("(" + lhs + " <= " + rhs + ")");
                    break;
                case GREATER_THAN:
                    lhs = new Expression("(" + lhs + " > " + rhs + ")");
                    break;
                case GREATER_EQUAL:
                    lhs = new Expression("(" + lhs + " >= " + rhs + ")");
                    break;
                case FULLAND:
                    lhs = new Expression("(" + lhs + " and " + rhs + ")");
                    break;
                case FULLOR:
                    lhs = new Expression("(" + lhs + " or " + rhs + ")");
                    break;
                case MINUS:
                    lhs = new Expression("(" + lhs + " - " + rhs + ")");
                    break;
                case MULTIPLY:
                    lhs = new Expression("(" + lhs + " * " + rhs + ")");
                    break;
                case PLUS:
                    lhs = new Expression("(" + lhs + " + " + rhs + ")");
                    break;
                case IDENTIFIER:
                    switch (op.value.toString()) {
                        case "add":
                            lhs = new Expression("(" + lhs + " & " + rhs + ")");
                            break;
                        case "eq":
                        case "ne":
                        case "lt":
                        case "ge":
                        case "gt":
                        case "le":
                            lhs = new Expression("(" + lhs + " " + op.value.toString() + " " + rhs + ")");
                            break;
                    }
                    break;
            }
        }

        if (debugMode) {
            System.out.println("/expression1");
        }
        return lhs;
    }
    
    /*

    private List<GraphTargetItem> commands(boolean inWith, boolean inFunction, boolean inMethod, int forinlevel, boolean inTellTarget, List<VariableActionItem> variables, List<FunctionActionItem> functions, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        List<GraphTargetItem> ret = new ArrayList<>();
        if (debugMode) {
            System.out.println("commands:");
        }
        GraphTargetItem cmd;
        while ((cmd = command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval)) != null) {
            ret.add(cmd);
        }
        if (debugMode) {
            System.out.println("/commands");
        }
        return ret;
    }

    private GraphTargetItem type(List<VariableActionItem> variables) throws IOException, ActionParseException, InterruptedException {
        GraphTargetItem ret;

        ParsedSymbol s = lex();
        expectedIdentifier(s, lexer.yyline());
        ret = new VariableActionItem(s.value.toString(), null, false);
        variables.add((VariableActionItem) ret);
        s = lex();
        while (s.type == SymbolType.DOT) {
            s = lex();
            expectedIdentifier(s, lexer.yyline());
            ret = new GetMemberActionItem(null, null, ret, pushConst(s.value.toString()));
            s = lex();
        }
        lexer.pushback(s);
        return ret;
    }

    private void expected(ParsedSymbol symb, int line, Object... expected) throws IOException, ActionParseException {
        boolean found = false;
        for (Object t : expected) {
            if (symb.type == t) {
                found = true;
            }
            if (symb.group == t) {
                found = true;
            }
        }
        if (!found) {
            String expStr = "";
            boolean first = true;
            for (Object e : expected) {
                if (!first) {
                    expStr += " or ";
                }
                expStr += e;
                first = false;
            }
            throw new ActionParseException("" + expStr + " expected but " + symb.type + " found", line);
        }
    }

    private ParsedSymbol expectedType(Object... type) throws IOException, ActionParseException, InterruptedException {
        ParsedSymbol symb = lex();
        expected(symb, lexer.yyline(), type);
        return symb;
    }

    private ParsedSymbol lex() throws IOException, ActionParseException, InterruptedException {
        if (CancellableWorker.isInterrupted()) {
            throw new InterruptedException();
        }
        ParsedSymbol ret = lexer.lex();
        if (ret.type == SymbolType.IDENTIFIER) {
            if (replacements.containsKey(ret.value.toString())) {
                ret.value = replacements.get(ret.value.toString());
            }
        }
        if (debugMode) {
            System.out.println(ret);
        }
        return ret;
    }

    private List<GraphTargetItem> call(boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, List<VariableActionItem> variables, List<FunctionActionItem> functions, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        List<GraphTargetItem> ret = new ArrayList<>();
        //expected(SymbolType.PARENT_OPEN); //MUST BE HANDLED BY CALLER
        ParsedSymbol s = lex();
        while (s.type != SymbolType.PARENT_CLOSE) {
            if (s.type != SymbolType.COMMA) {
                lexer.pushback(s);
            }
            ret.addexpression();
            s = lex();
            expected(s, lexer.yyline(), SymbolType.COMMA, SymbolType.PARENT_CLOSE);
        }
        return ret;
    }

    private FunctionActionItem function(boolean withBody, String functionName, boolean isMethod, List<VariableActionItem> variables, List<FunctionActionItem> functions, boolean inTellTarget, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        int functionLine = lexer.yyline();
        GraphTargetItem ret = null;
        ParsedSymbol s;
        expectedType(SymbolType.PARENT_OPEN);
        s = lex();
        List<String> paramNames = new ArrayList<>();

        while (s.type != SymbolType.PARENT_CLOSE) {
            if (s.type != SymbolType.COMMA) {
                lexer.pushback(s);
            }
            s = lex();
            expectedIdentifier(s, lexer.yyline());
            paramNames.add(s.value.toString());
            s = lex();
            if (s.type == SymbolType.COLON) {
                type(variables);
                s = lex();
            }

            if (!s.isType(SymbolType.COMMA, SymbolType.PARENT_CLOSE)) {
                expected(s, lexer.yyline(), SymbolType.COMMA, SymbolType.PARENT_CLOSE);
            }
        }
        List<GraphTargetItem> body = null;
        List<VariableActionItem> subvariables = new ArrayList<>();
        List<FunctionActionItem> subfunctions = new ArrayList<>();
        Reference<Boolean> subHasEval = new Reference<>(false);
        if (withBody) {
            expectedType(SymbolType.CURLY_OPEN);
            body = commands(false, true, isMethod, 0, inTellTarget, subvariables, subfunctions, subHasEval);
            expectedType(SymbolType.CURLY_CLOSE);
        }

        if (subHasEval.getVal()) {
            hasEval.setVal(true);
        }

        FunctionActionItem retf = new FunctionActionItem(null, null, functionName, paramNames, new HashMap<>(), body, constantPool, -1, subvariables, subfunctions, subHasEval.getVal(), new ArrayList<>(), null);
        functions.add(retf);
        retf.line = functionLine;
        return retf;
    }

    private GetMemberActionItem getFirstGetMember(GraphTargetItem item) {
        while (item instanceof GetMemberActionItem) {
            GetMemberActionItem mem = (GetMemberActionItem) item;
            if (!(mem.memberName instanceof DirectValueActionItem)) {
                return null;
            }
            DirectValueActionItem dv = ((DirectValueActionItem) mem.memberName);
            if (!dv.isString()) {
                return null;
            }
            if (!(mem.object instanceof GetMemberActionItem)) {
                return mem;
            }
            item = mem.object;
        }
        return null;
    }

    private List<String> getMembersPath(GraphTargetItem item) {
        List<String> ret = new ArrayList<>();
        while (item instanceof GetMemberActionItem) {
            GetMemberActionItem mem = (GetMemberActionItem) item;
            if (!(mem.memberName instanceof DirectValueActionItem)) {
                return null;
            }
            DirectValueActionItem dv = ((DirectValueActionItem) mem.memberName);
            if (!dv.isString()) {
                return null;
            }
            ret.add(0, dv.getAsString());
            item = mem.object;
        }
        if (item instanceof DirectValueActionItem) {
            DirectValueActionItem dv1 = (DirectValueActionItem) item;
            if (dv1.value instanceof RegisterNumber) {
                RegisterNumber rn = (RegisterNumber) dv1.value;
                if ("this".equals(rn.name)) {
                    ret.add(0, "this");
                    return ret;
                }
                if ("super".equals(rn.name)) {
                    ret.add(0, "super");
                    return ret;
                }
            }
        }
        if (!((item instanceof GetVariableActionItem || item instanceof VariableActionItem))) {
            return null;
        }

        if (item instanceof GetVariableActionItem) {
            GetVariableActionItem gv = (GetVariableActionItem) item;
            if (!(gv.name instanceof DirectValueActionItem)) {
                return null;
            }
            DirectValueActionItem dv = ((DirectValueActionItem) gv.name);
            if (!dv.isString()) {
                return null;
            }
            String varName = dv.getAsString();
            ret.add(0, varName);
            return ret;
        }

        if (item instanceof VariableActionItem) {
            VariableActionItem v = (VariableActionItem) item;
            ret.add(0, v.getVariableName());
            return ret;
        }

        return null;
    }
    
    private void fetchGettersSetters(List<String> parts, Set<String> superGetProperties, Set<String> superSetProperties) {
        if (parts == null) {
            return;
        }         
        String fullExtendsName = String.join(".", parts);

        String clsName = parts.remove(parts.size() - 1);

        String key;
        if (Configuration.flattenASPackages.get()) {
            key = "\\__Packages\\" + String.join(".", parts) + "\\" + clsName;
        } else {
            key = "\\__Packages\\" + String.join("\\", parts) + "\\" + clsName;
        }

        ASMSource extendsAsm = swf.getASMs(false).get(key);
        if (extendsAsm == null) {
            return;
        }
        
        List<GraphTargetItem> list = extendsAsm.getActionsToTree();
        for (GraphTargetItem it2 : list) {
            if (it2 instanceof ClassActionItem) {
                ClassActionItem cai = (ClassActionItem) it2;
                List<String> cName = getMembersPath(cai.className);
                if (cName != null) {
                    String fullExtendsName2 = String.join(".", cName);
                    if (fullExtendsName.equals(fullExtendsName2)) {
                        for (MyEntry<GraphTargetItem, GraphTargetItem> entry : cai.traits) {
                            String keyAsStr = entry.getKey().toString();
                            if (keyAsStr.startsWith("__set__")) {
                                superSetProperties.add(keyAsStr.substring("__set__".length()));
                            }
                            if (keyAsStr.startsWith("__get__")) {
                                superGetProperties.add(keyAsStr.substring("__get__".length()));
                            }
                        }
                    }
                }
                if (cai.extendsOp != null) {
                    fetchGettersSetters(getMembersPath(cai.extendsOp), superGetProperties, superSetProperties);
                }
            }
        }        
    }

    private GraphTargetItem traits(boolean isInterface, GraphTargetItem nameStr, GraphTargetItem extendsStr, List<GraphTargetItem> implementsStr, List<VariableActionItem> variables, List<FunctionActionItem> functions, boolean inTellTarget, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {

        Set<String> superGetProperties = new HashSet<>();
        Set<String> superSetProperties = new HashSet<>();
        Set<String> thisGetProperties = new HashSet<>();
        Set<String> thisSetProperties = new HashSet<>();

        if (extendsStr != null) {
            fetchGettersSetters(getMembersPath(extendsStr), superGetProperties, superSetProperties);
        }
        
        thisGetProperties.addAll(superGetProperties);
        thisSetProperties.addAll(superSetProperties);

        
        ParsedSymbol s;
        List<MyEntry<GraphTargetItem, GraphTargetItem>> traits = new ArrayList<>();
        List<Boolean> traitsStatic = new ArrayList<>();

        String classNameStr = "";
        if (nameStr instanceof GetMemberActionItem) {
            GetMemberActionItem mem = (GetMemberActionItem) nameStr;
            if (mem.memberName instanceof VariableActionItem) {
                classNameStr = ((VariableActionItem) mem.memberName).getVariableName();
            } else if (mem.memberName instanceof DirectValueActionItem) {
                classNameStr = ((DirectValueActionItem) mem.memberName).toStringNoQuotes(LocalData.empty);
            }
        } else if (nameStr instanceof VariableActionItem) {
            VariableActionItem var = (VariableActionItem) nameStr;
            classNameStr = var.getVariableName();
        }

        looptrait:
        while (true) {
            s = lex();
            boolean isGetter = false;
            boolean isSetter = false;
            boolean isStatic = false;
            while (s.isType(SymbolType.STATIC, SymbolType.PUBLIC, SymbolType.PRIVATE)) {
                if (s.type == SymbolType.STATIC) {
                    isStatic = true;
                }
                s = lex();
            }
            switch (s.type) {
                case FUNCTION:
                    s = lex();

                    if (s.type == SymbolType.SET) {
                        isSetter = true;
                        s = lex();
                    } else if (s.type == SymbolType.GET) {
                        isGetter = true;
                        s = lex();
                    }

                    expectedIdentifier(s, lexer.yyline());
                    String fname = s.value.toString();
                    if (fname.equals(classNameStr)) { //constructor
                        //actually there's no difference, it's instance trait
                    }

                    if (!isStatic) {
                        if (isGetter) {
                            thisGetProperties.add(fname);
                        }
                        if (isSetter) {
                            thisSetProperties.add(fname);
                        }
                    }

                    if (!isInterface) {
                        if (isStatic) {
                            FunctionActionItem ft = function(!isInterface, "", true, variables, functions, inTellTarget, hasEval);
                            ft.calculatedFunctionName = pushConst(fname);
                            ft.isSetter = isSetter;
                            ft.isGetter = isGetter;
                            //staticFunctions.add(ft);
                            traits.add(new MyEntry<>(ft.calculatedFunctionName, ft));
                            traitsStatic.add(true);

                            if (isSetter) {
                                //add return getter automatically
                                GraphTargetItem callM = new CallMethodActionItem(null, null, nameStr, pushConst("__get__" + fname), new ArrayList<>());
                                GraphTargetItem retV = new ReturnActionItem(null, null, callM);
                                ft.actions.add(retV);
                            }
                        } else {
                            FunctionActionItem ft = function(!isInterface, "", true, variables, functions, inTellTarget, hasEval);
                            ft.calculatedFunctionName = pushConst(fname);
                            ft.isSetter = isSetter;
                            ft.isGetter = isGetter;
                            //instanceFunctions.add(ft);
                            traits.add(new MyEntry<>(ft.calculatedFunctionName, ft));
                            traitsStatic.add(false);

                            if (isSetter) {
                                //add return getter automatically
                                GraphTargetItem thisVar = new VariableActionItem("this", null, false);
                                ft.addVariable((VariableActionItem) thisVar);
                                GraphTargetItem callM = new CallMethodActionItem(null, null, thisVar, pushConst("__get__" + fname), new ArrayList<>());
                                GraphTargetItem retV = new ReturnActionItem(null, null, callM);
                                ft.actions.add(retV);
                            }
                        }

                    }
                    break;
                case VAR:
                    s = lex();
                    expectedIdentifier(s, lexer.yyline());
                    String ident = s.value.toString();
                    s = lex();
                    if (s.type == SymbolType.COLON) {
                        type(variables);
                        s = lex();
                    }
                    if (s.type == SymbolType.ASSIGN) {
                        traits.add(new MyEntry<>(pushConst(ident), expression(false, false, false, false, true, variables, functions, false, hasEval)));
                        traitsStatic.add(isStatic);
                        s = lex();
                    }
                    if (s.type != SymbolType.SEMICOLON) {
                        lexer.pushback(s);
                    }
                    break;
                default:
                    lexer.pushback(s);
                    break looptrait;

            }
        }

        for (MyEntry<GraphTargetItem, GraphTargetItem> it : traits) {
            GraphTargetItem val = it.getValue();
            Set<GraphTargetItem> subItems = val.getAllSubItemsRecursively();
            subItems.add(val);
            for (GraphTargetItem si : subItems) {
                if (si instanceof GetMemberActionItem) {
                    List<String> path = getMembersPath(si);
                    if (path != null) {
                        String varName = path.get(0);
                        String memberName = path.get(1);
                        switch (varName) {
                            case "this":
                                if (thisGetProperties.contains(memberName)) {
                                    GetMemberActionItem gm = getFirstGetMember(si);
                                    gm.isGetter = true;
                                }
                                break;
                            case "super":
                                if (superGetProperties.contains(memberName)) {
                                    GetMemberActionItem gm = getFirstGetMember(si);
                                    gm.isGetter = true;
                                }
                                break;
                        }
                    }
                }
                if (si instanceof SetMemberActionItem) {
                    SetMemberActionItem sm = (SetMemberActionItem) si;
                    if (sm.objectName instanceof DirectValueActionItem
                            && (sm.object instanceof VariableActionItem)) {

                        String memberName = ((DirectValueActionItem) sm.objectName).getAsString();

                        VariableActionItem v = (VariableActionItem) sm.object;
                        if ("this".equals(v.getVariableName())) {
                            if (thisSetProperties.contains(memberName)) {
                                sm.isSetter = true;
                            }
                        }
                        if ("super".equals(v.getVariableName())) {
                            if (superSetProperties.contains(memberName)) {
                                sm.isSetter = true;
                            }
                        }
                    }
                }
                if (si instanceof CallMethodActionItem) {
                    CallMethodActionItem cm = (CallMethodActionItem) si;
                    if (cm.methodName instanceof DirectValueActionItem
                            && (cm.scriptObject instanceof VariableActionItem)) {
                        String memberName = ((DirectValueActionItem) cm.methodName).getAsString();

                        VariableActionItem v = (VariableActionItem) cm.scriptObject;
                        if ("this".equals(v.getVariableName())) {
                            if (thisGetProperties.contains(memberName)) {
                                cm.isGetter = true;
                            }
                        }
                        if ("super".equals(v.getVariableName())) {
                            if (superGetProperties.contains(memberName)) {
                                cm.isGetter = true;
                            }
                        }
                    }
                }
                if (si instanceof NewMethodActionItem) {
                    NewMethodActionItem nm = (NewMethodActionItem) si;
                    if (nm.methodName instanceof DirectValueActionItem
                            && (nm.scriptObject instanceof VariableActionItem)) {
                        String memberName = ((DirectValueActionItem) nm.methodName).getAsString();

                        VariableActionItem v = (VariableActionItem) nm.scriptObject;
                        if ("this".equals(v.getVariableName())) {
                            if (thisGetProperties.contains(memberName)) {
                                nm.isGetter = true;
                            }
                        }
                        if ("super".equals(v.getVariableName())) {
                            if (superGetProperties.contains(memberName)) {
                                nm.isGetter = true;
                            }
                        }
                    }
                }
                
                if (si instanceof DeleteActionItem) {
                    DeleteActionItem d = (DeleteActionItem) si;
                    if (d.propertyName instanceof DirectValueActionItem
                            && (d.object instanceof VariableActionItem)) {
                        String memberName = ((DirectValueActionItem) d.propertyName).getAsString();

                        VariableActionItem v = (VariableActionItem) d.object;
                        if ("this".equals(v.getVariableName())) {
                            if (thisGetProperties.contains(memberName)) {
                                d.isGetter = true;
                            }
                        }
                        if ("super".equals(v.getVariableName())) {
                            if (superGetProperties.contains(memberName)) {
                                d.isGetter = true;
                            }
                        }
                    }
                }
            }
        }

        if (isInterface) {
            return new InterfaceActionItem(nameStr, implementsStr);
        } else {
            return new ClassActionItem(nameStr, extendsStr, implementsStr, traits, traitsStatic);
        }
    }

    private GraphTargetItem expressionCommands(ParsedSymbol s, boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, int forinlevel, List<VariableActionItem> variables, List<FunctionActionItem> functions, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        if (debugMode) {
            System.out.println("expressionCommands:");
        }
        if (inWith) {
            switch (s.type) {
                case DUPLICATEMOVIECLIP:
                case GETURL:
                case GOTOANDSTOP:
                case GOTOANDPLAY:
                case NEXTFRAME:
                case PLAY:
                case PREVFRAME:
                case STOP:
                case UNLOADMOVIE:
                case UNLOADMOVIENUM:
                case LOADVARIABLES:
                case LOADMOVIE:
                case LOADVARIABLESNUM:
                case LOADMOVIENUM:
                case REMOVEMOVIECLIP:
                    GraphTargetItem functionName = pushConst((String) s.value);
                    expectedType(SymbolType.PARENT_OPEN);
                    List<GraphTargetItem> args = call(inWith, inFunction, inMethod, inTellTarget, variables, functions, hasEval);
                    return new CallFunctionActionItem(null, null, functionName, args);
            }
        }

        GraphTargetItem ret = null;
        switch (s.type) {
            case DUPLICATEMOVIECLIP:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem src3 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem tar3 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem dep3 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new CloneSpriteActionItem(null, null, src3, tar3, dep3);
                break;
            case FSCOMMAND:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem command = expression();
                s = lex();
                GraphTargetItem parameter = null;
                if (s.isType(SymbolType.COMMA)) {
                    parameter = expression();
                } else {
                    lexer.pushback(s);
                }
                ret = new FSCommandActionItem(null, null, command, parameter);
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case FSCOMMAND2:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem arg0 = expression();
                List<GraphTargetItem> args = new ArrayList<>();
                args.add(arg0);
                s = lex();
                while (s.isType(SymbolType.COMMA)) {
                    args.add(0, expression());
                    s = lex();
                }
                expected(s, lexer.yyline(), SymbolType.PARENT_CLOSE);
                ret = new FSCommand2ActionItem(null, null, args);
                break;
            case SET:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem name1 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem value1 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new SetVariableActionItem(null, null, name1, value1);
                ((SetVariableActionItem) ret).forceUseSet = true;
                hasEval.setVal(true); //FlashPro does this (using definelocal for funcs) only for eval func, but we will also use set since it is generated by obfuscated identifiers
                break;
            case TRACE:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new TraceActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;

            case GETURL:
                expectedType(SymbolType.PARENT_OPEN);
                s = lex();
                if (s.type == SymbolType.STRING) {
                    ParsedSymbol urlSymb = s;
                    s = lex();
                    if (s.type == SymbolType.COMMA) {
                        ParsedSymbol targetSymb = lex();
                        if (targetSymb.type == SymbolType.STRING) {
                            ParsedSymbol s2 = lex();
                            if (s2.type == SymbolType.PARENT_CLOSE) {
                                ret = new GetURLActionItem(null, null, urlSymb.value.toString(), targetSymb.value.toString());
                                break;
                            }
                            lexer.pushback(s2);
                        }
                        lexer.pushback(targetSymb);
                    } else if (s.type == SymbolType.PARENT_CLOSE) {
                        ret = new GetURLActionItem(null, null, urlSymb.value.toString(), "");
                        break;
                    }
                    lexer.pushback(s);
                    lexer.pushback(urlSymb);
                } else {
                    lexer.pushback(s);
                }
                GraphTargetItem url = expression();
                s = lex();
                expected(s, lexer.yyline(), SymbolType.PARENT_CLOSE, SymbolType.COMMA);
                int sendVarsMethod = 0;
                GraphTargetItem target;
                if (s.type == SymbolType.COMMA) {
                    target = expression();
                    s = lex();
                    if (s.type == SymbolType.COMMA) {
                        s = lex();
                        expected(s, lexer.yyline(), SymbolType.STRING);
                        if (s.value.equals("GET")) {
                            sendVarsMethod = 1;
                        } else if (s.value.equals("POST")) {
                            sendVarsMethod = 2;
                        } else {
                            throw new ActionParseException("Invalid method, \"GET\" or \"POST\" expected.", lexer.yyline());
                        }
                    } else {
                        lexer.pushback(s);
                    }
                } else {
                    lexer.pushback(s);
                    target = new DirectValueActionItem(null, null, 0, "", new ArrayList<>());
                }
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new GetURL2ActionItem(null, null, url, target, sendVarsMethod);
                break;
            case GOTOANDSTOP:
            case GOTOANDPLAY:
                SymbolType gtKind = s.type;
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem gtsFrame = expression();
                int gtsSceneBias = -1;
                s = lex();
                if (s.type == SymbolType.COMMA) { //Handle scene?
                    if ((gtsFrame instanceof DirectValueActionItem) && (((DirectValueActionItem) gtsFrame).value instanceof Long)) {
                        gtsSceneBias = (int) (long) (Long) ((DirectValueActionItem) gtsFrame).value;
                    } else {
                        throw new ActionParseException("Scene bias must be number", lexer.yyline());
                    }

                    gtsFrame = expression();
                } else {
                    lexer.pushback(s);
                }
                ret = new GotoFrame2ActionItem(null, null, gtsFrame, gtsSceneBias != -1, gtKind == SymbolType.GOTOANDPLAY, gtsSceneBias);
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case NEXTFRAME:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new NextFrameActionItem(null, null);
                break;
            case PLAY:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new PlayActionItem(null, null);
                break;
            case PREVFRAME:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new PrevFrameActionItem(null, null);
                break;
            case STOP:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new StopActionItem(null, null);
                break;
            case STOPALLSOUNDS:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new StopAllSoundsActionItem(null, null);
                break;
            case TOGGLEHIGHQUALITY:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new ToggleHighQualityActionItem(null, null);
                break;

            case STOPDRAG:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new StopDragActionItem(null, null);
                break;

            case UNLOADMOVIE:
            case UNLOADMOVIENUM:
                SymbolType unloadType = s.type;
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem unTargetOrNum = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                if (unloadType == SymbolType.UNLOADMOVIE) {
                    ret = new UnLoadMovieActionItem(null, null, unTargetOrNum);
                }
                if (unloadType == SymbolType.UNLOADMOVIENUM) {
                    ret = new UnLoadMovieNumActionItem(null, null, unTargetOrNum);
                }
                break;
            case PRINT:
            case PRINTASBITMAP:
            case PRINTASBITMAPNUM:
            case PRINTNUM:
                SymbolType printType = s.type;
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem printTarget = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem printBBox = expression();
                expectedType(SymbolType.PARENT_CLOSE);

                switch (printType) {
                    case PRINT:
                        ret = new PrintActionItem(null, null, printTarget, printBBox);
                        break;
                    case PRINTNUM:
                        ret = new PrintNumActionItem(null, null, printTarget, printBBox);
                        break;
                    case PRINTASBITMAP:
                        ret = new PrintAsBitmapActionItem(null, null, printTarget, printBBox);
                        break;
                    case PRINTASBITMAPNUM:
                        ret = new PrintAsBitmapNumActionItem(null, null, printTarget, printBBox);
                        break;
                }
                break;
            case LOADVARIABLES:
            case LOADMOVIE:
            case LOADVARIABLESNUM:
            case LOADMOVIENUM:
                SymbolType loadType = s.type;
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem url2 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem targetOrNum = expression();

                s = lex();
                expected(s, lexer.yyline(), SymbolType.PARENT_CLOSE, SymbolType.COMMA);
                int lvmethod = 0;
                if (s.type == SymbolType.COMMA) {
                    s = lex();
                    expected(s, lexer.yyline(), SymbolType.STRING);
                    if (s.value.equals("POST")) {
                        lvmethod = 2;
                    } else if (s.value.equals("GET")) {
                        lvmethod = 1;
                    } else {
                        throw new ActionParseException("Invalid method, \"GET\" or \"POST\" expected.", lexer.yyline());
                    }
                } else {
                    lexer.pushback(s);
                }
                expectedType(SymbolType.PARENT_CLOSE);
                switch (loadType) {
                    case LOADVARIABLES:
                        ret = new LoadVariablesActionItem(null, null, url2, targetOrNum, lvmethod);
                        break;
                    case LOADMOVIE:
                        ret = new LoadMovieActionItem(null, null, url2, targetOrNum, lvmethod);
                        break;
                    case LOADVARIABLESNUM:
                        ret = new LoadVariablesNumActionItem(null, null, url2, targetOrNum, lvmethod);
                        break;
                    case LOADMOVIENUM:
                        ret = new LoadMovieNumActionItem(null, null, url2, targetOrNum, lvmethod);
                        break;
                }
                break;
            case REMOVEMOVIECLIP:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new RemoveSpriteActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case STARTDRAG:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem dragTarget = expression();
                GraphTargetItem lockCenter;
                GraphTargetItem constrain;
                GraphTargetItem x1 = null;
                GraphTargetItem y1 = null;
                GraphTargetItem x2 = null;
                GraphTargetItem y2 = null;
                s = lex();
                if (s.type == SymbolType.COMMA) {
                    lockCenter = expression();
                    s = lex();
                    if (s.type == SymbolType.COMMA) {
                        constrain = new DirectValueActionItem(null, null, 0, 1L, new ArrayList<>());
                        x1 = expression();
                        s = lex();
                        if (s.type == SymbolType.COMMA) {
                            y1 = expression();
                            s = lex();
                            if (s.type == SymbolType.COMMA) {
                                x2 = expression();
                                s = lex();
                                if (s.type == SymbolType.COMMA) {
                                    y2 = expression();
                                } else {
                                    lexer.pushback(s);
                                    y2 = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                                }
                            } else {
                                lexer.pushback(s);
                                x2 = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                                y2 = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                            }
                        } else {
                            lexer.pushback(s);
                            x2 = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                            y2 = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                            y1 = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());

                        }
                    } else {
                        lexer.pushback(s);
                        constrain = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                        //ret.add(new ActionPush(Boolean.FALSE));
                    }
                } else {
                    lockCenter = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                    constrain = new DirectValueActionItem(null, null, 0, 0L, new ArrayList<>());
                    lexer.pushback(s);
                }
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new StartDragActionItem(null, null, dragTarget, lockCenter, constrain, x1, y1, x2, y2);
                break;
            case CALL:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new CallActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case GETVERSION:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new GetVersionActionItem(null, null);
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case MBORD:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new MBCharToAsciiActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case MBCHR:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new MBAsciiToCharActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case MBLENGTH:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new MBStringLengthActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case MBSUBSTRING:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem val1 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem index1 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem len1 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new MBStringExtractActionItem(null, null, val1, index1, len1);
                break;
            case SUBSTRING:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem val2 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem index2 = expression();
                expectedType(SymbolType.COMMA);
                GraphTargetItem len2 = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new StringExtractActionItem(null, null, val2, index2, len2);
                break;
            case LENGTH:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new StringLengthActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case RANDOM:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new RandomNumberActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case INT:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new ToIntegerActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case NUMBER_OP:
                ParsedSymbol sopn = s;
                s = lex();
                if (s.type == SymbolType.DOT) {
                    lexer.pushback(s);
                    VariableActionItem vi = new VariableActionItem(sopn.value.toString(), null, false);
                    variables.add(vi);
                    ret = vi; //memberOrCall(vi, inWith, inFunction, inMethod, variables, functions);
                } else {
                    expected(s, lexer.yyline(), SymbolType.PARENT_OPEN);
                    ret = new ToNumberActionItem(null, null, expression());
                    expectedType(SymbolType.PARENT_CLOSE);
                }
                break;
            case STRING_OP:
                ParsedSymbol sop = s;
                s = lex();
                if (s.type == SymbolType.DOT) {
                    lexer.pushback(s);
                    VariableActionItem vi2 = new VariableActionItem(sop.value.toString(), null, false);
                    variables.add(vi2);
                    ret = vi2; //memberOrCall(vi2, inWith, inFunction, inMethod, variables, functions);
                } else {
                    expected(s, lexer.yyline(), SymbolType.PARENT_OPEN);
                    ret = new ToStringActionItem(null, null, expression());
                    expectedType(SymbolType.PARENT_CLOSE);
                    //ret = memberOrCall(ret, inWith, inFunction, inMethod, variables, functions);
                }
                break;
            case ORD:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new CharToAsciiActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case CHR:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new AsciiToCharActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case GETTIMER:
                expectedType(SymbolType.PARENT_OPEN);
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new GetTimeActionItem(null, null);
                break;
            case TARGETPATH:
                expectedType(SymbolType.PARENT_OPEN);
                ret = new TargetPathActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            default:
                return null;
        }
        if (debugMode) {
            System.out.println("/expressionCommands");
        }
        return ret;
    }

    private boolean isIdentifier(ParsedSymbol s, Object... exceptions) {
        for (Object ex : exceptions) {
            if (s.isType(ex)) {
                return true;
            }
        }
        return s.isType(SymbolType.IDENTIFIER,
                SymbolType.TRUE, SymbolType.FALSE, SymbolGroup.GLOBALCONST,
                SymbolType.GET, SymbolType.SET,
                SymbolType.EACH, SymbolGroup.GLOBALFUNC,
                SymbolType.NUMBER_OP, SymbolType.STRING_OP);
    }

    private void expectedIdentifier(ParsedSymbol s, int line, Object... exceptions) throws IOException, ActionParseException {
        for (Object ex : exceptions) {
            if (s.isType(ex)) {
                return;
            }
        }
        if (!isIdentifier(s)) {
            throw new ActionParseException(SymbolType.IDENTIFIER + " expected but " + s.type + " found", line);
        }
    }

    private GraphTargetItem command(boolean inWith, boolean inFunction, boolean inMethod, int forinlevel, boolean inTellTarget, boolean mustBeCommand, List<VariableActionItem> variables, List<FunctionActionItem> functions, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        LexBufferer buf = new LexBufferer();
        lexer.addListener(buf);
        GraphTargetItem ret = null;
        if (debugMode) {
            System.out.println("command:");
        }
        ParsedSymbol s = lex();
        if (s.type == SymbolType.EOF) {
            return null;
        }
        if (s.group == SymbolGroup.GLOBALFUNC) {
            ParsedSymbol s2 = lex();
            if (s2.type != SymbolType.PARENT_OPEN) {
                lexer.removeListener(buf);
                buf.pushAllBack(lexer);

                ret = expression();
                s = lex();
                if ((s != null) && (s.type != SymbolType.SEMICOLON)) {
                    lexer.pushback(s);
                }
                return ret;
            } else {
                lexer.pushback(s2);
            }
        }

        switch (s.type) {
            case WITH:
                int withLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem wvar = expression(inWith, inFunction, inMethod, inTellTarget, false, variables, functions, false, hasEval);
                expectedType(SymbolType.PARENT_CLOSE);
                expectedType(SymbolType.CURLY_OPEN);
                List<GraphTargetItem> wcmd = commands(true, inFunction, inMethod, forinlevel, inTellTarget, variables, functions, hasEval);
                expectedType(SymbolType.CURLY_CLOSE);
                ret = new WithActionItem(null, null, wvar, wcmd);
                ret.line = withLine;
                break;
            case DELETE:
                GraphTargetItem varDel = expression(inWith, inFunction, inMethod, inTellTarget, false, variables, functions, false, hasEval);
                if (varDel instanceof GetMemberActionItem) {
                    GetMemberActionItem gm = (GetMemberActionItem) varDel;
                    ret = new DeleteActionItem(null, null, gm.object, gm.memberName);
                } else if (varDel instanceof VariableActionItem) {
                    variables.remove(varDel);
                    ret = new DeleteActionItem(null, null, null, pushConst(((VariableActionItem) varDel).getVariableName()));
                } else if ((varDel instanceof EvalActionItem) || (varDel instanceof ParenthesisItem)) {
                    ret = new DeleteActionItem(null, null, null, varDel.value);
                } else if (varDel instanceof DirectValueActionItem) {
                    ret = new DeleteActionItem(null, null, null, varDel);
                } else {
                    ret = new DeleteActionItem(null, null, null, varDel);
                }
                break;
            case TELLTARGET:
                int tellTargetLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem tellTarget = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                expectedType(SymbolType.CURLY_OPEN);
                List<GraphTargetItem> tellcmds = commands(inWith, inFunction, inMethod, forinlevel, true, variables, functions, hasEval);
                expectedType(SymbolType.CURLY_CLOSE);
                TellTargetActionItem tt = new TellTargetActionItem(null, null, tellTarget, tellcmds);
                if (inTellTarget) {
                    tt.nested = true;
                }
                ret = tt;
                ret.line = tellTargetLine;
                break;

            case IFFRAMELOADED:
                int ifFrameLoadedLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem iflExpr = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                expectedType(SymbolType.CURLY_OPEN);
                List<GraphTargetItem> iflComs = commands(inWith, inFunction, inMethod, forinlevel, inTellTarget, variables, functions, hasEval);
                expectedType(SymbolType.CURLY_CLOSE);
                ret = new IfFrameLoadedActionItem(iflExpr, iflComs, null, null);
                ret.line = ifFrameLoadedLine;
                break;
            case CLASS:
                int classLine = lexer.yyline();
                GraphTargetItem classTypeStr = type(variables);
                s = lex();
                GraphTargetItem extendsTypeStr = null;
                if (s.type == SymbolType.EXTENDS) {
                    extendsTypeStr = type(variables);
                    s = lex();
                }
                List<GraphTargetItem> implementsTypeStrs = new ArrayList<>();
                if (s.type == SymbolType.IMPLEMENTS) {
                    do {
                        GraphTargetItem implementsTypeStr = type(variables);
                        implementsTypeStrs.add(implementsTypeStr);
                        s = lex();
                    } while (s.type == SymbolType.COMMA);
                }
                expected(s, lexer.yyline(), SymbolType.CURLY_OPEN);
                ret = (traits(false, classTypeStr, extendsTypeStr, implementsTypeStrs, variables, functions, inTellTarget, hasEval));
                ret.line = classLine;
                expectedType(SymbolType.CURLY_CLOSE);
                break;
            case INTERFACE:
                GraphTargetItem interfaceTypeStr = type(variables);
                s = lex();
                List<GraphTargetItem> intExtendsTypeStrs = new ArrayList<>();

                if (s.type == SymbolType.EXTENDS) {
                    do {
                        GraphTargetItem intExtendsTypeStr = type(variables);
                        intExtendsTypeStrs.add(intExtendsTypeStr);
                        s = lex();
                    } while (s.type == SymbolType.COMMA);
                }
                expected(s, lexer.yyline(), SymbolType.CURLY_OPEN);
                ret = (traits(true, interfaceTypeStr, null, intExtendsTypeStrs, variables, functions, inTellTarget, hasEval));
                expectedType(SymbolType.CURLY_CLOSE);
                break;
            case FUNCTION:
                s = lex();
                expectedIdentifier(s, lexer.yyline());
                ret = (function(true, s.value.toString(), false, variables, functions, inTellTarget, hasEval));
                break;
            case VAR:
                s = lex();
                expectedIdentifier(s, lexer.yyline());
                String varIdentifier = s.value.toString();
                s = lex();
                if (s.type == SymbolType.COLON) {
                    type(variables);
                    s = lex();
                    //TODO: handle value type
                }

                if (s.type == SymbolType.ASSIGN) {
                    GraphTargetItem varval = expression();
                    ret = new VariableActionItem(varIdentifier, varval, true);
                    variables.add((VariableActionItem) ret);
                } else {
                    ret = new VariableActionItem(varIdentifier, new DirectValueActionItem(Undefined.INSTANCE), true);
                    variables.add((VariableActionItem) ret);
                    lexer.pushback(s);
                }
                break;
            case CURLY_OPEN:
                ret = new BlockItem(DIALECT, null, null, commands(inWith, inFunction, inMethod, forinlevel, inTellTarget, variables, functions, hasEval));
                expectedType(SymbolType.CURLY_CLOSE);
                break;
            case INCREMENT: //preincrement
            case DECREMENT: //predecrement
                GraphTargetItem varincdec = expression(inWith, inFunction, inMethod, inTellTarget, false, variables, functions, false, hasEval);
                if (s.type == SymbolType.INCREMENT) {
                    ret = new PreIncrementActionItem(null, null, varincdec);
                } else if (s.type == SymbolType.DECREMENT) {
                    ret = new PreDecrementActionItem(null, null, varincdec);
                }
                break;
            case SUPER: //constructor call
                ParsedSymbol ss2 = lex();
                if (ss2.type == SymbolType.PARENT_OPEN) {
                    List<GraphTargetItem> args = call(inWith, inFunction, inMethod, inTellTarget, variables, functions, hasEval);
                    VariableActionItem supItem = new VariableActionItem(s.value.toString(), null, false);
                    variables.add(supItem);
                    ret = new CallMethodActionItem(null, null, supItem, new DirectValueActionItem(null, null, 0, Undefined.INSTANCE, constantPool), args);
                } else { //no constructor call, but it could be calling parent methods... => handle in expression
                    lexer.pushback(ss2);
                    lexer.pushback(s);
                }
                break;
            case IF:
                int ifLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem ifExpr = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                GraphTargetItem onTrue = command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval);
                List<GraphTargetItem> onTrueList = new ArrayList<>();
                onTrueList.add(onTrue);
                s = lex();
                List<GraphTargetItem> onFalseList = null;
                if (s.type == SymbolType.ELSE) {
                    onFalseList = new ArrayList<>();
                    onFalseList.add(command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval));
                } else {
                    lexer.pushback(s);
                }
                ret = new IfItem(DIALECT, null, null, ifExpr, onTrueList, onFalseList);
                ret.line = ifLine;
                break;
            case WHILE:
                int whileLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                List<GraphTargetItem> whileExpr = new ArrayList<>();
                whileExpr.add(expression(inWith, inFunction, inMethod, inTellTarget, true, variables, functions, true, hasEval));
                expectedType(SymbolType.PARENT_CLOSE);
                List<GraphTargetItem> whileBody = new ArrayList<>();
                whileBody.add(command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval));
                ret = new WhileItem(DIALECT, null, null, null, whileExpr, whileBody);
                ret.line = whileLine;
                break;
            case DO:
                int doLine = lexer.yyline();
                List<GraphTargetItem> doBody = new ArrayList<>();
                doBody.add(command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval));
                expectedType(SymbolType.WHILE);
                expectedType(SymbolType.PARENT_OPEN);
                List<GraphTargetItem> doExpr = new ArrayList<>();
                doExpr.add(expression(inWith, inFunction, inMethod, inTellTarget, true, variables, functions, true, hasEval));
                expectedType(SymbolType.PARENT_CLOSE);
                ret = new DoWhileItem(DIALECT, null, null, null, doBody, doExpr);
                ret.line = doLine;
                break;
            case FOR:
                int forLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                s = lex();
                boolean forin = false;
                GraphTargetItem collection = null;
                String objIdent;
                VariableActionItem item = null;
                int innerExprReg = 0;
                boolean define = false;
                if (s.type == SymbolType.VAR || isIdentifier(s)) {
                    ParsedSymbol s2 = null;
                    ParsedSymbol ssel = s;
                    if (s.type == SymbolType.VAR) {
                        s2 = lex();
                        ssel = s2;
                        define = true;
                    }

                    if (isIdentifier(ssel)) {
                        objIdent = ssel.value.toString();

                        ParsedSymbol s3 = lex();
                        if (s3.type == SymbolType.IN) {                            
                            item = new VariableActionItem(objIdent, null, define);

                            item.setStoreValue(new GraphTargetItem(DIALECT) {

                                @Override
                                public GraphTextWriter appendTo(GraphTextWriter writer, LocalData localData) throws InterruptedException {
                                    return writer;
                                }

                                @Override
                                public boolean hasReturnValue() {
                                    return false;
                                }

                                @Override
                                public GraphTargetItem returnType() {
                                    return TypeItem.UNBOUNDED;
                                }

                                //toSource is Empty
                            });

                            variables.add(item);

                            collection = expression();
                            forin = true;
                        } else {
                            lexer.pushback(s3);
                            if (s2 != null) {
                                lexer.pushback(s2);
                            }
                            lexer.pushback(s);
                        }
                    } else {
                        if (s2 != null) {
                            lexer.pushback(s2);
                        }
                        lexer.pushback(s);
                    }
                } else {
                    lexer.pushback(s);
                }
                List<GraphTargetItem> forFinalCommands = new ArrayList<>();
                GraphTargetItem forExpr = null;
                List<GraphTargetItem> forFirstCommands = new ArrayList<>();
                if (!forin) {
                    GraphTargetItem fc = command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval);
                    if (fc != null) { //can be empty command
                        forFirstCommands.add(fc);
                    }
                    forExpr = expression();
                    if (forExpr == null) {
                        forExpr = new TrueItem(DIALECT, null, null);
                    }
                    expectedType(SymbolType.SEMICOLON);
                    GraphTargetItem fcom = command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval);
                    if (fcom != null) {
                        forFinalCommands.add(fcom);
                    }
                }
                expectedType(SymbolType.PARENT_CLOSE);
                List<GraphTargetItem> forBody = new ArrayList<>();
                forBody.add(command(inWith, inFunction, inMethod, forin ? forinlevel + 1 : forinlevel, inTellTarget, true, variables, functions, hasEval));
                if (forin) {
                    ret = new ForInActionItem(null, null, null, item, collection, forBody);
                } else {
                    ret = new ForItem(DIALECT, null, null, null, forFirstCommands, forExpr, forFinalCommands, forBody);
                }
                ret.line = forLine;
                break;
            case SWITCH:
                int switchLine = lexer.yyline();
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem switchExpr = expression();
                expectedType(SymbolType.PARENT_CLOSE);
                expectedType(SymbolType.CURLY_OPEN);
                s = lex();
                //ret.addAll(switchExpr);
               
                List<List<ActionIf>> caseIfs = new ArrayList<>();
                List<List<GraphTargetItem>> caseCmds = new ArrayList<>();
                List<GraphTargetItem> caseExprsAll = new ArrayList<>();
                List<Integer> valueMapping = new ArrayList<>();
                int pos = 0;
                while (s.type == SymbolType.CASE || s.type == SymbolType.DEFAULT) {
                    //List<GraphTargetItem> caseExprs; = new ArrayList<>();
                    while (s.type == SymbolType.CASE || s.type == SymbolType.DEFAULT) {
                        GraphTargetItem curCaseExpr = s.type == SymbolType.DEFAULT ? new DefaultItem(DIALECT) : expression();
                        //caseExprs.add(curCaseExpr);
                        expectedType(SymbolType.COLON);
                        s = lex();
                        caseExprsAll.add(curCaseExpr);
                        valueMapping.add(pos);
                    }
                    pos++;
                    lexer.pushback(s);
                    List<GraphTargetItem> caseCmd = commands(inWith, inFunction, inMethod, forinlevel, inTellTarget, variables, functions, hasEval);
                    caseCmds.add(caseCmd);
                    s = lex();
                }
                expected(s, lexer.yyline(), SymbolType.CURLY_CLOSE);
                ret = new SwitchItem(DIALECT, null, null, null, switchExpr, caseExprsAll, caseCmds, valueMapping);
                ret.line = switchLine;
                break;
            case BREAK:
                ret = new BreakItem(DIALECT, null, null, 0); //? There is no more than 1 level continue/break in AS1/2
                break;
            case CONTINUE:
                ret = new ContinueItem(DIALECT, null, null, 0); //? There is no more than 1 level continue/break in AS1/2
                break;
            case RETURN:
                GraphTargetItem retexpr = expression();
                if (retexpr == null) {
                    retexpr = new DirectValueActionItem(null, null, 0, Undefined.INSTANCE, new ArrayList<>());
                }
                ret = new ReturnActionItem(null, null, retexpr);
                break;
            case TRY:
                int tryLine = lexer.yyline();
                List<GraphTargetItem> tryCommands = new ArrayList<>();
                tryCommands.add(command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval));
                s = lex();
                boolean found = false;
                List<List<GraphTargetItem>> catchCommands = new ArrayList<>();
                List<GraphTargetItem> catchExceptionNames = new ArrayList<>();
                List<GraphTargetItem> catchExceptionTypes = new ArrayList<>();

                while (s.type == SymbolType.CATCH) {
                    expectedType(SymbolType.PARENT_OPEN);
                    s = lex();
                    expectedIdentifier(s, lexer.yyline(), SymbolType.STRING);
                    catchExceptionNames.add(pushConst((String) s.value));
                    s = lex();
                    if (s.type == SymbolType.COLON) {
                        catchExceptionTypes.add(type(variables));
                    } else {
                        catchExceptionTypes.add(null);
                        lexer.pushback(s);
                    }
                    expectedType(SymbolType.PARENT_CLOSE);
                    List<GraphTargetItem> cc = new ArrayList<>();
                    cc.add(command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval));
                    catchCommands.add(cc);
                    s = lex();
                    found = true;
                }
                List<GraphTargetItem> finallyCommands = null;
                if (s.type == SymbolType.FINALLY) {
                    finallyCommands = new ArrayList<>();
                    finallyCommands.add(command(inWith, inFunction, inMethod, forinlevel, inTellTarget, true, variables, functions, hasEval));
                    found = true;
                    s = lex();
                }
                if (!found) {
                    expected(s, lexer.yyline(), SymbolType.CATCH, SymbolType.FINALLY);
                }
                lexer.pushback(s);
                ret = new TryActionItem(tryCommands, catchExceptionNames, catchExceptionTypes, catchCommands, finallyCommands);
                ret.line = tryLine;
                break;
            case THROW:
                ret = new ThrowActionItem(null, null, expression());
                break;
            case SEMICOLON: //empty command
                if (debugMode) {
                    System.out.println("/command");
                }
                return new EmptyCommand(DIALECT);
            case DIRECTIVE:
                switch ((String) s.value) {
                    case "strict":
                        ret = new StrictModeActionItem(null, null, 1);
                        break;
                    default:
                        throw new ActionParseException("Unknown directive: #" + s.value, lexer.yyline());
                }
                break;
            default:
                lexer.pushback(s);
                ret = expression(inWith, inFunction, inMethod, inTellTarget, true, variables, functions, true, hasEval);
        }
        if (debugMode) {
            System.out.println("/command");
        }
        lexer.removeListener(buf);
        if (ret == null) {  //can be popped expression
            buf.pushAllBack(lexer);
            ret = expression();
        }
        s = lex();
        if ((s != null) && (s.type != SymbolType.SEMICOLON)) {
            lexer.pushback(s);
        }

        return ret;

    }

    private GraphTargetItem expression(boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, boolean allowRemainder, List<VariableActionItem> variables, List<FunctionActionItem> functions, boolean allowComma, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        if (debugMode) {
            System.out.println("expression:");
        }
        List<GraphTargetItem> commaItems = new ArrayList<>();
        ParsedSymbol symb;
        do {
            GraphTargetItem prim = expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, true, hasEval);
            if (prim == null) {
                return null;
            }
            GraphTargetItem expr = expression1(prim, GraphTargetItem.NOPRECEDENCE, inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, hasEval);
            commaItems.add(expr);
            symb = lex();
        } while (allowComma && symb != null && symb.type == SymbolType.COMMA);
        if (symb != null) {
            lexer.pushback(symb);
        }
        if (debugMode) {
            System.out.println("/expression");
        }
        if (commaItems.size() == 1) {
            return commaItems.get(0);
        }
        return new CommaExpressionItem(DIALECT, null, null, commaItems);
    }

    private ParsedSymbol peekLex() throws IOException, ActionParseException, InterruptedException {
        ParsedSymbol lookahead = lex();
        lexer.pushback(lookahead);
        return lookahead;
    }

    private static final String[] operatorIdentifiers = new String[]{"add", "eq", "ne", "lt", "ge", "gt", "le"};

    private boolean isBinaryOperator(ParsedSymbol s) {
        if (s.type == SymbolType.IDENTIFIER && Arrays.asList(operatorIdentifiers).contains(s.value.toString())) {
            return true;
        }
        return s.type.isBinary();
    }

    private int getSymbPrecedence(ParsedSymbol s) {
        if (s.type == SymbolType.IDENTIFIER && Arrays.asList(operatorIdentifiers).contains(s.value.toString())) {
            switch (s.value.toString()) {
                case "add":
                    return Precedence.PRECEDENCE_ADDITIVE;
                case "eq":
                case "ne":
                    return Precedence.PRECEDENCE_EQUALITY;
                case "lt":
                case "ge":
                case "gt":
                case "le":
                    return Precedence.PRECEDENCE_RELATIONAL;
            }
        }
        return s.type.getPrecedence();
    }

    private GraphTargetItem expression1(GraphTargetItem lhs, int min_precedence, boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, boolean allowRemainder, List<VariableActionItem> variables, List<FunctionActionItem> functions, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        ParsedSymbol op;
        GraphTargetItem rhs;
        GraphTargetItem mhs = null;
        ParsedSymbol lookahead = peekLex();
        if (debugMode) {
            System.out.println("expression1:");
        }
        //Note: algorithm from http://en.wikipedia.org/wiki/Operator-precedence_parser
        //with relation operators reversed as we have precedence in reverse order
        while (isBinaryOperator(lookahead) && getSymbPrecedence(lookahead) <= min_precedence) {
            op = lookahead;
            lex();

            //Note: Handle ternar operator as Binary
            //http://stackoverflow.com/questions/13681293/how-can-i-incorporate-ternary-operators-into-a-precedence-climbing-algorithm
            if (op.type == SymbolType.TERNAR) {
                if (debugMode) {
                    System.out.println("ternar-middle:");
                }
                mhs = expression(inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, false, hasEval);
                expectedType(SymbolType.COLON);
                if (debugMode) {
                    System.out.println("/ternar-middle");
                }
            }

            rhs = expressionPrimary(allowRemainder, inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, true, hasEval);
            if (rhs == null) {
                throw new ActionParseException("Missing operand", lexer.yyline());
                //lexer.pushback(op);
                //break;
            }

            lookahead = peekLex();
            while ((isBinaryOperator(lookahead) && getSymbPrecedence(lookahead) < getSymbPrecedence(op))
                    || (lookahead.type.isRightAssociative() && getSymbPrecedence(lookahead) == getSymbPrecedence(op))) {
                rhs = expression1(rhs, getSymbPrecedence(lookahead), inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, hasEval);
                lookahead = peekLex();
            }

            switch (op.type) {

                case TERNAR:
                    lhs = new TernarOpItem(DIALECT, null, null, lhs, mhs, rhs);
                    lhs.line = lexer.yyline();
                    break;
                case SHIFT_LEFT:
                    lhs = new LShiftActionItem(null, null, lhs, rhs);
                    break;
                case SHIFT_RIGHT:
                    lhs = new RShiftActionItem(null, null, lhs, rhs);
                    break;
                case USHIFT_RIGHT:
                    lhs = new URShiftActionItem(null, null, lhs, rhs);
                    break;
                case BITAND:
                    lhs = new BitAndActionItem(null, null, lhs, rhs);
                    break;
                case BITOR:
                    lhs = new BitOrActionItem(null, null, lhs, rhs);
                    break;
                case DIVIDE:
                    lhs = new DivideActionItem(null, null, lhs, rhs);
                    break;
                case MODULO:
                    lhs = new ModuloActionItem(null, null, lhs, rhs);
                    break;
                case EQUALS:
                    lhs = new EqActionItem(null, null, lhs, rhs, true); //FIXME SWF version?
                    break;
                case STRICT_EQUALS:
                    lhs = new StrictEqActionItem(null, null, lhs, rhs);
                    break;
                case NOT_EQUAL:
                    lhs = new NeqActionItem(null, null, lhs, rhs, true); //FIXME SWF version
                    break;
                case STRICT_NOT_EQUAL:
                    lhs = new StrictNeqActionItem(null, null, lhs, rhs);
                    break;
                case LOWER_THAN:
                    lhs = new LtActionItem(null, null, lhs, rhs, true); //FIXME SWF version
                    break;
                case LOWER_EQUAL:
                    lhs = new LeActionItem(null, null, lhs, rhs);
                    break;
                case GREATER_THAN:
                    lhs = new GtActionItem(null, null, lhs, rhs);
                    break;
                case GREATER_EQUAL:
                    lhs = new GeActionItem(null, null, lhs, rhs, true); //FIXME SWF version
                    break;
                case AND:
                    lhs = new AndItem(DIALECT, null, null, lhs, rhs);
                    break;
                case OR:
                    lhs = new OrItem(DIALECT, null, null, lhs, rhs);
                    break;
                case FULLAND:
                    lhs = new AndActionItem(null, null, lhs, rhs);
                    break;
                case FULLOR:
                    lhs = new OrActionItem(null, null, lhs, rhs);
                    break;
                case MINUS:
                    lhs = new SubtractActionItem(null, null, lhs, rhs);
                    break;
                case MULTIPLY:
                    lhs = new MultiplyActionItem(null, null, lhs, rhs);
                    break;
                case PLUS:
                    lhs = new AddActionItem(null, null, lhs, rhs, swfVersion >= 5);
                    break;
                case XOR:
                    lhs = new BitXorActionItem(null, null, lhs, rhs);
                    break;
                case INSTANCEOF:
                    lhs = new InstanceOfActionItem(null, null, lhs, rhs);
                    break;
                case ASSIGN:
                case ASSIGN_BITAND:
                case ASSIGN_BITOR:
                case ASSIGN_DIVIDE:
                case ASSIGN_MINUS:
                case ASSIGN_MODULO:
                case ASSIGN_MULTIPLY:
                case ASSIGN_PLUS:
                case ASSIGN_SHIFT_LEFT:
                case ASSIGN_SHIFT_RIGHT:
                case ASSIGN_USHIFT_RIGHT:
                case ASSIGN_XOR:
                    GraphTargetItem assigned = rhs;
                    switch (op.type) {
                        case ASSIGN:
                            //assigned = assigned;
                            break;
                        case ASSIGN_BITAND:
                            assigned = new BitAndActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_BITOR:
                            assigned = new BitOrActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_DIVIDE:
                            assigned = new DivideActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_MINUS:
                            assigned = new SubtractActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_MODULO:
                            assigned = new ModuloActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_MULTIPLY:
                            assigned = new MultiplyActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_PLUS:
                            assigned = new AddActionItem(null, null, lhs, assigned, swfVersion >= 5);
                            break;
                        case ASSIGN_SHIFT_LEFT:
                            assigned = new LShiftActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_SHIFT_RIGHT:
                            assigned = new RShiftActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_USHIFT_RIGHT:
                            assigned = new URShiftActionItem(null, null, lhs, assigned);
                            break;
                        case ASSIGN_XOR:
                            assigned = new BitXorActionItem(null, null, lhs, assigned);
                            break;
                    }
                    if (lhs instanceof GetPropertyActionItem) {
                        lhs = new SetPropertyActionItem(null, null, ((GetPropertyActionItem) lhs).target, ((GetPropertyActionItem) lhs).propertyIndex, assigned);
                    } else if (lhs instanceof VariableActionItem) {
                        if (assigned != rhs) {
                            lhs = new VariableActionItem(((VariableActionItem) lhs).getVariableName(), assigned, false);
                            variables.add((VariableActionItem) lhs);
                        } else {
                            ((VariableActionItem) lhs).setStoreValue(assigned);
                            ((VariableActionItem) lhs).setDefinition(false);
                        }
                    } else if (lhs instanceof GetMemberActionItem) {
                        lhs = new SetMemberActionItem(null, null, ((GetMemberActionItem) lhs).object, ((GetMemberActionItem) lhs).memberName, assigned);
                    } else {
                        throw new ActionParseException("Invalid assignment", lexer.yyline());
                    }
                    break;
                case IDENTIFIER:
                    switch (op.value.toString()) {
                        case "add":
                            lhs = new StringAddActionItem(null, null, lhs, rhs);
                            break;
                        case "eq":
                            lhs = new StringEqActionItem(null, null, lhs, rhs);
                            break;
                        case "ne":
                            lhs = new StringNeActionItem(null, null, lhs, rhs);
                            break;
                        case "lt":
                            lhs = new StringLtActionItem(null, null, lhs, rhs);
                            break;
                        case "ge":
                            lhs = new StringGeActionItem(null, null, lhs, rhs);
                            break;
                        case "gt":
                            lhs = new StringGtActionItem(null, null, lhs, rhs);
                            break;
                        case "le":
                            lhs = new StringLeActionItem(null, null, lhs, rhs);
                            break;
                    }
                    break;
            }
        }

        if (debugMode) {
            System.out.println("/expression1");
        }
        return lhs;
    }

    private boolean isType(GraphTargetItem item) {
        if (item == null) {
            return false;
        }
        while (item instanceof GetMemberActionItem) {
            item = ((GetMemberActionItem) item).object;
        }
        return (item instanceof VariableActionItem);
    }

    private int brackets(List<GraphTargetItem> ret, boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, List<VariableActionItem> variables, List<FunctionActionItem> functions, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        ParsedSymbol s = lex();
        int arrCnt = 0;
        if (s.type == SymbolType.BRACKET_OPEN) {
            s = lex();

            while (s.type != SymbolType.BRACKET_CLOSE) {
                if (s.type != SymbolType.COMMA) {
                    lexer.pushback(s);
                }
                arrCnt++;
                ret.addexpression();
                s = lex();
                if (!s.isType(SymbolType.COMMA, SymbolType.BRACKET_CLOSE)) {
                    expected(s, lexer.yyline(), SymbolType.COMMA, SymbolType.BRACKET_CLOSE);
                }
            }
        } else {
            lexer.pushback(s);
            return -1;
        }
        return arrCnt;
    }

    private GraphTargetItem handleVariable(ParsedSymbol s, GraphTargetItem ret, List<VariableActionItem> variables, Reference<Boolean> allowMemberOrCall, boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, List<FunctionActionItem> functions, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        if (s.value.equals("not")) {
            ret = new NotItem(DIALECT, null, null, expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, false, variables, functions, true, hasEval));
        } else {
            String varName = s.value.toString();           

            ret = new VariableActionItem(varName, null, false);
            variables.add((VariableActionItem) ret);
            allowMemberOrCall.setVal(true);
        }
        return ret;
    }

    private GraphTargetItem expressionPrimary(boolean allowEmpty, boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, boolean allowRemainder, List<VariableActionItem> variables, List<FunctionActionItem> functions, boolean allowCall, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        if (debugMode) {
            System.out.println("primary:");
        }
        boolean allowMemberOrCall = false;
        GraphTargetItem ret = null;
        ParsedSymbol s = lex();

        switch (s.type) {
            case PREPROCESSOR:
                expectedType(SymbolType.PARENT_OPEN);
                switch ("" + s.value) {
                    //AS 1/2:
                    //AS2:
                    case "constant":
                        s = lex();
                        expected(s, lexer.yyline(), SymbolType.INTEGER);
                        ret = new UnresolvedConstantActionItem((int) (long) (Long) s.value);
                        break;
                    case "enumerate":
                        ret = new EnumerateActionItem(null, null, expression(inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, false, hasEval));
                        break;
                    //Both ASs
                    case "dup":
                        ret = new DuplicateItem(DIALECT, null, null, expression(inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, false, hasEval), 0);
                        allowMemberOrCall = true;
                        break;
                    case "push":
                        ret = new PushItem(expression(inWith, inFunction, inMethod, inTellTarget, allowRemainder, variables, functions, false, hasEval));
                        break;
                    case "pop":
                        ret = new PopItem(DIALECT, null, null);
                        allowMemberOrCall = true;
                        break;
                    case "swap":
                        ret = new SwapItem(DIALECT, null, null);
                        break;
                    case "strict":
                        s = lex();
                        expected(s, lexer.yyline(), SymbolType.INTEGER);
                        ret = new StrictModeActionItem(null, null, (int) (long) (Long) s.value);
                        break;
                    case "goto": //TODO
                        throw new ActionParseException("Compiling §§" + s.value + " is not available, sorry", lexer.yyline());
                    default:
                        throw new ActionParseException("Unknown preprocessor instruction: §§" + s.value, lexer.yyline());

                }
                expectedType(SymbolType.PARENT_CLOSE);
                break;
            case NEGATE:
                versionRequired(s, 5);
                ret = expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, false, variables, functions, true, hasEval);
                ret = new BitXorActionItem(null, null, ret, new DirectValueActionItem(4.294967295E9));

                break;
            case MINUS:
                s = lex();
                if (s.isType(SymbolType.DOUBLE)) {
                    ret = new DirectValueActionItem(null, null, 0, -(double) (Double) s.value, new ArrayList<>());

                } else if (s.isType(SymbolType.INTEGER)) {
                    ret = new DirectValueActionItem(null, null, 0, -(long) (Long) s.value, new ArrayList<>());

                } else {
                    lexer.pushback(s);
                    GraphTargetItem num = expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, true, variables, functions, true, hasEval);
                    if ((num instanceof DirectValueActionItem)
                            && (((DirectValueActionItem) num).value instanceof Long)) {
                        ((DirectValueActionItem) num).value = -(Long) ((DirectValueActionItem) num).value;
                        ret = num;
                    } else if ((num instanceof DirectValueActionItem)
                            && (((DirectValueActionItem) num).value instanceof Double)) {
                        Double d = (Double) ((DirectValueActionItem) num).value;
                        if (d.isInfinite()) {
                            ((DirectValueActionItem) num).value = Double.NEGATIVE_INFINITY;
                        } else {
                            ((DirectValueActionItem) num).value = -d;
                        }
                        ret = (num);
                    } else if ((num instanceof DirectValueActionItem)
                            && (((DirectValueActionItem) num).value instanceof Float)) {
                        ((DirectValueActionItem) num).value = -(Float) ((DirectValueActionItem) num).value;
                        ret = (num);
                    } else {
                        ret = (new SubtractActionItem(null, null, new DirectValueActionItem(null, null, 0, (Long) 0L, new ArrayList<>()), num));
                    }
                }
                break;
            case TYPEOF:
                ret = new TypeOfActionItem(null, null, expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, false, variables, functions, true, hasEval));
                allowMemberOrCall = true;
                break;
            case TRUE:
                ret = new DirectValueActionItem(null, null, 0, Boolean.TRUE, new ArrayList<>());
                allowMemberOrCall = true;
                break;
            case NULL:
                ret = new DirectValueActionItem(null, null, 0, Null.INSTANCE, new ArrayList<>());
                allowMemberOrCall = true;
                break;
            case UNDEFINED:
                ret = new DirectValueActionItem(null, null, 0, Undefined.INSTANCE, new ArrayList<>());
                allowMemberOrCall = true;
                break;
            case FALSE:
                ret = new DirectValueActionItem(null, null, 0, Boolean.FALSE, new ArrayList<>());
                allowMemberOrCall = true;
                break;
            case CURLY_OPEN: //Object literal
                s = lex();
                List<GraphTargetItem> objectNames = new ArrayList<>();
                List<GraphTargetItem> objectValues = new ArrayList<>();
                while (s.type != SymbolType.CURLY_CLOSE) {
                    if (s.type != SymbolType.COMMA) {
                        lexer.pushback(s);
                    }
                    s = lex();
                    expectedIdentifier(s, lexer.yyline());
                    objectNames.add(0, pushConst((String) s.value));
                    expectedType(SymbolType.COLON);
                    objectValues.add(0, expression());
                    s = lex();
                    if (!s.isType(SymbolType.COMMA, SymbolType.CURLY_CLOSE)) {
                        expected(s, lexer.yyline(), SymbolType.COMMA, SymbolType.CURLY_CLOSE);
                    }
                }
                ret = new InitObjectActionItem(null, null, objectNames, objectValues);
                allowMemberOrCall = true;
                break;
            case BRACKET_OPEN: //Array literal or just brackets
                lexer.pushback(s);
                List<GraphTargetItem> inBrackets = new ArrayList<>();
                int arrCnt = brackets(inBrackets, inWith, inFunction, inMethod, inTellTarget, variables, functions, hasEval);
                ret = new InitArrayActionItem(null, null, inBrackets);
                allowMemberOrCall = true;
                break;
            case FUNCTION:
                s = lex();
                String fname = "";
                if (isIdentifier(s)) {
                    fname = s.value.toString();
                } else {
                    lexer.pushback(s);
                }
                ret = function(true, fname, false, variables, functions, inTellTarget, hasEval);
                allowMemberOrCall = true;
                break;
            case STRING:
                ret = pushConst(s.value.toString());
                allowMemberOrCall = true;
                break;
            case NEWLINE:
                ret = new DirectValueActionItem(null, null, 0, "\n", new ArrayList<>());
                allowMemberOrCall = true;
                break;
            case INTEGER:
            case DOUBLE:
                ret = new DirectValueActionItem(null, null, 0, s.value, new ArrayList<>());
                allowMemberOrCall = true;
                break;
            case DELETE:
                GraphTargetItem varDel = expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, false, variables, functions, true, hasEval);
                if (varDel instanceof GetMemberActionItem) {
                    GetMemberActionItem gm = (GetMemberActionItem) varDel;
                    ret = new DeleteActionItem(null, null, gm.object, gm.memberName);
                } else {
                    if (varDel instanceof VariableActionItem) {
                        varDel = pushConst(((VariableActionItem) varDel).getVariableName());
                    }
                    ret = new DeleteActionItem(null, null, null, varDel);
                }
                break;
            case INCREMENT:
            case DECREMENT: //preincrement
                GraphTargetItem prevar = expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, false, variables, functions, true, hasEval);
                if (s.type == SymbolType.INCREMENT) {
                    ret = new PreIncrementActionItem(null, null, prevar);
                }
                if (s.type == SymbolType.DECREMENT) {
                    ret = new PreDecrementActionItem(null, null, prevar);
                }

                break;
            case NOT:
                ret = new NotItem(DIALECT, null, null, expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, false, variables, functions, true, hasEval));

                break;
            case PARENT_OPEN:
                GraphTargetItem pexpr = expression(inWith, inFunction, inMethod, inTellTarget, true, variables, functions, true, hasEval);
                if (pexpr == null) {
                    throw new ActionParseException("Expression expected", lexer.yyline());
                }
                ret = new ParenthesisItem(DIALECT, null, null, pexpr);
                expectedType(SymbolType.PARENT_CLOSE);
                allowMemberOrCall = true;
                break;
            case NEW:
                GraphTargetItem newvar = expressionPrimary(false, inWith, inFunction, inMethod, inTellTarget, false, variables, functions, false, hasEval);
                if (newvar instanceof ToNumberActionItem) {
                    List<GraphTargetItem> args = new ArrayList<>();
                    if (((ToNumberActionItem) newvar).value != null) {
                        args.add(((ToNumberActionItem) newvar).value);
                    }
                    ret = new NewObjectActionItem(null, null, pushConst("Number"), args);
                } else if (newvar instanceof ToStringActionItem) {
                    List<GraphTargetItem> args = new ArrayList<>();
                    if (((ToStringActionItem) newvar).value != null) {
                        args.add(((ToStringActionItem) newvar).value);
                    }
                    ret = new NewObjectActionItem(null, null, pushConst("String"), args);
                } else if (newvar instanceof GetMemberActionItem) {

                    GetMemberActionItem ca = (GetMemberActionItem) newvar;
                    expectedType(SymbolType.PARENT_OPEN);
                    List<GraphTargetItem> args = call(inWith, inFunction, inMethod, inTellTarget, variables, functions, hasEval);
                    ret = new NewMethodActionItem(null, null, ca.object, ca.memberName, args);
                } else if (newvar instanceof VariableActionItem) {
                    VariableActionItem cf = (VariableActionItem) newvar;
                    expectedType(SymbolType.PARENT_OPEN);
                    List<GraphTargetItem> args = call(inWith, inFunction, inMethod, inTellTarget, variables, functions, hasEval);
                    ret = new NewObjectActionItem(null, null, pushConst(cf.getVariableName()), args);
                } else {
                    throw new ActionParseException("Invalid new item", lexer.yyline());
                }
                allowMemberOrCall = true;

                break;
            case EVAL:
                expectedType(SymbolType.PARENT_OPEN);
                GraphTargetItem evar = new EvalActionItem(null, null, expression());
                expectedType(SymbolType.PARENT_CLOSE);
                hasEval.setVal(true);
                //evar = memberOrCall(evar, inWith, inFunction, inMethod, variables, functions);
                ret = evar;
                allowMemberOrCall = true;

                break;
            case IDENTIFIER:
            case THIS:
            case SUPER:
                Reference<Boolean> allowMemberOrCallRef = new Reference<>(allowMemberOrCall);
                ret = handleVariable(s, ret, variables, allowMemberOrCallRef, inWith, inFunction, inMethod, inTellTarget, functions, hasEval);
                allowMemberOrCall = allowMemberOrCallRef.getVal();

                break;
            default:

                boolean isGlobalFuncVar = false;
                if (s.group == SymbolGroup.GLOBALFUNC) {
                    ParsedSymbol s2 = peekLex();
                    if (s2.type != SymbolType.PARENT_OPEN) {
                        Reference<Boolean> allowMemberOrCallRef2 = new Reference<>(allowMemberOrCall);
                        ret = handleVariable(s, ret, variables, allowMemberOrCallRef2, inWith, inFunction, inMethod, inTellTarget, functions, hasEval);
                        allowMemberOrCall = allowMemberOrCallRef2.getVal();
                        isGlobalFuncVar = true;
                    }
                }

                if (!isGlobalFuncVar) {
                    GraphTargetItem excmd = expressionCommands(s, inWith, inFunction, inMethod, inTellTarget, -1, variables, functions, hasEval);
                    if (excmd != null) {
                        //?
                        ret = excmd;
                        allowMemberOrCall = true; //?
                        break;
                    }
                    lexer.pushback(s);
                }
        }

        if (allowMemberOrCall && ret != null) {
            ret = memberOrCall(ret, inWith, inFunction, inMethod, inTellTarget, variables, functions, allowCall, hasEval);
        }
        if (debugMode) {
            System.out.println("/primary");
        }
        return ret;
    }

    private boolean isCastOp(GraphTargetItem item) {
        LocalData localData = LocalData.create(new ConstantPool(constantPool), this.swf, new LinkedHashSet<>());
        List<String> items = new ArrayList<>();
        while (item instanceof GetMemberActionItem) {
            GetMemberActionItem mem = (GetMemberActionItem) item;
            if (mem.memberName instanceof DirectValueActionItem) {
                items.add(0, mem.memberName.toStringNoQuotes(localData));
            }
            item = mem.object;
        }
        if (item instanceof VariableActionItem) {
            VariableActionItem v = (VariableActionItem) item;
            items.add(0, v.getVariableName());
        }

        if (items.isEmpty()) {
            return false;
        }
        String fullName = String.join(".", items);
        if (BUILTIN_CASTS.contains(fullName)) {
            return true;
        }
        if (swfClasses.contains(fullName)) {
            return true;
        }
        return false;
    }

    private GraphTargetItem memberOrCall(GraphTargetItem ret, boolean inWith, boolean inFunction, boolean inMethod, boolean inTellTarget, List<VariableActionItem> variables, List<FunctionActionItem> functions, boolean allowCall, Reference<Boolean> hasEval) throws IOException, ActionParseException, InterruptedException {
        ParsedSymbol op = lex();
        while (op.isType(SymbolType.PARENT_OPEN, SymbolType.BRACKET_OPEN, SymbolType.DOT)) {
            if (op.type == SymbolType.PARENT_OPEN) {
                if (!allowCall) {
                    break;
                }
                List<GraphTargetItem> args = call(inWith, inFunction, inMethod, inTellTarget, variables, functions, hasEval);
                if (isCastOp(ret) && args.size() == 1) {
                    ret = new CastOpActionItem(null, null, ret, args.get(0));
                } else if (ret instanceof GetMemberActionItem) {
                    GetMemberActionItem mem = (GetMemberActionItem) ret;
                    ret = new CallMethodActionItem(null, null, mem.object, mem.memberName, args);
                } else if (ret instanceof VariableActionItem) {
                    VariableActionItem var = (VariableActionItem) ret;

                    if (var.getVariableName().equals("getProperty")
                            && args.size() == 2
                            && (args.get(1) instanceof VariableActionItem)
                            && (Action.propertyNamesListLowerCase.contains(((VariableActionItem) args.get(1)).getVariableName().toLowerCase()))) {
                        ret = new GetPropertyActionItem(null, null, args.get(0), Action.propertyNamesListLowerCase.indexOf(((VariableActionItem) args.get(1)).getVariableName().toLowerCase()));
                    } else if (var.getVariableName().equals("setProperty")
                            && args.size() == 3
                            && (args.get(1) instanceof VariableActionItem)
                            && (Action.propertyNamesListLowerCase.contains(((VariableActionItem) args.get(1)).getVariableName().toLowerCase()))) {
                        ret = new SetPropertyActionItem(null, null, args.get(0), Action.propertyNamesListLowerCase.indexOf(((VariableActionItem) args.get(1)).getVariableName().toLowerCase()), args.get(2));
                    } else {
                        ret = new CallFunctionActionItem(null, null, var, args);
                    }
                } else if (ret instanceof EvalActionItem) {
                    EvalActionItem ev = (EvalActionItem) ret;
                    ret = new CallFunctionActionItem(null, null, ev.value, args);
                } else {
                    ret = new CallFunctionActionItem(null, null, ret, args);
                }
            }
            if (op.type == SymbolType.BRACKET_OPEN) {
                GraphTargetItem rhs = expression(inWith, inFunction, inMethod, inTellTarget, false, variables, functions, false, hasEval);
                ret = new GetMemberActionItem(null, null, ret, rhs);
                expectedType(SymbolType.BRACKET_CLOSE);
            }
            if (op.type == SymbolType.DOT) {
                ParsedSymbol s = lex();
                expectedIdentifier(s, lexer.yyline(), SymbolType.THIS, SymbolType.SUPER);

                ret = new GetMemberActionItem(null, null, ret, pushConst(s.value.toString()));
            }
            op = lex();
        }

        switch (op.type) {
            case INCREMENT: //postincrement
                if (!(ret instanceof VariableActionItem) && !(ret instanceof GetMemberActionItem)) {
                    throw new ActionParseException("Invalid assignment", lexer.yyline());
                }
                ret = new PostIncrementActionItem(null, null, ret);
                op = lex();
                break;
            case DECREMENT: //postdecrement
                if (!(ret instanceof VariableActionItem) && !(ret instanceof GetMemberActionItem)) {
                    throw new ActionParseException("Invalid assignment", lexer.yyline());
                }
                ret = new PostDecrementActionItem(null, null, ret);
                op = lex();
                break;
        }

        lexer.pushback(op);
        return ret;
    }

    private DirectValueActionItem pushConst(String s) throws IOException, ActionParseException {

        //ActionConstantPool was introduced in SWF 5
        if (swfVersion < 5) {
            return new DirectValueActionItem(null, null, 0, s, constantPool);
        }

        int index = constantPool.indexOf(s);
        if (index == -1) {
            int newItemLen = ActionConstantPool.calculateSize(s, charset);
            if (constantPool.size() < 0xffff
                    && constantPoolLength + newItemLen <= 0xffff) {
                // constant pool is not full
                constantPool.add(s);
                index = constantPool.indexOf(s);
                constantPoolLength += newItemLen;
            }
        }

        if (index == -1) {
            return new DirectValueActionItem(null, null, 0, s, constantPool);
        }

        return new DirectValueActionItem(null, null, 0, new ConstantIndex(index), constantPool);
    }

    private ActionScriptLexer lexer = null;

    private List<String> constantPool;

    private int constantPoolLength = 2; //ActionConstantPool starts with UI16 constant count

    
    public List<GraphTargetItem> treeFromString(String str, List<String> constantPool) throws ActionParseException, IOException, InterruptedException {

        try {
            replacements = IdentifiersDeobfuscation.getReplacementsFromDoc(str);
        } catch (Exception ex) {
            throw new ActionParseException(ex.getMessage(), -1);
        }

        List<GraphTargetItem> retTree = new ArrayList<>();
        this.constantPool = constantPool;
        this.constantPoolLength = ActionConstantPool.calculateSize(constantPool, charset);
        lexer = new ActionScriptLexer(new StringReader(str));
        if (swfVersion >= ActionScriptLexer.SWF_VERSION_CASE_SENSITIVE) {
            lexer.setCaseSensitiveIdentifiers(true);
        }

        BUTTONCONDACTION newButtonCond = new BUTTONCONDACTION();

        if (targetSource instanceof BUTTONCONDACTION) {
            ParsedSymbol symb = lex();
            if (symb.type != SymbolType.IDENTIFIER || !"on".equals(symb.value)) {
                throw new ActionParseException("on keyword expected but " + symb + " found", lexer.yyline());
            }
            expectedType(SymbolType.PARENT_OPEN);
            symb = lex();
            boolean condEmpty = true;
            while (symb.type == SymbolType.IDENTIFIER) {
                condEmpty = false;
                switch ((String) symb.value) {
                    case "press":
                        newButtonCond.condOverUpToOverDown = true;
                        break;
                    case "release":
                        newButtonCond.condOverDownToOverUp = true;
                        break;
                    case "releaseOutside":
                        newButtonCond.condOutDownToIdle = true;
                        break;
                    case "rollOver":
                        newButtonCond.condIdleToOverUp = true;
                        break;
                    case "rollOut":
                        newButtonCond.condOverUpToIddle = true;
                        break;
                    case "dragOut":
                        newButtonCond.condOverDownToOutDown = true;
                        break;
                    case "dragOver":
                        newButtonCond.condOutDownToOverDown = true;
                        break;
                    case "keyPress":
                        symb = lex();
                        expected(symb, lexer.yyline(), SymbolType.STRING);
                        Integer key = CLIPACTIONRECORD.stringToKey((String) symb.value);
                        if (key == null) {
                            throw new ActionParseException("Invalid key", lexer.yyline());
                        }
                        newButtonCond.condKeyPress = key;
                        break;
                    default:
                        throw new ActionParseException("Unrecognized event type", lexer.yyline());
                }
                symb = lex();
                if (symb.type == SymbolType.PARENT_CLOSE) {
                    break;
                }
                expected(symb, lexer.yyline(), SymbolType.COMMA);
                symb = lex();
            }
            expected(symb, lexer.yyline(), SymbolType.PARENT_CLOSE);
            if (condEmpty) {
                throw new ActionParseException("condition must be non empty", lexer.yyline());
            }
            expectedType(SymbolType.CURLY_OPEN);
        }

        CLIPEVENTFLAGS newClipEventFlags = new CLIPEVENTFLAGS();
        int newClipActionRecordKey = 0;
        if (targetSource instanceof CLIPACTIONRECORD) {
            ParsedSymbol symb = lex();
            if (symb.type != SymbolType.IDENTIFIER || (!"on".equals(symb.value) && !"onClipEvent".equals(symb.value))) {
                throw new ActionParseException("on or onClipEvent keyword expected but " + symb + " found", lexer.yyline());
            }
            expectedType(SymbolType.PARENT_OPEN);
            if ("on".equals(symb.value)) {
                symb = lex();
                boolean condEmpty = true;
                while (symb.type == SymbolType.IDENTIFIER) {
                    condEmpty = false;
                    switch ((String) symb.value) {
                        case "press":
                            newClipEventFlags.clipEventPress = true;
                            break;
                        case "release":
                            newClipEventFlags.clipEventRelease = true;
                            break;
                        case "releaseOutside":
                            newClipEventFlags.clipEventReleaseOutside = true;
                            break;
                        case "rollOver":
                            newClipEventFlags.clipEventRollOver = true;
                            break;
                        case "rollOut":
                            newClipEventFlags.clipEventRollOut = true;
                            break;
                        case "dragOut":
                            newClipEventFlags.clipEventDragOut = true;
                            break;
                        case "dragOver":
                            newClipEventFlags.clipEventDragOver = true;
                            break;
                        case "initialize":
                            newClipEventFlags.clipEventInitialize = true;
                            break;
                        case "construct":
                            newClipEventFlags.clipEventConstruct = true;
                            break;

                        case "keyPress":
                            symb = lex();
                            expected(symb, lexer.yyline(), SymbolType.STRING);
                            Integer key = CLIPACTIONRECORD.stringToKey((String) symb.value);
                            if (key == null) {
                                throw new ActionParseException("Invalid key", lexer.yyline());
                            }
                            newClipActionRecordKey = key;
                            newClipEventFlags.clipEventKeyPress = true;
                            break;
                        default:
                            throw new ActionParseException("Unrecognized event type", lexer.yyline());
                    }
                    symb = lex();
                    if (symb.type == SymbolType.PARENT_CLOSE) {
                        break;
                    }
                    expected(symb, lexer.yyline(), SymbolType.COMMA);
                    symb = lex();
                }
                expected(symb, lexer.yyline(), SymbolType.PARENT_CLOSE);
                if (condEmpty) {
                    throw new ActionParseException("condition must be non empty", lexer.yyline());
                }
            } else if ("onClipEvent".equals(symb.value)) {
                symb = lex();
                expected(symb, lexer.yyline(), SymbolType.IDENTIFIER);

                switch ((String) symb.value) {
                    case "keyUp":
                        newClipEventFlags.clipEventKeyUp = true;
                        break;
                    case "keyDown":
                        newClipEventFlags.clipEventKeyDown = true;
                        break;
                    case "mouseUp":
                        newClipEventFlags.clipEventMouseUp = true;
                        break;
                    case "mouseDown":
                        newClipEventFlags.clipEventMouseDown = true;
                        break;
                    case "mouseMove":
                        newClipEventFlags.clipEventMouseMove = true;
                        break;
                    case "unload":
                        newClipEventFlags.clipEventUnload = true;
                        break;
                    case "enterFrame":
                        newClipEventFlags.clipEventEnterFrame = true;
                        break;
                    case "load":
                        newClipEventFlags.clipEventLoad = true;
                        break;
                    case "data":
                        newClipEventFlags.clipEventData = true;
                        break;
                    default:
                        throw new ActionParseException("Unrecognized clipEvent type", lexer.yyline());
                }
                expectedType(SymbolType.PARENT_CLOSE);
            }
            expectedType(SymbolType.CURLY_OPEN);
        }

        List<VariableActionItem> vars = new ArrayList<>();
        List<FunctionActionItem> functions = new ArrayList<>();
        Reference<Boolean> hasEval = new Reference<>(false);
        retTree.addAll(commands(false, false, false, 0, false, vars, functions, hasEval));
        for (VariableActionItem v : vars) {
            String varName = v.getVariableName();
            GraphTargetItem stored = v.getStoreValue();
            int propIndex = -1;
            boolean hasSubVars = false;
            propIndex = Action.propertyNamesListLowerCase.indexOf(varName.toLowerCase());
            if (v.isDefinition()) {
                if (hasSubVars) {
                    throw new ActionParseException("Invalid : character in variable definition", lexer.yyline());
                }
                v.setBoxedValue(new DefineLocalActionItem(null, null, pushConst(varName), stored));
            } else if (stored != null) {
                if (propIndex > -1) {
                    v.setBoxedValue(new SetPropertyActionItem(null, null, pushConst(""), propIndex, stored));
                } else {
                    v.setBoxedValue(new SetVariableActionItem(null, null, pushConst(varName), stored));
                }

            } else if (propIndex > -1) {
                v.setBoxedValue(new GetPropertyActionItem(null, null, pushConst(""), propIndex));
            } else {
                v.setBoxedValue(new GetVariableActionItem(null, null, pushConst(varName)));
            }
        }

        if ((targetSource instanceof BUTTONCONDACTION) || (targetSource instanceof CLIPACTIONRECORD)) {
            expectedType(SymbolType.CURLY_CLOSE);
        }

        if (lex().type != SymbolType.EOF) {
            throw new ActionParseException("Parsing finished before end of the file", lexer.yyline());
        }
        if (targetSource instanceof BUTTONCONDACTION) {
            BUTTONCONDACTION targetButtonCond = (BUTTONCONDACTION) targetSource;
            targetButtonCond.condIdleToOverDown = newButtonCond.condIdleToOverDown;
            targetButtonCond.condIdleToOverUp = newButtonCond.condIdleToOverUp;
            targetButtonCond.condOutDownToIdle = newButtonCond.condOutDownToIdle;
            targetButtonCond.condOutDownToOverDown = newButtonCond.condOutDownToOverDown;
            targetButtonCond.condOverDownToIdle = newButtonCond.condOverDownToIdle;
            targetButtonCond.condOverDownToOutDown = newButtonCond.condOverDownToOutDown;
            targetButtonCond.condOverDownToOverUp = newButtonCond.condOverDownToOverUp;
            targetButtonCond.condOverUpToIddle = newButtonCond.condOverUpToIddle;
            targetButtonCond.condOverUpToOverDown = newButtonCond.condOverUpToOverDown;
            targetButtonCond.condKeyPress = newButtonCond.condKeyPress;
        }

        if (targetSource instanceof CLIPACTIONRECORD) {
            CLIPACTIONRECORD targetClipActionRecord = (CLIPACTIONRECORD) targetSource;
            targetClipActionRecord.eventFlags = newClipEventFlags;
            targetClipActionRecord.keyCode = newClipActionRecordKey;
            targetClipActionRecord.getParentClipActions().calculateAllEventFlags();
        }
        return retTree;
    }

    private List<GraphSourceItem> generateActionList(List<GraphTargetItem> tree, List<String> constantPool, boolean secondRun) throws CompilationException {
        ActionSourceGenerator gen = new ActionSourceGenerator(swfVersion, constantPool, constantPoolLength, charset);
        SourceGeneratorLocalData localData = new SourceGeneratorLocalData(new HashMap<>(), 0, Boolean.FALSE, 0);
        localData.secondRun = secondRun;
        return gen.generate(localData, tree);
    }

    private List<Action> actionsFromTree(List<GraphTargetItem> tree, List<String> constantPool, boolean doOrder, String charset) throws CompilationException, NeedsGenerateAgainException {
        List<Action> ret = new ArrayList<>();

        List<GraphSourceItem> srcList = generateActionList(tree, constantPool, doOrder == false);

        if (doOrder) {
            List<String> orderedConstantPool = new ArrayList<>();
            boolean canChangeInPlace;
            int lastIndex = constantPool.size() - 1;
            if (lastIndex <= ActionPush.MAX_CONSTANT_INDEX_TYPE8) {
                //can change constant indices as ActionPush contains always 1 byte per constant
                canChangeInPlace = true;
            } else {
                //variable number bytes per ActionPush constant,
                //must generate again to make relative offsets in jumps work
                canChangeInPlace = false;
            }

            //create ordered constant pool, update constantindices when we can changeinplace
            for (GraphSourceItem src : srcList) {
                if (src instanceof ActionPush) {
                    ActionPush ap = (ActionPush) src;
                    for (int i = 0; i < ap.values.size(); i++) {
                        Object val = ap.values.get(i);
                        if (val instanceof ConstantIndex) {
                            ConstantIndex ci = (ConstantIndex) val;
                            String cval = constantPool.get(ci.index);
                            int orderedIndex = orderedConstantPool.indexOf(cval);
                            if (orderedIndex == -1) {
                                orderedIndex = orderedConstantPool.size();
                                orderedConstantPool.add(cval);
                            }
                            if (canChangeInPlace) {
                                //Do NOT change ci.index directly - it may be cloned from other location
                                ap.values.set(i, new ConstantIndex(orderedIndex));
                            }
                        }
                    }
                }
            }
            if (!canChangeInPlace) {
                //generate again, as number of bytes per ActionPush can change
                throw new NeedsGenerateAgainException(orderedConstantPool);
            }
            constantPool = orderedConstantPool;
        }
        for (GraphSourceItem s : srcList) {
            if (s instanceof Action) {
                ret.add((Action) s);
            }
        }
        if (!constantPool.isEmpty()) {
            ret.add(0, new ActionConstantPool(constantPool, charset));
        }
        return ret;
    }

   
    public List<Action> actionsFromString(String s, String charset) throws ActionParseException, IOException, CompilationException, InterruptedException {
        try {
            List<String> constantPool = new ArrayList<>();
            List<GraphTargetItem> tree = treeFromString(s, constantPool);
            return actionsFromTree(tree, constantPool, true, charset);
        } catch (NeedsGenerateAgainException nga) {
            //Can happen when constantpool needs reordering and number of constants > 256
            try {
                List<String> newConstantPool = nga.getNewConstantPool();
                List<GraphTargetItem> tree = treeFromString(s, newConstantPool);
                return actionsFromTree(tree, newConstantPool, false, charset);
            } catch (NeedsGenerateAgainException ex) {
                //should not happen as doOrder parameter is set to false
                return new ArrayList<>();
            }
        }
    }

    private void versionRequired(ParsedSymbol s, int min) throws ActionParseException {
        versionRequired(s.value.toString(), min, Integer.MAX_VALUE);
    }

    private void versionRequired(ParsedSymbol s, int min, int max) throws ActionParseException {
        versionRequired(s.value.toString(), min, max);
    }

    private void versionRequired(String type, int min, int max) throws ActionParseException {
        if (min == max && swfVersion != min) {
            throw new ActionParseException(type + " requires SWF version " + min, lexer.yyline());
        }
        if (swfVersion < min) {
            throw new ActionParseException(type + " requires at least SWF version " + min, lexer.yyline());
        }
        if (swfVersion > max) {
            throw new ActionParseException(type + " requires SWF version lower than " + max, lexer.yyline());
        }
    }
*/
}
