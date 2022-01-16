// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtBehavior;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A constructor, or a method with receiver.
 */
public class Callable {

    private final List<ValueEmitterNode> receiver;
    private final CtBehavior behavior;
    private final SourceInfo sourceInfo;

    public Callable(List<ValueEmitterNode> receiver, CtBehavior behavior, SourceInfo sourceInfo) {
        this.receiver = receiver;
        this.behavior = behavior;
        this.sourceInfo = sourceInfo;
    }

    public List<ValueEmitterNode> getReceiver() {
        return receiver;
    }

    public CtBehavior getBehavior() {
        return behavior;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Callable that = (Callable) o;
        return Objects.equals(receiver, that.receiver) && TypeHelper.equals(behavior, that.behavior);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiver, TypeHelper.hashCode(behavior));
    }

    public Callable deepClone() {
        return new Callable(new ArrayList<>(Node.deepClone(receiver)), behavior, sourceInfo);
    }

}
