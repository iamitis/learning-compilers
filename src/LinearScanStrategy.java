import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class LinearScanStrategy implements RegisterAllocationStrategy {
    private final List<Interval> intervals;
    private final List<Interval> inRegIntervals;
    private final List<Boolean> used;
    private final Map<String, VarPlace> varPlaces;
    private int stackVarNum;
    private final AsmBuilder asmBuilder;

    public LinearScanStrategy(LLVMValueRef func, AsmBuilder asmBuilder) {
        this.asmBuilder = asmBuilder;
        intervals = new ArrayList<>();
        inRegIntervals = new ArrayList<>();
        varPlaces = new HashMap<>();
        used = new ArrayList<>(RISCVRegister.GPRegsNum);
        stackVarNum = 0;

        initUsed();
        getIntervals(func);
    }

    @Override
    public void allocate(String var) {
        VarPlace vp = new VarPlace(var);
        varPlaces.put(var, vp);

        Interval varInterval = findInterval(var);
        assert varInterval != null;
        expireOldInterval(varInterval);
        if (inRegIntervals.size() < RISCVRegister.GPRegsNum) {
            for (int i = 0; i < RISCVRegister.GPRegsNum; ++i) {
                if (!used.get(i)) {
                    used.set(i, true);
                    inRegIntervals.add(varInterval);
                    vp.setReg(RISCVRegister.GPRegs.get(i));
                    break;
                }
            }
        } else {
            ++stackVarNum;
            spillAtInterval(varInterval);
        }
    }

    @Override
    public int getStackSize(LLVMValueRef func) {
        return stackVarNum * 4;
    }

    @Override
    public VarPlace find(String var) {
        return varPlaces.get(var);
    }

    private Interval findInterval(String var) {
        for (Interval interval : intervals) {
            if (interval.getName().equals(var)) {
                return interval;
            }
        }
        return null;
    }

    private void getIntervals(LLVMValueRef func) {
        int lineNum = 0;
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                int opcode = LLVMGetInstructionOpcode(inst);
                String newVarName = null;
                String oldVarName = null;
                switch (opcode) {
                    case LLVMAlloca:
                        newVarName = LLVMPrintValueToString(inst).getString();
                        checkStartPoint(newVarName, lineNum);
                        break;
                    case LLVMLoad:
                    case LLVMICmp:
                    case LLVMZExt:
                        newVarName = LLVMPrintValueToString(inst).getString();
                        checkStartPoint(newVarName, lineNum);
                        oldVarName = LLVMPrintValueToString(LLVMGetOperand(inst, 0)).getString();
                        checkEndPoint(oldVarName, lineNum);
                        break;
                    case LLVMStore:
                        oldVarName = LLVMPrintValueToString(LLVMGetOperand(inst, 0)).getString();
                        checkEndPoint(oldVarName, lineNum);
                        oldVarName = LLVMPrintValueToString(LLVMGetOperand(inst, 1)).getString();
                        checkEndPoint(oldVarName, lineNum);
                        break;
                    case LLVMAdd:
                    case LLVMSub:
                    case LLVMMul:
                    case LLVMSDiv:
                    case LLVMXor:
                        newVarName = LLVMPrintValueToString(inst).getString();
                        checkStartPoint(newVarName, lineNum);
                        oldVarName = LLVMPrintValueToString(LLVMGetOperand(inst, 0)).getString();
                        checkEndPoint(oldVarName, lineNum);
                        oldVarName = LLVMPrintValueToString(LLVMGetOperand(inst, 1)).getString();
                        checkEndPoint(oldVarName, lineNum);
                        break;
                    default:
                        break;
                }
                ++lineNum;
            }
        }
    }

    private void checkStartPoint(String newVarName, int lineNum) {
        Interval interval = new Interval(newVarName);
        interval.setStart(lineNum);
        interval.setEnd(lineNum);
        intervals.add(interval);
    }

    private void checkEndPoint(String oldVarName, int lineNum) {
        for (Interval interval : intervals) {
            if (interval.getName().equals(oldVarName)) {
                interval.setEnd(lineNum);
                break;
            }
        }
    }

    private void expireOldInterval(Interval varInterval) {
        inRegIntervals.sort(Comparator.comparingInt(Interval::getEnd));
        int shouldExpire = -1;
        for (int i = 0; i < inRegIntervals.size(); ++i) {
            if (inRegIntervals.get(i).getEnd() >= varInterval.getStart()) {
                shouldExpire = i;
                break;
            }
            String oldName = inRegIntervals.get(i).getName();
            VarPlace oldVP = varPlaces.get(oldName);
            String oldReg = oldVP.getReg();
            int oldRegIndex = RISCVRegister.GPRegs.indexOf(oldReg);
            used.set(oldRegIndex, false);
            shouldExpire = i + 1;
        }
        if (shouldExpire > 0) {
            inRegIntervals.subList(0, shouldExpire).clear();
        }
    }

    private void spillAtInterval(Interval varInterval) {
        VarPlace vp = varPlaces.get(varInterval.getName());

        inRegIntervals.sort(Comparator.comparingInt(Interval::getEnd));
        Interval spillInterval = inRegIntervals.get(inRegIntervals.size() - 1);
        if (spillInterval.getEnd() > varInterval.getEnd()) {
            // spill -> stack; remove from inRegIntervals
            VarPlace spillVP = varPlaces.get(spillInterval.getName());
            String spillReg = spillVP.getReg();
            spillVP.setStackOffset(stackVarNum * 4);
            inRegIntervals.remove(spillInterval);
            // `sw <spillReg>, (stackVarNum * 4)(sp)`
            asmBuilder.genOp2(RISCVOpcode.SW, spillReg, (stackVarNum * 4) + "(" + RISCVRegister.STACK_TOP + ")");
            // var -> reg; add varInterval to inRegIntervals
            vp.setReg(spillReg);
            inRegIntervals.add(varInterval);
        } else {
            // var -> stack
            vp.setStackOffset(stackVarNum * 4);
        }
    }

    private void initUsed() {
        for (int i = 0; i < RISCVRegister.GPRegsNum; ++i) {
            used.add(false);
        }
    }

    private static class Interval {
        private final String name;
        private int start;
        private int end;

        public Interval(String name) {
            this.name = name;
            this.start = -1;
            this.end = -1;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public void setEnd(int end) {
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public String getName() {
            return name;
        }
    }
}
