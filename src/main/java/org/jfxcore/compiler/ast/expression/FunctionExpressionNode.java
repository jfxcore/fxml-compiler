// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.util.ObservableFunctionEmitterFactory;
import org.jfxcore.compiler.ast.expression.util.SimpleFunctionEmitterFactory;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class FunctionExpressionNode extends AbstractNode implements ExpressionNode {

    private final CtClass invocationContext;
    private final List<Node> arguments;
    private PathExpressionNode path;
    private PathExpressionNode inversePath;

    public FunctionExpressionNode(
            CtClass invocationContext,
            PathExpressionNode path,
            Collection<? extends Node> arguments,
            @Nullable PathExpressionNode inversePath,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.invocationContext = invocationContext;
        this.path = checkNotNull(path);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.inversePath = inversePath;
    }

    public CtClass getInvocationContext() {
        return invocationContext;
    }

    public PathExpressionNode getPath() {
        return path;
    }

    public List<Node> getArguments() {
        return arguments;
    }

    @Nullable
    public PathExpressionNode getInversePath() {
        return inversePath;
    }

    @Override
    public BindingEmitterInfo toEmitter(BindingMode bindingMode,
                                        TypeInstance invokingType,
                                        @Nullable TypeInstance targetType) {
        boolean bidirectional = bindingMode == BindingMode.BIDIRECTIONAL;

        BindingEmitterInfo emitterInfo = bindingMode.isObservable() ?
            new ObservableFunctionEmitterFactory(this, invokingType, targetType).newInstance(bidirectional) :
            new SimpleFunctionEmitterFactory(this, invokingType, targetType).newInstance();

        if (emitterInfo == null) {
            emitterInfo = new SimpleFunctionEmitterFactory(this, invokingType, targetType).newInstance();
        }

        return emitterInfo;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        path = (PathExpressionNode)path.accept(visitor);
        acceptChildren(arguments, visitor, Node.class);

        if (inversePath != null) {
            inversePath = (PathExpressionNode) inversePath.accept(visitor);
        }
    }

    @Override
    public FunctionExpressionNode deepClone() {
        return new FunctionExpressionNode(
            invocationContext,
            path.deepClone(),
            deepClone(arguments),
            inversePath != null ? inversePath.deepClone() : null,
            getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionExpressionNode that = (FunctionExpressionNode)o;
        return TypeHelper.equals(invocationContext, that.invocationContext) &&
            path.equals(that.path) &&
            arguments.equals(that.arguments) &&
            Objects.equals(inversePath, that.inversePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(TypeHelper.hashCode(invocationContext), path, arguments, inversePath);
    }

}
