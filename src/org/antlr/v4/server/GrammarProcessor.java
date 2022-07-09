package org.antlr.v4.server;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.tool.*;

import java.util.Arrays;

public class GrammarProcessor {
    public static String interp(String grammar, String input, String startRule) {
        grammar = grammar.replace("\r", "");
        input = input.replace("\r", "");
        startRule = startRule.strip();
        Grammar g = null;
        LexerGrammar lg = null;
        CollectGrammarErrorsAndWarnings listener = new CollectGrammarErrorsAndWarnings();
        try {
            g = new IgnoreTokenVocabGrammar(null, grammar, null, listener);
            System.err.println("grammar warns" + listener.warnings);
            System.err.println("grammar errors" + listener.errors);

            if ( listener.errors.size()>0 ) {
                return String.format("{\"tool_warnings\":[%s],\"tool_errors\":[%s],\"result\":{}}",
                        String.join(",", listener.warnings),
                        String.join(",", listener.errors));
            }
        }
        catch (RecognitionException re) {
            // shouldn't get here.
            System.err.println("Can't parse grammar");
        }

        CharStream charStream = CharStreams.fromString(input);

        LexerInterpreter lexEngine = (lg != null) ?
                lg.createLexerInterpreter(charStream) :
                g.createLexerInterpreter(charStream);

        CollectLexOrParseSyntaxErrors lexListener = new CollectLexOrParseSyntaxErrors();
        lexEngine.removeErrorListeners();
        lexEngine.addErrorListener(lexListener);

        CommonTokenStream tokens = new CommonTokenStream(lexEngine);

        tokens.fill();

        GrammarParserInterpreter parser = g.createGrammarParserInterpreter(tokens);

        CollectLexOrParseSyntaxErrors parseListener = new CollectLexOrParseSyntaxErrors();
        parser.removeErrorListeners();
        parser.addErrorListener(parseListener);

        Rule r = g.rules.get(startRule);
        if (r == null) {
            System.err.println("No such start rule: " + startRule);
            return null;
        }
        ParseTree t = parser.parse(r.index);
        ParseInfo parseInfo = parser.getParseInfo();

        System.out.println("lex msgs" + lexListener.msgs);
        System.out.println("parse msgs" + parseListener.msgs);

        System.out.println(t.toStringTree(parser));

        TokenStream tokenStream = parser.getInputStream();
        CharStream inputStream = tokenStream.getTokenSource().getInputStream();
        String json = JsonSerializer.toJSON(
                t,
                Arrays.asList(parser.getRuleNames()),
                tokenStream,
                inputStream,
                lexListener.msgs,
                parseListener.msgs);
//		System.out.println(json);

        json = String.format("{\"tool_warnings\":[%s],\"tool_errors\":[%s],\"result\":{}, \"result\":%s}",
                String.join(",", listener.warnings),
                String.join(",", listener.errors),
                json);

        return json;
    }
}