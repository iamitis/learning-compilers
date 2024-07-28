public class VarPlace {
    private String name;
    private boolean inReg;
    private String reg;
    private int stackOffset;

    public VarPlace(String name) {
        this.name = name;
        this.inReg = false;
        this.reg = null;
        this.stackOffset = -1;
    }

    public boolean isInReg() {
        return inReg;
    }

    public void setReg(String reg) {
        this.inReg = true;
        this.reg = reg;
    }

    public void setStackOffset(int offset) {
        this.inReg = false;
        this.stackOffset = offset;
    }

    public String getReg() {
        return reg;
    }

    public int getStackOffset() {
        return stackOffset;
    }
}
