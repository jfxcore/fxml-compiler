// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.TestBase;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.QualifiedName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
public class FxmlParserTest extends TestBase {

    @Test
    public void CDataSection_Is_Not_Processed() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Test xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    CDATA section: <![CDATA[ < > & ]]>.
                </Test>
            """).parseDocument();

        assertEquals("CDATA section:  < > & .", ((ObjectNode)document.getRoot()).getTextContent().getText());
    }

    @Test
    public void CDataStart_Can_Appear_Within_CDataSection() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Test xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    CDATA section: <![CDATA[ <![CDATA[ ]]>.
                </Test>
            """).parseDocument();

        assertEquals("CDATA section:  <![CDATA[ .", ((ObjectNode)document.getRoot()).getTextContent().getText());
    }

    @Test
    public void CDataEnd_Is_Escaped_With_CDataSection() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Test xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    CDATA section: <![CDATA[<![CDATA[...]]><![CDATA[]]]]><![CDATA[>]]>
                </Test>
            """).parseDocument();

        assertEquals("CDATA section: <![CDATA[...]]>", ((ObjectNode)document.getRoot()).getTextContent().getText());
    }

    @Test
    public void FxNamespace_Is_Not_Required() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" prefWidth="10"/>
            """).parseDocument();

        //noinspection ConstantConditions
        assertEquals("10", ((ObjectNode)document.getRoot()).findProperty("prefWidth").getTrimmedTextNotEmpty(null));
    }

    @Test
    public void Implicit_FxNamespace() {
        DocumentNode document = new FxmlParser(Path.of("."), """
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <GridPane prefWidth="10" fx:id="myGridPane"/>
            """, new EmbeddingContext(List.of(), QualifiedName.of("TestHost"), new Location(0, 0))).parseDocument();

        var root = (ObjectNode)document.getRoot();

        //noinspection ConstantConditions
        assertEquals("myGridPane", root.findIntrinsicProperty(Intrinsics.ID).getTrimmedTextNotEmpty(null));
    }

    @Test
    public void Custom_FxNamespace() {
        DocumentNode document = new FxmlParser(Path.of("."), """
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <GridPane prefWidth="10" xmlns:foo="http://jfxcore.org/fxml/2.0" foo:id="myGridPane"/>
            """, new EmbeddingContext(List.of(), QualifiedName.of("TestHost"), new Location(0, 0))).parseDocument();

        var root = (ObjectNode)document.getRoot();

        //noinspection ConstantConditions
        assertEquals("myGridPane", root.findIntrinsicProperty(Intrinsics.ID).getTrimmedTextNotEmpty(null));
    }

    @Test
    public void Custom_FxNamespace_Hides_Implicit_FxNamespace() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new FxmlParser(Path.of("."), """
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <GridPane prefWidth="10" xmlns:foo="http://jfxcore.org/fxml/2.0" fx:id="myGridPane"/>
            """, new EmbeddingContext(List.of(), QualifiedName.of("TestHost"), new Location(0, 0))).parseDocument());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());
        assertEquals("Unknown XML namespace: fx", ex.getDiagnostic().getMessage());
    }

    @Test
    public void Unknown_Namespace_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <GridPane xmlns="http://javafx.com/javafx" foo:prefWidth="10"/>
            """).parseDocument());

        assertEquals(ErrorCode.UNKNOWN_NAMESPACE, ex.getDiagnostic().getCode());
    }

    @Test
    public void Reserved_Namespace_Cannot_Be_Rebound() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns:xml="http://jfxcore.org/error"/>
            """).parseDocument());

        assertEquals(ErrorCode.RESERVED_NAMESPACE_CANNOT_BE_REBOUND, ex.getDiagnostic().getCode());
    }

    @Test
    public void ProcessingInstructions_Are_Parsed_Correctly() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import javafx.scene.layout.*?>
                <?import javafx.scene.control.Label?>
                <GridPane xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
            """).parseDocument();

        assertTrue(document.getImports().contains("javafx.scene.layout.*"));
        assertTrue(document.getImports().contains("javafx.scene.control.Label"));
    }

    @Test
    public void Prefix_ProcessingInstruction_Is_Parsed_Correctly() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import org.jfxcore.markup.resource.*?>
                <?prefix % = StaticResource?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="% greeting; formatArguments=foo, bar"/>
            """).parseDocument();

        var property = ((ObjectNode)document.getRoot()).findProperty("text");
        assertNotNull(property);
        var objectNode = assertInstanceOf(ObjectNode.class, property.getValues().get(0));
        assertEquals("StaticResource", objectNode.getType().getMarkupName());
        assertTrue(objectNode.getChildren().get(0) instanceof TextNode textNode && textNode.getText().equals("greeting"));
        assertEquals("formatArguments", objectNode.getProperties().get(0).getName());
    }

    @Test
    public void Fully_Qualified_Prefix_ProcessingInstruction_Is_Parsed_Correctly() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?prefix % = org.jfxcore.markup.resource.StaticResource?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="% greeting; formatArguments=foo, bar"/>
            """).parseDocument();

        var property = ((ObjectNode)document.getRoot()).findProperty("text");
        assertNotNull(property);
        var objectNode = assertInstanceOf(ObjectNode.class, property.getValues().get(0));
        assertEquals("org.jfxcore.markup.resource.StaticResource", objectNode.getType().getMarkupName());
        assertTrue(objectNode.getChildren().get(0) instanceof TextNode textNode && textNode.getText().equals("greeting"));
        assertEquals("formatArguments", objectNode.getProperties().get(0).getName());
    }

    @Test
    public void Builtin_Prefixes_Are_Parsed_Without_Imports_Or_Declarations() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="% greeting"
                       graphic="@icons/app.png"/>
            """).parseDocument();

        var root = (ObjectNode)document.getRoot();
        var textProperty = root.findProperty("text");
        assertNotNull(textProperty);
        var textValue = assertInstanceOf(ObjectNode.class, textProperty.getValues().get(0));
        assertEquals("org.jfxcore.markup.resource.StaticResource", textValue.getType().getName());
        assertTrue(textValue.getChildren().get(0) instanceof TextNode textNode && textNode.getText().equals("greeting"));

        var graphicProperty = root.findProperty("graphic");
        assertNotNull(graphicProperty);
        var graphicValue = assertInstanceOf(ObjectNode.class, graphicProperty.getValues().get(0));
        assertEquals("org.jfxcore.markup.resource.ClassPathResource", graphicValue.getType().getName());
        assertTrue(graphicValue.getChildren().get(0) instanceof TextNode textNode && textNode.getText().equals("icons/app.png"));
    }

    @Test
    public void Escaped_Prefix_Is_Not_Processed() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="\\% greeting"/>
            """).parseDocument();

        assertEquals("% greeting", getPropertyText(document, "text"));
    }

    @Test
    public void Explicit_Prefix_Declaration_Overrides_Builtin_Default() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?prefix % = com.example.CustomResource?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="%greeting"/>
            """).parseDocument();

        var property = ((ObjectNode)document.getRoot()).findProperty("text");
        assertNotNull(property);
        var objectNode = assertInstanceOf(ObjectNode.class, property.getValues().get(0));
        assertEquals("com.example.CustomResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof TextNode textNode && textNode.getText().equals("greeting"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@", "%", "&", "^", "°", "§", "?", "~"})
    public void All_Custom_Prefix_Mappings_Are_Parsed_Correctly(String prefix) {
        DocumentNode document = new FxmlParser(String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?prefix %s = com.example.CustomResource?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text="%sgreeting"/>
            """, prefix, prefix)).parseDocument();

        var property = ((ObjectNode)document.getRoot()).findProperty("text");
        assertNotNull(property);
        var objectNode = assertInstanceOf(ObjectNode.class, property.getValues().get(0));
        assertEquals("com.example.CustomResource", objectNode.getType().getName());
        assertTrue(objectNode.getChildren().get(0) instanceof TextNode textNode && textNode.getText().equals("greeting"));
    }

    @Test
    public void Prefix_ProcessingInstruction_With_Identifier_Character_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?prefix t = StaticResource?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
            """).parseDocument());

        assertEquals(ErrorCode.INVALID_EXPRESSION, ex.getDiagnostic().getCode());
    }

    @Test
    public void Duplicate_Prefix_ProcessingInstruction_Fails() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?prefix % = StaticResource?>
                <?prefix % = ClassPathResource?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"/>
            """).parseDocument());

        assertEquals(ErrorCode.DUPLICATE_PREFIX_DECLARATION, ex.getDiagnostic().getCode());
        assertEquals("Prefix '%' is already declared for 'StaticResource'", ex.getDiagnostic().getMessage());
    }

    @Test
    public void Unescape_Character_Entity_References() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       gt="&gt;"
                       lt="&lt;"
                       quot="&quot;"
                       amp="&amp;"
                       apos="&apos;"
                       supplementary="&#x1F600;">
                    <num1>&#100;</num1>
                    <num2>&#xff;</num2>
                    <num3>&#x1F600;</num3>
                </Label>
            """).parseDocument();

        assertEquals(">", getPropertyText(document, "gt"));
        assertEquals("<", getPropertyText(document, "lt"));
        assertEquals("\"", getPropertyText(document, "quot"));
        assertEquals("&", getPropertyText(document, "amp"));
        assertEquals("'", getPropertyText(document, "apos"));
        assertEquals("\uD83D\uDE00", getPropertyText(document, "supplementary"));
        assertEquals(String.valueOf((char)100), getElementText(document, "num1"));
        assertEquals(String.valueOf((char)255), getElementText(document, "num2"));
        assertEquals("\uD83D\uDE00", getElementText(document, "num3"));
    }

    @Test
    public void Unknown_Entities_Bare_Ampersands_And_OnePass_Decoding_Are_Preserved() {
        DocumentNode document = new FxmlParser("""
                <Label xmlns="http://javafx.com/javafx"
                       text="&unknown; & &amp;lt; &AMP;"/>
            """).parseDocument();

        assertEquals("&unknown; & &lt; &AMP;", getPropertyText(document, "text"));
    }

    @Test
    public void Invalid_Numeric_Reference_Has_Exact_Attribute_Source_Range() {
        for (String reference : new String[] {
                "&#;", "&#x;", "&#xG;", "&#x110000;", "&#xD800;", "&#0;", "&#1;"}) {
            String source = "<Label xmlns=\"http://javafx.com/javafx\" text=\"x" + reference + "y\"/>";
            MarkupException exception = assertThrows(MarkupException.class, () -> new FxmlParser(source).parseDocument(), reference);
            int start = source.indexOf(reference);

            assertEquals(ErrorCode.INVALID_EXPRESSION, exception.getDiagnostic().getCode());
            assertEquals(new SourceInfo(0, start, 0, start + reference.length()), exception.getSourceInfo());
        }
    }

    @Test
    public void Missing_Numeric_Semicolon_Stops_Before_Adjacent_Entity() {
        String source = "<Label xmlns=\"http://javafx.com/javafx\" text=\"x&#12&amp;y\"/>";
        MarkupException exception = assertThrows(MarkupException.class, () -> new FxmlParser(source).parseDocument());
        int start = source.indexOf("&#12");

        assertEquals(new SourceInfo(0, start, 0, start + 4), exception.getSourceInfo());
    }

    @Test
    public void Encoded_Compact_Prefix_Uses_Logical_Source_Spans_With_Raw_Projection() {
        String source = "<Label xmlns=\"http://javafx.com/javafx\" "
            + "xmlns:fx=\"http://jfxcore.org/fxml/2.0\" text=\"&#36;{foo}\"/>";
        DocumentNode document = new FxmlParser(source).parseDocument();
        var property = ((ObjectNode)document.getRoot()).findProperty("text");
        ObjectNode value = assertInstanceOf(ObjectNode.class, property.getValues().get(0));
        int start = source.indexOf("&#36;");

        assertEquals(new SourceInfo(0, start, 0, start + 2), value.getType().getSourceInfo());
        assertEquals(new SourceInfo(0, start, 0, start + 6), value.getSourceInfo());
        assertEquals(
            new SourceInfo(0, start, 0, start + 6),
            value.getType().getSourceInfo().toOriginal());
        assertEquals(
            new SourceInfo(0, start, 0, start + 10),
            value.getSourceInfo().toOriginal());
    }

    @Test
    public void Inline_Eof_After_Discarded_Whitespace_Uses_Logical_End_With_Raw_Projection() {
        String source = "<Label xmlns=\"http://javafx.com/javafx\" "
            + "xmlns:fx=\"http://jfxcore.org/fxml/2.0\" text=\"&#36;{foo   \"/>";
        MarkupException exception = assertThrows(MarkupException.class, () -> new FxmlParser(source).parseDocument());
        int end = source.indexOf('"', source.indexOf("&#36;"));

        assertEquals(ErrorCode.EXPECTED_TOKEN, exception.getDiagnostic().getCode());
        assertEquals(new SourceInfo(0, end - 4), exception.getSourceInfo());
        assertEquals(new SourceInfo(0, end), exception.getOriginalSourceInfo());
    }

    @Test
    public void Quoted_Text_Includes_All_Whitespace() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text1=" foo"
                       text2="  foo  "
                       text3="bar  "
                       text4="foo&#x0a;bar"
                       text5="  foo&#x0a;bar  "/>
            """).parseDocument();

        assertEquals(" foo", getPropertyText(document, "text1"));
        assertEquals("  foo  ", getPropertyText(document, "text2"));
        assertEquals("bar  ", getPropertyText(document, "text3"));
        assertEquals("foo\nbar", getPropertyText(document, "text4"));
        assertEquals("  foo\nbar  ", getPropertyText(document, "text5"));
    }

    @Test
    public void Escaped_Markup_Extension() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       text1="\\{foo}  "
                       text2="  \\{foo}  "
                       text3="  \\ bar "/>
            """).parseDocument();

        assertEquals("{foo}  ", getPropertyText(document, "text1"));
        assertEquals("  {foo}  ", getPropertyText(document, "text2"));
        assertEquals("  \\ bar ", getPropertyText(document, "text3"));
        assertSourceInfo(2, 18, 2, 25, getPropertyValue(document, "text1").getSourceInfo());
        assertSourceInfo(3, 18, 3, 27, getPropertyValue(document, "text2").getSourceInfo());
        assertSourceInfo(4, 18, 4, 26, getPropertyValue(document, "text3").getSourceInfo());
        assertSourceInfo(2, 19, 2, 26, getPropertyValue(document, "text1").getSourceInfo().toOriginal());
        assertSourceInfo(3, 18, 3, 28, getPropertyValue(document, "text2").getSourceInfo().toOriginal());
    }

    @Test
    public void Escaped_Markup_With_Entity_Whitespace_Uses_Logical_Text_And_Raw_Projection() {
        String source = "<Label xmlns=\"http://javafx.com/javafx\" "
            + "xmlns:fx=\"http://jfxcore.org/fxml/2.0\" text=\"&#32;\\&#36;{foo}\"/>";
        DocumentNode document = new FxmlParser(source).parseDocument();
        TextNode value = getPropertyValue(document, "text");
        int start = source.indexOf("&#32;");
        int end = source.indexOf('"', start);

        assertEquals(" ${foo}", value.getText());
        assertEquals(new SourceInfo(0, start, 0, start + 7), value.getSourceInfo());
        assertEquals(new SourceInfo(0, start, 0, end), value.getSourceInfo().toOriginal());
    }

    @Test
    public void Whitespace_Handling_InvalidValue() {
        MarkupException ex = assertThrows(MarkupException.class, () -> new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <text xml:space="invalid"/>
                </Label>
            """).parseDocument());

        assertEquals(ErrorCode.CANNOT_COERCE_PROPERTY_VALUE, ex.getDiagnostic().getCode());
        assertEquals("'invalid' is not a valid value for xml:space", ex.getDiagnostic().getMessage());
    }

    @Test
    public void Whitespace_Handling_Default() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <text xml:space="default">
                        foo
                        bar
                        baz
                    </text>
                </Label>
            """).parseDocument();

        ObjectNode element = getElement(document, "text");
        assertEquals("foo\nbar\nbaz", element.getTextContent().getText());

        SourceInfo sourceInfo = element.getTextContent().getSourceInfo();
        assertEquals(3, sourceInfo.getStart().getLine());
        assertEquals(12, sourceInfo.getStart().getColumn());
        assertEquals(5, sourceInfo.getEnd().getLine());
        assertEquals(15, sourceInfo.getEnd().getColumn());
    }

    @Test
    public void Whitespace_Handling_Preserve() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0">
                    <text xml:space="preserve">
                        foo
                        bar
                        baz
                    </text>
                </Label>
            """).parseDocument();

        ObjectNode element = getElement(document, "text");
        assertEquals(
            "\n            foo\n            bar\n            baz\n        ",
            element.getTextContent().getText());

        SourceInfo sourceInfo = element.getTextContent().getSourceInfo();
        assertEquals(2, sourceInfo.getStart().getLine());
        assertEquals(35, sourceInfo.getStart().getColumn());
        assertEquals(6, sourceInfo.getEnd().getLine());
        assertEquals(8, sourceInfo.getEnd().getColumn());
    }

    @Test
    public void InlineParser_Is_Only_Used_For_Attribute_Values() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       prefWidth="{fx:foo bar}">
                    <prefHeight>{fx:foo bar}</prefHeight>
                </Label>
            """).parseDocument();

        var properties = ((ObjectNode)document.getRoot()).getProperties();
        assertEquals(1, properties.size());
        var values = properties.get(0).getValues();
        assertEquals(1, values.size());
        assertInstanceOf(ObjectNode.class, values.get(0));
        assertTrue(((ObjectNode)values.get(0)).getType().isIntrinsic());

        var children = ((ObjectNode)document.getRoot()).getChildren();
        assertEquals(1, children.size());
        values = ((ObjectNode)children.get(0)).getChildren();
        assertEquals(1, values.size());
        assertInstanceOf(TextNode.class, values.get(0));
        assertEquals("{fx:foo bar}", ((TextNode)values.get(0)).getText());
    }

    @Test
    public void Attribute_Is_Parsed_As_List() {
        DocumentNode document = new FxmlParser("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"
                       userData="123.5,
                                 foo

                                 ,bar,
                                 ,
                                 true"/>
            """).parseDocument();

        var properties = ((ObjectNode)document.getRoot()).getProperties();
        assertEquals(1, properties.size());
        var values = properties.get(0).getValues();
        assertEquals(1, values.size());
        var list = assertInstanceOf(ListNode.class, values.get(0)).getValues();
        assertEquals(5, list.size());

        var item1 = assertInstanceOf(NumberNode.class, list.get(0));
        assertEquals("123.5", item1.getText());
        assertSourceInfo(2, 21, 2, 26, item1.getSourceInfo());

        var item2 = assertInstanceOf(TextNode.class, list.get(1));
        assertEquals("foo", item2.getText());
        assertSourceInfo(3, 21, 3, 24, item2.getSourceInfo());

        var item3 = assertInstanceOf(TextNode.class, list.get(2));
        assertEquals("bar", item3.getText());
        assertSourceInfo(5, 22, 5, 25, item3.getSourceInfo());

        var item4 = assertInstanceOf(TextNode.class, list.get(3));
        assertEquals("", item4.getText());
        assertSourceInfo(6, 21, 6, 21, item4.getSourceInfo());

        var item5 = assertInstanceOf(TextNode.class, list.get(4));
        assertEquals("true", item5.getText());
        assertSourceInfo(7, 21, 7, 25, item5.getSourceInfo());
    }

    @Test
    public void Attribute_List_Items_Use_Logical_Ranges_With_Raw_Projection() {
        String source = "<Label xmlns=\"http://javafx.com/javafx\" "
            + "userData=\"one&amp;two, &#x1F600;, three\"/>";
        DocumentNode document = new FxmlParser(source).parseDocument();
        ListNode list = assertInstanceOf(ListNode.class,
            ((ObjectNode)document.getRoot()).findProperty("userData").getValues().get(0));

        int firstStart = source.indexOf("one&amp;two");
        int firstEnd = source.indexOf(',', firstStart);
        int secondStart = source.indexOf("&#x1F600;");
        int thirdStart = source.indexOf("three");

        assertEquals("one&two", ((TextNode)list.getValues().get(0)).getText());
        assertEquals(new SourceInfo(0, firstStart, 0, firstStart + 7), list.getValues().get(0).getSourceInfo());
        assertEquals("\uD83D\uDE00", ((TextNode)list.getValues().get(1)).getText());
        assertEquals(new SourceInfo(0, firstStart + 9, 0, firstStart + 11), list.getValues().get(1).getSourceInfo());
        assertEquals(new SourceInfo(0, firstStart + 13, 0, firstStart + 18), list.getValues().get(2).getSourceInfo());
        assertEquals(
            new SourceInfo(0, firstStart, 0, firstEnd),
            list.getValues().get(0).getSourceInfo().toOriginal());
        assertEquals(
            new SourceInfo(0, secondStart, 0, secondStart + 9),
            list.getValues().get(1).getSourceInfo().toOriginal());
        assertEquals(
            new SourceInfo(0, thirdStart, 0, thirdStart + 5),
            list.getValues().get(2).getSourceInfo().toOriginal());
    }

    @Test
    public void Entity_Newline_List_Items_Use_Logical_Lines_With_Raw_Projection() {
        String source = "<Label xmlns=\"http://javafx.com/javafx\" userData=\"one&#10;two\"/>";
        DocumentNode document = new FxmlParser(source).parseDocument();
        ListNode list = assertInstanceOf(ListNode.class,
            ((ObjectNode)document.getRoot()).findProperty("userData").getValues().get(0));

        int firstStart = source.indexOf("one&#10;two");
        int secondStart = source.indexOf("two", firstStart);

        assertEquals(new SourceInfo(0, firstStart, 0, firstStart + 3), list.getValues().get(0).getSourceInfo());
        assertEquals(new SourceInfo(1, 0, 1, 3), list.getValues().get(1).getSourceInfo());
        assertEquals(
            new SourceInfo(0, secondStart, 0, secondStart + 3),
            list.getValues().get(1).getSourceInfo().toOriginal());
    }

    @Test
    public void Literal_Crlf_Attribute_List_Uses_Second_Line_Columns() {
        String source = "<Label xmlns=\"http://javafx.com/javafx\" userData=\"one,\r\n  two\"/>";
        DocumentNode document = new FxmlParser(source).parseDocument();
        ListNode list = assertInstanceOf(ListNode.class,
            ((ObjectNode)document.getRoot()).findProperty("userData").getValues().get(0));

        assertEquals(new SourceInfo(1, 2, 1, 5), list.getValues().get(1).getSourceInfo());
    }

    private ObjectNode getElement(DocumentNode document, String elementName) {
        return document
            .getRoot().as(ObjectNode.class)
            .getChildren().stream()
            .filter(e -> ((ObjectNode)e).getType().getMarkupName().equals(elementName))
            .findFirst().orElseThrow().as(ObjectNode.class);
    }

    private String getElementText(DocumentNode document, String elementName) {
        return getElement(document, elementName)
            .getTextContent()
            .getText();
    }

    private TextNode getPropertyValue(DocumentNode document, String propertyName) {
        return document
            .getRoot().as(ObjectNode.class)
            .getProperties().stream()
            .filter(p -> p.getName().equals(propertyName))
            .findFirst()
            .orElseThrow()
            .getValues().get(0).as(TextNode.class);
    }

    private String getPropertyText(DocumentNode document, String propertyName) {
        return getPropertyValue(document, propertyName).getText();
    }

    public static void assertSourceInfo(
            int startLine, int startColumn,
            int endLine, int endColumn,
            SourceInfo sourceInfo) {

        assertEquals(startLine, sourceInfo.getStart().getLine());
        assertEquals(startColumn, sourceInfo.getStart().getColumn());
        assertEquals(endLine, sourceInfo.getEnd().getLine());
        assertEquals(endColumn, sourceInfo.getEnd().getColumn());
    }
}
