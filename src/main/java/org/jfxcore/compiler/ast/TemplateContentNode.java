// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;

public class TemplateContentNode extends AbstractNode implements ValueNode {

    private final TypeInstance itemType;
    private final TypeInstance bindingContextClass;
    private ResolvedTypeNode type;
    private ValueNode content;

    public TemplateContentNode(
            ResolvedTypeNode type,
            ValueNode content,
            TypeInstance itemType,
            TypeInstance bindingContextClass,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = checkNotNull(type);
        this.content = checkNotNull(content);
        this.itemType = checkNotNull(itemType);
        this.bindingContextClass = checkNotNull(bindingContextClass);
    }

    public TypeInstance getBindingContextClass() {
        return bindingContextClass;
    }

    public ValueNode getContent() {
        return content;
    }

    public TypeInstance getItemType() {
        return itemType;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
        content = (ValueNode)content.accept(visitor);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public TemplateContentNode deepClone() {
        return new TemplateContentNode(
            type.deepClone(), content.deepClone(), itemType, bindingContextClass, getSourceInfo());
    }

}
