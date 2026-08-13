// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.CompilationContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LiteralConversionResolverTest extends TestBase {

    @Test
    public void Discovery_Is_Deferred_And_Lowering_Is_Idempotent() throws Exception {
        LiteralConversionResolver.Result result = LiteralConversionResolver.resolve(
            "1", new LiteralConversionResolver.TargetDescriptor(
                TypeInstance.intType(), List.of(TypeInstance.intType()), null, SourceInfo.none()));

        LiteralConversionResolver.LiteralConversionPlan literalPlan =
            ((LiteralConversionResolver.Result.Applicable)result).plan();
        Field literalLowered = LiteralConversionResolver.LiteralConversionPlan.class
            .getDeclaredField("lowered");
        literalLowered.setAccessible(true);

        assertNull(literalLowered.get(literalPlan));
        assertSame(literalPlan.lower(), literalPlan.lower());

        TransformContext context = new TransformContext(
            List.of(), CompilationContext.getCurrent().getClassPool(), null, null);
        LiteralValueNode literal = new LiteralValueNode("1", SourceInfo.none());
        TargetValueResolver.TargetContext target =
            TargetValueResolver.TargetContext.constructorParameter(
                TypeInstance.ObjectType(), "value", TypeInstance.intType(),
                TypeInstance.ObjectType(), 0, SourceInfo.none());
        TargetValueResolver.ResolutionResult targetResult =
            TargetValueResolver.resolve(context, literal, target);
        TargetValueResolver.ValuePlan valuePlan =
            ((TargetValueResolver.ResolutionResult.Applicable)targetResult).plan();
        Field valueLowered = TargetValueResolver.ValuePlan.class.getDeclaredField("lowered");
        valueLowered.setAccessible(true);

        assertNull(valueLowered.get(valuePlan));
        assertSame(valuePlan.lowerValue(), valuePlan.lowerValue());
    }
}
