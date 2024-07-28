package type;

import java.util.ArrayList;

public class FunctionType implements Type {
    private final Type returnType;
    private final ArrayList<Type> paramsType;
    public FunctionType(Type returnType, ArrayList<Type> paramsType) {
        this.returnType = returnType;
        this.paramsType = paramsType;
    }

    public Type getReturnType() {
        return this.returnType;
    }

    public ArrayList<Type> getParamsType() {
        return this.paramsType;
    }

    public String toString() {
        return this.returnType.toString();
    }
}
