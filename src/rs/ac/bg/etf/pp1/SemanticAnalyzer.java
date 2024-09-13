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

	// Type name to struct (TODO: Refactor)
	private Struct typeNameToStruct(String name) throws Exception {
		Obj typeObj = Tab.find(name);
		if (typeObj == Tab.noObj) { // Check if the type exists
			switch (name) {
			case "void":
				typeObj = insertType(name, VoidType);
				return typeObj.getType();
			case "bool":
				typeObj = insertType(name, BoolType);
				return typeObj.getType();

			default:// Type not found
				throw new Exception(name + " symbol not found");
			}

		} else if (typeObj.getKind() != 2) { // Not a type
			throw new Exception(name + " not a type");
		} else {
			typeNameMap.put(typeObj.getType(), name);

			return typeObj.getType();
		}
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
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(program.getProgramName().obj);
		Tab.closeScope();
	}

	public void visit(ProgramName programName) {
		showVisitInfo(programName);
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

	public void visit(DeclarationConst declaration) {
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
	private void processSingleVarSymbol(Struct typeStruct, VarSymbol symbol) {
		if (symbol instanceof VarSimple) { // VarSimple
			VarSimple varSimple = (VarSimple) symbol;
			varSimple.obj = Tab.insert(Obj.Var, varSimple.getSymbolName(), typeStruct);
			varSimple.obj.setAdr(adrMethod++);

			showInfo("New varSymbol: " + structToTypeName(typeStruct) + varSimple.getSymbolName(),  symbol);

			// showInfo("Deklarisana je " + (varSimple.obj.getLevel() > 0 ? "lokalna" : "globalna") + " promenljiva \""
			// 		+ varSimple.getSymbolName() + "\" tipa " + structToTypeName(typeStruct) + " sa adresom " + varSimple.obj.getAdr(),
			// 		symbol);
		} else if (symbol instanceof VarArray) { // VarArray
			VarArray varArray = (VarArray) symbol;
			varArray.obj = Tab.insert(Obj.Var, varArray.getSymbolName(), new Struct(Struct.Array, typeStruct));
			varArray.obj.setAdr(adrMethod++);
			showInfo("New varSymbol: " + structToTypeName(typeStruct) + varArray.getSymbolName() + "[]",  symbol);

			// showInfo(
			// 		"Deklarisan je " + (varArray.obj.getLevel() > 0 ? "lokalni" : "globalni") + " niz \"" + varArray.getSymbolName()
			// 				+ "\" promenljivih tipa " + structToTypeName(typeStruct) + " sa adresom " + varArray.obj.getAdr(),
			// 		symbol);
		} else { // VarMatrix
			VarMatrix varMatrix = (VarMatrix) symbol;
			varMatrix.obj = Tab.insert(Obj.Var, varMatrix.getSymbolName(), new Struct(Struct.Array, typeStruct));
			varMatrix.obj.setAdr(adrMethod++);
			showInfo("New varSymbol: " + structToTypeName(typeStruct) + varMatrix.getSymbolName() + "[][]",  symbol);

			// showInfo("Deklarisana je " + (varMatrix.obj.getLevel() > 0 ? "lokalna" : "globalna") + " matrica \""
			// 		+ varMatrix.getSymbolName() + "\" promenljivih tipa " + structToTypeName(typeStruct) + " sa adresom "
			// 		+ varMatrix.obj.getAdr(), symbol);
		}
	}

	// Multiple var symbols
	private void processVarSymbols(Struct typeStruct, VarSymbolList symbols) {
		if (symbols instanceof VarSymbols) {
			processSingleVarSymbol(typeStruct, ((VarSymbols) symbols).getVarSymbol());
			processVarSymbols(typeStruct, ((VarSymbols) symbols).getVarSymbolList());
		} else {
			processSingleVarSymbol(typeStruct, ((VarSymbolSingle) symbols).getVarSymbol());
		}
	}

	// Var declaration
	public void visit(DeclarationVar declaration) {
		Type type = declaration.getType();
		String typeName = type.getTypeName();

		try {
			type.struct = typeNameToStruct(typeName);
		} catch (Exception ex) {
			showError("Tip " + typeName + " nije podrzan u realizaciji za nivo A.", declaration);
			type.struct = VoidType; // Just to avoid an unwanted exception
		}

		processVarSymbols(type.struct, declaration.getVarSymbolList());
	}

	// Designator
	public void visit(DesignatorBaisc designator) {
		String name = designator.getDesignatorName();
		designator.obj = Tab.find(name);
		if (designator.obj != Tab.noObj) { // Symbol declared
			logger.debug(
					"Detektovano je koriscenje "
							+ (designator.obj.getKind() == 1
									? ((designator.obj.getLevel() > 0 ? "lokalne" : "globalne") + " promenljive")
									: "konstante")
							+ " \"" + name + "\" tipa " + structToTypeName(designator.obj.getType()));
		} else { // Symbol not declared (error)
			showError("Simbol \"" + designator.getDesignatorName() + "\" nije deklarisan ", designator);
		}
	}

	public void visit(DesignatorArray designator) {
		String name = designator.getArrayName();
		Obj arrayObj = Tab.find(name);
		if (arrayObj != Tab.noObj) {
			// Deliberately using Obj.Var instead of Obj.Elem, because of the Runtime
			// problem
			// designator.obj = new Obj(Obj.Elem, name + "_element",
			// arrayObj.getType().getElemType());
			designator.obj = arrayObj;
			logger.debug("Detektovan je pristup elementu " + (arrayObj.getLevel() > 0 ? "lokalnog" : "globalnog")
					+ " niza \"" + name + "\"");
		} else {
			showError("Niz \"" + designator.getArrayName() + "\" nije deklarisan", designator);
		}
	}

	public void visit(DesignatorMatrix designator) {
		String name = designator.getMatrixName();
		Obj arrayObj = Tab.find(name);
		if (arrayObj != Tab.noObj) {
			// Deliberately using Obj.Var instead of Obj.Elem, because of the Runtime
			// problem
			// designator.obj = new Obj(Obj.Elem, name + "_element",
			// arrayObj.getType().getElemType());
			designator.obj = arrayObj;
			logger.debug("Detektovan je pristup elementu " + (arrayObj.getLevel() > 0 ? "lokalne" : "globalne")
					+ " matrice \"" + name + "\"");
		} else {
			showError("Matrica \"" + designator.getMatrixName() + "\" nije deklarisan", designator);
		}
	}

	// ********** Expr validation **********

	private Struct detectFactorStruct(Factor factor) {
		if (factor instanceof FactorNumber) { // FactorNumber
			factor.struct = IntType;
		} else if (factor instanceof FactorChar) { // CharConstant
			factor.struct = CharType;
		} else if (factor instanceof FactorDesignator) { // FactorDesignator
			Designator designator = ((FactorDesignator) factor).getDesignator();
			if (designator instanceof DesignatorBaisc) { // Simple Designator
				factor.struct = Tab.find(((DesignatorBaisc) designator).getDesignatorName()).getType();
			} else if (designator instanceof DesignatorArray) { // DesignatorArray
				factor.struct = Tab.find(((DesignatorArray) designator).getArrayName()).getType().getElemType();
			} else { // DesignatorMatrix
				factor.struct = Tab.find(((DesignatorMatrix) designator).getMatrixName()).getType()
						.getElemType();
			}
		} else { // FactorExpr
			factor.struct = detectExprType(((FactorExpr) factor).getExpr());
		}

		return factor.struct;
	}

	private Struct detectMulopStruct(FactorList operationsMul) {
		if (operationsMul instanceof FactorListempty) {
			return VoidType;
		} else {
			Struct FactorListStruct = detectMulopStruct(
					((FactorListNext) operationsMul).getFactorList());
			Factor factor = ((FactorListNext) operationsMul).getFactor();
			factor.struct = detectFactorStruct(factor);
			if (!factor.struct.equals(FactorListStruct) && FactorListStruct != VoidType) {
				showError("Nekompatibilni tipovi " + structToTypeName(factor.struct) + " i "
						+ structToTypeName(FactorListStruct), operationsMul);
			}

			return factor.struct;
		}
	}

	private Struct detectTermType(Term term) {
		Struct mulopStruct;
		if (term instanceof TermNegative) {
			term.struct = detectFactorStruct(((TermNegative) term).getFactor());
			mulopStruct = detectMulopStruct(((TermNegative) term).getFactorList());
		} else {// TermBasic
			term.struct = detectFactorStruct(((TermBasic) term).getFactor());
			mulopStruct = detectMulopStruct(((TermBasic) term).getFactorList());
		}
		if (!term.struct.equals(mulopStruct) && mulopStruct != VoidType) {
			showError(
					"Nekompatibilni tipovi " + structToTypeName(term.struct) + " i " + structToTypeName(mulopStruct),
					term);
		}

		if (term instanceof TermNegative) {
			if ((term.struct != IntType && term.struct != VoidType)
					|| (mulopStruct != IntType && mulopStruct != VoidType)) {
				showError("'-' sme da stoji samo ispred izraza tipa int", term);
			}
		}

		return term.struct;
	}

	private Struct detectAddopStruct(TermList TermList) {
		if (TermList instanceof TermListEmpty) { // TermListNextEmtpy
			return VoidType;
		} else {// TermListNext
			Struct TermListStruct = detectAddopStruct(((TermListNext) TermList).getTermList());
			Term term = ((TermListNext) TermList).getTerm();
			term.struct = detectTermType(term);
			if (!term.struct.equals(TermListStruct) && TermListStruct != VoidType) {
				showError("Nekompatibilni tipovi " + structToTypeName(term.struct) + " i "
						+ structToTypeName(TermListStruct), TermList);
			}

			return term.struct;
		}
	}

	private Struct detectExprType(Expr Expr) {
		Expr.struct = detectTermType(Expr.getTerm());

		Struct TermListStruct = detectAddopStruct(Expr.getTermList());
		if (!Expr.struct.equals(TermListStruct) && TermListStruct != VoidType) {
			showError("Nekompatibilni tipovi " + structToTypeName(Expr.struct) + " i "
					+ structToTypeName(TermListStruct), Expr);
		}

		return Expr.struct;
	}

	// ********** Statement validation **********

	public void visit(StatementAssignExpr statement) {
		Designator designator = statement.getDesignator();

		String designatorName;
		Struct designatorType;

		if (designator instanceof DesignatorBaisc) {
			designatorName = ((DesignatorBaisc) designator).getDesignatorName();
			designatorType = designator.obj.getType();
		} else if (designator instanceof DesignatorArray) {
			designatorName = ((DesignatorArray) designator).getArrayName();
			designatorType = designator.obj.getType().getElemType();
		} else {
			designatorName = ((DesignatorMatrix) designator).getMatrixName();
			designatorType = designator.obj.getType().getElemType();
		}

		if (designator.obj.getKind() == Obj.Con) {
			showError("Nije dozvoljena dodela vrednosti konstanti", statement);
		}

		Struct ExprType = detectExprType(statement.getExpr());
		if (!designatorType.equals(ExprType)) {
			showError("Designator \"" + designatorName + "\" je tipa " + structToTypeName(designatorType)
					+ " a dodeljeni izraz je tipa " + structToTypeName(ExprType), statement);
		}
	}

	public void visit(StatementPrint statement) {
		showInfo("Detektovana je print instrukcija tipa "
				+ structToTypeName(detectExprType(statement.getExpr())), statement);
	}

	public void visit(StatementInc statement) {
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			showError("Nije dozvoljeno inkrementiranje konstante", statement);
		}

		Struct designatorType = statement.getDesignator().obj.getType();
		if (designatorType.getKind() == Struct.Array) {
			designatorType = designatorType.getElemType();
		}

		if (!designatorType.equals(IntType)) {
			showError("Nije dozvoljeno inkrementiranje varijable koja nije tipa int", statement);
		}
	}

	public void visit(StatementDec statement) {
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			showError("Nije dozvoljeno dekrementiranje konstante", statement);
		}

		if (!statement.getDesignator().obj.getType().equals(IntType)) {
			showError("Nije dozvoljeno dekrementiranje varijable koja nije tipa int", statement);
		}
	}

	public void visit(StatementRead statement) {
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			showError("Nije dozvoljeno citanje vrednosti u konstantu", statement);
		}
		Struct type = statement.getDesignator().obj.getType();
		if (type.getKind() == 3) { // Array
			type = type.getElemType();
		}

		if (!type.equals(IntType) && !type.equals(CharType)) {
			showError("Nije dozvoljeno citanje vrednosti u varijablu tipa " + structToTypeName(type), statement);
		}
	}

	public void visit(StatementNewArray statement) {
		Struct designatorType = statement.getDesignator().obj.getType();
		try {
			statement.getType().struct = typeNameToStruct(statement.getType().getTypeName());
		} catch (Exception e) {
			showError(statement.getType().getTypeName() + " tip nije podrzan", statement);
		}
		Struct ExprType = detectExprType(statement.getExpr());

		if (!ExprType.equals(IntType)) {
			showError("Velicina niza mora da bude tipa int", statement);
		}

		if (designatorType.getKind() == Struct.Array) {
			if (statement.getDesignator() instanceof DesignatorBaisc) { // Must be DesignatorBaisc
				if (!designatorType.getElemType().equals(statement.getType().struct)) {
					showError("Alocira se niz tipa " + structToTypeName(statement.getType().struct) + ", ali niz \""
							+ ((DesignatorBaisc) statement.getDesignator()).getDesignatorName() + "\" je tipa "
							+ structToTypeName(designatorType), statement);
				}
			} else { // Bad Matrix
				showError(
						"MicroJava ne podrzava nizove nizova, nego posebno deklarisane matrice (\""
								+ ((DesignatorArray) statement.getDesignator()).getArrayName() + "[][]\")",
						statement);
			}
		} else { // Not an array
			showError("\"" + ((DesignatorBaisc) statement.getDesignator()).getDesignatorName() + "\" nije niz",
					statement);
		}
	}

	public void visit(StatementNewMatrix statement) {
		Struct designatorType = statement.getDesignator().obj.getType();
		try {
			statement.getType().struct = typeNameToStruct(statement.getType().getTypeName());
		} catch (Exception e) {
			showError(statement.getType().getTypeName() + " tip nije podrzan", statement);
		}
		Struct ExprType = detectExprType(statement.getExpr());
		Struct ExprType1 = detectExprType(statement.getExpr1());

		if (!ExprType.equals(IntType) || !ExprType1.equals(IntType)) {
			showError("Dimenzije matrice moraju biti tipa int", statement);
		}

		if (designatorType.getKind() == Struct.Array) {
			if (statement.getDesignator() instanceof DesignatorBaisc) { // Must be DesignatorBaisc
				if (!designatorType.getElemType().equals(statement.getType().struct)) {
					showError("Alocira se niz tipa " + structToTypeName(statement.getType().struct) + ", ali niz \""
							+ ((DesignatorBaisc) statement.getDesignator()).getDesignatorName() + "\" je tipa "
							+ structToTypeName(designatorType), statement);
				}
			} else { // Bad Matrix
				showError(
						"MicroJava ne podrzava matrice nizova, nego samo prostih tipova (\""
								+ ((DesignatorArray) statement.getDesignator()).getArrayName() + "[][]\")",
						statement);
			}
		} else { // Not an array
			showError("\"" + ((DesignatorBaisc) statement.getDesignator()).getDesignatorName() + "\" nije niz",
					statement);
		}
	}

	private void checkMultiDesignator(MultiDesignator multiDesignator, Struct type, SyntaxNode node) {
		if (!(multiDesignator instanceof MultiDesignatorSkip)) { // Has a designator
			Designator designator; // Designator from the left side
			if (multiDesignator instanceof MultiDesignatorWithDesignator) {
				designator = ((MultiDesignatorWithDesignator) multiDesignator).getDesignator();
			} else if (multiDesignator instanceof MultiDesignatorLast) {
				designator = ((MultiDesignatorLast) multiDesignator).getDesignator();
			} else { // MultiDesignatorLastComma
				designator = ((MultiDesignatorLastComma) multiDesignator).getDesignator();
			}

			// Must match the right side array element type
			if (designator instanceof DesignatorBaisc) {
				if (!type.equals(designator.obj.getType())) {
					showError("\"" + ((DesignatorBaisc) designator).getDesignatorName() + "\" treba da bude tipa "
							+ structToTypeName(type), node);
				}
			} else {
				if (!type.equals(designator.obj.getType().getElemType())) {
					showError("\"" + ((DesignatorArray) designator).getArrayName()
							+ "\" treba da bude niz tipa " + structToTypeName(type), node);
				}
			}

			if (multiDesignator instanceof MultiDesignatorWithDesignator) {
				checkMultiDesignator(((MultiDesignatorWithDesignator) multiDesignator).getMultiDesignator(), type,
						node);
			}
		} else { // Skip
			checkMultiDesignator(((MultiDesignatorSkip) multiDesignator).getMultiDesignator(), type, node);
		}
	}

	// Deprecated
	public void visit(StatementMultiAssign statement) {
		Struct arrayType = statement.getDesignator().obj.getType();
		if (arrayType.getKind() == Struct.Array) {
			checkMultiDesignator(statement.getMultiDesignator(), arrayType.getElemType(), statement);
		} else {
			if (statement.getDesignator() instanceof DesignatorBaisc) {
				showError(
						"\"" + ((DesignatorBaisc) statement.getDesignator()).getDesignatorName() + "\" mora biti niz",
						statement);
			} else {
				showError("\"" + ((DesignatorArray) statement.getDesignator()).getArrayName()
						+ "\" mora biti niz, a ne element niza", statement);
			}
		}
	}
}