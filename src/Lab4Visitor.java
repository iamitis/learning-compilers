import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import static org.bytedeco.llvm.global.LLVM.*;

public class Lab4Visitor extends SysYParserBaseVisitor<LLVMValueRef> {
    private final LLVMModuleRef module = LLVMModuleCreateWithName("module");
    private final LLVMBuilderRef builder = LLVMCreateBuilder();
    private final LLVMTypeRef i32Type = LLVMInt32Type();
    private final LLVMTypeRef voidType = LLVMVoidType();
    private final LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
    private final Stack<Map<String, LLVMValueRef>> tables = new Stack<>();
    private final Map<String, String> retTypeTable = new HashMap<>();
    private LLVMValueRef currentFunction = null;
    private final Stack<LLVMBasicBlockRef> currentWhileCond = new Stack<>();
    private final Stack<LLVMBasicBlockRef> currentWhileEnd = new Stack<>();
    private final Stack<LLVMBasicBlockRef> currentTrue = new Stack<>();
    private final Stack<LLVMBasicBlockRef> currentFalse = new Stack<>();
    private boolean andOr = false;

    public Lab4Visitor() {
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }

    public LLVMModuleRef getModule() {
        return this.module;
    }

    private long getNumber(String numStr) {
        if (numStr.startsWith("0")) {
            if (numStr.contains("x") || numStr.contains("X")) {
                return Integer.valueOf(numStr.substring(2), 16);
            }
            return Integer.valueOf(numStr, 8);
        }
        return Integer.parseInt(numStr);
    }

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        tables.push(new HashMap<>());
        visitChildren(ctx);
        tables.pop();
        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        // create function
        LLVMTypeRef retType = (ctx.funcType().INT() != null) ? i32Type : voidType;
        // get types of parameters
        PointerPointer<Pointer> paramTypes = null;
        int numOfParam = 0;
        SysYParser.FuncFParamsContext p = ctx.funcFParams();
        while (p != null) {
            ++numOfParam;
            p = p.funcFParams(0);
        }
        if (numOfParam > 0) {
            paramTypes = new PointerPointer<>(numOfParam);
            for (int i = 0; i < numOfParam; ++i) {
                paramTypes.put(i, i32Type);
            }
        }
        LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes, numOfParam, 0);
        String funcName = ctx.funcName().getText();
        LLVMValueRef function = LLVMAddFunction(module, funcName, funcType);
        currentFunction = function;
        tables.peek().put(funcName, function);
        // create basic block
        LLVMBasicBlockRef block = LLVMAppendBasicBlock(currentFunction, funcName + "Entry");
        LLVMPositionBuilderAtEnd(builder, block);

        tables.push(new HashMap<>());
        retTypeTable.put(funcName, ctx.funcType().getText());
        // store parameters into tables
        p = ctx.funcFParams();
        for (int i = 0; i < numOfParam; ++i) {
            String paramName = p.funcFParam().IDENT().getText();
            LLVMValueRef paramValue = LLVMGetParam(function, i);
            LLVMValueRef paramPtr = LLVMBuildAlloca(builder, i32Type, paramName);
            LLVMBuildStore(builder, paramValue, paramPtr);
            tables.peek().put(paramName, paramPtr);
            p = p.funcFParams(0);
        }

        visitChildren(ctx);

        if (ctx.funcType().INT() == null) {
            LLVMBuildRetVoid(builder);
        } else {
            LLVMBuildRet(builder, zero);
        }

        currentFunction = null;
        tables.pop();
        return function;
    }

    @Override
    public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
        String constName = ctx.IDENT().getText();
        LLVMValueRef initVal = visit(ctx.constInitVal().constExp().exp());
        if (tables.size() > 1) {
            LLVMValueRef constPtr = LLVMBuildAlloca(builder, i32Type, constName);
            LLVMBuildStore(builder, initVal, constPtr);
            tables.peek().put(constName, constPtr);
        } else {
            LLVMValueRef globalConst = LLVMAddGlobal(module, i32Type, constName);
            LLVMSetInitializer(globalConst, initVal);
            tables.peek().put(constName, globalConst);
        }

        return null;
    }

    @Override
    public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
        String varName = ctx.IDENT().getText();
        if (tables.size() > 1) {
            LLVMValueRef constPtr = LLVMBuildAlloca(builder, i32Type, varName);
            if (ctx.initVal() != null) {
                LLVMValueRef initVal = visit(ctx.initVal().exp());
                LLVMBuildStore(builder, initVal, constPtr);
            } else {
                LLVMBuildStore(builder, zero, constPtr);
            }
            tables.peek().put(varName, constPtr);
        } else {
            LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, varName);
            if (ctx.initVal() != null) {
                LLVMValueRef initVal = visit(ctx.initVal().exp());
                LLVMSetInitializer(globalVar, initVal);
            } else {
                LLVMSetInitializer(globalVar, zero);
            }
            tables.peek().put(varName, globalVar);
        }

        return null;
    }

    @Override
    public LLVMValueRef visitRetStmt(SysYParser.RetStmtContext ctx) {
        if (ctx.exp() != null) {
            return LLVMBuildRet(builder, visit(ctx.exp()));
        }
        return LLVMBuildRetVoid(builder);
    }

    @Override
    public LLVMValueRef visitAssignStmt(SysYParser.AssignStmtContext ctx) {
        String lVarName = ctx.lVal().IDENT().getText();
        LLVMValueRef lVarPtr = null;
        for (int i = tables.size() - 1; i >= 0 && lVarPtr == null; --i) {
            lVarPtr = tables.get(i).get(lVarName);
        }
        if (lVarPtr == null) {
            //System.err.println("couldn't find this variable");
        }
        LLVMValueRef assignVal = visit(ctx.exp());
        return LLVMBuildStore(builder, assignVal, lVarPtr);
    }

    @Override
    public LLVMValueRef visitLValExp(SysYParser.LValExpContext ctx) {
        String name = ctx.lVal().IDENT().getText();
        LLVMValueRef lValPtr = null;
        for (int i = tables.size() - 1; i >= 0 && lValPtr == null; --i) {
            lValPtr = tables.get(i).get(name);
        }
        if (lValPtr == null) {
            //System.err.println("couldn't find this variable");
        }
        return LLVMBuildLoad(builder, lValPtr, name);
    }

    @Override
    public LLVMValueRef visitNumberExp(SysYParser.NumberExpContext ctx) {
        long numVal = getNumber(ctx.number().getText());
        return LLVMConstInt(i32Type, numVal, 0);
    }

    @Override
    public LLVMValueRef visitParenExp(SysYParser.ParenExpContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public LLVMValueRef visitOneWay(SysYParser.OneWayContext ctx) {
        LLVMValueRef expVal = visit(ctx.exp());
        LLVMValueRef res = null;
        String op = ctx.unaryOp().getText();
        switch (op) {
            case "+":
                res = expVal;
                break;
            case "-":
                res = LLVMBuildSub(builder, zero, expVal, "res");
                break;
            case "!":
                res = LLVMBuildICmp(builder, LLVMIntNE, expVal, zero, "res");
                res = LLVMBuildXor(builder, res, LLVMConstInt(LLVMInt1Type(), 1, 0), "res");
                res = LLVMBuildZExt(builder, res, i32Type, "res");
                break;
            default:
                break;
        }
        return res;
    }

    @Override
    public LLVMValueRef visitExpTwoWay(SysYParser.ExpTwoWayContext ctx) {
        LLVMValueRef lhs = visit(ctx.lhs);
        LLVMValueRef rhs = visit(ctx.rhs);
        LLVMValueRef res = null;
        String op = ctx.op.getText();
        switch (op) {
            case "+":
                res = LLVMBuildAdd(builder, lhs, rhs, "res");
                break;
            case "-":
                res = LLVMBuildSub(builder, lhs, rhs, "res");
                break;
            case "*":
                res = LLVMBuildMul(builder, lhs, rhs, "res");
                break;
            case "/":
                res = LLVMBuildSDiv(builder, lhs, rhs, "res");
                break;
            case "%":
                res = LLVMBuildSDiv(builder, lhs, rhs, "res");
                res = LLVMBuildMul(builder, res, rhs, "res");
                res = LLVMBuildSub(builder, lhs, res, "res");
                break;
            default:
                break;
        }
        return res;
    }

    @Override
    public LLVMValueRef visitFuncCall(SysYParser.FuncCallContext ctx) {
        String funcName = ctx.funcName().getText();
        LLVMValueRef function = tables.get(0).get(funcName);
        LLVMTypeRef funcType = LLVMTypeOf(function);
        LLVMTypeRef retType = LLVMGetReturnType(funcType);
        PointerPointer<LLVMValueRef> realParams = null;
        int numOfParams = 0;
        if (ctx.funcRParams() != null) {
            numOfParams = ctx.funcRParams().param().size();
            realParams = new PointerPointer<>(numOfParams);
            for (int i = 0; i < numOfParams; ++i) {
                LLVMValueRef param = visitParam(ctx.funcRParams().param(i));
                realParams.put(i, param);
            }
        }
        String callerName = (retTypeTable.get(funcName).equals("void")) ? "" : "retValue";
        return LLVMBuildCall2(builder, retType, function, realParams, numOfParams, callerName);
    }

    @Override
    public LLVMValueRef visitCondTwoWay(SysYParser.CondTwoWayContext ctx) {
        String op = ctx.op.getText();
        if (op.equals("&&") || op.equals("||")) {
            andOr = true;
        }
        LLVMValueRef lhs = visit(ctx.lhs);
        LLVMValueRef rhs = null; // short circuit evaluation
        LLVMValueRef res = null;
        if (op.equals("&&") || op.equals("||")) {
            if (LLVMTypeOf(lhs).equals(LLVMInt32Type())) {
                lhs = LLVMBuildICmp(builder, LLVMIntNE, lhs, zero, "lhs");
            }
            LLVMBasicBlockRef lhsTrueBlock = LLVMAppendBasicBlock(currentFunction, "lhsTrue");
            LLVMBasicBlockRef lhsFalseBlock = LLVMAppendBasicBlock(currentFunction, "lhsFalse");
            LLVMBasicBlockRef condEndBlock = LLVMAppendBasicBlock(currentFunction, "condEnd");

            // OR
            /* (condStart:)
             * visit lhs ...
             * br (lhsValue) ? lhsTrue : lhsFalse
             * (lhsFalse:)
             * visit rhs ...
             * res = or lhsValue rhsValue
             * br condEnd
             * (lhsTrue:)
             * res = lhsValue
             * br condEnd
             * (condEnd:)
             */
            if (op.equals("||")) {
                // lhsStart: (we have visited lhs before)
                LLVMBuildCondBr(builder, lhs, lhsTrueBlock, lhsFalseBlock);
                // lhsFalse:
                LLVMPositionBuilderAtEnd(builder, lhsFalseBlock);
                rhs = visit(ctx.rhs);
                if (LLVMTypeOf(rhs).equals(LLVMInt32Type())) {
                    rhs = LLVMBuildICmp(builder, LLVMIntNE, rhs, zero, "rhs");
                }
                res = LLVMBuildOr(builder, lhs, rhs, "res");
                LLVMBuildBr(builder, condEndBlock);
                // lhsTrue: (res = true)
                LLVMPositionBuilderAtEnd(builder, lhsTrueBlock);
                LLVMBuildBr(builder, condEndBlock);
                // condEnd:
                LLVMPositionBuilderAtEnd(builder, condEndBlock);
            }

            // AND
            /* (condStart:)
             * visit lhs ...
             * br (lhsValue) ? lhsTrue : lhsFalse
             * (lhsTrue:)
             * visit rhs ...
             * res = and lhsValue rhsValue
             * br condEnd
             * (lhsFalse:)
             * res = lhsValue
             * br condEnd
             * (condEnd:)
             */
            else {
                // condStart: (we have visited lhs before
                LLVMBuildCondBr(builder, lhs, lhsTrueBlock, lhsFalseBlock);
                // lhsTrue:
                LLVMPositionBuilderAtEnd(builder, lhsTrueBlock);
                rhs = visit(ctx.rhs);
                if (LLVMTypeOf(rhs).equals(LLVMInt32Type())) {
                    rhs = LLVMBuildICmp(builder, LLVMIntNE, rhs, zero, "rhs");
                }
                res = LLVMBuildAnd(builder, lhs, rhs, "res");
                LLVMBuildBr(builder, condEndBlock);
                // lhsFalse: (res = false)
                LLVMPositionBuilderAtEnd(builder, lhsFalseBlock);
                LLVMBuildBr(builder, condEndBlock);
                // condEnd:
                LLVMPositionBuilderAtEnd(builder, condEndBlock);
            }
        } else {
            rhs = visit(ctx.rhs);
            rhs = LLVMBuildZExt(builder, rhs, i32Type, "rhs");
            lhs = LLVMBuildZExt(builder, lhs, i32Type, "lhs");
            switch (op) {
                case ">":
                    res = LLVMBuildICmp(builder, LLVMIntSGT, lhs, rhs, "res");
                    break;
                case ">=":
                    res = LLVMBuildICmp(builder, LLVMIntSGE, lhs, rhs, "res");
                    break;
                case "<":
                    res = LLVMBuildICmp(builder, LLVMIntSLT, lhs, rhs, "res");
                    break;
                case "<=":
                    res = LLVMBuildICmp(builder, LLVMIntSLE, lhs, rhs, "res");
                    break;
                case "==":
                    res = LLVMBuildICmp(builder, LLVMIntEQ, lhs, rhs, "res");
                    break;
                case "!=":
                    res = LLVMBuildICmp(builder, LLVMIntNE, lhs, rhs, "res");
                    break;
                default:
                    break;
            }
        }
        return res;
    }

    @Override
    public LLVMValueRef visitIfStmt(SysYParser.IfStmtContext ctx) {
        LLVMBasicBlockRef endBlock = LLVMAppendBasicBlock(currentFunction, "end");
        LLVMBasicBlockRef trueBlock = LLVMAppendBasicBlock(currentFunction, "true");
        LLVMBasicBlockRef falseBlock = LLVMAppendBasicBlock(currentFunction, "false");
        currentTrue.push(trueBlock);
        currentFalse.push(falseBlock);

        andOr = false;
        LLVMValueRef condValue = visit(ctx.cond());
        // handle case where the cond in if(cond) is a single expression,
        // which we should turn the type of value from i32 to i1.
        // e.g., if(0)...
        if (ctx.cond().getClass().equals(SysYParser.CondExpContext.class)) {
            condValue = LLVMBuildICmp(builder, LLVMIntNE, condValue, zero, "cond");
        }

        LLVMBuildCondBr(builder, condValue, trueBlock, falseBlock);

        // true:
        LLVMPositionBuilderAtEnd(builder, trueBlock);
        LLVMValueRef stmtValue = visit(ctx.stmt(0));
        LLVMBuildBr(builder, endBlock);
        // false:
        LLVMPositionBuilderAtEnd(builder, falseBlock);
        if (ctx.ELSE() != null) {
            stmtValue = visit(ctx.stmt(1));
        }
        LLVMBuildBr(builder, endBlock);
        // end:
        LLVMPositionBuilderAtEnd(builder, endBlock);

        currentTrue.pop();
        currentFalse.pop();
        return stmtValue;
    }

    @Override
    public LLVMValueRef visitWhileStmt(SysYParser.WhileStmtContext ctx) {
        LLVMBasicBlockRef whileCondBlock = LLVMAppendBasicBlock(currentFunction, "whileCond");
        LLVMBasicBlockRef whileBodyBlock = LLVMAppendBasicBlock(currentFunction, "whileBody");
        LLVMBasicBlockRef whileEndBlock = LLVMAppendBasicBlock(currentFunction, "whileEnd");
        currentWhileCond.push(whileCondBlock);
        currentWhileEnd.push(whileEndBlock);
        currentTrue.push(whileBodyBlock);
        currentFalse.push(whileEndBlock);

        LLVMBuildBr(builder, whileCondBlock);
        LLVMPositionBuilderAtEnd(builder, whileCondBlock);
        andOr = false;
        LLVMValueRef condValue = visit(ctx.cond());
        // handle case where the cond in if(cond) is a single expression,
        // which we should turn the type of value from i32 to i1.
        // e.g., if(0)...
        if (ctx.cond().getClass().equals(SysYParser.CondExpContext.class)) {
            condValue = LLVMBuildICmp(builder, LLVMIntNE, condValue, zero, "cond");
        }

        LLVMBuildCondBr(builder, condValue, whileBodyBlock, whileEndBlock);

        LLVMPositionBuilderAtEnd(builder, whileBodyBlock);
        LLVMValueRef stmtValue = visit(ctx.stmt());
        LLVMBuildBr(builder, whileCondBlock);

        LLVMPositionBuilderAtEnd(builder, whileEndBlock);
        currentWhileCond.pop();
        currentWhileEnd.pop();
        currentTrue.pop();
        currentFalse.pop();
        return stmtValue;
    }

    @Override
    public LLVMValueRef visitContinueStmt(SysYParser.ContinueStmtContext ctx) {
        LLVMBuildBr(builder, currentWhileCond.peek());
        return null;
    }

    @Override
    public LLVMValueRef visitBreakStmt(SysYParser.BreakStmtContext ctx) {
        LLVMBuildBr(builder, currentWhileEnd.peek());
        return super.visitBreakStmt(ctx);
    }
}
