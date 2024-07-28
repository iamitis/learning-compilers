import java.util.ArrayList;
import java.util.List;

public class RISCVRegister {
    public final static String SYSCALL = "a7";
    public final static String RET_VAL = "a0";
    public final static String STACK_TOP = "sp";
    public final static String TEMP_REG = "t0";
    public final static String OP1_REG = "t1";
    public final static String OP2_REG = "t2";
    public final static String ADDR_REG = "t3";

    public final static List<String> GPRegs = new ArrayList<>(
            List.of(
                    "t4", "t5", "t6",
                    "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11",
                    "a0", "a1", "a2", "a3", "a4", "a5", "a6"
            )
    );

    public final static int GPRegsNum = GPRegs.size();
}
