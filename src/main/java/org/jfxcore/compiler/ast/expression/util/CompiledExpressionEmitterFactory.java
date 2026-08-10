// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.expression.ExpressionAnalysisContext;
import org.jfxcore.compiler.ast.expression.path.InconvertibleArgumentException;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lowers ordered external expression inputs into fixed helper-method arguments.
 */
public final class CompiledExpressionEmitterFactory extends AbstractFunctionEmitterFactory {

    public CompiledExpressionEmitterFactory(TypeInstance invokingType) {
        super(invokingType, null);
    }

    public List<EmitMethodArgumentNode> createArguments(
            Collection<ExpressionAnalysisContext.Input> inputs,
            boolean preferObservable,
            String helperName) {
        var result = new ArrayList<EmitMethodArgumentNode>(inputs.size());
        int index = 0;

        for (ExpressionAnalysisContext.Input input : inputs) {
            try {
                result.add(createSingleFunctionArgumentValue(
                    input.expression(), TypeInstance.of(input.parameterType()), false, preferObservable));
            } catch (InconvertibleArgumentException ex) {
                if (ex.getCause() instanceof MarkupException markupException) {
                    throw markupException;
                }

                throw GeneralErrors.cannotAssignFunctionArgument(
                    input.expression().getSourceInfo(), helperName, index, ex.getTypeName());
            }

            ++index;
        }

        return List.copyOf(result);
    }
}
