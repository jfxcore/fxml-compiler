// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.Visitor;
import java.util.Objects;

public class TextSegmentNode extends PathSegmentNode {

    private final boolean observableSelector;
    private TextNode value;

    public TextSegmentNode(boolean observableSelector, TextNode value) {
        super(value.getText(), value.getSourceInfo());
        this.observableSelector = observableSelector;
        this.value = checkNotNull(value);

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
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (TextNode)value.accept(visitor);
    }

    @Override
    public TextSegmentNode deepClone() {
        return new TextSegmentNode(observableSelector, value.deepClone());
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
            && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), observableSelector, value);
    }

}
