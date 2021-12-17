// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Action;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.Supplier;
import java.util.Collection;
import java.util.stream.Collectors;

public interface Node {

    SourceInfo getSourceInfo();

    Node accept(Visitor visitor);

    void acceptChildren(Visitor visitor);

    Node deepClone();

    void remove();

    boolean isMarkedForRemoval();

    void setNodeData(NodeDataKey key, Object value);

    Object getNodeData(NodeDataKey key);

    @SuppressWarnings("unchecked")
    static <T extends Node> Collection<T> deepClone(Collection<T> collection) {
        return collection.stream().map(node -> (T)node.deepClone()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    default <T extends Node> T as(Class<T> nodeClass) {
        if (nodeClass.isInstance(this)) {
            return (T)this;
        }

        return null;
    }

    default <T extends Node> boolean typeEquals(Class<T> nodeClass) {
        return nodeClass == getClass();
    }

    default void unchecked(Action action) {
        ExceptionHelper.unchecked(getSourceInfo(), action);
    }

    default <T> T unchecked(Supplier<T> action) {
        return ExceptionHelper.unchecked(getSourceInfo(), action);
    }

}
