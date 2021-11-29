// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.TypeHelper;
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
    private final List<Node> values;

    public PropertyNode(String[] names, String markupName, Node value, boolean intrinsic, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.names = checkNotNull(names);
        this.name = String.join(".", names);
        this.markupName = checkNotNull(markupName);
        this.intrinsic = intrinsic;
        this.values = new ArrayList<>(1);
        this.values.add(checkNotNull(value));
    }

    public PropertyNode(
            String[] names,
            String markupName,
            Collection<? extends Node> values,
            boolean intrinsic,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.names = checkNotNull(names);
        this.name = String.join(".", names);
        this.markupName = checkNotNull(markupName);
        this.intrinsic = intrinsic;
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

    public Node getSingleValue(TransformContext context) {
        if (values.size() == 0) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                getSourceInfo(), TypeHelper.getJvmType(context.getParent(this)), name);
        }

        if (values.size() > 1) {
            throw PropertyAssignmentErrors.propertyCannotHaveMultipleValues(
                getSourceInfo(), TypeHelper.getJvmType(context.getParent(this)), name);
        }

        return values.get(0);
    }

    public String getTextValueNotEmpty(TransformContext context) {
        String text = getTextValue(context);
        if (text.isBlank()) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                getSourceInfo(), TypeHelper.getJvmType(context.getParent(this)), intrinsic ? markupName : name);
        }

        return text;
    }

    public String getTextValue(TransformContext context) {
        if (values.size() != 1 || !(values.get(0) instanceof TextNode)) {
            ObjectNode parent = (ObjectNode)context.getParent(this);
            String parentName;

            if (parent.getType().isIntrinsic()) {
                parentName = parent.getType().getMarkupName();
            } else {
                parentName = TypeHelper.getJvmType(parent).getSimpleName();
            }

            SourceInfo sourceInfo = SourceInfo.span(
                values.get(0).getSourceInfo(), values.get(values.size() - 1).getSourceInfo());

            throw PropertyAssignmentErrors.propertyMustContainText(sourceInfo, parentName, intrinsic ? markupName : name);
        }

        return ((TextNode)values.get(0)).getText();
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
        acceptChildren(values, visitor);
    }

    @Override
    public String toString() {
        return markupName;
    }

    @Override
    public PropertyNode deepClone() {
        return new PropertyNode(names, markupName, deepClone(values), intrinsic, getSourceInfo());
    }

}
