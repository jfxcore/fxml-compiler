// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class SubPathSegmentNode extends PathSegmentNode {

    private final boolean observableSelector;
    private final List<PathSegmentNode> segments;

    public SubPathSegmentNode(
            boolean observableSelector, Collection<? extends PathSegmentNode> segments, SourceInfo sourceInfo) {
        super(formatPath(segments), sourceInfo);
        this.observableSelector = observableSelector;
        this.segments = new ArrayList<>(checkNotNull(segments));
    }

    @Override
    public boolean isObservableSelector() {
        return observableSelector;
    }

    public List<PathSegmentNode> getSegments() {
        return segments;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(segments, visitor, PathSegmentNode.class);
    }

    @Override
    public boolean equals(String text) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SubPathSegmentNode pathNode = (SubPathSegmentNode) o;
        return observableSelector == pathNode.observableSelector
            && Objects.equals(segments, pathNode.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), observableSelector, segments);
    }

    @Override
    public SubPathSegmentNode deepClone() {
        return new SubPathSegmentNode(observableSelector, deepClone(segments), getSourceInfo());
    }

    private static String formatPath(Collection<? extends PathSegmentNode> segments) {
        var text = new StringBuilder();
        boolean firstSegment = true;

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
