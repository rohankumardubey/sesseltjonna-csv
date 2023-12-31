package com.github.skjolber.stcsv.builder;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.skjolber.stcsv.CsvReader;
import com.github.skjolber.stcsv.EmptyCsvReader;
import com.github.skjolber.stcsv.sa.DefaultStringArrayCsvReader;
import com.github.skjolber.stcsv.sa.NoLinebreakStringArrayCsvReader;
import com.github.skjolber.stcsv.sa.rfc4180.NoLinebreakRFC4180StringArrayCsvReader;
import com.github.skjolber.stcsv.sa.rfc4180.RFC4180StringArrayCsvReader;

public class StringArrayCsvReaderBuilder extends AbstractCsvBuilder<StringArrayCsvReaderBuilder> {

	protected boolean linebreaks = true;
	protected Map<String, Integer> columnIndexes;

	public CsvReader<String[]> build(Reader reader) throws Exception {
		if(skipComments) {
			throw new CsvBuilderException("Skipping comments not supported");
		}
		if(skipEmptyLines) {
			throw new CsvBuilderException("Skipping empty lines not supported");
		}
		
		CsvReader<String[]> r = reader(reader);
		if(columnIndexes != null) {
			String[] next = r.next();
			if(next != null) {
				List<Integer> source = new ArrayList<>();
				List<Integer> destination = new ArrayList<>();
				
				int maxDestination = -1;
				for(int i = 0; i < next.length; i++) {
					String n = next[i];
					
					Integer integer = columnIndexes.get(n);
					if(integer != null) {
						source.add(i);
						destination.add(integer);
						
						if(maxDestination < integer) {
							maxDestination = integer;
						}
					}
				}

				int[] s = new int[source.size()];
				int[] d = new int[source.size()];
				for(int i = 0; i < s.length; i++) {
					s[i] = source.get(i);
					d[i] = destination.get(i);
				}
				
				return new FixedIndexCsvReader(r, s, d, maxDestination + 1);
				
			}
		}
		return r;
	}

	private CsvReader<String[]> reader(Reader reader) throws IOException {
		
		char[] current = new char[bufferLength + 1];
		int offset = 0;
		int columns;
		try {
	
			do {
				int read = reader.read(current, offset, bufferLength - offset);
				if(read == -1) {
					if(offset > 0) {
						if(current[offset - 1] == '\n') {
							// the input ended with a newline
						} else {
							// artificially insert linebreak after last line
							// so that scanners detects end
							current[offset] = '\n';
							offset++;
						}
					}
					break;
				} else {
					offset += read;
				}
			} while(offset < bufferLength);

			if(offset == 0) {
				return new EmptyCsvReader<>();			
			}

			columns = countColumnsLine(current, offset);
		} catch(Exception e) {
			throw new CsvBuilderException(e);
		}
		if(divider == ',' && quoteCharacter == '"' && escapeCharacter == '"') {
			if(!linebreaks) {
				return new NoLinebreakRFC4180StringArrayCsvReader(reader, current, 0, offset, columns);
			}
			return new RFC4180StringArrayCsvReader(reader, current, 0, offset, columns);
		}
		if(quoteCharacter == escapeCharacter) {
			// TODO add implementation based on RFC4180 implementation
			throw new CsvBuilderException("Identical escape and quote character not supported");
		}
		if(!linebreaks) {
			return new NoLinebreakStringArrayCsvReader(reader, current, 0, offset, columns, quoteCharacter, escapeCharacter, divider);
		}
		return new DefaultStringArrayCsvReader(reader, current, 0, offset, columns, quoteCharacter, escapeCharacter, divider);
	}
	
	private int countColumnsLine(char[] current, int end) {
		int count = 0;

		iterate:
		for(int i = 0; i < end; i++) {
			count++;
			if(current[i] == quoteCharacter) { // first character must be quote if quoted
				while(true) {
					++i;
					if(current[i] == escapeCharacter) {
						if(quoteCharacter == escapeCharacter) {
							i++;
							if (current[i] != quoteCharacter) {
								break;
							}
						} else {
							// skip single character
							i++;
						}
					} else if(current[i] == quoteCharacter) {
						i++;
						break;
					}
				}
			}
			while(i < end) {
				if(current[i] == divider) {
					break;
				} else if(current[i] == '\n') {
					break iterate;
				}
				i++;
			}
		}
		return count;
	}
	
	public StringArrayCsvReaderBuilder quotedWithoutLinebreaks() {
		this.linebreaks = false;
		return this;
	}

	public StringArrayCsvReaderBuilder withColumnMapping(String sourceColumnName, int destinationIndex) {
		if(this.columnIndexes == null) {
			this.columnIndexes = new HashMap<>(32);
		}
		this.columnIndexes.put(sourceColumnName, destinationIndex);
		
		return this;
	}

}
