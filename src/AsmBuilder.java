import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class AsmBuilder {
    private final StringBuilder instructions = new StringBuilder();

    /**
     * "label:"
     * e.g. "mainEntry:"
     */
    public void genLabel(String labelName) {
        instructions
                .append(labelName)
                .append(":")
                .append(System.lineSeparator());
    }

    /**
     * "opcode dest, op1"<br>
     * e.g. "li t0, 1"
     */
    public void genOp2(String opcode, String op1, String op2) {
        instructions
                .append("  ")
                .append(opcode)
                .append(" ")
                .append(op1)
                .append(", ")
                .append(op2)
                .append(System.lineSeparator());
    }

    /**
     * "opcode dest, op1, op2"
     * e.g. "add t0, t0, t1"
     */
    public void genOp3(String opcode, String op1, String op2, String op3) {
        instructions
                .append("  ")
                .append(opcode)
                .append(" ")
                .append(op1)
                .append(", ")
                .append(op2)
                .append(", ")
                .append(op3)
                .append(System.lineSeparator());
    }

    // "ecall"
    public void genEcall() {
        instructions
                .append("  ")
                .append("ecall")
                .append(System.lineSeparator());
    }

    public void appendString(String s) {
        instructions.append(s).append(System.lineSeparator());
    }

    public void outputToFile(String file) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file));
            bw.write(instructions.toString());
            bw.flush();
            bw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void setSP(String stackSize) {
        int spIndex = instructions.indexOf("sp");
        if (stackSize.equals("0")) {
            instructions.replace(spIndex + 8, spIndex + 9, "0");
            return;
        }
        int len = stackSize.length();
        if (len == 1) {
            instructions.replace(spIndex + 9, spIndex + 10, stackSize);
        } else if (len == 2) {
            instructions.replace(spIndex + 9, spIndex + 11, stackSize);
        } else if (len == 3) {
            instructions.replace(spIndex + 9, spIndex + 12, stackSize);
        } else {
            throw new RuntimeException("Invalid stack size");
        }
    }
}
