// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.CtField;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.util.Bytecode;
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
public class EmitGetFieldNode extends AbstractNode implements ValueEmitterNode, NullableInfo, ParentStackInfo {

    private final CtField field;
    private final ResolvedTypeNode type;
    private final boolean requireNonNull;
    private final boolean loadRoot;

    public EmitGetFieldNode(
            CtField field,
            TypeInstance type,
            boolean requireNonNull,
            boolean loadRoot,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.field = checkNotNull(field);
        this.type = new ResolvedTypeNode(type, sourceInfo);
        this.requireNonNull = requireNonNull;
        this.loadRoot = loadRoot;

        if (loadRoot && Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("loadRoot");
        }
    }

    @Override
    public boolean isNullable() {
        return !requireNonNull;
    }

    @Override
    public boolean needsParentStack() {
        return loadRoot;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass fieldType = unchecked(this.field::getType);

        if (Modifier.isStatic(field.getModifiers())) {
            code.getstatic(field.getDeclaringClass(), field.getName(), types(fieldType));
        } else {
            if (loadRoot) {
                code.aload(context.getRuntimeContextLocal())
                    .invokevirtual(context.getRuntimeContextClass(),
                                   RuntimeContextGenerator.GET_ROOT_METHOD,
                                   function(ObjectType()))
                    .checkcast(context.getMarkupClass());
            }

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
        return new EmitGetFieldNode(field, type.getTypeInstance(), requireNonNull, loadRoot, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitGetFieldNode that = (EmitGetFieldNode)o;
        return TypeHelper.equals(field, that.field)
            && type.equals(that.type)
            && requireNonNull == that.requireNonNull
            && loadRoot == that.loadRoot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(TypeHelper.hashCode(field), type, requireNonNull, loadRoot);
    }
}
