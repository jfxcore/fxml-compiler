// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.path.ParamSegment;
import org.jfxcore.compiler.ast.expression.path.ParentSegment;
import org.jfxcore.compiler.ast.expression.path.Segment;
import java.util.Objects;

public class BindingContextNode extends AbstractNode {

    private final BindingContextSelector selector;
    private final int parentIndex;
    private ResolvedTypeNode type;

    public BindingContextNode(
            BindingContextSelector selector,
            TypeInstance type,
            int parentIndex,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.selector = checkNotNull(selector);
        this.parentIndex = parentIndex;
    }

    public BindingContextSelector getSelector() {
        return selector;
    }

    public ResolvedTypeNode getType() {
        return type;
    }

    public Segment toSegment() {
        return switch (selector) {
            case DEFAULT, SELF, PARENT -> new ParentSegment(type.getTypeInstance(), parentIndex);
            case TEMPLATED_ITEM -> new ParamSegment(type.getTypeInstance());
        };
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
    }

    @Override
    public BindingContextNode deepClone() {
        return new BindingContextNode(selector, type.getTypeInstance(), parentIndex, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingContextNode that = (BindingContextNode)o;
        return selector == that.selector &&
            parentIndex == that.parentIndex &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, parentIndex, type);
    }

}
