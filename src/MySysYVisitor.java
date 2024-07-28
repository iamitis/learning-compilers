import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class MySysYVisitor extends SysYParserBaseVisitor<Void> {
    private final static ArrayList<String> keyword = new ArrayList<>() {{
        add("CONST");
        add("VOID");
        add("INT");
        add("IF");
        add("ELSE");
        add("WHILE");
        add("BREAK");
        add("CONTINUE");
        add("RETURN");
    }};

    private final static ArrayList<String> operator = new ArrayList<>() {{
        add("PLUS");
        add("MINUS");
        add("MUL");
        add("DIV");
        add("MOD");
        add("ASSIGN");
        add("EQ");
        add("NEQ");
        add("LT");
        add("GT");
        add("LE");
        add("GE");
        add("NOT");
        add("AND");
        add("OR");
        add("COMMA");
        add("SEMICOLON");
    }};
    private final static ArrayList<String> certainTwoWayOp = new ArrayList<>() {{
        add("ASSIGN");
        add("EQ");
        add("NEQ");
        add("LT");
        add("GT");
        add("LE");
        add("GE");
        add("AND");
        add("OR");
    }};

    private final static ArrayList<String> L_bracket = new ArrayList<>() {{
        add("L_PAREN");
        add("L_BRACKT");
        add("L_BRACE");
    }};
    private final static ArrayList<String> R_bracket = new ArrayList<>() {{
        add("R_PAREN");
        add("R_BRACKT");
        add("R_BRACE");
    }};

    private final static String EOF = "EOF";
    private String text = null;
    private String type = null;

    private final static String reset = "\u001b[0m";
    private final static String brightCyan = "\u001b[96m";
    private final static String brightRed = "\u001b[91m";
    private final static String magenta = "\u001b[35m";
    private final static String white = "\u001b[97m";
    private final static String brightMagenta = "\u001b[95m";
    private final static String brightYellow = "\u001b[93m";
    private final static String underline = "\u001b[4m";
    private final static String brightGreen = "\u001b[92m";
    private final static String brightBlue = "\u001b[94m";
    private boolean meetStmt = false;
    private boolean meetDecl = false;
    private boolean meetBlock = false;
    private final static Map<Integer, String> colorList = new HashMap<>() {{
        put(0, brightRed);
        put(1, brightGreen);
        put(2, brightYellow);
        put(3, brightBlue);
        put(4, brightMagenta);
        put(5, brightCyan);
    }};
    private final static int COLOR_LIST_LEN = 6;
    private int bracketIndex = 0;

    private boolean startNewLine = false;
    private boolean meetFuncDef = false;
    private boolean attachBlock = false;
    private final static Stack<Integer> depthWhereSingleClauseStart = new Stack<>();
    private final static Stack<Integer> depthWhereBlockStart = new Stack<>();
    private final static Stack<Integer> depthWhereIEStart = new Stack<>();
    private final static ArrayList<String> attachStmt = new ArrayList<>() {{
        add("IF");
        add("ELSE");
        add("WHILE");
    }};
    private int depth = 0;
    private boolean requireBlank = false;
    private boolean emptyReturn = false;
    private boolean singleBlock = false;

    private boolean inStmt() {
        return meetStmt;
    }

    private boolean inDecl() {
        return meetDecl;
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        this.type = SysYParser.VOCABULARY.getSymbolicName(node.getSymbol().getType());
        if (type.equals(EOF)) {
            return null;
        }
        this.text = node.getText();
        printTerminalNode();
        return null;
    }

    @Override
    public Void visitDecl(SysYParser.DeclContext ctx) {
        meetDecl = true;
        return visitChildren(ctx);
    }

//    @Override
//    public Void visitStmt(SysYParser.StmtContext ctx) {
//        meetStmt = true;
//        //
//        if (ctx.getParent().getStart().getText().equals("if") &&
//                ctx.getParent().getChildCount() >= 6 &&
//                ctx.equals(ctx.getParent().getChild(4)) &&
//                ctx.getParent().getChild(5).getText().equals("else")) {
//            depthWhereIEStart.push(depth - 1);
//        }
//        // if we are in an attach-stmt(if, else and while) and its stmt is not a block, reset attach-block.
//        if (attachBlock && !ctx.getStart().getText().equals("{")) {
//            attachBlock = false;
//            // if its stmt is also not ELSE-IF, it is a single-line clause
//            if (!(type.equals("ELSE") && ctx.getStart().getText().equals("if"))) {
//                startNewLine = true;
//                depthWhereSingleClauseStart.push(depth);
//                ++depth;
//                requireBlank = false;
//            }
//
//        }
//        // if this stmt is a return-stmt, and it only has two children ("return", ";"), it is an empty return-stmt.
//        if (ctx.getStart().getText().equals("return") && ctx.getChildCount() == 2) {
//            emptyReturn = true;
//        }
//
//        return visitChildren(ctx);
//    }

    @Override
    public Void visitFuncName(SysYParser.FuncNameContext ctx) {
        meetFuncDef = false; // funcType has been printed, so we can reset this flag to avoid printing redundant blank line.
        type = "funcName";
        text = ctx.getChild(0).getText();
        format();
        highlight();
        System.out.print(ctx.getChild(0).getText());
        return null;
    }

    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        meetBlock = true;
        if (inStmt() && !attachBlock) {
            startNewLine = true;
            singleBlock = true;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        meetFuncDef = true;
        return visitChildren(ctx);
    }

    @Override
    public Void visitExpTwoWay(SysYParser.ExpTwoWayContext ctx) {
        visit(ctx.getChild(0));
        System.out.print(reset + " ");
        visit(ctx.getChild(1));
        System.out.print(reset + " ");
        visit(ctx.getChild(2));
        return null;
    }

    private void highlight() {
        if (inDecl()) {
            System.out.print(underline + brightMagenta);
        } else if (inStmt()) {
            System.out.print(reset + white);
        }

        if (keyword.contains(type)) {
            System.out.print(brightCyan);
        } else if (operator.contains(type)) {
            System.out.print(brightRed);
        } else if (type.equals("funcName")) {
            System.out.print(brightYellow);
        } else if (type.equals("INTEGER_CONST")) {
            System.out.print(magenta);
        } else if (L_bracket.contains(type)) {
            System.out.print(colorList.get((bracketIndex++) % COLOR_LIST_LEN));
        } else if (R_bracket.contains(type)) {
            System.out.print(colorList.get((--bracketIndex) % COLOR_LIST_LEN));
        } else if (!inStmt() && !inDecl()) {
            System.out.print(reset);
        }
    }

    private void handleHighlight() {
        if (type.equals("SEMICOLON")) {
            // decl or stmt reach end (may not)
            meetDecl = false;
            meetStmt = false; // ? I think I write it wrong but all subtask2 passed.
        }
    }

    private void format() {
        System.out.print(reset);
        // new line
        if (startNewLine) {
            startNewLine = false;
            System.out.println();
            if (meetFuncDef) {
                System.out.println();
            }
            // indent
            if (type.equals("R_BRACE")) {
                --depth;
            }
            for (int i = 0; i < depth; ++i) {
                System.out.print("    ");
            }
        }
        // blank
        // "{"
        if (type.equals("L_BRACE") && !singleBlock && !inDecl()) {
            requireBlank = true;
        }
        if (requireBlank || certainTwoWayOp.contains(type)) {
            System.out.print(" ");
        }
        requireBlank = certainTwoWayOp.contains(type); // =,==,&&... both sides have blank
    }

    private void handleNewLine() {
        // one stmt one line, meeting ";" means one stmt reaches end, and we should start a new line.
        if (type.equals("SEMICOLON")) {
            startNewLine = true;
        }
        // if meeting "{" or "}" we should start a new line, unless we are in a decl.
        if (!inDecl() && (type.equals("R_BRACE") || type.equals("L_BRACE"))) {
            startNewLine = true;
            singleBlock = false;
        }
        // if meeting IF, ELSE or WHILE, the next block is attached to them.
        // so the first "{" in the block should stay in same line.
        if (attachStmt.contains(type)) {
            attachBlock = true;
        }
        // once meeting the first "{", attachBlock should be reset.
        if (type.equals("L_BRACE")) {
            attachBlock = false;
        }
    }

    private void handleIndent() {
        if (meetBlock && type.equals("L_BRACE")) {
            depthWhereBlockStart.push(depth);
            ++depth;
            meetBlock = false;
        }
        // handle case where single-clause and block mix together
        if (!depthWhereBlockStart.empty() && type.equals("R_BRACE")) {
            depthWhereBlockStart.pop();
        }
        if (type.equals("SEMICOLON") || type.equals("R_BRACE")) {
            while (!depthWhereSingleClauseStart.empty() &&
                    depthWhereSingleClauseStart.peek() + 1 == depth) {
                if (!depthWhereBlockStart.empty()) {
                    if (depthWhereBlockStart.peek() + 1 < depth) {
                        if (!depthWhereIEStart.empty()) {
                            if (depthWhereIEStart.peek() + 1 < depth) {
                                --depth;
                                depthWhereSingleClauseStart.pop();
                            } else {
                                depthWhereIEStart.pop();
                                break;
                            }
                        } else {
                            --depth;
                            depthWhereSingleClauseStart.pop();
                        }
                    } else {
                        break;
                    }
                } else {
                    if (!depthWhereIEStart.empty()) {
                        if (depthWhereIEStart.peek() + 1 < depth) {
                            --depth;
                            depthWhereSingleClauseStart.pop();
                        } else {
                            depthWhereIEStart.pop();
                            break;
                        }
                    } else {
                        --depth;
                        depthWhereSingleClauseStart.pop();
                    }
                }
            }
            if (!depthWhereIEStart.empty() && depthWhereIEStart.peek() + 1 == depth) {
                depthWhereIEStart.pop();
            }
        }
    }

    private void handleBlank() {
        if ((keyword.contains(type) && !type.equals("BREAK") && !type.equals("CONTINUE")) ||
                type.equals("COMMA")) {
            requireBlank = true;
        }
        if (emptyReturn) {
            requireBlank = false;
            emptyReturn = false;
        }
    }

    private void printTerminalNode() {
        format();
        highlight();

        System.out.print(text + reset);

        handleHighlight();
        handleNewLine();
        handleIndent();
        handleBlank();
    }

}
