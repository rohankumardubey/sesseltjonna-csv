package com.github.skjolber.stcsv.builder;

import com.github.skjolber.stcsv.AbstractCsvReader;

public abstract class AbstractCsvBuilder<B>  {

	protected static final boolean byteBuddy;
	
	static {
		boolean present;
		try {
			Class.forName("net.bytebuddy.ByteBuddy");
			
			present = true;
		} catch(Exception e) {
			present = false;
		}
		byteBuddy = present;
	}
	
	protected static boolean isSafeByteUTF8Delimiter(char c) {
		//https://en.wikipedia.org/wiki/UTF-8#Description
		// all char 2, 3 and 4 are negative numbers in UTF-8
		return c >= 0;
	}
	
	protected static boolean isSafeCharDelimiter(char c) {
		return !Character.isLowSurrogate(c);
	}
	
	protected char divider = ',';
	protected char quoteCharacter = '"';
	protected char escapeCharacter = '"';
	
	protected boolean skipComments = false;
	protected boolean skipEmptyLines = false;
	protected boolean skippableFieldsWithoutLinebreaks = false;
	protected int bufferLength = AbstractCsvReader.DEFAULT_RANGE_LENGTH;

	public B skipEmptyLines() {
		this.skipEmptyLines = true;
		
		return (B) this;
	}

	public B skipComments() {
		this.skipComments = true;
		
		return (B) this;
	}

	/**
	 * 
	 * All fields (including any ignored columns) are without linebreaks.
	 * 
	 * @return this
	 */

	public B skippableFieldsWithoutLinebreaks() {
		this.skippableFieldsWithoutLinebreaks = true;
		
		return (B) this;
	}
	
	public B bufferLength(int length) {
		this.bufferLength = length;
		
		return (B) this;
	}
	
	public B divider(char c) {
		if(!isSafeCharDelimiter(c) || c == '\n') {
			throw new CsvBuilderException("Cannot use character '" + c + "' as divider");
		}
		this.divider = c;
		
		return (B) this;
	}
	
	public B quoteCharacter(char c) {
		this.quoteCharacter = c;
		
		return (B) this;
	}
	
	public B escapeCharacter(char c) {
		this.escapeCharacter = c;
		
		return (B) this;
	}

	public char getEscapeCharacter() {
		return escapeCharacter;
	}
	
	public char getQuoteCharacter() {
		return quoteCharacter;
	}

}
