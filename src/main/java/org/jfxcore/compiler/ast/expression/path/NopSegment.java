// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeInstance;

public class NopSegment extends Segment {

    public NopSegment(TypeInstance valueType) {
        super("<nop>", "<nop>", valueType, valueType, ObservableKind.NONE);
    }

    @Override
    public CtClass getDeclaringClass() {
        return ExceptionHelper.unchecked(SourceInfo.none(), () -> getValueTypeInstance().jvmType().getDeclaringClass());
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return new NopEmitter(getTypeInstance(), sourceInfo);
    }

    private static final class NopEmitter extends AbstractNode implements ValueEmitterNode {
        private final ResolvedTypeNode type;

        public NopEmitter(TypeInstance type, SourceInfo sourceInfo) {
            super(sourceInfo);
            this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        }

        @Override
        public void emit(BytecodeEmitContext context) {}

        @Override
        public ResolvedTypeNode getType() {
            return type;
        }

        @Override
        public NopEmitter deepClone() {
            return new NopEmitter(type.getTypeInstance(), getSourceInfo());
        }
    }

}
