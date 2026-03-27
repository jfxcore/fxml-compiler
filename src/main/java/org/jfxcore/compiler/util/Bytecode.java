// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import javassist.CtPrimitiveType;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.BitSet;

import static org.jfxcore.compiler.type.KnownSymbols.*;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class Bytecode {

    final javassist.bytecode.Bytecode bytecode;

    private final Locals locals = new Locals();

    private int extraStackSize;

    public Bytecode(TypeDeclaration clazz, int occupiedLocals) {
        this.bytecode = new javassist.bytecode.Bytecode(clazz.jvmType().getClassFile().getConstPool());
        this.locals.set(0, occupiedLocals);
    }

    public Bytecode(BehaviorDeclaration method) {
        this.bytecode = new javassist.bytecode.Bytecode(
            method.declaringType().jvmType().getClassFile().getConstPool());

        this.locals.set(0, method.occupiedSlots());
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

    public Bytecode anew(TypeDeclaration clazz) {
        bytecode.addNew(clazz.jvmType());
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

    public Bytecode checkcast(TypeDeclaration clazz) {
        bytecode.addCheckcast(clazz.jvmType());
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

    public Bytecode getfield(FieldDeclaration field) {
        bytecode.addGetfield(field.declaringType().jvmType(), field.name(), field.signature());
        return this;
    }

    public Bytecode getstatic(FieldDeclaration field) {
        bytecode.addGetstatic(field.declaringType().jvmType(), field.name(), field.signature());
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

    public Bytecode isinstanceof(TypeDeclaration type) {
        bytecode.addInstanceof(type.jvmType().getName());
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

    public Bytecode ldc(int i) {
        bytecode.addLdc(i);
        return this;
    }

    public Bytecode ldc(String s) {
        bytecode.addLdc(s);
        return this;
    }

    public Bytecode ldc(TypeDeclaration type) {
        bytecode.addLdc(bytecode.getConstPool().addClassInfo(type.jvmType()));
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

    public Bytecode newarray(TypeDeclaration type) {
        CtClass clazz = type.jvmType();

        if (clazz.isPrimitive()) {
            bytecode.addOpcode(Opcode.NEWARRAY);
            bytecode.add(((CtPrimitiveType)clazz).getArrayType());
        } else {
            bytecode.addOpcode(Opcode.ANEWARRAY);
            bytecode.addIndex(bytecode.getConstPool().addClassInfo(clazz));
        }

        return this;
    }

    public Bytecode newarray(TypeDeclaration type, int length) {
        if (type.isPrimitive()) {
            bytecode.addNewarray(((CtPrimitiveType)type.jvmType()).getArrayType(), length);
        } else {
            bytecode.addAnewarray(type.jvmType(), length);
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

    public Bytecode putfield(FieldDeclaration field) {
        bytecode.addPutfield(field.declaringType().jvmType(), field.name(), field.signature());
        return this;
    }

    public Bytecode putstatic(FieldDeclaration field) {
        bytecode.addPutstatic(field.declaringType().jvmType(), field.name(), field.signature());
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
    public Bytecode invoke(BehaviorDeclaration behavior) {
        if (behavior instanceof ConstructorDeclaration constructor) {
            bytecode.addInvokespecial(constructor.declaringType().jvmType(),
                                      MethodInfo.nameInit,
                                      constructor.signature());
        } else if (behavior instanceof MethodDeclaration method) {
            if (method.isStatic()) {
                bytecode.addInvokestatic(method.declaringType().jvmType(),
                                         method.name(),
                                         method.signature());
            } else if (method.declaringType().isInterface()) {
                bytecode.addInvokeinterface(method.declaringType().jvmType(),
                                            method.name(),
                                            method.signature(),
                                            getSlotCount(method.signature()));
            } else {
                bytecode.addInvokevirtual(method.declaringType().jvmType(),
                                          method.name(),
                                          method.signature());
            }
        } else {
            throw new IllegalArgumentException();
        }

        return this;
    }

    /**
     * Emits iload/lload/fload/dload/aload depending on the type of the specified class.
     */
    public Bytecode load(TypeDeclaration type, int n) {
        CtClass jvmType = type.jvmType();

        if (jvmType == CtClass.booleanType
                || jvmType == CtClass.charType
                || jvmType == CtClass.byteType
                || jvmType == CtClass.shortType
                || jvmType == CtClass.intType) {
            iload(n);
        } else if (jvmType == CtClass.longType) {
            lload(n);
        } else if (jvmType == CtClass.floatType) {
            fload(n);
        } else if (jvmType == CtClass.doubleType) {
            dload(n);
        } else {
            aload(n);
        }

        return this;
    }

    /**
     * Emits iload/lload/fload/dload/aload depending on the type of the specified class.
     */
    public Bytecode load(TypeDeclaration type, Local local) {
        return load(type, local.getIndex());
    }

    /**
     * Emits ireturn/lreturn/freturn/dreturn/areturn depending on the type of the specified class.
     */
    public Bytecode ret(TypeDeclaration type) {
        CtClass jvmType = type.jvmType();

        if (jvmType == CtClass.booleanType
                || jvmType == CtClass.charType
                || jvmType == CtClass.byteType
                || jvmType == CtClass.shortType
                || jvmType == CtClass.intType) {
            ireturn();
        } else if (jvmType == CtClass.longType) {
            lreturn();
        } else if (jvmType == CtClass.floatType) {
            freturn();
        } else if (jvmType == CtClass.doubleType) {
            dreturn();
        } else if (jvmType == CtClass.voidType) {
            vreturn();
        } else {
            areturn();
        }

        return this;
    }

    /**
     * Emits bastore/castore/sastore/iastore/lastore/fastore/dastore/aastore depending on the
     * type of the specified class.
     */
    public Bytecode arraystore(TypeDeclaration type) {
        CtClass jvmType = type.jvmType();

        if (jvmType == CtClass.byteType || jvmType == CtClass.booleanType) {
            bytecode.addOpcode(Opcode.BASTORE);
        } else if (jvmType == CtClass.charType) {
            bytecode.addOpcode(Opcode.CASTORE);
        } else if (jvmType == CtClass.shortType) {
            bytecode.addOpcode(Opcode.SASTORE);
        } else if (jvmType == CtClass.intType) {
            bytecode.addOpcode(Opcode.IASTORE);
        } else if (jvmType == CtClass.longType) {
            bytecode.addOpcode(Opcode.LASTORE);
        } else if (jvmType == CtClass.floatType) {
            bytecode.addOpcode(Opcode.FASTORE);
        } else if (jvmType == CtClass.doubleType) {
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
    public Bytecode arrayload(TypeDeclaration type) {
        CtClass jvmType = type.jvmType();

        if (jvmType == CtClass.byteType || jvmType == CtClass.booleanType) {
            bytecode.addOpcode(Opcode.BALOAD);
        } else if (jvmType == CtClass.charType) {
            bytecode.addOpcode(Opcode.CALOAD);
        } else if (jvmType == CtClass.shortType) {
            bytecode.addOpcode(Opcode.SALOAD);
        } else if (jvmType == CtClass.intType) {
            bytecode.addOpcode(Opcode.IALOAD);
        } else if (jvmType == CtClass.longType) {
            bytecode.addOpcode(Opcode.LALOAD);
        } else if (jvmType == CtClass.floatType) {
            bytecode.addOpcode(Opcode.FALOAD);
        } else if (jvmType == CtClass.doubleType) {
            bytecode.addOpcode(Opcode.DALOAD);
        } else {
            bytecode.addOpcode(Opcode.AALOAD);
        }

        return this;
    }

    /**
     * Emits istore/lstore/fstore/dstore/astore depending on the type of the specified class.
     */
    public Bytecode store(TypeDeclaration type, int n) {
        CtClass jvmType = type.jvmType();

        if (jvmType == CtClass.booleanType
                || jvmType == CtClass.charType
                || jvmType == CtClass.byteType
                || jvmType == CtClass.shortType
                || jvmType == CtClass.intType) {
            istore(n);
        } else if (jvmType == CtClass.longType) {
            lstore(n);
        } else if (jvmType == CtClass.floatType) {
            fstore(n);
        } else if (jvmType == CtClass.doubleType) {
            dstore(n);
        } else {
            astore(n);
        }

        return this;
    }

    /**
     * Emits istore/lstore/fstore/dstore/astore depending on the type of the specified class.
     */
    public Bytecode store(TypeDeclaration type, Local local) {
        return store(type, local.getIndex());
    }

    /**
     * Places the default value for the specified type on top of the operand stack.
     */
    public Bytecode defaultconst(TypeDeclaration type) {
        CtClass jvmType = type.jvmType();
        if (jvmType == CtClass.booleanType
                || jvmType == CtClass.charType
                || jvmType == CtClass.byteType
                || jvmType == CtClass.shortType
                || jvmType == CtClass.intType) {
            iconst(0);
        } else if (jvmType == CtClass.longType) {
            lconst(0);
        } else if (jvmType == CtClass.floatType) {
            fconst(0);
        } else if (jvmType == CtClass.doubleType) {
            dconst(0);
        } else {
            aconst_null();
        }

        return this;
    }

    /**
     * Emits bytecodes to box the specified type on top of the operand stack.
     */
    public Bytecode box(TypeDeclaration primitiveType) {
        CtClass jvmType = primitiveType.jvmType();

        if (jvmType == CtClass.booleanType) {
            bytecode.addInvokestatic(BooleanDecl().jvmType(), "valueOf", "(Z)Ljava/lang/Boolean;");
        } else if (jvmType == CtClass.charType) {
            bytecode.addInvokestatic(CharacterDecl().jvmType(), "valueOf", "(C)Ljava/lang/Character;");
        } else if (jvmType == CtClass.byteType) {
            bytecode.addInvokestatic(ByteDecl().jvmType(), "valueOf", "(B)Ljava/lang/Byte;");
        } else if (jvmType == CtClass.shortType) {
            bytecode.addInvokestatic(ShortDecl().jvmType(), "valueOf", "(S)Ljava/lang/Short;");
        } else if (jvmType == CtClass.intType) {
            bytecode.addInvokestatic(IntegerDecl().jvmType(), "valueOf", "(I)Ljava/lang/Integer;");
        } else if (jvmType == CtClass.longType) {
            bytecode.addInvokestatic(LongDecl().jvmType(), "valueOf", "(J)Ljava/lang/Long;");
        } else if (jvmType == CtClass.floatType) {
            bytecode.addInvokestatic(FloatDecl().jvmType(), "valueOf", "(F)Ljava/lang/Float;");
        } else if (jvmType == CtClass.doubleType) {
            bytecode.addInvokestatic(DoubleDecl().jvmType(), "valueOf", "(D)Ljava/lang/Double;");
        } else {
            throw new IllegalArgumentException("Not a primitive type.");
        }

        return this;
    }

    /**
     * Emits bytecodes to unbox the specified type on top of the operand stack.
     */
    public Bytecode unbox(TypeDeclaration sourceBox, TypeDeclaration targetPrimitive) {
        if (sourceBox.isPrimitive()) {
            throw new IllegalArgumentException("Not a boxed primitive type: source=" + sourceBox.name());
        }

        if (!targetPrimitive.isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive type: target=" + targetPrimitive.name());
        }

        if (sourceBox.equals(BooleanDecl()) && targetPrimitive.equals(booleanDecl())) {
            bytecode.addInvokevirtual(BooleanDecl().jvmType(), "booleanValue", "()Z");
            return this;
        }

        if (sourceBox.equals(CharacterDecl())
                && (targetPrimitive.equals(charDecl()) || targetPrimitive.equals(intDecl()))) {
            bytecode.addInvokevirtual(CharacterDecl().jvmType(), "charValue", "()C");
            return this;
        }

        if (sourceBox.subtypeOf(NumberDecl())) {
            switch (targetPrimitive.name()) {
                case "byte":
                    bytecode.addInvokevirtual(NumberDecl().jvmType(), "byteValue", "()B");
                    return this;
                case "short":
                    bytecode.addInvokevirtual(NumberDecl().jvmType(), "shortValue", "()S");
                    return this;
                case "int":
                    bytecode.addInvokevirtual(NumberDecl().jvmType(), "intValue", "()I");
                    return this;
                case "long":
                    bytecode.addInvokevirtual(NumberDecl().jvmType(), "longValue", "()J");
                    return this;
                case "float":
                    bytecode.addInvokevirtual(NumberDecl().jvmType(), "floatValue", "()F");
                    return this;
                case "double":
                    bytecode.addInvokevirtual(NumberDecl().jvmType(), "doubleValue", "()D");
                    return this;
            }
        }

        throw new IllegalArgumentException(
            "Inconvertible types: source=" + sourceBox.name() + ", target=" + targetPrimitive.name());
    }

    /**
     * Emits bytecodes to convert a numeric primitive into another numeric primitive.
     */
    public Bytecode primconv(TypeDeclaration source, TypeDeclaration target) {
        if (!source.isPrimitive() || !target.isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive type.");
        }

        if (source.equals(target)) {
            return this;
        }

        for (Conversion conversion : CONVERSIONS) {
            if (conversion.source == source.jvmType() && conversion.target == target.jvmType()) {
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
    private Bytecode numconv(TypeDeclaration source, TypeDeclaration target) {
        if (source.subtypeOf(target)) {
            return this;
        }

        if (source.isNumericPrimitive() && target.isNumericPrimitive()) {
            primconv(source, target);
        } else if (source.isNumericBox() && target.isNumericPrimitive()) {
            Local temp = acquireLocal(source);
            store(source, temp);
            load(source, temp);
            ifnull(
                () -> defaultconst(target),
                () -> load(source, temp)
                     .unbox(source, target));
            releaseLocal(temp);
        } else if (source.isNumericPrimitive() && target.isNumericBox()) {
            TypeDeclaration targetPrim = target.primitive().orElseThrow();
            primconv(source, targetPrim);
            box(targetPrim);
        } else if (source.isNumericBox() && target.isNumericBox()) {
            TypeDeclaration targetPrim = target.primitive().orElseThrow();
            Local temp = acquireLocal(source);
            store(source, temp);
            load(source, temp);
            ifnull(
                () -> defaultconst(target),
                () -> load(source, temp)
                     .unbox(source, targetPrim)
                     .box(targetPrim));
            releaseLocal(temp);
        } else {
            throw new IllegalArgumentException(
                "Not a numeric type: source=" + source.name() + ", target=" + target.name());
        }

        return this;
    }

    /**
     * Emits bytecodes to convert the source type into the target type using boxing conversions
     * and numeric conversions. This includes null checking for unboxing conversions.
     */
    public Bytecode autoconv(TypeDeclaration source, TypeDeclaration target) {
        if (source.subtypeOf(target)) {
            return this;
        }

        if (source.isNumeric() && target.isNumeric()) {
            return numconv(source, target);
        }

        if (source.isBoxOf(target)) {
            Local temp = acquireLocal(source);
            store(source, temp);
            load(source, temp);
            ifnull(
                () -> defaultconst(target),
                () -> load(source, temp)
                     .unbox(source, target));
            releaseLocal(temp);
            return this;
        }

        if (target.isBoxOf(source)) {
            return box(source);
        }

        if (source.isPrimitive() && source.boxed().subtypeOf(target)) {
            return box(source);
        }

        throw new IllegalArgumentException(
            "Inconvertible types: source=" + source.name() + ", target=" + target.name());
    }

    /**
     * Emits bytecodes to convert the source type into the target type using boxing conversions,
     * numeric conversions and downcasts. This includes null checking for unboxing conversions.
     */
    public Bytecode castconv(TypeDeclaration source, TypeDeclaration target) {
        try {
            return autoconv(source, target);
        } catch (IllegalArgumentException ex) {
            if (source.isPrimitive() && target.isPrimitive()) {
                throw ex;
            }
        }

        if (!source.isPrimitive() && target.isPrimitive()) {
            TypeDeclaration targetBox = target.boxed();

            if (!source.subtypeOf(targetBox) && !targetBox.subtypeOf(source)) {
                throw new IllegalArgumentException(
                    "Unrelated types: source=" + source.name() + ", target=" + target.name());
            }

            Local temp = acquireLocal(source);
            checkcast(targetBox);
            store(source, temp);
            load(source, temp);
            ifnull(
                () -> defaultconst(target),
                () -> load(source, temp)
                     .unbox(targetBox, target));
            releaseLocal(temp);
            return this;
        }

        if (source.isPrimitive() && !target.isPrimitive()) {
            TypeDeclaration sourceBox = source.boxed();

            if (!sourceBox.subtypeOf(target) && !target.subtypeOf(sourceBox)) {
                throw new IllegalArgumentException(
                    "Unrelated types: source=" + source.name() + ", target=" + target.name());
            }

            box(source);
            checkcast(target);
            return this;
        }

        if (!source.subtypeOf(target) && !target.subtypeOf(source)) {
            throw new IllegalArgumentException(
                "Unrelated types: source=" + source.name() + ", target=" + target.name());
        }

        checkcast(target);
        return this;
    }

    public Bytecode unboxObservable(TypeDeclaration source, TypeDeclaration target) {
        if (target.jvmType() == CtClass.booleanType && source.subtypeOf(ObservableBooleanValueDecl())) {
            bytecode.addInvokeinterface(ObservableBooleanValueDecl().jvmType(), "get", "()Z", 1);
            return this;
        }

        if (source.subtypeOf(ObservableNumberValueDecl())) {
            switch (target.name()) {
                case "byte":
                case "char":
                case "short":
                case "int":
                    bytecode.addInvokeinterface(ObservableNumberValueDecl().jvmType(), "intValue", "()I", 1);
                    switch (target.name()) {
                        case "byte": opcode(Opcode.I2B); break;
                        case "char": opcode(Opcode.I2C); break;
                        case "short": opcode(Opcode.I2S); break;
                    }
                    return this;
                case "long":
                    bytecode.addInvokeinterface(ObservableNumberValueDecl().jvmType(), "longValue", "()J", 1);
                    return this;
                case "float":
                    bytecode.addInvokeinterface(ObservableNumberValueDecl().jvmType(), "floatValue", "()F", 1);
                    return this;
                case "double":
                    bytecode.addInvokeinterface(ObservableNumberValueDecl().jvmType(), "doubleValue", "()D", 1);
                    return this;
            }
        }

        if (source.subtypeOf(ObservableBooleanValueDecl())) {
            bytecode.addInvokeinterface(ObservableValueDecl().jvmType(), "getValue", "()Ljava/lang/Object;", 1);
            checkcast(BooleanDecl());
            autoconv(BooleanDecl(), target);
        } else if (source.subtypeOf(ObservableNumberValueDecl())) {
            bytecode.addInvokeinterface(ObservableValueDecl().jvmType(), "getValue", "()Ljava/lang/Object;", 1);
            checkcast(NumberDecl());
            autoconv(NumberDecl(), target);
        } else if (!target.equals(ObjectDecl())) {
            if (target.isPrimitive()) {
                throw new IllegalArgumentException("source=" + source.name() + ", target=" + target.name());
            }

            bytecode.addInvokeinterface(ObservableValueDecl().jvmType(), "getValue", "()Ljava/lang/Object;", 1);
            checkcast(target);
        } else {
            bytecode.addInvokeinterface(ObservableValueDecl().jvmType(), "getValue", "()Ljava/lang/Object;", 1);
        }

        return this;
    }

    public Bytecode handleException(TypeDeclaration type, int start, int end, int handler) {
        bytecode.getExceptionTable().add(start, end, handler, bytecode.getConstPool().addClassInfo(type.jvmType()));
        return this;
    }

    public CodeAttribute toCodeAttribute() {
        if (extraStackSize != 0) {
            bytecode.setMaxStack(bytecode.getMaxStack() + extraStackSize);
        }

        bytecode.setMaxLocals(locals.maxLength);

        return bytecode.toCodeAttribute();
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
        return switch (opcode) {
            case (byte)Opcode.IFNULL -> Opcode.IFNONNULL;
            case (byte)Opcode.IFNONNULL -> Opcode.IFNULL;
            case (byte)Opcode.IFEQ -> Opcode.IFNE;
            case (byte)Opcode.IFNE -> Opcode.IFEQ;
            default -> throw new IllegalArgumentException();
        };
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

    private static int getSlotCount(String desc) {
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

    public Local acquireLocal(TypeDeclaration type) {
        return acquireLocal(type.equals(doubleDecl()) || type.equals(longDecl()));
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
