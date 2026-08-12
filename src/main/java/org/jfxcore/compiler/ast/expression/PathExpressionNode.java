// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInvoker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PathExpressionNode extends AbstractNode implements ExpressionNode {

    private final BindingOperator operator;
    private final List<PathSegmentNode> segments;
    private BindingContextNode bindingContext;

    public PathExpressionNode(
            BindingOperator operator,
            BindingContextNode bindingContext,
            Collection<? extends PathSegmentNode> segments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.bindingContext = checkNotNull(bindingContext);
        this.segments = new ArrayList<>(checkNotNull(segments));
    }

    public BindingOperator getOperator() {
        return operator;
    }

    public BindingContextNode getBindingContext() {
        return bindingContext;
    }

    @Override
    public int getBindingDistance() {
        return bindingContext.getBindingDistance();
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
            .map(PathSegmentNode::getText)
            .collect(Collectors.joining("."));
    }

    public ResolvedPath resolvePath(boolean preferObservable) {
        return resolvePath(preferObservable, true, Integer.MAX_VALUE);
    }

    public ResolvedPath resolvePath(boolean preferObservable, int limit) {
        return resolvePath(preferObservable, true, limit);
    }

    private ResolvedPath resolvePath(boolean preferObservable, boolean mayResolveAgainstImports, int limit) {
        mayResolveAgainstImports &= bindingContext.mayResolveAgainstImports();

        return resolvePathImpl(preferObservable, mayResolveAgainstImports, limit);
    }

    private ResolvedPath resolvePathImpl(boolean preferObservable, boolean mayResolveAgainstImports, int limit) {
        try {
            return ResolvedPath.parse(
                bindingContext.toSegment(),
                segments.stream().limit(limit).toList(),
                bindingContext.getSelector() == BindingContextSelector.STATIC,
                preferObservable,
                getSourceInfo());
        } catch (MarkupException ex) {
            if (!mayResolveAgainstImports) {
                throw ex;
            }

            // If we don't have a valid path expression, the only other possible interpretation would be
            // that the path begins with the name of a (possibly fully qualified) class.
            Resolver resolver = new Resolver(SourceInfo.none());
            StringBuilder classBuilder = new StringBuilder();
            TypeDeclaration type = null;
            int staticLimit = -1;

            for (int candidateLimit = 0; candidateLimit < segments.size() - 1; ++candidateLimit) {
                PathSegmentNode segment = segments.get(candidateLimit);

                // If the path contains an observable selector, it can't be the name of a class.
                if (segment.isObservableSelector()) {
                    throw ex;
                }

                if (!classBuilder.isEmpty()) {
                    classBuilder.append('.');
                }

                classBuilder.append(segment.getText());

                TypeDeclaration candidate = resolver.tryResolveNestedClass(
                    bindingContext.getType().getTypeDeclaration(), classBuilder.toString());

                if (candidate == null) {
                    candidate = resolver.tryResolveClassAgainstImports(classBuilder.toString());
                }

                if (candidate != null) {
                    type = candidate;
                    staticLimit = candidateLimit;
                }
            }

            // The path doesn't start with the name of a class, so let's throw the original exception.
            if (type == null) {
                throw ex;
            }

            // Create a new path expression that uses a STATIC binding context.
            var newPathExpression = new PathExpressionNode(
                operator,
                new BindingContextNode(
                    BindingContextSelector.STATIC,
                    new TypeInvoker(SourceInfo.none()).invokeType(type),
                    Integer.MAX_VALUE,
                    SourceInfo.none()),
                segments.stream().skip(staticLimit + 1).toList(),
                getSourceInfo());

            return newPathExpression.resolvePath(false, false, Integer.MAX_VALUE);
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        bindingContext = (BindingContextNode) bindingContext.accept(visitor);
        acceptChildren(segments, visitor, PathSegmentNode.class);
    }

    @Override
    public PathExpressionNode deepClone() {
        return new PathExpressionNode(
            operator, bindingContext.deepClone(), deepClone(segments), getSourceInfo()).copy(this);
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
