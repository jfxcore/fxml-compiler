// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.text.BinaryOperatorNode;
import org.jfxcore.compiler.ast.text.InvocationNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.SelectedMemberNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InvocationParserTest {

    @Test
    public void Postfix_Type_Arguments_Belong_To_Their_Named_Target() {
        InvocationNode invocation = assertInstanceOf(
            InvocationNode.class,
            new InlineParser("model.child<T>.create<U>()", null).parseExpressionStrict());
        PathNode path = assertInstanceOf(PathNode.class, invocation.getTarget());
        TextSegmentNode child = (TextSegmentNode)path.getSegments().get(1);
        TextSegmentNode create = (TextSegmentNode)path.getSegments().get(2);

        assertEquals("T", child.getTypeArguments().get(0).format());
        assertEquals("U", create.getTypeArguments().get(0).format());
        assertEquals("model.child<T>.create<U>()", invocation.format());
    }

    @Test
    public void Computed_Receivers_Support_Repeated_Property_And_Call_Suffixes() {
        SelectedMemberNode result = assertInstanceOf(
            SelectedMemberNode.class,
            new InlineParser("foo(a).bar<T>.baz(c).qux", null).parseExpressionStrict());

        assertEquals("foo(a).bar<T>.baz(c).qux", result.format());
        InvocationNode baz = assertInstanceOf(InvocationNode.class, result.getReceiver());
        SelectedMemberNode bazTarget = assertInstanceOf(SelectedMemberNode.class, baz.getTarget());
        assertEquals("baz", bazTarget.getMember().getText());
    }

    @Test
    public void Complete_Generic_Postfix_Has_Priority_Over_Relations() {
        assertInstanceOf(PathNode.class, new InlineParser("a < b >", null).parseExpressionStrict());
        assertInstanceOf(BinaryOperatorNode.class, new InlineParser("a < b > c", null).parseExpressionStrict());
        assertEquals("a<b>+c", ((SyntaxNode)new InlineParser("a < b > +c", null).parseExpressionStrict()).format());
        assertInstanceOf(InvocationNode.class, new InlineParser("a < b > (c)", null).parseExpressionStrict());
    }

    @Test
    public void Generic_Postfix_Speculation_Uses_The_Complete_List_And_Follower() {
        assertInstanceOf(BinaryOperatorNode.class, new InlineParser("a < b + c > (d)", null).parseExpressionStrict());
        assertInstanceOf(BinaryOperatorNode.class, new InlineParser("a < T", null).parseExpressionStrict());

        InvocationNode oneArgument = assertInstanceOf(
            InvocationNode.class,
            new InlineParser("f(a < b, c > +d)", null).parseExpressionStrict());
        assertEquals(1, oneArgument.getArguments().size());
        assertEquals("a<b,c>+d", ((SyntaxNode)oneArgument.getArguments().get(0)).format());

        InvocationNode twoArguments = assertInstanceOf(
            InvocationNode.class,
            new InlineParser("f(a < b, c > d)", null).parseExpressionStrict());
        assertEquals(2, twoArguments.getArguments().size());

        assertThrows(MarkupException.class, () -> new InlineParser("a<>(c)", null).parseExpressionStrict());
        assertThrows(MarkupException.class, () -> new InlineParser("a<T,>(c)", null).parseExpressionStrict());
    }

    @Test
    public void Old_Construction_And_Prefix_Witness_Syntax_Is_Rejected() {
        assertThrows(MarkupException.class, () -> new InlineParser("new Type()", null).parseExpressionStrict());
        assertThrows(MarkupException.class, () -> new InlineParser("outer.new Inner()", null).parseExpressionStrict());
        assertThrows(MarkupException.class, () -> new InlineParser("model.<T>method()", null).parseExpressionStrict());
    }
}
