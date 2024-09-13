package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	// Logger
	private static final Logger logger = Logger.getLogger(CodeGenerator.class);

	private int mainPC = 0;

	private int nErrors = 0;

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
		showInfo(" --- Visiting (code) '" + info.getClass().getSimpleName() + "'", info);
	}

	// Get nErrors
	public int getnErrors() {
		return nErrors;
	}

	// Get mainPC
	public int getMainPc() {
		return mainPC;
	}


	public void visit(MethodTypeName methodTypeName) {
		showVisitInfo(methodTypeName);

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
		showVisitInfo(method);

		// Close method
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	// Factors

	public void visit(FactorNumber factor) {
		showVisitInfo(factor);
		Code.loadConst(factor.getValue());
	}

	public void visit(FactorChar factor) {
		showVisitInfo(factor);
		Code.loadConst(factor.getValue().charAt(1));
	}

	public void visit(FactorDesignator factor) {
		showVisitInfo(factor);
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
		showVisitInfo(listElem);
		if (listElem.getMulop() instanceof MulopMul) {
			Code.put(Code.mul);
		} else if (listElem.getMulop() instanceof MulopDiv) {
			Code.put(Code.div);
		} else if (listElem.getMulop() instanceof MulopMod) {
			Code.put(Code.rem);
		}
	}

	public void visit(TermListNext listElem) {
		showVisitInfo(listElem);
		if (listElem.getAddop() instanceof AddopAdd) {
			Code.put(Code.add);
		} else if (listElem.getAddop() instanceof AddopSub) {
			Code.put(Code.sub);
		}
	}

	public void visit(TermNegative term) {
		showVisitInfo(term);
		Code.loadConst(-1);
		Code.put(Code.mul);
	}

	// Statements

	public void visit(StatementAssignExpr statement) {
		showVisitInfo(statement);
		if (statement.getDesignator() instanceof DesignatorIdent) {
			Code.store(statement.getDesignator().obj);
		} else if (statement.getDesignator() instanceof DesignatorArray) {
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.put(Code.pop);
			Code.put(Code.astore);
		} else if (statement.getDesignator() instanceof DesignatorMatrix) {
			Code.put(Code.dup_x2);
			Code.put(Code.pop);

			// Matrix access (2 + y * w + x)
			Code.load(statement.getDesignator().obj);
			Code.put(Code.dup_x2);
			Code.loadConst(0);
			Code.put(Code.aload); // Load w
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
		showVisitInfo(statement);

		// Need the index on the stack
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup); 
		}

		// Load designator
		Code.load(statement.getDesignator().obj);

		// Cannot increment matrix
		if (statement.getDesignator() instanceof DesignatorMatrix) {
			showError("Currently unimplemented: cannot increment matrix", statement);
			return;
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
		showVisitInfo(statement);

		// Need the index on the stack
		if (statement.getDesignator() instanceof DesignatorArray) {
			Code.put(Code.dup);
		}

		// Load designator
		Code.load(statement.getDesignator().obj);

		if (statement.getDesignator() instanceof DesignatorMatrix) {
			showError("Currently unimplemented: decrement increment matrix", statement);
			return;
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
		showVisitInfo(statement);

		// Print integer or character
		Code.loadConst(1);
		Struct exprStruct = statement.getExpr().struct;
		if (exprStruct == SemanticAnalyzer.IntType) {
			Code.put(Code.print);
		} else if (exprStruct == SemanticAnalyzer.CharType) {
			Code.put(Code.bprint);
		}
	}

	public void visit(StatementPrintWidth statement) {
		showVisitInfo(statement);
		// Print width
		Code.loadConst(statement.getWidth());
		Struct exprStruct = statement.getExpr().struct;
		if (exprStruct == SemanticAnalyzer.IntType) {
			Code.put(Code.print);
		} else if (exprStruct == SemanticAnalyzer.CharType) {
			Code.put(Code.bprint);
		}
	}

	public void visit(StatementRead statement) {
		showVisitInfo(statement);

		Obj designatorObj;
		Struct designatorType;

		if (statement.getDesignator() instanceof DesignatorIdent) {
			designatorObj = ((DesignatorIdent) statement.getDesignator()).obj;
			designatorType = designatorObj.getType();
		} else if (statement.getDesignator() instanceof DesignatorArray) { // DesignatorArray
			designatorObj = ((DesignatorArray) statement.getDesignator()).obj;
			designatorType = designatorObj.getType().getElemType();
		} else  {
			showError("Unsupported: cannot read matrix", statement);
			return;
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
		showVisitInfo(statement);
		Code.put(Code.newarray);
		if (statement.getType().struct == SemanticAnalyzer.CharType) {
			Code.put(0);
		} else {
			Code.put(1);
		}
		Code.store(statement.getDesignator().obj);
	}

	public void visit(StatementNewMatrix statement) {
		showVisitInfo(statement);
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

		// Store dimension 0
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