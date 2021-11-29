// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.util.ObservableFunctionEmitterFactory;
import org.jfxcore.compiler.ast.expression.util.SimpleFunctionEmitterFactory;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class FunctionExpressionNode extends AbstractNode implements ExpressionNode {

    private final List<Node> arguments;
    private PathExpressionNode path;
    private TextNode inverseMethod;

    public FunctionExpressionNode(
            PathExpressionNode path,
            @Nullable TextNode inverseMethod,
            Collection<? extends Node> arguments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.path = checkNotNull(path);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.inverseMethod = inverseMethod;
    }

    public PathExpressionNode getPath() {
        return path;
    }

    public List<Node> getArguments() {
        return arguments;
    }

    @Nullable
    public TextNode getInverseMethod() {
        return inverseMethod;
    }

    @Override
    public BindingEmitterInfo toEmitter(BindingMode bindingMode, TypeInstance invokingType) {
        boolean bidirectional = bindingMode == BindingMode.BIDIRECTIONAL;

        BindingEmitterInfo emitterInfo = bindingMode.isObservable() ?
            new ObservableFunctionEmitterFactory(this, invokingType).newInstance(bidirectional) :
            new SimpleFunctionEmitterFactory(this, invokingType).newInstance();

        if (emitterInfo == null) {
            emitterInfo = new SimpleFunctionEmitterFactory(this, invokingType).newInstance();
        }

        return emitterInfo;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        path = (PathExpressionNode)path.accept(visitor);
        if (inverseMethod != null) {
            inverseMethod = (TextNode)inverseMethod.accept(visitor);
        }

        acceptChildren(arguments, visitor);
    }

    @Override
    public FunctionExpressionNode deepClone() {
        return new FunctionExpressionNode(
            path.deepClone(),
            inverseMethod != null ? inverseMethod.deepClone() : null,
            deepClone(arguments),
            getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionExpressionNode that = (FunctionExpressionNode)o;
        return arguments.equals(that.arguments) &&
            path.equals(that.path) &&
            Objects.equals(inverseMethod, that.inverseMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(arguments, path, inverseMethod);
    }

}
