package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	private int mainPC = 0;

	public int getMainPc() {
		return mainPC;
	}

	public void visit(MethodTypeName methodTypeName) {

		// Main method
		if ("main".equalsIgnoreCase(methodTypeName.getName())) {
			mainPC = Code.pc;
		}
		methodTypeName.obj.setAdr(Code.pc);

		Method methodNode = (Method) methodTypeName.getParent();

		// Generate the entry
		Code.put(Code.enter);
		Code.put(0);
		Code.put(0 + SemanticAnalyzer.nLocalVars.get(methodNode.obj));
	}

	public void visit(Method method) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	// Factors
	public void visit(FactorNumber factor) {
		Code.loadConst(factor.getValue());
	}

	public void visit(FactorDesignator factor) {
		if (factor.getDesignator() instanceof DesignatorIdent) {
			switch (factor.getDesignator().obj.getKind()) {
			case Obj.Var:
				Code.load(factor.getDesignator().obj);
				break;
			case Obj.Con:
				Code.loadConst(factor.getDesignator().obj.getAdr());
				break;
			}
		} else if (factor.getDesignator() instanceof DesignatorArray) {
			Code.load(factor.getDesignator().obj);
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
			Code.put(Code.aload);
		} else if (factor.getDesignator() instanceof DesignatorMatrix) {
			Code.load(factor.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.loadConst(0);
			Code.put(Code.aload);
			Code.put(Code.mul);
			Code.put(Code.add);
			Code.loadConst(2);
			Code.put(Code.add);
			Code.put(Code.aload);
		}
	}

	public void visit(FactorChar factor) {
		Code.loadConst(factor.getValue().charAt(1));
	}

	// Operations
	public void visit(FactorListNext listElem) {
		if (listElem.getMulop() instanceof MulopMul) {
			Code.put(Code.mul);
		} else if (listElem.getMulop() instanceof MulopDiv) {
			Code.put(Code.div);
		} else if (listElem.getMulop() instanceof MulopMod) {
			Code.put(Code.rem);
		}
	}

	public void visit(TermListNext listElem) {
		if (listElem.getAddop() instanceof AddopAdd) {
			Code.put(Code.add);
		} else if (listElem.getAddop() instanceof AddopSub) {
			Code.put(Code.sub);
		}
	}

	public void visit(TermNegative term) {
		Code.loadConst(-1);
		Code.put(Code.mul);
	}

	// Statements

	public void visit(StatementAssignExpr statement) {
		if (statement.getDesignator() instanceof DesignatorIdent) {
			Code.store(statement.getDesignator().obj);
		} else if (statement.getDesignator() instanceof DesignatorArray) { // DesignatorArray
			// Code generator is not compatible with the Runtime, hence this
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.astore);
		} else if (statement.getDesignator() instanceof DesignatorMatrix) {
			Code.put(Code.dup_x2);
			Code.put(Code.pop);

			// Matrix index is calculated as 2 + y * m + x
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.loadConst(0);
			Code.put(Code.aload); // Load m
			Code.put(Code.mul);
			Code.put(Code.add);
			Code.loadConst(2);
			Code.put(Code.add);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.astore);
		}
	}

	private void processMultiDesignator(MultiDesignator md, Designator rightArray, int index) {
		if (md instanceof MultiDesignatorSkip) {
			processMultiDesignator(((MultiDesignatorSkip) md).getMultiDesignator(), rightArray, index + 1);
		} else {
			if (md instanceof MultiDesignatorWithDesignator) {
				processMultiDesignator(((MultiDesignatorWithDesignator) md).getMultiDesignator(), rightArray,
						index + 1);
			}

			Designator singleDesignator;
			if (md instanceof MultiDesignatorWithDesignator) {
				singleDesignator = ((MultiDesignatorWithDesignator) md).getDesignator();
			} else if (md instanceof MultiDesignatorLast) {
				singleDesignator = ((MultiDesignatorLast) md).getDesignator();
			} else {// MultiDesignatorLastComma
				singleDesignator = ((MultiDesignatorLastComma) md).getDesignator();
			}

			// Load the element from the right side array
			Code.loadConst(index);
			Code.load(rightArray.obj);
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
			Code.put(Code.aload);

			if (singleDesignator instanceof DesignatorIdent) {
				Code.store(singleDesignator.obj);
			} else if (singleDesignator instanceof DesignatorArray) { // DesignatorArray
				Code.load(singleDesignator.obj);
				Code.put(Code.dup_x2);
				Code.put(Code.pop);
				Code.put(Code.astore);
			} else { // DesignatorMatrix
				// TODO: Implement
				throw new RuntimeException("MJ ne podrzava upis u element matrice kroz multidesignator");
			}
		}
	}

	public void visit(StatementMultiAssign statement) {
		processMultiDesignator(statement.getMultiDesignator(), statement.getDesignator(), 0);
	}

	public void visit(StatementInc statement) {
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup); // Need that index on the stack to remain for astore
		}

		Code.load(statement.getDesignator().obj);

		if (statement.getDesignator() instanceof DesignatorMatrix) {
			// TODO: implement for matrix
			throw new RuntimeException("MJ ne podrzava direktno inkrementiranje elementa matrice");
		}

		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
			Code.put(Code.aload);
			Code.put(Code.dup);
		}

		Code.loadConst(1);
		Code.put(Code.add);
		if (statement.getDesignator() instanceof DesignatorIdent) {
			Code.store(statement.getDesignator().obj);
		} else {
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.astore);
		}
	}

	public void visit(StatementDec statement) {
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup); // Need that index on the stack to remain for astore
		}

		Code.load(statement.getDesignator().obj);

		if (statement.getDesignator() instanceof DesignatorMatrix) {
			// TODO: implement for matrix
			throw new RuntimeException("MJ ne podrzava direktno dekrementiranje elementa matrice");
		}

		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
			Code.put(Code.aload);
		}

		Code.loadConst(1);
		Code.put(Code.sub);

		if (statement.getDesignator() instanceof DesignatorIdent) {
			Code.store(statement.getDesignator().obj);
		} else {
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.astore);
		}
	}

	public void visit(StatementPrint statement) {
		Code.loadConst(1);
		if (statement.getExpr().struct == SemanticAnalyzer.IntType) {
			Code.put(Code.print);
		} else { // STRUCT_CHAR
			Code.put(Code.bprint);
		}
	}

	public void visit(StatementPrintWidth statement) {
		Code.loadConst(statement.getWidth());
		if (statement.getExpr().struct == SemanticAnalyzer.IntType) {
			Code.put(Code.print);
		} else { // STRUCT_CHAR
			Code.put(Code.bprint);
		}
	}

	public void visit(StatementRead statement) {
		Obj designatorObj;
		Struct designatorType;

		if (statement.getDesignator() instanceof DesignatorIdent) {
			designatorObj = ((DesignatorIdent) statement.getDesignator()).obj;
			designatorType = designatorObj.getType();
		} else if (statement.getDesignator() instanceof DesignatorArray) { // DesignatorArray
			designatorObj = ((DesignatorArray) statement.getDesignator()).obj;
			designatorType = designatorObj.getType().getElemType();
		} else { // DesignatorMatrix
			// TODO: implement
			throw new RuntimeException("MJ ne podrzava direktno citanje u element matrice");
		}

		if (designatorType == SemanticAnalyzer.IntType) { // STRUCT_INT
			Code.put(Code.read);
		} else { // STRUCT_CHAR
			Code.put(Code.bread);
		}

		if (statement.getDesignator() instanceof DesignatorIdent) {
			Code.store(designatorObj);
		} else {
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.astore);
		}
	}

	public void visit(StatementNewArray statement) {
		Code.put(Code.newarray);
		if (statement.getType().struct == SemanticAnalyzer.CharType) {
			Code.put(0);
		} else {
			Code.put(1);
		}
		Code.store(statement.getDesignator().obj);
	}

	public void visit(StatementNewMatrix statement) {
		// Preserve dimensions on the Expr stack
		Code.put(Code.dup2);
		// Calculate serialized matrix size
		Code.put(Code.mul);
		Code.loadConst(2);
		Code.put(Code.add);

		// Proceed like with an array
		Code.put(Code.newarray);
		if (statement.getType().struct == SemanticAnalyzer.CharType) {
			Code.put(0);
		} else {
			Code.put(1);
		}
		// Fucking with the Expr stack until it works
		Code.put(Code.dup_x2);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.loadConst(1);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.astore);

		Code.put(Code.dup2);
		Code.put(Code.pop);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.loadConst(0);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.astore);
		Code.store(statement.getDesignator().obj);
	}
}