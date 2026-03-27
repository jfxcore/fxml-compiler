// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;

public abstract class ReferenceableNode extends AbstractNode implements ValueEmitterNode {

    private final String fieldName;
    private final transient ReferenceableNode referencedNode;
    private transient StoredLocal storedLocal;

    public ReferenceableNode(ReferenceableNode referencedNode, String fieldName, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.referencedNode = referencedNode;
        this.fieldName = fieldName;
    }

    public String getId() {
        if (referencedNode != null) {
            return referencedNode.getId();
        }

        return fieldName;
    }

    public boolean isEmitInPreamble() {
        return fieldName != null;
    }

    public abstract ValueEmitterNode convertToLocalReference();

    @Override
    public abstract ReferenceableNode deepClone();

    protected ReferenceableNode getReferencedNode() {
        return referencedNode;
    }

    protected void storeLocal(Bytecode code, TypeDeclaration type) {
        Local local = code.acquireLocal(false);
        code.astore(local);
        storedLocal = new StoredLocal(type, local);
    }

    protected void loadLocal(Bytecode code) {
        storedLocal.load(code);
    }

    private static class StoredLocal {
        private final TypeDeclaration type;
        private final Local local;

        StoredLocal(TypeDeclaration type, Local local) {
            this.type = type;
            this.local = local;
        }

        void load(Bytecode code) {
            code.load(type, local);
        }
    }
}
