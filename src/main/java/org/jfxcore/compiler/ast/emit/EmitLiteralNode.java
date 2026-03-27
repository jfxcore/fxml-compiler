// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

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

        if (requestedType.getTypeDeclaration().isPrimitive()) {
            requestedType = new ResolvedTypeNode(
                new TypeInvoker(requestedType.getSourceInfo()).invokeType(requestedType.getTypeDeclaration().boxed()),
                requestedType.getSourceInfo());
        }

        return node;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        if (isEmitInPreamble()) {
            code.aload(0);
            emitLiteral(code, requestedType.getTypeDeclaration());
            code.dup_x1()
                .putfield(context.getLocalMarkupClass().requireField(getId()));
            storeLocal(code, requestedType.getTypeDeclaration());
        } else {
            emitLiteral(code, requestedType.getTypeDeclaration());
        }
    }

    private void emitLiteral(Bytecode code, TypeDeclaration targetType) {
        if (literal == null) {
            code.aconst_null();
            return;
        }

        switch (targetType.name()) {
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
                code.ldc(new Resolver(SourceInfo.none()).resolveClass(getLiteral(String.class)));
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
                    .invoke(BooleanDecl().requireDeclaredMethod("valueOf", booleanDecl()));
                break;
            case ByteName:
                code.iconst(getLiteral(Number.class).byteValue())
                    .invoke(ByteDecl().requireDeclaredMethod("valueOf", byteDecl()));
                break;
            case CharacterName:
                code.iconst(getLiteral(Character.class))
                    .invoke(CharacterDecl().requireDeclaredMethod("valueOf", charDecl()));
                break;
            case ShortName:
                code.iconst(getLiteral(Number.class).shortValue())
                    .invoke(ShortDecl().requireDeclaredMethod("valueOf", shortDecl()));
                break;
            case IntegerName:
                code.iconst(getLiteral(Number.class).intValue())
                    .invoke(IntegerDecl().requireDeclaredMethod("valueOf", intDecl()));
                break;
            case LongName:
                code.lconst(getLiteral(Number.class).longValue())
                    .invoke(LongDecl().requireDeclaredMethod("valueOf", longDecl()));
                break;
            case FloatName:
                code.fconst(getLiteral(Number.class).floatValue())
                    .invoke(FloatDecl().requireDeclaredMethod("valueOf", floatDecl()));
                break;
            case DoubleName:
                code.dconst(getLiteral(Number.class).doubleValue())
                    .invoke(DoubleDecl().requireDeclaredMethod("valueOf", doubleDecl()));
                break;
            case NumberName:
                emitNumber(code, literal);
                break;
            default:
                // An enum value is represented by a static field.
                code.getstatic(getLiteral(FieldDeclaration.class));
        }
    }

    private void emitNumber(Bytecode code, Object literal) {
        if (literal instanceof Byte) {
            code.iconst((byte)literal)
                .invoke(ByteDecl().requireDeclaredMethod("valueOf", byteDecl()));
        } else if (literal instanceof Short) {
            code.iconst((short)literal)
                .invoke(ShortDecl().requireDeclaredMethod("valueOf", shortDecl()));
        } else if (literal instanceof Integer) {
            code.iconst((int)literal)
                .invoke(IntegerDecl().requireDeclaredMethod("valueOf", intDecl()));
        } else if (literal instanceof Long) {
            code.lconst((long)literal)
                .invoke(LongDecl().requireDeclaredMethod("valueOf", longDecl()));
        } else if (literal instanceof Float) {
            code.fconst((float)literal)
                .invoke(FloatDecl().requireDeclaredMethod("valueOf", floatDecl()));
        } else if (literal instanceof Double) {
            code.dconst((double)literal)
                .invoke(DoubleDecl().requireDeclaredMethod("valueOf", doubleDecl()));
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
