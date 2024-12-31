// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.text.BooleanNode;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.FunctionNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.TestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;

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
                    baz(123px, 5.0, "qux quux")
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
        assertEquals("foo bar baz(123px,5.0,qux quux)", list.getText());
        assertEquals(2, list.getValues().size());
        assertEquals("foo bar", ((TextNode)(list.getValues().get(0))).getText());
        assertEquals("baz(123px,5.0,qux quux)", ((TextNode)(list.getValues().get(1))).getText());

        FunctionNode funcNode = (FunctionNode)list.getValues().get(1);
        assertEquals("baz", funcNode.getPath().getText());
        assertEquals(3, funcNode.getArguments().size());
        assertEquals("123px", ((TextNode)funcNode.getArguments().get(0)).getText());
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
        var functionNode = (FunctionNode)objectNode.getChildren().get(0);
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
                    prefWidth=${parent[GridPane:1]/prefWidth}
                }
            }
        """;

        ObjectNode root = new InlineParser(markup, "fx").parseObject();
        PropertyNode prefWidth = ((ObjectNode)root.getChildren().get(0)).findProperty("prefWidth");
        TextNode listNode = (TextNode)((ObjectNode)prefWidth.getValues().get(0)).getChildren().get(0);
        assertEquals("parent[GridPane:1]/prefWidth", listNode.getText());
    }

    @Test
    public void Intrinsic_Namespace_Is_Detected_When_Intrinsic_Prefix_Is_Specified() {
        ObjectNode root = new InlineParser("{GridPane prefWidth=$foo}", "fx").parseObject();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());

        root = new InlineParser("{GridPane prefWidth={foo:once foo}}", "foo").parseObject();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());
    }

    @Test
    public void Invalid_Intrinsic_Namespace_Fails() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new InlineParser("{GridPane prefWidth={foo:once foo}}", "bar").parseObject());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Literal_Is_Parsed_As_Boolean() {
        ObjectNode root = new InlineParser("{Foo bar=true}", null).parseObject();
        assertTrue(root.getProperty("bar").getValues().get(0) instanceof BooleanNode);
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
        ObjectNode root = new InlineParser("$<String>foo", null).parseObject();
        var segment = (TextSegmentNode)((PathNode)root.getChildren().get(0)).getSegments().get(0);
        assertEquals(1, segment.getWitnesses().size());
        assertEquals("String", segment.getWitnesses().get(0).getText());
        assertEquals("foo", segment.getValue().getText());
    }

    @Test
    public void TypeWitnessList_Is_Parsed_Correctly() {
        ObjectNode root = new InlineParser("$<j.l.String, Integer, j.l.Comparable<j.l.Double>>foo", null).parseObject();
        PathNode path = (PathNode)root.getChildren().get(0);
        assertEquals("foo", path.getText());
        TextSegmentNode segment = (TextSegmentNode)path.getSegments().get(0);
        assertEquals(3, segment.getWitnesses().size());
        assertEquals("j.l.String", segment.getWitnesses().get(0).getText());
        assertEquals("Integer", segment.getWitnesses().get(1).getText());
        PathNode witnessPath = segment.getWitnesses().get(2);
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
        ObjectNode root = new InlineParser("$<Foo>foo.<Bar>bar::<Baz<Double>>baz", null).parseObject();
        var segments = ((PathNode)root.getChildren().get(0)).getSegments();
        assertEquals(3, segments.size());
        var segment1 = (TextSegmentNode)segments.get(0);
        assertEquals(1, segment1.getWitnesses().size());
        assertEquals("Foo", segment1.getWitnesses().get(0).getText());
        assertEquals("foo", segment1.getValue().getText());
        assertFalse(segment1.isObservableSelector());
        var segment2 = (TextSegmentNode)segments.get(1);
        assertEquals(1, segment2.getWitnesses().size());
        assertEquals("Bar", segment2.getWitnesses().get(0).getText());
        assertEquals("bar", segment2.getValue().getText());
        assertFalse(segment2.isObservableSelector());
        var segment3 = (TextSegmentNode)segments.get(2);
        assertEquals(1, segment3.getWitnesses().size());
        assertEquals("Baz", segment3.getWitnesses().get(0).getText());
        assertEquals(1, segment3.getWitnesses().get(0).getArguments().size());
        assertEquals("Double", segment3.getWitnesses().get(0).getArguments().get(0).getText());
        assertEquals("baz", segment3.getValue().getText());
        assertTrue(segment3.isObservableSelector());
    }

    @Test
    public void Missing_Close_Angle_Bracket_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () ->
            new InlineParser("$<String", null).parseObject());
        assertEquals(ErrorCode.EXPECTED_TOKEN, ex.getDiagnostic().getCode());
        assertTrue(ex.getDiagnostic().getMessage().contains(">"));
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,once",
        "${foo.bar.baz},bind",
        "#{foo.bar.baz},bindBidirectional"
    })
    public void Compact_Syntax_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.getText().equals("foo.bar.baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$::foo::bar::baz,once",
        "${::foo::bar::baz},bind",
        "#{::foo::bar::baz},bindBidirectional"
    })
    public void Compact_Syntax_With_ObservableSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.getText().equals("foo::bar::baz"));
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,once",
        "${..foo.bar.baz},bind",
        "#{..foo.bar.baz},bindBidirectional"
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
        "$parent[Pane:1]/foo.bar.baz,once",
        "${parent[Pane:1]/foo.bar.baz},bind",
        "#{parent[Pane:1]/foo.bar.baz},bindBidirectional"
    })
    public void Compact_Syntax_With_ContextSelector_Is_Expanded(String compactIntrinsic, String intrinsicName) {
        ObjectNode objectNode = new InlineParser(compactIntrinsic, "fx").parseObject();
        assertEquals(objectNode.getType().getName(), intrinsicName);
        PathNode pathNode = (PathNode)objectNode.getChildren().get(0);
        assertEquals(3, pathNode.getSegments().size());
        assertEquals("foo", pathNode.getSegments().get(0).getText());
        assertEquals("bar", pathNode.getSegments().get(1).getText());
        assertEquals("baz", pathNode.getSegments().get(2).getText());
        assertEquals("parent", pathNode.getContextSelector().getSelector().getText());
        assertEquals("Pane", pathNode.getContextSelector().getSearchType().getText());
        assertEquals("1", pathNode.getContextSelector().getLevel().getText());
    }

    @ParameterizedTest
    @CsvSource({
        "$[..parent[Pane:1]/foo.bar.baz],once",
        "${[..parent[Pane:1]/foo.bar.baz]},bind",
        "#{[..parent[Pane:1]/foo.bar.baz]},bindBidirectional"
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
        assertEquals("parent", pathNode.getContextSelector().getSelector().getText());
        assertEquals("Pane", pathNode.getContextSelector().getSearchType().getText());
        assertEquals("1", pathNode.getContextSelector().getLevel().getText());
    }

    @ParameterizedTest
    @CsvSource({
        "$foo.bar.baz,once",
        "${foo.bar.baz},bind",
        "#{foo.bar.baz},bindBidirectional"
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
        "$..foo.bar.baz,once",
        "${..foo.bar.baz},bind",
        "#{..foo.bar.baz},bindBidirectional"
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
        "$foo.bar.baz,once",
        "${foo.bar.baz},bind",
        "#{foo.bar.baz},bindBidirectional"
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
        "$..foo.bar.baz,once",
        "${..foo.bar.baz},bind",
        "#{..foo.bar.baz},bindBidirectional"
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
        "$foo.bar.baz,once",
        "${foo.bar.baz},bind",
        "#{foo.bar.baz},bindBidirectional"
    })
    public void Compact_Syntax_Is_Expanded_Within_FunctionExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=func(%s, 'quux')}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObject().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        FunctionNode functionNode = (FunctionNode)property.getValues().get(0);
        assertEquals("func", functionNode.getPath().getText());
        assertEquals(2, functionNode.getArguments().size());
        ObjectNode objectNode = (ObjectNode)functionNode.getArguments().get(0);
        assertEquals(intrinsicName, objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof PathNode n && n.getText().equals("foo.bar.baz"));
        assertTrue(functionNode.getArguments().get(1) instanceof TextNode n && n.getText().equals("quux"));
    }

    @ParameterizedTest
    @CsvSource({
        "$..foo.bar.baz,once",
        "${..foo.bar.baz},bind",
        "#{..foo.bar.baz},bindBidirectional"
    })
    public void Compact_Content_Syntax_Is_Expanded_Within_FunctionExpression(String compactIntrinsic, String intrinsicName) {
        String input = String.format("{Test qux=func(%s, 'quux')}", compactIntrinsic);
        PropertyNode property = new InlineParser(input, "fx").parseObject().getProperties().get(0);
        assertEquals("qux", property.getName());
        assertEquals(1, property.getValues().size());
        FunctionNode functionNode = (FunctionNode)property.getValues().get(0);
        assertEquals("func", functionNode.getPath().getText());
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
}
