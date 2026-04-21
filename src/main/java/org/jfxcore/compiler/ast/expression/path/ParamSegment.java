// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitLoadLocalNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;

public class ParamSegment extends Segment {

    public ParamSegment(TypeInstance valueType) {
        super("<param>", "<param>", valueType, valueType, ValueSourceKind.NONE, ObservableDependencyKind.NONE);
    }


    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return new EmitLoadLocalNode(EmitLoadLocalNode.Variable.PARAM_1, getTypeInstance(), sourceInfo);
    }
}
