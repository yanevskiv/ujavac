package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;

import java_cup.runtime.Symbol;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;

public class MJCompiler {

	// Logger setup
	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	private static final Logger logger = Logger.getLogger(MJCompiler.class);

	// tsdump
	public static void tsdump() {
		Tab.dump();
	}

	// Main function (MJCompiler)
	public static void main(String[] args) throws Exception {
		logger.info("PP1 - Ivan Janevski - 2016/0270");

		// Source/object file
		File objFile = null, sourceFile = new File(args[0]);
		if (sourceFile.getAbsolutePath().endsWith(".mj")) {
			String objPath = sourceFile.getAbsolutePath().substring(0, sourceFile.getAbsolutePath().lastIndexOf(".mj")) + ".obj";
			objFile = new File(objPath);
		} else {
			logger.error("Source file must end in .mj extensnion");
			return;
		}

		try (Reader bufferedReader = new BufferedReader(new FileReader(sourceFile))) {

			// Lexer
			logger.info("===== Lexer =====");
			Yylex lexer = new Yylex(bufferedReader);

			// Parser
			MJParser parser = new MJParser(lexer);
			Symbol rootSymbol = parser.parse();
			Program prog = (Program) (rootSymbol.value);
			logger.info(prog.toString(""));

			// Semantic Analyzer
			logger.info(" ===== Semantic Analysis =====");
			Tab.init();
			SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
			prog.traverseBottomUp(semanticAnalyzer);
			
			// Dump symbol table
			tsdump();
			
			// Semantic errors
			if (semanticAnalyzer.getnErrors() > 0) {
				logger.info("Semantic parser error " + semanticAnalyzer.getnErrors());
				return;
			}

			// Program info
			logger.info("===================================");
			logger.info("Program " + prog.getProgramName().getName() + " passes Semantic analysis");
			logger.info("nVars: " + semanticAnalyzer.getnVars());
			logger.info("nPrints: " + semanticAnalyzer.getnPrints());
			logger.info("===================================");

			// Code generator
			logger.info(" ===== Code Generation =====");
			CodeGenerator codeGenerator = new CodeGenerator();
			prog.traverseBottomUp(codeGenerator);

			// Code write
			Code.dataSize = semanticAnalyzer.getnVars();
			Code.mainPc = codeGenerator.getMainPc();
			if (objFile.exists()) {
				objFile.delete();
			}

			logger.info("Writing to " + objFile.getPath());
			try (OutputStream outputStream = new FileOutputStream(objFile)) {
				Code.write(outputStream);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			logger.error(ex.getMessage(), ex);
		}
	}
}