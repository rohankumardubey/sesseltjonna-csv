package com.github.skjolber.stcsv;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.github.skjolber.stcsv.sa.DefaultStringArrayCsvReader;

public class AbstractCsvReaderTest extends AbstractCsvTest {

	@Test
	public void testReader1() throws Throwable {
		
		String line = "abcdefghijklmnopqrstuvwxyz,0123456789\n";
		
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < 1024 * 64; i++) {
			builder.append(line);
		}
		
		String string = builder.toString();
		
		StringReader reader = new StringReader(string);
		
		DefaultStringArrayCsvReader r = new DefaultStringArrayCsvReader(reader, 2, '"', '\'', ',');

		int count = 0;

		for(int i = 0; i < 1024; i++) {
			r.next();
			count++;
		}
		
		Reader subreader = r.getReader();

		String nextLine;
		while( (nextLine = readLine(subreader)) != null) {
			count++;
			assertEquals(line, nextLine);
		}
		
		assertEquals(1024 * 64, count);

	}

	@Test
	public void testReader2() throws Throwable {
		
		String line = "abcdefghijklmnopqrstuvwxyz,0123456789";
		
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < 1024 * 64; i++) {
			builder.append(line);
			builder.append("\n");
		}
		
		String string = builder.toString();
		
		StringReader reader = new StringReader(string);
		
		DefaultStringArrayCsvReader r = new DefaultStringArrayCsvReader(reader, 2, '"', '\'', ',');

		int count = 0;

		for(int i = 0; i < 1024; i++) {
			r.next();
			
			count++;
		}
		
		BufferedReader subreader = new BufferedReader(r.getReader());
		
		String nextLine;
		while( (nextLine = subreader.readLine()) != null) {
			count++;
			assertEquals(line, nextLine);
		}
		assertEquals(1024 * 64, count);
	}

	private String readLine(Reader subreader) throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		
		int character;
		while( (character = subreader.read()) != -1) {
			stringBuilder.append((char)character);
			
			if(character == '\n') {
				break;
			}
		}
		if(stringBuilder.length() == 0) {
			return null;
		}
		return stringBuilder.toString();
	}
	
}
