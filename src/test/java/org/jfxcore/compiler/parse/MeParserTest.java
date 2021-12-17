// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.text.BooleanNode;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.FunctionNode;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.junit.jupiter.api.Test;
import org.jfxcore.compiler.TestBase;

import static org.junit.jupiter.api.Assertions.*;

public class MeParserTest extends TestBase {

    @Test
    public void Non_Extension_Returns_Null() {
        String markup = """
            foo
        """;

        assertNull(new MeParser(markup, "fx").tryParseObject());
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

        ObjectNode root = new MeParser(markup, "fx").tryParseObject();

        assertEquals("GridPane", root.getType().getName());
        assertEquals(4, root.getProperties().size());
        assertEquals(1, root.getChildren().size());
        assertEquals("content text", ((TextNode)root.getChildren().get(0)).getText());

        assertEquals("fx:id", root.getProperties().get(0).getMarkupName());
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
        assertEquals("baz", funcNode.getName().getText());
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

        ObjectNode root = new MeParser(markup, "fx").tryParseObject();

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
            MarkupException.class, () -> new MeParser(markup, "fx").tryParseObject());

        assertEquals(ErrorCode.UNEXPECTED_END_OF_FILE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Empty_Property_Value_Throws() {
        String markup = """
            {GridPane
                style=
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new MeParser(markup, "fx").tryParseObject());

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

        ObjectNode root = new MeParser(markup, "fx").tryParseObject();
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

        ObjectNode root = new MeParser(markup, "fx").tryParseObject();
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

        ObjectNode root = new MeParser(markup, "fx").tryParseObject();
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
            MarkupException.class, () -> new MeParser(markup, "fx").tryParseObject());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Missing_Delimiter_Between_Property_And_Content_Throws() {
        String markup = """
            {Pane
                fx:id=pane0 {Pane}
            }
        """;

        MarkupException ex = assertThrows(
            MarkupException.class, () -> new MeParser(markup, "fx").tryParseObject());

        assertEquals(ErrorCode.UNEXPECTED_TOKEN, ex.getDiagnostic().getCode());
    }

    @Test
    public void Property_And_Content_On_Same_Line() {
        String markup = """
            { GridPane fx:id=pane0 foo; { GridPane fx:id=pane0 } }
        """;

        ObjectNode root = new MeParser(markup, "fx").tryParseObject();
        assertEquals(1, root.getProperties().size());
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
                    prefWidth={fx:bind parent[GridPane:1]/prefWidth}
                }
            }
        """;

        ObjectNode root = new MeParser(markup, "fx").tryParseObject();
        PropertyNode prefWidth = ((ObjectNode)root.getChildren().get(0)).findProperty("prefWidth");
        TextNode listNode = (TextNode)((ObjectNode)prefWidth.getValues().get(0)).getChildren().get(0);
        assertEquals("parent[GridPane:1]/prefWidth", listNode.getText());
    }

    @Test
    public void Intrinsic_Namespace_Is_Detected_When_Intrinsic_Prefix_Is_Specified() {
        ObjectNode root = new MeParser("{GridPane prefWidth={fx:once foo}}", "fx").tryParseObject();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());

        root = new MeParser("{GridPane prefWidth={foo:once foo}}", "foo").tryParseObject();
        assertTrue(((ObjectNode)root.getProperty("prefWidth").getValues().get(0)).getType().isIntrinsic());
    }

    @Test
    public void Invalid_Intrinsic_Namespace_Fails() {
        MarkupException ex = assertThrows(MarkupException.class,
            () -> new MeParser("{GridPane prefWidth={foo:once foo}}", "bar").tryParseObject());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());

        ex = assertThrows(MarkupException.class,
            () -> new MeParser("{GridPane prefWidth={fx:once foo}}", null).tryParseObject());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Literal_Is_Parsed_As_Boolean() {
        ObjectNode root = new MeParser("{Foo bar=true}", null).tryParseObject();
        assertTrue(root.getProperty("bar").getValues().get(0) instanceof BooleanNode);
    }

    @Test
    public void Literal_Is_Parsed_As_Number() {
        ObjectNode root = new MeParser("{Foo bar=5.0}", null).tryParseObject();
        assertTrue(root.getProperty("bar").getValues().get(0) instanceof NumberNode);
    }

}
