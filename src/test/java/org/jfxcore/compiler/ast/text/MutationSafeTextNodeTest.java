// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.InlineParser;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MutationSafeTextNodeTest {

    @Test
    public void StringLiteral_Retains_Decoded_Value_Lexeme_And_SourceSpan() {
        FunctionNode function = assertInstanceOf(FunctionNode.class,
            new InlineParser("format('line\\n',\"x\")", null).parseExpression());

        StringLiteralNode first = assertInstanceOf(StringLiteralNode.class, function.getArguments().get(0));
        StringLiteralNode second = assertInstanceOf(StringLiteralNode.class, function.getArguments().get(1));

        assertEquals("line\n", first.getText());
        assertEquals("'line\\n'", first.getLexeme());
        assertEquals("x", second.getText());
        assertEquals("\"x\"", second.getLexeme());
        assertEquals(new SourceInfo(0, 7, 0, 15), first.getSourceInfo());
        assertEquals("format('line\\n',\"x\")", function.formatText());
        assertEquals(function.formatText(), function.getText());
        assertEquals(function.formatText(), function.toString());

        StringLiteralNode alternateLexeme = new StringLiteralNode("line\n", "\"line\\n\"", first.getSourceInfo());
        assertNotEquals(first, alternateLexeme);

        FunctionNode clone = function.deepClone();
        assertEquals(function, clone);
        assertEquals(function.hashCode(), clone.hashCode());
        assertNotSame(function.getType(), clone.getType());
        assertNotSame(function.getArguments().get(0), clone.getArguments().get(0));
        assertEquals(first.getLexeme(), assertInstanceOf(StringLiteralNode.class, clone.getArguments().get(0)).getLexeme());
    }

    @Test
    public void BinaryOperator_Identity_Comes_From_Current_Children() {
        BinaryOperatorNode first = assertInstanceOf(BinaryOperatorNode.class, new InlineParser("a+b", null).parseExpression());
        BinaryOperatorNode second = assertInstanceOf(BinaryOperatorNode.class, new InlineParser("x+y", null).parseExpression());
        BinaryOperatorNode original = first.deepClone();

        replace(first, first.getLeft(), path("m", span(0, 1)));
        replace(first, first.getRight(), path("n", span(2, 3)));
        replace(second, second.getLeft(), path("m", span(0, 1)));
        replace(second, second.getRight(), path("n", span(2, 3)));

        assertEquals("m+n", first.getText());
        assertEquals("m+n", second.getText());
        assertNotEquals(original, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        replace(first, first.getType(), new TypeNode("int", span(0, 3)));
        replace(second, second.getType(), new TypeNode("int", span(0, 3)));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void Unary_And_Parenthesized_Text_Follows_Operand_Replacements() {
        UnaryOperatorNode unary = assertInstanceOf(UnaryOperatorNode.class, new InlineParser("-(a+1)", null).parseExpression());
        ParenthesizedNode group = assertInstanceOf(ParenthesizedNode.class, unary.getOperand());
        BinaryOperatorNode addition = assertInstanceOf(BinaryOperatorNode.class, group.getOperand());

        replace(unary, addition.getLeft(), path("b", span(2, 3)));
        replace(unary, addition.getRight(), new NumberNode("2", span(4, 5)));
        assertEquals("-(b+2)", unary.getText());

        ParenthesizedNode groupClone = group.deepClone();
        assertEquals(group, groupClone);
        assertEquals(group.hashCode(), groupClone.hashCode());
        assertEquals(group.getOpenParenSourceInfo(), groupClone.getOpenParenSourceInfo());
        assertEquals(group.getCloseParenSourceInfo(), groupClone.getCloseParenSourceInfo());

        replace(unary, group.getOperand(), path("c", span(2, 5)));
        assertEquals("-(c)", unary.getText());
        replace(unary, unary.getOperand(), path("d", span(1, 6)));
        assertEquals("-d", unary.getText());

        UnaryOperatorNode clone = unary.deepClone();
        assertEquals(unary, clone);
        assertEquals(unary.hashCode(), clone.hashCode());
        assertEquals(unary.getOperatorSourceInfo(), clone.getOperatorSourceInfo());
    }

    @Test
    public void Function_Text_Follows_Path_And_Argument_List_Mutations() {
        FunctionNode function = assertInstanceOf(FunctionNode.class, new InlineParser("foo(a,b)", null).parseExpression());
        Node firstArgument = function.getArguments().get(0);
        Node secondArgument = function.getArguments().get(1);

        replace(function, function.getPath(), path("bar", span(0, 3)));
        replace(function, firstArgument, path("c", span(4, 5)));
        remove(function, secondArgument);

        assertEquals("bar(c)", function.getText());
        assertEquals(1, function.getArguments().size());

        FunctionNode clone = function.deepClone();
        assertEquals(function, clone);
        assertEquals(function.hashCode(), clone.hashCode());
        assertNotSame(function.getPath(), clone.getPath());
        assertNotSame(function.getArguments().get(0), clone.getArguments().get(0));
    }

    @Test
    public void Path_Text_Follows_Context_Segment_Witness_And_TypeArgument_Mutations() {
        ContextSelectorNode context = new ContextSelectorNode(
            new TextNode("parent", span(1, 7)),
            new TextNode("Pane", span(8, 12)),
            new NumberNode("1", span(14, 15)),
            span(0, 1),
            span(7, 8),
            span(12, 13),
            span(15, 16),
            span(1, 16));

        TextNode widthName = new TextNode("width", span(17, 22));
        TextSegmentNode width = new TextSegmentNode(false, widthName, List.of(), span(16, 17), span(17, 22));
        PathNode contextualPath = new PathNode(context, List.of(width), List.of(), span(0, 22));

        assertEquals(":parent(Pane, 1).width", contextualPath.getText());
        replace(contextualPath, context.getSearchType(), new TextNode("VBox", span(8, 12)));
        replace(contextualPath, context.getLevel(), new NumberNode("2", span(14, 15)));
        replace(contextualPath, width.getValue(), new TextNode("height", span(17, 22)));
        assertEquals(":parent(VBox, 2).height", contextualPath.getText());

        PathNode simpleWitness = path("Foo", span(7, 10));
        PathNode comparable = new PathNode(
            null,
            List.of(new TextSegmentNode(
                false,
                new TextNode("Comparable", span(11, 21)),
                List.of(),
                span(11, 21))),
            List.of(path("String", span(22, 28))),
            span(11, 29));

        TextSegmentNode receiver = new TextSegmentNode(
            false, new TextNode("model", span(0, 5)), List.of(), span(0, 5));

        TextSegmentNode method = new TextSegmentNode(
            false,
            new TextNode("method", span(30, 36)),
            List.of(simpleWitness, comparable),
            span(5, 6),
            span(6, 36));

        PathNode witnessedPath = new PathNode(null, List.of(receiver, method), List.of(), span(0, 36));

        replace(witnessedPath, simpleWitness, path("Q", span(7, 10)));
        replace(witnessedPath, comparable.getArguments().get(0), path("Long", span(22, 28)));
        assertEquals("model.<Q,Comparable<Long>>method", witnessedPath.getText());

        PathNode selectedPath = assertInstanceOf(PathNode.class, new InlineParser("foo::bar", null).parseExpression());
        TextSegmentNode first = assertInstanceOf(TextSegmentNode.class, selectedPath.getSegments().get(0));
        TextSegmentNode selected = assertInstanceOf(TextSegmentNode.class, selectedPath.getSegments().get(1));
        assertNull(first.getSelectorSourceInfo());
        assertEquals(new SourceInfo(0, 3, 0, 5), selected.getSelectorSourceInfo());

        PathNode clone = selectedPath.deepClone();
        assertEquals(selectedPath, clone);
        assertEquals(selectedPath.hashCode(), clone.hashCode());
        assertNotSame(selectedPath.getSegments().get(1), clone.getSegments().get(1));
        assertEquals(selected.getSelectorSourceInfo(), clone.getSegments().get(1).getSelectorSourceInfo());
        ContextSelectorNode contextClone = contextualPath.deepClone().getContextSelector();
        assertNotSame(contextualPath.getContextSelector(), contextClone);
        assertEquals(span(0, 1), contextClone.getColonSourceInfo());
        assertEquals(span(7, 8), contextClone.getOpenParenSourceInfo());
        assertEquals(span(12, 13), contextClone.getCommaSourceInfo());
        assertEquals(span(15, 16), contextClone.getCloseParenSourceInfo());
    }

    @Test
    public void Constructor_Text_Follows_Qualifier_Generic_And_Argument_Mutations() {
        SourceInfo qualifierSeparator = span(5, 6);
        SourceInfo newKeyword = span(6, 9);
        SourceInfo constructorWitnesses = span(10, 13);
        SourceInfo classArguments = span(19, 22);
        SourceInfo openParen = span(22, 23);
        SourceInfo closeParen = span(36, 37);
        PathNode qualifier = path("outer", span(0, 5));
        PathNode witness = path("W", span(11, 12));
        PathNode constructedType = path("Inner", span(14, 19));
        PathNode classArgument = path("T", span(20, 21));
        PathNode value = path("value", span(23, 28));
        PathNode discarded = path("discard", span(29, 36));
        ConstructorNode constructor = new ConstructorNode(
            qualifier,
            List.of(witness),
            constructedType,
            List.of(classArgument),
            List.of(value, discarded),
            qualifierSeparator,
            newKeyword,
            constructorWitnesses,
            classArguments,
            openParen,
            closeParen,
            span(0, 37));

        assertEquals("outer.new <W> Inner<T>(value,discard)", constructor.getText());
        replace(constructor, qualifier, path("owner", span(0, 5)));
        replace(constructor, witness, path("X", span(11, 12)));
        replace(constructor, constructedType, path("Nested", span(14, 19)));
        replace(constructor, classArgument, path("U", span(20, 21)));
        replace(constructor, value, path("arg", span(23, 28)));
        remove(constructor, discarded);
        assertEquals("owner.new <X> Nested<U>(arg)", constructor.getText());

        ConstructorNode clone = constructor.deepClone();
        assertEquals(constructor, clone);
        assertEquals(constructor.hashCode(), clone.hashCode());
        assertNotSame(constructor.getQualifier(), clone.getQualifier());
        assertNotSame(constructor.getConstructorWitnesses().get(0), clone.getConstructorWitnesses().get(0));
        assertNotSame(constructor.getConstructedType(), clone.getConstructedType());
        assertNotSame(constructor.getClassArguments().get(0), clone.getClassArguments().get(0));
        assertNotSame(constructor.getArguments().get(0), clone.getArguments().get(0));
        assertNotSame(constructor.getType(), clone.getType());
        assertEquals(qualifierSeparator, clone.getQualifierSeparatorSourceInfo());
        assertEquals(newKeyword, clone.getNewSourceInfo());
        assertEquals(constructorWitnesses, clone.getConstructorWitnessSourceInfo());
        assertEquals(classArguments, clone.getClassArgumentsSourceInfo());
        assertEquals(openParen, clone.getOpenParenSourceInfo());
        assertEquals(closeParen, clone.getCloseParenSourceInfo());
        assertEquals(constructor.getSourceInfo(), clone.getSourceInfo());
    }

    @Test
    public void AttachedProperty_Text_Follows_DeclaringType_And_Property_Mutations() {
        SourceInfo selector = span(4, 5);
        SourceInfo openParen = span(5, 6);
        SourceInfo separator = span(14, 15);
        SourceInfo closeParen = span(23, 24);
        TextNode declaringType = new TextNode("GridPane", span(6, 14));
        TextNode property = new TextNode("rowIndex", span(15, 23));
        AttachedSegmentNode attached = new AttachedSegmentNode(
            false, declaringType, property, selector, openParen, separator, closeParen, span(5, 24));
        PathNode receiver = path("pane", span(0, 4));
        PathNode path = new PathNode(null,
            List.of((PathSegmentNode)receiver.getSegments().get(0).deepClone(), attached),
            List.of(), span(0, 24));

        assertEquals("pane.(GridPane.rowIndex)", path.getText());
        replace(path, declaringType, new TextNode("VBox", span(6, 14)));
        replace(path, property, new TextNode("columnIndex", span(15, 23)));
        assertEquals("columnIndex", attached.getText());
        assertEquals("pane.(VBox.columnIndex)", path.getText());

        PathNode clone = path.deepClone();
        AttachedSegmentNode attachedClone = assertInstanceOf(AttachedSegmentNode.class, clone.getSegments().get(1));
        assertEquals(path, clone);
        assertEquals(path.hashCode(), clone.hashCode());
        assertNotSame(attached, attachedClone);
        assertNotSame(attached.getDeclaringType(), attachedClone.getDeclaringType());
        assertNotSame(attached.getPropertyName(), attachedClone.getPropertyName());
        assertEquals(selector, attachedClone.getSelectorSourceInfo());
        assertEquals(openParen, attachedClone.getOpenParenSourceInfo());
        assertEquals(separator, attachedClone.getSeparatorSourceInfo());
        assertEquals(closeParen, attachedClone.getCloseParenSourceInfo());
        assertEquals(attached.getSourceInfo(), attachedClone.getSourceInfo());
    }

    private static PathNode path(String text, SourceInfo sourceInfo) {
        TextNode value = new TextNode(text, sourceInfo);
        TextSegmentNode segment = new TextSegmentNode(false, value, List.of(), sourceInfo);
        return new PathNode(null, List.of(segment), List.of(), sourceInfo);
    }

    private static SourceInfo span(int start, int end) {
        return new SourceInfo(0, start, 0, end);
    }

    private static void replace(Node root, Node target, Node replacement) {
        Visitor.visit(root, new Visitor() {
            @Override
            protected Node onVisited(Node node) {
                return node == target ? replacement : node;
            }
        });
    }

    private static void remove(Node root, Node target) {
        Visitor.visit(root, new Visitor() {
            @Override
            protected Node onVisited(Node node) {
                if (node == target) {
                    node.remove();
                }

                return node;
            }
        });
    }
}
