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

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}

	private static final Logger LOG = Logger.getLogger(MJCompiler.class);

	public static void tsdump() {
		Tab.dump();
	}

	public static void main(String[] args) throws Exception {

		LOG.info("MJCompiler v0.1 - RAJOVIC");

		File sourceFile = new File(args[0]);
		File objFile;
		if (sourceFile.getAbsolutePath().endsWith(".mj")) {
			objFile = new File(
					sourceFile.getAbsolutePath().substring(0, sourceFile.getAbsolutePath().lastIndexOf(".mj"))
							+ ".obj");
		} else {
			objFile = new File(sourceFile.getAbsolutePath() + ".obj");
		}

		try (Reader bufferedReader = new BufferedReader(new FileReader(sourceFile))) {
			LOG.info("Prevodim: " + sourceFile.getAbsolutePath());

			Yylex lexer = new Yylex(bufferedReader);

			MJParser parser = new MJParser(lexer);
			Symbol rootSymbol = parser.parse(); // Parsiranje
			Program prog = (Program) (rootSymbol.value);
			LOG.info(prog.toString("")); // Ispis sintaksnog stabla

			LOG.info("===================================");
			Tab.init();
			SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
			prog.traverseBottomUp(semanticAnalyzer);

			tsdump();

			if (semanticAnalyzer.getNumberOfErrors() > 0) {
				LOG.info("Semantički prolaz je detektovao " + semanticAnalyzer.getNumberOfErrors() + " gresaka");
				return;
			}

			LOG.info("Program " + prog.getProgramName().getName() + " je semanticki ispravan");
			LOG.info("Ukupno globalnih promenljivih: " + semanticAnalyzer.getNumberOfGlobalVars());
			LOG.info("===================================");
			CodeGenerator codeGenerator = new CodeGenerator();
			prog.traverseBottomUp(codeGenerator);

			Code.dataSize = semanticAnalyzer.getNumberOfGlobalVars();
			Code.mainPc = codeGenerator.getMainPc();

			if (objFile.exists()) {
				objFile.delete();
			}

			LOG.info("Writing to " + objFile.getPath());
			try (OutputStream outputStream = new FileOutputStream(objFile)) {
				Code.write(outputStream);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			LOG.error(ex.getMessage(), ex);
		}
	}
}