import org.bytedeco.llvm.LLVM.*;

import java.io.IOException;

import static org.bytedeco.llvm.global.LLVM.*;

public class LRScanner {
    private final LLVMModuleRef module;
    private LLVMValueRef currentFunc;
    private RegisterAllocationStrategy raStrategy;
    private final AsmBuilder asmBuilder = new AsmBuilder();
    private final String sysCodeOfRet = "93";
    private boolean isXor = false;

    public LRScanner(LLVMModuleRef module) {
        this.module = module;
    }

    public void scan() {
        scanGlbVar();

        // .text starts
        asmBuilder.appendString("  .text");
        asmBuilder.appendString("  .globl main");

        // main function starts
        LLVMValueRef main = LLVMGetFirstFunction(module);
        this.currentFunc = main;
        asmBuilder.genLabel("main");
        raStrategy = new LinearScanStrategy(main, asmBuilder);

        // initiate sp
        asmBuilder.appendString("addi " + RISCVRegister.STACK_TOP + ", " + RISCVRegister.STACK_TOP + ", -   ");

        // basic block starts
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(main); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            String bbName = LLVMGetBasicBlockName(bb).getString();
            asmBuilder.genLabel(bbName);
            // go through instructions
            for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                scanInst(inst);
            }
        }
    }

    public void outputToFile(String file) throws IOException {
        asmBuilder.outputToFile(file);
    }

    private void scanGlbVar() {
        for (LLVMValueRef glbVar = LLVMGetFirstGlobal(module); glbVar != null; glbVar = LLVMGetNextGlobal(glbVar)) {
            asmBuilder.appendString("  .data");
            String[] nameList = LLVMPrintValueToString(glbVar).getString().split(" ");
            int len = nameList.length;
            String value = nameList[len - 1];
            String name = getGlbName(glbVar);
            asmBuilder.genLabel(name);
            asmBuilder.appendString("  .word " + value);
        }
    }

    private void scanInst(LLVMValueRef inst) {
        instMsg(inst);
        int opcode = LLVMGetInstructionOpcode(inst);
        switch (opcode) {
            case LLVMRet:
                genRet(inst);
                break;
            case LLVMAdd:
            case LLVMSub:
            case LLVMMul:
            case LLVMSDiv:
                genCalc2(inst);
                break;
            case LLVMXor:
                isXor = true;
                genCalc2(inst);
                break;
            case LLVMICmp:
                genICmp(inst);
                break;
            case LLVMAlloca:
                genAlloca(inst);
                break;
            case LLVMStore:
                genStore(inst);
                break;
            case LLVMLoad:
                genLoad(inst);
                break;
            case LLVMZExt:
                genZExt(inst);
            default:
                break;
        }
    }

    /**
     * li a0, retVal <br>
     * add sp, sp, -stackSize <br>
     * li a7, 93 <br>
     * ecall <br>
     */
    private void genRet(LLVMValueRef retInst) {
        assert LLVMGetNumOperands(retInst) == 1;
        // load return value
        if (LLVMGetNumOperands(retInst) == 0) {
            asmBuilder.genOp2(RISCVOpcode.LI, RISCVRegister.OP1_REG, "0");
        } else {
            LLVMValueRef op1 = LLVMGetOperand(retInst, 0);
            prepareOpReg(op1, 1);
        }
        // return
        int stackSize = raStrategy.getStackSize(currentFunc);
        stackSize = (stackSize + 15) / 16 * 16;
        asmBuilder.setSP(String.valueOf(stackSize));
        asmBuilder.genOp2(RISCVOpcode.MV, RISCVRegister.RET_VAL, RISCVRegister.OP1_REG);
        asmBuilder.appendString("addi " + RISCVRegister.STACK_TOP + ", " + RISCVRegister.STACK_TOP + ", " + stackSize);
        asmBuilder.genOp2(RISCVOpcode.LI, RISCVRegister.SYSCALL, sysCodeOfRet);
        asmBuilder.genEcall();
    }

    private void genCalc2(LLVMValueRef calc2Inst) {
        assert LLVMGetNumOperands(calc2Inst) == 2;
        // calc
        LLVMValueRef op1 = LLVMGetOperand(calc2Inst, 0);
        LLVMValueRef op2 = LLVMGetOperand(calc2Inst, 1);
        prepareOpReg(op1, 1);
        prepareOpReg(op2, 2);
        int opcode = LLVMGetInstructionOpcode(calc2Inst);
        String riscvOp = mapOpcode(opcode);
        asmBuilder.genOp3(riscvOp, RISCVRegister.TEMP_REG, RISCVRegister.OP1_REG, RISCVRegister.OP2_REG);
        // allocate and store result
        String resName = LLVMPrintValueToString(calc2Inst).getString();
        raStrategy.allocate(resName);
        VarPlace resPlace = raStrategy.find(resName);
        if (resPlace.isInReg()) {
            String reg = resPlace.getReg();
            asmBuilder.genOp2(RISCVOpcode.MV, reg, RISCVRegister.TEMP_REG);
        } else {
            int offset = resPlace.getStackOffset();
            asmBuilder.genOp2(RISCVOpcode.SW, RISCVRegister.TEMP_REG, offset + "(" + RISCVRegister.STACK_TOP + ")");
        }
    }

    private void genICmp(LLVMValueRef icmpInst) {
        // icmp
        LLVMValueRef op1 = LLVMGetOperand(icmpInst, 0);
        prepareOpReg(op1, 1);
        int opcode = LLVMGetInstructionOpcode(icmpInst);
        String riscvOp = mapOpcode(opcode);
        asmBuilder.genOp2(riscvOp, RISCVRegister.TEMP_REG, RISCVRegister.OP1_REG);
        // allocate and store result
        String resName = LLVMPrintValueToString(icmpInst).getString();
        raStrategy.allocate(resName);
        VarPlace resPlace = raStrategy.find(resName);
        if (resPlace.isInReg()) {
            String reg = resPlace.getReg();
            asmBuilder.genOp2(RISCVOpcode.MV, reg, RISCVRegister.TEMP_REG);
        } else {
            int offset = resPlace.getStackOffset();
            asmBuilder.genOp2(RISCVOpcode.SW, RISCVRegister.TEMP_REG, offset + "(" + RISCVRegister.STACK_TOP + ")");
        }
    }

    private void genZExt(LLVMValueRef zextInst) {
        LLVMValueRef op1 = LLVMGetOperand(zextInst, 0);
        prepareOpReg(op1, 1);
        // allocate and store result
        String resName = LLVMPrintValueToString(zextInst).getString();
        raStrategy.allocate(resName);
        VarPlace resPlace = raStrategy.find(resName);
        if (resPlace.isInReg()) {
            String reg = resPlace.getReg();
            asmBuilder.genOp2(RISCVOpcode.MV, reg, RISCVRegister.OP1_REG);
        } else {
            int offset = resPlace.getStackOffset();
            asmBuilder.genOp2(RISCVOpcode.SW, RISCVRegister.OP1_REG, offset + "(" + RISCVRegister.STACK_TOP + ")");
        }
    }

    private void genAlloca(LLVMValueRef allocaInst) {
        String varName = LLVMPrintValueToString(allocaInst).getString();
        raStrategy.allocate(varName);
        VarPlace place = raStrategy.find(varName);
        if (place.isInReg()) {
            String reg = place.getReg();
            asmBuilder.genOp2(RISCVOpcode.LI, reg, "1");
        } else {
            int offset = place.getStackOffset();
            asmBuilder.genOp2(RISCVOpcode.LI, RISCVRegister.TEMP_REG, "1");
            asmBuilder.genOp2(RISCVOpcode.SW, RISCVRegister.TEMP_REG, offset + "(" + RISCVRegister.STACK_TOP + ")");
        }
    }

    private void genStore(LLVMValueRef storeInst) {
        assert LLVMGetNumOperands(storeInst) == 2;
        // `store op1, op2` : `store value, dest`
        LLVMValueRef op1 = LLVMGetOperand(storeInst, 0);
        LLVMValueRef op2 = LLVMGetOperand(storeInst, 1);
        // load value (op1)
        prepareOpReg(op1, 1);
        // allocate for dest var (op2)
        if (typeOf(op2).equals("glb")) {
            asmBuilder.genOp2(RISCVOpcode.LA, RISCVRegister.ADDR_REG, getGlbName(op2));
            asmBuilder.genOp2(RISCVOpcode.SW, RISCVRegister.OP1_REG, "0(" + RISCVRegister.ADDR_REG + ")");
            return;
        }
        String dest = LLVMPrintValueToString(op2).getString();
        // store
        VarPlace destPlace = raStrategy.find(dest);
        if (destPlace.isInReg()) {
            String reg = destPlace.getReg();
            asmBuilder.genOp2(RISCVOpcode.MV, reg, RISCVRegister.OP1_REG);
        } else {
            int offset = destPlace.getStackOffset();
            asmBuilder.genOp2(RISCVOpcode.SW, RISCVRegister.OP1_REG, offset + "(" + RISCVRegister.STACK_TOP + ")");
        }
    }

    private void genLoad(LLVMValueRef loadInst) {
        assert LLVMGetNumOperands(loadInst) == 1;
        // load source
        LLVMValueRef src = LLVMGetOperand(loadInst, 0);
        prepareOpReg(src, 1);
        // allocate for dest var
        String destName = LLVMPrintValueToString(loadInst).getString();
        raStrategy.allocate(destName);
        // store
        VarPlace destPlace = raStrategy.find(destName);
        if (destPlace.isInReg()) {
            String reg = destPlace.getReg();
            asmBuilder.genOp2(RISCVOpcode.MV, reg, RISCVRegister.OP1_REG);
        } else {
            int offset = destPlace.getStackOffset();
            asmBuilder.genOp2(RISCVOpcode.LW, RISCVRegister.OP1_REG, offset + "(" + RISCVRegister.STACK_TOP + ")");
        }
    }

    private String typeOf(LLVMValueRef operand) {
        String var = LLVMPrintValueToString(operand).getString();
        if (var.startsWith("@")) {
            return "glb";
        }
        if (var.startsWith("  %")) {
            return "local";
        }
        return "const";
    }

    private String getGlbName(LLVMValueRef var) {
        return LLVMPrintValueToString(var).getString()
                .strip()
                .split(" ")[0]
                .substring(1);
    }

    private void prepareOpReg(LLVMValueRef op, int opNO) {
        String opReg = opNO == 1 ? RISCVRegister.OP1_REG : RISCVRegister.OP2_REG;
        if (typeOf(op).equals("const")) {
            long op1Val = LLVMConstIntGetSExtValue(op);
            if (isXor && opNO == 2) {
                op1Val = 1;
                isXor = false;
            }
            asmBuilder.genOp2(RISCVOpcode.LI, opReg, String.valueOf(op1Val));
        } else if (typeOf(op).equals("glb")) {
            asmBuilder.genOp2(RISCVOpcode.LA, RISCVRegister.ADDR_REG, getGlbName(op));
            asmBuilder.genOp2(RISCVOpcode.LW, opReg, "0(" + RISCVRegister.ADDR_REG + ")");
        } else if (typeOf(op).equals("local")) {
            // 1. find where the local var is stored: register or stack
            // 2a. `mv <opReg>, <register>`
            // 2b. `lw <opReg>, <offset>(sp)`
            String opName = LLVMPrintValueToString(op).getString();
            VarPlace place = raStrategy.find(opName);
            if (place.isInReg()) {
                String reg = place.getReg();
                asmBuilder.genOp2(RISCVOpcode.MV, opReg, reg);
            } else {
                int offset = place.getStackOffset();
                asmBuilder.genOp2(RISCVOpcode.LW, opReg, offset + "(" + RISCVRegister.STACK_TOP + ")");
            }
        }
    }

    private String mapOpcode(int opcode) {
        switch (opcode) {
            case LLVMAdd:
                return RISCVOpcode.ADD;
            case LLVMSub:
                return RISCVOpcode.SUB;
            case LLVMMul:
                return RISCVOpcode.MUL;
            case LLVMSDiv:
                return RISCVOpcode.DIV;
            case LLVMXor:
                return RISCVOpcode.XOR;
            case LLVMICmp:
                return RISCVOpcode.SNEZ;
            default:
                return null;
        }
    }

    private void instMsg(LLVMValueRef inst) {
        System.out.println("instr: " + LLVMPrintValueToString(inst).getString());
        System.out.println("opcode: " + LLVMGetInstructionOpcode(inst) + " opnum: " + LLVMGetNumOperands(inst));
        for (int i = 0; i < LLVMGetNumOperands(inst); ++i) {
            System.out.println("operand" + (i + 1) + ": " + LLVMPrintValueToString(LLVMGetOperand(inst, i)).getString());
        }
        System.out.println();
    }
}
