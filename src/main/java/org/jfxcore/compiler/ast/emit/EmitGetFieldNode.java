// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.CtField;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.lang.reflect.Modifier;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Gets the value of a field and places it on top of the operand stack, applying boxing and/or numeric conversions
 * to coerce the value to the requested type. If the value is an observable type, retrieves its value.
 */
public class EmitGetFieldNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final CtField field;
    private final ResolvedTypeNode type;
    private final ObservableKind observableKind;
    private final boolean requireNonNull;

    public EmitGetFieldNode(
            CtField field,
            TypeInstance type,
            ObservableKind observableKind,
            boolean requireNonNull,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.field = checkNotNull(field);
        this.type = new ResolvedTypeNode(type, sourceInfo);
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
        CtClass fieldType = unchecked(this.field::getType);

        if (Modifier.isStatic(field.getModifiers())) {
            code.getstatic(field.getDeclaringClass(), field.getName(), types(fieldType));
        } else {
            code.getfield(field.getDeclaringClass(), field.getName(), fieldType);
        }

        if (requireNonNull) {
            code.ldc(field.getName())
                .invokestatic(ObjectsType(), "requireNonNull", function(ObjectType(), ObjectType(), StringType()))
                .checkcast(type.getJvmType());
        } else {
            code.ext_castconv(getSourceInfo(), fieldType, type.getJvmType());
        }
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitGetFieldNode deepClone() {
        return new EmitGetFieldNode(field, type.getTypeInstance(), observableKind, requireNonNull, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitGetFieldNode that = (EmitGetFieldNode)o;
        return TypeHelper.equals(field, that.field) &&
            type.equals(that.type) &&
            observableKind == that.observableKind &&
            requireNonNull == that.requireNonNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(TypeHelper.hashCode(field), type, observableKind, requireNonNull);
    }

}
