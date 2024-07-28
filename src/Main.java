import java.io.*;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;

import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToFile;

public class Main {
    public static final BytePointer error = new BytePointer();

    public static void main(String[] args) throws IOException {
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);

        // lab2
//        MyParserErrorListener myParserErrorListener = new MyParserErrorListener();
//        sysYParser.removeErrorListeners();
//        sysYParser.addErrorListener(myParserErrorListener);

        ParseTree tree = sysYParser.program();

        // lab2
//        if (myParserErrorListener.parserErrorDetected()) {
//            myParserErrorListener.printParserErrorInformation();
//            return;
//        }

        // lab3
        // TypeCheckVisitor visitor = new TypeCheckVisitor();
        // visitor.visit(tree);

        Lab4Visitor visitor = new Lab4Visitor();
        LLVMModuleRef module = visitor.getModule();
        visitor.visit(tree);
        if (LLVMPrintModuleToFile(module, "./tests/test1.ll", error) != 0) {
            LLVMDisposeMessage(error);
        }
        LRScanner lrScanner = new LRScanner(module);
        lrScanner.scan();
        lrScanner.outputToFile(args[1]);
    }

}