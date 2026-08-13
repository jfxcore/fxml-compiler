// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unresolved root for every XML attribute value.
 */
public final class AttributeValueNode extends AbstractNode implements SyntaxNode {

    public enum Form {
        LITERAL,
        SEQUENCE,
        SYNTAX
    }

    private final Form form;
    private final List<Node> values;

    public static AttributeValueNode literal(LiteralValueNode value, SourceInfo sourceInfo) {
        return new AttributeValueNode(Form.LITERAL, List.of(value), sourceInfo);
    }

    public static AttributeValueNode sequence(
            Collection<? extends Node> values, SourceInfo sourceInfo) {
        return new AttributeValueNode(Form.SEQUENCE, values, sourceInfo);
    }

    public static AttributeValueNode syntax(Node value, SourceInfo sourceInfo) {
        return new AttributeValueNode(Form.SYNTAX, List.of(value), sourceInfo);
    }

    private AttributeValueNode(Form form, Collection<? extends Node> values, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.form = checkNotNull(form);
        this.values = new ArrayList<>(checkNotNull(values));

        validateForm();
    }

    public Form getForm() {
        return form;
    }

    public LiteralValueNode getLiteral() {
        if (form != Form.LITERAL) {
            throw new IllegalStateException("Not a literal attribute value");
        }

        return (LiteralValueNode)values.get(0);
    }

    public List<Node> getItems() {
        if (form != Form.SEQUENCE) {
            throw new IllegalStateException("Not an attribute value sequence");
        }

        return Collections.unmodifiableList(values);
    }

    public Node getSyntax() {
        if (form != Form.SYNTAX) {
            throw new IllegalStateException("Not a syntax attribute value");
        }

        return values.get(0);
    }

    public Node getSingleValue() {
        if (values.size() != 1) {
            throw new IllegalStateException("Attribute value contains multiple items");
        }

        return values.get(0);
    }

    @Override
    public String format() {
        return values.stream().map(AttributeValueNode::formatNode).collect(Collectors.joining(","));
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        acceptChildren(values, visitor, form == Form.LITERAL ? LiteralValueNode.class : Node.class);
        validateForm();
    }

    @Override
    public AttributeValueNode deepClone() {
        return new AttributeValueNode(form, deepClone(values), getSourceInfo()).copy(this);
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AttributeValueNode other
            && form == other.form
            && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(form, values);
    }

    private static String formatNode(Node node) {
        if (node instanceof SyntaxNode syntaxNode) {
            return syntaxNode.format();
        }

        if (node instanceof LiteralValueNode literal) {
            return literal.getText();
        }

        return node.toString();
    }

    private void validateForm() {
        if (form == Form.SEQUENCE) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("The sequence form requires at least one child");
            }
        } else if (values.size() != 1) {
            throw new IllegalArgumentException("The " + form + " form requires exactly one child");
        } else if (form == Form.LITERAL && !(values.get(0) instanceof LiteralValueNode)) {
            throw new IllegalArgumentException("The literal form requires a literal child");
        }
    }
}
