// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;

/**
 * A mutable semantic expression node that participates in a compiled-expression plan.
 * Analysis state is kept in {@link ExpressionAnalysisContext}.
 */
public interface AnalyzedExpressionNode extends Node {

    TypeInstance analyze(ExpressionAnalysisContext context);

    /**
     * Lowers this analyzed semantic node into the emitter tree used for the compiled-expression helper body.
     */
    ValueEmitterNode toEmitter(ExpressionAnalysisContext context);

    int getBindingDistance();

    default @Nullable SourceInfo getFirstOperatorSourceInfo() {
        return null;
    }

    @Override
    AnalyzedExpressionNode deepClone();
}
