package com.github.skjolber.stcsv;

import java.io.Reader;
import java.lang.reflect.Constructor;

/**
 * 
 * Static CSV parser generator - for specific parser implementation
 * <br><br>
 * Thread-safe.
 * @param <T> csv line output value 
 */

public class DefaultStaticCsvMapper<T> implements StaticCsvMapper<T> {
	
	// https://stackoverflow.com/questions/28030465/performance-of-invoking-constructor-by-reflection
	
	private final Constructor<? extends AbstractCsvReader<T>> readerConstructor;
	private final Constructor<? extends AbstractCsvReader<T>> readerArrayConstructor;

	public DefaultStaticCsvMapper(Class<? extends AbstractCsvReader<T>> cls) throws Exception {
		if(cls != null) {
			this.readerConstructor = cls.getConstructor(Reader.class);
			this.readerArrayConstructor  = cls.getConstructor(Reader.class, char[].class, int.class, int.class);
		} else {
			this.readerConstructor = null;
			this.readerArrayConstructor = null;
		}
	}

	public AbstractCsvReader<T> newInstance(Reader reader) {
		try {
			if(readerArrayConstructor != null) {
				return readerConstructor.newInstance(reader);
			}
			return new EmptyCsvReader<>();
		} catch (Exception e) {
			throw new RuntimeException(e); // should never happen
		}
	}
	
	public AbstractCsvReader<T> newInstance(Reader reader, char[] current, int offset, int length) {
		try {
			if(readerArrayConstructor != null) {
				return readerArrayConstructor.newInstance(reader, current, offset, length);
			}
			return new EmptyCsvReader<>();
		} catch (Exception e) {
			throw new RuntimeException(e); // should never happen
		}
	}

	/*
	public AbstractCsvClassFactory<T> newInstance(Reader reader, boolean skipHeader) throws IOException {
		if(skipHeader) {
			do {
				int read = reader.read();
				if(read == -1) {
					return new NullCsvClassFactory<T>();
				} 
					
				if(read == (int)'\n') {
					break;
				}
			} while(true);
		}
		try {
			return constructor.newInstance(reader);
		} catch (Exception e) {
			throw new RuntimeException(); // should never happen
		}
	}
	*/
}