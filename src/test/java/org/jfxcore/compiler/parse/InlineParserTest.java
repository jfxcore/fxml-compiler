// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.AttachedSegmentNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.BinaryOperatorNode;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.ContextSelector;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.InvocationNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.LiteralKeywordNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.ParenthesizedNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.SelectedMemberNode;
import org.jfxcore.compiler.ast.text.StringLiteralNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.ast.text.UnaryOperator;
import org.jfxcore.compiler.ast.text.UnaryOperatorNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.XmlEntityDecoder;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.TestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class InlineParserTest extends TestBase {

    @Test
    public void Parse_Simple_Identifier() {
        var obj = new InlineParser("{foo}", "fx").parseObject();
        assertFalse(obj.getType().isIntrinsic());
        assertEquals("foo", obj.getType().getName());
        assertEquals("foo", obj.getType().getMarkupName());
    }

    @Test
    public void Parse_Fully_Qualified_Identifier() {
        var obj = new InlineParser("{foo.bar.baz}", "fx").parseObject();
        assertFalse(obj.getType().isIntrinsic());
        assertEquals("foo.bar.baz", obj.getType().getName());
        assertEquals("foo.bar.baz", obj.getType().getMarkupName());
    }

    @Test
    public void Parse_Namespace_With_Identifier() {
        var obj = new InlineParser("{fx:foo}", "fx").parseObject();
        assertTrue(obj.getType().isIntrinsic());
        assertEquals("foo", obj.getType().getName());
        assertEquals("fx:foo", obj.getType().getMarkupName());
    }

    @Test
    public void Parse_Namespace_With_Fully_Qualified_Identifier_Fails() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("{fx:foo.bar.baz}", "fx").parseObject());
        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Value_Must_Start_With_OpenCurly() {
        String markup = """
            foo
        """;

        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser(markup, "fx").parseObject());

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains("{"));
    }

    @Test
    public void Parse_ObjectNode_With_Properties() {
        String markup = """
            {GridPane
                fx:id = pane0
                list = 1 2   3    4
                composite = foo bar,
                    baz(123, 5.0, "qux quux")
                text = "foo, bar; baz"
                "content text"
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();

        assertEquals("GridPane", root.getType().getName());
        assertEquals(4, root.getProperties().size());
        assertEquals(1, root.getChildren().size());
        assertEquals("content text", ((TextNode)root.getChildren().get(0)).getText());

        assertEquals("fx:id", root.getProperties().get(0).getMarkupName());
        assertEquals("id", root.getProperties().get(0).getName());
        assertTrue(root.getProperties().get(0).isIntrinsic());
        assertEquals(1, root.getProperties().get(0).getValues().size());
        assertEquals("pane0", ((TextNode)root.getProperties().get(0).getValues().get(0)).getText());

        TextNode node = ((TextNode)root.getProperties().get(1).getValues().get(0));
        assertEquals("list", root.getProperties().get(1).getName());
        assertEquals(1, root.getProperties().get(1).getValues().size());
        assertEquals("1 2 3 4", node.getText());

        ListNode list = ((ListNode)root.getProperties().get(2).getValues().get(0));
        assertEquals("composite", root.getProperties().get(2).getName());
        assertEquals(1, root.getProperties().get(2).getValues().size());
        assertEquals("foo bar baz(123,5.0,\"qux quux\")", list.getText());
        assertEquals(2, list.getValues().size());
        assertEquals("foo bar", ((TextNode)(list.getValues().get(0))).getText());
        assertEquals("baz(123,5.0,\"qux quux\")", ((TextNode)(list.getValues().get(1))).getText());

        InvocationNode funcNode = (InvocationNode)list.getValues().get(1);
        assertEquals("baz", invocationPath(funcNode).getText());
        assertEquals(3, funcNode.getArguments().size());
        assertEquals("123", ((TextNode)funcNode.getArguments().get(0)).getText());
        assertEquals("5.0", ((TextNode)funcNode.getArguments().get(1)).getText());
        assertEquals("qux quux", ((TextNode)funcNode.getArguments().get(2)).getText());

        assertEquals("text", root.getProperties().get(3).getName());
        assertEquals(1, root.getProperties().get(3).getValues().size());
        assertEquals("foo, bar; baz", ((TextNode)root.getProperties().get(3).getValues().get(0)).getText());
    }

    @Test
    public void Collection_Content_Is_Allowed_In_Objects() {
        String markup = """
            {GridPane
                {test1
                    foo bar { VBox fx:id = baz }
                }
                {test2
                    { VBox fx:id = foo }, {VBox}, bar
                }
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();

        assertEquals(2, root.getChildren().size());

        CompositeNode compNode = (CompositeNode)((ObjectNode)root.getChildren().get(0)).getChildren().get(0);
        assertEquals("foo", ((TextNode)compNode.getValues().get(0)).getText());
        assertEquals("bar", ((TextNode)compNode.getValues().get(1)).getText());
        assertEquals("VBox", compNode.getValues().get(2).getType().getMarkupName());

        ListNode listNode = (ListNode)((ObjectNode)root.getChildren().get(1)).getChildren().get(0);
        assertEquals("VBox", listNode.getValues().get(0).getType().getMarkupName());
        assertEquals("VBox", listNode.getValues().get(1).getType().getMarkupName());
        assertEquals("bar", ((TextNode)listNode.getValues().get(2)).getText());
    }

    @Test
    public void Unmatched_Curly_Braces_Throws() {
        String markup = """
            {GridPane
                foo = {bar
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObject());

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains("}"));
    }

    @Test
    public void Unmatched_Parens_Throws() {
        String markup = """
            {foo bar(baz(qux)
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObject());

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains(")"));
    }

    @Test
    public void Function_Is_Parsed_With_Whitespace() {
        String markup = """
            {foo bar(
                baz , qux
            )}
        """;

        var objectNode = new InlineParser(markup, "fx").parseObject();
        assertEquals(1, objectNode.getChildren().size());
        var functionNode = (InvocationNode)objectNode.getChildren().get(0);
        assertEquals(2, functionNode.getArguments().size());
        assertEquals("baz", ((PathNode)functionNode.getArguments().get(0)).getText());
        assertEquals("qux", ((PathNode)functionNode.getArguments().get(1)).getText());
    }

    @Test
    public void Empty_Property_Value_Throws() {
        String markup = """
            {GridPane
                style=
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObject());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Block_Comments() {
        String markup = """
            {Label
                /* test comment */
                text = "foo /* not a comment */ bar"
                /*
                    multi
                    {line
                        comment
                    }
                */
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();
        assertEquals(0, root.getChildren().size());
        assertEquals(
            "foo /* not a comment */ bar",
            ((TextNode)root.getProperties().get(0).getValues().get(0)).getText());
    }

    @Test
    public void Line_Comments() {
        String markup = """
            {Label
                // test comment
                text = "foo // not a comment" // comment
                // /*
                    multi
                    {line
                        comment
                    }
                */
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();
        assertEquals(0, root.getChildren().size());
        assertEquals(
            "foo // not a comment",
            ((TextNode)root.getProperties().get(0).getValues().get(0)).getText());
    }

    @Test
    public void Escaped_Symbols_In_String_Literal() {
        String markup = """
            {Label
                text0 = "foo\\bbar"
                text1 = "foo\\tbar"
                text2 = "foo\\nbar"
                text3 = "foo\\fbar"
                text4 = "foo\\rbar"
                text5 = "foo\\"bar"
                text6 = "foo\\'bar"
                text7 = "\\u2661"
                text8 = "\\\\u2661"
                text9 = "\\\\\\u2661"
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();
        assertEquals("foo\bbar", ((TextNode)root.getProperties().get(0).getValues().get(0)).getText());
        assertEquals("foo\tbar", ((TextNode)root.getProperties().get(1).getValues().get(0)).getText());
        assertEquals("foo\nbar", ((TextNode)root.getProperties().get(2).getValues().get(0)).getText());
        assertEquals("foo\fbar", ((TextNode)root.getProperties().get(3).getValues().get(0)).getText());
        assertEquals("foo\rbar", ((TextNode)root.getProperties().get(4).getValues().get(0)).getText());
        assertEquals("foo\"bar", ((TextNode)root.getProperties().get(5).getValues().get(0)).getText());
        assertEquals("foo'bar", ((TextNode)root.getProperties().get(6).getValues().get(0)).getText());
        assertEquals("\u2661", ((TextNode)root.getProperties().get(7).getValues().get(0)).getText());
        assertEquals("\\u2661", ((TextNode)root.getProperties().get(8).getValues().get(0)).getText());
        assertEquals("\\\u2661", ((TextNode)root.getProperties().get(9).getValues().get(0)).getText());
    }

    @Test
    public void Missing_Delimiter_Between_Properties_Fails() {
        String markup = """
            {Pane
                fx:id=pane0 foo={Pane}
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObject());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Property_And_Content_On_Same_Line() {
        String markup = """
            { GridPane fx:bar=pane0 foo; { GridPane fx:bar=pane0 } }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();
        assertEquals(1, root.getProperties().size());
        assertTrue(root.getProperties().get(0).isIntrinsic());
        assertEquals("bar", root.getProperties().get(0).getName());
        assertEquals("pane0 foo", ((TextNode)root.getProperties().get(0).getValues().get(0)).getText());
        assertEquals(1, root.getChildren().size());
        assertEquals("GridPane", ((ObjectNode)root.getChildren().get(0)).getType().getName());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void Binding_Syntax_With_Parent_Selector() {
        String markup = """
            {GridPane
                {VBox
                    prefWidth=${:parent(GridPane, 1).prefWidth}
                }
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();
        PropertyNode prefWidth = ((ObjectNode)root.getChildren().get(0)).findProperty("prefWidth");
        PathNode path = assertInstanceOf(
            PathNode.class, ((ObjectNode)prefWidth.getValues().get(0)).getChildren().get(0));
        assertSame(ContextSelector.PARENT, path.getContextSelector().getSelector());
        assertEquals("GridPane", path.getContextSelector().getSearchType().getText());
        assertEquals("1", path.getContextSelector().getLevel().getText());
        assertEquals("prefWidth", path.getSegments().get(0).getText());
    }

    @Test
    public void Canonical_Context_Selectors_Are_Terminal_Or_Selected_Primaries() {
        for (ContextSelector selector : ContextSelector.values()) {
            String source = ":" + selector.getText();
            PathNode path = assertInstanceOf(PathNode.class, new InlineParser(source, "fx").parseExpression());
            ContextSelectorNode context = path.getContextSelector();

            assertNotNull(context);
            assertTrue(path.getSegments().isEmpty());
            assertEquals(source, path.getText());
            assertSame(selector, context.getSelector());
            assertEquals(new SourceInfo(0, 0, 0, 1), context.getColonSourceInfo());
            assertEquals(new SourceInfo(0, 1, 0, source.length()), context.getSelectorSourceInfo());
            assertEquals(new SourceInfo(0, 0, 0, source.length()), context.getSourceInfo());
            assertEquals(context.getSourceInfo(), path.getSourceInfo());
        }

        PathNode normal = assertInstanceOf(PathNode.class, new InlineParser(":parent.width", "fx").parseExpression());
        assertEquals("width", normal.getSegments().get(0).getText());
        assertFalse(normal.getSegments().get(0).isObservableSelector());

        PathNode observable = assertInstanceOf(PathNode.class, new InlineParser(":element::value", "fx").parseExpression());
        assertEquals("value", observable.getSegments().get(0).getText());
        assertTrue(observable.getSegments().get(0).isObservableSelector());
    }

    @Test
    public void Leading_Observable_Selector_Uses_The_Implicit_Context() {
        PathNode path = assertInstanceOf(PathNode.class,
            new InlineParser("::foo<T>::bar", "fx").parseExpression());

        assertNull(path.getContextSelector());
        assertEquals("::foo<T>::bar", path.getText());
        assertEquals(2, path.getSegments().size());
        assertTrue(path.getSegments().get(0).isObservableSelector());
        assertEquals(new SourceInfo(0, 0, 0, 2), path.getSegments().get(0).getSelectorSourceInfo());
        assertEquals("T", path.getSegments().get(0).getTypeArguments().get(0).getText());
        assertTrue(path.getSegments().get(1).isObservableSelector());

        PathNode ordinarySuffix = assertInstanceOf(PathNode.class,
            new InlineParser("::foo.bar", "fx").parseExpression());
        assertTrue(ordinarySuffix.getSegments().get(0).isObservableSelector());
        assertFalse(ordinarySuffix.getSegments().get(1).isObservableSelector());

        InvocationNode invocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("::foo<T>()", "fx").parseExpression());
        assertEquals("::foo<T>()", invocation.getText());

        PathNode attached = assertInstanceOf(PathNode.class,
            new InlineParser("::(Owner.property)", "fx").parseExpression());
        assertNull(attached.getContextSelector());
        assertTrue(assertInstanceOf(
            AttachedSegmentNode.class, attached.getSegments().get(0)).isObservableSelector());
        assertEquals("::(Owner.property)", attached.getText());

        PathNode contextAttached = assertInstanceOf(PathNode.class,
            new InlineParser(":context.(Owner.property)", "fx").parseExpression());
        assertSame(ContextSelector.CONTEXT, contextAttached.getContextSelector().getSelector());
        assertFalse(contextAttached.getSegments().get(0).isObservableSelector());
    }

    @Test
    public void Parent_Arguments_Retain_Their_Distinct_Children_And_Punctuation() {
        String source = ":parent(javafx.scene.layout.Pane, -12).width";
        PathNode path = assertInstanceOf(PathNode.class, new InlineParser(source, "fx").parseExpression());
        ContextSelectorNode context = path.getContextSelector();
        int comma = source.indexOf(',');
        int closeParen = source.indexOf(')');

        assertNotNull(context);
        assertEquals("javafx.scene.layout.Pane", context.getSearchType().getText());
        assertEquals("-12", context.getLevel().getText());
        assertEquals(new SourceInfo(0, 7, 0, 8), context.getOpenParenSourceInfo());
        assertEquals(new SourceInfo(0, comma, 0, comma + 1), context.getCommaSourceInfo());
        assertEquals(new SourceInfo(0, closeParen, 0, closeParen + 1), context.getCloseParenSourceInfo());
        assertEquals(":parent(javafx.scene.layout.Pane, -12).width", path.getText());

        ContextSelectorNode depthOnly = assertInstanceOf(PathNode.class,
            new InlineParser(":parent(2)", "fx").parseExpression()).getContextSelector();
        assertNotNull(depthOnly);
        assertNull(depthOnly.getSearchType());
        assertEquals("2", depthOnly.getLevel().getText());
        assertNull(depthOnly.getCommaSourceInfo());

        ContextSelectorNode typeOnly = assertInstanceOf(PathNode.class,
            new InlineParser(":parent(Pane)", "fx").parseExpression()).getContextSelector();
        assertNotNull(typeOnly);
        assertEquals("Pane", typeOnly.getSearchType().getText());
        assertNull(typeOnly.getLevel());
        assertNull(typeOnly.getCommaSourceInfo());

        ContextSelectorNode typedDepth = assertInstanceOf(PathNode.class,
            new InlineParser(":parent(Pane,+3)", "fx").parseExpression()).getContextSelector();
        assertNotNull(typedDepth);
        assertEquals("+3", typedDepth.getLevel().getText());
        assertEquals("parent(Pane, +3)", typedDepth.getText());
    }

    @Test
    public void Invalid_Or_Unsupported_Context_Forms_Are_Rejected_Locally() {
        for (String source : new String[] {
                ":self",
                ":context(1)",
                ":element(1)",
                ":root(1)",
                ":parent()",
                ":parent(1, Pane)",
                ":parent(Pane,)",
                ":parent(Pane, 1, 2)",
                ":parent(1.5)",
                "::",
                ":::foo",
                "::()",
                ".foo"}) {
            assertThrows(MarkupException.class, () -> new InlineParser(source, "fx").parseExpression(), source);
        }
    }

    @Test
    public void Malformed_Context_And_Callable_Forms_Report_Local_Spans() {
        assertExpressionError(":parent()", ErrorCode.EXPECTED_IDENTIFIER, 8, 9);
        assertExpressionError(":parent(Pane,)", ErrorCode.UNEXPECTED_TOKEN, 13, 14);
        assertExpressionError(":parent(1, Pane)", ErrorCode.EXPECTED_TOKEN, 9, 10);
        assertExpressionError(":parent(Pane, 1, 2)", ErrorCode.EXPECTED_TOKEN, 15, 16);
        assertExpressionError(":parent(1.5)", ErrorCode.UNEXPECTED_TOKEN, 8, 11);
        assertExpressionError(":::member", ErrorCode.EXPECTED_IDENTIFIER, 2, 3);
        assertExpressionError(":context()", ErrorCode.UNEXPECTED_TOKEN, 8, 9);
        assertExpressionError(":element()", ErrorCode.UNEXPECTED_TOKEN, 8, 9);
        assertExpressionError(":root()", ErrorCode.UNEXPECTED_TOKEN, 5, 6);
        assertExpressionError("pane.(GridPane.rowIndex)()", ErrorCode.UNEXPECTED_TOKEN, 24, 25);
    }

    @Test
    public void Context_Primaries_Participate_In_Relations_And_Qualified_Construction() {
        BinaryOperatorNode relation = assertBinary(
            new InlineParser(":parent(Pane) < owner", "fx").parseExpression(),
            BinaryOperator.LESS_THAN);
        PathNode parent = assertInstanceOf(PathNode.class, relation.getLeft());
        assertTrue(parent.getSegments().isEmpty());
        assertEquals("Pane", parent.getContextSelector().getSearchType().getText());

        InvocationNode construction = assertInstanceOf(InvocationNode.class,
            new InlineParser(":parent.Inner(value)", "fx").parseExpression());
        PathNode constructionTarget = invocationPath(construction);
        assertSame(ContextSelector.PARENT, constructionTarget.getContextSelector().getSelector());
        assertEquals("Inner", constructionTarget.getSegments().get(0).getText());

        InvocationNode method = assertInstanceOf(InvocationNode.class,
            new InlineParser(":parent.method<T>(value)", "fx").parseExpression());
        assertEquals("T", invocationPath(method).getSegments().get(0).getTypeArguments().get(0).getText());
    }

    @Test
    public void This_And_Context_Names_Are_Ordinary_Identifiers() {
        PathNode ordinaryThis = assertInstanceOf(PathNode.class, new InlineParser("this", "fx").parseExpression());
        assertNull(ordinaryThis.getContextSelector());
        assertEquals("this", ordinaryThis.getText());

        PathNode observable = assertInstanceOf(PathNode.class, new InlineParser("this::value", "fx").parseExpression());
        assertTrue(observable.getSegments().get(1).isObservableSelector());

        for (String name : new String[] {"context", "element", "root", "self", "parent", "item"}) {
            PathNode ordinary = assertInstanceOf(PathNode.class, new InlineParser(name + ".value", "fx").parseExpression());
            assertNull(ordinary.getContextSelector(), name);
            assertEquals(name, ordinary.getSegments().get(0).getText(), name);
        }

        PathNode selectedThis = assertInstanceOf(PathNode.class, new InlineParser("model.this", "fx").parseExpression());
        assertEquals("this", selectedThis.getSegments().get(1).getText());
        assertInstanceOf(InvocationNode.class, new InlineParser("this()", "fx").parseExpression());
    }

    @Test
    public void Attached_Property_Uses_Its_Dedicated_Restricted_Segment() {
        String source = "pane.(javafx.scene.layout.GridPane.rowIndex).value";
        PathNode path = assertInstanceOf(PathNode.class, new InlineParser(source, "fx").parseExpression());
        AttachedSegmentNode attached = assertInstanceOf(AttachedSegmentNode.class, path.getSegments().get(1));

        assertEquals("javafx.scene.layout.GridPane", attached.getDeclaringType().getText());
        assertEquals("rowIndex", attached.getPropertyName().getText());
        assertFalse(attached.isObservableSelector());
        assertEquals("value", path.getSegments().get(2).getText());
        assertEquals(source, path.getText());

        PathNode observablePath = assertInstanceOf(PathNode.class,
            new InlineParser("pane::(GridPane.rowIndex)", "fx").parseExpression());
        assertTrue(assertInstanceOf(
            AttachedSegmentNode.class,
            observablePath.getSegments().get(1)).isObservableSelector());

        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("pane.(Owner.value).method()", "fx").parseExpression());
        assertInstanceOf(AttachedSegmentNode.class, invocationPath(function).getSegments().get(1));

        assertThrows(MarkupException.class,
            () -> new InlineParser("pane.(GridPane)", "fx").parseExpression());
        assertThrows(MarkupException.class,
            () -> new InlineParser("pane.(GridPane.rowIndex)()", "fx").parseExpression());
    }

    @Test
    public void Intrinsic_Namespace_Is_Detected_When_Intrinsic_Prefix_Is_Specified() {
        ObjectNode root = new InlineParser("{GridPane prefWidth=$foo}", "fx").parseObject();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());

        root = new InlineParser("{GridPane prefWidth={foo:Evaluate foo}}", "foo").parseObject();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());
    }

    @Test
    public void Invalid_Intrinsic_Namespace_Fails() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("{GridPane prefWidth={foo:Evaluate foo}}", "bar").parseObject());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Literal_Is_Parsed_As_Text() {
        ObjectNode root = new InlineParser("{Foo bar=true}", null).parseObject();
        var value = root.getProperty("bar").getValues().get(0);
        assertTrue(value instanceof TextNode);
        assertFalse(value instanceof LiteralKeywordNode);
    }

    @Test
    public void Literal_Is_Parsed_As_Number() {
        ObjectNode root = new InlineParser("{Foo bar=5.0}", null).parseObject();
        assertTrue(root.getProperty("bar").getValues().get(0) instanceof NumberNode);
    }

    @Test
    public void Content_After_CurlyBraces_Is_Not_Allowed() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("{Foo bar=5.0}, {baz}", null).parseObject());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void ListContent_With_Empty_Strings_Works_Correctly() {
        ObjectNode root = new InlineParser("{Foo '', 'baz', ''}", null).parseObject();
        assertEquals("baz", ((TextNode)root.getChildren().get(0)).getText());
    }

    @Test
    public void TypeWitness_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("$foo<String>()", null).parseObject();
        PathNode path = invocationPath((InvocationNode)root.getChildren().get(0));
        var segment = (TextSegmentNode)path.getSegments().get(0);
        assertEquals(1, segment.getTypeArguments().size());
        assertEquals("String", segment.getTypeArguments().get(0).getText());
        assertEquals("foo", segment.getValue().getText());
    }

    @Test
    public void TypeWitnessList_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("$foo<j.l.String, Integer, j.l.Comparable<j.l.Double>>()", null).parseObject();
        PathNode path = invocationPath((InvocationNode)root.getChildren().get(0));
        assertEquals("foo", path.getSegments().get(0).getText());
        TextSegmentNode segment = (TextSegmentNode)path.getSegments().get(0);
        assertEquals(3, segment.getTypeArguments().size());
        assertEquals("j.l.String", segment.getTypeArguments().get(0).getText());
        assertEquals("Integer", segment.getTypeArguments().get(1).getText());
        PathNode witnessPath = segment.getTypeArguments().get(2);
        assertEquals(3, witnessPath.getSegments().size());
        assertEquals("j", witnessPath.getSegments().get(0).getText());
        assertEquals("l", witnessPath.getSegments().get(1).getText());
        assertEquals("Comparable", witnessPath.getSegments().get(2).getText());
        assertEquals(1, witnessPath.getArguments().size());
        PathNode argPath = witnessPath.getArguments().get(0);
        assertEquals(3, argPath.getSegments().size());
        assertEquals("j", argPath.getSegments().get(0).getText());
        assertEquals("l", argPath.getSegments().get(1).getText());
        assertEquals("Double", argPath.getSegments().get(2).getText());
    }

    @Test
    public void MultiSegment_Path_With_TypeWitnesses_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("$foo<Foo>.bar<Bar>::baz<Baz<Double>>()", null).parseObject();
        var segments = invocationPath((InvocationNode)root.getChildren().get(0)).getSegments();
        assertEquals(3, segments.size());
        var segment1 = (TextSegmentNode)segments.get(0);
        assertEquals(1, segment1.getTypeArguments().size());
        assertEquals("Foo", segment1.getTypeArguments().get(0).getText());
        assertEquals("foo", segment1.getValue().getText());
        assertFalse(segment1.isObservableSelector());
        var segment2 = (TextSegmentNode)segments.get(1);
        assertEquals(1, segment2.getTypeArguments().size());
        assertEquals("Bar", segment2.getTypeArguments().get(0).getText());
        assertEquals("bar", segment2.getValue().getText());
        assertFalse(segment2.isObservableSelector());
        var segment3 = (TextSegmentNode)segments.get(2);
        assertEquals(1, segment3.getTypeArguments().size());
        assertEquals("Baz<Double>", segment3.getTypeArguments().get(0).getText());
        assertEquals(1, segment3.getTypeArguments().get(0).getArguments().size());
        assertEquals("Double", segment3.getTypeArguments().get(0).getArguments().get(0).getText());
        assertEquals("baz", segment3.getValue().getText());
        assertTrue(segment3.isObservableSelector());
    }

    @Test
    public void Missing_Close_Angle_Bracket_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () ->
            new InlineParser("$value<String,>()", null).parseObject());
        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void ParameterizedType_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("{Foo <Bar, Comparable<Baz>, java.lang.String>}", null).parseObject();
        TextNode text = (TextNode)root.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS).getValues().get(0);
        assertEquals(new SourceInfo(0, 1, 0, 45), root.getType().getSourceInfo());
        assertEquals("Bar,Comparable<Baz>,java.lang.String", text.getText());
    }

    @Test
    public void ParameterizedType_Whitespace_Between_Identifiers_Is_Retained() {
        ObjectNode root = new InlineParser("{Foo<Bar, Comparable<Foo   Bar Baz>, java.lang.String>}", null).parseObject();
        TextNode text = (TextNode)root.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS).getValues().get(0);
        assertEquals(new SourceInfo(0, 1, 0, 54), root.getType().getSourceInfo());
        assertEquals("Bar,Comparable<Foo Bar Baz>,java.lang.String", text.getText());
    }

    @Test
    public void Markup_Extension_Head_Owns_Type_Arguments_With_Or_Without_Whitespace() {
        for (String source : new String[] {"{MyExt<T> value}", "{MyExt <T> value}"}) {
            ObjectNode object = new InlineParser(source, "fx").parseObject();
            PropertyNode typeArguments = object.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS);

            assertNotNull(typeArguments, source);
            assertEquals("T", assertInstanceOf(TextNode.class, typeArguments.getValues().get(0)).getText(), source);
            assertEquals("value", assertInstanceOf(PathNode.class, object.getChildren().get(0)).getText(), source);
        }

        ObjectNode witnessedContent = new InlineParser("{MyExt value<T>}", "fx").parseObject();
        assertNull(witnessedContent.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS));
        PathNode path = assertInstanceOf(PathNode.class, witnessedContent.getChildren().get(0));
        assertEquals("T", path.getSegments().get(0).getTypeArguments().get(0).getText());
    }

    @Test
    public void SyntaxMapping_Cannot_Be_Parameterized() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("${<Foo>bar}", null).parseObject());

        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void Prefix_Syntax_Cannot_Be_Parameterized() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("%<String>foo", "fx", Map.of('%', "StaticResource")).parseObject());

        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void Prefix_Syntax_Is_Expanded() {
        ObjectNode objectNode = new InlineParser(
            "%foo; formatArguments=bar, baz",
            "fx",
            Map.of('%', "StaticResource")).parseObject();

        assertEquals("StaticResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof TextNode n && n.getText().equals("foo"));
        assertEquals(1, objectNode.getProperties().size());
        assertEquals("formatArguments", objectNode.getProperties().get(0).getName());
    }

    @Test
    public void Prefix_Syntax_Allows_Whitespace_After_Prefix() {
        ObjectNode objectNode = new InlineParser("%   foo", "fx", Map.of('%', "StaticResource")).parseObject();
        assertEquals("StaticResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof TextNode n && n.getText().equals("foo"));
    }

    @Test
    public void Prefix_Syntax_Is_Expanded_Within_PropertyExpression() {
        PropertyNode property = new InlineParser(
            "{Test qux=% foo}",
            "fx",
            Map.of('%', "StaticResource")).parseObject().getProperties().get(0);

        assertEquals("qux", property.getName());
        ObjectNode objectNode = assertInstanceOf(ObjectNode.class, property.getValues().get(0));
        assertEquals("StaticResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof TextNode n && n.getText().equals("foo"));
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,Evaluate",
        "${foo.bar.baz},Observe",
        "#{foo.bar.baz},Synchronize"
    })
    public void Compact_Syntax_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.getText().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$::foo::bar::baz,Evaluate",
        "${::foo::bar::baz},Observe",
        ">{::foo::bar::baz},Push",
        "#{::foo::bar::baz},Synchronize"
    })
    public void Compact_Syntax_With_ObservableSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n
            && n.getText().equals("::foo::bar::baz"));
    }

    @Test
    public void Long_Form_Intrinsics_Accept_A_Leading_Observable_Selector() {
        for (String intrinsic : new String[] {"Evaluate", "Observe", "Push", "Synchronize"}) {
            ObjectNode objectNode = new InlineParser(
                "{fx:" + intrinsic + " ::foo}", "fx").parseObject();

            assertEquals(intrinsic, objectNode.getType().getName());
            PathNode path = assertInstanceOf(PathNode.class, objectNode.getChildren().get(0));
            assertEquals("::foo", path.getText());
            assertTrue(path.getSegments().get(0).isObservableSelector());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,Evaluate",
        "${..foo.bar.baz},Observe",
        "#{..foo.bar.baz},Synchronize"
    })
    public void Compact_Content_Syntax_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(intrinsicName, objectNode.getType().getName());
        List<ValueNode> values = ((CompositeNode)objectNode.getChildren().get(0)).getValues();
        assertEquals(3, values.size());
        assertTrue(values.get(0) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(1) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(2) instanceof PathNode t && t.getText().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "'$:parent(Pane, 1).foo.bar.baz',Evaluate",
        "'${:parent(Pane, 1).foo.bar.baz}',Observe",
        "'#{:parent(Pane, 1).foo.bar.baz}',Synchronize"
    })
    public void Compact_Syntax_With_ContextSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(objectNode.getType().getName(), intrinsicName);
        PathNode pathNode = (PathNode)objectNode.getChildren().get(0);
        assertEquals(3, pathNode.getSegments().size());
        assertEquals("foo", pathNode.getSegments().get(0).getText());
        assertEquals("bar", pathNode.getSegments().get(1).getText());
        assertEquals("baz", pathNode.getSegments().get(2).getText());
        assertSame(ContextSelector.PARENT, pathNode.getContextSelector().getSelector());
        assertEquals("Pane", pathNode.getContextSelector().getSearchType().getText());
        assertEquals("1", pathNode.getContextSelector().getLevel().getText());
    }

    @ParameterizedTest
    @CsvSource({
        "'$[..:parent(Pane, 1).foo.bar.baz]',Evaluate",
        "'${[..:parent(Pane, 1).foo.bar.baz]}',Observe",
        "'#{[..:parent(Pane, 1).foo.bar.baz]}',Synchronize"
    })
    public void Compact_Content_Syntax_With_ContextSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(objectNode.getType().getName(), intrinsicName);
        List<ValueNode> values = ((CompositeNode)objectNode.getChildren().get(0)).getValues();
        assertEquals(5, values.size());
        assertTrue(values.get(0) instanceof TextNode t && t.getText().equals("["));
        assertTrue(values.get(1) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(2) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(4) instanceof TextNode t && t.getText().equals("]"));
        PathNode pathNode = (PathNode)values.get(3);
        assertEquals(3, pathNode.getSegments().size());
        assertEquals("foo", pathNode.getSegments().get(0).getText());
        assertEquals("bar", pathNode.getSegments().get(1).getText());
        assertEquals("baz", pathNode.getSegments().get(2).getText());
        assertSame(ContextSelector.PARENT, pathNode.getContextSelector().getSelector());
        assertEquals("Pane", pathNode.getContextSelector().getSearchType().getText());
        assertEquals("1", pathNode.getContextSelector().getLevel().getText());
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,Evaluate",
        "${foo.bar.baz},Observe",
        "#{foo.bar.baz},Synchronize"
    })
    public void Compact_Syntax_Is_Expanded_Within_ListExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux, %s}", compactIntrinsic);
        ListNode list = (ListNode)new InlineParser(input, "fx").parseObject().getChildren().get(0);
        assertEquals(2, list.getValues().size());
        assertEquals("qux", ((TextNode)list.getValues().get(0)).getText());
        ObjectNode objectNode = (ObjectNode)list.getValues().get(1);
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.getText().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,Evaluate",
        "${..foo.bar.baz},Observe",
        "#{..foo.bar.baz},Synchronize"
    })
    public void Compact_Content_Syntax_Is_Expanded_Within_ListExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux, %s}", compactIntrinsic);
        ListNode list = (ListNode)new InlineParser(input, "fx").parseObject().getChildren().get(0);
        assertEquals(2, list.getValues().size());
        assertEquals("qux", ((TextNode)list.getValues().get(0)).getText());
        ObjectNode objectNode = (ObjectNode)list.getValues().get(1);
        assertEquals(intrinsicName, objectNode.getType().getName());
        List<ValueNode> values = ((CompositeNode)objectNode.getChildren().get(0)).getValues();
        assertEquals(3, values.size());
        assertTrue(values.get(0) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(1) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(2) instanceof PathNode t && t.getText().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,Evaluate",
        "${foo.bar.baz},Observe",
        "#{foo.bar.baz},Synchronize"
    })
    public void Compact_Syntax_Is_Expanded_Within_PropertyExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=%s}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObject().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        ObjectNode objectNode = (ObjectNode)property.getValues().get(0);
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.getText().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,Evaluate",
        "${..foo.bar.baz},Observe",
        "#{..foo.bar.baz},Synchronize"
    })
    public void Compact_Content_Syntax_Is_Expanded_Within_PropertyExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=%s}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObject().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        ObjectNode objectNode = (ObjectNode)property.getValues().get(0);
        assertEquals(intrinsicName, objectNode.getType().getName());
        List<ValueNode> values = ((CompositeNode)objectNode.getChildren().get(0)).getValues();
        assertEquals(3, values.size());
        assertTrue(values.get(0) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(1) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(2) instanceof PathNode t && t.getText().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,Evaluate",
        "${foo.bar.baz},Observe",
        "#{foo.bar.baz},Synchronize"
    })
    public void Compact_Syntax_Is_Expanded_Within_FunctionExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=func(%s, 'quux')}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObject().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        InvocationNode functionNode = (InvocationNode)property.getValues().get(0);
        assertEquals("func", invocationPath(functionNode).getText());
        assertEquals(2, functionNode.getArguments().size());
        ObjectNode objectNode = (ObjectNode)functionNode.getArguments().get(0);
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.getText().equals("foo.bar.baz"));
        assertTrue(functionNode.getArguments().get(1) instanceof TextNode n && n.getText().equals("quux"));
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,Evaluate",
        "${..foo.bar.baz},Observe",
        "#{..foo.bar.baz},Synchronize"
    })
    public void Compact_Content_Syntax_Is_Expanded_Within_FunctionExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=func(%s, 'quux')}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObject().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        InvocationNode functionNode = (InvocationNode)property.getValues().get(0);
        assertEquals("func", invocationPath(functionNode).getText());
        assertEquals(2, functionNode.getArguments().size());
        assertTrue(functionNode.getArguments().get(1) instanceof TextNode n && n.getText().equals("quux"));
        ObjectNode objectNode = (ObjectNode)functionNode.getArguments().get(0);
        assertEquals(intrinsicName, objectNode.getType().getName());
        List<ValueNode> values = ((CompositeNode)objectNode.getChildren().get(0)).getValues();
        assertEquals(3, values.size());
        assertTrue(values.get(0) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(1) instanceof TextNode t && t.getText().equals("."));
        assertTrue(values.get(2) instanceof PathNode t && t.getText().equals("foo.bar.baz"));
    }

    @Test
    public void Mapped_Path_Uses_Logical_Source_Ranges_With_Raw_Projection() {
        String raw = "foo&#46;bar";
        SourceMappedText input = SourceMappedText.decodedXml(raw, new Location(2, 4), XmlEntityDecoder.decode(raw));
        PathNode path = (PathNode)new InlineParser(input, null, Map.of()).parsePath();

        assertEquals("foo.bar", path.getText());
        assertEquals(new SourceInfo(2, 4, 2, 11), path.getSourceInfo());
        assertEquals(new SourceInfo(2, 4, 2, 7), path.getSegments().get(0).getSourceInfo());
        assertEquals(new SourceInfo(2, 8, 2, 11), path.getSegments().get(1).getSourceInfo());
        assertEquals(new SourceInfo(2, 4, 2, 15), path.getSourceInfo().toOriginal());
        assertEquals(
            new SourceInfo(2, 12, 2, 15),
            path.getSegments().get(1).getSourceInfo().toOriginal());
    }

    @Test
    public void Arithmetic_Expression_Uses_Java_Precedence() {
        ValueNode value = new InlineParser("a + b * c", "fx").parseExpression();

        BinaryOperatorNode add = assertInstanceOf(BinaryOperatorNode.class, value);
        assertEquals(BinaryOperator.ADD, add.getOperator());
        assertEquals("a", assertInstanceOf(PathNode.class, add.getLeft()).getText());

        BinaryOperatorNode multiply = assertInstanceOf(BinaryOperatorNode.class, add.getRight());
        assertEquals(BinaryOperator.MULTIPLY, multiply.getOperator());
        assertEquals("b", assertInstanceOf(PathNode.class, multiply.getLeft()).getText());
        assertEquals("c", assertInstanceOf(PathNode.class, multiply.getRight()).getText());
        assertEquals(new SourceInfo(0, 0, 0, 9), add.getSourceInfo());
        assertEquals(new SourceInfo(0, 2, 0, 3), add.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 6, 0, 7), multiply.getOperatorSourceInfo());
    }

    @Test
    public void Arithmetic_Expression_Is_Left_Associative() {
        BinaryOperatorNode subtract = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a-b-c", "fx").parseExpression());
        assertEquals(BinaryOperator.SUBTRACT, subtract.getOperator());
        assertInstanceOf(PathNode.class, subtract.getRight());
        assertEquals(BinaryOperator.SUBTRACT,
            assertInstanceOf(BinaryOperatorNode.class, subtract.getLeft()).getOperator());

        BinaryOperatorNode divide = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a/b/c", "fx").parseExpression());
        assertEquals(BinaryOperator.DIVIDE, divide.getOperator());
        assertEquals(BinaryOperator.DIVIDE,
            assertInstanceOf(BinaryOperatorNode.class, divide.getLeft()).getOperator());
    }

    @Test
    public void Compiled_Expression_Uses_The_Closed_Precedence_Order() {
        BinaryOperatorNode logicalOr = assertBinary(
            new InlineParser("ready || a + b < c * d && flag", "fx").parseExpression(),
            BinaryOperator.LOGICAL_OR);
        assertEquals("ready", assertInstanceOf(PathNode.class, logicalOr.getLeft()).getText());

        BinaryOperatorNode logicalAnd = assertBinary(logicalOr.getRight(), BinaryOperator.LOGICAL_AND);
        BinaryOperatorNode lessThan = assertBinary(logicalAnd.getLeft(), BinaryOperator.LESS_THAN);
        BinaryOperatorNode add = assertBinary(lessThan.getLeft(), BinaryOperator.ADD);
        BinaryOperatorNode multiply = assertBinary(lessThan.getRight(), BinaryOperator.MULTIPLY);

        assertEquals("a", assertInstanceOf(PathNode.class, add.getLeft()).getText());
        assertEquals("b", assertInstanceOf(PathNode.class, add.getRight()).getText());
        assertEquals("c", assertInstanceOf(PathNode.class, multiply.getLeft()).getText());
        assertEquals("d", assertInstanceOf(PathNode.class, multiply.getRight()).getText());
        assertEquals("flag", assertInstanceOf(PathNode.class, logicalAnd.getRight()).getText());
        assertEquals(new SourceInfo(0, 6, 0, 8), logicalOr.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 15, 0, 16), lessThan.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 23, 0, 25), logicalAnd.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 0, 0, 30), logicalOr.getSourceInfo());
    }

    @Test
    public void Equality_And_Relational_Operators_Are_Left_Associative() {
        BinaryOperatorNode identity = assertBinary(
            new InlineParser("a == b != c === d !== e", "fx").parseExpression(),
            BinaryOperator.IDENTITY_NOT_EQUAL);
        assertEquals(BinaryOperator.IDENTITY_EQUAL,
            assertBinary(identity.getLeft(), BinaryOperator.IDENTITY_EQUAL).getOperator());
        BinaryOperatorNode valueNotEqual = assertBinary(
            assertInstanceOf(BinaryOperatorNode.class, identity.getLeft()).getLeft(),
            BinaryOperator.VALUE_NOT_EQUAL);
        assertBinary(valueNotEqual.getLeft(), BinaryOperator.VALUE_EQUAL);

        BinaryOperatorNode relation = assertBinary(
            new InlineParser("a < b <= c > d >= e", "fx").parseExpression(),
            BinaryOperator.GREATER_THAN_OR_EQUAL);
        assertBinary(relation.getLeft(), BinaryOperator.GREATER_THAN);
        assertBinary(assertInstanceOf(BinaryOperatorNode.class, relation.getLeft()).getLeft(),
            BinaryOperator.LESS_THAN_OR_EQUAL);
        assertBinary(
            assertInstanceOf(BinaryOperatorNode.class,
                assertInstanceOf(BinaryOperatorNode.class, relation.getLeft()).getLeft()).getLeft(),
            BinaryOperator.LESS_THAN);
    }

    @Test
    public void Generalized_Unary_Operators_Are_Recursive() {
        UnaryOperatorNode not = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("!(a < b)", "fx").parseExpression());
        assertEquals(UnaryOperator.NOT, not.getOperator());
        ParenthesizedNode relation = assertInstanceOf(ParenthesizedNode.class, not.getOperand());
        assertBinary(relation.getOperand(), BinaryOperator.LESS_THAN);

        UnaryOperatorNode boolify = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("!!(value + offset)", "fx").parseExpression());
        assertEquals(UnaryOperator.BOOLIFY, boolify.getOperator());
        assertBinary(
            assertInstanceOf(ParenthesizedNode.class, boolify.getOperand()).getOperand(),
            BinaryOperator.ADD);

        UnaryOperatorNode nested = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("!-!!value", "fx").parseExpression());
        assertEquals(UnaryOperator.NOT, nested.getOperator());
        UnaryOperatorNode minus = assertInstanceOf(UnaryOperatorNode.class, nested.getOperand());
        assertEquals(UnaryOperator.MINUS, minus.getOperator());
        assertEquals(UnaryOperator.BOOLIFY,
            assertInstanceOf(UnaryOperatorNode.class, minus.getOperand()).getOperator());
    }

    @Test
    public void Compiled_String_Literal_Retains_Value_Lexeme_And_Span() {
        BinaryOperatorNode equality = assertBinary(
            new InlineParser("name == \"Sm\\\"ith\"", "fx").parseExpression(),
            BinaryOperator.VALUE_EQUAL);
        StringLiteralNode string = assertInstanceOf(StringLiteralNode.class, equality.getRight());

        assertEquals("Sm\"ith", string.getText());
        assertEquals("\"Sm\\\"ith\"", string.getLexeme());
        assertEquals(new SourceInfo(0, 8, 0, 17), string.getSourceInfo());
    }

    @Test
    public void Method_Type_Arguments_Are_Target_Anchored_And_Leave_Comparison_Tokens() {
        BinaryOperatorNode equality = assertBinary(
            new InlineParser("model.method<T>()==x", "fx").parseExpression(),
            BinaryOperator.VALUE_EQUAL);
        InvocationNode function = assertInstanceOf(InvocationNode.class, equality.getLeft());
        TextSegmentNode method = assertInstanceOf(
            TextSegmentNode.class, invocationPath(function).getSegments().get(1));

        assertEquals("method", method.getText());
        assertEquals("T", method.getTypeArguments().get(0).getText());
        assertEquals(new SourceInfo(0, 5, 0, 6), method.getSelectorSourceInfo());
        assertEquals(new SourceInfo(0, 6, 0, 15), method.getSourceInfo());
        assertEquals(new SourceInfo(0, 17, 0, 19), equality.getOperatorSourceInfo());

        BinaryOperatorNode greater = assertBinary(
            new InlineParser("model::method<T>(x)>=limit", "fx").parseExpression(),
            BinaryOperator.GREATER_THAN_OR_EQUAL);
        InvocationNode observableFunction = assertInstanceOf(InvocationNode.class, greater.getLeft());
        TextSegmentNode observableMethod = assertInstanceOf(
            TextSegmentNode.class, invocationPath(observableFunction).getSegments().get(1));
        assertTrue(observableMethod.isObservableSelector());
        assertEquals("T", observableMethod.getTypeArguments().get(0).getText());
    }

    @Test
    public void Method_Type_Arguments_Commit_Locally_And_Prefix_Positions_Are_Rejected() {
        MarkupException malformed = assertThrows(MarkupException.class,
            () -> new InlineParser("model.method<T,>(x)", "fx").parseExpression());
        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, malformed.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 15, 0, 16), malformed.getSourceInfo());

        assertInstanceOf(InvocationNode.class,
            new InlineParser("method<T>()", "fx").parseExpression());
        assertThrows(MarkupException.class,
            () -> new InlineParser("<T>method()", "fx").parseExpression());
        assertThrows(MarkupException.class,
            () -> new InlineParser("model.<T>method()", "fx").parseExpression());
        assertInstanceOf(InvocationNode.class,
            new InlineParser("::method()", "fx").parseExpression());
    }

    @Test
    public void Arithmetic_Expression_Supports_Grouping_And_Unary_Signs() {
        BinaryOperatorNode multiply = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("-(a + b) * +c", "fx").parseExpression());

        UnaryOperatorNode minus = assertInstanceOf(UnaryOperatorNode.class, multiply.getLeft());
        assertEquals(UnaryOperator.MINUS, minus.getOperator());
        ParenthesizedNode group = assertInstanceOf(ParenthesizedNode.class, minus.getOperand());
        assertEquals(BinaryOperator.ADD,
            assertInstanceOf(BinaryOperatorNode.class, group.getOperand()).getOperator());

        UnaryOperatorNode plus = assertInstanceOf(UnaryOperatorNode.class, multiply.getRight());
        assertEquals(UnaryOperator.PLUS, plus.getOperator());
    }

    @Test
    public void Arithmetic_Signs_Are_Structural_Without_Whitespace() {
        BinaryOperatorNode add = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a+1", "fx").parseExpression());
        assertEquals(BinaryOperator.ADD, add.getOperator());
        assertEquals("1", assertInstanceOf(NumberNode.class, add.getRight()).getText());

        BinaryOperatorNode multiply = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a*-1", "fx").parseExpression());
        UnaryOperatorNode negativeOne = assertInstanceOf(UnaryOperatorNode.class, multiply.getRight());
        assertEquals(UnaryOperator.MINUS, negativeOne.getOperator());
        assertEquals("1", assertInstanceOf(NumberNode.class, negativeOne.getOperand()).getText());

        UnaryOperatorNode negativePath = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("-a", "fx").parseExpression());
        assertEquals(UnaryOperator.MINUS, negativePath.getOperator());
        assertEquals("a", assertInstanceOf(PathNode.class, negativePath.getOperand()).getText());

        BinaryOperatorNode subtract = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a--b", "fx").parseExpression());
        assertEquals(BinaryOperator.SUBTRACT, subtract.getOperator());
        UnaryOperatorNode negativeB = assertInstanceOf(UnaryOperatorNode.class, subtract.getRight());
        assertEquals(UnaryOperator.MINUS, negativeB.getOperator());
        assertEquals("b", assertInstanceOf(PathNode.class, negativeB.getOperand()).getText());
        assertEquals(new SourceInfo(0, 1, 0, 2), subtract.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 2, 0, 3), negativeB.getOperatorSourceInfo());
    }

    @Test
    public void Arithmetic_Expression_Is_Accepted_In_Function_Arguments() {
        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("f(a * 2, b + 1)", "fx").parseExpression());

        assertEquals(2, function.getArguments().size());

        assertEquals(BinaryOperator.MULTIPLY, assertInstanceOf(
            BinaryOperatorNode.class, function.getArguments().get(0)).getOperator());

        assertEquals(BinaryOperator.ADD, assertInstanceOf(
            BinaryOperatorNode.class, function.getArguments().get(1)).getOperator());
    }

    @Test
    public void Invocation_Retains_One_Generic_Position_And_Punctuation_Spans() {
        InvocationNode invocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("java.lang.Type<T,W>(x,y+1)", "fx").parseExpression());

        PathNode target = invocationPath(invocation);
        TextSegmentNode terminal = assertInstanceOf(TextSegmentNode.class, target.getSegments().get(2));

        assertEquals("java.lang.Type<T,W>", target.getText());
        assertEquals("T", terminal.getTypeArguments().get(0).getText());
        assertEquals("W", terminal.getTypeArguments().get(1).getText());
        assertEquals(2, invocation.getArguments().size());
        assertBinary(invocation.getArguments().get(1), BinaryOperator.ADD);
        assertEquals(new SourceInfo(0, 14, 0, 19), terminal.getTypeArgumentsSourceInfo());
        assertEquals(new SourceInfo(0, 19, 0, 20), invocation.getOpenParenSourceInfo());
        assertEquals(new SourceInfo(0, 25, 0, 26), invocation.getCloseParenSourceInfo());
        assertEquals(new SourceInfo(0, 0, 0, 26), invocation.getSourceInfo());
        assertEquals("java.lang.Type<T,W>(x,y+1)", invocation.getText());
    }

    @Test
    public void Qualified_Invocation_Retains_The_Receiver_Path() {
        InvocationNode invocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("outer.Inner<T,W>(x)", "fx").parseExpression());

        PathNode target = invocationPath(invocation);
        TextSegmentNode terminal = assertInstanceOf(TextSegmentNode.class, target.getSegments().get(1));

        assertEquals("outer", target.getSegments().get(0).getText());
        assertEquals("Inner", terminal.getText());
        assertEquals("T", terminal.getTypeArguments().get(0).getText());
        assertEquals("W", terminal.getTypeArguments().get(1).getText());
        assertEquals(new SourceInfo(0, 5, 0, 6), terminal.getSelectorSourceInfo());
        assertEquals(new SourceInfo(0, 0, 0, 19), invocation.getSourceInfo());
        assertEquals("outer.Inner<T,W>(x)", invocation.getText());
    }

    @Test
    public void Selected_Invocation_Is_Repeatable_Postfix() {
        InvocationNode nested = assertInstanceOf(InvocationNode.class,
            new InlineParser("factory().Inner().Nested(value)", "fx").parseExpression());

        SelectedMemberNode nestedTarget = assertInstanceOf(SelectedMemberNode.class, nested.getTarget());
        assertEquals("Nested", nestedTarget.getMember().getText());

        InvocationNode inner = assertInstanceOf(InvocationNode.class, nestedTarget.getReceiver());
        SelectedMemberNode innerTarget = assertInstanceOf(SelectedMemberNode.class, inner.getTarget());
        assertEquals("Inner", innerTarget.getMember().getText());
        assertInstanceOf(InvocationNode.class, innerTarget.getReceiver());

        InvocationNode grouped = assertInstanceOf(InvocationNode.class,
            new InlineParser("(outer).Inner()", "fx").parseExpression());
        assertInstanceOf(ParenthesizedNode.class,
            assertInstanceOf(SelectedMemberNode.class, grouped.getTarget()).getReceiver());

        InvocationNode afterInvocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("Outer().Inner()", "fx").parseExpression());
        assertInstanceOf(InvocationNode.class,
            assertInstanceOf(SelectedMemberNode.class, afterInvocation.getTarget()).getReceiver());
    }

    @Test
    public void Invocations_Participate_In_Operator_Trees() {
        BinaryOperatorNode equality = assertBinary(
            new InlineParser("Box<T>(x)==outer.Inner(y)", "fx").parseExpression(),
            BinaryOperator.VALUE_EQUAL);
        InvocationNode leading = assertInstanceOf(InvocationNode.class, equality.getLeft());
        InvocationNode qualified = assertInstanceOf(InvocationNode.class, equality.getRight());

        assertEquals("T", ((TextSegmentNode)invocationPath(leading)
            .getSegments().get(0)).getTypeArguments().get(0).getText());
        assertEquals("outer.Inner", invocationPath(qualified).getText());
    }

    @Test
    public void Primitive_And_Malformed_Generic_Lists_Are_Handled_Locally() {
        InvocationNode primitiveArgument = assertInstanceOf(InvocationNode.class,
            new InlineParser("Type<int>(x)", "fx").parseExpression());

        assertEquals("int", invocationPath(primitiveArgument).getSegments().get(0).getTypeArguments().get(0).getText());

        for (String expression : new String[] {
                "Type<>(x)",
                "outer.Inner<>(x)",
                "Type<W,>(x)",
                "outer.Inner<T,>(x)"}) {
            assertThrows(MarkupException.class,
                () -> new InlineParser(expression, "fx").parseExpression(), expression);
        }
    }

    @Test
    public void Malformed_Invocation_Forms_Report_Local_Spans() {
        assertExpressionError("Type<>(x)", ErrorCode.EXPECTED_IDENTIFIER, 5, 6);
        assertExpressionError("outer.Inner<>(x)", ErrorCode.EXPECTED_IDENTIFIER, 12, 13);
        assertExpressionError("Type<W,>(x)", ErrorCode.EXPECTED_IDENTIFIER, 7, 8);
        assertExpressionError("outer.Inner<T,>(x)", ErrorCode.EXPECTED_IDENTIFIER, 14, 15);

        assertThrows(MarkupException.class, () -> new InlineParser("new Type()", "fx").parseExpression());
        assertThrows(MarkupException.class, () -> new InlineParser("outer.new Inner()", "fx").parseExpression());
    }

    @Test
    public void Call_Syntax_Remains_Neutral() {
        assertInstanceOf(InvocationNode.class, new InlineParser("Type(x)", "fx").parseExpression());

        InvocationNode genericInvocation = assertInstanceOf(InvocationNode.class,
            new InlineParser(":context.Type<T>(x)", "fx").parseExpression());

        assertEquals("T", invocationPath(genericInvocation).getSegments().get(0).getTypeArguments().get(0).getText());
        assertInstanceOf(PathNode.class, new InlineParser("Type<T>", "fx").parseExpression());

        assertThrows(MarkupException.class,
            () -> new InlineParser("outer.new", "fx").parseExpression());
        assertThrows(MarkupException.class,
            () -> new InlineParser("outer.new package.Inner()", "fx").parseExpression());
    }

    @Test
    public void Invocation_Arguments_Accept_Full_Expressions_Or_One_Whole_Object() {
        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("f(a || b && c, {Ext value=x}, Type(y))", "fx").parseExpression());

        assertEquals(3, function.getArguments().size());
        assertBinary(function.getArguments().get(0), BinaryOperator.LOGICAL_OR);
        assertInstanceOf(ObjectNode.class, function.getArguments().get(1));
        assertInstanceOf(InvocationNode.class, function.getArguments().get(2));

        MarkupException objectOperator = assertThrows(MarkupException.class,
            () -> new InlineParser("f({Ext} + value)", "fx").parseExpression());
        assertEquals(new SourceInfo(0, 8, 0, 9), objectOperator.getSourceInfo());

        assertThrows(MarkupException.class,
            () -> new InlineParser("f(..value)", "fx").parseExpression());
        assertThrows(MarkupException.class,
            () -> new InlineParser("(..value)", "fx").parseExpression());
    }

    @Test
    public void Invocation_Commits_Inside_Ordinary_Markup_Content() {
        ObjectNode object = new InlineParser("{MyExt Type<T,W>(value)}", "fx").parseObject();
        InvocationNode leading = assertInstanceOf(InvocationNode.class, object.getChildren().get(0));
        assertEquals(2, invocationPath(leading).getSegments().get(0).getTypeArguments().size());

        object = new InlineParser("{MyExt outer.Inner<T,W>(value)}", "fx").parseObject();
        InvocationNode qualified = assertInstanceOf(InvocationNode.class, object.getChildren().get(0));
        assertEquals("outer", invocationPath(qualified).getSegments().get(0).getText());
    }

    @Test
    public void Push_Prefix_And_Relational_Greater_Than_Remain_Positional() {
        ObjectNode push = new InlineParser(">{foo}", "fx").parseObject();
        assertEquals(Intrinsics.PUSH.getName(), push.getType().getName());

        assertBinary(
            new InlineParser("a > b", "fx").parseExpression(),
            BinaryOperator.GREATER_THAN);
    }

    @Test
    public void Compiled_Keyword_Literals_Are_Distinct_From_Text_And_Paths() {
        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("f(true, false, null, 'true', :element.true)", "fx").parseExpression());

        assertEquals(LiteralKeywordNode.Kind.TRUE, assertInstanceOf(
            LiteralKeywordNode.class, function.getArguments().get(0)).getKind());

        assertEquals(LiteralKeywordNode.Kind.FALSE, assertInstanceOf(
            LiteralKeywordNode.class, function.getArguments().get(1)).getKind());

        assertEquals(LiteralKeywordNode.Kind.NULL, assertInstanceOf(
            LiteralKeywordNode.class, function.getArguments().get(2)).getKind());

        assertEquals("true", assertInstanceOf(TextNode.class, function.getArguments().get(3)).getText());

        PathNode path = assertInstanceOf(PathNode.class, function.getArguments().get(4));
        assertSame(ContextSelector.ELEMENT, path.getContextSelector().getSelector());
        assertEquals("true", path.getSegments().get(0).getText());
    }

    @Test
    public void Zero_Argument_Function_Is_An_Arithmetic_Primary() {
        BinaryOperatorNode add = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("next() + next()", "fx").parseExpression());

        assertTrue(assertInstanceOf(InvocationNode.class, add.getLeft()).getArguments().isEmpty());
        assertTrue(assertInstanceOf(InvocationNode.class, add.getRight()).getArguments().isEmpty());
    }

    @Test
    public void Slash_Is_Always_Division() {
        BinaryOperatorNode division = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("width/height", "fx").parseExpression());
        assertNull(assertInstanceOf(PathNode.class, division.getLeft()).getContextSelector());
        assertNull(assertInstanceOf(PathNode.class, division.getRight()).getContextSelector());

        BinaryOperatorNode ordinaryNames = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("parent/width", "fx").parseExpression());
        assertEquals("parent", assertInstanceOf(PathNode.class, ordinaryNames.getLeft()).getText());
        assertEquals("width", assertInstanceOf(PathNode.class, ordinaryNames.getRight()).getText());
    }

    @Test
    public void NonExpression_Negative_Number_Remains_NumberNode() {
        PropertyNode property = new InlineParser("{foo value=-1}", "fx").parseObject().getProperties().get(0);
        assertEquals("-1", assertInstanceOf(NumberNode.class, property.getValues().get(0)).getText());
    }

    @Test
    public void NonExpression_Hyphenated_Text_Is_Preserved() {
        PropertyNode property = new InlineParser("{foo value=foo-bar}", "fx").parseObject().getProperties().get(0);
        assertEquals("foo-bar", assertInstanceOf(TextNode.class, property.getValues().get(0)).getText());
    }

    @Test
    public void Arithmetic_Expression_Reports_Unmatched_Parenthesis() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new InlineParser("(a + b", "fx").parseExpression());
        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Arithmetic_Expression_Reports_Missing_Operand() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new InlineParser("a +", "fx").parseExpression());
        assertEquals(ErrorCode.UNEXPECTED_END_OF_FILE, ex.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 3, 0, 3), ex.getSourceInfo());

        ex = assertThrows(MarkupException.class, () -> new InlineParser("* a", "fx").parseExpression());
        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 0, 0, 1), ex.getSourceInfo());
    }

    private BinaryOperatorNode assertBinary(ValueNode value, BinaryOperator operator) {
        BinaryOperatorNode binary = assertInstanceOf(BinaryOperatorNode.class, value);
        assertEquals(operator, binary.getOperator());
        return binary;
    }

    private PathNode invocationPath(InvocationNode invocation) {
        return assertInstanceOf(PathNode.class, invocation.getTarget());
    }

    private void assertExpressionError(
            String source, ErrorCode errorCode, int startColumn, int endColumn) {
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> new InlineParser(source, "fx").parseExpression(),
            source);

        assertEquals(errorCode, exception.getDiagnostic().getCode(), source);
        assertEquals(new SourceInfo(0, startColumn, 0, endColumn), exception.getSourceInfo(), source);
    }
}
