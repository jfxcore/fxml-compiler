// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.CtField;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Loads a string, primitive constant, null, or class literal, and places it on top of the operand stack.
 * If a boxed representation of the constant is required, the valueOf(...) method will be invoked.
 */
public class EmitLiteralNode extends ReferenceableNode {

    private final Object literal;
    private ResolvedTypeNode requestedType;

    public EmitLiteralNode(
            @Nullable String fieldName, TypeInstance requestedType, Object literal, SourceInfo sourceInfo) {
        super(null, fieldName, sourceInfo);
        this.requestedType = new ResolvedTypeNode(checkNotNull(requestedType), sourceInfo);
        this.literal = requestedType.isPrimitive() ? checkNotNull(literal) : literal;
    }

    public EmitLiteralNode(TypeInstance requestedType, Object literal, SourceInfo sourceInfo) {
        this(null, requestedType, literal, sourceInfo);
    }

    @SuppressWarnings("unchecked")
    public <T> T getLiteral(Class<T> literalClass) {
        if (!literalClass.isInstance(literal)) {
            throw new IllegalArgumentException(literalClass.getName());
        }

        return (T)literal;
    }

    @Override
    public ResolvedTypeNode getType() {
        return requestedType;
    }

    @Override
    public ValueEmitterNode convertToLocalReference() {
        if (!isEmitInPreamble()) {
            throw new UnsupportedOperationException();
        }

        EmitObjectNode node = EmitObjectNode.loadLocal(requestedType.getTypeInstance(), this, getSourceInfo());

        if (requestedType.getJvmType().isPrimitive()) {
            requestedType = new ResolvedTypeNode(
                new Resolver(requestedType.getSourceInfo()).getTypeInstance(
                    TypeHelper.getBoxedType(requestedType.getJvmType())),
                requestedType.getSourceInfo());
        }

        return node;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        if (isEmitInPreamble()) {
            code.aload(0);
            emitLiteral(code, requestedType.getJvmType());
            code.dup_x1()
                .putfield(context.getLocalMarkupClass(), getId(), requestedType.getJvmType());
            storeLocal(code, requestedType.getJvmType());
        } else {
            emitLiteral(code, requestedType.getJvmType());
        }
    }

    private void emitLiteral(Bytecode code, CtClass targetType) {
        if (literal == null) {
            code.aconst_null();
            return;
        }

        switch (targetType.getName()) {
            case "boolean":
                code.iconst(getLiteral(Boolean.class) ? 1 : 0);
                break;
            case "char":
                if (literal instanceof Character) {
                    code.iconst((Character)literal);
                } else if (literal instanceof Number) {
                    code.iconst(((Number)literal).intValue());
                } else {
                    throw new IllegalArgumentException(literal.getClass().getName());
                }
                break;
            case "byte":
            case "short":
            case "int":
                code.iconst(getLiteral(Number.class).intValue());
                break;
            case "long":
                code.lconst(getLiteral(Number.class).longValue());
                break;
            case "float":
                code.fconst(getLiteral(Number.class).floatValue());
                break;
            case "double":
                code.dconst(getLiteral(Number.class).doubleValue());
                break;
            case ClassName:
                code.ldc(code.getConstPool().addClassInfo(getLiteral(String.class)));
                break;
            case ObjectName:
                if (literal instanceof Number) {
                    emitNumber(code, literal);
                } else {
                    code.ldc(getLiteral(String.class));
                }
                break;
            case StringName:
                code.ldc(getLiteral(String.class));
                break;
            case BooleanName:
                code.iconst(getLiteral(Boolean.class) ? 1 : 0)
                    .invokestatic(BooleanType(), "valueOf", function(BooleanType(), booleanType));
                break;
            case ByteName:
                code.iconst(getLiteral(Number.class).byteValue())
                    .invokestatic(ByteType(), "valueOf", function(ByteType(), byteType));
                break;
            case CharacterName:
                code.iconst(getLiteral(Character.class))
                    .invokestatic(CharacterType(), "valueOf", function(CharacterType(), charType));
                break;
            case ShortName:
                code.iconst(getLiteral(Number.class).shortValue())
                    .invokestatic(ShortType(), "valueOf", function(ShortType(), shortType));
                break;
            case IntegerName:
                code.iconst(getLiteral(Number.class).intValue())
                    .invokestatic(IntegerType(), "valueOf", function(IntegerType(), intType));
                break;
            case LongName:
                code.lconst(getLiteral(Number.class).longValue())
                    .invokestatic(LongType(), "valueOf", function(LongType(), longType));
                break;
            case FloatName:
                code.fconst(getLiteral(Number.class).floatValue())
                    .invokestatic(FloatType(), "valueOf", function(FloatType(), floatType));
                break;
            case DoubleName:
                code.dconst(getLiteral(Number.class).doubleValue())
                    .invokestatic(DoubleType(), "valueOf", function(DoubleType(), doubleType));
                break;
            case NumberName:
                emitNumber(code, literal);
                break;
            default:
                // An enum value is represented by a static field.
                CtField field = getLiteral(CtField.class);
                code.getstatic(field.getDeclaringClass(), field.getName(), types(field.getDeclaringClass()));
        }
    }

    private void emitNumber(Bytecode code, Object literal) {
        if (literal instanceof Byte) {
            code.iconst((byte)literal)
                .invokestatic(ByteType(), "valueOf", function(ByteType(), byteType));
        } else if (literal instanceof Short) {
            code.iconst((short)literal)
                .invokestatic(ShortType(), "valueOf", function(ShortType(), shortType));
        } else if (literal instanceof Integer) {
            code.iconst((int)literal)
                .invokestatic(IntegerType(), "valueOf", function(IntegerType(), intType));
        } else if (literal instanceof Long) {
            code.lconst((long)literal)
                .invokestatic(LongType(), "valueOf", function(LongType(), longType));
        } else if (literal instanceof Float) {
            code.fconst((float)literal)
                .invokestatic(FloatType(), "valueOf", function(FloatType(), floatType));
        } else if (literal instanceof Double) {
            code.dconst((double)literal)
                .invokestatic(DoubleType(), "valueOf", function(DoubleType(), doubleType));
        } else {
            throw new IllegalArgumentException(literal.getClass().toString());
        }
    }

    @Override
    public EmitLiteralNode deepClone() {
        return new EmitLiteralNode(getId(), requestedType.getTypeInstance(), literal, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitLiteralNode that = (EmitLiteralNode)o;
        return Objects.equals(getId(), that.getId())
            && literal.equals(that.literal)
            && requestedType.equals(that.requestedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), literal, requestedType);
    }

}
