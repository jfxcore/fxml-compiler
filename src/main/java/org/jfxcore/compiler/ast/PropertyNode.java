// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.TransformContext;
import javassist.CtClass;
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

    /**
     * Gets the single {@link Node} value of this property.
     *
     * @throws MarkupException if the property is empty or if it has multiple values
     */
    public Node getSingleValue(TransformContext context) {
        if (values.size() == 0) {
            CtClass declaringType = tryGetJvmType(context.getParent(this));
            String propertyName = intrinsic ? markupName : name;
            throw declaringType != null
                ? PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), declaringType, propertyName)
                : PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), propertyName);
        }

        if (values.size() > 1) {
            CtClass declaringType = tryGetJvmType(context.getParent(this));
            String propertyName = intrinsic ? markupName : name;
            throw declaringType != null
                ? PropertyAssignmentErrors.propertyCannotHaveMultipleValues(getSourceInfo(), declaringType, propertyName)
                : PropertyAssignmentErrors.propertyCannotHaveMultipleValues(getSourceInfo(), propertyName);
        }

        return values.get(0);
    }

    /**
     * Gets the trimmed text value of this property.
     *
     * @throws MarkupException if the property is empty, if it has multiple values, or if it doesn't contain text
     */
    public String getTrimmedTextValue(TransformContext context) {
        String text = getTextNode(context).getText();
        if (text.isBlank()) {
            CtClass declaringType = tryGetJvmType(context.getParent(this));
            String propertyName = intrinsic ? markupName : name;
            throw declaringType != null
                ? PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), declaringType, propertyName)
                : PropertyAssignmentErrors.propertyCannotBeEmpty(getSourceInfo(), propertyName);
        }

        return text.trim();
    }

    /**
     * Gets the {@code SourceInfo} of the trimmed text value of this property.
     *
     * @throws MarkupException if the property is empty, if it has multiple values, or if it doesn't contain text
     */
    public SourceInfo getTrimmedTextSourceInfo(TransformContext context) {
        return getTextNode(context).getSourceInfo().getTrimmed();
    }

    private TextNode getTextNode(TransformContext context) {
        if (values.size() != 1 || !(values.get(0) instanceof TextNode)) {
            ObjectNode parent = (ObjectNode)context.getParent(this);
            String parentName;

            if (parent.getType().isIntrinsic()) {
                parentName = parent.getType().getMarkupName();
            } else if (parent.getType() instanceof ResolvedTypeNode resolvedTypeNode) {
                parentName = resolvedTypeNode.getJvmType().getSimpleName();
            } else {
                parentName = null;
            }

            String propertyName = intrinsic ? markupName : name;
            SourceInfo sourceInfo = SourceInfo.span(
                values.get(0).getSourceInfo(), values.get(values.size() - 1).getSourceInfo());

            throw parentName != null
                ? PropertyAssignmentErrors.propertyMustContainText(sourceInfo, parentName, propertyName)
                : PropertyAssignmentErrors.propertyMustContainText(sourceInfo, propertyName);
        }

        return (TextNode)values.get(0);
    }

    private CtClass tryGetJvmType(Node node) {
        if (node instanceof ValueNode valueNode
                && valueNode.getType() instanceof ResolvedTypeNode resolvedTypeNode) {
            return resolvedTypeNode.getJvmType();
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
        return new PropertyNode(names, markupName, deepClone(values), intrinsic, allowQualifiedName, getSourceInfo());
    }

}
