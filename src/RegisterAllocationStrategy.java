import org.bytedeco.llvm.LLVM.LLVMValueRef;

public interface RegisterAllocationStrategy {
    public void allocate(String var);
    public int getStackSize(LLVMValueRef func);
    public VarPlace find(String var);
}
