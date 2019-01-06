package com.github.skjolber.stcsv;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CALOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.ISUB;
import static org.objectweb.asm.Opcodes.NEW;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import com.github.skjolber.stcsv.column.bi.CsvColumnValueConsumer;
import com.github.skjolber.stcsv.column.bi.StringCsvColumnValueConsumer;
import com.github.skjolber.stcsv.column.tri.CsvColumnValueTriConsumer;
import com.github.skjolber.stcsv.column.tri.StringCsvColumnValueTriConsumer;

public abstract class AbstractColumn {

	protected final String name;
	protected final int index;
	protected final boolean optional;
	
	protected final boolean trimTrailingWhitespaces;
	protected final boolean trimLeadingWhitespaces;

	protected AbstractCsvMapper<?> parent;

	protected int currentArrayIndex;
	protected int currentOffsetIndex;
	protected int objectIndex;
	protected int startIndex;
	protected int rangeIndex;
	protected int intermediateIndex;
	
	protected String biConsumerInternalName;
	protected CsvColumnValueConsumer<?> biConsumer;
	
	protected String triConsumerInternalName;
	protected CsvColumnValueTriConsumer<?, ?> triConsumer;

	protected String setterName;
	protected Class<?> setterClass;
	
	protected Class<?> intermediate;

	public AbstractColumn(String name, int index, boolean optional, boolean trimTrailingWhitespaces, boolean trimLeadingWhitespaces) {
		this.name = name;
		this.index = index;
		this.optional = optional;
		this.trimTrailingWhitespaces = trimTrailingWhitespaces;
		this.trimLeadingWhitespaces = trimLeadingWhitespaces;
	}

	public void setVariableIndexes(int currentArrayIndex, int currentOffsetIndex, int objectIndex, int startIndex, int rangeIndex, int intermediateIndex) {
		this.currentArrayIndex = currentArrayIndex;
		this.currentOffsetIndex = currentOffsetIndex;
		this.objectIndex = objectIndex;
		this.startIndex = startIndex;
		this.rangeIndex = rangeIndex;
		this.intermediateIndex = intermediateIndex;
	}

	public void middle(MethodVisitor mv, String subClassInternalName, boolean inline) {
		inline(mv, subClassInternalName, parent.getDivider(), 1);
	}

	public void last(MethodVisitor mv, String subClassInternalName, boolean carriageReturn, boolean inline) {
		if(carriageReturn) {
			inline(mv, subClassInternalName, '\r', 2); // \r\n
		} else {
			inline(mv, subClassInternalName, '\n', 1); // \n
		}
	}	
	
	protected void inline(MethodVisitor mv, String subClassInternalName, int divider, int increment) {
	}

	public String getName() {
		return name;
	}
	
	public CsvColumnValueConsumer<?> getBiConsumer() {
		return biConsumer;
	}
	
	public void setBiConsumer(CsvColumnValueConsumer<?> consumer) {
		this.biConsumer = consumer;
	}

	public void setTriConsumer(CsvColumnValueTriConsumer<?, ?> consumer, Class<?> intermediate) {
		this.triConsumer = consumer;
		this.intermediate = intermediate;
	}

	public void setParent(AbstractCsvMapper<?> parent) {
		this.parent = parent;
	}

	public int getIndex() {
		return index;
	}
	
	public String getBiConsumerInternalName() {
		if(biConsumerInternalName == null) {
			if(biConsumer.getClass().getPackage().equals(StringCsvColumnValueConsumer.class.getPackage())) {
				biConsumerInternalName = CsvMapper.getInternalName(biConsumer.getClass());
			} else {
				biConsumerInternalName = CsvMapper.biConsumerName;
			}
			
		}
		return biConsumerInternalName;
	}
	

	public String getTriConsumerInternalName() {
		if(triConsumerInternalName == null) {
			if(triConsumer.getClass().getPackage().equals(StringCsvColumnValueTriConsumer.class.getPackage())) {
				// specific subclass
				triConsumerInternalName = CsvMapper.getInternalName(triConsumer.getClass());
			} else {
				triConsumerInternalName = CsvMapper.triConsumerName;
			}
		}
		return triConsumerInternalName;
	}

	
	public void setSetter(String setterName, Class<?> setterClass) {
		this.setterName = setterName;
		this.setterClass = setterClass;
	}
	
	public boolean isBiConsumer() {
		return biConsumer != null;
	}

	public boolean isTriConsumer() {
		return triConsumer != null;
	}

	protected void doubleSetter(MethodVisitor mv, int endIndex) {
		mv.visitVarInsn(ALOAD, objectIndex);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		mv.visitMethodInsn(INVOKESTATIC, "com/github/skjolber/stcsv/column/bi/DoubleCsvColumnValueConsumer", "parseDouble", "([CII)D", false);
		mv.visitMethodInsn(INVOKEVIRTUAL, parent.getMappedClassInternalName(), setterName, "(D)V", false);		
	}

	protected void booleanSetter(MethodVisitor mv, int endIndex) {
		mv.visitVarInsn(ALOAD, objectIndex);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		mv.visitMethodInsn(INVOKESTATIC, "com/github/skjolber/stcsv/column/bi/BooleanCsvColumnValueConsumer", "parseBoolean", "([CII)Z", false);
		mv.visitMethodInsn(INVOKEVIRTUAL, parent.getMappedClassInternalName(), setterName, "(Z)V", false);		
	}

	protected void longSetter(MethodVisitor mv, int endIndex) {
		mv.visitVarInsn(ALOAD, objectIndex);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		mv.visitMethodInsn(INVOKESTATIC, "com/github/skjolber/stcsv/column/bi/LongCsvColumnValueConsumer", "parseLong", "([CII)J", false);
		mv.visitMethodInsn(INVOKEVIRTUAL, parent.getMappedClassInternalName(), setterName, "(J)V", false);		
	}	
	
	protected void intSetter(MethodVisitor mv, int endIndex) {
		mv.visitVarInsn(ALOAD, objectIndex);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		mv.visitMethodInsn(INVOKESTATIC, "com/github/skjolber/stcsv/column/bi/IntCsvColumnValueConsumer", "parseInt", "([CII)I", false);
		mv.visitMethodInsn(INVOKEVIRTUAL, parent.getMappedClassInternalName(), setterName, "(I)V", false);		
	}

	protected void stringSetter(MethodVisitor mv, int endIndex) {
		mv.visitVarInsn(ALOAD, objectIndex);
		mv.visitTypeInsn(NEW, "java/lang/String");
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitInsn(ISUB);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([CII)V", false);
		mv.visitMethodInsn(INVOKEVIRTUAL, parent.getMappedClassInternalName(), setterName, "(Ljava/lang/String;)V", false);
	}
	
	protected void writeSetter(MethodVisitor mv, String subClassInternalName, int endIndex) {
		if(setterClass == String.class) {
			stringSetter(mv, endIndex);
		} else if(setterClass == int.class) {
			intSetter(mv, endIndex);
		} else if(setterClass == long.class) {
			longSetter(mv, endIndex);
		} else if(setterClass == boolean.class) {
			booleanSetter(mv, endIndex);
		} else if(setterClass == double.class) {
			doubleSetter(mv, endIndex);
		} else {
			throw new RuntimeException("No setter for " + getClass().getName());
		}
	}

	protected void writeBiConsumer(MethodVisitor mv, String subClassInternalName, int endIndex) {
		mv.visitFieldInsn(GETSTATIC, subClassInternalName, "v" + index, "L" + biConsumerInternalName + ";");
		mv.visitVarInsn(ALOAD, objectIndex);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		if(biConsumerInternalName == CsvMapper.biConsumerName) {
			mv.visitMethodInsn(INVOKEINTERFACE, CsvMapper.biConsumerName, "consume", "(Ljava/lang/Object;[CII)V", true);
		} else {
			mv.visitMethodInsn(INVOKEVIRTUAL, biConsumerInternalName, "consume", "(Ljava/lang/Object;[CII)V", false);
		}
	}
	
	protected Label ifNotAtChar(MethodVisitor mv, int character) {
		// jump to label if the two integer refs are not equal
		return jumpToLabelForChar(mv, character, IF_ICMPNE);
	}

	protected Label ifAtChar(MethodVisitor mv, int character) {
		// jump to label if the two integer refs are equal
		return jumpToLabelForChar(mv, character, IF_ICMPEQ);
	}

	protected Label jumpToLabelForChar(MethodVisitor mv, int character, int code) {
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitInsn(CALOAD);
		mv.visitLdcInsn(new Integer(character));
		Label l27 = new Label();
		mv.visitJumpInsn(code, l27);
		return l27;
	}

	protected void throwMappingException(MethodVisitor mv) {
		mv.visitTypeInsn(NEW, "com/github/skjolber/stcsv/CsvException");
		mv.visitInsn(DUP);
		mv.visitLdcInsn("Illegal value in field '" + name + "'");
		mv.visitMethodInsn(INVOKESPECIAL, "com/github/skjolber/stcsv/CsvException", "<init>", "(Ljava/lang/String;)V", false);
		mv.visitInsn(ATHROW);
	}

	protected void doIncrementWhileNotEqualToDivider(MethodVisitor mv, int divider) {
		Label l29 = new Label();
		mv.visitLabel(l29);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitIincInsn(currentOffsetIndex, 1);
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitInsn(CALOAD);
		mv.visitLdcInsn(new Integer(divider));
		mv.visitJumpInsn(IF_ICMPNE, l29);
	}
	
	protected void doDecrementWhileAtWhitespace(MethodVisitor mv, int variableIndex) {
		
		// while (current[--currentOffset] == ' ' || current[currentOffset] == '\t');
		Label condition = new Label();
		mv.visitLabel(condition);
		
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitIincInsn(variableIndex, -1);
		mv.visitVarInsn(ILOAD, variableIndex);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 32);
		mv.visitJumpInsn(IF_ICMPEQ, condition);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitVarInsn(ILOAD, variableIndex);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 9);
		mv.visitJumpInsn(IF_ICMPEQ, condition);
	}
	
	protected void trimTrailingWhitespace(MethodVisitor mv) {
		// keep the 'end' index in the range index
		saveOffsetInRange(mv);
		
		doDecrementWhileAtWhitespace(mv, rangeIndex);
		
		// increment one so that the end index is non-inclusive
		mv.visitIincInsn(rangeIndex, 1);
	}	

	protected void saveOffsetInStart(MethodVisitor mv) {
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitVarInsn(ISTORE, startIndex);
	}

	protected void saveOffsetInRange(MethodVisitor mv) {
		mv.visitVarInsn(ILOAD, currentOffsetIndex);
		mv.visitVarInsn(ISTORE, rangeIndex);
	}
	
	protected void writeValue(MethodVisitor mv, String subClassInternalName) {
		int endIndex;
		
		if(trimLeadingWhitespaces) {
			trimWhitespace(mv, subClassInternalName, startIndex);
		}

		if(trimTrailingWhitespaces) {
			trimTrailingWhitespace(mv);
			
			endIndex = rangeIndex;
		} else {
			endIndex = currentOffsetIndex;
		}
		
		Label emptyValueLabel = null;
		if(trimLeadingWhitespaces || trimTrailingWhitespaces) {
			// check whether the trimming reduced the content to empty string
			Label writeValueLabel = ifLargerThanStart(mv, endIndex);
			
			if(optional) {
				emptyValueLabel = new Label();
				mv.visitJumpInsn(GOTO, emptyValueLabel);
			} else {
				throwMappingException(mv);
			}
			
			mv.visitLabel(writeValueLabel);
		}
		
		writeValueProjection(mv, subClassInternalName, endIndex);
		
		if(emptyValueLabel != null) {
			mv.visitLabel(emptyValueLabel);
		}
			
	}

	protected void writeValueProjection(MethodVisitor mv, String subClassInternalName, int endIndex) {
		if(setterName != null) {
			writeSetter(mv, subClassInternalName, endIndex);
		} else if(biConsumer != null) {
			writeBiConsumer(mv, subClassInternalName, endIndex);
		} else if(triConsumer != null) {
			writeTriConsumer(mv, subClassInternalName, endIndex);
		}
	}

	protected void writeTriConsumer(MethodVisitor mv, String subClassInternalName, int endIndex) {
		mv.visitFieldInsn(GETSTATIC, subClassInternalName, "v" + index, "L" + triConsumerInternalName + ";");
		
		mv.visitVarInsn(ALOAD, objectIndex);
		mv.visitVarInsn(ALOAD, intermediateIndex);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		if(triConsumerInternalName == CsvMapper.triConsumerName) {
			mv.visitMethodInsn(INVOKEINTERFACE, CsvMapper.triConsumerName, "consume", "(Ljava/lang/Object;Ljava/lang/Object;[CII)V", true);
		} else {
			mv.visitMethodInsn(INVOKEVIRTUAL, triConsumerInternalName, "consume", "(Ljava/lang/Object;Ljava/lang/Object;[CII)V", false);
		}		
	}

	protected Label ifLargerThanStart(MethodVisitor mv, int endIndex) {
		// if(endIndex > start) {
		mv.visitVarInsn(ILOAD, startIndex);
		mv.visitVarInsn(ILOAD, endIndex);
		Label l24 = new Label();
		mv.visitJumpInsn(IF_ICMPLT, l24);
		
		return l24;
	}

	protected void trimWhitespace(MethodVisitor mv, String subClassInternalName, int index) {
		
		Label condition = new Label();
		mv.visitJumpInsn(GOTO, condition);
		
		Label add = new Label();
		mv.visitLabel(add);
		mv.visitIincInsn(index, 1);
		mv.visitLabel(condition);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, index);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 32);
		mv.visitJumpInsn(IF_ICMPEQ, add);
		mv.visitVarInsn(ALOAD, currentArrayIndex);
		mv.visitVarInsn(ILOAD, index);
		mv.visitInsn(CALOAD);
		mv.visitIntInsn(BIPUSH, 9);
		mv.visitJumpInsn(IF_ICMPEQ, add);
	}

	public CsvColumnValueTriConsumer<?, ?> getTriConsumer() {
		return triConsumer;
	}
	
}
