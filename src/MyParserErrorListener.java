import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;

public class MyParserErrorListener extends BaseErrorListener {

    ArrayList<ParserErrorInfo> parserErrorList;

    @Override
    public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        if (this.parserErrorList == null) {
            this.parserErrorList = new ArrayList<>();
        }
        ParserErrorInfo parserErrorInfo = new ParserErrorInfo(line, "");
        parserErrorList.add(parserErrorInfo);
    }

    private static class ParserErrorInfo {
        int line;
        String text;

        ParserErrorInfo(int line, String text) {
            this.line = line;
            this.text = text;
        }
    }

    public boolean parserErrorDetected() {
        return this.parserErrorList != null;
    }

    public void printParserErrorInformation() {
        if (parserErrorList == null) {
            System.out.println("no error detected");
            return;
        }
        for (ParserErrorInfo parserErrorInfo : parserErrorList) {
            String errorMsg = String.format("Error type B at Line %d: %s.", parserErrorInfo.line, parserErrorInfo.text);
            System.out.println(errorMsg);
        }
    }
}
