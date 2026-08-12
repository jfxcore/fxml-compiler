// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import javafx.beans.NamedArg;
import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class ImplicitConstructorResolverTest extends TestBase {

    public static class SelectionTarget {
        public SelectionTarget(@NamedArg("value") int value) {}
        public SelectionTarget(@NamedArg("value") long value) {}
    }

    @Test
    public void Only_Selected_Candidate_Is_Lowered_Once() throws Exception {
        SourceInfo sourceInfo = SourceInfo.none();
        var declaration = new Resolver(sourceInfo).resolveClass(SelectionTarget.class.getName());
        TypeInstance targetType = new TypeInvoker(sourceInfo).invokeType(declaration);
        TargetValueResolver.TargetContext target = TargetValueResolver.TargetContext.object(
            targetType, targetType, 0,
            TargetValueResolver.ConstructionSite.empty(sourceInfo));

        AtomicInteger identityLowerings = new AtomicInteger();
        AtomicInteger wideningLowerings = new AtomicInteger();
        List<ImplicitConstructorResolver.CandidateResult> candidates = new ArrayList<>();
        ConstructorDeclaration identityConstructor = null;
        Constructor<TargetValueResolver.ValuePlan> planConstructor =
            TargetValueResolver.ValuePlan.class.getDeclaredConstructor(
                TargetValueResolver.PlanKind.class,
                TargetValueResolver.TargetContext.class,
                List.class,
                TargetValueResolver.ConversionKind.class,
                Supplier.class);

        planConstructor.setAccessible(true);

        for (ConstructorDeclaration constructor : declaration.constructors()) {
            List<NamedArgumentMetadata.Parameter> parameters =
                NamedArgumentMetadata.get(targetType, constructor, sourceInfo);
            if (parameters.size() != 1) {
                continue;
            }

            TypeInstance formalType = parameters.get(0).type();
            boolean identity = formalType.equals(TypeInstance.intType());
            if (identity) {
                identityConstructor = constructor;
            }

            AtomicInteger lowerings = identity ? identityLowerings : wideningLowerings;

            TargetValueResolver.TargetContext argumentTarget =
                TargetValueResolver.TargetContext.constructorParameter(
                    targetType, "value", formalType, targetType, 0, sourceInfo);

            Supplier<TargetValueResolver.Lowered> lowerer = () -> {
                lowerings.incrementAndGet();
                return new TargetValueResolver.Lowered.Value(
                    new EmitLiteralNode(null, TypeInstance.intType(), 1, sourceInfo));
            };

            TargetValueResolver.ValuePlan valuePlan = planConstructor.newInstance(
                TargetValueResolver.PlanKind.VALUE,
                argumentTarget,
                List.of(TypeInstance.intType()),
                identity
                    ? TargetValueResolver.ConversionKind.IDENTITY
                    : TargetValueResolver.ConversionKind.STRICT,
                lowerer);

            List<TargetValueResolver.ValuePlan> mutablePlans = new ArrayList<>(List.of(valuePlan));

            ImplicitConstructorResolver.CandidateResult candidate =
                new ImplicitConstructorResolver.CandidateResult(
                    constructor, parameters, mutablePlans, 1, null, true);

            mutablePlans.clear();

            assertEquals(1, candidate.argumentPlans().size());
            candidates.add(candidate);
        }

        List<Node> arguments = new ArrayList<>(List.of(new LiteralValueNode("1", sourceInfo)));
        Method selector = ImplicitConstructorResolver.class.getDeclaredMethod(
            "selectCandidates", List.class, List.class,
            TargetValueResolver.TargetContext.class, SourceInfo.class);

        selector.setAccessible(true);

        ImplicitConstructorResolver.Result result =
            (ImplicitConstructorResolver.Result)selector.invoke(
                null, candidates, arguments, target, sourceInfo);

        ImplicitConstructorResolver.ConstructorPlan plan =
            assertInstanceOf(ImplicitConstructorResolver.Result.Applicable.class, result).plan();

        assertEquals(identityConstructor, plan.constructor());
        assertEquals(0, identityLowerings.get());
        assertEquals(0, wideningLowerings.get());

        candidates.clear();
        arguments.clear();
        assertSame(plan.lower(), plan.lower());
        assertEquals(1, identityLowerings.get());
        assertEquals(0, wideningLowerings.get());
    }
}
