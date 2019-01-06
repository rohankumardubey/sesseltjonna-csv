package com.github.skjolber.stcsv;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CALOAD;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.github.skjolber.stcsv.builder.CsvMappingBuilder;
import com.github.skjolber.stcsv.column.bi.CsvColumnValueConsumer;
import com.github.skjolber.stcsv.column.tri.CsvColumnValueTriConsumer;

/**
 * 
 * Dynamic CSV parser generator. Adapts the underlying implementation according 
 * to the first (header) line.
 * <br><br>
 * Uses ASM to build the parsers.
 * <br><br>
 * Thread-safe.
 */

public class AbstractCsvMapper<T> {

	protected static final String GENERATED_CLASS_SIMPLE_NAME = "GeneratedCsvClassFactory%d";
	protected static final String GENERATED_CLASS_FULL_NAME = "com.github.skjolber.stcsv." + GENERATED_CLASS_SIMPLE_NAME;
	protected static final String GENERATED_CLASS_FULL_INTERNAL = "com/github/skjolber/stcsv/" + GENERATED_CLASS_SIMPLE_NAME;

	protected static final String superClassInternalName = getInternalName(AbstractCsvReader.class);
	protected static final String csvStaticInitializer = getInternalName(CsvReaderStaticInitializer.class);
	protected static final String ignoredColumnName = getInternalName(IgnoredColumn.class);
	protected static final String biConsumerName = getInternalName(CsvColumnValueConsumer.class);
	protected static final String triConsumerName = getInternalName(CsvColumnValueTriConsumer.class);

	protected static AtomicInteger counter = new AtomicInteger();

	public static <T> CsvMappingBuilder<T> builder(Class<T> cls) {
		return new CsvMappingBuilder<T>(cls);
	}

	public static String getInternalName(Class<?> cls) {
		return getInternalName(cls.getName());
	}

	public static String getInternalName(String className) {
		return className.replace('.', '/');
	}

	protected int divider;
	protected Class<T> mappedClass;

	protected String mappedClassInternalName;

	protected Map<String, AbstractColumn> keys = new HashMap<>(); // thread safe for reading
	protected List<AbstractColumn> columns;

	protected final boolean skipEmptyLines;
	protected final boolean skipComments;
	protected final boolean skippableFieldsWithoutLinebreaks;
	protected final int bufferLength;

	/**
	 * Note: Stack variable types are fixed throughout the application, as below. 
	 * 
	 * The range index is dual purpose as end index for trimming quoted content.
	 * 
	 */

	protected final int currentOffsetIndex = 1;
	protected final int currentArrayIndex = 2;
	protected final int objectIndex = 3;
	protected final int startIndex = 4;
	protected final int rangeIndex = 5;
	protected final int intermediateIndex = 6;

	protected final Map<String, StaticCsvMapper<T>> factories = new ConcurrentHashMap<>();
	protected final ClassLoader classLoader;
	
	protected final boolean biConsumer;
	protected final boolean triConsumer;

	public AbstractCsvMapper(Class<T> cls, char divider, List<AbstractColumn> columns, boolean skipEmptyLines, boolean skipComments, boolean skippableFieldsWithoutLinebreaks, ClassLoader classLoader, int bufferLength) {
		this.mappedClass = cls;
		this.divider = divider;
		this.columns = columns;

		this.skipEmptyLines = skipEmptyLines;
		this.skipComments = skipComments;
		this.skippableFieldsWithoutLinebreaks = skippableFieldsWithoutLinebreaks;
		this.classLoader = classLoader;
		this.bufferLength = bufferLength;

		boolean biConsumer = false;
		boolean triConsumer = false;
		
		for (AbstractColumn column : columns) {
			keys.put(column.getName(),  column);

			column.setParent(this);
			column.setVariableIndexes(currentArrayIndex, currentOffsetIndex, objectIndex, startIndex, rangeIndex, intermediateIndex);
			
			if(column.isBiConsumer()) {
				biConsumer = true;
			}
			
			if(column.isTriConsumer()) {
				triConsumer = true;
			}
		}

		this.mappedClassInternalName = getInternalName(mappedClass);
		
		this.biConsumer = biConsumer;
		this.triConsumer = triConsumer;
	}

	public Class<T> getMappedClass() {
		return mappedClass;
	}

	public int getDivider() {
		return divider;
	}

	public Class<? extends AbstractCsvReader<T>> createDefaultReaderClass(boolean carriageReturns) throws Exception {
		List<String> names = new ArrayList<>();
		for (AbstractColumn column: columns) {
			names.add(column.getName());
		}
		return createReaderClass(carriageReturns, names);
	}

	public Class<? extends AbstractCsvReader<T>> createReaderClass(boolean carriageReturns, String header) throws Exception {
		return createReaderClass(carriageReturns, parseNames(header));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Class<? extends AbstractCsvReader<T>> createReaderClass(boolean carriageReturns, List<String> csvFileFieldNames) throws Exception {
		ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

		String subClassName = write(classWriter, csvFileFieldNames, carriageReturns);
		if(subClassName == null) {
			return null;
		}
		CsvReaderClassLoader<AbstractCsvReader<T>> loader = new CsvReaderClassLoader<AbstractCsvReader<T>>(classLoader);


		FileOutputStream fout = new FileOutputStream(new File("./my.class"));
		fout.write(classWriter.toByteArray());
		fout.close();

		return loader.load(classWriter.toByteArray(), subClassName);
	}

	protected String write(ClassWriter classWriter, List<String> csvFileFieldNames, boolean carriageReturns) {
		int subclassNumber = counter.incrementAndGet();
		String subClassName = String.format(GENERATED_CLASS_FULL_NAME, subclassNumber);
		String subClassInternalName = String.format(GENERATED_CLASS_FULL_INTERNAL, subclassNumber);

		AbstractColumn[] mapping = new AbstractColumn[csvFileFieldNames.size()]; 

		CsvColumnValueConsumer<?>[] biConsumers = new CsvColumnValueConsumer[mapping.length];
		CsvColumnValueTriConsumer<?, ?>[] triConsumers = new CsvColumnValueTriConsumer[mapping.length];

		boolean inline = true;

		int lastIndex = -1;
		int firstIndex = -1;
		for (int j = 0; j < csvFileFieldNames.size(); j++) {
			String name = csvFileFieldNames.get(j);
			AbstractColumn field = keys.get(name);

			if(field != null) {
				mapping[j] = field;

				if(firstIndex == -1) {
					firstIndex = j;
				}

				biConsumers[j] = field.getBiConsumer();
				triConsumers[j] = field.getTriConsumer();
				lastIndex = j;
			}
		}

		if(lastIndex == -1) {
			return null;
		}

		// generics seems to not work when generating multiple classes; 
		// fails for class number 2 because of failing method signature
		// TODO still generate such a beast for the first?
		classWriter.visit(Opcodes.V1_8,
				ACC_FINAL | ACC_PUBLIC,
				subClassInternalName,
				null,
				superClassInternalName,
				null);

		if(biConsumer || triConsumer) {
			// place in-scope values which will be read by static initializer
			CsvReaderStaticInitializer.add(subClassName, biConsumers, triConsumers);

			// write static fields
			fields(classWriter, mapping);

			// static initializer
			staticInitializer(classWriter, mapping, subClassInternalName, subClassName);
		}

		// constructor with reader
		constructor(classWriter, subClassInternalName);

		// parse main method
		{
			MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "next", "()Ljava/lang/Object;", null, new String[] { "java/io/IOException" });

			mv.visitCode();
			Label startVariableScope = new Label();
			mv.visitLabel(startVariableScope);

			// init offset and char array
			// int currentOffset = this.currentOffset;
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, superClassInternalName, "currentOffset", "I");
			mv.visitVarInsn(ISTORE, currentOffsetIndex);

			mv.visitVarInsn(ILOAD, 1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, superClassInternalName, "currentRange", "I");
			Label l2 = new Label();
			mv.visitJumpInsn(IF_ICMPLT, l2);

			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, superClassInternalName, "fill", "()I", false);
			Label l4 = new Label();
			mv.visitJumpInsn(IFNE, l4);
			mv.visitInsn(ACONST_NULL);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l4);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, currentOffsetIndex);
			mv.visitLabel(l2);

			// final char[] current = this.current;
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, superClassInternalName, "current", "[C");
			mv.visitVarInsn(ASTORE, currentArrayIndex);		    	

			// try-catch block
			Label startTryCatch = new Label();

			Label endVariableScope = new Label();

			//Label endTryCatch = new Label();
			Label exceptionHandling = new Label();
			mv.visitTryCatchBlock(startTryCatch, endVariableScope, exceptionHandling, "java/lang/ArrayIndexOutOfBoundsException");

			mv.visitLabel(startTryCatch);

			if(skipEmptyLines && skipComments) {
				writeSkipEmptyOrCommentedLines(mv, subClassInternalName, carriageReturns);
			} else if(skipEmptyLines) {
				writeSkipEmptyLines(mv, subClassInternalName, carriageReturns);
			} else if(skipComments) {
				writeSkipComments(mv, subClassInternalName);
			}
			
			// init value object, i.e. the object to which data-binding will occur
			mv.visitTypeInsn(NEW, getInternalName(mappedClass.getName()));
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, getInternalName(mappedClass.getName()), "<init>", "()V", false);
			mv.visitVarInsn(ASTORE, objectIndex);

			if(firstIndex > 0) {
				// skip first column(s)
				skipColumns(mv, firstIndex);
			}

			boolean wroteTriConsumer = false;
			
			int current = firstIndex;
			do {
				AbstractColumn column = mapping[current];
				if(column.isTriConsumer() && !wroteTriConsumer) {
					writeTriConsumerVariable(subClassInternalName, mv); 	
				}
				if(current == mapping.length - 1) {
					column.last(mv, subClassInternalName, carriageReturns, inline);
				} else {
					column.middle(mv, subClassInternalName, inline);
				}

				// at last
				if(current == lastIndex) {
					if(lastIndex + 1 < mapping.length) {
						// skip rest of line
						skipToLinebreak(mv);
					}
					break;
				} else {
					int previous = current;

					current++;

					while(mapping[current] == null) {
						current++;
					}

					if(current - previous > 1) {
						// skip middle column
						skipColumns(mv, current - previous - 1);
					}
				}
			} while(true);

			// save value
			saveCurrentOffset(mv, superClassInternalName, currentOffsetIndex);		    	

			// return object
			mv.visitVarInsn(ALOAD, objectIndex);
			mv.visitInsn(ARETURN);

			mv.visitLabel(endVariableScope);

			// throw block
			mv.visitLabel(exceptionHandling);
			mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/ArrayIndexOutOfBoundsException"});
			mv.visitVarInsn(ASTORE, 1);
			Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitLineNumber(33, l3);
			mv.visitTypeInsn(NEW, "com/github/skjolber/stcsv/CsvException");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKESPECIAL, "com/github/skjolber/stcsv/CsvException", "<init>", "(Ljava/lang/Throwable;)V", false);
			mv.visitInsn(ATHROW);

			mv.visitLocalVariable("this", "L" + subClassInternalName + ";", null, startVariableScope, endVariableScope, 0);
			mv.visitLocalVariable("value", "L" + mappedClassInternalName + ";", null, startVariableScope, endVariableScope, objectIndex);
			mv.visitLocalVariable("currentOffset", "I", null, startVariableScope, endVariableScope, currentOffsetIndex);
			mv.visitLocalVariable("current", "[C", null, startVariableScope, endVariableScope, currentArrayIndex);
			if(inline) {
				mv.visitLocalVariable("start", "I", null, startVariableScope, endVariableScope, startIndex);
				mv.visitLocalVariable("rangeIndex", "I", null, startVariableScope, endVariableScope, rangeIndex);
			}

			mv.visitMaxs(0, 0); // calculated by the asm library
			mv.visitEnd();
		}

		classWriter.visitEnd();
		return subClassName;
	}

	protected void writeTriConsumerVariable(String subClassInternalName, MethodVisitor mv) {
	}

	protected void constructor(ClassWriter classWriter, String subClassInternalName) {
		constructor(classWriter, subClassInternalName, null);
	}

	private void writeSkipComments(MethodVisitor mv, String subClassInternalName) {
		final int rangeVariableIndex = 3;

		Label l12 = new Label();
		mv.visitJumpInsn(GOTO, l12);
		Label l13 = new Label();
		mv.visitLabel(l13);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, subClassInternalName, "currentRange", "I");
		mv.visitVarInsn(ISTORE, rangeVariableIndex);
		Label l14 = new Label();
		mv.visitLabel(l14);
		mv.visitIincInsn(currentOffsetIndex, 1);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 10);
		mv.visitJumpInsn(IF_ICMPNE, l14);
		Label l16 = new Label();
		mv.visitLabel(l16);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitVarInsn(ILOAD, rangeVariableIndex);
		Label l17 = new Label();
		mv.visitJumpInsn(IF_ICMPNE, l17);
		Label l18 = new Label();
		mv.visitLabel(l18);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKEVIRTUAL, subClassInternalName, "fill", "()I", false);
		mv.visitInsn(DUP);
		mv.visitVarInsn(ISTORE, rangeVariableIndex);
				
		
		Label l10 = new Label();
		mv.visitJumpInsn(IFNE, l10);
		mv.visitInsn(ACONST_NULL);
		mv.visitInsn(ARETURN);
		mv.visitLabel(l10);	
		
		mv.visitInsn(ICONST_0);
		mv.visitVarInsn(ISTORE, currentOffsetIndex);
		mv.visitJumpInsn(GOTO, l12);
		mv.visitLabel(l17);
		mv.visitIincInsn(currentOffsetIndex, 1);
		mv.visitLabel(l12);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 35); // #
		mv.visitJumpInsn(IF_ICMPEQ, l13);



	}

	protected void skipToLinebreak(MethodVisitor mv) {
		if(skippableFieldsWithoutLinebreaks) {
			//skipToLineBreakWithoutLinebreak							
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitMethodInsn(INVOKESTATIC, ignoredColumnName, "skipToLineBreakWithoutLinebreak", "(L" + superClassInternalName + ";[CI)I", false);
			mv.visitVarInsn(ISTORE, currentOffsetIndex);
		} else {
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitMethodInsn(INVOKESTATIC, ignoredColumnName, "skipToLineBreak", "(L" + superClassInternalName + ";[CI)I", false);
			mv.visitVarInsn(ISTORE, currentOffsetIndex);
		}
	}

	protected void skipColumns(MethodVisitor mv, int count) {
		if(skippableFieldsWithoutLinebreaks) {
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitLdcInsn(new Integer(divider));
			mv.visitLdcInsn(new Integer(count));
			mv.visitMethodInsn(INVOKESTATIC, ignoredColumnName, "skipColumnsWithoutLinebreak", "([CICI)I", false);
			mv.visitVarInsn(ISTORE, currentOffsetIndex);
		} else {
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitLdcInsn(new Integer(divider));
			mv.visitLdcInsn(new Integer(count));
			mv.visitMethodInsn(INVOKESTATIC, ignoredColumnName, "skipColumns", "(L" + superClassInternalName + ";[CICI)I", false);
			mv.visitVarInsn(ISTORE, currentOffsetIndex);
		}
	}

	protected void writeSkipEmptyOrCommentedLines(MethodVisitor mv, String subClassInternalName, boolean carriageReturns) {
		/*

			while (current[currentOffset] == '#' || current[currentOffset] == '\n' ) {
				int value = this.currentRange;

				while(true) {
					if(current[currentOffset] == '\n') {
						if (currentOffset == value) {
							if ((value = this.fill()) == 0) {
								return null;
							}
	
							currentOffset = 0;
						} else {
							currentOffset++;
						}
						break;
					}
					currentOffset++;
				}
			}
			
		
		*/
		final int rangeVariableIndex = 3;

		Label l11 = new Label();
		mv.visitLabel(l11);
		Label l12 = new Label();
		mv.visitJumpInsn(GOTO, l12);
		Label l13 = new Label();
		mv.visitLabel(l13);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, subClassInternalName, "currentRange", "I");
		mv.visitVarInsn(ISTORE, rangeVariableIndex);
		Label l14 = new Label();
		mv.visitLabel(l14);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 10);
		Label l15 = new Label();
		mv.visitJumpInsn(IF_ICMPNE, l15);
		Label l16 = new Label();
		mv.visitLabel(l16);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitVarInsn(ILOAD, rangeVariableIndex);
		Label l17 = new Label();
		mv.visitJumpInsn(IF_ICMPNE, l17);
		Label l18 = new Label();
		mv.visitLabel(l18);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKEVIRTUAL, subClassInternalName, "fill", "()I", false);
		mv.visitInsn(DUP);
		mv.visitVarInsn(ISTORE, rangeVariableIndex);
		
		Label l10 = new Label();
		mv.visitJumpInsn(IFNE, l10);
		mv.visitInsn(ACONST_NULL);
		mv.visitInsn(ARETURN);
		mv.visitLabel(l10);
		
		mv.visitInsn(ICONST_0);
		mv.visitVarInsn(ISTORE, currentOffsetIndex);
		mv.visitJumpInsn(GOTO, l12);
		mv.visitLabel(l17);
		mv.visitIincInsn(currentOffsetIndex, 1);
		mv.visitJumpInsn(GOTO, l12);
		mv.visitLabel(l15);
		mv.visitIincInsn(currentOffsetIndex, 1);
		mv.visitJumpInsn(GOTO, l14);
		mv.visitLabel(l12);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 35); // #
		mv.visitJumpInsn(IF_ICMPEQ, l13);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, carriageReturns ? 13: 10); // n or r
		mv.visitJumpInsn(IF_ICMPEQ, l13);

	}

	protected void writeSkipEmptyLines(MethodVisitor mv, String subClassInternalName, boolean carriageReturns) {
		if(!carriageReturns) {
			/**
			if (current[currentOffset] == '\n') {
				int value = this.currentRange;

				do {
					if (currentOffset == value) {
						if ((value = this.fill()) == 0) {
							return null;
						}

						currentOffset = 0;
					} else {
						++currentOffset;
					}
				} while (current[currentOffset] != '\n');
			}					
			 */
			final int rangeVariableIndex = 3;
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitInsn(CALOAD);
			mv.visitIntInsn(BIPUSH, 10); // \n
			Label l5 = new Label();
			mv.visitJumpInsn(IF_ICMPNE, l5);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, subClassInternalName, "currentRange", "I");
			mv.visitVarInsn(ISTORE, rangeVariableIndex);
			Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitVarInsn(ILOAD, rangeVariableIndex);
			Label l8 = new Label();
			mv.visitJumpInsn(IF_ICMPNE, l8);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, subClassInternalName, "fill", "()I", false);
			mv.visitInsn(DUP);
			mv.visitVarInsn(ISTORE, rangeVariableIndex);
			Label l10 = new Label();
			mv.visitJumpInsn(IFNE, l10);
			mv.visitInsn(ACONST_NULL);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l10);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, currentOffsetIndex);
			Label l13 = new Label();
			mv.visitJumpInsn(GOTO, l13);
			mv.visitLabel(l8);
			mv.visitIincInsn(currentOffsetIndex, 1);
			mv.visitLabel(l13);
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitInsn(CALOAD);
			mv.visitIntInsn(BIPUSH, 10); // \n
			mv.visitJumpInsn(IF_ICMPEQ, l7);
			mv.visitLabel(l5);

		} else {

			/**
			if (current[currentOffset] == '\r') {
				int currentRange = this.currentRange;
				++currentOffset;

				while (current[currentOffset] == '\n') {
					if (currentOffset == currentRange) {
						if ((currentRange = this.fill()) == 0) {
							return null;
						}

						currentOffset = 0;
					} else {
						++currentOffset;

						if(current[currentOffset] == '\r') {
							++currentOffset;
						}
					}
				}
			}					
			 */
			final int rangeVariableIndex = 3;

			Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitInsn(CALOAD);
			mv.visitIntInsn(BIPUSH, 13);
			Label l5 = new Label();
			mv.visitJumpInsn(IF_ICMPNE, l5);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, subClassInternalName, "currentRange", "I");
			mv.visitVarInsn(ISTORE, 3);
			mv.visitIincInsn(currentOffsetIndex, 1);
			Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitInsn(CALOAD);
			mv.visitIntInsn(BIPUSH, 10);
			mv.visitJumpInsn(IF_ICMPNE, l5);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitVarInsn(ILOAD, rangeVariableIndex);
			Label l10 = new Label();
			mv.visitJumpInsn(IF_ICMPNE, l10);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, subClassInternalName, "fill", "()I", false);
			mv.visitInsn(DUP);
			mv.visitVarInsn(ISTORE, rangeVariableIndex);
			Label l12 = new Label();
			mv.visitJumpInsn(IFNE, l12);
			mv.visitInsn(ACONST_NULL);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l12);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, currentOffsetIndex);
			mv.visitJumpInsn(GOTO, l8);
			mv.visitLabel(l10);
			mv.visitIincInsn(currentOffsetIndex, 1);
			mv.visitVarInsn(ALOAD, currentArrayIndex);
			mv.visitVarInsn(ILOAD, currentOffsetIndex);
			mv.visitInsn(CALOAD);
			mv.visitIntInsn(BIPUSH, 13);
			mv.visitJumpInsn(IF_ICMPNE, l8);
			mv.visitIincInsn(currentOffsetIndex, 1);
			mv.visitJumpInsn(GOTO, l8);
			mv.visitLabel(l5);
		}
	}

	protected void staticInitializer(ClassWriter classWriter, AbstractColumn[] columns, String classInternalName, String className) {
		MethodVisitor mv = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();

		Label startLabel = new Label();
		mv.visitLabel(startLabel);

		int biConsumerArrayIndex = 1;
		int triConsumerArrayIndex = 2;

		mv.visitLdcInsn(className);
		mv.visitMethodInsn(INVOKESTATIC, csvStaticInitializer, "remove", "(Ljava/lang/String;)Lcom/github/skjolber/stcsv/CsvReaderStaticInitializer$CsvStaticFields;", false);
		mv.visitVarInsn(ASTORE, 0);
		mv.visitVarInsn(ALOAD, 0);
		
		if(biConsumer) {
			mv.visitMethodInsn(INVOKEVIRTUAL, "com/github/skjolber/stcsv/CsvReaderStaticInitializer$CsvStaticFields", "getBiConsumers", "()[L" + biConsumerName + ";", false);
			mv.visitVarInsn(ASTORE, biConsumerArrayIndex);
		}
		
		if(triConsumer) {
			mv.visitMethodInsn(INVOKEVIRTUAL, "com/github/skjolber/stcsv/CsvReaderStaticInitializer$CsvStaticFields", "getTriConsumers", "()[L" + triConsumerName + ";", false);
			mv.visitVarInsn(ASTORE, triConsumerArrayIndex);
		}
		
		// consumers
		for (int k = 0; k < columns.length; k++) {
			if(columns[k] != null) {
				if(biConsumer && columns[k].isBiConsumer()) {
					mv.visitVarInsn(ALOAD, biConsumerArrayIndex);
					mv.visitLdcInsn(new Integer(k));
					mv.visitInsn(AALOAD);
					mv.visitTypeInsn(CHECKCAST, columns[k].getBiConsumerInternalName());
					mv.visitFieldInsn(PUTSTATIC, classInternalName, "v" + columns[k].getIndex(), "L" + columns[k].getBiConsumerInternalName() + ";");
				} else if(triConsumer && columns[k].isTriConsumer()) {
					mv.visitVarInsn(ALOAD, triConsumerArrayIndex);
					mv.visitLdcInsn(new Integer(k));
					mv.visitInsn(AALOAD);
					mv.visitTypeInsn(CHECKCAST, columns[k].getTriConsumerInternalName());
					mv.visitFieldInsn(PUTSTATIC, classInternalName, "v" + columns[k].getIndex(), "L" + columns[k].getTriConsumerInternalName() + ";");
				}
			}
		}

		Label endLabel = new Label();
		mv.visitLabel(endLabel);
		mv.visitInsn(RETURN);
		mv.visitLocalVariable("fields", "Lcom/github/skjolber/stcsv/CsvReaderStaticInitializer$CsvStaticFields;", null, startLabel, endLabel, 0);
		if(biConsumer) {
			mv.visitLocalVariable("biConsumerList", "[L" + biConsumerName + ";", null, startLabel, endLabel, biConsumerArrayIndex);
		}
		if(triConsumer) {
			mv.visitLocalVariable("triConsumerList", "[L" + triConsumerName + ";", null, startLabel, endLabel, triConsumerArrayIndex);
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();		    	
	}

	protected void fields(ClassWriter classWriter, AbstractColumn[] mapping) {
		// static final fields
		for (int k = 0; k < mapping.length; k++) {
			if(mapping[k] != null) {
				if(mapping[k].isBiConsumer()) {
					classWriter
					.visitField(ACC_STATIC + ACC_PRIVATE + ACC_FINAL, "v" + mapping[k].getIndex(), "L" + mapping[k].getBiConsumerInternalName() + ";", null, null)
					.visitEnd();
				} else if(mapping[k].isTriConsumer()) {
					classWriter
					.visitField(ACC_STATIC + ACC_PRIVATE + ACC_FINAL, "v" + mapping[k].getIndex(), "L" + mapping[k].getTriConsumerInternalName() + ";", null, null)
					.visitEnd();
				}
			}
		}
	}

	protected void constructor(ClassWriter classWriter, String subClassInternalName, String intermediateInternalName) {
		{
			String signature;
			if(intermediateInternalName == null) {
				signature = "(Ljava/io/Reader;)V";
			} else {
				signature = "(Ljava/io/Reader;L" + intermediateInternalName + ";)V";
			}
			
			MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "<init>", signature, null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitLdcInsn(new Integer(bufferLength));
			mv.visitMethodInsn(INVOKESPECIAL, superClassInternalName, "<init>", "(Ljava/io/Reader;I)V", false);
			
			if(intermediateInternalName != null) {
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ALOAD, 2);
				mv.visitFieldInsn(PUTFIELD, subClassInternalName, "intermediate", "L" + intermediateInternalName + ";");				
			}
			
 			mv.visitInsn(RETURN);
			Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitLocalVariable("this", "L" + subClassInternalName + ";", null, l0, l2, 0);
			mv.visitLocalVariable("reader", "Ljava/io/Reader;", null, l0, l2, 1);
			if(intermediateInternalName != null) {
				mv.visitLocalVariable("intermediateInternalName", "Ljava/lang/String;", null, l0, l2, 2);
			}
			
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		{
			
			String signature;
			if(intermediateInternalName == null) {
				signature = "(Ljava/io/Reader;[CII)V";
			} else {
				signature = "(Ljava/io/Reader;[CIIL" + intermediateInternalName + ";)V";
			}

			MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, "<init>", signature, null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ILOAD, 3);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitMethodInsn(INVOKESPECIAL, superClassInternalName, "<init>", "(Ljava/io/Reader;[CII)V", false);
			
			if(intermediateInternalName != null) {
				mv.visitVarInsn(ALOAD, 0);
				mv.visitVarInsn(ALOAD, 5);
				mv.visitFieldInsn(PUTFIELD, subClassInternalName, "intermediate", "L" + intermediateInternalName + ";");				
			}

			
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitInsn(RETURN);
			Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitLocalVariable("this", "L" + subClassInternalName + ";", null, l0, l2, 0);
			mv.visitLocalVariable("reader", "Ljava/io/Reader;", null, l0, l2, 1);
			mv.visitLocalVariable("current", "[C", null, l0, l2, 2);
			mv.visitLocalVariable("offset", "I", null, l0, l2, 3);
			mv.visitLocalVariable("length", "I", null, l0, l2, 4);
			
			if(intermediateInternalName != null) {
				mv.visitLocalVariable("intermediateInternalName", "Ljava/lang/String;", null, l0, l2, 5);
			}

			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
	}

	protected String parseStaticFieldName(Class<?> cls) {
		String simpleName = cls.getSimpleName();

		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
	}

	protected void saveCurrentOffset(MethodVisitor mv, String superClassInternalName, int currentOffsetIndex) {
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitFieldInsn(PUTFIELD, superClassInternalName, "currentOffset", "I");		
	}

	protected List<String> parseNames(String writer) {
		List<String> names = new ArrayList<>();
		int start = 0;
		for(int i = 0; i < writer.length(); i++) {
			if(writer.charAt(i) == divider) {
				String trim = writer.substring(start, i).trim();
				if(!trim.isEmpty() && trim.charAt(0) == '"' && trim.charAt(trim.length() - 1) == '"') {
					names.add(trim.substring(1, trim.length() - 1));
				} else {
					names.add(trim);
				}
				start = i + 1;
			}
		}

		if(start < writer.length()) {
			String trim = writer.substring(start, writer.length()).trim();
			if(trim.charAt(0) == '"' && trim.charAt(trim.length() - 1) == '"') {
				names.add(trim.substring(1, trim.length() - 1));
			} else {
				names.add(trim);
			}
		}
		return names;
	}

	public String getMappedClassInternalName() {
		return mappedClassInternalName;
	}
	
	
}
