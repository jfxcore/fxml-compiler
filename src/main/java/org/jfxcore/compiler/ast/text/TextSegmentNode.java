// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class TextSegmentNode extends PathSegmentNode {

    private final boolean observableSelector;
    private final List<PathNode> witnesses;
    private TextNode value;

    public TextSegmentNode(boolean observableSelector,
                           TextNode value,
                           Collection<? extends PathNode> witnesses,
                           SourceInfo sourceInfo) {
        super(value.getText(), sourceInfo);
        this.observableSelector = observableSelector;
        this.value = checkNotNull(value);
        this.witnesses = new ArrayList<>(checkNotNull(witnesses));

        if (value instanceof PathSegmentNode) {
            throw new IllegalArgumentException("value");
        }
    }

    @Override
    public boolean isObservableSelector() {
        return observableSelector;
    }

    public TextNode getValue() {
        return value;
    }

    @Override
    public List<PathNode> getWitnesses() {
        return witnesses;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (TextNode)value.accept(visitor);
        acceptChildren(witnesses, visitor, PathNode.class);
    }

    @Override
    public TextSegmentNode deepClone() {
        return new TextSegmentNode(observableSelector, value.deepClone(), deepClone(witnesses), getSourceInfo());
    }

    @Override
    public boolean equals(String text) {
        return value.getText().equals(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TextSegmentNode that = (TextSegmentNode) o;
        return observableSelector == that.observableSelector
            && Objects.equals(value, that.value)
            && witnesses.equals(that.witnesses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), observableSelector, value, witnesses);
    }

}
