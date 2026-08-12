// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The result of resolving an expression.
 * <p>
 * Resolution is semantic and does not create emitter nodes. The emitter supplier materializes
 * the already-resolved expression only when lowering actually needs it.
 */
public final class ExpressionResolution {

    private final BindingTypeInfo typeInfo;
    private final Supplier<? extends ValueEmitterNode> emitterFactory;
    private BindingEmitterInfo emitter;

    public ExpressionResolution(
            BindingTypeInfo typeInfo,
            Supplier<? extends ValueEmitterNode> emitterFactory) {
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.emitterFactory = Objects.requireNonNull(emitterFactory);
    }

    public BindingTypeInfo getTypeInfo() {
        return typeInfo;
    }

    public BindingEmitterInfo toEmitter() {
        if (emitter != null) {
            return emitter;
        }

        ValueEmitterNode value = Objects.requireNonNull(emitterFactory.get());
        TypeInstance actualType = TypeHelper.getTypeInstance(value);

        if (!typeInfo.emittedType().equals(actualType)) {
            throw new IllegalStateException(
                "Resolved expression type " + typeInfo.emittedType().javaName()
                    + " differs from emitted type " + actualType.javaName());
        }

        return emitter = new BindingEmitterInfo(value, typeInfo);
    }
}
