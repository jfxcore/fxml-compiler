// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * Represents an unresolved type.
 */
public class TypeNode extends AbstractNode {

    public static final TypeNode NONE = new TypeNode("<none>", SourceInfo.none()) {
        @Override
        public TypeNode deepClone() {
            return this;
        }
    };

    private final String name;
    private final String markupName;
    private final boolean intrinsic;

    public TypeNode(String name, SourceInfo sourceInfo) {
        this(name, name, false, sourceInfo);
    }

    public TypeNode(String name, String markupName, SourceInfo sourceInfo) {
        this(name, markupName, false, sourceInfo);
    }

    public TypeNode(String name, String markupName, boolean intrinsic, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.name = checkNotNull(name);
        this.markupName = checkNotNull(markupName);
        this.intrinsic = intrinsic;
    }

    public boolean isIntrinsic() {
        return intrinsic;
    }

    public String getName() {
        return name;
    }

    public String getMarkupName() {
        return markupName;
    }

    @Override
    public String toString() {
        return markupName;
    }

    @Override
    public TypeNode deepClone() {
        return new TypeNode(name, markupName, intrinsic, getSourceInfo());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TypeNode)) {
            return false;
        }

        TypeNode that = (TypeNode)other;
        return Objects.equals(name, that.name)
            && Objects.equals(markupName, that.markupName)
            && intrinsic == that.intrinsic;
    }

}
