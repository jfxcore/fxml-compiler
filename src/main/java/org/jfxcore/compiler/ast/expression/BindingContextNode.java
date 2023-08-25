// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.path.NopSegment;
import org.jfxcore.compiler.ast.expression.path.ParamSegment;
import org.jfxcore.compiler.ast.expression.path.ParentSegment;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class BindingContextNode extends AbstractNode {

    private final BindingContextSelector selector;
    private final int parentIndex;
    private final boolean self;
    private ResolvedTypeNode type;

    public BindingContextNode(
            BindingContextSelector selector,
            TypeInstance type,
            int parentIndex,
            boolean self,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.selector = checkNotNull(selector);
        this.parentIndex = parentIndex;
        this.self = self;
    }

    public BindingContextSelector getSelector() {
        return selector;
    }

    public ResolvedTypeNode getType() {
        return type;
    }

    public boolean isSelf() {
        return self;
    }

    public Segment toSegment() {
        return switch (selector) {
            case STATIC -> new NopSegment(type.getTypeInstance());
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
        return new BindingContextNode(selector, type.getTypeInstance(), parentIndex, self, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingContextNode that = (BindingContextNode)o;
        return selector == that.selector &&
            parentIndex == that.parentIndex &&
            self == that.self &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, parentIndex, self, type);
    }

}
