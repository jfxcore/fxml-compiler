// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.emit.EmitApplyMarkupExtensionNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.ExpressionAnalysisContext;
import org.jfxcore.compiler.ast.expression.ExpressionResolution;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.transform.markup.util.MarkupExtensionInfo;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lowers ordered external expression inputs into fixed helper-method arguments.
 */
final class CompiledExpressionEmitterFactory {

    List<EmitMethodArgumentNode> createArguments(
            Collection<ExpressionAnalysisContext.Input> inputs) {
        var result = new ArrayList<EmitMethodArgumentNode>(inputs.size());

        for (ExpressionAnalysisContext.Input input : inputs) {
            TypeInstance parameterType = TypeInstance.of(input.parameterType());
            ExpressionResolution resolution = input.resolution();
            ValueEmitterNode value;
            ObservableDependencyKind dependency;

            if (resolution != null) {
                value = resolution.toEmitter().getValue();
                dependency = resolution.getTypeInfo().argumentDependencyKind();
            } else if (input.expression() instanceof ValueEmitterNode emitter) {
                MarkupExtensionInfo extension = MarkupExtensionInfo.of(emitter);
                if (extension instanceof MarkupExtensionInfo.Supplier supplier) {
                    value = new EmitApplyMarkupExtensionNode.Supplier(
                        emitter, supplier.markupExtensionInterface(), null,
                        parameterType, supplier.returnType(), null);
                } else if (extension instanceof MarkupExtensionInfo.PropertyConsumer) {
                    throw ObjectInitializationErrors.invalidMarkupExtensionUsage(
                        input.expression().getSourceInfo());
                } else {
                    value = emitter;
                }

                dependency = ObservableDependencyKind.NONE;
            } else {
                throw new AssertionError(input.expression().getClass().getName());
            }

            result.add(EmitMethodArgumentNode.newScalar(
                parameterType, value, dependency, input.expression().getSourceInfo()));
        }

        return List.copyOf(result);
    }
}
