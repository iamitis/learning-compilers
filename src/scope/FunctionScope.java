package scope;

import symbol.BaseSymbol;
import symbol.Symbol;
import type.Type;

public class FunctionScope extends BaseScope {
    private final BaseSymbol functionSymbol;

    public FunctionScope(BaseSymbol functionSymbol, Scope parentScope) {
        super(parentScope);
        this.functionSymbol = functionSymbol;
    }

    public BaseSymbol getFunctionSymbol() {
        return this.functionSymbol;
    }
}
