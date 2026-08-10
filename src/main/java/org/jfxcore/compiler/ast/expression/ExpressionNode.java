// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.type.TypeInstance;

public interface ExpressionNode extends Node {

    int NO_BINDING_DISTANCE = Integer.MAX_VALUE;

    BindingEmitterInfo toEmitter(BindingMode bindingMode,
                                 TypeInstance invokingType,
                                 @Nullable TypeInstance targetType);

    /**
     * Gets the smallest binding distance referenced by this expression. Expressions without a
     * binding context return {@link #NO_BINDING_DISTANCE} so minimum-distance aggregation ignores
     * them.
     */
    default int getBindingDistance() {
        return NO_BINDING_DISTANCE;
    }

    static int bindingDistance(@Nullable Node node) {
        return node instanceof ExpressionNode expression
            ? expression.getBindingDistance()
            : NO_BINDING_DISTANCE;
    }

    @Override
    ExpressionNode deepClone();
}
