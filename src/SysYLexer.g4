lexer grammar SysYLexer;

CONST
    : 'const' ;

INT
    : 'int' ;

VOID
    : 'void' ;

IF
    : 'if' ;

ELSE
    : 'else' ;

WHILE
    : 'while' ;

BREAK
    : 'break' ;

CONTINUE
    : 'continue' ;

RETURN
    : 'return' ;

PLUS
    : '+' ;

MINUS
    : '-' ;

MUL
    : '*' ;

DIV
    : '/' ;

MOD
    : '%' ;

ASSIGN
    : '=' ;

EQ
    : '==' ;

NEQ
    : '!=' ;

LT
    : '<' ;

GT
    : '>' ;

LE
    : '<=' ;

GE
    : '>=' ;

NOT
    : '!' ;

AND
	: '&&' ;

OR
	: '||' ;

L_PAREN
	: '(' ;

R_PAREN
	: ')' ;

L_BRACE
	: '{' ;

R_BRACE
	: '}' ;

L_BRACKT
	: '[' ;

R_BRACKT
	: ']' ;

COMMA
	: ',' ;

SEMICOLON
	: ';' ;

IDENT
    : (Letter|Underline) (Letter|Underline|Digit)* ;

INTEGER_CONST
    : DecimalConstant
    | OctalConstant
    | HexadecimalConstant
    ;

fragment Letter
    : [a-zA-Z] ;

fragment Underline
    : '_' ;

fragment Digit
    : [0-9] ;

fragment DecimalConstant
    : NonzeroDigit Digit* ;

fragment NonzeroDigit
    : [1-9] ;

fragment OctalConstant
    : Zero OctalDigit* ;

fragment Zero
    : '0' ;

fragment OctalDigit
    : [0-7] ;

fragment HexadecimalConstant
    : HexadecimalPrefix HexadecimalDigit+ ;

fragment HexadecimalPrefix
    : Zero [xX] ;

fragment HexadecimalDigit
    : [0-9a-fA-F] ;

WS
    : [ \r\n\t]+ -> skip ;

LINE_COMMENT
    : '//' .*? '\n' -> skip;

MULTILINE_COMMENT
    : '/*' .*? '*/' -> skip;