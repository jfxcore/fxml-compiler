// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;

public class EmitNopNode extends AbstractNode implements ValueEmitterNode {

    private final ResolvedTypeNode type;

    public EmitNopNode(TypeInstance type, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(type, sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {}

    @Override
    public EmitNopNode deepClone() {
        return new EmitNopNode(type.getTypeInstance(), getSourceInfo());
    }

}
