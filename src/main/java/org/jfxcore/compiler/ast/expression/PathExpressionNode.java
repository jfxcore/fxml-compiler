// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.util.ObservablePathEmitterFactory;
import org.jfxcore.compiler.ast.expression.util.SimplePathEmitterFactory;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PathExpressionNode extends AbstractNode implements ExpressionNode {

    private static final Pattern PATH_SPLIT_PATTERN = Pattern.compile(
        "::\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*|" +
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

    private final Operator operator;
    private final String path;
    private BindingContextNode source;
    private ResolvedPath resolvedPath;
    private ResolvedPath resolvedObservablePath;

    public PathExpressionNode(
            Operator operator, BindingContextNode source, String path, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.path = checkNotNull(path);
        this.source = checkNotNull(source);
    }

    public Operator getOperator() {
        return operator;
    }

    public BindingContextNode getSource() {
        return source;
    }

    public String getPath() {
        return path;
    }

    public ResolvedPath resolvePath(boolean preferObservable) {
        return resolvePath(preferObservable, false);
    }

    public ResolvedPath resolvePath(boolean preferObservable, boolean assumeMethod) {
        // If we assume that the path points to a method (i.e. the last segment is the method name),
        // we drop the last segment because it will not be part of the path.
        String[] path = getSplitPath();
        if (assumeMethod) {
            path = Arrays.copyOf(path, path.length - 1);
        }
        
        if (preferObservable) {
            if (resolvedObservablePath != null) {
                return resolvedObservablePath;
            }

            return resolvedObservablePath = ResolvedPath.parse(
                source.toSegment(), path,true, getSourceInfo());
        }

        if (resolvedPath != null) {
            return resolvedPath;
        }

        return resolvedPath = ResolvedPath.parse(
            source.toSegment(), path, false, getSourceInfo());
    }

    private String[] getSplitPath() {
        return PATH_SPLIT_PATTERN
            .matcher(this.path)
            .results()
            .map(MatchResult::group)
            .collect(Collectors.toList())
            .toArray(String[]::new);
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
        source = (BindingContextNode)source.accept(visitor);
    }

    @Override
    public PathExpressionNode deepClone() {
        return new PathExpressionNode(operator, source.deepClone(), path, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathExpressionNode that = (PathExpressionNode)o;
        return operator == that.operator &&
            path.equals(that.path) &&
            source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, path, source);
    }

}
