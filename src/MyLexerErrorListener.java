import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;

public class MyLexerErrorListener extends BaseErrorListener {

    private ArrayList<LexerErrorInfo> lexerErrorList;

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        if (lexerErrorList == null) {
            lexerErrorList = new ArrayList<>();
        }
        LexerErrorInfo lexerErrorInfo = new LexerErrorInfo(line, "");
        lexerErrorList.add(lexerErrorInfo);
    }

    public boolean lexerErrorDetected() {
        return lexerErrorList != null;
    }

    private static class LexerErrorInfo {
        int line;
        String text;

        LexerErrorInfo(int line, String text) {
            this.line = line;
            this.text = text;
        }
    }

    public void printLexerErrorInformation() {
        if (lexerErrorList == null) {
            System.err.println("no error detected");
            return;
        }
        for (LexerErrorInfo lexerErrorInfo : lexerErrorList) {
            String errorMsg = String.format("Error type A at Line %d: %s.", lexerErrorInfo.line, lexerErrorInfo.text);
            System.err.println(errorMsg);
        }
    }

}
