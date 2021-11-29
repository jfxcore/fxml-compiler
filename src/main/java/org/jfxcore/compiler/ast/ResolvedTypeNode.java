// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;

/**
 * Represents a type that was successfully resolved.
 */
public class ResolvedTypeNode extends TypeNode {

    private final TypeInstance type;

    public ResolvedTypeNode(TypeInstance type, SourceInfo sourceInfo) {
        this(type, type.jvmType().getName(), type.jvmType().getName(), false, sourceInfo);
    }

    public ResolvedTypeNode(
            TypeInstance type, String name, String markupName, boolean intrinsic, SourceInfo sourceInfo) {
        super(name, markupName, intrinsic, sourceInfo);
        this.type = checkNotNull(type);
    }

    public CtClass getJvmType() {
        return type.jvmType();
    }

    public TypeInstance getTypeInstance() {
        return type;
    }

    @Override
    public ResolvedTypeNode deepClone() {
        return new ResolvedTypeNode(type, getName(), getMarkupName(), isIntrinsic(), getSourceInfo());
    }

    @Override
    public String toString() {
        return type.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ResolvedTypeNode that = (ResolvedTypeNode)o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

}
