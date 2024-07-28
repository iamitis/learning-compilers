package scope;

import symbol.Symbol;

import java.util.Map;

public interface Scope {
    Map<String, Symbol> getSymbols();

    Scope getParentScope();

    int define(Symbol symbol);

    Symbol resolve(String name);
}
