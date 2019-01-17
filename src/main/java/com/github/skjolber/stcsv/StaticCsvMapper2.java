package com.github.skjolber.stcsv;

import java.io.Reader;
import java.lang.reflect.Constructor;

/**
 * 
 * Static CSV parser generator - for specific parser implementation
 * <br><br>
 * Thread-safe.
 * @param <T> csv line output value 
 * @param <T> intermediate processor
 */

public class StaticCsvMapper2<T, D> {
	
	// https://stackoverflow.com/questions/28030465/performance-of-invoking-constructor-by-reflection
	private final Constructor<? extends AbstractCsvReader<T>> readerConstructor;
	private final Constructor<? extends AbstractCsvReader<T>> readerArrayConstructor;

	public StaticCsvMapper2(Class<? extends AbstractCsvReader<T>> cls, Class<D> delegate) throws Exception {
		this.readerConstructor = cls.getConstructor(Reader.class, delegate);
		this.readerArrayConstructor  = cls.getConstructor(Reader.class, char[].class, int.class, int.class, delegate);
	}

	public AbstractCsvReader<T> newInstance(Reader reader, D delegate) {
		try {
			return readerConstructor.newInstance(reader, delegate);
		} catch (Exception e) {
			throw new RuntimeException(); // should never happen
		}
	}
	
	public AbstractCsvReader<T> newInstance(Reader reader, char[] current, int offset, int length, D delegate) {
		try {
			return readerArrayConstructor.newInstance(reader, current, offset, length, delegate);
		} catch (Exception e) {
			throw new RuntimeException(); // should never happen
		}
	}
}