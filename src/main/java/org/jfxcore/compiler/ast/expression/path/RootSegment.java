// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import org.jfxcore.compiler.ast.emit.EmitGetRootNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeInstance;

public class RootSegment extends Segment {

    public RootSegment(TypeInstance type) {
        super("<root>", "<root>", type, type, ObservableKind.NONE);
    }

    @Override
    public CtClass getDeclaringClass() {
        return ExceptionHelper.unchecked(SourceInfo.none(), () -> getValueTypeInstance().jvmType().getDeclaringClass());
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return new EmitGetRootNode(getTypeInstance(), sourceInfo);
    }

}
