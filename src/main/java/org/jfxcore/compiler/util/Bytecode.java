// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtPrimitiveType;
import javassist.Modifier;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.ExceptionTable;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.BitSet;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;
import static org.jfxcore.compiler.util.TypeHelper.*;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class Bytecode {

    final javassist.bytecode.Bytecode bytecode;

    private final Locals locals = new Locals();

    private int extraStackSize;

    public Bytecode(CtClass clazz, int occupiedLocals) {
        this.bytecode = new javassist.bytecode.Bytecode(clazz.getClassFile().getConstPool());
        this.locals.set(0, occupiedLocals);
    }

    public Bytecode aconst_null() {
        bytecode.addOpcode(Opcode.ACONST_NULL);
        return this;
    }

    public Bytecode aload(int n) {
        bytecode.addAload(n);
        return this;
    }

    public Bytecode aload(Local local) {
        if (local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addAload(local.getIndex());
        return this;
    }

    public Bytecode anew(CtClass clazz) {
        bytecode.addNew(clazz);
        return this;
    }

    public Bytecode anew(String classname) {
        bytecode.addNew(classname);
        return this;
    }

    public Bytecode areturn() {
        bytecode.addOpcode(Opcode.ARETURN);
        return this;
    }

    public Bytecode astore(int n) {
        bytecode.addAstore(n);
        return this;
    }

    public Bytecode astore(Local local) {
        if (local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addAstore(local.getIndex());
        return this;
    }

    public Bytecode athrow() {
        bytecode.addOpcode(Opcode.ATHROW);
        return this;
    }

    public Bytecode checkcast(CtClass clazz) {
        bytecode.addCheckcast(clazz);
        return this;
    }

    public Bytecode checkcast(String classname) {
        bytecode.addCheckcast(classname);
        return this;
    }

    public Bytecode dcmpl() {
        bytecode.addOpcode(Opcode.DCMPL);
        return this;
    }

    public Bytecode dconst(double d) {
        bytecode.addDconst(d);
        return this;
    }

    public Bytecode dload(int n) {
        bytecode.addDload(n);
        return this;
    }

    public Bytecode dload(Local local) {
        if (!local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addDload(local.getIndex());
        return this;
    }

    public Bytecode dreturn() {
        bytecode.addOpcode(Opcode.DRETURN);
        return this;
    }

    public Bytecode dstore(int n) {
        bytecode.addDstore(n);
        return this;
    }

    public Bytecode dstore(Local local) {
        if (!local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addDstore(local.getIndex());
        return this;
    }

    public Bytecode dup() {
        bytecode.addOpcode(Opcode.DUP);
        return this;
    }

    public Bytecode dup_x1() {
        bytecode.addOpcode(Opcode.DUP_X1);
        return this;
    }

    public Bytecode dup_x2() {
        bytecode.addOpcode(Opcode.DUP_X2);
        return this;
    }

    public Bytecode dup2_x1() {
        bytecode.addOpcode(Opcode.DUP2_X1);
        return this;
    }

    public Bytecode dup2_x2() {
        bytecode.addOpcode(Opcode.DUP2_X2);
        return this;
    }

    public Bytecode fcmpl() {
        bytecode.addOpcode(Opcode.FCMPL);
        return this;
    }

    public Bytecode fconst(float f) {
        bytecode.addFconst(f);
        return this;
    }

    public Bytecode fload(int n) {
        bytecode.addFload(n);
        return this;
    }

    public Bytecode fload(Local local) {
        if (local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addFload(local.getIndex());
        return this;
    }

    public Bytecode freturn() {
        bytecode.addOpcode(Opcode.FRETURN);
        return this;
    }

    public Bytecode fstore(int n) {
        bytecode.addFstore(n);
        return this;
    }

    public Bytecode fstore(Local local) {
        if (local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addFstore(local.getIndex());
        return this;
    }

    public Bytecode getfield(CtClass declaring, String name, CtClass type) {
        bytecode.addGetfield(declaring, name, Descriptor.of(type));
        return this;
    }

    public Bytecode getstatic(CtClass c, String name, String type) {
        bytecode.addGetstatic(c, name, type);
        return this;
    }

    public Bytecode getstatic(String c, String name, String type) {
        bytecode.addGetstatic(c, name, type);
        return this;
    }

    public Bytecode goto_position(int position) {
        bytecode.addOpcode(Opcode.GOTO);
        bytecode.addIndex(position - bytecode.getSize());
        return this;
    }

    public Label goto_label() {
        bytecode.addOpcode(Opcode.GOTO);
        return new Label(this, false);
    }

    public Bytecode iadd() {
        bytecode.addOpcode(Opcode.IADD);
        return this;
    }

    public Bytecode iconst(int i) {
        bytecode.addIconst(i);
        return this;
    }

    public Bytecode iinc(int n, int c) {
        bytecode.addOpcode(Opcode.IINC);
        bytecode.add(n, c);
        return this;
    }

    public Bytecode iload(int n) {
        bytecode.addIload(n);
        return this;
    }

    public Bytecode iload(Local local) {
        if (local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addIload(local.getIndex());
        return this;
    }

    public Label if_acmpeq() {
        bytecode.addOpcode(Opcode.IF_ACMPEQ);
        return new Label(this, false);
    }

    public Bytecode if_acmpeq(Runnable then) {
        return then(if_acmpne(), then);
    }

    public Bytecode if_acmpeq(Runnable then, Runnable else_) {
        return thenElse(if_acmpeq(), then, else_);
    }

    public Label if_acmpne() {
        bytecode.addOpcode(Opcode.IF_ACMPNE);
        return new Label(this, false);
    }

    public Bytecode if_acmpne(Runnable then) {
        return then(if_acmpeq(), then);
    }

    public Bytecode if_acmpne(Runnable then, Runnable else_) {
        return thenElse(if_acmpne(), then, else_);
    }

    public Label if_icmpeq() {
        bytecode.addOpcode(Opcode.IF_ICMPEQ);
        return new Label(this, false);
    }

    public Bytecode if_icmpeq(Runnable then) {
        return then(if_icmpne(), then);
    }

    public Bytecode if_icmpeq(Runnable then, Runnable else_) {
        return thenElse(if_icmpeq(), then, else_);
    }

    public Label if_icmpne() {
        bytecode.addOpcode(Opcode.IF_ICMPNE);
        return new Label(this, false);
    }

    public Bytecode if_icmpne(Runnable then) {
        return then(if_icmpeq(), then);
    }

    public Bytecode if_icmpne(Runnable then, Runnable else_) {
        return thenElse(if_icmpne(), then, else_);
    }

    public Label ifeq() {
        bytecode.addOpcode(Opcode.IFEQ);
        return new Label(this, false);
    }

    public Bytecode ifeq(Runnable then) {
        return then(ifne(), then);
    }

    public Bytecode ifeq(Runnable then, Runnable else_) {
        return thenElse(ifeq(), then, else_);
    }

    public Label ifne() {
        bytecode.addOpcode(Opcode.IFNE);
        return new Label(this, false);
    }

    public Bytecode ifne(Runnable then) {
        return then(ifeq(), then);
    }

    public Bytecode ifne(Runnable then, Runnable else_) {
        return thenElse(ifne(), then, else_);
    }

    public Label ifnonnull() {
        bytecode.addOpcode(Opcode.IFNONNULL);
        return new Label(this, false);
    }

    public Bytecode ifnonnull(Runnable then) {
        return then(ifnull(), then);
    }

    public Bytecode ifnonnull(Runnable then, Runnable else_) {
        return thenElse(ifnonnull(), then, else_);
    }

    public Label ifnull() {
        bytecode.addOpcode(Opcode.IFNULL);
        return new Label(this, false);
    }

    public Bytecode ifnull(Runnable then) {
        return then(ifnonnull(), then);
    }

    public Bytecode ifnull(Runnable then, Runnable else_) {
        return thenElse(ifnull(), then, else_);
    }

    public Bytecode isinstanceof(CtClass clazz) {
        bytecode.addInstanceof(clazz.getName());
        return this;
    }

    public Bytecode invokeinterface(CtClass clazz, String name, String desc) {
        bytecode.addInvokeinterface(clazz, name, desc, getSlotCount(desc));
        return this;
    }

    public Bytecode invokeinterface(String classname, String name, String desc) {
        bytecode.addInvokeinterface(classname, name, desc, getSlotCount(desc));
        return this;
    }

    public Bytecode invokespecial(CtClass clazz, String name, String desc) {
        bytecode.addInvokespecial(clazz, name, desc);
        return this;
    }

    public Bytecode invokespecial(String classname, String name, String desc) {
        bytecode.addInvokespecial(classname, name, desc);
        return this;
    }

    public Bytecode invokestatic(CtClass clazz, String name, String desc) {
        bytecode.addInvokestatic(clazz, name, desc);
        return this;
    }

    public Bytecode invokestatic(String classname, String name, String desc) {
        bytecode.addInvokestatic(classname, name, desc);
        return this;
    }

    public Bytecode invokevirtual(CtClass clazz, String name, String desc) {
        bytecode.addInvokevirtual(clazz, name, desc);
        return this;
    }

    public Bytecode invokevirtual(String classname, String name, String desc) {
        bytecode.addInvokevirtual(classname, name, desc);
        return this;
    }

    public Bytecode ireturn() {
        bytecode.addOpcode(Opcode.IRETURN);
        return this;
    }

    public Bytecode istore(int n) {
        bytecode.addIstore(n);
        return this;
    }

    public Bytecode istore(Local local) {
        if (local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addIstore(local.getIndex());
        return this;
    }

    public Bytecode isub() {
        bytecode.addOpcode(Opcode.ISUB);
        return this;
    }

    public Bytecode ixor() {
        bytecode.addOpcode(Opcode.IXOR);
        return this;
    }

    public Bytecode lcmp() {
        bytecode.addOpcode(Opcode.LCMP);
        return this;
    }

    public Bytecode lconst(long l) {
        bytecode.addLconst(l);
        return this;
    }

    public Bytecode ldc(String s) {
        bytecode.addLdc(s);
        return this;
    }

    public Bytecode ldc(int i) {
        bytecode.addLdc(i);
        return this;
    }

    public Bytecode lload(int n) {
        bytecode.addLload(n);
        return this;
    }

    public Bytecode lload(Local local) {
        if (!local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addLload(local.getIndex());
        return this;
    }

    public Bytecode lreturn() {
        bytecode.addOpcode(Opcode.LRETURN);
        return this;
    }

    public Bytecode lstore(int n) {
        bytecode.addLstore(n);
        return this;
    }

    public Bytecode lstore(Local local) {
        if (!local.isWide()) {
            throw new IllegalArgumentException("Invalid local");
        }

        bytecode.addLstore(local.getIndex());
        return this;
    }

    public Bytecode newarray(CtClass clazz) {
        if (clazz.isPrimitive()) {
            bytecode.addOpcode(Opcode.NEWARRAY);
            bytecode.add(((CtPrimitiveType)clazz).getArrayType());
        } else {
            bytecode.addOpcode(Opcode.ANEWARRAY);
            bytecode.addIndex(getConstPool().addClassInfo(clazz));
        }

        return this;
    }

    public Bytecode newarray(CtClass clazz, int length) {
        if (clazz.isPrimitive()) {
            bytecode.addNewarray(((CtPrimitiveType)clazz).getArrayType(), length);
        } else {
            bytecode.addAnewarray(clazz, length);
        }

        return this;
    }

    public Bytecode nop() {
        bytecode.addOpcode(Opcode.NOP);
        return this;
    }

    public Bytecode opcode(int opcode) {
        bytecode.addOpcode(opcode);
        return this;
    }

    public Bytecode putfield(CtClass declaring, String name, CtClass type) {
        bytecode.addPutfield(declaring, name, Descriptor.of(type));
        return this;
    }

    public Bytecode putstatic(CtClass declaring, String name, CtClass type) {
        bytecode.addPutstatic(declaring, name, Descriptor.of(type));
        return this;
    }

    public Bytecode pop() {
        bytecode.addOpcode(Opcode.POP);
        return this;
    }

    public Bytecode pop2() {
        bytecode.addOpcode(Opcode.POP2);
        return this;
    }

    public int position() {
        return bytecode.getSize();
    }

    public Bytecode vreturn() {
        bytecode.addOpcode(Opcode.RETURN);
        return this;
    }

    /**
     * Emits invokespecial/invokestatic/invokeinterface/invokevirtual depending on the type of the behavior.
     */
    public Bytecode ext_invoke(CtBehavior behavior) {
        if (behavior instanceof CtConstructor) {
            invokespecial(behavior.getDeclaringClass(), MethodInfo.nameInit, behavior.getSignature());
        } else if (Modifier.isStatic(behavior.getModifiers())) {
            invokestatic(behavior.getDeclaringClass(), behavior.getName(), behavior.getSignature());
        } else if (behavior.getDeclaringClass().isInterface()) {
            invokeinterface(behavior.getDeclaringClass(), behavior.getName(), behavior.getSignature());
        } else {
            invokevirtual(behavior.getDeclaringClass(), behavior.getName(), behavior.getSignature());
        }

        return this;
    }

    /**
     * Emits iconst/lconst/fconst/dconst depending on the type of the specified class.
     */
    public Bytecode ext_const(CtClass type, Number n) {
        if (type == CtClass.booleanType
            || type == CtClass.charType
            || type == CtClass.byteType
            || type == CtClass.shortType
            || type == CtClass.intType) {
            iconst(n.intValue());
        } else if (type == CtClass.longType) {
            lconst(n.longValue());
        } else if (type == CtClass.floatType) {
            fconst(n.floatValue());
        } else if (type == CtClass.doubleType) {
            dconst(n.doubleValue());
        } else {
            throw new IllegalArgumentException();
        }

        return this;
    }

    /**
     * Emits iload/lload/fload/dload/aload depending on the type of the specified class.
     */
    public Bytecode ext_load(CtClass type, int n) {
        if (type == CtClass.booleanType
            || type == CtClass.charType
            || type == CtClass.byteType
            || type == CtClass.shortType
            || type == CtClass.intType) {
            iload(n);
        } else if (type == CtClass.longType) {
            lload(n);
        } else if (type == CtClass.floatType) {
            fload(n);
        } else if (type == CtClass.doubleType) {
            dload(n);
        } else {
            aload(n);
        }

        return this;
    }

    /**
     * Emits iload/lload/fload/dload/aload depending on the type of the specified class.
     */
    public Bytecode ext_load(CtClass type, Local local) {
        return ext_load(type, local.getIndex());
    }

    /**
     * Emits ireturn/lreturn/freturn/dreturn/areturn depending on the type of the specified class.
     */
    public Bytecode ext_return(CtClass type) {
        if (type == CtClass.booleanType
            || type == CtClass.charType
            || type == CtClass.byteType
            || type == CtClass.shortType
            || type == CtClass.intType) {
            ireturn();
        } else if (type == CtClass.longType) {
            lreturn();
        } else if (type == CtClass.floatType) {
            freturn();
        } else if (type == CtClass.doubleType) {
            dreturn();
        } else {
            areturn();
        }

        return this;
    }

    /**
     * Emits bastore/castore/sastore/iastore/lastore/fastore/dastore/aastore depending on the
     * type of the specified class.
     */
    public Bytecode ext_arraystore(CtClass type) {
        if (type == CtClass.byteType || type == CtClass.booleanType) {
            bytecode.addOpcode(Opcode.BASTORE);
        } else if (type == CtClass.charType) {
            bytecode.addOpcode(Opcode.CASTORE);
        } else if (type == CtClass.shortType) {
            bytecode.addOpcode(Opcode.SASTORE);
        } else if (type == CtClass.intType) {
            bytecode.addOpcode(Opcode.IASTORE);
        } else if (type == CtClass.longType) {
            bytecode.addOpcode(Opcode.LASTORE);
        } else if (type == CtClass.floatType) {
            bytecode.addOpcode(Opcode.FASTORE);
        } else if (type == CtClass.doubleType) {
            bytecode.addOpcode(Opcode.DASTORE);
        } else {
            bytecode.addOpcode(Opcode.AASTORE);
        }

        return this;
    }

    /**
     * Emits baload/caload/saload/iaload/laload/faload/daload/aaload depending on the
     * type of the specified class.
     */
    public Bytecode ext_arrayload(CtClass type) {
        if (type == CtClass.byteType || type == CtClass.booleanType) {
            bytecode.addOpcode(Opcode.BALOAD);
        } else if (type == CtClass.charType) {
            bytecode.addOpcode(Opcode.CALOAD);
        } else if (type == CtClass.shortType) {
            bytecode.addOpcode(Opcode.SALOAD);
        } else if (type == CtClass.intType) {
            bytecode.addOpcode(Opcode.IALOAD);
        } else if (type == CtClass.longType) {
            bytecode.addOpcode(Opcode.LALOAD);
        } else if (type == CtClass.floatType) {
            bytecode.addOpcode(Opcode.FALOAD);
        } else if (type == CtClass.doubleType) {
            bytecode.addOpcode(Opcode.DALOAD);
        } else {
            bytecode.addOpcode(Opcode.AALOAD);
        }

        return this;
    }

    /**
     * Emits istore/lstore/fstore/dstore/astore depending on the type of the specified class.
     */
    public Bytecode ext_store(CtClass type, int n) {
        if (type == CtClass.booleanType
            || type == CtClass.charType
            || type == CtClass.byteType
            || type == CtClass.shortType
            || type == CtClass.intType) {
            istore(n);
        } else if (type == CtClass.longType) {
            lstore(n);
        } else if (type == CtClass.floatType) {
            fstore(n);
        } else if (type == CtClass.doubleType) {
            dstore(n);
        } else {
            astore(n);
        }

        return this;
    }

    /**
     * Emits istore/lstore/fstore/dstore/astore depending on the type of the specified class.
     */
    public Bytecode ext_store(CtClass type, Local local) {
        return ext_store(type, local.getIndex());
    }

    /**
     * Places the default value for the specified type on top of the operand stack.
     */
    public Bytecode ext_defaultconst(CtClass type) {
        if (type == CtClass.booleanType
            || type == CtClass.charType
            || type == CtClass.byteType
            || type == CtClass.shortType
            || type == CtClass.intType) {
            iconst(0);
        } else if (type == CtClass.longType) {
            lconst(0);
        } else if (type == CtClass.floatType) {
            fconst(0);
        } else if (type == CtClass.doubleType) {
            dconst(0);
        } else {
            aconst_null();
        }

        return this;
    }

    /**
     * Emits bytecodes to box the specified type on top of the operand stack.
     */
    public Bytecode ext_box(CtClass primitiveType) {
        if (primitiveType == CtClass.booleanType) {
            bytecode.addInvokestatic(Classes.BooleanType(), "valueOf", "(Z)Ljava/lang/Boolean;");
        } else if (primitiveType == CtClass.charType) {
            bytecode.addInvokestatic(Classes.CharacterType(), "valueOf", "(C)Ljava/lang/Character;");
        } else if (primitiveType == CtClass.byteType) {
            bytecode.addInvokestatic(Classes.ByteType(), "valueOf", "(B)Ljava/lang/Byte;");
        } else if (primitiveType == CtClass.shortType) {
            bytecode.addInvokestatic(Classes.ShortType(), "valueOf", "(S)Ljava/lang/Short;");
        } else if (primitiveType == CtClass.intType) {
            bytecode.addInvokestatic(Classes.IntegerType(), "valueOf", "(I)Ljava/lang/Integer;");
        } else if (primitiveType == CtClass.longType) {
            bytecode.addInvokestatic(Classes.LongType(), "valueOf", "(J)Ljava/lang/Long;");
        } else if (primitiveType == CtClass.floatType) {
            bytecode.addInvokestatic(Classes.FloatType(), "valueOf", "(F)Ljava/lang/Float;");
        } else if (primitiveType == CtClass.doubleType) {
            bytecode.addInvokestatic(Classes.DoubleType(), "valueOf", "(D)Ljava/lang/Double;");
        } else {
            throw new IllegalArgumentException("Not a primitive type.");
        }

        return this;
    }

    /**
     * Emits bytecodes to unbox the specified type on top of the operand stack.
     */
    public Bytecode ext_unbox(SourceInfo sourceInfo, CtClass sourceBox, CtClass targetPrimitive) {
        if (sourceBox.isPrimitive()) {
            throw new IllegalArgumentException("Not a boxed primitive type: source=" + sourceBox.getName());
        }

        if (!targetPrimitive.isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive type: target=" + targetPrimitive.getName());
        }

        if (sourceBox.equals(Classes.BooleanType()) && targetPrimitive == CtClass.booleanType) {
            bytecode.addInvokevirtual(Classes.BooleanType(), "booleanValue", "()Z");
            return this;
        }

        if (sourceBox.equals(Classes.CharacterType()) && targetPrimitive == CtClass.charType) {
            bytecode.addInvokevirtual(Classes.CharacterType(), "charValue", "()C");
            return this;
        }

        if (unchecked(sourceInfo, () -> sourceBox.subtypeOf(Classes.NumberType()))) {
            switch (targetPrimitive.getName()) {
                case "byte":
                    bytecode.addInvokevirtual(Classes.NumberType(), "byteValue", "()B");
                    return this;
                case "short":
                    bytecode.addInvokevirtual(Classes.NumberType(), "shortValue", "()S");
                    return this;
                case "int":
                    bytecode.addInvokevirtual(Classes.NumberType(), "intValue", "()I");
                    return this;
                case "long":
                    bytecode.addInvokevirtual(Classes.NumberType(), "longValue", "()J");
                    return this;
                case "float":
                    bytecode.addInvokevirtual(Classes.NumberType(), "floatValue", "()F");
                    return this;
                case "double":
                    bytecode.addInvokevirtual(Classes.NumberType(), "doubleValue", "()D");
                    return this;
            }
        }

        throw new IllegalArgumentException(
            "Inconvertible types: source=" + sourceBox.getName() + ", target=" + targetPrimitive.getName());
    }

    /**
     * Emits bytecodes to convert a numeric primitive into another numeric primitive.
     */
    public Bytecode ext_primitiveconv(CtClass source, CtClass target) {
        if (!source.isPrimitive() || !target.isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive type.");
        }

        if (TypeHelper.equals(source, target)) {
            return this;
        }

        for (Conversion conversion : CONVERSIONS) {
            if (conversion.source == source && conversion.target == target) {
                if (conversion.opcodes[0] != Opcode.NOP) {
                    opcode(conversion.opcodes[0]);

                    if (conversion.opcodes.length > 1) {
                        opcode(conversion.opcodes[1]);
                    }
                }

                return this;
            }
        }

        throw new IllegalArgumentException("Not a numeric primitive type.");
    }

    /**
     * Emits bytecodes to convert a numeric type into another numeric type.
     * This includes primitive conversion as well as boxing conversion.
     */
    public Bytecode ext_numericconv(SourceInfo sourceInfo, CtClass source, CtClass target) {
        if (unchecked(sourceInfo, () -> source.subtypeOf(target))) {
            return this;
        }

        if (isNumericPrimitive(source) && isNumericPrimitive(target)) {
            ext_primitiveconv(source, target);
        } else if (isNumericBox(source) && isNumericPrimitive(target)) {
            Local temp = acquireLocal(source);
            ext_store(source, temp);
            ext_load(source, temp);
            ifnull(
                () -> ext_defaultconst(target),
                () -> ext_load(source, temp)
                     .ext_unbox(sourceInfo, source, target));
            releaseLocal(temp);
        } else if (isNumericPrimitive(source) && isNumericBox(target)) {
            CtClass targetPrim = getPrimitiveType(target);
            ext_primitiveconv(source, targetPrim);
            ext_box(targetPrim);
        } else if (isNumericBox(source) && isNumericBox(target)) {
            Local temp = acquireLocal(source);
            ext_store(source, temp);
            ext_load(source, temp);
            ifnull(
                () -> ext_defaultconst(target),
                () -> ext_load(source, temp)
                     .ext_unbox(sourceInfo, source, getPrimitiveType(target))
                     .ext_box(getPrimitiveType(target)));
            releaseLocal(temp);
        } else {
            throw new IllegalArgumentException(
                "Not a numeric type: source=" + source.getName() + ", target=" + target.getName());
        }

        return this;
    }

    /**
     * Emits bytecodes to convert the source type into the target type using boxing conversions
     * and numeric conversions. This includes null checking for unboxing conversions.
     */
    public Bytecode ext_autoconv(SourceInfo sourceInfo, CtClass source, CtClass target) {
        if (unchecked(sourceInfo, () -> source.subtypeOf(target))) {
            return this;
        }

        if (isNumeric(source) && isNumeric(target)) {
            return ext_numericconv(sourceInfo, source, target);
        }

        if (isPrimitiveBox(source, target)) {
            Local temp = acquireLocal(source);
            ext_store(source, temp);
            ext_load(source, temp);
            ifnull(
                () -> ext_defaultconst(target),
                () -> ext_load(source, temp)
                     .ext_unbox(sourceInfo, source, target));
            releaseLocal(temp);
            return this;
        }

        if (isPrimitiveBox(target, source)) {
            return ext_box(source);
        }

        if (source.isPrimitive() && unchecked(sourceInfo, () -> getBoxedType(source).subtypeOf(target))) {
            return ext_box(source);
        }

        throw new IllegalArgumentException(
            "Inconvertible types: source=" + source.getName() + ", target=" + target.getName());
    }

    /**
     * Emits bytecodes to convert the source type into the target type using boxing conversions,
     * numeric conversions and downcasts. This includes null checking for unboxing conversions.
     */
    public Bytecode ext_castconv(SourceInfo sourceInfo, CtClass source, CtClass target) {
        try {
            return ext_autoconv(sourceInfo, source, target);
        } catch (IllegalArgumentException ex) {
            if (source.isPrimitive() && target.isPrimitive()) {
                throw ex;
            }
        }

        if (source.equals(Classes.ObjectType()) && isNumeric(target)) {
            checkcast(Classes.NumberType());
            return ext_autoconv(sourceInfo, Classes.NumberType(), target);
        }

        if (!source.isPrimitive() && target.isPrimitive()) {
            CtClass targetBox = getBoxedType(target);

            if (unchecked(sourceInfo, () -> !source.subtypeOf(targetBox) && !targetBox.subtypeOf(source))) {
                throw new IllegalArgumentException(
                    "Unrelated types: source=" + source.getName() + ", target=" + target.getName());
            }

            checkcast(targetBox);
            Local temp = acquireLocal(source);
            ext_store(source, temp);
            ext_load(source, temp);
            ifnull(
                () -> ext_defaultconst(target),
                () -> ext_load(source, temp)
                     .ext_unbox(sourceInfo, targetBox, target));
            releaseLocal(temp);
            return this;
        }

        if (source.isPrimitive() && !target.isPrimitive()) {
            CtClass sourceBox = getBoxedType(source);

            if (unchecked(sourceInfo, () -> !sourceBox.subtypeOf(target) && !target.subtypeOf(sourceBox))) {
                throw new IllegalArgumentException(
                    "Unrelated types: source=" + source.getName() + ", target=" + target.getName());
            }

            ext_box(source);
            checkcast(target);
            return this;
        }

        if (unchecked(sourceInfo, () -> !source.subtypeOf(target) && !target.subtypeOf(source))) {
            throw new IllegalArgumentException(
                "Unrelated types: source=" + source.getName() + ", target=" + target.getName());
        }

        checkcast(target);
        return this;
    }

    public Bytecode ext_ObservableUnbox(SourceInfo sourceInfo, CtClass source, CtClass target) {
        if (unchecked(sourceInfo, () -> target == CtClass.booleanType && source.subtypeOf(Classes.ObservableBooleanValueType()))) {
            invokeinterface(Classes.ObservableBooleanValueType(), "get", Descriptors.function(CtClass.booleanType));
            return this;
        }

        if (unchecked(sourceInfo, () -> source.subtypeOf(Classes.ObservableNumberValueType()))) {
            switch (target.getName()) {
                case "byte":
                case "char":
                case "short":
                case "int":
                    invokeinterface(Classes.ObservableNumberValueType(), "intValue", Descriptors.function(CtClass.intType));
                    switch (target.getName()) {
                        case "byte": opcode(Opcode.I2B); break;
                        case "char": opcode(Opcode.I2C); break;
                        case "short": opcode(Opcode.I2S); break;
                    }
                    return this;
                case "long":
                    invokeinterface(Classes.ObservableNumberValueType(), "longValue", Descriptors.function(CtClass.longType));
                    return this;
                case "float":
                    invokeinterface(Classes.ObservableNumberValueType(), "floatValue", Descriptors.function(CtClass.floatType));
                    return this;
                case "double":
                    invokeinterface(Classes.ObservableNumberValueType(), "doubleValue", Descriptors.function(CtClass.doubleType));
                    return this;
            }
        }

        if (unchecked(sourceInfo, () -> source.subtypeOf(Classes.ObservableBooleanValueType()))) {
            invokeinterface(Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType()));
            checkcast(Classes.BooleanType());
            ext_autoconv(sourceInfo, Classes.BooleanType(), target);
        } else if (unchecked(sourceInfo, () -> source.subtypeOf(Classes.ObservableNumberValueType()))) {
            invokeinterface(Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType()));
            checkcast(Classes.NumberType());
            ext_autoconv(sourceInfo, Classes.NumberType(), target);
        } else if (!TypeHelper.equals(target, Classes.ObjectType())) {
            if (target.isPrimitive()) {
                throw new IllegalArgumentException("source=" + source.getName() + ", target=" + target.getName());
            }

            invokeinterface(Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType()));
            checkcast(target);
        } else {
            invokeinterface(Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType()));
        }

        return this;
    }

    public ExceptionTable getExceptionTable() {
        return bytecode.getExceptionTable();
    }

    public CodeAttribute toCodeAttribute() {
        if (extraStackSize != 0) {
            bytecode.setMaxStack(bytecode.getMaxStack() + extraStackSize);
        }

        bytecode.setMaxLocals(locals.maxLength);

        return bytecode.toCodeAttribute();
    }

    public ConstPool getConstPool() {
        return bytecode.getConstPool();
    }

    public void addExtraStackSize(int value) {
        extraStackSize += value;
    }

    private Bytecode then(Label label, Runnable then) {
        then.run();
        label.resume();
        return this;
    }

    private Bytecode thenElse(Label label, Runnable then, Runnable else_) {
        int size = bytecode.getSize();
        else_.run();

        if (size == bytecode.getSize()) {
            int offset = size - (label.isWide() ? 4 : 2) - 1;
            bytecode.write(offset, invertBranch(bytecode.get()[offset]));
            then.run();
            label.resume();
        } else {
            Label skip = goto_label();
            label.resume();
            then.run();
            skip.resume();
        }

        return this;
    }

    private int invertBranch(byte opcode) {
        switch (opcode) {
            case (byte)Opcode.IFNULL:
                return Opcode.IFNONNULL;
            case (byte)Opcode.IFNONNULL:
                return Opcode.IFNULL;
            case (byte)Opcode.IFEQ:
                return Opcode.IFNE;
            case (byte)Opcode.IFNE:
                return Opcode.IFEQ;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static final class Conversion {
        final CtClass source, target;
        final int[] opcodes;

        Conversion(CtClass source, CtClass target, int opcode) {
            this.source = source;
            this.target = target;
            this.opcodes = new int[] {opcode};
        }

        Conversion(CtClass source, CtClass target, int opcode1, int opcode2) {
            this.source = source;
            this.target = target;
            this.opcodes = new int[] {opcode1, opcode2};
        }
    }

    private static final Conversion[] CONVERSIONS = {
        new Conversion(CtClass.charType, CtClass.byteType, Opcode.I2B),
        new Conversion(CtClass.shortType, CtClass.byteType, Opcode.I2B),
        new Conversion(CtClass.intType, CtClass.byteType, Opcode.I2B),
        new Conversion(CtClass.longType, CtClass.byteType, Opcode.L2I, Opcode.I2B),
        new Conversion(CtClass.floatType, CtClass.byteType, Opcode.F2I, Opcode.I2B),
        new Conversion(CtClass.doubleType, CtClass.byteType, Opcode.D2I, Opcode.I2B),

        new Conversion(CtClass.byteType, CtClass.charType, Opcode.I2C),
        new Conversion(CtClass.shortType, CtClass.charType, Opcode.I2C),
        new Conversion(CtClass.intType, CtClass.charType, Opcode.I2C),
        new Conversion(CtClass.longType, CtClass.charType, Opcode.L2I, Opcode.I2C),
        new Conversion(CtClass.floatType, CtClass.charType, Opcode.F2I, Opcode.I2C),
        new Conversion(CtClass.doubleType, CtClass.charType, Opcode.D2I, Opcode.I2C),

        new Conversion(CtClass.byteType, CtClass.shortType, Opcode.I2S),
        new Conversion(CtClass.charType, CtClass.shortType, Opcode.I2S),
        new Conversion(CtClass.intType, CtClass.shortType, Opcode.I2S),
        new Conversion(CtClass.longType, CtClass.shortType, Opcode.L2I, Opcode.I2S),
        new Conversion(CtClass.floatType, CtClass.shortType, Opcode.F2I, Opcode.I2S),
        new Conversion(CtClass.doubleType, CtClass.shortType, Opcode.D2I, Opcode.I2S),

        new Conversion(CtClass.byteType, CtClass.intType, Opcode.NOP),
        new Conversion(CtClass.charType, CtClass.intType, Opcode.NOP),
        new Conversion(CtClass.shortType, CtClass.intType, Opcode.NOP),
        new Conversion(CtClass.longType, CtClass.intType, Opcode.L2I),
        new Conversion(CtClass.floatType, CtClass.intType, Opcode.F2I),
        new Conversion(CtClass.doubleType, CtClass.intType, Opcode.D2I),

        new Conversion(CtClass.byteType, CtClass.longType, Opcode.I2L),
        new Conversion(CtClass.charType, CtClass.longType, Opcode.I2L),
        new Conversion(CtClass.shortType, CtClass.longType, Opcode.I2L),
        new Conversion(CtClass.intType, CtClass.longType, Opcode.I2L),
        new Conversion(CtClass.floatType, CtClass.longType, Opcode.F2L),
        new Conversion(CtClass.doubleType, CtClass.longType, Opcode.D2L),

        new Conversion(CtClass.byteType, CtClass.floatType, Opcode.I2F),
        new Conversion(CtClass.charType, CtClass.floatType, Opcode.I2F),
        new Conversion(CtClass.shortType, CtClass.floatType, Opcode.I2F),
        new Conversion(CtClass.intType, CtClass.floatType, Opcode.I2F),
        new Conversion(CtClass.longType, CtClass.floatType, Opcode.L2F),
        new Conversion(CtClass.doubleType, CtClass.floatType, Opcode.D2F),

        new Conversion(CtClass.byteType, CtClass.doubleType, Opcode.I2D),
        new Conversion(CtClass.charType, CtClass.doubleType, Opcode.I2D),
        new Conversion(CtClass.shortType, CtClass.doubleType, Opcode.I2D),
        new Conversion(CtClass.intType, CtClass.doubleType, Opcode.I2D),
        new Conversion(CtClass.longType, CtClass.doubleType, Opcode.L2D),
        new Conversion(CtClass.floatType, CtClass.doubleType, Opcode.F2D),
    };

    public static int getSlotCount(String desc) {
        if (!desc.startsWith("(")) {
            throw new IllegalArgumentException();
        }

        int count = 1;

        for (int i = 1; i < desc.length(); i++) {
            if (desc.charAt(i) == 'L') {
                do { i++; } while (desc.charAt(i) != ';');
                count++;
            } else if (desc.charAt(i) == '[') {
                if (desc.charAt(i + 1) == 'L') {
                    do { i++; } while (desc.charAt(i) != ';');
                } else {
                    i++;
                }

                count++;
            } else if (desc.charAt(i) == 'D' || desc.charAt(i) == 'J') {
                count += 2;
            } else if (desc.charAt(i) == ')') {
                break;
            } else {
                count++;
            }
        }

        return count;
    }

    private static class Locals extends BitSet {
        private int maxLength;

        @Override
        public void set(int bitIndex) {
            super.set(bitIndex);
            maxLength = Math.max(maxLength, length());
        }

        @Override
        public void set(int fromIndex, int toIndex) {
            super.set(fromIndex, toIndex);
            maxLength = Math.max(maxLength, length());
        }
    }

    public Local acquireLocal(CtClass type) {
        return acquireLocal(type == CtClass.doubleType || type == CtClass.longType);
    }

    public Local acquireLocal(boolean wide) {
        for (int i = 0; i < locals.length(); ++i) {
            if (locals.get(i)) {
                continue;
            }

            if (wide) {
                if (i == locals.length() - 1 || !locals.get(i + 1)) {
                    locals.set(i);
                    locals.set(i + 1);
                    return new Local(i, true);
                }
            } else {
                locals.set(i);
                return new Local(i, false);
            }
        }

        locals.set(locals.length());

        if (wide) {
            locals.set(locals.length());
            return new Local(locals.length() - 2, true);
        }

        return new Local(locals.length() - 1, false);
    }

    public void releaseLocal(Local local) {
        locals.clear(local.getIndex());

        if (local.isWide()) {
            locals.clear(local.getIndex() + 1);
        }
    }

}
