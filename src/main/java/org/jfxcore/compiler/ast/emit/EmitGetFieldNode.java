// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Objects;

import static org.jfxcore.compiler.type.TypeSymbols.*;

/**
 * Gets the value of a field and places it on top of the operand stack, applying boxing and/or numeric conversions
 * to coerce the value to the requested type.
 */
public class EmitGetFieldNode extends AbstractNode implements ValueEmitterNode, NullableInfo, ParentStackInfo {

    private final FieldDeclaration field;
    private final ResolvedTypeNode type;
    private final boolean requireNonNull;
    private final boolean loadRoot;


    public EmitGetFieldNode(
            FieldDeclaration field,
            TypeInstance type,
            boolean requireNonNull,
            boolean loadRoot,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.field = checkNotNull(field);
        this.type = new ResolvedTypeNode(type, sourceInfo);
        this.requireNonNull = requireNonNull;
        this.loadRoot = loadRoot;

        if (loadRoot && field.isStatic()) {
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
        TypeDeclaration fieldType = field.type();

        if (field.isStatic()) {
            code.getstatic(field);
        } else {
            if (loadRoot) {
                code.aload(context.getRuntimeContextLocal())
                    .invoke(context.getRuntimeContextClass()
                                   .requireDeclaredMethod(RuntimeContextGenerator.GET_ROOT_METHOD))
                    .checkcast(context.getMarkupClass());
            }

            code.getfield(field);
        }

        if (requireNonNull) {
            code.ldc(field.name())
                .invoke(ObjectsDecl().requireDeclaredMethod("requireNonNull", ObjectDecl(), StringDecl()))
                .checkcast(type.getTypeDeclaration());
        } else {
            code.castconv(fieldType, type.getTypeDeclaration());
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
        return field.equals(that.field)
            && type.equals(that.type)
            && requireNonNull == that.requireNonNull
            && loadRoot == that.loadRoot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, type, requireNonNull, loadRoot);
    }
}
