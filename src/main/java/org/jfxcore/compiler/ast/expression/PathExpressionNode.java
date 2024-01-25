// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import javassist.CtClass;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.expression.util.ObservablePathEmitterFactory;
import org.jfxcore.compiler.ast.expression.util.SimplePathEmitterFactory;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PathExpressionNode extends AbstractNode implements ExpressionNode {

    private final Operator operator;
    private final List<PathSegmentNode> segments;
    private BindingContextNode bindingContext;
    private ResolvedPath resolvedPath;
    private ResolvedPath resolvedObservablePath;

    public PathExpressionNode(
            Operator operator,
            BindingContextNode bindingContext,
            Collection<? extends PathSegmentNode> segments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.bindingContext = checkNotNull(bindingContext);
        this.segments = new ArrayList<>(checkNotNull(segments));
    }

    public Operator getOperator() {
        return operator;
    }

    public BindingContextNode getBindingContext() {
        return bindingContext;
    }

    public List<PathSegmentNode> getSegments() {
        return segments;
    }

    public boolean isSimplePath() {
        return segments.stream().noneMatch(
            segment -> segment.isObservableSelector() || !(segment instanceof TextSegmentNode));
    }

    public String getSimplePath() {
        return getSimplePath(Integer.MAX_VALUE);
    }

    public String getSimplePath(int limit) {
        if (!isSimplePath()) {
            throw ParserErrors.invalidExpression(getSourceInfo());
        }

        return segments.stream()
            .limit(limit)
            .map(TextNode::getText)
            .collect(Collectors.joining("."));
    }

    public ResolvedPath resolvePath(boolean preferObservable) {
        return resolvePath(preferObservable, Integer.MAX_VALUE);
    }

    public ResolvedPath resolvePath(boolean preferObservable, int limit) {
        if (preferObservable) {
            if (resolvedObservablePath != null) {
                return resolvedObservablePath;
            }

            return resolvedObservablePath = resolvePathImpl(true, limit);
        }

        if (resolvedPath != null) {
            return resolvedPath;
        }

        return resolvedPath = resolvePathImpl(false, limit);
    }

    private ResolvedPath resolvePathImpl(boolean preferObservable, int limit) {
        try {
            return ResolvedPath.parse(
                bindingContext.toSegment(),
                segments.stream().limit(limit).toList(),
                bindingContext.getSelector() == BindingContextSelector.STATIC,
                preferObservable,
                getSourceInfo());
        } catch (MarkupException ex) {
            // If we don't have a valid path expression, the only other possible interpretation would be
            // that the path begins with the name of a (possibly fully qualified) class.
            Resolver resolver = new Resolver(SourceInfo.none());
            StringBuilder classBuilder = new StringBuilder();
            CtClass type = null;
            int staticLimit = 0;

            while (staticLimit < getSegments().size() - 1) {
                PathSegmentNode segment = getSegments().get(staticLimit);

                // If the path contains an observable selector, it can't be the name of a class.
                if (segment.isObservableSelector()) {
                    throw ex;
                }

                if (!classBuilder.isEmpty()) {
                    classBuilder.append('.');
                }

                classBuilder.append(segment.getText());
                type = resolver.tryResolveClassAgainstImports(classBuilder.toString());
                if (type != null) {
                    break;
                }

                ++staticLimit;
            }

            // The path doesn't start with the name of a class, so let's throw the original exception.
            if (type == null) {
                throw ex;
            }

            // Create a new path expression that uses a STATIC binding context.
            var newPathExpression = new PathExpressionNode(
                getOperator(),
                new BindingContextNode(
                    BindingContextSelector.STATIC,
                    resolver.getTypeInstance(type),
                    Integer.MAX_VALUE,
                    SourceInfo.none()),
                getSegments().stream().skip(staticLimit + 1).toList(),
                getSourceInfo());

            return newPathExpression.resolvePath(false);
        }
    }

    @Override
    public BindingEmitterInfo toEmitter(BindingMode bindingMode, TypeInstance invokingType) {
        boolean bidirectional = bindingMode == BindingMode.BIDIRECTIONAL;

        BindingEmitterInfo emitterInfo = bindingMode.isObservable() ?
            new ObservablePathEmitterFactory(this).newInstance(bidirectional) :
            new SimplePathEmitterFactory(this).newInstance();

        if (emitterInfo == null) {
            emitterInfo = new SimplePathEmitterFactory(this).newInstance();
        }

        return emitterInfo;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        bindingContext = (BindingContextNode) bindingContext.accept(visitor);
        acceptChildren(segments, visitor, PathSegmentNode.class);
    }

    @Override
    public PathExpressionNode deepClone() {
        return new PathExpressionNode(operator, bindingContext.deepClone(), deepClone(segments), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathExpressionNode that = (PathExpressionNode)o;
        return operator == that.operator &&
            bindingContext.equals(that.bindingContext) &&
            segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, bindingContext, segments);
    }

}
