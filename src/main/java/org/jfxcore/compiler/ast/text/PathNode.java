// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PathNode extends TextNode {

    private final List<PathSegmentNode> segments;
    private final List<PathNode> arguments;
    private ContextSelectorNode contextSelector;

    public PathNode(
            @Nullable ContextSelectorNode contextSelector,
            Collection<? extends PathSegmentNode> segments,
            Collection<? extends PathNode> arguments,
            SourceInfo sourceInfo) {
        super(formatPath(contextSelector, segments), sourceInfo);
        this.contextSelector = contextSelector;
        this.segments = new ArrayList<>(checkNotNull(segments));
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    public @Nullable ContextSelectorNode getContextSelector() {
        return contextSelector;
    }

    public List<PathSegmentNode> getSegments() {
        return segments;
    }

    public List<PathNode> getArguments() {
        return arguments;
    }

    public TypeInstance resolve() {
        SourceInfo sourceInfo = SourceInfo.span(
            segments.get(0).getSourceInfo(),
            segments.get(segments.size() - 1).getSourceInfo());

        String typeName = segments.stream().map(PathSegmentNode::getText).collect(Collectors.joining("."));

        return new TypeInvoker(sourceInfo).invokeType(
            new Resolver(sourceInfo).resolveClassAgainstImports(typeName),
            arguments.stream()
                .map(PathNode::resolve)
                .collect(Collectors.toList()));
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(segments, visitor, PathSegmentNode.class);
        acceptChildren(arguments, visitor, PathNode.class);

        if (contextSelector != null) {
            contextSelector = (ContextSelectorNode)contextSelector.accept(visitor);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PathNode pathNode = (PathNode) o;
        return Objects.equals(segments, pathNode.segments)
            && Objects.equals(arguments, pathNode.arguments)
            && Objects.equals(contextSelector, pathNode.contextSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), segments, arguments, contextSelector);
    }

    @Override
    public PathNode deepClone() {
        return new PathNode(contextSelector, deepClone(segments), deepClone(arguments), getSourceInfo());
    }

    private static String formatPath(
            @Nullable ContextSelectorNode contextSelector,
            Collection<? extends PathSegmentNode> segments) {
        var text = new StringBuilder();
        boolean firstSegment = true;

        if (contextSelector != null) {
            text.append(contextSelector.getText()).append("/");
        }

        for (PathSegmentNode segment : segments) {
            if (firstSegment) {
                firstSegment = false;
            } else {
                text.append(segment.isObservableSelector() ? "::" : ".");
            }

            if (segment instanceof SubPathSegmentNode) {
                text.append("(").append(segment.getText()).append(")");
            } else {
                text.append(segment.getText());
            }
        }

        return text.toString();
    }
}
