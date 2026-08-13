// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitArithmeticExpressionNode;
import org.jfxcore.compiler.ast.emit.EmitCompiledExpressionNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.CompilationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CompiledExpressionLoweringTest extends TestBase {

    @Test
    public void Helper_Is_Declared_By_Emitter_Generator_Discovery() {
        TypeDeclaration markupClass = TypeDeclaration.of(
            CompilationContext.getCurrent().getClassPool().makeClass(getClass().getName() + "$Markup"));

        SourceInfo sourceInfo = SourceInfo.none();
        var expression = new CompiledExpressionNode(
            markupClass, "arithmetic expression",
            new ArithmeticExpressionNode(
                BinaryOperator.ADD,
                LiteralExpressionNode.ofNumber(1, sourceInfo),
                LiteralExpressionNode.ofNumber(2, sourceInfo),
                sourceInfo,
                sourceInfo),
            sourceInfo);

        BindingEmitterInfo emitterInfo = expression.resolve(
            BindingMode.ONCE, TypeInstance.of(markupClass), null).toEmitter();

        EmitCompiledExpressionNode emitter = assertInstanceOf(
            EmitCompiledExpressionNode.class, emitterInfo.getValue());

        assertInstanceOf(EmitArithmeticExpressionNode.class, emitter.getBody());
        assertTrue(markupClass.declaredMethods().isEmpty());
        assertEquals(1, emitter.emitGenerators(null).size());
        assertEquals(1, markupClass.declaredMethods().size());
        assertTrue(markupClass.declaredMethods().get(0).name().startsWith("__FX$eval$"));
    }
}
