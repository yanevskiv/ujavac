package rs.ac.bg.etf.pp1;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	private static final Logger LOG = Logger.getLogger(SemanticAnalyzer.class);

	// Structs
	public static final Struct STRUCT_INT = Tab.intType;
	public static final Struct STRUCT_CHAR = Tab.charType;
	public static final Struct STRUCT_BOOL = new Struct(Struct.Bool);
	public static final Struct STRUCT_NONE = new Struct(Struct.None);

	private final Map<Struct, String> typeNameMap = new HashMap<>();

	public int numberOfGlobalVars = 0;
	public static Map<Obj, Integer> numberOfLocalVars = new HashMap<>();
	private int numberOfErrors = 0;

	private int constAddressCounter = 0;
	private int currentMethodAddressCounter = 0;
	private Obj currentMethod = null;

	public int getNumberOfGlobalVars() {
		return numberOfGlobalVars;
	}

	public int getNumberOfErrors() {
		return numberOfErrors;
	}

	private void reportError(String message, SyntaxNode info) {
		numberOfErrors++;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		LOG.error(msg.toString());
	}

	public void reportInfo(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		LOG.info(msg.toString());
	}

	// Insert a type to symbol table when needed
	private Obj insertType(String name, Struct typeStruct) {
		LOG.info("Inserting a new type " + name + " into the symbol table");

		Obj typeObj = Tab.insert(Obj.Type, name, typeStruct);
		typeNameMap.put(typeObj.getType(), name);

		return typeObj;
	}

	private Struct typeNameToStruct(String name) throws Exception {
		Obj typeObj = Tab.find(name);
		if (typeObj == Tab.noObj) { // Check if the type exists
			switch (name) {
			case "void":
				typeObj = insertType(name, STRUCT_NONE);
				return typeObj.getType();
			case "bool":
				typeObj = insertType(name, STRUCT_BOOL);
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

	private String structToTypeName(Struct type) {
		if (type.getKind() == Struct.Array) {
			return typeNameMap.get(type.getElemType()) + "[]";
		} else {
			return typeNameMap.get(type);
		}
	}

	// Visitor methods

	public void visit(ProgramName programName) {
		programName.obj = Tab.insert(Obj.Prog, programName.getName(), Tab.noType);
		Tab.openScope();
	}

	public void visit(Program program) {
		numberOfGlobalVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(program.getProgramName().obj);
		Tab.closeScope();
	}

	public void visit(MethodTypeName methodTypeName) {
		Type type = methodTypeName.getType();
		String typeName = type.getTypeName();
		String methodName = methodTypeName.getName();

		try {
			type.struct = typeNameToStruct(typeName);
		} catch (Exception ex) {
			type.struct = STRUCT_NONE; // Just to avoid an unwanted exception
			reportError("Tip " + typeName + " nije podrzan u realizaciji za nivo A.", methodTypeName);
		}

		currentMethod = Tab.insert(Obj.Meth, methodName, type.struct);
		methodTypeName.obj = currentMethod;
		Tab.openScope();
		reportInfo("Obradjuje se funkcija " + methodName + " koja vraca " + typeName, methodTypeName);
	}

	public void visit(Method method) {
		LOG.debug("Obradjena je funkcija \"" + method.getMethodTypeName().getName() + "\" koja vraca \""
				+ method.getMethodTypeName().getType().getTypeName() + "\" tip sa ukupno "
				+ Tab.currentScope().getnVars() + " lokalnih promenljivih");

		numberOfLocalVars.put(method.obj, Tab.currentScope.getnVars());
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();

		currentMethod = null;
		currentMethodAddressCounter = 0;
	}

	public void visit(DeclarationConst declaration) {
		Type type = declaration.getType();
		String typeName = type.getTypeName();
		String constName = declaration.getSymbolName();

		try {
			type.struct = typeNameToStruct(typeName);

			declaration.obj = Tab.insert(Obj.Con, constName, type.struct);

			switch (type.struct.getKind()) {
			case Struct.Int:
				declaration.obj.setAdr(declaration.getValue());
				break;
			case Struct.Char:
				break;
			case Struct.Bool:
				break;
			default:
				reportError("Tip " + typeName + " ne moze da bude konstanta. Samo int, bool ili char.", declaration);
			}

			LOG.debug("Deklarisana je konstanta \"" + constName + "\" tipa " + typeName);
		} catch (Exception ex) {
			reportError("Tip " + typeName + " nije deklarisan u tabeli simbola.", declaration);
			type.struct = STRUCT_NONE; // Just to avoid an unwanted exception
			declaration.obj = new Obj(Obj.Con, constName, type.struct);
		}
	}

	private void processSingleVarSymbol(Struct typeStruct, VarSymbol symbol) {
		if (symbol instanceof VarSimple) { // VarSimple
			VarSimple vs = (VarSimple) symbol;
			vs.obj = Tab.insert(Obj.Var, vs.getSymbolName(), typeStruct);
			vs.obj.setAdr(currentMethodAddressCounter++);

			reportInfo("Deklarisana je " + (vs.obj.getLevel() > 0 ? "lokalna" : "globalna") + " promenljiva \""
					+ vs.getSymbolName() + "\" tipa " + structToTypeName(typeStruct) + " sa adresom " + vs.obj.getAdr(),
					symbol);
		} else if (symbol instanceof VarArray) { // VarArray
			VarArray va = (VarArray) symbol;
			va.obj = Tab.insert(Obj.Var, va.getSymbolName(), new Struct(Struct.Array, typeStruct));
			va.obj.setAdr(currentMethodAddressCounter++);

			reportInfo(
					"Deklarisan je " + (va.obj.getLevel() > 0 ? "lokalni" : "globalni") + " niz \"" + va.getSymbolName()
							+ "\" promenljivih tipa " + structToTypeName(typeStruct) + " sa adresom " + va.obj.getAdr(),
					symbol);
		} else { // VarMatrix
			VarMatrix vm = (VarMatrix) symbol;
			// Just like an array
			vm.obj = Tab.insert(Obj.Var, vm.getSymbolName(), new Struct(Struct.Array, typeStruct));
			vm.obj.setAdr(currentMethodAddressCounter++);
			reportInfo("Deklarisana je " + (vm.obj.getLevel() > 0 ? "lokalna" : "globalna") + " matrica \""
					+ vm.getSymbolName() + "\" promenljivih tipa " + structToTypeName(typeStruct) + " sa adresom "
					+ vm.obj.getAdr(), symbol);
		}
	}

	private void processVarSymbols(Struct typeStruct, VarSymbolList symbols) {
		if (symbols instanceof VarSymbols) { // VarSymbols
			processSingleVarSymbol(typeStruct, ((VarSymbols) symbols).getVarSymbol());
			processVarSymbols(typeStruct, ((VarSymbols) symbols).getVarSymbolList());
		} else { // VarSymbolSingle
			processSingleVarSymbol(typeStruct, ((VarSymbolSingle) symbols).getVarSymbol());
		}
	}

	public void visit(DeclarationVar declaration) {
		Type type = declaration.getType();
		String typeName = type.getTypeName();

		try {
			type.struct = typeNameToStruct(typeName);
		} catch (Exception ex) {
			reportError("Tip " + typeName + " nije podrzan u realizaciji za nivo A.", declaration);
			type.struct = STRUCT_NONE; // Just to avoid an unwanted exception
		}

		processVarSymbols(type.struct, declaration.getVarSymbolList());
	}

	public void visit(SimpleDesignator designator) {
		String name = designator.getDesignatorName();
		designator.obj = Tab.find(name);
		if (designator.obj != Tab.noObj) { // Symbol declared
			LOG.debug(
					"Detektovano je koriscenje "
							+ (designator.obj.getKind() == 1
									? ((designator.obj.getLevel() > 0 ? "lokalne" : "globalne") + " promenljive")
									: "konstante")
							+ " \"" + name + "\" tipa " + structToTypeName(designator.obj.getType()));
		} else { // Symbol not declared (error)
			reportError("Simbol \"" + designator.getDesignatorName() + "\" nije deklarisan ", designator);
		}
	}

	public void visit(ArrayElementDesignator designator) {
		String name = designator.getArrayName();
		Obj arrayObj = Tab.find(name);
		if (arrayObj != Tab.noObj) {
			// Deliberately using Obj.Var instead of Obj.Elem, because of the Runtime
			// problem
			// designator.obj = new Obj(Obj.Elem, name + "_element",
			// arrayObj.getType().getElemType());
			designator.obj = arrayObj;
			LOG.debug("Detektovan je pristup elementu " + (arrayObj.getLevel() > 0 ? "lokalnog" : "globalnog")
					+ " niza \"" + name + "\"");
		} else {
			reportError("Niz \"" + designator.getArrayName() + "\" nije deklarisan", designator);
		}
	}

	public void visit(MatrixElementDesignator designator) {
		String name = designator.getMatrixName();
		Obj arrayObj = Tab.find(name);
		if (arrayObj != Tab.noObj) {
			// Deliberately using Obj.Var instead of Obj.Elem, because of the Runtime
			// problem
			// designator.obj = new Obj(Obj.Elem, name + "_element",
			// arrayObj.getType().getElemType());
			designator.obj = arrayObj;
			LOG.debug("Detektovan je pristup elementu " + (arrayObj.getLevel() > 0 ? "lokalne" : "globalne")
					+ " matrice \"" + name + "\"");
		} else {
			reportError("Matrica \"" + designator.getMatrixName() + "\" nije deklarisan", designator);
		}
	}

	// ********** Expression validation **********

	private Struct detectFactorStruct(Factor factor) {
		if (factor instanceof FactorNumericConstant) { // FactorNumericConstant
			factor.struct = STRUCT_INT;
		} else if (factor instanceof FactorCharConstant) { // CharConstant
			factor.struct = STRUCT_CHAR;
		} else if (factor instanceof FactorDesignator) { // FactorDesignator
			Designator designator = ((FactorDesignator) factor).getDesignator();
			if (designator instanceof SimpleDesignator) { // Simple Designator
				factor.struct = Tab.find(((SimpleDesignator) designator).getDesignatorName()).getType();
			} else if (designator instanceof ArrayElementDesignator) { // ArrayElementDesignator
				factor.struct = Tab.find(((ArrayElementDesignator) designator).getArrayName()).getType().getElemType();
			} else { // MatrixElementDesignator
				factor.struct = Tab.find(((MatrixElementDesignator) designator).getMatrixName()).getType()
						.getElemType();
			}
		} else { // FactorInParenthesis
			factor.struct = detectExpressionType(((FactorInParenthesis) factor).getExpression());
		}

		return factor.struct;
	}

	private Struct detectMulopStruct(MulOperations operationsMul) {
		if (operationsMul instanceof OperationsMultiplicationEmpty) {
			return STRUCT_NONE;
		} else {
			Struct mulOperationsStruct = detectMulopStruct(
					((OperationsMultiplication) operationsMul).getMulOperations());
			Factor factor = ((OperationsMultiplication) operationsMul).getFactor();
			factor.struct = detectFactorStruct(factor);
			if (!factor.struct.equals(mulOperationsStruct) && mulOperationsStruct != STRUCT_NONE) {
				reportError("Nekompatibilni tipovi " + structToTypeName(factor.struct) + " i "
						+ structToTypeName(mulOperationsStruct), operationsMul);
			}

			return factor.struct;
		}
	}

	private Struct detectTermType(Term term) {
		Struct mulopStruct;
		if (term instanceof TermNegated) {
			term.struct = detectFactorStruct(((TermNegated) term).getFactor());
			mulopStruct = detectMulopStruct(((TermNegated) term).getMulOperations());
		} else {// TermSimple
			term.struct = detectFactorStruct(((TermSimple) term).getFactor());
			mulopStruct = detectMulopStruct(((TermSimple) term).getMulOperations());
		}
		if (!term.struct.equals(mulopStruct) && mulopStruct != STRUCT_NONE) {
			reportError(
					"Nekompatibilni tipovi " + structToTypeName(term.struct) + " i " + structToTypeName(mulopStruct),
					term);
		}

		if (term instanceof TermNegated) {
			if ((term.struct != STRUCT_INT && term.struct != STRUCT_NONE)
					|| (mulopStruct != STRUCT_INT && mulopStruct != STRUCT_NONE)) {
				reportError("'-' sme da stoji samo ispred izraza tipa int", term);
			}
		}

		return term.struct;
	}

	private Struct detectAddopStruct(AddOperations addOperations) {
		if (addOperations instanceof OperationsAddEmpty) { // OperationsAddEmtpy
			return STRUCT_NONE;
		} else {// OperationsAdd
			Struct addOperationsStruct = detectAddopStruct(((OperationsAdd) addOperations).getAddOperations());
			Term term = ((OperationsAdd) addOperations).getTerm();
			term.struct = detectTermType(term);
			if (!term.struct.equals(addOperationsStruct) && addOperationsStruct != STRUCT_NONE) {
				reportError("Nekompatibilni tipovi " + structToTypeName(term.struct) + " i "
						+ structToTypeName(addOperationsStruct), addOperations);
			}

			return term.struct;
		}
	}

	private Struct detectExpressionType(Expression expression) {
		expression.struct = detectTermType(expression.getTerm());

		Struct addOperationsStruct = detectAddopStruct(expression.getAddOperations());
		if (!expression.struct.equals(addOperationsStruct) && addOperationsStruct != STRUCT_NONE) {
			reportError("Nekompatibilni tipovi " + structToTypeName(expression.struct) + " i "
					+ structToTypeName(addOperationsStruct), expression);
		}

		return expression.struct;
	}

	// ********** Statement validation **********

	public void visit(StatementAssignExpression statement) {
		Designator designator = statement.getDesignator();

		String designatorName;
		Struct designatorType;

		if (designator instanceof SimpleDesignator) {
			designatorName = ((SimpleDesignator) designator).getDesignatorName();
			designatorType = designator.obj.getType();
		} else if (designator instanceof ArrayElementDesignator) {
			designatorName = ((ArrayElementDesignator) designator).getArrayName();
			designatorType = designator.obj.getType().getElemType();
		} else {
			designatorName = ((MatrixElementDesignator) designator).getMatrixName();
			designatorType = designator.obj.getType().getElemType();
		}

		if (designator.obj.getKind() == Obj.Con) {
			reportError("Nije dozvoljena dodela vrednosti konstanti", statement);
		}

		Struct expressionType = detectExpressionType(statement.getExpression());
		if (!designatorType.equals(expressionType)) {
			reportError("Designator \"" + designatorName + "\" je tipa " + structToTypeName(designatorType)
					+ " a dodeljeni izraz je tipa " + structToTypeName(expressionType), statement);
		}
	}

	public void visit(StatementPrint statement) {
		reportInfo("Detektovana je print instrukcija tipa "
				+ structToTypeName(detectExpressionType(statement.getExpression())), statement);
	}

	public void visit(StatementIncrement statement) {
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			reportError("Nije dozvoljeno inkrementiranje konstante", statement);
		}

		Struct designatorType = statement.getDesignator().obj.getType();
		if (designatorType.getKind() == Struct.Array) {
			designatorType = designatorType.getElemType();
		}

		if (!designatorType.equals(STRUCT_INT)) {
			reportError("Nije dozvoljeno inkrementiranje varijable koja nije tipa int", statement);
		}
	}

	public void visit(StatementDecrement statement) {
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			reportError("Nije dozvoljeno dekrementiranje konstante", statement);
		}

		if (!statement.getDesignator().obj.getType().equals(STRUCT_INT)) {
			reportError("Nije dozvoljeno dekrementiranje varijable koja nije tipa int", statement);
		}
	}

	public void visit(StatementRead statement) {
		if (statement.getDesignator().obj.getKind() == Obj.Con) {
			reportError("Nije dozvoljeno citanje vrednosti u konstantu", statement);
		}
		Struct type = statement.getDesignator().obj.getType();
		if (type.getKind() == 3) { // Array
			type = type.getElemType();
		}

		if (!type.equals(STRUCT_INT) && !type.equals(STRUCT_CHAR)) {
			reportError("Nije dozvoljeno citanje vrednosti u varijablu tipa " + structToTypeName(type), statement);
		}
	}

	public void visit(StatementAllocateArray statement) {
		Struct designatorType = statement.getDesignator().obj.getType();
		try {
			statement.getType().struct = typeNameToStruct(statement.getType().getTypeName());
		} catch (Exception e) {
			reportError(statement.getType().getTypeName() + " tip nije podrzan", statement);
		}
		Struct expressionType = detectExpressionType(statement.getExpression());

		if (!expressionType.equals(STRUCT_INT)) {
			reportError("Velicina niza mora da bude tipa int", statement);
		}

		if (designatorType.getKind() == Struct.Array) {
			if (statement.getDesignator() instanceof SimpleDesignator) { // Must be SimpleDesignator
				if (!designatorType.getElemType().equals(statement.getType().struct)) {
					reportError("Alocira se niz tipa " + structToTypeName(statement.getType().struct) + ", ali niz \""
							+ ((SimpleDesignator) statement.getDesignator()).getDesignatorName() + "\" je tipa "
							+ structToTypeName(designatorType), statement);
				}
			} else { // Bad Matrix
				reportError(
						"MicroJava ne podrzava nizove nizova, nego posebno deklarisane matrice (\""
								+ ((ArrayElementDesignator) statement.getDesignator()).getArrayName() + "[][]\")",
						statement);
			}
		} else { // Not an array
			reportError("\"" + ((SimpleDesignator) statement.getDesignator()).getDesignatorName() + "\" nije niz",
					statement);
		}
	}

	public void visit(StatementAllocateMatrix statement) {
		Struct designatorType = statement.getDesignator().obj.getType();
		try {
			statement.getType().struct = typeNameToStruct(statement.getType().getTypeName());
		} catch (Exception e) {
			reportError(statement.getType().getTypeName() + " tip nije podrzan", statement);
		}
		Struct expressionType = detectExpressionType(statement.getExpression());
		Struct expressionType1 = detectExpressionType(statement.getExpression1());

		if (!expressionType.equals(STRUCT_INT) || !expressionType1.equals(STRUCT_INT)) {
			reportError("Dimenzije matrice moraju biti tipa int", statement);
		}

		if (designatorType.getKind() == Struct.Array) {
			if (statement.getDesignator() instanceof SimpleDesignator) { // Must be SimpleDesignator
				if (!designatorType.getElemType().equals(statement.getType().struct)) {
					reportError("Alocira se niz tipa " + structToTypeName(statement.getType().struct) + ", ali niz \""
							+ ((SimpleDesignator) statement.getDesignator()).getDesignatorName() + "\" je tipa "
							+ structToTypeName(designatorType), statement);
				}
			} else { // Bad Matrix
				reportError(
						"MicroJava ne podrzava matrice nizova, nego samo prostih tipova (\""
								+ ((ArrayElementDesignator) statement.getDesignator()).getArrayName() + "[][]\")",
						statement);
			}
		} else { // Not an array
			reportError("\"" + ((SimpleDesignator) statement.getDesignator()).getDesignatorName() + "\" nije niz",
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
			if (designator instanceof SimpleDesignator) {
				if (!type.equals(designator.obj.getType())) {
					reportError("\"" + ((SimpleDesignator) designator).getDesignatorName() + "\" treba da bude tipa "
							+ structToTypeName(type), node);
				}
			} else {
				if (!type.equals(designator.obj.getType().getElemType())) {
					reportError("\"" + ((ArrayElementDesignator) designator).getArrayName()
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
			if (statement.getDesignator() instanceof SimpleDesignator) {
				reportError(
						"\"" + ((SimpleDesignator) statement.getDesignator()).getDesignatorName() + "\" mora biti niz",
						statement);
			} else {
				reportError("\"" + ((ArrayElementDesignator) statement.getDesignator()).getArrayName()
						+ "\" mora biti niz, a ne element niza", statement);
			}
		}
	}
}