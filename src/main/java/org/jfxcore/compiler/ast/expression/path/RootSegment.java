// Copyright (c) 2023, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import javassist.CtField;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.EmitGetFieldNode;
import org.jfxcore.compiler.ast.emit.EmitGetRootNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeInstance;

public class RootSegment extends Segment {

    private final CtField rootField;

    public RootSegment(TypeInstance type,
                       TypeInstance valueType,
                       ObservableKind observableKind,
                       @Nullable CtField rootField) {
        super(rootField != null ? rootField.getName() : "<root>", "<root>", type, valueType, observableKind);
        this.rootField = rootField;
    }

    @Override
    public CtClass getDeclaringClass() {
        return ExceptionHelper.unchecked(SourceInfo.none(), () -> getValueTypeInstance().jvmType().getDeclaringClass());
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return rootField != null
            ? new EmitGetFieldNode(rootField, getTypeInstance(), false, true, sourceInfo)
            : new EmitGetRootNode(getTypeInstance(), sourceInfo);
    }

}
