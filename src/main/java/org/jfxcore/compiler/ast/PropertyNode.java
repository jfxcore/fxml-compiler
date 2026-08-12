// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents the name and value of a property.
 * In a FXML document, a property is represented by an attribute or by a lower-case element.
 */
public class PropertyNode extends AbstractNode {

    private final String name;
    private final String[] names;
    private final String markupName;
    private final boolean intrinsic;
    private final boolean allowQualifiedName;
    private final List<Node> values;

    public PropertyNode(
            String[] names,
            String markupName,
            Node value,
            boolean intrinsic,
            boolean allowQualifiedName,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.names = checkNotNull(names);
        this.name = String.join(".", names);
        this.markupName = checkNotNull(markupName);
        this.intrinsic = intrinsic;
        this.allowQualifiedName = allowQualifiedName;
        this.values = new ArrayList<>(1);
        this.values.add(checkNotNull(value));
    }

    public PropertyNode(
            String[] names,
            String markupName,
            Collection<? extends Node> values,
            boolean intrinsic,
            boolean allowQualifiedName,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.names = checkNotNull(names);
        this.name = String.join(".", names);
        this.markupName = checkNotNull(markupName);
        this.intrinsic = intrinsic;
        this.allowQualifiedName = allowQualifiedName;
        this.values = new ArrayList<>(checkNotNull(values));
    }

    public String getName() {
        return name;
    }

    public String[] getNames() {
        return names;
    }

    public String getMarkupName() {
        return markupName;
    }

    public List<Node> getValues() {
        return values;
    }

    public boolean isAllowQualifiedName() {
        return allowQualifiedName;
    }

    public Node getSingleValue(TransformContext context) {
        if (values.isEmpty()) {
            TypeDeclaration declaringType = tryGetTypeDeclaration(context.getParent(this));
            String propertyName = intrinsic ? markupName : name;
            throw declaringType != null
                ? PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), declaringType, propertyName)
                : PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), propertyName);
        }

        if (values.size() > 1) {
            TypeDeclaration declaringType = tryGetTypeDeclaration(context.getParent(this));
            String propertyName = intrinsic ? markupName : name;

            throw declaringType != null
                ? PropertyAssignmentErrors.propertyCannotHaveMultipleValues(getSourceInfo(), declaringType, propertyName)
                : PropertyAssignmentErrors.propertyCannotHaveMultipleValues(getSourceInfo(), propertyName);
        }

        Node value = values.get(0);
        if (value instanceof AttributeValueNode attributeValue) {
            if (attributeValue.getForm() == AttributeValueNode.Form.SEQUENCE
                    && attributeValue.getItems().size() != 1) {
                TypeDeclaration declaringType = tryGetTypeDeclaration(context.getParent(this));
                String propertyName = intrinsic ? markupName : name;

                throw declaringType != null
                    ? PropertyAssignmentErrors.propertyCannotHaveMultipleValues(getSourceInfo(), declaringType, propertyName)
                    : PropertyAssignmentErrors.propertyCannotHaveMultipleValues(getSourceInfo(), propertyName);
            }

            return attributeValue.getSingleValue();
        }

        return value;
    }

    public String getTrimmedTextNotEmpty(TransformContext context) {
        String text = getLiteralText(context);
        if (text.isBlank()) {
            TypeDeclaration declaringType = tryGetTypeDeclaration(context.getParent(this));
            String propertyName = intrinsic ? markupName : name;
            throw declaringType != null
                ? PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), declaringType, propertyName)
                : PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), propertyName);
        }

        return text.trim();
    }

    public SourceInfo getTrimmedTextSourceInfo(TransformContext context) {
        return getLiteralNode(context).getSourceInfo().getTrimmed();
    }

    public String getLiteralText(TransformContext context) {
        Node node = getLiteralNode(context);
        return ((LiteralValueNode)node).getText();
    }

    private Node getLiteralNode(TransformContext context) {
        Node value = values.size() == 1 ? values.get(0) : null;
        if (value instanceof AttributeValueNode attributeValue
                && attributeValue.getForm() == AttributeValueNode.Form.LITERAL) {
            value = attributeValue.getLiteral();
        }

        if (!(value instanceof LiteralValueNode)) {
            ObjectNode parent = (ObjectNode)context.getParent(this);
            String parentName;

            if (parent.getType().isIntrinsic()) {
                parentName = parent.getType().getMarkupName();
            } else if (parent.getType() instanceof ResolvedTypeNode resolvedTypeNode) {
                parentName = resolvedTypeNode.getTypeDeclaration().simpleName();
            } else {
                parentName = null;
            }

            String propertyName = intrinsic ? markupName : name;

            SourceInfo sourceInfo = values.isEmpty()
                ? getSourceInfo()
                : SourceInfo.span(values.get(0).getSourceInfo(), values.get(values.size() - 1).getSourceInfo());

            throw parentName != null
                ? PropertyAssignmentErrors.propertyMustContainText(sourceInfo, parentName, propertyName)
                : PropertyAssignmentErrors.propertyMustContainText(sourceInfo, propertyName);
        }

        return value;
    }

    private TypeDeclaration tryGetTypeDeclaration(Node node) {
        if (node instanceof ValueNode valueNode
                && valueNode.getType() instanceof ResolvedTypeNode resolvedTypeNode) {
            return resolvedTypeNode.getTypeDeclaration();
        }

        return null;
    }

    public boolean isIntrinsic() {
        return intrinsic;
    }

    public boolean isIntrinsic(Intrinsic node) {
        return intrinsic && this.name.equals(node.getName());
    }

    public boolean isIntrinsic(Intrinsic... nodes) {
        for (Intrinsic node : nodes) {
            if (isIntrinsic(node)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(values, visitor, Node.class);
    }

    @Override
    public String toString() {
        return markupName;
    }

    @Override
    public PropertyNode deepClone() {
        return new PropertyNode(
            names, markupName, deepClone(values), intrinsic, allowQualifiedName, getSourceInfo()).copy(this);
    }
}
