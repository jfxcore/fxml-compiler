// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.util.ObservableConstructorEmitterFactory;
import org.jfxcore.compiler.ast.expression.util.SimpleConstructorEmitterFactory;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Semantic representation of a leading or enclosing-instance-qualified constructor.
 */
public final class ConstructorExpressionNode extends AbstractNode implements ExpressionNode {

    private final TypeDeclaration invocationContext;
    private final List<PathNode> constructorWitnesses;
    private final List<PathNode> classArguments;
    private final List<Node> arguments;

    private @Nullable ExpressionNode qualifier;
    private @Nullable PathExpressionNode inversePath;
    private PathNode constructedType;

    public ConstructorExpressionNode(
            TypeDeclaration invocationContext,
            @Nullable ExpressionNode qualifier,
            @Nullable PathExpressionNode inversePath,
            Collection<? extends PathNode> constructorWitnesses,
            PathNode constructedType,
            Collection<? extends PathNode> classArguments,
            Collection<? extends Node> arguments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.invocationContext = checkNotNull(invocationContext);
        this.qualifier = qualifier;
        this.inversePath = inversePath;
        this.constructorWitnesses = new ArrayList<>(checkNotNull(constructorWitnesses));
        this.constructedType = checkNotNull(constructedType);
        this.classArguments = new ArrayList<>(checkNotNull(classArguments));
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    public TypeDeclaration getInvocationContext() {
        return invocationContext;
    }

    public @Nullable ExpressionNode getQualifier() {
        return qualifier;
    }

    public @Nullable PathExpressionNode getInversePath() {
        return inversePath;
    }

    public List<PathNode> getConstructorWitnesses() {
        return constructorWitnesses;
    }

    public PathNode getConstructedType() {
        return constructedType;
    }

    public List<PathNode> getClassArguments() {
        return classArguments;
    }

    public List<Node> getArguments() {
        return arguments;
    }

    @Override
    public int getBindingDistance() {
        int result = qualifier != null ? qualifier.getBindingDistance() : NO_BINDING_DISTANCE;

        if (inversePath != null) {
            result = Math.min(result, inversePath.getBindingDistance());
        }

        for (Node argument : arguments) {
            result = Math.min(result, ExpressionNode.bindingDistance(argument));
        }

        return result;
    }

    @Override
    public BindingEmitterInfo toEmitter(
            BindingMode bindingMode,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType) {
        if (bindingMode.isContent()) {
            throw GeneralErrors.expressionNotApplicable(getSourceInfo(), false);
        }

        if (bindingMode.isReverse()) {
            throw BindingSourceErrors.expressionNotInvertible(getSourceInfo());
        }

        BindingEmitterInfo emitterInfo = bindingMode.isObservable()
            ? new ObservableConstructorEmitterFactory(this, invokingType).newInstance(bindingMode.isBidirectional())
            : new SimpleConstructorEmitterFactory(this, invokingType).newInstance();

        if (emitterInfo == null) {
            emitterInfo = new SimpleConstructorEmitterFactory(this, invokingType).newInstance();
        }

        return emitterInfo;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);

        if (qualifier != null) {
            qualifier = (ExpressionNode)qualifier.accept(visitor);
        }

        if (inversePath != null) {
            inversePath = (PathExpressionNode)inversePath.accept(visitor);
        }

        acceptChildren(constructorWitnesses, visitor, PathNode.class);
        constructedType = (PathNode)constructedType.accept(visitor);
        acceptChildren(classArguments, visitor, PathNode.class);
        acceptChildren(arguments, visitor, Node.class);
    }

    @Override
    public ConstructorExpressionNode deepClone() {
        return new ConstructorExpressionNode(
            invocationContext,
            qualifier != null ? qualifier.deepClone() : null,
            inversePath != null ? inversePath.deepClone() : null,
            deepClone(constructorWitnesses),
            constructedType.deepClone(),
            deepClone(classArguments),
            deepClone(arguments),
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ConstructorExpressionNode that
            && invocationContext.equals(that.invocationContext)
            && Objects.equals(qualifier, that.qualifier)
            && Objects.equals(inversePath, that.inversePath)
            && constructorWitnesses.equals(that.constructorWitnesses)
            && constructedType.equals(that.constructedType)
            && classArguments.equals(that.classArguments)
            && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            invocationContext, qualifier, constructorWitnesses,
            inversePath, constructedType, classArguments, arguments);
    }
}
