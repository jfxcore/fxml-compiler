// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import javassist.CtClass;
import javassist.CtMethod;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.emit.EmitMapToBooleanNode;
import org.jfxcore.compiler.ast.emit.EmitConvertToBooleanNode;
import org.jfxcore.compiler.ast.emit.EmitMethodCallNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.ExceptionHelper.*;

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

        return switch (bindingMode) {
            case ONCE -> new EmitConvertToBooleanNode(child, this, child.getSourceInfo());

            case UNIDIRECTIONAL -> {
                Resolver resolver = new Resolver(child.getSourceInfo());
                TypeInstance typeInstance = resolver.getTypeInstance(TypeHelper.getJvmType(child));
                TypeInstance argType = resolver.findObservableArgument(typeInstance);

                if (!argType.equals(BooleanType()) && !argType.equals(CtClass.booleanType)) {
                    yield new EmitMapToBooleanNode(child, this == NOT, child.getSourceInfo());
                }

                if (this == NOT) {
                    CtMethod method = unchecked(SourceInfo.none(), () -> BindingsType().getMethod(
                        "not", "(Ljavafx/beans/value/ObservableBooleanValue;)Ljavafx/beans/binding/BooleanBinding;"));

                    yield new EmitMethodCallNode(method, List.of(), List.of(child), child.getSourceInfo());
                }

                yield child;
            }

            case BIDIRECTIONAL -> {
                child.setNodeData(NodeDataKey.BIND_BIDIRECTIONAL_NEGATED, Boolean.TRUE);
                yield child;
            }

            default -> throw new IllegalArgumentException("bindingMode");
        };
    }

}
