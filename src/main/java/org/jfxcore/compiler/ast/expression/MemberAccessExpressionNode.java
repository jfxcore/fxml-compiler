// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitInvariantPathNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.path.ExpressionSegment;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Property-path selection rooted in an arbitrary expression result.
 */
public final class MemberAccessExpressionNode extends AbstractNode implements ExpressionNode {

    private final BindingOperator operator;
    private final List<PathSegmentNode> segments;
    private ExpressionNode receiver;
    private transient Map<ResolutionKey, ResolvedPath> resolvedPaths;

    public MemberAccessExpressionNode(
            BindingOperator operator,
            ExpressionNode receiver,
            Collection<? extends PathSegmentNode> segments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.receiver = checkNotNull(receiver);
        this.segments = new ArrayList<>(checkNotNull(segments));

        if (this.segments.isEmpty()) {
            throw new IllegalArgumentException("segments");
        }
    }

    public BindingOperator getOperator() {
        return operator;
    }

    public ExpressionNode getReceiver() {
        return receiver;
    }

    public List<PathSegmentNode> getSegments() {
        return segments;
    }

    @Override
    public int getBindingDistance() {
        return receiver.getBindingDistance();
    }

    public ResolvedPath resolvePath(boolean preferObservable, TypeInstance invokingType) {
        if (resolvedPaths == null) {
            resolvedPaths = new HashMap<>();
        }

        var key = new ResolutionKey(invokingType, preferObservable);

        return resolvedPaths.computeIfAbsent(key, ignored -> {
            BindingEmitterInfo receiverInfo = receiver.toEmitter(
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                invokingType,
                null);

            return ResolvedPath.parse(
                new ExpressionSegment(receiverInfo), segments, false,
                preferObservable, getSourceInfo());
        });
    }

    @Override
    public BindingEmitterInfo toEmitter(
            BindingMode bindingMode,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType) {
        boolean bidirectional = bindingMode == BindingMode.BIDIRECTIONAL;

        if (bindingMode.isObservable()) {
            BindingEmitterInfo observable = createObservableEmitter(invokingType, bidirectional);
            if (observable != null) {
                return observable;
            }
        }

        return createSimpleEmitter(invokingType);
    }

    private BindingEmitterInfo createSimpleEmitter(TypeInstance invokingType) {
        ResolvedPath path = resolvePath(false, invokingType);
        ValueEmitterNode value = new EmitInvariantPathNode(path.toValueEmitters(getSourceInfo()), getSourceInfo());
        value = operator.toEmitter(value, BindingMode.ONCE);
        Segment last = path.get(path.size() - 1);

        return new BindingEmitterInfo(
            value, TypeHelper.getTypeInstance(value), null, ValueSourceKind.NONE,
            path.getObservableDependencyKind(), last.getDeclaringType(), last.getDisplayName(),
            false, false, getSourceInfo());
    }

    private @Nullable BindingEmitterInfo createObservableEmitter(TypeInstance invokingType, boolean bidirectional) {
        ResolvedPath path = resolvePath(true, invokingType);
        Segment last = path.get(path.size() - 1);

        if (bidirectional && path.getValueSourceKind() != ValueSourceKind.WRITABLE) {
            MarkupException ex = last.getDeclaringType() == null
                ? BindingSourceErrors.invalidBidirectionalBindingSource(
                    getSourceInfo(), last.getValueTypeInstance(), false)
                : BindingSourceErrors.invalidBidirectionalBindingSource(
                    getSourceInfo(), last.getDeclaringType(), last.getDisplayName());
            ex.getProperties().put("sourceType", path.getValueTypeInstance());
            throw ex;
        }

        if (path.isInvariant() || isDirectContentSource(path)) {
            return null;
        }

        var emitPath = new EmitObservablePathNode(path, bidirectional, getSourceInfo());
        ValueEmitterNode value = emitPath;

        if (bidirectional && !operator.isInvertible(path.getValueTypeInstance())) {
            throw BindingSourceErrors.expressionNotInvertible(value.getSourceInfo());
        }

        value = operator.toEmitter(value, bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);

        boolean exposesValueSource = path.getValueSourceKind() != ValueSourceKind.NONE
            || ObservableDependencyKind.get(path.getValueTypeInstance().declaration())
                != ObservableDependencyKind.CONTENT
            && !path.getValueTypeInstance().subtypeOf(CollectionDecl())
            && !path.getValueTypeInstance().subtypeOf(MapDecl());

        return new BindingEmitterInfo(
            value,
            operator.evaluateType(path.getValueTypeInstance()),
            exposesValueSource ? TypeHelper.getTypeInstance(value) : null,
            exposesValueSource ? ValueSourceKind.get(TypeHelper.getTypeDeclaration(value)) : ValueSourceKind.NONE,
            path.getObservableDependencyKind(),
            last.getDeclaringType(),
            last.getDisplayName(),
            false,
            emitPath.isCompiledPath(),
            getSourceInfo());
    }

    private boolean isDirectContentSource(ResolvedPath path) {
        return path.fold().getGroups().length == 1
            && path.getValueSourceKind() == ValueSourceKind.NONE
            && ObservableDependencyKind.get(path.getValueTypeInstance().declaration())
                == ObservableDependencyKind.CONTENT;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);

        if (resolvedPaths != null && !resolvedPaths.isEmpty()) {
            throw new IllegalStateException("Member access cannot be mutated after resolution");
        }

        receiver = (ExpressionNode)receiver.accept(visitor);
        acceptChildren(segments, visitor, PathSegmentNode.class);
    }

    @Override
    public MemberAccessExpressionNode deepClone() {
        return new MemberAccessExpressionNode(
            operator, receiver.deepClone(), deepClone(segments), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MemberAccessExpressionNode that
            && operator == that.operator
            && receiver.equals(that.receiver)
            && segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, receiver, segments);
    }

    private record ResolutionKey(TypeInstance invokingType, boolean preferObservable) {}
}
