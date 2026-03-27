// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A constructor, or a method with receiver.
 */
public class Callable {

    private final List<ValueEmitterNode> receiver;
    private final BehaviorDeclaration behavior;
    private final SourceInfo sourceInfo;

    public Callable(List<ValueEmitterNode> receiver, BehaviorDeclaration behavior, SourceInfo sourceInfo) {
        this.receiver = receiver;
        this.behavior = behavior;
        this.sourceInfo = sourceInfo;
    }

    public List<ValueEmitterNode> getReceiver() {
        return receiver;
    }

    public BehaviorDeclaration getBehavior() {
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
        return Objects.equals(receiver, that.receiver) && behavior.equals(that.behavior);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiver, behavior);
    }

    public Callable deepClone() {
        return new Callable(new ArrayList<>(Node.deepClone(receiver)), behavior, sourceInfo);
    }
}
