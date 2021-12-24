// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class PathNode extends TextNode {

    private final List<PathSegmentNode> segments;
    private ContextSelectorNode contextSelector;

    public PathNode(
            @Nullable ContextSelectorNode contextSelector,
            Collection<? extends PathSegmentNode> segments,
            SourceInfo sourceInfo) {
        super(formatPath(contextSelector, segments), sourceInfo);
        this.contextSelector = contextSelector;
        this.segments = new ArrayList<>(checkNotNull(segments));
    }

    public @Nullable ContextSelectorNode getContextSelector() {
        return contextSelector;
    }

    public List<PathSegmentNode> getSegments() {
        return segments;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(segments, visitor);

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
            && Objects.equals(contextSelector, pathNode.contextSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), segments, contextSelector);
    }

    @Override
    public PathNode deepClone() {
        return new PathNode(contextSelector, deepClone(segments), getSourceInfo());
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
