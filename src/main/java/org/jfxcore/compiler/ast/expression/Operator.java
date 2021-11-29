// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import javassist.CtClass;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.PropertyKey;
import org.jfxcore.compiler.ast.emit.EmitConvertToBooleanBindingNode;
import org.jfxcore.compiler.ast.emit.EmitConvertToBooleanNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.util.TypeInstance;

public enum Operator {

    IDENTITY,

    /** Converts {@code null}, {@code false} and 0 to {@code true}, all other values to {@code false}. */
    NOT,

    /** Converts {@code null}, {@code false} and 0 to {@code false}, all other values to {@code true}. */
    BOOLIFY;

    /**
     * Returns the type of the expression after the operator has been evaluated for {@code operandType}.
     */
    public TypeInstance evaluateType(TypeInstance operandType) {
        if (this == IDENTITY) {
            return operandType;
        }

        return new TypeInstance(CtClass.booleanType);
    }

    public boolean isInvertible(TypeInstance operandType) {
        return switch (this) {
            case IDENTITY -> true;
            // Disabled until support for negated bidirectional boolean bindings is available
            // case NOT -> operandType.equals(CtClass.booleanType) || operandType.equals(Classes.BooleanType());
            default -> false;
        };
    }

    public ValueEmitterNode toEmitter(ValueEmitterNode child, BindingMode bindingMode) {
        if (this == IDENTITY) {
            return child;
        }

        switch (bindingMode) {
            case UNIDIRECTIONAL:
                return new EmitConvertToBooleanBindingNode(child, this, child.getSourceInfo());

            case BIDIRECTIONAL:
                child.setProperty(PropertyKey.BIND_BIDIRECTIONAL_NEGATED, Boolean.TRUE);
                return child;

            default:
                return new EmitConvertToBooleanNode(child, this, child.getSourceInfo());
        }
    }

}
