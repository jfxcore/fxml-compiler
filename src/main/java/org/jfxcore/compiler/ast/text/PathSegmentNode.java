// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.List;

public abstract class PathSegmentNode extends DerivedTextNode {

    private final @Nullable SourceInfo selectorSourceInfo;

    protected PathSegmentNode(@Nullable SourceInfo selectorSourceInfo, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.selectorSourceInfo = selectorSourceInfo;
    }

    protected PathSegmentNode(
            @Nullable SourceInfo selectorSourceInfo, TypeNode type, SourceInfo sourceInfo) {
        super(type, sourceInfo);
        this.selectorSourceInfo = selectorSourceInfo;
    }

    public @Nullable SourceInfo getSelectorSourceInfo() {
        return selectorSourceInfo;
    }

    public abstract List<PathNode> getTypeArguments();

    /**
     * If this path segment refers to an {@code ObservableValue}, determines whether the segment
     * should select the ObservableValue instance itself, rather than the value of the ObservableValue.
     */
    public abstract boolean isObservableSelector();

    public abstract boolean equals(String text);

}
