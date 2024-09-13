
package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{

	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline+1, yycolumn);
	}
	
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline+1, yycolumn, value);
	}

%}

%cup
%line
%column

%xstate COMMENT
%xstate LONG_COMMENT

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

" "    { }
"\t"   { }
"\r\n" { }
"\r"   { }
"\n"   { }
"\b"   { }
"\f"   { }

"program"   { return new_symbol(sym.PROGRAM, yytext());}
"print" 	{ return new_symbol(sym.PRINT, yytext()); }
"read"		{ return new_symbol(sym.READ, yytext()); }
"new"		{ return new_symbol(sym.NEW, yytext()); }
"const"		{ return new_symbol(sym.CONST, yytext()); }
"true"		{ return new_symbol(sym.TRUE, yytext()); }
"false"		{ return new_symbol(sym.FALSE, yytext()); }

"++" 		{ return new_symbol(sym.INC, yytext()); }
"--" 		{ return new_symbol(sym.DEC, yytext()); }

"+" 		{ return new_symbol(sym.ADD, yytext()); }
"-"			{ return new_symbol(sym.SUB, yytext()); }
"*"			{ return new_symbol(sym.MUL, yytext()); }
"/"			{ return new_symbol(sym.DIV, yytext()); }
"%"			{ return new_symbol(sym.MOD, yytext()); }
"=" 		{ return new_symbol(sym.ASSIGN, yytext()); }

";" 		{ return new_symbol(sym.SEMICOLON, yytext()); }
"," 		{ return new_symbol(sym.COMMA, yytext()); }

"(" 		{ return new_symbol(sym.LPAREN, yytext()); }
")" 		{ return new_symbol(sym.RPAREN, yytext()); }
"[" 		{ return new_symbol(sym.LSQUARE, yytext()); }
"]"			{ return new_symbol(sym.RSQUARE, yytext()); }
"{" 		{ return new_symbol(sym.LBRACE, yytext()); }
"}"			{ return new_symbol(sym.RBRACE, yytext()); }


"//" { yybegin(COMMENT); }
<COMMENT> . { yybegin(COMMENT); }
<COMMENT> "\n" { yybegin(YYINITIAL); }

"/*"  { yybegin(LONG_COMMENT); }
<LONG_COMMENT> . { yybegin(LONG_COMMENT); }
<LONG_COMMENT> "*/" { yybegin(YYINITIAL); }

[0-9]+  { return new_symbol(sym.NUMBER, Integer.valueOf(yytext())); }
'.' 	{return new_symbol (sym.CHAR, yytext()); }
([a-z]|[A-Z])[a-z|A-Z|0-9|_]* 	{return new_symbol (sym.IDENT, yytext()); }

. { System.err.println("Lexical error on line " + (yyline + 1) + ": Unknown character '" + yytext() + "'"); }