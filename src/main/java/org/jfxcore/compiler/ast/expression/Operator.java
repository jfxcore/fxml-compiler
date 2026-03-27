// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.emit.EmitConvertToBooleanNode;
import org.jfxcore.compiler.ast.emit.EmitMapToBooleanNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;

import static org.jfxcore.compiler.type.TypeSymbols.*;

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

        return TypeInstance.booleanType();
    }

    public boolean isInvertible(TypeInstance operandType) {
        return switch (this) {
            case IDENTITY -> true;
            case NOT, BOOLIFY -> operandType.equals(booleanDecl()) || operandType.equals(BooleanDecl());
        };
    }

    public boolean isBoolean() {
        return this == BOOLIFY || this == NOT;
    }

    public ValueEmitterNode toEmitter(ValueEmitterNode child, BindingMode bindingMode) {
        if (this == IDENTITY) {
            return child;
        }

        return switch (bindingMode) {
            case ONCE -> new EmitConvertToBooleanNode(child, this, child.getSourceInfo());

            case UNIDIRECTIONAL -> {
                Resolver resolver = new Resolver(child.getSourceInfo());
                TypeInstance typeInstance = TypeHelper.getTypeInstance(child);
                TypeInstance argType = resolver.findObservableArgument(typeInstance);

                if (this == BOOLIFY && (argType.equals(BooleanDecl()) || argType.equals(booleanDecl()))) {
                    yield child;
                }

                yield new EmitMapToBooleanNode(child, this == NOT, child.getSourceInfo());
            }

            case BIDIRECTIONAL -> {
                if (this == NOT) {
                    child.setNodeData(NodeDataKey.BIND_BIDIRECTIONAL_INVERT_BOOLEAN, Boolean.TRUE);
                }

                yield child;
            }

            default -> throw new IllegalArgumentException("bindingMode");
        };
    }
}
