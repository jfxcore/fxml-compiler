// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import javassist.CtField;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.path.NopSegment;
import org.jfxcore.compiler.ast.expression.path.ParamSegment;
import org.jfxcore.compiler.ast.expression.path.ParentSegment;
import org.jfxcore.compiler.ast.expression.path.RootSegment;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.util.ObservableKind.*;

public class BindingContextNode extends AbstractNode {

    private final BindingContextSelector selector;
    private final int bindingDistance;
    private final CtField contextField;
    private final TypeInstance type;
    private final TypeInstance valueType;
    private final TypeInstance observableType;
    private ResolvedTypeNode typeNode;

    public BindingContextNode(
            BindingContextSelector selector,
            TypeInstance type,
            int bindingDistance,
            SourceInfo sourceInfo) {
        this(selector, type, type, null, null, bindingDistance, sourceInfo);
    }

    public BindingContextNode(
            BindingContextSelector selector,
            TypeInstance type,
            TypeInstance valueType,
            @Nullable TypeInstance observableType,
            CtField contextField,
            int bindingDistance,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.typeNode = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.type = checkNotNull(type);
        this.valueType = checkNotNull(valueType);
        this.observableType = observableType;
        this.contextField = contextField;
        this.selector = checkNotNull(selector);
        this.bindingDistance = bindingDistance;

        if (selector == BindingContextSelector.CONTEXT) {
            Objects.requireNonNull(contextField, "contextField");
        }
    }

    public BindingContextSelector getSelector() {
        return selector;
    }

    public ResolvedTypeNode getType() {
        return typeNode;
    }

    /**
     * Gets the distance to the referenced parent, where self == 0, first parent == 1, etc.
     */
    public int getBindingDistance() {
        return bindingDistance;
    }

    public Segment toSegment() {
        return switch (selector) {
            case STATIC -> new NopSegment(type);
            case CONTEXT -> new RootSegment(type, valueType, observableType != null ? FX_OBSERVABLE : NONE, contextField);
            case ROOT -> new RootSegment(type, type, NONE, null);
            case SELF -> new ParentSegment(type, 0);
            case PARENT -> new ParentSegment(type, bindingDistance);
            case TEMPLATED_ITEM -> new ParamSegment(type);
        };
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        typeNode = (ResolvedTypeNode)typeNode.accept(visitor);
    }

    @Override
    public BindingContextNode deepClone() {
        return new BindingContextNode(
            selector, type, valueType, observableType, contextField, bindingDistance, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindingContextNode that = (BindingContextNode)o;
        return selector == that.selector &&
            bindingDistance == that.bindingDistance &&
            TypeHelper.equals(contextField, that.contextField) &&
            type.equals(that.type) &&
            valueType.equals(that.valueType) &&
            Objects.equals(observableType, that.observableType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            selector, bindingDistance, TypeHelper.hashCode(contextField), type, valueType, observableType);
    }

}
