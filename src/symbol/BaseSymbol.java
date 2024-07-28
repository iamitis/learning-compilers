package symbol;

import type.Type;

public class BaseSymbol implements Symbol {
    final String symbolName;
    final Type symbolType;

    public BaseSymbol(String symbolName, Type symbolType) {
        this.symbolName = symbolName;
        this.symbolType = symbolType;
    }

    public String getName() {
        return this.symbolName;
    }

    public Type getType() {
        return this.symbolType;
    }
}
