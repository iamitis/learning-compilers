import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.llvm.global.LLVM.*;

public class NoStrategy implements RegisterAllocationStrategy {
    private int stackSize;
    private final Map<String, Integer> varTable = new HashMap<>();

    public NoStrategy() {
        this.stackSize = -1;
    }

    @Override
    public void allocate(String var) {
    }

    @Override
    public int getStackSize(LLVMValueRef func) {
        if (stackSize == -1) {
            calcStackSize(func);
        }
        return this.stackSize;
    }

    /**
     * Go through all instruction.<br>
     * If the opcode is alloc or load, add the dest to var-table:
     * varTable.set(dest, varNum * 4)
     */
    private void calcStackSize(LLVMValueRef func) {
        int varNum = 0;
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                int opcode = LLVMGetInstructionOpcode(inst);
                switch (opcode) {
                    case LLVMAlloca:
                    case LLVMLoad:
                    case LLVMAdd:
                    case LLVMMul:
                    case LLVMSDiv:
                        String dest = LLVMPrintValueToString(inst).getString();
                        varTable.put(dest, varNum++);
                        break;
                    default:
                        break;
                }
            }
        }
        this.stackSize = varNum * 4;
    }

    @Override
    public VarPlace find(String var) {
        return null;
    }
}
