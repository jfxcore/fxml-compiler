// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Objects;

/**
 * Emits the child value, then replaces it with {@code true} or {@code false} as specified by {@link Operator}.
 */
public class EmitConvertToBooleanNode extends AbstractNode implements ValueEmitterNode {

    private final Operator operator;
    private final ResolvedTypeNode type;
    private EmitterNode child;

    public EmitConvertToBooleanNode(EmitterNode child, Operator operator, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.child = checkNotNull(child);
        this.operator = checkNotNull(operator);
        this.type = new ResolvedTypeNode(new Resolver(sourceInfo).getTypeInstance(CtClass.booleanType), sourceInfo);
    }

    @Override
    public TypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.emit(child);

        Bytecode code = context.getOutput();
        CtClass type = TypeHelper.getJvmType(child);

        if (operator == Operator.NOT) {
            if (type.isPrimitive()) {
                if (type == CtClass.doubleType) {
                    code.dconst(0)
                        .dcmpl();
                } else if (type == CtClass.longType) {
                    code.lconst(0)
                        .lcmp();
                }

                code.ifeq(() -> code.iconst(1), () -> code.iconst(0));
            } else {
                code.ifnull(() -> code.iconst(1), () -> code.iconst(0));
            }
        } else if (operator == Operator.BOOLIFY) {
            if (type.isPrimitive()) {
                if (type == CtClass.doubleType) {
                    code.dconst(0)
                        .dcmpl();
                } else if (type == CtClass.longType) {
                    code.lconst(0)
                        .lcmp();
                }

                code.ifeq(() -> code.iconst(0), () -> code.iconst(1));
            } else {
                code.ifnull(() -> code.iconst(0), () -> code.iconst(1));
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (EmitterNode)child.accept(visitor);
    }

    @Override
    public EmitConvertToBooleanNode deepClone() {
        return new EmitConvertToBooleanNode(child.deepClone(), operator, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitConvertToBooleanNode that = (EmitConvertToBooleanNode)o;
        return operator == that.operator &&
            type.equals(that.type) &&
            child.equals(that.child);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, type, child);
    }

}
