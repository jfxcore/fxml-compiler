// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.diagnostic.SourceInfo;

public abstract class PathSegmentNode extends TextNode {

    protected PathSegmentNode(String text, SourceInfo sourceInfo) {
        super(text, sourceInfo);
    }

    /**
     * If this path segment refers to an {@code ObservableValue}, determines whether the segment
     * should select the ObservableValue instance itself, rather than the value of the ObservableValue.
     */
    public abstract boolean isObservableSelector();

    public abstract boolean equals(String text);

}
