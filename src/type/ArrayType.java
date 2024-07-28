package type;

public class ArrayType implements Type {
    private final int elementNum;
    private final int dimension;
    private final Type elementType;

    public ArrayType(int dimension, int elementNum, Type elementType) {
        this.dimension = dimension;
        this.elementNum = elementNum;
        this.elementType = elementType;
    }

    public String toString() {
        StringBuilder typeStr = new StringBuilder();
        if (elementNum == 0) {
            typeStr.append(elementType);
        } else {
            typeStr.append("(")
                    .append(elementNum)
                    .append(",")
                    .append(elementType.toString())
                    .append(")");
        }
        return typeStr.toString();
    }

    public Type getElementType() {
        return this.elementType;
    }

    public int getDimension() {
        return this.dimension;
    }

    public int getElementNum() {
        return this.elementNum;
    }
}
