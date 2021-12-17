// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an object entity.
 * In a FXML document, an object entity is represented by an upper-case element.
 */
public class ObjectNode extends AbstractNode implements ValueNode {

    private TypeNode type;
    private final List<PropertyNode> properties;
    private final List<Node> children;

    public ObjectNode(
            TypeNode type,
            Collection<? extends PropertyNode> properties,
            Collection<? extends Node> children,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = checkNotNull(type);
        this.properties = new PropertyList();
        this.properties.addAll(checkNotNull(properties));
        this.children = new ArrayList<>(checkNotNull(children));
    }

    public boolean isIntrinsic(Intrinsic node) {
        return Intrinsics.find(this) == node;
    }

    public boolean isIntrinsic(Intrinsic node, Intrinsic... nodes) {
        Intrinsic intrinsic = Intrinsics.find(this);
        if (intrinsic == node) {
            return true;
        }

        for (Intrinsic n : nodes) {
            if (intrinsic == n) {
                return true;
            }
        }

        return false;
    }

    public TypeNode getType() {
        return type;
    }

    public List<PropertyNode> getProperties() {
        return properties;
    }

    public List<Node> getChildren() {
        return children;
    }

    public @Nullable PropertyNode findIntrinsicProperty(Intrinsic node) {
        for (PropertyNode property : properties) {
            if (property.isIntrinsic() && node.getName().equals(property.getName())) {
                return property;
            }
        }

        return null;
    }

    public @Nullable PropertyNode findProperty(String name) {
        for (PropertyNode property : properties) {
            if (!property.isIntrinsic() && name.equals(property.getName())) {
                return property;
            }
        }

        return null;
    }

    public PropertyNode getProperty(String name) {
        PropertyNode propertyNode = findProperty(name);

        if (propertyNode == null) {
            throw PropertyAssignmentErrors.propertyMustBeSpecified(
                getSourceInfo(), getType().getMarkupName(), name);
        }

        return propertyNode;
    }

    public TextNode getTextContent() {
        if (children.size() > 1) {
            throw ObjectInitializationErrors.objectCannotHaveMultipleChildren(
                SourceInfo.span(children.get(0).getSourceInfo(), children.get(children.size() - 1).getSourceInfo()),
                TypeHelper.getJvmType(this));
        }

        if (children.size() == 0 || !(children.get(0) instanceof TextNode)) {
            throw ObjectInitializationErrors.objectMustContainText(
                children.size() == 0 ? getSourceInfo() : children.get(0).getSourceInfo(),
                TypeHelper.getJvmType(this));
        }

        return (TextNode)children.get(0);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (TypeNode)type.accept(visitor);
        acceptChildren(properties, visitor);
        acceptChildren(children, visitor);
    }

    @Override
    public String toString() {
        return type.toString();
    }

    @Override
    public ObjectNode deepClone() {
        return new ObjectNode(
            type.deepClone(),
            deepClone(properties),
            deepClone(children),
            getSourceInfo());
    }

    private class PropertyList extends AbstractList<PropertyNode> {
        private final List<PropertyNode> list = new ArrayList<>();
        private final Set<String> names = new HashSet<>();

        @Override
        public PropertyNode get(int index) {
            return list.get(index);
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public PropertyNode set(int index, PropertyNode element) {
            names.remove(list.get(index).getMarkupName());

            if (names.contains(element.getMarkupName())) {
                throw PropertyAssignmentErrors.duplicateProperty(
                    element.getSourceInfo(), ObjectNode.this.getType().getMarkupName(), element.getMarkupName());
            }

            names.add(element.getMarkupName());
            return list.set(index, element);
        }

        @Override
        public void add(int index, PropertyNode element) {
            if (names.contains(element.getMarkupName())) {
                throw PropertyAssignmentErrors.duplicateProperty(
                    element.getSourceInfo(), ObjectNode.this.getType().getMarkupName(), element.getMarkupName());
            }

            names.add(element.getMarkupName());
            list.add(index, element);
        }

        @Override
        public PropertyNode remove(int index) {
            names.remove(list.get(index).getMarkupName());
            return list.remove(index);
        }
    }

}
