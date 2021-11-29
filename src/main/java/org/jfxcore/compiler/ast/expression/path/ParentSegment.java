// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.EmitGetParentNode;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeInstance;

public class ParentSegment extends Segment {

    private final TypeInstance searchType;
    private final Integer level;

    public ParentSegment(TypeInstance type, @Nullable TypeInstance searchType, @Nullable Integer level) {
        super("<parent>", "<parent>", type, type, ObservableKind.NONE);
        this.searchType = searchType;
        this.level = level;
    }

    @Override
    public CtClass getDeclaringClass() {
        return ExceptionHelper.unchecked(SourceInfo.none(), () -> getValueTypeInstance().jvmType().getDeclaringClass());
    }

    @Override
    public ValueEmitterNode toEmitter(SourceInfo sourceInfo) {
        return new EmitGetParentNode(getTypeInstance(), searchType, level, sourceInfo);
    }

}
