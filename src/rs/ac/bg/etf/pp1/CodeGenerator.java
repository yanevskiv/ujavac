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

		// Open method
		Code.put(Code.enter);
		Code.put(0);
		Code.put(0 + SemanticAnalyzer.nLocalVars.get(methodNode.obj));
	}

	public void visit(Method method) {
		// Close method
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	// Factors

	public void visit(FactorNumber factor) {
		Code.loadConst(factor.getValue());
	}

	public void visit(FactorChar factor) {
		Code.loadConst(factor.getValue().charAt(1));
	}

	public void visit(FactorDesignator factor) {
		if (factor.getDesignator() instanceof DesignatorIdent) {
			int kind = factor.getDesignator().obj.getKind();
			if (kind == Obj.Var) {
				Code.load(factor.getDesignator().obj);
			} else if (kind == Obj.Con) {
				Code.loadConst(factor.getDesignator().obj.getAdr());
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

			// Matrix index (2 + y * m + x)
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

	public void visit(StatementInc statement) {
		// Need the index on the stack
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup); 
		}

		// Load designator
		Code.load(statement.getDesignator().obj);

		// Cannot increment matrix
		if (statement.getDesignator() instanceof DesignatorMatrix) {
			throw new RuntimeException("Currently unimplemented: cannot increment matrix");
		}

		// Load array value
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
			Code.put(Code.aload);
			Code.put(Code.dup);
		}
		
		// Add by one
		Code.loadConst(1);
		Code.put(Code.add);

		// Store value
		if (statement.getDesignator() instanceof DesignatorIdent) {
			Code.store(statement.getDesignator().obj);
		} else if (statement.getDesignator() instanceof DesignatorArray) {
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.astore);
		}
	}

	public void visit(StatementDec statement) {
		// Need the index on the stack
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup);
		}

		// Load designator
		Code.load(statement.getDesignator().obj);

		if (statement.getDesignator() instanceof DesignatorMatrix) {
			throw new RuntimeException("Currently unimplemented: decrement increment matrix");
		}

		// Load array value
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
			Code.put(Code.aload);
		}

		// Sub by one
		Code.loadConst(1);
		Code.put(Code.sub);

		// Store value
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
		Struct exprStruct = statement.getExpr().struct;
		if (exprStruct == SemanticAnalyzer.IntType) {
			Code.put(Code.print);
		} else if (exprStruct == SemanticAnalyzer.CharType) {
			Code.put(Code.bprint);
		}
	}

	public void visit(StatementPrintWidth statement) {
		Code.loadConst(statement.getWidth());
		Struct exprStruct = statement.getExpr().struct;
		if (exprStruct == SemanticAnalyzer.IntType) {
			Code.put(Code.print);
		} else if (exprStruct == SemanticAnalyzer.CharType) {
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
		} else  {
			throw new RuntimeException("Unsupported: cannot read matrix");
		}

		if (designatorType == SemanticAnalyzer.IntType) {
			Code.put(Code.read);
		} else {
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
		// Preserve dimensions
		Code.put(Code.dup2);

		// Calculate linear (1D) size
		Code.put(Code.mul);
		Code.loadConst(2);
		Code.put(Code.add);

		// Create an array (int or char)
		Code.put(Code.newarray);
		if (statement.getType().struct == SemanticAnalyzer.CharType) {
			Code.put(0);
		} else {
			Code.put(1);
		}

		// Store dimension 1
		Code.put(Code.dup_x2);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.loadConst(1);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.astore);

		// Store dimension 2
		Code.put(Code.dup2);
		Code.put(Code.pop);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.loadConst(0);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.astore);

		// Store matrix
		Code.store(statement.getDesignator().obj);
	}
}