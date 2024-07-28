import enums.SemanticErrorType;
import org.antlr.v4.runtime.tree.ParseTree;
import scope.BaseScope;
import scope.FunctionScope;
import scope.Scope;
import symbol.BaseSymbol;
import symbol.Symbol;
import type.*;

import java.util.ArrayList;

public class TypeCheckVisitor extends SysYParserBaseVisitor<Void> {
    private Scope currentScope = null;
    private boolean hasError = false;
    private final static String noErrorMsg = "No semantic errors in the program!";
    private boolean retStmt = false;
    private boolean operand = false;
    private boolean atRight = false;
    private boolean condTwoWayError = false;
    private String retType = null;

    @Override
    public Void visitProgram(SysYParser.ProgramContext ctx) {
        currentScope = new BaseScope(null);
        visitChildren(ctx);
        if (!hasError) {
            System.err.println(noErrorMsg);
        }
        return null;
    }

    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        // initiate a new function-symbol
        String funcName = ctx.funcName().getText();
        Type funcRetType = ctx.funcType().getText().equals("int") ? new INTType() : new VOIDType();
        this.retType = funcRetType.toString();
        ArrayList<Type> funcParamsType = null;
        if (ctx.funcFParams() != null) {
            funcParamsType = new ArrayList<>();
        }
        Type funcType = new FunctionType(funcRetType, funcParamsType);
        BaseSymbol funcSymbol = new BaseSymbol(funcName, funcType);
        // try to define this new symbol
        int errorCode = currentScope.define(funcSymbol);
        if (errorCode != 0) {
            hasError = true;
            printError(ctx.funcName().getStart().getLine(), SemanticErrorType.REDEFINED_FUNC);
            return null;
        }
        // checkout new scope and visit children
        // remember we should fill p-params type in the symbol and the new scope
        currentScope = new FunctionScope(funcSymbol, currentScope);
        currentScope.define(funcSymbol);
        if (ctx.funcFParams() != null) {
            visit(ctx.funcFParams());
        }
        currentScope = new BaseScope(currentScope);
        visitBlock(ctx.block());
        currentScope = currentScope.getParentScope();
        currentScope = currentScope.getParentScope();
        this.retType = null;
        return null;
    }

    @Override
    public Void visitFuncFParam(SysYParser.FuncFParamContext ctx) {
        BaseSymbol fParamSymbol;
        String fParamName = ctx.IDENT().getText();
        Type fParamType;
        if (ctx.L_BRACKT().isEmpty()) {
            fParamType = new INTType();
        } else {
            // one-dimensional array
            fParamType = new ArrayType(1, -1, new INTType());
        }
        fParamSymbol = new BaseSymbol(fParamName, fParamType);
        int errorCode = currentScope.define(fParamSymbol);
        if (errorCode == 0) {
            ArrayList<Type> fParamsType = ((FunctionType) ((FunctionScope) currentScope).getFunctionSymbol().getType()).getParamsType();
            fParamsType.add(fParamType);
        } else {
            hasError = true;
            printError(ctx.IDENT().getSymbol().getLine(), SemanticErrorType.REDECLARED_VAR);
        }
        return null;
    }

    @Override
    public Void visitConstDef(SysYParser.ConstDefContext ctx) {
        String constName = ctx.IDENT().getText();
        Type constType = new INTType();
        if (!ctx.L_BRACKT().isEmpty()) {
            int dimension = ctx.L_BRACKT().size();
            for (int i = 1; i <= dimension; ++i) {
                constType = new ArrayType(i, -1, constType);
            }
            // check type of exp. e.g., array[exp]
            for (int i = 0; i < dimension; ++i) {
                checkExp(ctx.constExp(i).exp());
            }
        }
        // check type of constInitVal matched
        if (constType.toString().equals("int")) {
            atRight = true;
            SysYParser.ExpContext expCtx = ctx.constInitVal().constExp() == null ? null : ctx.constInitVal().constExp().exp();
            Type assignType = expCtx == null ? new ArrayType(1, -1, new INTType()) : checkExp(expCtx);
            atRight = false;
            if (assignType == null) { // another error
                return null;
            }
            if (!assignType.toString().equals(constType.toString())) {
                hasError = true;
                printError(ctx.ASSIGN().getSymbol().getLine(), SemanticErrorType.MISMATCHED_ASSIGN);
                return null;
            }
        }
        // check redefined
        Symbol constSymbol = new BaseSymbol(constName, constType);
        int errorCode = currentScope.define(constSymbol);
        if (errorCode != 0) {
            hasError = true;
            printError(ctx.getStart().getLine(), SemanticErrorType.REDECLARED_VAR);
            return null;
        }
        return null;
    }

    @Override
    public Void visitVarDef(SysYParser.VarDefContext ctx) {
        String varName = ctx.IDENT().getText();
        Type varType = new INTType();
        if (!ctx.L_BRACKT().isEmpty()) {
            int dimension = ctx.L_BRACKT().size();
            for (int i = 1; i <= dimension; ++i) {
                varType = new ArrayType(i, -1, varType);
            }
            // check type of exp. e.g., array[exp]
            for (int i = 0; i < dimension; ++i) {
                checkExp(ctx.constExp(i).exp());
            }
        }
        // check assign
        if (ctx.ASSIGN() != null && varType.toString().equals("int")) {
            atRight = true;
            SysYParser.ExpContext expCtx = ctx.initVal().exp();
            Type assignType = expCtx == null ? new ArrayType(1, -1, new INTType()) : checkExp(expCtx);
            atRight = false;
            if (assignType == null) { // another error
                return null;
            }
            if (!assignType.toString().equals(varType.toString())) {
                hasError = true;
                printError(ctx.ASSIGN().getSymbol().getLine(), SemanticErrorType.MISMATCHED_ASSIGN);
                return null;
            }
        }
        // try to define a new symbol
        Symbol valSymbol = new BaseSymbol(varName, varType);
        int errorCode = currentScope.define(valSymbol);
        if (errorCode != 0) {
            hasError = true;
            printError(ctx.getStart().getLine(), SemanticErrorType.REDECLARED_VAR);
            return null;
        }
        return null;
    }

//    @Override
//    public Void visitStmt(SysYParser.StmtContext ctx) {
//        // if meeting return-statement, check return-type
//        if (ctx.getStart().getText().equals("return")) {
//            if (ctx.getChildCount() <= 2) {
//                if (this.retType.equals("void")) {
//                    return null;
//                }
//                hasError = true;
//                printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_RETURN);
//                return null;
//            }
//            SysYParser.ExpContext retCtx = ctx.exp();
//            String expectedType = this.retType;
//            retStmt = true;
//            Type actualType = checkExp(retCtx);
//            retStmt = false;
//            if (actualType != null && !actualType.toString().equals(expectedType)) {
//                hasError = true;
//                printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_RETURN);
//            }
//            return null;
//        }
//        // assign-statement, check type of left val and exp
//        if (ctx.getChild(1) != null && ctx.getChild(1).getText().equals("=")) {
//            Type leftType = checkLValType(ctx.lVal());
//            if (leftType == null) {
//                return null;
//            }
//            atRight = true;
//            Type rightType = checkExp(ctx.exp());
//            atRight = false;
//            if (rightType == null) {
//                return null;
//            }
//            if (!leftType.toString().equals(rightType.toString())) {
//                hasError = true;
//                printError(ctx.ASSIGN().getSymbol().getLine(), SemanticErrorType.MISMATCHED_ASSIGN);
//                return null;
//            }
//            return null;
//        }
//        // function call exp;
//        if (ctx.getChild(0).getClass().equals(SysYParser.FuncCallContext.class)) {
//            checkFuncCall(((SysYParser.FuncCallContext) ctx.exp()));
//            return null;
//        }
//        // block
//        if (ctx.getChild(0).getClass().equals(SysYParser.BlockContext.class)) {
//            currentScope = new BaseScope(currentScope);
//            visitChildren(ctx);
//            currentScope = currentScope.getParentScope();
//            return null;
//        }
//
//        visitChildren(ctx);
//        return null;
//    }

    @Override
    public Void visitLValExp(SysYParser.LValExpContext ctx) {
        checkLValType(ctx.lVal());
        return null;
    }

    @Override
    public Void visitOneWay(SysYParser.OneWayContext ctx) {
        operand = true;
        Type lValType = checkExp(ctx.exp());
        operand = false;
        if (lValType != null && !lValType.toString().equals("int")) {
            hasError = true;
            printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_OPERAND);
        }
        return null;
    }

    @Override
    public Void visitExpTwoWay(SysYParser.ExpTwoWayContext ctx) {
        operand = true;
        Type leftType = checkExp(ctx.lhs);
        operand = false;
        if (leftType == null) {
            return null;
        }
        operand = true;
        Type rightType = checkExp(ctx.rhs);
        if (rightType == null) {
            return null;
        }
        operand = false;
        if (!leftType.toString().equals(rightType.toString())) {
            hasError = true;
            printError(ctx.op.getLine(), SemanticErrorType.MISMATCHED_OPERAND);
            return null;
        }
        return null;
    }

    @Override
    public Void visitCondTwoWay(SysYParser.CondTwoWayContext ctx) {
        Type leftType = null;
        Type rightType = null;
        if (ctx.lhs.getClass().equals(SysYParser.CondExpContext.class)) {
            operand = true;
            leftType = checkExp(((SysYParser.CondExpContext) ctx.lhs).exp());
            operand = false;
            if (leftType == null) {
                return null;
            }
            if (!leftType.toString().equals("int")) {
                hasError = true;
                condTwoWayError = true;
                printError(ctx.lhs.getStart().getLine(), SemanticErrorType.MISMATCHED_OPERAND);
                return null;
            }
        } else {
            visitCondTwoWay((SysYParser.CondTwoWayContext) ctx.lhs);
            if (condTwoWayError) {
                return null;
            }
        }
        if (ctx.rhs.getClass().equals(SysYParser.CondExpContext.class)) {
            operand = true;
            rightType = checkExp(((SysYParser.CondExpContext) ctx.rhs).exp());
            operand = false;
            if (rightType == null) {
                return null;
            }
            if (!rightType.toString().equals("int")) {
                hasError = true;
                condTwoWayError = true;
                printError(ctx.rhs.getStart().getLine(), SemanticErrorType.MISMATCHED_OPERAND);
                return null;
            }
        } else {
            visitCondTwoWay((SysYParser.CondTwoWayContext) ctx.rhs);
            if (condTwoWayError) {
                return null;
            }
        }
        condTwoWayError = false;
        return null;
    }

    // check returned type of exp
    private Type checkExp(SysYParser.ExpContext ctx) {
        if (ctx.getClass().equals(SysYParser.ParenExpContext.class)) {
            return checkExp(((SysYParser.ParenExpContext) ctx).exp());
        } else if (ctx.getClass().equals(SysYParser.LValExpContext.class)) {
            return checkLValType(((SysYParser.LValExpContext) ctx).lVal());
        } else if (ctx.getClass().equals(SysYParser.NumberExpContext.class)) {
            return new INTType();
        } else if (ctx.getClass().equals(SysYParser.FuncCallContext.class)) {
            return checkFuncCall(((SysYParser.FuncCallContext) ctx));
        } else if (ctx.getClass().equals(SysYParser.OneWayContext.class)) {
            operand = true;
            Type type = checkExp(((SysYParser.OneWayContext) ctx).exp());
            operand = false;
            if (type == null) {
                return null;
            }
            if (!type.toString().equals("int")) {
                hasError = true;
                printError(((SysYParser.OneWayContext) ctx).exp().getStart().getLine(), SemanticErrorType.MISMATCHED_OPERAND);
                return null;
            }
            return type;
        } else if (ctx.getClass().equals(SysYParser.ExpTwoWayContext.class)) {
            operand = true;
            Type leftType = checkExp(((SysYParser.ExpTwoWayContext) ctx).lhs);
            operand = false;
            if (leftType == null) {
                return null;
            }
            operand = true;
            Type rightType = checkExp(((SysYParser.ExpTwoWayContext) ctx).rhs);
            if (rightType == null) {
                return null;
            }
            operand = false;
            if (leftType != null && rightType != null) {
                if (!(leftType.toString().equals("int") && rightType.toString().equals("int"))) {
                    hasError = true;
                    printError(((SysYParser.ExpTwoWayContext) ctx).op.getLine(), SemanticErrorType.MISMATCHED_OPERAND);
                    return null;
                }
                return new INTType();
            }
            return null;
        }
        return null;
    }

    private Type checkLValType(SysYParser.LValContext ctx) {
        Symbol symbol = currentScope.resolve(ctx.IDENT().getText());
        if (symbol != null) {
            Type type = symbol.getType();
            // check if its type is array-type
            int bracketNum = ctx.L_BRACKT().size();
            for (int i = 0; i < bracketNum; ++i) {
                if (type.toString().equals("int")) {
                    hasError = true;
                    printError(ctx.L_BRACKT(i).getSymbol().getLine(),
                            SemanticErrorType.NOT_ARRAY);
                    return null;
                }
                type = ((ArrayType) type).getElementType();
            }
            // check type of exp inside bracket. e.g., array[exp]
            for (int i = 0; i < bracketNum; ++i) {
                Type expType = checkExp(ctx.exp(i));
                if (expType == null) {
                    return null;
                }
                if (!expType.toString().equals("int")) {
                    hasError = true;
                    printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_OPERAND);
                    return null;
                }
            }
            // if its type is function-type but there is no "(" and ")"
            if (type.getClass().equals(FunctionType.class)) {
                hasError = true;
                if (operand) {
                    printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_OPERAND);
                } else if (atRight) {
                    printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_ASSIGN);
                } else if (retStmt) {
                    printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_RETURN);
                } else {
                    printError(ctx.getStart().getLine(), SemanticErrorType.NOT_ASSIGNABLE);
                }
                return null;
            }
            return type;
        }
        hasError = true;
        printError(ctx.getStart().getLine(), SemanticErrorType.UNDECLARED_VAR);
        return null;
    }

    private Type checkFuncCall(SysYParser.FuncCallContext ctx) {
        Symbol symbol = currentScope.resolve(ctx.funcName().getText());
        if (symbol != null) {
            Type type = symbol.getType();
            if (!type.getClass().equals(FunctionType.class)) {
                hasError = true;
                printError(ctx.funcName().getStart().getLine(),
                        SemanticErrorType.NOT_FUNC);
                return null;
            }
            // check type of params
            ArrayList<Type> fParamsType = ((FunctionType) type).getParamsType();
            int fLen = fParamsType == null ? 0 : fParamsType.size();
            int rLen = ctx.funcRParams() == null ? 0 : ctx.funcRParams().param().size();
            if (fLen == rLen) {
                for (int i = 0; i < fLen; ++i) {
                    Type fType = fParamsType.get(i);
                    Type rType = checkExp(ctx.funcRParams().param().get(i).exp());
                    if (rType == null) {
                        return null;
                    }
                    if (!rType.toString().equals(fType.toString())) {
                        hasError = true;
                        printError(ctx.funcRParams().param().get(i).getStart().getLine(), SemanticErrorType.MISMATCHED_PARAM);
                        return null;
                    }
                }
            } else {
                hasError = true;
                printError(ctx.getStart().getLine(), SemanticErrorType.MISMATCHED_PARAM);
                return null;
            }
            return type;
        }
        hasError = true;
        printError(ctx.getStart().getLine(), SemanticErrorType.UNDEFINED_FUNC);
        return null;
    }

    private Type checkCondType(SysYParser.CondContext ctx) {
        if (ctx.getClass().equals(SysYParser.CondExpContext.class)) {
            return checkExp(((SysYParser.CondExpContext) ctx).exp());
        }
        visitCondTwoWay((SysYParser.CondTwoWayContext) ctx);
        visitCondTwoWay((SysYParser.CondTwoWayContext) ctx);
        return null;
    }

    private void printError(int lineNO, SemanticErrorType type) {
        String msg = String.format("Error type %d at Line %d: %s",
                type.ordinal(), lineNO, type);
        System.err.println(msg);
    }
}
