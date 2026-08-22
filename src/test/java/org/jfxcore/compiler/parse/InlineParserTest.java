// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.ContentSelectionNode;
import org.jfxcore.compiler.ast.AttributeValueNode;
import org.jfxcore.compiler.ast.InlineArgumentSequenceNode;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.AttachedSegmentNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.BinaryOperatorNode;
import org.jfxcore.compiler.ast.text.ContextSelector;
import org.jfxcore.compiler.ast.text.ContextSelectorNode;
import org.jfxcore.compiler.ast.text.InvocationNode;
import org.jfxcore.compiler.ast.text.LiteralKeywordNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.ParenthesizedNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.SelectedMemberNode;
import org.jfxcore.compiler.ast.text.StringLiteralNode;
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
    public void Malformed_Content_Selection_And_Numeric_Path_Segment_Are_Expression_Errors() {
        MarkupException content = assertThrows(
            MarkupException.class, () -> new InlineParser("$...list", "fx").parseObjectStrict());
        assertEquals(ErrorCode.INVALID_EXPRESSION, content.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 1, 0, 8), content.getSourceInfo());

        MarkupException path = assertThrows(
            MarkupException.class,
            () -> new InlineParser("${:parent.0123}", "fx").parseObjectStrict());
        assertEquals(ErrorCode.UNEXPECTED_EXPRESSION, path.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 2, 0, 14), path.getSourceInfo());
    }

    @Test
    public void Parse_Simple_Identifier() {
        var obj = new InlineParser("{foo}", "fx").parseObjectStrict();
        assertFalse(obj.getType().isIntrinsic());
        assertEquals("foo", obj.getType().getName());
        assertEquals("foo", obj.getType().getMarkupName());
    }

    @Test
    public void Parse_Fully_Qualified_Identifier() {
        var obj = new InlineParser("{foo.bar.baz}", "fx").parseObjectStrict();
        assertFalse(obj.getType().isIntrinsic());
        assertEquals("foo.bar.baz", obj.getType().getName());
        assertEquals("foo.bar.baz", obj.getType().getMarkupName());
    }

    @Test
    public void Parse_Namespace_With_Identifier() {
        var obj = new InlineParser("{fx:foo}", "fx").parseObjectStrict();
        assertTrue(obj.getType().isIntrinsic());
        assertEquals("foo", obj.getType().getName());
        assertEquals("fx:foo", obj.getType().getMarkupName());
    }

    @Test
    public void Parse_Namespace_With_Fully_Qualified_Identifier_Fails() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("{fx:foo.bar.baz}", "fx").parseObjectStrict());
        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Value_Must_Start_With_OpenCurly() {
        String markup = """
            foo
        """;

        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser(markup, "fx").parseObjectStrict());

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

        ObjectNode root = new InlineParser(markup, "fx").parseObjectStrict();

        assertEquals("GridPane", root.getType().getName());
        assertEquals(4, root.getProperties().size());
        assertEquals(1, root.getChildren().size());
        assertEquals("content text", ((LiteralValueNode)root.getChildren().get(0)).getText());

        assertEquals("fx:id", root.getProperties().get(0).getMarkupName());
        assertEquals("id", root.getProperties().get(0).getName());
        assertTrue(root.getProperties().get(0).isIntrinsic());
        assertEquals(1, root.getProperties().get(0).getValues().size());
        assertEquals("pane0", ((LiteralValueNode)root.getProperties().get(0).getValues().get(0)).getText());

        LiteralValueNode node = ((LiteralValueNode)root.getProperties().get(1).getValues().get(0));
        assertEquals("list", root.getProperties().get(1).getName());
        assertEquals(1, root.getProperties().get(1).getValues().size());
        assertEquals("1 2   3    4", node.getText());

        InlineArgumentSequenceNode list = ((InlineArgumentSequenceNode)root.getProperties().get(2).getValues().get(0));
        assertEquals("composite", root.getProperties().get(2).getName());
        assertEquals(1, root.getProperties().get(2).getValues().size());
        assertEquals(2, list.getValues().size());
        assertEquals("foo bar", ((LiteralValueNode)(list.getValues().get(0))).getText());
        assertEquals("baz(123, 5.0, \"qux quux\")", ((LiteralValueNode)(list.getValues().get(1))).getText());

        assertEquals("text", root.getProperties().get(3).getName());
        assertEquals(1, root.getProperties().get(3).getValues().size());
        assertEquals("foo, bar; baz", ((LiteralValueNode)root.getProperties().get(3).getValues().get(0)).getText());
    }

    @Test
    public void Literal_Content_Adjacent_To_A_Nested_Object_Is_Rejected() {
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

        assertThrows(MarkupException.class, () -> new InlineParser(markup, "fx").parseObjectStrict());
    }

    @Test
    public void Unmatched_Curly_Braces_Throws() {
        String markup = """
            {GridPane
                foo = {bar
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObjectStrict());

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains("}"));
    }

    @Test
    public void Unmatched_Parens_Throws() {
        String markup = """
            {foo bar(baz(qux)
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObjectStrict());

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains(")"));
    }

    @Test
    public void Unmatched_Parens_Are_Reported_At_Mapped_End_Of_Input() {
        String raw = "{foo bar&#40;baz";
        SourceMappedText input = SourceMappedText.decodedXml(
            raw, new Location(2, 4), XmlEntityDecoder.decode(raw));

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(input, "fx", Map.of()).parseObjectStrict());

        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertEquals(new SourceInfo(2, 16), ex.getSourceInfo());
        assertEquals(new SourceInfo(2, 20), ex.getOriginalSourceInfo());
    }

    @Test
    public void Invocation_Shaped_Ordinary_Content_Remains_Literal() {
        String markup = """
            {foo bar(
                baz , qux
            )}
        """;

        var objectNode = new InlineParser(markup, "fx").parseObjectStrict();
        assertEquals(1, objectNode.getChildren().size());
        var literal = assertInstanceOf(LiteralValueNode.class, objectNode.getChildren().get(0));
        assertEquals("bar(\n        baz , qux\n    )", literal.getText());
    }

    @Test
    public void Prefix_Literal_Preserves_Interior_Source_Text_And_Excludes_Outer_Whitespace() {
        String source = "@    my    styles . css  ";
        ObjectNode object = new InlineParser(
            source, "fx", Map.of('@', "ClassPathResource")).parseObjectStrict();
        LiteralValueNode literal = assertInstanceOf(LiteralValueNode.class, object.getChildren().get(0));

        assertEquals("my    styles . css", literal.getText());
        assertEquals(
            new SourceInfo(0, source.indexOf("my"), 0, source.indexOf("css") + 3),
            literal.getSourceInfo());
    }

    @Test
    public void Brace_Style_Literal_Preserves_Interior_Source_Text() {
        ObjectNode object = new InlineParser(
            "{ClassPathResource    my    styles . css  }", "fx").parseObjectStrict();
        LiteralValueNode literal = assertInstanceOf(LiteralValueNode.class, object.getChildren().get(0));

        assertEquals("my    styles . css", literal.getText());
    }

    @Test
    public void Named_Property_Literal_Preserves_Interior_Source_Text() {
        ObjectNode object = new InlineParser(
            "{Extension value =   my    value  }", "fx").parseObjectStrict();
        LiteralValueNode literal = assertInstanceOf(
            LiteralValueNode.class, object.getProperty("value").getValues().get(0));

        assertEquals("my    value", literal.getText());
    }

    @Test
    public void Standalone_Quoted_Literal_Still_Unquotes_And_Unescapes_Its_Value() {
        ObjectNode object = new InlineParser(
            "{Extension 'my\\tvalue'}", "fx").parseObjectStrict();
        LiteralValueNode literal = assertInstanceOf(LiteralValueNode.class, object.getChildren().get(0));

        assertEquals("my\tvalue", literal.getText());
    }

    @Test
    public void Inline_Literal_Omits_Block_Comments_And_Preserves_Surrounding_Whitespace() {
        ObjectNode object = new InlineParser(
            "{Extension foo /* explanation */ bar}", "fx").parseObjectStrict();
        LiteralValueNode literal = assertInstanceOf(LiteralValueNode.class, object.getChildren().get(0));

        assertEquals("foo  bar", literal.getText());
    }

    @Test
    public void Top_Level_Separator_And_Its_Layout_Remain_Outside_Literal_Values() {
        ObjectNode object = new InlineParser("{Extension foo   , bar}", "fx").parseObjectStrict();
        InlineArgumentSequenceNode sequence = assertInstanceOf(
            InlineArgumentSequenceNode.class, object.getChildren().get(0));
        LiteralValueNode first = assertInstanceOf(LiteralValueNode.class, sequence.getValues().get(0));
        LiteralValueNode second = assertInstanceOf(LiteralValueNode.class, sequence.getValues().get(1));

        assertEquals("foo", first.getText());
        assertEquals("bar", second.getText());
        assertEquals(new SourceInfo(0, 11, 0, 14), first.getSourceInfo());
    }

    @Test
    public void Empty_Property_Value_Throws() {
        String markup = """
            {GridPane
                style=
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObjectStrict());

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

        ObjectNode root = new InlineParser(markup, "fx").parseObjectStrict();
        assertEquals(0, root.getChildren().size());
        assertEquals(
            "foo /* not a comment */ bar",
            ((LiteralValueNode)root.getProperties().get(0).getValues().get(0)).getText());
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

        ObjectNode root = new InlineParser(markup, "fx").parseObjectStrict();
        assertEquals(0, root.getChildren().size());
        assertEquals(
            "foo // not a comment",
            ((LiteralValueNode)root.getProperties().get(0).getValues().get(0)).getText());
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

        ObjectNode root = new InlineParser(markup, "fx").parseObjectStrict();
        assertEquals("foo\bbar", ((LiteralValueNode)root.getProperties().get(0).getValues().get(0)).getText());
        assertEquals("foo\tbar", ((LiteralValueNode)root.getProperties().get(1).getValues().get(0)).getText());
        assertEquals("foo\nbar", ((LiteralValueNode)root.getProperties().get(2).getValues().get(0)).getText());
        assertEquals("foo\fbar", ((LiteralValueNode)root.getProperties().get(3).getValues().get(0)).getText());
        assertEquals("foo\rbar", ((LiteralValueNode)root.getProperties().get(4).getValues().get(0)).getText());
        assertEquals("foo\"bar", ((LiteralValueNode)root.getProperties().get(5).getValues().get(0)).getText());
        assertEquals("foo'bar", ((LiteralValueNode)root.getProperties().get(6).getValues().get(0)).getText());
        assertEquals("\u2661", ((LiteralValueNode)root.getProperties().get(7).getValues().get(0)).getText());
        assertEquals("\\u2661", ((LiteralValueNode)root.getProperties().get(8).getValues().get(0)).getText());
        assertEquals("\\\u2661", ((LiteralValueNode)root.getProperties().get(9).getValues().get(0)).getText());
    }

    @Test
    public void Missing_Delimiter_Between_Properties_Fails() {
        String markup = """
            {Pane
                fx:id=pane0 foo={Pane}
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new InlineParser(markup, "fx").parseObjectStrict());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Property_And_Content_On_Same_Line() {
        String markup = """
            { GridPane fx:bar=pane0 foo; { GridPane fx:bar=pane0 } }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObjectStrict();
        assertEquals(1, root.getProperties().size());
        assertTrue(root.getProperties().get(0).isIntrinsic());
        assertEquals("bar", root.getProperties().get(0).getName());
        assertEquals("pane0 foo", ((LiteralValueNode)root.getProperties().get(0).getValues().get(0)).getText());
        assertEquals(1, root.getChildren().size());
        assertEquals("GridPane", ((ObjectNode)root.getChildren().get(0)).getType().getName());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void Binding_Syntax_With_Parent_Selector() {
        String markup = """
            {GridPane
                {VBox
                    prefWidth=${:parent<GridPane>(1).prefWidth}
                }
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObjectStrict();
        PropertyNode prefWidth = ((ObjectNode)root.getChildren().get(0)).findProperty("prefWidth");
        PathNode path = assertInstanceOf(
            PathNode.class, ((ObjectNode)prefWidth.getValues().get(0)).getChildren().get(0));
        assertSame(ContextSelector.PARENT, path.getContextSelector().getSelector());
        assertEquals("GridPane", path.getContextSelector().getSearchType().getName());
        assertEquals("1", path.getContextSelector().getLevel().getText());
        assertEquals("prefWidth", path.getSegments().get(0).getText());
    }

    @Test
    public void Canonical_Context_Selectors_Are_Terminal_Or_Selected_Primaries() {
        for (ContextSelector selector : ContextSelector.values()) {
            String source = ":" + selector.getText();
            PathNode path = assertInstanceOf(PathNode.class, new InlineParser(source, "fx").parseExpressionStrict());
            ContextSelectorNode context = path.getContextSelector();

            assertNotNull(context);
            assertTrue(path.getSegments().isEmpty());
            assertEquals(source, path.format());
            assertSame(selector, context.getSelector());
            assertEquals(new SourceInfo(0, 0, 0, 1), context.getColonSourceInfo());
            assertEquals(new SourceInfo(0, 1, 0, source.length()), context.getSelectorSourceInfo());
            assertEquals(new SourceInfo(0, 0, 0, source.length()), context.getSourceInfo());
            assertEquals(context.getSourceInfo(), path.getSourceInfo());
        }

        PathNode normal = assertInstanceOf(PathNode.class, new InlineParser(":parent.width", "fx").parseExpressionStrict());
        assertEquals("width", normal.getSegments().get(0).getText());
        assertFalse(normal.getSegments().get(0).isObservableSelector());

        PathNode observable = assertInstanceOf(PathNode.class, new InlineParser(":element::value", "fx").parseExpressionStrict());
        assertEquals("value", observable.getSegments().get(0).getText());
        assertTrue(observable.getSegments().get(0).isObservableSelector());
    }

    @Test
    public void Leading_Observable_Selector_Uses_The_Implicit_Context() {
        PathNode path = assertInstanceOf(PathNode.class,
            new InlineParser("::foo<T>::bar", "fx").parseExpressionStrict());

        assertNull(path.getContextSelector());
        assertEquals("::foo<T>::bar", path.format());
        assertEquals(2, path.getSegments().size());
        assertTrue(path.getSegments().get(0).isObservableSelector());
        assertEquals(new SourceInfo(0, 0, 0, 2), path.getSegments().get(0).getSelectorSourceInfo());
        assertEquals("T", path.getSegments().get(0).getTypeArguments().get(0).format());
        assertTrue(path.getSegments().get(1).isObservableSelector());

        PathNode ordinarySuffix = assertInstanceOf(PathNode.class,
            new InlineParser("::foo.bar", "fx").parseExpressionStrict());
        assertTrue(ordinarySuffix.getSegments().get(0).isObservableSelector());
        assertFalse(ordinarySuffix.getSegments().get(1).isObservableSelector());

        InvocationNode invocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("::foo<T>()", "fx").parseExpressionStrict());
        assertEquals("::foo<T>()", invocation.format());

        PathNode attached = assertInstanceOf(PathNode.class,
            new InlineParser("::(Owner.property)", "fx").parseExpressionStrict());
        assertNull(attached.getContextSelector());
        assertTrue(assertInstanceOf(
            AttachedSegmentNode.class, attached.getSegments().get(0)).isObservableSelector());
        assertEquals("::(Owner.property)", attached.format());

        PathNode contextAttached = assertInstanceOf(PathNode.class,
            new InlineParser(":context.(Owner.property)", "fx").parseExpressionStrict());
        assertSame(ContextSelector.CONTEXT, contextAttached.getContextSelector().getSelector());
        assertFalse(contextAttached.getSegments().get(0).isObservableSelector());
    }

    @Test
    public void Parent_Arguments_Retain_Their_Distinct_Children_And_Punctuation() {
        String source = ":parent<javafx.scene.layout.Pane>(-12).width";
        PathNode path = assertInstanceOf(PathNode.class, new InlineParser(source, "fx").parseExpressionStrict());
        ContextSelectorNode context = path.getContextSelector();
        int openAngle = source.indexOf('<');
        int closeAngle = source.indexOf('>');
        int openParen = source.indexOf('(');
        int closeParen = source.indexOf(')');

        assertNotNull(context);
        assertEquals("javafx.scene.layout.Pane", context.getSearchType().getName());
        assertEquals("-12", context.getLevel().getText());
        assertEquals(new SourceInfo(0, openAngle, 0, openAngle + 1), context.getOpenAngleSourceInfo());
        assertEquals(new SourceInfo(0, closeAngle, 0, closeAngle + 1), context.getCloseAngleSourceInfo());
        assertEquals(new SourceInfo(0, openParen, 0, openParen + 1), context.getOpenParenSourceInfo());
        assertEquals(new SourceInfo(0, closeParen, 0, closeParen + 1), context.getCloseParenSourceInfo());
        assertEquals(":parent<javafx.scene.layout.Pane>(-12).width", path.format());

        ContextSelectorNode depthOnly = assertInstanceOf(PathNode.class,
            new InlineParser(":parent(2)", "fx").parseExpressionStrict()).getContextSelector();
        assertNotNull(depthOnly);
        assertNull(depthOnly.getSearchType());
        assertEquals("2", depthOnly.getLevel().getText());
        assertNull(depthOnly.getOpenAngleSourceInfo());
        assertNull(depthOnly.getCloseAngleSourceInfo());

        ContextSelectorNode typeOnly = assertInstanceOf(PathNode.class,
            new InlineParser(":parent<Pane>", "fx").parseExpressionStrict()).getContextSelector();
        assertNotNull(typeOnly);
        assertEquals("Pane", typeOnly.getSearchType().getName());
        assertNull(typeOnly.getLevel());
        assertNull(typeOnly.getOpenParenSourceInfo());
        assertNull(typeOnly.getCloseParenSourceInfo());

        ContextSelectorNode typedDepth = assertInstanceOf(PathNode.class,
            new InlineParser(":parent<Pane>(+3)", "fx").parseExpressionStrict()).getContextSelector();
        assertNotNull(typedDepth);
        assertEquals("+3", typedDepth.getLevel().getText());
        assertEquals("parent<Pane>(+3)", typedDepth.format());
    }

    @Test
    public void Invalid_Or_Unsupported_Context_Forms_Are_Rejected_Locally() {
        for (String source : new String[] {
                ":self",
                ":context(1)",
                ":element(1)",
                ":root(1)",
                ":parent()",
                ":parent(Pane)",
                ":parent(Pane, 1)",
                ":parent(1, Pane)",
                ":parent<>",
                ":parent<Pane, VBox>",
                ":parent<Pane>()",
                ":parent<Pane>(1, 2)",
                ":parent(1.5)",
                "::",
                ":::foo",
                "::()",
                ".foo"}) {
            assertThrows(MarkupException.class, () -> new InlineParser(source, "fx").parseExpressionStrict(), source);
        }
    }

    @Test
    public void Malformed_Context_And_Callable_Forms_Report_Local_Spans() {
        assertExpressionError(":parent()", ErrorCode.UNEXPECTED_TOKEN, 8, 9);
        assertExpressionError(":parent(Pane)", ErrorCode.UNEXPECTED_TOKEN, 8, 12);
        assertExpressionError(":parent(Pane, 1)", ErrorCode.UNEXPECTED_TOKEN, 8, 12);
        assertExpressionError(":parent(1, Pane)", ErrorCode.EXPECTED_TOKEN, 9, 10);
        assertExpressionError(":parent<>", ErrorCode.EXPECTED_IDENTIFIER, 8, 9);
        assertExpressionError(":parent<Pane, VBox>", ErrorCode.EXPECTED_TOKEN, 12, 13);
        assertExpressionError(":parent<Pane>()", ErrorCode.UNEXPECTED_TOKEN, 14, 15);
        assertExpressionError(":parent<Pane>(1, 2)", ErrorCode.EXPECTED_TOKEN, 15, 16);
        assertExpressionError(":parent(1.5)", ErrorCode.UNEXPECTED_TOKEN, 8, 11);
        assertExpressionError(":::member", ErrorCode.EXPECTED_IDENTIFIER, 2, 3);
        assertExpressionError(":context()", ErrorCode.UNEXPECTED_TOKEN, 8, 9);
        assertExpressionError(":element()", ErrorCode.UNEXPECTED_TOKEN, 8, 9);
        assertExpressionError(":root()", ErrorCode.UNEXPECTED_TOKEN, 5, 6);
        assertExpressionError("pane.(GridPane.rowIndex)()", ErrorCode.UNEXPECTED_TOKEN, 24, 25);
    }

    @Test
    public void Context_Primaries_Participate_In_Relations_And_Qualified_Construction() {
        BinaryOperatorNode bareRelation = assertBinary(
            new InlineParser("(:parent) < owner", "fx").parseExpressionStrict(),
            BinaryOperator.LESS_THAN);
        ParenthesizedNode groupedParent = assertInstanceOf(ParenthesizedNode.class, bareRelation.getLeft());
        assertNull(assertInstanceOf(
            PathNode.class, groupedParent.getOperand()).getContextSelector().getSearchType());

        BinaryOperatorNode relation = assertBinary(
            new InlineParser(":parent<Pane> < owner", "fx").parseExpressionStrict(),
            BinaryOperator.LESS_THAN);
        PathNode parent = assertInstanceOf(PathNode.class, relation.getLeft());
        assertTrue(parent.getSegments().isEmpty());
        assertEquals("Pane", parent.getContextSelector().getSearchType().getName());

        InvocationNode construction = assertInstanceOf(InvocationNode.class,
            new InlineParser(":parent.Inner(value)", "fx").parseExpressionStrict());
        PathNode constructionTarget = invocationPath(construction);
        assertSame(ContextSelector.PARENT, constructionTarget.getContextSelector().getSelector());
        assertEquals("Inner", constructionTarget.getSegments().get(0).getText());

        InvocationNode method = assertInstanceOf(InvocationNode.class,
            new InlineParser(":parent.method<T>(value)", "fx").parseExpressionStrict());
        assertEquals("T", invocationPath(method).getSegments().get(0).getTypeArguments().get(0).format());
    }

    @Test
    public void This_And_Context_Names_Are_Ordinary_Identifiers() {
        PathNode ordinaryThis = assertInstanceOf(PathNode.class, new InlineParser("this", "fx").parseExpressionStrict());
        assertNull(ordinaryThis.getContextSelector());
        assertEquals("this", ordinaryThis.format());

        PathNode observable = assertInstanceOf(PathNode.class, new InlineParser("this::value", "fx").parseExpressionStrict());
        assertTrue(observable.getSegments().get(1).isObservableSelector());

        for (String name : new String[] {"context", "element", "root", "self", "parent", "item"}) {
            PathNode ordinary = assertInstanceOf(PathNode.class, new InlineParser(name + ".value", "fx").parseExpressionStrict());
            assertNull(ordinary.getContextSelector(), name);
            assertEquals(name, ordinary.getSegments().get(0).getText(), name);
        }

        PathNode selectedThis = assertInstanceOf(PathNode.class, new InlineParser("model.this", "fx").parseExpressionStrict());
        assertEquals("this", selectedThis.getSegments().get(1).getText());
        assertInstanceOf(InvocationNode.class, new InlineParser("this()", "fx").parseExpressionStrict());
    }

    @Test
    public void Attached_Property_Uses_Its_Dedicated_Restricted_Segment() {
        String source = "pane.(javafx.scene.layout.GridPane.rowIndex).value";
        PathNode path = assertInstanceOf(PathNode.class, new InlineParser(source, "fx").parseExpressionStrict());
        AttachedSegmentNode attached = assertInstanceOf(AttachedSegmentNode.class, path.getSegments().get(1));

        assertEquals("javafx.scene.layout.GridPane", attached.getDeclaringType().getName());
        assertEquals("rowIndex", attached.getPropertyName().getName());
        assertFalse(attached.isObservableSelector());
        assertEquals("value", path.getSegments().get(2).getText());
        assertEquals(source, path.format());

        PathNode observablePath = assertInstanceOf(PathNode.class,
            new InlineParser("pane::(GridPane.rowIndex)", "fx").parseExpressionStrict());
        assertTrue(assertInstanceOf(
            AttachedSegmentNode.class,
            observablePath.getSegments().get(1)).isObservableSelector());

        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("pane.(Owner.value).method()", "fx").parseExpressionStrict());
        assertInstanceOf(AttachedSegmentNode.class, invocationPath(function).getSegments().get(1));

        assertThrows(MarkupException.class,
            () -> new InlineParser("pane.(GridPane)", "fx").parseExpressionStrict());
        assertThrows(MarkupException.class,
            () -> new InlineParser("pane.(GridPane.rowIndex)()", "fx").parseExpressionStrict());
    }

    @Test
    public void Intrinsic_Namespace_Is_Detected_When_Intrinsic_Prefix_Is_Specified() {
        ObjectNode root = new InlineParser("{GridPane prefWidth=$foo}", "fx").parseObjectStrict();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());

        root = new InlineParser("{GridPane prefWidth={foo:Evaluate foo}}", "foo").parseObjectStrict();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());
    }

    @Test
    public void Invalid_Intrinsic_Namespace_Fails() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("{GridPane prefWidth={foo:Evaluate foo}}", "bar").parseObjectStrict());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Literal_Is_Parsed_As_Literal_Value() {
        ObjectNode root = new InlineParser("{Foo bar=true}", null).parseObjectStrict();
        var value = root.getProperty("bar").getValues().get(0);
        assertTrue(value instanceof LiteralValueNode);
        assertFalse(value instanceof LiteralKeywordNode);
    }

    @Test
    public void Ordinary_Number_Remains_A_Literal_Value() {
        ObjectNode root = new InlineParser("{Foo bar=5.0}", null).parseObjectStrict();
        LiteralValueNode value = assertInstanceOf(
            LiteralValueNode.class, root.getProperty("bar").getValues().get(0));
        assertEquals("5.0", value.getText());
    }

    @Test
    public void Content_After_CurlyBraces_Is_Not_Allowed() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("{Foo bar=5.0}, {baz}", null).parseObjectStrict());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void ListContent_With_Empty_Strings_Works_Correctly() {
        ObjectNode root = new InlineParser("{Foo '', 'baz', ''}", null).parseObjectStrict();
        InlineArgumentSequenceNode sequence = assertInstanceOf(
            InlineArgumentSequenceNode.class, root.getChildren().get(0));
        assertEquals(List.of("", "baz", ""), sequence.getValues().stream()
            .map(value -> assertInstanceOf(LiteralValueNode.class, value).getText()).toList());
    }

    @Test
    public void TypeWitness_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("$foo<String>()", null).parseObjectStrict();
        PathNode path = invocationPath((InvocationNode)root.getChildren().get(0));
        var segment = (TextSegmentNode)path.getSegments().get(0);
        assertEquals(1, segment.getTypeArguments().size());
        assertEquals("String", segment.getTypeArguments().get(0).format());
        assertEquals("foo", segment.getValue().getName());
    }

    @Test
    public void TypeWitnessList_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("$foo<j.l.String, Integer, j.l.Comparable<j.l.Double>>()", null).parseObjectStrict();
        PathNode path = invocationPath((InvocationNode)root.getChildren().get(0));
        assertEquals("foo", path.getSegments().get(0).getText());
        TextSegmentNode segment = (TextSegmentNode)path.getSegments().get(0);
        assertEquals(3, segment.getTypeArguments().size());
        assertEquals("j.l.String", segment.getTypeArguments().get(0).format());
        assertEquals("Integer", segment.getTypeArguments().get(1).format());
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
        ObjectNode root = new InlineParser("$foo<Foo>.bar<Bar>::baz<Baz<Double>>()", null).parseObjectStrict();
        var segments = invocationPath((InvocationNode)root.getChildren().get(0)).getSegments();
        assertEquals(3, segments.size());
        var segment1 = (TextSegmentNode)segments.get(0);
        assertEquals(1, segment1.getTypeArguments().size());
        assertEquals("Foo", segment1.getTypeArguments().get(0).format());
        assertEquals("foo", segment1.getValue().getName());
        assertFalse(segment1.isObservableSelector());
        var segment2 = (TextSegmentNode)segments.get(1);
        assertEquals(1, segment2.getTypeArguments().size());
        assertEquals("Bar", segment2.getTypeArguments().get(0).format());
        assertEquals("bar", segment2.getValue().getName());
        assertFalse(segment2.isObservableSelector());
        var segment3 = (TextSegmentNode)segments.get(2);
        assertEquals(1, segment3.getTypeArguments().size());
        assertEquals("Baz<Double>", segment3.getTypeArguments().get(0).format());
        assertEquals(1, segment3.getTypeArguments().get(0).getArguments().size());
        assertEquals("Double", segment3.getTypeArguments().get(0).getArguments().get(0).format());
        assertEquals("baz", segment3.getValue().getName());
        assertTrue(segment3.isObservableSelector());
    }

    @Test
    public void Missing_Close_Angle_Bracket_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () ->
            new InlineParser("$value<String,>()", null).parseObjectStrict());
        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void ParameterizedType_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("{Foo <Bar, Comparable<Baz>, java.lang.String>}", null).parseObjectStrict();
        LiteralValueNode text = (LiteralValueNode)root.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS).getValues().get(0);
        assertEquals(new SourceInfo(0, 1, 0, 45), root.getType().getSourceInfo());
        assertEquals("Bar,Comparable<Baz>,java.lang.String", text.getText());
    }

    @Test
    public void ParameterizedType_Whitespace_Between_Identifiers_Is_Retained() {
        ObjectNode root = new InlineParser("{Foo<Bar, Comparable<Foo   Bar Baz>, java.lang.String>}", null).parseObjectStrict();
        LiteralValueNode text = (LiteralValueNode)root.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS).getValues().get(0);
        assertEquals(new SourceInfo(0, 1, 0, 54), root.getType().getSourceInfo());
        assertEquals("Bar,Comparable<Foo Bar Baz>,java.lang.String", text.getText());
    }

    @Test
    public void Markup_Extension_Head_Owns_Type_Arguments_With_Or_Without_Whitespace() {
        for (String source : new String[] {"{MyExt<T> value}", "{MyExt <T> value}"}) {
            ObjectNode object = new InlineParser(source, "fx").parseObjectStrict();
            PropertyNode typeArguments = object.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS);

            assertNotNull(typeArguments, source);
            assertEquals("T", assertInstanceOf(LiteralValueNode.class, typeArguments.getValues().get(0)).getText(), source);
            assertEquals("value", assertInstanceOf(LiteralValueNode.class, object.getChildren().get(0)).getText(), source);
        }

        ObjectNode witnessedContent = new InlineParser("{MyExt value<T>}", "fx").parseObjectStrict();
        assertNull(witnessedContent.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS));
        LiteralValueNode value = assertInstanceOf(LiteralValueNode.class, witnessedContent.getChildren().get(0));
        assertEquals("value<T>", value.getText());
    }

    @Test
    public void SyntaxMapping_Cannot_Be_Parameterized() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("${<Foo>bar}", null).parseObjectStrict());

        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void Prefix_Syntax_Cannot_Be_Parameterized() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("%<String>foo", "fx", Map.of('%', "StaticResource")).parseObjectStrict());

        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, ex.getDiagnostic().getCode());
    }

    @Test
    public void Prefix_Syntax_Is_Expanded() {
        ObjectNode objectNode = new InlineParser(
            "%foo; formatArguments=bar, baz",
            "fx",
            Map.of('%', "StaticResource")).parseObjectStrict();

        assertEquals("StaticResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof LiteralValueNode n && n.getText().equals("foo"));
        assertEquals(1, objectNode.getProperties().size());
        assertEquals("formatArguments", objectNode.getProperties().get(0).getName());
    }

    @Test
    public void Prefix_Syntax_Allows_Whitespace_After_Prefix() {
        ObjectNode objectNode = new InlineParser("%   foo", "fx", Map.of('%', "StaticResource")).parseObjectStrict();
        assertEquals("StaticResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof LiteralValueNode n && n.getText().equals("foo"));
    }

    @Test
    public void Prefix_Syntax_Is_Expanded_Within_PropertyExpression() {
        PropertyNode property = new InlineParser(
            "{Test qux=% foo}",
            "fx",
            Map.of('%', "StaticResource")).parseObjectStrict().getProperties().get(0);

        assertEquals("qux", property.getName());
        ObjectNode objectNode = assertInstanceOf(ObjectNode.class, property.getValues().get(0));
        assertEquals("StaticResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof LiteralValueNode n && n.getText().equals("foo"));
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,Evaluate",
        "${foo.bar.baz},Observe",
        "#{foo.bar.baz},Synchronize"
    })
    public void Compact_Syntax_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObjectStrict();
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.format().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$::foo::bar::baz,Evaluate",
        "${::foo::bar::baz},Observe",
        ">{::foo::bar::baz},Push",
        "#{::foo::bar::baz},Synchronize"
    })
    public void Compact_Syntax_With_ObservableSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObjectStrict();
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n
            && n.format().equals("::foo::bar::baz"));
    }

    @Test
    public void Long_Form_Intrinsics_Accept_A_Leading_Observable_Selector() {
        for (String intrinsic : new String[] {"Evaluate", "Observe", "Push", "Synchronize"}) {
            ObjectNode objectNode = new InlineParser(
                "{fx:" + intrinsic + " ::foo}", "fx").parseObjectStrict();

            assertEquals(intrinsic, objectNode.getType().getName());
            PathNode path = assertInstanceOf(PathNode.class, objectNode.getChildren().get(0));
            assertEquals("::foo", path.format());
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
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObjectStrict();
        assertEquals(intrinsicName, objectNode.getType().getName());
        ContentSelectionNode content = assertInstanceOf(
            ContentSelectionNode.class, objectNode.getChildren().get(0));
        assertEquals("foo.bar.baz", assertInstanceOf(PathNode.class, content.getValue()).format());
    }

    @ParameterizedTest
    @CsvSource({
        "'$:parent<Pane>(1).foo.bar.baz',Evaluate",
        "'${:parent<Pane>(1).foo.bar.baz}',Observe",
        "'#{:parent<Pane>(1).foo.bar.baz}',Synchronize"
    })
    public void Compact_Syntax_With_ContextSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObjectStrict();
        assertEquals(objectNode.getType().getName(), intrinsicName);
        PathNode pathNode = (PathNode)objectNode.getChildren().get(0);
        assertEquals(3, pathNode.getSegments().size());
        assertEquals("foo", pathNode.getSegments().get(0).getText());
        assertEquals("bar", pathNode.getSegments().get(1).getText());
        assertEquals("baz", pathNode.getSegments().get(2).getText());
        assertSame(ContextSelector.PARENT, pathNode.getContextSelector().getSelector());
        assertEquals("Pane", pathNode.getContextSelector().getSearchType().getName());
        assertEquals("1", pathNode.getContextSelector().getLevel().getText());
    }

    @ParameterizedTest
    @CsvSource({
        "'$..:parent<Pane>(1).foo.bar.baz',Evaluate",
        "'${..:parent<Pane>(1).foo.bar.baz}',Observe",
        "'#{..:parent<Pane>(1).foo.bar.baz}',Synchronize"
    })
    public void Compact_Content_Syntax_With_ContextSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObjectStrict();
        assertEquals(objectNode.getType().getName(), intrinsicName);
        ContentSelectionNode content = assertInstanceOf(
            ContentSelectionNode.class, objectNode.getChildren().get(0));
        PathNode pathNode = assertInstanceOf(PathNode.class, content.getValue());
        assertEquals(3, pathNode.getSegments().size());
        assertEquals("foo", pathNode.getSegments().get(0).getText());
        assertEquals("bar", pathNode.getSegments().get(1).getText());
        assertEquals("baz", pathNode.getSegments().get(2).getText());
        assertSame(ContextSelector.PARENT, pathNode.getContextSelector().getSelector());
        assertEquals("Pane", pathNode.getContextSelector().getSearchType().getName());
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
        InlineArgumentSequenceNode list = (InlineArgumentSequenceNode)
            new InlineParser(input, "fx").parseObjectStrict().getChildren().get(0);
        assertEquals(2, list.getValues().size());
        assertEquals("qux", ((LiteralValueNode)list.getValues().get(0)).getText());
        ObjectNode objectNode = (ObjectNode)list.getValues().get(1);
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.format().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,Evaluate",
        "${..foo.bar.baz},Observe",
        "#{..foo.bar.baz},Synchronize"
    })
    public void Compact_Content_Syntax_Is_Expanded_Within_ListExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux, %s}", compactIntrinsic);
        InlineArgumentSequenceNode list = (InlineArgumentSequenceNode)
            new InlineParser(input, "fx").parseObjectStrict().getChildren().get(0);
        assertEquals(2, list.getValues().size());
        assertEquals("qux", ((LiteralValueNode)list.getValues().get(0)).getText());
        ObjectNode objectNode = (ObjectNode)list.getValues().get(1);
        assertEquals(intrinsicName, objectNode.getType().getName());
        ContentSelectionNode content = assertInstanceOf(
            ContentSelectionNode.class, objectNode.getChildren().get(0));
        assertEquals("foo.bar.baz", assertInstanceOf(PathNode.class, content.getValue()).format());
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,Evaluate",
        "${foo.bar.baz},Observe",
        "#{foo.bar.baz},Synchronize"
    })
    public void Compact_Syntax_Is_Expanded_Within_PropertyExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=%s}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObjectStrict().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        ObjectNode objectNode = (ObjectNode)property.getValues().get(0);
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.format().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,Evaluate",
        "${..foo.bar.baz},Observe",
        "#{..foo.bar.baz},Synchronize"
    })
    public void Compact_Content_Syntax_Is_Expanded_Within_PropertyExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=%s}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObjectStrict().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        ObjectNode objectNode = (ObjectNode)property.getValues().get(0);
        assertEquals(intrinsicName, objectNode.getType().getName());
        ContentSelectionNode content = assertInstanceOf(
            ContentSelectionNode.class, objectNode.getChildren().get(0));
        assertEquals("foo.bar.baz", assertInstanceOf(PathNode.class, content.getValue()).format());
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,Evaluate",
        "${foo.bar.baz},Observe",
        "#{foo.bar.baz},Synchronize"
    })
    public void Compact_Syntax_Inside_Ordinary_Invocation_Shaped_Text_Remains_Literal(
            String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=func(%s, 'quux')}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObjectStrict().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        LiteralValueNode literal = assertInstanceOf(LiteralValueNode.class, property.getValues().get(0));
        assertEquals("func(" + compactIntrinsic + ", 'quux')", literal.getText());
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,Evaluate",
        "${..foo.bar.baz},Observe",
        "#{..foo.bar.baz},Synchronize"
    })
    public void Compact_Content_Syntax_Inside_Ordinary_Invocation_Shaped_Text_Remains_Literal(
            String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=func(%s, 'quux')}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObjectStrict().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        LiteralValueNode literal = assertInstanceOf(LiteralValueNode.class, property.getValues().get(0));
        assertEquals("func(" + compactIntrinsic + ", 'quux')", literal.getText());
    }

    @Test
    public void Mapped_Path_Uses_Logical_Source_Ranges_With_Raw_Projection() {
        String raw = "foo&#46;bar";
        SourceMappedText input = SourceMappedText.decodedXml(raw, new Location(2, 4), XmlEntityDecoder.decode(raw));
        PathNode path = (PathNode)new InlineParser(input, null, Map.of()).parsePathReferenceStrict();

        assertEquals("foo.bar", path.format());
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
        SyntaxNode value = new InlineParser("a + b * c", "fx").parseExpressionStrict();

        BinaryOperatorNode add = assertInstanceOf(BinaryOperatorNode.class, value);
        assertEquals(BinaryOperator.ADD, add.getOperator());
        assertEquals("a", assertInstanceOf(PathNode.class, add.getLeft()).format());

        BinaryOperatorNode multiply = assertInstanceOf(BinaryOperatorNode.class, add.getRight());
        assertEquals(BinaryOperator.MULTIPLY, multiply.getOperator());
        assertEquals("b", assertInstanceOf(PathNode.class, multiply.getLeft()).format());
        assertEquals("c", assertInstanceOf(PathNode.class, multiply.getRight()).format());
        assertEquals(new SourceInfo(0, 0, 0, 9), add.getSourceInfo());
        assertEquals(new SourceInfo(0, 2, 0, 3), add.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 6, 0, 7), multiply.getOperatorSourceInfo());
    }

    @Test
    public void Arithmetic_Expression_Is_Left_Associative() {
        BinaryOperatorNode subtract = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a-b-c", "fx").parseExpressionStrict());
        assertEquals(BinaryOperator.SUBTRACT, subtract.getOperator());
        assertInstanceOf(PathNode.class, subtract.getRight());
        assertEquals(BinaryOperator.SUBTRACT,
            assertInstanceOf(BinaryOperatorNode.class, subtract.getLeft()).getOperator());

        BinaryOperatorNode divide = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a/b/c", "fx").parseExpressionStrict());
        assertEquals(BinaryOperator.DIVIDE, divide.getOperator());
        assertEquals(BinaryOperator.DIVIDE,
            assertInstanceOf(BinaryOperatorNode.class, divide.getLeft()).getOperator());
    }

    @Test
    public void Compiled_Expression_Uses_The_Closed_Precedence_Order() {
        BinaryOperatorNode logicalOr = assertBinary(
            new InlineParser("ready || a + b < c * d && flag", "fx").parseExpressionStrict(),
            BinaryOperator.LOGICAL_OR);
        assertEquals("ready", assertInstanceOf(PathNode.class, logicalOr.getLeft()).format());

        BinaryOperatorNode logicalAnd = assertBinary(logicalOr.getRight(), BinaryOperator.LOGICAL_AND);
        BinaryOperatorNode lessThan = assertBinary(logicalAnd.getLeft(), BinaryOperator.LESS_THAN);
        BinaryOperatorNode add = assertBinary(lessThan.getLeft(), BinaryOperator.ADD);
        BinaryOperatorNode multiply = assertBinary(lessThan.getRight(), BinaryOperator.MULTIPLY);

        assertEquals("a", assertInstanceOf(PathNode.class, add.getLeft()).format());
        assertEquals("b", assertInstanceOf(PathNode.class, add.getRight()).format());
        assertEquals("c", assertInstanceOf(PathNode.class, multiply.getLeft()).format());
        assertEquals("d", assertInstanceOf(PathNode.class, multiply.getRight()).format());
        assertEquals("flag", assertInstanceOf(PathNode.class, logicalAnd.getRight()).format());
        assertEquals(new SourceInfo(0, 6, 0, 8), logicalOr.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 15, 0, 16), lessThan.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 23, 0, 25), logicalAnd.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 0, 0, 30), logicalOr.getSourceInfo());
    }

    @Test
    public void Equality_And_Relational_Operators_Are_Left_Associative() {
        BinaryOperatorNode identity = assertBinary(
            new InlineParser("a == b != c === d !== e", "fx").parseExpressionStrict(),
            BinaryOperator.IDENTITY_NOT_EQUAL);
        assertEquals(BinaryOperator.IDENTITY_EQUAL,
            assertBinary(identity.getLeft(), BinaryOperator.IDENTITY_EQUAL).getOperator());
        BinaryOperatorNode valueNotEqual = assertBinary(
            assertInstanceOf(BinaryOperatorNode.class, identity.getLeft()).getLeft(),
            BinaryOperator.VALUE_NOT_EQUAL);
        assertBinary(valueNotEqual.getLeft(), BinaryOperator.VALUE_EQUAL);

        BinaryOperatorNode relation = assertBinary(
            new InlineParser("a < b <= c > d >= e", "fx").parseExpressionStrict(),
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
    public void Unary_Operators_Are_Recursive() {
        UnaryOperatorNode not = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("!(a < b)", "fx").parseExpressionStrict());
        assertEquals(UnaryOperator.NOT, not.getOperator());
        ParenthesizedNode relation = assertInstanceOf(ParenthesizedNode.class, not.getOperand());
        assertBinary(relation.getOperand(), BinaryOperator.LESS_THAN);

        UnaryOperatorNode boolify = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("!!(value + offset)", "fx").parseExpressionStrict());
        assertEquals(UnaryOperator.BOOLIFY, boolify.getOperator());
        assertBinary(
            assertInstanceOf(ParenthesizedNode.class, boolify.getOperand()).getOperand(),
            BinaryOperator.ADD);

        UnaryOperatorNode nested = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("!-!!value", "fx").parseExpressionStrict());
        assertEquals(UnaryOperator.NOT, nested.getOperator());
        UnaryOperatorNode minus = assertInstanceOf(UnaryOperatorNode.class, nested.getOperand());
        assertEquals(UnaryOperator.MINUS, minus.getOperator());
        assertEquals(UnaryOperator.BOOLIFY,
            assertInstanceOf(UnaryOperatorNode.class, minus.getOperand()).getOperator());
    }

    @Test
    public void Compiled_String_Literal_Retains_Value_Lexeme_And_Span() {
        BinaryOperatorNode equality = assertBinary(
            new InlineParser("name == \"Sm\\\"ith\"", "fx").parseExpressionStrict(),
            BinaryOperator.VALUE_EQUAL);
        StringLiteralNode string = assertInstanceOf(StringLiteralNode.class, equality.getRight());

        assertEquals("Sm\"ith", string.getText());
        assertEquals("\"Sm\\\"ith\"", string.getLexeme());
        assertEquals(new SourceInfo(0, 8, 0, 17), string.getSourceInfo());
    }

    @Test
    public void Method_Type_Arguments_Are_Target_Anchored_And_Leave_Comparison_Tokens() {
        BinaryOperatorNode equality = assertBinary(
            new InlineParser("model.method<T>()==x", "fx").parseExpressionStrict(),
            BinaryOperator.VALUE_EQUAL);
        InvocationNode function = assertInstanceOf(InvocationNode.class, equality.getLeft());
        TextSegmentNode method = assertInstanceOf(
            TextSegmentNode.class, invocationPath(function).getSegments().get(1));

        assertEquals("method", method.getText());
        assertEquals("T", method.getTypeArguments().get(0).format());
        assertEquals(new SourceInfo(0, 5, 0, 6), method.getSelectorSourceInfo());
        assertEquals(new SourceInfo(0, 6, 0, 15), method.getSourceInfo());
        assertEquals(new SourceInfo(0, 17, 0, 19), equality.getOperatorSourceInfo());

        BinaryOperatorNode greater = assertBinary(
            new InlineParser("model::method<T>(x)>=limit", "fx").parseExpressionStrict(),
            BinaryOperator.GREATER_THAN_OR_EQUAL);
        InvocationNode observableFunction = assertInstanceOf(InvocationNode.class, greater.getLeft());
        TextSegmentNode observableMethod = assertInstanceOf(
            TextSegmentNode.class, invocationPath(observableFunction).getSegments().get(1));
        assertTrue(observableMethod.isObservableSelector());
        assertEquals("T", observableMethod.getTypeArguments().get(0).format());
    }

    @Test
    public void Method_Type_Arguments_Commit_Locally_And_Prefix_Positions_Are_Rejected() {
        MarkupException malformed = assertThrows(MarkupException.class,
            () -> new InlineParser("model.method<T,>(x)", "fx").parseExpressionStrict());
        assertEquals(ErrorCode.EXPECTED_IDENTIFIER, malformed.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 15, 0, 16), malformed.getSourceInfo());

        assertInstanceOf(InvocationNode.class,
            new InlineParser("method<T>()", "fx").parseExpressionStrict());
        assertThrows(MarkupException.class,
            () -> new InlineParser("<T>method()", "fx").parseExpressionStrict());
        assertThrows(MarkupException.class,
            () -> new InlineParser("model.<T>method()", "fx").parseExpressionStrict());
        assertInstanceOf(InvocationNode.class,
            new InlineParser("::method()", "fx").parseExpressionStrict());
    }

    @Test
    public void Arithmetic_Expression_Supports_Grouping_And_Unary_Signs() {
        BinaryOperatorNode multiply = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("-(a + b) * +c", "fx").parseExpressionStrict());

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
            new InlineParser("a+1", "fx").parseExpressionStrict());
        assertEquals(BinaryOperator.ADD, add.getOperator());
        assertEquals("1", assertInstanceOf(NumberNode.class, add.getRight()).getText());

        BinaryOperatorNode multiply = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a*-1", "fx").parseExpressionStrict());
        UnaryOperatorNode negativeOne = assertInstanceOf(UnaryOperatorNode.class, multiply.getRight());
        assertEquals(UnaryOperator.MINUS, negativeOne.getOperator());
        assertEquals("1", assertInstanceOf(NumberNode.class, negativeOne.getOperand()).getText());

        UnaryOperatorNode negativePath = assertInstanceOf(UnaryOperatorNode.class,
            new InlineParser("-a", "fx").parseExpressionStrict());
        assertEquals(UnaryOperator.MINUS, negativePath.getOperator());
        assertEquals("a", assertInstanceOf(PathNode.class, negativePath.getOperand()).format());

        BinaryOperatorNode subtract = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("a--b", "fx").parseExpressionStrict());
        assertEquals(BinaryOperator.SUBTRACT, subtract.getOperator());
        UnaryOperatorNode negativeB = assertInstanceOf(UnaryOperatorNode.class, subtract.getRight());
        assertEquals(UnaryOperator.MINUS, negativeB.getOperator());
        assertEquals("b", assertInstanceOf(PathNode.class, negativeB.getOperand()).format());
        assertEquals(new SourceInfo(0, 1, 0, 2), subtract.getOperatorSourceInfo());
        assertEquals(new SourceInfo(0, 2, 0, 3), negativeB.getOperatorSourceInfo());
    }

    @Test
    public void Arithmetic_Expression_Is_Accepted_In_Function_Arguments() {
        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("f(a * 2, b + 1)", "fx").parseExpressionStrict());

        assertEquals(2, function.getArguments().size());

        assertEquals(BinaryOperator.MULTIPLY, assertInstanceOf(
            BinaryOperatorNode.class, function.getArguments().get(0)).getOperator());

        assertEquals(BinaryOperator.ADD, assertInstanceOf(
            BinaryOperatorNode.class, function.getArguments().get(1)).getOperator());
    }

    @Test
    public void Invocation_Retains_One_Generic_Position_And_Punctuation_Spans() {
        InvocationNode invocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("java.lang.Type<T,W>(x,y+1)", "fx").parseExpressionStrict());

        PathNode target = invocationPath(invocation);
        TextSegmentNode terminal = assertInstanceOf(TextSegmentNode.class, target.getSegments().get(2));

        assertEquals("java.lang.Type<T,W>", target.format());
        assertEquals("T", terminal.getTypeArguments().get(0).format());
        assertEquals("W", terminal.getTypeArguments().get(1).format());
        assertEquals(2, invocation.getArguments().size());
        assertBinary(invocation.getArguments().get(1), BinaryOperator.ADD);
        assertEquals(new SourceInfo(0, 14, 0, 19), terminal.getTypeArgumentsSourceInfo());
        assertEquals(new SourceInfo(0, 19, 0, 20), invocation.getOpenParenSourceInfo());
        assertEquals(new SourceInfo(0, 25, 0, 26), invocation.getCloseParenSourceInfo());
        assertEquals(new SourceInfo(0, 0, 0, 26), invocation.getSourceInfo());
        assertEquals("java.lang.Type<T,W>(x,y+1)", invocation.format());
    }

    @Test
    public void Qualified_Invocation_Retains_The_Receiver_Path() {
        InvocationNode invocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("outer.Inner<T,W>(x)", "fx").parseExpressionStrict());

        PathNode target = invocationPath(invocation);
        TextSegmentNode terminal = assertInstanceOf(TextSegmentNode.class, target.getSegments().get(1));

        assertEquals("outer", target.getSegments().get(0).getText());
        assertEquals("Inner", terminal.getText());
        assertEquals("T", terminal.getTypeArguments().get(0).format());
        assertEquals("W", terminal.getTypeArguments().get(1).format());
        assertEquals(new SourceInfo(0, 5, 0, 6), terminal.getSelectorSourceInfo());
        assertEquals(new SourceInfo(0, 0, 0, 19), invocation.getSourceInfo());
        assertEquals("outer.Inner<T,W>(x)", invocation.format());
    }

    @Test
    public void Selected_Invocation_Is_Repeatable_Postfix() {
        InvocationNode nested = assertInstanceOf(InvocationNode.class,
            new InlineParser("factory().Inner().Nested(value)", "fx").parseExpressionStrict());

        SelectedMemberNode nestedTarget = assertInstanceOf(SelectedMemberNode.class, nested.getTarget());
        assertEquals("Nested", nestedTarget.getMember().getText());

        InvocationNode inner = assertInstanceOf(InvocationNode.class, nestedTarget.getReceiver());
        SelectedMemberNode innerTarget = assertInstanceOf(SelectedMemberNode.class, inner.getTarget());
        assertEquals("Inner", innerTarget.getMember().getText());
        assertInstanceOf(InvocationNode.class, innerTarget.getReceiver());

        InvocationNode grouped = assertInstanceOf(InvocationNode.class,
            new InlineParser("(outer).Inner()", "fx").parseExpressionStrict());
        assertInstanceOf(ParenthesizedNode.class,
            assertInstanceOf(SelectedMemberNode.class, grouped.getTarget()).getReceiver());

        InvocationNode afterInvocation = assertInstanceOf(InvocationNode.class,
            new InlineParser("Outer().Inner()", "fx").parseExpressionStrict());
        assertInstanceOf(InvocationNode.class,
            assertInstanceOf(SelectedMemberNode.class, afterInvocation.getTarget()).getReceiver());
    }

    @Test
    public void Invocations_Participate_In_Operator_Trees() {
        BinaryOperatorNode equality = assertBinary(
            new InlineParser("Box<T>(x)==outer.Inner(y)", "fx").parseExpressionStrict(),
            BinaryOperator.VALUE_EQUAL);
        InvocationNode leading = assertInstanceOf(InvocationNode.class, equality.getLeft());
        InvocationNode qualified = assertInstanceOf(InvocationNode.class, equality.getRight());

        assertEquals("T", ((TextSegmentNode)invocationPath(leading)
            .getSegments().get(0)).getTypeArguments().get(0).format());
        assertEquals("outer.Inner", invocationPath(qualified).format());
    }

    @Test
    public void Primitive_And_Malformed_Generic_Lists_Are_Handled_Locally() {
        InvocationNode primitiveArgument = assertInstanceOf(InvocationNode.class,
            new InlineParser("Type<int>(x)", "fx").parseExpressionStrict());

        assertEquals("int", invocationPath(primitiveArgument).getSegments().get(0).getTypeArguments().get(0).format());

        for (String expression : new String[] {
                "Type<>(x)",
                "outer.Inner<>(x)",
                "Type<W,>(x)",
                "outer.Inner<T,>(x)"}) {
            assertThrows(MarkupException.class,
                () -> new InlineParser(expression, "fx").parseExpressionStrict(), expression);
        }
    }

    @Test
    public void Malformed_Invocation_Forms_Report_Local_Spans() {
        assertExpressionError("Type<>(x)", ErrorCode.EXPECTED_IDENTIFIER, 5, 6);
        assertExpressionError("outer.Inner<>(x)", ErrorCode.EXPECTED_IDENTIFIER, 12, 13);
        assertExpressionError("Type<W,>(x)", ErrorCode.EXPECTED_IDENTIFIER, 7, 8);
        assertExpressionError("outer.Inner<T,>(x)", ErrorCode.EXPECTED_IDENTIFIER, 14, 15);

        assertThrows(MarkupException.class, () -> new InlineParser("new Type()", "fx").parseExpressionStrict());
        assertThrows(MarkupException.class, () -> new InlineParser("outer.new Inner()", "fx").parseExpressionStrict());
    }

    @Test
    public void Call_Syntax_Is_Parsed_As_An_Invocation() {
        assertInstanceOf(InvocationNode.class, new InlineParser("Type(x)", "fx").parseExpressionStrict());

        InvocationNode genericInvocation = assertInstanceOf(InvocationNode.class,
            new InlineParser(":context.Type<T>(x)", "fx").parseExpressionStrict());

        assertEquals("T", invocationPath(genericInvocation).getSegments().get(0).getTypeArguments().get(0).format());
        assertInstanceOf(PathNode.class, new InlineParser("Type<T>", "fx").parseExpressionStrict());

        assertThrows(MarkupException.class,
            () -> new InlineParser("outer.new", "fx").parseExpressionStrict());
        assertThrows(MarkupException.class,
            () -> new InlineParser("outer.new package.Inner()", "fx").parseExpressionStrict());
    }

    @Test
    public void Invocation_Arguments_Accept_Full_Expressions_Or_One_Whole_Object() {
        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("f(a || b && c, {Ext value=x}, Type(y))", "fx").parseExpressionStrict());

        assertEquals(3, function.getArguments().size());
        assertBinary(function.getArguments().get(0), BinaryOperator.LOGICAL_OR);
        assertInstanceOf(ObjectNode.class, function.getArguments().get(1));
        assertInstanceOf(InvocationNode.class, function.getArguments().get(2));

        MarkupException objectOperator = assertThrows(MarkupException.class,
            () -> new InlineParser("f({Ext} + value)", "fx").parseExpressionStrict());
        assertEquals(new SourceInfo(0, 8, 0, 9), objectOperator.getSourceInfo());

        assertThrows(MarkupException.class,
            () -> new InlineParser("f(..value)", "fx").parseExpressionStrict());
        assertThrows(MarkupException.class,
            () -> new InlineParser("(..value)", "fx").parseExpressionStrict());
    }

    @Test
    public void Invocation_Shaped_Markup_Content_Remains_Literal() {
        ObjectNode object = new InlineParser("{MyExt Type<T,W>(value)}", "fx").parseObjectStrict();
        LiteralValueNode leading = assertInstanceOf(LiteralValueNode.class, object.getChildren().get(0));
        assertEquals("Type<T,W>(value)", leading.getText());

        object = new InlineParser("{MyExt outer.Inner<T,W>(value)}", "fx").parseObjectStrict();
        LiteralValueNode qualified = assertInstanceOf(LiteralValueNode.class, object.getChildren().get(0));
        assertEquals("outer.Inner<T,W>(value)", qualified.getText());
    }

    @Test
    public void Push_Prefix_And_Relational_Greater_Than_Remain_Positional() {
        ObjectNode push = new InlineParser(">{foo}", "fx").parseObjectStrict();
        assertEquals(Intrinsics.PUSH.getName(), push.getType().getName());

        assertBinary(
            new InlineParser("a > b", "fx").parseExpressionStrict(),
            BinaryOperator.GREATER_THAN);
    }

    @Test
    public void Compiled_Keyword_Literals_Are_Distinct_From_Text_And_Paths() {
        InvocationNode function = assertInstanceOf(InvocationNode.class,
            new InlineParser("f(true, false, null, 'true', :element.true)", "fx").parseExpressionStrict());

        assertEquals(LiteralKeywordNode.Kind.TRUE, assertInstanceOf(
            LiteralKeywordNode.class, function.getArguments().get(0)).getKind());

        assertEquals(LiteralKeywordNode.Kind.FALSE, assertInstanceOf(
            LiteralKeywordNode.class, function.getArguments().get(1)).getKind());

        assertEquals(LiteralKeywordNode.Kind.NULL, assertInstanceOf(
            LiteralKeywordNode.class, function.getArguments().get(2)).getKind());

        assertEquals("true", assertInstanceOf(StringLiteralNode.class, function.getArguments().get(3)).getText());

        PathNode path = assertInstanceOf(PathNode.class, function.getArguments().get(4));
        assertSame(ContextSelector.ELEMENT, path.getContextSelector().getSelector());
        assertEquals("true", path.getSegments().get(0).getText());
    }

    @Test
    public void Zero_Argument_Function_Is_An_Arithmetic_Primary() {
        BinaryOperatorNode add = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("next() + next()", "fx").parseExpressionStrict());

        assertTrue(assertInstanceOf(InvocationNode.class, add.getLeft()).getArguments().isEmpty());
        assertTrue(assertInstanceOf(InvocationNode.class, add.getRight()).getArguments().isEmpty());
    }

    @Test
    public void Slash_Is_Always_Division() {
        BinaryOperatorNode division = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("width/height", "fx").parseExpressionStrict());
        assertNull(assertInstanceOf(PathNode.class, division.getLeft()).getContextSelector());
        assertNull(assertInstanceOf(PathNode.class, division.getRight()).getContextSelector());

        BinaryOperatorNode ordinaryNames = assertInstanceOf(BinaryOperatorNode.class,
            new InlineParser("parent/width", "fx").parseExpressionStrict());
        assertEquals("parent", assertInstanceOf(PathNode.class, ordinaryNames.getLeft()).format());
        assertEquals("width", assertInstanceOf(PathNode.class, ordinaryNames.getRight()).format());
    }

    @Test
    public void NonExpression_Negative_Number_Remains_Literal() {
        PropertyNode property = new InlineParser("{foo value=-1}", "fx").parseObjectStrict().getProperties().get(0);
        assertEquals("-1", assertInstanceOf(LiteralValueNode.class, property.getValues().get(0)).getText());
    }

    @Test
    public void NonExpression_Hyphenated_Text_Is_Preserved() {
        PropertyNode property = new InlineParser("{foo value=foo-bar}", "fx").parseObjectStrict().getProperties().get(0);
        assertEquals("foo-bar", assertInstanceOf(LiteralValueNode.class, property.getValues().get(0)).getText());
    }

    @Test
    public void Arithmetic_Expression_Reports_Unmatched_Parenthesis() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new InlineParser("(a + b", "fx").parseExpressionStrict());
        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Arithmetic_Expression_Reports_Missing_Operand() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new InlineParser("a +", "fx").parseExpressionStrict());
        assertEquals(ErrorCode.UNEXPECTED_END_OF_FILE, ex.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 3, 0, 3), ex.getSourceInfo());

        ex = assertThrows(MarkupException.class, () -> new InlineParser("* a", "fx").parseExpressionStrict());
        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 0, 0, 1), ex.getSourceInfo());
    }

    @Test
    public void Generic_Attribute_Classifies_Each_Outer_Item_Independently() {
        Map<Character, String> prefixes = Map.of('@', "Resource");
        AttributeValueNode firstInline = new InlineParser(
            "@a, plain.css", "fx", prefixes).parseAttribute(AttributeMode.GENERIC);
        AttributeValueNode lastInline = new InlineParser(
            "plain.css, @a", "fx", prefixes).parseAttribute(AttributeMode.GENERIC);

        assertEquals(AttributeValueNode.Form.SEQUENCE, firstInline.getForm());
        assertInstanceOf(ObjectNode.class, firstInline.getItems().get(0));
        assertInstanceOf(LiteralValueNode.class, firstInline.getItems().get(1));
        assertInstanceOf(LiteralValueNode.class, lastInline.getItems().get(0));
        assertInstanceOf(ObjectNode.class, lastInline.getItems().get(1));
        assertEquals("plain.css", ((LiteralValueNode)firstInline.getItems().get(1)).getText());
        assertEquals("plain.css", ((LiteralValueNode)lastInline.getItems().get(0)).getText());
    }

    @Test
    public void Generic_Attribute_Keeps_Opaque_Literal_Items_Out_Of_Expression_Grammar() {
        AttributeValueNode value = new InlineParser(
            "plain.css, true, foo bar, foo(bar), @resource", "fx", Map.of('@', "Resource"))
            .parseAttribute(AttributeMode.GENERIC);

        assertEquals(5, value.getItems().size());
        assertEquals(List.of("plain.css", "true", "foo bar", "foo(bar)"),
            value.getItems().subList(0, 4).stream()
                .map(node -> assertInstanceOf(LiteralValueNode.class, node).getText())
                .toList());
        assertInstanceOf(ObjectNode.class, value.getItems().get(4));
    }

    @Test
    public void Escaped_Item_Prefixes_Remain_On_The_Literal_Path() {
        AttributeValueNode value = new InlineParser(
            "\\@a, \\%b, \\{Ext}, \\$path, \\^custom", "fx",
            Map.of('@', "Resource", '%', "StaticResource", '^', "Custom"))
            .parseAttribute(AttributeMode.GENERIC);

        assertEquals(AttributeValueNode.Form.LITERAL, value.getForm());
        assertEquals("@a, %b, {Ext}, $path, ^custom", value.getLiteral().getText());
        assertEquals(List.of("@a", "%b", "{Ext}", "$path", "^custom"),
            value.getLiteral().getCoercionParts().stream().map(LiteralValueNode::getText).toList());
    }

    @Test
    public void Escaped_Custom_Prefix_Remains_Literal_Inside_An_Attribute_Sequence() {
        AttributeValueNode value = new InlineParser(
            "\\^literal, ^resource", "fx", Map.of('^', "CustomResource"))
            .parseAttribute(AttributeMode.GENERIC);

        assertEquals(AttributeValueNode.Form.SEQUENCE, value.getForm());
        LiteralValueNode literal = assertInstanceOf(LiteralValueNode.class, value.getItems().get(0));
        assertEquals("^literal", literal.getText());
        assertEquals(new SourceInfo(0, 0, 0, 8), literal.getSourceInfo());
        assertEquals(new SourceInfo(0, 1, 0, 9), literal.getSourceInfo().toOriginal());
        ObjectNode extension = assertInstanceOf(ObjectNode.class, value.getItems().get(1));
        assertEquals("CustomResource", extension.getType().getName());
    }

    @Test
    public void Generic_Attribute_Preserves_Empty_Members_Around_Inline_Items() {
        AttributeValueNode value = new InlineParser(
            ", @a,,", "fx", Map.of('@', "Resource")).parseAttribute(AttributeMode.GENERIC);

        assertEquals(4, value.getItems().size());
        assertEquals("", assertInstanceOf(LiteralValueNode.class, value.getItems().get(0)).getText());
        assertInstanceOf(ObjectNode.class, value.getItems().get(1));
        assertEquals("", assertInstanceOf(LiteralValueNode.class, value.getItems().get(2)).getText());
        assertEquals("", assertInstanceOf(LiteralValueNode.class, value.getItems().get(3)).getText());
    }

    @Test
    public void Intrinsic_Slots_Select_Expression_And_Path_Reference_Grammars() {
        ObjectNode value = new InlineParser(
            "#{foo + bar; converter=converters.number; format=formats.decimal; inverseMethod=model.parse}",
            "fx").parseObjectStrict();

        assertInstanceOf(BinaryOperatorNode.class, value.getChildren().get(0));
        assertInstanceOf(PathNode.class, value.findProperty("converter").getValues().get(0));
        assertInstanceOf(PathNode.class, value.findProperty("format").getValues().get(0));
        assertInstanceOf(PathNode.class, value.findProperty("inverseMethod").getValues().get(0));
    }

    @Test
    public void Expression_Entry_Point_Rejects_A_Valid_Prefix_Followed_By_Another_Token() {
        MarkupException exception = assertThrows(
            MarkupException.class,
            () -> new InlineParser("foo bar", "fx").parseExpressionStrict());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, exception.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, 4, 0, 7), exception.getSourceInfo());
    }

    @Test
    public void Parse_Syntax_Nodes_Are_Not_Typed_Literal_Values() {
        assertFalse(ValueNode.class.isAssignableFrom(PathNode.class));
        assertFalse(ValueNode.class.isAssignableFrom(InvocationNode.class));
        assertFalse(ValueNode.class.isAssignableFrom(BinaryOperatorNode.class));
        assertFalse(ValueNode.class.isAssignableFrom(AttributeValueNode.class));
    }

    private BinaryOperatorNode assertBinary(Node value, BinaryOperator operator) {
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
            () -> new InlineParser(source, "fx").parseExpressionStrict(),
            source);

        assertEquals(errorCode, exception.getDiagnostic().getCode(), source);
        assertEquals(new SourceInfo(0, startColumn, 0, endColumn), exception.getSourceInfo(), source);
    }
}
