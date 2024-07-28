package scope;

import symbol.Symbol;
import type.Type;

import java.util.HashMap;
import java.util.Map;

public class BaseScope implements Scope {
    private final Scope parentScope;
    private final Map<String, Symbol> symbols = new HashMap<>();

    public BaseScope(Scope parentScope) {
        this.parentScope = parentScope;
    }

    public Scope getParentScope() {
        return this.parentScope;
    }

    public Map<String, Symbol> getSymbols() {
        return this.symbols;
    }

    /**
     * @return successful 0 otherwise 1
     */
    public int define(Symbol symbol) {
        if (symbols.containsKey(symbol.getName())) {
            return 1;
        }
        symbols.put(symbol.getName(), symbol);
        return 0;
    }

    public Symbol resolve(String name) {
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return symbol;
        }
        if (parentScope != null) {
            return parentScope.resolve(name);
        }
        return null;
    }
}
