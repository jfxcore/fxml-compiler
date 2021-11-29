// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
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
    private final TypeInstance searchType;
    private final Integer level;
    private ResolvedTypeNode type;

    public BindingContextNode(
            BindingContextSelector selector,
            TypeInstance type,
            @Nullable TypeInstance searchType,
            @Nullable Integer level,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.selector = checkNotNull(selector);
        this.searchType = searchType;
        this.level = level;
    }

    public BindingContextNode(
            BindingContextSelector selector,
            TypeInstance type,
            @Nullable Integer level,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.selector = checkNotNull(selector);
        this.searchType = null;
        this.level = level;
    }

    public BindingContextSelector getSelector() {
        return selector;
    }

    public ResolvedTypeNode getType() {
        return type;
    }

    public Segment toSegment() {
        return switch (selector) {
            case DEFAULT -> new ParentSegment(type.getTypeInstance(), null, null);
            case PARENT -> new ParentSegment(type.getTypeInstance(), searchType, level);
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
        return new BindingContextNode(selector, type.getTypeInstance(), searchType, level, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingContextNode that = (BindingContextNode)o;
        return selector == that.selector &&
            Objects.equals(level, that.level) &&
            Objects.equals(searchType, that.searchType) &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, searchType, level, type);
    }

}
