// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.EmitGetFieldNode;
import org.jfxcore.compiler.ast.emit.EmitGetRootNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ObservableKind;

public class RootSegment extends Segment {

    private final FieldDeclaration rootField;

    public RootSegment(TypeInstance type,
                       TypeInstance valueType,
                       ObservableKind observableKind,
                       @Nullable FieldDeclaration rootField) {
        super(rootField != null ? rootField.name() : "<root>", "<root>", type, valueType, observableKind);
        this.rootField = rootField;
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return rootField != null
            ? new EmitGetFieldNode(rootField, getTypeInstance(), false, true, sourceInfo)
            : new EmitGetRootNode(getTypeInstance(), sourceInfo);
    }

    @Override
    public @Nullable TypeDeclaration getDeclaringType() {
        return rootField != null ? rootField.declaringType() : null;
    }
}
