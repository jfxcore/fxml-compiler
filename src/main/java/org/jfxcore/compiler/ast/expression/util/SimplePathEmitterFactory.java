// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitInvariantPathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.util.TypeHelper;

public class SimplePathEmitterFactory implements EmitterFactory {

    private final PathExpressionNode pathExpression;

    public SimplePathEmitterFactory(PathExpressionNode pathExpression) {
        this.pathExpression = pathExpression;
    }

    @Override
    public BindingEmitterInfo newInstance() {
        ResolvedPath path = pathExpression.resolvePath(false);

        ValueEmitterNode value = new EmitInvariantPathNode(
            path.toValueEmitters(false, pathExpression.getSourceInfo()), pathExpression.getSourceInfo());

        value = pathExpression.getOperator().toEmitter(value, BindingMode.ONCE);

        Segment lastSegment = path.get(path.size() - 1);

        return new BindingEmitterInfo(
            value,
            TypeHelper.getTypeInstance(value),
            null,
            lastSegment.getDeclaringClass(),
            lastSegment.getDisplayName(),
            false,
            false,
            pathExpression.getSourceInfo());
    }
}
