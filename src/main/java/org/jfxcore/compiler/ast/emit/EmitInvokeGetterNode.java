// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Calls a getter and places the value on top of the operand stack, applying boxing and/or numeric conversions
 * to coerce the value to the requested type. If the value is an observable type, optionally retrieves its value.
 */
public class EmitInvokeGetterNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final MethodDeclaration getter;
    private final ValueSourceKind valueSourceKind;
    private final ResolvedTypeNode type;
    private final boolean requireNonNull;

    public EmitInvokeGetterNode(
            MethodDeclaration getter,
            TypeInstance type,
            ValueSourceKind valueSourceKind,
            boolean requireNonNull,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.getter = checkNotNull(getter);
        this.valueSourceKind = valueSourceKind;
        this.requireNonNull = requireNonNull || valueSourceKind.isNonNull();
    }

    @Override
    public boolean isNullable() {
        return !valueSourceKind.isNonNull();
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        TypeDeclaration returnType = getter.returnType();

        code.invoke(getter);

        if (requireNonNull) {
            code.ldc(getter.name())
                .invoke(ObjectsDecl().requireDeclaredMethod("requireNonNull", ObjectDecl(), StringDecl()))
                .checkcast(type.getTypeDeclaration());
        } else {
            code.castconv(returnType, type.getTypeDeclaration());
        }
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitInvokeGetterNode deepClone() {
        return new EmitInvokeGetterNode(
            getter, type.getTypeInstance(), valueSourceKind, requireNonNull, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitInvokeGetterNode that = (EmitInvokeGetterNode)o;
        return getter.equals(that.getter) &&
            valueSourceKind == that.valueSourceKind &&
            type.equals(that.type) &&
            requireNonNull == that.requireNonNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getter, valueSourceKind, type, requireNonNull);
    }
}
