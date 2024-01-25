// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractNode implements Node {

    private final transient SourceInfo sourceInfo;
    private final transient Map<NodeDataKey, Object> userData = new HashMap<>();
    private transient boolean markedForRemoval;

    public AbstractNode(SourceInfo sourceInfo) {
        this.sourceInfo = checkNotNull(sourceInfo);
    }

    @Override
    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    @Override
    public void remove() {
        markedForRemoval = true;
    }

    @Override
    public boolean isMarkedForRemoval() {
        return markedForRemoval;
    }

    @Override
    public void setNodeData(NodeDataKey key, Object value) {
        userData.put(key, value);
    }

    @Override
    public Object getNodeData(NodeDataKey key) {
        return userData.get(key);
    }

    @Override
    public final Node accept(Visitor visitor) {
        Node node = checkNotNull(visitor.visit(this));

        if (node == Visitor.STOP_SUBTREE) {
            return this;
        }

        try {
            visitor.push(node);
            node.acceptChildren(visitor);
        } finally {
            visitor.pop();
        }

        return node;
    }

    @Override
    public void acceptChildren(Visitor visitor) {}

    @SuppressWarnings("unchecked")
    protected static <T extends Node> void acceptChildren(List<T> children, Visitor visitor, Class<?> type) {
        for (int i = 0; i < children.size(); ++i) {
            Node node = children.get(i).accept(visitor);
            if (!type.isInstance(node)) {
                throw ParserErrors.unexpectedExpression(node.getSourceInfo());
            }

            children.set(i, checkNotNull((T)node));
        }

        children.removeIf(Node::isMarkedForRemoval);
    }

    protected <T extends Node> Collection<T> deepClone(Collection<T> collection) {
        return Node.deepClone(collection);
    }

    protected static <T> T checkNotNull(T value) {
        if (value == null) {
            throw new NullPointerException();
        }

        return value;
    }

    protected static <T> Collection<T> checkNotNull(Collection<T> collection) {
        if (collection == null) {
            throw new NullPointerException();
        }

        for (T c : collection) {
            if (c == null) {
                throw new NullPointerException();
            }
        }

        return collection;
    }

}
