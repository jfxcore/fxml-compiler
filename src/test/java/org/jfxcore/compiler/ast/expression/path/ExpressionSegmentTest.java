// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.expression.BindingTypeInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionResolution;
import org.jfxcore.compiler.ast.expression.LiteralExpressionNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ExpressionSegmentTest extends TestBase {

    private static final SourceInfo NONE = SourceInfo.none();

    @Test
    public void Equality_And_Hashing_Use_The_Source_Expression_Without_Lowering() {
        AtomicInteger lowerCount = new AtomicInteger();
        ExpressionSegment first = newSegment(
            LiteralExpressionNode.ofString("value", NONE), false, lowerCount);
        ExpressionSegment second = newSegment(
            LiteralExpressionNode.ofString("value", NONE), false, lowerCount);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(0, lowerCount.get());
    }

    @Test
    public void Different_Source_Expressions_Are_Not_Equal() {
        AtomicInteger lowerCount = new AtomicInteger();
        ExpressionSegment first = newSegment(
            LiteralExpressionNode.ofString("first", NONE), false, lowerCount);
        ExpressionSegment second = newSegment(
            LiteralExpressionNode.ofString("second", NONE), false, lowerCount);

        assertNotEquals(first, second);
        assertEquals(0, lowerCount.get());
    }

    @Test
    public void Different_Semantic_Metadata_Is_Not_Equal() {
        AtomicInteger lowerCount = new AtomicInteger();
        LiteralExpressionNode source = LiteralExpressionNode.ofString("value", NONE);

        assertNotEquals(
            newSegment(source, false, lowerCount),
            newSegment(source, true, lowerCount));
        assertEquals(0, lowerCount.get());
    }

    @Test
    public void Nullability_Uses_Retained_Semantic_Metadata_Without_Lowering() {
        AtomicInteger lowerCount = new AtomicInteger();

        assertTrue(newSegment(
            LiteralExpressionNode.ofString("nullable", NONE), true, lowerCount).isNullable());
        assertFalse(newSegment(
            LiteralExpressionNode.ofString("non-null", NONE), false, lowerCount).isNullable());
        assertEquals(0, lowerCount.get());
    }

    @Test
    public void Semantic_Identity_Is_Stable_After_The_Source_Ast_Mutates() {
        AtomicInteger lowerCount = new AtomicInteger();
        MutableExpression source = new MutableExpression("before");
        ExpressionSegment segment = newSegment(source, false, lowerCount);
        int hashCode = segment.hashCode();

        source.setValue("after");

        assertEquals(hashCode, segment.hashCode());
        assertEquals(
            newSegment(new MutableExpression("before"), false, lowerCount),
            segment);
        assertEquals(0, lowerCount.get());
    }

    private ExpressionSegment newSegment(
            ExpressionNode sourceExpression,
            boolean mayBeNull,
            AtomicInteger lowerCount) {
        TypeInstance type = TypeInstance.StringType();
        BindingTypeInfo typeInfo = new BindingTypeInfo(
            type, type, null, ValueSourceKind.NONE, ObservableDependencyKind.NONE,
            null, "literal", false, false, mayBeNull, sourceExpression.getSourceInfo());
        ExpressionResolution resolution = new ExpressionResolution(
            typeInfo,
            () -> {
                lowerCount.incrementAndGet();
                return new EmitLiteralNode(
                    type, "value", sourceExpression.getSourceInfo());
            });

        return new ExpressionSegment(sourceExpression, resolution);
    }

    private static final class MutableExpression extends AbstractNode implements ExpressionNode {
        private String value;

        private MutableExpression(String value) {
            super(NONE);
            this.value = value;
        }

        private void setValue(String value) {
            this.value = value;
        }

        @Override
        public MutableExpression deepClone() {
            return new MutableExpression(value).copy(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj
                || obj instanceof MutableExpression other && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
