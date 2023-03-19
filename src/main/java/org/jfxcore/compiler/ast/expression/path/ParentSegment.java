// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import org.jfxcore.compiler.ast.emit.EmitGetParentNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeInstance;

public class ParentSegment extends Segment {

    private final int parentIndex;

    public ParentSegment(TypeInstance type, int parentIndex) {
        super("<parent>", "<parent>", type, type, ObservableKind.NONE);
        this.parentIndex = parentIndex;
    }

    @Override
    public CtClass getDeclaringClass() {
        return ExceptionHelper.unchecked(SourceInfo.none(), () -> getValueTypeInstance().jvmType().getDeclaringClass());
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return new EmitGetParentNode(getTypeInstance(), parentIndex, sourceInfo);
    }

}
