// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.IdentifierNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.ast.text.UnaryOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExpressionBindingDistanceTest extends TestBase {

    private static final SourceInfo NONE = SourceInfo.none();

    @Test
    public void Binding_Node_Uses_The_Polymorphic_Expression_Distance() {
        BindingNode binding = BindingNode.newInstance(
            BindingMode.ONCE, new DistanceExpression(7), null, null, NONE);

        assertEquals(7, binding.getBindingDistance());
    }

    @Test
    public void Function_Distance_Ignores_Missing_Inverse_Literals_And_Markup_Objects() {
        FunctionExpressionNode nestedFunction = new FunctionExpressionNode(
            ObjectDecl(), path(4), List.of(path(1)), null, NONE);

        FunctionExpressionNode function = new FunctionExpressionNode(
            ObjectDecl(), path(5),
            List.of(
                LiteralExpressionNode.ofNumber(1, NONE),
                path(2), nestedFunction,
                new ObjectNode(
                    new ResolvedTypeNode(TypeInstance.ObjectType(), NONE),
                    List.of(), List.of(), false, NONE)),
            null, NONE);

        assertEquals(1, function.getBindingDistance());
    }

    @Test
    public void Qualified_Member_Construction_Distance_Includes_Qualifier_And_Expression_Arguments() {
        ConstructorExpressionNode construction = new ConstructorExpressionNode(
            ObjectDecl(), path(4), null, List.of(), typePath("Object"), List.of(),
            List.of(path(2), LiteralExpressionNode.ofNumber(1, NONE)), NONE);

        assertEquals(2, construction.getBindingDistance());
    }

    @Test
    public void Unqualified_Construction_Distance_Comes_From_Expression_Arguments() {
        ConstructorExpressionNode construction = new ConstructorExpressionNode(
            ObjectDecl(), null, null, List.of(), typePath("Object"), List.of(),
            List.of(LiteralExpressionNode.ofNumber(1, NONE), path(3)), NONE);

        assertEquals(3, construction.getBindingDistance());
    }

    @Test
    public void Invariant_Expressions_Have_No_Binding_Distance() {
        ConstructorExpressionNode construction = new ConstructorExpressionNode(
            ObjectDecl(), null, null, List.of(), typePath("Object"), List.of(),
            List.of(LiteralExpressionNode.ofNumber(1, NONE)), NONE);

        assertEquals(ExpressionNode.NO_BINDING_DISTANCE, construction.getBindingDistance());
        assertEquals(
            ExpressionNode.NO_BINDING_DISTANCE,
            LiteralExpressionNode.ofString("value", NONE).getBindingDistance());
    }

    @Test
    public void Arithmetic_Distance_Is_The_Minimum_External_Operand_Distance() {
        CompiledExpressionNode expression = new CompiledExpressionNode(
            ObjectDecl(), "arithmetic expression",
            new ArithmeticExpressionNode(
                BinaryOperator.ADD, new ExternalExpressionNode(path(3), NONE),
                LiteralExpressionNode.ofNumber(1, NONE), NONE, NONE),
            NONE);

        assertEquals(3, expression.getBindingDistance());
    }

    @Test
    public void Resolution_Does_Not_Mutate_The_Original_Expression() {
        CompiledExpressionNode expression = new CompiledExpressionNode(
            ObjectDecl(), "expression", LiteralExpressionNode.ofNumber(1, NONE), NONE);

        BindingTypeInfo result = expression.resolve(
            BindingMode.ONCE, TypeInstance.ObjectType(), TypeInstance.intType()).getTypeInfo();

        assertEquals(TypeInstance.intType(), result.emittedType());
        assertDoesNotThrow(() -> expression.accept(new Visitor() {
            @Override
            protected org.jfxcore.compiler.ast.Node onVisited(org.jfxcore.compiler.ast.Node node) {
                return node;
            }
        }));
    }

    @Test
    public void One_Resolution_Materializes_Exactly_One_Emitter() {
        ExpressionResolution resolution = LiteralExpressionNode.ofNumber(1, NONE).resolve(
            BindingMode.ONCE, TypeInstance.ObjectType(), TypeInstance.intType());

        BindingEmitterInfo first = resolution.toEmitter();
        BindingEmitterInfo second = resolution.toEmitter();

        assertSame(first, second);
        assertSame(first.getValue(), second.getValue());
        assertEquals(resolution.getTypeInfo().emittedType(), first.getType());
    }

    @Test
    public void Comparison_And_Logical_Roots_Aggregate_Distance_Polymorphically() {
        ComparisonExpressionNode comparison = new ComparisonExpressionNode(
            BinaryOperator.LESS_THAN,
            new ExternalExpressionNode(path(5), NONE),
            new ArithmeticExpressionNode(
                BinaryOperator.ADD,
                new ExternalExpressionNode(path(3), NONE),
                LiteralExpressionNode.ofNumber(1, NONE),
                NONE, NONE),
            NONE, NONE);

        LogicalExpressionNode logical = new LogicalExpressionNode(
            BinaryOperator.LOGICAL_AND, comparison,
            new ExternalExpressionNode(path(1), NONE),
            NONE, NONE);

        assertEquals(3, comparison.getBindingDistance());
        assertEquals(1, logical.getBindingDistance());
    }

    @Test
    public void All_Invariant_Mixed_Trees_Have_No_Binding_Distance() {
        ComparisonExpressionNode comparison = new ComparisonExpressionNode(
            BinaryOperator.VALUE_EQUAL,
            LiteralExpressionNode.ofString("value", NONE),
            LiteralExpressionNode.ofNull(NONE),
            NONE, NONE);

        LogicalExpressionNode logical = new LogicalExpressionNode(
            UnaryOperator.NOT,
            new GroupExpressionNode(comparison, NONE),
            NONE, NONE);

        assertEquals(ExpressionNode.NO_BINDING_DISTANCE, logical.getBindingDistance());
    }

    private static PathExpressionNode path(int distance) {
        return new PathExpressionNode(
            BindingOperator.IDENTITY,
            new BindingContextNode(BindingContextSelector.ROOT, TypeInstance.ObjectType(), distance, NONE),
            List.of(), NONE);
    }

    private static PathNode typePath(String name) {
        return new PathNode(
            null,
            List.of(new TextSegmentNode(false, new IdentifierNode(name, NONE), List.of(), NONE)),
            List.of(), NONE);
    }

    private static final class DistanceExpression extends AbstractNode implements ExpressionNode {
        private final int distance;

        private DistanceExpression(int distance) {
            super(NONE);
            this.distance = distance;
        }

        @Override
        public int getBindingDistance() {
            return distance;
        }

        @Override
        public DistanceExpression deepClone() {
            return new DistanceExpression(distance).copy(this);
        }
    }
}
