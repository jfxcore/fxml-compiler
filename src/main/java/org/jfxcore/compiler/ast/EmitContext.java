// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.util.ArrayStack;
import java.util.List;

public abstract class EmitContext<T> {

    private final ArrayStack<Node> parents;
    private final T output;

    public EmitContext(T output) {
        this.output = output;
        this.parents = new ArrayStack<>();
    }

    protected EmitContext(T output, List<Node> parents) {
        this.output = output;
        this.parents = new ArrayStack<>(parents);
    }

    public T getOutput() {
        return output;
    }

    public Node getParent() {
        return parents.get(parents.size() - 2);
    }

    public ArrayStack<Node> getParents() {
        return parents;
    }

    public abstract void emit(Node node);

    @SuppressWarnings("unchecked")
    public <U extends Node> U findParent(Class<U> type) {
        for (int i = parents.size() - 1; i >= 0; --i) {
            if (type.isInstance(parents.get(i))) {
                return (U)parents.get(i);
            }
        }

        throw new RuntimeException("Parent not found: " + type.getName());
    }

}
