parser grammar SysYParser;

options {
    tokenVocab = SysYLexer;
}

program
    : compUnit
    ;

compUnit
    : (funcDef | decl)+ EOF
    ;

decl
    : constDecl | varDecl
    ;

constDecl
    : CONST INT constDef (COMMA constDef)* SEMICOLON
    ;

constDef
    : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal
    ;

constInitVal
    : constExp
    | L_BRACE (constInitVal (COMMA constInitVal)*)? R_BRACE
    ;

varDecl
    : INT varDef (COMMA varDef)* SEMICOLON
    ;

varDef
    : IDENT (L_BRACKT constExp R_BRACKT)*
    | IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN initVal
    ;

initVal
    : exp
    | L_BRACE (initVal (COMMA initVal)*)? R_BRACE
    ;

funcDef
    : funcType funcName L_PAREN (funcFParams)? R_PAREN block
    ;

funcType
    : VOID
    | INT
    ;

funcFParams
    : funcFParam (COMMA funcFParams)*
    ;

funcFParam
    : INT IDENT (L_BRACKT R_BRACKT (L_BRACKT exp R_BRACKT)*)?
    ;

block
    : L_BRACE (blockItem)* R_BRACE
    ;

blockItem
    : decl
    | stmt
    ;

stmt
    : lVal ASSIGN exp SEMICOLON                 # assignStmt
    | (exp)? SEMICOLON                          # expStmt
    | block                                     # blockStmt
    | IF L_PAREN cond R_PAREN stmt (ELSE stmt)? # ifStmt
    | WHILE L_PAREN cond R_PAREN stmt           # whileStmt
    | BREAK SEMICOLON                           # breakStmt
    | CONTINUE SEMICOLON                        # continueStmt
    | RETURN (exp)? SEMICOLON                   # retStmt
    ;

exp
   : L_PAREN exp R_PAREN                    # parenExp
   | lVal                                   # lValExp
   | number                                 # numberExp
   | funcName L_PAREN funcRParams? R_PAREN  # funcCall
   | unaryOp exp                            # oneWay
   | lhs=exp op=(MUL | DIV | MOD) rhs=exp           # expTwoWay
   | lhs=exp op=(PLUS | MINUS) rhs=exp              # expTwoWay
   ;

cond
   : exp                            # condExp
   | lhs=cond op=(LT | GT | LE | GE) rhs=cond  # condTwoWay
   | lhs=cond op=(EQ | NEQ) rhs=cond           # condTwoWay
   | lhs=cond op=AND rhs=cond                  # condTwoWay
   | lhs=cond op=OR rhs=cond                   # condTwoWay
   ;

lVal
   : IDENT (L_BRACKT exp R_BRACKT)*
   ;

number
   : INTEGER_CONST
   ;

unaryOp
   : PLUS
   | MINUS
   | NOT
   ;

funcRParams
   : param (COMMA param)*
   ;

param
   : exp
   ;

constExp
   : exp
   ;

funcName
    : IDENT
    ;