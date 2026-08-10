// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PathNode extends DerivedTextNode {

    private final List<PathSegmentNode> segments;
    private final List<PathNode> arguments;
    private ContextSelectorNode contextSelector;

    public PathNode(
            @Nullable ContextSelectorNode contextSelector,
            Collection<? extends PathSegmentNode> segments,
            Collection<? extends PathNode> arguments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.contextSelector = contextSelector;
        this.segments = new ArrayList<>(checkNotNull(segments));
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    private PathNode(
            @Nullable ContextSelectorNode contextSelector,
            Collection<? extends PathSegmentNode> segments,
            Collection<? extends PathNode> arguments,
            TypeNode type,
            SourceInfo sourceInfo) {
        super(type, sourceInfo);
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

    @Override
    public String formatText() {
        String path = formatPath(contextSelector, segments);
        if (arguments.isEmpty()) {
            return path;
        }

        return path + "<" + arguments.stream()
            .map(PathNode::formatText)
            .collect(Collectors.joining(",")) + ">";
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
        return new PathNode(
            contextSelector != null ? contextSelector.deepClone() : null,
            deepClone(segments), deepClone(arguments), getType().deepClone(), getSourceInfo()).copy(this);
    }

    private static String formatPath(
            @Nullable ContextSelectorNode contextSelector,
            Collection<? extends PathSegmentNode> segments) {
        var text = new StringBuilder();
        boolean firstSegment = contextSelector == null;

        if (contextSelector != null) {
            text.append(':').append(contextSelector.formatText());
        }

        for (PathSegmentNode segment : segments) {
            if (firstSegment) {
                firstSegment = false;
                if (segment.isObservableSelector()) {
                    text.append("::");
                }
            } else {
                text.append(segment.isObservableSelector() ? "::" : ".");
            }

            if (segment instanceof AttachedSegmentNode) {
                text.append("(").append(segment.formatText()).append(")");
            } else {
                text.append(segment.formatText());
            }
        }

        return text.toString();
    }
}
