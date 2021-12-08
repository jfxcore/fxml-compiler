// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.NotFoundException;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Emits its child node, which places an {@link javafx.beans.value.ObservableValue} on top of the operand stack.
 * Then converts the observable value to a {@link javafx.beans.binding.BooleanBinding} as specified by {@link Operator}.
 */
public class EmitConvertToBooleanBindingNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final Operator operator;
    private final ResolvedTypeNode type;
    private EmitterNode child;

    public EmitConvertToBooleanBindingNode(EmitterNode child, Operator operator, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.child = checkNotNull(child);
        this.operator = checkNotNull(operator);
        this.type = new ResolvedTypeNode(new Resolver(sourceInfo).getTypeInstance(ObservableBooleanValueType()), sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.emit(child);

        Resolver resolver = new Resolver(getSourceInfo());
        TypeInstance typeInstance = resolver.getTypeInstance(TypeHelper.getJvmType(child));
        CtClass type = resolver.findObservableArgument(typeInstance).jvmType();
        boolean isBoolean = unchecked(() -> type.subtypeOf(BooleanType()));

        if (isBoolean && operator == Operator.BOOLIFY) {
            return;
        }

        emitBinding(isBoolean, context.getOutput());
    }

    private void emitBinding(boolean isBoolean, Bytecode code) {
        if (!isBoolean) {
            if (operator == Operator.BOOLIFY) {
                invokestaticSafe(code, BindingsType(), "toBoolean", function(BooleanBindingType(), ObservableValueType()));
            } else if (operator == Operator.NOT) {
                invokestaticSafe(code, BindingsType(), "toBoolean", function(BooleanBindingType(), ObservableValueType()));
                code.invokestatic(BindingsType(), "not", function(BooleanBindingType(), ObservableBooleanValueType()));
            } else {
                throw new UnsupportedOperationException();
            }
        } else {
            if (operator == Operator.NOT) {
                code.invokestatic(BindingsType(), "not", function(BooleanBindingType(), ObservableBooleanValueType()));
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (EmitterNode)child.accept(visitor);
    }

    @Override
    public EmitConvertToBooleanBindingNode deepClone() {
        return new EmitConvertToBooleanBindingNode(child.deepClone(), operator, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitConvertToBooleanBindingNode that = (EmitConvertToBooleanBindingNode)o;
        return operator == that.operator &&
            type.equals(that.type) &&
            child.equals(that.child);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, type, child);
    }

    /**
     * Check whether the method actually exists, as it is exclusive to the JFXcore runtime
     */
    private void invokestaticSafe(Bytecode code, CtClass type, String name, String desc) {
        try {
            type.getMethod(name, desc);
        } catch (NotFoundException ex) {
            throw BindingSourceErrors.bindingNotSupported(getSourceInfo());
        }

        code.invokestatic(type, name, desc);
    }

}
