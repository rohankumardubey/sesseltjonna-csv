package com.github.skjolber.stcsv;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

public class AbstractCsvTest {

	protected File file = new File("src/test/resources/gtfs/trips-plain-5000.txt");
	protected File quotedFile = new File("src/test/resources/gtfs/trips-quoted-5000.txt");
	protected File semicolonFile = new File("src/test/resources/gtfs/trips-plain-semicolon-5000.txt");
	protected File quotedSemicolonFile = new File("src/test/resources/gtfs/trips-quoted-semicolon-5000.txt");

	protected void compare(CsvParser referenceParser, CsvReader<String[]> factory) throws Exception {
		int count = 0;
		
		boolean fail = false;
		do {
			String[] trip = factory.next();
			if(trip == null) {
				break;
			}
		
			String[] row = referenceParser.parseNext();
			if(row == null) {
				System.out.println("Empty row returned");
				for(int i = 0; i < trip.length; i++) {
					System.err.println(trip[i]);
				}
				throw new RuntimeException("");
			}
			for(int i = 0; i < trip.length; i++) {
				//System.out.println(trip[i]);
				if(!Objects.equals(trip[i], row[i])) {
					System.err.println("Line " + count + " column " + i + ": reference '" + trip[i] + "' vs ours '" + row[i] + "'");
					
					fail = true;
					
					throw new RuntimeException();
				}
			}
			count++;
		} while(true);

		assertFalse(fail);
	}

	public static CsvParser referenceParser(File file, Charset charset, boolean crnl) throws Exception {
		InputStream input = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(input, charset);
		
		return referenceParser(reader, charset, crnl ? "\r\n" : "\n", '"', '"', ',');
	}
	
	public static CsvParser referenceParser(File file, Charset charset, boolean crnl, char quote, char escape, char seperator) throws Exception {
		InputStream input = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(input, charset);

		return referenceParser(reader, charset, crnl ? "\r\n" : "\n", quote, escape, seperator);
	}
	
	public static CsvParser referenceParser(Reader reader, Charset charset, String lineSeperator, char quote, char escape, char seperator) throws Exception {
		CsvParserSettings settings = new CsvParserSettings();
		//the file used in the example uses '\n' as the line separator sequence.
		//the line separator sequence is defined here to ensure systems such as MacOS and Windows
		//are able to process this file correctly (MacOS uses '\r'; and Windows uses '\r\n').
		
		CsvFormat format = settings.getFormat();
		format.setLineSeparator(lineSeperator);
		format.setDelimiter(seperator);
		format.setQuote(quote);
		format.setQuoteEscape(escape);

		settings.setIgnoreLeadingWhitespaces(false);
		settings.setIgnoreTrailingWhitespaces(false);
		settings.setSkipEmptyLines(false);
		settings.setColumnReorderingEnabled(false);
		
		//##CODE_START

		// creates a CSV parser
		CsvParser parser = new CsvParser(settings);
		
		if(lineSeperator.equals("\r\n")) {
			parser.beginParsing(new CarriageReturnNewLineReader(reader));		
		} else {
			parser.beginParsing(reader);		
		}
		
		return parser;
	}	
}
