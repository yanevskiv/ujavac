package rs.ac.bg.etf.pp1;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	// Logger
	private static final Logger logger = Logger.getLogger(SemanticAnalyzer.class);

	// Structs
	public static final Struct VoidType = new Struct(Struct.None);
	public static final Struct BoolType = new Struct(Struct.Bool);
	public static final Struct IntType = Tab.intType;
	public static final Struct CharType = Tab.charType;

	// Typename map
	private final Map<Struct, String> typeNameMap = new HashMap<>();

	// Var/error count
	public int nVars = 0;
	public static Map<Obj, Integer> nLocalVars = new HashMap<>();
	private int nErrors = 0;
	private int nPrints = 0;

	private int adrConst = 0;
	private int adrMethod = 0;
	private Obj currMethod = null;

	// Get nVars
	public int getnVars() {
		return nVars;
	}

	// Get nErrors
	public int getnErrors() {
		return nErrors;
	}

	// Get nPrints
	public int getnPrints() {
		return nPrints;
	}

	// Show error
	private void showError(String message, SyntaxNode info) {
		nErrors++;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0) {
			msg.append(" on line  ").append(line);
		}
		logger.error(msg.toString());
	}

	// Show info
	public void showInfo(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0) {
			msg.append(" on line ").append(line);
		}
		logger.info(msg.toString());
	}

	// Show visit info
	public void showVisitInfo(SyntaxNode info) {
		showInfo("Visiting " + info.getClass().getSimpleName(), info);
	}

	// Insert a type to symbol table
	private Obj insertType(String name, Struct typeStruct) {
		logger.info("Adding a new type " + name);

		Obj typeObj = Tab.insert(Obj.Type, name, typeStruct);
		typeNameMap.put(typeObj.getType(), name);

		return typeObj;
	}

	// Type name to struct
	private Struct typeNameToStruct(String name) throws Exception {
		Obj typeObj = Tab.find(name);
		
		// Check if type exists
		if (typeObj == Tab.noObj) {
			if ("void".equals(name)) {
				typeObj = insertType(name, VoidType);
				return typeObj.getType();
			} else if ("bool".equals(name)) {
				typeObj = insertType(name, BoolType);
				return typeObj.getType();
			}
			throw new Exception(name + " symbol not found");
		}
		// Check if type
		if (typeObj.getKind() != Obj.Type) { // Not a type
			throw new Exception(name + " not a type");
		}

		// Create new type
		typeNameMap.put(typeObj.getType(), name);
		return typeObj.getType();
	}

	// Struct to type name (TODO: refactor)
	private String structToTypeName(Struct type) {
		if (type.getKind() == Struct.Array) {
			return typeNameMap.get(type.getElemType()) + "[]";
		} else {
			return typeNameMap.get(type);
		}
	}

	// Visitor methods
	public void visit(Program program) {
		showVisitInfo(program);

		// Close program
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(program.getProgramName().obj);
		Tab.closeScope();
	}

	public void visit(ProgramName programName) {
		showVisitInfo(programName);

		// Open program
		programName.obj = Tab.insert(Obj.Prog, programName.getName(), Tab.noType);
		Tab.openScope();
	}

	public void visit(Method method) {
		showVisitInfo(method);

		// Close method
		nLocalVars.put(method.obj, Tab.currentScope.getnVars());
		Tab.chainLocalSymbols(currMethod);
		Tab.closeScope();
		currMethod = null;
		adrMethod = 0;
	}

	public void visit(MethodTypeName methodTypeName) {
		showVisitInfo(methodTypeName);

		// Extract type info
		Type type = methodTypeName.getType();
		String typeName = type.getTypeName();
		String methodName = methodTypeName.getName();

		// Assign struct
		try {
			type.struct = typeNameToStruct(typeName);
		} catch (Exception ex) {
			type.struct = VoidType; // Just to avoid an unwanted exception
			showError("Unsupported type name '" + typeName + "'", methodTypeName);
		}

		// Open method
		currMethod = Tab.insert(Obj.Meth, methodName, type.struct);
		methodTypeName.obj = currMethod;
		Tab.openScope();
	}

	// Symbol list validation

	public void visit(ConstSymbol declaration) {
		showVisitInfo(declaration);

		// Extract type info
		Type type = declaration.getType();
		String typeName = type.getTypeName();
		String constName = declaration.getSymbolName();

		// Assign struct
		try {
			type.struct = typeNameToStruct(typeName);
			declaration.obj = Tab.insert(Obj.Con, constName, type.struct);

			// Check kind
			int kind = type.struct.getKind();
			if (kind == Struct.Int) {
				declaration.obj.setAdr(declaration.getValue());
			} else if (kind != Struct.Char && kind != Struct.Bool) {
				showError("Invalid constant type '" + typeName + "'. Only int, bool, char can be constants!", declaration);
			} 

			logger.debug("New constant: '" + typeName + " " + constName);
		} catch (Exception ex) {
			showError("Unsupported type name '" + typeName + "'", declaration);
			type.struct = VoidType;
			declaration.obj = new Obj(Obj.Con, constName, type.struct);
		}
	}

	// Var symbol
	private void processVarSymbol(Struct typeStruct, VarSymbol symbol) {
		if (symbol instanceof VarIdent) {
			VarIdent VarIdent = (VarIdent) symbol;
			VarIdent.obj = Tab.insert(Obj.Var, VarIdent.getSymbolName(), typeStruct);
			VarIdent.obj.setAdr(adrMethod++);
			showInfo("New varSymbol: '" + structToTypeName(typeStruct) + " " + VarIdent.getSymbolName() + "'",  symbol);
		} else if (symbol instanceof VarArray) {
			VarArray varArray = (VarArray) symbol;
			varArray.obj = Tab.insert(Obj.Var, varArray.getSymbolName(), new Struct(Struct.Array, typeStruct));
			varArray.obj.setAdr(adrMethod++);
			showInfo("New varSymbol: " + structToTypeName(typeStruct) + varArray.getSymbolName() + "[]",  symbol);
		} else {
			VarMatrix varMatrix = (VarMatrix) symbol;
			varMatrix.obj = Tab.insert(Obj.Var, varMatrix.getSymbolName(), new Struct(Struct.Array, typeStruct));
			varMatrix.obj.setAdr(adrMethod++);
			showInfo("New varSymbol: " + structToTypeName(typeStruct) + varMatrix.getSymbolName() + "[][]",  symbol);
		}
	}

	// Multiple var symbols
	private void processVarListNext(Struct typeStruct, VarSymbolList symbols) {
		if (symbols instanceof VarListNext) {
			processVarSymbol(typeStruct, ((VarListNext) symbols).getVarSymbol());
			processVarListNext(typeStruct, ((VarListNext) symbols).getVarSymbolList());
		} else {
			processVarSymbol(typeStruct, ((VarListLast) symbols).getVarSymbol());
		}
	}

	// Var declaration
	public void visit(VarDecl varDecl) {
		showVisitInfo(varDecl);

		// Extract type info
		Type type = varDecl.getType();
		String typeName = type.getTypeName();

		// Assign struct
		try {
			type.struct = typeNameToStruct(typeName);
		} catch (Exception ex) {
			showError("Unsupported type '" + typeName + "'", type);
			type.struct = VoidType;
		}

		// Process symbol list
		processVarListNext(type.struct, varDecl.getVarSymbolList());
	}

	// Designator validation

	public void visit(DesignatorIdent designatorIdent) {

		// Get name info
		String name = designatorIdent.getDesignatorName();

		// Assign obj
		designatorIdent.obj = Tab.find(name);
		if (designatorIdent.obj != Tab.noObj) { // Symbol declared
			String localGlobal = (designatorIdent.obj.getLevel() > 0) ? "local" : "global";
			String varConst = (designatorIdent.obj.getKind() == Obj.Var) ? "var" : "const";
			String typeName = structToTypeName(designatorIdent.obj.getType());
			logger.debug("Accessing symbol " + (localGlobal + " " + varConst) + " '" + typeName + " " + name + "'");
		} else {
			showError("Undeclared symbol '" + name + "'", designatorIdent);
		}
	}

	public void visit(DesignatorArray designatorArray) {
		String name = designatorArray.getArrayName();
		Obj arrayObj = Tab.find(name);
		if (arrayObj != Tab.noObj) {
			designatorArray.obj = arrayObj;
			logger.debug("Accessing array '" + name + "[]'");
		} else {
			showError("Undeclared array '" + name + "[]'", designatorArray);
		}
	}

	public void visit(DesignatorMatrix designatorMatrix) {
		String name = designatorMatrix.getMatrixName();
		Obj arrayObj = Tab.find(name);
		if (arrayObj != Tab.noObj) {
			designatorMatrix.obj = arrayObj;
			logger.debug("Accessing matrix '" + name + "[][]'");
		} else {
			showError("Undeclared matrix '" + name + "[][]'", designatorMatrix);
		}
	}

	// Statement validation

	public void visit(StatementAssignExpr statement) {
		showVisitInfo(statement);
		Designator designator = statement.getDesignator();

		// Extract designator name and type
		String designatorName;
		Struct designatorType;
		if (designator instanceof DesignatorIdent) {
			designatorName = ((DesignatorIdent) designator).getDesignatorName();
			designatorType = designator.obj.getType();
		} else if (designator instanceof DesignatorArray) {
			designatorName = ((DesignatorArray) designator).getArrayName();
			designatorType = designator.obj.getType().getElemType();
		} else if (designator instanceof DesignatorMatrix) {
			designatorName = ((DesignatorMatrix) designator).getMatrixName();
			designatorType = designator.obj.getType().getElemType();
		} else {
			designatorName = null;
			designatorType = VoidType;
		}

		// Unknown type
		if (designatorType.equals(VoidType)) {
			showError("Unknown desigantor type", statement);
			return;
		}

		// Cannot assign to const
		if (designator.obj.getKind() == Obj.Con) {
			showError("Cannot assign to const", statement);
		}

		// Cannot have incompatible assignments
		Struct exprType = detectExprType(statement.getExpr());
		if (!designatorType.equals(exprType)) {
			String typeLeft = structToTypeName(designatorType);
			String typeRight = structToTypeName(exprType);
			showError("Cannot assign expression of type '" + typeRight + "' to variable of type '" + typeLeft + "'", statement);
		}
	}

	public void visit(StatementPrint statement) {
		showVisitInfo(statement);
		String typeName = structToTypeName(detectExprType(statement.getExpr()));
		logger.debug("Found print(): " + typeName);
		nPrints++;
	}

	public void visit(StatementInc statement) {
		showVisitInfo(statement);

		// Cannot increment const
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			showError("Cannot increment a const", statement);
		}

		// Get designator type
		Struct designatorType = statement.getDesignator().obj.getType();
		if (designatorType.getKind() == Struct.Array) {
			designatorType = designatorType.getElemType();
		}

		// Cannot increment non-int
		if (!designatorType.equals(IntType)) {
			showError("Only 'int' types can be incremented", statement);
		}
	}

	public void visit(StatementDec statement) {
		showVisitInfo(statement);

		// Cannot decrement const
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			showError("Cannot decrement a const", statement);
		}

		// Get designator type
		Struct designatorType = statement.getDesignator().obj.getType();
		if (designatorType.getKind() == Struct.Array) {
			designatorType = designatorType.getElemType();
		}

		// Cannot increment non-int
		if (!designatorType.equals(IntType)) {
			showError("Only 'int' types can be decremented", statement);
		}
	}

	public void visit(StatementRead statement) {
		showVisitInfo(statement);

		// Cannot read into a const
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			showError("Cannot read into a const", statement);
		}

		// Get read type
		Struct type = statement.getDesignator().obj.getType();
		if (type.getKind() == Struct.Array) { // Array
			type = type.getElemType();
		}

		// Compatibility check
		if (!type.equals(IntType) && !type.equals(CharType)) {
			showError("Only 'int' and 'char' types can be read()", statement);
		}
	}

	public void visit(StatementNewArray statement) {
		showVisitInfo(statement);

		// Check if type can be array
		Struct designatorType = statement.getDesignator().obj.getType();
		try {
			statement.getType().struct = typeNameToStruct(statement.getType().getTypeName());
		} catch (Exception e) {
			String typeName = statement.getType().getTypeName();
			showError("Cannot create an array of type '" + typeName + "'", statement);
		}

		// Array size expression
		Struct exprType = detectExprType(statement.getExpr());
		if (!exprType.equals(IntType)) {
			showError("Array size must be of type 'int'", statement);
		}

		// Check array
		if (designatorType.getKind() == Struct.Array) {
			if (statement.getDesignator() instanceof DesignatorIdent) { // Must be DesignatorIdent
				if (!designatorType.getElemType().equals(statement.getType().struct)) {
					String typeName = structToTypeName(statement.getType().struct);
					String designatorName = ((DesignatorIdent) statement.getDesignator()).getDesignatorName();
					String designatorTypeName = structToTypeName(designatorType);
					showError("Cannot assign an array of type '" + typeName + "[]' to an array variable of type '" + designatorTypeName + "[]'", statement);
				}
			} else {
				showError("Cannot create array of an array (use matrix[][] instead)", statement);
			}
		} else { // Not an array
			showError("Cannot assign array to a non-array variable", statement);
		}
	}

	public void visit(StatementNewMatrix statement) {
		showVisitInfo(statement);

		// Check if type is a matrix
		Struct designatorType = statement.getDesignator().obj.getType();
		try {
			statement.getType().struct = typeNameToStruct(statement.getType().getTypeName());
		} catch (Exception e) {
			String typeName = statement.getType().getTypeName();
			showError("Cannot create a matrix of type '" + typeName + "'", statement);
		}

		// Matrix size expressions
		Struct exprType = detectExprType(statement.getExpr());
		Struct exprType1 = detectExprType(statement.getExpr1());
		if (!exprType.equals(IntType) || !exprType1.equals(IntType)) {
			showError("Matrix sizes must be of type 'int'", statement);
		}

		// Check matrix
		if (designatorType.getKind() == Struct.Array) {
			if (statement.getDesignator() instanceof DesignatorIdent) { // Must be DesignatorIdent
				if (!designatorType.getElemType().equals(statement.getType().struct)) {
					String typeName = structToTypeName(statement.getType().struct);
					String designatorName = ((DesignatorIdent) statement.getDesignator()).getDesignatorName();
					String designatorTypeName = structToTypeName(designatorType);
					showError("Cannot assign matrix of type '" + typeName + "[][]' to a matrix variable of type '" + designatorTypeName + "[][]'", statement);
				}
			}
		} else { // Not a matri
			showError("Cannot assign matrix to a non-matrix variable", statement);
		}
	}

	// Expr validation

	private Struct detectFactorStruct(Factor factor) {
		if (factor instanceof FactorNumber) {
			factor.struct = IntType;
		} else if (factor instanceof FactorChar) {
			factor.struct = CharType;
		} else if (factor instanceof FactorExpr) {
			factor.struct = detectExprType(((FactorExpr) factor).getExpr());
		} else if (factor instanceof FactorDesignator) {
			Designator designator = ((FactorDesignator) factor).getDesignator();
			if (designator instanceof DesignatorIdent) {
				factor.struct = Tab.find(((DesignatorIdent) designator).getDesignatorName()).getType();
			} else if (designator instanceof DesignatorArray) { 
				factor.struct = Tab.find(((DesignatorArray) designator).getArrayName()).getType().getElemType();
			} else if (designator instanceof DesignatorMatrix) {
				factor.struct = Tab.find(((DesignatorMatrix) designator).getMatrixName()).getType().getElemType();
			}
		}
		return factor.struct;
	}

	private Struct detectMulopStruct(FactorList factorList) {
		if (factorList instanceof FactorListempty) {
			return VoidType;
		}

		// Extract struct info
		Struct factorListStruct = detectMulopStruct(((FactorListNext) factorList).getFactorList());
		Factor factor = ((FactorListNext) factorList).getFactor();
		factor.struct = detectFactorStruct(factor);

		// Compatibility check
		if (!factor.struct.equals(factorListStruct) && factorListStruct != VoidType) {
			String typeLeft = structToTypeName(factorListStruct);
			String typeRight = structToTypeName(factor.struct);
			showError("Incompatible multiplication types '" + typeLeft + "' and '" + typeRight, factorList);
		}

		return factor.struct;
	}

	private Struct detectTermType(Term term) {
		// Extract struct info
		Struct mulopStruct = VoidType;
		if (term instanceof TermNegative) {
			term.struct = detectFactorStruct(((TermNegative) term).getFactor());
			mulopStruct = detectMulopStruct(((TermNegative) term).getFactorList());
		} else if (term instanceof TermBasic) {
			term.struct = detectFactorStruct(((TermBasic) term).getFactor());
			mulopStruct = detectMulopStruct(((TermBasic) term).getFactorList());
		}

		// Compatibility check
		if (!term.struct.equals(mulopStruct) && mulopStruct != VoidType) {
			String typeLeft = structToTypeName(mulopStruct);
			String typeRight = structToTypeName(term.struct);
			showError("Incompatible addition types '" + typeLeft + "' and '" + typeRight, term);
		}

		// Cannot negate anything but int type
		if (term instanceof TermNegative) {
			if ((term.struct != IntType && term.struct != VoidType) || (mulopStruct != IntType && mulopStruct != VoidType)) {
				showError("Only 'int' types can be negated", term);
			}
		}

		return term.struct;
	}

	private Struct detectAddopStruct(TermList termList) {
		if (termList instanceof TermListEmpty) {
			return VoidType;
		}
		
		// Extract struct info
		Struct termListSturct = detectAddopStruct(((TermListNext) termList).getTermList());
		Term term = ((TermListNext) termList).getTerm();
		term.struct = detectTermType(term);

		// Compatibilty check
		if (!term.struct.equals(termListSturct) && termListSturct != VoidType) {
			String typeLeft = structToTypeName(termListSturct);
			String typeRight = structToTypeName(term.struct);
			showError("Incompatible addition types '" + typeLeft + "' and '" + typeRight, term);
		}

		return term.struct;
	}

	private Struct detectExprType(Expr expr) {
		expr.struct = detectTermType(expr.getTerm());
		Struct termListSturct = detectAddopStruct(expr.getTermList());

		// Compatibility check
		if (!expr.struct.equals(termListSturct) && termListSturct != VoidType) {
			String typeLeft = structToTypeName(termListSturct);
			String typeRight = structToTypeName(expr.struct);
			showError("Incompatible expression types '" + typeLeft + "' and '" + typeRight, expr);
		}

		return expr.struct;
	}
}