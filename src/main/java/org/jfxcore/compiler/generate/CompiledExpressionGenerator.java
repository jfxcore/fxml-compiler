// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.lang.reflect.Modifier;

/**
 * Declares and emits the static helper method owned by one compiled-expression emitter.
 */
public final class CompiledExpressionGenerator implements Generator {

    private final MethodDeclaration method;
    private final TypeDeclaration resultType;
    private final ValueEmitterNode body;

    public CompiledExpressionGenerator(
            TypeDeclaration invocationContext,
            String methodName,
            TypeDeclaration resultType,
            TypeDeclaration[] parameterTypes,
            ValueEmitterNode body) {
        this.resultType = resultType;
        this.body = body;
        this.method = invocationContext
            .createMethod(methodName, resultType, parameterTypes)
            .setModifiers(Modifier.STATIC);
    }

    public MethodDeclaration getMethod() {
        return method;
    }

    @Override
    public boolean consume(BytecodeEmitContext context) {
        return true;
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {}

    @Override
    public void emitMethods(BytecodeEmitContext context) {}

    @Override
    public void emitCode(BytecodeEmitContext context) {
        var helperContext = new BytecodeEmitContext(context, method, -1);
        helperContext.emit(body);
        method.setCode(helperContext.getOutput().ret(resultType));
    }
}
