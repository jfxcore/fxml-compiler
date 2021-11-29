// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class EmitUrlNode extends AbstractNode implements ValueEmitterNode, ParentStackInfo {

    private final ResolvedTypeNode type;
    private final String url;
    private final TypeInstance targetType;

    public EmitUrlNode(String url, TypeInstance targetType, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.url = checkNotNull(url);
        this.targetType = checkNotNull(targetType);
        this.type = new ResolvedTypeNode(targetType, sourceInfo);
    }

    @Override
    public TypeNode getType() {
        return type;
    }

    @Override
    public boolean needsParentStack() {
        return true;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        code.aload(context.getRuntimeContextLocal())
            .ldc(url)
            .invokevirtual(context.getRuntimeContextClass(), "getResource", function(URLType(), StringType()));

        if (unchecked(() -> targetType.subtypeOf(StringType()))) {
            code.invokevirtual(URLType(), "toExternalForm", function(StringType()));
        } else if (unchecked(() -> targetType.subtypeOf(URIType()))) {
            code.invokevirtual(URLType(), "toURI", function(URIType()));
        }
    }

    @Override
    public EmitUrlNode deepClone() {
        return new EmitUrlNode(url, targetType, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitUrlNode that = (EmitUrlNode)o;
        return type.equals(that.type) && url.equals(that.url) && targetType.equals(that.targetType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, url, targetType);
    }

}
