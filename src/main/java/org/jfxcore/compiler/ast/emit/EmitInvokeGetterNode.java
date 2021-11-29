// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.CtMethod;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Calls a getter and places the value on top of the operand stack, applying boxing and/or numeric conversions
 * to coerce the value to the requested type. If the value is an observable type, optionally retrieves its value.
 */
public class EmitInvokeGetterNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final CtMethod getter;
    private final ObservableKind observableKind;
    private final ResolvedTypeNode type;
    private final boolean requireNonNull;

    public EmitInvokeGetterNode(
            CtMethod getter,
            TypeInstance type,
            ObservableKind observableKind,
            boolean requireNonNull,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.getter = checkNotNull(getter);
        this.observableKind = checkNotNull(observableKind);
        this.requireNonNull = requireNonNull || observableKind.isNonNull();
    }

    @Override
    public boolean isNullable() {
        return !observableKind.isNonNull();
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass returnType = unchecked(this.getter::getReturnType);

        code.ext_invoke(getter);

        if (requireNonNull) {
            code.ldc(getter.getName())
                .invokestatic(ObjectsType(), "requireNonNull", function(ObjectType(), ObjectType(), StringType()))
                .checkcast(type.getJvmType());
        } else {
            code.ext_castconv(getSourceInfo(), returnType, type.getJvmType());
        }
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitInvokeGetterNode deepClone() {
        return new EmitInvokeGetterNode(getter, type.getTypeInstance(), observableKind, requireNonNull, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitInvokeGetterNode that = (EmitInvokeGetterNode)o;
        return TypeHelper.equals(getter, that.getter) &&
            observableKind == that.observableKind &&
            type.equals(that.type) &&
            requireNonNull == that.requireNonNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(TypeHelper.hashCode(getter), observableKind, type, requireNonNull);
    }

}
